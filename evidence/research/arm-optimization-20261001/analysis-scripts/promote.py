"""Stage the production policy only after a successful complete confirmation."""
from pathlib import Path
import json,sys
root=Path('E:/projects/outpost');here=Path(__file__).resolve().parent;files=here/'files'
run=root/'evidence/runs'/sys.argv[1]
report=json.loads((run/'arm-checks.json').read_text(encoding='utf-8-sig'))
assert report['passed'] and report['phase']=='confirm' and report['rounds']==3
rows=[r for r in report['answers'] if not r.get('warmup') and r['id'].startswith('case')]
assert len(rows)==12 and all(r['status']=='returned' and r['stopReason'] in (0,1) for r in rows)
assert all(r['threads']==4 and r['promptThreads']==6 and r['width']==8 and r['decodeRows']==4 and r['persistentThreads'] and r['affinityMask']==0 for r in rows if r['fast'])
def replace(name,old,new):
    path=files/name;text=path.read_text(encoding='utf-8');assert text.count(old)==1,(name,old);path.write_text(text.replace(old,new),encoding='utf-8')
replace('app/src/main/cpp/q2_kernel.c','#if defined(__aarch64__)\nstatic _Atomic int automatic_mode = 0; // Research gate; enable only after Android parity and model confirmation.\n#else\nstatic _Atomic int automatic_mode = 1;\n#endif','static _Atomic int automatic_mode = 1;')
replace('app/src/main/java/dev/outpost/app/NativeEngine.java','rowTile,decodeRows,false,0);}','rowTile,decodeRows,android.os.Build.SUPPORTED_ABIS[0].equals("arm64-v8a"),0);}')
replace('app/build.gradle',"versionCode 16\n        versionName '0.14.0'","versionCode 17\n        versionName '0.15.0'")
replace('app/src/main/java/dev/outpost/app/RuntimeSettings.java','        String value=Build.FINGERPRINT', '        String kernel=cpu.getString("abi").equals("arm64-v8a")?"q2-arm-dot-pool-v1":"q2-row-v3-phase";\n        String value=Build.FINGERPRINT')
replace('app/src/main/java/dev/outpost/app/RuntimeSettings.java','+"/q2-row-v3-phase/"+spec.sha256();','+"/"+kernel+"/"+spec.sha256();')
replace('app/src/main/java/dev/outpost/app/RuntimeSettings.java','        int threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));','''        // Confirmed Pixel/Bonsai preset; other hardware, OS builds and models remain conservative.
        if(spec==ModelStore.BONSAI4&&Build.FINGERPRINT.equals("google/blazer/blazer:17/CP3A.260905.009/16091614:user/release-keys"))try {
            org.json.JSONObject cpu=new org.json.JSONObject(NativeEngine.kernelProfile());
            if(cpu.getString("abi").equals("arm64-v8a")&&cpu.getInt("onlineCpus")==8&&cpu.getInt("featureMask")==7168
                &&cpu.getString("selected").equals("Q2_0 NEON DotProd"))return new Profile(4,6,128,8,true,1,4);
        }catch(org.json.JSONException ignored){}
        int threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));''')
print('Production changes staged after confirmation; build/install/regression still required.')
