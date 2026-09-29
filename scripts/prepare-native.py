"""Fetch pinned official build dependencies and a small emulator test model."""
import concurrent.futures
import hashlib
import json
import pathlib
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local'

def digest(path, algorithm):
    h = hashlib.new(algorithm)
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def download(url, path, algorithm, expected):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and digest(path, algorithm) == expected:
        print('Verified cache:', path.name, flush=True)
        return path
    partial = path.with_suffix(path.suffix + '.partial')
    request = urllib.request.Request(url, headers={'User-Agent': 'Outpost-dev/0.2'})
    with urllib.request.urlopen(request, timeout=60) as response, partial.open('wb') as target:
        shutil.copyfileobj(response, target, 1024 * 1024)
    if digest(partial, algorithm) != expected:
        raise RuntimeError('Checksum mismatch: ' + path.name)
    partial.replace(path)
    print('Downloaded and verified:', path.name, flush=True)
    return path

def prepare_tool(tool):
    archive = download('https://dl.google.com/android/repository/' + tool['Url'],
                       LOCAL / 'downloads' / tool['Url'], 'sha1', tool['SHA1'])
    destination = LOCAL / 'native' / ('cmake' if tool['Package'].startswith('cmake') else 'ndk')
    marker = destination / '.complete'
    if not marker.exists():
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as z:
            for entry in z.infolist():
                target = (destination / entry.filename).resolve()
                if not target.is_relative_to(destination.resolve()):
                    raise RuntimeError('Unsafe archive path')
            z.extractall(destination)
        marker.write_text(tool['SHA1'], encoding='utf-8')
    print('Ready:', tool['Package'], flush=True)

def prepare_model(model):
    url = f"https://huggingface.co/{model['repo']}/resolve/{model['revision']}/{model['file']}"
    download(url, LOCAL / 'models' / model['file'], 'sha256', model['sha256'])

if __name__ == '__main__':
    tools = json.loads((ROOT / 'toolchain-lock.json').read_text(encoding='utf-8-sig'))
    model = json.loads((ROOT / 'model-lock.json').read_text(encoding='utf-8-sig'))
    source = LOCAL / 'llama.cpp'
    revision = (ROOT / 'llama-revision.txt').read_text().strip()
    if not source.exists():
        subprocess.run(['git', 'init', str(source)], check=True)
        subprocess.run(['git', '-C', str(source), 'remote', 'add', 'origin', 'https://github.com/ggml-org/llama.cpp.git'], check=True)
        subprocess.run(['git', '-C', str(source), 'fetch', '--depth', '1', 'origin', revision], check=True)
        subprocess.run(['git', '-C', str(source), 'checkout', '--detach', 'FETCH_HEAD'], check=True)
    actual = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != revision:
        raise RuntimeError('llama.cpp revision differs from llama-revision.txt')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        futures = [executor.submit(prepare_tool, tool) for tool in tools]
        futures.append(executor.submit(prepare_model, model))
        for f in futures:
            f.result()
    properties = ROOT / 'local.properties'
    lines = properties.read_text(encoding='utf-8-sig').splitlines() if properties.exists() else []
    lines = [line for line in lines if not line.startswith('cmake.dir=')]
    lines.append('cmake.dir=' + (LOCAL / 'native' / 'cmake').as_posix())
    properties.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print('Native dependencies and emulator model ready.', flush=True)
