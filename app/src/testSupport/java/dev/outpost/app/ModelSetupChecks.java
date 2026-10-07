package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Exercises both setup routes while blocking external apps and network downloads. */
final class ModelSetupChecks {
    static List<String> run(Instrumentation test,MainActivity app,File screenshot)throws Exception {
        List<String> checks=new ArrayList<>();ModelStore.Spec before=ModelStore.selected(app);
        Intent[] browser={null},picker={null};
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if(Intent.ACTION_VIEW.equals(intent.getAction())){browser[0]=new Intent(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}
                if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())){picker[0]=new Intent(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}
                return null;
            }
        };
        test.addMonitor(monitor);
        try {
            open(test,app);
            for(ModelStore.Spec spec:ModelStore.PROFILES) {
                click(test,name(spec));require(ModelStore.selected(app)==spec,"Model selection persists: "+spec.id(),checks);
                Intent intent=MainActivity.modelDownloadIntent(spec);
                require(Intent.ACTION_VIEW.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_BROWSABLE)
                    &&"https".equals(intent.getData().getScheme())&&"huggingface.co".equals(intent.getData().getHost())
                    &&intent.getData().getPath().endsWith("/"+spec.filename())&&intent.getData().getPath().matches(".*/resolve/[0-9a-f]{40}/[^/]+"),"Selected model has a pinned HTTPS source: "+spec.id(),checks);
            }
            var image=test.getUiAutomation().takeScreenshot();require(image!=null,"Model setup screen can be captured",checks);
            try(var out=new FileOutputStream(screenshot)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            click(test,app.getString(R.string.chat_download_model));click(test,app.getString(R.string.chat_download_action));
            require(browser[0]!=null&&browser[0].getData().toString().equals(ModelStore.BONSAI4.downloadUrl()),"Download action opens the selected pinned file in the browser",checks);
            open(test,app);click(test,app.getString(R.string.chat_import_model));click(test,app.getString(R.string.chat_choose_file));
            require(picker[0]!=null&&Intent.ACTION_OPEN_DOCUMENT.equals(picker[0].getAction())&&picker[0].hasCategory(Intent.CATEGORY_OPENABLE),"Local model action opens the system file picker",checks);
            open(test,app);click(test,name(before));click(test,app.getString(R.string.chat_done));
            require(ModelStore.selected(app)==before,"Setup checks restore the selected model",checks);
            return List.copyOf(checks);
        } finally {test.removeMonitor(monitor);ModelStore.select(app,before);}
    }
    private static void require(boolean condition,String name,List<String> checks){if(!condition)throw new AssertionError(name);checks.add(name);}
    private static String name(ModelStore.Spec spec){return spec==ModelStore.BONSAI4?"Bonsai 4B":spec==ModelStore.BONSAI17?"Bonsai 1.7B":"Qwen 1.5B";}
    private static void open(Instrumentation test,MainActivity app){test.runOnMainSync(()->{app.showSettings();app.findViewById(R.id.chat_model).performClick();});test.waitForIdleSync();SystemClock.sleep(150);}
    private static void click(Instrumentation test,String text){
        for(int attempt=0;attempt<20;attempt++){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();boolean clicked=false;
            if(root!=null){List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text);
                for(AccessibilityNodeInfo node:nodes){if(!clicked&&text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString())&&node.isClickable())clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);node.recycle();}root.recycle();}
            if(clicked){test.waitForIdleSync();SystemClock.sleep(150);return;}SystemClock.sleep(100);
        }
        throw new AssertionError("Setup control unavailable: "+text);
    }
}
