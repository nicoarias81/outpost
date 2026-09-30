package dev.outpost.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Local retrieval, immutable source locators, and transactional text/CSV packs. */
public final class Library extends SQLiteOpenHelper {
    public static final int MAX_IMPORT_BYTES = 1_048_576;
    static final int SCHEMA_VERSION = 2;
    private static final String ACTIVE = "(d.package_id IS NULL OR EXISTS (SELECT 1 FROM knowledge_packs k "
        + "WHERE k.id=d.package_id AND k.version=d.package_version AND k.active=1))";
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "a", "al", "algo", "como", "con", "cual", "cuando", "de", "del", "el", "en", "es", "esta", "este", "esto",
        "hay", "la", "las", "lo", "los", "me", "mi", "para", "por", "puede", "que", "se", "sin", "son", "su", "un", "una", "y",
        "the", "is", "of", "and", "or", "to", "what", "how", "does", "it"));
    public record Document(String id, String title, String category, String source, String url, String date, String body) {}
    public record Hit(Document document, String passage, int number, double score) {}
    public record Metadata(int revision, String sha256, String format, String language, String contentDate,
                           String importedAt, String packageId, int packageVersion, boolean active) {}
    public record Pack(String id, int version, String title, String source, String license, String language,
                       String contentDate, String importedAt, int documents) {}
    public record InstallResult(String id, int version, int documents, boolean changed) {}
    private record Fragment(int ordinal, String text) {}
    private final Context context;
    public Library(Context context) { this(context, "library.db"); }
    Library(Context context, String databaseName) {
        super(context, databaseName, null, SCHEMA_VERSION);
        this.context = context.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL, source TEXT NOT NULL, url TEXT NOT NULL, date TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE VIRTUAL TABLE passages USING fts4(doc_id, part, content, search_text, notindexed=doc_id, notindexed=part, notindexed=content)");
        addMetadataSchema(db);
        try (InputStream in = context.getAssets().open("library.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = in.read(buffer)) != -1) bytes.write(buffer, 0, size);
            JSONArray seed = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < seed.length(); i++) {
                JSONObject d = seed.getJSONObject(i);
                Document item = new Document(d.getString("id"), d.getString("title"), d.getString("category"),
                    d.getString("source"), d.getString("url"), d.getString("date"), d.getString("body"));
                insert(db, item, "text", "en", "", null, 0);
            }
        } catch (Exception e) { throw new IllegalStateException("Could not prepare the library", e); }
    }
    private static void addMetadataSchema(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE documents ADD COLUMN revision INTEGER NOT NULL DEFAULT 1");
        db.execSQL("ALTER TABLE documents ADD COLUMN content_sha256 TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE documents ADD COLUMN format TEXT NOT NULL DEFAULT 'text'");
        db.execSQL("ALTER TABLE documents ADD COLUMN language TEXT NOT NULL DEFAULT 'und'");
        db.execSQL("ALTER TABLE documents ADD COLUMN content_date TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE documents ADD COLUMN imported_at TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE documents ADD COLUMN package_id TEXT");
        db.execSQL("ALTER TABLE documents ADD COLUMN package_version INTEGER");
        db.execSQL("CREATE TABLE knowledge_packs (id TEXT NOT NULL,version INTEGER NOT NULL,title TEXT NOT NULL,"
            + "source TEXT NOT NULL,license TEXT NOT NULL,language TEXT NOT NULL,content_date TEXT NOT NULL,"
            + "imported_at TEXT NOT NULL,manifest_sha256 TEXT NOT NULL,active INTEGER NOT NULL,PRIMARY KEY(id,version))");
        db.execSQL("CREATE INDEX documents_package ON documents(package_id,package_version)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // SQLiteOpenHelper executes this upgrade in a transaction. Never drop/reseed user data.
        if (oldVersion == 1 && newVersion == 2) {
            addMetadataSchema(db);
            try (Cursor c = db.rawQuery("SELECT id,body,date FROM documents", null)) {
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues();
                    v.put("content_sha256", Evidence.sha256(c.getString(1)));
                    v.put("imported_at", c.getString(2));
                    db.update("documents", v, "id=?", new String[]{c.getString(0)});
                }
            }
            return;
        }
        throw new IllegalStateException("Unsupported library schema migration");
    }
    private static List<Fragment> fragments(String body, String format) {
        List<Fragment> result = new ArrayList<>();
        if (format.equals("csv")) {
            CsvTable.Table table = CsvTable.parse(body);
            for (CsvTable.Row row : table.rows()) result.add(new Fragment(row.number(), table.passage(row)));
        } else {
            List<String> parts = chunks(body);
            for (int i = 0; i < parts.size(); i++) result.add(new Fragment(i + 1, parts.get(i)));
        }
        return result;
    }
    private static void insert(SQLiteDatabase db, Document d, String format, String language, String sourceDate, String packageId, int packageVersion) {
        List<Fragment> parts = fragments(d.body(), format);
        ContentValues v = new ContentValues();
        v.put("id", d.id()); v.put("title", d.title()); v.put("category", d.category());
        v.put("source", d.source()); v.put("url", d.url()); v.put("date", d.date()); v.put("body", d.body());
        v.put("revision", 1); v.put("content_sha256", Evidence.sha256(d.body())); v.put("format", format);
        v.put("language", language); v.put("content_date", sourceDate); v.put("imported_at", d.date());
        if (packageId != null) { v.put("package_id", packageId); v.put("package_version", packageVersion); v.put("revision", packageVersion); }
        db.insertOrThrow("documents", null, v);
        for (Fragment part : parts) {
            ContentValues p = new ContentValues();
            p.put("doc_id", d.id()); p.put("part", part.ordinal()); p.put("content", part.text());
            p.put("search_text", normalize(d.title() + " " + part.text()));
            db.insertOrThrow("passages", null, p);
        }
    }
    public synchronized Document importText(String name, String body) { return importDocument(name, body, "text"); }
    public synchronized Document importCsv(String name, String body) { return importDocument(name, body, "csv"); }
    private Document importDocument(String name, String body, String format) {
        if (body == null || body.trim().isEmpty()) throw new IllegalArgumentException("The document is empty.");
        if (body.indexOf('\0') >= 0 || body.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES)
            throw new IllegalArgumentException("Import UTF-8 text up to 1 MiB.");
        String title = name == null || name.isBlank() ? "Imported document" : name;
        int end = Math.min(160, title.length());
        if (end < title.length() && end > 0 && Character.isHighSurrogate(title.charAt(end - 1))) end--;
        title = title.substring(0, end);
        String content = format.equals("csv") ? body : body.trim();
        Document d = new Document(UUID.randomUUID().toString(), title, "My documents",
            "Imported file · unverified content", "", java.time.Instant.now().toString(), content);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try { insert(db, d, format, "und", "", null, 0); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
        return d;
    }
    public synchronized InstallResult installPack(String json, BooleanSupplier canceled) {
        KnowledgePack.Parsed pack = KnowledgePack.parse(json);
        cancellation(canceled);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT version,manifest_sha256,active FROM knowledge_packs WHERE id=? ORDER BY version DESC",
                    new String[]{pack.id()})) {
                if (c.moveToFirst()) {
                    if (pack.version() < c.getInt(0)) throw new IllegalArgumentException("An older pack cannot replace a newer installed version");
                    if (pack.version() == c.getInt(0)) {
                        if (!pack.sha256().equals(c.getString(1))) throw new IllegalArgumentException("This pack version already exists with different content");
                        return new InstallResult(pack.id(), pack.version(), pack.documents().size(), false);
                    }
                }
            }
            String importedAt = java.time.Instant.now().toString();
            ContentValues p = new ContentValues();
            p.put("id", pack.id()); p.put("version", pack.version()); p.put("title", pack.title()); p.put("source", pack.source());
            p.put("license", pack.license()); p.put("language", pack.language()); p.put("content_date", pack.contentDate());
            p.put("imported_at", importedAt); p.put("manifest_sha256", pack.sha256()); p.put("active", 0);
            db.insertOrThrow("knowledge_packs", null, p);
            for (KnowledgePack.Item item : pack.documents()) {
                cancellation(canceled);
                String id = "pack/" + pack.id() + "/" + pack.version() + "/" + item.id();
                insert(db, new Document(id, item.title(), item.category(), pack.source(), item.url(), importedAt, item.body()),
                    item.format(), pack.language(), item.contentDate(), pack.id(), pack.version());
            }
            cancellation(canceled);
            ContentValues inactive = new ContentValues(); inactive.put("active", 0);
            db.update("knowledge_packs", inactive, "id=?", new String[]{pack.id()});
            ContentValues active = new ContentValues(); active.put("active", 1);
            db.update("knowledge_packs", active, "id=? AND version=?", new String[]{pack.id(), Integer.toString(pack.version())});
            cancellation(canceled);
            db.setTransactionSuccessful();
            return new InstallResult(pack.id(), pack.version(), pack.documents().size(), true);
        } finally { db.endTransaction(); }
    }
    private static void cancellation(BooleanSupplier canceled) {
        if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Knowledge pack import canceled");
    }
    public synchronized List<Pack> packs() {
        List<Pack> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT k.*, (SELECT count(*) FROM documents d WHERE d.package_id=k.id AND d.package_version=k.version) AS documents "
                + "FROM knowledge_packs k WHERE active=1 ORDER BY title,id", null)) {
            while (c.moveToNext()) result.add(new Pack(c.getString(c.getColumnIndexOrThrow("id")), c.getInt(c.getColumnIndexOrThrow("version")),
                c.getString(c.getColumnIndexOrThrow("title")), c.getString(c.getColumnIndexOrThrow("source")),
                c.getString(c.getColumnIndexOrThrow("license")), c.getString(c.getColumnIndexOrThrow("language")),
                c.getString(c.getColumnIndexOrThrow("content_date")), c.getString(c.getColumnIndexOrThrow("imported_at")),
                c.getInt(c.getColumnIndexOrThrow("documents"))));
        }
        return result;
    }
    public synchronized void removePack(String id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.execSQL("DELETE FROM passages WHERE doc_id IN (SELECT id FROM documents WHERE package_id=?)", new Object[]{id});
            db.delete("documents", "package_id=?", new String[]{id});
            db.delete("knowledge_packs", "id=?", new String[]{id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public synchronized List<Document> documents() {
        List<Document> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,category,source,url,date FROM documents d WHERE " + ACTIVE + " ORDER BY category,title,id", null)) {
            while (c.moveToNext()) result.add(document(c));
        }
        return result;
    }
    public synchronized Document load(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM documents WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("Document unavailable");
            return document(c);
        }
    }
    public synchronized Metadata metadata(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT revision,content_sha256,format,language,content_date,imported_at,package_id,package_version,"
                + ACTIVE + " AS active FROM documents d WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("Document metadata unavailable");
            return new Metadata(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                c.getString(5), c.getString(6), c.getInt(7), c.getInt(8) == 1);
        }
    }
    public synchronized Evidence evidence(Hit hit) {
        Metadata m = metadata(hit.document().id());
        return resolve(new Evidence.Locator(hit.document().id(), m.revision(), m.format().equals("csv") ? "row" : "passage", hit.number(), m.sha256()));
    }
    public synchronized Evidence resolve(Evidence.Locator locator) {
        Metadata m = metadata(locator.documentId());
        if (m.revision() != locator.revision() || !m.sha256().equals(locator.contentSha256())
            || !locator.kind().equals(m.format().equals("csv") ? "row" : "passage"))
            throw new IllegalArgumentException("Evidence locator no longer matches its source");
        Document d = load(locator.documentId());
        for (Fragment f : fragments(d.body(), m.format())) if (f.ordinal() == locator.ordinal())
            return new Evidence(locator, d.title(), f.text(), d.source(), d.url(), m.contentDate(), m.importedAt(), m.language(), m.active());
        throw new IllegalArgumentException("Evidence fragment unavailable");
    }
    public synchronized List<Hit> search(String question) {
        List<String> terms = terms(question);
        if (terms.isEmpty()) return List.of();
        List<String> matches = new ArrayList<>();
        for (String term : terms) matches.add(term + "*");
        List<Hit> hits = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
            "SELECT d.id,d.title,d.category,d.source,d.url,d.date,p.content,p.part FROM passages p JOIN documents d ON d.id=p.doc_id "
                + "WHERE passages MATCH ? AND " + ACTIVE + " LIMIT 500", new String[]{String.join(" OR ", matches)})) {
            while (c.moveToNext()) {
                Document d = document(c); String passage = c.getString(c.getColumnIndexOrThrow("content"));
                Set<String> words = new HashSet<>(Arrays.asList(normalize(passage).split(" ")));
                Set<String> titleWords = new HashSet<>(Arrays.asList(normalize(d.title()).split(" ")));
                double score = 0;
                for (String term : terms) {
                    if (words.stream().anyMatch(w -> w.startsWith(term))) score += 3;
                    if (titleWords.stream().anyMatch(w -> w.startsWith(term))) score += 2;
                }
                hits.add(new Hit(d, passage, c.getInt(c.getColumnIndexOrThrow("part")), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.document().id()).thenComparingInt(Hit::number));
        List<Hit> selected = new ArrayList<>(); Map<String, Document> loaded = new HashMap<>();
        for (Hit hit : hits.subList(0, Math.min(8, hits.size()))) {
            Document full = loaded.get(hit.document().id());
            if (full == null) { full = load(hit.document().id()); loaded.put(full.id(), full); }
            selected.add(new Hit(full, hit.passage(), hit.number(), hit.score()));
        }
        return selected;
    }
    private static Document document(Cursor c) {
        return new Document(c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("title")),
            c.getString(c.getColumnIndexOrThrow("category")), c.getString(c.getColumnIndexOrThrow("source")),
            c.getString(c.getColumnIndexOrThrow("url")), c.getString(c.getColumnIndexOrThrow("date")),
            c.getColumnIndex("body") < 0 ? "" : c.getString(c.getColumnIndexOrThrow("body")));
    }
    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    static List<String> terms(String query) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String bounded = query == null ? "" : query.substring(0, Math.min(query.length(), 1000));
        for (String word : normalize(bounded).split(" +")) {
            if (word.length() > 1 && !STOP.contains(word)) result.add(word);
            if (result.size() == 20) break;
        }
        return new ArrayList<>(result);
    }
    static List<String> chunks(String body) {
        List<String> parts = new ArrayList<>();
        for (String paragraph : body.split("\\n\\s*\\n")) {
            int start = 0;
            while (start < paragraph.length()) {
                while (start < paragraph.length() && Character.isWhitespace(paragraph.charAt(start))) start++;
                if (start == paragraph.length()) break;
                int end = Math.min(start + 900, paragraph.length());
                if (end < paragraph.length()) {
                    int space = paragraph.lastIndexOf(' ', end);
                    if (space > start + 450) end = space;
                    else if (Character.isHighSurrogate(paragraph.charAt(end - 1))) end--;
                }
                parts.add(paragraph.substring(start, end).trim()); start = end;
            }
        }
        return parts;
    }
}
