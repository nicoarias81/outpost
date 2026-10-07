package dev.outpost.app;

import android.app.Instrumentation;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

final class FixtureGrants {
    static void grant(Instrumentation test,String run)throws Exception {
        if(!run.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Invalid fixture run");
        String command="am broadcast --include-stopped-packages -n "+test.getContext().getPackageName()+"/dev.outpost.app.FixtureGrantReceiver --es run_id "+run+" --es operation grant";
        try(android.os.ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);FileInputStream input=new FileInputStream(fd.getFileDescriptor())){
            String response=new String(input.readAllBytes(),StandardCharsets.UTF_8);if(!response.contains("result=-1")||!response.contains("granted"))throw new IllegalStateException(response);
        }
        Uri tree=DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"root");
        for(int i=0;i<5;i++){
            try(android.database.Cursor cursor=test.getTargetContext().getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree,"root"),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null)){if(cursor!=null&&cursor.moveToFirst())return;}
            catch(RuntimeException ignored){}
            android.os.SystemClock.sleep(100);
        }
        throw new IllegalStateException("Synthetic document provider did not become readable after its owner granted access.");
    }
}
