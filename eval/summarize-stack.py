"""Validate same-APK original-versus-current policy comparisons."""
from pathlib import Path
import json,hashlib,statistics,argparse
p=argparse.ArgumentParser();p.add_argument('--run',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():raise SystemExit('Preserve previous output')
raw=(a.run/'arm-checks.json').read_bytes();r=json.loads(raw);meta=json.loads((a.run/'run.json').read_text(encoding='utf-8-sig'))
assert r['passed'] and r['phase']=='stack-confirm' and meta['runId']==r['runId']
assert all(c['passed'] for c in r['checks'])
rows={x['id']:x for x in r['answers']}
out={'schemaVersion':1,'runId':r['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),'buildReceipt':meta['buildReceipt'],'checksPassed':r['checksPassed'],'exactParity':True,'cases':[],
     'scope':'Original0.16 4/6 DotProd policy versus combined0.18 6/6 + attention4 + I8MM policy inside one final APK; not cross-APK timing, multiplied historical ratios or energy evidence.'}
for case in range(3):
 pairs=[];reference=None
 for round in range(3):
  base=rows[f'stack/case{case}/round{round}/original'];candidate=rows[f'stack/case{case}/round{round}/current']
  for k in ['system','user','tokenIds','logitTrace','text','stopReason','firstLogitsHash','promptTokens','maxTokens','sampler']:assert base[k]==candidate[k],(case,round,k)
  if reference:
   for k in ['tokenIds','logitTrace','text','stopReason']:assert reference[k]==base[k]
  else:reference=base
  for row in [base,candidate]:
   assert row['status']=='returned' and row['stopReason']==0 and row['maxTokens']==192 and row['deadlineMs']==120000
   assert len(row['tokenIds'])==row['tokens'] and len(row['logitTrace'])==row['tokens']+1
   for condition in [row['conditionsBefore'],row['conditionsAfter']]:assert condition['thermalStatus']<3 and condition['interactive'] and condition['benchmarkHasFocus']
  assert base['threads']==4 and base['promptThreads']==6 and base['attentionThreads']==base['matrixKernel']==0
  assert candidate['threads']==candidate['promptThreads']==6 and candidate['attentionThreads']==4 and candidate['matrixKernel']==1
  assert base['i8mm']['matrixNodes']==0 and candidate['i8mm']['matrixNodes']>0
  assert candidate['attention']['slicedCalls']>=36*(candidate['tokens']-1)
  pairs.append({'round':round,'tokens':base['tokens'],'candidateFirst':candidate['startMonoNs']<base['startMonoNs'],
                **{k:{'original':base[k],'current':candidate[k],'ratio':candidate[k]/base[k]} for k in ['prefillMs','decodeMs','firstTokenMs','totalMs','processCpuMs']},
                'conditionsOriginal':base['conditionsBefore'],'conditionsCurrent':candidate['conditionsBefore']})
 medians={k:statistics.median(x[k]['ratio'] for x in pairs) for k in ['prefillMs','decodeMs','firstTokenMs','totalMs','processCpuMs']}
 times={k:{arm:statistics.median(x[k][arm] for x in pairs) for arm in ['original','current']} for k in ['prefillMs','decodeMs','firstTokenMs','totalMs','processCpuMs']}
 out['cases'].append({'case':case,'pairs':pairs,'medianRatios':medians,'medianTimes':times,'totalImproved':medians['totalMs']<1})
out['combinedGate']=all(c['totalImproved'] for c in out['cases'])
a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
for c in out['cases']:print(c['case'],c['medianTimes'],'ratios',c['medianRatios'])
print('Combined gate:',out['combinedGate'])
