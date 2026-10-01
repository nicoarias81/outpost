"""Attach this session's manual review decisions to captured outputs; not an automatic grader."""
from pathlib import Path
from collections import Counter
import hashlib
import json

root=Path('E:/projects/outpost')
run=root/'evidence/runs/candidate-pilot-20261001T100306Z-07d419d8'
raw=(run/'candidate-checks.json').read_bytes()
report=json.loads(raw)
assert report['passed'] and report['target']=='Pixel10Pro'
decisions={
    ('d01-travel-arrival','spark17'):('partial','States the late-arrival arrangement and absent code, but contradicts it with the sentence saying no specific arrangement is needed.'),
    ('d01-travel-arrival','bonsai4'):('partial','Correct late-arrival arrangement and absent code, but the answer ends mid-response at the 120-second deadline.'),
    ('d02-manual-revision','spark17'):('pass','Correct Z-42, source1, required isolation check and qualified technician; does not claim isolation already occurred.'),
    ('d02-manual-revision','bonsai4'):('fail','Incorrectly excludes firmware3.2 from the documented3.0–3.4 range and concludes no applicable filter is documented; also deadline-truncated.'),
    ('d03-issue-list','spark17'):('partial','Lists closed I-14 under open items and only then excludes it in a note; the requested filtered list is confusing.'),
    ('d03-issue-list','bonsai4'):('fail','Incorrectly assigns I-13 to Noor although the record assigns it to Luis.'),
    ('d04-farm-units','spark17'):('pass','Correct54kg calculation and units; preserves provisional rate and missing soil/local-suitability conditions.'),
    ('d04-farm-units','bonsai4'):('pass','Correct54kg calculation and units; preserves provisional rate and missing soil/local-suitability conditions.'),
    ('d05-hiking-freshness','spark17'):('pass','Does not infer current water availability or quality from the old observation; identifies missing current evidence.'),
    ('d05-hiking-freshness','bonsai4'):('pass','Does not infer current water availability or quality from the old observation; identifies missing current evidence.'),
    ('d06-driving-gap','spark17'):('pass','Asks for road/context without inventing a station, distance or citation. Look-it-up wording should specify prepared offline evidence.'),
    ('d06-driving-gap','bonsai4'):('pass','Asks for road/location without inventing a station, distance or citation; current opening status still needs evidence.'),
    ('d07-followup-correction','spark17'):('pass','Uses corrected River House booking, side entrance after20:00, no code, and the matching current source1.'),
    ('d07-followup-correction','bonsai4'):('partial','Correct River House rule but wrong citation2, which points to Cedar Lodge in the actual supplied source slots.'),
    ('d08-general-preparation','spark17'):('fail','Misleadingly explains independence from mobile data through locally stored GPS data. A later satellite reference does not resolve the incorrect causal explanation or clearly explain prepared offline maps.'),
    ('d08-general-preparation','bonsai4'):('fail','Conflates missing satellite signals with missing detailed map data, implies network/Wi-Fi data are required in GPS-only mode, and invents citation1 with no sources.'),
}
policy=json.loads((root/'evidence/research/spark-admission-20261001/pilot-review.json').read_text())['policy']
policy['reviewer']['name']='Codex assistant, physical Pixel implementation and evaluation session'
policy['scope']='Manual review of8 known synthetic development questions replayed on this Pixel; not independent, blinded or field validation.'
rows=[r for r in report['answers'] if r.get('fixtureId')]
assert len(rows)==32
reviews=[]
for row in rows:
    key=(row['fixtureId'],row['model'])
    verdict,note=decisions[key]
    if key==('d01-travel-arrival','bonsai4') and row['arm']=='retrieval':
        note='Correct late lockbox arrangement; the deadline interrupts even the sentence about the absent code, leaving the response incomplete.'
    fixed=next(r for r in rows if (r['fixtureId'],r['model'],r['arm'])==(*key,'fixed'))
    identical=row['text']==fixed['text']
    if not identical:
        assert key[1]=='bonsai4' and key[0] in ('d01-travel-arrival','d02-manual-revision')
        assert row['stopReason']==fixed['stopReason']==3
        assert row['text'].startswith(fixed['text']) or fixed['text'].startswith(row['text'])
        print('Reviewed deadline variant:',row['id'],row['text'])
    reviews.append({'fixtureId':key[0],'arm':row['arm'],'model':key[1],'verdict':verdict,'note':note,'stopReason':row['stopReason'],'matchesFixedText':identical,'textSha256':hashlib.sha256(row['text'].encode()).hexdigest(),'promptSha256':hashlib.sha256(row['user'].encode()).hexdigest(),'selectedSourceTitles':[s['title'] for s in row['selectedSources']]})
counts={m:dict(Counter(r['verdict'] for r in reviews if r['model']==m and r['arm']=='fixed')) for m in ('spark17','bonsai4')}
result={'policy':policy,'runId':report['runId'],'reportSha256':hashlib.sha256(raw).hexdigest(),'reviewDate':'2026-10-01','scope':'32 output records cover8 unique questions, two models and two evidence arms. Duplicate prompts/answers are not independent quality samples. Labels describe the actual phone outputs; execution PASS is not quality.','uniqueFixedCaseCounts':counts,'generalKnowledgeReferences':[{'url':'https://spaceplace.nasa.gov/gps/en/','claim':'The receiver uses satellite signals to calculate position.','checked':'2026-10-01'},{'url':'https://support.google.com/maps/answer/6291838?hl=en','claim':'Downloaded map areas can be used offline.','checked':'2026-10-01'}],'referenceScope':'Primary pages checked by the evaluator separately; not supplied to the offline model or proposed as Google Maps ingestion.','reviews':reviews}
out=root/'evidence/research/pixel10-20261001/pilot-review.json'
assert not out.exists()
out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(counts,indent=2))
