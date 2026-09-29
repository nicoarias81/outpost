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

/** Local, bounded lexical retrieval. No language model or generated answers. */
public final class Library extends SQLiteOpenHelper {
    public static final int MAX_IMPORT_BYTES = 1_048_576;
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "a", "al", "algo", "como", "con", "cual", "cuando", "de", "del", "el", "en", "es", "esta", "este", "esto",
        "hay", "la", "las", "lo", "los", "me", "mi", "para", "por", "puede", "que", "se", "sin", "son", "su", "un", "una", "y",
        "the", "is", "of", "and", "or", "to", "what", "how", "does", "it"));

    public record Document(String id, String title, String category, String source, String url, String date, String body) {}
    public record Hit(Document document, String passage, int number, double score) {}
    private final Context context;

    public Library(Context context) { this(context, "library.db"); }
    Library(Context context, String databaseName) {
        super(context, databaseName, null, 1);
        this.context = context.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL, source TEXT NOT NULL, url TEXT NOT NULL, date TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE VIRTUAL TABLE passages USING fts4(doc_id, part, content, search_text, notindexed=doc_id, notindexed=part, notindexed=content)");
        try (InputStream in = context.getAssets().open("library.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = in.read(buffer)) != -1) bytes.write(buffer, 0, size);
            JSONArray seed = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < seed.length(); i++) {
                JSONObject d = seed.getJSONObject(i);
                insert(db, new Document(d.getString("id"), d.getString("title"), d.getString("category"),
                    d.getString("source"), d.getString("url"), d.getString("date"), d.getString("body")));
            }
        } catch (Exception e) { throw new IllegalStateException("Could not prepare the library", e); }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Library migration is unavailable");
    }
    private static void insert(SQLiteDatabase db, Document d) {
        ContentValues v = new ContentValues();
        v.put("id", d.id()); v.put("title", d.title()); v.put("category", d.category());
        v.put("source", d.source()); v.put("url", d.url()); v.put("date", d.date()); v.put("body", d.body());
        db.insertOrThrow("documents", null, v);
        List<String> chunks = chunks(d.body());
        for (int i = 0; i < chunks.size(); i++) {
            String part = chunks.get(i);
            ContentValues p = new ContentValues();
            p.put("doc_id", d.id()); p.put("part", i + 1); p.put("content", part);
            p.put("search_text", normalize(d.title() + " " + part));
            db.insertOrThrow("passages", null, p);
        }
    }
    public synchronized Document importText(String name, String body) {
        if (body == null || body.trim().isEmpty()) throw new IllegalArgumentException("The document is empty.");
        if (body.indexOf('\0') >= 0 || body.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES)
            throw new IllegalArgumentException("Import UTF-8 text up to 1 MiB.");
        String title = name == null || name.isBlank() ? "Imported document" : name;
        title = title.substring(0, Math.min(160, title.length()));
        Document d = new Document(UUID.randomUUID().toString(), title, "My documents", "Imported file · unverified content", "", java.time.LocalDate.now().toString(), body.trim());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try { insert(db, d); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        return d;
    }
    public synchronized List<Document> documents() {
        List<Document> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,category,source,url,date FROM documents ORDER BY category, title", null)) {
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
    public synchronized List<Hit> search(String question) {
        List<String> terms = terms(question);
        if (terms.isEmpty()) return List.of();
        List<String> matches = new ArrayList<>();
        for (String term : terms) matches.add(term + "*");
        String expression = String.join(" OR ", matches);
        List<Hit> hits = new ArrayList<>();
        // No user syntax reaches MATCH: terms contain only normalized letters/digits.
        try (Cursor c = getReadableDatabase().rawQuery(
            "SELECT d.id,d.title,d.category,d.source,d.url,d.date,p.content,p.part FROM passages p JOIN documents d ON d.id=p.doc_id WHERE passages MATCH ? LIMIT 500",
            new String[]{expression})) {
            while (c.moveToNext()) {
                Document d = document(c);
                String passage = c.getString(c.getColumnIndexOrThrow("content"));
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
        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
            .thenComparing(h -> h.document().id()).thenComparingInt(Hit::number));
        // Load full documents only for the selected results, once per unique source.
        // Repeating a 1 MiB body for every candidate passage would exhaust a phone's RAM.
        List<Hit> selected = new ArrayList<>();
        Map<String, Document> loaded = new HashMap<>();
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
                parts.add(paragraph.substring(start, end).trim());
                start = end;
            }
        }
        return parts;
    }
}
