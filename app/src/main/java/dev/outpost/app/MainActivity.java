package dev.outpost.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    volatile int cacheReleaseRequests;
    static final int QUERY_ID = 1001, SEARCH_ID = 1002;
    static final int GENERATE_ID = 1003, CANCEL_ID = 1004;
    static final int REVIEW_ID = 1005;
    static final int SPECULATION_ID=R.id.speculation_toggle;
    static final int BG = 0xFFF5F3EB, INK = 0xFF223B32, GREEN = 0xFF275D4B;
    static final int MUTED = 0xFF65716A, LINE = 0xFFDFE3D7, PAPER = 0xFFFFFEF9, PALE = 0xFFE9EEDC;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService inference = Executors.newSingleThreadExecutor();
    private Library library;
    private List<Library.Pack> knowledgePacks = List.of();
    private java.util.concurrent.atomic.AtomicBoolean packCancellation;
    volatile boolean packImportDone = true;
    volatile String packImportError = "";
    private ModelStore models;
    private JudgeStore judgeModels;
    private NativeEngine engine;
    private long activeRun;
    private ScrollView pageScroll;
    volatile boolean answerDone = true;
    volatile NativeEngine.Result lastAnswer;
    volatile NativeEngine.Decision lastReview;
    volatile boolean reviewDone = true;
    private LinearLayout root, content, nav, results;
    private EditText query;
    private int tab = 0, generation = 0;
    private String currentQuery = "";
    private List<Library.Document> documents = new ArrayList<>();
    volatile List<Library.Hit> lastHits = List.of();
    volatile boolean searchDone = false;
    volatile boolean documentOpen = false;
    private boolean ready;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        library = new Library(this);
        models = new ModelStore(this);
        judgeModels = new JudgeStore(this);
        engine = new NativeEngine();
        if (state != null) { currentQuery = state.getString("query", ""); tab = state.getInt("tab", 0); }
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(root);
        TextView loading = text(getString(R.string.ui_preparing_your_library), 20, INK); loading.setPadding(dp(24), dp(48), dp(24), dp(24)); root.addView(loading);
        worker.execute(() -> {
            try {
                judgeModels.prepareHead();
                documents = library.documents();
                knowledgePacks = library.packs();
                runOnUiThread(() -> { if (!isDestroyed()) { ready = true; render(); if (!currentQuery.isEmpty() && tab == 0) search(currentQuery); } });
            } catch (Exception e) { runOnUiThread(() -> loading.setText(R.string.library_error)); }
        });
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (query != null && tab == 0) currentQuery = query.getText().toString();
        out.putString("query", currentQuery); out.putInt("tab", tab); super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if (packCancellation != null) packCancellation.set(true);
        cancelAnswer();
        generation++;
        inference.execute(engine::close); inference.shutdown();
        worker.execute(library::close); worker.shutdown(); super.onDestroy();
    }
    @Override protected void onStop() {
        cancelAnswer();
        cacheReleaseRequests++;
        if(engine!=null && !inference.isShutdown()) inference.execute(engine::clearCache);
        super.onStop();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level>=TRIM_MEMORY_RUNNING_LOW && engine!=null && !inference.isShutdown()) {
            cacheReleaseRequests++;
            cancelAnswer(); inference.execute(engine::clearCache);
        }
    }
    private void cancelAnswer() { if (engine != null && activeRun > 0) engine.cancel(activeRun); }
    private void render() {
        if (!ready || isDestroyed()) return;
        root.removeAllViews();
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(24), dp(16), dp(24), dp(12));
        Compass compass = new Compass(this); header.addView(compass, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView name = text(getString(R.string.app_name), 22, INK); name.setTypeface(Typeface.create("serif", Typeface.BOLD));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.leftMargin = dp(10); header.addView(name, nameParams);
        TextView offline = text(getString(R.string.ui_offline), 10, GREEN); offline.setTypeface(null, Typeface.BOLD); offline.setPadding(dp(10), dp(8), dp(10), dp(8)); offline.setBackground(shape(PALE, 20, 0)); header.addView(offline);
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        pageScroll = scroll;
        content = column(); content.setPadding(dp(24), dp(12), dp(24), dp(24)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tab == 0) explore(); else if (tab == 1) libraryPage(); else statusPage();
        nav = row(); nav.setPadding(dp(16), dp(8), dp(16), dp(8)); nav.setBackgroundColor(PAPER);
        String[] labels = {getString(R.string.ui_explore), getString(R.string.ui_library), getString(R.string.ui_status)};
        for (int i = 0; i < labels.length; i++) {
            int page = i;
            Button b = button(labels[i], i == tab ? GREEN : PAPER, i == tab ? Color.WHITE : MUTED);
            b.setContentDescription(labels[i] + (i == tab ? getString(R.string.ui_selected) : ""));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(3), 0, dp(3), 0); nav.addView(b, p);
            b.setOnClickListener(v -> {
                if (query != null && tab == 0) currentQuery = query.getText().toString();
                hideKeyboard(); cancelAnswer(); generation++; tab = page; render();
                if (tab == 0 && !currentQuery.isEmpty()) search(currentQuery);
            });
        }
        root.addView(nav);
    }
    private void explore() {
        // Opens directly on the question surface: no landing block and no preset questions.
        LinearLayout searchCard = column(); searchCard.setPadding(dp(18), dp(18), dp(18), dp(18)); searchCard.setBackground(shape(GREEN, 20, 0)); add(content, searchCard, 24, 0);
        TextView searchLabel = text(getString(R.string.ui_what_do_you_want_to_understand), 17, Color.WHITE); searchLabel.setTypeface(null, Typeface.BOLD); searchCard.addView(searchLabel);
        query = new EditText(this); query.setId(QUERY_ID); query.setSingleLine(false); query.setMaxLines(3); query.setTextSize(16); query.setTextColor(INK); query.setHintTextColor(MUTED); query.setHint(getString(R.string.ui_enter_a_question_or_topic)); query.setContentDescription(getString(R.string.ui_question_to_search_in_your_library));
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH); query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        query.setPadding(dp(14), dp(12), dp(14), dp(12)); query.setMinHeight(dp(72)); query.setBackground(shape(PAPER, 12, 0)); query.setText(currentQuery); add(searchCard, query, 14, 0);
        Button searchButton = button(getString(R.string.ui_search_my_library), 0xFFD8E7AD, INK); searchButton.setId(SEARCH_ID); add(searchCard, searchButton, 12, dp(50));
        searchButton.setOnClickListener(v -> search(query.getText().toString()));
        query.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { search(query.getText().toString()); return true; } return false; });
        results = column(); add(content, results, 20, 0);
        LinearLayout note = column(); note.setPadding(dp(16), dp(16), dp(16), dp(16)); note.setBackground(shape(PALE, 14, 0)); add(content, note, 20, 0);
        TextView title = text(documents.size() + getString(R.string.ui_documents_available_offline), 14, INK); title.setTypeface(null, Typeface.BOLD); note.addView(title);
        add(note, text(models.ready() ? getString(R.string.ui_local_experimental_ai_installed_search_for_a_topic_and_draft_an_a) : getString(R.string.ui_search_without_installing_anything_else_to_draft_with_ai_import_a), 12, MUTED), 6, 0);
    }
    void search(String question) {
        if (!ready) return;
        cancelAnswer();
        currentQuery = question.trim(); hideKeyboard();
        if (Library.terms(currentQuery).isEmpty()) { query.setError(getString(R.string.ui_enter_a_topic_for_example_solar_energy)); return; }
        query.setError(null); int request = ++generation; String requestedQuery = currentQuery;
        searchDone = false; results.removeAllViews(); results.addView(text(getString(R.string.ui_searching_on_your_device), 14, MUTED));
        worker.execute(() -> {
            long start = SystemClock.elapsedRealtime();
            try {
                List<Library.Hit> hits = library.search(requestedQuery); long millis = SystemClock.elapsedRealtime() - start;
                runOnUiThread(() -> {
                    if (isDestroyed() || tab != 0 || request != generation) return;
                    lastHits = hits; searchDone = true; showHits(hits, millis);
                });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed() && tab == 0 && request == generation) { searchDone = true; results.removeAllViews(); results.addView(text(getString(R.string.ui_could_not_complete_the_search_try_again), 14, MUTED)); } }); }
        });
    }
    private void showHits(List<Library.Hit> hits, long millis) {
        results.removeAllViews();
        label(results, hits.isEmpty() ? getString(R.string.ui_no_matches) : getString(R.string.ui_passages_found) + millis + getString(R.string.ui_ms));
        if (hits.isEmpty()) {
            add(results, text(getString(R.string.ui_your_library_does_not_cover_this_topic_yet), 23, INK), 12, 0);
            add(results, text(getString(R.string.ui_try_more_specific_words_or_import_a_document_in_library_no_answer), 14, MUTED), 10, 0); return;
        }
        add(results, text(getString(R.string.ui_read_the_passages_or_draft_an_answer_from_the_first_three_always_), 12, MUTED), 8, 0);
        answerCard(hits);
        for (int i = 0; i < hits.size(); i++) {
            Library.Hit hit = hits.get(i);
            LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(PAPER, 14, LINE));
            TextView caption = text("[" + (i + 1) + "]  " + hit.document().category().toUpperCase(Locale.ROOT), 10, GREEN); caption.setTypeface(null, Typeface.BOLD); card.addView(caption);
            TextView title = text(hit.document().title(), 19, INK); title.setTypeface(Typeface.create("serif", Typeface.BOLD)); add(card, title, 8, 0);
            TextView passage = text(hit.passage(), 14, INK); passage.setMaxLines(5); passage.setEllipsize(android.text.TextUtils.TruncateAt.END); add(card, passage, 8, 0);
            add(card, text(hit.document().source() + getString(R.string.ui_read_passage) + hit.number() + getString(R.string.ui_and_context), 11, MUTED), 12, 0);
            card.setFocusable(true); card.setOnClickListener(v -> openDocument(hit.document(), hit.passage(), hit.number())); add(results, card, 12, 0);
        }
    }
    private void answerCard(List<Library.Hit> hits) {
        LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(PALE, 14, 0)); add(results, card, 14, 0);
        label(card, getString(R.string.local_ai_draft));
        add(card,text(models.spec().name(),11,MUTED),6,0);
        TextView output = text(models.ready() ? getString(R.string.ui_the_test_model_can_make_mistakes_drafting_happens_here_offline) : getString(R.string.ui_the_test_model_is_missing_import_it_in_status_the_passages_remain), 14, INK); add(card, output, 10, 0);
        TextView metrics = text("", 11, MUTED); add(card, metrics, 8, 0);
        Button generate = button(models.ready() ? getString(R.string.ui_draft_from_sources) : getString(R.string.ui_open_status_to_import_a_model), GREEN, Color.WHITE); generate.setId(GENERATE_ID); add(card, generate, 12, dp(50));
        Button cancel = button(getString(R.string.stop_generation), PAPER, GREEN); cancel.setId(CANCEL_ID); cancel.setVisibility(View.GONE); add(card, cancel, 8, dp(48));
        TextView reviewOutput = text("", 12, MUTED); add(card, reviewOutput, 10, 0);
        Button review = button(getString(R.string.review_first), PAPER, GREEN); review.setId(REVIEW_ID); review.setVisibility(View.GONE); add(card, review, 8, dp(52));
        review.setOnClickListener(v -> {
            if (!judgeModels.ready()) { generation++; tab = 2; render(); return; }
            if (lastAnswer == null || lastAnswer.text().isEmpty()) return;
            String claim = EvidenceReview.firstClaim(lastAnswer.text());
            String evidence = EvidenceReview.evidence(ResearchPrompt.prepare(currentQuery, hits).sources());
            int screen = generation; long request = engine.request(); activeRun = request;
            reviewDone = false; lastReview = null; review.setEnabled(false); generate.setEnabled(false);
            reviewOutput.setText(getString(R.string.review_working, claim));
            cancel.setVisibility(View.VISIBLE); cancel.setEnabled(true); cancel.setText(R.string.stop_review);
            cancel.setOnClickListener(stop -> { engine.cancel(request); cancel.setEnabled(false); cancel.setText(R.string.stopping); });
            inference.execute(() -> {
                try {
                    NativeEngine.Decision result = engine.judge(request, judgeModels.file(), judgeModels.head(), evidence, EvidenceReview.instruction(claim), EvidenceReview.OPTIONS);
                    runOnUiThread(() -> {
                        if(isDestroyed() || generation!=screen || activeRun!=request) return;
                        lastReview=result; reviewDone=true; activeRun=0;
                        reviewOutput.setText(getString(R.string.review_result, claim, EvidenceReview.display(result)));
                        review.setEnabled(true); generate.setEnabled(true); cancel.setVisibility(View.GONE);
                    });
                } catch(Exception e) { runOnUiThread(() -> {
                    if(isDestroyed() || generation!=screen || activeRun!=request) return;
                    reviewDone=true; activeRun=0; reviewOutput.setText(R.string.review_error);
                    review.setEnabled(true); generate.setEnabled(true); cancel.setVisibility(View.GONE);
                }); }
            });
        });
        generate.setOnClickListener(v -> {
            if (!models.ready()) { cancelAnswer(); generation++; tab = 2; render(); return; }
            ResearchPrompt.Prepared prepared = ResearchPrompt.prepare(currentQuery, hits);
            if (prepared.sources().isEmpty()) return;
            cancelAnswer();
            int screen = generation; long request = engine.request(); activeRun = request;
            answerDone = false; lastAnswer = null;
            ModelStore generationModel = models;
            review.setVisibility(View.GONE); reviewOutput.setText("");
            generate.setEnabled(false); cancel.setEnabled(true); cancel.setVisibility(View.VISIBLE); cancel.setText(R.string.stop_generation);
            output.setText(R.string.preparing_model); metrics.setText("");
            pageScroll.post(() -> pageScroll.smoothScrollTo(0, results.getTop()));
            cancel.setOnClickListener(stop -> { engine.cancel(request); cancel.setEnabled(false); cancel.setText(R.string.stopping); });
            inference.execute(() -> {
                try {
                    RuntimeSettings.Profile profile=RuntimeSettings.load(this,generationModel.spec());
                    ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
                    ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
                    boolean speculative=RuntimeSettings.speculationEnabled(this,generationModel.spec());
                    engine.configure(new NativeEngine.Configuration(profile.threads(),profile.promptThreads(),profile.batch(),!memory.lowMemory,profile.width(),speculative ? 3:0,true));
                    NativeEngine.Result result = engine.generateWithSampling(request, generationModel.file(), ResearchPrompt.SYSTEM, prepared.user(), 192, generationModel.spec().sampled(), (value, count) -> runOnUiThread(() -> {
                        if (!isDestroyed() && generation == screen && activeRun == request) { output.setText(value); metrics.setText(getResources().getQuantityString(R.plurals.generated_tokens, count, count)); }
                    }));
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != screen || activeRun != request) return;
                        lastAnswer = result; answerDone = true; activeRun = 0;
                        output.setText(ResearchPrompt.hasAnswerContent(result.text()) ? result.text() : getString(R.string.no_explanation));
                        String ending = result.reason() == 2 ? getString(R.string.ui_canceled) : result.reason() == 3 ? getString(R.string.ui_time_limit_reached) : result.reason() == 1 ? getString(R.string.ui_length_limit_reached) : getString(R.string.ui_generation_complete);
                        String first = result.firstTokenMs() < 0 ? getString(R.string.ui_no_first_token) : String.format(Locale.getDefault(), getString(R.string.ui_first_token_1f_s), result.firstTokenMs()/1000.0);
                        metrics.setText(getResources().getQuantityString(R.plurals.generation_metrics, (int)result.tokens(), ending, result.tokens(), first, result.totalMs()/1000.0, ResearchPrompt.citationNote(result.text(), prepared.sources().size())));
                        generate.setEnabled(true); generate.setText(R.string.regenerate); cancel.setVisibility(View.GONE);
                        if(result.reason()==0 && ResearchPrompt.hasAnswerContent(result.text())) { review.setText(judgeModels.ready() ? R.string.review_first : R.string.import_reviewer); review.setVisibility(View.VISIBLE); }
                    });
                } catch (Exception e) { runOnUiThread(() -> {
                    if (isDestroyed() || generation != screen || activeRun != request) return;
                    answerDone = true; activeRun = 0; output.setText(R.string.generation_error); generate.setEnabled(true); cancel.setVisibility(View.GONE);
                }); }
            });
        });
    }
    private void libraryPage() {
        label(content, getString(R.string.ui_your_local_collection)); heading(getString(R.string.ui_library));
        add(content, text(getString(R.string.ui_sources_you_can_read_even_without_coverage), 15, MUTED), 8, 0);
        Button importButton = button(getString(R.string.ui_import_text_or_markdown), GREEN, Color.WHITE); add(content, importButton, 20, dp(52));
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 10);
        });
        add(content, text(getString(R.string.ui_utf_8_txt_and_md_files_up_to_1_mib_choose_a_file_stored_on_your_d), 12, MUTED), 8, 0);
        Button importPack = button(getString(R.string.import_knowledge_pack), GREEN, Color.WHITE);
        importPack.setId(R.id.import_knowledge_pack); add(content, importPack, 12, dp(52));
        importPack.setEnabled(packCancellation == null);
        importPack.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 13);
        });
        add(content, text(getString(R.string.knowledge_pack_help), 12, MUTED), 8, 0);
        if (packCancellation != null) {
            Button stop = button(getString(R.string.cancel_pack_import), PAPER, GREEN);
            stop.setId(R.id.cancel_pack_import); add(content, stop, 8, dp(48));
            stop.setOnClickListener(v -> { if (packCancellation != null) packCancellation.set(true); stop.setEnabled(false); });
        }
        for (Library.Pack pack : knowledgePacks) {
            status(pack.title(), getResources().getQuantityString(R.plurals.pack_details, pack.documents(), pack.version(), pack.documents(), pack.language(), pack.source(), pack.license()));
            Button remove = button(getString(R.string.remove_pack), PAPER, GREEN); add(content, remove, 8, dp(48));
            remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(getString(R.string.remove_pack))
                .setMessage(getString(R.string.remove_pack_confirmation, pack.title()))
                .setNegativeButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.remove_pack, (dialog, which) -> {
                    cancelAnswer(); generation++;
                    worker.execute(() -> {
                        try {
                            library.removePack(pack.id());
                            List<Library.Document> updated = library.documents(); List<Library.Pack> packs = library.packs();
                            runOnUiThread(() -> { if (!isDestroyed()) { documents=updated; knowledgePacks=packs; render(); } });
                        } catch (Exception error) { runOnUiThread(() -> {
                            if (!isDestroyed()) Toast.makeText(this, R.string.pack_remove_error, Toast.LENGTH_LONG).show();
                        }); }
                    });
                }).show());
        }
        for (Library.Document d : documents) {
            LinearLayout card = column(); card.setBackground(shape(PAPER, 14, LINE)); card.setPadding(dp(16), dp(16), dp(16), dp(16));
            label(card, d.category().toUpperCase(Locale.ROOT));
            TextView title = text(d.title(), 19, INK); title.setTypeface(Typeface.create("serif", Typeface.BOLD)); add(card, title, 7, 0);
            add(card, text(d.source(), 11, MUTED), 8, 0); card.setFocusable(true); card.setOnClickListener(v -> openDocument(d, null)); add(content, card, 14, 0);
        }
        add(content, text(getString(R.string.ui_the_six_included_notes_are_educational_demo_summaries_with_refere), 12, MUTED), 20, 0);
    }
    void openDocument(Library.Document d, String passage) { openDocument(d, passage, 0); }
    void openDocument(Library.Document d, String passage, int ordinal) {
        documentOpen = false;
        worker.execute(() -> {
            try {
                Library.Document full = d.body().isEmpty() ? library.load(d.id()) : d;
                Library.Metadata metadata = library.metadata(full.id());
                Evidence selected = ordinal > 0 ? library.evidence(new Library.Hit(full, passage, ordinal, 0)) : null;
                runOnUiThread(() -> { if (!isDestroyed()) { showDocument(full, passage, metadata, selected); documentOpen = true; } });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) Toast.makeText(this, getString(R.string.ui_could_not_open_the_document), Toast.LENGTH_SHORT).show(); }); }
        });
    }
    private void showDocument(Library.Document d, String passage, Library.Metadata metadata, Evidence selected) {
        ScrollView scroll = new ScrollView(this); LinearLayout body = column(); body.setPadding(dp(24), dp(16), dp(24), dp(24)); scroll.addView(body);
        body.addView(text(d.source(), 13, GREEN)); add(body, text(getString(R.string.ui_added) + d.date(), 12, MUTED), 6, 0);
        if (!d.url().isEmpty()) add(body, text(getString(R.string.ui_demo_note_written_from_the_reference_the_date_records_incorporati), 12, MUTED), 10, 0);
        add(body, text(getString(R.string.source_identity, metadata.revision(), metadata.language(),
            metadata.sha256().substring(0, 12)), 12, MUTED), 8, 0);
        add(body, text(getString(R.string.source_content_date,
            metadata.contentDate().isEmpty() ? getString(R.string.unknown_value) : metadata.contentDate()), 12, MUTED), 6, 0);
        if (!metadata.active()) add(body, text(getString(R.string.archived_source_version), 12, MUTED), 6, 0);
        if (selected != null) {
            add(body, text(selected.locator().label(), 12, GREEN), 8, 0);
            if (metadata.format().equals("csv")) {
                TextView record = text(selected.content(), 14, INK); record.setTextIsSelectable(true);
                record.setBackgroundColor(PALE); add(body, record, 10, 0);
                add(body, text(getString(R.string.original_csv_below), 12, MUTED), 8, 0);
            }
        }
        SpannableString fullText = new SpannableString(d.body());
        if (passage != null) {
            int offset = d.body().indexOf(passage);
            if (offset >= 0) fullText.setSpan(new BackgroundColorSpan(PALE), offset, offset + passage.length(), 0);
        }
        TextView documentText = text("", 16, INK); documentText.setText(fullText); documentText.setTextIsSelectable(true); documentText.setLineSpacing(dp(4), 1); add(body, documentText, 20, 0);
        if (!d.url().isEmpty()) { TextView url = text(getString(R.string.ui_original_reference_requires_internet_outside_this_app) + d.url(), 11, MUTED); url.setTextIsSelectable(true); add(body, url, 20, 0); }
        new AlertDialog.Builder(this).setTitle(d.title()).setView(scroll).setPositiveButton(getString(R.string.ui_back), null).show();
    }
    private void statusPage() {
        label(content, getString(R.string.ui_on_this_device)); heading(getString(R.string.ui_prototype_status));
        status(getString(R.string.ui_connection), getString(R.string.ui_the_app_does_not_request_internet_permission_local_search_and_rea));
        status(getString(R.string.ui_library), documents.size() + getString(R.string.ui_documents_sqlite_fts4_index_with_word_and_prefix_search));
        status(getString(R.string.ui_local_compute),NativeEngine.hardwareSummary()+getString(R.string.ui_selection_combines_cpu_operating_system_and_included_kernels_emul));
        RuntimeSettings.Profile runtime=RuntimeSettings.load(this,models.spec());
        status(getString(R.string.ui_runtime_profile),(runtime.measured() ? getString(R.string.ui_measured_on_this_device) : getString(R.string.ui_initial_configuration))+" · "+runtime.promptThreads()+getString(R.string.ui_prompt_threads)+runtime.threads()+getString(R.string.ui_decode_threads_prompt_batch)+runtime.batch()+getString(R.string.ui_groups_of)+runtime.width()+getString(R.string.ui_token_s_prefix_cache_while_the_app_is_visible));
        if(models.spec()==ModelStore.BONSAI4) {
            boolean speculative=RuntimeSettings.speculationEnabled(this,models.spec());
            status(getString(R.string.ui_experimental_speculation),getString(R.string.ui_may_speed_up_text_that_repeats_sources_and_slow_down_other_answer));
            Button toggle=button(speculative ? getString(R.string.ui_disable_context_speculation) : getString(R.string.ui_enable_context_speculation),PAPER,GREEN);
            toggle.setId(SPECULATION_ID); add(content,toggle,8,dp(56));
            toggle.setOnClickListener(v->{ cancelAnswer(); generation++; RuntimeSettings.setSpeculation(this,models.spec(),!speculative); render(); });
        }
        status(getString(R.string.ui_ai_engine), models.spec().name() + "\n" + (models.ready() ? getString(R.string.ui_model_verified_and_installed_experimental_local_drafting_with_lla) : String.format(Locale.getDefault(),getString(R.string.ui_model_awaiting_import_0f_mb_its_sha_256_is_verified_before_use),models.spec().bytes()/1000000.0)) + getString(R.string.ui_this_model_tests_the_integration_it_does_not_establish_that_the_b));
        for(ModelStore.Spec profile:ModelStore.PROFILES) {
            ModelStore candidate=new ModelStore(this,profile);
            Button choose=button((profile.id().equals(models.spec().id()) ? "✓ " : "")+profile.name(),PAPER,GREEN); add(content,choose,8,dp(52));
            choose.setOnClickListener(v -> { cancelAnswer(); generation++; ModelStore.select(this,profile); models=candidate; render(); });
        }
        Button importModel = button(models.ready() ? getString(R.string.ui_reimport_test_model) : getString(R.string.ui_import_test_model_gguf), GREEN, Color.WHITE); add(content, importModel, 12, dp(52));
        importModel.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 11);
        });
        add(content, text(getString(R.string.ui_exact_file) + models.spec().filename() + getString(R.string.ui_download_it_beforehand_as_described_in_the_readme_this_app_does_n), 12, MUTED), 8, 0);
        status(getString(R.string.ui_evidence_reviewer), JudgeStore.NAME + "\n" + (judgeModels.ready() ? getString(R.string.ui_installed_and_verified_classifies_one_claim_against_the_retrieved) : getString(R.string.ui_awaiting_import_812_mb_the_auxiliary_classifier_is_included_in_th)) + getString(R.string.ui_optional_first_claim_review_it_does_not_verify_the_full_draft_or_));
        Button importJudge = button(getString(R.string.ui_import_kev_reviewer_gguf), GREEN, Color.WHITE); add(content, importJudge, 12, dp(52));
        importJudge.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, 12);
        });
        add(content, text(getString(R.string.ui_exact_file) + JudgeStore.FILENAME + getString(R.string.ui_community_alternative_inspired_by_jev_evaluated_locally_without_c), 12, MUTED), 8, 0);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        status(getString(R.string.ui_environment), android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE + getString(R.string.ui_system_ram) + String.format(Locale.getDefault(), "%.1f GiB", memory.totalMem / 1073741824.0) + getString(R.string.ui_the_emulator_does_not_represent_pixel_speed_or_power_consumption));
        Button check = button(getString(R.string.ui_run_20_checks), GREEN, Color.WHITE); add(content, check, 20, dp(52));
        TextView output = text(getString(R.string.ui_retrieval_checks_on_the_demo_collection_they_do_not_measure_ai_qu), 13, MUTED); add(content, output, 10, 0);
        check.setOnClickListener(v -> {
            check.setEnabled(false); output.setText(R.string.checking);
            worker.execute(() -> {
                int passed = 0; long start = SystemClock.elapsedRealtime(); StringBuilder failures = new StringBuilder();
                // Isolated in-memory fixture so user imports cannot change the baseline.
                try (Library fixture = new Library(this, null)) {
                    for (RetrievalChecks.Case c : RetrievalChecks.CASES) {
                        List<Library.Hit> found = fixture.search(c.query());
                        boolean ok = c.expectedId() == null ? found.isEmpty() : found.stream().limit(3).anyMatch(h -> h.document().id().equals(c.expectedId()));
                        if (ok) passed++; else failures.append("\n• ").append(c.query());
                    }
                    String message = passed + getString(R.string.ui_20_checks_passed) + (SystemClock.elapsedRealtime() - start) + getString(R.string.ui_ms_expected_match_among_the_first_three_passages) + failures + getString(R.string.ui_functional_check_only_not_an_ai_benchmark);
                    runOnUiThread(() -> { if (!isDestroyed()) { output.setText(message); check.setEnabled(true); } });
                } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { output.setText(R.string.check_error); check.setEnabled(true); } }); }
            });
        });
    }
    private void status(String title, String description) {
        LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(PAPER, 14, LINE));
        TextView t = text(title, 16, INK); t.setTypeface(null, Typeface.BOLD); card.addView(t); add(card, text(description, 13, MUTED), 8, 0); add(content, card, 14, 0);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 13 && result == RESULT_OK && data != null && data.getData() != null) { importPack(data.getData()); return; }
        if (request == 12 && result == RESULT_OK && data != null && data.getData() != null) { importJudge(data.getData()); return; }
        if (request == 11 && result == RESULT_OK && data != null && data.getData() != null) { importModel(data.getData()); return; }
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); Toast.makeText(this, getString(R.string.ui_importing_document), Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            try {
                String name = getString(R.string.ui_document_txt);
                try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst()) name = c.getString(0);
                }
                String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
                if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".md") && !lowerName.endsWith(".markdown") && !lowerName.endsWith(".csv")) throw new IllegalArgumentException(getString(R.string.ui_choose_a_utf_8_txt_or_md_file));
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new IllegalArgumentException(getString(R.string.ui_could_not_open_the_file));
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > Library.MAX_IMPORT_BYTES) throw new IllegalArgumentException(getString(R.string.ui_the_file_exceeds_the_1_mib_limit));
                        out.write(buffer, 0, n);
                    }
                    bytes = out.toByteArray();
                }
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                Library.Document d = lowerName.endsWith(".csv") ? library.importCsv(name, text) : library.importText(name, text); List<Library.Document> updated = library.documents();
                runOnUiThread(() -> { if (!isDestroyed()) { documents = updated; tab = 1; generation++; render(); Toast.makeText(this, getString(R.string.ui_saved) + d.title(), Toast.LENGTH_SHORT).show(); } });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle(getString(R.string.ui_could_not_import)).setMessage(e instanceof IllegalArgumentException ? e.getMessage() : getString(R.string.ui_check_that_it_is_a_utf_8_text_file_accessible_on_the_device)).setPositiveButton(getString(R.string.ui_ok), null).show(); }); }
        });
    }
    void importPack(Uri uri) {
        if (packCancellation != null) return;
        cancelAnswer(); generation++;
        java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
        packCancellation = canceled; packImportDone = false; packImportError = ""; tab = 1; render();
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalArgumentException(getString(R.string.ui_could_not_open_the_file));
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (canceled.get()) throw new java.util.concurrent.CancellationException();
                    if (output.size() + count > KnowledgePack.MAX_BYTES) throw new IllegalArgumentException(getString(R.string.pack_too_large));
                    output.write(buffer, 0, count);
                }
                String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
                Library.InstallResult installed = library.installPack(json, canceled::get);
                List<Library.Document> updated = library.documents(); List<Library.Pack> packs = library.packs();
                runOnUiThread(() -> {
                    packCancellation = null; packImportDone = true;
                    if (!isDestroyed()) {
                        documents = updated; knowledgePacks = packs; generation++; render();
                        Toast.makeText(this, getResources().getQuantityString(R.plurals.pack_import_success, installed.documents(), installed.documents()), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    packCancellation = null; packImportDone = true;
                    packImportError = error instanceof java.util.concurrent.CancellationException
                        ? getString(R.string.pack_import_canceled) : (error instanceof IllegalArgumentException
                        ? error.getMessage() : getString(R.string.pack_import_error));
                    if (!isDestroyed()) { render(); new AlertDialog.Builder(this).setTitle(R.string.pack_import_title)
                        .setMessage(packImportError).setPositiveButton(R.string.ui_ok, null).show(); }
                });
            }
        });
    }
    private void importModel(Uri uri) {
        ModelStore destination = models;
        Toast.makeText(this, getString(R.string.ui_copying_and_verifying_the_model), Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException(getString(R.string.ui_could_not_open_the_model));
                destination.install(input, bytes -> { });
                runOnUiThread(() -> { if (!isDestroyed()) { generation++; tab = 2; render(); Toast.makeText(this, getString(R.string.ui_model_verified_you_can_now_draft_from_sources), Toast.LENGTH_LONG).show(); } });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) new AlertDialog.Builder(this).setTitle(getString(R.string.ui_could_not_import_the_model)).setMessage(e instanceof IllegalArgumentException ? e.getMessage() : getString(R.string.ui_check_the_file_and_available_storage)).setPositiveButton(getString(R.string.ui_ok), null).show(); }); }
        });
    }
    private void importJudge(Uri uri) {
        Toast.makeText(this,getString(R.string.ui_copying_and_verifying_kev),Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            try(InputStream input=getContentResolver().openInputStream(uri)) {
                if(input==null) throw new IllegalArgumentException(getString(R.string.ui_could_not_open_the_file));
                judgeModels.install(input);
                runOnUiThread(() -> { if(!isDestroyed()) { generation++; tab=2; render(); Toast.makeText(this,getString(R.string.ui_reviewer_installed),Toast.LENGTH_SHORT).show(); } });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed()) new AlertDialog.Builder(this).setTitle(getString(R.string.ui_could_not_import_kev)).setMessage(e instanceof IllegalArgumentException ? e.getMessage() : getString(R.string.ui_check_the_file_and_available_storage)).setPositiveButton(getString(R.string.ui_ok),null).show(); }); }
        });
    }
    private void hideKeyboard() { View f = getCurrentFocus(); if (f != null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0); }
    private void heading(String title) { TextView h = text(title, 32, INK); h.setTypeface(Typeface.create("serif", Typeface.NORMAL)); add(content, h, 10, 0); }
    private void label(LinearLayout parent, String title) { TextView t = text(title, 10, GREEN); t.setLetterSpacing(.1f); t.setTypeface(null, Typeface.BOLD); parent.addView(t); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2), 1); return t; }
    private Button button(String value, int bg, int fg) { Button b = new Button(this); b.setText(value); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(fg); b.setTypeface(null, Typeface.BOLD); b.setMinHeight(dp(48)); b.setPadding(dp(10), 0, dp(10), 0); b.setBackground(shape(bg, 12, 0)); b.setStateListAnimator(null); return b; }
    private GradientDrawable shape(int color, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (stroke != 0) d.setStroke(dp(1), stroke); return d; }
    private void add(LinearLayout parent, View view, int top, int height) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height > 0 ? height : -2); p.topMargin = dp(top); parent.addView(view, p); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class Compass extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path p = new Path();
        Compass(Context context) { super(context); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(); paint.setColor(GREEN); c.drawCircle(w/2, h/2, w/2, paint);
            p.reset(); p.moveTo(w*.5f,h*.15f); p.lineTo(w*.62f,h*.42f); p.lineTo(w*.85f,h*.5f); p.lineTo(w*.58f,h*.62f); p.lineTo(w*.5f,h*.85f); p.lineTo(w*.38f,h*.58f); p.lineTo(w*.15f,h*.5f); p.lineTo(w*.42f,h*.38f); p.close(); paint.setColor(PAPER); c.drawPath(p,paint);
        }
    }
}
