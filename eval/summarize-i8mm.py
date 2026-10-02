"""Validate recorded on-device I8MM evidence; do not execute model/kernel work."""
from pathlib import Path
import argparse,json,statistics,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():raise SystemExit('Preserve prior summaries')
raw=(a.run/'arm-checks.json').read_bytes();r=json.loads(raw);meta=json.loads((a.run/'run.json').read_text(encoding='utf-8-sig'))
assert r['passed'] and r['runId']==meta['runId'] and all(c['passed'] for c in r['checks'])
out={'schemaVersion':1,'runId':r['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),'buildReceipt':meta['buildReceipt'],'checksPassed':r['checksPassed'],'productAdmission':False}
if r['phase']=='i8mm-numeric':
 proof=r['i8mm'];assert proof['supported'] and proof['primitiveFailures']==0 and proof['failures']==0
 out.update(primitiveComparisons=proof['primitiveComparisons'],exactValueComparisons=proof['comparisons'],matrices=[])
 keys=sorted({(b['k'],b['rows'],b['columns'],b['width']) for b in proof['benchmarks']})
 for key in keys:
  rows=[b for b in proof['benchmarks'] if (b['k'],b['rows'],b['columns'],b['width'])==key]
  assert len(rows)==6 and all(b['parity'] and b['dispatch'] for b in rows)
  times={mode:[b['ns'] for b in rows if b['i8mm']==mode] for mode in [False,True]}
  base=statistics.median(times[False]);candidate=statistics.median(times[True])
  out['matrices'].append({'k':key[0],'rows':key[1],'columns':key[2],'width':key[3],'dotNs':times[False],'candidateNs':times[True],'medianDotNs':base,'medianCandidateNs':candidate,'dotOverCandidate':base/candidate})
 out['scope']='Matrix graph timing includes conversion/packing and is not model speed. One-column rows retain DotProd in both arms.'
else:
 assert r['phase'] in ['i8mm-model','i8mm-confirm'];rounds=3 if r['phase']=='i8mm-confirm' else 1
 byid={x['id']:x for x in r['answers']};out.update(rounds=rounds,exactParity=True,cases=[])
 for case in range(3):
  pairs=[];reference=None
  for round in range(rounds):
   base=byid[f'i8mm/case{case}/round{round}/dot'];candidate=byid[f'i8mm/case{case}/round{round}/candidate']
   for key in ['system','user','tokenIds','logitTrace','text','stopReason','firstLogitsHash','promptTokens','maxTokens','sampler']:assert base[key]==candidate[key],(case,round,key)
   if reference:
    for key in ['tokenIds','logitTrace','text','stopReason']:assert reference[key]==base[key]
   else:reference=base
   for row,wanted in [(base,False),(candidate,True)]:
    assert row['status']=='returned' and row['stopReason']<2 and row['threads']==row['promptThreads']==6 and row['attentionThreads']==4
    assert row['maxTokens']==192 and row['deadlineMs']==120000 and row['prefillChunk']==32 and row['decodeChunk']==0
    assert row['i8mm']['requested']==wanted and (row['i8mm']['matrixNodes']>0)==wanted
    assert row['attention']['slicedCalls']>=36*(row['tokens']-1)
    for cond in [row['conditionsBefore'],row['conditionsAfter']]:assert cond['thermalStatus']<3 and cond['interactive'] and cond['benchmarkHasFocus']
   pairs.append({'round':round,'tokens':base['tokens'],'candidateFirst':candidate['startMonoNs']<base['startMonoNs'],
                 **{key:{'dot':base[key],'candidate':candidate[key],'ratio':candidate[key]/base[key]} for key in ['prefillMs','decodeMs','firstTokenMs','totalMs','processCpuMs']}})
  ratios={key:statistics.median(x[key]['ratio'] for x in pairs) for key in ['prefillMs','decodeMs','firstTokenMs','totalMs','processCpuMs']}
  out['cases'].append({'case':case,'pairs':pairs,'medianRatios':ratios,'latencyGate':ratios['prefillMs']<=0.95 and ratios['totalMs']<=1.03})
 out['boundedConfirmationGate']=rounds>=3 and all(c['latencyGate'] for c in out['cases'])
 out['scope']='Known synthetic complete answers on one USB-powered Pixel; exact same-input logits/tokens required. Counterbalanced timing, not general field-quality or energy evidence.'
a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
if 'cases' in out:
 for c in out['cases']:print(c['case'],c['medianRatios'],'gate',c['latencyGate'])
 print('Bounded confirmation gate:',out['boundedConfirmationGate'])
else:print('Exact value comparisons:',out['exactValueComparisons'])
