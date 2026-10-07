"""One registered Pixel workflow, synthetic test packages, recovery journals and radio restoration."""
from pathlib import Path
import argparse,datetime,hashlib,json,re,subprocess,time,uuid
R=Path(__file__).resolve().parents[1];cfg=json.loads((R/'.local/developer-settings.json').read_text(encoding='utf-8-sig'));reg=json.loads((R/'.local/pixel10-target.json').read_text(encoding='utf-8-sig'))
assert reg['model']=='Pixel 10 Pro' and reg['authorization']=='owner-request-2026-10-01'
serial=reg['serial'];adb=str(Path(cfg['sdk'])/'platform-tools/adb.exe');QA='dev.outpost.app.releaseqa';REG='dev.outpost.app.regressionqa'
p=argparse.ArgumentParser();p.add_argument('action',choices=['all','restore']);p.add_argument('--functional-only',action='store_true');a=p.parse_args();active=R/'.local/pixel-full-active.json';state=None
def call(*args,timeout=120,allow=False,binary=False):
 result=subprocess.run([adb,'-s',serial,*map(str,args)],capture_output=True,timeout=timeout)
 if binary:
  if result.returncode:raise RuntimeError(result.stderr.decode('utf-8',errors='replace')[-500:])
  return result.stdout
 text=result.stdout.decode('utf-8',errors='replace').strip();error=result.stderr.decode('utf-8',errors='replace').strip()
 if result.returncode and not allow:raise RuntimeError(('ADB failed: '+error+' '+text)[-700:])
 return text
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()
def save():
 active.write_text(json.dumps(state,indent=2)+'\n');Path(state['out'],'run-progress.json').write_text(json.dumps(state,indent=2)+'\n')
def app_hashes(pkg):
 paths=call('shell','pm','path',pkg,allow=True).splitlines()
 return {Path(x[8:]).name:call('shell','sha256sum',x[8:]).split()[0] for x in paths if x.startswith('package:')}
def private(pkg,script):return call('shell','run-as',pkg,'sh','-c',"'"+script+"'")
def digest(pkg,path):
 text=private(pkg,f'if test -d {path}; then find {path} -type f -exec sha256sum {{}} + | sort | sha256sum; else echo absent; fi')
 return text
def original():
 values={pkg:app_hashes(pkg) for pkg in ['dev.outpost.app','dev.outpost.app.test']}
 if values['dev.outpost.app']:
  for path in ['databases','shared_prefs','files/documents']:values[path]=digest('dev.outpost.app',path)
 return values
def settings():return {k:call('shell','settings','get','global',k) for k in ['airplane_mode_on','wifi_on','mobile_data','bluetooth_on']}
def data_prefs():
 raw=call('shell','settings list global | grep "^mobile_data"')
 return dict(x.split('=',1) for x in raw.splitlines() if re.fullmatch(r'mobile_data(?:[0-9]+|_always_on)?=.*',x))
def network():return call('shell','dumpsys connectivity | grep "Active default network"')
def pull_private(pkg,relative,dest):
 files=private(pkg,f'if test -d {relative}; then find {relative} -type f; elif test -f {relative}; then echo {relative}; fi').splitlines()
 for file in files:
  assert file.startswith('files/evidence/') and '..' not in file
  name=Path(file).name;dest.mkdir(parents=True,exist_ok=True)
  (dest/name).write_bytes(call('exec-out','run-as',pkg,'cat',file,binary=True))
def instrument(pkg,label,extras,relative=None,prefix=None,limit=1800):
 print('Running '+label,flush=True);state['currentPhase']=label;save();call('shell','am','force-stop',pkg)
 folder=Path(state['out'])/(label+'-'+uuid.uuid4().hex[:6]);folder.mkdir()
 component=pkg+'.test/dev.outpost.app.'+('ReleaseInstrumentation' if pkg==QA else 'GenerationInstrumentation')
 output=call('shell','am','instrument','-w',*extras,component,timeout=limit)
 (folder/'instrumentation.log').write_text(output.replace(serial,'<registered-pixel>')+'\n',encoding='utf-8')
 if relative:pull_private(pkg,relative,folder)
 if prefix:
  lines=[x[len(prefix):] for x in output.splitlines() if x.startswith(prefix)];assert len(lines)==1,'Missing structured '+label+' result'
  result=json.loads(lines[0]);(folder/'result.json').write_text(json.dumps(result,indent=2)+'\n')
 else:
  candidates=list(folder.glob('*checks.json'));assert len(candidates)==1,'Missing '+label+' report'
  result=json.loads(candidates[0].read_text(encoding='utf-8-sig'))
 passed=result.get('passed',bool(re.search(r'\bPASS',output)) and not re.search(r'\bFAIL',output))
 entry={'phase':label,'passed':passed,'folder':str(folder.relative_to(R)),'checks':len(result.get('checks',[])) if isinstance(result.get('checks'),list) else result.get('checksPassed',0)}
 state['phases'].append(entry);save()
 if not passed:raise RuntimeError(label+' failed: '+str(result.get('error','inspect report')))
 print('PASS '+label,flush=True);return result,folder
