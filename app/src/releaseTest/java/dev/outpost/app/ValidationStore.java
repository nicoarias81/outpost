package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opaque QA-store isolation for a complete acceptance run. Model files move with their verification markers. */
final class ValidationStore {
    static final List<String> PATHS=List.of("databases","shared_prefs","files/documents","files/release-acceptance-owned","files/release-retained.json","files/models");
    static String digest(File f)throws Exception{
        if(!f.exists())return "absent";
        if(f.isFile())return DemoChecks.hash(f);
        StringBuilder text=new StringBuilder();try(var paths=Files.walk(f.toPath())){
            for(var p:paths.sorted().toList()){if(Files.isSymbolicLink(p))throw new IllegalStateException("Symlink in QA store");if(Files.isRegularFile(p))text.append(f.toPath().relativize(p)).append('=').append(DemoChecks.hash(p.toFile())).append('\n');}
        }
        return DemoChecks.hex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static void run(Instrumentation test,Bundle args){
        boolean passed=false;JSONObject result=new JSONObject();Context c=test.getTargetContext();
        try{
            if(!c.getPackageName().equals("dev.outpost.app.releaseqa"))throw new IllegalStateException("Dedicated release QA required");
            String session=args.getString("validation_session","");if(!session.matches("pixel-full-[0-9TZ]+-[a-f0-9]{8}"))throw new IllegalArgumentException("Invalid validation identity");
            File journal=new File(c.getFilesDir(),"validation-store-active.json"),backup=new File(c.getFilesDir(),session);
            String phase=args.getString("phase","");JSONObject state;
            if(phase.equals("validation-isolate")){
                if(journal.exists()&&!DemoChecks.read(journal).getString("status").equals("restored"))throw new IllegalStateException("Restore previous validation store first");
                if(!new File(c.getFilesDir(),"release-acceptance-owned").isFile())throw new IllegalStateException("Existing QA ownership marker required");
                if(!backup.mkdir())throw new IllegalStateException("New private backup required");
                state=new JSONObject().put("session",session).put("status","preparing").put("entries",new JSONArray());DemoChecks.write(journal,state.toString(2));
                for(int i=0;i<PATHS.size();i++){
                    String path=PATHS.get(i);File original=new File(c.getDataDir(),path),saved=new File(backup,"original-"+i);
                    state.getJSONArray("entries").put(new JSONObject().put("path",path).put("index",i).put("before",digest(original)));DemoChecks.write(journal,state.toString(2));
                    if(original.exists())Files.move(original.toPath(),saved.toPath());
                }
                state.put("status","isolated");DemoChecks.write(journal,state.toString(2));
            }else if(phase.equals("validation-restore")){
                state=DemoChecks.read(journal);if(!state.getString("session").equals(session))throw new IllegalStateException("Restoration identity mismatch");
                JSONArray entries=state.getJSONArray("entries");for(int i=0;i<entries.length();i++){
                    JSONObject e=entries.getJSONObject(i);String path=e.getString("path");if(!PATHS.contains(path)||e.getInt("index")!=i)throw new IllegalStateException("Invalid recovery entry");
                    File target=new File(c.getDataDir(),path),original=new File(backup,"original-"+i),saved=new File(backup,"test-"+i);
                    if(original.exists()){
                        if(target.exists()){if(saved.exists())throw new IllegalStateException("Recovery archive exists");Files.move(target.toPath(),saved.toPath());}
                        Files.move(original.toPath(),target.toPath());
                    }else if(e.getString("before").equals("absent")&&target.exists()){
                        if(saved.exists())throw new IllegalStateException("Recovery archive exists");Files.move(target.toPath(),saved.toPath());
                    }
                    String after=digest(target);if(!after.equals(e.getString("before")))throw new IllegalStateException("QA restoration digest differs: "+path);e.put("after",after);
                }
                state.put("status","restored");DemoChecks.write(journal,state.toString(2));
            }else throw new IllegalArgumentException("Unknown isolation phase");
            result.put("state",state);passed=true;
        }catch(Throwable error){try{result.put("error",error.toString());}catch(Exception ignored){}}
        finally{try{result.put("passed",passed);}catch(Exception ignored){}Bundle out=new Bundle();out.putString("stream","\nVALIDATION_RESULT "+result+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
    }
}
