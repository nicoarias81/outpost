#!/usr/bin/env python3
"""Prepare a bounded offline text/CSV knowledge pack; no network or inference."""
import argparse
import hashlib
import json
from pathlib import Path
import re

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--id",required=True)
    p.add_argument("--version",required=True,type=int)
    p.add_argument("--title",required=True)
    p.add_argument("--source",required=True)
    p.add_argument("--license",required=True,help="Declared rights/provenance; this tool does not determine redistribution rights")
    p.add_argument("--language",default="en")
    p.add_argument("--content-date",default="")
    p.add_argument("--output",required=True,type=Path)
    p.add_argument("documents",nargs="+",type=Path)
    a=p.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}",a.id) or a.version<1:p.error("Invalid package ID or version")
    if not 1<=len(a.documents)<=32:p.error("A pack needs 1..32 documents")
    if a.output.exists():p.error("Output already exists; use a new path/version")
    if a.content_date:
        import datetime
        datetime.date.fromisoformat(a.content_date)
    docs=[]
    for i,path in enumerate(a.documents,1):
        data=path.read_bytes()
        if not 0<len(data)<=1_048_576:p.error(f"Document must be non-empty and at most 1 MiB: {path}")
        if path.suffix.lower() not in [".txt",".md",".markdown",".csv"]:p.error("Only text, Markdown and CSV are supported")
        body=data.decode("utf-8")
        if "\0" in body:p.error("NUL is not allowed in text content")
        docs.append({"id":f"document-{i}","title":path.name,"category":"Imported knowledge",
                     "format":"csv" if path.suffix.lower()==".csv" else "text","body":body,
                     "sha256":hashlib.sha256(body.encode("utf-8")).hexdigest(),"contentDate":a.content_date})
    pack={"schemaVersion":1,"id":a.id,"version":a.version,"title":a.title,"source":a.source,
          "license":a.license,"language":a.language,"contentDate":a.content_date,"documents":docs}
    result=(json.dumps(pack,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
    if len(result)>4_194_304:p.error("Package exceeds 4 MiB")
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with a.output.open("xb") as output:output.write(result)
    print(f"Wrote {a.output}: {len(docs)} documents, {len(result)} bytes")
    print("sha256",hashlib.sha256(result).hexdigest())
    print("The Android importer performs final schema/CSV/integrity validation before activation.")

if __name__=="__main__":main()