def qa_phase(phase,extras=(),label=None):
 return instrument(QA,label or phase,('-e','phase',phase,*extras),prefix='RELEASE_RESULT ')
def store_phase(phase):
 result,folder=instrument(QA,phase,('-e','phase',phase,'-e','validation_session',state['session']),prefix='VALIDATION_RESULT ')
 return result
def off():
 call('shell','cmd','connectivity','airplane-mode','enable');call('shell','svc','wifi','disable');end=time.time()+30
 while time.time()<end:
  current=settings()
  if current['airplane_mode_on']=='1' and current['wifi_on']=='0' and network()=='Active default network: none':break
  time.sleep(.5)
 assert settings()['airplane_mode_on']=='1' and settings()['wifi_on']=='0' and network()=='Active default network: none'
 assert data_prefs()==state['dataPreferencesBefore']
 state['offlineEvidence']={'airplaneMode':True,'wifiOff':True,'defaultNetwork':'none','dataPreferencesUnchanged':True};save()
def restore_radios():
 old=state['radioBefore'];call('shell','cmd','connectivity','airplane-mode','enable' if old['airplane_mode_on']=='1' else 'disable');call('shell','svc','wifi','enable' if old['wifi_on']=='1' else 'disable')
 if old['bluetooth_on'] in ['0','1'] and call('shell','settings','get','global','bluetooth_on')!=old['bluetooth_on']:call('shell','svc','bluetooth','enable' if old['bluetooth_on']=='1' else 'disable')
 end=time.time()+20
 while settings()!=old and time.time()<end:time.sleep(.5)
 assert settings()==old,'Radio restoration differs';assert data_prefs()==state['dataPreferencesBefore'],'Mobile-data preferences differ'
 state['radiosRestored']=True;save()
def restore():
 try:
  if state.get('releaseStoreIsolated') and not state.get('releaseStoreRestored'):
   store_phase('validation-restore');state['releaseStoreRestored']=True;save()
 finally:
  try:
   if state.get('regressionArchives') and not state.get('regressionDataRestored'):
    call('shell','am','force-stop',REG);backup='files/'+state['session']+'-regression-original'
    for entry in state['regressionArchives']:
     path=entry['path'];index=entry['index']
     if private(REG,f'if test -d {path}; then echo yes; else echo no; fi')=='yes':private(REG,f'mv {path} {backup}/test-{index}')
     if entry['present']:private(REG,f'mv {backup}/original-{index} {path}')
     assert digest(REG,path)==entry['digest'],'Regression data restoration differs'
    private(REG,'printf %s '+state['regressionPriorOwner']+' > files/pixel-regression-owned');state['regressionMarkerFinal']=state['regressionPriorOwner'];state['regressionDataRestored']=True;save()
  finally:restore_radios()
 assert original()==state['originalBefore'],'Original app/data identity changed'
 state['originalPreserved']=True;state['status']='restored';state['currentPhase']=None;save();Path(state['out'],'run.json').write_text(json.dumps(state,indent=2)+'\n')
 print('Original app/data, QA store and connection settings restored.',flush=True)
props={k:call('shell','getprop',k) for k in ['ro.kernel.qemu','ro.product.manufacturer','ro.product.model','ro.product.cpu.abi','ro.build.fingerprint','sys.boot_completed']}
assert props['ro.kernel.qemu']!='1' and props['ro.product.manufacturer']=='Google' and props['ro.product.model']=='Pixel 10 Pro' and props['ro.product.cpu.abi']=='arm64-v8a' and props['sys.boot_completed']=='1'
if a.action=='restore':
 state=json.loads(active.read_text());assert re.fullmatch('pixel-full-[0-9TZ]+-[a-f0-9]{8}',state['session']);restore();raise SystemExit
