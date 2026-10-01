from pathlib import Path
import hashlib,json,struct

root=Path('E:/projects/outpost')
base=root/'evidence/research/pixel10-20261001'
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
runs=[]
for name,file,count in [('candidate-admission-20261001T095919Z-c0129c18','candidate-checks.json',23),('candidate-pilot-20261001T100306Z-07d419d8','candidate-checks.json',37),('chat-20261001T104406Z-e60ee466','chat-checks.json',45)]:
    directory=root/'evidence/runs'/name;report=read(directory/file);identity=read(directory/'run.json')
    assert report['passed'] and report['checksPassed']==count and report['runId']==identity['runId']==name
    assert identity['target']=='Pixel10Pro'
    runs.append({'runId':name,'passed':True,'executionChecks':count,'reportSha256':digest(directory/file),'appSha256':identity['appSha256'],'testApkSha256':identity['testApkSha256']})
isolation=read(root/'evidence/runs/pixel-isolation-20261001T104400Z-4cf26208/isolation.json')
assert isolation['status']=='restored' and all(e['beforeDigest']==e['afterDigest'] for e in isolation['entries'])
screen=read(base/'screen-control.json');assert screen['restoredScreenOffTimeout']==screen['previousScreenOffTimeout'] and screen['restoredStayOnWhilePluggedIn']==screen['previousStayOnWhilePluggedIn']
initial=read(base/'initial-device.json');final=read(base/'final-device.json');assert initial['radioSettings']==final['radioSettings']
visual=[]
for name in ('chat-home.png','chat-settings.png','chat-documents.png','chat-pdf.png','chat-conversation.png','chat-sourced.png'):
    path=root/'evidence/runs/chat-20261001T104406Z-e60ee466'/name;data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n'
    width,height=struct.unpack('>II',data[16:24]);assert (width,height)==(1080,2410)
    visual.append({'file':name,'sha256':digest(path),'bytes':len(data),'width':width,'height':height,'reviewedBy':'Codex assistant','reviewScope':'English visible app text, synthetic content, readable controls and expected UI state. Not an exhaustive accessibility/IME/provider audit.'})
record={'date':'2026-10-01','target':'owner-authorized registered Pixel10Pro','productMainAndNativeChanged':False,'runs':runs,'privateDataRestored':True,'isolationJournalSha256':digest(root/'evidence/runs/pixel-isolation-20261001T104400Z-4cf26208/isolation.json'),'screenSettingsRestored':True,'radioSettingsPreserved':True,'visualReview':visual,'qualityReviewSha256':digest(base/'pilot-review.json'),'scope':'Bounded native compatibility and UI validation plus an exploratory known-fixture pilot. Execution PASS is not answer quality. No product model promotion, host inference, battery-life claim or controlled cross-device speedup.'}
out=base/'validation.json';assert not out.exists();out.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
(base/'README.md').write_text('''# Pixel 10 Pro evidence — 2026-10-01

[Results](../../../docs/pixel10-results-2026-10-01.md) and [guarded runbook](../../../docs/pixel10-testing.md) own interpretation and reproduction.

`validation.json` binds the successful 23/37/45-check runs, visual review and data/screen restoration. `pilot-analysis.json`, `pilot-review.json` and `paired-summary/` are derived from the raw 32-answer pilot. Review labels are the implementing assistant's attributed decisions, not an automatic grader or independent field evaluation. Source scripts are retained under `analysis-scripts/`.

The initial/final device snapshots contain limited technical metadata. Full serial registration, models, APK backups and private app state are not in Git. The original user databases/preferences stayed on the phone and were restored with matching aggregate file digests. The visual isolation journal is in `evidence/runs/pixel-isolation-20261001T104400Z-4cf26208`.

Build receipts preserve the first preparatory test APK, the actually executed admission/pilot APK, the intermediate keep-awake APK, and the final visual APK. Main/native APK bytes remained unchanged. No different same-version bytes replaced the frozen user-test release.

Two pre-execution stops remain in raw evidence: a PowerShell variable collision before model execution and the visual suite's existing-content guard. The final suite used temporary isolated app data. No personal message/document contents are included here.
''',encoding='utf-8')
print(json.dumps({'runs':runs,'visualCapturesReviewed':len(visual),'dataRestored':True,'screenRestored':True,'radiosPreserved':True},indent=2))
