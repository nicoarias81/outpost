package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real SQLite migration, package rollback, CSV identity and application UI checks. */
final class KnowledgeChecks {
    private final Instrumentation test;
    private final JSONArray checks = new JSONArray();
    private int passed;
    private final String runId;
    KnowledgeChecks(Instrumentation test, String runId) {
        this.test = test;
        if (runId == null || !runId.matches("[A-Za-z0-9_-]{8,80}")) throw new IllegalArgumentException("Knowledge run ID required");
        this.runId = runId;
        File dir = new File(test.getTargetContext().getFilesDir(), "evidence/knowledge/" + runId);
        if (dir.exists() || !dir.mkdirs()) throw new IllegalStateException("Knowledge run already exists or cannot be created");
    }
    private void check(boolean value, String name) throws Exception {
        checks.put(new JSONObject().put("name", name).put("passed", value));
        if (!value) throw new AssertionError(name);
        passed++;
    }
    private interface Action { void run() throws Exception; }
    private void rejects(Action action, String name) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | java.util.concurrent.CancellationException expected) { rejected = true; }
        check(rejected, name);
    }
    static String pack(String id, int version, String filter) throws Exception {
        String body = "Synthetic equipment record. Asset M7 lists replacement filter " + filter
            + ". No current safety state is recorded.";
        String csv = "issue_id,status,owner,record_date,measurement_date\n"
            + "OP-1,open,Ada,2026-09-20,2026-09-18\nOP-2,closed,Lee,2026-09-21,2026-09-19\n";
        JSONArray documents = new JSONArray()
            .put(new JSONObject().put("id", "equipment").put("title", "Synthetic equipment reference").put("category", "Evaluation")
                .put("format", "text").put("body", body).put("sha256", Evidence.sha256(body)))
            .put(new JSONObject().put("id", "issues").put("title", "Synthetic issue register").put("category", "Evaluation")
                .put("format", "csv").put("body", csv).put("sha256", Evidence.sha256(csv)));
        return new JSONObject().put("schemaVersion", 1).put("id", id).put("version", version)
            .put("title", "Synthetic field kit").put("source", "Outpost evaluation fixture")
            .put("license", "Original synthetic fixture").put("language", "en")
            .put("contentDate", "2026-09-20").put("documents", documents).toString();
    }
    void run() {
        Bundle result = new Bundle(); MainActivity activity = null; boolean complete = false;
        String legacyName = "migration-" + UUID.randomUUID() + ".db";
        String failedMigrationName = "migration-failure-" + UUID.randomUUID() + ".db";
        String uiId = "test-ui-" + UUID.randomUUID();
        try {
            RuntimePolicy.Choice near=RuntimePolicy.choose(new long[]{1000,900,800,785},true);
            check(near.fastestWidth()==8 && near.candidateWidth()==4 && near.selectedWidth()==4,"Near tie chooses width four and reports fastest separately");
            check(RuntimePolicy.choose(new long[]{1000,970,965,960},true).selectedWidth()==1,"Small gain does not override baseline");
            check(RuntimePolicy.choose(new long[]{1000,960,955,900},true).selectedWidth()==8,"Clearly faster width eight can be selected");
            RuntimePolicy.Choice failed=RuntimePolicy.choose(new long[]{1000,900,800,700},false);
            check(failed.selectedWidth()==1 && failed.speedup()==1 && failed.timeReductionPercent()==0,"Parity failure reports actual fallback gain, not candidate gain");
            check(Math.abs(near.speedup()-1.25)<1e-9 && Math.abs(near.timeReductionPercent()-20)<1e-9,"Speedup and elapsed-time reduction have explicit distinct semantics");
            // Recreate the actual v1 schema with user content; a new empty v2 database is not a migration test.
            String retained = "Persisted user record: replacement PX-77.";
            try (SQLiteDatabase old = test.getTargetContext().openOrCreateDatabase(legacyName, 0, null)) {
                old.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL,source TEXT NOT NULL,url TEXT NOT NULL,date TEXT NOT NULL,body TEXT NOT NULL)");
                old.execSQL("CREATE VIRTUAL TABLE passages USING fts4(doc_id,part,content,search_text,notindexed=doc_id,notindexed=part,notindexed=content)");
                old.execSQL("INSERT INTO documents VALUES (?,?,?,?,?,?,?)", new Object[]{"user-kept", "Retained", "My documents", "Unverified import", "", "2026-09-01", retained});
                old.execSQL("INSERT INTO passages VALUES (?,?,?,?)", new Object[]{"user-kept", 1, retained, Library.normalize(retained)});
                old.setVersion(1);
            }
            try (Library migrated = new Library(test.getTargetContext(), legacyName)) {
                check(migrated.getReadableDatabase().getVersion() == 5, "Schema 1 upgrades to 5");
                check(migrated.documents().size() == 1, "Migration does not reseed or duplicate user data");
                check(migrated.load("user-kept").body().equals(retained), "Migration preserves source bytes and document identity");
                check(migrated.search("PX-77").size() == 1, "Legacy FTS rows survive migration");
                Library.Metadata metadata = migrated.metadata("user-kept");
                check(metadata.sha256().equals(Evidence.sha256(retained)), "Legacy content hash is backfilled");
                check(metadata.importedAt().equals("2026-09-01") && metadata.contentDate().isEmpty(), "Migration does not invent content dates");
                Evidence evidence = migrated.evidence(migrated.search("PX-77").get(0));
                check(migrated.resolve(evidence.locator()).content().equals(evidence.content()), "Migrated source locator resolves");
            }
            try (SQLiteDatabase old = test.getTargetContext().openOrCreateDatabase(failedMigrationName, 0, null)) {
                old.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL,source TEXT NOT NULL,url TEXT NOT NULL,date TEXT NOT NULL,body TEXT NOT NULL)");
                old.execSQL("CREATE VIRTUAL TABLE passages USING fts4(doc_id,part,content,search_text,notindexed=doc_id,notindexed=part,notindexed=content)");
                old.execSQL("INSERT INTO documents VALUES (?,?,?,?,?,?,?)", new Object[]{"kept-on-failure", "Retained", "My documents", "Import", "", "2026-09-01", retained});
                // Force failure after the first ALTER TABLE, to verify actual upgrade rollback.
                old.execSQL("ALTER TABLE documents ADD COLUMN content_sha256 TEXT NOT NULL DEFAULT ''");
                old.setVersion(1);
            }
            boolean migrationRejected = false;
            try (Library bad = new Library(test.getTargetContext(), failedMigrationName)) { bad.getWritableDatabase(); }
            catch (android.database.sqlite.SQLiteException expected) { migrationRejected = true; }
            check(migrationRejected, "Incompatible legacy schema stops without resetting the database");
            try (SQLiteDatabase old = test.getTargetContext().openOrCreateDatabase(failedMigrationName, 0, null)) {
                check(old.getVersion()==1, "Failed migration preserves the old schema version");
                try (android.database.Cursor c=old.rawQuery("SELECT body FROM documents WHERE id='kept-on-failure'",null)) {
                    check(c.moveToFirst() && retained.equals(c.getString(0)), "Failed migration preserves user content");
                }
                boolean addedColumn=false;
                try (android.database.Cursor c=old.rawQuery("PRAGMA table_info(documents)",null)) { while(c.moveToNext()) if(c.getString(1).equals("revision")) addedColumn=true; }
                check(!addedColumn, "Failed migration rolls back earlier ALTER TABLE operations");
            }
            String quoted = "id,description,value\r\nA,\"comma, and \"\"quote\"\"\",=2+2\r\nB,\"line one\nline two\",07\r\n";
            CsvTable.Table table = CsvTable.parse(quoted);
            check(table.rows().size() == 2, "Quoted newline stays in one CSV record");
            check(table.rows().get(0).cells().get(1).equals("comma, and \"quote\""), "CSV quotes and commas are preserved");
            check(table.rows().get(0).cells().get(2).equals("=2+2"), "CSV formulas remain literal strings");
            check(table.rows().get(1).cells().get(2).equals("07"), "CSV leading zeros are preserved");
            check(table.rows().get(1).number() == 3, "Record ordinal counts header, not physical lines");
            check(CsvTable.parse("\ufeffid,value\nA,1").headers().get(0).equals("id"), "UTF-8 BOM is not a header character");
            rejects(() -> CsvTable.parse("h".repeat(121)+"\nvalue"), "Oversized CSV header rejected before index expansion");
            rejects(() -> CsvTable.parse("id,id\nA,B"), "Duplicate headers rejected");
            rejects(() -> CsvTable.parse("id,value\nA"), "Ragged CSV rejected");
            rejects(() -> CsvTable.parse("id,value\nA,\"broken"), "Unclosed quotes rejected");
            rejects(() -> CsvTable.parse("id,value\nA,\"x\"tail"), "Characters after closing quote rejected");
            try (Library library = new Library(test.getTargetContext(), null)) {
                rejects(() -> library.importText("invalid-unicode.txt", new String(new char[]{(char)0xD800})), "Malformed Unicode cannot hash as replacement text");
                check(library.documents().isEmpty(), "Invalid Unicode import leaves the library unchanged");
                Library.Document csv = library.importCsv("records.csv", quoted);
                List<Library.Hit> rowHits = library.search("line two");
                Library.Hit row = rowHits.stream().filter(h -> h.document().id().equals(csv.id())).findFirst().orElseThrow();
                Evidence rowEvidence = library.evidence(row);
                check(rowEvidence.locator().kind().equals("row") && rowEvidence.locator().ordinal() == 3, "CSV evidence has the exact record locator");
                check(rowEvidence.content().contains("value: 07"), "Row evidence preserves header/value association");
                check(library.load(csv.id()).body().equals(quoted), "Original CSV stays inspectable without reserialization");
                rejects(() -> library.resolve(new Evidence.Locator(csv.id(), 1, "row", 3, "0".repeat(64))), "Wrong hash cannot resolve a locator");
                String v1 = pack("test-pack", 1, "F-28"), v2 = pack("test-pack", 2, "F-91");
                Library.InstallResult first = library.installPack(v1, () -> false);
                check(first.changed() && library.packs().size() == 1, "Pack is activated after validation");
                check(library.documents().size() == 3, "Pack documents coexist with user imports in an otherwise empty library");
                check(!library.installPack(v1, () -> false).changed() && library.documents().size() == 3, "Identical version import is idempotent");
                Library.Hit oldHit = library.search("F-28").get(0);
                Evidence old = library.evidence(oldHit);
                check(old.contentDate().equals("2026-09-20") && old.language().equals("en"), "Source date and language are explicit metadata");
                rejects(() -> library.installPack(v2.replace("F-91", "F-92"), () -> false), "Tampered checksum rejected");
                check(library.packs().get(0).version() == 1 && !library.search("F-28").isEmpty(), "Corrupt update preserves active data");
                AtomicInteger checkpoints = new AtomicInteger();
                rejects(() -> library.installPack(v2, () -> checkpoints.incrementAndGet() >= 3), "Mid-import cancellation aborts the transaction");
                check(library.packs().get(0).version() == 1 && library.search("F-91").isEmpty(), "Canceled import leaves no new searchable rows");
                rejects(() -> library.metadata("pack/test-pack/2/equipment"), "Canceled import leaves no hidden partial document");
                library.installPack(v2, () -> false);
                check(library.packs().get(0).version() == 2, "New version becomes active atomically");
                check(library.search("F-28").isEmpty() && !library.search("F-91").isEmpty(), "Search excludes retired pack versions");
                check(library.resolve(old.locator()).content().contains("F-28") && !library.resolve(old.locator()).active(), "Old citation still opens its archived version");
                rejects(() -> library.installPack(v1, () -> false), "Older pack cannot silently replace newer version");
                rejects(() -> library.installPack(v2 + " ", () -> false), "Same version cannot acquire a different manifest identity");
                rejects(() -> KnowledgePack.parse(v1.replace("\"version\":1", "\"version\":1.5")), "Fractional package versions rejected");
                rejects(() -> KnowledgePack.parse(v1.replace("\"schemaVersion\":1", "\"schemaVersion\":2")), "Unsupported schema rejected");
                rejects(() -> KnowledgePack.parse("{\"extra\":"+"[".repeat(1000)+"0"+"]".repeat(1000)+"}"), "Deeply nested JSON is rejected before recursive parsing");
                rejects(() -> KnowledgePack.parse("{\"extra\":["+"0,".repeat(11000)+"0]}"), "Excessive JSON structure is rejected before allocation");

                rejects(() -> KnowledgePack.parse(v1.replace("\"id\":\"test-pack\"", "\"id\":\"../escape\"")), "Unsafe-looking package IDs rejected");
                library.removePack("test-pack");
                check(library.packs().isEmpty() && library.documents().size() == 1, "Removing a pack preserves unrelated documents");
                rejects(() -> library.resolve(old.locator()), "Removed source is unavailable, not rebound to another version");
            }
            // Exercise the real Activity import path and reader without running a model.
            MainActivity launched = (MainActivity)test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = launched;
            for (int i=0;i<100 && launched.findViewById(MainActivity.QUERY_ID)==null;i++) SystemClock.sleep(100);
            test.waitForIdleSync();
            try(Library installed=new Library(test.getTargetContext())) { installed.installPack(pack(uiId,1,"ZX-904"),()->false); }
            test.runOnMainSync(launched::showSettings);test.waitForIdleSync();
            check(launched.findViewById(R.id.chat_import)!=null,"English document import is reachable through Settings");
            screenshot("knowledge-library.png");
            Library.Hit hit;
            try(Library installed=new Library(test.getTargetContext())) { hit=installed.search("ZX-904").stream().filter(h->h.document().id().contains(uiId)).findFirst().orElseThrow(); }
            check(hit.document().id().contains(uiId),"Retrieval resolves installed pack content without a mock product corpus");
            test.runOnMainSync(() -> launched.openDocument(hit.document(), hit.passage(), hit.number()));
            for(int i=0;i<100 && !launched.documentOpen;i++) SystemClock.sleep(50);
            check(launched.documentOpen, "Reader opens exact source locator and metadata");
            screenshot("knowledge-source.png");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); test.waitForIdleSync();
            Library.Hit csvHit;
            try(Library installed=new Library(test.getTargetContext())) { csvHit=installed.search("OP-1").stream().filter(h->h.document().id().contains(uiId)&&h.document().id().endsWith("/issues")&&h.number()==2).findFirst().orElseThrow(); }
            test.runOnMainSync(() -> launched.openDocument(csvHit.document(),csvHit.passage(),csvHit.number()));
            for(int i=0;i<100 && !launched.documentOpen;i++) SystemClock.sleep(50);
            check(launched.documentOpen,"CSV source reader opens the selected record");
            test.waitForIdleSync(); SystemClock.sleep(200);
            android.view.accessibility.AccessibilityNodeInfo activeRoot=test.getUiAutomation().getRootInActiveWindow();
            check(activeRoot!=null && test.getTargetContext().getPackageName().equals(String.valueOf(activeRoot.getPackageName())), "Source reader is the active Outpost window");
            List<android.view.accessibility.AccessibilityNodeInfo> rowLabels=activeRoot.findAccessibilityNodeInfosByText("CSV record 2");
            List<android.view.accessibility.AccessibilityNodeInfo> dateLabels=activeRoot.findAccessibilityNodeInfosByText("record_date: 2026-09-20");
            check(!rowLabels.isEmpty() && !dateLabels.isEmpty(), "Active CSV reader exposes record locator and date field meaning in English");
            for(var node:rowLabels) node.recycle(); for(var node:dateLabels) node.recycle(); activeRoot.recycle();
            screenshot("knowledge-csv.png");
            JSONObject report = new JSONObject().put("runId", runId).put("appVersion", test.getTargetContext().getPackageManager()
                .getPackageInfo(test.getTargetContext().getPackageName(),0).versionName)
                .put("passed", true).put("checksPassed", passed).put("checks", checks)
                .put("scope", "Emulator SQLite migration/transaction/CSV/source-identity/UI checks; no model quality or real-world dataset validation.");
            write("knowledge-checks.json", report.toString(2));
            result.putString("stream", "\nPASS knowledge: " + passed + " checks.\n");
            complete = true;
        } catch (Throwable error) {
            try { write("knowledge-checks.json", new JSONObject().put("runId",runId).put("passed",false).put("checks",checks).put("error",android.util.Log.getStackTraceString(error)).toString(2)); }
            catch (Exception ignored) {}
            result.putString("stream", "\nFAIL knowledge: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (activity != null) { MainActivity last=activity; test.runOnMainSync(last::finish); }
            try (Library library = new Library(test.getTargetContext())) { library.removePack(uiId); }
            catch (Exception ignored) {}
            test.getTargetContext().deleteDatabase(legacyName);
            test.getTargetContext().deleteDatabase(failedMigrationName);
        }
        test.finish(complete ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void click(MainActivity activity, String label) {
        ArrayList<View> views = new ArrayList<>();
        activity.getWindow().getDecorView().findViewsWithText(views,label,View.FIND_VIEWS_WITH_TEXT);
        for(View v:views) if(v instanceof Button b && b.getText().toString().equals(label)) { b.performClick(); return; }
        throw new AssertionError("Button unavailable: "+label);
    }
    private File path(String name) { File dir=new File(test.getTargetContext().getFilesDir(),"evidence/knowledge/"+runId); dir.mkdirs(); return new File(dir,name); }
    private void write(String name,String text) throws Exception { Files.write(path(name).toPath(),text.getBytes(StandardCharsets.UTF_8)); }
    private void screenshot(String name) throws Exception {
        test.waitForIdleSync(); SystemClock.sleep(150);
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        if(bitmap==null) throw new IllegalStateException("Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(path(name))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
    }
}
