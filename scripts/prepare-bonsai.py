"""Download the upstream-compatible g64 variants. No host inference."""
import concurrent.futures
import importlib.util
import json
import pathlib

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('prepare',ROOT/'scripts/prepare-native.py')
prepare=importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

def fetch(model):
    target=ROOT/'.local/models'/model['file']
    url=f"https://huggingface.co/{model['repo']}/resolve/{model['revision']}/{model['file']}"
    prepare.download(url,target,'sha256',model['sha256'])
    if target.stat().st_size!=model['size']:
        raise RuntimeError('Unexpected model size')

if __name__=='__main__':
    models=json.loads((ROOT/'bonsai-lock.json').read_text())['models']
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(fetch,models))
    print('Ternary Bonsai 1.7B and 4B g64 weights verified.',flush=True)
