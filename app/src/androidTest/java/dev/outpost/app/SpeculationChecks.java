package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class SpeculationChecks {
    private final Instrumentation test;
    private final JSONObject report=new JSONObject();
    private final JSONArray rows=new JSONArray();
    private String phase;
    private ModelStore model;
    private static final String SYSTEM="Use only the fictitious record. Follow the request literally. Do not add facts or procedures.";
    private static final String TEXT="Test record: pump M7. Nameplate identifier: BR-417. Listed replacement: filter F-28. "
        +"Last recorded inspection: September 12. The tank contains 720 liters and the recorded flow rate is 24 liters per minute. "
        +"No authorization to intervene or confirmed safety status is recorded. This record is fictitious and is not a real manual.";
    private static final String COPY="SOURCE [1]\nSTART OF TEXT\n"+TEXT+"\nEND OF TEXT\nCopy the text between the markers exactly, without the markers or additional comments.";
    private static final String QUESTION="SOURCE [1]\n"+TEXT+"\nWhat information is missing to decide on a repair? Answer in two sentences.";
    SpeculationChecks(Instrumentation test) { this.test=test; }
    private NativeEngine.Configuration config(int depth,boolean adaptive) { return new NativeEngine.Configuration(4,4,128,true,4,depth,adaptive); }
    void run(String phase) {
        this.phase=phase; Bundle output=new Bundle();
        try(NativeEngine e=new NativeEngine()) {
            model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);
            if(!model.ready()) throw new AssertionError("Bonsai 4B must already be installed");
            int[] unit=NativeEngine.nativeSpeculationChecks();
            report.put("version",test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0).versionName)
                .put("modelSha256",model.spec().sha256()).put("system",SYSTEM).put("runtime",new JSONObject(NativeEngine.kernelProfile()))
                .put("unitChecks",unit[0]).put("unitFailures",unit[1]).put("rows",rows)
                .put("adaptivePolicy","At least 8 emitted tokens, a matching suffix of 8..16 tokens, up to 3 drafts in the app; stop after 3 windows without estimated >5% benefit. Fixed diagnostic mode uses suffixes of 4..16 tokens.")
                .put("limits","Emulator x86 only. Fictitious records. The oracle uses a previous answer and is not a practical draft source. Batched target arithmetic may differ from serial arithmetic; text parity is reported separately from algorithm checks. PSS is sampled after calls, not peak RAM.");
            require(unit[0]>250 && unit[1]==0,"Speculation controller/sampling unit checks failed");
            if(phase.equals("unit")) { }
            else if(phase.equals("pilot")) pilot(e);
            else if(phase.equals("benchmark")) benchmark(e);
            else if(phase.equals("lifecycle")) lifecycle(e);
            else if(phase.equals("draft-cost")) draftCost(e);
            else if(phase.equals("guard")) guard(e);
            else if(phase.equals("profile")) profile(e);
            else if(phase.equals("missions")) missions(e);
            else if(phase.equals("ui")) ui();
            else if(phase.equals("audit")) audit(e);
            else if(phase.equals("energy-audit")) energyAudit(e);
            else throw new IllegalArgumentException("Unknown speculation phase");
            report.put("passed",true); write(); output.putString("stream","\nPASS speculation "+phase+": "+rows.length()+" calls recorded. Inspect parity and timings separately.\n");
            test.finish(Activity.RESULT_OK,output);
        } catch(Throwable error) {
            try { report.put("passed",false).put("error",android.util.Log.getStackTraceString(error)); write(); } catch(Exception ignored) { }
            output.putString("stream","\nFAIL speculation: "+android.util.Log.getStackTraceString(error)); test.finish(Activity.RESULT_CANCELED,output);
        }
    }
    private NativeEngine.Result call(NativeEngine e,String label,String user,int depth,boolean adaptive,boolean sampled,int max,int[] oracle) throws Exception {
        e.configure(config(depth,adaptive)); e.oracleForTests(oracle);
        NativeEngine.Result r=e.generateWithSampling(e.request(),model.file(),SYSTEM,user,max,sampled,(s,n)->{});
        add(e,label,user,depth,adaptive,sampled,oracle!=null,r);
        require(r.reason()<2 && r.tokens()>0,"Generation did not finish: "+label);
        return r;
    }
    private void add(NativeEngine e,String label,String user,int depth,boolean adaptive,boolean sampled,boolean oracle,NativeEngine.Result r) throws Exception {
        rows.put(new JSONObject().put("label",label).put("model",model.spec().id()).put("prompt",user).put("depth",depth).put("adaptive",adaptive).put("sampled",sampled).put("oracle",oracle)
            .put("matrixWidth",e.configuration().matrixWidth()).put("cacheEnabled",e.configuration().cache())
            .put("text",r.text()).put("tokenIds",new JSONArray(e.lastTokens())).put("tokens",r.tokens()).put("reason",r.reason()).put("firstTokenMs",r.firstTokenMs())
            .put("totalMs",r.totalMs()).put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("cachedTokens",r.cachedTokens()).put("promptTokens",r.promptTokens())
            .put("drafted",r.drafted()).put("accepted",r.accepted()).put("verifyPasses",r.verifyPasses()).put("rejectedWindows",r.rejectedWindows()).put("plainSteps",r.plainSteps())
            .put("verifyMicros",r.verifyMicros()).put("draftMicros",r.draftMicros()).put("disabledByCost",r.speculationDisabled()).put("pssKiBAfter",Debug.getPss()));
        write(); status(label+": decode "+r.decodeMs()+" ms, "+r.tokens()+" tokens, accepted "+r.accepted()+"/"+r.drafted()+", windows "+r.verifyPasses()+", disabled "+r.speculationDisabled());
    }
    private void pilot(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,true,1,null);
        int[] caps=e.modelCapabilities(); report.put("targetLayers",caps[0]).put("targetMtpLayers",caps[1]).put("targetVocabulary",caps[2]);
        NativeEngine.Result base=call(e,"copy-baseline",COPY,0,false,true,96,null); int[] ids=e.lastTokens();
        double[] audit=e.verificationAudit(Arrays.copyOf(ids,Math.min(12,ids.length)),4);
        report.put("teacherForcedAudit",new JSONObject().put("positions",audit[0]).put("maxLogitDifference",audit[1]).put("meanAbsoluteDifference",audit[2])
            .put("meanKlNats",audit[3]).put("sameTop1Positions",audit[4]).put("bitIdenticalPositions",audit[5])); write();
        NativeEngine.Result lookup=call(e,"copy-lookup-3",COPY,3,false,true,96,null);
        NativeEngine.Result auto=call(e,"copy-adaptive-3",COPY,3,true,true,96,null);
        NativeEngine.Result oracle=call(e,"copy-reference-oracle-3",COPY,3,false,true,96,ids);
        report.put("copyLookupTextParity",base.text().equals(lookup.text())).put("copyAdaptiveTextParity",base.text().equals(auto.text())).put("oracleTextParity",base.text().equals(oracle.text()));
        NativeEngine.Result normal=call(e,"question-baseline",QUESTION,0,false,true,96,null);
        NativeEngine.Result speculative=call(e,"question-adaptive-3",QUESTION,3,true,true,96,null);
        report.put("questionTextParity",normal.text().equals(speculative.text()));
        NativeEngine.Result fallback=call(e,"no-repetition", "Reply only with the word blue.",3,true,true,16,null);
        require(fallback.verifyPasses()==0,"Expected serial fallback on a short non-repeating answer");
    }
    private void benchmark(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,true,1,null);
        JSONArray groups=new JSONArray();
        for(String user:new String[]{COPY,QUESTION}) {
            String label=user.equals(COPY) ? "copy" : "question";
            List<Long> base=new ArrayList<>(),fast=new ArrayList<>(); List<Boolean> parity=new ArrayList<>();
            for(int round=0;round<3;round++) {
                NativeEngine.Result a,b;
                if(round%2==0) { a=call(e,label+"-"+round+"-baseline",user,0,false,true,96,null); b=call(e,label+"-"+round+"-adaptive",user,3,true,true,96,null); }
                else { b=call(e,label+"-"+round+"-adaptive",user,3,true,true,96,null); a=call(e,label+"-"+round+"-baseline",user,0,false,true,96,null); }
                base.add(a.decodeMs()); fast.add(b.decodeMs()); parity.add(a.text().equals(b.text()));
            }
            groups.put(new JSONObject().put("case",label).put("baselineMedianDecodeMs",median(base)).put("adaptiveMedianDecodeMs",median(fast))
                .put("speedup",(double)median(base)/median(fast)).put("pairedTextParity",new JSONArray(parity)));
        }
        report.put("comparisons",groups);
    }
    private void lifecycle(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,false,1,null);
        NativeEngine.Result base=call(e,"baseline",COPY,0,false,false,64,null); int[] ids=e.lastTokens();
        int[] wrong=ids.clone(); for(int i=1;i<wrong.length;i++) wrong[i]=0;
        NativeEngine.Result rejected=call(e,"deliberately-wrong-drafts",COPY,3,false,false,64,wrong);
        require(rejected.rejectedWindows()>0 && rejected.accepted()<rejected.drafted(),"Wrong proposals were not rejected");
        report.put("wrongDraftTextParity",base.text().equals(rejected.text()));
        e.configure(config(3,false)); e.oracleForTests(ids); long run=e.request();
        NativeEngine.Result cancelled=e.generateWithSampling(run,model.file(),SYSTEM,COPY,64,false,(s,n)->{if(n==6)e.cancel(run);});
        add(e,"cancel-after-six",COPY,3,false,false,true,cancelled);
        require(cancelled.reason()==2 && cancelled.tokens()==6 && cancelled.verifyPasses()>0,"Speculative cancellation leaked uncommitted output");
        NativeEngine.Result recovered=call(e,"after-cancel",COPY,0,false,false,64,null);
        require(recovered.cachedTokens()==0 && recovered.text().equals(base.text()),"Cancellation recovery failed");
        NativeEngine.Result bounded=call(e,"three-token-limit",COPY,7,false,false,3,ids);
        require(bounded.tokens()==3 && bounded.reason()==1,"Speculation exceeded output budget");
        String revised=COPY.replace("F-28","F-91");
        NativeEngine.Result revisedBase=call(e,"revised-source-baseline",revised,0,false,false,96,null);
        NativeEngine.Result revisedFast=call(e,"revised-source-speculative",revised,3,true,false,96,null);
        require(revisedFast.text().contains("F-91") && !revisedFast.text().contains("F-28"),"Stale source leaked into output");
        report.put("revisedTextParity",revisedBase.text().equals(revisedFast.text()));
        NativeEngine.Result eos=call(e,"short-eos","Say hello without adding anything else.",7,false,false,96,null);
        require(eos.reason()==0,"EOS was not respected");
        require(eos.accepted()<=eos.drafted(),"Invalid acceptance counters");
    }
    private void draftCost(NativeEngine e) throws Exception {
        JSONArray costs=new JSONArray();
        NativeEngine.Result targetOracle=null;
        long targetMicros=0,draftMicros=0;
        for(ModelStore.Spec spec:new ModelStore.Spec[]{ModelStore.BONSAI4,ModelStore.BONSAI17}) {
            model=new ModelStore(test.getTargetContext(),spec); require(model.ready(),"Install both pinned Bonsai models");
            call(e,spec.id()+"-warmup",COPY,0,false,false,1,null);
            List<Long> rates=new ArrayList<>();
            for(int r=0;r<3;r++) { NativeEngine.Result x=call(e,spec.id()+"-cost-"+r,COPY,0,false,false,64,null); rates.add(x.decodeMs()*1000/Math.max(1,x.tokens()-1)); }
            long perStep=median(rates);
            costs.put(new JSONObject().put("model",spec.id()).put("medianMicrosPerGeneratedStep",perStep));
            if(spec==ModelStore.BONSAI4) {
                targetMicros=perStep; int[] ids=e.lastTokens();
                targetOracle=call(e,"target-zero-cost-oracle",COPY,3,false,false,64,ids);
            } else draftMicros=perStep;
        }
        report.put("serialModelCosts",costs).put("costLimits","Standalone warm decode costs only. Does not measure simultaneous residency, draft acceptance, prefill synchronization, or a trained MTP head.");
        if(targetOracle!=null) report.put("optimisticHelperBudget",new JSONObject().put("allOracleProposalsAccepted",targetOracle.accepted()==targetOracle.drafted())
            .put("baselineEquivalentMicros",targetMicros*Math.max(1,targetOracle.tokens()-1))
            .put("oracleAndDraftMicros",targetOracle.decodeMs()*1000+targetOracle.drafted()*draftMicros)
            .put("idealSpeedup",(double)(targetMicros*Math.max(1,targetOracle.tokens()-1))/(targetOracle.decodeMs()*1000+targetOracle.drafted()*draftMicros))
            .put("limits","Optimistic estimate for this workload. Assumes perfect proposals and ignores helper prefill, synchronization, extra residency and memory traffic. Not an actual dual-model implementation or an MTP measurement."));
    }
    private void guard(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,false,1,null);
        NativeEngine.Result base=call(e,"baseline",COPY,0,false,false,64,null); int[] ids=e.lastTokens();
        int[] wrong=ids.clone(); Arrays.fill(wrong,0);
        NativeEngine.Result protectedRun=call(e,"wrong-drafts-with-cost-guard",COPY,3,true,false,64,wrong);
        require(protectedRun.speculationDisabled() && protectedRun.verifyPasses()==3 && protectedRun.plainSteps()>3,"Cost guard did not stop unprofitable speculation");
        require(base.text().equals(protectedRun.text()),"Cost guard changed deterministic output");
        e.configure(config(3,false)); e.oracleForTests(ids); long request=e.request();
        final Thread[] cancelThread={null};
        NativeEngine.Result interrupted=e.generateWithSampling(request,model.file(),SYSTEM,COPY,64,false,(s,n)->{
            if(n==1) { cancelThread[0]=new Thread(()->{try {Thread.sleep(100);}catch(InterruptedException ex){Thread.currentThread().interrupt();}e.cancel(request);}); cancelThread[0].start(); }
        });
        if(cancelThread[0]!=null) cancelThread[0].join(); add(e,"cancel-during-verification",COPY,3,false,false,true,interrupted);
        require(interrupted.reason()==2 && interrupted.tokens()==1 && interrupted.verifyPasses()>0,"Cancellation during verification leaked a draft");
        NativeEngine.Result recovered=call(e,"after-verification-cancel",COPY,0,false,false,64,null);
        require(recovered.cachedTokens()==0 && recovered.text().equals(base.text()),"Verification cancellation recovery failed");
    }
    private void profile(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,false,1,null);
        List<Long> scalar=new ArrayList<>(),grouped=new ArrayList<>(); String reference=null;
        for(int round=0;round<3;round++) for(int i=0;i<2;i++) {
            int width=round%2==0 ? (i==0 ? 1:4) : (i==0 ? 4:1);
            e.configure(new NativeEngine.Configuration(4,4,128,false,width)); e.oracleForTests(null);
            NativeEngine.Result r=e.generate(e.request(),model.file(),SYSTEM,COPY,4,(s,n)->{});
            add(e,"profile-"+round+"-width-"+width,COPY,0,false,false,false,r);
            require(r.reason()==1 && r.tokens()==4,"Profile control failed");
            if(reference==null) reference=r.text(); require(reference.equals(r.text()),"Profile output changed");
            (width==1 ? scalar:grouped).add(r.totalMs());
        }
        int chosen=(double)median(scalar)/median(grouped)>1.05 ? 4:1;
        RuntimeSettings.save(test.getTargetContext(),model.spec(),new RuntimeSettings.Profile(4,4,128,chosen,true));
        report.put("baselineMedianMs",median(scalar)).put("groupedMedianMs",median(grouped)).put("selectedWidth",chosen);
    }
    private void audit(NativeEngine e) throws Exception {
        call(e,"warmup",COPY,0,false,true,1,null);
        call(e,"reference-for-audit",COPY,0,false,true,32,null);
        int[] ids=Arrays.copyOf(e.lastTokens(),24); JSONArray checks=new JSONArray();
        for(int width:new int[]{2,4,8}) {
            double[] a=e.verificationAudit(ids,width);
            checks.put(new JSONObject().put("width",width).put("positions",a[0]).put("maxLogitDifference",a[1]).put("meanAbsoluteDifference",a[2]).put("meanKlNats",a[3]).put("sameTop1Positions",a[4]).put("bitIdenticalPositions",a[5]));
        }
        report.put("teacherForcedAudits",checks);
    }
    private void energyAudit(NativeEngine e) throws Exception {
        try(Library library=new Library(test.getTargetContext(),null)) {
            ResearchPrompt.Prepared p=ResearchPrompt.prepare("What is the difference between kW and kWh?",library.search("kW kWh"));
            e.configure(config(0,false));
            NativeEngine.Result base=e.generateWithSampling(e.request(),model.file(),ResearchPrompt.SYSTEM,p.user(),128,true,(s,n)->{});
            add(e,"energy-reference",p.user(),0,false,true,false,base); int[] ids=e.lastTokens();
            require(ids.length>=64,"Energy diagnostic needs at least 64 generated tokens");
            e.clearCache(); e.generateWithSampling(e.request(),model.file(),ResearchPrompt.SYSTEM,p.user(),1,true,(s,n)->{});
            double[] a=e.verificationAudit(Arrays.copyOf(ids,64),4);
            report.put("actualSystem",ResearchPrompt.SYSTEM).put("teacherForcedEnergy",new JSONObject().put("positions",a[0]).put("maxLogitDifference",a[1])
                .put("meanAbsoluteDifference",a[2]).put("meanKlNats",a[3]).put("sameTop1Positions",a[4]).put("bitIdenticalPositions",a[5])
                .put("firstDifferentPosition",a[6]).put("firstDifferentTop1Position",a[7]));
            e.configure(config(3,true));
            NativeEngine.Result spec=e.generateWithSampling(e.request(),model.file(),ResearchPrompt.SYSTEM,p.user(),128,true,(s,n)->{});
            add(e,"energy-speculative",p.user(),3,true,true,false,spec);
            int[] fast=e.lastTokens(); int common=0; while(common<ids.length && common<fast.length && ids[common]==fast[common]) common++;
            report.put("commonOutputTokens",common).put("textParity",base.text().equals(spec.text()));
        }
    }
    private void missions(NativeEngine e) throws Exception {
        JSONArray comparisons=new JSONArray();
        for(String[] c:RuntimeChecks.fieldCases()) {
            String user="SOURCE [1] — FICTITIOUS TEST DATA: "+c[1]+"\nQUESTION: "+c[2];
            call(e,c[0]+"-warmup",user,0,false,true,1,null);
            NativeEngine.Result a=call(e,c[0]+"-baseline",user,0,false,true,96,null);
            NativeEngine.Result b=call(e,c[0]+"-speculative",user,3,true,true,96,null);
            comparisons.put(new JSONObject().put("case",c[0]).put("textParity",a.text().equals(b.text())).put("baselineDecodeMs",a.decodeMs()).put("speculativeDecodeMs",b.decodeMs())
                .put("expectedMarker",c[3]).put("markerPresent",c[3].isEmpty() ? JSONObject.NULL : b.text().contains(c[3])));
        }
        report.put("comparisons",comparisons).put("qualityLimits","These are fictitious field controls. Marker presence and text parity are not factual verification or real mission success.");
    }
    private void ui() throws Exception {
        ModelStore.select(test.getTargetContext(),ModelStore.BONSAI4);
        boolean previous=RuntimeSettings.speculationEnabled(test.getTargetContext(),ModelStore.BONSAI4);
        RuntimeSettings.setSpeculation(test.getTargetContext(),ModelStore.BONSAI4,false);
        MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            for(int i=0;i<100 && activity.findViewById(MainActivity.QUERY_ID)==null;i++) SystemClock.sleep(100);
            search(activity);
            NativeEngine.Result baseline=generateUi(activity);
            test.runOnMainSync(()->clickButton(activity,activity.getString(R.string.ui_status))); test.waitForIdleSync();
            test.runOnMainSync(()->activity.findViewById(MainActivity.SPECULATION_ID).performClick()); test.waitForIdleSync();
            require(RuntimeSettings.speculationEnabled(test.getTargetContext(),ModelStore.BONSAI4),"UI did not enable speculation");
            test.runOnMainSync(()->{
                View toggle=activity.findViewById(MainActivity.SPECULATION_ID); android.view.ViewParent p=toggle.getParent();
                while(p!=null && !(p instanceof ScrollView)) p=p.getParent();
                if(p instanceof ScrollView scroll) scroll.scrollTo(0,Math.max(0,toggle.getTop()-scroll.getHeight()/2));
            });
            screenshot("speculation-state.png");
            test.runOnMainSync(()->clickButton(activity,activity.getString(R.string.ui_explore))); test.waitForIdleSync(); search(activity);
            NativeEngine.Result speculative=generateUi(activity);
            report.put("uiBaseline",uiResult(baseline)).put("uiSpeculative",uiResult(speculative)).put("textParity",baseline.text().equals(speculative.text())); write();
            require(speculative.verifyPasses()>0,"UI option did not activate speculative verification");
            require(baseline.text().equals(speculative.text()),"Speculation changed this UI response");
            screenshot("speculation-answer.png");
        } finally {
            RuntimeSettings.setSpeculation(test.getTargetContext(),ModelStore.BONSAI4,previous);
            test.runOnMainSync(activity::finish); test.waitForIdleSync();
        }
    }
    private void search(MainActivity activity) throws Exception {
        test.runOnMainSync(()->{ ((EditText)activity.findViewById(MainActivity.QUERY_ID)).setText("What is the difference between kW and kWh?"); activity.findViewById(MainActivity.SEARCH_ID).performClick(); });
        for(int i=0;i<100 && !activity.searchDone;i++) SystemClock.sleep(100); test.waitForIdleSync();
    }
    private NativeEngine.Result generateUi(MainActivity activity) throws Exception {
        test.runOnMainSync(()->activity.findViewById(MainActivity.GENERATE_ID).performClick());
        for(int i=0;i<1300 && !activity.answerDone;i++) SystemClock.sleep(100);
        require(activity.lastAnswer!=null && activity.lastAnswer.reason()==0,"UI generation did not finish"); test.waitForIdleSync(); return activity.lastAnswer;
    }
    private JSONObject uiResult(NativeEngine.Result r) throws Exception {
        return new JSONObject().put("text",r.text()).put("tokens",r.tokens()).put("firstTokenMs",r.firstTokenMs()).put("decodeMs",r.decodeMs()).put("totalMs",r.totalMs())
            .put("cachedTokens",r.cachedTokens()).put("drafted",r.drafted()).put("accepted",r.accepted()).put("verifyPasses",r.verifyPasses()).put("disabledByCost",r.speculationDisabled());
    }
    private void clickButton(MainActivity activity,String name) {
        ArrayList<View> found=new ArrayList<>(); activity.getWindow().getDecorView().findViewsWithText(found,name,View.FIND_VIEWS_WITH_TEXT);
        for(View v:found) if(v instanceof Button b && b.getText().toString().equals(name)) { v.performClick(); return; }
        throw new AssertionError("Button not found: "+name);
    }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(300); Bitmap bitmap=test.getUiAutomation().takeScreenshot(); if(bitmap==null) throw new IllegalStateException("Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),"evidence/"+name))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);} bitmap.recycle();
    }
    private long median(List<Long> x) { var v=new ArrayList<>(x); v.sort(Long::compare); return v.get(v.size()/2); }
    private void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private void status(String text) { Bundle b=new Bundle(); b.putString("stream","\n"+text+"\n"); test.sendStatus(0,b); }
    private void write() throws Exception {
        File dir=new File(test.getTargetContext().getFilesDir(),"evidence"); if(!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create evidence directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,"speculation-"+phase+".json"))) {out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    }
}
