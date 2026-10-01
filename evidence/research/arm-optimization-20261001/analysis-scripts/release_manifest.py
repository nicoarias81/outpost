from pathlib import Path
import argparse,hashlib,json,struct

parser=argparse.ArgumentParser();parser.add_argument('--admission',required=True);parser.add_argument('--x86',required=True);args=parser.parse_args()
root=Path('E:/projects/outpost')
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
receipt=read(root/'.local/build-receipt.json');assert receipt['versionName']=='0.15.0' and receipt['versionCode']==17
app=root/'app/build/outputs/apk/debug/app-debug.apk';test=root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
assert digest(app)==receipt['appSha256'] and digest(test)==receipt['testApkSha256']
run_specs=[('arm-confirm-20261001T133232Z-a8581810','arm-checks.json',79,False),('arm-lifecycle-20261001T140850Z-b50c180e','arm-checks.json',9,True),('arm-trace-20261001T140941Z-98c14bfd','arm-checks.json',14,True),('chat-20261001T141139Z-ef7751e2','chat-checks.json',45,True),(args.admission,'candidate-checks.json',23,True)]
runs=[]
for run_id,name,count,final in run_specs:
    directory=root/'evidence/runs'/run_id;report=read(directory/name);identity=read(directory/'run.json')
    assert report['passed'] and report['checksPassed']==count and report['runId']==identity['runId']==run_id
    build=identity.get('buildReceipt',identity)
    if final:assert build['appSha256']==receipt['appSha256'] and build['testApkSha256']==receipt['testApkSha256']
    runs.append({'runId':run_id,'report':name,'reportSha256':digest(directory/name),'checksPassed':count,'finalArtifact':final})
x86=read(root/'evidence/runs'/args.x86/'run.json')
assert x86['passed'] and x86['originalApksRestored'] and len(x86['phases'])==4
assert x86['buildReceipt']['appSha256']==receipt['appSha256']
runs.append({'runId':args.x86,'phases':x86['phases'],'originalApksRestored':True})
isolation=[]
for directory in sorted((root/'evidence/runs').glob('pixel-isolation-*')):
    if directory.name<'pixel-isolation-20261001T112000Z':continue
    report=read(directory/'isolation.json');assert report['status']=='restored'
    assert all(e['beforeDigest']==e['afterDigest'] for e in report['entries'])
    isolation.append({'runId':directory.name,'journalSha256':digest(directory/'isolation.json'),'restored':True})
visual=[]
chatdir=root/'evidence/runs/chat-20261001T141139Z-ef7751e2'
chat=read(chatdir/'chat-checks.json');p=chat['runtimeProfile']
assert (p['threads'],p['promptThreads'],p['batch'],p['width'],p['rowTile'],p['decodeRows'],p['persistentThreads'],p['kernel'])==(4,6,128,8,1,4,True,'Q2_0 NEON DotProd')
for name in ['chat-home.png','chat-settings.png','chat-documents.png','chat-pdf.png','chat-conversation.png','chat-sourced.png']:
    path=chatdir/name;data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n';width,height=struct.unpack('>II',data[16:24]);assert (width,height)==(1080,2410)
    visual.append({'file':name,'sha256':digest(path),'width':width,'height':height,'reviewer':'Codex assistant','scope':'Synthetic English app content and expected visible state; not exhaustive accessibility/provider testing.'})
metrics_path='evidence/research/arm-optimization-20261001/confirmation-metrics.json'
metrics=read(root/metrics_path);assert all(c['normalEos'] and c['fullOutputEquivalent'] for c in metrics['cases'])
manifest={'version':'0.15.0','versionCode':17,'date':'2026-10-01','passed':True,'artifactName':'outpost-0.15.0-user-test.apk','appSha256':receipt['appSha256'],'appBytes':app.stat().st_size,'testApkSha256':receipt['testApkSha256'],'buildReceipt':receipt,'metricsPath':metrics_path,'metricsLink':'../'+metrics_path,'manifestLink':'../evidence/releases/0.15.0/manifest.json','runs':runs,'isolationRestorations':isolation,'visualReview':visual,'productProfile':p,'productChatAnswers':[{k:a[k] for k in ('case','text','tokens','firstTokenMs','totalMs','reason')} for a in chat['answers']],'scope':'Local debug candidate. Paired native timings on the specifically authorized Pixel, unchanged model/prompt/sampling and product120-second/192-token limits. No field-quality, universal speedup, energy or public distribution claim.'}
out=root/'evidence/releases/0.15.0';out.mkdir(parents=True,exist_ok=True)
assert not (out/'manifest.json').exists()
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
(out/'build-receipt.json').write_bytes((root/'.local/build-receipt.json').read_bytes())
print(json.dumps({'manifest':str(out/'manifest.json'),'appSha256':manifest['appSha256'],'validatedRuns':len(runs),'restoredIsolations':len(isolation),'visuals':len(visual),'profile':p},indent=2))
