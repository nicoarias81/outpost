#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include "ggml-cpu.h"
#include <sched.h>
#include "q2_kernel.h"
#include "q2_arm.h"
#include "q2_dispatch.h"
#include "speculation.h"
#include "attention_probe.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>
#include <sstream>
#include <locale>

namespace {
using Clock = std::chrono::steady_clock;
long elapsed(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now() - start).count();
}

uint64_t hash_logits(const float *values,size_t count){const auto *raw=reinterpret_cast<const unsigned char *>(values);uint64_t h=14695981039346656037ULL;for(size_t j=0;j<count*sizeof(float);j++){h^=raw[j];h*=1099511628211ULL;}return h;}

long micros(Clock::time_point start) { return std::chrono::duration_cast<std::chrono::microseconds>(Clock::now()-start).count(); }
struct SampleEvent {uint64_t hash;int index;llama_token token;int row;const char *kind;};
struct RoundEvent {
    const char *kind="terminal";int first=0,committed=0,proposed=0,accepted=0,samples=0;
    long total_us=0,proposal_us=0,evaluate_us=0,sample_us=0,rollback_us=0,emit_us=0,trace_us=0;
    bool complete=false;
};
struct RoundScope {
    bool capture,finished=false;std::vector<RoundEvent> &events;int &generated;Clock::time_point start=Clock::now();RoundEvent value;
    RoundScope(bool enabled,std::vector<RoundEvent>& out,int& n):capture(enabled),events(out),generated(n){value.first=n;}
    void finish(bool complete){if(finished)return;value.total_us=micros(start);value.committed=generated-value.first;value.complete=complete;if(capture)events.push_back(value);finished=true;}
    long controller_us()const{return std::max(0L,value.total_us-value.trace_us);}
    ~RoundScope(){finish(false);}
};
struct Session {
    std::mutex inference;
    std::atomic<jlong> cancelledThrough{0};
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    int context_batch = 0;
    int context_threads=0,context_prompt_threads=0,context_width=0,context_rows=1,context_decode_rows=1,context_prefill_chunk=0,context_decode_chunk=0,context_attention_threads=0;
    std::string context_kernel;
    std::vector<llama_token> prefix;
    std::vector<float> prefix_logits;
    std::vector<llama_token> last_tokens;
    std::vector<llama_token> last_prompt_tokens;
    bool capture_logit_trace=false;
    std::vector<uint64_t> logit_trace;
    std::vector<SampleEvent> sample_events;
    bool capture_spec_stats=false;
    int test_attention_mode=0;
    std::string last_attention_stats="{}";
    std::vector<RoundEvent> round_events;
    int context_template=0;
    long test_generation_deadline_ms=120000;
    bool persistent_threads=false;
    uint32_t affinity_mask=0,effective_affinity_mask=0;
    ggml_threadpool_t pool=nullptr,pool_batch=nullptr;
    int pool_threads=0,pool_prompt_threads=0,pool_creations=0;
    uint32_t affinity_before=0,affinity_during=0,affinity_after=0;
    bool affinity_restored=true;
    bool affinity_captured=false;
    bool pools_paused=true;
    std::string path;
    void pause_pools(){if(pool)ggml_threadpool_pause(pool);if(pool_batch&&pool_batch!=pool)ggml_threadpool_pause(pool_batch);pools_paused=true;}
    void free_pools(){
        // The CPU backend retains its pool pointer; destroy the context before its pools.
        if(context)clear_context();
        if(pool_batch&&pool_batch!=pool)ggml_threadpool_free(pool_batch);
        if(pool)ggml_threadpool_free(pool);pool=nullptr;pool_batch=nullptr;pool_threads=0;pool_prompt_threads=0;effective_affinity_mask=0;
    }
    void prepare_pools(int threads,int prompt_threads){
        if(!persistent_threads)return;
        uint32_t desired_mask=(affinity_captured&&affinity_mask&&(affinity_mask&affinity_before)==affinity_mask&&__builtin_popcount(affinity_mask)>=std::max(threads,prompt_threads))?affinity_mask:0;
        if(!pool||pool_threads!=threads||pool_prompt_threads!=prompt_threads||effective_affinity_mask!=desired_mask){
            free_pools();effective_affinity_mask=desired_mask;
            auto create=[&](int n){auto settings=ggml_threadpool_params_default(n);settings.paused=true;
                if(effective_affinity_mask){settings.strict_cpu=true;for(int i=0;i<8;i++)settings.cpumask[i]=(effective_affinity_mask&(1u<<i))!=0;}
                auto p=ggml_threadpool_new(&settings);if(!p)throw std::runtime_error("Cannot allocate inference thread pool");pool_creations++;return p;};
            pool=create(threads);pool_batch=prompt_threads==threads?pool:create(prompt_threads);pool_threads=threads;pool_prompt_threads=prompt_threads;
        }
    }
    void clear_context() {
        pause_pools();if(context){llama_detach_threadpool(context);llama_free(context);}
        context = nullptr; context_batch = 0; prefix.clear(); prefix_logits.clear();
    }
    ~Session() { clear_context(); free_pools(); if (model) llama_model_free(model); }
    bool cancelled(jlong run) const { return cancelledThrough.load() >= run; }
};
uint32_t affinity_bits(const cpu_set_t &cpus){uint32_t bits=0;for(int i=0;i<8;i++)if(CPU_ISSET(i,&cpus))bits|=1u<<i;return bits;}
struct RestoreAffinity {
    Session *session;cpu_set_t saved;bool captured;
    explicit RestoreAffinity(Session *s):session(s){CPU_ZERO(&saved);captured=sched_getaffinity(0,sizeof(saved),&saved)==0;session->affinity_captured=captured;session->affinity_before=captured?affinity_bits(saved):0;}
    ~RestoreAffinity(){cpu_set_t current;CPU_ZERO(&current);if(sched_getaffinity(0,sizeof(current),&current)==0)session->affinity_during=affinity_bits(current);
        session->affinity_restored=!captured||!session->affinity_mask||sched_setaffinity(0,sizeof(saved),&saved)==0;CPU_ZERO(&current);if(sched_getaffinity(0,sizeof(current),&current)==0)session->affinity_after=affinity_bits(current);}
};
struct Abort {
    std::shared_ptr<Session> session;
    jlong run;
    Clock::time_point start;
    long deadline_ms=120000;
};
bool abort_eval(void *data) {
    auto *a = static_cast<Abort *>(data);
    return a->session->cancelled(a->run) || elapsed(a->start) > a->deadline_ms;
}
bool load_progress(float, void *data) { return !abort_eval(data); }
std::mutex registry_mutex;
// One active graph per process also keeps the batched-kernel policy stable across workers.
std::mutex execution_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> sessions;
jlong next_id = 1;
std::once_flag backend;
std::shared_ptr<Session> get(jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto it = sessions.find(id);
    if (it == sessions.end()) throw std::runtime_error("Engine is closed");
    return it->second;
}
std::string bytes(JNIEnv *env, jbyteArray data) {
    if (!data) throw std::runtime_error("Missing input");
    auto size = env->GetArrayLength(data);
    if (size > 24000) throw std::runtime_error("Input is too large");
    std::string text(size, '\0');
    env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte *>(text.data()));
    return text;
}
void fail(JNIEnv *env, const char *text) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text);
}
// Only emit complete UTF-8 sequences. JNI modified UTF-8 is not used for model text.
size_t complete_prefix(const std::string &s) {
    size_t p = 0;
    while (p < s.size()) {
        auto c = static_cast<unsigned char>(s[p]);
        size_t n = c < 0x80 ? 1 : c < 0xe0 ? 2 : c < 0xf0 ? 3 : 4;
        if (p + n > s.size()) break;
        p += n;
    }
    return p;
}
bool emit(JNIEnv *env, jobject callback, jmethodID method, const std::string &text, int count) {
    size_t length = complete_prefix(text);
    jbyteArray value = env->NewByteArray(static_cast<jsize>(length));
    if (!value) return false;
    env->SetByteArrayRegion(value, 0, static_cast<jsize>(length), reinterpret_cast<const jbyte *>(text.data()));
    env->CallVoidMethod(callback, method, value, count);
    env->DeleteLocalRef(value);
    return !env->ExceptionCheck();
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_outpost_app_NativeEngine_nativeCreate(JNIEnv *env, jclass) {
    try {
        std::call_once(backend, [] {
            llama_log_set([](ggml_log_level level, const char *text, void *) {
                if (level == GGML_LOG_LEVEL_ERROR) __android_log_write(ANDROID_LOG_ERROR, "OutpostEngine", text);
            }, nullptr);
            llama_backend_init();
        });
        std::lock_guard<std::mutex> lock(registry_mutex);
        jlong id = next_id++;
        sessions.emplace(id, std::make_shared<Session>());
        return id;
    } catch (const std::exception &e) { fail(env, e.what()); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeCancel(JNIEnv *, jclass, jlong id, jlong run) {
    try {
        auto session = get(id);
        jlong current = session->cancelledThrough.load();
        while (current < run && !session->cancelledThrough.compare_exchange_weak(current, run)) {}
    } catch (...) { }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeClose(JNIEnv *, jclass, jlong id) {
    std::shared_ptr<Session> removed;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto it = sessions.find(id);
        if (it == sessions.end()) return;
        removed = it->second;
        sessions.erase(it);
    }
    removed->cancelledThrough.store(INT64_MAX);
    // Active calls own a shared reference; closing cannot free a running model.
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeClearCache(JNIEnv *env,jclass,jlong id) {
    try { auto s=get(id); std::lock_guard<std::mutex> lock(s->inference); s->clear_context(); }
    catch(const std::exception &e) { fail(env,e.what()); }
}
llama_token sample_saved_logits(llama_sampler *sampler,const std::vector<float> &logits) {
    std::vector<llama_token_data> candidates(logits.size());
    for(size_t i=0;i<logits.size();i++) candidates[i]={(llama_token)i,logits[i],0.0f};
    llama_token_data_array distribution{candidates.data(),candidates.size(),-1,false};
    llama_sampler_apply(sampler,&distribution);
    if(distribution.selected<0 || (size_t)distribution.selected>=distribution.size) throw std::runtime_error("Invalid cached-logit sample");
    auto token=distribution.data[distribution.selected].id;
    llama_sampler_accept(sampler,token);
    return token;
}

namespace {
std::vector<llama_token> protocol_tokens(const llama_vocab *vocab,const std::string &text,bool special) {
    int count=-llama_tokenize(vocab,text.data(),text.size(),nullptr,0,false,special);
    if(count==0)return {};
    if(count<0 || count>24000)throw std::runtime_error("Invalid protocol token count");
    std::vector<llama_token> result(count);
    if(llama_tokenize(vocab,text.data(),text.size(),result.data(),count,false,special)!=count)
        throw std::runtime_error("Protocol tokenization failed");
    return result;
}
std::vector<llama_token> spark_prompt(const llama_model *model,const std::string &system,const std::string &user) {
    char arch[64]={0};
    if(llama_model_meta_val_str(model,"general.architecture",arch,sizeof(arch))<1 || std::string(arch)!="spark2_5")
        throw std::runtime_error("Spark protocol requires a Spark model");
    const auto *vocab=llama_model_get_vocab(model);
    std::vector<llama_token> result;
    auto marker=[&](const char *text,llama_token expected) {
        auto ids=protocol_tokens(vocab,text,true);
        if(ids.size()!=1 || ids[0]!=expected)throw std::runtime_error("Unsupported Spark vocabulary");
        result.push_back(ids[0]);
    };
    auto content=[&](const std::string &text) {
        // This backend still recognizes USER_DEFINED tokens when parse_special=false.
        // Every special spelling in the pinned Spark vocabulary starts with '<'. Split after it
        // to preserve literal bytes without recognizing a complete control/reasoning/tool marker.
        size_t start=0;
        while(start<text.size()) {
            size_t angle=text.find('<',start),end=angle==std::string::npos?text.size():angle+1;
            auto ids=protocol_tokens(vocab,text.substr(start,end-start),false);
            result.insert(result.end(),ids.begin(),ids.end());start=end;
        }
    };
    // Exact two-message, no-tools, enable_thinking=false projection of the pinned official template.
    // GGUF template SHA256: 84493de66d859ab2694ec760056c82690af45de2dd170f1b6245456378f32f52.
    marker("<｜start▁of▁sentence｜>",0);marker("<|System|>",130972);
    content(std::string("\nyou are a helpful assistant.")+(system.empty()?"":"\n\n"+system));
    marker("<｜end▁of▁sentence｜>",1);marker("<｜start▁of▁sentence｜>",0);marker("<|User|>",130973);
    content(user);
    marker("<｜end▁of▁sentence｜>",1);marker("<｜start▁of▁sentence｜>",0);marker("<|Bot|>",130976);marker("</think>",4);
    return result;
}
jintArray token_array(JNIEnv *env,const std::vector<llama_token> &tokens) {
    auto result=env->NewIntArray(tokens.size());
    if(result && !tokens.empty())env->SetIntArrayRegion(result,0,tokens.size(),tokens.data());
    return result;
}
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeLastPromptTokens(JNIEnv *env,jclass,jlong id) {
    try {auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);return token_array(env,s->last_prompt_tokens);}
    catch(const std::exception &e){fail(env,e.what());return nullptr;}
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeTokenizeForTests(JNIEnv *env,jclass,jlong id,jbyteArray text,jboolean special) {
    try {auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);if(!s->model)throw std::runtime_error("Model not loaded");return token_array(env,protocol_tokens(llama_model_get_vocab(s->model),bytes(env,text),special));}
    catch(const std::exception &e){fail(env,e.what());return nullptr;}
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeModelTemplateForTests(JNIEnv *env,jclass,jlong id) {
    try {auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);if(!s->model)throw std::runtime_error("Model not loaded");
        const char *text=llama_model_chat_template(s->model,nullptr);if(!text)throw std::runtime_error("Model template missing");
        auto result=env->NewByteArray(std::strlen(text));if(result)env->SetByteArrayRegion(result,0,std::strlen(text),reinterpret_cast<const jbyte *>(text));return result;
    }catch(const std::exception &e){fail(env,e.what());return nullptr;}
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeGenerate(JNIEnv *env, jclass, jlong id,
    jlong run, jbyteArray model_path, jbyteArray system_text, jbyteArray user_text,
    jint max_tokens,jint template_policy,jint sampler_policy,jint seed,jint threads,jint prompt_threads,jint batch_size,jboolean cache,jint matrix_width,
    jint spec_depth,jboolean adaptive,jint row_tile,jint decode_rows,jboolean persistent_threads,jint affinity_mask,jint prefill_chunk,jint decode_chunk,jint attention_threads,jintArray oracle_tokens,jobject callback) {
    try {
        if (run <= 0 || max_tokens < 1 || max_tokens > 256 || !callback) throw std::runtime_error("Invalid generation parameters");
        if(template_policy<0 || template_policy>2 || sampler_policy<0 || sampler_policy>2 || seed<0
            || (template_policy==0 && sampler_policy==2) || (template_policy>0 && sampler_policy==1))throw std::runtime_error("Invalid generation policy");
        if(template_policy>0 && spec_depth!=0)throw std::runtime_error("Spark speculation is not admitted");
        if (threads<1 || threads>8 || prompt_threads<1 || prompt_threads>8 || batch_size<16 || batch_size>512) throw std::runtime_error("Invalid runtime configuration");
        if(matrix_width!=1 && matrix_width!=2 && matrix_width!=4 && matrix_width!=8) throw std::runtime_error("Invalid matrix kernel width");
        if(spec_depth<0 || spec_depth>7) throw std::runtime_error("Invalid speculation depth");
        auto valid_chunk=[](int c){return c==0||(c>=16&&c<=256&&(c&(c-1))==0);};
        if(!valid_chunk(prefill_chunk)||!valid_chunk(decode_chunk))throw std::runtime_error("Invalid row scheduling chunk");
        if(row_tile!=1 && row_tile!=2) throw std::runtime_error("Invalid prefill row tile");
        if(decode_rows!=1 && decode_rows!=2 && decode_rows!=4) throw std::runtime_error("Invalid decode row tile");
        std::vector<llama_token> oracle;
        if(oracle_tokens) {
            int n=env->GetArrayLength(oracle_tokens);
            if(n>256) throw std::runtime_error("Oracle is limited to test-sized sequences");
            oracle.resize(n); env->GetIntArrayRegion(oracle_tokens,0,n,oracle.data());
        }
        auto session = get(id);
        std::lock_guard<std::mutex> execution(execution_mutex);
        std::lock_guard<std::mutex> lock(session->inference);
        if(attention_threads!=0&&attention_threads!=4)throw std::runtime_error("Invalid logical attention worker count");
        if(attention_threads&&session->test_attention_mode!=0&&session->test_attention_mode!=4)throw std::runtime_error("Conflicting attention policies");
        const int effective_attention_threads=attention_threads?attention_threads:(session->test_attention_mode==4?4:0);
        const int attention_mode=effective_attention_threads?4:session->test_attention_mode;
        if(effective_attention_threads&&(threads!=6||prompt_threads!=6||spec_depth!=0||template_policy!=0))throw std::runtime_error("Fixed attention requires Bonsai 6/6 and ordinary decoding");
        if(affinity_mask<0||affinity_mask>255||(!persistent_threads&&affinity_mask))throw std::runtime_error("Invalid thread-pool affinity policy");
        RestoreAffinity restore_affinity(session.get());
        if(session->persistent_threads!=(bool)persistent_threads||session->affinity_mask!=(uint32_t)affinity_mask){session->clear_context();session->free_pools();session->persistent_threads=persistent_threads;session->affinity_mask=affinity_mask;}
        outpost_q2_set_batch_width(matrix_width);
        outpost_q2_set_row_tiles(row_tile,decode_rows);
        outpost_q2_arm_chunks(prefill_chunk,decode_chunk);
        session->logit_trace.clear();session->sample_events.clear();session->round_events.clear();
        outpost_attention_reset();session->last_attention_stats="{}";
        if(session->capture_spec_stats)session->round_events.reserve(max_tokens);
        if(session->capture_logit_trace){session->logit_trace.reserve(max_tokens+8);session->sample_events.reserve(max_tokens+8);}
        session->last_tokens.clear();
        session->last_prompt_tokens.clear();
        struct Cleanup {
            Session *s; bool keep=false;
            ~Cleanup() {
                if (s->context) llama_set_abort_callback(s->context,nullptr,nullptr);
                s->pause_pools();
                if (!keep) s->clear_context();
            }
        } cleanup{session.get()};
        auto start = Clock::now();
        Abort abort{session, run, start, session->test_generation_deadline_ms};
        long load_ms = 0, first_ms = -1;
        long prepare_ms=0, prefill_ms=0, decode_ms=0, reused=0;
        uint64_t logits_hash=0;
        long drafted=0,accepted=0,verify_passes=0,rejections=0,plain_steps=0,verify_us=0,draft_us=0;
        outpost::spec::Controller controller; controller.adaptive=adaptive;
        int generated = 0, prompt_count = 0, reason = 0;
        std::string path = bytes(env, model_path);
        std::string system = bytes(env, system_text), user = bytes(env, user_text);
        if (!abort_eval(&abort)) {
            if (!session->model || session->path != path) {
                session->clear_context();
                if (session->model) { llama_model_free(session->model); session->model = nullptr; }
                auto params = llama_model_default_params();
                params.n_gpu_layers = 0;
                params.progress_callback = load_progress;
                params.progress_callback_user_data = &abort;
                session->model = llama_model_load_from_file(path.c_str(), params);
                load_ms = elapsed(start);
                if (!session->model && !abort_eval(&abort)) throw std::runtime_error("Cannot load GGUF model");
                if (session->model) session->path = path;
            }
            if (session->model && !abort_eval(&abort)) {
                auto prepare_start=Clock::now();
                const llama_vocab *vocab = llama_model_get_vocab(session->model);
                for(auto token:oracle) if(token<0 || token>=llama_vocab_n_tokens(vocab)) throw std::runtime_error("Invalid oracle token");
                const char *tmpl = llama_model_chat_template(session->model, nullptr);
                if (!tmpl) throw std::runtime_error("Model has no chat template");
                std::vector<llama_token> tokens;
                if(template_policy>0)tokens=spark_prompt(session->model,system,user);
                else {
                    llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
                    int needed = llama_chat_apply_template(tmpl, messages, 2, true, nullptr, 0);
                    if (needed < 1 || needed > 24000) throw std::runtime_error("Unsupported chat template or oversized input");
                    std::vector<char> prompt(needed + 1);
                    int length = llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(), prompt.size());
                    if (length < 1 || length > needed) throw std::runtime_error("Cannot format chat prompt");
                    std::string formatted(prompt.data(), length);
                    // The built-in ChatML formatter omits Bonsai's fixed non-thinking suffix.
                    // Reproduce the suffix present in both pinned official GGUF templates.
                    const std::string template_text(tmpl);
                    if (template_text.find("assistant\\n<think>\\n\\n</think>\\n\\n") != std::string::npos) {
                        formatted += "<think>\n\n</think>\n\n";
                    }
                    int n=-llama_tokenize(vocab,formatted.data(),formatted.size(),nullptr,0,true,true);
                    if(n<1 || n+max_tokens>=2048)throw std::runtime_error("Context limit exceeded; shorten the question or sources");
                    tokens.resize(n);
                    if(llama_tokenize(vocab,formatted.data(),formatted.size(),tokens.data(),tokens.size(),true,true)!=n)throw std::runtime_error("Cannot tokenize input");
                }
                int count=(int)tokens.size();
                if(count<1 || count+max_tokens>=2048)throw std::runtime_error("Context limit exceeded; shorten the question or sources");
                session->last_prompt_tokens=tokens;
                prompt_count = count;
                if (!cache || session->context_template!=template_policy || session->context_batch!=batch_size || session->context_threads!=threads || session->context_prompt_threads!=prompt_threads
                    || session->context_width!=matrix_width || session->context_rows!=row_tile || session->context_decode_rows!=decode_rows || session->context_prefill_chunk!=prefill_chunk || session->context_decode_chunk!=decode_chunk || session->context_attention_threads!=effective_attention_threads || session->context_kernel!=outpost_q2_name()) session->clear_context();
                session->prepare_pools(threads,prompt_threads);
                if (!session->context) {
                    auto params = llama_context_default_params();
                    params.n_ctx = 2048; params.n_batch = batch_size; params.n_ubatch = batch_size;
                    params.n_threads = threads; params.n_threads_batch = prompt_threads;
                    params.no_perf = true;
                    // Spark research policy1 uses compact SWA; policy2 is its full-cache reference.
                    params.swa_full = template_policy!=1;
                    session->context=llama_init_from_model(session->model,params);
                    session->context_batch=batch_size;
                    session->context_template=template_policy;
                    session->context_threads=threads; session->context_prompt_threads=prompt_threads; session->context_width=matrix_width;
                    session->context_rows=row_tile;
                    session->context_decode_rows=decode_rows;
                    session->context_prefill_chunk=prefill_chunk;session->context_decode_chunk=decode_chunk;session->context_attention_threads=effective_attention_threads;
                    session->context_kernel=outpost_q2_name();
                }
                auto *context=session->context;
                if (!context) throw std::runtime_error("Cannot allocate model context");
                llama_set_n_threads(context,threads,prompt_threads);
                if(session->persistent_threads){llama_attach_threadpool(context,session->pool,session->pool_batch);session->pools_paused=false;}
                llama_set_abort_callback(context,abort_eval,&abort);
                while (cache && reused<count && reused<(long)session->prefix.size() && tokens[reused]==session->prefix[reused]) reused++;
                bool complete_hit=reused==count && session->prefix.size()==tokens.size() && session->prefix_logits.size()==(size_t)llama_vocab_n_tokens(vocab);
                // Keep the same prefill batch boundaries as a cold evaluation. Partial-batch reuse can change rounding.
                if(!complete_hit) reused=(std::min<long>(reused,count-1)/batch_size)*batch_size;
                if(template_policy==1 && reused>0) {
                    // Successful suffix removal does not restore already evicted SWA history.
                    // The pinned Spark profile has a 512-token window. Require the complete
                    // preceding window to remain resident before reusing any cached prefix.
                    auto memory=llama_get_memory(context);
                    llama_pos first=llama_memory_seq_pos_min(memory,0),last=llama_memory_seq_pos_max(memory,0);
                    if(first<0 || first>std::max<long>(0,reused-512) || last<reused-1) {
                        llama_memory_clear(memory,false);reused=0;complete_hit=false;
                    }
                }
                if (!llama_memory_seq_rm(llama_get_memory(context),0,(llama_pos)reused,-1)) {
                    llama_memory_clear(llama_get_memory(context),false); reused=0; complete_hit=false;
                }
                session->prefix.clear();
                prepare_ms=elapsed(prepare_start);
                auto prefill_start=Clock::now();
                for (int offset=(int)reused; offset<count && !abort_eval(&abort); offset+=batch_size) {
                    int n = std::min((int)batch_size,count-offset);
                    std::vector<llama_pos> positions(n);
                    for(int j=0;j<n;j++) positions[j]=offset+j;
                    auto batch=llama_batch_get_one(tokens.data()+offset,n); batch.pos=positions.data();
                    int prefill_rc;
                    if(attention_mode==4){OutpostAttentionScope attention(4,offset,n);prefill_rc=llama_decode(context,batch);}
                    else prefill_rc=llama_decode(context,batch);
                    if (prefill_rc != 0) {
                        if (abort_eval(&abort)) break;
                        throw std::runtime_error("Prompt evaluation failed");
                    }
                }
                prefill_ms=elapsed(prefill_start);
                if (!abort_eval(&abort) && cache) {
                    session->prefix=tokens;
                    if(!complete_hit) {
                        const float *logits=llama_get_logits_ith(context,-1);
                        session->prefix_logits.assign(logits,logits+llama_vocab_n_tokens(vocab));
                    }
                }
                auto decode_start=Clock::now();
                llama_sampler *sampler_raw = nullptr;
                if (sampler_policy!=0) {
                    sampler_raw = llama_sampler_chain_init(llama_sampler_chain_default_params());
                    if(sampler_policy==1)llama_sampler_chain_add(sampler_raw,llama_sampler_init_top_k(20));
                    llama_sampler_chain_add(sampler_raw,llama_sampler_init_top_p(sampler_policy==1?0.8f:0.95f,1));
                    llama_sampler_chain_add(sampler_raw,llama_sampler_init_temp(sampler_policy==1?0.7f:1.0f));
                    llama_sampler_chain_add(sampler_raw,llama_sampler_init_dist((uint32_t)seed));
                } else sampler_raw = llama_sampler_init_greedy();
                std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(sampler_raw, llama_sampler_free);
                jclass cls = env->GetObjectClass(callback);
                jmethodID update = env->GetMethodID(cls, "onBytes", "([BI)V");
                env->DeleteLocalRef(cls);
                if (!update) return nullptr;
                std::string output;
                std::vector<llama_token> history=tokens;
                auto commit=[&](llama_token token) {
                    std::vector<char> piece(256);
                    int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
                    if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
                    if (n < 0) throw std::runtime_error("Cannot decode model token");
                    output.append(piece.data(), n); generated++;
                    history.push_back(token); session->last_tokens.push_back(token);
                    if (first_ms < 0) first_ms = elapsed(start);
                    return emit(env,callback,update,output,generated);
                };
                llama_token pending=LLAMA_TOKEN_NULL;
                if(!abort_eval(&abort)) {
                    const auto *raw=reinterpret_cast<const unsigned char *>(complete_hit ? session->prefix_logits.data() : llama_get_logits_ith(context,-1));
                    logits_hash=14695981039346656037ULL;
                    for(size_t j=0;j<(size_t)llama_vocab_n_tokens(vocab)*sizeof(float);j++) { logits_hash^=raw[j]; logits_hash*=1099511628211ULL; }
                    pending=complete_hit ? sample_saved_logits(sampler.get(),session->prefix_logits) : llama_sampler_sample(sampler.get(),context,-1);
                    if(session->capture_logit_trace){session->logit_trace.push_back(logits_hash);session->sample_events.push_back({logits_hash,0,pending,-1,"initial"});}
                }
                while(generated<max_tokens && !abort_eval(&abort)) {
                    if(llama_vocab_is_eog(vocab,pending)) break;
                    RoundScope round(session->capture_spec_stats,session->round_events,generated);
                    auto emit_start=Clock::now();bool emitted=commit(pending);round.value.emit_us+=micros(emit_start);
                    if(!emitted) return nullptr;
                    if(generated==max_tokens) { reason=1;round.finish(true);break; }
                    if(abort_eval(&abort)) break;
                    auto target_sample=[&](int row,int output_index,const char *kind) {
                        uint64_t hash=0;auto trace_start=Clock::now();
                        if(session->capture_logit_trace)hash=hash_logits(llama_get_logits_ith(context,row),llama_vocab_n_tokens(vocab));
                        if(session->capture_logit_trace)round.value.trace_us+=micros(trace_start);
                        auto sample_start=Clock::now();llama_token token=llama_sampler_sample(sampler.get(),context,row);
                        round.value.sample_us+=micros(sample_start);round.value.samples++;
                        if(session->capture_logit_trace){auto record_start=Clock::now();session->logit_trace.push_back(hash);session->sample_events.push_back({hash,output_index,token,row,kind});round.value.trace_us+=micros(record_start);}
                        return token;
                    };
                    auto proposed_start=Clock::now();
                    std::vector<llama_token> draft;
                    int depth=std::min((int)spec_depth,(int)max_tokens-generated-1);
                    if(depth>0 && controller.allowed() && (!adaptive || generated>=8)) {
                        if(!oracle.empty()) {
                            for(int i=generated;i<(int)oracle.size() && (int)draft.size()<depth;i++) draft.push_back(oracle[i]);
                        } else draft=outpost::spec::lookup(history,depth,adaptive ? 8:4);
                        auto special=std::find_if(draft.begin(),draft.end(),[&](llama_token t) { return llama_vocab_is_eog(vocab,t) || llama_vocab_is_control(vocab,t); });
                        draft.erase(special,draft.end());
                    }
                    round.value.proposal_us=micros(proposed_start);draft_us+=round.value.proposal_us;round.value.proposed=(int)draft.size();
                    llama_pos position=count+generated-1;
                    if(draft.empty()) {
                        round.value.kind="serial";auto normal_start=Clock::now();
                        auto batch=llama_batch_get_one(&pending,1); batch.pos=&position;
                        int decode_rc;
                        if(attention_mode==4){OutpostAttentionScope attention(4,position,1);decode_rc=llama_decode(context,batch);}
                        else decode_rc=llama_decode(context,batch);
                        if(decode_rc!=0) {
                            if(abort_eval(&abort)) break;
                            throw std::runtime_error("Token generation failed");
                        }
                        round.value.evaluate_us=micros(normal_start);plain_steps++;
                        if(!abort_eval(&abort))pending=target_sample(-1,generated,"plain");
                        round.finish(!abort_eval(&abort));if(round.value.complete)controller.observe_plain(round.controller_us());
                        continue;
                    }
                    round.value.kind="verify";drafted+=draft.size(); verify_passes++;
                    std::vector<llama_token> input{pending}; input.insert(input.end(),draft.begin(),draft.end());
                    std::vector<llama_pos> positions(input.size()); std::vector<int8_t> outputs(input.size(),1);
                    for(size_t i=0;i<input.size();i++) positions[i]=position+(llama_pos)i;
                    auto batch=llama_batch_get_one(input.data(),input.size()); batch.pos=positions.data(); batch.logits=outputs.data();
                    auto verification_start=Clock::now();
                    int verify_rc;
                    if(attention_mode==2){
                        struct RestoreThreads {llama_context *ctx;int decode,prompt;~RestoreThreads(){llama_set_n_threads(ctx,decode,prompt);}} restore{context,threads,prompt_threads};
                        llama_set_n_threads(context,threads,threads);
                        OutpostAttentionScope attention(2,position,(int)input.size());
                        verify_rc=llama_decode(context,batch);
                    }else if(attention_mode==3){
                        OutpostAttentionScope attention(3,position,(int)input.size());
                        verify_rc=llama_decode(context,batch);
                    }else verify_rc=llama_decode(context,batch);
                    if(verify_rc!=0) {
                        if(abort_eval(&abort)) break;
                        throw std::runtime_error("Speculative verification failed");
                    }
                    round.value.evaluate_us=micros(verification_start);
                    if(abort_eval(&abort)) break;
                    const int next_index=generated;
                    auto result=outpost::spec::verify(draft,[&](int row) { return target_sample(row,next_index+row,"verify"); });
                    auto rollback_start=Clock::now();
                    if(!llama_memory_seq_rm(llama_get_memory(context),0,position+1+result.accepted,-1)) throw std::runtime_error("Speculation requires partial KV removal");
                    round.value.rollback_us=micros(rollback_start);verify_us+=micros(verification_start);
                    accepted+=result.accepted;round.value.accepted=result.accepted;if(result.accepted<(int)draft.size())rejections++;
                    for(int i=0;i<result.accepted && !abort_eval(&abort);i++){auto emit_start=Clock::now();bool ok=commit(draft[i]);round.value.emit_us+=micros(emit_start);if(!ok)return nullptr;}
                    pending=result.next;
                    round.finish(!abort_eval(&abort));if(round.value.complete)controller.observe_window(round.controller_us(),round.value.committed-1);
                }
                decode_ms=elapsed(decode_start);
            }
        }
        if (session->cancelled(run)) reason = 2;
        else if (elapsed(start) > abort.deadline_ms) reason = 3;
        cleanup.keep=cache && reason<2;
        session->last_attention_stats=outpost_attention_stats();
        jlong data[] = {prompt_count,generated,load_ms,first_ms,elapsed(start),reason,prepare_ms,prefill_ms,decode_ms,reused,(jlong)logits_hash,
            drafted,accepted,verify_passes,rejections,plain_steps,verify_us,draft_us,(jlong)controller.disabled};
        jlongArray result = env->NewLongArray(19);
        if (result) env->SetLongArrayRegion(result,0,19,data);
        return result;
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}

// Kev token layout and bilinear pointer readout adapted from dohnuts.cpp
// src/side/kev.cpp at 63374ff55a66c50b266adfef422e1fc4b0ee5717 (Apache-2.0).
// Only one question is evaluated per call, so its sequence is fully causal.
namespace {
std::vector<llama_token> kev_tokens(const llama_vocab *vocab, const std::string &text, bool special) {
    int count = -llama_tokenize(vocab, text.data(), text.size(), nullptr, 0, false, special);
    if (count == 0) return {};
    if (count < 0 || count > 2048) throw std::runtime_error("Decision input exceeds token budget");
    std::vector<llama_token> ids(count);
    if (llama_tokenize(vocab, text.data(), text.size(), ids.data(), count, false, special) != count)
        throw std::runtime_error("Decision tokenization failed");
    return ids;
}
llama_token kev_marker(const llama_vocab *vocab, const char *text) {
    auto ids = kev_tokens(vocab, text, true);
    if (ids.size() != 1) throw std::runtime_error("Missing Kev delimiter token");
    return ids[0];
}
void kev_append(std::vector<llama_token> &ids, const llama_vocab *vocab, const std::string &text) {
    // Special tokens from untrusted evidence must never become control markers.
    auto more = kev_tokens(vocab, text, false);
    ids.insert(ids.end(), more.begin(), more.end());
}
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeJudge(JNIEnv *env, jclass, jlong id, jlong run,
    jbyteArray model_path, jbyteArray head_path, jbyteArray state_text, jbyteArray instruction,
    jobjectArray options) {
    try {
        const int count = options ? env->GetArrayLength(options) : 0;
        if (count < 2 || count > 8 || run <= 0) throw std::runtime_error("Invalid decision request");
        auto session = get(id);
        std::lock_guard<std::mutex> execution(execution_mutex);
        std::lock_guard<std::mutex> lock(session->inference);
        session->clear_context();
        auto start = Clock::now();
        Abort abort{session, run, start};
        std::vector<double> output(count + 4, 0.0);
        long load_ms = 0;
        if (!abort_eval(&abort)) {
            std::string path = bytes(env, model_path);
            if (!session->model || session->path != path) {
                if (session->model) { llama_model_free(session->model); session->model = nullptr; }
                auto params = llama_model_default_params();
                params.n_gpu_layers = 0; params.progress_callback = load_progress; params.progress_callback_user_data = &abort;
                session->model = llama_model_load_from_file(path.c_str(), params);
                load_ms = elapsed(start);
                if (!session->model && !abort_eval(&abort)) throw std::runtime_error("Cannot load Kev model");
                if (session->model) session->path = path;
            }
            if (session->model && !abort_eval(&abort)) {
                const llama_vocab *vocab = llama_model_get_vocab(session->model);
                const int hidden = llama_model_n_embd(session->model), dim = 256;
                if (hidden != 1024) throw std::runtime_error("Unexpected Kev hidden dimension");
                const size_t half = static_cast<size_t>(dim) * (hidden + 1);
                std::ifstream file(bytes(env, head_path), std::ios::binary | std::ios::ate);
                if (!file || file.tellg() != static_cast<std::streamoff>(half * 2 * sizeof(float))) throw std::runtime_error("Invalid Kev head size");
                std::vector<float> head(half * 2);
                file.seekg(0); file.read(reinterpret_cast<char *>(head.data()), half * 2 * sizeof(float));
                if (!file) throw std::runtime_error("Truncated Kev head");
                for (float value : head) if (!std::isfinite(value)) throw std::runtime_error("Invalid Kev head value");
                std::vector<llama_token> ids{kev_marker(vocab, "<|fim_prefix|>")};
                kev_append(ids, vocab, bytes(env, state_text));
                if (ids.size() > 1024) throw std::runtime_error("Evidence too long for decision");
                ids.push_back(kev_marker(vocab, "<|fim_middle|>"));
                kev_append(ids, vocab, bytes(env, instruction));
                std::vector<int> positions;
                for (int i = 0; i < count; i++) {
                    ids.push_back(kev_marker(vocab, "<|box_start|>"));
                    auto option = static_cast<jbyteArray>(env->GetObjectArrayElement(options, i));
                    std::string option_text = bytes(env, option); env->DeleteLocalRef(option);
                    kev_append(ids, vocab, option_text);
                    ids.push_back(kev_marker(vocab, "<|box_end|>")); positions.push_back(ids.size() - 1);
                }
                ids.push_back(kev_marker(vocab, "<|fim_suffix|>"));
                if (ids.size() > 1536) throw std::runtime_error("Decision context exceeds 1536 tokens");
                auto params = llama_context_default_params();
                params.n_ctx = 2048; params.n_batch = 1536; params.n_ubatch = 128;
                params.n_threads = 4; params.n_threads_batch = 4; params.n_seq_max = 1;
                params.embeddings = true; params.pooling_type = LLAMA_POOLING_TYPE_NONE;
                params.abort_callback = abort_eval; params.abort_callback_data = &abort; params.no_perf = true;
                std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(session->model, params), llama_free);
                if (!ctx) throw std::runtime_error("Cannot create Kev context");
                llama_batch batch = llama_batch_init(ids.size(), 0, 1);
                batch.n_tokens = ids.size();
                for (int i = 0; i < batch.n_tokens; i++) {
                    batch.token[i] = ids[i]; batch.pos[i] = i; batch.n_seq_id[i] = 1; batch.seq_id[i][0] = 0; batch.logits[i] = 0;
                }
                int rc = llama_decode(ctx.get(), batch); llama_batch_free(batch);
                if (rc && !abort_eval(&abort)) throw std::runtime_error("Kev forward pass failed");
                if (!abort_eval(&abort)) {
                    auto project = [&](const float *weights, const float *embedding) {
                        if (!embedding) throw std::runtime_error("Kev embeddings unavailable");
                        std::vector<double> values(dim);
                        for (int d = 0; d < dim; d++) {
                            const float *row = weights + static_cast<size_t>(d) * (hidden + 1);
                            double sum = row[hidden];
                            for (int i = 0; i < hidden; i++) sum += static_cast<double>(row[i]) * embedding[i];
                            if (!std::isfinite(sum)) throw std::runtime_error("Nonfinite Kev projection");
                            values[d] = sum;
                        }
                        return values;
                    };
                    auto query = project(head.data(), llama_get_embeddings_ith(ctx.get(), ids.size()-1));
                    double maximum = -INFINITY;
                    for (int j = 0; j < count; j++) {
                        auto key = project(head.data() + half, llama_get_embeddings_ith(ctx.get(), positions[j]));
                        double dot = 0; for (int d = 0; d < dim; d++) dot += query[d] * key[d];
                        // Publisher's fitted temperature, not calibrated for our Spanish corpus.
                        output[j] = dot / std::sqrt(static_cast<double>(dim)) / 2.406050072164233;
                        maximum = std::max(maximum, output[j]);
                    }
                    double total = 0; for (int j = 0; j < count; j++) total += (output[j] = std::exp(output[j]-maximum));
                    for (int j = 0; j < count; j++) output[j] /= total;
                    output[count] = ids.size();
                }
            }
        }
        output[count+1] = load_ms; output[count+2] = elapsed(start);
        output[count+3] = session->cancelled(run) ? 2 : elapsed(start) > 120000 ? 3 : 0;
        jdoubleArray result = env->NewDoubleArray(output.size());
        if (result) env->SetDoubleArrayRegion(result, 0, output.size(), output.data());
        return result;
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeKernelChecks(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);
    outpost_q2_report r=outpost_q2_test();
    double values[]={ (double)r.supported,(double)r.cases,(double)r.bit_mismatches,(double)r.dispatch_ok,(double)r.guard_ok,r.max_abs,r.max_rel,r.scalar_ns,r.fast_ns };
    jdoubleArray result=env->NewDoubleArray(9); if(result) env->SetDoubleArrayRegion(result,0,9,values); return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeDeadlineForTests(JNIEnv *env,jclass,jlong id,jint millis) {
    try {
        if(millis<120000||millis>300000)throw std::runtime_error("Research deadline outside120–300 seconds");
        auto session=get(id);std::lock_guard<std::mutex> execution(execution_mutex);std::lock_guard<std::mutex> lock(session->inference);
        session->test_generation_deadline_ms=millis;
    }catch(const std::exception &error){fail(env,error.what());}
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeThreadpoolAudit(JNIEnv *env,jclass,jlong id) {
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);char text[512];
        snprintf(text,sizeof(text),"{\"enabled\":%s,\"poolCreations\":%d,\"threads\":%d,\"promptThreads\":%d,\"attentionThreads\":%d,\"requestedMask\":%u,\"effectiveMask\":%u,\"affinityBefore\":%u,\"affinityDuring\":%u,\"affinityAfter\":%u,\"affinityRestored\":%s,\"pausedAfterRequest\":%s}",s->persistent_threads?"true":"false",s->pool_creations,s->pool_threads,s->pool_prompt_threads,s->context?s->context_attention_threads:0,s->affinity_mask,s->effective_affinity_mask,s->affinity_before,s->affinity_during,s->affinity_after,s->affinity_restored?"true":"false",s->pools_paused?"true":"false");return env->NewStringUTF(text);
    }catch(const std::exception &error){fail(env,error.what());return nullptr;}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeArmScheduleChecks(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);char report[4096];outpost_q2_arm_schedule_checks(report,sizeof(report));return env->NewStringUTF(report);
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_armScheduleAudit(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);char report[1024];outpost_q2_arm_schedule_audit(report,sizeof(report));return env->NewStringUTF(report);
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeArmGraphChecks(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);
    std::vector<char> report(131072);outpost_q2_arm_graph_checks(report.data(),report.size());return env->NewStringUTF(report.data());
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_kernelName(JNIEnv *env,jclass) { return env->NewStringUTF(outpost_q2_name()); }
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_outpost_app_NativeEngine_kernelWasUsed(JNIEnv *,jclass) { return outpost_q2_was_used()!=0; }
extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_setKernelAutomatic(JNIEnv *,jclass,jboolean automatic) {
    std::lock_guard<std::mutex> execution(execution_mutex); outpost_q2_set_mode(automatic);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_kernelProfile(JNIEnv *env,jclass) {
    char report[3072]; outpost_q2_profile(report,sizeof(report)); return env->NewStringUTF(report);
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeDispatchChecks(JNIEnv *env,jclass) {
    int failed=0; int count=br_q2_policy_checks(&failed);
    jint values[]={count,failed}; jintArray result=env->NewIntArray(2);
    if(result) env->SetIntArrayRegion(result,0,2,values); return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_setKernelBatchWidth(JNIEnv *,jclass,jint width) {
    std::lock_guard<std::mutex> execution(execution_mutex); outpost_q2_set_batch_width(width);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_outpost_app_NativeEngine_kernelBatchUsed(JNIEnv *,jclass) { return outpost_q2_batch_used()!=0; }
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeBatchChecks(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);
    auto r=outpost_q2_batch_test();
    double v[]={(double)r.supported,(double)r.cases,(double)r.bit_mismatches,(double)r.dispatch_ok,r.max_abs};
    auto out=env->NewDoubleArray(5); if(out) env->SetDoubleArrayRegion(out,0,5,v); return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeRowsBenchmark(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);std::vector<char> report(65536);
    outpost_q2_rows_benchmark(report.data(),report.size());return env->NewStringUTF(report.data());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_outpost_app_NativeEngine_kernelRowsUsed(JNIEnv *,jclass){return outpost_q2_rows_used()!=0;}
extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_beginRowsProfile(JNIEnv *,jclass){std::lock_guard<std::mutex> execution(execution_mutex);outpost_q2_rows_profile_begin();}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_endRowsProfile(JNIEnv *env,jclass){std::lock_guard<std::mutex> execution(execution_mutex);char report[24576];outpost_q2_rows_profile_end(report,sizeof(report));return env->NewStringUTF(report);}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeRowsAudit(JNIEnv *env,jclass,jlong id,jintArray continuation,jint rows) {
    try {
        int count=continuation?env->GetArrayLength(continuation):0;
        if(count<1||count>32||(rows!=2&&rows!=4))throw std::runtime_error("Invalid row audit dimensions");
        auto s=get(id);std::lock_guard<std::mutex> execution(execution_mutex);std::lock_guard<std::mutex> lock(s->inference);
        if(!s->context||s->prefix.empty())throw std::runtime_error("Row audit requires a cached prompt");
        struct Restore {Session *session;int rows,decode;bool complete=false;~Restore(){outpost_q2_set_row_tiles(rows,decode);if(!complete)session->clear_context();}} restore{s.get(),s->context_rows,s->context_decode_rows};
        auto *ctx=s->context;const int prefix=s->prefix.size(),vocab=llama_vocab_n_tokens(llama_model_get_vocab(s->model));
        if(prefix>(int)s->prefix.size()||prefix+count>=2048)throw std::runtime_error("Row audit exceeds context");
        std::vector<llama_token> input(count);env->GetIntArrayRegion(continuation,0,count,input.data());for(auto t:input)if(t<0||t>=vocab)throw std::runtime_error("Invalid row audit token");
        auto reset=[&]{if(!llama_memory_seq_rm(llama_get_memory(ctx),0,prefix,-1))throw std::runtime_error("Cannot reset row audit KV");};
        auto decode=[&](int i){llama_pos pos=prefix+i;auto b=llama_batch_get_one(&input[i],1);b.pos=&pos;if(llama_decode(ctx,b)!=0)throw std::runtime_error("Row audit decode failed");return llama_get_logits_ith(ctx,-1);};
        reset();outpost_q2_set_row_tile(1);std::vector<std::vector<float>> reference(count);
        for(int i=0;i<count;i++){const float *p=decode(i);reference[i].assign(p,p+vocab);}
        reset();outpost_q2_set_row_tile(rows);double maximum=0,identical=0;
        for(int i=0;i<count;i++){const float *p=decode(i);if(!std::memcmp(reference[i].data(),p,vocab*sizeof(float)))identical++;for(int j=0;j<vocab;j++)maximum=std::max(maximum,std::abs((double)reference[i][j]-p[j]));}
        bool applied=outpost_q2_rows_used();reset();restore.complete=true;
        double values[]={(double)count,maximum,identical,applied?1.0:0.0};auto out=env->NewDoubleArray(4);if(out)env->SetDoubleArrayRegion(out,0,4,values);return out;
    }catch(const std::exception &e){fail(env,e.what());return nullptr;}
}

extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeLastTokens(JNIEnv *env,jclass,jlong id) {
    try {
        auto s=get(id); std::lock_guard<std::mutex> lock(s->inference);
        auto out=env->NewIntArray(s->last_tokens.size());
        if(out && !s->last_tokens.empty()) env->SetIntArrayRegion(out,0,s->last_tokens.size(),s->last_tokens.data()); return out;
    } catch(const std::exception &e) { fail(env,e.what()); return nullptr; }
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeModelCapabilities(JNIEnv *env,jclass,jlong id) {
    try {
        auto s=get(id); std::lock_guard<std::mutex> lock(s->inference);
        if(!s->model) throw std::runtime_error("Load a model before capability inspection");
        jint v[]={llama_model_n_layer(s->model),llama_model_n_layer_nextn(s->model),llama_vocab_n_tokens(llama_model_get_vocab(s->model))};
        auto out=env->NewIntArray(3); if(out) env->SetIntArrayRegion(out,0,3,v); return out;
    } catch(const std::exception &e) { fail(env,e.what()); return nullptr; }
}
extern "C" JNIEXPORT jintArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeSpeculationChecks(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> execution(execution_mutex);
    int failed=0; int checks=outpost::spec::checks(&failed);
    // Same RNG stream and conditional toy distribution, including early draft mismatches.
    for(int chained=0;chained<2;chained++) for(int seed=1;seed<=256;seed++) {
        auto make=[&] {
            if(!chained) return llama_sampler_init_dist(seed);
            auto *chain=llama_sampler_chain_init(llama_sampler_chain_default_params());
            llama_sampler_chain_add(chain,llama_sampler_init_top_k(20));
            llama_sampler_chain_add(chain,llama_sampler_init_top_p(0.8f,1));
            llama_sampler_chain_add(chain,llama_sampler_init_temp(0.7f));
            llama_sampler_chain_add(chain,llama_sampler_init_dist(seed)); return chain;
        };
        std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> serial(make(),llama_sampler_free);
        std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> speculative(make(),llama_sampler_free);
        auto draw=[&](llama_sampler *sampler,llama_token previous) {
            return sample_saved_logits(sampler,{0.3f*(previous%4),-0.7f,0.4f,0.8f-0.1f*(previous%4)});
        };
        std::vector<llama_token> reference,actual; llama_token previous=0;
        for(int i=0;i<128;i++) { previous=draw(serial.get(),previous); reference.push_back(previous); }
        previous=0;
        while(actual.size()<128) {
            int k=std::min<int>(seed%8,127-actual.size());
            std::vector<llama_token> draft(k);
            for(int i=0;i<k;i++) draft[i]=(seed+i)%4;
            auto verified=outpost::spec::verify(draft,[&](int row) { return draw(speculative.get(),row ? draft[row-1] : previous); });
            actual.insert(actual.end(),draft.begin(),draft.begin()+verified.accepted); actual.push_back(verified.next); previous=verified.next;
        }
        checks++; if(actual!=reference) failed++;
    }
    jint values[]={checks,failed}; auto out=env->NewIntArray(2); if(out) env->SetIntArrayRegion(out,0,2,values); return out;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeVerificationAudit(JNIEnv *env,jclass,jlong id,jintArray continuation,jint width) {
    try {
        int count=continuation ? env->GetArrayLength(continuation) : 0;
        if(count<1 || count>64 || width<2 || width>8) throw std::runtime_error("Invalid audit dimensions");
        auto s=get(id); std::lock_guard<std::mutex> execution(execution_mutex); std::lock_guard<std::mutex> lock(s->inference);
        if(!s->context || s->prefix.empty()) throw std::runtime_error("Audit requires a cached prompt");
        auto *ctx=s->context; const int prefix=s->prefix.size(),vocab=llama_vocab_n_tokens(llama_model_get_vocab(s->model));
        if(prefix>(int)s->prefix.size()||prefix+count>=2048) throw std::runtime_error("Audit exceeds context");
        std::vector<llama_token> input(count); env->GetIntArrayRegion(continuation,0,count,input.data());
        for(auto t:input) if(t<0 || t>=vocab) throw std::runtime_error("Invalid audit token");
        auto reset=[&] { if(!llama_memory_seq_rm(llama_get_memory(ctx),0,prefix,-1)) throw std::runtime_error("Cannot reset audit KV"); };
        reset();
        std::vector<std::vector<float>> reference(count);
        for(int i=0;i<count;i++) {
            llama_pos pos=prefix+i; auto batch=llama_batch_get_one(&input[i],1); batch.pos=&pos;
            if(llama_decode(ctx,batch)!=0) throw std::runtime_error("Serial audit failed");
            const float *p=llama_get_logits_ith(ctx,-1); reference[i].assign(p,p+vocab);
        }
        reset();
        double max_abs=0,mean_abs=0,mean_kl=0,top1_same=0,bit_same=0;
        int first_bit_difference=-1,first_top1_difference=-1;
        for(int offset=0;offset<count;offset+=width) {
            int n=std::min((int)width,count-offset); std::vector<llama_pos> positions(n); std::vector<int8_t> flags(n,1);
            for(int i=0;i<n;i++) positions[i]=prefix+offset+i;
            auto batch=llama_batch_get_one(input.data()+offset,n); batch.pos=positions.data(); batch.logits=flags.data();
            if(llama_decode(ctx,batch)!=0) throw std::runtime_error("Batched audit failed");
            for(int i=0;i<n;i++) {
                const auto &a=reference[offset+i]; const float *b=llama_get_logits_ith(ctx,i);
                int ia=0,ib=0; double sa=0,sb=0;
                for(int j=0;j<vocab;j++) { if(a[j]>a[ia]) ia=j; if(b[j]>b[ib]) ib=j; }
                for(int j=0;j<vocab;j++) { sa+=std::exp((double)a[j]-a[ia]); sb+=std::exp((double)b[j]-b[ib]); }
                double za=std::log(sa)+a[ia],zb=std::log(sb)+b[ib];
                for(int j=0;j<vocab;j++) {
                    double error=std::abs((double)a[j]-b[j]); max_abs=std::max(max_abs,error); mean_abs+=error;
                    mean_kl+=std::exp(a[j]-za)*((a[j]-za)-(b[j]-zb));
                }
                bool identical=std::memcmp(a.data(),b,vocab*sizeof(float))==0;
                top1_same+=ia==ib; bit_same+=identical;
                if(!identical && first_bit_difference<0) first_bit_difference=offset+i;
                if(ia!=ib && first_top1_difference<0) first_top1_difference=offset+i;
            }
        }
        reset();
        double values[]={(double)count,max_abs,mean_abs/(count*vocab),mean_kl/count,top1_same,bit_same,(double)first_bit_difference,(double)first_top1_difference};
        auto out=env->NewDoubleArray(8); if(out) env->SetDoubleArrayRegion(out,0,8,values); return out;
    } catch(const std::exception &e) { fail(env,e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeTraceLogitsForTests(JNIEnv *env,jclass,jlong id,jboolean enabled){
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);s->capture_logit_trace=enabled;s->logit_trace.clear();s->sample_events.clear();}catch(const std::exception &e){fail(env,e.what());}
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_outpost_app_NativeEngine_nativeLogitTrace(JNIEnv *env,jclass,jlong id){
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);std::vector<jlong> values;for(auto h:s->logit_trace)values.push_back((jlong)h);auto result=env->NewLongArray(values.size());if(result&&!values.empty())env->SetLongArrayRegion(result,0,values.size(),values.data());return result;}catch(const std::exception &e){fail(env,e.what());return nullptr;}
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeSpecStatsForTests(JNIEnv *env,jclass,jlong id,jboolean enabled){
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);s->capture_spec_stats=enabled;s->round_events.clear();}catch(const std::exception&e){fail(env,e.what());}
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeSpecDiagnostics(JNIEnv *env,jclass,jlong id){
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);std::ostringstream out;out.imbue(std::locale::classic());
        out<<"{\"schemaVersion\":1,\"sampleTrace\":[";
        for(size_t i=0;i<s->sample_events.size();i++){auto &e=s->sample_events[i];bool emitted=e.index>=0&&(size_t)e.index<s->last_tokens.size()&&s->last_tokens[e.index]==e.token;
            if(i)out<<",";out<<"{\"outputIndex\":"<<e.index<<",\"tokenId\":"<<e.token<<",\"logitsHash\":\""<<e.hash<<"\",\"row\":"<<e.row<<",\"kind\":\""<<e.kind<<"\",\"emitted\":"<<(emitted?"true":"false")<<"}";}
        out<<"],\"rounds\":[";
        for(size_t i=0;i<s->round_events.size();i++){auto &e=s->round_events[i];if(i)out<<",";
            out<<"{\"kind\":\""<<e.kind<<"\",\"firstOutputIndex\":"<<e.first<<",\"committed\":"<<e.committed<<",\"proposed\":"<<e.proposed<<",\"accepted\":"<<e.accepted<<",\"samples\":"<<e.samples
               <<",\"totalUs\":"<<e.total_us<<",\"proposalUs\":"<<e.proposal_us<<",\"evaluateUs\":"<<e.evaluate_us<<",\"sampleUs\":"<<e.sample_us<<",\"rollbackUs\":"<<e.rollback_us<<",\"emitUs\":"<<e.emit_us<<",\"traceUs\":"<<e.trace_us
               <<",\"controllerUs\":"<<std::max(0L,e.total_us-e.trace_us)<<",\"complete\":"<<(e.complete?"true":"false")<<"}";}
        out<<"],\"scope\":\"Round timing includes anchor emission, proposal, target evaluation/sampling, rollback and accepted-token emission. Controller excludes measured hash/trace overhead; remaining instrumentation overhead is not corrected. Terminal or cancelled rounds do not train it. Sample events include un-emitted pending/EOS/cancelled tokens.\"}";
        return env->NewStringUTF(out.str().c_str());
    }catch(const std::exception&e){fail(env,e.what());return nullptr;}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeCausalCurve(JNIEnv *env,jclass,jlong id,jlong run,jintArray continuation,jint verify_threads,jint attention_mode,jint prefix_limit) {
    try {
        const int count=continuation?env->GetArrayLength(continuation):0;
        if(run<=0||count!=16||(verify_threads!=4&&verify_threads!=6)||attention_mode<0||attention_mode>3||(attention_mode==2&&verify_threads!=4)||(attention_mode==3&&verify_threads!=6)||prefix_limit<0)throw std::runtime_error("Invalid bounded curve request");
        auto s=get(id);std::lock_guard<std::mutex> execution(execution_mutex);std::lock_guard<std::mutex> lock(s->inference);
        if(!s->context||s->prefix.empty()||s->context_template!=0||s->context_threads!=4||s->context_attention_threads!=0||llama_model_n_layer(s->model)!=36)throw std::runtime_error("Curve requires cached Bonsai4 prefill with the original four-worker decoder");
        auto *ctx=s->context;const int prefix=prefix_limit?prefix_limit:(int)s->prefix.size(),vocab=llama_vocab_n_tokens(llama_model_get_vocab(s->model));
        if(prefix>(int)s->prefix.size()||prefix+count>=2048)throw std::runtime_error("Curve exceeds context");
        std::vector<llama_token> tokens(count);env->GetIntArrayRegion(continuation,0,count,tokens.data());
        for(auto t:tokens)if(t<0||t>=vocab)throw std::runtime_error("Invalid curve token");
        Abort abort{s,run,Clock::now(),120000};bool keep=false;
        struct Cleanup {Session *s;bool &keep;~Cleanup(){if(s->context){llama_set_abort_callback(s->context,nullptr,nullptr);llama_set_n_threads(s->context,s->context_threads,s->context_prompt_threads);}s->pause_pools();if(!keep)s->clear_context();}} cleanup{s.get(),keep};
        llama_set_abort_callback(ctx,abort_eval,&abort);llama_set_n_threads(ctx,s->context_threads,verify_threads);
        if(s->persistent_threads){llama_attach_threadpool(ctx,s->pool,s->pool_batch);s->pools_paused=false;}
        auto reset=[&](){if(!llama_memory_seq_rm(llama_get_memory(ctx),0,prefix,-1))throw std::runtime_error("Cannot reset curve KV");};
        int current_mode=0;
        auto evaluate=[&](int offset,int n){
            if(abort_eval(&abort))throw std::runtime_error("Curve cancelled or deadline reached");
            std::vector<llama_pos> positions(n);std::vector<int8_t> flags(n,1);for(int i=0;i<n;i++)positions[i]=prefix+offset+i;
            auto batch=llama_batch_get_one(tokens.data()+offset,n);batch.pos=positions.data();batch.logits=flags.data();
            OutpostAttentionScope attention(current_mode,prefix+offset,n);
            auto start=Clock::now();int rc=llama_decode(ctx,batch);long us=micros(start);
            if(rc!=0||abort_eval(&abort))throw std::runtime_error("Curve evaluation interrupted");return us;
        };
        outpost_attention_reset();reset();std::vector<std::vector<float>> reference(count);std::vector<uint64_t> hashes(count);
        for(int i=0;i<count;i++){evaluate(i,1);const float *p=llama_get_logits_ith(ctx,-1);reference[i].assign(p,p+vocab);hashes[i]=hash_logits(p,vocab);}
        std::string original_stats=outpost_attention_stats();
        auto mode_reference=reference;std::vector<uint64_t> mode_hashes=hashes;
        current_mode=attention_mode;
        if(attention_mode==1){reset();for(int i=0;i<count;i++){evaluate(i,1);const float *p=llama_get_logits_ith(ctx,-1);mode_reference[i].assign(p,p+vocab);mode_hashes[i]=hash_logits(p,vocab);}}
        std::ostringstream out;out.imbue(std::locale::classic());
        out<<"{\"attentionMode\":"<<attention_mode<<",\"originalAttention\":"<<original_stats<<",\"modeReferenceHashes\":[";
        for(int i=0;i<count;i++){if(i)out<<",";out<<"\""<<mode_hashes[i]<<"\"";}out<<"],\"schemaVersion\":2,\"prefixTokens\":"<<prefix<<",\"positions\":"<<count<<",\"decodeThreads\":"<<s->context_threads<<",\"verifyThreads\":"<<verify_threads<<",\"promptThreads\":"<<s->context_prompt_threads
           <<",\"referenceHashes\":[";
        for(int i=0;i<count;i++){if(i)out<<",";out<<"\""<<hashes[i]<<"\"";}out<<"],\"observations\":[";
        const int widths[]={1,2,4,8};bool first=true;
        for(int round=0;round<2;round++)for(int order=0;order<4;order++){
            const int width=widths[round?3-order:order];auto reset_start=Clock::now();reset();long reset_us=micros(reset_start),evaluate_us=0;outpost_attention_reset();
            int bit_same=0,mode_same=0,top_same=0,first_difference=-1;double max_abs=0,mean_abs=0;std::vector<long> calls;std::vector<uint64_t> actual;
            for(int offset=0;offset<count;offset+=width){
                long us=evaluate(offset,width);calls.push_back(us);evaluate_us+=us;
                for(int row=0;row<width;row++){
                    const auto &a=reference[offset+row];const float *b=llama_get_logits_ith(ctx,row);actual.push_back(hash_logits(b,vocab));
                    mode_same+=std::memcmp(mode_reference[offset+row].data(),b,(size_t)vocab*sizeof(float))==0;
                    bool same=std::memcmp(a.data(),b,(size_t)vocab*sizeof(float))==0;bit_same+=same;if(!same&&first_difference<0)first_difference=offset+row;
                    int ia=0,ib=0;for(int j=0;j<vocab;j++){
                        if(!std::isfinite(a[j])||!std::isfinite(b[j]))throw std::runtime_error("Nonfinite curve logits");
                        if(a[j]>a[ia])ia=j;if(b[j]>b[ib])ib=j;double d=std::abs((double)a[j]-b[j]);max_abs=std::max(max_abs,d);mean_abs+=d;
                    }top_same+=ia==ib;
                }
            }
            if(!first)out<<",";first=false;
            out<<"{\"attention\":"<<outpost_attention_stats()<<",\"modeBitIdenticalPositions\":"<<mode_same<<",\"round\":"<<round<<",\"batchRows\":"<<width<<",\"evaluateUs\":"<<evaluate_us<<",\"resetUs\":"<<reset_us<<",\"bitIdenticalPositions\":"<<bit_same<<",\"sameTop1Positions\":"<<top_same
               <<",\"firstDifferentPosition\":"<<first_difference<<",\"maxAbsoluteLogitDifference\":"<<max_abs<<",\"meanAbsoluteLogitDifference\":"<<mean_abs/(count*(double)vocab)<<",\"batchMicros\":[";
            for(size_t i=0;i<calls.size();i++){if(i)out<<",";out<<calls[i];}out<<"],\"logitHashes\":[";
            for(size_t i=0;i<actual.size();i++){if(i)out<<",";out<<"\""<<actual[i]<<"\"";}out<<"]}";
        }
        reset();keep=prefix_limit==0;
        out<<"],\"scope\":\"Teacher-forced forward-cost/numerical diagnostic on the same cached prefix. No drafting, sampling or emission. Resets, logit copying and comparisons are outside evaluation timing. Rates are optimistic forward-only bounds, not deployable speculation or app speed. Context suffix removed, original thread settings restored, pools paused after return.\"}";
        return env->NewStringUTF(out.str().c_str());
    }catch(const std::exception&e){fail(env,e.what());return nullptr;}
}

extern "C" JNIEXPORT void JNICALL
Java_dev_outpost_app_NativeEngine_nativeAttentionForTests(JNIEnv *env,jclass,jlong id,jint mode) {
    try{if(mode!=0&&mode!=2&&mode!=3&&mode!=4)throw std::runtime_error("Unsupported attention test mode");auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);s->test_attention_mode=mode;}catch(const std::exception&e){fail(env,e.what());}
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_outpost_app_NativeEngine_nativeAttentionStats(JNIEnv *env,jclass,jlong id) {
    try{auto s=get(id);std::lock_guard<std::mutex> lock(s->inference);return env->NewStringUTF(s->last_attention_stats.c_str());}catch(const std::exception&e){fail(env,e.what());return nullptr;}
}
