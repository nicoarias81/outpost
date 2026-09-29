package dev.outpost.app;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

final class JudgeStore {
    static final String NAME = "Kev 0.8B Q8_0 · experimental reviewer";
    static final String FILENAME = "kev-0.8b-q8_0.gguf";
    static final String SHA = "bb280c1fe370a50a165ba1d2617e3549f33e97f05045ee4000fea6e92aedb584";
    static final String HEAD_SHA = "81e93556686afce55e4aacc93428dede06e3909d47173e31e1b3a6fb78000171";
    static final long BYTES = 811843040L;
    private final Context context;
    private final File directory;
    JudgeStore(Context context) { this.context = context.getApplicationContext(); directory = new File(context.getFilesDir(), "kev"); }
    File file() { return new File(directory, FILENAME); }
    File head() { return new File(directory, "kev-head.f32"); }
    boolean ready() {
        if (file().length() != BYTES || head().length() != 2099200) return false;
        try { return SHA.equals(new String(Files.readAllBytes(new File(directory,"verified.sha256").toPath()), StandardCharsets.US_ASCII).trim()); }
        catch (Exception e) { return false; }
    }
    synchronized void prepareHead() throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Could not prepare the reviewer.");
        if (head().length() == 2099200) {
            try (InputStream in = new FileInputStream(head())) { if (hash(in).equals(HEAD_SHA)) return; }
        }
        try (InputStream in = context.getAssets().open("kev/kev-head.f32")) { copyVerified(in, head(), 2099200, HEAD_SHA); }
    }
    @android.annotation.SuppressLint("UsableSpace")
    synchronized void install(InputStream input) throws Exception {
        prepareHead();
        if (directory.getUsableSpace() < BYTES + 16*1024*1024) throw new IllegalArgumentException("About 830 MB of free storage is required to import the reviewer.");
        copyVerified(input, file(), BYTES, SHA);
        try (FileOutputStream out = new FileOutputStream(new File(directory,"verified.sha256"))) { out.write(SHA.getBytes(StandardCharsets.US_ASCII)); }
    }
    private void copyVerified(InputStream in, File target, long expectedSize, String expectedHash) throws Exception {
        File temporary = new File(directory, target.getName()+".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = 0;
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[1024*1024]; int n;
                while ((n=in.read(buffer))!=-1) { count+=n; if(count>expectedSize) throw new IllegalArgumentException("Incorrect Kev file."); digest.update(buffer,0,n); out.write(buffer,0,n); }
                out.getFD().sync();
            }
            if (count!=expectedSize || !hex(digest.digest()).equals(expectedHash)) throw new IllegalArgumentException("Kev does not match the verified file specified in the instructions.");
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally { if(temporary.exists()) temporary.delete(); }
    }
    private static String hash(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[65536]; int n;
        while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder b=new StringBuilder(); for(byte x:bytes) b.append(String.format(java.util.Locale.ROOT,"%02x",x&255)); return b.toString(); }
}
