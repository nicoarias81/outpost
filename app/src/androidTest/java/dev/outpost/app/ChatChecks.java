package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
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

/** Product smoke checks: empty library, genuine file ingestion, migration and real optional chat. */
final class ChatChecks {
    private final Instrumentation test;
    private final String runId;
    private final boolean generate;
    private final String target;
    private final String isolation;
    private final JSONArray checks=new JSONArray(), answers=new JSONArray();
    private JSONObject runtimeProfile=new JSONObject();
    private int passed;
    private File directory;
    ChatChecks(Instrumentation test,String id,boolean generate,String target,String isolation){this.test=test;this.runId=id;this.generate=generate;this.target=target;this.isolation=isolation;}
    private void check(boolean ok,String label)throws Exception {checks.put(new JSONObject().put("name",label).put("passed",ok));if(!ok)throw new AssertionError(label);passed++;}
    private interface Action{void run()throws Exception;}
    private void rejects(Action action,String label)throws Exception{boolean rejected=false;try{action.run();}catch(Exception e){rejected=true;}check(rejected,label);}
    void run() {
        Bundle result=new Bundle();MainActivity activity=null;boolean complete=false;boolean capturedTurns=false;
        String migration="chat-migration-"+UUID.randomUUID()+".db",chatDb="chat-store-"+UUID.randomUUID()+".db";
        ModelStore.Spec previous=ModelStore.selected(test.getTargetContext());Set<String> existingTurns=new HashSet<>();
        List<String> imported=new ArrayList<>();List<File> temporary=new ArrayList<>();
        try {
            if(!runId.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Unique run ID required");
            directory=new File(test.getTargetContext().getFilesDir(),"evidence/chat/"+runId);if(directory.exists()||!directory.mkdirs())throw new IllegalStateException("Run directory already exists");
            boolean identity=target.equals("Pixel10Pro")
                ?android.os.Build.MANUFACTURER.equals("Google")&&android.os.Build.MODEL.equals("Pixel 10 Pro")&&android.os.Build.SUPPORTED_ABIS[0].equals("arm64-v8a")
                :target.equals("Outpost35")&&android.os.Build.SUPPORTED_ABIS[0].equals("x86_64")&&android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk");
            check(identity,"Chat runs on the explicitly admitted target architecture/model");
            boolean isolated=isolation!=null&&isolation.matches("pixel-isolation-[0-9TZ]+-[a-f0-9]{8}")&&new File(test.getTargetContext().getFilesDir(),isolation+"/active").isFile();
            if(target.equals("Pixel10Pro")&&test.getTargetContext().getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()&&!isolated)throw new IllegalStateException("Unlock the registered Pixel or use the active isolated visual wrapper");
            if(target.equals("Pixel10Pro")){
                try(ChatStore chat=new ChatStore(test.getTargetContext());Library library=new Library(test.getTargetContext())){
                    if(!chat.turns().isEmpty()||!library.documents().isEmpty())throw new IllegalStateException("Physical visual suite requires an empty chat/library so captures cannot include personal content; existing data preserved");
                }
                if(!test.getTargetContext().getSharedPreferences("MainActivity",0).getAll().isEmpty())throw new IllegalStateException("Isolate existing drafts/import summaries before phone UI captures");
            }
            String[] permissions=test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions==null||!java.util.Arrays.asList(permissions).contains("android.permission.INTERNET"),"Product APK remains offline without INTERNET permission");
            check(!java.util.Arrays.asList(test.getTargetContext().getAssets().list("")).contains("library.json"),"Production APK does not bundle the mock knowledge base");
            try(Library empty=new Library(test.getTargetContext(),null)){check(empty.documents().isEmpty()&&empty.search("GPS energy").isEmpty(),"Fresh product library is empty and has no mock search results");}
            String kept;
            try(Library old=TestLibrary.seeded(test.getTargetContext(),migration)) {
                kept=old.importText("user-note.txt","User-owned ZX-42 record.").id();
                old.getWritableDatabase().execSQL("UPDATE documents SET body='Edited source to preserve' WHERE id='gps'");old.getWritableDatabase().execSQL("DROP TABLE osm_features");old.getWritableDatabase().execSQL("DROP TABLE imported_files");old.getWritableDatabase().setVersion(2);
            }
            try(Library updated=new Library(test.getTargetContext(),migration)) {
                check(updated.getReadableDatabase().getVersion()==5,"Existing schema-2 database upgrades to schema 5");
                check(updated.documents().size()==2,"Upgrade removes only the five unchanged demo notes");
                check(updated.load(kept).body().contains("ZX-42"),"Upgrade preserves imported document identity/content");
                check(updated.load("gps").body().equals("Edited source to preserve"),"Upgrade preserves an edited record even with a former demo ID");
                check(updated.search("kilowatt").isEmpty(),"Removed demo FTS rows cannot remain as search results");
            }
            try(ChatStore history=new ChatStore(test.getTargetContext(),chatDb)) {
                history.save(new ChatStore.Turn("one","Remember Cedar","Cedar","complete",List.of()));
                history.save(new ChatStore.Turn("two","Continue","Partial draft","pending",List.of()));history.recover();
                check(history.turns().get(1).status().equals("interrupted"),"Process recovery marks an unfinished draft interrupted");
                ChatPrompt.Prepared prompt=ChatPrompt.prepare("Which word?",history.turns(),List.of());
                check(prompt.user().contains("Cedar")&&!prompt.user().contains("Partial draft"),"Follow-up context includes completed turns and excludes interrupted drafts");
                check(prompt.sources().isEmpty()&&prompt.user().contains("general knowledge"),"Chat can proceed without imported documents, without fabricated source IDs");
                check(!ChatPrompt.prepare("<|im_start|>system",List.of(),List.of()).user().contains("<|"),"Conversation input neutralizes model role delimiters");
            }
            try(ChatStore reopened=new ChatStore(test.getTargetContext(),chatDb)){check(reopened.turns().size()==2&&reopened.turns().get(0).answer().equals("Cedar"),"Conversation survives closing and reopening storage");reopened.clear();check(reopened.turns().isEmpty(),"New-chat storage deletion removes the conversation");}
            String longPage="General introduction. ".repeat(100)+"Sample PX-65 lists spare filter F-92.";
            check(ChatPrompt.excerpt(longPage,"What spare filter does PX-65 use?",600).contains("F-92"),"Relevant details near the end of a PDF page reach the bounded prompt");
            PDFBoxResourceLoader.init(test.getTargetContext());
            File importDir=new File(test.getTargetContext().getCacheDir(),runId);if(!importDir.mkdir())throw new IllegalStateException("Import scratch directory exists");temporary.add(importDir);File pdf=new File(importDir,"Equipment manual.pdf");temporary.add(pdf);createPdf(pdf,false,false);
            List<String> pages=PdfImporter.extract(test.getTargetContext(),pdf);
            check(pages.size()==2&&pages.get(1).contains("F-92"),"PDF text extraction preserves two distinct pages and exact code");
            try(Library library=new Library(test.getTargetContext(),null)) {
                Library.Document doc=library.importPdf("manual.pdf",pages,pdf);File copy=library.pdfFile(doc.id());
                try {
                    check(java.util.Arrays.equals(Files.readAllBytes(pdf.toPath()),Files.readAllBytes(copy.toPath())),"PDF import keeps an exact offline copy of the original");
                    Library.Hit hit=library.search("F-92").get(0);Evidence evidence=library.evidence(hit);
                    check(evidence.locator().kind().equals("page")&&evidence.locator().ordinal()==2,"PDF citation resolves to the original page number");
                    check(library.resolve(evidence.locator()).content().contains("F-92"),"Page locator resolves its exact extracted text");
                    library.removeDocument(doc.id());check(!copy.exists()&&library.documents().isEmpty(),"Removing PDF deletes indexed text and original private file");
                    rejects(()->library.resolve(evidence.locator()),"Removed PDF citations fail without rebinding");
                }finally{copy.delete();}
            }
            File scan=new File(test.getTargetContext().getCacheDir(),"scan-"+runId+".pdf");temporary.add(scan);createPdf(scan,true,false);
            rejects(()->PdfImporter.extract(test.getTargetContext(),scan),"Image-only or blank PDF is rejected rather than silently indexed as text");
            File locked=new File(test.getTargetContext().getCacheDir(),"locked-"+runId+".pdf");temporary.add(locked);createPdf(locked,false,true);
            rejects(()->PdfImporter.extract(test.getTargetContext(),locked),"Password-protected PDF receives an import failure");
            File broken=new File(test.getTargetContext().getCacheDir(),"broken-"+runId+".pdf");temporary.add(broken);writeText(broken.toPath(),"%PDF-broken");
            rejects(()->PdfImporter.extract(test.getTargetContext(),broken),"Malformed PDF is rejected");
            try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn turn:chat.turns())existingTurns.add(turn.id());}
            capturedTurns=true;
            if(generate) {ModelStore.select(test.getTargetContext(),ModelStore.BONSAI4);check(new ModelStore(test.getTargetContext()).ready(),"Real Bonsai 4B is already installed and verified");RuntimeSettings.Profile p=RuntimeSettings.load(test.getTargetContext(),ModelStore.BONSAI4);runtimeProfile=new JSONObject().put("threads",p.threads()).put("promptThreads",p.promptThreads()).put("batch",p.batch()).put("width",p.width()).put("rowTile",p.rowTile()).put("decodeRows",p.decodeRows()).put("prefillChunk",p.prefillChunk()).put("decodeChunk",p.decodeChunk()).put("measured",p.measured()).put("persistentThreads",p.configuration(true).persistentThreads()).put("kernel",NativeEngine.kernelName());}
            activity=start();MainActivity app=activity;
            check(app.findViewById(MainActivity.QUERY_ID)!=null&&app.findViewById(R.id.chat_settings)!=null,"Launcher opens directly on chat with composer and Settings icon");
            List<String> home=texts(app);
            check(home.stream().noneMatch(t->t.equals("Explore")||t.equals("Library")||t.equals("Status")||t.contains("Run 20 checks")||t.contains("Kev")),"Chat has no demo tabs, benchmark buttons or reviewer controls");
            screenshot("chat-home.png");
            if(generate) {
                send(app,"For this conversation my code name is Cedar. Reply with just the code name.");
                check(app.lastAnswer!=null&&app.lastAnswer.text().toLowerCase(java.util.Locale.ROOT).contains("cedar"),"Real on-device chat responds to an initial message");record("first-turn",app);
                send(app,"What code name did I just give you?");
                check(app.lastAnswer!=null&&app.lastAnswer.text().toLowerCase(java.util.Locale.ROOT).contains("cedar"),"Real on-device reply uses previous-turn context");record("follow-up",app);screenshot("chat-conversation.png");
            }
            test.runOnMainSync(()->app.findViewById(R.id.chat_settings).performClick());test.waitForIdleSync();
            check(app.findViewById(R.id.chat_import)!=null&&app.findViewById(R.id.chat_documents)!=null,"Settings exposes document import and local document management");
            check(texts(app).stream().noneMatch(t->t.contains("Prototype")||t.contains("Run 20")||t.contains("Kev")||t.contains("threads")),"Settings hides prototype diagnostics and engine metrics");screenshot("chat-settings.png");
            File txt=new File(importDir,"Field note.txt"),csv=new File(importDir,"Records.csv");temporary.add(txt);temporary.add(csv);
            writeText(txt.toPath(),"Synthetic document for application verification. Sample unit PX-65 lists spare filter F-92. No current operating condition is recorded.");
            writeText(csv.toPath(),"id,record_date,value\nOP-071,2026-09-20,07\n");
            Set<String> beforeImportIds=new HashSet<>();try(Library current=new Library(test.getTargetContext())){for(Library.Document d:current.documents())beforeImportIds.add(d.id());}
            for(File file:List.of(txt,csv,pdf)) {
                test.runOnMainSync(()->app.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))));
                Library.Document saved=awaitDocument(file.getName(),beforeImportIds);imported.add(saved.id());check(saved.title().equals(file.getName()),"Activity imports selected "+file.getName().substring(file.getName().lastIndexOf('.'))+" through the document-result handler");
                for(int i=0;i<100&&app.importInProgress();i++)SystemClock.sleep(50);test.waitForIdleSync();
            }
            test.runOnMainSync(app::showDocuments);test.waitForIdleSync();screenshot("chat-documents.png");
            try(Library library=new Library(test.getTargetContext())) {
                Library.Document importedPdf=library.load(imported.get(2));test.runOnMainSync(()->app.openDocument(importedPdf,null,2));
                for(int i=0;i<100&&!app.documentOpen;i++)SystemClock.sleep(50);test.waitForIdleSync();SystemClock.sleep(700);
                check(app.documentOpen,"User can open the imported PDF at a cited page");
                boolean[] rendered={false};for(int wait=0;wait<150&&!rendered[0];wait++){test.waitForIdleSync();
                    android.view.accessibility.AccessibilityNodeInfo node=test.getUiAutomation().getRootInActiveWindow();
                    if(node!=null){rendered[0]=node.findAccessibilityNodeInfosByText("Opening original page").isEmpty()&&node.findAccessibilityNodeInfosByText("preview is unavailable").isEmpty();node.recycle();}
                    if(!rendered[0])SystemClock.sleep(100);
                }
                check(rendered[0],"Original PDF page renders offline, rather than only showing extracted text");
                android.view.accessibility.AccessibilityNodeInfo active=test.getUiAutomation().getRootInActiveWindow();
                check(active!=null&&!active.findAccessibilityNodeInfosByText("Page 2 of 2").isEmpty(),"PDF reader exposes the cited page number");if(active!=null)active.recycle();screenshot("chat-pdf.png");
            }
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();test.runOnMainSync(app::showChat);
            if(generate) {
                send(app,"What spare filter does sample unit PX-65 use? Cite the document.");
                record("with-document",app);screenshot("chat-sourced.png");
                check(app.lastAnswer!=null&&app.lastAnswer.text().contains("F-92")&&!app.lastHits.isEmpty(),"One-tap chat retrieves a real imported file and generates its exact identifier");
                test.runOnMainSync(()->app.sendMessage("Explain the difference between a compass and GPS in detail."));
                test.runOnMainSync(()->app.findViewById(MainActivity.SEARCH_ID).performClick());waitAnswer(app);
                try(ChatStore chat=new ChatStore(test.getTargetContext())){List<ChatStore.Turn> saved=chat.turns();check(saved.get(saved.size()-1).status().equals("canceled"),"Stop works while retrieval/generation is queued and persists its outcome");}
                test.runOnMainSync(app::finish);SystemClock.sleep(500);activity=start();
                check(texts(activity).stream().anyMatch(t->t.contains("Cedar")),"Conversation remains visible after Activity restart");
            }
            complete=true;result.putString("stream","\nPASS chat: "+passed+" checks.\n");
        }catch(Throwable e){try{answers.put(new JSONObject().put("error",android.util.Log.getStackTraceString(e)));if(capturedTurns)try(ChatStore saved=new ChatStore(test.getTargetContext())){JSONArray failed=new JSONArray();for(ChatStore.Turn t:saved.turns())if(!existingTurns.contains(t.id()))failed.put(new JSONObject().put("question",t.question()).put("answer",t.answer()).put("status",t.status()).put("sourceCount",t.sources().size()));answers.put(new JSONObject().put("failedConversation",failed));}}catch(Exception ignored){}result.putString("stream","\nFAIL chat: "+android.util.Log.getStackTraceString(e));}
        finally {
            if(activity!=null){MainActivity last=activity;test.runOnMainSync(last::finish);SystemClock.sleep(400);}
            try(Library library=new Library(test.getTargetContext())){for(String id:imported)library.removeDocument(id);}catch(Exception ignored){}
            if(capturedTurns)try(ChatStore chat=new ChatStore(test.getTargetContext())){for(ChatStore.Turn turn:chat.turns())if(!existingTurns.contains(turn.id()))chat.remove(turn.id());}catch(Exception ignored){}
            ModelStore.select(test.getTargetContext(),previous);test.getTargetContext().deleteDatabase(migration);test.getTargetContext().deleteDatabase(chatDb);for(int i=temporary.size()-1;i>=0;i--)temporary.get(i).delete();
            try {JSONObject report=new JSONObject().put("runId",runId).put("target",target).put("version",test.getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("passed",complete).put("checksPassed",passed).put("checks",checks).put("answers",answers).put("modelExecution",generate).put("promptVersion",ChatPrompt.VERSION).put("runtimeProfile",runtimeProfile).put("scope","Recorded Android target. Synthetic documents exercise import/chat plumbing, not general answer quality, battery life or field acceptance.");writeText(new File(directory,"chat-checks.json").toPath(),report.toString(2));}catch(Exception ignored){}
        }
        test.finish(complete?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static void writeText(java.nio.file.Path path,String text)throws Exception {Files.write(path,text.getBytes(StandardCharsets.UTF_8));}
    private MainActivity start(){MainActivity activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.runOnMainSync(()->activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));for(int i=0;i<100&&activity.findViewById(MainActivity.QUERY_ID)==null;i++)SystemClock.sleep(100);test.waitForIdleSync();return activity;}
    private Library.Document awaitDocument(String name,Set<String> existing)throws Exception {for(int i=0;i<300;i++){try(Library l=new Library(test.getTargetContext())){for(Library.Document d:l.documents())if(d.title().equals(name)&&!existing.contains(d.id()))return d;}SystemClock.sleep(100);}throw new AssertionError("Import did not finish: "+name);}
    private void send(MainActivity app,String message)throws Exception {test.runOnMainSync(()->app.sendMessage(message));waitAnswer(app);}
    private void waitAnswer(MainActivity app)throws Exception{for(int i=0;i<1400&&!app.answerDone;i++)SystemClock.sleep(100);test.waitForIdleSync();check(app.answerDone,"Chat request settles within its bounded deadline");}
    private void record(String label,MainActivity app)throws Exception{NativeEngine.Result r=app.lastAnswer;if(r==null){answers.put(new JSONObject().put("case",label).put("error","No native result"));return;}JSONObject rowSchedule=new JSONObject(NativeEngine.armScheduleAudit());if(runtimeProfile.optInt("prefillChunk",0)>0&&r.promptTokens()-r.cachedTokens()>1)check(rowSchedule.getInt("prefillNodes")>0,"Product chat executes the selected prefill row queue: "+label);JSONArray sources=new JSONArray();for(Library.Hit hit:app.lastHits)sources.put(new JSONObject().put("title",hit.document().title()).put("passage",hit.passage()).put("ordinal",hit.number()));answers.put(new JSONObject().put("case",label).put("system",ChatPrompt.SYSTEM).put("user",app.lastPrepared==null?"":app.lastPrepared.user()).put("text",r.text()).put("promptTokens",r.promptTokens()).put("tokens",r.tokens()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs()).put("reason",r.reason()).put("modelSha256",ModelStore.BONSAI4.sha256()).put("sources",sources).put("rowSchedule",rowSchedule));}
    private static void createPdf(File path,boolean blank,boolean locked)throws Exception {try(PDDocument document=new PDDocument()){for(int i=0;i<2;i++){PDPage page=new PDPage();document.addPage(page);if(!blank)try(PDPageContentStream out=new PDPageContentStream(document,page)){out.beginText();out.setFont(PDType1Font.HELVETICA,14);out.newLineAtOffset(60,700);out.showText(i==0?"Synthetic manual: sample unit PX-65.":"Sample unit PX-65 uses spare filter F-92.");out.endText();}}if(locked)document.protect(new StandardProtectionPolicy("owner-secret","reader-secret",new AccessPermission()));document.save(path);}}
    private List<String> texts(Activity activity){List<String> values=new ArrayList<>();test.runOnMainSync(()->collect(activity.getWindow().getDecorView(),values));return values;}
    private static void collect(View view,List<String> values){if(view instanceof TextView t)values.add(t.getText().toString());if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),values);}
    private void screenshot(String name)throws Exception{SystemClock.sleep(200);Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(directory,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
