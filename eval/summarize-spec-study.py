"""Summarize completed Pixel speculation diagnostics; never treat forward rates as app speed."""
import argparse,hashlib,json,statistics
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--trace',type=Path,required=True);p.add_argument('--curve',type=Path,required=True);p.add_argument('--edges',type=Path);p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists():raise SystemExit('Choose a new output path')
def read(directory,phase):
 raw=(directory/'arm-checks.json').read_bytes();r=json.loads(raw);i=json.loads((directory/'run.json').read_text(encoding='utf-8-sig'))
 assert r['passed'] and r['phase']==phase and i['runId']==r['runId']
 return r,i,hashlib.sha256(raw).hexdigest()
trace,ti,th=read(a.trace,'spec-trace');curve,ci,ch=read(a.curve,'spec-curve')
assert ti['buildReceipt']['appSha256']==ci['buildReceipt']['appSha256']
out={'schemaVersion':1,'appSha256':ti['buildReceipt']['appSha256'],'traceRun':trace['runId'],'curveRun':curve['runId'],'traceReportSha256':th,'curveReportSha256':ch,
     'executionPassed':True,'speculationParityGatePassed':trace['speculationParityGatePassed'],'comparisons':trace['speculativeComparisons'],
     'scope':'Bounded synthetic diagnostic. Forward costs exclude proposal/sampling/emission and do not establish deployable speculation speed. Numerical gates remain separate from execution success.','curves':[],'generation':[]}
for row in trace['answers']:
 if row.get('warmup'):continue
 samples=row['diagnostics']['sampleTrace'];rounds=row['diagnostics']['rounds'];tokens=row['tokenIds']
 assert len(samples)==len(row['logitTrace'])
 assert [x['outputIndex'] for x in samples]==list(range(len(samples)))
 assert [x['tokenId'] for x in samples if x['emitted']]==tokens
 assert all(x['logitsHash']==row['logitTrace'][i] for i,x in enumerate(samples))
 assert sum(x['committed'] for x in rounds)==len(tokens)
 assert 1+sum(x['samples'] for x in rounds)==len(samples)
 assert sum(x['kind']=='verify' for x in rounds)==row['verifyPasses']
 costs={k:sum(x[k] for x in rounds) for k in ['totalUs','controllerUs','proposalUs','evaluateUs','sampleUs','rollbackUs','emitUs','traceUs']}
 for rr in rounds:
  assert rr['controllerUs']==rr['totalUs']-rr['traceUs']
  assert sum(rr[k] for k in ['proposalUs','evaluateUs','sampleUs','rollbackUs','emitUs','traceUs'])<=rr['totalUs']
 out['generation'].append({'id':row['id'],'tokens':row['tokens'],'reason':row['reason'],'oracle':row['oracle'],'totalMs':row['totalMs'],'decodeMs':row['decodeMs'],'proposed':row['drafted'],'accepted':row['accepted'],'sampleEvents':len(samples),'unemittedSamples':sum(not x['emitted'] for x in samples),'costsUs':costs})
for c in curve['curves']:
 assert c['positions']==16 and len(c['observations'])==8
 entry={'prefixTokens':c['prefixTokens'],'decodeThreads':c['decodeThreads'],'verifyThreads':c['verifyThreads'],'conditionsBefore':c['conditionsBefore'],'conditionsAfter':c['conditionsAfter'],'batches':[]}
 for cond in (c['conditionsBefore'],c['conditionsAfter']):assert cond['thermalStatus']<3 and cond['interactive'] and cond['benchmarkHasFocus']
 groups={b:[r for r in c['observations'] if r['batchRows']==b] for b in (1,2,4,8)}
 baseline=statistics.median(r['evaluateUs'] for r in groups[1])
 for b,rows in groups.items():
  assert len(rows)==2 and {r['round'] for r in rows}=={0,1}
  median=statistics.median(r['evaluateUs'] for r in rows)
  for row in rows:
   assert sum(row['batchMicros'])==row['evaluateUs']
   assert len(row['logitHashes'])==16
   assert sum(x==y for x,y in zip(row['logitHashes'],c['referenceHashes']))==row['bitIdenticalPositions']
  if b==1:assert all(r['bitIdenticalPositions']==16 for r in rows)
  entry['batches'].append({'rows':b,'evaluateUsObservations':[r['evaluateUs'] for r in rows],'medianEvaluateUs':median,'forwardOnlySerialOverBatchRatio':baseline/median,'bitIdenticalPositions':[r['bitIdenticalPositions'] for r in rows],'top1SamePositions':[r['sameTop1Positions'] for r in rows],'maxAbsoluteLogitDifference':max(r['maxAbsoluteLogitDifference'] for r in rows)})
 out['curves'].append(entry)
if a.edges:
 edges,ei,eh=read(a.edges,'spec-edges');assert ei['buildReceipt']['appSha256']==out['appSha256']
 out['edgesRun']=edges['runId'];out['edgesReportSha256']=eh;out['shortEosFullParity']=edges['shortEosFullParity'];out['adaptiveCostCheck']=edges['adaptiveCostCheck']
out['productAdmissionAllowed']=out['speculationParityGatePassed'] and all(all(x['bitIdenticalPositions']==[16,16] for x in g['batches']) for g in out['curves'])
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
print('Numerical adoption gate:',out['productAdmissionAllowed'])
for c in out['curves']:print(c['prefixTokens'],c['verifyThreads'],[(b['rows'],round(b['forwardOnlySerialOverBatchRatio'],2),b['bitIdenticalPositions']) for b in c['batches']])
