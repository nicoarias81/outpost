package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exercises verified model import in the new synthetic regression package. */
final class ModelBootstrapChecks {
    static void run(Instrumentation test){
        boolean passed=false;JSONObject report=new JSONObject();JSONArray models=new JSONArray();
        try{
            if(!TestTargets.ownedQa(test)||!TestTargets.admitted(test))throw new IllegalStateException("Owned physical regression QA required");
            for(var spec:ModelStore.PROFILES){
                Bundle progress=new Bundle();progress.putString("stream","\nImporting pinned "+spec.id()+"...\n");test.sendStatus(0,progress);
                ModelStore store=new ModelStore(test.getTargetContext(),spec);File source=new File(test.getTargetContext().getExternalFilesDir(null),spec.filename());
                long start=android.os.SystemClock.elapsedRealtime();
                try(var input=new FileInputStream(source)){store.install(input,n->{});}
                if(!store.ready())throw new AssertionError("Imported model is not ready");
                models.put(new JSONObject().put("id",spec.id()).put("bytes",store.file().length()).put("sha256",spec.sha256()).put("importMs",android.os.SystemClock.elapsedRealtime()-start));
            }
            ModelStore.select(test.getTargetContext(),ModelStore.BONSAI4);passed=true;
        }catch(Throwable error){try{report.put("error",error.toString());}catch(Exception ignored){}}
        try{report.put("passed",passed).put("models",models);}catch(Exception ignored){}
        Bundle out=new Bundle();out.putString("stream","\nBOOTSTRAP_RESULT "+report+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
}
