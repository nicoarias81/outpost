"""Validate exact normal-decode parity and paired timing on recorded Pixel runs."""
from pathlib import Path
import argparse,json,statistics,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():raise SystemExit('Preserve prior summaries')
raw=(a.run/'arm-checks.json').read_bytes();r=json.loads(raw);meta=json.loads((a.run/'run.json').read_text(encoding='utf-8-sig'))
assert r['passed'] and r['phase'] in ['decode-six-pilot','decode-six-confirm'] and meta['runId']==r['runId']
assert all(c['passed'] for c in r['checks'])
rounds=1 if r['phase'].endswith('pilot') else 3
rows={row['id']:row for row in r['answers']}
out={'schemaVersion':1,'runId':r['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),'buildReceipt':meta['buildReceipt'],'checksPassed':r['checksPassed'],'rounds':rounds,'exactParity':True,'cases':[],
     'scope':'Complete sampled answers on the registered Pixel; identical prompt/model/sampler/output budget and full logits/tokens required. USB-powered, uncontrolled background activity and no energy claim. Pilot alone cannot admit a preset.'}
for case in range(3):
 pairs=[];reference=None
 for round in range(rounds):
  base=rows[f'decode6/case{case}/round{round}/plain'];candidate=rows[f'decode6/case{case}/round{round}/candidate']
  for key in ['system','user','promptTokens','maxTokens','sampler','tokenIds','logitTrace','text','stopReason','firstLogitsHash']:assert base[key]==candidate[key],(case,round,key)
  if reference is None:reference=base
  else:
   for key in ['tokenIds','logitTrace','text','stopReason']:assert reference[key]==base[key],(case,round,key)
  for row in (base,candidate):
   assert row['status']=='returned' and row['stopReason']<2 and row['maxTokens']==192 and row['deadlineMs']==120000
   assert row['prefillChunk']==32 and row['decodeChunk']==0 and row['decodeRows']==4 and row['width']==8
   assert len(row['tokenIds'])==row['tokens'] and len(row['logitTrace'])==row['tokens']+(row['stopReason']==0)
   for cond in (row['conditionsBefore'],row['conditionsAfter']):assert cond['thermalStatus']<3 and cond['interactive'] and cond['benchmarkHasFocus']
  assert base['threads']==4 and candidate['threads']==6 and base['promptThreads']==candidate['promptThreads']==6
  stats=candidate['attention'];assert stats['slicedCalls']>=36*(candidate['tokens']-1)
  assert all(stats[k]==0 for k in ['shapeRejections','maskRejections','scratchRejections'])
  pairs.append({'round':round,'candidateFirst':candidate['startMonoNs']<base['startMonoNs'],'tokens':base['tokens'],
                **{metric:{'plain':base[metric],'candidate':candidate[metric],'ratio':candidate[metric]/base[metric]} for metric in ['decodeMs','totalMs','firstTokenMs','prefillMs','processCpuMs']},
                'conditionsPlainBefore':base['conditionsBefore'],'conditionsCandidateBefore':candidate['conditionsBefore']})
 entry={'case':case,'pairs':pairs,'medianRatios':{metric:statistics.median(pair[metric]['ratio'] for pair in pairs) for metric in ['decodeMs','totalMs','firstTokenMs','prefillMs','processCpuMs']}}
 entry['latencyGate']=entry['medianRatios']['decodeMs']<=0.95 and entry['medianRatios']['totalMs']<=1.03
 out['cases'].append(entry)
for case in range(2):
 b=rows[f'decode6/control{case}/plain'];c=rows[f'decode6/control{case}/candidate']
 assert all(b[k]==c[k] for k in ['tokenIds','logitTrace','text','stopReason'])
 if case==1:assert b['promptTokens']%128==1
out['boundedConfirmationGate']=rounds>=3 and all(c['latencyGate'] for c in out['cases'])
out['productAdmission']=False # Broader lifecycle, integration and release checks are separate.
a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
for c in out['cases']:print(c['case'],c['medianRatios'],'latency gate:',c['latencyGate'])
print('Bounded confirmation gate:',out['boundedConfirmationGate'])
