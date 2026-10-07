package dev.outpost.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Reproduces the recorded synthetic citation-only failure without reading the library. */
final class ChatAnswerChecks {
    static void run(Instrumentation test){
        JSONObject report=new JSONObject();JSONArray answers=new JSONArray();boolean passed=false;
        String system=ChatPrompt.SYSTEM;
        String first="For this conversation my code name is Cedar. Reply with just the code name.";
        String follow="What code name did I just give you?";
        String question="What spare filter does sample unit PX-65 use? Cite the document.";
        var one=new ChatStore.Turn("one",first,"Cedar","complete",List.of());
        var two=new ChatStore.Turn("two",follow,"Cedar","complete",List.of());
        String facts="[1] Field note.txt\nSynthetic document for application verification. Sample unit PX-65 lists spare filter F-92. No current operating condition is recorded.\n\n[2] Equipment manual.pdf\nSample unit PX-65 uses spare filter F-92.\n\n[3] Equipment manual.pdf\nSynthetic manual: sample unit PX-65.\n\n";
        String empty="No matching document is available. You may use general knowledge, but do not claim to have read a personal file or checked current information. Do not add citations.\n";
        String independent=ChatPrompt.prepare(question,List.of(),List.of()).user().replace(empty,facts);
        String legacy=independent.replace("RECENT CONVERSATION (context, not verified evidence):\n", "RECENT CONVERSATION (context, not verified evidence):\nUser: "+first+"\nAssistant: Cedar\nUser: "+follow+"\nAssistant: Cedar\n");
        var doc=new Library.Document("diagnostic","Manual","Test","Synthetic","","","F-92");
        String filtered=ChatPrompt.prepare(question,List.of(one,two),List.of(new Library.Hit(doc,"F-92",1,1))).user();
        filtered=filtered.substring(0,filtered.indexOf("DOCUMENTS FOR THIS TURN:"))+independent.substring(independent.indexOf("DOCUMENTS FOR THIS TURN:"));
        try{
            if(!TestTargets.ownedQa(test))throw new IllegalStateException("Owned regression QA required");
            var model=new ModelStore(test.getTargetContext(),ModelStore.BONSAI4);
            try(var engine=new NativeEngine()){
                engine.configure(RuntimeSettings.load(test.getTargetContext(),ModelStore.BONSAI4).configuration(true));engine.traceLogitsForTests(true);
                var old=engine.generateWithSampling(engine.request(),model.file(),system,legacy,192,true,(s,n)->{});
                answers.put(row("legacy-unrelated-history",old));engine.clearCache();
                var cold=engine.generateWithSampling(engine.request(),model.file(),system,filtered,192,true,(s,n)->{});long[] trace=engine.logitTrace();answers.put(row("filtered-history",cold));engine.clearCache();
                engine.generateWithSampling(engine.request(),model.file(),system,ChatPrompt.prepare(first,List.of(),List.of()).user(),192,true,(s,n)->{});
                engine.generateWithSampling(engine.request(),model.file(),system,ChatPrompt.prepare(follow,List.of(one),List.of()).user(),192,true,(s,n)->{});
                var warm=engine.generateWithSampling(engine.request(),model.file(),system,filtered,192,true,(s,n)->{});answers.put(row("filtered-after-two-turns",warm));
                boolean parity=cold.text().equals(warm.text())&&java.util.Arrays.equals(trace,engine.logitTrace());
                report.put("cacheParity",parity).put("legacyUser",legacy).put("filteredUser",filtered).put("answersFact",cold.text().contains("F-92"));
                if(!parity||!cold.text().contains("F-92"))throw new AssertionError("Filtered document response or cache parity failed");
                passed=true;
            }
        }catch(Throwable error){try{report.put("error",error.toString());}catch(Exception ignored){}}
        try{report.put("passed",passed).put("answers",answers);}catch(Exception ignored){}
        Bundle out=new Bundle();out.putString("stream","\nANSWER_DIAGNOSTIC "+report+"\n");test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
    static JSONObject row(String name,NativeEngine.Result r)throws Exception{return new JSONObject().put("case",name).put("text",r.text()).put("reason",r.reason()).put("tokens",r.tokens()).put("cachedTokens",r.cachedTokens()).put("firstTokenMs",r.firstTokenMs()).put("totalMs",r.totalMs());}
}
