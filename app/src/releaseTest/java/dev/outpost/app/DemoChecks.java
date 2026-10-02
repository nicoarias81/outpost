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
                if(phase.equals("demo-compare-selected")&&i!=1&&i!=3)continue;
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
    final JSONArray interactions=new JSONArray();
    long recordingStart;
    void event(String action)throws Exception{interactions.put(new JSONObject().put("action",action).put("elapsedMs",SystemClock.elapsedRealtime()-recordingStart));write(new File(output,"interactions.json"),interactions.toString(2));}
    android.view.View find(android.view.View root,java.util.function.Predicate<android.view.View> predicate){
        if(predicate.test(root))return root;
        if(root instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++){var match=find(group.getChildAt(i),predicate);if(match!=null)return match;}
        return null;
    }
    android.view.View view(int id){final android.view.View[] found={null};test.runOnMainSync(()->found[0]=activity.findViewById(id));return found[0];}
    void tap(android.view.View target,String label)throws Exception{
        check(target!=null,"Visible target: "+label);int[] xy=new int[2];boolean[] visible={false};
        test.runOnMainSync(()->{android.graphics.Rect rect=new android.graphics.Rect();visible[0]=target.isShown()&&target.isEnabled()&&target.getGlobalVisibleRect(rect);xy[0]=rect.centerX();xy[1]=rect.centerY();});
        check(visible[0],"Touchable target: "+label);long time=SystemClock.uptimeMillis();
        for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){
            var touch=android.view.MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,xy[0],xy[1],0);touch.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(touch);touch.recycle();SystemClock.sleep(80);
        }
        test.waitForIdleSync();event(label);
    }
    void swipeUp()throws Exception{
        int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;long time=SystemClock.uptimeMillis();
        for(int i=0;i<=16;i++){int action=i==0?0:i==16?1:2;float y=height*(.76f-.40f*i/16);var touch=android.view.MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,width*.72f,y,0);touch.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(touch);touch.recycle();SystemClock.sleep(28);}
        test.waitForIdleSync();event("Scroll visible page");SystemClock.sleep(1100);
    }
    void documentsTour()throws Exception{
        tap(view(R.id.chat_settings),"Open Settings");SystemClock.sleep(1700);capture("settings.png");
        tap(view(R.id.chat_documents),"Open loaded documents");SystemClock.sleep(1800);capture("documents.png");
        android.view.View[] open={null};
        for(int page=0;page<6;page++){
            test.runOnMainSync(()->{var title=find(activity.getWindow().getDecorView(),v->v instanceof android.widget.TextView t&&t.getText().toString().equals("Pump revision C.txt"));
                if(title!=null){var button=find((android.view.View)title.getParent(),v->v instanceof android.widget.Button b&&b.getText().toString().equals("Open"));android.graphics.Rect rect=new android.graphics.Rect();if(button!=null&&button.getGlobalVisibleRect(rect)&&rect.height()>=button.getHeight()-2)open[0]=button;}});
            if(open[0]!=null)break;swipeUp();
        }
        capture("manual-in-library.png");tap(open[0],"Open Pump revision C source");waitFor(()->activity.documentOpen,5000,"Source opened from document library");SystemClock.sleep(2500);capture("source.png");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);event("Close source");SystemClock.sleep(600);
        android.view.View[] back={null};test.runOnMainSync(()->back[0]=find(activity.getWindow().getDecorView(),v->"Back to chat".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())));
        tap(back[0],"Return to chat");SystemClock.sleep(700);
    }
    void typeQuestion(String question)throws Exception{
        EditText input=(EditText)view(MainActivity.QUERY_ID);
        // Test input only: avoid personal suggestions without changing the user's keyboard settings.
        test.runOnMainSync(()->{input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);input.setImeHintLocales(new android.os.LocaleList(java.util.Locale.US));});
        tap(input,"Focus question field");
        waitFor(()->{boolean[] visible={false};test.runOnMainSync(()->{var insets=activity.getWindow().getDecorView().getRootWindowInsets();visible[0]=insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime());});return visible[0];},7000,"Real keyboard visible");
        SystemClock.sleep(900);event("Keyboard visible");capture("keyboard.png");
        var keys=android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD);
        for(int i=0;i<question.length();i++){
            boolean[] focused={false};test.runOnMainSync(()->focused[0]=input.hasFocus()&&activity.hasWindowFocus());check(focused[0],"Question field retains focus at character "+i);
            var events=keys.getEvents(new char[]{question.charAt(i)});check(events!=null,"Supported keyboard character "+i);
            for(var key:events)test.sendKeySync(key);
            SystemClock.sleep(question.charAt(i)==' '?170:65+(i*17)%50);
            if(i==Math.min(30,question.length()-1))capture("typing.png");
        }
        test.waitForIdleSync();String[] typed={""};test.runOnMainSync(()->typed[0]=input.getText().toString());check(question.equals(typed[0]),"Typed question matches comparison prompt");
        event("Question typed character by character");SystemClock.sleep(1000);capture("question-ready.png");
    }
    void inspectPlaceSource()throws Exception{
        SystemClock.sleep(2400);swipeUp();event("Read stored place results");
        android.view.View[] sources={null};test.runOnMainSync(()->sources[0]=find(activity.getWindow().getDecorView(),v->v instanceof android.widget.Button b&&b.getText().toString().equals("View sources")));
        tap(sources[0],"View saved OSM sources");SystemClock.sleep(1600);capture("osm-source-list.png");
        var root=test.getUiAutomation().getRootInActiveWindow();check(root!=null,"Source picker is visible");
        var matches=root.findAccessibilityNodeInfosByText("Restaurante La Ancha");check(!matches.isEmpty(),"Restaurant source in picker");
        var node=matches.get(0);check(context.getPackageName().contentEquals(node.getPackageName()),"Source picker belongs to demo app");
        android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);check(!rect.isEmpty(),"Restaurant source has visible bounds");
        long time=SystemClock.uptimeMillis();for(int action:new int[]{0,1}){var touch=android.view.MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,rect.centerX(),rect.centerY(),0);touch.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(touch);touch.recycle();SystemClock.sleep(80);}
        event("Open Restaurante La Ancha saved record");waitFor(()->activity.documentOpen,5000,"Saved OSM record is visible");SystemClock.sleep(4200);capture("osm-source.png");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);event("Return from saved OSM record");SystemClock.sleep(1000);
    }
    void record()throws Exception{
        own();check(Settings.Global.getInt(context.getContentResolver(),"airplane_mode_on",0)==1,"Airplane mode on");
        check(Settings.Global.getInt(context.getContentResolver(),"wifi_on",1)==0,"Wi-Fi off");
        String[] permissions=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        check(permissions==null||permissions.length==0,"Demo app declares no permissions");
        int scene=Integer.parseInt(args.getString("scene","0"));
        boolean general=scene==cases.getJSONArray("cases").length()+1;
        String question=general?cases.getJSONObject("generalKnowledgeCase").getString("question"):scene<cases.getJSONArray("cases").length()?cases.getJSONArray("cases").getJSONObject(scene).getString("question"):cases.getString("placeQuestion");
        if(general)try(Library library=new Library(context)){check(library.search(question).isEmpty(),"General question retrieves no documents before capture");}
        try(ChatStore chat=new ChatStore(context)){chat.clear();}
        ModelStore.select(context,ModelStore.BONSAI4);MainActivity app=start();
        write(new File(output,"ready.json"),new JSONObject().put("question",question).put("scene",scene).toString());
        waitFor(()->new File(output,"go").isFile(),60000,"Host recording started");long start=SystemClock.elapsedRealtime();recordingStart=start;
        SystemClock.sleep(1000);if(scene==1)documentsTour();typeQuestion(question);
        long submitted=SystemClock.elapsedRealtime()-start;tap(view(MainActivity.SEARCH_ID),"Send question");
        waitFor(()->app.answerDone,135000,"Recorded request completed");long finished=SystemClock.elapsedRealtime()-start;
        JSONObject answer=app.lastAnswer!=null?result("Bonsai 4B","scene-"+scene,app.lastAnswer):new JSONObject().put("model","structured OSM query").put("text",app.lastPlaces==null?"":app.lastPlaces.text());
        answer.put("mobileDataPreference",Settings.Global.getInt(context.getContentResolver(),"mobile_data",-1));
        if(general)check(app.lastPrepared!=null&&app.lastPrepared.sources().isEmpty()&&app.lastHits.isEmpty(),"General knowledge answer has zero retrieved documents");
        answer.put("route",app.lastPlaces==null?"chat":app.lastAnswer==null?"places-direct":"places-with-model").put("placeSourceCount",app.lastPlaces==null?0:app.lastPlaces.sources().size());
        answer.put("retrievedPassageCount",app.lastPrepared==null?0:app.lastPrepared.sources().size());
        if(app.lastPrepared!=null)answer.put("system",app.lastSystem).put("preparedUser",app.lastPrepared.user());
        answer.put("interactionMethod","Visible native keyboard; incremental key events; injected touchscreen navigation; automated walkthrough").put("question",question).put("submittedMs",submitted).put("finishedMs",finished).put("sceneStartElapsedRealtime",start);answers.put(answer);
        check(!answer.getString("text").isBlank(),"Actual app returned visible content");
        SystemClock.sleep(1800);
        if(app.lastPlaces!=null){test.runOnMainSync(()->scrollTop(app.getWindow().getDecorView()));SystemClock.sleep(800);}
        capture("answer.png");if(scene==cases.getJSONArray("cases").length()){check(app.lastAnswer!=null&&app.lastPlaces!=null&&app.lastPlaces.sources().size()>0,"Place answer uses the local model with OSM sources");inspectPlaceSource();}
        if(app.lastAnswer!=null)check(app.lastAnswer.reason()==0,"Recorded answer ended naturally");
        SystemClock.sleep(5200);event("Answer reading pause complete");answer.put("sceneEndMs",SystemClock.elapsedRealtime()-start);
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
            switch(phase){case "demo-prepare"->prepare();case "demo-compare","demo-compare-extra","demo-compare-summary","demo-compare-selected"->compare();case "demo-add-summary"->addSummary();case "demo-record"->record();case "demo-restore"->restore();default->throw new IllegalArgumentException("Unknown demo phase");}
            passed=true;
        }catch(Throwable error){try{report.put("error",error.toString());}catch(Exception ignored){}}
        finally{
            if(activity!=null){MainActivity close=activity;test.runOnMainSync(close::finish);test.waitForIdleSync();}
            try{report.put("passed",passed).put("session",session).put("phase",phase).put("checks",checks).put("answers",answers);if(output!=null)write(new File(output,"result.json"),report.toString(2));}catch(Exception ignored){}
            Bundle result=new Bundle();result.putString("stream","\nDEMO_RESULT "+report+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
