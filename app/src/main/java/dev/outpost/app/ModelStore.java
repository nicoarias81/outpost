package dev.outpost.app;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.function.LongConsumer;

/** Only pinned generator profiles are accepted; each is verified before native parsing. */
final class ModelStore {
    static final String NAME = "Qwen2.5 1.5B Instruct · Q4_K_M";
    static final String FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf";
    static final String SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
    static final long BYTES = 1117320736L;
    record Spec(String id, String name, String filename, long bytes, String sha256, boolean sampled) {}
    static final Spec QWEN = new Spec("qwen15",NAME,FILENAME,BYTES,SHA256,false);
    static final Spec BONSAI17 = new Spec("bonsai17","Ternary Bonsai 1.7B · Q2_0 g64","Ternary-Bonsai-1.7B-Q2_0_g64.gguf",490163968L,"6d0ecb3d9055969b5cde332b6fdb60e67ed3599e9f73e56b977731e1467e5c91",true);
    static final Spec BONSAI4 = new Spec("bonsai4","Ternary Bonsai 4B · Q2_0 g64","Ternary-Bonsai-4B-Q2_0_g64.gguf",1137806656L,"9d968b04a3c9a794897bcc744c8072fb6a061c0e42efd03c989401ddf8baef0c",true);
    static final java.util.List<Spec> PROFILES = java.util.List.of(QWEN,BONSAI17,BONSAI4);
    private final File directory;
    private final Spec spec;
    ModelStore(Context context) { this(context,selected(context)); }
    ModelStore(Context context,Spec spec) { directory = new File(context.getFilesDir(), "models"); this.spec=spec; }
    Spec spec() { return spec; }
    static Spec selected(Context context) {
        String id=context.getSharedPreferences("generator",Context.MODE_PRIVATE).getString("profile",QWEN.id());
        for(Spec s:PROFILES) if(s.id().equals(id)) return s;
        return QWEN;
    }
    static void select(Context context,Spec spec) { context.getSharedPreferences("generator",Context.MODE_PRIVATE).edit().putString("profile",spec.id()).apply(); }
    File file() { return new File(directory, spec==QWEN ? "qwen-test.gguf" : spec.filename()); }
    private File marker() { return new File(directory,spec==QWEN ? "verified.sha256" : spec.id()+".sha256"); }
    boolean ready() {
        if (file().length() != spec.bytes()) return false;
        try { return spec.sha256().equals(new String(Files.readAllBytes(marker().toPath()), StandardCharsets.US_ASCII).trim()); }
        catch (Exception e) { return false; }
    }
    @android.annotation.SuppressLint("UsableSpace") // Conservative free-space check; do not evict other apps' caches.
    synchronized void install(InputStream input, LongConsumer progress) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Could not prepare storage.");
        if (directory.getUsableSpace() < spec.bytes() + 16 * 1024 * 1024) throw new IllegalArgumentException("Not enough storage for another copy of the selected model.");
        File partial = new File(directory, spec.id()+".part");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[1024 * 1024]; int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > spec.bytes()) throw new IllegalArgumentException("The file does not match the selected model.");
                    digest.update(buffer, 0, read); out.write(buffer, 0, read); progress.accept(total);
                }
                out.getFD().sync();
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (total != spec.bytes() || !spec.sha256().contentEquals(hash)) throw new IllegalArgumentException("Incorrect or incomplete model. Select the exact GGUF: "+spec.filename());
            Files.move(partial.toPath(), file().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (FileOutputStream marker = new FileOutputStream(marker())) { marker.write(spec.sha256().getBytes(StandardCharsets.US_ASCII)); }
        } finally {
            if (partial.exists() && !partial.delete()) partial.deleteOnExit();
        }
    }
}
