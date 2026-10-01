"""Attribute app-only simpleperf samples to observed first-text boundaries.

Offline report processing only: no device access or model execution. Native
symbols are accepted only when their ELF build ID matches the recorded DSO.
"""
import argparse
import bisect
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--engine', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.ndk / 'simpleperf'))
    from simpleperf_report_lib import ReportLib
    llvm = args.ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin'
    notes = subprocess.check_output([llvm / 'llvm-readelf.exe', '-n', args.engine], text=True)
    build_id = re.search(r'Build ID: ([0-9a-f]+)', notes).group(1)
    nm = subprocess.check_output([llvm / 'llvm-nm.exe', '-n', '-S', '-C', '--defined-only', args.engine], text=True)
    symbols = []
    for line in nm.splitlines():
        match = re.match(r'^([0-9a-f]+) ([0-9a-f]+) ([TtWw]) (.*)$', line)
        if match and int(match[2], 16):
            symbols.append((int(match[1], 16), int(match[2], 16), match[4]))
    symbols.sort()
    addresses = [s[0] for s in symbols]
    report_bytes = (args.run / 'arm-checks.json').read_bytes()
    report = json.loads(report_bytes)
    identity = json.loads((args.run / 'run.json').read_text(encoding='utf-8-sig'))
    if not report['passed'] or report['phase'] != 'profile':
        raise ValueError('A completed profile protocol is required')
    if report['runId'] != identity['runId']:
        raise ValueError('Run identity mismatch')
    output = {'schemaVersion': 1, 'runId': report['runId'], 'nativeBuildId': build_id,
              'reportSha256': hashlib.sha256(report_bytes).hexdigest(),
              'appSha256': identity['buildReceipt']['appSha256'],
              'testApkSha256': identity['buildReceipt']['testApkSha256'],
              'nativeElfSha256': hashlib.sha256(args.engine.read_bytes()).hexdigest(),
              'event': 'cpu-clock:u', 'frequencyHz': 100, 'clock': 'monotonic',
              'scope': 'Weighted leaf CPU samples, not wall-time shares, memory-bandwidth measurements or stack traces. Before-first-text includes preparation/prefill/sampling; after-first-text includes decode/sampling/callbacks. Single on/off pair per case is not a calibrated overhead estimate.',
              'cases': []}
    for case in range(2):
        rows = [r for r in report['answers'] if r['id'].startswith(f'profile/case{case}/') and not r.get('warmup')]
        control = next(r for r in rows if not r['cpuSampled'])
        sampled = next(r for r in rows if r['cpuSampled'])
        for field in ['text', 'tokenIds', 'logitTrace', 'stopReason', 'promptTokens', 'tokens']:
            if control[field] != sampled[field]:
                raise ValueError(f'Sampling changed {field}')
        start, first, end = (sampled[k] for k in ['startMonoNs', 'firstCallbackMonoNs', 'endMonoNs'])
        if not start < first < end:
            raise ValueError('Invalid observable boundaries')
        profile_file = args.run / f'profile-case{case}.data'
        log = (args.run / f'profile-case{case}.log').read_text()
        counts = re.search(r'Samples recorded: (\d+)\. Samples lost: (\d+)', log.replace(',', ''))
        if not counts or int(counts[1]) == 0 or int(counts[2]) != 0:
            raise ValueError('Missing samples, incomplete capture or sample loss')
        phases = {p: {'samples': 0, 'period': 0, 'functions': Counter(), 'cpu': Counter(), 'ips': Counter()} for p in ['beforeFirstText', 'afterFirstText']}
        outside = 0
        lib = ReportLib()
        lib.SetRecordFile(str(profile_file))
        metadata = lib.MetaInfo()
        if metadata.get('clockid') != 'monotonic' or metadata.get('system_wide_collection') != 'false':
            lib.Close()
            raise ValueError('Expected app-only monotonic sampling')
        checked = set()
        pids = set()
        try:
            while (sample := lib.GetNextSample()) is not None:
                if sample.time < start or sample.time >= end:
                    outside += 1
                    continue
                if sample.in_kernel or lib.GetEventOfCurrentSample().name != 'cpu-clock:u':
                    raise ValueError('Unexpected profiling event or kernel sample')
                pids.add(sample.pid)
                if lib.GetProcessNameOfCurrentSample() != 'dev.outpost.app':
                    raise ValueError('Unexpected sampled process')
                sym = lib.GetSymbolOfCurrentSample()
                name = sym.symbol_name
                if sym.dso_name.endswith('liboutpost_engine.so'):
                    if sym.dso_name not in checked:
                        recorded_id = lib.GetBuildIdForPath(sym.dso_name).removeprefix('0x')
                        # Simpleperf pads shorter ELF build IDs to its maximum width.
                        if not recorded_id.startswith(build_id) or recorded_id[len(build_id):].strip('0'):
                            raise ValueError(f'Native ELF identity mismatch: {recorded_id} != {build_id}')
                        checked.add(sym.dso_name)
                    index = bisect.bisect_right(addresses, sym.vaddr_in_file) - 1
                    if index >= 0:
                        address, size, resolved = symbols[index]
                        if sym.vaddr_in_file < address + size:
                            name = resolved
                        else:
                            name = f'[unresolved engine 0x{sym.vaddr_in_file:x}]'
                else:
                    name = sym.dso_name.split('/')[-1] + ': ' + name
                phase = phases['beforeFirstText' if sample.time < first else 'afterFirstText']
                phase['samples'] += 1
                phase['period'] += sample.period
                phase['functions'][name] += sample.period
                phase['cpu'][sample.cpu] += sample.period
                if sym.dso_name.endswith('liboutpost_engine.so'):
                    phase['ips'][(name, sym.vaddr_in_file)] += sample.period
        finally:
            lib.Close()
        if len(pids) != 1 or not checked:
            raise ValueError('Expected one app process and its verified native engine')
        entry = {'case': case, 'file': profile_file.name, 'sha256': hashlib.sha256(profile_file.read_bytes()).hexdigest(),
                 'samplesRecorded': int(counts[1]), 'samplesLost': int(counts[2]), 'samplesOutsideRequests': outside,
                 'simpleperfVersion': metadata.get('simpleperf_version'),
                 'allTokensAndDistributionsEqual': True,
                 'control': {k: control[k] for k in ['promptTokens', 'tokens', 'firstTokenMs', 'prefillMs', 'decodeMs', 'totalMs', 'conditionsBefore', 'conditionsAfter']},
                 'sampled': {k: sampled[k] for k in ['promptTokens', 'tokens', 'firstTokenMs', 'prefillMs', 'decodeMs', 'totalMs', 'conditionsBefore', 'conditionsAfter']},
                 'phases': {}}
        for label, phase in phases.items():
            if not phase['period'] or not phase['samples']:
                raise ValueError(f'No samples in {label}')
            entry['phases'][label] = {
                'samples': phase['samples'], 'sampledCpuMs': phase['period'] / 1e6,
                'functions': [{'name': name, 'cpuMs': period / 1e6, 'percent': 100 * period / phase['period']} for name, period in phase['functions'].most_common(24)],
                'cpuPercent': {str(cpu): 100 * period / phase['period'] for cpu, period in sorted(phase['cpu'].items())},
                'topNativeAddresses': [{'function': key[0], 'address': hex(key[1]), 'percent': 100 * period / phase['period']} for key, period in phase['ips'].most_common(16)]}
        output['cases'].append(entry)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        raise FileExistsError('Preserve previous analysis; choose a new output path')
    args.output.write_text(json.dumps(output, indent=2) + '\n', encoding='utf-8')
    for case in output['cases']:
        print(f"case{case['case']}: {case['samplesRecorded']} samples; tokens/logits equal; control/sample {case['control']['totalMs']}/{case['sampled']['totalMs']} ms")
        for phase, data in case['phases'].items():
            print(phase, [(row['name'], round(row['percent'], 2)) for row in data['functions'][:6]])


if __name__ == '__main__':
    main()
