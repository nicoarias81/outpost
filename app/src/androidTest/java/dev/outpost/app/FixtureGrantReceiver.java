package dev.outpost.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.List;

/** Ordered test-only broadcast acknowledges completed fixture permission changes. */
public final class FixtureGrantReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String run=intent.getStringExtra("run_id");
        if(run==null||!run.matches("[A-Za-z0-9_-]{8,80}")){setResultCode(0);return;}
        boolean revoke="revoke".equals(intent.getStringExtra("operation"));
        try {
            ComponentName provider=new ComponentName(context,FolderDocumentsProvider.class);
            if(!revoke)context.getPackageManager().setComponentEnabledSetting(provider,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            List<Uri> fixtures=List.of(
                DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"root"),
                DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"empty"),
                DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"many"),
                DocumentsContract.buildTreeDocumentUri(FolderDocumentsProvider.AUTHORITY,"osm-root"),
                DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,"note"),
                DocumentsContract.buildDocumentUri(FolderDocumentsProvider.AUTHORITY,"change-"+run));
            for(Uri uri:fixtures){if(revoke)context.revokeUriPermission("dev.outpost.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);else context.grantUriPermission("dev.outpost.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);}
            context.getSharedPreferences("fixture-provider",Context.MODE_PRIVATE).edit().putBoolean("active",!revoke).commit();
            context.getContentResolver().notifyChange(DocumentsContract.buildRootsUri(FolderDocumentsProvider.AUTHORITY),null);
            setResultCode(-1);setResultData(revoke?"revoked":"granted");
        }catch(Exception error){setResultCode(0);setResultData(error.toString());}
    }
}
