"""Validate and summarize ARM row-scheduling experiments without grading answers."""
import argparse,hashlib,json,statistics
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('run',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--second-run',type=Path)
a=p.parse_args()
if a.output.exists():raise SystemExit('Preserve prior summaries; choose a new output path')
raw=(a.run/'arm-checks.json').read_bytes();r=json.loads(raw)
identity=json.loads((a.run/'run.json').read_text(encoding='utf-8-sig'))
assert r['passed'] and r['runId']==identity['runId'] and r['phase'] in ('row-tune','row-confirm','row-confirm-reverse')
rows=[x for x in r['answers'] if not x.get('warmup')]
for row in rows:row['_campaign']=0
campaigns=1;second=None;second_identity=None
if a.second_run:
    assert r['phase']=='row-confirm'
    second=json.loads((a.second_run/'arm-checks.json').read_bytes())
    second_identity=json.loads((a.second_run/'run.json').read_text(encoding='utf-8-sig'))
    assert second['passed'] and second['phase']=='row-confirm-reverse' and second['reversedOrder']
    assert second_identity['runId']==second['runId']
    assert second_identity['buildReceipt']['appSha256']==identity['buildReceipt']['appSha256']
    for row in second['answers']:
        if not row.get('warmup'):row['_campaign']=1;rows.append(row)
    campaigns=2
rounds=2 if r['phase']=='row-tune' else 3
case_names=['tuning'] if r['phase']=='row-tune' else ['case0','case1']
out={'schemaVersion':1,'runId':r['runId'],'phase':r['phase'],'reportSha256':hashlib.sha256(raw).hexdigest(),
     'appSha256':identity['buildReceipt']['appSha256'],'testApkSha256':identity['buildReceipt']['testApkSha256'],
     'scope':'Rotated/reversed known synthetic workloads. Same attention workers, model, sampling and output budgets. Matched full logit traces establish bounded numerical parity, not answer quality or cross-device speed.','cases':[]}
if second:
    out['secondRunId']=second['runId'];out['secondReportSha256']=hashlib.sha256((a.second_run/'arm-checks.json').read_bytes()).hexdigest();out['secondTestApkSha256']=second_identity['buildReceipt']['testApkSha256']
for case in case_names:
    selected=rows if case=='tuning' else [x for x in rows if f'/{case}/' in x['id']]
    assert len(selected)==(10 if case=='tuning' else 6*campaigns)
    first=selected[0]
    for row in selected:
        assert row['status']=='returned' and row['threads']==4 and row['promptThreads']==6
        assert row['width']==8 and row['decodeRows']==4 and row['persistentThreads'] and row['affinityMask']==0
        for key in ['system','user','maxTokens','deadlineMs','sampler','tokenIds','text','firstLogitsHash','stopReason','logitTrace']:
            assert row[key]==first[key],key
        assert row['logitTrace'] and row['deadlineMs']==120000
        for key in ['conditionsBefore','conditionsAfter']:
            assert row[key]['interactive'] and row[key]['benchmarkHasFocus'] and row[key]['benchmarkResumed']
            assert row[key]['thermalStatus']<3
        assert row['threadpool']['pausedAfterRequest'] and row['threadpool']['affinityRestored']
        assert row['rowSchedule']['prefillChunk']==row['prefillChunk'] and row['rowSchedule']['decodeChunk']==row['decodeChunk']
        assert (row['rowSchedule']['prefillNodes']>0)==(row['prefillChunk']>0)
        assert (row['rowSchedule']['decodeNodes']>0)==(row['decodeChunk']>0)
    assert first['stopReason']==(1 if case=='tuning' else 0)
    configurations=sorted({(x['prefillChunk'],x['decodeChunk']) for x in selected})
    groups=[]
    for pc,dc in configurations:
        group=[x for x in selected if (x['prefillChunk'],x['decodeChunk'])==(pc,dc)]
        assert len(group)==rounds*campaigns
        for campaign in range(campaigns):
            for n in range(rounds):assert sum(f'/round{n}/' in x['id'] and x['_campaign']==campaign for x in group)==1
        groups.append({'prefillChunk':pc,'decodeChunk':dc,'metrics':{key:{'observations':[x[key] for x in group], 'median':statistics.median(x[key] for x in group)} for key in ['prefillMs','firstTokenMs','decodeMs','totalMs']}})
    baseline=next(x for x in groups if (x['prefillChunk'],x['decodeChunk'])==(0,0))
    for group in groups:
        group['baselineOverCandidateRatio']={key:baseline['metrics'][key]['median']/group['metrics'][key]['median'] for key in group['metrics']}
    entry={'case':case,'tokens':first['tokens'],'stopReason':first['stopReason'],'allTokensAndDistributionsEqual':True,'configurations':groups}
    if case!='tuning':
        pairs=[]
        for campaign in range(campaigns):
            for round in range(rounds):
                pair=[x for x in selected if x['_campaign']==campaign and f'/round{round}/' in x['id']]
                assert len(pair)==2
                control=next(x for x in pair if x['prefillChunk']==0 and x['decodeChunk']==0)
                candidate=next(x for x in pair if x['prefillChunk'] or x['decodeChunk'])
                pairs.append({'campaign':campaign,'round':round,'ratios':{k:control[k]/candidate[k] for k in ['prefillMs','firstTokenMs','decodeMs','totalMs']}})
        entry['pairedRatios']=pairs
    out['cases'].append(entry)
if r['phase']=='row-tune':
    best=min(out['cases'][0]['configurations'],key=lambda x:x['metrics']['totalMs']['median'])
    out['exploratoryChoice']={'prefillChunk':best['prefillChunk'],'decodeChunk':best['decodeChunk'],'requiresCompleteConfirmation':True}
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
for case in out['cases']:
    print(case['case'])
    for row in case['configurations']:print(row['prefillChunk'],row['decodeChunk'],{k:v['median'] for k,v in row['metrics'].items()},row['baselineOverCandidateRatio'])
if 'exploratoryChoice' in out:print('Exploratory choice:',out['exploratoryChoice'])
