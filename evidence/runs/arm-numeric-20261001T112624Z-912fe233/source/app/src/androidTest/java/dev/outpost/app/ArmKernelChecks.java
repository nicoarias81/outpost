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
        @Override public void onCreate(Bundle state){super.onCreate(null);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);message=new TextView(this);message.setTextSize(20);message.setPadding(32,96,32,32);message.setText("Outpost performance test\n\nPlease leave this screen open.\nYour conversation and documents are not used.");setContentView(message);}
    }
    private final Instrumentation test;private final Bundle args;
    private final JSONObject report=new JSONObject();private final JSONArray checks=new JSONArray(),answers=new JSONArray();
    private File output;private BenchActivity screen;
    ArmKernelChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;}
    private void write()throws Exception{Files.write(output.toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));}
    private void require(boolean ok,String label)throws Exception{checks.put(new JSONObject().put("label",label).put("passed",ok));write();if(!ok)throw new AssertionError(label);}
    private void status(String text){Bundle b=new Bundle();b.putString("stream","\n"+text+"\n");test.sendStatus(0,b);if(screen!=null)test.runOnMainSync(()->screen.message.setText("Outpost performance test\n\n"+text+"\n\nPlease leave this screen open."));}
    private JSONObject conditions()throws Exception{
        var context=test.getTargetContext();Intent battery=context.registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return new JSONObject().put("thermalStatus",context.getSystemService(PowerManager.class).getCurrentThermalStatus()).put("interactive",context.getSystemService(PowerManager.class).isInteractive()).put("keyguard",context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()).put("batteryTenthsC",battery==null?-1:battery.getIntExtra("temperature",-1)).put("plugged",battery==null?-1:battery.getIntExtra("plugged",-1));
    }
    void run(){boolean passed=false;Bundle end=new Bundle();
        try{
            String id=args.getString("arm_run",""),phase=args.getString("arm_phase","numeric");if(!id.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run required");
            File dir=new File(test.getTargetContext().getFilesDir(),"evidence/arm/"+id);if(dir.exists()||!dir.mkdirs())throw new IllegalStateException("Run exists");output=new File(dir,"arm-checks.json");
            report.put("runId",id).put("phase",phase).put("passed",false).put("checks",checks).put("answers",answers).put("initialRuntime",new JSONObject(NativeEngine.kernelProfile())).put("scope","Foreground Android ARM experiment. Synthetic prompts only; exact parity and actual dispatch required. USB-powered, no energy claim.");write();
            require(android.os.Build.MANUFACTURER.equals("Google")&&android.os.Build.MODEL.equals("Pixel 10 Pro")&&android.os.Build.SUPPORTED_ABIS[0].equals("arm64-v8a"),"Registered Pixel architecture/model");
            require(!conditions().getBoolean("keyguard"),"Unlock the Pixel before foreground measurements");
            screen=(BenchActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            report.put("conditionsBefore",conditions());write();
            if(phase.equals("numeric")){
                status("Checking 16,000+ vectors, guard pages and reference dispatch...");
                double[] d=NativeEngine.nativeKernelChecks();report.put("vectors",new JSONObject().put("supported",d[0]==1).put("comparisons",d[1]).put("bitMismatches",d[2]).put("dispatch",d[3]==1).put("guard",d[4]==1).put("maxAbsolute",d[5]).put("maxRelative",d[6]).put("referenceNs",d[7]).put("dotProdNs",d[8]));write();
                require(d[0]==1&&d[1]>=16000&&d[2]==0&&d[3]==1&&d[4]==1,"Direct ARM dot preserves all bits and guarded bounds");
                int[] policy=NativeEngine.nativeDispatchChecks();require(policy[0]>=50&&policy[1]==0,"Capability/dispatch policy checks");
                status("Checking matrix strides, tails, workers and real Bonsai shapes...");JSONObject graphs=new JSONObject(NativeEngine.nativeArmGraphChecks());report.put("graphs",graphs);write();
                require(graphs.getBoolean("supported")&&graphs.getInt("comparisons")>10000&&graphs.getInt("failures")==0&&graphs.getJSONArray("benchmarks").length()==60,"Prepared matrix path is bitwise equivalent across all graphs");
            }else{
                ModelStore model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(var in=new FileInputStream(model.file())){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                require(model.file().length()==model.spec().bytes()&&hash.toString().equals(model.spec().sha256()),"Pinned Bonsai bytes verified; file pages are warm");report.put("modelSha256",hash.toString());write();
                try(NativeEngine engine=new NativeEngine()){
                    if(phase.equals("tune"))tune(engine,model);
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
        JSONObject before=conditions();if(before.getBoolean("keyguard")||!before.getBoolean("interactive")||before.getInt("thermalStatus")>=PowerManager.THERMAL_STATUS_SEVERE)throw new IllegalStateException("Foreground/thermal precondition changed");
        NativeEngine.setKernelAutomatic(fast);engine.clearCache();engine.configure(new NativeEngine.Configuration(threads,threads,128,false,width,0,true,1,1));
        JSONObject row=new JSONObject().put("id",id).put("fast",fast).put("threads",threads).put("width",width).put("batch",128).put("maxTokens",cap).put("sampler","top-k20/top-p0.8/temp0.7/seed42").put("system",system).put("user",user).put("status","running").put("conditionsBefore",before);answers.put(row);write();status("Starting "+id+" / "+(fast?"DotProd":"reference")+" / "+threads+" threads / width "+width);
        long cpu=android.os.Process.getElapsedCpuTime(),start=SystemClock.elapsedRealtime();NativeEngine.Result r;
        try{r=engine.generateWithSampling(engine.request(),model.file(),system,user,cap,true,(s,n)->{});}catch(Throwable e){row.put("status","error").put("error",e.toString());write();throw e;}
        row.put("status","returned").put("text",r.text()).put("tokens",r.tokens()).put("promptTokens",r.promptTokens()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("loadMs",r.loadMs()).put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("stopReason",r.reason()).put("firstLogitsHash",Long.toUnsignedString(r.firstLogitsHash())).put("tokenIds",new JSONArray(engine.lastTokens())).put("kernelUsed",NativeEngine.kernelWasUsed()).put("batchUsed",NativeEngine.kernelBatchUsed()).put("processCpuMs",android.os.Process.getElapsedCpuTime()-cpu).put("wallMs",SystemClock.elapsedRealtime()-start).put("conditionsAfter",conditions());write();
        require(NativeEngine.kernelWasUsed()==fast,"Requested kernel actually executed: "+id);status("Completed "+id+": "+r.totalMs()+"ms / first "+r.firstTokenMs()+"ms / "+r.tokens()+" tokens / stop "+r.reason());return r;
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
        int threads=Integer.parseInt(args.getString("arm_threads","4")),width=Integer.parseInt(args.getString("arm_width","4"));int rounds=confirm?3:1;
        report.put("comparisonThreads",threads).put("comparisonWidth",width).put("rounds",rounds);write();
        List<ChatPrompt.Prepared> prompts=prompts();
        for(int c=0;c<prompts.size();c++){
            var prompt=prompts.get(c);String expected=null;long hash=0;int[] tokens=null;
            for(int round=0;round<rounds;round++)for(int order=0;order<2;order++){
                boolean fast=(c+round+order)%2!=0;
                call(engine,model,"case"+c+"/round"+round+"/warmup",ChatPrompt.SYSTEM,prompt.user(),fast,threads,width,8);
                answers.getJSONObject(answers.length()-1).put("warmup",true);write();
                NativeEngine.Result r=call(engine,model,"case"+c+"/round"+round,ChatPrompt.SYSTEM,prompt.user(),fast,threads,width,192);
                require(r.reason()==0,"Complete EOS answer within unchanged192-token/120-second budget");
                if(expected==null){expected=r.text();hash=r.firstLogitsHash();tokens=engine.lastTokens();}
                else require(expected.equals(r.text())&&hash==r.firstLogitsHash()&&Arrays.equals(tokens,engine.lastTokens()),"Full model token/text/initial-logit parity");
            }
        }
    }
    private void tune(NativeEngine engine,ModelStore model)throws Exception{
        String system=ChatPrompt.SYSTEM,user=prompts().get(1).user();int[] workers={2,4,6,8},widths={1,2,4,8};
        String expected=null;long hash=0;int[] tokens=null;
        call(engine,model,"tune/warmup",system,user,true,4,4,8);answers.getJSONObject(answers.length()-1).put("warmup",true);write();
        for(int round=0;round<2;round++)for(int step=0;step<16;step++){
            int choice=round==0?step:15-step,t=workers[choice/4],w=widths[choice%4];NativeEngine.Result r=call(engine,model,"tune/round"+round+"/t"+t+"/w"+w,system,user,true,t,w,32);
            require(r.reason()<2&&r.tokens()>0,"Bounded exploratory output returned");
            if(expected==null){expected=r.text();hash=r.firstLogitsHash();tokens=engine.lastTokens();}
            else require(expected.equals(r.text())&&hash==r.firstLogitsHash()&&Arrays.equals(tokens,engine.lastTokens()),"Tuning preserves text/tokens/logits across configurations");
        }
    }
}
