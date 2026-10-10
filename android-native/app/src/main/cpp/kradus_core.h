// Kradus Media shared library engine (header-only, STL only).
//
// One implementation of the local music-library core used by every Kradus
// front-end:
//   - Android app  (native-lib.cpp, thin JNI wrappers around this file)
//   - Windows app  (windows/, same engine, console UI)
//   - desktop parity test (g++ harness, exercises this file directly)
//
// Contents:
//   1. normalizeDoc  - single-pass JSON parse + validate + migrate + serialize.
//                      Mirrors the model rules 1:1 (safeTrack/safeFolder,
//                      internet-folder migration, clamps, queue cleanup).
//   2. mergeDedupeVec - hash-set dedupe of fresh uris vs existing uris.
//   3. scanRoot       - iterative recursive .mp3 scan (no symlink following,
//                      hidden entries skipped, depth + file caps).
//
// Pure local computation: no network, no exfiltration, no tracking.
#pragma once

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#if defined(_WIN32)
// Windows (MSVC and MinGW alike): no POSIX <dirent.h> with d_type, so the
// scanner below uses std::filesystem with identical rules.
#include <filesystem>
#include <system_error>
#else
#include <dirent.h>
#include <sys/stat.h>
#endif

namespace kradus {

static const char* kInternetFolderName = "Internetdan qo\xE2\x80\x99shilganlar";  // ’ = U+2019
static const char* kInternetFolderNameLower = "internetdan qo\xE2\x80\x99shilganlar";
static const char* kInternetFolderColor = "#4D6784";
static const int kSchemaVersion = 2;
// Artist fallback — must stay identical to Models.kt ("Noma'lum ijrochi", ASCII apostrophe).
static const char* kArtistFallback = "Noma'lum ijrochi";

// A corrupt or hostile document with pathological nesting (e.g. 100k open
// brackets) must fail strictly, never overflow the stack and crash the app.
static const int kMaxJsonDepth = 64;

static long long nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

// ---------------- JSON DOM ----------------

struct JVal {
    enum Type { NUL, BOOL, NUM, STR, ARR, OBJ } type = NUL;
    bool b = false;
    double num = 0.0;
    std::string s;
    std::vector<JVal> a;
    std::vector<std::pair<std::string, JVal>> o;

