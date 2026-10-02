package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.EditText;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Acceptance on a newly provisioned, explicitly owned synthetic release store. */
final class AcceptanceChecks {
    final Instrumentation test;final Bundle args;final Context context;
    final JSONArray checks=new JSONArray(),answers=new JSONArray();
    MainActivity activity;File output;String run;
    AcceptanceChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;context=test.getTargetContext();}
    void check(boolean value,String label)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",value));if(!value)throw new AssertionError(label);}
    void waitFor(java.util.function.BooleanSupplier done,long limit,String label)throws Exception{
        long end=SystemClock.uptimeMillis()+limit;while(!done.getAsBoolean()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);test.waitForIdleSync();check(done.getAsBoolean(),label);
    }
    MainActivity start()throws Exception{
        MainActivity app=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=app;
        boolean[] ready={false};waitFor(()->{test.runOnMainSync(()->ready[0]=app.findViewById(MainActivity.QUERY_ID)!=null);return ready[0];},15000,"Chat loads after process start");return app;
    }
    void capture(String name)throws Exception{SystemClock.sleep(200);var bitmap=test.getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot available: "+name);try(var out=new FileOutputStream(new File(output,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    void send(String name,String question,String required)throws Exception{
        MainActivity app=activity;test.runOnMainSync(()->app.sendMessage(question));waitFor(()->app.answerDone,135000,"Request completes: "+name);
        JSONObject answer=new JSONObject().put("case",name).put("question",question);
        if(app.lastAnswer!=null){var a=app.lastAnswer;answer.put("text",a.text()).put("firstTokenMs",a.firstTokenMs()).put("totalMs",a.totalMs()).put("tokens",a.tokens()).put("reason",a.reason());}
        else if(app.lastPlaces!=null)answer.put("text",app.lastPlaces.text()).put("route","places");
        answers.put(answer);check(answer.has("text")&&answer.getString("text").toLowerCase(java.util.Locale.ROOT).contains(required.toLowerCase(java.util.Locale.ROOT)),"Expected grounded result: "+name);
        if(app.lastAnswer!=null)check(app.lastAnswer.reason()==0,"Natural completion: "+name);
    }
    static String osm(){return "<osm version=\"0.6\"><node id=\"101\" lat=\"40.40\" lon=\"-3.70\"><tag k=\"name\" v=\"Cedar Museum\"/><tag k=\"tourism\" v=\"museum\"/></node><node id=\"102\" lat=\"40.4001\" lon=\"-3.7001\"><tag k=\"name\" v=\"Willow Kitchen\"/><tag k=\"amenity\" v=\"restaurant\"/></node><node id=\"103\" lat=\"40.402\" lon=\"-3.702\"><tag k=\"name\" v=\"Ridge Park\"/><tag k=\"leisure\" v=\"park\"/></node></osm>";}
    void fixture(String name,String text)throws Exception{Files.write(new File(context.getCacheDir(),name).toPath(),text.getBytes(StandardCharsets.UTF_8));}
    void prepare()throws Exception{
        File marker=new File(context.getFilesDir(),"release-acceptance-owned");
        try(Library library=new Library(context);ChatStore chat=new ChatStore(context)){
            check(!marker.exists()&&library.documents().isEmpty()&&chat.turns().isEmpty(),"New package contains no prior documents or conversation");
            Files.write(marker.toPath(),run.getBytes(StandardCharsets.US_ASCII));
            fixture("Field manual.txt","Synthetic field manual: the spare filter for sample pump PX-65 is F-92. This describes the spare part only; no current equipment condition is recorded.");
            fixture("Farm log.csv","plot,irrigation_time\nNorth,06:30\nSouth,07:15\n");
            fixture("Travel kit.txt","Synthetic travel kit: the meeting place is Cedar gate. The mountain shelter is called Aspen Hut. The roadside assistance reference is ROAD-42. No current opening hours or availability are recorded.");
            fixture("Area.osm",osm());
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context);
            try(var doc=new com.tom_roush.pdfbox.pdmodel.PDDocument()){
                var page=new com.tom_roush.pdfbox.pdmodel.PDPage();doc.addPage(page);
                try(var stream=new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc,page)){stream.beginText();stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,14);stream.newLineAtOffset(60,700);stream.showText("Synthetic manual: sample pump PX-65 uses spare filter F-92.");stream.endText();}doc.save(new File(context.getCacheDir(),"Manual.pdf"));
            }
        }
        start();capture("empty-chat.png");
        for(String name:List.of("Field manual.txt","Farm log.csv","Travel kit.txt","Area.osm","Manual.pdf")){
            File file=new File(context.getCacheDir(),name);MainActivity app=activity;
            test.runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))));
            waitFor(()->!app.importInProgress(),20000,"File-result handler completes: "+name);
            try(Library library=new Library(context)){check(library.documents().stream().anyMatch(d->d.title().equals(name)),"File appears in product library: "+name);}
        }
        MainActivity app=activity;test.runOnMainSync(app::showDocuments);capture("imported-documents.png");
        try(Library library=new Library(context)){
            var pdf=library.documents().stream().filter(d->library.metadata(d.id()).format().equals("pdf")).findFirst().orElseThrow();
            test.runOnMainSync(()->app.openDocument(pdf,null,1));waitFor(()->app.documentOpen,10000,"Imported PDF reader opens");SystemClock.sleep(900);capture("pdf-reader.png");
        }
        try(Library library=new Library(context,"accept-reject-"+UUID.randomUUID()+".db")){
            File invalid=new File(context.getCacheDir(),"broken.csv");Files.write(invalid.toPath(),"id,value\nA,1,extra\n".getBytes(StandardCharsets.UTF_8));
            boolean rejected=false;try{new DocumentImporter(context,library).importFile(Uri.fromFile(invalid),invalid.getName(),invalid.getName(),invalid.length(),new DocumentImporter.Cancellation());}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected&&library.documents().isEmpty(),"Malformed CSV creates no partial document");
            DocumentImporter.Cancellation canceled=new DocumentImporter.Cancellation();canceled.cancel();boolean stopped=false;
            try{new DocumentImporter(context,library).importFile(Uri.fromFile(invalid),invalid.getName(),invalid.getName(),invalid.length(),canceled);}catch(java.util.concurrent.CancellationException expected){stopped=true;}
            check(stopped&&library.documents().isEmpty(),"Canceled import creates no partial document");
        }
    }
    void model()throws Exception{
        ModelStore store=new ModelStore(context,ModelStore.BONSAI4);
        if(!store.ready())try(var input=new FileInputStream(new File(context.getExternalFilesDir(null),ModelStore.BONSAI4.filename()))){store.install(input,n->{});}
        check(store.ready(),"Pinned Bonsai 4B imported and ready");ModelStore.select(context,ModelStore.BONSAI4);
        var profile=RuntimeSettings.load(context,ModelStore.BONSAI4);check(profile.configuration(true).speculativeDepth()==0,"Production speculation remains off");
    }
    void chat()throws Exception{
        model();start();
        send("traveler-place","What restaurants are near Cedar Museum?","Willow Kitchen");
        check(activity.lastPlaces!=null&&!activity.lastPlaces.sources().isEmpty(),"Place answer has stored OSM source");
        send("park-lookup","Where is Ridge Park?","Ridge Park");
        send("field-manual","What spare filter does sample pump PX-65 use? Cite the document.","F-92");
        check(!activity.lastHits.isEmpty(),"Field answer uses imported evidence");capture("grounded-answer.png");
        send("farmer-record","According to the farm log, what time is irrigation for the North plot?","06:30");
        send("hiker-shelter","According to my travel kit, what is the mountain shelter called?","Aspen Hut");
        send("roadside-reference","What roadside assistance reference did I store in the travel kit?","ROAD-42");
        MainActivity app=activity;
        test.runOnMainSync(()->{app.sendMessage("Explain the difference between a compass and GPS in detail.");app.findViewById(MainActivity.SEARCH_ID).performClick();});
        waitFor(()->app.answerDone,15000,"Stop settles a queued request");
        try(ChatStore chat=new ChatStore(context)){check(chat.turns().get(chat.turns().size()-1).status().equals("canceled"),"Cancellation is persisted");}
        send("after-cancel","What spare filter does sample pump PX-65 use?","F-92");
        capture("after-cancel.png");
    }
    void seed()throws Exception{
        try(ChatStore chat=new ChatStore(context)){
            check(chat.turns().stream().anyMatch(t->t.answer().contains("F-92")),"Completed answer exists before process death");
            chat.save(new ChatStore.Turn("release-interrupted","Synthetic interrupted request","Partial synthetic response","pending",List.of()));
        }
        // Activity.getPreferences uses the full class name when the QA package differs from its Java namespace.
        String activityName=MainActivity.class.getName();String prefix=context.getPackageName()+".";
        if(activityName.startsWith(prefix))activityName=activityName.substring(prefix.length());
        context.getSharedPreferences(activityName,0).edit().putBoolean("folder_running",true).putString("draft","Retained release draft").commit();
        JSONObject retained=new JSONObject();try(Library library=new Library(context)){for(var d:library.documents())retained.put(d.id(),library.metadata(d.id()).sha256());}
        Files.write(new File(context.getFilesDir(),"release-retained.json").toPath(),retained.toString().getBytes(StandardCharsets.UTF_8));
        check(retained.length()>=5,"Document identities recorded before update/restart");
    }
    void recover()throws Exception{
        start();
        try(ChatStore chat=new ChatStore(context)){var turns=chat.turns();check(turns.stream().anyMatch(t->t.id().equals("release-interrupted")&&t.status().equals("interrupted")),"Pending conversation recovers after process death");check(turns.stream().anyMatch(t->t.answer().contains("F-92")),"Completed answer survives process death/update");}
        JSONObject retained=new JSONObject(new String(Files.readAllBytes(new File(context.getFilesDir(),"release-retained.json").toPath()),StandardCharsets.UTF_8));
        try(Library library=new Library(context)){check(library.documents().size()==retained.length(),"Document count survives update/restart");for(var d:library.documents())check(retained.getString(d.id()).equals(library.metadata(d.id()).sha256()),"Document identity/content retained: "+d.title());}
        MainActivity app=activity;boolean[] draft={false};test.runOnMainSync(()->draft[0]=((EditText)app.findViewById(MainActivity.QUERY_ID)).getText().toString().equals("Retained release draft"));check(draft[0],"Unsent draft survives process death/update");
        boolean[] reportAvailable={false};
        test.runOnMainSync(()->{app.showSettings();var report=app.findViewById(R.id.folder_report);reportAvailable[0]=report!=null;if(report!=null)report.performClick();});
        check(reportAvailable[0],"Folder recovery report is offered in Settings");test.waitForIdleSync();SystemClock.sleep(200);
        capture("recovery-report.png");
        var root=test.getUiAutomation().getRootInActiveWindow();
        answers.put(new JSONObject().put("case","recovery-accessibility").put("hasRoot",root!=null).put("rootPackage",root==null?"":String.valueOf(root.getPackageName())));
        if(root!=null)root.recycle();
        waitFor(()->{
            var current=test.getUiAutomation().getRootInActiveWindow();
            try{return current!=null&&context.getPackageName().contentEquals(current.getPackageName())
                &&!current.findAccessibilityNodeInfosByText("previous folder import was interrupted").isEmpty();}
            finally{if(current!=null)current.recycle();}
        },5000,"Interrupted folder status is surfaced");
        capture("recovered-settings.png");
    }
    void run(){
        JSONObject result=new JSONObject();boolean passed=false;
        try{
            run=args.getString("accept_run","");if(!run.matches("accept-[0-9TZ]+-[a-f0-9]{8}"))throw new IllegalArgumentException("Unique acceptance run required");
            String pkg=context.getPackageName();check(pkg.equals("dev.outpost.app.releaseqa")||pkg.equals("dev.outpost.mobile"),"Dedicated release acceptance package");
            String phase=args.getString("phase");
            File marker=new File(context.getFilesDir(),"release-acceptance-owned");
            if(!phase.equals("accept-prepare"))check(marker.isFile()&&new String(Files.readAllBytes(marker.toPath()),StandardCharsets.US_ASCII).equals(args.getString("owner_run")),"Synthetic store ownership matches recorded provisioning");
            output=new File(context.getExternalFilesDir(null),run);check(output.mkdir(),"Unique evidence directory");
            switch(phase){case "accept-prepare"->prepare();case "accept-chat"->chat();case "accept-seed"->seed();case "accept-recover"->recover();default->throw new IllegalArgumentException("Unknown acceptance phase");}
            passed=true;
        }catch(Throwable error){try{result.put("error",error.toString());}catch(Exception ignored){}}
        finally{
            if(activity!=null){MainActivity close=activity;test.runOnMainSync(close::finish);test.waitForIdleSync();}
            try{result.put("passed",passed).put("checks",checks).put("answers",answers).put("run",run);if(output!=null)Files.write(new File(output,"result.json").toPath(),result.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
            Bundle bundle=new Bundle();bundle.putString("stream","\nRELEASE_RESULT "+result+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,bundle);
        }
    }
}
