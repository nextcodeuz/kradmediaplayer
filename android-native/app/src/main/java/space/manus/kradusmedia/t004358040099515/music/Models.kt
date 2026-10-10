package space.manus.kradusmedia.t004358040099515.music

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Kotlin port of lib/music/types.ts + lib/music/model.ts.
 * Pure JVM code (no Android imports) so unit tests run without a device.
 *
 * Rules mirrored 1:1 from the TypeScript source:
 * - device tracks must be file://, url/demo must be credential-free http(s)
 * - volume clamped to [0,1], playbackRate to [0.5,2]
 * - orphan URL tracks migrate into a single persistent internet folder
 * - parse fails loudly on corrupt docs / newer schema instead of wiping data
 */

enum class TrackSource { DEVICE, WEB, URL, DEMO }

enum class RepeatMode { OFF, ALL, ONE }

data class Track(
    val id: String,
    val title: String,
    val artist: String = "Noma'lum ijrochi",
    val album: String = "",
    val uri: String,
    val source: TrackSource,
    val folderId: String? = null,
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    /** Album/cover art uri (MediaStore albumart content:// or extracted file://). Null = letter placeholder. */
    val artUri: String? = null
)

data class MusicFolder(
    val id: String,
    val name: String,
    val color: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isInternet: Boolean = false
)

data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val currentTrackId: String? = null,
    val queueIds: List<String> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val volume: Float = 0.82f,
    val playbackRate: Float = 1f
)

data class TrackPatch(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val folderId: String? = null,
    val clearFolder: Boolean = false
)

const val INTERNET_FOLDER_NAME = "Internetdan qo'shilganlar"
const val INTERNET_FOLDER_COLOR = "#4D6784"
const val SCHEMA_VERSION = 2
const val DEMO_URL = "https://samplelib.com/mp3/sample-50s.mp3"

val EMPTY_LIBRARY = LibrarySnapshot()
val FOLDER_COLORS = listOf("#4D6784", "#596E64", "#82725A", "#665D73", "#5A6F78")

private val gson = Gson()
private val colorRegex = Regex("^#[\\da-fA-F]{6}$")

fun makeId(prefix: String): String =
    "$prefix-${System.currentTimeMillis().toString(36)}-${(100000..999999).random().toString(36)}"

fun fileTitle(name: String): String =
    name.substringBeforeLast('.', name).replace(Regex("[_-]+"), " ").trim().ifEmpty { "Nomsiz trek" }

fun safeFileName(name: String): String =
    name.replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(96).ifEmpty { "track.mp3" }

private fun parseUrl(uri: String): java.net.URI? = try {
    java.net.URI(uri)
} catch (_: Exception) {
    null
}

private fun isCleanHttpUrl(uri: String): Boolean {
    val parsed = parseUrl(uri) ?: return false
    val scheme = parsed.scheme?.lowercase() ?: return false
    if (scheme != "https" && scheme != "http") return false
    if (parsed.host.isNullOrEmpty()) return false
    if (parsed.userInfo != null) return false
    return true
}

