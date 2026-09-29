package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.EditText;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs real SQLite, package and Activity checks inside the Android emulator. */
public class OfflineInstrumentation extends Instrumentation {
    private final JSONArray results = new JSONArray();
    private int checks;
    private void check(boolean passed, String name) throws Exception {
        results.put(new JSONObject().put("check", name).put("passed", passed));
        if (!passed) throw new AssertionError(name);
        checks++;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle out = new Bundle();
        String database = "test-" + UUID.randomUUID() + ".db";
        long started = SystemClock.elapsedRealtime();
        try {
            PackageInfo info = getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app", PackageManager.GET_PERMISSIONS);
            check(info.requestedPermissions == null || !Arrays.asList(info.requestedPermissions).contains("android.permission.INTERNET"), "APK has no INTERNET permission");
            check(!ResearchPrompt.hasAnswerContent("[1] [2]."),"Bare reference numbers are not displayed as an explanation");
            check(ResearchPrompt.hasAnswerContent("300 Wh [1]."),"Numeric answers retain meaningful content");
            ModelStore.Spec previous=ModelStore.selected(getTargetContext());
            try {
                ModelStore.select(getTargetContext(),ModelStore.BONSAI17);
                check(new ModelStore(getTargetContext()).spec().id().equals("bonsai17"),"Generator profile selection persists independently of weights");
                check(!new ModelStore(getTargetContext(),ModelStore.QWEN).file().equals(new ModelStore(getTargetContext(),ModelStore.BONSAI17).file()),"Qwen and Bonsai use different installed files");
                check(!new ModelStore(getTargetContext(),ModelStore.BONSAI17).file().equals(new ModelStore(getTargetContext(),ModelStore.BONSAI4).file()),"Bonsai sizes use distinct installed files");
                getTargetContext().getSharedPreferences("generator",android.content.Context.MODE_PRIVATE).edit().putString("profile","../../invalid").commit();
                check(ModelStore.selected(getTargetContext()).id().equals(ModelStore.QWEN.id()),"Unknown saved profile falls back without becoming a path");
            } finally { ModelStore.select(getTargetContext(),previous); }
            String importedId;
            try (Library library = new Library(getTargetContext(), database)) {
                check(library.documents().size() == 6, "Six attributed demo documents installed");
                for (RetrievalChecks.Case c : RetrievalChecks.CASES) {
                    List<Library.Hit> hits = library.search(c.query());
                    boolean passed = c.expectedId() == null ? hits.isEmpty() : hits.stream().limit(3).anyMatch(h -> h.document().id().equals(c.expectedId()));
                    check(passed, "Retrieval: " + c.query());
                }
                check(library.search("!!! \" OR * ' --").isEmpty(), "FTS control characters cannot inject query syntax");
                check(library.search("gps \" OR ** ' --").stream().anyMatch(h -> h.document().id().equals("gps")), "Mixed punctuation query remains searchable");
                check(Library.terms("the is of and").isEmpty(), "Stopword-only input is empty");
                check(Library.normalize("café").equals(Library.normalize("cafe")), "Accent normalization remains language-independent");
                Library.Document imported = library.importText("Notes.txt", "Test azimuth expedition.\n\nThe mineralogicaunica key identifies this passage.");
                importedId = imported.id();
                List<Library.Hit> importedHits = library.search("mineralogicaunica");
                check(importedHits.size() == 1 && importedHits.get(0).number() == 2, "Imported passage indexed with source position");
                check(importedHits.get(0).document().body().contains(importedHits.get(0).passage()), "Citation passage exists verbatim in stored document");
                check(importedHits.get(0).document().source().contains("unverified"), "Imported content is not labeled verified");
                library.importText("Long.md", "section ".repeat(2000) + "terminofinalunico");
                check(library.search("terminofinalunico").size() == 1, "Text beyond first chunk remains searchable");
                boolean emptyRejected = false;
                try { library.importText("Empty.txt", "   "); } catch (IllegalArgumentException expected) { emptyRejected = true; }
                check(emptyRejected, "Reject empty import");
                boolean largeRejected = false;
                try { library.importText("Large.txt", "é".repeat(600000)); } catch (IllegalArgumentException expected) { largeRejected = true; }
                check(largeRejected, "UTF-8 byte size limit enforced");
                boolean binaryRejected = false;
                try { library.importText("Binary.txt", "abc\0xyz"); } catch (IllegalArgumentException expected) { binaryRejected = true; }
                check(binaryRejected, "Reject embedded NUL/binary content");
                Library.Document big = library.importText("Many passages.txt", "uniquefragments ".repeat(60000));
                List<Library.Hit> bigHits = library.search("uniquefragments");
                check(bigHits.size() == 8, "Near-limit document returns bounded results without duplicating full body per candidate");
                check(bigHits.get(0).document() == bigHits.get(1).document(), "Selected passages share one loaded source object");
                check(library.documents().stream().allMatch(d -> d.body().isEmpty()), "Library list loads metadata only");
                check(library.load(big.id()).body().length() == big.body().length(), "Full source can be loaded on demand");
            }
            try (Library reopened = new Library(getTargetContext(), database)) {
                check(reopened.search("mineralogicaunica").get(0).document().id().equals(importedId), "Imported source and index survive reopening database");
                check(reopened.documents().size() == 9, "Database reopening does not duplicate seeds");
            }
            Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity activity = (MainActivity)startActivitySync(launch);
            for (int i = 0; i < 100 && activity.findViewById(MainActivity.QUERY_ID) == null; i++) SystemClock.sleep(100);
            waitForIdleSync();
            check(activity.findViewById(MainActivity.QUERY_ID) != null, "Main screen finishes loading");
            screenshot("home.png");
            runOnMainSync(() -> { ((EditText)activity.findViewById(MainActivity.QUERY_ID)).setText("What is the difference between kW and kWh?"); activity.findViewById(MainActivity.SEARCH_ID).performClick(); });
            for (int i = 0; i < 100 && !activity.searchDone; i++) SystemClock.sleep(100);
            waitForIdleSync();
            check(activity.searchDone && activity.lastHits.stream().anyMatch(h -> h.document().id().equals("energy")), "Search button executes real local query");
            screenshot("search.png");
            Library.Hit hit = activity.lastHits.get(0);
            runOnMainSync(() -> activity.openDocument(hit.document(), hit.passage()));
            for (int i = 0; i < 100 && !activity.documentOpen; i++) SystemClock.sleep(100);
            check(activity.documentOpen, "Source reader opens asynchronously");
            waitForIdleSync(); screenshot("source.png");
            JSONObject report = new JSONObject().put("version", getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("checksPassed", checks).put("results", results)
                .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("device", android.os.Build.MODEL)
                .put("androidApi", android.os.Build.VERSION.SDK_INT).put("scope", "Functional retrieval and Activity checks; this suite does not run LLM generation; no physical-device measurement");
            write("checks.json", report.toString(2));
            out.putString("stream", "\nPASS: " + checks + " functional checks. Evidence saved in app files/evidence.\n");
            finish(Activity.RESULT_OK, out);
        } catch (Throwable e) {
            try { write("failure.json", new JSONObject().put("checksPassed", checks).put("error", e.toString()).put("results", results).toString(2)); } catch (Exception ignored) { }
            out.putString("stream", "\nFAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, out);
        } finally { getTargetContext().deleteDatabase(database); }
    }
    private File evidence(String name) {
        File directory = new File(getTargetContext().getFilesDir(), "evidence"); directory.mkdirs(); return new File(directory, name);
    }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(250);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("Cannot capture emulator screenshot");
        try (FileOutputStream stream = new FileOutputStream(evidence(name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
    }
    private void write(String name, String text) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(evidence(name))) { stream.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
}
