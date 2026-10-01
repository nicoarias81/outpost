package dev.outpost.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/** Chat is the home screen. Local files and model setup live in Settings. */
public final class MainActivity extends Activity {
    static final int QUERY_ID=1001, SEARCH_ID=1002, GENERATE_ID=1003;
    static final int BG=0xFFF8F8F3, PAPER=0xFFFFFFFF, INK=0xFF203E34, GREEN=0xFF285F4D,
        MUTED=0xFF67776F, LINE=0xFFDFE6DD, PALE=0xFFE8F0E6;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService inference=Executors.newSingleThreadExecutor();
    private Library library;
    private ChatStore chats;
    private ModelStore models;
    private NativeEngine engine;
    private LinearLayout root, content, messages;
    private ScrollView scroll;
    private EditText query;
    private Button send;
    private String screen="chat", draft="";
    private List<Library.Document> documents=List.of();
    private int documentPage;
    private static final int DOCUMENT_PAGE_SIZE=50;
    private final List<ChatStore.Turn> turns=new ArrayList<>();
    private final Map<String,TextView> replyViews=new HashMap<>();
    private boolean ready;
    private volatile boolean importing;
    boolean importInProgress(){return importing;}
    private long activeRun;
    private volatile DocumentImporter.Cancellation folderCancellation;
    volatile FolderImporter.Report lastFolderReport;
    private FolderImporter.Progress folderProgress;
    private TextView folderProgressView;
    private String folderSummary="";
    private final java.util.concurrent.atomic.AtomicReference<FolderImporter.Progress> pendingFolderProgress=new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean folderUpdateQueued=new java.util.concurrent.atomic.AtomicBoolean();
    boolean folderImportInProgress(){return folderCancellation!=null;}

