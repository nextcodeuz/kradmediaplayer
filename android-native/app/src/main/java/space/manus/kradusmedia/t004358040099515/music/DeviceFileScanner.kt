package space.manus.kradusmedia.t004358040099515.music

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    val media = scanAllAudio(context)
    val out = ArrayList(media)
    val knownUris = out.map { it.uri }.toHashSet()
    if (!isAllFilesGranted(context)) return@withContext out

    // Path -> MediaStore entry (for content uri + art reuse and meta).
    val pathIndex = buildPathIndex(context)
    val byUri = media.associateBy { it.uri }

    val foundPaths = scanMp3Files(context)
    for (path in foundPaths) {
        val indexed = pathIndex[path]
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

/** Absolute path -> content uri for every indexed audio file. */
private fun buildPathIndex(context: Context): Map<String, String> {
    val map = HashMap<String, String>(4096)
    val collection = audioCollection()
    val projection = arrayOf(
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media._ID
    )
    try {
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                val data = c.getString(iData) ?: continue
                val id = c.getLong(iId)
                map[data] = ContentUris.withAppendedId(collection, id).toString()
            }
        }
    } catch (_: Exception) {
    }
    return map
}

private fun audioCollection(): Uri =
    if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
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

private fun parseFoundJson(json: String): List<String> {
    return try {
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val list: List<Map<String, Any?>> = Gson().fromJson(json, type) ?: return emptyList()
        list.mapNotNull { it["p"] as? String }
    } catch (_: Exception) {
        emptyList()
    }
}
