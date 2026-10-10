// Kradus Media native fast paths (C++, -O3) — thin JNI wrappers.
//
// All heavy work lives in the shared header kradus_core.h (same engine as the
// Windows app and the desktop parity test):
//   1. normalizeJsonBytes - single-pass JSON parse + validate + migrate.
//   2. mergeDedupeIndices - hash-set dedupe of fresh uris vs existing uris.
//   3. scanMp3DirsBytes   - iterative recursive .mp3 scan, user-granted roots.
//
// JSON crosses the boundary as plain UTF-8 bytes (not Modified UTF-8), so
// every code point — including emoji — round-trips exactly.
// Pure local computation: no network, no exfiltration, no tracking.

#include <jni.h>

#include <string>
#include <utility>
#include <vector>

#include "kradus_core.h"

static std::string jstringToStd(JNIEnv* env, jstring j) {
    if (!j) return std::string();
    const char* c = env->GetStringUTFChars(j, nullptr);
    std::string s = c ? c : "";
    if (c) env->ReleaseStringUTFChars(j, c);
    return s;
}

static std::string jbytesToStd(JNIEnv* env, jbyteArray arr) {
    if (!arr) return std::string();
    jsize n = env->GetArrayLength(arr);
    std::string s((size_t)n, '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte*>(&s[0]));
    return s;
}

static jbyteArray stdToJbytes(JNIEnv* env, const std::string& s) {
    jbyteArray out = env->NewByteArray((jsize)s.size());
    if (!out) return nullptr;
    if (!s.empty()) env->SetByteArrayRegion(out, 0, (jsize)s.size(),
                                            reinterpret_cast<const jbyte*>(s.data()));
    return out;
}

extern "C" {

// byte[] normalizeJsonBytes(byte[] input) — normalized JSON (UTF-8) or null.
JNIEXPORT jbyteArray JNICALL
Java_space_manus_kradusmedia_t004358040099515_music_NativeBridge_normalizeJsonBytes(
    JNIEnv* env, jobject, jbyteArray input) {
    if (!input) return nullptr;
    std::string src = jbytesToStd(env, input);
    kradus::Parser parser{src.data(), src.data() + src.size()};
    kradus::JVal root;
    if (!parser.parseValue(root)) return nullptr;
    parser.skipWs();
    if (parser.p != parser.end) return nullptr;
    std::string out;
    if (!kradus::normalizeDoc(root, out)) return nullptr;
    return stdToJbytes(env, out);
}

// int[] mergeDedupeIndices(String[] existing, String[] fresh)
JNIEXPORT jintArray JNICALL
Java_space_manus_kradusmedia_t004358040099515_music_NativeBridge_mergeDedupeIndices(
    JNIEnv* env, jobject, jobjectArray existing, jobjectArray fresh) {
    if (!existing || !fresh) return nullptr;
    jsize nE = env->GetArrayLength(existing);
    jsize nF = env->GetArrayLength(fresh);
    std::vector<std::string> eVec;
    std::vector<std::string> fVec;
    eVec.reserve((size_t)nE);
    fVec.reserve((size_t)nF);
    for (jsize i = 0; i < nE; ++i) {
        auto* js = (jstring)env->GetObjectArrayElement(existing, i);
        if (js) {
            eVec.push_back(jstringToStd(env, js));
            env->DeleteLocalRef(js);
        }
    }
    for (jsize i = 0; i < nF; ++i) {
        auto* js = (jstring)env->GetObjectArrayElement(fresh, i);
        fVec.push_back(js ? jstringToStd(env, js) : "");
        if (js) env->DeleteLocalRef(js);
    }
    std::vector<jint> idx;
    for (int v : kradus::mergeDedupeVec(eVec, fVec)) idx.push_back(v);
    jintArray out = env->NewIntArray((jsize)idx.size());
    if (!out) return nullptr;
    if (!idx.empty()) env->SetIntArrayRegion(out, 0, (jsize)idx.size(), idx.data());
    return out;
}

// byte[] scanMp3DirsBytes(String[] roots, int maxFiles) — JSON array (UTF-8) or null.
JNIEXPORT jbyteArray JNICALL
Java_space_manus_kradusmedia_t004358040099515_music_NativeBridge_scanMp3DirsBytes(
    JNIEnv* env, jobject, jobjectArray roots, jint maxFiles) {
    if (!roots || maxFiles <= 0) return nullptr;
    jsize n = env->GetArrayLength(roots);
    std::vector<std::pair<std::string, long long>> found;
    found.reserve(1024);
    for (jsize i = 0; i < n; ++i) {
        auto* js = (jstring)env->GetObjectArrayElement(roots, i);
        std::string root = js ? jstringToStd(env, js) : "";
        if (js) env->DeleteLocalRef(js);
        if (root.empty()) continue;
        kradus::scanRoot(root, (int)maxFiles, found);
        if ((int)found.size() >= maxFiles) break;
    }
    std::string o;
    o.reserve(found.size() * 160);
    o.push_back('[');
    for (size_t i = 0; i < found.size(); ++i) {
        if (i) o.push_back(',');
        o += "{\"p\":";
        kradus::writeEscaped(found[i].first, o);
        o += ",\"s\":";
        o += std::to_string(found[i].second);
        o.push_back('}');
    }
    o.push_back(']');
    return stdToJbytes(env, o);
}

}  // extern "C"
