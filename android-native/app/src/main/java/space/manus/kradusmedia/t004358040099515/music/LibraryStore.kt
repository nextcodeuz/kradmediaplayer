package space.manus.kradusmedia.t004358040099515.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kotlin port of lib/music/storage.ts.
 *
 * - Keys mirror AsyncStorage: kradus.media.library.v2 + legacy v1 migration.
 * - 450 ms debounce + serial write queue (Mutex) + explicit flush on background.
 * - Device MP3s are copied into the app-private filesDir/kradus-audio dir with a
 *   unique name; deletion only touches files under that prefix.
 * - Local audio is never uploaded anywhere.
 */
class LibraryStore(private val appContext: Context) {

    // Lazy: the first getSharedPreferences() call reads + parses the whole XML
    // file synchronously (multi-MB on big libraries). A direct val would run
    // that disk read on the main thread (ViewModel init); lazy moves it onto
    // whichever background worker loads or saves first.
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var pending: LibrarySnapshot? = null
    private var debounceJob: Job? = null

    val audioDir: File
        get() {
            val dir = File(appContext.filesDir, AUDIO_DIRECTORY_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Off the main thread: reading the (possibly multi-MB) document, the native
     * normalize pass and the JSON parse are all CPU/heap heavy. Running them on
     * the caller's dispatcher (viewModelScope = Main) froze the UI and invited
     * the low-memory killer on weak phones, so this always runs on IO.
     */
    suspend fun loadLibrary(): LibrarySnapshot = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            var unreadable: Exception? = null
            for (key in listOf(STORAGE_KEY, LEGACY_STORAGE_KEY)) {
                val raw = prefs.getString(key, null) ?: continue
                try {
                    val snapshot = parseFast(raw)
                    if (key == LEGACY_STORAGE_KEY) {
                        // Preserve v1 until the v2 write succeeds.
                        try {
                            prefs.edit().putString(STORAGE_KEY, serializeLibrary(snapshot)).apply()
                            prefs.edit().remove(LEGACY_STORAGE_KEY).apply()
                        } catch (_: Exception) {
                            // Returning normalized data is safer than an empty library.
                        }
                    }
                    return@withLock snapshot
                } catch (e: Exception) {
                    if (e.message?.contains("yangiroq formatda") == true) throw e
                    unreadable = e
                }
            }
            if (unreadable != null) throw IllegalStateException("Saqlangan kutubxona o'qilmadi; ma'lumot ustidan yozilmadi.")
            LibrarySnapshot()
        }
    }

    /**
     * C++ single-pass parse + validate + migrate (huge-library fast path).
     * Falls back to the pure-Kotlin parser on any failure so precise error
     * messages (corrupt doc / newer schema) are preserved.
     */
    private fun parseFast(raw: String): LibrarySnapshot {
        if (NativeBridge.available) {
            try {
                val normalized = NativeBridge.normalizeJson(raw)
                if (normalized != null) return parseNormalizedSnapshot(normalized)
            } catch (_: Throwable) {
                // Fall through to Kotlin.
            }
        }
        return parseLibrarySnapshot(raw)
    }

    /** Debounced persist: frequent volume/progress-adjacent writes collapse into one. */
    fun scheduleSave(snapshot: LibrarySnapshot) {
        pending = snapshot
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flush()
        }
    }

    /** Immediate flush, e.g. when the app goes to background. */
    fun flush() {
        val snapshot = pending ?: return
        pending = null
        debounceJob?.cancel()
        debounceJob = null
        scope.launch {
            writeMutex.withLock {
                try {
                    prefs.edit().putString(STORAGE_KEY, serializeLibrary(snapshot)).apply()
                    prefs.edit().remove(LEGACY_STORAGE_KEY).apply()
                } catch (_: Exception) {
                    // Keep in-memory state; surface via callback from ViewModel if needed.
                }
            }
        }
    }

    /**
     * One-time cleanup: delete prefs files left by the removed remote-management
     * prototype (kradus.enrollment.* etc.). Our only legitimate file is
     * kradus_media_prefs.xml; anything else under the kradus.* namespace is a
     * leftover and is deleted. Idempotent; runs on the store's IO path.
     */
    suspend fun purgeLegacyTraces() = withContext(Dispatchers.IO) {
        try {
            val dir = File(appContext.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles()?.forEach { f ->
                val n = f.name
                if (n.startsWith("kradus.") && n != "$PREFS_NAME.xml" && n.endsWith(".xml")) {
                    try {
                        f.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Copy a user-picked document into private storage (sequential caller-side).
     * Returns the private file:// uri, or null on failure.
     */
    fun copyIntoPrivateStorage(source: Uri, displayName: String, id: String): String? {
        return try {
            val target = File(audioDir, "$id-${safeFileName(displayName)}")
            appContext.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            null
        }
    }

    /** True only for file:// uris inside this app's own private audio directory. */
    fun isManagedAudioFile(uri: String): Boolean {
        return try {
            if (!uri.startsWith("file://")) return false
            val prefix = audioDir.canonicalPath + File.separator
            val path = Uri.parse(uri).path ?: return false
            File(path).canonicalPath.startsWith(prefix)
        } catch (_: Exception) {
            false
        }
    }

    /** Deletes only files inside this app's own private audio directory. */
    fun deleteManagedAudioFile(uri: String): Boolean {
        return try {
            val prefix = audioDir.canonicalPath + File.separator
            val path = if (uri.startsWith("file://")) Uri.parse(uri).path ?: return false else uri
            val file = File(path)
            if (!file.canonicalPath.startsWith(prefix)) return false
            if (!file.exists()) return false
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val STORAGE_KEY = "kradus.media.library.v2"
        const val LEGACY_STORAGE_KEY = "kradus.media.library.v1"
        const val AUDIO_DIRECTORY_NAME = "kradus-audio"
        private const val PREFS_NAME = "kradus_media_prefs"
        const val SAVE_DEBOUNCE_MS = 450L
    }
}
