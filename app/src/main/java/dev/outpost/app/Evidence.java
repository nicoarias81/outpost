package dev.outpost.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

/** Inspectable local evidence. A locator identifies content, not a confidence score. */
public record Evidence(Locator locator, String title, String content, String source, String url,
                       String contentDate, String importedAt, String language, boolean active) {
    public record Locator(String documentId, int revision, String kind, int ordinal, String contentSha256) {
        public Locator {
            if (documentId == null || documentId.isBlank() || revision < 1 || ordinal < 1
                || !(kind.equals("passage") || kind.equals("row") || kind.equals("page") || kind.equals("element"))
                || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid evidence locator");
        }
        public JSONObject toJson() {
            try {
                return new JSONObject().put("documentId", documentId).put("revision", revision)
                    .put("kind", kind).put("ordinal", ordinal).put("contentSha256", contentSha256);
            } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        }
        public String label() { return (kind.equals("element") ? "OSM entry " : kind.equals("page") ? "Page " : kind.equals("row") ? "CSV record " : "Passage ") + ordinal + " · revision " + revision; }
    }
    static String sha256(String text) {
        try {
            java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(encoded);
            byte[] bytes = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.nio.charset.CharacterCodingException error) { throw new IllegalArgumentException("Text contains malformed Unicode", error); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
