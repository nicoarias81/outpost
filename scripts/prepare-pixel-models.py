"""Copy three pinned, locally cached research models to the registered Pixel; no downloads or host inference."""
import hashlib
import json
import pathlib
import re
import subprocess
import uuid

root = pathlib.Path(__file__).resolve().parents[1]
registration = json.loads((root / '.local/pixel10-target.json').read_text(encoding='utf-8-sig'))
settings = json.loads((root / '.local/developer-settings.json').read_text(encoding='utf-8-sig'))
serial = registration['serial']
if registration.get('model') != 'Pixel 10 Pro' or registration.get('authorization') != 'owner-request-2026-10-01' or not re.fullmatch(r'[A-Za-z0-9]{6,40}', serial):
    raise ValueError('Invalid explicitly authorized Pixel registration')
adb = str(pathlib.Path(settings['sdk']) / 'platform-tools/adb.exe')

def run(*args):
    p = subprocess.run([adb, '-s', serial, *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode:
        raise RuntimeError(p.stderr.decode('utf-8', errors='replace')[:1500])
    return p.stdout.decode('utf-8').strip()

for key, expected in [('ro.product.model', 'Pixel 10 Pro'), ('ro.product.manufacturer', 'Google'), ('ro.product.cpu.abi', 'arm64-v8a'), ('sys.boot_completed', '1')]:
    if run('shell', 'getprop', key) != expected:
        raise RuntimeError('Incorrect registered target: ' + key)
if run('shell', 'getprop', 'ro.kernel.qemu') == '1':
    raise RuntimeError('Physical Pixel required')
run('shell', 'run-as', 'dev.outpost.app', 'pwd')

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(4 * 1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def exists(path, private=True):
    prefix = ['shell', 'run-as', 'dev.outpost.app'] if private else ['shell']
    return bool(run(*prefix, 'sh', '-c', f"'if test -e {path}; then echo present; fi'"))

spark = json.loads((root / 'app/src/androidTest/assets/candidates/spark17-lock.json').read_text())
bonsai = next(m for m in json.loads((root / 'bonsai-lock.json').read_text())['models'] if m['id'] == 'bonsai4')
qwen = json.loads((root / 'model-lock.json').read_text())
models = [(bonsai, bonsai['file'], 'bonsai4.sha256'), (spark, spark['file'], None), (qwen, 'qwen-test.gguf', 'verified.sha256')]
# Validate every host file before modifying phone storage.
for model, remote_name, marker in models:
    for name in (model['file'], remote_name):
        if not re.fullmatch(r'[A-Za-z0-9._-]+\.gguf', name):
            raise ValueError('Invalid pinned filename')
    if not re.fullmatch(r'[0-9a-f]{64}', model['sha256']):
        raise ValueError('Invalid pinned hash')
    local = root / '.local/models' / model['file']
    size = model.get('bytes', model.get('size'))
    if local.stat().st_size != size or digest(local) != model['sha256']:
        raise ValueError('Host model identity mismatch: ' + model['file'])

run('shell', 'run-as', 'dev.outpost.app', 'mkdir', '-p', 'files/models')
for model, remote_name, marker in models:
    remote = 'files/models/' + remote_name
    size = model.get('bytes', model.get('size'))
    expected = model['sha256']
    if exists(remote):
        if int(run('shell', 'run-as', 'dev.outpost.app', 'stat', '-c', '%s', remote)) != size or run('shell', 'run-as', 'dev.outpost.app', 'sha256sum', remote).split()[0] != expected:
            raise RuntimeError('Existing model differs; preserving it: ' + remote_name)
    else:
        available = int(run('shell', 'df', '-k', '/data').splitlines()[-1].split()[3]) * 1024
        if available < 2 * size + 256 * 1024 * 1024:
            raise RuntimeError('Insufficient storage for staging and private copy; no eviction')
        suffix = uuid.uuid4().hex[:12]
        staging = '/data/local/tmp/outpost-pixel-' + suffix + '.gguf'
        partial = remote + '.pixel-' + suffix + '.part'
        if exists(staging, False) or exists(partial):
            raise RuntimeError('Staging collision; preserving files')
        print('Copying and verifying ' + remote_name, flush=True)
        run('push', str(root / '.local/models' / model['file']), staging)
        if run('shell', 'sha256sum', staging).split()[0] != expected:
            raise RuntimeError('ADB staging hash mismatch; retained ' + staging)
        run('shell', 'run-as', 'dev.outpost.app', 'cp', staging, partial)
        if int(run('shell', 'run-as', 'dev.outpost.app', 'stat', '-c', '%s', partial)) != size or run('shell', 'run-as', 'dev.outpost.app', 'sha256sum', partial).split()[0] != expected:
            raise RuntimeError('Private staging mismatch; retained ' + partial)
        run('shell', 'run-as', 'dev.outpost.app', 'mv', partial, remote)
        run('shell', 'rm', staging)
    # Only product models receive readiness markers; Spark remains research-only.
    if marker:
        path = 'files/models/' + marker
        if exists(path):
            if run('shell', 'run-as', 'dev.outpost.app', 'cat', path).strip() != expected:
                raise RuntimeError('Existing readiness marker differs; preserved')
        else:
            run('shell', 'run-as', 'dev.outpost.app', 'sh', '-c', f"'printf %s {expected} > {path}'")
    print('Verified ' + remote_name + ': ' + expected, flush=True)
print('Models ready on the registered Pixel. Product selection and radio settings unchanged.')
