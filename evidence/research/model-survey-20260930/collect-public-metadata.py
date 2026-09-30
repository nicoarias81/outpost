"""Read public metadata only; never download weights or execute upstream code."""
import argparse, concurrent.futures, datetime, hashlib, json, pathlib, urllib.request

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',type=pathlib.Path,required=True,help='Scratch output directory; contains fetched metadata/source text, never weights.')
ROOT = parser.parse_args().output.resolve()
ROOT.mkdir(exist_ok=True,parents=True)
CACHE = ROOT / 'sources'
CACHE.mkdir(exist_ok=True)
REPOS = [
 'LiquidAI/LFM2.5-1.2B-Instruct', 'LiquidAI/LFM2.5-1.2B-Instruct-GGUF',
 'LiquidAI/LFM2-2.6B', 'LiquidAI/LFM2-2.6B-GGUF',
 'Qwen/Qwen3.5-2B', 'Qwen/Qwen3.5-4B', 'Qwen/Qwen3-4B-Instruct-2507',
 'Qwen/Qwen3-4B-Instruct-2507-GGUF',
 'google/gemma-4-E2B-it', 'google/gemma-4-E4B-it',
 'microsoft/bitnet-b1.58-2B-4T', 'microsoft/bitnet-b1.58-2B-4T-gguf',
 'microsoft/Phi-4-mini-instruct', 'HuggingFaceTB/SmolLM3-3B',
 'HuggingFaceTB/SmolLM3-3B-GGUF',
 'LiquidAI/LFM2.5-2.6B', 'LiquidAI/LFM2.5-2.6B-GGUF',
 'LiquidAI/LFM2.5-350M', 'LiquidAI/LFM2.5-350M-GGUF',
 'LiquidAI/LFM2.5-1.2B-Instruct-DSpark', 'LiquidAI/LFM2.5-Embedding-350M',
 'google/gemma-4-E2B-it-qat-q4_0-gguf', 'google/gemma-4-E4B-it-qat-q4_0-gguf',
 'google/gemma-4-E2B-it-assistant',
 'ggml-org/Qwen3.5-2B-GGUF', 'ggml-org/Qwen3.5-4B-GGUF',
 'unsloth/Qwen3.5-2B-GGUF', 'unsloth/Qwen3.5-4B-GGUF',
 'unsloth/Qwen3-4B-Instruct-2507-GGUF',
]
KEYS = ['model_type','architectures','hidden_size','intermediate_size','num_hidden_layers',
 'num_attention_heads','num_key_value_heads','head_dim','vocab_size','max_position_embeddings',
 'layer_types','full_attention_interval','linear_num_key_heads','linear_num_value_heads',
 'linear_key_head_dim','linear_value_head_dim','linear_conv_kernel_dim',
 'num_heads','num_kv_heads','block_types','conv_L_cache','layerwise_embedding_size',
 'hidden_size_per_layer_input','num_global_key_value_heads','global_head_dim',
 'sliding_window','dtype','torch_dtype','tie_word_embeddings','rope_parameters']

def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent':'Outpost-static-research/1.0'})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read(4_000_001)
        if len(data)>4_000_000: raise ValueError('Metadata exceeded 4MB bound')
        return data

def record(url, dest):
    data = fetch(url)
    dest.parent.mkdir(exist_ok=True,parents=True)
    dest.write_bytes(data)
    return data, {'url':url,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}

def model(repo):
    output = {'repo':repo}
    folder=CACHE/repo.replace('/','--')
    try:
        raw, output['metadata_source']=record('https://huggingface.co/api/models/'+repo+'?blobs=true',folder/'api.json')
        meta=json.loads(raw)
        revision=meta['sha']; output['revision']=revision
        output['license']=meta.get('cardData',{}).get('license')
        output['gated']=meta.get('gated')
        output['parameter_metadata']=meta.get('safetensors')
        output['artifacts']=[f for f in meta.get('siblings',[]) if f['rfilename'].lower().endswith(('.gguf','.onnx'))]
        output['files']=[]
        for name in ['README.md','config.json','generation_config.json','LICENSE','LICENSE.md','qad/config.json','qad/generation_config.json','qad/README.md']:
            if not any(f['rfilename']==name for f in meta.get('siblings',[])): continue
            try:
                data, entry=record(f'https://huggingface.co/{repo}/resolve/{revision}/{name}',folder/name)
                entry['file']=name;output['files'].append(entry)
                if name=='config.json':
                    cfg=json.loads(data); text=cfg.get('text_config',cfg)
                    output['text_config']={k:text[k] for k in KEYS if k in text}
                    output['outer_model_type']=cfg.get('model_type')
            except Exception as e: output.setdefault('errors',[]).append(name+': '+str(e))
    except Exception as e: output['error']=str(e)
    return output

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    models=list(pool.map(model,REPOS))
engram={}
try:
    raw, engram['commit_source']=record('https://api.github.com/repos/deepseek-ai/Engram/commits/main',CACHE/'Engram'/'commit.json')
    engram['commit']=json.loads(raw)['sha']
    raw,engram['tree_source']=record('https://api.github.com/repos/deepseek-ai/Engram/git/trees/'+engram['commit']+'?recursive=1',CACHE/'Engram'/'tree.json')
    engram['tree']=[x['path'] for x in json.loads(raw)['tree'] if x['type']=='blob']
    engram['files']=[]
    for name in ['README.md','engram_demo_v1.py','LICENSE']:
        data,entry=record('https://raw.githubusercontent.com/deepseek-ai/Engram/'+engram['commit']+'/'+name,CACHE/'Engram'/name)
        entry['file']=name;engram['files'].append(entry)
except Exception as e: engram['error']=str(e)
report={'captured_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'scope':'Public metadata only; no weights fetched, no upstream code/model execution. API artifact hashes are publisher metadata, not locally verified weight hashes.','models':models,'engram':engram}
(ROOT/'survey-metadata.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'models':[{'repo':m['repo'],'revision':m.get('revision'),'error':m.get('error'),'artifacts':len(m.get('artifacts',[]))} for m in models],'engram':engram.get('commit'),'engram_error':engram.get('error')},indent=2))
