package dev.outpost.app;

import android.content.Context;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.Writer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Offline text extraction with page identity. Scans are not silently treated as searchable text. */
final class PdfImporter {
    static final int MAX_BYTES=10*1024*1024, MAX_PAGES=100, MAX_TEXT=750_000;
    static List<String> extract(Context context,File file) throws IOException {
        if(file.length()==0 || file.length()>MAX_BYTES) throw new IllegalArgumentException("Choose a PDF up to 10 MiB.");
        PDFBoxResourceLoader.init(context.getApplicationContext());
        MemoryUsageSetting memory=MemoryUsageSetting.setupMixed(4*1024*1024,32*1024*1024).setTempDir(context.getCacheDir());
        try(PDDocument pdf=PDDocument.load(file,"",memory)) {
            if(pdf.isEncrypted()) throw new IllegalArgumentException("This PDF is encrypted. Import an unlocked copy.");
            if(pdf.getNumberOfPages()<1 || pdf.getNumberOfPages()>MAX_PAGES)
                throw new IllegalArgumentException("Choose a PDF with 1 to 100 pages.");
            PDFTextStripper stripper=new PDFTextStripper(); stripper.setSortByPosition(true);
            List<String> pages=new ArrayList<>(); int total=0; boolean anyText=false;
            for(int page=1;page<=pdf.getNumberOfPages();page++) {
                if(Thread.currentThread().isInterrupted()) throw new IOException("Import interrupted.");
                stripper.setStartPage(page); stripper.setEndPage(page);
                final int remaining=MAX_TEXT-total; StringBuilder body=new StringBuilder();
                stripper.writeText(pdf,new Writer() {
                    @Override public void write(char[] chars,int offset,int length) throws IOException {
                        if(body.length()+length>remaining) throw new IOException("PDF text exceeds the supported size. Import a shorter document.");
                        body.append(chars,offset,length);
                    }
                    @Override public void flush() {}
                    @Override public void close() {}
                });
                String text=body.toString().replace('\0',' ').trim();
                total+=text.length(); anyText|=!text.isBlank(); pages.add(text);
            }
            if(!anyText) throw new IllegalArgumentException("No readable text was found. Scanned PDFs need OCR before import.");
            return List.copyOf(pages);
        } catch(com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new IllegalArgumentException("This PDF needs a password. Import an unlocked copy.",e);
        }
    }
}
