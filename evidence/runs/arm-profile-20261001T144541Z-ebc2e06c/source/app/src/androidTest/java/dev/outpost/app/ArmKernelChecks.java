package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Foreground ARM research. The replacement Activity never opens personal app state. */
final class ArmKernelChecks {
    static final class BenchActivity extends Activity {
        TextView message;
        volatile boolean resumed;
        @Override public void onCreate(Bundle state){super.onCreate(null);setShowWhenLocked(true);setTurnScreenOn(true);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);message=new TextView(this);message.setTextSize(20);message.setPadding(32,96,32,32);message.setText("Outpost performance test\n\nPlease leave this screen open.\nYour conversation and documents are not used.\nThe device lock remains enabled.");setContentView(message);}
        @Override protected void onResume(){super.onResume();resumed=true;}
        @Override protected void onPause(){resumed=false;super.onPause();}
    }
    private final Instrumentation test;private final Bundle args;
    private final JSONObject report=new JSONObject();private final JSONArray checks=new JSONArray(),answers=new JSONArray();
    private File output;private BenchActivity screen;private int promptThreadsOverride;
    ArmKernelChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;}
    private void write()throws Exception{Files.write(output.toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));}
    private void require(boolean ok,String label)throws Exception{checks.put(new JSONObject().put("label",label).put("passed",ok));write();if(!ok)throw new AssertionError(label);}
    private void status(String text){Bundle b=new Bundle();b.putString("stream","\n"+text+"\n");test.sendStatus(0,b);if(screen!=null)test.runOnMainSync(()->screen.message.setText("Outpost performance test\n\n"+text+"\n\nPlease leave this screen open."));}
    private JSONObject conditions()throws Exception{
        var context=test.getTargetContext();Intent battery=context.registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean[] focus={false};if(screen!=null)test.runOnMainSync(()->focus[0]=screen.hasWindowFocus());
        return new JSONObject().put("thermalStatus",context.getSystemService(PowerManager.class).getCurrentThermalStatus()).put("interactive",context.getSystemService(PowerManager.class).isInteractive()).put("keyguard",context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()).put("benchmarkResumed",screen!=null&&screen.resumed).put("benchmarkHasFocus",focus[0]).put("batteryTenthsC",battery==null?-1:battery.getIntExtra("temperature",-1)).put("plugged",battery==null?-1:battery.getIntExtra("plugged",-1));
    }
    void run(){boolean passed=false;Bundle end=new Bundle();
        try{
            String id=args.getString("arm_run",""),phase=args.getString("arm_phase","numeric");if(!id.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run required");
            File dir=new File(test.getTargetContext().getFilesDir(),"evidence/arm/"+id);if(dir.exists()||!dir.mkdirs())throw new IllegalStateException("Run exists");output=new File(dir,"arm-checks.json");
            report.put("runId",id).put("phase",phase).put("passed",false).put("checks",checks).put("answers",answers).put("initialRuntime",new JSONObject(NativeEngine.kernelProfile())).put("scope","Foreground Android ARM experiment. Synthetic prompts only; exact parity and actual dispatch required. USB-powered, no energy claim.");write();
            require(android.os.Build.MANUFACTURER.equals("Google")&&android.os.Build.MODEL.equals("Pixel 10 Pro")&&android.os.Build.SUPPORTED_ABIS[0].equals("arm64-v8a"),"Registered Pixel architecture/model");
            JSONObject initial=conditions();require(initial.getInt("thermalStatus")<PowerManager.THERMAL_STATUS_SEVERE,"Thermal status permits the bounded test");
            screen=(BenchActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            for(int i=0;i<30&&(!conditions().getBoolean("interactive")||!conditions().getBoolean("benchmarkHasFocus"));i++)SystemClock.sleep(100);
            require(conditions().getBoolean("interactive")&&conditions().getBoolean("benchmarkHasFocus")&&screen.resumed,"Dedicated benchmark Activity is visible, focused and resumed");
            report.put("conditionsBefore",conditions()).put("foregroundActivity",true).put("keyguardPolicy","Test Activity may occlude keyguard using standard Activity APIs; it never dismisses authentication or loads personal app state.").put("numericTimingScope","Foreground numeric control; not model speed");write();
            if(phase.equals("numeric")){
                status("Checking 16,000+ vectors, guard pages and reference dispatch...");
                double[] d=NativeEngine.nativeKernelChecks();report.put("vectors",new JSONObject().put("supported",d[0]==1).put("comparisons",d[1]).put("bitMismatches",d[2]).put("dispatch",d[3]==1).put("guard",d[4]==1).put("maxAbsolute",d[5]).put("maxRelative",d[6]).put("referenceNs",d[7]).put("dotProdNs",d[8]));write();
                require(d[0]==1&&d[1]>=16000&&d[2]==0&&d[3]==1&&d[4]==1,"Direct ARM dot preserves all bits and guarded bounds");
                int[] policy=NativeEngine.nativeDispatchChecks();require(policy[0]>=50&&policy[1]==0,"Capability/dispatch policy checks");
                status("Checking matrix strides, tails, workers and real Bonsai shapes...");JSONObject graphs=new JSONObject(NativeEngine.nativeArmGraphChecks());report.put("graphs",graphs);write();
                require(graphs.getBoolean("supported")&&graphs.getInt("comparisons")>10000&&graphs.getInt("failures")==0&&graphs.getJSONArray("benchmarks").length()==189,"Prepared matrix path is bitwise equivalent across all graphs");
            }else{
                ModelStore model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(var in=new FileInputStream(model.file())){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                require(model.file().length()==model.spec().bytes()&&hash.toString().equals(model.spec().sha256()),"Pinned Bonsai bytes verified; file pages are warm");report.put("modelSha256",hash.toString());write();
                try(NativeEngine engine=new NativeEngine()){
                    if(phase.equals("tune"))tune(engine,model);
                    else if(phase.equals("controller"))controller(engine,model);
                    else if(phase.equals("lifecycle"))lifecycle(engine,model);
                    else if(phase.equals("trace"))trace(engine,model);
                    else if(phase.equals("profile"))profile(engine,model);
                    else if(phase.equals("model")||phase.equals("confirm"))paired(engine,model,phase.equals("confirm"));
                    else throw new IllegalArgumentException("Unknown ARM phase");
                }
            }
            report.put("conditionsAfter",conditions()).put("passed",true).put("checksPassed",checks.length());write();passed=true;end.putString("stream","\nPASS ARM "+phase+": "+checks.length()+" controls.\n");
        }catch(Throwable error){try{report.put("error",android.util.Log.getStackTraceString(error));write();}catch(Exception ignored){}end.putString("stream","\nFAIL ARM: "+android.util.Log.getStackTraceString(error));}
        finally{NativeEngine.setKernelAutomatic(false);if(screen!=null){BenchActivity last=screen;test.runOnMainSync(last::finish);}}
        test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,end);
    }
    private NativeEngine.Result call(NativeEngine engine,ModelStore model,String id,String system,String user,boolean fast,int threads,int width,int cap)throws Exception{
        boolean pools=fast&&"true".equals(args.getString("arm_pools","false"));int mask=pools&&"performance".equals(args.getString("arm_affinity","none"))?performanceMask(threads):0;
        return call(engine,model,id,system,user,fast,threads,width,cap,pools,mask);
    }
    private static int performanceMask(int threads){return ((1<<threads)-1)<<(8-threads);}
    private NativeEngine.Result call(NativeEngine engine,ModelStore model,String id,String system,String user,boolean fast,int threads,int width,int cap,boolean pools,int mask)throws Exception{
        JSONObject before=conditions();if(!before.getBoolean("benchmarkHasFocus")||!before.getBoolean("benchmarkResumed")||!before.getBoolean("interactive")||before.getInt("thermalStatus")>=PowerManager.THERMAL_STATUS_SEVERE)throw new IllegalStateException("Foreground/thermal precondition changed");
        NativeEngine.setKernelAutomatic(fast);engine.clearCache();engine.configure(new NativeEngine.Configuration(threads,promptThreadsOverride>0?promptThreadsOverride:threads,128,false,width,0,true,1,Integer.parseInt(args.getString("arm_rows","1")),pools,mask));
        JSONObject row=new JSONObject().put("id",id).put("fast",fast).put("threads",threads).put("promptThreads",promptThreadsOverride>0?promptThreadsOverride:threads).put("width",width).put("batch",128).put("persistentThreads",pools).put("affinityMask",mask).put("decodeRows",Integer.parseInt(args.getString("arm_rows","1"))).put("deadlineMs",engine.deadlineForTests()).put("maxTokens",cap).put("sampler","top-k20/top-p0.8/temp0.7/seed42").put("system",system).put("user",user).put("status","running").put("conditionsBefore",before);answers.put(row);write();status("Starting "+id+" / "+(fast?"DotProd":"reference")+" / "+threads+" threads / width "+width);
        long cpu=android.os.Process.getElapsedCpuTime(),start=SystemClock.elapsedRealtime(),startMonoNs=System.nanoTime();long[] firstCallbackNs={0};NativeEngine.Result r;
        try{r=engine.generateWithSampling(engine.request(),model.file(),system,user,cap,true,(s,n)->{if(firstCallbackNs[0]==0)firstCallbackNs[0]=System.nanoTime();});}catch(Throwable e){row.put("status","error").put("error",e.toString());write();throw e;}
        long endMonoNs=System.nanoTime();row.put("startMonoNs",startMonoNs).put("firstCallbackMonoNs",firstCallbackNs[0]).put("endMonoNs",endMonoNs).put("prepareMs",r.prepareMs());
        row.put("status","returned").put("text",r.text()).put("tokens",r.tokens()).put("promptTokens",r.promptTokens()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("loadMs",r.loadMs()).put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("stopReason",r.reason()).put("firstLogitsHash",Long.toUnsignedString(r.firstLogitsHash())).put("tokenIds",new JSONArray(engine.lastTokens())).put("kernelUsed",NativeEngine.kernelWasUsed()).put("batchUsed",NativeEngine.kernelBatchUsed()).put("rowKernelUsed",NativeEngine.kernelRowsUsed()).put("processCpuMs",android.os.Process.getElapsedCpuTime()-cpu).put("wallMs",SystemClock.elapsedRealtime()-start).put("conditionsAfter",conditions());write();
        JSONArray trace=new JSONArray();for(long h:engine.logitTrace())trace.put(Long.toUnsignedString(h));row.put("logitTrace",trace);
        JSONObject poolState=new JSONObject(engine.threadpoolAudit());row.put("threadpool",poolState);write();require(poolState.getBoolean("affinityRestored")&&poolState.getInt("affinityBefore")==poolState.getInt("affinityAfter")&&poolState.getBoolean("pausedAfterRequest"),"Caller affinity restored and pools paused: "+id);if(mask!=0)require(poolState.getInt("effectiveMask")==mask&&(poolState.getInt("affinityDuring")&~mask)==0,"Requested application-thread affinity applied");require(NativeEngine.kernelWasUsed()==fast,"Requested kernel actually executed: "+id);status("Completed "+id+": "+r.totalMs()+"ms / first "+r.firstTokenMs()+"ms / "+r.tokens()+" tokens / stop "+r.reason());return r;
    }
    private List<ChatPrompt.Prepared> prompts()throws Exception{
        JSONObject fixtures;try(var in=test.getContext().getAssets().open("candidates/missions-v1.json")){fixtures=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        List<ChatPrompt.Prepared> result=new ArrayList<>();JSONArray list=fixtures.getJSONArray("development");
        for(int i=0;i<2;i++){JSONObject c=list.getJSONObject(i);List<Library.Hit> hits=new ArrayList<>();JSONArray docs=c.getJSONArray("documents");
            for(int j=0;j<docs.length();j++){JSONObject d=docs.getJSONObject(j);Library.Document doc=new Library.Document("arm-fixture-"+i+"-"+j,d.getString("title"),"Synthetic","Synthetic","","2026-10-01",d.getString("body"));hits.add(new Library.Hit(doc,doc.body(),j+1,1));}
            result.add(ChatPrompt.prepare(c.getString("question"),List.of(),hits));}
        return result;
    }
    private void paired(NativeEngine engine,ModelStore model,boolean confirm)throws Exception{
        int threads=Integer.parseInt(args.getString("arm_threads","4")),width=Integer.parseInt(args.getString("arm_width","4"));int rounds=confirm?3:1;int promptThreads=Integer.parseInt(args.getString("arm_prompt_threads",Integer.toString(threads)));engine.traceLogitsForTests(true);
        engine.deadlineForTests(300000);report.put("comparisonThreads",threads).put("comparisonWidth",width).put("comparisonPromptThreads",promptThreads).put("referenceThreads",4).put("rounds",rounds).put("researchDeadlineMs",300000).put("productDeadlineMs",120000);write();
        ChatPrompt.Prepared shortPrompt=ChatPrompt.prepare("For this conversation my code name is Cedar. Reply with just the code name.",List.of(),List.of());
        NativeEngine.Result shortReference=call(engine,model,"complete-control/reference",ChatPrompt.SYSTEM,shortPrompt.user(),false,4,width,192);int[] shortTokens=engine.lastTokens();long[] shortTrace=engine.logitTrace();promptThreadsOverride=promptThreads;
        NativeEngine.Result shortFast=call(engine,model,"complete-control/candidate",ChatPrompt.SYSTEM,shortPrompt.user(),true,threads,width,192);
        require(shortReference.reason()==0&&shortFast.reason()==0&&shortReference.text().equals(shortFast.text())&&shortReference.firstLogitsHash()==shortFast.firstLogitsHash()&&Arrays.equals(shortTokens,engine.lastTokens())&&Arrays.equals(shortTrace,engine.logitTrace()),"Complete short chat has identical tokens/text/logits");
        List<ChatPrompt.Prepared> prompts=prompts();
        for(int c=0;c<prompts.size();c++){
            var prompt=prompts.get(c);String expected=null;long hash=0;int[] tokens=null;long[] expectedTrace=null;int expectedStop=-1;
            for(int round=0;round<rounds;round++)for(int order=0;order<2;order++){
                boolean fast=(c+round+order)%2!=0;promptThreadsOverride=fast?promptThreads:4;
                call(engine,model,"case"+c+"/round"+round+"/warmup",ChatPrompt.SYSTEM,prompt.user(),fast,fast?threads:4,width,8);
                answers.getJSONObject(answers.length()-1).put("warmup",true);write();
                NativeEngine.Result r=call(engine,model,"case"+c+"/round"+round,ChatPrompt.SYSTEM,prompt.user(),fast,fast?threads:4,width,192);
                require(r.reason()==0||r.reason()==1,"Request finishes naturally or at unchanged192-token cap within equal300-second research deadline");
                if(expected==null){expected=r.text();hash=r.firstLogitsHash();tokens=engine.lastTokens();expectedTrace=engine.logitTrace();expectedStop=(int)r.reason();}
                else require(expectedStop==r.reason()&&expected.equals(r.text())&&hash==r.firstLogitsHash()&&Arrays.equals(tokens,engine.lastTokens())&&Arrays.equals(expectedTrace,engine.logitTrace()),"Full generated sequence, stop reason and initial logits remain identical");
            }
        }
    }
    private void controller(NativeEngine engine,ModelStore model)throws Exception{
        String user=prompts().get(1).user();String[] names={"legacy","pools-only","kernel-only","kernel-pools","kernel-pools-affinity"};
        boolean[] fast={false,false,true,true,true},pools={false,true,false,true,true};int[] masks={0,0,0,0,240};
        call(engine,model,"controller/warmup",ChatPrompt.SYSTEM,user,false,4,8,8,false,0);answers.getJSONObject(answers.length()-1).put("warmup",true);write();
        String expected=null;long hash=0;int[] tokens=null;
        for(int round=0;round<2;round++)for(int order=0;order<5;order++){
            int index=round==0?order:4-order;String id="controller/round"+round+"/"+names[index];
            NativeEngine.Result r=call(engine,model,id,ChatPrompt.SYSTEM,user,fast[index],4,8,32,pools[index],masks[index]);
            require(r.reason()<2&&r.tokens()>0,"Bounded controller diagnostic returned");
            if(expected==null){expected=r.text();hash=r.firstLogitsHash();tokens=engine.lastTokens();}
            else require(expected.equals(r.text())&&hash==r.firstLogitsHash()&&Arrays.equals(tokens,engine.lastTokens()),"Controller changes preserve all sampled tokens and initial logits");
        }
    }
    private boolean equivalent(NativeEngine.Result r,int[] actual,long hash,int[] expected){
        if(r.reason()>1||r.tokens()==0||r.firstLogitsHash()!=hash||actual.length>expected.length)return false;
        for(int i=0;i<actual.length;i++)if(actual[i]!=expected[i])return false;return true;
    }
    private void profile(NativeEngine engine,ModelStore model)throws Exception{
        // Both arms retain logit tracing; only the sampled arm runs the external profiler.
        // The first Java text callback is an observable boundary, not a GPU/graph timing event.
        engine.traceLogitsForTests(true);promptThreadsOverride=6;
        require("4".equals(args.getString("arm_rows")),"Profiling uses the admitted four-row decoder");
        report.put("profileProtocol",new JSONObject().put("event","cpu-clock:u").put("frequencyHz",100)
            .put("clock","CLOCK_MONOTONIC / System.nanoTime").put("stackCapture",false)
            .put("scope","Own app process; before-first-text and after-first-text CPU samples. These include scheduling/spin and sampling costs, not pure operator wall time or memory bandwidth."));write();
        List<ChatPrompt.Prepared> cases=prompts();
        for(int c=0;c<cases.size();c++){
            String user=cases.get(c).user();
            call(engine,model,"profile/case"+c+"/warmup",ChatPrompt.SYSTEM,user,true,4,8,8,true,0);
            answers.getJSONObject(answers.length()-1).put("warmup",true);write();
            String expected=null;int[] tokens=null;long[] trace=null;long stop=-1;
            for(int order=0;order<2;order++){
                boolean sampled=(c+order)%2!=0;
                AppCpuProfiler profiler=sampled?new AppCpuProfiler(output.getParentFile(),"profile-case"+c):null;
                NativeEngine.Result result;
                try{result=call(engine,model,"profile/case"+c+"/"+(sampled?"sampled":"control"),ChatPrompt.SYSTEM,user,true,4,8,64,true,0);}
                finally{if(profiler!=null)profiler.close();}
                JSONObject row=answers.getJSONObject(answers.length()-1);row.put("cpuSampled",sampled);write();
                require(result.reason()==0||result.reason()==1,"Profile request completes within unchanged product deadline");
                if(expected==null){expected=result.text();tokens=engine.lastTokens();trace=engine.logitTrace();stop=result.reason();}
                else require(expected.equals(result.text())&&Arrays.equals(tokens,engine.lastTokens())&&Arrays.equals(trace,engine.logitTrace())&&stop==result.reason(),"CPU sampling preserves every output token, logit distribution and stop reason");
            }
        }
        promptThreadsOverride=0;
    }
    private void trace(NativeEngine engine,ModelStore model)throws Exception{
        engine.traceLogitsForTests(true);String user=prompts().get(1).user();promptThreadsOverride=4;
        NativeEngine.Result reference=call(engine,model,"trace/reference4-4",ChatPrompt.SYSTEM,user,false,4,8,8,false,0);long[] expected=engine.logitTrace();int[] tokens=engine.lastTokens();
        JSONArray comparisons=new JSONArray();int[][] configs={{6,6},{4,6},{4,4}};
        for(int[] c:configs){promptThreadsOverride=c[1];NativeEngine.Result r=call(engine,model,"trace/candidate"+c[0]+"-"+c[1],ChatPrompt.SYSTEM,user,true,c[0],8,8,true,0);long[] actual=engine.logitTrace();
            int first=-1;for(int i=0;i<Math.min(expected.length,actual.length);i++)if(expected[i]!=actual[i]){first=i;break;}
            boolean same=Arrays.equals(expected,actual)&&Arrays.equals(tokens,engine.lastTokens())&&reference.text().equals(r.text());comparisons.put(new JSONObject().put("threads",c[0]).put("promptThreads",c[1]).put("allLogitsIdentical",same).put("firstDifferentLogitIndex",first));report.put("traceComparison",comparisons);write();
            if(c[0]==4)require(same,"Four decode workers preserve every sampled distribution");
        }
        promptThreadsOverride=0;
    }
    private void lifecycle(NativeEngine engine,ModelStore model)throws Exception{
        NativeEngine.setKernelAutomatic(true);engine.configure(new NativeEngine.Configuration(4,4,128,true,8,0,true,1,4,true,0));
        ChatPrompt.Prepared prompt=ChatPrompt.prepare("For this conversation my code name is Cedar. Reply with just the code name.",List.of(),List.of());
        NativeEngine.Result first=engine.generateWithSampling(engine.request(),model.file(),ChatPrompt.SYSTEM,prompt.user(),48,true,(s,n)->{});int[] expected=engine.lastTokens();
        JSONObject firstPool=new JSONObject(engine.threadpoolAudit());
        NativeEngine.Result repeated=engine.generateWithSampling(engine.request(),model.file(),ChatPrompt.SYSTEM,prompt.user(),48,true,(s,n)->{});
        JSONObject secondPool=new JSONObject(engine.threadpoolAudit());
        require(first.reason()==0&&repeated.reason()==0&&first.firstLogitsHash()==repeated.firstLogitsHash()&&first.text().equals(repeated.text())&&Arrays.equals(expected,engine.lastTokens()),"Persistent workers preserve exact cached answer");
        require(repeated.cachedTokens()==first.promptTokens()&&firstPool.getInt("poolCreations")==secondPool.getInt("poolCreations")&&secondPool.getBoolean("pausedAfterRequest"),"Exact cache reuse retains sleeping workers without creating new pools");
        long request=engine.request();NativeEngine.Result stopped=engine.generateWithSampling(request,model.file(),ChatPrompt.SYSTEM,"Explain several differences between a maintenance log and a manufacturer manual.",96,true,(s,n)->{if(n>=3)engine.cancel(request);});
        require(stopped.cancelled()&&stopped.tokens()==3&&new JSONObject(engine.threadpoolAudit()).getBoolean("pausedAfterRequest"),"Cancellation joins the graph and pauses persistent workers");
        NativeEngine.Result recovered=engine.generateWithSampling(engine.request(),model.file(),ChatPrompt.SYSTEM,prompt.user(),48,true,(s,n)->{});
        require(recovered.cachedTokens()==0&&recovered.text().equals(first.text())&&recovered.firstLogitsHash()==first.firstLogitsHash(),"Cancelled context is discarded and recovery matches cold output");
        engine.configure(new NativeEngine.Configuration(2,6,128,false,8,0,true,1,4,true,0));
        NativeEngine.Result resized=engine.generateWithSampling(engine.request(),model.file(),ChatPrompt.SYSTEM,prompt.user(),48,true,(s,n)->{});JSONObject resizedPool=new JSONObject(engine.threadpoolAudit());
        require(resized.text().equals(first.text())&&resized.firstLogitsHash()==first.firstLogitsHash()&&resizedPool.getInt("threads")==2&&resizedPool.getInt("promptThreads")==6&&resizedPool.getBoolean("pausedAfterRequest"),"Independent prompt/decode pools resize without changing the answer");
        report.put("lifecycle",new JSONObject().put("firstText",first.text()).put("repeatReusedTokens",repeated.cachedTokens()).put("firstPool",firstPool).put("repeatedPool",secondPool).put("resizedPool",resizedPool));write();
    }
    private void tune(NativeEngine engine,ModelStore model)throws Exception{
        engine.deadlineForTests(300000);String system=ChatPrompt.SYSTEM,user=prompts().get(1).user();
        NativeEngine.Result reference=call(engine,model,"tune/reference",system,user,false,4,8,64,false,0);int[] expected=engine.lastTokens();long hash=reference.firstLogitsHash();
        require(reference.reason()<2&&expected.length>=8,"Reference sequence available for tuning");
        int[] prompts={2,4,6,8},widths={4,8};double[][] times=new double[8][2];boolean[] allowed=new boolean[8];Arrays.fill(allowed,true);
        for(int round=0;round<2;round++)for(int step=0;step<8;step++){
            int choice=round==0?step:7-step;promptThreadsOverride=prompts[choice/2];int width=widths[choice%2];
            NativeEngine.Result r=call(engine,model,"prefill/round"+round+"/p"+promptThreadsOverride+"/w"+width,system,user,true,4,width,8,true,0);
            boolean parity=equivalent(r,engine.lastTokens(),hash,expected);allowed[choice]&=parity;times[choice][round]=r.firstTokenMs();answers.getJSONObject(answers.length()-1).put("tuningEligible",parity);write();
        }
        int best=-1;double bestTime=Double.POSITIVE_INFINITY;
        for(int i=0;i<8;i++)if(allowed[i]&&(times[i][0]+times[i][1])/2<bestTime){best=i;bestTime=(times[i][0]+times[i][1])/2;}
        require(best>=0,"At least one prefill configuration preserves reference logits/tokens");
        promptThreadsOverride=prompts[best/2];int width=widths[best%2];int[] decoders={1,2,4,6,8};double[][] decode=new double[5][2];boolean[] decodeAllowed=new boolean[5];Arrays.fill(decodeAllowed,true);
        for(int round=0;round<2;round++)for(int step=0;step<5;step++){
            int choice=round==0?step:4-step;NativeEngine.Result r=call(engine,model,"decode/round"+round+"/t"+decoders[choice],system,user,true,decoders[choice],width,64,true,0);
            boolean parity=equivalent(r,engine.lastTokens(),hash,expected)&&engine.lastTokens().length==expected.length;
            decodeAllowed[choice]&=parity;decode[choice][round]=r.decodeMs();answers.getJSONObject(answers.length()-1).put("tuningEligible",parity);write();
        }
        int bestDecode=-1;bestTime=Double.POSITIVE_INFINITY;
        for(int i=0;i<5;i++)if(decodeAllowed[i]&&(decode[i][0]+decode[i][1])/2<bestTime){bestDecode=i;bestTime=(decode[i][0]+decode[i][1])/2;}
        require(bestDecode>=0,"At least one decode configuration preserves the full reference sequence");
        report.put("recommendation",new JSONObject().put("threads",decoders[bestDecode]).put("promptThreads",promptThreadsOverride).put("width",width).put("batch",128).put("decodeRows",Integer.parseInt(args.getString("arm_rows","1"))).put("persistentThreads",true).put("affinityMask",0).put("scope","Exploratory two-stage selection. Requires independent complete-output confirmation before product adoption."));write();promptThreadsOverride=0;
    }
}
