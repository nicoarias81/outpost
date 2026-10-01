from pathlib import Path
import json,statistics,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument('--curve',type=Path,required=True);p.add_argument('--trace',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if a.output.exists():raise SystemExit('Preserve prior summary; use a new path')
def read(path,phase):
 raw=(path/'arm-checks.json').read_bytes();r=json.loads(raw);meta=json.loads((path/'run.json').read_text(encoding='utf-8-sig'))
 assert r['passed'] and r['phase']==phase and meta['runId']==r['runId']
 assert all(c['passed'] for c in r['checks'])
 return r,meta,hashlib.sha256(raw).hexdigest()
r,meta,sha=read(a.curve,'attention-six')
out={'schemaVersion':1,'curveRun':r['runId'],'curveReportSha256':sha,'buildReceipt':meta['buildReceipt'],'curveChecks':r['checksPassed'],'comparisons':[],
     'scope':'Forward-only Pixel research. Same four logical attention workers; four or six physical graph workers. Two width orders per mode, not user-visible speed or energy evidence.','productAdmission':False}
for prefix in [253,509,1666]:
 bymode={c['attentionMode']:c for c in r['curves'] if c['prefixTokens']==prefix};assert set(bymode)=={2,3}
 assert bymode[2]['referenceHashes']==bymode[3]['referenceHashes']
 item={'prefixTokens':prefix,'batches':[]}
 for width in [1,2,4,8]:
  vals={}
  for mode,c in bymode.items():
   rows=[o for o in c['observations'] if o['batchRows']==width];assert len(rows)==2
   for row in rows:
    stats=row['attention'];workers=6 if mode==3 and width>1 else 4
    assert row['bitIdenticalPositions']==16 and row['maxAbsoluteLogitDifference']==0 and row['logitHashes']==c['referenceHashes']
    assert stats['calls']==36*16//width and stats['threadsMin']==stats['threadsMax']==workers
    assert all(stats[k]==0 for k in ['shapeRejections','maskRejections','scratchRejections'])
    assert sum(row['batchMicros'])==row['evaluateUs']
   vals[mode]=[row['evaluateUs'] for row in rows]
  med={mode:statistics.median(v) for mode,v in vals.items()}
  item['batches'].append({'rows':width,'fourWorkersUs':vals[2],'sixWorkersUs':vals[3],'medianFourUs':med[2],'medianSixUs':med[3],'fourOverSix':med[2]/med[3]})
 out['comparisons'].append(item)
out['candidateExactVectors']=384
if a.trace:
 t,tm,ts=read(a.trace,'attention-trace-six');assert tm['buildReceipt']['appSha256']==meta['buildReceipt']['appSha256']
 assert t['speculationParityGatePassed'] and t['shortEosFullParity']
 rows={row['id']:row for row in t['answers']};base=rows['spec/plain']
 for row in t['answers']:
  stats=row['attention'];assert all(stats[k]==0 for k in ['shapeRejections','maskRejections','scratchRejections'])
  if row['verifyPasses']:assert stats['calls']==stats['slicedCalls']==36*row['verifyPasses'] and stats['threadsMin']==stats['threadsMax']==6
  if row['id'].startswith('spec/') and not row.get('warmup'):
   assert row['tokenIds']==base['tokenIds'][:row['tokens']]
   assert row['logitTrace']==base['logitTrace'][:len(row['logitTrace'])]
 out.update(traceRun=t['runId'],traceReportSha256=ts,traceChecks=t['checksPassed'],sampledParityPassed=True,adaptiveCostCheck=t['adaptiveCostCheck'])
 out['generation']=[{k:row[k] for k in ['id','tokens','decodeMs','totalMs','accepted','drafted','verifyPasses']} for row in t['answers']]
a.output.write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
for c in out['comparisons']:print(c['prefixTokens'],[(b['rows'],round(b['medianFourUs']/1000,2),round(b['medianSixUs']/1000,2),round(b['fourOverSix'],3)) for b in c['batches']])
