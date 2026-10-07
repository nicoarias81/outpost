package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
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

final class OsmChecks {
    private final Instrumentation test;private final String run;private final boolean generate;
    private final JSONArray checks=new JSONArray(),answers=new JSONArray();private int passed;private File output;
    OsmChecks(Instrumentation test,String run,boolean generate){this.test=test;this.run=run;this.generate=generate;}
    private void check(boolean value,String label)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",value));if(!value)throw new AssertionError(label);passed++;}
    private interface Action{void run()throws Exception;}
    private void rejects(Action action,String label)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,label);}
    private void text(File file,String value)throws Exception{Files.write(file.toPath(),value.getBytes(StandardCharsets.UTF_8));}
    void run() {
        boolean success=false,captured=false;MainActivity activity=null;Bundle response=new Bundle();
        String migration="osm-migration-"+UUID.randomUUID()+".db";File scratch=null;
        Set<String> existingDocs=new HashSet<>(),existingTurns=new HashSet<>();ModelStore.Spec previous=ModelStore.selected(test.getTargetContext());
        android.content.SharedPreferences preferences=test.getTargetContext().getSharedPreferences("MainActivity",android.content.Context.MODE_PRIVATE);java.util.Map<String,?> savedPreferences=preferences.getAll();
        try {
            if(!run.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run ID required");
            output=new File(test.getTargetContext().getFilesDir(),"evidence/osm/"+run);if(output.exists()||!output.mkdirs())throw new IllegalStateException("Run directory exists");
            scratch=new File(test.getTargetContext().getCacheDir(),run);if(!scratch.mkdir())throw new IllegalStateException("Scratch directory exists");
            File xml=new File(scratch,"places.osm"),json=new File(scratch,"water.json"),bad=new File(scratch,"bad.osm");text(xml,OsmFixtures.XML);text(json,OsmFixtures.JSON);
            check(TestTargets.admitted(test),"OSM checks use an admitted synthetic target");
            String[] permissions=test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions==null||permissions.length==0,"OSM imports add no Internet or location permission");
            OsmImporter.Extract parsed=OsmImporter.parse(xml,()->false);
            check(parsed.features().size()==3&&parsed.objects()==6&&parsed.omitted()==3,"XML indexes tagged current features and omits geometry-only/deleted objects");
            check(parsed.snapshot().equals("2026-09-01T12:00:00Z")&&!parsed.bounds().isEmpty(),"Extract timestamp and declared bounds are retained separately");
            OsmImporter.Feature pharmacy=parsed.features().get(0),cafe=parsed.features().get(1),park=parsed.features().get(2);
            check(pharmacy.key().equals("node/10")&&cafe.key().equals("way/10"),"Node and way IDs remain distinct even with the same number");
            check(pharmacy.latitude()==40.01&&pharmacy.tags().get("opening_hours").equals("Mo-Fr 09:00-18:00"),"Coordinates and original opening-hours tag are preserved");
            check(!pharmacy.tags().containsKey("wheelchair"),"Missing tags remain absent rather than false");
            check(cafe.latitude()!=null&&Math.abs(cafe.latitude()-40.001)<1e-8&&cafe.positionKind().contains("derived"),"Complete way geometry yields an explicitly approximate center");
            check(park.latitude()==null&&park.longitude()==null,"Missing relation geometry does not invent coordinates");
            check(pharmacy.text(parsed.snapshot()).contains("not a live")&&parsed.summary().contains("ODbL"),"Evidence retains snapshot limits and OSM attribution");
            OsmImporter.Extract overpass=OsmImporter.parse(json,()->false);
            check(overpass.features().size()==2&&overpass.features().get(1).positionKind().contains("exported"),"Overpass JSON nodes and exported way centers are supported");
            text(bad,OsmFixtures.XML.replace("ref=\"2\"","ref=\"999\""));check(OsmImporter.parse(bad,()->false).features().get(1).latitude()==null,"Incomplete way geometry retains unknown coordinates");
            text(bad,OsmFixtures.XML.replace("lat=\"40.01\"","lat=\"NaN\""));rejects(()->OsmImporter.parse(bad,()->false),"Non-finite coordinates are rejected");
            text(bad,OsmFixtures.XML.replace("id=\"99\"","id=\"10\""));rejects(()->OsmImporter.parse(bad,()->false),"Ambiguous duplicate object IDs are rejected");
            text(bad,"<!DOCTYPE osm [<!ENTITY x SYSTEM 'file:///data/local/tmp/not-readable'>]><osm version='0.6'/>");rejects(()->OsmImporter.parse(bad,()->false),"DTD/external-entity input is rejected before XML parsing");
            text(bad,"<osmChange version='0.6'/>");rejects(()->OsmImporter.parse(bad,()->false),"OSM change files cannot masquerade as current extracts");
            text(bad,"{\"version\":0.6,\"remark\":\"runtime error\",\"elements\":[]}");rejects(()->OsmImporter.parse(bad,()->false),"Overpass error/partial output is rejected");
            text(bad,"{\"type\":\"FeatureCollection\",\"features\":[]}");rejects(()->OsmImporter.parse(bad,()->false),"GeoJSON/arbitrary JSON is not silently misread as Overpass data");
            text(bad,"{\"version\":0.6,\"extra\":"+"[".repeat(40)+"0"+"]".repeat(40)+",\"elements\":[]}");rejects(()->OsmImporter.parse(bad,()->false),"Excessive JSON nesting is bounded before parsing");
            text(bad,"<osm version='0.6'><node id='1'><tag k='name' v='"+"x".repeat(17000)+"'/></node></osm>");rejects(()->OsmImporter.parse(bad,()->false),"Oversized XML attribute tokens are rejected");
            text(bad,OsmFixtures.JSON.replace("\"type\":\"way\",\"id\":31","\"type\":\"node\",\"id\":31"));rejects(()->OsmImporter.parse(bad,()->false),"A node cannot silently use an ambiguous center representation");
            rejects(()->OsmImporter.parse(xml,()->true),"Parser honors pre-cancellation");
            String kept;try(Library old=new Library(test.getTargetContext(),migration)){kept=old.importText("keep.txt","Existing document remains.").id();old.getWritableDatabase().execSQL("DROP TABLE osm_features");old.getWritableDatabase().setVersion(4);}
            try(Library upgraded=new Library(test.getTargetContext(),migration)){check(upgraded.getReadableDatabase().getVersion()==5&&upgraded.load(kept).body().equals("Existing document remains."),"Schema4-to5 migration preserves existing document identity/content");}
            try(Library library=new Library(test.getTargetContext(),null)) {
                try {
                    DocumentImporter importer=new DocumentImporter(test.getTargetContext(),library);Uri uri=Uri.fromFile(xml);
                    Library.Document doc=importer.importFile(uri,"places.osm","places.osm",-1,new DocumentImporter.Cancellation()).document();
                    check(library.osmFeatureCount(doc.id())==3,"Typed OSM features are stored separately from the small document body");
                    java.util.concurrent.atomic.AtomicInteger polls=new java.util.concurrent.atomic.AtomicInteger();
                    rejects(()->library.importFileSnapshot("canceled.osm","osm",null,null,xml,"canceled-snapshot",Evidence.sha256(OsmFixtures.XML),parsed,()->polls.incrementAndGet()>=3),"Cancellation during indexing rolls back the whole new extract");
                    check(library.documents().size()==1&&library.osmFeatureCount(doc.id())==3,"Partial OSM index cancellation preserves the prior document and feature rows");
                    check(java.util.Arrays.equals(Files.readAllBytes(xml.toPath()),Files.readAllBytes(library.osmFile(doc.id()).toPath())),"The exact original OSM extract is retained offline");
                    Library.Hit hit=library.search("Fixture Pharmacy").get(0);Evidence evidence=library.evidence(hit);
                    check(evidence.locator().kind().equals("element")&&evidence.title().contains("node/10")&&evidence.content().contains("Sample Lane"),"Retrieval resolves an exact source feature with its OSM identity");
                    check(evidence.url().equals("https://www.openstreetmap.org/node/10"),"Feature source URL uses the original typed ID");
                    check(!library.search("pharmacies").isEmpty(),"Common place-category aliases remain searchable offline");
                    check(!importer.importFile(uri,"places.osm","places.osm",-1,new DocumentImporter.Cancellation()).added(),"Repeating identical OSM bytes does not duplicate the extract");
                    text(xml,"<osm version='0.6'><node id='0'/></osm>");rejects(()->importer.importFile(uri,"places.osm","places.osm",-1,new DocumentImporter.Cancellation()),"Malformed replacement cannot partially overwrite an existing dataset");
                    check(library.osmFeatureCount(doc.id())==3&&library.resolve(evidence.locator()).content().contains("Sample Lane"),"A rejected replacement preserves existing feature citations");
                    text(xml,OsmFixtures.XML.replace("Sample Lane","New Sample Lane"));Library.Document newer=importer.importFile(uri,"places.osm","places.osm",-1,new DocumentImporter.Cancellation()).document();
                    check(!newer.id().equals(doc.id())&&library.resolve(evidence.locator()).content().contains("addr:street: Sample Lane"),"Changed extracts create new snapshots without rebinding old citations");
                    library.removeDocument(doc.id());check(!library.osmFile(doc.id()).exists()&&library.osmFeatureCount(doc.id())==0,"Removing an extract removes its feature index and original file");
                    rejects(()->library.resolve(evidence.locator()),"Removed OSM citations report unavailable");
                }finally{for(Library.Document d:library.documents())if(library.metadata(d.id()).format().equals("osm"))library.removeDocument(d.id());}
            }
            text(xml,OsmFixtures.XML);
            grant();
            try(Library library=new Library(test.getTargetContext(),null)) {
                try{Uri tree=DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"osm-root");FolderImporter.Report report=new FolderImporter(test.getTargetContext().getContentResolver(),new DocumentImporter(test.getTargetContext(),library)).run(tree,new DocumentImporter.Cancellation(),p->{});check(report.progress().imported()==2&&report.progress().failed()==0,"Recursive SAF import accepts both OSM XML and Overpass JSON");}
                finally{for(Library.Document d:library.documents())library.removeDocument(d.id());}
            }
            try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())existingDocs.add(d.id());}
            try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn turn:chat.turns())existingTurns.add(turn.id());}captured=true;
            if(generate){ModelStore.select(test.getTargetContext(),ModelStore.BONSAI4);check(new ModelStore(test.getTargetContext()).ready(),"Verified Bonsai 4B is ready for the optional OSM chat check");}
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity app=activity;
            for(int i=0;i<100&&app.findViewById(MainActivity.QUERY_ID)==null;i++)SystemClock.sleep(100);test.waitForIdleSync();
            test.runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(xml))));
            for(int i=0;i<400&&app.importInProgress();i++)SystemClock.sleep(100);test.waitForIdleSync();
            Library.Document loaded;
            try(Library library=new Library(test.getTargetContext())){loaded=library.documents().stream().filter(d->!existingDocs.contains(d.id())&&library.metadata(d.id()).format().equals("osm")).findFirst().orElseThrow();check(library.osmFeatureCount(loaded.id())==3,"Add file imports and indexes the OSM extract through the Activity");}
            test.runOnMainSync(()->app.openDocument(loaded,null));for(int i=0;i<100&&!app.documentOpen;i++)SystemClock.sleep(100);test.waitForIdleSync();SystemClock.sleep(200);screenshot("osm-browser.png");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            test.runOnMainSync(()->app.openDocument(loaded,null,1));for(int i=0;i<100&&!app.documentOpen;i++)SystemClock.sleep(100);test.waitForIdleSync();SystemClock.sleep(150);
            var root=test.getUiAutomation().getRootInActiveWindow();check(root!=null&&!root.findAccessibilityNodeInfosByText("OpenStreetMap contributors").isEmpty()&&!root.findAccessibilityNodeInfosByText("Sample Lane").isEmpty(),"Source reader shows original feature facts with OSM attribution");if(root!=null)root.recycle();screenshot("osm-source.png");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            if(generate){test.runOnMainSync(app::showChat);test.runOnMainSync(()->app.sendMessage("Which street is Fixture Pharmacy on? Cite the OSM source."));for(int i=0;i<1400&&!app.answerDone;i++)SystemClock.sleep(100);test.waitForIdleSync();NativeEngine.Result result=app.lastAnswer;
                answers.put(new JSONObject().put("text",result==null?"":result.text()).put("system",app.lastSystem==null?"":app.lastSystem).put("user",app.lastPrepared==null?"":app.lastPrepared.user()).put("tokens",result==null?0:result.tokens()).put("totalMs",result==null?0:result.totalMs()).put("reason",result==null?-1:result.reason()));screenshot("osm-chat.png");
                check(app.answerDone&&result!=null&&result.text().contains("Sample Lane")&&result.text().contains("[1]"),"Real offline chat answers from the imported OSM feature and cites its source");
                check(!result.text().toLowerCase(java.util.Locale.ROOT).matches("(?s).*street.{0,40}(unknown|unavailable|not available|not recorded|not supplied|not provided).*"),"OSM narration does not deny the supplied street name");
            }
            success=true;response.putString("stream","\nPASS OSM: "+passed+" checks.\n");
        }catch(Throwable error){try{checks.put(new JSONObject().put("error",android.util.Log.getStackTraceString(error)));}catch(Exception ignored){}response.putString("stream","\nFAIL OSM: "+android.util.Log.getStackTraceString(error));}
        finally {
            if(activity!=null){MainActivity app=activity;test.runOnMainSync(app::finish);SystemClock.sleep(400);}
            if(captured){try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())if(!existingDocs.contains(d.id()))library.removeDocument(d.id());}catch(Exception ignored){}
                try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn turn:chat.turns())if(!existingTurns.contains(turn.id()))chat.remove(turn.id());}catch(Exception ignored){}}
            ModelStore.select(test.getTargetContext(),previous);test.getTargetContext().deleteDatabase(migration);
            if(scratch!=null){File[] files=scratch.listFiles();if(files!=null)for(File file:files)file.delete();scratch.delete();}
            var edit=preferences.edit();for(String key:List.of("folder_operation","folder_running","folder_summary")){Object old=savedPreferences.get(key);if(old instanceof String s)edit.putString(key,s);else if(old instanceof Boolean b)edit.putBoolean(key,b);else edit.remove(key);}edit.commit();
            try{Files.write(new File(output,"osm-checks.json").toPath(),new JSONObject().put("runId",run).put("version",test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0).versionName).put("passed",success).put("checksPassed",passed).put("checks",checks).put("answers",answers).put("modelExecution",generate).put("scope","Synthetic OSM/Overpass fixtures, actual SQLite/SAF/Activity on an admitted synthetic target. Not real-place accuracy or routing validation.").toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        }
        test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,response);
    }
    private void grant()throws Exception {FixtureGrants.grant(test,run);}
    private void screenshot(String name)throws Exception{SystemClock.sleep(400);Bitmap b=test.getUiAutomation().takeScreenshot();if(b==null)throw new IllegalStateException("No screenshot");try(FileOutputStream f=new FileOutputStream(new File(output,name))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
}
