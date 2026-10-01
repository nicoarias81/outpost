"""Read frozen reports only; no inference or automatic quality score."""
from pathlib import Path
import hashlib
import json
import statistics
import sys

root=Path('E:/projects/outpost')
run=root/'evidence/runs'/sys.argv[1]
report=json.loads((run/'candidate-checks.json').read_text(encoding='utf-8-sig'))
assert report['passed'] and report['phase']=='pilot' and report['target']=='Pixel10Pro'
rows=[r for r in report['answers'] if r.get('fixtureId')]
assert len(rows)==32
models={}
for model in ('spark17','bonsai4'):
    selected=[r for r in rows if r['model']==model]
    fixed=[r for r in selected if r['arm']=='fixed']
    temperatures=[r[k]['batteryTemperatureTenthsC']/10 for r in selected for k in ('conditionsBefore','conditionsAfter')]
    thermal=[r[k]['thermalStatus'] for r in selected for k in ('conditionsBefore','conditionsAfter')]
    models[model]={
        'returnedCalls':len(selected),
        'fixedCaseStops':{str(reason):sum(r['stopReason']==reason for r in fixed) for reason in (0,1,2,3)},
        'allArmStops':{str(reason):sum(r['stopReason']==reason for r in selected) for reason in (0,1,2,3)},
        'fixedNativeMedianMs':statistics.median(r['totalMs'] for r in fixed),
        'fixedFirstTokenMedianMs':statistics.median(r['firstTokenMs'] for r in fixed),
        'fixedOutputMedianTokens':statistics.median(r['tokens'] for r in fixed),
        'sampledProcessPeakKiB':max(r['memory']['sampledPeakPssKiB'] for r in selected),
        'sampledBatteryTemperatureMinMaxC':[min(temperatures),max(temperatures)],
        'observedBoundaryThermalStatuses':sorted(set(thermal)),
        'medianProcessCpuToWallRatio':statistics.median(r['processCpuMs']/r['wallMsIncludingProbe'] for r in selected),
        'majorFaultDeltas':[r['memory']['samples'][-1].get('majorFaults',0)-r['memory']['samples'][0].get('majorFaults',0) for r in selected],
        'maxMemorySamplingGapMs':max(r['memory']['maxObservedGapMs'] for r in selected),
        'allEos':all(r['stopReason']==0 for r in selected),
    }
pairs=[]
for case in sorted({r['fixtureId'] for r in rows}):
    pair={'fixture':case,'models':{}}
    for model in models:
        a=next(r for r in rows if r['fixtureId']==case and r['model']==model and r['arm']=='fixed')
        b=next(r for r in rows if r['fixtureId']==case and r['model']==model and r['arm']=='retrieval')
        pair['models'][model]={'fixed':{k:a[k] for k in ('totalMs','firstTokenMs','prefillMs','decodeMs','tokens','stopReason')},'retrieval':{k:b[k] for k in ('totalMs','tokens','stopReason')},'sameArmPrompt':(a['system'],a['user'])==(b['system'],b['user']),'sameArmText':a['text']==b['text']}
    pairs.append(pair)
result={'runId':report['runId'],'reportSha256':hashlib.sha256((run/'candidate-checks.json').read_bytes()).hexdigest(),'scope':'Descriptive physical Pixel pilot. Screen/foreground state changed during the run; no controlled cross-device speedup or energy claim. Process peaks may include prior model during switching. Deadline/cap outputs are incomplete costs, not full-answer latency.','models':models,'cases':pairs}
out=root/'evidence/research/pixel10-20261001/pilot-analysis.json'
assert not out.exists()
out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
