"""Read GGUF v3 metadata and tensor type IDs only; never execute the model."""
import collections
import json
import struct
import sys

SIZES={0:1,1:1,2:2,3:2,4:4,5:4,6:4,7:1,10:8,11:8,12:8}
FORMATS={0:'B',1:'b',2:'H',3:'h',4:'I',5:'i',6:'f',7:'?',10:'Q',11:'q',12:'d'}

def inspect(path):
    with open(path,'rb') as f:
        def integer(fmt): return struct.unpack('<'+fmt,f.read(struct.calcsize(fmt)))[0]
        def string(keep=True):
            size=integer('Q')
            if keep: return f.read(size).decode('utf-8')
            f.seek(size,1)
        def value(kind,keep=False):
            if kind==8: return string(keep)
            if kind==9:
                item=integer('I'); size=integer('Q')
                if item in SIZES: f.seek(SIZES[item]*size,1)
                else:
                    for _ in range(size): value(item)
                return None
            if kind not in SIZES: raise ValueError('Unknown metadata type')
            if keep: return integer(FORMATS[kind])
            f.seek(SIZES[kind],1)
        if f.read(4)!=b'GGUF' or integer('I')!=3: raise ValueError('Expected GGUF v3')
        tensors=integer('Q'); entries=integer('Q'); metadata={}
        for _ in range(entries):
            name=string(); kind=integer('I')
            keep=name in ('general.architecture','general.name','general.file_type','tokenizer.chat_template')
            v=value(kind,keep)
            if keep: metadata[name]=v
        types=collections.Counter()
        for _ in range(tensors):
            string(False); dimensions=integer('I'); f.seek(8*dimensions,1)
            types[integer('I')]+=1; f.seek(8,1)
        return {'path':path,'metadata':metadata,'tensorTypeCounts':dict(types)}

if __name__=='__main__':
    for path in sys.argv[1:]: print(json.dumps(inspect(path),ensure_ascii=False,indent=2))