@Suppress("UNCHECKED_CAST")
fun safeFolder(value: Any?): MusicFolder? {
    if (value !is Map<*, *>) return null
    val id = value["id"] as? String ?: return null
    val name = value["name"] as? String ?: return null
    if (id.isBlank() || id.length > 160 || name.isBlank()) return null
    val color = value["color"] as? String
    val createdAt = (value["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    return MusicFolder(
        id = id,
        name = name.trim().take(100),
        color = if (color != null && colorRegex.matches(color)) color else INTERNET_FOLDER_COLOR,
        createdAt = createdAt,
        isInternet = value["kind"] == "internet"
    )
}

@Suppress("UNCHECKED_CAST")
fun safeTrack(value: Any?): Track? {
    if (value !is Map<*, *>) return null
    val id = value["id"] as? String ?: ""
    val title = (value["title"] as? String)?.trim() ?: ""
    val uri = (value["uri"] as? String)?.trim() ?: ""
    val sourceRaw = value["source"] as? String ?: return null
    val source = when (sourceRaw) {
        "device" -> TrackSource.DEVICE
        "url" -> TrackSource.URL
        "demo" -> TrackSource.DEMO
        "web" -> TrackSource.WEB
        else -> return null
    }
    if (id.isEmpty() || id.length > 160 || title.isEmpty() || uri.isEmpty() || uri.length > 4096) return null
    // Mirror TS: legacy "web" entries are durable-filtered at save time; treat as invalid on load
    // except when explicitly kept in memory.
    if (source == TrackSource.WEB) return null
    // Device tracks: private file:// copies, absolute paths, or MediaStore content://
    // references from a library scan (permission-gated, never uploaded).
    if (source == TrackSource.DEVICE &&
        !uri.startsWith("file://") && !uri.startsWith("/") && !uri.startsWith("content://")
    ) return null
    if ((source == TrackSource.URL || source == TrackSource.DEMO) && !isCleanHttpUrl(uri)) return null
    return Track(
        id = id,
        title = title.take(240),
        artist = (value["artist"] as? String)?.take(240) ?: "Noma'lum ijrochi",
        album = (value["album"] as? String)?.take(240) ?: "",
        uri = uri,
        source = source,
        folderId = value["folderId"] as? String,
        favorite = value["favorite"] == true,
        addedAt = (value["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        artUri = (value["albumArtUri"] as? String)?.take(4096)?.takeIf {
            it.startsWith("content://") || it.startsWith("file://")
        }
    )
}

private fun makeInternetFolder(folders: List<MusicFolder>): MusicFolder {
    val usedIds = folders.map { it.id }.toSet()
    var id = "folder-internet-sources"
    var suffix = 1
    while (usedIds.contains(id)) id = "folder-internet-sources-${suffix++}"
    return MusicFolder(id, INTERNET_FOLDER_NAME, INTERNET_FOLDER_COLOR, System.currentTimeMillis(), true)
}

/** Validates and migrates persisted user state without touching the filesystem. */
@Suppress("UNCHECKED_CAST")
fun normalizeLibrarySnapshot(input: Any?): LibrarySnapshot {
    val raw = input as? Map<String, Any?> ?: emptyMap()
    val folderIds = mutableSetOf<String>()
    val folders = ((raw["folders"] as? List<*>) ?: emptyList<Any?>())
        .mapNotNull { safeFolder(it) }
        .filter { f -> if (folderIds.contains(f.id)) false else { folderIds.add(f.id); true } }
        .toMutableList()

    val seenTrackIds = mutableSetOf<String>()
    val tracks = ((raw["tracks"] as? List<*>) ?: emptyList<Any?>())
        .mapNotNull { safeTrack(it) }
        .filter { t -> if (seenTrackIds.contains(t.id)) false else { seenTrackIds.add(t.id); true } }

    var internetFolder = folders.firstOrNull { it.isInternet }
        ?: folders.firstOrNull { it.name.lowercase() == INTERNET_FOLDER_NAME.lowercase() }
    val hasUrlTracks = tracks.any { it.source == TrackSource.URL }
    if (internetFolder == null && hasUrlTracks) {
        internetFolder = makeInternetFolder(folders)
        folders.add(internetFolder)
        folderIds.add(internetFolder.id)
    } else if (internetFolder != null && !internetFolder.isInternet) {
        val upgraded = internetFolder.copy(isInternet = true)
        val index = folders.indexOfFirst { it.id == upgraded.id }
        if (index >= 0) folders[index] = upgraded
        internetFolder = upgraded
    }

    val normalizedTracks = tracks.map { track ->
        val validFolderId = if (track.folderId != null && folderIds.contains(track.folderId)) track.folderId else null
        if (track.source == TrackSource.URL && validFolderId == null && internetFolder != null)
            track.copy(folderId = internetFolder.id)
        else track.copy(folderId = validFolderId)
    }
    val validTrackIds = normalizedTracks.map { it.id }.toSet()
    val currentTrackId = (raw["currentTrackId"] as? String)?.takeIf { validTrackIds.contains(it) }
    val queueIds = ((raw["queueIds"] as? List<*>)?.filterIsInstance<String>()
        ?.filter { validTrackIds.contains(it) }?.distinct()) ?: emptyList()
    val volume = ((raw["volume"] as? Number)?.toFloat() ?: EMPTY_LIBRARY.volume).coerceIn(0f, 1f)
    val playbackRate = ((raw["playbackRate"] as? Number)?.toFloat() ?: 1f).coerceIn(0.5f, 2f)
    val repeatMode = when (raw["repeatMode"]) {
        "one" -> RepeatMode.ONE
        "all" -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
    return LibrarySnapshot(
        tracks = normalizedTracks,
        folders = folders,
        currentTrackId = currentTrackId,
        queueIds = queueIds,
        repeatMode = repeatMode,
        shuffle = raw["shuffle"] == true,
        volume = volume,
        playbackRate = playbackRate
    )
}

/** Parse a persisted document strictly so unreadable data never becomes an empty snapshot. */
fun parseLibrarySnapshot(serialized: String): LibrarySnapshot {
    val type = object : TypeToken<Map<String, Any?>>() {}.type
    val parsed: Map<String, Any?> = try {
        gson.fromJson(serialized, type) ?: throw IllegalArgumentException("Bo'sh hujjat")
    } catch (e: com.google.gson.JsonSyntaxException) {
        throw IllegalArgumentException("Kutubxona hujjati buzilgan: ${e.message}")
    } catch (_: StackOverflowError) {
        // Hostile nesting (thousands of brackets) must not crash the app.
        throw IllegalArgumentException("Kutubxona hujjati buzilgan: juda chuqur tuzilma.")
    }
    if (parsed["tracks"] !is List<*> || parsed["folders"] !is List<*>) {
        throw IllegalArgumentException("Kutubxona hujjatining asosiy ro'yxatlari mavjud emas.")
    }
    val schemaVersion = (parsed["schemaVersion"] as? Number)?.toInt()
    if (schemaVersion != null && schemaVersion > SCHEMA_VERSION) {
        throw IllegalArgumentException("Saqlangan kutubxona ushbu ilovadan yangiroq formatda.")
    }
    return normalizeLibrarySnapshot(parsed)
}

// ---------- native fast-path parse ----------
//
// The C++ path (NativeBridge.normalizeJson) already parsed, validated and
// normalized the document, and returns clean JSON. Parsing it through the loose
// Map<String,Any?> tree above would allocate one LinkedTreeMap + boxed Double
// per track key — hundreds of MB of churn for a 30k-track library, which is
// what pushed this app into the low-memory killer on weak phones. Instead we
// decode into a typed model so numbers stay primitive and no map nodes exist.

private data class TrackDto(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val uri: String? = null,
    val source: String? = null,
    val folderId: String? = null,
    val favorite: Boolean = false,
    val addedAt: Long = 0L,
    val albumArtUri: String? = null
)

private data class FolderDto(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,
    val createdAt: Long = 0L,
    val kind: String? = null
)

private data class SnapshotDto(
    val tracks: List<TrackDto>? = null,
    val folders: List<FolderDto>? = null,
    val currentTrackId: String? = null,
    val queueIds: List<String>? = null,
    val repeatMode: String? = null,
    val shuffle: Boolean = false,
    val volume: Float = 0.82f,
    val playbackRate: Float = 1f
)

/**
 * Parse a document already normalized by the C++ fast path. Values are coerced
 * and invalid rows skipped (never crash); the caller keeps [parseLibrarySnapshot]
 * as the strict fallback if native output is ever unexpected.
 */
fun parseNormalizedSnapshot(serialized: String): LibrarySnapshot {
    val dto: SnapshotDto = try {
        gson.fromJson(serialized, SnapshotDto::class.java)
            ?: throw IllegalArgumentException("Bo'sh hujjat")
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: com.google.gson.JsonParseException) {
        throw IllegalArgumentException("Kutubxona hujjati buzilgan: ${e.message}")
    } catch (_: StackOverflowError) {
        throw IllegalArgumentException("Kutubxona hujjati buzilgan: juda chuqur tuzilma.")
    }
    val folderIds = HashSet<String>()
    val folders = ArrayList<MusicFolder>(dto.folders?.size ?: 0)
    dto.folders?.forEach { f ->
        val id = f.id ?: return@forEach
        val name = f.name ?: return@forEach
        if (id.isBlank() || id.length > 160 || name.isBlank()) return@forEach
        if (!folderIds.add(id)) return@forEach
        folders.add(
            MusicFolder(
                id = id,
                name = name.trim().take(100),
                color = f.color?.takeIf { colorRegex.matches(it) } ?: INTERNET_FOLDER_COLOR,
                createdAt = f.createdAt,
                isInternet = f.kind == "internet"
            )
        )
    }
    val trackIds = HashSet<String>()
    val tracks = ArrayList<Track>(dto.tracks?.size ?: 0)
    dto.tracks?.forEach { t ->
        val id = t.id ?: return@forEach
        if (id.isBlank() || id.length > 160 || !trackIds.add(id)) return@forEach
        val source = when (t.source) {
            "device" -> TrackSource.DEVICE
            "url" -> TrackSource.URL
            "demo" -> TrackSource.DEMO
            else -> return@forEach
        }
        val uri = t.uri ?: return@forEach
        val title = t.title?.trim() ?: ""
        if (title.isEmpty() || uri.isEmpty() || uri.length > 4096) return@forEach
        if (source == TrackSource.DEVICE &&
            !uri.startsWith("file://") && !uri.startsWith("/") && !uri.startsWith("content://")
        ) return@forEach
        tracks.add(
            Track(
                id = id,
                title = title.take(240),
                artist = t.artist?.take(240) ?: "Noma'lum ijrochi",
                album = t.album?.take(240) ?: "",
                uri = uri,
                source = source,
                folderId = t.folderId?.takeIf { folderIds.contains(it) },
                favorite = t.favorite,
                addedAt = t.addedAt,
                artUri = t.albumArtUri?.take(4096)
                    ?.takeIf { it.startsWith("content://") || it.startsWith("file://") }
            )
        )
    }
    val validIds = tracks.mapTo(HashSet()) { it.id }
    return LibrarySnapshot(
        tracks = tracks,
        folders = folders,
        currentTrackId = dto.currentTrackId?.takeIf { validIds.contains(it) },
        queueIds = dto.queueIds?.filter { validIds.contains(it) }?.distinct() ?: emptyList(),
        repeatMode = when (dto.repeatMode) {
            "one" -> RepeatMode.ONE
            "all" -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
        shuffle = dto.shuffle,
        volume = dto.volume.coerceIn(0f, 1f),
        playbackRate = dto.playbackRate.coerceIn(0.5f, 2f)
    )
}

fun serializeLibrary(snapshot: LibrarySnapshot): String {
    val durableTracks = snapshot.tracks.filter { it.source != TrackSource.WEB }
    val durableIds = durableTracks.map { it.id }.toSet()
    val normalized = normalizeLibrarySnapshot(
        mapOf(
            "tracks" to durableTracks.map { trackToMap(it) },
            "folders" to snapshot.folders.map { folderToMap(it) },
            "currentTrackId" to snapshot.currentTrackId,
            "queueIds" to snapshot.queueIds.filter { durableIds.contains(it) },
            "repeatMode" to when (snapshot.repeatMode) {
                RepeatMode.ONE -> "one"; RepeatMode.ALL -> "all"; else -> "off"
            },
            "shuffle" to snapshot.shuffle,
            "volume" to snapshot.volume,
            "playbackRate" to snapshot.playbackRate
        )
    )
    return gson.toJson(
        mapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "tracks" to normalized.tracks.map { trackToMap(it) },
            "folders" to normalized.folders.map { folderToMap(it) },
            "currentTrackId" to normalized.currentTrackId,
            "queueIds" to normalized.queueIds,
            "repeatMode" to when (normalized.repeatMode) {
                RepeatMode.ONE -> "one"; RepeatMode.ALL -> "all"; else -> "off"
            },
            "shuffle" to normalized.shuffle,
            "volume" to normalized.volume,
            "playbackRate" to normalized.playbackRate
        )
    )
}

fun trackToMap(t: Track): Map<String, Any?> = mapOf(
    "id" to t.id,
    "title" to t.title,
    "artist" to t.artist,
    "album" to t.album,
    "uri" to t.uri,
    "source" to when (t.source) {
        TrackSource.DEVICE -> "device"; TrackSource.URL -> "url"
        TrackSource.DEMO -> "demo"; TrackSource.WEB -> "web"
    },
    "folderId" to t.folderId,
    "favorite" to t.favorite,
    "addedAt" to t.addedAt,
    "albumArtUri" to t.artUri
)

fun folderToMap(f: MusicFolder): Map<String, Any?> = mapOf(
    "id" to f.id,
    "name" to f.name,
    "color" to f.color,
    "createdAt" to f.createdAt,
    "kind" to if (f.isInternet) "internet" else null
)

fun formatTime(seconds: Long): String {
    if (seconds < 0) return "0:00"
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
