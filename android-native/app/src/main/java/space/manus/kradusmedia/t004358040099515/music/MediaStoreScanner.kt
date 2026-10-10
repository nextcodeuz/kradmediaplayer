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
 * Returns EVERYTHING flagged IS_MUSIC, newest first, with album-art uris.
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

suspend fun scanAllAudio(context: Context): List<ScannedAudio> = withContext(Dispatchers.IO) {
    val out = ArrayList<ScannedAudio>(512)
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
        MediaStore.Audio.Media.DATE_ADDED
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
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")
        while (c.moveToNext()) {
            val id = c.getLong(iId)
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
                    uri = ContentUris.withAppendedId(collection, id).toString(),
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
    out
}
