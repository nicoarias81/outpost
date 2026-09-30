package dev.outpost.app;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Synthetic SAF provider in the test APK only. No access to arbitrary files. Disabled outside tests. */
public final class FolderDocumentsProvider extends DocumentsProvider {
    static final String AUTHORITY="dev.outpost.app.test.folders";
    private static final String[] DOCS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
    private final Map<String,Integer> reads=new HashMap<>();
    @Override public boolean onCreate(){return true;}
    private static boolean directory(String id){return id.equals("root")||id.equals("sub")||id.equals("deep")||id.equals("other")||id.equals("denied")||id.equals("empty")||id.equals("many");}
    private static String name(String id){return switch(id){case "root"->"Field kit";case "sub"->"Manuals";case "deep"->"More";case "other"->"Other";case "denied"->"Restricted";case "empty"->"Empty";case "many"->"Many files";case "note","other-note"->"note.TXT";case "rows"->"issues.csv";case "pdf"->"guide.PDF";case "hidden"->".hidden.md";case "unsupported"->"photo.png";case "bad"->"broken.csv";case "oversized"->"large.txt";case "virtual"->"virtual.txt";case "unknown-size"->"unknown.txt";default->id.startsWith("change-")?"changing.txt":id.startsWith("many-")?id+".txt":"missing";};}
    private static String mime(String id){return directory(id)?DocumentsContract.Document.MIME_TYPE_DIR:id.equals("pdf")?"application/pdf":id.equals("rows")||id.equals("bad")?"text/csv":id.equals("unsupported")?"image/png":"text/plain";}
    private void row(MatrixCursor cursor,String id){
        MatrixCursor.RowBuilder row=cursor.newRow();
        for(String col:cursor.getColumnNames())row.add(col,switch(col){
            case DocumentsContract.Document.COLUMN_DOCUMENT_ID->id;
            case DocumentsContract.Document.COLUMN_DISPLAY_NAME->name(id);
            case DocumentsContract.Document.COLUMN_MIME_TYPE->mime(id);
            case DocumentsContract.Document.COLUMN_FLAGS->id.equals("virtual")?DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT:0;
            case DocumentsContract.Document.COLUMN_SIZE->id.equals("oversized")?2L*1024*1024:null;
            default->null;
        });
    }
    @Override public Cursor queryRoots(String[] projection){MatrixCursor c=new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS});c.addRow(new Object[]{"fixture","root","Outpost test folders",DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD});return c;}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException {
        if(name(id).equals("missing"))throw new FileNotFoundException("Unknown fixture");MatrixCursor c=new MatrixCursor(projection==null?DOCS:projection);row(c,id);return c;
    }
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sortOrder)throws FileNotFoundException {
        if(parent.equals("denied"))throw new SecurityException("Synthetic denied folder");
        MatrixCursor c=new MatrixCursor(projection==null?DOCS:projection);
        String[] children=switch(parent){
            case "root"->new String[]{"note","sub","other","unsupported","bad","oversized","virtual","unknown-size","denied","root","note"};
            case "sub"->new String[]{"rows","deep"};case "deep"->new String[]{"pdf","hidden"};case "other"->new String[]{"other-note"};case "empty"->new String[]{};case "many"->java.util.stream.IntStream.range(0,60).mapToObj(i->String.format(java.util.Locale.ROOT,"many-%02d",i)).toArray(String[]::new);
            default->throw new FileNotFoundException("Unknown directory");
        };
        for(String child:children)row(c,child);return c;
    }
    @Override public boolean isChildDocument(String parent,String id){return parent.equals("root")||parent.equals("many")&&id.startsWith("many-")||parent.equals("sub")&&(id.equals("rows")||id.equals("deep")||id.equals("pdf")||id.equals("hidden"))||parent.equals("deep")&&(id.equals("pdf")||id.equals("hidden"))||parent.equals("other")&&id.equals("other-note");}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException {
        if(!mode.equals("r")||directory(id)||name(id).equals("missing"))throw new FileNotFoundException("Only fixture reads are available");
        if(signal!=null)signal.throwIfCanceled();
        byte[] bytes;
        if(id.equals("pdf"))bytes=pdf();
        else {
            String body=switch(id){case "note"->"Folder sample: spare filter AX-71.";case "other-note"->"A different note in another subfolder: BX-82.";case "rows"->"id,value\nOP-1,07\n";case "hidden"->"Nested hidden Markdown is included.";case "unknown-size"->"Unknown size metadata is supported.";case "bad"->"id,value\nA,1,extra\n";default->"Fixture";};
            if(id.startsWith("change-")){int n=reads.merge(id,1,Integer::sum);body=n==1?"Original changing file.":"Updated changing file.";}
            bytes=body.getBytes(StandardCharsets.UTF_8);
        }
        File file;
        try {file=File.createTempFile("folder-fixture-",".data",getContext().getCacheDir());try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);}ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);file.delete();return fd;}
        catch(Exception e){throw new FileNotFoundException(e.toString());}
    }
    private static byte[] pdf() {
        String content="BT /F1 14 Tf 50 720 Td (Nested PDF record: pump PX-64 uses F-91.) Tj ET";
        String[] objects={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>","<< /Length "+content.length()+" >>\nstream\n"+content+"\nendstream"};
        StringBuilder out=new StringBuilder("%PDF-1.4\n");int[] offsets=new int[objects.length];
        for(int i=0;i<objects.length;i++){offsets[i]=out.length();out.append(i+1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");}
        int xref=out.length();out.append("xref\n0 6\n0000000000 65535 f \n");for(int offset:offsets)out.append(String.format(java.util.Locale.ROOT,"%010d 00000 n \n",offset));
        out.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
