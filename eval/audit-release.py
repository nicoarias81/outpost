"""Static artifact validation only; never loads or executes native code."""
import argparse, hashlib, json, re, struct, subprocess, tempfile, zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--project',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
r=a.project;settings=json.loads((r/'.local/developer-settings.json').read_text(encoding='utf-8-sig'))
bt=Path(settings['sdk'])/'build-tools/35.0.0'
ndk=r/'.local/native/ndk/android-ndk-r28b/toolchains/llvm/prebuilt/windows-x86_64/bin'
def run(*args):return subprocess.check_output([str(x) for x in args],encoding='utf-8',errors='replace')
def sha(b):return hashlib.sha256(b).hexdigest()
gradle=(r/'app/build.gradle').read_text()
version=re.search(r"versionName\s+'([^']+)'",gradle).group(1)
code=re.search(r'versionCode\s+(\d+)',gradle).group(1)
expected=set(re.findall(r'Java_\w+', (r/'app/src/main/cpp/release.exports').read_text()))
checks=[]
def check(ok,name):
 checks.append(dict(name=name,passed=bool(ok)))
 if not ok:raise AssertionError(name)
def inspect(path,package):
 badging=run(bt/'aapt.exe','dump','badging',path)
 manifest=run(bt/'aapt.exe','dump','xmltree',path,'AndroidManifest.xml')
 check(f"package: name='{package}'" in badging,'package '+package)
 check(f"versionCode='{code}'" in badging and f"versionName='{version}'" in badging,'version '+package)
 check('application-debuggable' not in badging,'non-debuggable '+package)
 check('uses-permission' not in manifest and 'E: instrumentation' not in manifest,'no permissions or instrumentation '+package)
 check(not any('E: '+s in manifest for s in ['provider','service','receiver','activity-alias','profileable']),'no auxiliary components '+package)
 check(manifest.count('E: activity ') == 1,'one launcher activity '+package)
 check('android:allowBackup' in manifest and re.search(r'android:allowBackup[^\n]*=\(type 0x12\)0x0',manifest),'backup disabled '+package)
 check(not re.search(r'android:(debuggable|testOnly)[^\n]*=\(type 0x12\)0xffffffff',manifest),'no debug or testOnly flag '+package)
 run(bt/'zipalign.exe','-c','-P','16','4',path)
 check(True,'16KiB ZIP alignment '+package)
 result={'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size,'native':{},'assets':{}}
 with zipfile.ZipFile(path) as z:
  names=z.namelist()
  check(not any(n.startswith('assets/kev/') or n.endswith(('.gguf','.safetensors')) or n=='assets/library.json' for n in names),'no research head, models or fixture library '+package)
  for n in names:
   if n.startswith('assets/'):result['assets'][n]=sha(z.read(n))
  for n in ['llama-MIT.txt','pdfbox-android-LICENSE.txt','pdfbox-android-NOTICE.txt','bouncycastle-LICENSE.html','android-NDK-NOTICE.txt','android-toolchain-NOTICE.txt']:
   check('assets/licenses/'+n in names,'notice '+n+' '+package)
  libs=[n for n in names if n.endswith('.so')]
  check(set(libs)=={f'lib/{abi}/{lib}' for abi in ['arm64-v8a','x86_64'] for lib in ['liboutpost_engine.so','libc++_shared.so']},'two complete ABIs '+package)
  for n in libs:
   b=z.read(n);check(b[:6]==b'\x7fELF\x02\x01','ELF64 little-endian '+n)
   phoff=struct.unpack_from('<Q',b,32)[0];entsize,count=struct.unpack_from('<HH',b,54)
   loads=[struct.unpack_from('<IIQQQQQQ',b,phoff+i*entsize) for i in range(count)]
   loads=[x for x in loads if x[0]==1]
   check(bool(loads) and all(x[7]>=16384 and x[2]%16384==x[3]%16384 for x in loads),'16KiB ELF LOAD alignment '+n)
   result['native'][n]={'sha256':sha(b),'loadAlignments':[x[7] for x in loads]}
   if n.endswith('liboutpost_engine.so'):
    with tempfile.TemporaryDirectory(prefix='outpost-static-') as td:
     f=Path(td)/'engine.so';f.write_bytes(b)
     symbols=run(ndk/'llvm-nm.exe','-D','--defined-only',f)
    exported={line.split()[-1] for line in symbols.splitlines() if line.split()}
    check(exported==expected,'exact seven production JNI exports '+n)
    result['native'][n]['exports']=sorted(exported)
 return result
try:
 modelSource=(r/'app/src/main/java/dev/outpost/app/ModelStore.java').read_text(encoding='utf-8')
 locks=[json.loads((r/'model-lock.json').read_text()),*json.loads((r/'bonsai-lock.json').read_text())['models']]
 for lock in locks:
  check(lock['repo']+'/resolve/'+lock['revision']+'/' in modelSource,'pinned model download source '+lock['file'])
 release=inspect(r/'app/build/outputs/apk/release/app-release-unsigned.apk','dev.outpost.app')
 qa=inspect(r/'app/build/outputs/apk/releaseQa/app-releaseQa.apk','dev.outpost.app.releaseqa')
 check(release['native']==qa['native'],'QA and unsigned release native bytes identical')
 check(release['assets']==qa['assets'],'QA and unsigned release assets identical')
 with zipfile.ZipFile(r/'app/build/outputs/bundle/release/app-release.aab') as z:
  check(not any(n.startswith('base/assets/kev/') or n.startswith('META-INF/') and n.endswith(('.RSA','.DSA','.EC')) for n in z.namelist()),'AAB has no research head or signature')
  for n,v in release['native'].items():check(sha(z.read('base/'+n))==v['sha256'],'AAB native identity '+n)
 report=dict(passed=True,checks=checks,release=release,qa=qa,limits='Static alignment is not 16KiB runtime validation; QA signature/package differ from production. Signing and exact signed-device acceptance pending.')
except Exception as e:
 report=dict(passed=False,checks=checks,error=str(e))
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':report['passed'],'checks':len(checks),'report':str(a.output),'error':report.get('error')}))
raise SystemExit(0 if report['passed'] else 1)
