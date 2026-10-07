"""Build-only on host; SQLite capability and retrieval probes run on Outpost35."""
from pathlib import Path
import datetime,hashlib,json,subprocess,time,urllib.request,uuid,zipfile
R=Path(__file__).resolve().parents[1];cfg=json.loads((R/'.local/developer-settings.json').read_text(encoding='utf-8-sig'))
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
run='pack-options-'+stamp+'-'+uuid.uuid4().hex[:8];work=R/'.local'/run;work.mkdir()
out=R/'evidence/research/pack-options-20261007'/run;out.mkdir(parents=True)
adb=Path(cfg['sdk'])/'platform-tools/adb.exe';serial='emulator-5582'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def call(*args,timeout=120,allow_error=False):
 p=subprocess.run([str(adb),'-s',serial,*map(str,args)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
 if p.returncode and not allow_error:raise RuntimeError(p.stderr[-600:]+p.stdout[-600:])
 return {'exitCode':p.returncode,'stdout':p.stdout.strip(),'stderr':p.stderr.strip()}
for prop,expected in [('ro.kernel.qemu','1'),('ro.boot.qemu.avd_name','Outpost35'),('ro.product.cpu.abi','x86_64'),('sys.boot_completed','1')]:assert call('shell','getprop',prop)['stdout']==expected,(prop,expected)
platform={}
for name,sql in [('version','select sqlite_version();'),('fts4','CREATE VIRTUAL TABLE probe USING fts4(body);'),('fts5','CREATE VIRTUAL TABLE probe USING fts5(body);')]:
 platform[name]=call('shell',f"sqlite3 :memory: '{sql}'",allow_error=True)
source='https://www.sqlite.org/2024/sqlite-amalgamation-3460100.zip';archive=work/'sqlite-amalgamation-3460100.zip'
with urllib.request.urlopen(source,timeout=30) as response:archive.write_bytes(response.read())
assert sha(archive)=='77823cb110929c2bcb0f5d48e4833b5c59a8a6e40cdea3936b99e199dbbe5784', 'SQLite research source hash mismatch'
src=work/'sqlite-source';src.mkdir()
with zipfile.ZipFile(archive) as z:
 for name in ['sqlite3.c','sqlite3.h','sqlite3ext.h','shell.c']:
  member='sqlite-amalgamation-3460100/'+name;data=z.read(member);assert len(data)<15*1024*1024
  (src/name).write_bytes(data)
ndk=R/'.local/native/ndk/android-ndk-r28b';compiler=ndk/'toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe';binary=work/'outpost-sqlite-fts5'
command=[str(compiler),'--target=x86_64-linux-android28','-O2','-DNDEBUG','-DSQLITE_ENABLE_FTS4','-DSQLITE_ENABLE_FTS5','-DSQLITE_THREADSAFE=1',str(src/'sqlite3.c'),str(src/'shell.c'),'-ldl','-lm','-o',str(binary)]
p=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
(out/'compile.log').write_text(p.stdout+p.stderr,encoding='utf-8');assert p.returncode==0,p.stderr[-1400:]
remote='/data/local/tmp/'+run
call('shell','mkdir',remote);call('push',binary,remote+'/sqlite');call('shell','chmod','700',remote+'/sqlite')
version=call('shell',remote+'/sqlite','--version')
results=[]
for engine in ['fts4','fts5']:
 sql=f'''PRAGMA journal_mode=DELETE;
CREATE TABLE records(id INTEGER PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL);
CREATE VIRTUAL TABLE search USING {engine}(title,body,content=records);
BEGIN;
WITH RECURSIVE seq(n) AS (VALUES(1) UNION ALL SELECT n+1 FROM seq WHERE n<10000)
INSERT INTO records SELECT n,
 CASE WHEN n=10000 THEN 'PX-80 revision C' ELSE 'Pump routine '||n END,
 CASE WHEN n=10000 THEN 'pump serial 5274 filter F-12 connector J4 applies to serial numbers 5000 through 5999'
 ELSE 'pump inspected routine inspection completed' END FROM seq;
INSERT INTO search(rowid,title,body) SELECT id,title,body FROM records;
COMMIT;
'''
 fixture=work/(engine+'.sql');fixture.write_bytes(sql.encode('utf-8'));call('push',fixture,remote+'/'+engine+'.sql')
 db=remote+'/'+engine+'.db';built=call('shell',f'{remote}/sqlite {db} < {remote}/{engine}.sql',timeout=120)
 query="pump* OR filter* OR connector* OR serial* OR 5274*"
 if engine=='fts4':
  statement=f"SELECT count(*) AS candidates,max(rowid=10000) AS target_present FROM (SELECT rowid FROM search WHERE search MATCH '{query}' LIMIT 500);"
 else:
  statement=f"SELECT rowid,title,bm25(search) AS score FROM search WHERE search MATCH '{query}' ORDER BY rank LIMIT 5;"
 q=work/(engine+'-query.sql');q.write_bytes(statement.encode('utf-8'));call('push',q,remote+'/'+q.name)
 measured=[];answer=None
 for iteration in range(5):
  start=time.perf_counter();r=call('shell',f'{remote}/sqlite -json {db} < {remote}/{q.name}');elapsed=(time.perf_counter()-start)*1000
  answer=json.loads(r['stdout']);measured.append(round(elapsed,3))
 (out/(engine+'-query.json')).write_text(json.dumps(answer,indent=2)+'\n')
 size=int(call('shell',f'stat -c %s {db}')['stdout'])
 results.append({'engine':engine,'rows':10000,'databaseBytes':size,'result':answer,'hostObservedRoundTripMs':measured,'measurementScope':'CLI process start, SQLite query, JSON serialization and ADB round trip. No isolated search latency or APK/JNI measurement.'})
assert results[0]['result'][0]['target_present']==0
assert results[1]['result'][0]['rowid']==10000
report={'runId':run,'checkedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'target':serial,'device':'Outpost35 Android 15 x86_64','platformSqlite':platform,'bundledSqliteVersion':version['stdout'],'source':{'url':source,'sha256':sha(archive)},'binarySha256':sha(binary),'compileCommand':command,'fixtureScope':'One constructed candidate-truncation witness. Generic pump matches precede the only record with the required serial, filter and connector. Not an unseen corpus or answer-quality benchmark.','results':results,'conclusion':'The candidate cap omits the matching record before any Java ranking. Native FTS5/BM25 returns that record first on the same corpus. Android integration and real-corpus memory/latency tests remain necessary.'}
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
(R/'.local/pack-options-active.json').write_text(json.dumps({'runId':run,'work':str(work),'out':str(out),'remote':remote,'status':'complete'},indent=2)+'\n')
print(json.dumps({'runId':run,'out':str(out),'platformFts5':platform['fts5'],'results':results}))
