package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs only in the isolated, non-debuggable release QA package on an admitted target. */
public final class ReleaseInstrumentation extends Instrumentation {
    private final JSONArray checks=new JSONArray();
    private Bundle args;
    private void check(boolean ok,String name)throws Exception {
        checks.put(new JSONObject().put("name",name).put("passed",ok));
        if(!ok)throw new AssertionError(name);
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);args=arguments==null?new Bundle():arguments;start();}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        // Only a test-owned empty/synthetic package may display above keyguard.
        String pkg=getTargetContext().getPackageName();
        if((pkg.equals("dev.outpost.app.releaseqa")||pkg.equals("dev.outpost.mobile"))
            &&new File(getTargetContext().getFilesDir(),"release-acceptance-owned").isFile()){
            activity.setShowWhenLocked(true);activity.setTurnScreenOn(true);
            activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        super.callActivityOnCreate(activity,state);
    }
    @Override public void onStart(){
        if(args.getString("phase","").startsWith("accept-")){new AcceptanceChecks(this,args).run();return;}
        JSONObject result=new JSONObject();Activity activity=null;boolean passed=false;
        try {
            var context=getTargetContext();
            check(context.getPackageName().equals("dev.outpost.app.releaseqa"),"isolated QA application ID");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"target is not debuggable");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_ALLOW_BACKUP)==0,"backup disabled");
            var info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
            check(info.requestedPermissions==null||info.requestedPermissions.length==0,"no requested permissions");
            check(context.getAssets().list("kev").length==0,"research head absent");
            check(context.getAssets().list("licenses").length>=6,"dependency notices present");
            check(new JSONObject(NativeEngine.kernelProfile()).has("abi"),"native runtime loads and reports dispatch");
            boolean hidden=false;try{NativeEngine.nativeDispatchChecks();}catch(UnsatisfiedLinkError expected){hidden=true;}
            check(hidden,"research JNI is unavailable");
            File modelInput=new File(context.getExternalFilesDir(null),ModelStore.BONSAI17.filename());
            result.put("modelStagingPath",modelInput.getAbsolutePath());
            if(args.getString("phase","smoke").equals("generate")){
                ModelStore model=new ModelStore(context,ModelStore.BONSAI17);
                if(!model.ready())try(var in=new FileInputStream(modelInput)){model.install(in,n->{});}
                check(model.ready(),"pinned Bonsai 1.7B hash verified");
                try(NativeEngine engine=new NativeEngine()){
                    engine.configure(new NativeEngine.Configuration(4,4,128,true));
                    String system="Answer in English. Follow the instruction exactly.";
                    String prompt="Reply with exactly the word Hello.";
                    var first=engine.generateWithSampling(engine.request(),model.file(),system,prompt,24,true,(s,n)->{});
                    check(first.reason()==0&&first.tokens()>0&&!first.text().isBlank(),"release JNI generates a completed answer");
                    engine.clearCache();
                    var second=engine.generateWithSampling(engine.request(),model.file(),system,prompt,24,true,(s,n)->{});
                    check(first.text().equals(second.text())&&first.firstLogitsHash()==second.firstLogitsHash(),"repeat after cache clear preserves answer and first logits");
                    result.put("answer",first.text()).put("firstTokenMs",first.firstTokenMs()).put("totalMs",first.totalMs());
                }
            } else {
                try(Library library=new Library(context,"release-smoke-"+java.util.UUID.randomUUID()+".db")){
                    DocumentImporter importer=new DocumentImporter(context,library);
                    String[][] fixtures={{"guide.txt","The emergency assembly point is Cedar gate."},{"issues.csv","issue,status\nGEN-7,open\n"},{"places.osm","<osm version=\"0.6\"><node id=\"71\" lat=\"40.4\" lon=\"-3.7\"><tag k=\"name\" v=\"Cedar Museum\"/><tag k=\"tourism\" v=\"museum\"/></node></osm>"}};
                    for(String[] fixture:fixtures){
                        File file=new File(context.getCacheDir(),fixture[0]);Files.write(file.toPath(),fixture[1].getBytes(StandardCharsets.UTF_8));
                        var imported=importer.importFile(Uri.fromFile(file),fixture[0],fixture[0],file.length(),new DocumentImporter.Cancellation());
                        check(imported.document()!=null,"import "+fixture[0]);
                        check(!importer.importFile(Uri.fromFile(file),fixture[0],fixture[0],file.length(),new DocumentImporter.Cancellation()).added(),"unchanged snapshot skipped "+fixture[0]);
                    }
                    check(!library.search("Cedar gate").isEmpty(),"imported text searchable");
                    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context);
                    File pdf=new File(context.getCacheDir(),"manual.pdf");
                    try(var document=new com.tom_roush.pdfbox.pdmodel.PDDocument()){
                        var page=new com.tom_roush.pdfbox.pdmodel.PDPage();document.addPage(page);
                        try(var stream=new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document,page)){
                            stream.beginText();stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,14);
                            stream.newLineAtOffset(60,700);stream.showText("Synthetic release manual: filter R-19.");stream.endText();
                        }document.save(pdf);
                    }
                    var importedPdf=importer.importFile(Uri.fromFile(pdf),"manual.pdf","manual.pdf",pdf.length(),new DocumentImporter.Cancellation());
                    check(library.metadata(importedPdf.document().id()).format().equals("pdf"),"PDF extracted and imported");
                    check(!library.search("R-19").isEmpty(),"PDF content searchable");
                    check(library.documents().size()==4,"all imported documents retained");
                }
            }
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity launched=activity;
            long until=android.os.SystemClock.uptimeMillis()+10000;
            boolean[] chat={false};
            do{runOnMainSync(()->chat[0]=launched.findViewById(MainActivity.QUERY_ID)!=null);if(!chat[0])android.os.SystemClock.sleep(100);}while(!chat[0]&&android.os.SystemClock.uptimeMillis()<until);
            check(chat[0],"launcher opens chat");
            runOnMainSync(()->((MainActivity)launched).showSettings());
            boolean[] about={false};runOnMainSync(()->about[0]=launched.findViewById(R.id.release_about)!=null);
            check(about[0],"Settings exposes About and privacy");
            runOnMainSync(()->((MainActivity)launched).showAbout());waitForIdleSync();
            File screenshot=new File(context.getExternalFilesDir(null),"release-about.png");
            var image=getUiAutomation().takeScreenshot();
            try(var output=new java.io.FileOutputStream(screenshot)){
                check(image!=null&&image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output),"About screenshot saved");
            }finally{if(image!=null)image.recycle();}
            passed=true;
        }catch(Throwable error){try{result.put("error",error.toString());}catch(Exception ignored){}}
        finally{
            if(activity!=null){final Activity close=activity;runOnMainSync(close::finish);}
            try{result.put("passed",passed).put("checks",checks);}catch(Exception ignored){}
            Bundle output=new Bundle();output.putString("stream","\nRELEASE_RESULT "+result+"\n");finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,output);
        }
    }
}
