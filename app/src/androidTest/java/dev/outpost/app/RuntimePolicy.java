package dev.outpost.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Calibration policy and report semantics, independently testable without a model run. */
final class RuntimePolicy {
    static final int[] WIDTHS = {1, 2, 4, 8};
    record Choice(int fastestWidth, int candidateWidth, int selectedWidth, long baselineMs,
                  long candidateMs, long selectedMs, boolean parity) {
        double speedup() { return (double)baselineMs / selectedMs; }
        double timeReductionPercent() { return 100.0 * (1.0 - (double)selectedMs / baselineMs); }
        JSONObject toJson() throws org.json.JSONException {
            return new JSONObject().put("widthOrder", new JSONArray(java.util.List.of(1,2,4,8)))
                .put("fastestWidth",fastestWidth).put("candidateWidth",candidateWidth).put("selectedWidth",selectedWidth)
                .put("baselineMedianMs",baselineMs).put("candidateMedianMs",candidateMs).put("selectedMedianMs",selectedMs)
                .put("candidateSpeedup",(double)baselineMs/candidateMs).put("selectedSpeedup",speedup())
                .put("selectedTimeReductionPercent",timeReductionPercent()).put("parity",parity)
                .put("rule","Narrowest grouping within 5% of fastest; adopt only with parity and baseline/selected > 1.05.");
        }
    }
    static Choice choose(long[] medians, boolean parity) {
        if (medians.length != WIDTHS.length) throw new IllegalArgumentException("Four width measurements are required");
        for (long value:medians) if(value<=0) throw new IllegalArgumentException("Positive timing measurements required");
        int fastest=0;
        for(int i=1;i<medians.length;i++) if(medians[i]<medians[fastest]) fastest=i;
        int candidate=fastest;
        for(int i=0;i<fastest;i++) if((double)medians[i]/medians[fastest]<=1.05) { candidate=i; break; }
        int selected=parity && (double)medians[0]/medians[candidate]>1.05 ? candidate:0;
        return new Choice(WIDTHS[fastest],WIDTHS[candidate],WIDTHS[selected],
            medians[0],medians[candidate],medians[selected],parity);
    }
}
