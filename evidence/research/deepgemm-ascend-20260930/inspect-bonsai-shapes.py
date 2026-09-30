"""Static GGUF header read only; no tensor-data load or model execution."""
from pathlib import Path
from collections import Counter
import hashlib
import json
import math
import struct

import argparse
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[3])
parser.add_argument('--output', type=Path, help='Optional new JSON file; an existing path is never overwritten.')
args = parser.parse_args()
ROOT = args.root.resolve()
LOCK = json.loads((ROOT / 'bonsai-lock.json').read_text(encoding='utf-8'))
MODEL = next(x for x in LOCK['models'] if x['id'] == 'bonsai4')
FILE = ROOT / '.local/models' / MODEL['file']
formats = {0:'B',1:'b',2:'H',3:'h',4:'I',5:'i',6:'f',7:'?',10:'Q',11:'q',12:'d'}
with FILE.open('rb') as f:
    def number(fmt):
        return struct.unpack('<'+fmt, f.read(struct.calcsize('<'+fmt)))[0]
    def string(keep=False):
        n = number('Q')
        if keep:
            return f.read(n).decode('utf-8')
        f.seek(n, 1)
    def skip(kind):
        if kind == 8:
            string()
        elif kind == 9:
            inner, count = number('I'), number('Q')
            if inner in formats:
                f.seek(struct.calcsize('<'+formats[inner])*count, 1)
            else:
                for _ in range(count): skip(inner)
        else:
            f.seek(struct.calcsize('<'+formats[kind]), 1)
    assert f.read(4) == b'GGUF' and number('I') == 3
    tensor_count, entries = number('Q'), number('Q')
    for _ in range(entries):
        string()
        skip(number('I'))
    tensors = []
    for _ in range(tensor_count):
        name = string(True)
        shape = [number('Q') for _ in range(number('I'))]
        kind, offset = number('I'), number('Q')
        tensors.append({'name': name, 'shape': shape, 'type': kind})
    header_end = f.tell()
q2 = [x for x in tensors if x['type'] == 42]
groups = Counter(tuple(x['shape']) for x in q2)
out = {'scope': 'Static header metadata only; no inference or tensor-data scan. Lock hash not freshly recomputed.',
       'modelFile': FILE.name, 'actualBytes': FILE.stat().st_size, 'modelSha256FromLock': MODEL['sha256'],
       'tensorCount': tensor_count, 'headerBytesReadOrSkipped': header_end,
       'tensorTypes': dict(Counter(x['type'] for x in tensors)),
       'q2MatrixShapes': [{'ggmlDimensions': list(shape), 'count': count, 'packedBytesEach': math.prod(shape)//64*18,
                          'examples': [x['name'] for x in q2 if tuple(x['shape']) == shape][:3]} for shape, count in sorted(groups.items())],
       'q2MatrixPayloadBytes': sum(math.prod(x['shape'])//64*18 for x in q2)}
if args.output:
    with args.output.open('x', encoding='utf-8') as result:
        result.write(json.dumps(out, indent=2)+'\n')
print(json.dumps(out, indent=2))
