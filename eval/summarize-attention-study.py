"""Validate and summarize device-only attention experiments; no numerical kernel execution."""
import argparse,hashlib,json,statistics
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--curve',type=Path,required=True)
p.add_argument('--trace',type=Path)
p.add_argument('--baseline',type=Path)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists():raise SystemExit('Choose a new output path; preserve earlier evidence')
def read(path,phase):
 raw=(path/'arm-checks.json').read_bytes(); report=json.loads(raw)
 run=json.loads((path/'run.json').read_text(encoding='utf-8-sig'))
 assert report['passed'] and report['phase']==phase and report['runId']==run['runId']
 assert all(x['passed'] for x in report['checks'])
 return report,run,hashlib.sha256(raw).hexdigest()
r,run,sha=read(a.curve,'attention-curve')
out={'schemaVersion':1,'curveRun':r['runId'],'curveReportSha256':sha,'curveReceipt':run['buildReceipt'],'executionPassed':True,'curves':[],
     'scope':'Bounded warm-page USB-powered synthetic Pixel study. Forward-only medians exclude sampling/emission/proposal. Not a user-visible or deployable drafter speed claim.',
     'productSpeculationDepth':0,'productAdoption':False}
normal={c['prefixTokens']:c for c in r['curves'] if c['attentionMode']==0}
assert set(normal)=={253,509,765,1066,1666}
candidate_vectors=0
for c in r['curves']:
 mode=c['attentionMode'];prefix=c['prefixTokens'];assert c['positions']==16 and len(c['observations'])==8
 assert c['referenceHashes']==normal[prefix]['referenceHashes']
 assert c['originalAttention']['calls']==36*16 and c['originalAttention']['threadsMin']==c['originalAttention']['threadsMax']==4
 for cond in (c['conditionsBefore'],c['conditionsAfter']):assert cond['thermalStatus']<3 and cond['interactive'] and cond['benchmarkHasFocus']
 groups={w:[o for o in c['observations'] if o['batchRows']==w] for w in (1,2,4,8)}
 baseline=statistics.median(o['evaluateUs'] for o in groups[1])
 entry={'prefixTokens':prefix,'attentionMode':mode,'modeReferenceEqualsOriginal':c['modeReferenceHashes']==c['referenceHashes'],'originalAttention':c['originalAttention'],'batches':[]}
 for width,rows in groups.items():
  assert len(rows)==2 and {row['round'] for row in rows}=={0,1}
  for row in rows:
   assert len(row['logitHashes'])==16 and len(row['batchMicros'])==16//width and sum(row['batchMicros'])==row['evaluateUs']
   assert sum(x==y for x,y in zip(row['logitHashes'],c['referenceHashes']))==row['bitIdenticalPositions']
   assert sum(x==y for x,y in zip(row['logitHashes'],c['modeReferenceHashes']))==row['modeBitIdenticalPositions']
   stats=row['attention'];assert stats['calls']==36*16//width
   assert not any(stats[key] for key in ('shapeRejections','maskRejections','scratchRejections'))
   if mode==2:
    assert row['bitIdenticalPositions']==16 and row['maxAbsoluteLogitDifference']==0
    if width>1:assert stats['slicedCalls']==stats['calls'] and stats['sliceRows']==36*16
    candidate_vectors+=16
   if mode==1:assert row['modeBitIdenticalPositions']==16 and stats['forcedReferenceCalls']==stats['calls']
   if mode==0 and width==1:assert row['bitIdenticalPositions']==16
  median=statistics.median(row['evaluateUs'] for row in rows)
  entry['batches'].append({'rows':width,'evaluateUs':[row['evaluateUs'] for row in rows],'medianEvaluateUs':median,'forwardOnlySerialOverBatchRatio':baseline/median,
                         'identicalToOriginal':[row['bitIdenticalPositions'] for row in rows],'identicalToModeReference':[row['modeBitIdenticalPositions'] for row in rows],
                         'firstDifferentPosition':[row['firstDifferentPosition'] for row in rows],'attention':rows[0]['attention']})
 out['curves'].append(entry)
out['candidateExactLogitVectors']=candidate_vectors
assert candidate_vectors==640
if a.trace:
 t,tr,ts=read(a.trace,'attention-trace');out.update(traceRun=t['runId'],traceReportSha256=ts,traceReceipt=tr['buildReceipt'],sampledComparisons=t['speculativeComparisons'])
 assert t['speculationParityGatePassed'] and t['shortEosFullParity']
 assert all(c['fullTraceParity'] and c['textParity'] for c in t['speculativeComparisons'])
 rows={row['id']:row for row in t['answers']};base=rows['spec/plain']
 out['generation']=[]
 for row in t['answers']:
  samples=row['diagnostics']['sampleTrace'];rounds=row['diagnostics']['rounds'];stats=row['attention']
  assert [x['outputIndex'] for x in samples]==list(range(len(samples)))
  assert [x['tokenId'] for x in samples if x['emitted']]==row['tokenIds']
  assert [x['logitsHash'] for x in samples]==row['logitTrace']
  assert sum(x['committed'] for x in rounds)==row['tokens']
  assert 1+sum(x['samples'] for x in rounds)==len(samples)
  assert not any(stats[key] for key in ('shapeRejections','maskRejections','scratchRejections'))
  if row['verifyPasses']:assert stats['slicedCalls']==stats['calls']==36*row['verifyPasses']
  if row['id'] in ('spec/request-lookup','spec/wrong-proposals','spec/cancel','spec/recovery','spec/exact-cache'):
   assert row['tokenIds']==base['tokenIds'][:row['tokens']]
   assert row['logitTrace']==base['logitTrace'][:len(row['logitTrace'])]
  for rr in rounds:
   assert rr['controllerUs']==rr['totalUs']-rr['traceUs']
   assert sum(rr[k] for k in ('proposalUs','evaluateUs','sampleUs','rollbackUs','emitUs','traceUs'))<=rr['totalUs']
  out['generation'].append({k:row[k] for k in ('id','oracle','tokens','reason','totalMs','firstTokenMs','prefillMs','decodeMs','drafted','accepted','verifyPasses','rejectedWindows','disabledByCost','attention')})
 assert rows['edges/adaptive-wrong']['tokenIds']==rows['edges/controller-reference']['tokenIds']
 assert rows['edges/adaptive-wrong']['logitTrace']==rows['edges/controller-reference']['logitTrace']
 out['sampledParityPassed']=True;out['adaptiveCostCheck']=t['adaptiveCostCheck']
 if a.baseline:
  old=json.loads((a.baseline/'arm-checks.json').read_bytes());oldbase=next(x for x in old['answers'] if x['id']=='spec/plain')
  assert all(base[k]==oldbase[k] for k in ('system','user','tokenIds','logitTrace','text','reason'))
  out['unchangedNormalBaselineRun']=old['runId'];out['unchangedNormalBaselinePassed']=True
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
print('Candidate exact logit vectors:',candidate_vectors)
for c in out['curves']:
 if c['attentionMode']==2:print(c['prefixTokens'],[(b['rows'],round(b['medianEvaluateUs']/1000,2),round(b['forwardOnlySerialOverBatchRatio'],2)) for b in c['batches']])
if a.trace:print('Sampled parity and unchanged normal baseline: passed')
