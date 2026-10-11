package space.manus.kradusmedia.t004358040099515.music

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-device music discovery:
 *  1. MediaStore (primary — works with just the music permission).
 *  2. If All-Files-Access is granted: native C++ recursive .mp3 scan of the
 *     standard music locations, matched against MediaStore by absolute path so
 *     already-indexed files reuse their content:// uri + album art (dedupe-safe);
 *     non-indexed files are referenced as private file:// uris with on-demand
 *     embedded cover-art extraction.
 *
 * Local-only: paths never leave the device.
 */
private const val DIR_SCAN_CAP = 60000

fun isAllFilesGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

suspend fun scanDeviceFiles(context: Context): List<ScannedAudio> = withContext(Dispatchers.IO) {
    // One MediaStore pass gives both the tracks and the path index (no second
    // full-device query).
    val scan = scanAllAudio(context)
    val out = ArrayList(scan.items)
    val knownUris = out.map { it.uri }.toHashSet()
    if (!isAllFilesGranted(context)) return@withContext out

    // Path -> MediaStore entry (for content uri + art reuse and meta).
    val byUri = scan.items.associateBy { it.uri }

    val foundPaths = scanMp3Files(context)
    for (path in foundPaths) {
        val indexed = scan.pathIndex[path]
        if (indexed != null) {
            val entry = byUri[indexed]
            if (entry != null && knownUris.add(entry.uri)) out.add(entry)
            continue
        }
        val fileUri = Uri.fromFile(File(path)).toString()
        if (!knownUris.add(fileUri)) continue
        out.add(
            ScannedAudio(
                title = fileTitle(File(path).name),
                artist = "Noma'lum ijrochi",
                album = "",
                uri = fileUri,
                artUri = null, // embedded picture extracted on demand by ArtworkCache
                dateAdded = 0L
            )
        )
    }
    out.sortByDescending { it.dateAdded }
    out
}

/** Standard music locations on internal + removable storage. */
private val SCAN_DIR_NAMES = listOf(
    Environment.DIRECTORY_MUSIC,
    Environment.DIRECTORY_DOWNLOADS,
    Environment.DIRECTORY_PODCASTS,
    // Places where music piles up on real phones: voice recorders, audiobook
    // apps, hand-copied "MP3"/"Songs" folders. Missing dirs cost one failed
    // opendir and are skipped, so listing generously is safe.
    "Recordings",
    "Audiobooks",
    "Media",
    "Sounds",
    "MP3",
    "Songs",
    "Audio"
)

private fun musicRoots(context: Context): Array<String> {
    val roots = LinkedHashSet<String>()
    for (name in SCAN_DIR_NAMES) {
        try {
            roots.add(Environment.getExternalStoragePublicDirectory(name).absolutePath)
        } catch (_: Exception) {
        }
    }
    try {
        for (dir in context.getExternalFilesDirs(null)) {
            if (dir == null) continue
            val abs = dir.absolutePath
            val marker = "/Android/data/"
            val idx = abs.indexOf(marker)
            if (idx > 0) {
                val storageRoot = abs.substring(0, idx)
                for (name in SCAN_DIR_NAMES) roots.add("$storageRoot/$name")
            }
        }
    } catch (_: Exception) {
    }
    return roots.toTypedArray()
}

private fun scanMp3Files(context: Context): List<String> {
    val roots = musicRoots(context)
    // Native fast path.
    if (NativeBridge.available) {
        try {
            val json = NativeBridge.scanMp3Dirs(roots, DIR_SCAN_CAP)
            if (json != null) return parseFoundJson(json)
        } catch (_: Throwable) {
        }
    }
    // Kotlin fallback: same rules (skip hidden, no symlink follow, cap).
    val out = ArrayList<String>(1024)
    val stack = ArrayDeque<File>()
    for (r in roots) {
        val f = File(r)
        if (f.isDirectory) stack.add(f)
    }
    val depths = HashMap<String, Int>()
    while (stack.isNotEmpty() && out.size < DIR_SCAN_CAP) {
        val dir = stack.removeLast()
        val depth = depths[dir.absolutePath] ?: 0
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: continue
        for (child in children) {
            if (out.size >= DIR_SCAN_CAP) break
            if (child.name.startsWith(".")) continue
            try {
                if (isSymlink(child)) continue
                if (child.isDirectory) {
                    if (depth < 32) {
                        stack.add(child)
                        depths[child.absolutePath] = depth + 1
                    }
                } else if (child.isFile && child.name.lowercase().endsWith(".mp3")) {
                    out.add(child.canonicalPath)
                }
            } catch (_: Exception) {
            }
        }
    }
    return out
}

