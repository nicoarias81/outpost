package dev.outpost.app;

import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;

final class EvidenceReview {
    static final String[] OPTIONS = {
        "Supported: the evidence supports the entire claim.",
        "Contradicted: the evidence contradicts the claim.",
        "Not stated: the evidence does not establish whether the claim is true."
    };
    static final String[] LABELS = {"Suggested support", "Possible contradiction", "Insufficient evidence"};
    static String instruction(String claim) { return "Using only the evidence, classify this claim: " + ResearchPrompt.clean(claim,600); }
    static String firstClaim(String answer) {
        String clean = answer.replaceAll("\\[\\d+\\]", "").replaceAll("\\s+([.,;:!?])", "$1").trim();
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ENGLISH); sentences.setText(clean);
        int end=sentences.next(); return ResearchPrompt.clean(end==BreakIterator.DONE ? clean : clean.substring(0,end).trim(),600);
    }
    static String evidence(List<Library.Hit> sources) {
        StringBuilder text=new StringBuilder();
        for(Library.Hit hit:sources) { if(text.length()>2500) break; text.append(ResearchPrompt.clean(hit.passage(),850)).append("\n\n"); }
        return text.toString().trim();
    }
    static String display(NativeEngine.Decision result) {
        if(result.reason()!=0) return result.reason()==2 ? "Review canceled." : "Review time limit reached.";
        double[] p=result.probabilities();
        return "According to Kev: " + LABELS[result.best()] + "\n" + String.format(Locale.ROOT,"Support %.0f %% · contradiction %.0f %% · not stated %.0f %%\n%.1f s",p[0]*100,p[1]*100,p[2]*100,result.totalMs()/1000.0)
            + "\nIndicative scores, without validated calibration for this corpus. They do not guarantee truth.";
    }
}
