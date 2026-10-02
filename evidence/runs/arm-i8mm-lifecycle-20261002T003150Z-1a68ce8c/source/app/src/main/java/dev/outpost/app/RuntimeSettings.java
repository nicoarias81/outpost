package dev.outpost.app;

import android.content.Context;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A measured profile is local to this build, CPU, OS and exact model. */
final class RuntimeSettings {
    record Profile(int threads,int promptThreads,int batch,int width,boolean measured,int rowTile,int decodeRows,int prefillChunk,int decodeChunk,int attentionThreads,int matrixKernel) {
        Profile(int threads,int promptThreads,int batch,int width,boolean measured,int rowTile,int decodeRows,int prefillChunk,int decodeChunk,int attentionThreads){this(threads,promptThreads,batch,width,measured,rowTile,decodeRows,prefillChunk,decodeChunk,attentionThreads,0);}
        Profile(int threads,int promptThreads,int batch,int width,boolean measured,int rowTile,int decodeRows,int prefillChunk,int decodeChunk){this(threads,promptThreads,batch,width,measured,rowTile,decodeRows,prefillChunk,decodeChunk,0);}
        Profile(int threads,int promptThreads,int batch,int width,boolean measured,int rowTile,int decodeRows){this(threads,promptThreads,batch,width,measured,rowTile,decodeRows,0,0);}
        Profile(int threads,int promptThreads,int batch,int width,boolean measured){this(threads,promptThreads,batch,width,measured,1,1);}
        Profile(int threads,int promptThreads,int batch,int width,boolean measured,int rowTile){this(threads,promptThreads,batch,width,measured,rowTile,1);}
        NativeEngine.Configuration configuration(boolean cache) { return new NativeEngine.Configuration(threads,promptThreads,batch,cache,width,0,true,rowTile,decodeRows,Build.SUPPORTED_ABIS[0].equals("arm64-v8a"),0,prefillChunk,decodeChunk,attentionThreads,matrixKernel); }
    }
    private static String key(Context context,ModelStore.Spec spec) throws Exception {
        org.json.JSONObject cpu=new org.json.JSONObject(NativeEngine.kernelProfile());
        String kernel=cpu.getString("abi").equals("arm64-v8a")?"q2-arm-i8mm-attn4-v1":"q2-row-v3-phase";
        String value=Build.FINGERPRINT+"/"+Build.VERSION.SDK_INT+"/"+cpu.getString("abi")+"/"+cpu.getInt("featureMask")+"/"+cpu.getInt("onlineCpus")
            +"/"+context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName+"/"+kernel+"/"+spec.sha256();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(); for(byte b:digest) out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return out.toString();
    }
    static Profile load(Context context,ModelStore.Spec spec) {
        try {
            String text=context.getSharedPreferences("runtime-calibration",0).getString(key(context,spec),"");
            if(!text.isEmpty()) {
                org.json.JSONObject v=new org.json.JSONObject(text);
                Profile p=new Profile(v.getInt("threads"),v.getInt("promptThreads"),v.getInt("batch"),v.getInt("width"),true,v.optInt("rowTile",1),v.optInt("decodeRows",1),v.optInt("prefillChunk",0),v.optInt("decodeChunk",0),v.optInt("attentionThreads",0),v.optInt("matrixKernel",0));
                p.configuration(true);
                if(p.width()==1 || p.width()==2 || p.width()==4 || p.width()==8) return p;
            }
        } catch(Exception ignored) { }
        // Confirmed Pixel/Bonsai preset; other hardware, OS builds and models remain conservative.
        if(spec==ModelStore.BONSAI4&&Build.FINGERPRINT.equals("google/blazer/blazer:17/CP3A.260905.009/16091614:user/release-keys"))try {
            org.json.JSONObject cpu=new org.json.JSONObject(NativeEngine.kernelProfile());
            if(cpu.getString("abi").equals("arm64-v8a")&&cpu.getInt("onlineCpus")==8&&cpu.getInt("featureMask")==7168
                &&cpu.getString("selected").equals("Q2_0 NEON DotProd")&&cpu.getJSONObject("matrixI8mm").getBoolean("compiled")&&cpu.getJSONObject("matrixI8mm").getBoolean("cpuCompatible"))return new Profile(6,6,128,8,true,1,4,32,0,4,1);
        }catch(org.json.JSONException ignored){}
        int threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));
        return new Profile(threads,threads,128,1,false);
    }
    static void save(Context context,ModelStore.Spec spec,Profile p) throws Exception {
        p.configuration(true);
        if(p.width()!=1 && p.width()!=2 && p.width()!=4 && p.width()!=8) throw new IllegalArgumentException("Invalid batch kernel width");
        String value=new org.json.JSONObject().put("threads",p.threads()).put("promptThreads",p.promptThreads()).put("batch",p.batch()).put("width",p.width()).put("rowTile",p.rowTile()).put("decodeRows",p.decodeRows()).put("prefillChunk",p.prefillChunk()).put("decodeChunk",p.decodeChunk()).put("attentionThreads",p.attentionThreads()).put("matrixKernel",p.matrixKernel()).toString();
        if(!context.getSharedPreferences("runtime-calibration",0).edit().putString(key(context,spec),value).commit()) throw new IllegalStateException("Cannot save calibration");
    }
    static boolean speculationEnabled(Context context,ModelStore.Spec spec) {
        if(spec!=ModelStore.BONSAI4) return false;
        try { return context.getSharedPreferences("speculative-context",0).getBoolean(key(context,spec),false); }
        catch(Exception ignored) { return false; }
    }
    static void setSpeculation(Context context,ModelStore.Spec spec,boolean enabled) {
        if(spec!=ModelStore.BONSAI4) throw new IllegalArgumentException("Speculation has only been evaluated for Bonsai 4B");
        try {
            if(!context.getSharedPreferences("speculative-context",0).edit().putBoolean(key(context,spec),enabled).commit()) throw new IllegalStateException("Cannot save speculation setting");
        } catch(Exception error) { throw new IllegalStateException("Cannot save speculation setting",error); }
    }
}