for journal in ['demo-active.json','pixel-ui-active.json','pixel-full-active.json']:
 path=R/'.local'/journal
 if path.exists():assert json.loads(path.read_text(encoding='utf-8-sig'))['status']=='restored','Restore '+journal+' first'
release=json.loads((R/'.local/release-build-receipt.json').read_text(encoding='utf-8-sig'));regression=json.loads((R/'.local/regression-build-receipt.json').read_text(encoding='utf-8-sig'))
for receipt in [release,regression]:
 for asset in receipt['artifacts']:assert sha(R/asset['path'])==asset['sha256'],'Build asset changed'
assert release['mainSourcesSha256']==regression['mainSourcesSha256'],'Build source identities differ'
script=". ./scripts/environment.ps1; Get-OutpostSourceFingerprint main; Get-OutpostSourceFingerprint androidTest"
verify=subprocess.run(['pwsh','-NoProfile','-ExecutionPolicy','Bypass','-Command',script],cwd=R,capture_output=True,text=True,check=True).stdout.strip().splitlines()
assert verify==[regression['mainSourcesSha256'],regression['testSourcesSha256']],'Sources changed after build' 
existing=app_hashes(REG)
if existing:
 prior=json.loads(active.read_text());assert prior['status']=='restored' and prior.get('regressionCreated') and not prior.get('regressionRemoved'),'Unknown existing regression package'
 assert private(REG,'cat files/pixel-regression-owned')==prior.get('regressionMarkerFinal',prior['regressionOwner']),'Existing regression ownership differs'

