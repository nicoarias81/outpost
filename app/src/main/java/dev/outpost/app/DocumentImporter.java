package dev.outpost.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared one-file ingestion for the file picker and recursive folder imports. */
final class DocumentImporter {
    record Result(Library.Document document, boolean added) {}
    static final class Cancellation {
        private final AtomicBoolean stopped=new AtomicBoolean();
        final CancellationSignal signal=new CancellationSignal();
        void cancel(){stopped.set(true);signal.cancel();}
        boolean canceled(){return stopped.get();}
        void check(){if(canceled()||Thread.currentThread().isInterrupted())throw new CancellationException("Import canceled.");}
    }
    private final Context context;
    private final Library library;
    DocumentImporter(Context context,Library library){this.context=context.getApplicationContext();this.library=library;}
    static String format(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".pdf"))return "pdf";
        if(lower.endsWith(".csv"))return "csv";
        if(lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".markdown"))return "text";
        return "";
    }
    static int limit(String format){return format.equals("pdf")?PdfImporter.MAX_BYTES:Library.MAX_IMPORT_BYTES;}
    private String sourceKey(Uri uri) {
        if(DocumentsContract.isDocumentUri(context,uri))return uri.getAuthority()+"\n"+DocumentsContract.getDocumentId(uri);
        return uri.toString();
    }
    Result importFile(Uri uri,String filename,String displayPath,long knownSize,Cancellation cancel)throws Exception {
        cancel.check();String format=format(filename);
        if(format.isEmpty())throw new IllegalArgumentException("Unsupported file type.");
        int max=limit(format);
        if(knownSize>max)throw new IllegalArgumentException(format.equals("pdf")?"PDF exceeds 10 MiB.":"Text or CSV exceeds 1 MiB.");
        File staged=File.createTempFile("outpost-import-",format.equals("pdf")?".pdf":".data",context.getCacheDir());
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r",cancel.signal)) {
                if(descriptor==null)throw new IllegalArgumentException("This file could not be opened.");
                try(InputStream input=descriptor.createInputStream();FileOutputStream output=new FileOutputStream(staged)) {
                    byte[] buffer=new byte[8192];int count,total=0;
                    while((count=input.read(buffer))!=-1) {
                        cancel.check();if(count>max-total)throw new IllegalArgumentException("This file exceeds its import size limit.");
                        output.write(buffer,0,count);digest.update(buffer,0,count);total+=count;
                    }
                }
            }
            cancel.check();StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            String key=sourceKey(uri)+"\nformat:"+format,sha=hash.toString();Library.Document existing=library.importedFile(key,sha);
            if(existing!=null)return new Result(existing,false);
            List<String> pages=null;String body=null;
            if(format.equals("pdf"))pages=PdfImporter.extract(context,staged,cancel::canceled);
            else body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(staged.toPath()))).toString();
            cancel.check();
            return library.importFileSnapshot(displayPath,format,body,pages,staged,key,sha,cancel::canceled);
        } finally {staged.delete();}
    }
}
