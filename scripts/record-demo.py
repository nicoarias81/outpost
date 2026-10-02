"""Prepare and record a real Pixel demo. No host model execution or public posting."""
import argparse,datetime,hashlib,json,os,re,subprocess,time,uuid
from pathlib import Path
R=Path(__file__).resolve().parents[1]
if not (R/'app').exists():R=Path('E:/projects/outpost')
p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','compare','compare-selected','compare-summary','add-summary','record','restore']);p.add_argument('--scene',type=int,default=0);p.add_argument('--from-case',type=int,default=0);p.add_argument('--allow-offline-transition',action='store_true');a=p.parse_args()
if a.action=='record' and not a.allow_offline_transition:p.error('Pass --allow-offline-transition only after owner approval for temporary phone disconnection.')
settings=json.loads((R/'.local/developer-settings.json').read_text(encoding='utf-8-sig'));registration=json.loads((R/'.local/pixel10-target.json').read_text(encoding='utf-8-sig'))
assert registration['model']=='Pixel 10 Pro' and registration['authorization']=='owner-request-2026-10-01'
serial=registration['serial'];assert re.fullmatch('[A-Za-z0-9]{6,40}',serial)
adb=str(Path(settings['sdk'])/'platform-tools/adb.exe');package='dev.outpost.app.releaseqa';component=package+'.test/dev.outpost.app.ReleaseInstrumentation'
active=R/'.local/demo-active.json';state=None
def call(*args,timeout=240):
 result=subprocess.run([adb,'-s',serial,*map(str,args)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
 if result.returncode:raise RuntimeError('ADB failed: '+result.stderr[-600:])
 return result.stdout.strip()
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def save():active.write_text(json.dumps(state,indent=2)+'\n',encoding='utf-8')
def radios():return {k:call('shell','settings','get','global',k) for k in ['airplane_mode_on','wifi_on','mobile_data']}
def data_preferences():
 raw=call('shell','settings list global | grep "^mobile_data"')
 return dict(line.split('=',1) for line in raw.splitlines() if re.fullmatch(r'mobile_data(?:[0-9]+|_always_on)?=.*',line))
def default_network():return call('shell','dumpsys connectivity | grep "Active default network"').strip()
def guard():
 props={k:call('shell','getprop',k) for k in ['ro.kernel.qemu','ro.product.manufacturer','ro.product.model','ro.product.cpu.abi','ro.build.fingerprint','sys.boot_completed']}
 assert props['ro.kernel.qemu']!='1' and props['ro.product.manufacturer']=='Google' and props['ro.product.model']=='Pixel 10 Pro' and props['ro.product.cpu.abi']=='arm64-v8a' and props['sys.boot_completed']=='1'
 return props
def original_state():
 out={}
 for name in ['dev.outpost.app','dev.outpost.app.test']:
  path=call('shell','pm','path',name);assert path.startswith('package:');out[name]=call('shell','sha256sum',path[8:]).split()[0]
 for rel in ['databases','shared_prefs','files/documents']:
  cmd=f"if test -d {rel}; then find {rel} -type f -exec sha256sum {{}} + | sort | sha256sum; else echo absent; fi"
  out[rel]=call('shell','run-as','dev.outpost.app','sh','-c',"'"+cmd+"'")
 return out
def instrument(phase,extra=()):
 call('shell','am','force-stop',package)
 result=call('shell','am','instrument','-w','-e','phase',phase,'-e','demo_session',state['session'],*extra,component,timeout=1500)
 path=Path(state['out']);(path/(phase+('-'+str(a.scene) if phase=='demo-record' else '')+'.log')).write_text(result+'\n',encoding='utf-8')
 lines=[line[len('DEMO_RESULT '):] for line in result.splitlines() if line.startswith('DEMO_RESULT ')];assert len(lines)==1,'No structured demo result'
 data=json.loads(lines[0]);(path/(phase+'.json')).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
 call('pull',remote, str(path/'device'))
 if not data['passed']:raise RuntimeError('Demo phase failed: '+str(data.get('error')))
 return data
def restore_radios():
 if 'showTouchesBefore' in state:
  value=state['showTouchesBefore']
  call('shell','settings','delete','system','show_touches') if value=='null' else call('shell','settings','put','system','show_touches',value)
  assert call('shell','settings','get','system','show_touches')==value,'Touch indicator restoration mismatch'
 before=state['radios']
 call('shell','cmd','connectivity','airplane-mode','enable' if before['airplane_mode_on']=='1' else 'disable')
 call('shell','svc','wifi','enable' if before['wifi_on']=='1' else 'disable')
 end=time.time()+15
 while radios()!=before and time.time()<end:time.sleep(.5)
 assert radios()==before,'Radio restoration mismatch'
 if state.get('bluetoothBefore') in ['0','1']:
  current=call('shell','settings','get','global','bluetooth_on')
  if current!=state['bluetoothBefore']:call('shell','svc','bluetooth','enable' if state['bluetoothBefore']=='1' else 'disable')
  end=time.time()+15
  while call('shell','settings','get','global','bluetooth_on')!=state['bluetoothBefore'] and time.time()<end:time.sleep(.5)
  assert call('shell','settings','get','global','bluetooth_on')==state['bluetoothBefore'],'Bluetooth restoration mismatch'
 if 'mobileDataPreferencesBefore' in state:assert data_preferences()==state['mobileDataPreferencesBefore'],'Mobile-data preference changed'
 state['radiosRestored']=True;save()
props=guard()
if a.action=='prepare':
 baseline_file=R/'.local/demo-tools/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf'
 assert baseline_file.is_file() and sha(baseline_file)=='9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0','Run scripts/prepare-demo-tools.py before device preparation'
 if active.exists():assert json.loads(active.read_text())['status']=='restored','Restore the previous demo first'
 receipt=json.loads((R/'.local/release-build-receipt.json').read_text(encoding='utf-8-sig'))
 for asset in receipt['artifacts']:assert sha(R/asset['path'])==asset['sha256'],'Build artifact changed'
 session='demo-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8]
 out=R/'evidence/runs'/session;out.mkdir(parents=True)
 state={'session':session,'status':'preparing','out':str(out),'device':props,'radios':radios(),'mobileDataPreferencesBefore':data_preferences(),'radiosRestored':False,'original':original_state(),'receipt':receipt,'scenes':[]}
 save();remote=f'/sdcard/Android/data/{package}/files/{session}'
 call('install','-r',R/'app/build/outputs/apk/releaseQa/app-releaseQa.apk')
 call('install','-r',R/'app/build/outputs/apk/androidTest/releaseQa/app-releaseQa-androidTest.apk')
 instrument('demo-prepare');state['status']='isolated';save()
 baseline=R/'.local/demo-tools/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf'
 target=f'/sdcard/Android/data/{package}/files/{baseline.name}'
 prior=call('shell',f'if test -f {target}; then sha256sum {target}; fi')
 if not prior or prior.split()[0]!=sha(baseline):call('push',baseline,target)
 assert call('shell','sha256sum',target).split()[0]==sha(baseline)
 print(json.dumps({'session':session,'status':state['status'],'out':str(out)}))
else:
 assert active.exists(),'No demo journal';state=json.loads(active.read_text());assert re.fullmatch('demo-[0-9TZ]+-[a-f0-9]{8}',state['session'])
 assert state['status']!='restored','Session already restored';remote=f'/sdcard/Android/data/{package}/files/{state["session"]}'
 if a.action=='add-summary':
  result=instrument('demo-add-summary');print(json.dumps({'passed':result['passed']}))
 elif a.action in ['compare','compare-selected','compare-summary']:
  result=instrument('demo-compare-selected' if a.action=='compare-selected' else 'demo-compare-summary' if a.action=='compare-summary' else ('demo-compare-extra' if a.from_case else 'demo-compare'),('-e','case_start',str(a.from_case)));print(json.dumps({'passed':result['passed'],'answers':len(result['answers']),'out':state['out']}))
 elif a.action=='restore':
  try:
   if not state.get('qaRestored'):
    instrument('demo-restore');state['qaRestored']=True;save()
  finally:restore_radios()
  assert original_state()==state['original'],'Original app/data changed'
  state['status']='restored';save();Path(state['out'],'session.json').write_text(json.dumps(state,indent=2)+'\n')
  print(json.dumps({'status':'restored','originalStatePreserved':True,'radios':radios()}))
 elif a.action=='record':
  assert 0<=a.scene<=len(json.loads((R/'app/src/releaseTest/assets/demo/cases.json').read_text())['cases'])+1
  scene_dir=f'{remote}/scene-{a.scene}';host=Path(state['out']);recorder=None;runner=None;pid=None
  if 'showTouchesBefore' not in state:state['showTouchesBefore']=call('shell','settings','get','system','show_touches')
  if 'bluetoothBefore' not in state:state['bluetoothBefore']=call('shell','settings','get','global','bluetooth_on')
  state['status']='recording';state['radiosRestored']=False;save()
  try:
   call('shell','settings','put','system','show_touches','1')
   call('shell','cmd','connectivity','airplane-mode','enable');call('shell','svc','wifi','disable')
   expected={'airplane_mode_on':'1','wifi_on':'0','mobile_data':state['radios']['mobile_data']}
   end=time.time()+30
   transitions=[]
   while True:
    observed=radios();network=default_network();transitions.append({'settings':observed,'defaultNetwork':network})
    if observed==expected and network=='Active default network: none':break
    if time.time()>end:raise RuntimeError('Offline transition did not complete: '+str(transitions[-1]))
    time.sleep(.5)
   state['offlineTransition']=transitions;save()
   assert radios()==expected,'Airplane mode and Wi-Fi state required'
   assert default_network()=='Active default network: none','No active default network required'
   call('shell','am','force-stop',package)
   log=host/f'scene-{a.scene}-instrumentation.log';logfile=log.open('w',encoding='utf-8')
   runner=subprocess.Popen([adb,'-s',serial,'shell','am','instrument','-w','-e','phase','demo-record','-e','demo_session',state['session'],'-e','scene',str(a.scene),component],stdout=logfile,stderr=subprocess.STDOUT,text=True)
   end=time.time()+30
   while call('shell',f'if test -f {scene_dir}/ready.json; then echo ready; fi')!='ready':
    if time.time()>end or runner.poll() is not None:raise RuntimeError('Demo Activity was not ready')
    time.sleep(.3)
   recfile=host/f'scene-{a.scene}-recording.log';recout=recfile.open('w',encoding='utf-8')
   command=f'screenrecord --size 720x1606 --bit-rate 8000000 --time-limit 160 {scene_dir}/raw.mp4 & echo $! > {scene_dir}/record.pid; wait'
   recorder=subprocess.Popen([adb,'-s',serial,'shell',command],stdout=recout,stderr=subprocess.STDOUT,text=True)
   time.sleep(1)
   if recorder.poll() is not None:raise RuntimeError('Screen recording process ended before capture')
   pid=call('shell','cat',scene_dir+'/record.pid');assert re.fullmatch('[0-9]+',pid)
   call('shell','touch',scene_dir+'/go');end=time.time()+150
   while call('shell',f'if test -f {scene_dir}/scene-complete.json; then echo done; fi')!='done':
    if time.time()>end or runner.poll() is not None:raise RuntimeError('Recorded scene did not complete')
    time.sleep(.4)
   call('shell','kill','-2',pid);recorder.wait(timeout=15);pid=None
   call('shell','touch',scene_dir+'/stop-confirmed');runner.wait(timeout=15);logfile.close();recout.close()
   call('pull',scene_dir,str(host/f'scene-{a.scene}'))
   result=json.loads((host/f'scene-{a.scene}/result.json').read_text());assert result['passed'],result.get('error')
   assert default_network()=='Active default network: none','Network appeared during the scene'
   state['scenes'].append({'scene':a.scene,'offlineEvidence':{'radioSettings':expected,'defaultNetwork':'none','mobileDataPreferenceUnchanged':data_preferences()==state['mobileDataPreferencesBefore']},'videoSha256':sha(host/f'scene-{a.scene}/raw.mp4'),'result':result});state['status']='isolated';save()
   print(json.dumps({'scene':a.scene,'passed':True,'out':str(host/f'scene-{a.scene}')}))
  finally:
   if pid:
    try:call('shell','kill','-2',pid)
    except Exception:pass
   if recorder:
    try:recorder.wait(timeout=15)
    except Exception:recorder.terminate()
   if runner and runner.poll() is None:
    call('shell','am','force-stop',package);runner.terminate()
   restore_radios()
   Path(state['out'],'session-progress.json').write_text(json.dumps(state,indent=2)+'\n')
