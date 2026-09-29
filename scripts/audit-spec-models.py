"""Inspect GGUF headers/tensor descriptors for MTP and tokenizer compatibility; no inference."""
import hashlib
import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIZES = {0:1, 1:1, 2:2, 3:2, 4:4, 5:4, 6:4, 7:1, 10:8, 11:8, 12:8}
FORMATS = {0:'B', 1:'b', 2:'H', 3:'h', 4:'I', 5:'i', 6:'f', 7:'?', 10:'Q', 11:'q', 12:'d'}

def inspect(path):
    with path.open('rb') as f:
        def number(fmt):
            return struct.unpack('<'+fmt, f.read(struct.calcsize(fmt)))[0]
        def string(keep=True):
            size=number('Q')
            if keep:
                return f.read(size).decode('utf-8')
            f.seek(size,1)
        def value(kind, keep=False):
            if kind==8:
                return string(keep)
            if kind==9:
                sub=number('I'); count=number('Q')
                if sub in SIZES:
                    f.seek(SIZES[sub]*count,1)
                else:
                    for _ in range(count): value(sub)
                return None
            if keep: return number(FORMATS[kind])
            f.seek(SIZES[kind],1)
        if f.read(4)!=b'GGUF' or number('I')!=3:
            raise ValueError('Expected GGUF v3')
        n_tensors=number('Q'); n_meta=number('Q'); metadata={}; tokenizer={}
        for _ in range(n_meta):
            key=string(); kind=number('I'); start=f.tell()
            keep=key.startswith(('general.architecture','qwen3.')) or any(s in key.lower() for s in ('nextn','mtp','draft','predict'))
            v=value(kind,keep)
            if keep: metadata[key]=v
            end=f.tell()
            if key.startswith('tokenizer.') and key!='tokenizer.chat_template':
                f.seek(start); tokenizer[key]=hashlib.sha256(struct.pack('<I',kind)+f.read(end-start)).hexdigest(); f.seek(end)
        names=[]
        for _ in range(n_tensors):
            names.append(string()); dims=number('I'); f.seek(dims*8,1); number('I'); number('Q')
        blocks=sorted({int(m.group(1)) for name in names if (m:=re.match(r'blk\.(\d+)\.',name))})
        return {'file':path.name,'bytes':path.stat().st_size,'metadata':metadata,'tensorCount':n_tensors,
                'blockIndices':blocks,'mtpOrDraftTensorNames':[n for n in names if re.search(r'mtp|nextn|draft|predict',n,re.I)],
                'nonBlockTensors':[n for n in names if not n.startswith('blk.')], 'tokenizerFieldsSha256':tokenizer}

if __name__=='__main__':
    lock=json.loads((ROOT/'bonsai-lock.json').read_text(encoding='utf-8'))
    rows=[]
    for model in lock['models']:
        row=inspect(ROOT/'.local/models'/model['file'])
        row['modelSha256FromVerifiedLock']=model['sha256']; rows.append(row)
    report={'models':rows,'tokenizerFieldsIdentical':rows[0]['tokenizerFieldsSha256']==rows[1]['tokenizerFieldsSha256'],
            'scope':'Header/tensor-descriptor inspection only; no model execution. Audit applies to the pinned installed GGUFs, not all possible external adapters.'}
    dest=ROOT/'evidence/speculation/model-audit.json'; dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'models':[{k:r[k] for k in ('file','tensorCount','mtpOrDraftTensorNames','nonBlockTensors')} for r in rows],
                      'tokenizerFieldsIdentical':report['tokenizerFieldsIdentical']},indent=2))
