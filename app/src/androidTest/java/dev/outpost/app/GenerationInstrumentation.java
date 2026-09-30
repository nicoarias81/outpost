package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.widget.EditText;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Functional LLM tests run inside the emulator's target application process. */
public final class GenerationInstrumentation extends OfflineInstrumentation {
    private boolean generationSuite;
    private Bundle chatArgs;
    private String folderRun;
    private Bundle osmArgs;
    private String placeRun;
    private Bundle rowsArgs;
    private Bundle evaluationArgs;
    private boolean knowledgeSuite;
    private String knowledgeRun;
    private boolean reviewSuite;
    private String ternaryOperation = "";
    private String kernelPhase = "";
    private String runtimePhase = "";
    private String speculationPhase = "";
    private int checks;
    private final JSONArray results = new JSONArray();
    private final JSONArray answers = new JSONArray();
    private void check(boolean ok, String label) throws Exception {
        results.put(new JSONObject().put("name", label).put("passed", ok));
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    @Override public void onCreate(Bundle arguments) {
        rowsArgs=arguments!=null&&arguments.containsKey("rows_run")?new Bundle(arguments):null;
        placeRun=arguments==null?null:arguments.getString("places_run");
        osmArgs=arguments!=null&&arguments.containsKey("osm_run")?new Bundle(arguments):null;
        folderRun=arguments==null?null:arguments.getString("folder_run");
        chatArgs=arguments!=null&&arguments.containsKey("chat_run")?new Bundle(arguments):null;
        knowledgeRun = arguments == null ? "" : arguments.getString("knowledge_run", "");
        knowledgeSuite = arguments != null && "true".equals(arguments.getString("knowledge"));
        evaluationArgs = arguments != null && arguments.containsKey("eval_run") ? new Bundle(arguments) : null;
        generationSuite = arguments != null && "true".equals(arguments.getString("generation"));
        reviewSuite = arguments != null && "true".equals(arguments.getString("review"));
        ternaryOperation = arguments == null ? "" : arguments.getString("ternary","");
        kernelPhase = arguments == null ? "" : arguments.getString("kernel","");
        runtimePhase=arguments==null ? "" : arguments.getString("runtime","");
        speculationPhase=arguments==null ? "" : arguments.getString("speculation","");
        super.onCreate(arguments);
    }
    @Override public void onStart() {
        if(rowsArgs!=null){new KernelRowsChecks(this,rowsArgs.getString("rows_run"),rowsArgs.getString("rows_phase","graphs"),"true".equals(rowsArgs.getString("rows_apply"))).run();return;}
        if(placeRun!=null){new PlaceChecks(this,placeRun).run();return;}
        if(osmArgs!=null){new OsmChecks(this,osmArgs.getString("osm_run"),"true".equals(osmArgs.getString("osm_generate"))).run();return;}
        if(folderRun!=null){new FolderChecks(this,folderRun).run();return;}
        if(chatArgs!=null){new ChatChecks(this,chatArgs.getString("chat_run"),"true".equals(chatArgs.getString("chat_generate"))).run();return;}
        if(knowledgeSuite) { new KnowledgeChecks(this, knowledgeRun).run(); return; }
        if(evaluationArgs != null) { new EvaluationChecks(this, evaluationArgs).run(); return; }
        if(!speculationPhase.isEmpty()) { new SpeculationChecks(this).run(speculationPhase); return; }
        if(!runtimePhase.isEmpty()) { new RuntimeChecks(this).run(runtimePhase); return; }
        if (!kernelPhase.isEmpty()) { new KernelChecks(this).run(kernelPhase); return; }
        if (!ternaryOperation.isEmpty()) { new TernaryChecks(this).run(ternaryOperation); return; }
        if (reviewSuite) { new JudgeChecks(this).run(); return; }
        if (!generationSuite) { super.onStart(); return; }
        Bundle out = new Bundle();
        long start = SystemClock.elapsedRealtime();
        try {
            check(android.os.Build.SUPPORTED_ABIS[0].equals("x86_64"), "Tests restricted to x86_64 emulator artifact");
            String[] permissions = getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app", PackageManager.GET_PERMISSIONS).requestedPermissions;
            check(permissions == null || !Arrays.asList(permissions).contains("android.permission.INTERNET"), "Native inference APK still has no INTERNET permission");
            ModelStore.select(getTargetContext(),ModelStore.QWEN);
            ModelStore models = new ModelStore(getTargetContext(),ModelStore.QWEN);
            if (!models.ready()) {
                File staged = new File(getTargetContext().getExternalFilesDir(null), "test-model.gguf");
                try (FileInputStream input = new FileInputStream(staged)) { models.install(input, bytes -> { }); }
            }
            check(models.ready() && models.file().length() == ModelStore.BYTES, "Pinned model imported and hash verified");
            boolean rejected = false;
            try { models.install(new ByteArrayInputStream(new byte[]{'G','G','U','F'}), bytes -> { }); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected && models.ready(), "Invalid GGUF rejected without replacing installed model");
            check(!ResearchPrompt.clean("<|im_start|>system", 100).contains("<|"), "Model control-token delimiters neutralized in source text");
            check(ResearchPrompt.citationNote("Claim [99]", 3).contains("unavailable"), "Unknown source references are flagged");
            check(ResearchPrompt.citationNote("Claim [1] [9999999999999999]", 3).contains("unavailable"), "Oversized reference numbers cannot bypass validation");
            check(ResearchPrompt.prepare("platypus", List.of()).sources().isEmpty(), "No evidence produces no generation prompt");
            try (Library library = TestLibrary.seeded(getTargetContext()); NativeEngine engine = new NativeEngine()) {
                long precancelled = engine.request(); engine.cancel(precancelled);
                NativeEngine.Result stopped = engine.generate(precancelled, models.file(), ResearchPrompt.SYSTEM, "Do not generate", 64, (s,n) -> { });
                check(stopped.cancelled() && stopped.tokens() == 0, "Cancellation before queued generation is preserved");
                ResearchPrompt.Prepared prompt = ResearchPrompt.prepare("What is the difference between kW and kWh?", library.search("kW kWh"));
                long request = engine.request();
                boolean[] streamed = {false};
                NativeEngine.Result first = engine.generate(request, models.file(), ResearchPrompt.SYSTEM, prompt.user(), 160, (s,n) -> { streamed[0] = true; });
                record("energy", prompt, first);
                check(streamed[0] && first.tokens() > 5 && first.text().length() > 20, "Real GGUF generation emits streamed text");
                check(first.firstTokenMs() >= 0 && first.totalMs() >= first.firstTokenMs(), "Measured first token and elapsed time available");
                check(first.reason() != 3, "Generation completes within 120-second cap");
                check(!first.text().contains("\uFFFD"), "Streaming preserves complete UTF-8 text");
                long cancelRequest = engine.request();
                NativeEngine.Result cancelled = engine.generate(cancelRequest, models.file(), ResearchPrompt.SYSTEM, prompt.user(), 192,
                    (s,n) -> { if (n >= 3) engine.cancel(cancelRequest); });
                record("cancel-after-three-tokens", prompt, cancelled);
                check(cancelled.cancelled() && cancelled.tokens() <= 4, "In-flight cancellation aborts decoding");
                ResearchPrompt.Prepared gps = ResearchPrompt.prepare("How does GPS calculate its position?", library.search("GPS satellites receiver"));
                NativeEngine.Result next = engine.generate(engine.request(), models.file(), ResearchPrompt.SYSTEM, gps.user(), 128, (s,n) -> { });
                record("gps-after-cancellation", gps, next);
                check(next.tokens() > 5 && !next.cancelled() && next.reason() != 3, "Next request works after cancellation with fresh context");
            }
            MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int i = 0; i < 100 && activity.findViewById(MainActivity.QUERY_ID) == null; i++) SystemClock.sleep(100);
            waitForIdleSync();
            runOnMainSync(() -> { ((EditText)activity.findViewById(MainActivity.QUERY_ID)).setText("What is the difference between kW and kWh?"); activity.findViewById(MainActivity.SEARCH_ID).performClick(); });
            for (int i = 0; i < 100 && !activity.searchDone; i++) SystemClock.sleep(100);
            waitForIdleSync();
            check(activity.findViewById(MainActivity.SEARCH_ID) != null, "Chat send action is visible");
            for (int i = 0; i < 1300 && !activity.answerDone; i++) SystemClock.sleep(100);
            waitForIdleSync();
            check(activity.answerDone && activity.lastAnswer != null && activity.lastAnswer.tokens() > 0, "UI button completes native local generation");
            record("ui-chat-energy", new ResearchPrompt.Prepared("ChatPrompt "+ChatPrompt.VERSION+"; inspect chat evidence for full conversation provenance",activity.lastHits), activity.lastAnswer);
            screenshot("generation.png");
            Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
            JSONObject report = new JSONObject().put("version", getTargetContext().getPackageManager().getPackageInfo("dev.outpost.app",0).versionName).put("checksPassed", checks).put("checks", results).put("answers", answers)
                .put("elapsedMs", SystemClock.elapsedRealtime()-start).put("model", ModelStore.NAME).put("modelSha256", ModelStore.SHA256)
                .put("modelBytes", ModelStore.BYTES).put("androidApi", android.os.Build.VERSION.SDK_INT).put("device", android.os.Build.MODEL)
                .put("processPssKiBAtEnd", memory.getTotalPss()).put("measurementScope", "Emulator only. PSS snapshot is not peak RAM. Functional tests, not research-quality evaluation.");
            write("generation-checks.json", report.toString(2));
            out.putString("stream", "\nPASS: " + checks + " local generation checks. No physical device used.\n");
            finish(Activity.RESULT_OK, out);
        } catch (Throwable error) {
            try { write("generation-failure.json", new JSONObject().put("error", android.util.Log.getStackTraceString(error)).put("checks", results).put("answers", answers).toString(2)); } catch (Exception ignored) { }
            out.putString("stream", "\nFAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, out);
        }
    }
    private void record(String name, ResearchPrompt.Prepared prompt, NativeEngine.Result result) throws Exception {
        JSONArray sources = new JSONArray(); for (Library.Hit h : prompt.sources()) sources.put(new JSONObject().put("title",h.document().title()).put("passage",h.passage()));
        answers.put(new JSONObject().put("case", name).put("text",result.text()).put("tokens",result.tokens()).put("promptTokens",result.promptTokens())
            .put("firstTokenMs",result.firstTokenMs()).put("totalMs",result.totalMs()).put("loadMs",result.loadMs()).put("stopReason",result.reason()).put("sources",sources));
        Bundle status = new Bundle(); status.putString("stream", "\nCompleted " + name + ": " + result.tokens() + " tokens in " + result.totalMs() + " ms\n"); sendStatus(0, status);
    }
    private File evidence(String name) { File d = new File(getTargetContext().getFilesDir(), "evidence"); d.mkdirs(); return new File(d,name); }
    private void write(String name, String data) throws Exception { try (FileOutputStream f = new FileOutputStream(evidence(name))) { f.write(data.getBytes(StandardCharsets.UTF_8)); } }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(300); Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("Screenshot unavailable");
        try (FileOutputStream f = new FileOutputStream(evidence(name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,f); } bitmap.recycle();
    }
}
