#pragma once
#include "llama.h"
#include <functional>
#include <vector>

namespace outpost::spec {
std::vector<llama_token> lookup(const std::vector<llama_token> &history,int depth,int min_match=4);
struct Verified { int accepted; llama_token next; };
// The target samples every output token. A mismatch ends the window immediately.
Verified verify(const std::vector<llama_token> &draft,const std::function<llama_token(int)> &sample_target);
struct Controller {
    bool adaptive=false,disabled=false;
    int plain_steps=0,windows=0,advanced=0;
    double plain_us=0,window_us=0;
    bool allowed() const { return !disabled && (!adaptive || plain_steps>=2); }
    void observe_plain(double us);
    void observe_window(double us,int accepted);
};
int checks(int *failures);
}
