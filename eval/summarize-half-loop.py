"""Validate and summarize rotated, complete-output ARM scheduling pairs."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('run',type=Path)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists(): raise SystemExit('Choose a new output path')
raw=(a.run/'arm-checks.json').read_bytes()
r=json.loads(raw)
identity=json.loads((a.run/'run.json').read_text(encoding='utf-8-sig'))
assert r['passed'] and r['phase']=='halfloop' and r['runId']==identity['runId']
rows=[x for x in r['answers'] if not x.get('warmup')]
assert len(rows)==12
cases=[]
for case in range(2):
    subset=[x for x in rows if x['id'].startswith(f'halfloop/case{case}/')]
    assert len(subset)==6
    base=subset[0]
    for row in subset:
        for key in ['system','user','maxTokens','deadlineMs','sampler','tokenIds','text','firstLogitsHash','stopReason','logitTrace']:
            assert row[key]==base[key],key
        assert row['logitTrace'] and row['stopReason']==0
        assert row['threads']==4 and row['promptThreads']==6 and row['width']==8
        assert row['decodeRows']==4 and row['persistentThreads'] and not row['affinityMask']
        assert row['halfLoopUsed']==row['halfLoop'] and row['maxTokens']==192 and row['deadlineMs']==120000
        for key in ['conditionsBefore','conditionsAfter']:
            assert row[key]['interactive'] and row[key]['benchmarkHasFocus'] and row[key]['benchmarkResumed']
            assert row[key]['thermalStatus']<3
        assert row['threadpool']['affinityRestored'] and row['threadpool']['pausedAfterRequest']
    arms={}
    for flag,name in [(False,'control'),(True,'candidate')]:
        selected=[x for x in subset if x['halfLoop']==flag]
        assert len(selected)==3
        arms[name]={k:{'observations':[x[k] for x in selected],'median':statistics.median(x[k] for x in selected)}
                    for k in ['prefillMs','firstTokenMs','decodeMs','totalMs']}
    ratios={k:arms['control'][k]['median']/arms['candidate'][k]['median'] for k in arms['control']}
    paired=[]
    for round in range(3):
        pair=[x for x in subset if f'/round{round}/' in x['id']]
        assert len(pair)==2 and pair[0]['halfLoop']!=pair[1]['halfLoop']
        left=next(x for x in pair if not x['halfLoop']);right=next(x for x in pair if x['halfLoop'])
        paired.append({'round':round,'prefillRatio':left['prefillMs']/right['prefillMs'],'totalRatio':left['totalMs']/right['totalMs']})
    cases.append({'case':case,'tokens':base['tokens'],'normalEos':True,'allTokensAndDistributionsEqual':True,
                  'arms':arms,'controlOverCandidateRatio':ratios,'pairs':paired})
out={'schemaVersion':1,'runId':r['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),
     'appSha256':identity['buildReceipt']['appSha256'],'testApkSha256':identity['buildReceipt']['testApkSha256'],
     'cases':cases,'scope':'Known synthetic cases, three rotated full-output pairs. Same product limits and runtime policy; only eight-column scheduling differs. Ratios greater than 1 favor the candidate. No quality, energy or cross-device claim.'}
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
for case in cases: print(json.dumps({'case':case['case'],'ratios':case['controlOverCandidateRatio'],'arms':case['arms']}))
