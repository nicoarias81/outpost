"""Download the pinned Kev conversion; no model inference runs on the host."""
import concurrent.futures
import importlib.util
import json
import pathlib
import shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare', ROOT / 'scripts/prepare-native.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
lock = json.loads((ROOT / 'judge-lock.json').read_text())

def fetch(item):
    url = f"https://huggingface.co/{lock['repo']}/resolve/{lock['revision']}/{item['file']}"
    target = ROOT / '.local/models/kev' / item['file']
    prepare.download(url, target, 'sha256', item['sha256'])
    if target.stat().st_size != item['size']:
        raise RuntimeError('Wrong size: ' + item['file'])
    if not item['file'].endswith('.gguf'):
        assets = ROOT / 'app/src/debug/assets/kev'
        assets.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(target, assets / item['file'])

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        list(executor.map(fetch, lock['files']))
    print('Kev GGUF, pointer head and metadata verified.', flush=True)
