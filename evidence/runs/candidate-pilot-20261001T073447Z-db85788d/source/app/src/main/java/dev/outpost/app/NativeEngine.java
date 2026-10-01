package dev.outpost.app;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** Native inference lives in the Android process. No HTTP or host inference. */
final class NativeEngine implements AutoCloseable {
    static { System.loadLibrary("outpost_engine"); }
    static native double[] nativeKernelChecks();
    static native double[] nativeBatchChecks();
    static native String nativeRowsBenchmark();
    static native boolean kernelRowsUsed();
    static native void beginRowsProfile();
    static native String endRowsProfile();
    static native void setKernelBatchWidth(int width);
    static native boolean kernelBatchUsed();
    static native int[] nativeDispatchChecks();
    static native String kernelProfile();
    static native String kernelName();
    static native int[] nativeSpeculationChecks();
    static native boolean kernelWasUsed();
    static native void setKernelAutomatic(boolean automatic);
    static String hardwareSummary() {
        try {
            org.json.JSONObject profile=new org.json.JSONObject(kernelProfile());
            org.json.JSONArray candidates=profile.getJSONArray("candidates");
            StringBuilder available=new StringBuilder();
            for(int i=1;i<candidates.length();i++) {
                org.json.JSONObject c=candidates.getJSONObject(i);
                if(c.getBoolean("cpuCompatible")) {
                    if(available.length()>0) available.append(", ");
                    available.append(c.getString("name").replace("Q2_0 ",""));
                    if(!c.getBoolean("compiled")) available.append(" (kernel pending)");
                    else if(!c.getBoolean("enabled")) available.append(" (disabled)");
                }
            }
            return profile.getString("abi")+" · "+profile.getInt("onlineCpus")+" logical CPUs\nActive path: "+profile.getString("selected")
                +"\nCompatible capabilities: "+(available.length()>0 ? available : "reference path");
        } catch(org.json.JSONException e) { return "Could not read CPU profile. Active path: "+kernelName(); }
    }
    private volatile long handle = nativeCreate();
    record Configuration(int threads,int promptThreads,int batch,boolean cache,int matrixWidth,int speculativeDepth,boolean adaptive,int rowTile,int decodeRows) {
        Configuration(int threads,int promptThreads,int batch,boolean cache,int matrixWidth,int speculativeDepth,boolean adaptive,int legacyRows) {this(threads,promptThreads,batch,cache,matrixWidth,speculativeDepth,adaptive,legacyRows==2?2:1,legacyRows);}
        Configuration(int threads,int promptThreads,int batch,boolean cache,int matrixWidth,int speculativeDepth,boolean adaptive) {this(threads,promptThreads,batch,cache,matrixWidth,speculativeDepth,adaptive,1);}
        Configuration(int threads,int promptThreads,int batch,boolean cache,int matrixWidth) { this(threads,promptThreads,batch,cache,matrixWidth,0,true); }
        Configuration(int threads,int promptThreads,int batch,boolean cache) { this(threads,promptThreads,batch,cache,1); }
        Configuration {
            if(threads<1 || threads>8 || promptThreads<1 || promptThreads>8 || batch<16 || batch>512) throw new IllegalArgumentException("Invalid runtime configuration");
            if(matrixWidth!=1 && matrixWidth!=2 && matrixWidth!=4 && matrixWidth!=8) throw new IllegalArgumentException("Invalid matrix kernel width");
            if(speculativeDepth<0 || speculativeDepth>7) throw new IllegalArgumentException("Invalid speculation depth");
            if(rowTile!=1 && rowTile!=2) throw new IllegalArgumentException("Invalid prefill row tile");
            if(decodeRows!=1 && decodeRows!=2 && decodeRows!=4) throw new IllegalArgumentException("Invalid decode row tile");
        }
    }
    private volatile Configuration configuration=new Configuration(4,4,128,true);
    void configure(Configuration value) { configuration=value; }
    Configuration configuration() { return configuration; }
    void clearCache() { nativeClearCache(handle); }
    private int[] oracleForTests;
    void oracleForTests(int[] ids) { oracleForTests=ids==null ? null : ids.clone(); }
    int[] lastTokens() { return nativeLastTokens(handle); }
    int[] modelCapabilities() { return nativeModelCapabilities(handle); }
    double[] verificationAudit(int[] tokens,int width) { return nativeVerificationAudit(handle,tokens,width); }
    double[] rowsAudit(int[] tokens,int rows) { return nativeRowsAudit(handle,tokens,rows); }
    private final AtomicLong nextRequest = new AtomicLong(1);
    interface Listener { void onText(String text, int tokens); }
    record Result(String text, long promptTokens, long tokens, long loadMs, long firstTokenMs, long totalMs, long reason,
                  long prepareMs,long prefillMs,long decodeMs,long cachedTokens,long firstLogitsHash,
                  long drafted,long accepted,long verifyPasses,long rejectedWindows,long plainSteps,long verifyMicros,long draftMicros,boolean speculationDisabled) {
        boolean cancelled() { return reason == 2; }
    }
    long request() { return nextRequest.getAndIncrement(); }
    record Decision(double[] probabilities, long inputTokens, long loadMs, long totalMs, int reason) {
        int best() { int best = 0; for (int i=1; i<probabilities.length; i++) if (probabilities[i]>probabilities[best]) best=i; return best; }
    }
    Decision judge(long request, File model, File head, String evidence, String instruction, String[] choices) {
        byte[][] options = new byte[choices.length][];
        for (int i = 0; i < choices.length; i++) options[i] = utf8(choices[i]);
        double[] result = nativeJudge(handle, request, utf8(model.getAbsolutePath()), utf8(head.getAbsolutePath()), utf8(evidence), utf8(instruction), options);
        if (result == null || result.length != choices.length + 4) throw new IllegalStateException("Invalid review result.");
        return new Decision(java.util.Arrays.copyOf(result, choices.length), (long)result[choices.length], (long)result[choices.length+1], (long)result[choices.length+2], (int)result[choices.length+3]);
    }
    void cancel(long request) { if (request > 0) nativeCancel(handle, request); }
    Result generate(long request, File model, String system, String user, int maxTokens, Listener listener) {
        return generateWithSampling(request,model,system,user,maxTokens,false,listener);
    }
    Result generateWithSampling(long request,File model,String system,String user,int maxTokens,boolean sampled,Listener listener) {
        return generateWithPolicy(request,model,system,user,maxTokens,new GenerationPolicy(0,sampled?1:0,42),listener);
    }
    Result generateWithPolicy(long request,File model,String system,String user,int maxTokens,GenerationPolicy policy,Listener listener) {
        Callback callback = new Callback(listener);
        Configuration c=configuration;
        long[] result = nativeGenerate(handle,request,utf8(model.getAbsolutePath()),utf8(system),utf8(user),maxTokens,policy.template(),policy.sampler(),policy.seed(),c.threads(),c.promptThreads(),c.batch(),c.cache(),c.matrixWidth(),c.speculativeDepth(),c.adaptive(),c.rowTile(),c.decodeRows(),oracleForTests,callback);
        if (result == null || result.length != 19) throw new IllegalStateException("The engine did not return a valid result.");
        return new Result(callback.text,result[0],result[1],result[2],result[3],result[4],result[5],result[6],result[7],result[8],result[9],result[10],result[11],result[12],result[13],result[14],result[15],result[16],result[17],result[18]!=0);
    }
    /** Research protocols are explicit; the product's existing boolean API retains its behavior. */
    record GenerationPolicy(int template, int sampler, int seed) {
        static GenerationPolicy spark(boolean sampled,int seed) { return new GenerationPolicy(2,sampled?2:0,seed); }
        GenerationPolicy {
            if (template<0 || template>2 || sampler<0 || sampler>2 || seed<0
                || (template==0 && sampler==2) || (template>0 && sampler==1))
                throw new IllegalArgumentException("Invalid generation policy");
        }
    }
    int[] lastPromptTokens() { return nativeLastPromptTokens(handle); }
    int[] tokenizeForTests(String text,boolean special) { return nativeTokenizeForTests(handle,utf8(text),special); }
    String modelTemplateForTests() { return new String(nativeModelTemplateForTests(handle),StandardCharsets.UTF_8); }
    private static native int[] nativeLastPromptTokens(long handle);
    private static native int[] nativeTokenizeForTests(long handle,byte[] text,boolean special);
    private static native byte[] nativeModelTemplateForTests(long handle);
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    @Override public void close() { long id = handle; handle = 0; nativeClose(id); }
    static final class Callback {
        private final Listener listener;
        private String text = "";
        Callback(Listener listener) { this.listener = listener; }
        public void onBytes(byte[] utf8, int tokens) {
            text = new String(utf8, StandardCharsets.UTF_8);
            listener.onText(text, tokens);
        }
    }
    private static native long nativeCreate();
    private static native void nativeCancel(long handle, long request);
    private static native void nativeClose(long handle);
    private static native void nativeClearCache(long handle);
    private static native int[] nativeLastTokens(long handle);
    private static native int[] nativeModelCapabilities(long handle);
    private static native double[] nativeVerificationAudit(long handle,int[] tokens,int width);
    private static native double[] nativeRowsAudit(long handle,int[] tokens,int rows);
    private static native long[] nativeGenerate(long handle,long request,byte[] path,byte[] system,byte[] user,int maxTokens,int template,int sampler,int seed,int threads,int promptThreads,int batch,boolean cache,int matrixWidth,int specDepth,boolean adaptive,int rowTile,int decodeRows,int[] oracle,Callback callback);
    private static native double[] nativeJudge(long handle, long request, byte[] path, byte[] head, byte[] evidence, byte[] instruction, byte[][] choices);
}
