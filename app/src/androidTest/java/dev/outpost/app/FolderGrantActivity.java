package dev.outpost.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.util.List;

/** Test-package owner grants only synthetic fixture URIs, then revokes them after instrumentation. */
public final class FolderGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String run=getIntent().getStringExtra("run_id");
        if(run==null||!run.matches("[A-Za-z0-9_-]{8,80}")){finish();return;}
        boolean revoke="revoke".equals(getIntent().getStringExtra("operation"));
        ComponentName provider=new ComponentName(this,FolderDocumentsProvider.class);
        int flags=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        List<Uri> fixtures=List.of(
            DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"root"),
            DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"empty"),
            DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"many"),
            DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,"note"),
            DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,"change-"+run));
        if(!revoke)getPackageManager().setComponentEnabledSetting(provider,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        for(Uri uri:fixtures){if(revoke)revokeUriPermission("dev.outpost.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);else grantUriPermission("dev.outpost.app",uri,flags);}
        if(revoke)getPackageManager().setComponentEnabledSetting(provider,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
        finish();
    }
}