    private volatile boolean closed;
    private volatile boolean stopRequested;
    volatile boolean answerDone=true, searchDone=true, documentOpen;
    volatile int cacheReleaseRequests;
    volatile NativeEngine.Result lastAnswer;
    volatile ChatPrompt.Prepared lastPrepared;
    volatile PlaceQueries.Answer lastPlaces;
    volatile List<Library.Hit> lastHits=List.of();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        library=new Library(this); chats=new ChatStore(this); models=new ModelStore(this); engine=new NativeEngine();
        folderSummary=getPreferences(MODE_PRIVATE).getBoolean("folder_running",false)?getString(R.string.folder_interrupted):getPreferences(MODE_PRIVATE).getString("folder_summary","");
        draft=state==null ? getPreferences(MODE_PRIVATE).getString("draft","") : state.getString("draft","");
        root=column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                v.setPadding(i.left,i.top,i.right,i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(root); root.addView(text(getString(R.string.chat_loading),16,MUTED));
        worker.execute(()->{
            try {
                documents=library.documents(); chats.recover(); List<ChatStore.Turn> saved=chats.turns();
                runOnUiThread(()->{if(!closed){turns.addAll(saved);ready=true;render();}});
            } catch(Exception e){runOnUiThread(()->{if(!closed){root.removeAllViews();root.addView(text(getString(R.string.chat_load_error),16,INK));}});}
        });
    }
    private void rememberDraft() { if(query!=null && screen.equals("chat")) draft=query.getText().toString(); }
    @Override protected void onSaveInstanceState(Bundle state) {rememberDraft();state.putString("draft",draft);super.onSaveInstanceState(state);}
    @Override protected void onStop() {
        rememberDraft();getPreferences(MODE_PRIVATE).edit().putString("draft",draft).apply();
        stopAnswer();cacheReleaseRequests++;if(!inference.isShutdown())inference.execute(engine::clearCache);super.onStop();
    }
    @Override protected void onDestroy() {
        closed=true;stopAnswer();if(folderCancellation!=null)folderCancellation.cancel();
        // Drain retrieval before closing inference; its final reply must be saved before DB close.
        worker.execute(()->{inference.execute(()->{engine.close();worker.execute(()->{library.close();chats.close();});worker.shutdown();});inference.shutdown();});
        super.onDestroy();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level>=TRIM_MEMORY_RUNNING_LOW&&!inference.isShutdown()){stopAnswer();cacheReleaseRequests++;inference.execute(engine::clearCache);}
    }
    private void stopAnswer() {if(activeRun>0){stopRequested=true;engine.cancel(activeRun);}}
    @Override public void onBackPressed() {
        if(!screen.equals("chat")){showChat();return;}super.onBackPressed();
    }
    void showChat(){screen="chat";render();}
    void showSettings(){rememberDraft();hideKeyboard();screen="settings";render();}
    void showDocuments(){rememberDraft();hideKeyboard();screen="documents";render();}
    private void render() {
        if(!ready||closed)return;
        root.removeAllViews();query=null;replyViews.clear();folderProgressView=null;
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(8),dp(16),dp(8));
        if(!screen.equals("chat")) {
            Button back=icon("back",R.string.chat_back);back.setOnClickListener(v->showChat());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        LinearLayout name=column();TextView title=text(getString(screen.equals("chat")?R.string.app_name:screen.equals("settings")?R.string.chat_settings:R.string.chat_documents),22,INK);
        title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));name.addView(title);
        if(screen.equals("chat"))name.addView(text(getString(R.string.chat_offline),11,MUTED));
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(screen.equals("chat")) {
            Button settings=icon("settings",R.string.chat_settings);settings.setId(R.id.chat_settings);settings.setOnClickListener(v->showSettings());header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        root.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(20),dp(16),dp(20),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(screen.equals("chat")){chatPage();composer();}else if(screen.equals("settings"))settingsPage();else documentsPage();
    }
    private void chatPage() {
        messages=content;
        if(turns.isEmpty()) {
            LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(22),dp(60),dp(22),dp(40));
            TextView title=text(getString(R.string.chat_welcome),26,INK);title.setGravity(Gravity.CENTER);empty.addView(title);
            TextView hint=text(getString(R.string.chat_welcome_help),14,MUTED);hint.setGravity(Gravity.CENTER);add(empty,hint,14,0);
            messages.addView(empty,new LinearLayout.LayoutParams(-1,-1));
        } else for(ChatStore.Turn turn:turns) renderTurn(turn);
        if(!models.ready()) {
            Button setup=button(getString(R.string.chat_setup_model),GREEN,Color.WHITE);setup.setOnClickListener(v->showSettings());add(messages,setup,20,48);
        }
        scrollBottom();
    }
    private void renderTurn(ChatStore.Turn turn) {
        TextView question=text(turn.question(),16,INK);question.setTextIsSelectable(true);question.setPadding(dp(16),dp(12),dp(16),dp(12));question.setBackground(shape(PALE,18,0));
        LinearLayout.LayoutParams questionParams=new LinearLayout.LayoutParams(-2,-2);questionParams.gravity=Gravity.END;questionParams.leftMargin=dp(28);questionParams.topMargin=dp(16);messages.addView(question,questionParams);
        TextView answer=text(turn.answer().isBlank()&&turn.status().equals("pending")?getString(R.string.chat_thinking):turn.answer(),16,INK);
        answer.setTextIsSelectable(true);answer.setPadding(0,dp(8),dp(12),0);add(messages,answer,8,0);replyViews.put(turn.id(),answer);
        String note=switch(turn.status()) {
            case "interrupted","canceled"->getString(R.string.chat_stopped);
            case "limit"->getString(R.string.chat_limit);
            case "error"->getString(R.string.chat_failed);
            default->"";
        };
        if(!note.isEmpty())add(messages,text(note,12,MUTED),8,0);
        if(!turn.sources().isEmpty()) {
            Button sources=button(getString(R.string.chat_sources),BG,GREEN);sources.setOnClickListener(v->showSources(turn.sources()));add(messages,sources,6,44);
        }
        View spacer=new View(this);add(messages,spacer,12,8);
    }
    private void composer() {
        if(importing){Button progress=button(getString(R.string.folder_chat_busy),BG,GREEN);progress.setOnClickListener(v->showSettings());root.addView(progress,new LinearLayout.LayoutParams(-1,dp(44)));}
        LinearLayout bar=row();bar.setGravity(Gravity.BOTTOM);bar.setPadding(dp(16),dp(8),dp(16),dp(12));bar.setBackgroundColor(BG);
        query=new EditText(this);query.setId(QUERY_ID);query.setHint(R.string.chat_message);query.setContentDescription(getString(R.string.chat_message));
        query.setTextSize(16);query.setTextColor(INK);query.setHintTextColor(MUTED);query.setMaxLines(4);query.setMinHeight(dp(52));
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        query.setImeOptions(EditorInfo.IME_ACTION_SEND);query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(600)});query.setText(draft);
        query.setPadding(dp(16),dp(12),dp(16),dp(12));query.setBackground(shape(PAPER,24,LINE));bar.addView(query,new LinearLayout.LayoutParams(0,-2,1));
        send=icon(answerDone?"send":"stop",answerDone?R.string.chat_send:R.string.chat_stop);send.setId(SEARCH_ID);send.setEnabled(!importing);send.setAlpha(importing?.45f:1f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(52),dp(52));p.leftMargin=dp(8);bar.addView(send,p);
        send.setOnClickListener(v->{if(!answerDone)stopAnswer();else sendMessage(query.getText().toString());});
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND&&answerDone){sendMessage(query.getText().toString());return true;}return false;});
        root.addView(bar);
    }
    void sendMessage(String question) {
        question=question.trim();if(!ready||!answerDone||importing||question.isEmpty())return;
        if(!models.ready()&&PlaceQueries.parse(question,turns)==null){rememberDraft();showSettings();Toast.makeText(this,R.string.chat_setup_model,Toast.LENGTH_LONG).show();return;}
        hideKeyboard();draft="";getPreferences(MODE_PRIVATE).edit().remove("draft").apply();
        long request=engine.request();activeRun=request;stopRequested=false;answerDone=false;searchDone=false;lastAnswer=null;lastPlaces=null;lastPrepared=null;
        ChatStore.Turn pending=new ChatStore.Turn(UUID.randomUUID().toString(),question,"","pending",List.of());
        List<ChatStore.Turn> history=List.copyOf(turns);turns.add(pending);render();ModelStore model=models;
        worker.execute(()->{
            try {
                chats.save(pending);
                PlaceQueries.Answer places=library.answerPlaces(pending.question(),history,()->closed||stopRequested);
                if(places!=null){lastPlaces=places;lastHits=List.of();searchDone=true;
                    ChatStore.Turn reply=new ChatStore.Turn(pending.id(),pending.question(),stopRequested?"":places.text(),stopRequested?"canceled":"complete",places.sources());
                    finishTurn(reply,null,request);return;}
                if(!model.ready()){searchDone=true;finishTurn(pending.withAnswer(getString(R.string.chat_setup_model),"complete"),null,request);return;}
                List<Library.Hit> found=library.search(pending.question());
                if(found.isEmpty()&&!history.isEmpty())found=library.search(pending.question()+" "+history.get(history.size()-1).question());
                ChatPrompt.Prepared prepared=ChatPrompt.prepare(pending.question(),history,found);lastPrepared=prepared;
                List<ChatStore.Source> sources=new ArrayList<>();
                for(Library.Hit hit:prepared.sources()){Evidence evidence=library.evidence(hit);sources.add(new ChatStore.Source(evidence.title(),evidence.locator()));}
                ChatStore.Turn withSources=new ChatStore.Turn(pending.id(),pending.question(),"","pending",List.copyOf(sources));chats.save(withSources);
                lastHits=prepared.sources();searchDone=true;
                if(closed||stopRequested){finishTurn(withSources.withAnswer("","canceled"),null,request);return;}
                runOnUiThread(()->replaceTurn(withSources));
                inference.execute(()->{
                    try {
                        RuntimeSettings.Profile p=RuntimeSettings.load(this,model.spec());
                        ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(m);
                        engine.configure(p.configuration(!m.lowMemory));
                        NativeEngine.Result result=engine.generateWithSampling(request,model.file(),ChatPrompt.SYSTEM,prepared.user(),192,model.spec().sampled(),(value,count)->runOnUiThread(()->{
                            if(!closed&&activeRun==request){ChatStore.Turn streaming=withSources.withAnswer(value,"pending");replaceTurn(streaming);TextView view=replyViews.get(pending.id());if(view!=null){view.setText(value);scrollBottom();}}
                        }));
                        String answer=ResearchPrompt.hasAnswerContent(result.text())?result.text():getString(R.string.chat_no_answer);
                        String status=result.reason()==2?"canceled":result.reason()==1||result.reason()==3?"limit":"complete";
                        finishTurn(withSources.withAnswer(answer,status),result,request);
                    }catch(Exception error){finishTurn(withSources.withAnswer("","error"),null,request);}
                });
            }catch(Exception error){searchDone=true;finishTurn(pending.withAnswer("",stopRequested?"canceled":"error"),null,request);}
        });
    }
    private void finishTurn(ChatStore.Turn turn,NativeEngine.Result result,long request) {
        worker.execute(()->{
            try{chats.save(turn);}catch(Exception e){android.util.Log.e("Outpost","Could not save response",e);}
            runOnUiThread(()->{if(!closed&&activeRun==request){rememberDraft();replaceTurn(turn);lastAnswer=result;answerDone=true;activeRun=0;render();}});
        });
    }
    private void replaceTurn(ChatStore.Turn value){for(int i=0;i<turns.size();i++)if(turns.get(i).id().equals(value.id())){turns.set(i,value);break;}}
    private void settingsPage() {
        TextView intro=text(getString(R.string.chat_settings_help),14,MUTED);add(content,intro,0,0);
        Button imports=button(getString(R.string.chat_import),GREEN,Color.WHITE);imports.setId(R.id.chat_import);imports.setEnabled(answerDone&&!importing);imports.setOnClickListener(v->pickDocument());add(content,imports,24,52);
        folderControls(content);
        add(content,text(getString(R.string.chat_import_help),12,MUTED),8,0);
        Button files=button(getResources().getQuantityString(R.plurals.chat_document_count,documents.size(),documents.size()),PAPER,INK);files.setId(R.id.chat_documents);files.setOnClickListener(v->showDocuments());add(content,files,12,52);
        label(content,getString(R.string.chat_model));
        add(content,text(modelName(models.spec()),17,INK),8,0);add(content,text(getString(models.ready()?R.string.chat_model_ready:R.string.chat_model_missing),13,MUTED),6,0);
        Button model=button(getString(R.string.chat_manage_model),PAPER,GREEN);model.setId(R.id.chat_model);model.setEnabled(answerDone&&!importing);model.setOnClickListener(v->manageModel());add(content,model,12,48);
        label(content,getString(R.string.chat_conversation));
        Button clear=button(getString(R.string.chat_new),PAPER,INK);clear.setId(R.id.chat_new);clear.setEnabled(answerDone&&!importing);
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.chat_new).setMessage(R.string.chat_clear_confirm)
            .setNegativeButton(R.string.cancel_action,null).setPositiveButton(R.string.chat_clear,(d,w)->worker.execute(()->{chats.clear();runOnUiThread(()->{if(!closed){turns.clear();draft="";showChat();}});})).show());add(content,clear,12,48);
        if(importing&&folderCancellation==null)add(content,text(getString(R.string.chat_importing),14,GREEN),16,0);
        if(!answerDone)add(content,text(getString(R.string.chat_wait_reply),13,MUTED),16,0);
    }
    private static String modelName(ModelStore.Spec spec){return spec==ModelStore.BONSAI4?"Bonsai 4B":spec==ModelStore.BONSAI17?"Bonsai 1.7B":"Qwen 1.5B";}
    private void manageModel() {
        String[] names=ModelStore.PROFILES.stream().map(MainActivity::modelName).toArray(String[]::new);int selected=ModelStore.PROFILES.indexOf(models.spec());
        new AlertDialog.Builder(this).setTitle(R.string.chat_choose_model).setSingleChoiceItems(names,selected,(dialog,index)->{
            ModelStore.Spec spec=ModelStore.PROFILES.get(index);ModelStore.select(this,spec);models=new ModelStore(this,spec);dialog.dismiss();render();
        }).setNeutralButton(R.string.chat_import_model,(d,w)->{
            new AlertDialog.Builder(this).setTitle(R.string.chat_import_model).setMessage(getString(R.string.chat_model_file,models.spec().filename()))
                .setNegativeButton(R.string.cancel_action,null).setPositiveButton(R.string.chat_choose_file,(dialog,which)->pick(11)).show();
        }).setNegativeButton(R.string.chat_done,null).show();
    }
    private void documentsPage() {
        if(documents.isEmpty()){add(content,text(getString(R.string.chat_no_documents),22,INK),16,0);add(content,text(getString(R.string.chat_no_documents_help),14,MUTED),12,0);}
        Button addFile=button(getString(R.string.chat_import),GREEN,Color.WHITE);addFile.setEnabled(answerDone&&!importing);addFile.setOnClickListener(v->pickDocument());add(content,addFile,16,50);
        folderControls(content);
        int lastPage=Math.max(0,(documents.size()-1)/DOCUMENT_PAGE_SIZE);documentPage=Math.min(documentPage,lastPage);
        int first=documentPage*DOCUMENT_PAGE_SIZE,end=Math.min(first+DOCUMENT_PAGE_SIZE,documents.size());
        if(!documents.isEmpty())add(content,text(getString(R.string.folder_document_page,first+1,end,documents.size()),12,MUTED),16,0);
        for(Library.Document document:documents.subList(first,end)) {
            LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(shape(PAPER,16,LINE));
            add(card,text(document.title(),17,INK),0,0);
            Button open=button(getString(R.string.chat_open),PAPER,GREEN);open.setOnClickListener(v->openDocument(document,null));add(card,open,8,44);
            Button remove=button(getString(R.string.chat_remove),PAPER,MUTED);remove.setEnabled(answerDone&&!importing);remove.setOnClickListener(v->confirmRemove(document));add(card,remove,0,44);add(content,card,16,0);
        }
        if(lastPage>0){
            LinearLayout pages=row();Button previous=button(getString(R.string.chat_previous),PAPER,GREEN),next=button(getString(R.string.chat_next),PAPER,GREEN);
            previous.setId(R.id.documents_previous);next.setId(R.id.documents_next);previous.setEnabled(documentPage>0);next.setEnabled(documentPage<lastPage);
            previous.setOnClickListener(v->{documentPage--;render();});next.setOnClickListener(v->{documentPage++;render();});
            pages.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));pages.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));add(content,pages,16,0);
        }
    }
    private void confirmRemove(Library.Document document) {
        worker.execute(()->{
            try {
                Library.Metadata metadata=library.metadata(document.id());
                String message=getString(metadata.packageId()==null?R.string.chat_remove_confirm:R.string.chat_remove_pack_confirm,document.title());
                runOnUiThread(()->{if(!closed)new AlertDialog.Builder(this).setTitle(R.string.chat_remove).setMessage(message)
                    .setNegativeButton(R.string.cancel_action,null).setPositiveButton(R.string.chat_remove,(d,w)->worker.execute(()->{
                        try{if(metadata.packageId()!=null)library.removePack(metadata.packageId());else library.removeDocument(document.id());refreshDocuments();}
                        catch(Exception e){error(getString(R.string.chat_remove_error));}
                    })).show();});
            }catch(Exception e){error(getString(R.string.chat_remove_error));}
        });
    }
    private void showSources(List<ChatStore.Source> sources) {
        String[] labels=new String[sources.size()];for(int i=0;i<labels.length;i++)labels[i]="["+(i+1)+"] "+sources.get(i).title();
        new AlertDialog.Builder(this).setTitle(R.string.chat_sources).setItems(labels,(d,index)->{
            Evidence.Locator locator=sources.get(index).locator();worker.execute(()->{
                try{Evidence evidence=library.resolve(locator);Library.Document document=library.load(locator.documentId());Library.Metadata metadata=library.metadata(document.id());runOnUiThread(()->{if(!closed)showDocument(document,evidence.content(),metadata,locator.ordinal());});}
                catch(Exception e){error(getString(R.string.chat_source_removed));}
            });
        }).setNegativeButton(R.string.chat_done,null).show();
    }
    void openDocument(Library.Document document,String passage){openDocument(document,passage,0);}
    void openDocument(Library.Document document,String passage,int ordinal) {
        documentOpen=false;worker.execute(()->{try{Library.Document full=library.load(document.id());Library.Metadata metadata=library.metadata(full.id());runOnUiThread(()->{if(!closed){showDocument(full,passage,metadata,ordinal);documentOpen=true;}});}catch(Exception e){error(getString(R.string.chat_source_removed));}});
    }
    private void showDocument(Library.Document document,String passage,Library.Metadata metadata,int ordinal) {
        if(metadata.format().equals("osm")){showOsm(document,ordinal);return;}
        if(metadata.format().equals("pdf")){showPdf(document,Math.max(1,ordinal));return;}
        ScrollView view=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(20),dp(12),dp(20),dp(20));view.addView(body);
        add(body,text(document.source(),12,MUTED),0,0);
        if(!metadata.active())add(body,text(getString(R.string.chat_archived_source),12,MUTED),8,0);
        if(ordinal>0){String label=(metadata.format().equals("csv")?"CSV record ":"Passage ")+ordinal;add(body,text(label,13,GREEN),12,0);if(passage!=null)add(body,text(passage,16,INK),8,0);}
        add(body,text(getString(R.string.source_content_date,metadata.contentDate().isEmpty()?getString(R.string.unknown_value):metadata.contentDate()),12,MUTED),12,0);
        TextView original=text(document.body(),15,INK);original.setTextIsSelectable(true);add(body,original,18,0);
        add(body,text(getString(R.string.source_identity,metadata.revision(),metadata.language(),metadata.sha256().substring(0,12)),11,MUTED),18,0);
        new AlertDialog.Builder(this).setTitle(document.title()).setView(view).setPositiveButton(R.string.chat_done,null).show();
    }
    private void showOsm(Library.Document document,int ordinal) {
        worker.execute(()->{
            try {
                OsmStorage.Row selected=ordinal>0?library.osmFeature(document.id(),ordinal):null;
                int count=library.osmFeatureCount(document.id());
                runOnUiThread(()->{
                    if(closed)return;
                    LinearLayout body=column();body.setPadding(dp(18),dp(12),dp(18),dp(12));
                    TextView credit=text(OsmImporter.ATTRIBUTION,13,GREEN);add(body,credit,0,0);add(body,text(OsmImporter.LICENSE_URL,11,MUTED),4,0);
                    ScrollView list=new ScrollView(this);LinearLayout rows=column();list.addView(rows);body.addView(list,new LinearLayout.LayoutParams(-1,dp(390)));
                    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(selected==null?document.title():selected.feature().name()).setView(body).setPositiveButton(R.string.chat_done,null).create();
                    if(selected!=null){TextView record=text(selected.text(),14,INK);record.setTextIsSelectable(true);add(rows,record,10,0);if(!selected.feature().url().isEmpty()){TextView url=text(selected.feature().url(),12,GREEN);url.setTextIsSelectable(true);add(rows,url,12,0);}add(rows,text(document.body(),12,MUTED),20,0);}
                    else {
                        int[] page={0};LinearLayout controls=row();Button previous=button(getString(R.string.chat_previous),PAPER,GREEN),next=button(getString(R.string.chat_next),PAPER,GREEN);TextView range=text("",12,INK);range.setGravity(Gravity.CENTER);
                        controls.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(range,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(controls);
                        Runnable display=()->{
                            previous.setEnabled(false);next.setEnabled(false);int requested=page[0];
                            worker.execute(()->{try{List<OsmStorage.Row> features=library.osmFeatures(document.id(),requested*50);runOnUiThread(()->{if(closed||!dialog.isShowing()||page[0]!=requested)return;rows.removeAllViews();add(rows,text(document.body(),12,MUTED),8,0);for(OsmStorage.Row item:features){Button open=button(item.feature().name()+" · "+item.feature().key(),PAPER,GREEN);open.setOnClickListener(v->showOsm(document,item.ordinal()));add(rows,open,10,52);}range.setText(getString(R.string.osm_range,requested*50+1,Math.min(count,(requested+1)*50),count));previous.setEnabled(requested>0);next.setEnabled((requested+1)*50<count);list.scrollTo(0,0);});}catch(Exception error){error(getString(R.string.chat_source_removed));}});
                        };
                        previous.setOnClickListener(v->{page[0]--;display.run();});next.setOnClickListener(v->{page[0]++;display.run();});dialog.setOnShowListener(d->display.run());
                    }
                    dialog.show();documentOpen=true;
                });
            }catch(Exception e){error(getString(R.string.chat_source_removed));}
        });
    }

    private void showPdf(Library.Document document,int requestedPage) {
        try {
            JSONArray pages=new JSONArray(document.body());int[] page={Math.min(requestedPage,pages.length())};
            LinearLayout body=column();body.setPadding(dp(16),dp(8),dp(16),dp(12));LinearLayout controls=row();
            Button prev=button(getString(R.string.chat_previous),PAPER,GREEN),next=button(getString(R.string.chat_next),PAPER,GREEN);TextView number=text("",13,INK);number.setGravity(Gravity.CENTER);
            controls.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(number,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(controls);
            ScrollView viewport=new ScrollView(this);LinearLayout pageBody=column();viewport.addView(pageBody);body.addView(viewport,new LinearLayout.LayoutParams(-1,dp(390)));
            ImageView image=new ImageView(this);image.setId(R.id.chat_pdf_image);image.setAdjustViewBounds(true);pageBody.addView(image,new LinearLayout.LayoutParams(-1,-2));TextView previewState=text(getString(R.string.chat_pdf_loading),12,MUTED);add(pageBody,previewState,8,0);TextView extracted=text("",14,INK);extracted.setTextIsSelectable(true);add(pageBody,extracted,12,0);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(document.title()).setView(body).setPositiveButton(R.string.chat_done,null).create();
            Bitmap[] shown={null};int[] revision={0};
            Runnable display=()->{
                int target=page[0],request=++revision[0];number.setText(getString(R.string.chat_pdf_page,target,pages.length()));prev.setEnabled(target>1);next.setEnabled(target<pages.length());
                String value=pages.optString(target-1,"");extracted.setText(value.isBlank()?getString(R.string.chat_pdf_no_text):value);image.setImageDrawable(null);previewState.setText(R.string.chat_pdf_loading);
                if(shown[0]!=null){shown[0].recycle();shown[0]=null;}
                worker.execute(()->{
                    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(library.pdfFile(document.id()),ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfRenderer.Page pdfPage=renderer.openPage(target-1)) {
                        int width=900,height=Math.max(1,Math.min(1800,Math.round(width*(float)pdfPage.getHeight()/pdfPage.getWidth())));
                        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);pdfPage.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        runOnUiThread(()->{if(!closed&&dialog.isShowing()&&revision[0]==request){shown[0]=bitmap;image.setImageBitmap(bitmap);previewState.setText("");}else bitmap.recycle();});
                    }catch(Exception e){android.util.Log.e("Outpost","PDF preview failed",e);runOnUiThread(()->{if(dialog.isShowing()&&revision[0]==request)previewState.setText(R.string.chat_pdf_preview_missing);});}
                });
            };
            prev.setOnClickListener(v->{page[0]--;display.run();});next.setOnClickListener(v->{page[0]++;display.run();});
            dialog.setOnDismissListener(d->{revision[0]++;image.setImageDrawable(null);if(shown[0]!=null)shown[0].recycle();});dialog.show();display.run();documentOpen=true;
        }catch(Exception e){error(getString(R.string.chat_source_removed));}
    }
    static Intent folderPickerIntent(){return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);}
    private void pickDocument(){pick(10);}
    private void folderControls(LinearLayout parent) {
        Button folder=button(getString(R.string.folder_add),PAPER,GREEN);folder.setId(R.id.folder_import);folder.setEnabled(answerDone&&!importing);folder.setOnClickListener(v->startActivityForResult(folderPickerIntent(),14));add(parent,folder,10,50);
        add(parent,text(getString(R.string.folder_help),12,MUTED),8,0);
        if(folderCancellation!=null) {
            folderProgressView=text(folderProgress==null?getString(R.string.folder_scanning):folderProgressText(folderProgress),13,GREEN);add(parent,folderProgressView,12,0);
            Button cancel=button(getString(R.string.folder_cancel),PAPER,GREEN);cancel.setId(R.id.folder_cancel);cancel.setOnClickListener(v->{DocumentImporter.Cancellation current=folderCancellation;if(current!=null)current.cancel();cancel.setEnabled(false);cancel.setText(R.string.folder_stopping);});add(parent,cancel,8,48);
        } else if(!folderSummary.isEmpty()) {
            Button summary=button(getString(R.string.folder_last_report),PAPER,GREEN);summary.setId(R.id.folder_report);summary.setOnClickListener(v->showFolderSummary());add(parent,summary,10,48);
        }
    }
    private String folderProgressText(FolderImporter.Progress p){return getString(R.string.folder_progress,p.scanned(),p.imported(),p.unchanged(),p.skipped(),p.failed(),p.current());}
    private void showFolderSummary() {
        TextView message=text(folderSummary,14,INK);message.setTextIsSelectable(true);message.setPadding(dp(20),dp(16),dp(20),dp(16));ScrollView view=new ScrollView(this);view.addView(message);
        new AlertDialog.Builder(this).setTitle(R.string.folder_summary_title).setView(view).setPositiveButton(R.string.chat_done,null).show();
    }
    private String folderReportText(FolderImporter.Report report) {
        FolderImporter.Progress p=report.progress();StringBuilder text=new StringBuilder(getString(R.string.folder_counts,p.imported(),p.unchanged(),p.skipped(),p.failed(),p.scanned()));
        if(report.canceled())text.append("\n\n").append(getString(R.string.folder_canceled));
        if(report.limited())text.append("\n\n").append(getString(R.string.folder_limited));
        for(FolderImporter.Issue issue:report.issues())text.append("\n\n").append(issue.path()).append("\n").append(issue.reason());
        if(report.omittedDetails()>0)text.append("\n\n").append(getString(R.string.folder_more_issues,report.omittedDetails()));
        return text.toString();
    }
    void importFolder(Uri tree) {
        if(importing||!answerDone)return;
        importing=true;lastFolderReport=null;folderProgress=null;folderSummary="";
        DocumentImporter.Cancellation cancel=new DocumentImporter.Cancellation();folderCancellation=cancel;
        String operation=UUID.randomUUID().toString();getPreferences(MODE_PRIVATE).edit().putString("folder_operation",operation).putBoolean("folder_running",true).apply();render();
        worker.execute(()->{
            String summary;
            try {
                FolderImporter.Report report=new FolderImporter(getContentResolver(),new DocumentImporter(this,library)).run(tree,cancel,p->{
                    pendingFolderProgress.set(p);
                    if(folderUpdateQueued.compareAndSet(false,true))runOnUiThread(()->{
                        folderUpdateQueued.set(false);FolderImporter.Progress latest=pendingFolderProgress.get();
                        if(!closed&&folderCancellation==cancel){folderProgress=latest;if(folderProgressView!=null)folderProgressView.setText(folderProgressText(latest));}
                    });
                });
                lastFolderReport=report;summary=folderReportText(report);
            }catch(Exception error){summary=getString(R.string.folder_open_error);}
            String finished=summary;
            if(operation.equals(getPreferences(MODE_PRIVATE).getString("folder_operation","")))getPreferences(MODE_PRIVATE).edit().putBoolean("folder_running",false).putString("folder_summary",finished).apply();
            List<Library.Document> refreshed;
            try{refreshed=library.documents();}catch(Exception error){refreshed=documents;}
            List<Library.Document> updated=refreshed;
            runOnUiThread(()->{folderCancellation=null;importing=false;folderSummary=finished;documents=updated;if(!closed){render();showFolderSummary();}});
        });
    }

    private void pick(int request) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        if(request==10)intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/csv","text/markdown","application/pdf","application/xml","text/xml","application/json","application/vnd.openstreetmap.data+xml","application/octet-stream"});
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==10)importDocument(data.getData());else if(request==11)importModel(data.getData());else if(request==14)importFolder(data.getData());
    }
    void importDocument(Uri uri) {
        if(importing||!answerDone)return;importing=true;render();
        worker.execute(()->{
            try {
                String name="document";
                if("file".equals(uri.getScheme()))name=new File(uri.getPath()).getName();
                else try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))name=c.getString(0);}
                DocumentImporter.Result result=new DocumentImporter(this,library).importFile(uri,name,name,-1,new DocumentImporter.Cancellation());
                refreshDocuments();runOnUiThread(()->{if(!closed)Toast.makeText(this,result.added()?R.string.chat_imported:R.string.folder_file_unchanged,Toast.LENGTH_SHORT).show();});
            }catch(Exception e){error(e instanceof IllegalArgumentException?e.getMessage():getString(R.string.chat_import_error));}
            finally{runOnUiThread(()->{importing=false;if(!closed)render();});}
        });
    }
    static void copyBounded(InputStream input,java.io.OutputStream output,int max)throws Exception {
        if(input==null)throw new IllegalArgumentException("The file could not be opened.");byte[] buffer=new byte[8192];int n,total=0;
        while((n=input.read(buffer))!=-1){if(n>max-total)throw new IllegalArgumentException("This file exceeds the import size limit.");output.write(buffer,0,n);total+=n;}
    }
    private void importModel(Uri uri) {
        if(importing||!answerDone)return;importing=true;ModelStore destination=models;render();
        worker.execute(()->{try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalArgumentException(getString(R.string.chat_import_error));destination.install(input,n->{});runOnUiThread(()->{if(!closed)Toast.makeText(this,R.string.chat_model_ready,Toast.LENGTH_LONG).show();});}
            catch(Exception e){error(e instanceof IllegalArgumentException?e.getMessage():getString(R.string.chat_model_error));}
            finally{runOnUiThread(()->{importing=false;if(!closed)render();});}});
    }
    private void refreshDocuments(){List<Library.Document> updated=library.documents();runOnUiThread(()->{if(!closed){documents=updated;render();}});}
    private void error(String value){runOnUiThread(()->{if(!closed)new AlertDialog.Builder(this).setTitle(R.string.chat_could_not_complete).setMessage(value).setPositiveButton(R.string.chat_done,null).show();});}
    private void hideKeyboard(){View view=getCurrentFocus();if(view!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private void scrollBottom(){if(scroll!=null&&screen.equals("chat"))scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);return layout;}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(3),1);return view;}
    private Button button(String value,int bg,int fg){Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(fg);b.setMinHeight(dp(48));b.setPadding(dp(12),0,dp(12),0);b.setBackground(shape(bg,14,0));return b;}
    private Button icon(String kind,int description){Button button=new IconButton(this,kind);button.setContentDescription(getString(description));button.setBackground(shape(kind.equals("settings")||kind.equals("back")?BG:GREEN,26,0));return button;}
    private void label(LinearLayout parent,String value){TextView label=text(value,13,MUTED);label.setTypeface(null,Typeface.BOLD);add(parent,label,28,0);}
    private GradientDrawable shape(int color,int radius,int border){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));if(border!=0)shape.setStroke(dp(1),border);return shape;}
    private void add(LinearLayout parent,View view,int top,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height>0?dp(height):-2);p.topMargin=dp(top);parent.addView(view,p);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static final class IconButton extends Button {
        private final String kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        IconButton(Context context,String kind){super(context);this.kind=kind;setMinWidth(0);setMinHeight(0);setPadding(0,0,0,0);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,s=getWidth()/4f;paint.setColor(kind.equals("settings")||kind.equals("back")?GREEN:Color.WHITE);paint.setStrokeWidth(getWidth()/24f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
            if(kind.equals("settings")){canvas.drawCircle(x,y,s*.7f,paint);canvas.drawCircle(x,y,s*.22f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(x+(float)Math.cos(a)*s*.72f,y+(float)Math.sin(a)*s*.72f,x+(float)Math.cos(a)*s,y+(float)Math.sin(a)*s,paint);}}
            else if(kind.equals("back")){canvas.drawLine(x+s*.6f,y,x-s*.6f,y,paint);canvas.drawLine(x-s*.6f,y,x,y-s*.6f,paint);canvas.drawLine(x-s*.6f,y,x,y+s*.6f,paint);}
            else if(kind.equals("stop")){paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(x-s*.5f,y-s*.5f,x+s*.5f,y+s*.5f,s*.1f,s*.1f,paint);}
            else{canvas.drawLine(x,y+s*.7f,x,y-s*.7f,paint);canvas.drawLine(x,y-s*.7f,x-s*.6f,y-s*.1f,paint);canvas.drawLine(x,y-s*.7f,x+s*.6f,y-s*.1f,paint);}
        }
        @Override public boolean performClick(){return super.performClick();}
    }
}
