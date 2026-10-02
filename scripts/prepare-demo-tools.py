from pathlib import Path
import hashlib,json,urllib.request,zipfile
root=Path(__file__).resolve().parents[1];local=root/'.local/demo-tools';local.mkdir(parents=True,exist_ok=True)
assets=[
 {'name':'tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf','bytes':668788096,'sha256':'9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0','url':'https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/52e7645ba7c309695bec7ac98f4f005b139cf465/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf'},
 {'name':'imageio_ffmpeg-0.6.0-py3-none-win_amd64.whl','bytes':31246824,'sha256':'02fa47c83703c37df6bfe4896aab339013f62bf02c5ebf2dce6da56af04ffc0a','url':'https://files.pythonhosted.org/packages/2c/c6/fa760e12a2483469e2bf5058c5faff664acf66cadb4df2ad6205b016a73d/imageio_ffmpeg-0.6.0-py3-none-win_amd64.whl'}]
def digest(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
for asset in assets:
 p=local/asset['name']
 if not p.exists() or p.stat().st_size!=asset['bytes'] or digest(p)!=asset['sha256']:
  partial=p.with_suffix(p.suffix+'.partial');req=urllib.request.Request(asset['url'],headers={'User-Agent':'Outpost-demo-preparation'})
  with urllib.request.urlopen(req,timeout=60) as response,partial.open('wb') as out:
   while b:=response.read(1024*1024):out.write(b)
  assert partial.stat().st_size==asset['bytes'] and digest(partial)==asset['sha256'],asset['name']
  partial.replace(p)
 print('Verified',asset['name'],p.stat().st_size,flush=True)
 if p.suffix=='.whl':
  with zipfile.ZipFile(p) as z:
   files=[n for n in z.namelist() if n.endswith('.exe') and '/binaries/' in n];assert len(files)==1
   (local/'ffmpeg.exe').write_bytes(z.read(files[0]))
(local/'downloads.json').write_text(json.dumps(assets,indent=2)+'\n')
print('Local video tool and baseline model are ready. No model executed on the host.')
