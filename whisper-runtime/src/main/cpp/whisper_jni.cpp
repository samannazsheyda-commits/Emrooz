#include <jni.h>
#include <whisper.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace {
struct Decoder {
    whisper_context *context = nullptr;
    std::mutex decode_mutex;
    std::atomic<bool> cancelled{false};
    ~Decoder() { if (context) whisper_free(context); }
};
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Decoder>> registry;
std::atomic<jlong> next_id{1};
std::shared_ptr<Decoder> find_decoder(jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    const auto it = registry.find(id);
    return it == registry.end() ? nullptr : it->second;
}
void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
bool abort_decode(void *user) { return static_cast<Decoder *>(user)->cancelled.load(); }
bool begin_encoder(whisper_context *, whisper_state *, void *user) { return !abort_decode(user); }
void silent_log(ggml_log_level, const char *, void *) { }
struct Pcm {
    std::vector<float> samples;
    explicit Pcm(size_t size) : samples(size) { }
    ~Pcm() {
        volatile float *p = samples.data();
        for (size_t i = 0; i < samples.size(); ++i) p[i] = 0.0f;
    }
};
jstring utf8_string(JNIEnv *env, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    jclass string_class = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(string_class, constructor, bytes, encoding));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(encoding);
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nameemrooz_whisper_WhisperContext_nativeCreate(JNIEnv *env, jclass, jstring path) {
    if (!path) { fail(env, "Model path is missing"); return 0; }
    const char *file = env->GetStringUTFChars(path, nullptr);
    if (!file) return 0;
    try {
        whisper_log_set(silent_log, nullptr);
        auto decoder = std::make_shared<Decoder>();
        auto params = whisper_context_default_params();
        params.use_gpu = false;
        params.flash_attn = false;
        decoder->context = whisper_init_from_file_with_params(file, params);
        env->ReleaseStringUTFChars(path, file);
        file = nullptr;
        if (!decoder->context) { fail(env, "Offline Whisper model could not be loaded"); return 0; }
        const auto id = next_id.fetch_add(1);
        std::lock_guard<std::mutex> lock(registry_mutex);
        registry.emplace(id, std::move(decoder));
        return id;
    } catch (...) {
        if (file) env->ReleaseStringUTFChars(path, file);
        fail(env, "Offline Whisper initialization failed");
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nameemrooz_whisper_WhisperContext_nativeTranscribe(JNIEnv *env, jobject, jlong id, jfloatArray input, jstring language) {
    auto decoder = find_decoder(id);
    if (!decoder) { fail(env, "Whisper context is closed"); return nullptr; }
    try {
        std::lock_guard<std::mutex> lock(decoder->decode_mutex);
        if (decoder->cancelled.load()) return utf8_string(env, "");
        const jsize count = input ? env->GetArrayLength(input) : 0;
        if (count == 0) return utf8_string(env, "");
        if (count < 0 || count > 30 * WHISPER_SAMPLE_RATE) { fail(env, "Invalid PCM length"); return nullptr; }
        Pcm pcm(static_cast<size_t>(count));
        env->GetFloatArrayRegion(input, 0, count, pcm.samples.data());
        if (env->ExceptionCheck()) return nullptr;
        double energy = 0.0;
        for (auto &sample : pcm.samples) {
            if (!std::isfinite(sample)) sample = 0.0f;
            sample = std::clamp(sample, -1.0f, 1.0f);
            energy += static_cast<double>(sample) * sample;
        }
        // Never ask Whisper to invent text from silence.
        if (std::sqrt(energy / count) < 0.00001) return utf8_string(env, "");
        const char *lang = env->GetStringUTFChars(language, nullptr);
        if (!lang) return nullptr;
        std::string selected_language(lang);
        env->ReleaseStringUTFChars(language, lang);
        if (selected_language != "fa" && selected_language != "en") { fail(env, "Unsupported language"); return nullptr; }
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
        params.translate = false;
        params.language = selected_language.c_str();
        params.detect_language = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_realtime = false;
        params.print_progress = false;
        params.print_timestamps = false;
        params.print_special = false;
        params.suppress_blank = true;
        params.suppress_nst = true;
        params.temperature = 0.0f;
        params.temperature_inc = 0.0f;
        params.no_speech_thold = 0.6f;
        params.abort_callback = abort_decode;
        params.abort_callback_user_data = decoder.get();
        params.encoder_begin_callback = begin_encoder;
        params.encoder_begin_callback_user_data = decoder.get();
        const int result = whisper_full(decoder->context, params, pcm.samples.data(), count);
        if (decoder->cancelled.load()) return utf8_string(env, "");
        if (result != 0) { fail(env, "Offline Whisper decoding failed"); return nullptr; }
        std::string text;
        const int segments = whisper_full_n_segments(decoder->context);
        for (int i = 0; i < segments; ++i) {
            if (whisper_full_get_segment_no_speech_prob(decoder->context, i) > 0.6f) continue;
            const char *part = whisper_full_get_segment_text(decoder->context, i);
            if (part) text += part;
        }
        return utf8_string(env, text);
    } catch (...) {
        fail(env, "Offline Whisper decoding failed");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_nameemrooz_whisper_WhisperContext_nativeCancel(JNIEnv *, jobject, jlong id) {
    if (auto decoder = find_decoder(id)) decoder->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nameemrooz_whisper_WhisperContext_nativeFree(JNIEnv *, jobject, jlong id) {
    std::shared_ptr<Decoder> decoder;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto it = registry.find(id);
        if (it == registry.end()) return;
        decoder = std::move(it->second);
        registry.erase(it);
    }
    decoder->cancelled.store(true);
    // Active native calls retain shared ownership until decoding has returned.
}
