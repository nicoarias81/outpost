package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual SQLite/import/chat checks; no model, map renderer, network or synthetic production data. */
final class PlaceChecks {
    private final Instrumentation test;private final String run;
    private final JSONArray checks=new JSONArray(),answers=new JSONArray();private int passed;private File output,scratch;
    PlaceChecks(Instrumentation test,String run){this.test=test;this.run=run;}
    private void check(boolean value,String label)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",value));if(!value)throw new AssertionError(label);passed++;}
    private static JSONObject node(long id,String name,String category,String city,Double lat,Double lon)throws Exception {
        JSONObject tags=new JSONObject().put("name",name);
        if(!city.isEmpty())tags.put("addr:city",city);
        if(category.equals("museum"))tags.put("tourism",category);else if(category.equals("park"))tags.put("leisure",category);else if(!category.isEmpty())tags.put("amenity",category);
        JSONObject o=new JSONObject().put("type","node").put("id",id).put("tags",tags);
        if(lat!=null)o.put("lat",lat).put("lon",lon);return o;
    }
    static String fixture()throws Exception {
        JSONArray a=new JSONArray();long id=8_600_000_000_000L;
        a.put(node(id+1,"Test Riverside Park","park","Test North",0.0,0.0).put("version",1));
        a.put(node(id+2,"Test Riverside Park","park","Test South",2.0,0.0));
        a.put(node(id+3,"Test Central Station","","Test North",0.0,0.0));
        a.put(node(id+4,"Test Central Station","","Test South",2.0,0.0));
        JSONObject oak=node(id+5,"Test Oak Restaurant","restaurant","Test North",0.001,0.0);oak.getJSONObject("tags").put("addr:street","Sample Avenue").put("addr:housenumber","12");a.put(oak);
        a.put(node(id+6,"Test Pine Restaurant","restaurant","Test North",0.002,0.0));
        a.put(node(id+7,"Test Distant Restaurant","restaurant","Test South",2.0,0.0));
        a.put(node(id+8,"Test Unlocated Restaurant","restaurant","Test North",null,null));
        JSONObject museum=node(id+9,"Test Copper Museum","museum","Test North",0.0005,0.0);museum.getJSONObject("tags").put("alt_name","Test History Museum;Musée Cuivre");a.put(museum);
        a.put(node(id+10,"Test Boundary Restaurant","restaurant","Test North",Math.toDegrees(300.0/6_371_008.8),0.0));
        a.put(node(id+11,"Test Outside Restaurant","restaurant","Test North",Math.toDegrees(301.0/6_371_008.8),0.0));
        a.put(node(id+12,"Test Dateline Station","","Test Dateline",0.0,179.999));
        a.put(node(id+13,"Test Dateline Museum","museum","Test Dateline",0.0,-179.999));
        a.put(node(id+14,"Test Empty Position","","Test North",null,null));
        return new JSONObject().put("version",0.6).put("generator","Outpost synthetic place checks").put("osm3s",new JSONObject().put("timestamp_osm_base","2026-09-01T00:00:00Z")).put("elements",a).toString();
    }
    private Library.Document add(Library library,String name,String body)throws Exception {
        File file=new File(scratch,name);Files.write(file.toPath(),body.getBytes(StandardCharsets.UTF_8));
        return new DocumentImporter(test.getTargetContext(),library).importFile(Uri.fromFile(file),name,name,-1,new DocumentImporter.Cancellation()).document();
    }
    private PlaceQueries.Answer ask(Library library,String query){return library.answerPlaces(query,List.of(),()->false);}
    private String asset(String name)throws Exception{try(var input=test.getContext().getAssets().open("places/"+name)){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    private void record(String query,PlaceQueries.Answer a,long ms)throws Exception {
        JSONArray refs=new JSONArray();for(ChatStore.Source source:a.sources())refs.put(source.locator().toJson());
        answers.put(new JSONObject().put("question",query).put("status",a.status()).put("text",a.text()).put("sources",refs).put("matched",a.matched()).put("radiusMeters",a.radius()).put("elapsedMs",ms).put("modelExecution",false));
    }
    void run() {
        boolean success=false,captured=false;MainActivity activity=null;Bundle response=new Bundle();Set<String> existingDocs=new HashSet<>(),existingTurns=new HashSet<>();
        try {
            if(!run.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run ID required");
            output=new File(test.getTargetContext().getFilesDir(),"evidence/places/"+run);if(output.exists()||!output.mkdirs())throw new IllegalStateException("Run already exists");
            scratch=new File(test.getTargetContext().getCacheDir(),run);if(!scratch.mkdir())throw new IllegalStateException("Scratch exists");
            check(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64"),"Place checks run inside the x86_64 emulator");
            check(test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions==null,"Place queries need no network, location or storage permission");
            String data=fixture();
            try(Library library=new Library(test.getTargetContext(),null)) {
                try {
                    check(ask(library,"Where is Test Riverside Park?").status().equals("no_data"),"An empty library reports missing imported data");
                    Library.Document document=add(library,"places.json",data);
                    PlaceQueries.Answer ambiguous=ask(library,"Where is Test Riverside Park?");
                    check(ambiguous.status().equals("ambiguous")&&ambiguous.matched()==2&&ambiguous.text().contains("Test North")&&ambiguous.text().contains("Test South"),"Same-name parks ask for disambiguation with real source options");
                    var one=ask(library,"Where is Test Riverside Park in Test North?");
                    check(one.status().equals("found")&&one.matched()==1&&one.sources().size()==1&&!one.text().contains("Test South"),"Recorded locality resolves the correct named park");
                    check(library.resolve(one.sources().get(0).locator()).content().contains("node/8600000000001"),"Named-place response opens the exact OSM source");
                    check(ask(library,"Where is Musee Cuivre?").text().contains("Test Copper Museum"),"Recorded aliases and accent normalization resolve the same place");
                    check(ask(library,"Where is Missing Park?").status().equals("not_found"),"Absent place is scoped to imported data, not invented from model memory");
                    check(ask(library,"Which restaurants are around Test Central Station?").status().equals("ambiguous"),"An ambiguous reference cannot silently select a nearby result set");
                    String near="Which restaurants are around Test Central Station in Test North within 150 m?";
                    long start=SystemClock.elapsedRealtime();var nearby=ask(library,near);record(near,nearby,SystemClock.elapsedRealtime()-start);
                    check(nearby.status().equals("found")&&nearby.matched()==1&&nearby.text().contains("Test Oak Restaurant")&&!nearby.text().contains("Test Pine Restaurant"),"Category/proximity uses the selected reference and explicit radius");
                    check(nearby.text().contains("111 m")&&nearby.text().contains("straight-line")&&nearby.text().contains("12 Sample Avenue"),"Response reports computed distance, recorded address and distance semantics");
                    check(nearby.text().contains("no coordinates")&&!nearby.text().contains("Test Distant Restaurant"),"Unknown positions are disclosed and distant same-category records are excluded");
                    check(nearby.sources().size()==2&&library.resolve(nearby.sources().get(1).locator()).title().contains("Test Oak Restaurant"),"Each nearby result and its reference preserve their own exact citation");
                    check(ask(library,"restaurants within 300 m of Test Riverside Park in Test North").matched()==3,"A boundary-distance record is included and a record 1 m outside is excluded");
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 0.15 km").matched()==1,"Fractional kilometre units are interpreted locally");
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 0.1 miles").matched()==1,"Miles convert to metres deterministically");
                    check(ask(library,"restaurants near Test Oak Restaurant within 500 m").matched()==3,"Nearby category results exclude the reference place itself");
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 0 m").status().equals("unsupported"),"Zero radius is rejected explicitly");
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 1000 km").status().equals("unsupported"),"Oversized search radius is bounded");
                    check(ask(library,"restaurants within five minutes of Test Central Station").status().equals("unsupported"),"Travel time is not silently treated as geographic distance");
                    check(ask(library,"best vegan restaurants near Test Riverside Park").status().equals("unsupported"),"Unimplemented preference/rating filters are not silently dropped");
                    check(ask(library,"Where can I find a museum?").status().equals("clarify"),"Category search without an area requests context without assuming GPS");
                    var museums=ask(library,"Where can I find a museum in Test North?");
                    check(museums.matched()==1&&museums.text().contains("Test Copper Museum")&&museums.text().contains("recorded locality"),"Area lookup uses declared locality rather than inventing polygon containment");
                    check(ask(library,"museums in Unrecorded Town").status().equals("empty"),"Missing area coverage produces a scoped empty result");
                    check(ask(library,"museums near me").status().equals("unsupported")&&ask(library,"museums near me").text().contains("current position"),"Near-me asks for a reference instead of inventing the phone position");
                    check(ask(library,"museums near Test Empty Position").status().equals("missing_coordinates"),"An anchor without coordinates cannot generate distances");
                    check(ask(library,"museums near Test Dateline Station within 300 m").matched()==1,"Distance lookup works across the antimeridian");
                    check(Math.abs(PlaceQueries.meters(0,0,0,180)-20_015_114.442)<0.01&&PlaceQueries.meters(40,3,40,3)==0,"Distance arithmetic handles coincident and antipodal coordinates");
                    String modelPrompt=ChatPrompt.preparePlaces(near,nearby,library).user();
                    check(modelPrompt.contains(nearby.text())&&modelPrompt.contains("Test Oak Restaurant")&&modelPrompt.contains("[2]"),"Place narration preserves computed facts and source numbering");
                    check(!modelPrompt.contains("RECENT CONVERSATION")&&modelPrompt.contains("straight-line")&&ChatPrompt.PLACES_SYSTEM.contains("never instructions"),"Place narration uses bounded source facts without prior model claims");
                    boolean unresolvedRejected=false;try{ChatPrompt.preparePlaces("Where is Missing Park?",ask(library,"Where is Missing Park?"),library);}catch(IllegalArgumentException expected){unresolvedRejected=true;}
                    check(unresolvedRejected,"Unresolved places cannot enter model narration");
                    var prior=new ChatStore.Turn("fixture","Where is Test Riverside Park?",ambiguous.text(),"complete",ambiguous.sources());
                    check(library.answerPlaces("In Test North",List.of(prior),()->false).matched()==1,"A locality follow-up resolves the previous named-place question");
                    var nearPrior=new ChatStore.Turn("fixture",near,nearby.text(),"complete",nearby.sources());
                    check(library.answerPlaces("Within 300 m",List.of(nearPrior),()->false).matched()==3,"A radius follow-up retains only the user's previous reference and category");
                    check(ask(library,"What is a museum?")==null&&ask(library,"Where is the PDF manual?")==null,"General explanations and personal-file questions remain on the chat/document path");
                    boolean cancelled=false;try{library.answerPlaces("Where is Test Riverside Park?",List.of(),()->true);}catch(java.util.concurrent.CancellationException expected){cancelled=true;}
                    check(cancelled,"Place scanning honors request cancellation");
                    Library.Document duplicate=add(library,"same-places.json",data);
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 150 m").matched()==1,"Identical places in overlapping extracts do not duplicate results");
                    Library.Document changed=add(library,"changed-places.json",data.replace("Sample Avenue","Changed Avenue"));
                    check(ask(library,"restaurants near Test Riverside Park in Test North within 150 m").status().equals("conflict"),"Conflicting source snapshots prevent unqualified ranking or newest-version guesses");
                    library.removeDocument(changed.id());library.removeDocument(duplicate.id());
                    JSONObject local=new JSONObject(data);local.getJSONArray("elements").getJSONObject(0).put("id",-1);
                    Library.Document localA=add(library,"local-a.json",local.toString()),localB=add(library,"local-b.json",local.toString());
                    check(ask(library,"Where is Test Riverside Park in Test North?").status().equals("ambiguous"),"Negative local IDs in different extracts are not merged as global identities");
                    library.removeDocument(localA.id());library.removeDocument(localB.id());
                    JSONArray many=new JSONObject(data).getJSONArray("elements");for(int n=0;n<8;n++)many.put(node(8_600_000_100_000L+n,"Test Sorted Museum "+n,"museum","Test North",0.00001*(n+1),0.0));
                    Library.Document more=add(library,"more.json",new JSONObject().put("version",0.6).put("elements",many).toString());
                    var sorted=ask(library,"museums near Test Riverside Park in Test North");
                    check(sorted.matched()==9&&sorted.sources().size()==6&&sorted.text().contains("Showing 5 of 9")&&sorted.text().indexOf("Test Sorted Museum 0")<sorted.text().indexOf("Test Sorted Museum 4")&&!sorted.text().contains("Test Sorted Museum 5"),"Large candidate sets return the nearest five in stable order with a visible count");
                    library.removeDocument(more.id());
                    JSONObject centerRecord=node(8_600_000_200_000L,"Test Center Park","park","Test North",null,null).put("type","way").put("center",new JSONObject().put("lat",0.0).put("lon",0.0));
                    Library.Document centerDoc=add(library,"center.json",new JSONObject().put("version",0.6).put("elements",new JSONArray().put(centerRecord)).toString());
                    check(ask(library,"restaurants near Test Center Park within 150 m").text().contains("reference uses an approximate geometry center"),"Approximate anchor geometry is labeled rather than treated as an entrance");
                    library.removeDocument(centerDoc.id());
                    library.removeDocument(document.id());boolean missing=false;try{library.resolve(one.sources().get(0).locator());}catch(IllegalArgumentException expected){missing=true;}
                    check(missing,"Removing source data leaves prior place citations unavailable rather than rebound");
                    Library.Document bounded=add(library,"bounded.json",data);android.database.sqlite.SQLiteDatabase db=library.getWritableDatabase();
                    var insert=db.compileStatement("INSERT INTO osm_features(document_id,ordinal,element_key,payload,content) VALUES (?,?,?,?,?)");
                    // Store validated internal payload shape, then exceed the global scan-row bound.
                    String payload=library.osmFeature(bounded.id(),1).feature().json().toString();db.beginTransaction();try{for(int n=100;n<20_101;n++){insert.bindString(1,bounded.id());insert.bindLong(2,n);insert.bindString(3,"test/"+n);insert.bindString(4,payload);insert.bindString(5,"Bounded fixture");insert.executeInsert();}db.setTransactionSuccessful();}finally{db.endTransaction();insert.close();}
                    check(ask(library,"Where is Test Riverside Park?").status().equals("limit"),"Over-limit stored collections fail visibly instead of silently truncating search");
                }finally{for(Library.Document doc:library.documents())library.removeDocument(doc.id());}
            }
            try(Library real=new Library(test.getTargetContext(),null)) {
                try{add(real,"madrid-named-places.osm",asset("madrid-named-places.osm"));JSONArray cases=new JSONObject(asset("madrid-expected.json")).getJSONArray("cases");
                    for(int i=0;i<cases.length();i++){JSONObject expected=cases.getJSONObject(i);String question=expected.getString("question");long start=SystemClock.elapsedRealtime();var answer=ask(real,question);record(question,answer,SystemClock.elapsedRealtime()-start);
                        check(answer.status().equals("found")&&answer.matched()==expected.getInt("matched"),"Frozen public OSM subset matches independent candidate count: "+question);
                        JSONArray keys=expected.getJSONArray("expectedKeys");int offset=expected.isNull("referenceKey")?0:1;boolean same=answer.sources().size()==keys.length()+offset;
                        for(int k=0;k<keys.length()&&same;k++)same=real.resolve(answer.sources().get(k+offset).locator()).url().endsWith("/"+keys.getString(k));
                        check(same,"Frozen public OSM subset matches independent nearest-place identity/order: "+question);
                    }
                }finally{for(Library.Document doc:real.documents())real.removeDocument(doc.id());}
            }
            try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())existingDocs.add(d.id());}
            try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn t:chat.turns())existingTurns.add(t.id());}captured=true;
            activity=start();MainActivity app=activity;
            // A real empty model directory tests the no-model branch without moving installed user weights.
            android.content.Context emptyModelContext=new android.content.ContextWrapper(test.getTargetContext()){@Override public File getFilesDir(){return scratch;}};
            ModelStore absent=new ModelStore(emptyModelContext,ModelStore.BONSAI4);check(!absent.ready(),"Isolated model context has no installed generator");
            var models=MainActivity.class.getDeclaredField("models");models.setAccessible(true);test.runOnMainSync(()->{try{models.set(app,absent);}catch(Exception e){throw new RuntimeException(e);}});
            File uiFile=new File(scratch,"ui-places.json");Files.write(uiFile.toPath(),data.getBytes(StandardCharsets.UTF_8));
            test.runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(uiFile))));
            for(int i=0;i<400&&app.importInProgress();i++)SystemClock.sleep(50);test.waitForIdleSync();
            test.runOnMainSync(app::showChat);
            send(app,"Where is Test Riverside Park in Test North?");check(app.lastPlaces!=null&&app.lastPlaces.matched()==1&&app.lastAnswer==null,"Production chat answers a named park without a loaded model");screenshot("places-park.png");
            send(app,"Which restaurants are around Test Central Station in Test North within 150 m?");check(app.lastPlaces!=null&&app.lastPlaces.matched()==1&&app.lastPlaces.text().contains("Test Oak Restaurant"),"Production chat answers restaurant proximity from actual imported rows");screenshot("places-nearby.png");
            send(app,"Where can I find a museum?");check(app.lastPlaces.status().equals("clarify"),"Production chat asks for the missing area");
            send(app,"In Test North");check(app.lastPlaces.matched()==1&&app.lastPlaces.text().contains("Test Copper Museum"),"Production chat resolves the museum-area follow-up");screenshot("places-museum.png");
            PlaceQueries.Answer last=app.lastPlaces;ChatStore.Source source=last.sources().get(0);Library.Document sourceDoc;
            try(Library library=new Library(test.getTargetContext())){check(library.resolve(source.locator()).title().contains("Test Copper Museum"),"Chat source reference resolves the requested museum");sourceDoc=library.load(source.locator().documentId());}
            test.runOnMainSync(()->app.openDocument(sourceDoc,null,source.locator().ordinal()));for(int i=0;i<100&&!app.documentOpen;i++)SystemClock.sleep(50);test.waitForIdleSync();
            var root=test.getUiAutomation().getRootInActiveWindow();check(root!=null&&!root.findAccessibilityNodeInfosByText("Test Copper Museum").isEmpty(),"Exact OSM source is inspectable from a place answer");if(root!=null)root.recycle();screenshot("places-source.png");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();test.runOnMainSync(app::finish);SystemClock.sleep(400);activity=start();
            try(ChatStore chat=new ChatStore(test.getTargetContext())){check(chat.turns().stream().anyMatch(t->!existingTurns.contains(t.id())&&t.answer().contains("111 m")&&t.sources().size()==2),"Computed place answers and exact references survive Activity restart");}
            File realFile=new File(scratch,"madrid-ui.osm");Files.write(realFile.toPath(),asset("madrid-named-places.osm").getBytes(StandardCharsets.UTF_8));MainActivity realApp=activity;
            test.runOnMainSync(()->{try{models.set(realApp,absent);}catch(Exception e){throw new RuntimeException(e);}});
            test.runOnMainSync(()->realApp.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(realFile))));for(int i=0;i<400&&realApp.importInProgress();i++)SystemClock.sleep(50);test.waitForIdleSync();test.runOnMainSync(realApp::showChat);
            send(realApp,"Where is Plaza de la Lealtad?");check(realApp.lastPlaces.matched()==1&&realApp.lastAnswer==null,"Production chat locates the real imported park without model generation");
            send(realApp,"restaurants near Museo de Colecciones ICO within 500 m");check(realApp.lastPlaces.matched()==47&&realApp.lastPlaces.sources().size()==6&&realApp.lastAnswer==null,"Production chat returns five actual nearby restaurants from the frozen real subset");screenshot("places-real.png");
            send(realApp,"museums near Plaza de la Lealtad within 1000 m");check(realApp.lastPlaces.matched()==6&&realApp.lastAnswer==null,"Production chat answers a real-data museum query with deterministic source evidence");
            success=true;response.putString("stream","\nPASS places: "+passed+" checks.\n");
        }catch(Throwable e){try{checks.put(new JSONObject().put("error",android.util.Log.getStackTraceString(e)));}catch(Exception ignored){}response.putString("stream","\nFAIL places: "+android.util.Log.getStackTraceString(e));}
        finally {
            if(activity!=null){MainActivity app=activity;test.runOnMainSync(app::finish);SystemClock.sleep(300);}
            if(captured){try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())if(!existingDocs.contains(d.id()))library.removeDocument(d.id());}catch(Exception ignored){}
                try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn t:chat.turns())if(!existingTurns.contains(t.id()))chat.remove(t.id());}catch(Exception ignored){}}
            if(scratch!=null){File[] files=scratch.listFiles();if(files!=null)for(File file:files)file.delete();scratch.delete();}
            try{Files.write(new File(output,"place-checks.json").toPath(),new JSONObject().put("runId",run).put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("passed",success).put("checksPassed",passed).put("checks",checks).put("answers",answers).put("modelExecution",false).put("scope","Synthetic import/SQLite/chat/source checks plus a frozen prepared public Madrid OSM subset, with an isolated absent-model context in Outpost35. No live availability, full-area coverage, physical-device or broad accuracy claim.").toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        }
        test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,response);
    }
    private MainActivity start(){MainActivity app=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));for(int i=0;i<100&&app.findViewById(MainActivity.QUERY_ID)==null;i++)SystemClock.sleep(100);test.waitForIdleSync();return app;}
    private void send(MainActivity app,String question)throws Exception{long start=SystemClock.elapsedRealtime();test.runOnMainSync(()->app.sendMessage(question));for(int i=0;i<400&&!app.answerDone;i++)SystemClock.sleep(50);test.waitForIdleSync();if(app.lastPlaces!=null)record(question,app.lastPlaces,SystemClock.elapsedRealtime()-start);check(app.answerDone&&app.lastPlaces!=null,"Place response settles without native generation: "+question);}
    private void screenshot(String name)throws Exception{SystemClock.sleep(150);Bitmap b=test.getUiAutomation().takeScreenshot();if(b==null)throw new IllegalStateException("No screenshot");try(FileOutputStream f=new FileOutputStream(new File(output,name))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
}
