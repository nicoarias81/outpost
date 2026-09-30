package dev.outpost.app;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Version 1 text/CSV pack. No archive extraction, remote URLs, or executable content. */
final class KnowledgePack {
    static final int MAX_BYTES = 4_194_304, MAX_DOCUMENTS = 32;
    record Item(String id, String title, String category, String format, String body,
                String sha256, String url, String contentDate) {}
    record Parsed(String id, int version, String title, String source, String license,
                  String language, String contentDate, String sha256, List<Item> documents) {}
    static Parsed parse(String json) {
        if (json == null || json.indexOf('\0') >= 0 || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Knowledge pack exceeds 4 MiB");
        checkStructureBounds(json);
        try {
            JSONObject root = new JSONObject(json);
            if (integer(root, "schemaVersion") != 1) throw new IllegalArgumentException("Unsupported knowledge pack schema");
            String id = identifier(string(root, "id"));
            int version = integer(root, "version");
            if (version < 1) throw new IllegalArgumentException("Package version must be a positive integer");
            String title = text(root, "title", 160), source = text(root, "source", 240), license = text(root, "license", 240);
            String language = text(root, "language", 35);
            if (!language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) throw new IllegalArgumentException("Invalid package language");
            String date = date(root.optString("contentDate", ""));
            JSONArray docs = root.getJSONArray("documents");
            if (docs.length() < 1 || docs.length() > MAX_DOCUMENTS) throw new IllegalArgumentException("A pack needs 1 to 32 documents");
            List<Item> items = new ArrayList<>(); HashSet<String> ids = new HashSet<>();
            for (int i = 0; i < docs.length(); i++) {
                JSONObject d = docs.getJSONObject(i);
                String docId = identifier(string(d, "id"));
                if (!ids.add(docId)) throw new IllegalArgumentException("Duplicate document ID in knowledge pack");
                String format = d.optString("format", "text");
                if (!format.equals("text") && !format.equals("csv")) throw new IllegalArgumentException("Unsupported package document format");
                String body = string(d, "body");
                if (body.isBlank() || body.indexOf('\0') >= 0 || body.getBytes(StandardCharsets.UTF_8).length > Library.MAX_IMPORT_BYTES)
                    throw new IllegalArgumentException("Each document must be non-empty UTF-8 text up to 1 MiB");
                String hash = string(d, "sha256");
                if (!hash.matches("[0-9a-f]{64}") || !hash.equals(Evidence.sha256(body)))
                    throw new IllegalArgumentException("Content checksum mismatch: " + docId);
                if (format.equals("csv")) CsvTable.parse(body);
                String url = d.optString("url", "");
                if (url.length() > 2048 || url.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid source URL");
                items.add(new Item(docId, text(d, "title", 160), text(d, "category", 80), format, body,
                    hash, url, date(d.optString("contentDate", date))));
            }
            return new Parsed(id, version, title, source, license, language, date, Evidence.sha256(json), List.copyOf(items));
        } catch (org.json.JSONException error) { throw new IllegalArgumentException("Invalid knowledge pack JSON", error); }
    }
    private static String identifier(String value) {
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid package or document ID");
        return value;
    }
    private static int integer(JSONObject object, String key) throws org.json.JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
            || number.doubleValue() != number.intValue()) throw new IllegalArgumentException("Invalid integer " + key);
        return number.intValue();
    }
    private static String text(JSONObject object, String key, int limit) throws org.json.JSONException {
        String value = string(object, key).trim();
        if (value.isEmpty() || value.length() > limit || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
    private static String string(JSONObject object, String key) throws org.json.JSONException {
        Object value = object.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException("Invalid string " + key);
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i>=text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("Malformed Unicode in " + key);
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Malformed Unicode in " + key);
        }
        return text;
    }
    private static void checkStructureBounds(String json) {
        boolean quoted=false, escaped=false; int depth=0, tokens=0;
        for(int i=0;i<json.length();i++) {
            char c=json.charAt(i);
            if(quoted) {
                if(escaped) escaped=false;
                else if(c=='\\') escaped=true;
                else if(c=='"') quoted=false;
                continue;
            }
            if(c=='"') quoted=true;
            else if(c=='{' || c=='[') {
                if(++depth>16) throw new IllegalArgumentException("Knowledge pack nesting exceeds the limit");
                tokens++;
            } else if(c=='}' || c==']') depth--;
            else if(c==',' || c==':') tokens++;
            if(tokens>10000) throw new IllegalArgumentException("Knowledge pack structure exceeds the limit");
        }
    }
    private static String date(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) return "";
        try { return LocalDate.parse(value).toString(); }
        catch (java.time.format.DateTimeParseException error) { throw new IllegalArgumentException("Invalid content date", error); }
    }
}
