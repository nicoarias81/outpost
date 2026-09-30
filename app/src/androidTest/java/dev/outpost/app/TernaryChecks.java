package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.widget.EditText;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** Model comparisons, not a scored benchmark; all generation runs on Android. */
final class TernaryChecks {
    static final String SAMPLED_POLICY = "top_k20 top_p0.8 temp0.7 seed42; compact prompt v2; 2048 context; 192 output tokens; 120s deadline; 4 CPU threads; fixed official non-thinking suffix for Bonsai";
    static final String GREEDY_POLICY = "greedy; compact prompt v2; 2048 context; 192 output tokens; 120s deadline; 4 CPU threads; fixed official non-thinking suffix for Bonsai";
    private final Instrumentation test;
    private record Probe(String id,String query,String retrieval) {}
    private static final Probe[] PROBES={
        new Probe("energy","What is the difference between kW and kWh?","kW kWh"),
        new Probe("gps","How does GPS calculate its position?","GPS satellites receiver"),
        new Probe("comparison","Compare what a compass indicates with what GPS calculates.","GPS compass orientation"),
        new Probe("insufficient","What exact percentage efficiency do the solar panels in these sources have?","eficiencia paneles solares")
    };
    TernaryChecks(Instrumentation test) { this.test=test; }
    private ModelStore.Spec find(String id) {
        for(ModelStore.Spec spec:ModelStore.PROFILES) if(spec.id().equals(id)) return spec;
        throw new IllegalArgumentException("Unknown profile");
    }
    void run(String operation) {
        Bundle out=new Bundle();
        try {
            if(!android.os.Build.SUPPORTED_ABIS[0].equals("x86_64")) throw new AssertionError("Emulator artifact required");
            String[] permissions=test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",PackageManager.GET_PERMISSIONS).requestedPermissions;
            if(permissions!=null && Arrays.asList(permissions).contains("android.permission.INTERNET")) throw new AssertionError("Unexpected network permission");
            String[] action=operation.split(":",2); ModelStore.Spec spec=find(action[1]);
            ModelStore models=new ModelStore(test.getTargetContext(),spec);
            if(action[0].equals("install")) {
                File staged=new File(test.getTargetContext().getExternalFilesDir(null),"test-"+spec.id()+".gguf");
                if(!models.ready()) {
                    try(FileInputStream input=new FileInputStream(staged)) { models.install(input,bytes->{}); }
                    if(!models.ready()) throw new AssertionError("Imported weights unavailable");
                }
                // This exact staging file is created by test-bonsai.ps1, not a user's document.
                if(staged.exists() && !staged.delete()) status("Verified model installed; staging file could not be removed");
                out.putString("stream","\nINSTALLED "+spec.id()+" with verified SHA-256\n"); test.finish(Activity.RESULT_OK,out); return;
            }
            if(!models.ready()) throw new AssertionError("Install the selected model first");
            if(action[0].equals("select")) {
                ModelStore.select(test.getTargetContext(),spec);
                if(!ModelStore.selected(test.getTargetContext()).id().equals(spec.id())) throw new AssertionError("Profile selection did not persist");
                MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                for(int i=0;i<100 && activity.findViewById(MainActivity.QUERY_ID)==null;i++) SystemClock.sleep(100);
                test.waitForIdleSync(); test.runOnMainSync(() -> clickButton(activity,activity.getString(R.string.ui_status))); test.waitForIdleSync(); screenshot("models.png");
                out.putString("stream","\nPASS selected "+spec.id()+"\n"); test.finish(Activity.RESULT_OK,out); return;
            }
            if(action[0].equals("ui") || action[0].equals("ui-use")) {
                ModelStore.select(test.getTargetContext(),spec);
                MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                for(int i=0;i<100 && activity.findViewById(MainActivity.QUERY_ID)==null;i++) SystemClock.sleep(100);
                test.waitForIdleSync();
                test.runOnMainSync(() -> { ((EditText)activity.findViewById(MainActivity.QUERY_ID)).setText(PROBES[0].query()); activity.findViewById(MainActivity.SEARCH_ID).performClick(); });
                for(int i=0;i<100 && !activity.searchDone;i++) SystemClock.sleep(100);
                test.waitForIdleSync(); test.runOnMainSync(() -> activity.findViewById(MainActivity.GENERATE_ID).performClick());
                for(int i=0;i<1300 && !activity.answerDone;i++) SystemClock.sleep(100);
                if(activity.lastAnswer==null || !ResearchPrompt.hasAnswerContent(activity.lastAnswer.text()) || activity.lastAnswer.reason()!=0) throw new AssertionError("UI did not complete a substantive response");
                test.waitForIdleSync(); screenshot("bonsai-"+spec.id()+".png");
                write("bonsai-ui-"+spec.id()+".json",new JSONObject().put("model",spec.name()).put("text",activity.lastAnswer.text()).put("tokens",activity.lastAnswer.tokens()).put("firstTokenMs",activity.lastAnswer.firstTokenMs()).put("totalMs",activity.lastAnswer.totalMs()).put("stopReason",activity.lastAnswer.reason()).put("kernel",NativeEngine.kernelName()).put("fastPathUsed",NativeEngine.kernelWasUsed()).toString(2));
                if(action[0].equals("ui-use") && spec.id().equals("bonsai4")) {
                    NativeEngine.Result cold=activity.lastAnswer;
                    String coldText=cold.text();
                    int releases=activity.cacheReleaseRequests;
                    test.runOnMainSync(() -> activity.findViewById(MainActivity.GENERATE_ID).performClick());
                    for(int i=0;i<1300 && !activity.answerDone;i++) SystemClock.sleep(100);
                    NativeEngine.Result warm=activity.lastAnswer;
                    if(warm==null) throw new AssertionError("Warm UI generation failed");
                    test.waitForIdleSync(); screenshot("bonsai-warm-"+spec.id()+".png");
                    write("bonsai-warm-"+spec.id()+".json",new JSONObject().put("text",warm.text()).put("coldText",coldText).put("tokens",warm.tokens()).put("firstTokenMs",warm.firstTokenMs()).put("totalMs",warm.totalMs()).put("prefillMs",warm.prefillMs()).put("decodeMs",warm.decodeMs()).put("cachedTokens",warm.cachedTokens()).put("promptTokens",warm.promptTokens()).put("reason",warm.reason()).put("coldLogitsHash",Long.toUnsignedString(cold.firstLogitsHash(),16)).put("warmLogitsHash",Long.toUnsignedString(warm.firstLogitsHash(),16)).put("releaseRequestsDuringRepeat",activity.cacheReleaseRequests-releases).toString(2));
                    if(warm.reason()!=0 || warm.cachedTokens()<=0 || !warm.text().equals(coldText)) throw new AssertionError("Warm UI generation did not preserve the answer/prefix; diagnostic saved");
                    test.runOnMainSync(() -> activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
                    test.runOnMainSync(() -> activity.findViewById(MainActivity.GENERATE_ID).performClick());
                    for(int i=0;i<1300 && !activity.answerDone;i++) SystemClock.sleep(100);
                    NativeEngine.Result released=activity.lastAnswer;
                    if(released==null || released.reason()!=0 || released.cachedTokens()!=0 || !released.text().equals(coldText)) throw new AssertionError("Memory callback did not release cached context");
                    write("bonsai-trim-"+spec.id()+".json",new JSONObject().put("callbackSimulated",true).put("cachedTokens",released.cachedTokens()).put("textPreserved",true).put("firstTokenMs",released.firstTokenMs()).put("totalMs",released.totalMs()).toString(2));
                }
                // Exercise the visible selector and return to the previous default generator.
                test.runOnMainSync(() -> clickButton(activity,activity.getString(R.string.ui_status))); test.waitForIdleSync();
                test.runOnMainSync(() -> clickButton(activity,ModelStore.QWEN.name())); test.waitForIdleSync();
                if(!ModelStore.selected(test.getTargetContext()).id().equals(ModelStore.QWEN.id())) throw new AssertionError("Visible profile selector did not persist selection");
                if(action[0].equals("ui-use")) {
                    test.runOnMainSync(() -> clickButton(activity,spec.name())); test.waitForIdleSync();
                    if(!ModelStore.selected(test.getTargetContext()).id().equals(spec.id())) throw new AssertionError("Requested profile did not persist");
                }
                screenshot("models.png");
                out.putString("stream","\nPASS UI "+spec.id()+"\n"); test.finish(Activity.RESULT_OK,out); return;
            }
            if(!action[0].equals("compare")) throw new IllegalArgumentException("Unknown operation");
            JSONArray results=new JSONArray(); long started=SystemClock.elapsedRealtime();
            int pss=0;
            try(Library library=new Library(test.getTargetContext(),null); NativeEngine engine=new NativeEngine()) {
                for(Probe probe:PROBES) {
                    ResearchPrompt.Prepared prompt=ResearchPrompt.prepare(probe.query(),library.search(probe.retrieval()));
                    JSONArray sources=new JSONArray(); for(Library.Hit h:prompt.sources()) sources.put(new JSONObject().put("id",h.document().id()).put("passage",h.passage()));
                    JSONObject row=new JSONObject().put("id",probe.id()).put("question",probe.query()).put("sources",sources);
                    try {
                        NativeEngine.Result answer=engine.generateWithSampling(engine.request(),models.file(),ResearchPrompt.SYSTEM,prompt.user(),192,spec.sampled(),(s,n)->{});
                        Debug.MemoryInfo memory=new Debug.MemoryInfo(); Debug.getMemoryInfo(memory); pss=Math.max(pss,memory.getTotalPss());
                        row.put("text",answer.text()).put("inputTokens",answer.promptTokens()).put("outputTokens",answer.tokens()).put("firstTokenMs",answer.firstTokenMs()).put("loadMs",answer.loadMs()).put("totalMs",answer.totalMs()).put("stopReason",answer.reason()).put("pssKiBAfterCase",memory.getTotalPss()).put("hasReasoningTags",answer.text().contains("<think>") || answer.text().contains("</think>")).put("kernel",NativeEngine.kernelName()).put("q2FastPathUsed",NativeEngine.kernelWasUsed());
                        status(spec.id()+" / "+probe.id()+": "+answer.tokens()+" tokens; "+answer.totalMs()+" ms; stop="+answer.reason());
                    } catch(Exception e) { row.put("error",e.toString()); status(spec.id()+" / "+probe.id()+": "+e.getMessage()); }
                    results.put(row);
                    write("bonsai-"+spec.id()+".json",report(spec,results,started,pss).toString(2));
                }
                long run=engine.request(); engine.cancel(run);
                NativeEngine.Result cancelled=engine.generate(run,models.file(),ResearchPrompt.SYSTEM,"Do not generate",32,(s,n)->{});
                if(!cancelled.cancelled() || cancelled.tokens()!=0) throw new AssertionError("Pre-cancellation failed");
            }
            out.putString("stream","\nPASS comparison completed for "+spec.id()+"; quality and truncation recorded separately.\n"); test.finish(Activity.RESULT_OK,out);
        } catch(Throwable e) {
            try { write("bonsai-failure.json",new JSONObject().put("operation",operation).put("error",android.util.Log.getStackTraceString(e)).toString(2)); } catch(Exception ignored) { }
            out.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(e)); test.finish(Activity.RESULT_CANCELED,out);
        }
    }
    private JSONObject report(ModelStore.Spec spec,JSONArray results,long start,int pss) throws Exception {
        return new JSONObject().put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("model",spec.name()).put("id",spec.id()).put("bytes",spec.bytes()).put("sha256",spec.sha256()).put("cases",results).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("maximumOfPostCasePssKiB",pss)
            .put("policy",(spec.sampled() ? SAMPLED_POLICY : GREEDY_POLICY))
            .put("limits","Four development probes. Not an independent benchmark. PSS values sampled after cases, not peak RAM. Android emulator only.");
    }
    private void status(String text) { Bundle b=new Bundle(); b.putString("stream","\n"+text+"\n"); test.sendStatus(0,b); }
    private void clickButton(MainActivity activity,String name) {
        java.util.ArrayList<android.view.View> found=new java.util.ArrayList<>();
        activity.getWindow().getDecorView().findViewsWithText(found,name,android.view.View.FIND_VIEWS_WITH_TEXT);
        for(android.view.View view:found) if(view instanceof android.widget.Button && ((android.widget.Button)view).getText().toString().endsWith(name)) { view.performClick(); return; }
        throw new AssertionError("Button not found: "+name);
    }
    private File evidence(String name) { File directory=new File(test.getTargetContext().getFilesDir(),"evidence"); directory.mkdirs(); return new File(directory,name); }
    private void write(String name,String text) throws Exception { try(FileOutputStream out=new FileOutputStream(evidence(name))) { out.write(text.getBytes(StandardCharsets.UTF_8)); } }
    private void screenshot(String name) throws Exception { SystemClock.sleep(300); Bitmap bitmap=test.getUiAutomation().takeScreenshot(); if(bitmap==null) throw new IllegalStateException("Screenshot unavailable"); try(FileOutputStream out=new FileOutputStream(evidence(name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle(); }
}
