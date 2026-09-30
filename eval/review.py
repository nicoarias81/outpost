#!/usr/bin/env python3
"""Validate attributed review records against a frozen evaluation run; never infer quality from markers."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

def load(path): return json.loads(path.read_text(encoding="utf-8-sig"))

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("review",type=Path)
    args=parser.parse_args()
    root=args.review.resolve().parent
    review=load(args.review); results=load(root/"results.json"); manifest=load(root/"manifest.json")
    digest=hashlib.sha256((root/"results.json").read_bytes()).hexdigest()
    if review.get("resultsSha256")!=digest:raise ValueError("Review does not identify these exact recorded results")
    if review.get("runId")!=results.get("runId"):raise ValueError("Review/run identity mismatch")
    if review.get("rubricVersion") != 2:raise ValueError("This review validator requires explicit rubricVersion 2")
    author=review.get("reviewer",{})
    if author.get("kind") not in ["human","assistant"] or not author.get("name"):raise ValueError("An attributed reviewer is required")
    rows={(r["fixtureId"],r["variant"]):r for r in results["rows"] if "variant" in r}
    fixtures={f["id"]:f for f in manifest["fixtures"]}
    seen=set(); counts=collections.defaultdict(collections.Counter)
    for item in review["assessments"]:
        key=(item["fixtureId"],item["variant"])
        if key not in rows or key in seen:raise ValueError("Unknown or duplicate reviewed row")
        seen.add(key); fixture=fixtures[key[0]]
        if not item.get("rationale"):raise ValueError("Review rationale required")
        if item["taskOutcome"] not in ["satisfied","partial","failed"]:raise ValueError("Invalid task outcome")
        indices=item.get("criticalFailureIndices",[])
        if len(set(indices))!=len(indices) or any(type(i)!=int or i<0 or i>=len(fixture["critical_failures"]) for i in indices):
            raise ValueError("Critical failures must reference the frozen fixture definitions")
        if indices and item["taskOutcome"]!="failed":raise ValueError("A critical failure cannot be averaged away")
        if set(item["dimensions"])!=set(fixture["review_dimensions"]):raise ValueError("Review dimensions must match the fixture")
        for name,score in item["dimensions"].items():
            if score is None:
                if not item.get("unscoredReasons",{}).get(name):raise ValueError("Unscored dimensions need reasons")
            elif type(score)!=int or not 0<=score<=3:raise ValueError("Scores must be integers 0..3 or explicitly unscored")
        counts[key[1]][item["taskOutcome"]]+=1
        counts[key[1]]["criticalFailures"]+=len(indices)
    if seen!=set(rows):raise ValueError("Every attempted fixture/variant needs a review row")
    print(json.dumps({"runId":results["runId"],"reviewer":author,"reviewedRows":len(seen),
        "taskOutcomes":{k:dict(v) for k,v in counts.items()},
        "limits":"Attributed development review only. Counts are not general accuracy, clinical/field validation, or bounty compliance."},indent=2))

if __name__=="__main__":main()
