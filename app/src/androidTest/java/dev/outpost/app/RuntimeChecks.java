package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Debug;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Performance controls use explicitly fictitious field records; no real-world procedure is prescribed. */
final class RuntimeChecks {
    private final Instrumentation test;
    private final JSONObject report=new JSONObject();
    private final JSONArray rows=new JSONArray();
    private ModelStore model;
    private String phase;
    private static final String SYSTEM="Answer in English using only the test record. Do not invent missing information. Cite [1].";
    private static final String RECORD="SOURCE [1] — FICTITIOUS TEST RECORD, NOT A REAL MANUAL.\n"
        +"Asset: irrigation pump M7. Nameplate identifier: BR-417. Record revision: 3. Record date: 2026-09-15. "
        +"The farmer has this downloaded record and a local notebook, without data coverage. "
        +"The tank contains 720 liters according to a user-provided reading; the recorded flow rate is 24 liters per minute. "
        +"The last recorded inspection was September 12. The listed replacement is filter F-28. "
        +"There is no repair procedure or confirmed safety status in this record. "
        +"These figures describe this test record and do not establish current conditions. "
        +"The previous record was replaced: an answer must use the current revision.\n";
    private static final String QUESTION="Explain what information I have about the pump and what is missing to decide on a repair, in three sentences.";
    RuntimeChecks(Instrumentation test) { this.test=test; }
    private String prompt(String record,String question) { return record+"\nQUESTION: "+question+"\nANSWER:"; }
    private record Trial(RuntimeSettings.Profile p,NativeEngine.Result r) { }
    void run(String phase) {
        this.phase=phase; Bundle output=new Bundle();
        try(NativeEngine engine=new NativeEngine()) {
            model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);
            if(!model.ready()) throw new AssertionError("Bonsai 4B must already be installed");
            report.put("version",test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0).versionName)
                .put("modelSha256",model.spec().sha256()).put("runtime",new JSONObject(NativeEngine.kernelProfile()))
                .put("system",SYSTEM).put("fictitiousRecord",RECORD).put("rows",rows)
                .put("limits","Android x86 emulator only. Synthetic field records, not validated real manuals. PSS after calls is not peak memory. Timings are local experiments, not phone predictions.");
            switch(phase) {
                case "calibrate" -> calibrate(engine);
                case "batch" -> batch(engine);
                case "cache" -> cache(engine);
                case "missions" -> missions(engine);
                default -> throw new IllegalArgumentException("Unknown runtime phase");
            }
            report.put("passed",true); write();
            output.putString("stream","\nPASS runtime "+phase+": "+rows.length()+" recorded calls.\n");
            test.finish(Activity.RESULT_OK,output);
        } catch(Throwable error) {
            try { report.put("passed",false).put("error",android.util.Log.getStackTraceString(error)); write(); } catch(Exception ignored) { }
            output.putString("stream","\nFAIL runtime: "+android.util.Log.getStackTraceString(error)); test.finish(Activity.RESULT_CANCELED,output);
        } finally { NativeEngine.setKernelBatchWidth(1); NativeEngine.setKernelAutomatic(true); }
    }
    private RuntimeSettings.Profile p(int threads,int prompt,int batch,int width) { return new RuntimeSettings.Profile(threads,prompt,batch,width,true); }
    private NativeEngine.Result call(NativeEngine engine,String label,RuntimeSettings.Profile profile,boolean cache,String user,int max,boolean sampled) throws Exception {
        engine.configure(profile.configuration(cache));
        NativeEngine.Result r=engine.generateWithSampling(engine.request(),model.file(),SYSTEM,user,max,sampled,(s,n)->{});
        add(label,profile,cache,user,r);
        require(r.reason()<2 && r.tokens()>0,"Generation did not finish within its token/time budget: "+label);
        return r;
    }
    private void add(String label,RuntimeSettings.Profile p,boolean cache,String user,NativeEngine.Result r) throws Exception {
        rows.put(new JSONObject().put("label",label).put("threads",p.threads()).put("promptThreads",p.promptThreads()).put("batch",p.batch()).put("width",p.width()).put("cache",cache)
            .put("prompt",user).put("text",r.text()).put("promptTokens",r.promptTokens()).put("tokens",r.tokens()).put("loadMs",r.loadMs()).put("prepareMs",r.prepareMs())
            .put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("cachedTokens",r.cachedTokens())
            .put("firstLogitsHash",Long.toUnsignedString(r.firstLogitsHash(),16)).put("reason",r.reason()).put("batchKernelUsed",NativeEngine.kernelBatchUsed()).put("pssKiBAfter",Debug.getPss()));
        write(); Bundle b=new Bundle(); b.putString("stream","\n"+label+": prefill "+r.prefillMs()+" ms, decode "+r.decodeMs()+" ms, reused "+r.cachedTokens()+", total "+r.totalMs()+" ms\n"); test.sendStatus(0,b);
    }
    private void calibrate(NativeEngine e) throws Exception {
        String user=prompt(RECORD,QUESTION);
        // The sweep isolates threads and batch by holding the grouped path at width 1. That is a
        // deliberate control, not a verdict on the width: preserve whatever the batch phase chose.
        int selectedWidth=RuntimeSettings.load(test.getTargetContext(),model.spec()).width();
        RuntimeSettings.Profile baseline=p(4,4,128,1);
        NativeEngine.Result reference=call(e,"warmup-excluded",baseline,false,user,32,false);
        List<Trial> decode=new ArrayList<>();
        for(int t:new int[]{4,2,3}) decode.add(new Trial(p(t,4,128,1),call(e,"decode-threads-"+t,p(t,4,128,1),false,user,32,false)));
        Trial d=decode.stream().filter(x->x.r.text().equals(reference.text())).min(Comparator.comparingLong(x->x.r.decodeMs())).orElseThrow();
        List<Trial> prefill=new ArrayList<>();
        for(int t:new int[]{4,2,3}) prefill.add(new Trial(p(d.p.threads(),t,128,1),call(e,"prefill-threads-"+t,p(d.p.threads(),t,128,1),false,user,32,false)));
        Trial f=prefill.stream().filter(x->x.r.text().equals(reference.text())).min(Comparator.comparingLong(x->x.r.prefillMs())).orElseThrow();
        List<Trial> batches=new ArrayList<>(); batches.add(f);
        for(int size:new int[]{32,64,256}) {
            RuntimeSettings.Profile c=p(d.p.threads(),f.p.promptThreads(),size,1);
            batches.add(new Trial(c,call(e,"batch-size-"+size,c,false,user,32,false)));
        }
        Trial best=batches.stream().filter(x->x.r.text().equals(reference.text())).min(Comparator.comparingLong(x->x.r.totalMs())).orElseThrow();
        List<Long> baseTimes=new ArrayList<>(),candidateTimes=new ArrayList<>(); boolean parity=true;
        for(int round=0;round<3;round++) {
            RuntimeSettings.Profile[] order=round%2==0 ? new RuntimeSettings.Profile[]{baseline,best.p} : new RuntimeSettings.Profile[]{best.p,baseline};
            for(int i=0;i<2;i++) {
                boolean isBase=round%2==0 ? i==0 : i==1;
                NativeEngine.Result r=call(e,"confirm-"+round+(isBase?"-baseline":"-candidate"),order[i],false,user,32,false);
                (isBase?baseTimes:candidateTimes).add(r.totalMs()); parity &= r.text().equals(reference.text());
            }
        }
        double gain=(double)median(baseTimes)/median(candidateTimes)-1;
        RuntimeSettings.Profile measured=parity && gain>0.05 ? best.p : baseline;
        // The stored width was measured by the batch phase at the threads and batch that were current
        // then, and this phase can change both. Confirm the width again at the combination about to be
        // saved, so the saved profile is a configuration that was actually measured as a whole.
        int width=selectedWidth;
        if(width>1) {
            RuntimeSettings.Profile wide=p(measured.threads(),measured.promptThreads(),measured.batch(),width);
            RuntimeSettings.Profile narrow=p(measured.threads(),measured.promptThreads(),measured.batch(),1);
            List<Long> wideTimes=new ArrayList<>(),narrowTimes=new ArrayList<>(); boolean widthParity=true;
            for(int round=0;round<3;round++) {
                RuntimeSettings.Profile[] order=round%2==0 ? new RuntimeSettings.Profile[]{narrow,wide} : new RuntimeSettings.Profile[]{wide,narrow};
                for(int i=0;i<2;i++) {
                    boolean isNarrow=round%2==0 ? i==0 : i==1;
                    NativeEngine.Result r=call(e,"width-confirm-"+round+(isNarrow?"-width-1":"-width-"+width),order[i],false,user,32,false);
                    (isNarrow?narrowTimes:wideTimes).add(r.totalMs()); widthParity &= reference.text().equals(r.text());
                }
            }
            double widthGain=(double)median(narrowTimes)/median(wideTimes)-1;
            if(!widthParity || widthGain<=0.05) width=1;
            report.put("widthConfirmationSpeedup",1+widthGain).put("widthConfirmationTimeReductionPercent",100*(1-(double)median(wideTimes)/median(narrowTimes))).put("widthConfirmationGain",widthGain).put("widthConfirmationParity",widthParity).put("widthAfterConfirmation",width);
        }
        RuntimeSettings.Profile chosen=p(measured.threads(),measured.promptThreads(),measured.batch(),width);
        RuntimeSettings.save(test.getTargetContext(),model.spec(),chosen);
        report.put("confirmationSpeedup",1+gain).put("confirmationTimeReductionPercent",100*(1-(double)median(candidateTimes)/median(baseTimes))).put("confirmationGain",gain).put("confirmationTextParity",parity).put("chosen",profile(chosen)).put("acceptanceRule","Identical text and baseline/candidate median time > 1.05, three alternating comparisons. Speedup is distinct from percent time saved.");
    }
    private void batch(NativeEngine e) throws Exception {
        RuntimeSettings.Profile base=RuntimeSettings.load(test.getTargetContext(),model.spec());
        String user=prompt(RECORD,QUESTION);
        call(e,"warmup-excluded",p(base.threads(),base.promptThreads(),base.batch(),1),false,user,32,false);
        int[] widths={1,2,4,8};
        List<List<Long>> totals=List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        List<List<Long>> prompts=List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        NativeEngine.Result reference=null; boolean parity=true;
        for(int round=0;round<3;round++) for(int i=0;i<widths.length;i++) {
            int index=(i+round)%widths.length,width=widths[index];
            RuntimeSettings.Profile c=p(base.threads(),base.promptThreads(),base.batch(),width);
            NativeEngine.Result r=call(e,"matrix-"+round+"-width-"+width,c,false,user,32,false);
            if(reference==null) reference=r;
            parity &= reference.text().equals(r.text()) && reference.firstLogitsHash()==r.firstLogitsHash();
            if(width>1) require(NativeEngine.kernelBatchUsed(),"Full model did not use batch kernel");
            totals.get(index).add(r.totalMs()); prompts.get(index).add(r.prefillMs());
        }
        long[] medianValues=new long[4]; for(int i=0;i<4;i++) medianValues[i]=median(totals.get(i));
        RuntimePolicy.Choice selection=RuntimePolicy.choose(medianValues,parity);
        int width=selection.selectedWidth();
        double gain=selection.speedup()-1;
        RuntimeSettings.Profile chosen=p(base.threads(),base.promptThreads(),base.batch(),width);
        RuntimeSettings.save(test.getTargetContext(),model.spec(),chosen);
        report.put("exactFirstLogitsAndTextParity",parity).put("medianTotalsMs",new JSONArray(List.of(median(totals.get(0)),median(totals.get(1)),median(totals.get(2)),median(totals.get(3)))))
            .put("medianPrefillMs",new JSONArray(List.of(median(prompts.get(0)),median(prompts.get(1)),median(prompts.get(2)),median(prompts.get(3)))))
            .put("gain",gain).put("gainDefinition","selectedSpeedup - 1; not elapsed-time reduction")
            .put("selection",selection.toJson()).put("chosen",profile(chosen));
        require(parity,"Batched model changed first-token logits or generated text");
    }
    private void cache(NativeEngine e) throws Exception {
        RuntimeSettings.Profile cfg=RuntimeSettings.load(test.getTargetContext(),model.spec());
        String a=prompt(RECORD,"What is the pump identifier? Answer in one sentence."),b=prompt(RECORD,"Which filter is listed as the replacement? Answer in one sentence.");
        NativeEngine.Result cold=call(e,"cold-A",cfg,true,a,48,true);
        NativeEngine.Result shared=call(e,"shared-prefix-B",cfg,true,b,48,true);
        require(cold.cachedTokens()==0 && shared.cachedTokens()>cold.promptTokens()/2,"Expected a substantial shared prefix");
        NativeEngine.Result independent=call(e,"uncached-B",cfg,false,b,48,true);
        require(shared.text().equals(independent.text()) && shared.firstLogitsHash()==independent.firstLogitsHash(),"Shared prefix changed logits or seeded answer");
        call(e,"cold-B",cfg,true,b,48,true);
        NativeEngine.Result repeated=call(e,"identical-B",cfg,true,b,48,true);
        require(repeated.cachedTokens()==repeated.promptTokens() && repeated.text().equals(independent.text()) && repeated.firstLogitsHash()==independent.firstLogitsHash(),"Identical-prompt cache failed");
        RuntimeSettings.Profile resized=p(cfg.threads(),cfg.promptThreads(),cfg.batch()==64 ? 128 : 64,cfg.width());
        require(call(e,"batch-configuration-changed",resized,true,b,48,true).cachedTokens()==0,"Batch configuration retained an incompatible context");
        call(e,"restore-batch-configuration",cfg,true,b,48,true);
        String changed=prompt(RECORD.replace("Record revision: 3","Record revision: 4").replace("filter F-28","filter F-91"),"Which filter is listed as the replacement? Answer in one sentence.");
        NativeEngine.Result corrected=call(e,"source-revision-changed",cfg,true,changed,48,true);
        NativeEngine.Result correctedCold=call(e,"source-revision-uncached",cfg,false,changed,48,true);
        require(corrected.cachedTokens()<shared.cachedTokens() && corrected.text().equals(correctedCold.text()) && corrected.firstLogitsHash()==correctedCold.firstLogitsHash() && corrected.text().contains("F-91"),"Source revision cache invalidation failed");
        call(e,"before-release",cfg,true,b,48,false); e.clearCache();
        require(call(e,"after-release",cfg,true,b,48,false).cachedTokens()==0,"Explicit release did not clear prefix");
        long request=e.request(); e.cancel(request);
        NativeEngine.Result before=e.generateWithSampling(request,model.file(),SYSTEM,b,48,false,(s,n)->{}); add("cancel-before-prefill",cfg,true,b,before);
        require(before.reason()==2 && before.tokens()==0,"Pre-cancel failed");
        require(call(e,"after-pre-cancel",cfg,true,b,48,false).cachedTokens()==0,"Cancelled request retained prefix");
        request=e.request(); final long active=request;
        NativeEngine.Result during=e.generateWithSampling(request,model.file(),SYSTEM,prompt(RECORD,QUESTION),64,false,(s,n)->{if(n==2)e.cancel(active);});
        add("cancel-during-output",cfg,true,prompt(RECORD,QUESTION),during);
        require(during.reason()==2 && during.tokens()==2,"Output cancellation failed");
        require(call(e,"after-output-cancel",cfg,true,b,48,false).cachedTokens()==0,"Output cancellation retained prefix");
        boolean oversized=false;
        try { e.generate(e.request(),model.file(),SYSTEM,"x ".repeat(3000),48,(s,n)->{}); } catch(IllegalStateException expected) { oversized=true; }
        require(oversized,"Oversized request should be rejected");
        require(call(e,"after-invalid-request",cfg,true,b,48,false).cachedTokens()==0,"Failed request retained prefix");
        ModelStore saved=model; model=new ModelStore(test.getTargetContext(),ModelStore.QWEN);
        require(call(e,"switch-to-qwen",cfg,true,a,32,false).cachedTokens()==0,"Model switch retained prefix"); model=saved;
        require(call(e,"switch-back-to-bonsai",cfg,true,b,48,false).cachedTokens()==0,"Switch back retained prefix");
        JudgeStore judge=new JudgeStore(test.getTargetContext());
        require(judge.ready(),"Installed Kev reviewer required for cache transition check");
        NativeEngine.Decision decision=e.judge(e.request(),judge.file(),judge.head(),"The replacement filter is F-28.",EvidenceReview.instruction("The replacement filter is F-28."),EvidenceReview.OPTIONS);
        require(decision.reason()==0,"Reviewer transition failed");
        report.put("reviewTransition",new JSONObject().put("reason",decision.reason()).put("inputTokens",decision.inputTokens()).put("totalMs",decision.totalMs()));
        require(call(e,"return-after-review",cfg,true,b,48,false).cachedTokens()==0,"Reviewer retained generator prefix");
        report.put("seededTextParity",true).put("invalidationsPassed",true).put("chosen",profile(cfg));
    }
    static String[][] fieldCases() {
        return new String[][]{
            {"traveler","Fictitious reservation: Trail Hostel, check-in from 16:00. Reservation code AB-314. Luggage storage at reception from 10:00. There is no information about restaurants.","I arrive at 11:00 without mobile data. What option do I have to leave my backpack?","10:00"},
            {"farmer",RECORD,"Which filter is listed as the replacement for this pump?","F-28"},
            {"field-engineer","Fictitious inspection record: tower T-82; document revision 5; last recorded inspection 2026-09-10. No authorization to intervene or current electrical state is recorded.","Can I infer from this record that intervention is authorized?",""},
            {"mountaineer","Fictitious itinerary: shelter A, 1200 m; pass B, 1450 m. The record has no weather forecast or current trail conditions.","What is the positive elevation difference from the shelter to the pass according to the record?","250"},
            {"driver","Fictitious test vehicle record: warning X17 refers to page 42 of the local manual. This record contains no diagnosis. The saved map covers only the northern region.","I have no signal and X17 appears. Which page of the manual should I consult?","42"}
        };
    }
    private void missions(NativeEngine e) throws Exception {
        RuntimeSettings.Profile cfg=RuntimeSettings.load(test.getTargetContext(),model.spec());
        String[][] cases=fieldCases();
        for(String[] c:cases) {
            String user=prompt("SOURCE [1] — FICTITIOUS TEST DATA: "+c[1],c[2]);
            NativeEngine.Result baseline=call(e,c[0]+"-baseline",p(4,4,128,1),false,user,96,true);
            NativeEngine.Result optimized=call(e,c[0]+"-optimized",cfg,false,user,96,true);
            require(baseline.text().equals(optimized.text()),"Mission text changed: "+c[0]);
            rows.getJSONObject(rows.length()-1).put("expectedMarker",c[3]).put("markerPresent",c[3].isEmpty() || optimized.text().contains(c[3]));
        }
        report.put("textParity",true).put("chosen",profile(cfg)).put("qualityLimits","Five synthetic mission-shaped controls. Markers and text parity do not establish real-world task success or safe field procedures.");
    }
    private JSONObject profile(RuntimeSettings.Profile p) throws Exception { return new JSONObject().put("threads",p.threads()).put("promptThreads",p.promptThreads()).put("batch",p.batch()).put("width",p.width()); }
    private long median(List<Long> values) { List<Long> sorted=new ArrayList<>(values); sorted.sort(Long::compare); return sorted.get(sorted.size()/2); }
    private void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private void write() throws Exception {
        File dir=new File(test.getTargetContext().getFilesDir(),"evidence"); if(!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create evidence directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,"runtime-"+phase+".json"))) {out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
    }
}
