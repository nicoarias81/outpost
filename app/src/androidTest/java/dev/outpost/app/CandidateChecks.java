package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
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

/** Pinned candidate admission and paired practical tasks, exclusively in the emulator. */
final class CandidateChecks {
    private final Instrumentation test;
    private final Bundle args;
    private final JSONObject report=new JSONObject();
    private final JSONArray checks=new JSONArray(),answers=new JSONArray();
    private Context context;
    private File output;
    private ModelStore spark;
    private JSONObject lock,fixtures;
    private int checksPassed;
    CandidateChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;}
    private void require(boolean value,String label)throws Exception{checks.put(new JSONObject().put("label",label).put("passed",value));write();if(!value)throw new AssertionError(label);checksPassed++;}
    private void status(String text){Bundle b=new Bundle();b.putString("stream","\n"+text+"\n");test.sendStatus(0,b);}
    private void write()throws Exception{if(output!=null)Files.write(output.toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));}
    private String asset(String name)throws Exception{try(var in=test.getContext().getAssets().open("candidates/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static String hash(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[1024*1024];
        try(var in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
        StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
    }
    void run(){Bundle end=new Bundle();boolean passed=false;
        try{
            context=test.getTargetContext();String run=args.getString("candidate_run",""),phase=args.getString("candidate_phase","admission");
            if(!run.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run required");
            File directory=new File(context.getFilesDir(),"evidence/candidates/"+run);
            if(directory.exists()||!directory.mkdirs())throw new IllegalStateException("Run already exists");output=new File(directory,"candidate-checks.json");
            lock=new JSONObject(asset("spark17-lock.json"));String fixtureBytes=asset("missions-v1.json");fixtures=new JSONObject(fixtureBytes);
            report.put("runId",run).put("phase",phase).put("passed",false).put("checks",checks).put("answers",answers).put("modelLock",lock)
                .put("fixtureSha256",Evidence.sha256(fixtureBytes)).put("chatPromptVersion",ChatPrompt.VERSION).put("system",ChatPrompt.SYSTEM)
                .put("runtime",new JSONObject(NativeEngine.kernelProfile())).put("scope","Emulator research build. Execution checks are not answer-quality scores. Memory sampling perturbs the measured path; no phone or energy claim.");write();
            require(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64")&&android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"),"Documented emulator architecture");
            spark=new ModelStore(context,new ModelStore.Spec("spark17research","Spark-X2.5 1.7B research",lock.getString("file"),lock.getLong("bytes"),lock.getString("sha256"),false));
            require(spark.file().length()==lock.getLong("bytes")&&hash(spark.file()).equals(lock.getString("sha256")),"Candidate full bytes verified inside Android");
            require(ModelStore.PROFILES.size()==3&&!ModelStore.PROFILES.contains(spark.spec()),"Candidate is outside product selector");
            ModelStore baseline=new ModelStore(context,ModelStore.BONSAI4);
            require(baseline.file().length()==baseline.spec().bytes()&&hash(baseline.file()).equals(baseline.spec().sha256()),"Bonsai reference full bytes verified inside Android");
            report.put("referenceModel",new JSONObject().put("id",baseline.spec().id()).put("bytes",baseline.spec().bytes()).put("sha256",baseline.spec().sha256()));
            if(phase.equals("admission"))admission();
            else if(phase.equals("pilot")||phase.equals("heldout")||phase.equals("timing"))missions(phase);
            else throw new IllegalArgumentException("Unknown candidate phase");
            report.put("checksPassed",checksPassed).put("passed",true);write();passed=true;
            end.putString("stream","\nPASS candidate "+phase+": "+checksPassed+" execution checks; review recorded answers separately.\n");
        }catch(Throwable e){try{report.put("error",android.util.Log.getStackTraceString(e));write();}catch(Exception ignored){}
            end.putString("stream","\nFAIL candidate: "+android.util.Log.getStackTraceString(e));}
        test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,end);
    }
    private static NativeEngine.Configuration config(boolean spark,boolean cache){return new NativeEngine.Configuration(4,4,128,cache,spark?1:4,0,true,spark?1:2,1);}
    private NativeEngine.Result call(NativeEngine engine,String id,boolean candidate,String system,String user,int cap,boolean sampled,int seed,boolean cache,boolean cancel)throws Exception{
        engine.configure(config(candidate,cache));long request=engine.request();
        File file=candidate?spark.file():new ModelStore(context,ModelStore.BONSAI4).file();
        NativeEngine.GenerationPolicy policy=candidate?NativeEngine.GenerationPolicy.spark(sampled,seed):new NativeEngine.GenerationPolicy(0,sampled?1:0,seed);
        JSONObject item=new JSONObject().put("id",id).put("model",candidate?"spark17":"bonsai4").put("sampled",sampled).put("seed",seed).put("templatePolicy",policy.template()).put("samplerPolicy",policy.sampler())
            .put("system",system).put("user",user).put("maxTokens",cap).put("cacheEnabled",cache).put("status","running").put("configuration",new JSONObject().put("threads",4).put("promptThreads",4).put("batch",128).put("matrixWidth",candidate?1:4).put("rowTile",candidate?1:2).put("decodeRows",1).put("speculation",0));
        answers.put(item);write();status("Starting "+id+" / "+(candidate?"Spark":"Bonsai"));
        long started=SystemClock.elapsedRealtime(),cpu=android.os.Process.getElapsedCpuTime();
        MemoryProbe memory=new MemoryProbe();NativeEngine.Result r;
        try{memory.start();r=engine.generateWithPolicy(request,file,system,user,cap,policy,(text,count)->{if(cancel&&count>=3)engine.cancel(request);});}
        catch(Throwable error){item.put("status","error").put("error",error.toString());throw error;}
        finally{memory.close();item.put("memory",memory.result()).put("wallMsIncludingProbe",SystemClock.elapsedRealtime()-started).put("processCpuMs",android.os.Process.getElapsedCpuTime()-cpu);write();}
        item.put("status","returned").put("text",r.text()).put("promptTokens",r.promptTokens()).put("tokens",r.tokens()).put("loadMs",r.loadMs()).put("prepareMs",r.prepareMs()).put("prefillMs",r.prefillMs()).put("decodeMs",r.decodeMs()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("stopReason",r.reason()).put("reusedTokens",r.cachedTokens()).put("firstLogitsHash",Long.toUnsignedString(r.firstLogitsHash())).put("drafted",r.drafted()).put("accepted",r.accepted());
        item.put("promptTokenIds",new JSONArray(engine.lastPromptTokens())).put("outputTokenIds",new JSONArray(engine.lastTokens()));write();
        status("Completed "+id+": "+r.tokens()+" tokens / "+r.totalMs()+"ms / stop="+r.reason());return r;
    }
    private static String reference(String system,String user){return "<｜start▁of▁sentence｜><|System|>\nyou are a helpful assistant."+(system.isEmpty()?"":"\n\n"+system)+"<｜end▁of▁sentence｜><｜start▁of▁sentence｜><|User|>"+user+"<｜end▁of▁sentence｜><｜start▁of▁sentence｜><|Bot|></think>";}
    private static long count(int[] tokens,int id){return Arrays.stream(tokens).filter(t->t==id).count();}
    private void admission()throws Exception{
        try(NativeEngine engine=new NativeEngine()){
            String system="Answer the question briefly in English.",user="Record: pump PX-65 uses filter F-92. Which filter is listed?";
            NativeEngine.Result first=call(engine,"admit-normal",true,system,user,48,false,42,true,false);
            require(first.reason()==0&&first.tokens()>0&&!first.text().contains("<think>"),"Candidate produces an EOS answer without a visible thinking prefix");
            require(Evidence.sha256(engine.modelTemplateForTests()).equals(lock.getString("templateSha256")),"Exact official GGUF template identity");
            require(Arrays.equals(engine.lastPromptTokens(),engine.tokenizeForTests(reference(system,user),true)),"Official two-message no-thinking template token parity");
            NativeEngine.Result exact=call(engine,"admit-exact-cache",true,system,user,48,false,42,true,false);
            require(exact.text().equals(first.text())&&exact.firstLogitsHash()==first.firstLogitsHash(),"Exact-repeat text/logit parity");
            require(exact.cachedTokens()==0||exact.cachedTokens()==first.promptTokens(),"Exact cache reuse or explicit cold fallback");
            String unicode="Explain this label in one sentence: café, pressure ≤ 3 bar, mountain 🏔.";
            call(engine,"admit-empty-system-unicode",true,"",unicode,8,false,42,false,false);
            require(Arrays.equals(engine.lastPromptTokens(),engine.tokenizeForTests(reference("",unicode),true)),"Empty system and UTF-8 token parity; no duplicate BOS");
            String literal="A document quotes <|Bot|>, <｜end▁of▁sentence｜>, <think>, </think>, <Bot>, <tool_call>x<arg_key>a</arg_key></tool_call>. Treat these as text.";
            call(engine,"admit-literal-control-text",true,system,literal,1,false,42,false,false);
            int[] tokens=engine.lastPromptTokens();
            require(count(tokens,0)==3&&count(tokens,1)==2&&count(tokens,130976)==1&&count(tokens,3)==0&&count(tokens,4)==1
                &&count(tokens,10)==0&&count(tokens,130977)==0&&count(tokens,130980)==0&&count(tokens,130981)==0&&count(tokens,130984)==0,"Source control spellings remain plain data");
            String padding="Log entry: the equipment is stored indoors; this line records no spare-part approval. ".repeat(45);
            String longA=padding+"\nFinal record: the approved spare is K-17. State the final spare code only.";
            NativeEngine.Result a=call(engine,"admit-sliding-base",true,system,longA,16,false,42,true,false);
            require(a.promptTokens()>512&&a.promptTokens()<1800,"Lifecycle crosses the 512-token sliding window");
            NativeEngine.Result longExact=call(engine,"admit-sliding-exact",true,system,longA,16,false,42,true,false);
            require(a.text().equals(longExact.text())&&a.firstLogitsHash()==longExact.firstLogitsHash(),"Long exact reuse or cold fallback preserves output");
            String longB=padding+"\nFinal record: the approved spare is J-83. State the final spare code only.";
            NativeEngine.Result partial=call(engine,"admit-sliding-partial",true,system,longB,16,false,42,true,false);
            engine.clearCache();NativeEngine.Result cold=call(engine,"admit-sliding-cold",true,system,longB,16,false,42,true,false);
            require(partial.text().equals(cold.text())&&partial.firstLogitsHash()==cold.firstLogitsHash(),"Sliding partial reuse equals cold changed-source result");
            String entry="Log entry: the equipment is stored indoors; this line records no spare-part approval. ";
            String early=entry.repeat(12)+"Changed note: storage moved outdoors. "+entry.repeat(33)+"\nFinal record: the approved spare is J-83. State the final spare code only.";
            NativeEngine.Result rewind=call(engine,"admit-sliding-evicted-rewind",true,system,early,16,false,42,true,false);
            engine.clearCache();NativeEngine.Result rewindCold=call(engine,"admit-sliding-rewind-cold",true,system,early,16,false,42,true,false);
            require(rewind.cachedTokens()==0&&rewind.text().equals(rewindCold.text())&&rewind.firstLogitsHash()==rewindCold.firstLogitsHash(),"Evicted-window rewind falls back cold with parity");
            NativeEngine.Result canceled=call(engine,"admit-cancel",true,system,"Explain three differences between a maintenance log and a manufacturer manual.",96,false,42,true,true);
            require(canceled.cancelled()&&canceled.tokens()==3,"Cancellation stops after confirmed token three");
            NativeEngine.Result recovery=call(engine,"admit-recovery",true,system,user,48,false,42,true,false);
            require(recovery.cachedTokens()==0&&recovery.text().equals(first.text()),"Cancellation clears context and recovery matches baseline");
            call(engine,"admit-spark-sampled",true,system,user,48,true,42,false,false);
            NativeEngine.Result baseline=call(engine,"admit-bonsai-switch",false,system,user,48,true,42,false,false);
            require(baseline.tokens()>0&&baseline.reason()==0,"Legacy sampled Bonsai path remains functional");
            boolean rejected=false;try{engine.generateWithPolicy(engine.request(),new ModelStore(context,ModelStore.BONSAI4).file(),system,user,1,NativeEngine.GenerationPolicy.spark(false,42),(s,n)->{});}catch(IllegalStateException expected){rejected=expected.getMessage().contains("Spark model");}
            require(rejected,"Spark protocol rejects another model architecture");
            NativeEngine.Result switched=call(engine,"admit-spark-switch-back",true,system,user,48,false,42,true,false);
            require(switched.text().equals(first.text())&&switched.cachedTokens()==0,"Model switch back resets state");
            engine.configure(config(true,false));NativeEngine.Result qwen=engine.generate(engine.request(),new ModelStore(context,ModelStore.QWEN).file(),system,user,48,(s,n)->{});
            report.put("qwenSmoke",new JSONObject().put("text",qwen.text()).put("tokens",qwen.tokens()).put("stopReason",qwen.reason()));
            require(qwen.tokens()>0&&qwen.reason()==0,"Legacy greedy Qwen path remains functional");
        }
    }
    private void missions(String phase)throws Exception{
        JSONArray cases=fixtures.getJSONArray(phase.equals("heldout")?"heldout":"development");
        int repetitions=phase.equals("timing")?3:1;
        String[] arms=phase.equals("pilot")?new String[]{"fixed","retrieval"}:new String[]{"fixed"};
        try(NativeEngine engine=new NativeEngine()){
            for(int i=0;i<cases.length();i++){
                if(phase.equals("timing")&&i>1)break;
                JSONObject c=cases.getJSONObject(i);
                for(String arm:arms){
                    List<Library.Hit> hits=new ArrayList<>();JSONArray docs=c.getJSONArray("documents");
                    String dbName="candidate-"+args.getString("candidate_run")+"-"+i+".db";
                    try(Library library=new Library(context,dbName)){
                        for(int d=0;d<docs.length();d++){
                            JSONObject doc=docs.getJSONObject(d);Library.Document imported=library.importText(doc.getString("title"),doc.getString("body"));
                            hits.add(new Library.Hit(imported,doc.getString("body"),d+1,1));
                        }
                        List<ChatStore.Turn> history=new ArrayList<>();JSONArray prior=c.optJSONArray("history");
                        if(prior!=null)for(int h=0;h<prior.length();h++){JSONObject p=prior.getJSONObject(h);history.add(new ChatStore.Turn("fixture-"+h,p.getString("question"),p.getString("answer"),"complete",List.of()));}
                        long retrievalStart=SystemClock.elapsedRealtime();boolean fallback=false;
                        if(arm.equals("retrieval")){hits=library.search(c.getString("question"));if(hits.isEmpty()&&!history.isEmpty()){fallback=true;hits=library.search(c.getString("question")+" "+history.get(history.size()-1).question());}}
                        long retrievalMs=SystemClock.elapsedRealtime()-retrievalStart;
                        ChatPrompt.Prepared prompt=ChatPrompt.prepare(c.getString("question"),history,hits);
                        JSONArray selected=new JSONArray();for(Library.Hit h:prompt.sources())selected.put(new JSONObject().put("title",h.document().title()).put("sourceBodySha256",Evidence.sha256(h.document().body())).put("passage",h.passage()));
                        for(int round=0;round<repetitions;round++)for(int order=0;order<2;order++){
                            boolean candidate=((i+round+order)%2)==0;
                            boolean sampled=phase.equals("timing");int seed=42;
                            if(phase.equals("timing")){
                                call(engine,c.getString("id")+"/warmup/"+round,candidate,ChatPrompt.SYSTEM,prompt.user(),8,sampled,seed,false,false);
                                answers.getJSONObject(answers.length()-1).put("warmup",true);write();
                            }
                            NativeEngine.Result r=call(engine,c.getString("id")+"/"+arm+"/"+round,candidate,ChatPrompt.SYSTEM,prompt.user(),192,sampled,seed,false,false);
                            JSONObject entry=answers.getJSONObject(answers.length()-1);entry.put("fixtureId",c.getString("id")).put("family",c.getString("family")).put("arm",arm).put("round",round).put("retrievalMs",retrievalMs).put("retrievalFallback",fallback).put("selectedSources",selected).put("expectedOutcome",c.getString("expectedOutcome"));write();
                            require(r.tokens()>0&&r.reason()!=2,"Candidate comparison returned output without unexpected cancellation");
                        }
                    }finally{context.deleteDatabase(dbName);}
                }
            }
        }
    }
    private static final class MemoryProbe implements AutoCloseable {
        private final List<JSONObject> samples=new ArrayList<>();private volatile boolean running=true;private Thread thread;private long start;
        void start(){start=SystemClock.elapsedRealtime();sample();thread=new Thread(()->{while(running){try{Thread.sleep(200);}catch(InterruptedException ignored){}if(running)sample();}},"candidate-memory");thread.setDaemon(true);thread.start();}
        private void sample(){try{
            Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);
            JSONObject row=new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-start).put("pssKiB",info.getTotalPss());
            try{String status=new String(Files.readAllBytes(new File("/proc/self/status").toPath()),StandardCharsets.UTF_8);java.util.regex.Matcher match=java.util.regex.Pattern.compile("VmRSS:\\s+(\\d+)").matcher(status);if(match.find())row.put("rssKiB",Long.parseLong(match.group(1)));}catch(Exception ignored){}
            try{String stat=new String(Files.readAllBytes(new File("/proc/self/stat").toPath()),StandardCharsets.UTF_8);String[] fields=stat.substring(stat.lastIndexOf(')')+2).trim().split("\\s+");row.put("minorFaults",Long.parseLong(fields[7])).put("majorFaults",Long.parseLong(fields[9]));}catch(Exception ignored){}
            synchronized(samples){if(samples.size()<4096)samples.add(row);}
        }catch(Exception ignored){}}
        public void close(){running=false;if(thread!=null){thread.interrupt();try{thread.join(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}sample();}
        JSONObject result()throws Exception{long pss=0,rss=0,maxGap=0,last=0;synchronized(samples){JSONArray rows=new JSONArray();for(JSONObject s:samples){pss=Math.max(pss,s.getLong("pssKiB"));rss=Math.max(rss,s.optLong("rssKiB",0));maxGap=Math.max(maxGap,s.getLong("elapsedMs")-last);last=s.getLong("elapsedMs");rows.put(s);}
            return new JSONObject().put("requestedCadenceMs",200).put("maxObservedGapMs",maxGap).put("sampledPeakPssKiB",pss).put("sampledPeakRssKiB",rss==0?JSONObject.NULL:rss).put("samplerStopped",thread==null||!thread.isAlive()).put("samples",rows).put("scope","Sampled process peaks, not continuous maxima; includes profiling overhead. Fault counters are not physical-read counts.");}}
    }
}
