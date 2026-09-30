package dev.outpost.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.OperationCanceledException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Iterative SAF traversal. Each file commits independently; failures never discard prior imports. */
final class FolderImporter {
    static final int MAX_ENTRIES=20_000, MAX_DEPTH=64, MAX_PATH=2048, MAX_DETAILS=200;
    record Issue(String path,String reason) {}
    record Progress(int scanned,int imported,int unchanged,int skipped,int failed,String current) {}
    record Report(Progress progress,boolean canceled,boolean limited,List<Issue> issues,int omittedDetails) {}
    private record Node(String id,String name,String path,String mime,long size,int flags,int depth) {}
    private static final String[] COLUMNS={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS};
    private final ContentResolver resolver;
    private final DocumentImporter importer;
    private int scanned,imported,unchanged,skipped,failed,omitted,listed=1;
    private boolean limited;
    private String current="";
    private final List<Issue> issues=new ArrayList<>();
    private long lastUpdate;
    FolderImporter(ContentResolver resolver,DocumentImporter importer){this.resolver=resolver;this.importer=importer;}
    Report run(Uri tree,DocumentImporter.Cancellation cancel,Consumer<Progress> progress) {
        if(!"content".equals(tree.getScheme())||!DocumentsContract.isTreeUri(tree))throw new IllegalArgumentException("Choose a folder using the system picker.");
        ArrayDeque<Node> pending=new ArrayDeque<>();Set<String> visited=new HashSet<>();Set<String> queued=new HashSet<>();
        String root=DocumentsContract.getTreeDocumentId(tree);boolean stopped=false;
        pending.push(new Node(root,"Selected folder","","",-1,0,0));queued.add(root);
        try {
            while(!pending.isEmpty()) {
                cancel.check();Node node=pending.pop();
                if(!visited.add(node.id()))continue;
                scanned++;current=node.path();publish(progress,true);
                Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,node.id());
                try {
                    if(node.depth()==0) {
                        try(Cursor c=query(uri,cancel)) {if(!c.moveToFirst())throw new IllegalArgumentException("Selected folder is unavailable.");node=read(c,"",0);}
                        if(!Document.MIME_TYPE_DIR.equals(node.mime()))throw new IllegalArgumentException("The selected item is not a folder.");
                        current=node.path();
                    }
                    if(Document.MIME_TYPE_DIR.equals(node.mime())) {
                        if(node.depth()>=MAX_DEPTH){limited=true;failed++;issue(node.path(),"Folder nesting exceeds 64 levels.");continue;}
                        try(Cursor c=query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,node.id()),cancel)) {
                            if(c.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING,false))throw new IllegalArgumentException("Folder listing is still loading. Make files available locally and retry.");
                            if(c.getExtras().containsKey(DocumentsContract.EXTRA_ERROR))throw new IllegalArgumentException("The provider could not list this folder.");
                            while(c.moveToNext()) {
                                cancel.check();
                                if(++listed>MAX_ENTRIES){limited=true;issue(node.path(),"The 20,000-item limit was reached. Import remaining subfolders separately.");break;}
                                Node child;
                                try{child=read(c,node.path(),node.depth()+1);}
                                catch(IllegalArgumentException bad){failed++;issue(node.path(),bad.getMessage());continue;}
                                if(queued.contains(child.id())){skipped++;issue(child.path(),"Already processed in this folder.");continue;}
                                if(queued.size()>=MAX_ENTRIES){limited=true;issue(node.path(),"The 20,000-item limit was reached. Import remaining subfolders separately.");break;}
                                queued.add(child.id());
                                if(child.path().length()>MAX_PATH){failed++;issue(child.path().substring(0,MAX_PATH),"The relative path is too long.");continue;}
                                pending.push(child);
                            }
                        }
                    } else if(DocumentImporter.format(node.name()).isEmpty()) {skipped++;issue(node.path(),"Unsupported type; accepts PDF, TXT, Markdown and CSV.");}
                    else if((node.flags()&Document.FLAG_VIRTUAL_DOCUMENT)!=0){skipped++;issue(node.path(),"Export a local copy of this document before importing it.");}
                    else {
                        DocumentImporter.Result result=importer.importFile(uri,node.name(),node.path(),node.size(),cancel);
                        if(result.added())imported++;else unchanged++;
                    }
                } catch(CancellationException|OperationCanceledException e){throw e;}
                catch(Exception e){failed++;issue(node.path(),reason(e));}
                publish(progress,false);
            }
        } catch(CancellationException|OperationCanceledException e){stopped=true;}
        publish(progress,true);
        return new Report(snapshot(),stopped,limited,List.copyOf(issues),omitted);
    }
    private Cursor query(Uri uri,DocumentImporter.Cancellation cancel) {
        Cursor cursor=resolver.query(uri,COLUMNS,null,null,null,cancel.signal);
        if(cursor==null)throw new IllegalArgumentException("Folder listing is unavailable.");return cursor;
    }
    private Node read(Cursor c,String parent,int depth) {
        String id=c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID));
        String name=c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME));
        if(id==null||id.isBlank()||name==null||name.isBlank())throw new IllegalArgumentException("The provider returned an unnamed item.");
        String path=parent.isEmpty()?name:parent+"/"+name;
        int size=c.getColumnIndex(Document.COLUMN_SIZE),flags=c.getColumnIndex(Document.COLUMN_FLAGS);
        return new Node(id,name,path,c.getString(c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)),size<0||c.isNull(size)?-1:c.getLong(size),flags<0||c.isNull(flags)?0:c.getInt(flags),depth);
    }
    private void issue(String path,String reason){if(issues.size()<MAX_DETAILS)issues.add(new Issue(path,reason));else omitted++;}
    private static String reason(Exception e) {
        if(e instanceof SecurityException)return "Permission denied. Choose the folder again or make the file available locally.";
        if(e instanceof java.nio.charset.CharacterCodingException)return "The file is not valid UTF-8 text.";
        if(e instanceof IllegalArgumentException&&e.getMessage()!=null)return e.getMessage();
        return "Could not read or import this item. Check its format and local availability.";
    }
    private Progress snapshot(){return new Progress(scanned,imported,unchanged,skipped,failed,current);}
    private void publish(Consumer<Progress> listener,boolean force){long now=android.os.SystemClock.elapsedRealtime();if(force||now-lastUpdate>=100){lastUpdate=now;listener.accept(snapshot());}}
}
