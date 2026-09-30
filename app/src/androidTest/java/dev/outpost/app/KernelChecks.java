package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Runs numeric kernel checks and a paired decoder check inside Android. */
final class KernelChecks {
    private final Instrumentation test;
    KernelChecks(Instrumentation test) { this.test=test; }
    void run(String phase) {
        Bundle output=new Bundle(); JSONObject report=new JSONObject();
        try(NativeEngine engine=new NativeEngine()) {
            engine.configure(new NativeEngine.Configuration(4,4,128,false));
            String version=test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0).versionName;
            if(phase.equals("batch")) {
                double[] r=NativeEngine.nativeBatchChecks();
                report.put("version",version).put("comparisons",(int)r[1]).put("bitMismatches",(int)r[2]).put("graphDispatchPassed",r[3]==1).put("maxAbsoluteDifference",r[4]);
                write("kernel-batch.json",report);
                if(r[0]!=1 || r[1]<7000 || r[2]!=0 || r[3]!=1) throw new AssertionError("Batched kernel parity failed");
                output.putString("stream","\nPASS batched kernel: "+(int)r[1]+" values, identical bits and real graph dispatch.\n");
            } else if(phase.equals("dispatch")) {
                int[] checks=NativeEngine.nativeDispatchChecks();
                report.put("version",version).put("policyChecks",checks[0]).put("failures",checks[1])
                    .put("runtimeProfile",new JSONObject(NativeEngine.kernelProfile()))
                    .put("limits","Synthetic x86/ARM capability and eligibility tests run inside the x86 emulator. They do not execute VNNI or ARM kernels.");
                NativeEngine.setKernelAutomatic(false);
                JSONObject fallback=new JSONObject(NativeEngine.kernelProfile());
                report.put("forcedFallback",fallback.getString("selected"));
                NativeEngine.setKernelAutomatic(true);
                report.put("restored",new JSONObject(NativeEngine.kernelProfile()).getString("selected"));
                write("kernel-dispatch.json",report);
                if(checks[0]<50 || checks[1]!=0 || !fallback.getString("selected").equals("Q2_0 reference")) throw new AssertionError("Dispatch policy failed");
                output.putString("stream","\nPASS dispatch: "+checks[0]+" capability/eligibility checks. No unsupported ISA executed.\n");
            } else if(phase.equals("numeric")) {
                double[] r=NativeEngine.nativeKernelChecks();
                report.put("version",version).put("backend",NativeEngine.kernelName()).put("avx2F16cAvailable",r[0]==1)
                    .put("vectorsCompared",(int)r[1]).put("bitMismatches",(int)r[2]).put("runtimeDispatchAndFallbackPassed",r[3]==1)
                    .put("guardPagesPassed",r[4]==1).put("maxAbsoluteDifference",r[5]).put("maxRelativeDifference",r[6])
                    .put("scalarNanosecondsPer4096",r[7]).put("avx2NanosecondsPer4096",r[8]).put("microbenchmarkSpeedup",r[7]/r[8])
                    .put("limits","Median of 3 warm-cache rounds, 20000 dots per round. Not end-to-end speed or a phone measurement.");
                write("kernel-numeric.json",report);
                if(r[0]!=1 || r[1]<16000 || r[2]!=0 || r[3]!=1 || r[4]!=1) throw new AssertionError("Kernel correctness/dispatch check failed");
                output.putString("stream","\nPASS kernel: "+(int)r[1]+" vectors, zero bit differences. Dot-product speedup "+r[7]/r[8]+"x.\n");
            } else if(phase.equals("decoder") || phase.equals("decoder4")) {
                ModelStore model=new ModelStore(test.getTargetContext(),phase.equals("decoder4") ? ModelStore.BONSAI4 : ModelStore.BONSAI17);
                if(!model.ready()) throw new AssertionError("Install "+model.spec().name()+" before parity check");
                String system="Answer in English in one short sentence, using only the source.";
                String user="SOURCE: Power is measured in watts. Energy is measured in watt-hours.\nQUESTION: What is energy measured in?";
                NativeEngine.setKernelAutomatic(false);
                NativeEngine.Result scalar=engine.generate(engine.request(),model.file(),system,user,48,(s,n)->{});
                boolean scalarSelected=!NativeEngine.kernelWasUsed();
                status("Scalar decoder: "+scalar.tokens()+" tokens in "+scalar.totalMs()+" ms");
                NativeEngine.setKernelAutomatic(true);
                NativeEngine.Result fast=engine.generate(engine.request(),model.file(),system,user,48,(s,n)->{});
                boolean fastSelected=NativeEngine.kernelWasUsed();
                status("AVX2 decoder: "+fast.tokens()+" tokens in "+fast.totalMs()+" ms");
                report.put("model",model.spec().name()).put("system",system).put("user",user)
                    .put("scalar",result(scalar)).put("avx2",result(fast)).put("scalarSelected",scalarSelected).put("avx2Selected",fastSelected)
                    .put("identicalText",scalar.text().equals(fast.text())).put("limits","One paired prompt, scalar first with model load, then AVX2 with warm model. Full-model regression, not a quality benchmark.");
                write("kernel-"+phase+".json",report);
                if(!scalarSelected || !fastSelected || scalar.tokens()==0 || scalar.reason()!=0 || fast.reason()!=0 || !scalar.text().equals(fast.text())) throw new AssertionError("Paired decoder mismatch");
                output.putString("stream","\nPASS decoder: identical text using scalar and AVX2; real model used both paths.\n");
            } else if(phase.equals("sampling")) {
                ModelStore model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);
                org.json.JSONArray probes=new org.json.JSONArray();
                try(Library library=TestLibrary.seeded(test.getTargetContext())) {
                    ResearchPrompt.Prepared prompt=ResearchPrompt.prepare("What is the difference between kW and kWh?",library.search("kW kWh"));
                    NativeEngine.Result sampled=engine.generateWithSampling(engine.request(),model.file(),ResearchPrompt.SYSTEM,prompt.user(),192,true,(s,n)->{});
                    probes.put(result(sampled).put("case","same-prompt-sampled")); status("Same prompt with sampling: "+sampled.tokens()+" tokens");
                    String context=EvidenceReview.evidence(prompt.sources());
                    String plain="CONTEXT:\n"+context+"\nQUESTION: What is the difference between kW and kWh?";
                    NativeEngine.Result simple=engine.generate(engine.request(),model.file(),"Answer in English using the context. Explain the answer in two sentences.",plain,128,(s,n)->{});
                    probes.put(result(simple).put("case","simple-prompt-greedy")); status("Simple prompt: "+simple.tokens()+" tokens");
                    NativeEngine.Result references=engine.generate(engine.request(),model.file(),"Answer in English using the source. Write a brief explanation and cite [1] at the end.","SOURCE [1]:\n"+context+"\nQUESTION: What is the difference between kW and kWh?",128,(s,n)->{});
                    probes.put(result(references).put("case","simple-citation-greedy")); status("Simple citation prompt: "+references.tokens()+" tokens");
                }
                report.put("model",model.spec().name()).put("probes",probes).put("sampledPolicy","top_k20; top_p0.8; temp0.7; seed42");
                write("kernel-sampling.json",report);
                output.putString("stream","\nPASS diagnostic completed; inspect actual answers, not just the completion status.\n");
            } else throw new IllegalArgumentException("Unknown kernel phase");
            test.finish(Activity.RESULT_OK,output);
        } catch(Throwable error) {
            try { report.put("error",android.util.Log.getStackTraceString(error)); write("kernel-failure.json",report); } catch(Exception ignored) {}
            output.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(error)); test.finish(Activity.RESULT_CANCELED,output);
        } finally { NativeEngine.setKernelAutomatic(true); }
    }
    private JSONObject result(NativeEngine.Result r) throws Exception { return new JSONObject().put("text",r.text()).put("tokens",r.tokens()).put("promptTokens",r.promptTokens()).put("loadMs",r.loadMs()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("stopReason",r.reason()); }
    private void status(String value) { Bundle b=new Bundle();b.putString("stream","\n"+value+"\n");test.sendStatus(0,b); }
    private void write(String name,JSONObject value) throws Exception { File dir=new File(test.getTargetContext().getFilesDir(),"evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))) {out.write(value.toString(2).getBytes(StandardCharsets.UTF_8));} }
}