private fun isSymlink(file: File): Boolean {
    return try {
        file.canonicalPath != File(file.parent, file.name).absolutePath
    } catch (_: Exception) {
        true
    }
}

/**
 * Hand parser for the native scan output [{"p":path,"s":size},...].
 *
 * The shape is fixed and machine-written, so a tiny scanner replaces the
 * Gson List<Map<String,Any?>> tree — which ballooned into tens of MB of
 * LinkedTreeMap/Double nodes on 60k-file scans and invited the low-memory
 * killer on weak phones. Returns paths only; sizes are informational.
 * Empty list on any malformed input.
 */
internal fun parseFoundJson(json: String): List<String> {
    return try {
        FoundParser(json).parse()
    } catch (_: Exception) {
        emptyList()
    }
}

private class FoundParser(private val s: String) {
    private var i = 0

    fun parse(): List<String> {
        val out = ArrayList<String>()
        ws()
        expect('[')
        ws()
        if (peek() == ']') {
            i++
            end()
            return out
        }
        while (true) {
            ws()
            expect('{')
            var path: String? = null
            while (true) {
                ws()
                if (parseString() == "p") path = parseStringAfterColon() else skipValueAfterColon()
                ws()
                when (peek()) {
                    ',' -> { i++; continue }
                    '}' -> { i++; break }
                    else -> throw IllegalArgumentException("bad object")
                }
            }
            if (path != null) out.add(path)
            ws()
            when (peek()) {
                ',' -> { i++; continue }
                ']' -> {
                    i++
                    end()
                    return out
                }
                else -> throw IllegalArgumentException("bad array")
            }
        }
    }

    /** Only whitespace may follow the array (Gson parity: trailing junk fails). */
    private fun end() {
        ws()
        if (i != s.length) throw IllegalArgumentException("trailing data")
    }

    private fun parseStringAfterColon(): String {
        ws()
        expect(':')
        ws()
        return parseString()
    }

    private fun skipValueAfterColon() {
        ws()
        expect(':')
        ws()
        skipValue()
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) throw IllegalArgumentException("open string")
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (i >= s.length) throw IllegalArgumentException("open escape")
                    when (val e = s[i++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append(12.toChar()) // formfeed
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) throw IllegalArgumentException("open unicode")
                            var cp = s.substring(i, i + 4).toInt(16)
                            i += 4
                            if (cp in 0xD800..0xDBFF) {
                                if (i + 6 > s.length || s[i] != '\\' || s[i + 1] != 'u')
                                    throw IllegalArgumentException("lone surrogate")
                                i += 2
                                val lo = s.substring(i, i + 4).toInt(16)
                                i += 4
                                cp = 0x10000 + ((cp - 0xD800) shl 10) + (lo - 0xDC00)
                            }
                            sb.appendCodePoint(cp)
                        }
                        else -> throw IllegalArgumentException("bad escape")
                    }
                }
                else -> {
                    if (c < ' ') throw IllegalArgumentException("raw control")
                    sb.append(c)
                }
            }
        }
    }

    private fun skipValue() {
        when (val c = peek()) {
            '"' -> parseString()
            't' -> expectLit("true")
            'f' -> expectLit("false")
            'n' -> expectLit("null")
            '-', in '0'..'9' -> skipNumber()
            else -> throw IllegalArgumentException("bad value")
        }
    }

    private fun skipNumber() {
        if (peek() == '-') i++
        while (i < s.length && s[i] in '0'..'9') i++
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i] in '0'..'9') i++
        }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            i++
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            while (i < s.length && s[i] in '0'..'9') i++
        }
    }

    private fun expectLit(lit: String) {
        if (!s.startsWith(lit, i)) throw IllegalArgumentException("bad literal")
        i += lit.length
    }

    private fun ws() {
        while (i < s.length) {
            val c = s[i]
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break
            i++
        }
    }

    private fun peek(): Char {
        if (i >= s.length) throw IllegalArgumentException("open input")
        return s[i]
    }

    private fun expect(c: Char) {
        if (i >= s.length || s[i] != c) throw IllegalArgumentException("expected $c")
        i++
    }
}
