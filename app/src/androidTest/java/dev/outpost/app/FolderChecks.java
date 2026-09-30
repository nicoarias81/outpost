package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real content-provider traversal and app integration, entirely inside the emulator. */
final class FolderChecks {
    private final Instrumentation test;private final String run;
    private final JSONArray checks=new JSONArray();private int passed;private File output;
    FolderChecks(Instrumentation test,String run){this.test=test;this.run=run;}
    private void check(boolean value,String label)throws Exception{checks.put(new JSONObject().put("name",label).put("passed",value));if(!value)throw new AssertionError(label);passed++;}
    private Uri tree(String id){return DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,id);}
    private FolderImporter importer(Library library){return new FolderImporter(test.getTargetContext().getContentResolver(),new DocumentImporter(test.getTargetContext(),library));}
    private void cleanPdfs(Library library){for(Library.Document d:library.documents())if(library.metadata(d.id()).format().equals("pdf"))library.pdfFile(d.id()).delete();}
    void run() {
        Bundle response=new Bundle();boolean success=false;MainActivity activity=null;Instrumentation.ActivityMonitor monitor=null;
        android.content.SharedPreferences preferences=test.getTargetContext().getSharedPreferences("MainActivity",android.content.Context.MODE_PRIVATE);
        java.util.Map<String,?> beforePreferences=preferences.getAll();
        String migration="folder-migration-"+UUID.randomUUID()+".db";Set<String> existing=new HashSet<>();boolean captured=false;
        try {
            if(!run.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run ID required");
            output=new File(test.getTargetContext().getFilesDir(),"evidence/folders/"+run);if(output.exists()||!output.mkdirs())throw new IllegalStateException("Run already exists");
            check(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64"),"Tests run only in the x86_64 emulator");
            String[] permissions=test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions==null||permissions.length==0,"Folder selection adds no broad storage or network permission");
            Intent picker=MainActivity.folderPickerIntent();
            check(Intent.ACTION_OPEN_DOCUMENT_TREE.equals(picker.getAction()),"Folder action uses the system directory picker");
            check((picker.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(picker.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0,"Folder picker requests read access without source write access");
            String retained;
            try(Library old=new Library(test.getTargetContext(),migration)){retained=old.importText("keep.txt","Existing user document.").id();old.getWritableDatabase().execSQL("DROP TABLE imported_files");old.getWritableDatabase().setVersion(3);}
            try(Library upgraded=new Library(test.getTargetContext(),migration)){check(upgraded.getReadableDatabase().getVersion()==4,"Actual schema-3 database migrates to schema 4");check(upgraded.load(retained).body().equals("Existing user document."),"Schema upgrade preserves existing document bytes and identity");}
            grantFixtureAccess();
            try(Library library=new Library(test.getTargetContext(),null)) {
                try {
                    FolderImporter.Report first=importer(library).run(tree("root"),new DocumentImporter.Cancellation(),p->{});
                    JSONObject traversal=new JSONObject().put("imported",first.progress().imported()).put("failed",first.progress().failed()).put("skipped",first.progress().skipped());
                    JSONArray details=new JSONArray();for(FolderImporter.Issue issue:first.issues())details.put(new JSONObject().put("path",issue.path()).put("reason",issue.reason()));traversal.put("issues",details);
                    Files.write(new File(output,"traversal.json").toPath(),traversal.toString(2).getBytes(StandardCharsets.UTF_8));
                    Bundle diagnostic=new Bundle();diagnostic.putString("stream",traversal.toString()+"\n");test.sendStatus(0,diagnostic);
                    check(first.progress().imported()==6,"Recursively imports TXT, CSV, PDF and Markdown from multiple nested folders");
                    check(first.progress().failed()==3,"Malformed CSV, oversized file and unreadable subfolder are reported independently");
                    check(first.progress().skipped()==4,"Unsupported, virtual and repeated provider entries are skipped without looping");
                    check(!first.canceled()&&!first.limited(),"Finite cyclic provider graph completes without an artificial partial-success claim");
                    check(library.documents().stream().filter(d->d.title().endsWith("note.TXT")).count()==2,"Equal filenames in different subfolders remain separate documents");
                    check(library.documents().stream().anyMatch(d->d.source().contains("Manuals/More/guide.PDF")),"Full folder provenance is retained in source metadata");
                    check(!library.search("Nested hidden Markdown").isEmpty(),"Supported hidden files are included");
                    check(!library.search("Unknown size metadata").isEmpty(),"A missing provider file size is handled with streaming limits");
                    Library.Hit pdf=library.search("F-91").get(0);check(library.evidence(pdf).locator().kind().equals("page"),"Nested PDF remains page-addressable after folder import");
                    FolderImporter.Report again=importer(library).run(tree("root"),new DocumentImporter.Cancellation(),p->{});
                    check(again.progress().imported()==0&&again.progress().unchanged()==6&&library.documents().size()==6,"Repeating the folder does not duplicate unchanged documents");
                    Uri leaf=DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,"note");
                    DocumentImporter.Result individual=new DocumentImporter(test.getTargetContext(),library).importFile(leaf,"note.TXT","note.TXT",-1,new DocumentImporter.Cancellation());
                    check(!individual.added(),"Single-file and tree URIs identify the same unchanged provider document");
                    Library.Document removed=individual.document();library.removeDocument(removed.id());
                    check(new DocumentImporter(test.getTargetContext(),library).importFile(leaf,"note.TXT","note.TXT",-1,new DocumentImporter.Cancellation()).added(),"A removed document can be imported again without a stale deduplication record");
                    String change="change-"+run;Uri changing=DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,change);
                    DocumentImporter one=new DocumentImporter(test.getTargetContext(),library);
                    Library.Document a=one.importFile(changing,"changing.txt","changing.txt",-1,new DocumentImporter.Cancellation()).document();
                    Library.Document b=one.importFile(changing,"changing.txt","changing.txt",-1,new DocumentImporter.Cancellation()).document();
                    check(!a.id().equals(b.id())&&library.load(a.id()).body().contains("Original")&&b.body().contains("Updated"),"Changed source bytes create a new snapshot and preserve older citations");
                    FolderImporter.Report empty=importer(library).run(tree("empty"),new DocumentImporter.Cancellation(),p->{});
                    check(empty.progress().imported()==0&&empty.progress().failed()==0,"Empty directory is a successful zero-file import");
                }finally{cleanPdfs(library);}
            }
            try(Library library=new Library(test.getTargetContext(),null)) {
                try {
                    DocumentImporter.Cancellation cancel=new DocumentImporter.Cancellation();
                    FolderImporter.Report partial=importer(library).run(tree("root"),cancel,p->{if(p.imported()>=1)cancel.cancel();});
                    check(partial.canceled()&&partial.progress().imported()==1&&library.documents().size()==1,"Cancel keeps completed files and prevents further imports");
                    FolderImporter.Report continued=importer(library).run(tree("root"),new DocumentImporter.Cancellation(),p->{});
                    check(continued.progress().imported()==5&&continued.progress().unchanged()==1,"Selecting the folder again continues safely after cancellation");
                }finally{cleanPdfs(library);}
            }
            try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())existing.add(d.id());}captured=true;
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity app=activity;
            for(int i=0;i<100&&app.findViewById(MainActivity.QUERY_ID)==null;i++)SystemClock.sleep(100);
            test.runOnMainSync(app::showSettings);test.waitForIdleSync();
            check(app.findViewById(R.id.folder_import)!=null,"Settings exposes Add folder");screenshot("folder-settings.png");
            Intent selected=new Intent().setData(tree("root")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            monitor=new Instrumentation.ActivityMonitor(new IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),new Instrumentation.ActivityResult(Activity.RESULT_OK,selected),true);test.addMonitor(monitor);
            test.runOnMainSync(()->app.findViewById(R.id.folder_import).performClick());
            for(int i=0;i<300&&app.lastFolderReport==null;i++)SystemClock.sleep(100);
            for(int i=0;i<300&&app.importInProgress();i++)SystemClock.sleep(100);test.waitForIdleSync();
            check(monitor.getHits()==1,"Add folder launches the real picker contract");
            check(app.lastFolderReport!=null&&app.lastFolderReport.progress().imported()==6,"Picker result imports the complete supported tree through the Activity");
            android.view.accessibility.AccessibilityNodeInfo window=test.getUiAutomation().getRootInActiveWindow();
            check(window!=null&&!window.findAccessibilityNodeInfosByText("Folder import summary").isEmpty(),"Activity presents an English import summary");if(window!=null)window.recycle();screenshot("folder-summary.png");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();test.runOnMainSync(app::showDocuments);test.waitForIdleSync();screenshot("folder-documents.png");
            check(app.findViewById(R.id.folder_report)!=null,"Last folder report remains accessible from document management");
            test.runOnMainSync(()->app.importFolder(tree("many")));
            for(int i=0;i<400&&app.importInProgress();i++)SystemClock.sleep(100);test.waitForIdleSync();
            check(app.lastFolderReport!=null&&app.lastFolderReport.progress().imported()==60,"A larger folder imports every supported file");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();test.runOnMainSync(app::showDocuments);test.waitForIdleSync();
            check(app.findViewById(R.id.documents_next)!=null&&app.findViewById(R.id.documents_next).isEnabled(),"Document management pages large collections instead of rendering every card");
            test.runOnMainSync(()->app.findViewById(R.id.documents_next).performClick());test.waitForIdleSync();
            check(app.findViewById(R.id.documents_previous).isEnabled(),"Later document pages remain reachable");screenshot("folder-paged.png");
            success=true;response.putString("stream","\nPASS folders: "+passed+" checks.\n");
        }catch(Throwable error){try{checks.put(new JSONObject().put("error",android.util.Log.getStackTraceString(error)));}catch(Exception ignored){}response.putString("stream","\nFAIL folders: "+android.util.Log.getStackTraceString(error));}
        finally {
            if(monitor!=null)test.removeMonitor(monitor);
            if(activity!=null){MainActivity app=activity;test.runOnMainSync(app::finish);SystemClock.sleep(500);}
            if(captured)try(Library library=new Library(test.getTargetContext())){for(Library.Document d:library.documents())if(!existing.contains(d.id()))library.removeDocument(d.id());}catch(Exception ignored){}
            android.content.SharedPreferences.Editor restore=preferences.edit();
            for(String key:List.of("folder_operation","folder_running","folder_summary")){Object old=beforePreferences.get(key);if(old instanceof String text)restore.putString(key,text);else if(old instanceof Boolean flag)restore.putBoolean(key,flag);else restore.remove(key);}restore.commit();
            test.getTargetContext().deleteDatabase(migration);
            try{JSONObject report=new JSONObject().put("runId",run).put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("passed",success).put("checksPassed",passed).put("checks",checks).put("scope","Real Android DocumentsProvider, SQLite and Activity checks on Outpost35; synthetic files; no model execution or physical phone.");Files.write(new File(output,"folder-checks.json").toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        }
        test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,response);
    }
    private void grantFixtureAccess()throws Exception {
        // Instrumentation startup revokes earlier transient grants. Issue them after the target is running.
        String command="am start -W -n dev.outpost.app.test/dev.outpost.app.FolderGrantActivity --es run_id "+run+" --es operation grant";
        try(android.os.ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);
            java.io.FileInputStream input=new java.io.FileInputStream(fd.getFileDescriptor())) {
            String output=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            if(!output.contains("Status: ok"))throw new IllegalStateException("Fixture grant activity failed: "+output);
        }
    }
    private void screenshot(String name)throws Exception{SystemClock.sleep(150);Bitmap b=test.getUiAutomation().takeScreenshot();if(b==null)throw new IllegalStateException("No screenshot");try(FileOutputStream f=new FileOutputStream(new File(output,name))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
}
