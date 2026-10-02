#include "speculation.h"
#include <algorithm>

namespace outpost::spec {
std::vector<llama_token> lookup(const std::vector<llama_token> &h,int depth,int min_match) {
    if(depth<=0 || depth>7 || min_match<1 || h.size()<(size_t)min_match+1) return {};
    const int size=(int)h.size();
    for(int key=std::min(16,size-1);key>=min_match;key--) {
        for(int start=size-key-1;start>=0;start--) {
            if(std::equal(h.begin()+start,h.begin()+start+key,h.end()-key)) {
                int n=std::min(depth,size-start-key);
                return {h.begin()+start+key,h.begin()+start+key+n};
            }
        }
    }
    return {};
}
Verified verify(const std::vector<llama_token> &draft,const std::function<llama_token(int)> &sample_target) {
    for(int i=0;;i++) {
        const auto token=sample_target(i);
        if(i==(int)draft.size() || token!=draft[i]) return {i,token};
    }
}
void Controller::observe_plain(double us) {
    if(us<=0) return;
    plain_us=plain_steps ? 0.8*plain_us+0.2*us : us; plain_steps++;
}
void Controller::observe_window(double us,int accepted) {
    windows++; advanced+=accepted+1; window_us+=us;
    if(adaptive && windows>=3 && plain_steps>=2 && window_us/advanced>=plain_us*0.95) disabled=true;
}
int checks(int *failures) {
    int n=0; *failures=0;
#define CHECK(c) do { n++; if(!(c)) (*failures)++; } while(0)
    CHECK(lookup({},3).empty()); CHECK(lookup({1,2,3},3).empty());
    CHECK(lookup({1,2,3,4,5,6,9,1,2,3,4},3)==std::vector<llama_token>({5,6,9}));
    CHECK(lookup({1,2,3,4,5,6,9,1,2,3,4},1)==std::vector<llama_token>({5}));
    CHECK(lookup({1,2,3,4,7,1,2,3,4,8,9,1,2,3,4},2)==std::vector<llama_token>({8,9}));
    CHECK(lookup({1,2,3,4,5,6,7,8},3).empty());
    CHECK(lookup({1,2,3,4,1,2,3,4},8).empty());
    for(int k=0;k<=7;k++) for(int mismatch=0;mismatch<=k;mismatch++) {
        std::vector<llama_token> draft(k); for(int i=0;i<k;i++) draft[i]=i+10;
        int calls=0;
        auto r=verify(draft,[&](int i) { calls++; return i<mismatch ? i+10 : 999; });
        CHECK(r.accepted==mismatch && r.next==999 && calls==mismatch+1);
    }
    Controller c; c.adaptive=true; CHECK(!c.allowed()); c.observe_plain(100); CHECK(!c.allowed()); c.observe_plain(100); CHECK(c.allowed());
    c.observe_window(300,0); c.observe_window(300,0); CHECK(c.allowed()); c.observe_window(300,0); CHECK(c.disabled);
    Controller fast; fast.adaptive=true; fast.observe_plain(100); fast.observe_plain(100);
    for(int i=0;i<5;i++) fast.observe_window(200,3);
    CHECK(fast.allowed());
#undef CHECK
    return n;
}
}
