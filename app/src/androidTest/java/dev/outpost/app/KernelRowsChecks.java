package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Row-reuse experiments execute real numeric graphs and models only inside Android. */
final class KernelRowsChecks {
    private final Instrumentation test;private final String run,phase;private final boolean applyProfile;private File output;
    private final JSONObject report=new JSONObject();private final JSONArray results=new JSONArray();
    KernelRowsChecks(Instrumentation test,String run,String phase,boolean applyProfile){this.test=test;this.run=run;this.phase=phase;this.applyProfile=applyProfile;}
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void status(String message){Bundle b=new Bundle();b.putString("stream","\n"+message+"\n");test.sendStatus(0,b);}
    private void write()throws Exception{Files.write(output.toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));}
    void run(){boolean success=false;Bundle finish=new Bundle();
        try(NativeEngine engine=new NativeEngine()) {
            require(run.matches("[A-Za-z0-9_-]{8,80}"),"Unique run ID required");File dir=new File(test.getTargetContext().getFilesDir(),"evidence/rows/"+run);require(!dir.exists()&&dir.mkdirs(),"Run exists");output=new File(dir,"rows-checks.json");
            report.put("runId",run).put("phase",phase).put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("runtime",new JSONObject(NativeEngine.kernelProfile())).put("results",results).put("passed",false);
            require(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64"),"Only the documented emulator is measured");
            if(phase.equals("graphs")) {
                status("Running exact row arithmetic, strided/tail/worker graphs and actual-shape controls...");
                JSONObject graphs=new JSONObject(NativeEngine.nativeRowsBenchmark());report.put("graphs",graphs);write();
                require(graphs.getBoolean("supported")&&graphs.getInt("comparisons")>2000&&graphs.getInt("failures")==0&&graphs.getBoolean("guardPassed")&&graphs.getInt("benchmarkFailures")==0,"Row graph/reference/guard or dispatch failure");
                require(graphs.getJSONArray("benchmarks").length()==108,"All six shapes, cache conditions and rotated rounds are required");
                require(graphs.getJSONArray("prefillBenchmarks").length()==36,"Paired prefill shapes and cache conditions are required");
                // Configure the prefill-only policy through a pre-canceled request: no weights are loaded.
                engine.configure(new NativeEngine.Configuration(4,4,128,false,4,0,true,2,1));long canceled=engine.request();engine.cancel(canceled);
                NativeEngine.Result skipped=engine.generate(canceled,new ModelStore(test.getTargetContext(),ModelStore.BONSAI4).file(),"Unused","Unused",1,(s,n)->{});
                require(skipped.cancelled()&&skipped.tokens()==0,"Numeric setup unexpectedly generated tokens");
                double[] widths=NativeEngine.nativeBatchChecks();
                report.put("prefillWidthChecks",new JSONObject().put("comparisons",widths[1]).put("bitMismatches",widths[2]).put("dispatchPassed",widths[3]==1).put("maxAbsoluteDifference",widths[4]).put("scope","Existing grouped graph suite executed with prefillRows2/decodeRows1: widths1/2/4/8, odd column tails and1/2/4 workers; direct old-kernel controls included."));write();
                require(widths[0]==1&&widths[1]>7000&&widths[2]==0&&widths[3]==1&&widths[4]==0,"Prefill row reuse failed a grouped-width/tail control");
            }else if(phase.equals("model")){
                ModelStore model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);require(model.ready(),"Verified Bonsai4 must be installed");
                report.put("modelSha256",model.spec().sha256()).put("modelExecution",true).put("matrixWidth",4).put("cache",false).put("threads",4).put("batch",128).put("scope","Three rotated rounds per case. Warm weights, rebuilt context per request, fixed sampled32-token budget. Model output parity and timing controls, not field-answer quality or phone performance.");
                String system="Answer briefly in English using only the supplied fictitious record. Do not invent missing facts.";
                String[] labels={"short-record","long-record"};
                String[] users={"RECORD: Sample pump PX-65 uses filter F-92. Its inspection date is unknown. QUESTION: Which filter is listed and what information is missing?",
                    "RECORD: A fictitious field kit contains a pump PX-65, spare filter F-92, a paper manual revision3 and a spare seal K-17. "+
                    "The pump has an inspection label but the recorded inspection date is unknown. The operator reports reduced flow. The previous owner used filter F-10, which is not listed for PX-65. "+
                    "No live measurements, authorization to intervene, wiring diagram or equipment-isolation status are supplied. A different pump PX-70 uses filter F-11 and those parts must not be confused. "+
                    "The site notebook records a replacement hose H-9, 10metres of cable and a battery charger; those entries do not establish the source of the flow problem. "+
                    "QUESTION: Which filter is listed for PX-65, and what is unknown before deciding what to do?"};
                engine.configure(new NativeEngine.Configuration(4,4,128,false,4,0,true,1));engine.generateWithSampling(engine.request(),model.file(),system,users[0],8,true,(s,n)->{});
                for(int c=0;c<users.length;c++) {
                    String expected=null;
                    for(int round=0;round<3;round++)for(int order=0;order<3;order++) {
                        int rows=1<<((round+order)%3);engine.configure(new NativeEngine.Configuration(4,4,128,false,4,0,true,rows));NativeEngine.beginRowsProfile();
                        NativeEngine.Result r=engine.generateWithSampling(engine.request(),model.file(),system,users[c],32,true,(s,n)->{});JSONObject shapes=new JSONObject(NativeEngine.endRowsProfile());
                        JSONObject item=result(r).put("case",labels[c]).put("round",round).put("rowTile",rows).put("rowKernelUsed",NativeEngine.kernelRowsUsed()).put("shapes",shapes).put("system",system).put("user",users[c]);
                        results.put(item);write();status(labels[c]+" round"+round+" rows"+rows+": "+r.totalMs()+"ms total / "+r.decodeMs()+"ms decode, "+r.tokens()+" tokens");
                        require(r.tokens()>0&&r.reason()<2,"Model timing probe failed or exceeded the deadline");require(NativeEngine.kernelRowsUsed()==(rows>1),"Requested decode path was not used");
                        if(expected==null)expected=r.text();require(expected.equals(r.text()),"Row candidate changed model text");
                    }
                }
                JSONArray medians=new JSONArray();double[] totals=new double[3],decodes=new double[3];boolean[] noRegression={true,true,true};
                for(String label:labels){double baseline=median(label,1,"totalMs");for(int index=0;index<3;index++){int rows=1<<index;double total=median(label,rows,"totalMs"),decode=median(label,rows,"decodeMs");totals[index]+=total;decodes[index]+=decode;noRegression[index]&=total<=baseline*1.05;medians.put(new JSONObject().put("case",label).put("rowTile",rows).put("totalMs",total).put("decodeMs",decode).put("totalSpeedup",baseline/total));}}
                int selected=1;double fastest=Math.min(totals[0],Math.min(totals[1],totals[2]));
                for(int index=1;index<3;index++)if(noRegression[index]&&totals[0]/totals[index]>1.05&&decodes[0]/decodes[index]>1.05&&totals[index]<=fastest*1.05){selected=1<<index;break;}
                report.put("medians",medians).put("selectedCandidateRows",selected).put("selectionRule","Narrowest candidate within5% of fastest, >1.05x total and decode aggregate speedup, no case >5% slower; experimental, not automatic product adoption.");
                // Inspect all vocabulary logits under identical teacher-forced continuation positions.
                engine.configure(new NativeEngine.Configuration(4,4,128,true,4,0,true,1));NativeEngine.Result seed=engine.generateWithSampling(engine.request(),model.file(),system,users[1],24,true,(s,n)->{});
                int[] tokens=engine.lastTokens();tokens=Arrays.copyOf(tokens,Math.min(16,tokens.length));require(tokens.length>0,"No audit continuation");JSONArray audits=new JSONArray();
                for(int rows:new int[]{2,4}){double[] audit=engine.rowsAudit(tokens,rows);audits.put(new JSONObject().put("rows",rows).put("positions",audit[0]).put("maxAbsoluteLogitDifference",audit[1]).put("bitIdenticalPositions",audit[2]).put("rowKernelUsed",audit[3]==1));report.put("logitAudits",audits);write();require(audit[0]==audit[2]&&audit[1]==0&&audit[3]==1,"Teacher-forced logits changed");}
                report.put("auditSeed",result(seed));
            }else if(phase.equals("confirm")){confirm(engine);}else throw new IllegalArgumentException("Unknown row phase");
            success=true;report.put("passed",true);write();finish.putString("stream","\nPASS rows "+phase+". Inspect paired results and selection before adoption.\n");
        }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));write();}catch(Exception ignored){}finish.putString("stream","\nFAIL rows: "+android.util.Log.getStackTraceString(e));}
        finally{try{NativeEngine.endRowsProfile();}catch(Exception ignored){}NativeEngine.setKernelAutomatic(true);}
        test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,finish);
    }
    private double median(String label,int rows,String key)throws Exception{List<Long> values=new ArrayList<>();for(int i=0;i<results.length();i++){JSONObject r=results.getJSONObject(i);if(r.getString("case").equals(label)&&r.getInt("rowTile")==rows)values.add(r.getLong(key));}values.sort(Long::compare);return values.get(values.size()/2);}
    private void confirm(NativeEngine engine)throws Exception {
        ModelStore model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);require(model.ready(),"Verified Bonsai4 must be installed");
        String system="Answer in English in three concise sentences using only the fictitious record. Preserve identifiers and dates. Distinguish known details from missing information.";
        String[] labels={"confirmation-reservation","confirmation-manual"};
        String[] users={"FICTITIOUS RECORD: A saved coach reservation is for passenger Alex, service C-42 on October 12 at 09:30 from West Terminal, gate 6. Booking reference R-817. A separate outdated draft lists gate 3; the confirmed ticket explicitly replaces that draft. Arrival time and live service status are not provided. QUESTION: Summarize the confirmed travel details and what remains unknown.",
            "FICTITIOUS RECORD: Workshop note for compressor ZX-81, manual revision 7. The listed replacement seal is S-204, not the S-120 used by ZX-18. The owner reports a pressure reading of 4 bar, but no measurement time is recorded. No fault diagnosis, inspection date or authorization to work on the equipment is supplied. The note lists adapter A-33 as compatible only with ZX-18. QUESTION: What parts and reading apply to ZX-81, and what important information is missing?"};
        report.put("modelSha256",model.spec().sha256()).put("modelExecution",true).put("matrixWidth",4).put("threads",4).put("batch",128).put("cache",false).put("decodeRows",1)
            .put("selectionRule","Phase-separated candidate: two prefill rows, retained one-row decode. Three alternating pairs, the 192-token product budget and normal EOS. Require exact output/first-logit parity, >1.05x aggregate total speedup and no individual case total >5% slower.")
            .put("scope","Different from the initial tuning prompts but reused after inspecting the combined-policy regression; these are development controls, not a held-out/blinded quality study. Fixed sampler, emulator only, not a comparison against the width1 conservative default.");
        engine.configure(new NativeEngine.Configuration(4,4,128,false,4,0,true,1));engine.generateWithSampling(engine.request(),model.file(),system,users[0],8,true,(s,n)->{});
        for(int c=0;c<users.length;c++) {
            String expected=null;long firstHash=0;
            for(int round=0;round<3;round++)for(int order=0;order<2;order++) {
                int rows=1+((round+order)%2);engine.configure(new NativeEngine.Configuration(4,4,128,false,4,0,true,rows,1));
                NativeEngine.Result r=engine.generateWithSampling(engine.request(),model.file(),system,users[c],192,true,(s,n)->{});
                results.put(result(r).put("case",labels[c]).put("round",round).put("rowTile",rows).put("decodeRows",1).put("system",system).put("user",users[c]).put("rowKernelUsed",NativeEngine.kernelRowsUsed()));write();
                status(labels[c]+" round "+round+" rows "+rows+": "+r.totalMs()+" ms / prefill "+r.prefillMs()+" / decode "+r.decodeMs()+" / "+r.tokens()+" tokens");
                require(r.tokens()>0&&r.reason()==0,"Confirmation answer must finish normally within its budget");require(NativeEngine.kernelRowsUsed()==(rows==2),"Confirmation path did not execute");
                if(expected==null){expected=r.text();firstHash=r.firstLogitsHash();}require(expected.equals(r.text())&&firstHash==r.firstLogitsHash(),"Confirmation output or prompt logits changed");
            }
        }
        double baseline=0,candidate=0;boolean noRegression=true;JSONArray medians=new JSONArray();
        for(String label:labels){double a=median(label,1,"totalMs"),b=median(label,2,"totalMs");baseline+=a;candidate+=b;noRegression&=b<=a*1.05;medians.put(new JSONObject().put("case",label).put("baselineMs",a).put("candidateMs",b).put("speedup",a/b).put("elapsedTimeReductionPercent",100*(1-b/a)));}
        boolean adopt=noRegression&&baseline/candidate>1.05;report.put("medians",medians).put("aggregateSpeedup",baseline/candidate).put("candidateMeetsGate",adopt).put("profileApplied",false);write();
        // Configuration changes must invalidate exact-prefix reuse even when arithmetic agrees.
        engine.configure(new NativeEngine.Configuration(4,4,128,true,4,0,true,2,1));NativeEngine.Result cold=engine.generateWithSampling(engine.request(),model.file(),system,users[0],16,true,(s,n)->{});
        NativeEngine.Result warm=engine.generateWithSampling(engine.request(),model.file(),system,users[0],16,true,(s,n)->{});
        require(warm.cachedTokens()==warm.promptTokens()&&warm.text().equals(cold.text()),"Candidate exact cache reuse failed");
        engine.configure(new NativeEngine.Configuration(4,4,128,true,4,0,true,1));NativeEngine.Result changed=engine.generateWithSampling(engine.request(),model.file(),system,users[0],16,true,(s,n)->{});
        require(changed.cachedTokens()==0&&changed.text().equals(cold.text()),"Row policy change did not invalidate cached computation");
        engine.configure(new NativeEngine.Configuration(4,4,128,true,4,0,true,2,1));long request=engine.request();NativeEngine.Result canceled=engine.generateWithSampling(request,model.file(),system,users[0],96,true,(s,n)->{if(n>=3)engine.cancel(request);});
        require(canceled.cancelled()&&canceled.tokens()<=4,"Candidate cancellation failed");NativeEngine.Result recovered=engine.generateWithSampling(engine.request(),model.file(),system,users[0],16,true,(s,n)->{});require(recovered.cachedTokens()==0&&recovered.text().equals(cold.text()),"Candidate cancellation recovery failed");
        ModelStore qwen=new ModelStore(test.getTargetContext(),ModelStore.QWEN);require(qwen.ready(),"Qwen required for model-switch fallback check");
        NativeEngine.Result other=engine.generate(engine.request(),qwen.file(),"Answer briefly.","What is a compass used for?",8,(s,n)->{});require(other.tokens()>0&&!NativeEngine.kernelRowsUsed(),"Non-Q2 model did not preserve fallback");
        NativeEngine.Result back=engine.generateWithSampling(engine.request(),model.file(),system,users[0],16,true,(s,n)->{});require(back.cachedTokens()==0&&back.text().equals(cold.text())&&NativeEngine.kernelRowsUsed(),"Model switch did not restore the candidate correctly");
        report.put("lifecycle",new JSONObject().put("exactCacheTokens",warm.cachedTokens()).put("changedRowsCacheTokens",changed.cachedTokens()).put("canceledTokens",canceled.tokens()).put("recoveryCacheTokens",recovered.cachedTokens()).put("nonQ2Fallback",true).put("modelSwitchRestored",true));
        if(applyProfile&&adopt){RuntimeSettings.Profile profile=new RuntimeSettings.Profile(4,4,128,4,true,2,1);RuntimeSettings.save(test.getTargetContext(),model.spec(),profile);RuntimeSettings.Profile loaded=RuntimeSettings.load(test.getTargetContext(),model.spec());require(loaded.rowTile()==2&&loaded.decodeRows()==1&&loaded.measured()&&loaded.width()==4,"Measured profile did not round-trip");report.put("profileApplied",true).put("appliedProfile",new JSONObject().put("threads",4).put("promptThreads",4).put("batch",128).put("matrixWidth",4).put("rowTile",2).put("decodeRows",1).put("kernelIdentity","q2-row-v3-phase"));}
    }
    private JSONObject result(NativeEngine.Result r)throws Exception{return new JSONObject().put("text",r.text()).put("tokens",r.tokens()).put("promptTokens",r.promptTokens()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("loadMs",r.loadMs()).put("cachedTokens",r.cachedTokens()).put("firstLogitsHash",r.firstLogitsHash()).put("reason",r.reason());}
}
