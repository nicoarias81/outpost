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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separates functional assertions from measured decision accuracy. */
final class JudgeChecks {
    private final Instrumentation test;
    private int checks;
    private final JSONArray functional = new JSONArray(), decisions = new JSONArray();
    private record Case(String id, String lang, String evidence, String claim, int expected) {}
    private static final List<Case> CASES = List.of(
        new Case("en-power-supported","en","Power is measured in watts (W). Energy is measured in watt-hours (Wh).","Energy is measured in watt-hours.",0),
        new Case("en-energy-contradicted","en","Power is measured in watts (W). Energy is measured in watt-hours (Wh).","Energy is measured in watts instead of watt-hours.",1),
        new Case("en-gps-unknown","en","A GPS receiver uses radio signals from satellites to estimate its position.","This receiver has an accuracy of exactly one metre.",2),
        new Case("en-gps-supported","en","A GPS receiver uses radio signals from satellites to estimate its position.","GPS receivers receive signals from satellites.",0),
        new Case("en-solar-contradicted","en","Photovoltaic panels convert sunlight to electricity. Their output is direct current.","The panels produce alternating current rather than direct current.",1),
        new Case("en-solar-unknown","en","Photovoltaic panels convert sunlight to electricity.","These panels have an efficiency of 22 percent.",2),
        new Case("es-power-supported","es","La potencia se mide en vatios (W). La energía se mide en vatios hora (Wh).","La energía se mide en vatios hora.",0),
        new Case("es-energy-contradicted","es","La potencia se mide en vatios (W). La energía se mide en vatios hora (Wh).","La energía se mide en vatios en lugar de vatios hora.",1),
        new Case("es-gps-unknown","es","Un receptor GPS utiliza señales de radio de satélites para estimar su posición.","Este receptor tiene una precisión de exactamente un metro.",2),
        new Case("es-gps-supported","es","Un receptor GPS utiliza señales de radio de satélites para estimar su posición.","Los receptores GPS reciben señales de satélites.",0),
        new Case("es-solar-contradicted","es","Los paneles fotovoltaicos convierten luz solar en electricidad. Producen corriente continua.","Los paneles producen corriente alterna en lugar de continua.",1),
        new Case("es-solar-unknown","es","Los paneles fotovoltaicos convierten luz solar en electricidad.","Estos paneles tienen una eficiencia del 22 por ciento.",2)
    );
    JudgeChecks(Instrumentation test) { this.test=test; }
    private void check(boolean ok,String message) throws Exception { functional.put(new JSONObject().put("check",message).put("passed",ok)); if(!ok) throw new AssertionError(message); checks++; }
    private void status(String message) { Bundle b=new Bundle(); b.putString("stream","\n"+message+"\n"); test.sendStatus(0,b); }
    void run() {
        Bundle output=new Bundle(); long started=SystemClock.elapsedRealtime();
        try {
            ModelStore.select(test.getTargetContext(),ModelStore.QWEN);
            String[] permissions=test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions==null || !Arrays.asList(permissions).contains("android.permission.INTERNET"),"Reviewer APK has no INTERNET permission");
            JudgeStore models=new JudgeStore(test.getTargetContext()); models.prepareHead();
            if(!models.ready()) {
                status("Verifying and importing pinned Kev weights");
                try(FileInputStream input=new FileInputStream(new File(test.getTargetContext().getExternalFilesDir(null),"test-kev.gguf"))) { models.install(input); }
            }
            check(models.ready(),"Kev GGUF and pointer head verified");
            boolean rejected=false;
            try { models.install(new ByteArrayInputStream(new byte[]{1,2,3})); } catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected && models.ready(),"Bad reviewer import preserves previous model");
            check(EvidenceReview.firstClaim("Una frase [1]. Otra frase [2].").equals("Una frase."),"Review explicitly selects only the first sentence");
            int correct=0, falseSupport=0, english=0, spanish=0, diagnosticFalseSupport=0;
            try(NativeEngine engine=new NativeEngine()) {
                long cancelled=engine.request(); engine.cancel(cancelled);
                NativeEngine.Decision stopped=engine.judge(cancelled,models.file(),models.head(),"Evidence","Question",EvidenceReview.OPTIONS);
                check(stopped.reason()==2,"Queued review cancellation preserved");
                for(Case item:CASES) {
                    NativeEngine.Decision result=engine.judge(engine.request(),models.file(),models.head(),item.evidence(),EvidenceReview.instruction(item.claim()),EvidenceReview.OPTIONS);
                    valid(result); boolean ok=result.best()==item.expected();
                    if(ok) { correct++; if(item.lang().equals("es")) spanish++; else english++; }
                    if(item.expected()!=0 && result.best()==0) falseSupport++;
                    record(item,result);
                    status(item.id()+": predicted="+result.best()+" expected="+item.expected()+" time="+result.totalMs()+" ms");
                }
                Case repeated=CASES.get(1);
                String[] reordered={EvidenceReview.OPTIONS[2],EvidenceReview.OPTIONS[0],EvidenceReview.OPTIONS[1]};
                NativeEngine.Decision swapped=engine.judge(engine.request(),models.file(),models.head(),repeated.evidence(),EvidenceReview.instruction(repeated.claim()),reordered);
                valid(swapped);
                int mapped=new int[]{2,0,1}[swapped.best()];
                decisions.put(new JSONObject().put("id","en-energy-options-reordered").put("predicted",mapped).put("expected",1).put("probabilitiesInReorderedOrder",new JSONArray(swapped.probabilities())).put("totalMs",swapped.totalMs()));
                try(Library library=TestLibrary.seeded(test.getTargetContext())) {
                    String evidence=EvidenceReview.evidence(ResearchPrompt.prepare("kW kWh",library.search("kW kWh")).sources());
                    Case actual=new Case("observed-qwen-0.5b-error","es",evidence,"La potencia y energía se mide en vatios (W) o kilovatios (kW).",1);
                    NativeEngine.Decision result=engine.judge(engine.request(),models.file(),models.head(),evidence,EvidenceReview.instruction(actual.claim()),EvidenceReview.OPTIONS);
                    valid(result); record(actual,result); status("Actual previous model error classified as "+result.best());
                    if(result.best()==0) diagnosticFalseSupport++;
                    for(Case diagnostic:List.of(
                        new Case("atomic-power-full-evidence","es",evidence,"La potencia se mide en vatios (W) o kilovatios (kW).",0),
                        new Case("atomic-energy-full-evidence","es",evidence,"La energía se mide en vatios (W) o kilovatios (kW).",1),
                        new Case("atomic-energy-short-evidence","es","La potencia se mide en vatios (W). La energía se mide en vatios hora (Wh).","La energía se mide en vatios (W).",1)
                    )) {
                        NativeEngine.Decision diagnosticResult=engine.judge(engine.request(),models.file(),models.head(),diagnostic.evidence(),EvidenceReview.instruction(diagnostic.claim()),EvidenceReview.OPTIONS);
                        valid(diagnosticResult); record(diagnostic,diagnosticResult);
                        if(diagnostic.expected()!=0 && diagnosticResult.best()==0) diagnosticFalseSupport++;
                        status(diagnostic.id()+": predicted="+diagnosticResult.best()+" expected="+diagnostic.expected());
                    }
                }
                ModelStore generator=new ModelStore(test.getTargetContext(),ModelStore.QWEN);
                NativeEngine.Result back=engine.generate(engine.request(),generator.file(),"Answer in English.","Say hello.",8,(s,n)->{});
                check(back.tokens()>0 && back.reason()!=3,"Same session switches from Kev back to the generator");
            }
            Debug.MemoryInfo memory=new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
            JSONObject report=new JSONObject().put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("fixtureVersion","bilingual-v1-with-english-library").put("model",JudgeStore.NAME).put("modelSha256",JudgeStore.SHA).put("headSha256",JudgeStore.HEAD_SHA)
                .put("functionalChecksPassed",checks).put("functionalChecks",functional).put("evaluationCases",decisions)
                .put("balancedCases",12).put("correct",correct).put("englishCorrect",english).put("spanishCorrect",spanish).put("falseSupport",falseSupport)
                .put("diagnosticFalseSupport",diagnosticFalseSupport)
                .put("uiReviewAvailable",false)
                .put("processPssKiBAtEnd",memory.getTotalPss()).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("limits","Small bilingual development probe. Not a held-out benchmark or calibration. Same weights evaluated in Android x86_64 only; no physical device or remote inference.");
            write("review-checks.json",report.toString(2));
            output.putString("stream","\nPASS: "+checks+" functional review checks. Simple cases: "+correct+"/12; false support on simple cases: "+falseSupport+"; false support on error diagnostics: "+diagnosticFalseSupport+".\n"); test.finish(Activity.RESULT_OK,output);
        } catch(Throwable error) {
            try { write("review-failure.json",new JSONObject().put("error",android.util.Log.getStackTraceString(error)).put("functionalChecks",functional).put("decisions",decisions).toString(2)); } catch(Exception ignored) { }
            output.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error)); test.finish(Activity.RESULT_CANCELED,output);
        }
    }
    private void valid(NativeEngine.Decision result) throws Exception {
        double total=0; for(double p:result.probabilities()) { if(!Double.isFinite(p) || p<0 || p>1) throw new AssertionError("Invalid distribution"); total+=p; }
        check(result.reason()==0 && Math.abs(total-1)<1e-6 && result.inputTokens()>0,"Native decision is finite, normalized and complete");
    }
    private void record(Case item,NativeEngine.Decision result) throws Exception {
        decisions.put(new JSONObject().put("id",item.id()).put("language",item.lang()).put("evidence",item.evidence()).put("claim",item.claim()).put("expected",item.expected()).put("predicted",result.best()).put("probabilities",new JSONArray(result.probabilities())).put("inputTokens",result.inputTokens()).put("loadMs",result.loadMs()).put("totalMs",result.totalMs()));
    }
    private File evidence(String name) { File dir=new File(test.getTargetContext().getFilesDir(),"evidence"); dir.mkdirs(); return new File(dir,name); }
    private void write(String name,String value) throws Exception { try(FileOutputStream out=new FileOutputStream(evidence(name))) { out.write(value.getBytes(StandardCharsets.UTF_8)); } }
    private void screenshot(String name) throws Exception { SystemClock.sleep(300); Bitmap bitmap=test.getUiAutomation().takeScreenshot(); if(bitmap==null) throw new IllegalStateException("Screenshot unavailable"); try(FileOutputStream out=new FileOutputStream(evidence(name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle(); }
}
