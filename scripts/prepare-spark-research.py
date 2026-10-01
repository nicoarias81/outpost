"""Provision the pinned research GGUF on Outpost35; never run inference on the host."""
import hashlib,json,pathlib,re,subprocess,urllib.request

root=pathlib.Path(__file__).resolve().parents[1]
lock=json.loads((root/'app/src/androidTest/assets/candidates/spark17-lock.json').read_text())
settings=json.loads((root/'.local/developer-settings.json').read_text())
adb=str(pathlib.Path(settings['sdk'])/'platform-tools/adb.exe')
serial='emulator-5582'
def run(*args):
    p=subprocess.run([adb,'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
    return p.stdout.decode('utf-8').strip()
for field,expected in [('ro.kernel.qemu','1'),('ro.boot.qemu.avd_name','Outpost35'),('sys.boot_completed','1')]:
    if run('shell','getprop',field)!=expected:raise RuntimeError('Incorrect emulator identity/state: '+field)
for field,expected in [('airplane_mode_on','1'),('wifi_on','0'),('mobile_data','0')]:
    if run('shell','settings','get','global',field)!=expected:raise RuntimeError('Offline emulator required')
filename=lock['file']
if not re.fullmatch(r'[A-Za-z0-9._-]+\.gguf',filename):raise ValueError('Invalid pinned filename')
local=root/'.local/models'/filename
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(4*1024*1024),b''):h.update(b)
    return h.hexdigest()
if not local.exists():
    url=f"https://huggingface.co/{lock['repo']}/resolve/{lock['revision']}/{filename}"
    partial=local.with_suffix('.gguf.part')
    if partial.exists():raise RuntimeError('Inspect existing partial download before retrying')
    local.parent.mkdir(parents=True,exist_ok=True);n=0;h=hashlib.sha256()
    with urllib.request.urlopen(url,timeout=60) as source,partial.open('xb') as target:
        while b:=source.read(4*1024*1024):
            n+=len(b)
            if n>lock['bytes']:raise ValueError('Oversized model')
            target.write(b);h.update(b)
    if n!=lock['bytes'] or h.hexdigest()!=lock['sha256']:raise ValueError('Downloaded model identity mismatch')
    partial.rename(local)
if local.stat().st_size!=lock['bytes'] or digest(local)!=lock['sha256']:raise ValueError('Host candidate identity mismatch')
remote='files/models/'+filename
run('shell','run-as','dev.outpost.app','mkdir','-p','files/models')
existing=run('shell','run-as','dev.outpost.app','sh','-c',f"'if test -e {remote}; then sha256sum {remote}; fi'")
if existing:
    if existing.split()[0]!=lock['sha256']:raise RuntimeError('Existing candidate differs; preserving it')
    print('Candidate already verified on Outpost35. Product selection unchanged.');raise SystemExit(0)
part=remote+'.part'
existing=run('shell','run-as','dev.outpost.app','sh','-c',f"'if test -e {part}; then echo present; fi'")
if existing:
    if int(run('shell','run-as','dev.outpost.app','stat','-c','%s',part))!=0:raise RuntimeError('Existing nonempty app-private partial preserved; inspect before retrying')
    print('Removing the empty candidate staging file left by a failed transfer.',flush=True)
    run('shell','run-as','dev.outpost.app','rm',part)
staging='/data/local/tmp/outpost-spark17-'+lock['sha256'][:12]+'.gguf'
print('Pushing the pinned model through ADB binary file transfer...',flush=True)
run('push',str(local),staging)
if run('shell','sha256sum',staging).split()[0]!=lock['sha256']:raise RuntimeError('ADB staging identity mismatch')
run('shell','run-as','dev.outpost.app','cp',staging,part)
actual=run('shell','run-as','dev.outpost.app','sha256sum',part).split()[0]
size=int(run('shell','run-as','dev.outpost.app','stat','-c','%s',part))
if actual!=lock['sha256'] or size!=lock['bytes']:raise RuntimeError('App-private candidate identity mismatch; partial retained')
run('shell','run-as','dev.outpost.app','mv',part,remote)
run('shell','rm',staging)
print('Pinned research model verified inside Outpost35; no product profile selected.')
