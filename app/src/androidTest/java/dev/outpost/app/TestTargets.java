package dev.outpost.app;

import android.app.Instrumentation;
import android.os.Build;
import java.io.File;

/** Physical data suites require a dedicated, explicitly owned synthetic package. */
final class TestTargets {
    static final String QA="dev.outpost.app.regressionqa";
    static boolean ownedQa(Instrumentation test){
        if(!test.getTargetContext().getPackageName().equals(QA))return false;
        File marker=new File(test.getTargetContext().getFilesDir(),"pixel-regression-owned");
        try{return marker.isFile()&&new String(java.nio.file.Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.US_ASCII).matches("pixel-regression-[0-9TZ]+-[a-f0-9]{8}");}
        catch(Exception error){return false;}
    }
    static boolean admitted(Instrumentation test){
        if(Build.SUPPORTED_ABIS[0].equals("x86_64")&&Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"))return true;
        return ownedQa(test)&&Build.MANUFACTURER.equals("Google")&&Build.MODEL.equals("Pixel 10 Pro")&&Build.SUPPORTED_ABIS[0].equals("arm64-v8a");
    }
}
