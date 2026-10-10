package space.manus.kradusmedia.t004358040099515.music

/**
 * JNI bridge to the C++ fast paths (app/src/main/cpp/native-lib.cpp).
 *
 * Every call is guarded: [available] is false when the .so is missing or the
 * device can't load it — all callers MUST fall back to the pure-Kotlin path.
 * Unit tests run on the JVM without native code, so fallback is also the test path.
 *
 * Local-only computation: no network, no exfiltration, no tracking.
 */
object NativeBridge {

    val available: Boolean = runCatching {
        System.loadLibrary("kradus")
        true
    }.getOrDefault(false)

    /**
     * Single-pass parse + validate + migrate + serialize. Null on strict failure.
     * UTF-8 bytes in/out (not Modified UTF-8) so every code point, including
     * emoji/supplementary characters, round-trips exactly.
     */
    external fun normalizeJsonBytes(input: ByteArray): ByteArray?

    /** Indices of [fresh] whose uri is not in [existing]. Null on failure. */
    external fun mergeDedupeIndices(existing: Array<String>, fresh: Array<String>): IntArray?

    /** Recursive .mp3 scan of [roots] (cap [maxFiles]). UTF-8 JSON bytes or null. */
    external fun scanMp3DirsBytes(roots: Array<String>, maxFiles: Int): ByteArray?

    fun normalizeJson(input: String): String? =
        normalizeJsonBytes(input.toByteArray(Charsets.UTF_8))?.toString(Charsets.UTF_8)

    fun scanMp3Dirs(roots: Array<String>, maxFiles: Int): String? =
        scanMp3DirsBytes(roots, maxFiles)?.toString(Charsets.UTF_8)
}
