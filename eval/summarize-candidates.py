"""Summarize captured Android candidate runs; does not score quality or execute models."""
import argparse,hashlib,json,statistics
from pathlib import Path
from collections import defaultdict,Counter

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('runs',nargs='+',type=Path,help='Completed candidate run directories')
parser.add_argument('--output',required=True,type=Path,help='New summary directory')
args=parser.parse_args()
if args.output.exists():raise SystemExit('Output exists; preserve it and choose a new summary directory')
summaries=[];inspection=[]
for run in args.runs:
    raw=(run/'candidate-checks.json').read_bytes();report=json.loads(raw)
    identity=json.loads((run/'run.json').read_text(encoding='utf-8-sig'))
    if not report.get('passed'):raise ValueError('Execution did not pass: '+str(run))
    if report['runId']!=identity['runId'] or report['fixtureSha256']!=identity['fixtureSha256']:raise ValueError('Run identity mismatch')
    rows=[r for r in report['answers'] if r.get('fixtureId') and not r.get('warmup')]
    expected={'pilot':32,'heldout':48,'timing':12}.get(report['phase'])
    if expected is None or len(rows)!=expected:raise ValueError('Unexpected phase/answer count: '+str(run))
    pairs=defaultdict(dict);groups=defaultdict(list)
    for row in rows:
        if row['status']!='returned':raise ValueError('Incomplete model call')
        key=(row['fixtureId'],row['arm'],row['round'])
        if row['model'] in pairs[key]:raise ValueError('Duplicate pair member')
        pairs[key][row['model']]=row;groups[(row['model'],row['arm'])].append(row)
    for key,pair in pairs.items():
        if set(pair)!={'spark17','bonsai4'}:raise ValueError('Unpaired outcome')
        a,b=pair['spark17'],pair['bonsai4']
        if (a['system'],a['user'],a['maxTokens'],a['seed'])!=(b['system'],b['user'],b['maxTokens'],b['seed']):raise ValueError('Prompt/budget pairing mismatch')
        inspection.append({'runId':report['runId'],'fixture':key[0],'arm':key[1],'round':key[2],'expectedOutcome':a['expectedOutcome'],'prompt':a['user'],'selectedSources':a['selectedSources'],'answers':{m:{k:r.get(k) for k in ['text','stopReason','tokens','totalMs','firstTokenMs','loadMs']} for m,r in pair.items()}})
    stats=[]
    for (model,arm),items in sorted(groups.items()):
        stats.append({'model':model,'arm':arm,'calls':len(items),'stopReasons':dict(Counter(str(r['stopReason']) for r in items)),'medianNativeTotalMs':statistics.median(r['totalMs'] for r in items),'medianFirstTokenMs':statistics.median(r['firstTokenMs'] for r in items),'medianLoadMs':statistics.median(r['loadMs'] for r in items),'medianOutputTokens':statistics.median(r['tokens'] for r in items),'maxSampledPssKiB':max(r['memory']['sampledPeakPssKiB'] for r in items),'maxSamplingGapMs':max(r['memory']['maxObservedGapMs'] for r in items),'memoryScope':'Timing rows follow warmup; pilot/heldout peaks may include previous model during switching. Sampled process maxima, not continuous peaks.'})
    timing=[]
    if report['phase']=='timing':
        for fixture in sorted({r['fixtureId'] for r in rows}):
            entry={'fixture':fixture,'models':{}}
            for model in ['spark17','bonsai4']:
                selected=[r for r in rows if r['fixtureId']==fixture and r['model']==model]
                entry['models'][model]={'normalEosAll':all(r['stopReason']==0 for r in selected),'nativeTotalMs':[r['totalMs'] for r in selected],'medianNativeTotalMs':statistics.median(r['totalMs'] for r in selected),'firstTokenMs':[r['firstTokenMs'] for r in selected],'sampledPeakPssKiB':[r['memory']['sampledPeakPssKiB'] for r in selected],'textsIdenticalWithinModel':len({r['text'] for r in selected})==1}
            if all(x['normalEosAll'] for x in entry['models'].values()):entry['bonsaiOverSparkNativeMedian']=entry['models']['bonsai4']['medianNativeTotalMs']/entry['models']['spark17']['medianNativeTotalMs']
            timing.append(entry)
    summaries.append({'runId':report['runId'],'phase':report['phase'],'reportSha256':hashlib.sha256(raw).hexdigest(),'identity':identity,'pairedTasks':len(pairs),'statistics':stats,'timingPairs':timing})
args.output.mkdir(parents=True)
(args.output/'metrics.json').write_text(json.dumps({'scope':'Descriptive paired emulator metrics. No automated answer-quality verdict, confidence interval, phone claim or field acceptance.','runs':summaries},indent=2)+'\n',encoding='utf-8')
(args.output/'answers-for-review.json').write_text(json.dumps(inspection,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps([{'run':r['runId'],'pairs':r['pairedTasks'],'statistics':r['statistics'],'timing':r['timingPairs']} for r in summaries],indent=2))
