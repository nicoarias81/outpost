"""Summarize completed paired ARM confirmation without grading answer quality."""
import argparse,hashlib,json,statistics
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('run',type=Path)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
if args.output.exists():raise SystemExit('Choose a new output path; preserve prior summaries')
raw=(args.run/'arm-checks.json').read_bytes();report=json.loads(raw)
identity=json.loads((args.run/'run.json').read_text(encoding='utf-8-sig'))
assert report['passed'] and report['phase']=='confirm' and report['rounds']==3
assert identity['runId']==report['runId']
rows=[r for r in report['answers'] if not r.get('warmup') and r['id'].startswith('case')]
assert len(rows)==12
cases=[]
for case in ('case0','case1'):
    selected=[r for r in rows if r['id'].split('/')[0]==case]
    assert len(selected)==6
    first=selected[0]
    for row in selected:
        assert row['status']=='returned'
        assert (row['system'],row['user'],row['maxTokens'],row['deadlineMs'],row['sampler'])==(first['system'],first['user'],first['maxTokens'],first['deadlineMs'],first['sampler'])
        assert (row['tokenIds'],row['text'],row['firstLogitsHash'],row['stopReason'])==(first['tokenIds'],first['text'],first['firstLogitsHash'],first['stopReason'])
        assert row['logitTrace'] and row['logitTrace']==first['logitTrace']
        assert row['conditionsBefore']['interactive'] and row['conditionsBefore']['benchmarkHasFocus'] and row['conditionsBefore']['benchmarkResumed']
        assert row['conditionsAfter']['interactive'] and row['conditionsAfter']['benchmarkHasFocus'] and row['conditionsAfter']['benchmarkResumed']
        assert row['threadpool']['affinityRestored'] and row['threadpool']['pausedAfterRequest']
    entry={'case':case,'question':first['user'],'text':first['text'],'tokens':first['tokens'],'stopReason':first['stopReason'],'fullOutputEquivalent':True,'normalEos':first['stopReason']==0,'arms':{}}
    for fast in (False,True):
        group=[r for r in selected if r['fast']==fast];assert len(group)==3
        name='optimized' if fast else 'legacy'
        entry['arms'][name]={
            'configuration':{k:group[0][k] for k in ('threads','promptThreads','width','decodeRows','persistentThreads','affinityMask','batch')},
            'totalMs':[r['totalMs'] for r in group],
            'firstTokenMs':[r['firstTokenMs'] for r in group],
            'medianTotalMs':statistics.median(r['totalMs'] for r in group),
            'medianFirstTokenMs':statistics.median(r['firstTokenMs'] for r in group),
            'medianPrefillMs':statistics.median(r['prefillMs'] for r in group),
            'medianDecodeMs':statistics.median(r['decodeMs'] for r in group),
            'wouldExceedProductDeadline':[r['totalMs']>120000 for r in group],
            'sampledBatteryTemperatureRangeC':[min(r[b]['batteryTenthsC']/10 for r in group for b in ('conditionsBefore','conditionsAfter')),max(r[b]['batteryTenthsC']/10 for r in group for b in ('conditionsBefore','conditionsAfter'))],
            'sampledThermalStatuses':sorted({r[b]['thermalStatus'] for r in group for b in ('conditionsBefore','conditionsAfter')}),
        }
    before,after=entry['arms']['legacy'],entry['arms']['optimized']
    entry['nativeTotalMedianRatio']=before['medianTotalMs']/after['medianTotalMs']
    entry['firstTokenMedianRatio']=before['medianFirstTokenMs']/after['medianFirstTokenMs']
    cases.append(entry)
result={'runId':report['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),'identity':identity,'cases':cases,'scope':'Three rotated pairs per known synthetic question. Same exact generated sequence and initial logits.300-second research deadline on both arms avoids censoring the legacy path; product deadline remains120 seconds. EOS/cap are explicit. No energy, broad-model accuracy or universal speedup claim.'}
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps([{k:c[k] for k in ('case','tokens','normalEos','nativeTotalMedianRatio','firstTokenMedianRatio','arms')} for c in cases],indent=2))