session='pixel-full-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8]
owner=session.replace('pixel-full-','pixel-regression-');out=R/'evidence/runs'/session;out.mkdir(parents=True)
state={'session':session,'functionalOnly':a.functional_only,'status':'preparing','out':str(out),'device':props,'radioBefore':settings(),'dataPreferencesBefore':data_prefs(),'initialDefaultNetwork':network(),'originalBefore':original(),'releaseReceipt':release,'regressionReceipt':regression,'regressionOwner':owner,'regressionCreated':False,'regressionExistedBefore':bool(existing),'regressionPriorOwner':private(REG,'cat files/pixel-regression-owned') if existing else None,'regressionArchives':[],'releaseStoreIsolated':False,'phases':[],'passed':False,'originalPreserved':False,'radiosRestored':False}
save();remote='/sdcard/Android/data/'+REG+'/files'
try:
 for receipt in [release,regression]:
  for item in receipt['artifacts']:
   if item['path'].endswith('.apk') and ('releaseQa/' in item['path'] or 'regressionQa/' in item['path']):call('install','-r',R/item['path'])
 state['regressionCreated']=True;save();call('shell','am','force-stop',REG);call('shell','run-as',REG,'mkdir','-p','files')
 if existing:
  backup='files/'+session+'-regression-original';private(REG,'mkdir '+backup)
  for index,path in enumerate(['databases','shared_prefs','files/documents']):
   present=private(REG,f'if test -d {path}; then echo yes; else echo no; fi')=='yes'
   before=digest(REG,path);state['regressionArchives'].append({'path':path,'index':index,'present':present,'digest':before});save()
   if present:private(REG,f'mv {path} {backup}/original-{index}')
 private(REG,'printf %s '+owner+' > files/pixel-regression-owned')
 call('shell','mkdir','-p',remote)
 for name,pin in [('qwen2.5-1.5b-instruct-q4_k_m.gguf','6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e'),('Ternary-Bonsai-1.7B-Q2_0_g64.gguf','6d0ecb3d9055969b5cde332b6fdb60e67ed3599e9f73e56b977731e1467e5c91'),('Ternary-Bonsai-4B-Q2_0_g64.gguf','9d968b04a3c9a794897bcc744c8072fb6a061c0e42efd03c989401ddf8baef0c')]:
  file=R/'.local/models'/name;assert sha(file)==pin,'Cached model differs';print('Staging '+name,flush=True);present=call('shell',f'if test -f {remote}/{name}; then sha256sum {remote}/{name}; fi')
  if not present or present.split()[0]!=pin:call('push',file,remote+'/'+name,timeout=240)
 instrument(REG,'model-import',('-e','model_bootstrap','true'),prefix='BOOTSTRAP_RESULT ')
 qa_external='/sdcard/Android/data/'+QA+'/files'
 call('shell','mkdir','-p',qa_external)
 for name in ['Ternary-Bonsai-1.7B-Q2_0_g64.gguf','Ternary-Bonsai-4B-Q2_0_g64.gguf']:
  source=R/'.local/models'/name;expected=sha(source)
  prior=call('shell',f'if test -f {qa_external}/{name}; then sha256sum {qa_external}/{name}; fi')
  if prior:assert prior.split()[0]==expected,'Existing QA staging model differs; preserve it before testing'
  else:call('shell','cp',remote+'/'+name,qa_external+'/'+name)
  assert call('shell','sha256sum',qa_external+'/'+name).split()[0]==expected
 state['releaseStoreIsolated']=True;save();store_phase('validation-isolate')
 accept='accept-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8];state['acceptOwner']=accept;save()
 args=('-e','accept_run',accept,'-e','owner_run',accept)
 result,folder=qa_phase('accept-prepare',args)
 call('pull','/sdcard/Android/data/'+QA+'/files/'+accept,str(folder/'device'))
 qa_phase('smoke',label='release-smoke-before-offline')
 qa_phase('generate',label='release-bonsai17-before-offline')
 print('Setup complete. Enabling airplane mode for offline tests.',flush=True);off()
 for suite,flags,file in [
  ('chat',('chat_generate','true','chat_target','Pixel10Pro'),'chat-checks.json'),
  ('knowledge',('knowledge','true'),'knowledge-checks.json'),
  ('folders',(),'folder-checks.json'),('osm',('osm_generate','true'),'osm-checks.json'),('places',(),'place-checks.json')]:
  run=suite+'-'+uuid.uuid4().hex[:20];params=[]
  key={'chat':'chat_run','knowledge':'knowledge_run','folders':'folder_run','osm':'osm_run','places':'places_run'}[suite];params+=['-e',key,run]
  for i in range(0,len(flags),2):params+=['-e',flags[i],flags[i+1]]
  receiver=REG+'.test/dev.outpost.app.FixtureGrantReceiver'
  if suite in ['folders','osm']:
   granted=call('shell','am','broadcast','--include-stopped-packages','-n',receiver,'--es','run_id',run,'--es','operation','grant');assert 'result=-1' in granted,granted
  try:instrument(REG,suite,params,relative='files/evidence/'+suite+'/'+run)
  finally:
   if suite in ['folders','osm']:call('shell','am','broadcast','--include-stopped-packages','-n',receiver,'--es','run_id',run,'--es','operation','revoke')
 instrument(REG,'answer-history-regression',('-e','answer_diagnostic','true'),prefix='ANSWER_DIAGNOSTIC ')
 instrument(REG,'qwen-generation',('-e','generation','true'),relative='files/evidence/generation-checks.json')
 for phase in ([] if a.functional_only else ['numeric','row-numeric','i8mm-numeric','controller','lifecycle','row-lifecycle','decode-six-lifecycle','i8mm-lifecycle','stack-confirm']):
  run='arm-'+phase+'-'+uuid.uuid4().hex[:16]
  instrument(REG,'arm-'+phase,('-e','arm_run',run,'-e','arm_phase',phase),relative='files/evidence/arm/'+run,limit=2400)
 for phase in ['chat','seed','recover']:
  run='accept-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8]
  if phase=='recover':call('shell','am','force-stop',QA);call('install','-r',R/'app/build/outputs/apk/releaseQa/app-releaseQa.apk')
  result,folder=qa_phase('accept-'+phase,('-e','accept_run',run,'-e','owner_run',accept))
  call('pull','/sdcard/Android/data/'+QA+'/files/'+run,str(folder/'device'))
 assert network()=='Active default network: none';state['passed']=True;save()
except Exception as error:
 state['failure']=str(error).replace(serial,'<registered-pixel>');save();print('FAILED: '+state['failure'],flush=True)
finally:restore()
if state['passed'] and not state['regressionExistedBefore']:
 assert private(REG,'cat files/pixel-regression-owned')==owner
 call('uninstall',REG+'.test');call('uninstall',REG);state['regressionRemoved']=True;save();Path(state['out'],'run.json').write_text(json.dumps(state,indent=2)+'\n')
print(json.dumps({'passed':state['passed'],'out':state['out'],'phases':state['phases'],'originalPreserved':state['originalPreserved'],'radiosRestored':state['radiosRestored']}),flush=True)
if not state['passed']:raise SystemExit(1)