    const JVal* find(const char* key) const {
        if (type != OBJ) return nullptr;
        for (const auto& kv : o) {
            if (kv.first == key) return &kv.second;
        }
        return nullptr;
    }
};

static void encodeUtf8(unsigned cp, std::string& out) {
    if (cp < 0x80) {
        out.push_back((char)cp);
    } else if (cp < 0x800) {
        out.push_back((char)(0xC0 | (cp >> 6)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        out.push_back((char)(0xE0 | (cp >> 12)));
        out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    } else {
        out.push_back((char)(0xF0 | (cp >> 18)));
        out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back((char)(0x80 | (cp & 0x3F)));
    }
}

struct Parser {
    const char* p;
    const char* end;
    int depth = 0;

    struct DepthGuard {
        Parser* q;
        explicit DepthGuard(Parser* q_) : q(q_) { ++q->depth; }
        ~DepthGuard() { --q->depth; }
    };

    void skipWs() {
        while (p < end && (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r')) ++p;
    }
    bool parseValue(JVal& out) {
        skipWs();
        if (p >= end) return false;
        char c = *p;
        if (c == '{') return parseObj(out);
        if (c == '[') return parseArr(out);
        if (c == '"') {
            out.type = JVal::STR;
            return parseStr(out.s);
        }
        if (c == 't') return parseLit("true", out, JVal::BOOL, true);
        if (c == 'f') return parseLit("false", out, JVal::BOOL, false);
        if (c == 'n') return parseLit("null", out, JVal::NUL, false);
        if (c == '-' || (c >= '0' && c <= '9')) {
            out.type = JVal::NUM;
            return parseNum(out.num);
        }
        return false;
    }
    bool parseLit(const char* lit, JVal& out, JVal::Type t, bool bv) {
        size_t n = std::strlen(lit);
        if ((size_t)(end - p) < n || std::memcmp(p, lit, n) != 0) return false;
        p += n;
        out.type = t;
        out.b = bv;
        return true;
    }
    bool parseNum(double& out) {
        const char* s = p;
        if (p < end && *p == '-') ++p;
        if (p >= end) return false;
        if (*p == '0') {
            ++p;
        } else if (*p >= '1' && *p <= '9') {
            while (p < end && *p >= '0' && *p <= '9') ++p;
        } else {
            return false;
        }
        if (p < end && *p == '.') {
            ++p;
            if (p >= end || *p < '0' || *p > '9') return false;
            while (p < end && *p >= '0' && *p <= '9') ++p;
        }
        if (p < end && (*p == 'e' || *p == 'E')) {
            ++p;
            if (p < end && (*p == '+' || *p == '-')) ++p;
            if (p >= end || *p < '0' || *p > '9') return false;
            while (p < end && *p >= '0' && *p <= '9') ++p;
        }
        try {
            out = std::stod(std::string(s, p));
        } catch (...) {
            return false;
        }
        return true;
    }
    bool parseStr(std::string& out) {
        // *p == '"'
        ++p;
        while (p < end) {
            char c = *p++;
            if (c == '"') return true;
            if (c == '\\') {
                if (p >= end) return false;
                char e = *p++;
                switch (e) {
                    case '"': out.push_back('"'); break;
                    case '\\': out.push_back('\\'); break;
                    case '/': out.push_back('/'); break;
                    case 'b': out.push_back('\b'); break;
                    case 'f': out.push_back('\f'); break;
                    case 'n': out.push_back('\n'); break;
                    case 'r': out.push_back('\r'); break;
                    case 't': out.push_back('\t'); break;
                    case 'u': {
                        if (end - p < 4) return false;
                        unsigned cp = 0;
                        for (int i = 0; i < 4; ++i) {
                            char h = *p++;
                            cp <<= 4;
                            if (h >= '0' && h <= '9') cp |= (unsigned)(h - '0');
                            else if (h >= 'a' && h <= 'f') cp |= (unsigned)(h - 'a' + 10);
                            else if (h >= 'A' && h <= 'F') cp |= (unsigned)(h - 'A' + 10);
                            else return false;
                        }
                        if (cp >= 0xD800 && cp <= 0xDBFF) {
                            // surrogate pair
                            if (end - p < 6 || p[0] != '\\' || p[1] != 'u') return false;
                            p += 2;
                            unsigned lo = 0;
                            for (int i = 0; i < 4; ++i) {
                                char h = *p++;
                                lo <<= 4;
                                if (h >= '0' && h <= '9') lo |= (unsigned)(h - '0');
                                else if (h >= 'a' && h <= 'f') lo |= (unsigned)(h - 'a' + 10);
                                else if (h >= 'A' && h <= 'F') lo |= (unsigned)(h - 'A' + 10);
                                else return false;
                            }
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                        }
                        encodeUtf8(cp, out);
                        break;
                    }
                    default:
                        return false;
                }
            } else {
                out.push_back(c);
            }
        }
        return false;
    }
    bool parseArr(JVal& out) {
        DepthGuard g(this);
        if (depth > kMaxJsonDepth) return false;
        // *p == '['
        ++p;
        out.type = JVal::ARR;
        skipWs();
        if (p < end && *p == ']') {
            ++p;
            return true;
        }
        while (true) {
            JVal v;
            if (!parseValue(v)) return false;
            out.a.push_back(std::move(v));
            skipWs();
            if (p >= end) return false;
            if (*p == ',') {
                ++p;
                continue;
            }
            if (*p == ']') {
                ++p;
                return true;
            }
            return false;
        }
    }
    bool parseObj(JVal& out) {
        DepthGuard g(this);
        if (depth > kMaxJsonDepth) return false;
        // *p == '{'
        ++p;
        out.type = JVal::OBJ;
        skipWs();
        if (p < end && *p == '}') {
            ++p;
            return true;
        }
        while (true) {
            skipWs();
            if (p >= end || *p != '"') return false;
            std::string key;
            if (!parseStr(key)) return false;
            skipWs();
            if (p >= end || *p != ':') return false;
            ++p;
            JVal v;
            if (!parseValue(v)) return false;
            out.o.emplace_back(std::move(key), std::move(v));
            skipWs();
            if (p >= end) return false;
            if (*p == ',') {
                ++p;
                continue;
            }
            if (*p == '}') {
                ++p;
                return true;
            }
            return false;
        }
    }
};

// ---------------- JSON writer ----------------

static void writeEscaped(const std::string& s, std::string& out) {
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back((char)c);
                }
        }
    }
    out.push_back('"');
}

// ---------------- model helpers (mirror Models.kt) ----------------

static std::string trimWs(const std::string& s) {
    size_t b = 0;
    while (b < s.size() && (s[b] == ' ' || s[b] == '\t' || s[b] == '\n' || s[b] == '\r' ||
                            s[b] == '\f' || s[b] == '\v'))
        ++b;
    size_t e = s.size();
    while (e > b && (s[e - 1] == ' ' || s[e - 1] == '\t' || s[e - 1] == '\n' ||
                     s[e - 1] == '\r' || s[e - 1] == '\f' || s[e - 1] == '\v'))
        --e;
    return s.substr(b, e - b);
}

static std::string asciiLower(std::string s) {
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
    }
    return s;
}

static bool startsWith(const std::string& s, const char* pre) {
    size_t n = std::strlen(pre);
    return s.size() >= n && std::memcmp(s.data(), pre, n) == 0;
}

// Truncate to N unicode code points without splitting multi-byte sequences.
static std::string utf8Take(const std::string& s, size_t n) {
    size_t i = 0, count = 0;
    while (i < s.size() && count < n) {
        unsigned char c = (unsigned char)s[i];
        size_t len = 1;
        if (c < 0x80) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        if (i + len > s.size()) break;  // truncated input; stop
        i += len;
        ++count;
    }
    return s.substr(0, i);
}

static bool isHexColor(const std::string& s) {
    if (s.size() != 7 || s[0] != '#') return false;
    for (size_t i = 1; i < 7; ++i) {
        char c = s[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
            return false;
    }
    return true;
}

static bool isCleanHttpUrl(const std::string& uri) {
    size_t sep = uri.find("://");
    if (sep == std::string::npos) return false;
    std::string scheme = asciiLower(uri.substr(0, sep));
    if (scheme != "https" && scheme != "http") return false;
    std::string rest = uri.substr(sep + 3);
    size_t authEnd = rest.find_first_of("/?#");
    std::string authority = (authEnd == std::string::npos) ? rest : rest.substr(0, authEnd);
    if (authority.empty()) return false;
    if (authority.find('@') != std::string::npos) return false;  // credentials
    for (char c : uri) {
        if (c <= 0x20 || c == 0x7F) return false;
    }
    return true;
}

struct Track {
    std::string id, title, artist, album, uri;
    int source = -1;  // 0 device, 1 url, 2 demo
    std::string folderId;
    bool hasFolder = false;
    bool favorite = false;
    long long addedAt = 0;
    std::string artUri;
};

struct Folder {
    std::string id, name, color;
    long long createdAt = 0;
    bool internet = false;
};

static const JVal* getStr(const JVal& o, const char* key, std::string& out) {
    const JVal* v = o.find(key);
    if (v && v->type == JVal::STR) {
        out = v->s;
        return v;
    }
    return nullptr;
}

static bool safeFolder(const JVal& v, Folder& out, long long now) {
    if (v.type != JVal::OBJ) return false;
    std::string id, name;
    if (!getStr(v, "id", id) || !getStr(v, "name", name)) return false;
    std::string nameT = trimWs(name);
    if (id.empty() || id.size() > 160 || nameT.empty()) return false;
    out.id = id;
    out.name = utf8Take(nameT, 100);
    std::string color;
    getStr(v, "color", color);
    out.color = isHexColor(color) ? color : kInternetFolderColor;
    const JVal* ca = v.find("createdAt");
    out.createdAt = (ca && ca->type == JVal::NUM && std::isfinite(ca->num)) ? (long long)ca->num : now;
    const JVal* kind = v.find("kind");
    out.internet = (kind && kind->type == JVal::STR && kind->s == "internet");
    return true;
}

static bool safeTrack(const JVal& v, Track& out, long long now) {
    if (v.type != JVal::OBJ) return false;
    std::string id, title, uri, sourceRaw;
    if (!getStr(v, "id", id) || !getStr(v, "title", title) || !getStr(v, "uri", uri)) return false;
    if (!getStr(v, "source", sourceRaw)) return false;
    std::string titleT = trimWs(title);
    std::string uriT = trimWs(uri);
    int source = -1;
    if (sourceRaw == "device") source = 0;
    else if (sourceRaw == "url") source = 1;
    else if (sourceRaw == "demo") source = 2;
    else return false;  // "web" and anything else are not durable
    if (id.empty() || id.size() > 160 || titleT.empty() || uriT.empty() || uriT.size() > 4096)
        return false;
    if (source == 0) {
        if (!startsWith(uriT, "file://") && !startsWith(uriT, "/") && !startsWith(uriT, "content://"))
            return false;
    } else {
        if (!isCleanHttpUrl(uriT)) return false;
    }
    out.id = id;
    out.title = utf8Take(titleT, 240);
    std::string artist;
    out.artist = getStr(v, "artist", artist) ? utf8Take(artist, 240) : kArtistFallback;
    std::string album;
    out.album = getStr(v, "album", album) ? utf8Take(album, 240) : "";
    out.uri = uriT;
    out.source = source;
    std::string fid;
    if (getStr(v, "folderId", fid)) {
        out.folderId = fid;
        out.hasFolder = true;
    } else {
        out.hasFolder = false;
    }
    const JVal* fav = v.find("favorite");
    out.favorite = (fav && fav->type == JVal::BOOL && fav->b);
    const JVal* aa = v.find("addedAt");
    out.addedAt = (aa && aa->type == JVal::NUM && std::isfinite(aa->num)) ? (long long)aa->num : now;
    std::string art;
    if (getStr(v, "albumArtUri", art) && art.size() <= 4096 &&
        (startsWith(art, "content://") || startsWith(art, "file://"))) {
        out.artUri = art;
    }
    return true;
}

static bool normalizeDoc(const JVal& root, std::string& outJson) {
    if (root.type != JVal::OBJ) return false;
    const JVal* tracksV = root.find("tracks");
    const JVal* foldersV = root.find("folders");
    if (!tracksV || tracksV->type != JVal::ARR || !foldersV || foldersV->type != JVal::ARR)
        return false;
    const JVal* schemaV = root.find("schemaVersion");
    if (schemaV && schemaV->type == JVal::NUM && (long long)schemaV->num > kSchemaVersion) return false;

    long long now = nowMs();

    std::vector<Folder> folders;
    std::unordered_set<std::string> folderIds;
    for (const JVal& f : foldersV->a) {
        Folder fo;
        if (!safeFolder(f, fo, now)) continue;
        if (folderIds.count(fo.id)) continue;
        folderIds.insert(fo.id);
        folders.push_back(std::move(fo));
    }

    std::vector<Track> tracks;
    std::unordered_set<std::string> trackIds;
    for (const JVal& t : tracksV->a) {
        Track tr;
        if (!safeTrack(t, tr, now)) continue;
        if (trackIds.count(tr.id)) continue;
        trackIds.insert(tr.id);
        tracks.push_back(std::move(tr));
    }

    // Internet folder migration.
    int internetIdx = -1;
    for (size_t i = 0; i < folders.size(); ++i) {
        if (folders[i].internet) {
            internetIdx = (int)i;
            break;
        }
    }
    if (internetIdx < 0) {
        for (size_t i = 0; i < folders.size(); ++i) {
            if (asciiLower(folders[i].name) == kInternetFolderNameLower) {
                folders[i].internet = true;
                internetIdx = (int)i;
                break;
            }
        }
    }
    bool hasUrl = false;
    for (const Track& t : tracks) {
        if (t.source == 1) {
            hasUrl = true;
            break;
        }
    }
    if (internetIdx < 0 && hasUrl) {
        std::string nid = "folder-internet-sources";
        int suffix = 1;
        while (folderIds.count(nid)) {
            nid = "folder-internet-sources-" + std::to_string(suffix++);
        }
        Folder nf;
        nf.id = nid;
        nf.name = kInternetFolderName;
        nf.color = kInternetFolderColor;
        nf.createdAt = now;
        nf.internet = true;
        folderIds.insert(nid);
        folders.push_back(std::move(nf));
        internetIdx = (int)folders.size() - 1;
    }
    const std::string* internetId = (internetIdx >= 0) ? &folders[(size_t)internetIdx].id : nullptr;

    for (Track& t : tracks) {
        bool validFolder = t.hasFolder && folderIds.count(t.folderId);
        if (t.source == 1 && !validFolder && internetId) {
            t.folderId = *internetId;
            t.hasFolder = true;
        } else if (!validFolder) {
            t.hasFolder = false;
            t.folderId.clear();
        }
    }

    std::unordered_set<std::string> validTrackIds;
    for (const Track& t : tracks) validTrackIds.insert(t.id);

    std::string currentId;
    bool hasCurrent = false;
    {
        std::string cid;
        if (getStr(root, "currentTrackId", cid) && validTrackIds.count(cid)) {
            currentId = cid;
            hasCurrent = true;
        }
    }
    std::vector<std::string> queue;
    const JVal* qv = root.find("queueIds");
    if (qv && qv->type == JVal::ARR) {
        std::unordered_set<std::string> seen;
        for (const JVal& q : qv->a) {
            if (q.type == JVal::STR && validTrackIds.count(q.s) && !seen.count(q.s)) {
                seen.insert(q.s);
                queue.push_back(q.s);
            }
        }
    }
    double volume = 0.82;
    {
        const JVal* vv = root.find("volume");
        if (vv && vv->type == JVal::NUM && std::isfinite(vv->num)) {
            volume = vv->num;
            if (volume < 0) volume = 0;
            if (volume > 1) volume = 1;
        }
    }
    double rate = 1.0;
    {
        const JVal* rv = root.find("playbackRate");
        if (rv && rv->type == JVal::NUM && std::isfinite(rv->num)) {
            rate = rv->num;
            if (rate < 0.5) rate = 0.5;
            if (rate > 2) rate = 2.0;
        }
    }
    std::string repeat = "off";
    {
        std::string rm;
        if (getStr(root, "repeatMode", rm) && (rm == "one" || rm == "all")) repeat = rm;
    }
    const JVal* sh = root.find("shuffle");
    bool shuffle = (sh && sh->type == JVal::BOOL && sh->b);

    // ---- serialize normalized snapshot ----
    std::string o;
    o.reserve(1 << 20);
    o += "{\"schemaVersion\":2,\"tracks\":[";
    bool first = true;
    for (const Track& t : tracks) {
        if (!first) o.push_back(',');
        first = false;
        o += "{\"id\":";
        writeEscaped(t.id, o);
        o += ",\"title\":";
        writeEscaped(t.title, o);
        o += ",\"artist\":";
        writeEscaped(t.artist, o);
        o += ",\"album\":";
        writeEscaped(t.album, o);
        o += ",\"uri\":";
        writeEscaped(t.uri, o);
        o += ",\"source\":";
        writeEscaped(t.source == 0 ? "device" : (t.source == 1 ? "url" : "demo"), o);
        o += ",\"folderId\":";
        if (t.hasFolder) writeEscaped(t.folderId, o);
        else o += "null";
        o += ",\"favorite\":";
        o += (t.favorite ? "true" : "false");
        o += ",\"addedAt\":";
        o += std::to_string(t.addedAt);
        if (!t.artUri.empty()) {
            o += ",\"albumArtUri\":";
            writeEscaped(t.artUri, o);
        }
        o.push_back('}');
    }
    o += "],\"folders\":[";
    first = true;
    for (const Folder& f : folders) {
        if (!first) o.push_back(',');
        first = false;
        o += "{\"id\":";
        writeEscaped(f.id, o);
        o += ",\"name\":";
        writeEscaped(f.name, o);
        o += ",\"color\":";
        writeEscaped(f.color, o);
        o += ",\"createdAt\":";
        o += std::to_string(f.createdAt);
        if (f.internet) o += ",\"kind\":\"internet\"";
        o.push_back('}');
    }
    o += "],\"currentTrackId\":";
    if (hasCurrent) writeEscaped(currentId, o);
    else o += "null";
    o += ",\"queueIds\":[";
    for (size_t i = 0; i < queue.size(); ++i) {
        if (i) o.push_back(',');
        writeEscaped(queue[i], o);
    }
    char numbuf[64];
    std::snprintf(numbuf, sizeof(numbuf), "%.7g", volume);
    o += "],\"repeatMode\":";
    writeEscaped(repeat, o);
    o += ",\"shuffle\":";
    o += (shuffle ? "true" : "false");
    o += ",\"volume\":";
    o += numbuf;
    std::snprintf(numbuf, sizeof(numbuf), "%.7g", rate);
    o += ",\"playbackRate\":";
    o += numbuf;
    o.push_back('}');
    outJson = std::move(o);
    return true;
}

// ---------------- recursive mp3 scan ----------------

static bool hasMp3Suffix(const char* name) {
    size_t n = std::strlen(name);
    if (n < 5) return false;
    const char* ext = name + n - 4;
    return (ext[0] == '.') && (std::tolower((unsigned char)ext[1]) == 'm') &&
           (std::tolower((unsigned char)ext[2]) == 'p') &&
           (std::tolower((unsigned char)ext[3]) == '3');
}

#if defined(_WIN32)
// Windows has no POSIX readdir with d_type: identical scan over std::filesystem
// (hidden skipped, symlinks never followed, depth/file caps kept,
// forward-slash UTF-8 paths like on every other platform).
static void scanRoot(const std::string& root, int maxFiles,
                     std::vector<std::pair<std::string, long long>>& out) {
    namespace fs = std::filesystem;
    if ((int)out.size() >= maxFiles) return;
    std::error_code ec;
    if (!fs::is_directory(root, ec) || ec) return;
    fs::recursive_directory_iterator it(root, fs::directory_options::skip_permission_denied, ec);
    fs::recursive_directory_iterator end;
    if (ec) return;
    for (; it != end && (int)out.size() < maxFiles; it.increment(ec)) {
        if (ec) {
            ec.clear();
            continue;
        }
        std::string name = it->path().filename().u8string();
        if (name.empty() || name[0] == '.') {
            std::error_code ec2;
            if (it->is_directory(ec2) && !ec2) it.disable_recursion_pending();
            continue;
        }
        std::error_code ec2;
        if (it->is_symlink(ec2) && !ec2) {  // never follow symlinks (loop-safe)
            it.disable_recursion_pending();
            continue;
        }
        if (it.depth() >= 32) it.disable_recursion_pending();
        if (it->is_directory(ec2) && !ec2) continue;
        if (!it->is_regular_file(ec2) || ec2) continue;
        if (!hasMp3Suffix(name.c_str())) continue;
        std::string full = it->path().generic_u8string();
        if (full.size() > 4096) continue;
        std::uintmax_t sz = it->file_size(ec2);
        if (ec2) continue;
        out.emplace_back(full, (long long)sz);
    }
}
#else
static void scanRoot(const std::string& root, int maxFiles,
                     std::vector<std::pair<std::string, long long>>& out) {
    if ((int)out.size() >= maxFiles) return;
    std::vector<std::pair<std::string, int>> stack;
    stack.emplace_back(root, 0);
    while (!stack.empty() && (int)out.size() < maxFiles) {
        std::string dir = std::move(stack.back().first);
        int depth = stack.back().second;
        stack.pop_back();
        DIR* dp = opendir(dir.c_str());
        if (!dp) continue;
        while (dirent* de = readdir(dp)) {
            if ((int)out.size() >= maxFiles) break;
            const char* name = de->d_name;
            if (name[0] == '\0' || std::strcmp(name, ".") == 0 || std::strcmp(name, "..") == 0) continue;
            if (name[0] == '.') continue;  // hidden dirs/files skipped
            std::string full = dir + "/" + name;
            if (full.size() > 4096) continue;
            unsigned char t = de->d_type;
            if (t == DT_LNK) continue;  // never follow symlinks (loop-safe)
            if (t == DT_DIR || t == DT_UNKNOWN) {
                bool isDir = (t == DT_DIR);
                if (!isDir) {
                    struct stat st;
                    if (stat(full.c_str(), &st) != 0) continue;
                    isDir = S_ISDIR(st.st_mode);
                    if (!isDir && !S_ISREG(st.st_mode)) continue;
                }
                if (isDir) {
                    if (depth < 32) stack.emplace_back(full, depth + 1);
                    continue;
                }
            }
            if (t != DT_REG && t != DT_UNKNOWN) continue;
            if (!hasMp3Suffix(name)) continue;
            struct stat st;
            if (stat(full.c_str(), &st) != 0 || !S_ISREG(st.st_mode)) continue;
            out.emplace_back(full, (long long)st.st_size);
        }
        closedir(dp);
    }
}
#endif

// Pure dedupe core.
static std::vector<int> mergeDedupeVec(const std::vector<std::string>& existing,
                                       const std::vector<std::string>& fresh) {
    std::unordered_set<std::string> known;
    known.reserve(existing.size() * 2 + 1);
    for (const auto& s : existing) known.insert(s);
    std::vector<int> idx;
    idx.reserve(fresh.size());
    for (size_t i = 0; i < fresh.size(); ++i) {
        if (known.insert(fresh[i]).second) idx.push_back((int)i);
    }
    return idx;
}

}  // namespace kradus
