package dev.outpost.app;

import android.content.Context;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A measured profile is local to this build, CPU, OS and exact model. */
final class RuntimeSettings {
    record Profile(int threads,int promptThreads,int batch,int width,boolean measured) {
        NativeEngine.Configuration configuration(boolean cache) { return new NativeEngine.Configuration(threads,promptThreads,batch,cache,width); }
    }
    private static String key(Context context,ModelStore.Spec spec) throws Exception {
        org.json.JSONObject cpu=new org.json.JSONObject(NativeEngine.kernelProfile());
        String value=Build.FINGERPRINT+"/"+Build.VERSION.SDK_INT+"/"+cpu.getString("abi")+"/"+cpu.getInt("featureMask")+"/"+cpu.getInt("onlineCpus")
            +"/"+context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName+"/q2-batch-v1/"+spec.sha256();
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder(); for(byte b:digest) out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return out.toString();
    }
    static Profile load(Context context,ModelStore.Spec spec) {
        try {
            String text=context.getSharedPreferences("runtime-calibration",0).getString(key(context,spec),"");
            if(!text.isEmpty()) {
                org.json.JSONObject v=new org.json.JSONObject(text);
                Profile p=new Profile(v.getInt("threads"),v.getInt("promptThreads"),v.getInt("batch"),v.getInt("width"),true);
                p.configuration(true);
                if(p.width()==1 || p.width()==2 || p.width()==4) return p;
            }
        } catch(Exception ignored) { }
        int threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()));
        return new Profile(threads,threads,128,1,false);
    }
    static void save(Context context,ModelStore.Spec spec,Profile p) throws Exception {
        p.configuration(true);
        if(p.width()!=1 && p.width()!=2 && p.width()!=4) throw new IllegalArgumentException("Invalid batch kernel width");
        String value=new org.json.JSONObject().put("threads",p.threads()).put("promptThreads",p.promptThreads()).put("batch",p.batch()).put("width",p.width()).toString();
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
