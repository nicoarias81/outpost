package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.EditText;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real app demonstration in an isolated QA store; no generated or substituted UI responses. */
final class DemoChecks {
    final Instrumentation test;final Bundle args;final Context context;final JSONArray checks=new JSONArray(),answers=new JSONArray();
    MainActivity activity;String session,phase;File output;JSONObject cases;
    DemoChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;context=test.getTargetContext();}
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}
    static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var in=new FileInputStream(file)){byte[] bytes=new byte[1024*1024];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return hex(digest.digest());}
    static void write(File file,String text)throws Exception{File tmp=new File(file.getParentFile(),file.getName()+".tmp");try(var out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    static JSONObject read(File file)throws Exception{return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    void check(boolean ok,String name)throws Exception{checks.put(new JSONObject().put("name",name).put("passed",ok));if(!ok)throw new AssertionError(name);}
    File stateFile(){return new File(context.getFilesDir(),"demo-active.json");}
    File relative(String path){return new File(context.getDataDir(),path);}
    String digest(File directory)throws Exception{
        if(!directory.exists())return "absent";
        try(var paths=Files.walk(directory.toPath())){
            List<java.nio.file.Path> all=paths.sorted().collect(java.util.stream.Collectors.toList());StringBuilder manifest=new StringBuilder();
            for(var p:all){if(Files.isSymbolicLink(p))throw new IllegalStateException("Symlink in QA store");if(Files.isRegularFile(p))manifest.append(directory.toPath().relativize(p)).append('=').append(hash(p.toFile())).append('\n');}
            return hex(MessageDigest.getInstance("SHA-256").digest(manifest.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
    void own()throws Exception{JSONObject state=read(stateFile());check(state.getString("session").equals(session)&&!state.getString("status").equals("restored"),"Active isolated demo store");}
    void prepare()throws Exception{
        check(new File(context.getFilesDir(),"release-acceptance-owned").isFile(),"Existing QA ownership marker");
        if(stateFile().exists())check(read(stateFile()).getString("status").equals("restored"),"Previous demo restoration complete");
        File backup=new File(context.getFilesDir(),session);check(backup.mkdir(),"Unique QA backup directory");
        JSONObject state=new JSONObject().put("session",session).put("status","preparing").put("entries",new JSONArray());write(stateFile(),state.toString(2));
        for(String path:List.of("databases","shared_prefs","files/documents")){
            File source=relative(path),target=new File(backup,"original-"+source.getName());
            JSONObject entry=new JSONObject().put("path",path).put("before",digest(source));state.getJSONArray("entries").put(entry);write(stateFile(),state.toString(2));
            if(source.exists())Files.move(source.toPath(),target.toPath());
        }
        state.put("status","isolated");write(stateFile(),state.toString(2));
        try(Library library=new Library(context)){
            check(library.documents().isEmpty(),"Demo library starts empty");
            JSONArray docs=cases.getJSONArray("documents");
            for(int i=0;i<docs.length();i++){
                JSONObject doc=docs.getJSONObject(i);File f=new File(context.getCacheDir(),doc.getString("file"));Files.write(f.toPath(),doc.getString("text").getBytes(StandardCharsets.UTF_8));
                var added=new DocumentImporter(context,library).importFile(Uri.fromFile(f),f.getName(),f.getName(),f.length(),new DocumentImporter.Cancellation());
                check(added.added(),"Imported demonstration source: "+f.getName());
            }
            File osm=new File(context.getCacheDir(),"Madrid places.osm");try(var in=test.getContext().getAssets().open("demo/madrid.osm")){Files.copy(in,osm.toPath(),StandardCopyOption.REPLACE_EXISTING);}check(hash(osm).equals(cases.getJSONObject("placeSource").getString("sha256")),"Public OSM snapshot identity");
            new DocumentImporter(context,library).importFile(Uri.fromFile(osm),osm.getName(),osm.getName(),osm.length(),new DocumentImporter.Cancellation());
            check(library.documents().size()==docs.length()+1,"All demonstration sources are present");
        }
        ModelStore.select(context,ModelStore.BONSAI4);check(new ModelStore(context).ready(),"Existing verified Bonsai 4B is ready");
        write(new File(output,"prepared.json"),state.toString(2));
    }
    void restore()throws Exception{
        JSONObject state=read(stateFile());check(state.getString("session").equals(session),"Restoration session identity");
        File backup=new File(context.getFilesDir(),session);JSONArray entries=state.getJSONArray("entries");
        for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.getJSONObject(i);String path=entry.getString("path");check(List.of("databases","shared_prefs","files/documents").contains(path),"Known restoration path");
            File target=relative(path),original=new File(backup,"original-"+target.getName()),saved=new File(backup,"demo-"+target.getName());
            if(original.exists()){
                if(target.exists()){check(!saved.exists(),"Archive destination unused");Files.move(target.toPath(),saved.toPath());}
                Files.move(original.toPath(),target.toPath());
            }else if(entry.getString("before").equals("absent")&&target.exists()){
                check(!saved.exists(),"Archive destination unused");Files.move(target.toPath(),saved.toPath());
            }
            entry.put("after",digest(target));check(entry.getString("before").equals(entry.getString("after")),"QA data restored: "+path);
        }
        state.put("status","restored");write(stateFile(),state.toString(2));write(new File(output,"restoration.json"),state.toString(2));
    }
    JSONObject result(String model,String id,NativeEngine.Result r)throws Exception{return new JSONObject().put("model",model).put("case",id).put("text",r.text()).put("promptTokens",r.promptTokens()).put("tokens",r.tokens()).put("reason",r.reason()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("firstLogitsHash",Long.toUnsignedString(r.firstLogitsHash()));}
    void addSummary()throws Exception{
        own();JSONArray docs=cases.getJSONArray("documents");JSONObject doc=docs.getJSONObject(docs.length()-1);
        check(doc.getString("file").equals("Farm readings note.txt"),"Known explicit companion source");
        File file=new File(context.getCacheDir(),doc.getString("file"));Files.write(file.toPath(),doc.getString("text").getBytes(StandardCharsets.UTF_8));
        try(Library library=new Library(context)){
            var result=new DocumentImporter(context,library).importFile(Uri.fromFile(file),file.getName(),file.getName(),file.length(),new DocumentImporter.Cancellation());
            check(result.added(),"Explicit text companion imported; original CSV kept");
        }
    }
    void compare()throws Exception{
        own();JSONObject pin=cases.getJSONObject("baseline");File tiny=new File(context.getExternalFilesDir(null),pin.getString("file"));
        check(tiny.length()==pin.getLong("bytes")&&hash(tiny).equals(pin.getString("sha256")),"TinyLlama file identity verified");
        File bonsai=new ModelStore(context,ModelStore.BONSAI4).file();check(hash(bonsai).equals(ModelStore.BONSAI4.sha256()),"Bonsai 4B file identity verified");
        JSONArray questions=cases.getJSONArray("cases");
        try(Library library=new Library(context);NativeEngine engine=new NativeEngine()){
            for(int i=Integer.parseInt(args.getString("case_start","0"));i<questions.length();i++){
                JSONObject q=questions.getJSONObject(i);var hits=library.search(q.getString("question"));var prompt=ChatPrompt.prepare(q.getString("question"),List.of(),hits);
                JSONArray evidence=new JSONArray();for(var hit:prompt.sources())evidence.put(new JSONObject().put("title",hit.document().title()).put("passage",hit.passage()).put("ordinal",hit.number()));
                for(int variant=0;variant<3;variant++){
                    boolean small=variant==0,sampled=variant==2;engine.clearCache();
                    engine.configure(small?new NativeEngine.Configuration(4,4,128,false):RuntimeSettings.load(context,ModelStore.BONSAI4).configuration(false));
                    var answer=engine.generateWithSampling(engine.request(),small?tiny:bonsai,ChatPrompt.SYSTEM,prompt.user(),192,sampled,(s,n)->{});
                    JSONObject record=result(small?pin.getString("name"):"Bonsai 4B",q.getString("id"),answer).put("sampler",sampled?"top-k20 top-p0.8 temperature0.7 seed42":"greedy").put("question",q.getString("question")).put("system",ChatPrompt.SYSTEM).put("user",prompt.user()).put("sources",evidence).put("expected",q.getString("expected"));
                    answers.put(record);write(new File(output,"answers-progress.json"),answers.toString(2));
                    check(answer.reason()<2&&!answer.text().isBlank(),"Model execution completed: "+q.getString("id")+" / "+variant);
                }
            }
        }
    }
    void waitFor(java.util.function.BooleanSupplier condition,long millis,String label)throws Exception{long end=SystemClock.uptimeMillis()+millis;while(!condition.getAsBoolean()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);test.waitForIdleSync();check(condition.getAsBoolean(),label);}
    MainActivity start()throws Exception{
        activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity app=activity;boolean[] ready={false};
        waitFor(()->{test.runOnMainSync(()->ready[0]=app.findViewById(MainActivity.QUERY_ID)!=null);return ready[0];},15000,"Chat ready");return app;
    }
    void capture(String name)throws Exception{var bitmap=test.getUiAutomation().takeScreenshot();check(bitmap!=null,"Real screenshot available");try(var out=new FileOutputStream(new File(output,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    static boolean scrollTop(android.view.View view){
        if(view instanceof android.widget.ScrollView scroll){scroll.fullScroll(android.view.View.FOCUS_UP);return true;}
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(scrollTop(group.getChildAt(i)))return true;
        return false;
    }
    void record()throws Exception{
        own();check(Settings.Global.getInt(context.getContentResolver(),"airplane_mode_on",0)==1,"Airplane mode on");
        check(Settings.Global.getInt(context.getContentResolver(),"wifi_on",1)==0,"Wi-Fi off");
        String[] permissions=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        check(permissions==null||permissions.length==0,"Demo app declares no permissions");
        int scene=Integer.parseInt(args.getString("scene","0"));
        String question=scene<cases.getJSONArray("cases").length()?cases.getJSONArray("cases").getJSONObject(scene).getString("question"):cases.getString("placeQuestion");
        try(ChatStore chat=new ChatStore(context)){chat.clear();}
        ModelStore.select(context,ModelStore.BONSAI4);MainActivity app=start();
        write(new File(output,"ready.json"),new JSONObject().put("question",question).put("scene",scene).toString());
        waitFor(()->new File(output,"go").isFile(),60000,"Host recording started");long start=SystemClock.elapsedRealtime();
        SystemClock.sleep(1800);test.runOnMainSync(()->((EditText)app.findViewById(MainActivity.QUERY_ID)).setText(question));SystemClock.sleep(2000);
        long submitted=SystemClock.elapsedRealtime()-start;test.runOnMainSync(()->app.findViewById(MainActivity.SEARCH_ID).performClick());
        waitFor(()->app.answerDone,135000,"Recorded request completed");long finished=SystemClock.elapsedRealtime()-start;
        JSONObject answer=app.lastAnswer!=null?result("Bonsai 4B","scene-"+scene,app.lastAnswer):new JSONObject().put("model","structured OSM query").put("text",app.lastPlaces==null?"":app.lastPlaces.text());
        answer.put("mobileDataPreference",Settings.Global.getInt(context.getContentResolver(),"mobile_data",-1));
        if(app.lastPrepared!=null)answer.put("system",ChatPrompt.SYSTEM).put("preparedUser",app.lastPrepared.user());
        answer.put("question",question).put("submittedMs",submitted).put("finishedMs",finished).put("sceneStartElapsedRealtime",start);answers.put(answer);
        check(!answer.getString("text").isBlank(),"Actual app returned visible content");
        SystemClock.sleep(1800);
        if(app.lastPlaces!=null){test.runOnMainSync(()->scrollTop(app.getWindow().getDecorView()));SystemClock.sleep(800);}
        capture("answer.png");
        if(app.lastAnswer!=null)check(app.lastAnswer.reason()==0,"Recorded answer ended naturally");
        SystemClock.sleep(4200);answer.put("sceneEndMs",SystemClock.elapsedRealtime()-start);
        write(new File(output,"scene-complete.json"),answer.toString(2));
        // Keep the synthetic Activity visible until the host closes the recording.
        waitFor(()->new File(output,"stop-confirmed").isFile(),45000,"Recording closed by host");
    }
    void run(){
        JSONObject report=new JSONObject();boolean passed=false;
        try{
            session=args.getString("demo_session","");phase=args.getString("phase","");check(session.matches("demo-[0-9TZ]+-[a-f0-9]{8}"),"Valid demo session");
            check(context.getPackageName().equals("dev.outpost.app.releaseqa"),"Isolated QA package");
            try(var in=test.getContext().getAssets().open("demo/cases.json")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);cases=new JSONObject(bytes.toString("UTF-8"));}
            String suffix=phase.equals("demo-record")?"scene-"+args.getString("scene","0"):phase;
            output=new File(context.getExternalFilesDir(null),session+"/"+suffix);check(output.mkdirs(),"New evidence directory");
            switch(phase){case "demo-prepare"->prepare();case "demo-compare","demo-compare-extra","demo-compare-summary"->compare();case "demo-add-summary"->addSummary();case "demo-record"->record();case "demo-restore"->restore();default->throw new IllegalArgumentException("Unknown demo phase");}
            passed=true;
        }catch(Throwable error){try{report.put("error",error.toString());}catch(Exception ignored){}}
        finally{
            if(activity!=null){MainActivity close=activity;test.runOnMainSync(close::finish);test.waitForIdleSync();}
            try{report.put("passed",passed).put("session",session).put("phase",phase).put("checks",checks).put("answers",answers);if(output!=null)write(new File(output,"result.json"),report.toString(2));}catch(Exception ignored){}
            Bundle result=new Bundle();result.putString("stream","\nDEMO_RESULT "+report+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
