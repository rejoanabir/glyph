#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstring>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "GlyphJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static std::atomic<bool> g_stop_flag{false};
static bool g_backend_initialized = false;

static void free_current_model() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_veltusstudio_glyph_LlamaBridge_nativeLoadModel(
        JNIEnv * env,
        jobject /* thiz */,
        jstring modelPath,
        jint nThreads,
        jint nCtx) {
    free_current_model();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading GGUF model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-safe across all Android devices

    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load GGUF model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : 2048;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully (n_ctx=%d, threads=%d)", ctx_params.n_ctx, ctx_params.n_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_veltusstudio_glyph_LlamaBridge_nativeStop(JNIEnv *, jobject) {
    g_stop_flag.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_veltusstudio_glyph_LlamaBridge_nativeUnload(JNIEnv *, jobject) {
    g_stop_flag.store(true);
    free_current_model();
}

extern "C" JNIEXPORT void JNICALL
Java_com_veltusstudio_glyph_LlamaBridge_nativeGenerate(
        JNIEnv * env,
        jobject /* thiz */,
        jobjectArray rolesArray,
        jobjectArray contentsArray,
        jfloat temperature,
        jint maxTokens,
        jobject callback) {
    if (!g_model || !g_ctx) return;

    g_stop_flag.store(false);
    llama_memory_clear(llama_get_memory(g_ctx), true);

    jsize msgCount = env->GetArrayLength(rolesArray);
    std::vector<std::string> roles_storage(msgCount);
    std::vector<std::string> contents_storage(msgCount);
    std::vector<llama_chat_message> chat_msgs(msgCount);

    for (jsize i = 0; i < msgCount; i++) {
        auto jRole = (jstring) env->GetObjectArrayElement(rolesArray, i);
        auto jContent = (jstring) env->GetObjectArrayElement(contentsArray, i);
        const char * rStr = env->GetStringUTFChars(jRole, nullptr);
        const char * cStr = env->GetStringUTFChars(jContent, nullptr);
        roles_storage[i] = rStr;
        contents_storage[i] = cStr;
        env->ReleaseStringUTFChars(jRole, rStr);
        env->ReleaseStringUTFChars(jContent, cStr);
        env->DeleteLocalRef(jRole);
        env->DeleteLocalRef(jContent);

        chat_msgs[i].role = roles_storage[i].c_str();
        chat_msgs[i].content = contents_storage[i].c_str();
    }

    // Format prompt using the model's embedded GGUF chat template (ChatML / Llama3 / Gemma / etc.)
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    std::vector<char> formatted(8192);
    int32_t len = llama_chat_apply_template(tmpl, chat_msgs.data(), chat_msgs.size(), true, formatted.data(), formatted.size());
    if (len > (int32_t) formatted.size()) {
        formatted.resize(len + 1);
        len = llama_chat_apply_template(tmpl, chat_msgs.data(), chat_msgs.size(), true, formatted.data(), formatted.size());
    }

    std::string prompt;
    if (len > 0) {
        prompt.assign(formatted.data(), len);
    } else {
        // Fallback ChatML format if GGUF lacks template metadata
        for (size_t i = 0; i < chat_msgs.size(); i++) {
            prompt += "<|im_start|>" + roles_storage[i] + "\n" + contents_storage[i] + "<|im_end|>\n";
        }
        prompt += "<|im_start|>assistant\n";
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const int n_prompt_tokens = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (n_prompt_tokens <= 0) return;

    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(vocab, prompt.c_str(), prompt.size(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        return;
    }

    // Evaluate prompt in batches of 256
    const int batch_size = 256;
    for (int i = 0; i < n_prompt_tokens; i += batch_size) {
        if (g_stop_flag.load()) return;
        int n_eval = std::min(batch_size, n_prompt_tokens - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, n_eval);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed during prompt eval");
            return;
        }
    }

    // Setup sampler chain
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    std::string utf8_buffer;
    int generated = 0;

    while (generated < maxTokens && !g_stop_flag.load()) {
        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char piece[256];
        int n = llama_token_to_piece(vocab, new_token, piece, sizeof(piece), 0, true);
        if (n > 0) {
            utf8_buffer.append(piece, n);
            // Flush only valid UTF-8 boundaries so multi-byte emojis/symbols never crash NewStringUTF
            size_t valid_len = 0;
            size_t idx = 0;
            while (idx < utf8_buffer.size()) {
                unsigned char c = (unsigned char) utf8_buffer[idx];
                size_t char_len = (c < 0x80) ? 1 : ((c & 0xE0) == 0xC0) ? 2 : ((c & 0xF0) == 0xE0) ? 3 : 4;
                if (idx + char_len <= utf8_buffer.size()) {
                    valid_len = idx + char_len;
                    idx += char_len;
                } else {
                    break;
                }
            }
            if (valid_len > 0) {
                std::string chunk = utf8_buffer.substr(0, valid_len);
                utf8_buffer.erase(0, valid_len);
                jstring jChunk = env->NewStringUTF(chunk.c_str());
                env->CallVoidMethod(callback, onTokenMethod, jChunk);
                env->DeleteLocalRef(jChunk);
            }
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            break;
        }
        generated++;
    }

    llama_sampler_free(smpl);
}
