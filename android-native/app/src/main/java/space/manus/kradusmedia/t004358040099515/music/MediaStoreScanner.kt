package space.manus.kradusmedia.t004358040099515.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-device music scan via MediaStore (no broad file access, no recursion).
 * Returns EVERYTHING flagged IS_MUSIC, newest first, with album-art uris —
 * plus an absolute-path index built in the SAME cursor pass, so the native
 * dir scanner can reuse content:// uris without a second full-device query.
 * Runs on Dispatchers.IO; single indexed query (fast even for 10k+ tracks).
 */
data class ScannedAudio(
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val artUri: String?,
    val dateAdded: Long
)

data class MediaScan(
    val items: List<ScannedAudio>,
    /** Absolute path -> content uri for every indexed audio file. */
    val pathIndex: Map<String, String>
)

suspend fun scanAllAudio(context: Context): MediaScan = withContext(Dispatchers.IO) {
    val out = ArrayList<ScannedAudio>(512)
    val pathIndex = HashMap<String, String>(4096)
    val collection: Uri = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATE_ADDED,
        // Raw "_data" name instead of the deprecated DATA constant: no
        // deprecation warning, and a missing column is just -1 (skipped).
        "_data"
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val iData = c.getColumnIndex("_data")
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")
        while (c.moveToNext()) {
            val id = c.getLong(iId)
            val uri = ContentUris.withAppendedId(collection, id).toString()
            if (iData >= 0) {
                try {
                    c.getString(iData)?.let { pathIndex[it] = uri }
                } catch (_: Exception) {
                }
            }
            val albumId = try {
                c.getLong(iAlbumId)
            } catch (_: Exception) {
                -1L
            }
            out.add(
                ScannedAudio(
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Nomsiz trek",
                    artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Noma'lum ijrochi",
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "",
                    uri = uri,
                    artUri = if (albumId > 0)
                        ContentUris.withAppendedId(albumArtBase, albumId).toString()
                    else null,
                    dateAdded = try {
                        c.getLong(iDate)
                    } catch (_: Exception) {
                        0L
                    }
                )
            )
        }
    }
    MediaScan(out, pathIndex)
}
