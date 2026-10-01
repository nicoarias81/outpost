#include "attention_probe.h"
#include "ggml-cpu-impl.h"
#include "ops.h"
#include <algorithm>
#include <atomic>
#include <sstream>

extern "C" void __real_ggml_compute_forward_flash_attn_ext(const ggml_compute_params *, ggml_tensor *);
namespace {
std::atomic<int> active{0}; // Zero is the product fast path; research mode + 1 otherwise.
int first_position = 0, requested_rows = 0;
struct Stats {
    long calls=0, split=0, vector=0, tiled=0, forced_ref=0, sliced=0, slice_rows=0;
    long rejected_shape=0, rejected_mask=0, rejected_scratch=0;
    long kv_min=0, kv_max=0, q_min=0, q_max=0, threads_min=0, threads_max=0;
    long logical_kv_min=0, logical_kv_max=0;
} stats;
void range(long value, long &lo, long &hi) { if(!lo || value<lo)lo=value;hi=std::max(hi,value); }
}
void outpost_attention_reset() { stats={}; }
void outpost_attention_begin(int mode,int position,int rows) {
    GGML_ASSERT(mode>=0 && mode<=4 && active.load()==0);
    first_position=position;requested_rows=rows;active.store(mode+1,std::memory_order_release);
}
void outpost_attention_end() { active.store(0,std::memory_order_release); }
std::string outpost_attention_stats() {
    std::ostringstream s;
    s<<"{\"calls\":"<<stats.calls<<",\"originalSplitCalls\":"<<stats.split
     <<",\"originalVectorCalls\":"<<stats.vector<<",\"originalTiledCalls\":"<<stats.tiled
     <<",\"forcedReferenceCalls\":"<<stats.forced_ref<<",\"slicedCalls\":"<<stats.sliced<<",\"sliceRows\":"<<stats.slice_rows
     <<",\"shapeRejections\":"<<stats.rejected_shape<<",\"maskRejections\":"<<stats.rejected_mask<<",\"scratchRejections\":"<<stats.rejected_scratch
     <<",\"kvMin\":"<<stats.kv_min<<",\"kvMax\":"<<stats.kv_max<<",\"queryMin\":"<<stats.q_min<<",\"queryMax\":"<<stats.q_max
     <<",\"threadsMin\":"<<stats.threads_min<<",\"threadsMax\":"<<stats.threads_max
     <<",\"logicalKvMin\":"<<stats.logical_kv_min<<",\"logicalKvMax\":"<<stats.logical_kv_max<<"}";return s.str();
}
extern "C" void __wrap_ggml_compute_forward_flash_attn_ext(const ggml_compute_params *p,ggml_tensor *dst) {
    const int control=active.load(std::memory_order_acquire);
    if(!control){__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
    const int mode=control-1;
    const auto *q=dst->src[0],*k=dst->src[1],*v=dst->src[2],*mask=dst->src[3];
    if(p->ith==0){
        stats.calls++;range(k->ne[1],stats.kv_min,stats.kv_max);range(q->ne[1],stats.q_min,stats.q_max);range(p->nth,stats.threads_min,stats.threads_max);
        bool kv=(k->type==GGML_TYPE_F16||k->type==GGML_TYPE_F32)&&k->type==v->type;
        if(!p->use_ref&&q->ne[1]==1&&q->ne[3]==1&&kv&&q->type==GGML_TYPE_F32&&k->ne[1]>=512)stats.split++;
        else if(!p->use_ref&&q->type==GGML_TYPE_F32&&kv&&q->ne[1]>=64&&v->ne[0]%4==0)stats.tiled++;
        else stats.vector++;
    }
    if(mode==1){auto params=*p;params.use_ref=true;if(p->ith==0)stats.forced_ref++;__real_ggml_compute_forward_flash_attn_ext(&params,dst);return;}
    if(mode==4){
        // Normal decode already has the exact serial KV extent and mask. Keep
        // its tensor unchanged, including non-dense cache layouts. Only the
        // logical attention worker count differs from the six-worker graph.
        if(q->ne[1]>1){__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
        if(p->nth!=6){if(p->ith==0)stats.rejected_shape++;__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
        if(p->ith==0){stats.sliced++;stats.slice_rows++;range(k->ne[1],stats.logical_kv_min,stats.logical_kv_max);}
        // The pinned single-query implementation has one internal pool barrier
        // in both branches. The planner's six-worker scratch also fits four.
        if(p->ith<4){auto logical=*p;logical.nth=4;__real_ggml_compute_forward_flash_attn_ext(&logical,dst);}
        else ggml_barrier(p->threadpool);
        ggml_barrier(p->threadpool);
        return;
    }
    if((mode!=2&&mode!=3)||q->ne[1]==1){__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
    // Narrow contract: sequential, dense causal Bonsai4 verification, four
    // logical workers and F16 K/V. Mode 3 keeps six scheduler workers for
    // surrounding matrix operations; only four execute attention arithmetic.
    // Unsupported shapes keep the original operation.
    bool shape=p->nth==(mode==3?6:4)&&!p->use_ref&&q->type==GGML_TYPE_F32&&k->type==GGML_TYPE_F16&&v->type==GGML_TYPE_F16
        &&q->ne[0]==128&&k->ne[0]==128&&v->ne[0]==128&&q->ne[2]==32&&k->ne[2]==8&&v->ne[2]==8
        &&q->ne[3]==1&&k->ne[3]==1&&v->ne[3]==1&&q->ne[1]==requested_rows&&requested_rows>=2&&requested_rows<=8
        &&first_position>=0&&first_position+requested_rows<=2048&&k->ne[1]==v->ne[1]&&k->ne[1]%256==0
        &&mask&&mask->type==GGML_TYPE_F16&&mask->ne[0]>=k->ne[1]&&mask->ne[1]>=q->ne[1]&&mask->ne[2]==1&&mask->ne[3]==1
        &&mask->nb[0]==2&&dst->ne[1]==q->ne[2]&&dst->ne[2]==q->ne[1]&&dst->ne[3]==1&&dst->nb[1]==128*sizeof(float);
    if(!shape){if(p->ith==0)stats.rejected_shape++;__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
    // Mask validation also rules out holes, sliding attention, reused unrelated
    // cells and non-causal/bias masks. Never infer valid positions from logits.
    bool dense=true;
    for(int row=0;row<requested_rows;row++){
        const auto *m=reinterpret_cast<const uint16_t *>(static_cast<const char *>(mask->data)+row*mask->nb[1]);
        int extent=((first_position+row+1+255)/256)*256;
        if(extent>k->ne[1])dense=false;
        for(int col=0;col<k->ne[1];col++)if(col<=first_position+row ? (m[col]&0x7fff)!=0 : m[col]!=0xfc00)dense=false;
    }
    if(!dense){if(p->ith==0)stats.rejected_mask++;__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
    size_t need=sizeof(float)*p->nth*(k->ne[0]+2*v->ne[0]+CACHE_LINE_SIZE_F32+q->ne[2]*(2+v->ne[0]));
    if(!p->wdata||p->wsize<need){if(p->ith==0)stats.rejected_scratch++;__real_ggml_compute_forward_flash_attn_ext(p,dst);return;}
    if(p->ith==0){stats.sliced++;stats.slice_rows+=requested_rows;}
    for(int row=0;row<requested_rows;row++){
        ggml_tensor qs=*q,ks=*k,vs=*v,ms=*mask,out=*dst;
        qs.ne[1]=1;qs.data=static_cast<char *>(q->data)+row*q->nb[1];
        const int extent=((first_position+row+1+255)/256)*256;
        ks.ne[1]=vs.ne[1]=extent;ms.ne[1]=1;ms.data=static_cast<char *>(mask->data)+row*mask->nb[1];
        out.ne[2]=1;out.data=static_cast<char *>(dst->data)+row*dst->nb[2];
        out.src[0]=&qs;out.src[1]=&ks;out.src[2]=&vs;out.src[3]=&ms;
        if(p->ith==0)range(extent,stats.logical_kv_min,stats.logical_kv_max);
        if(mode==3){
            // The pinned original single-query operation has exactly one
            // internal pool barrier, in both the vector and split-KV paths.
            // Idle workers participate in it, then in the exit barrier below.
            // Scratch indexing and reduction order use four logical workers.
            if(p->ith<4){auto logical=*p;logical.nth=4;__real_ggml_compute_forward_flash_attn_ext(&logical,&out);}
            else ggml_barrier(p->threadpool);
        }else __real_ggml_compute_forward_flash_attn_ext(p,&out);
        // Original split-KV reduction has no exit barrier. All readers must
        // finish before the next query reuses partials and the chunk counter.
        ggml_barrier(p->threadpool);
    }
}
