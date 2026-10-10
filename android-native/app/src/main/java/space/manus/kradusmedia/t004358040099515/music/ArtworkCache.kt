package space.manus.kradusmedia.t004358040099515.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.io.InputStream

/**
 * Cover-art (ablojka) loader with memory LRU cache + sampled decoding.
 *
 * Priority: MediaStore albumart uri (fast) -> embedded MP3 picture
 * (MediaMetadataRetriever) -> null (UI falls back to the letter tile).
 * Streaming URL tracks are NEVER pre-downloaded (spec: no surprise storage use).
 *
 * All IO must run on Dispatchers.IO (blocking decode).
 */
class ArtworkCache private constructor(private val appContext: Context) {

    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)

    fun peek(track: Track, reqSize: Int): Bitmap? = cache.get(keyOf(track, reqSize))

    fun load(track: Track, reqSize: Int): Bitmap? {
        val key = keyOf(track, reqSize)
        cache.get(key)?.let { return it }
        val bmp = try {
            when {
                track.artUri != null -> decodeStreamUri(Uri.parse(track.artUri), reqSize)
                track.uri.startsWith("content://") || track.uri.startsWith("file://") ->
                    extractEmbedded(Uri.parse(track.uri), reqSize)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeStreamUri(uri: Uri, reqSize: Int): Bitmap? {
        fun open(): InputStream? = try {
            appContext.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSize)
        }
        return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun extractEmbedded(uri: Uri, reqSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSize)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun sampleSize(w: Int, h: Int, req: Int): Int {
        if (w <= 0 || h <= 0 || req <= 0) return 1
        var s = 1
        while (w / (s * 2) >= req && h / (s * 2) >= req) s *= 2
        return s
    }

    companion object {
        // Bounded: a 512px hero bitmap is 1 MB; 48 mixed entries keep the worst
        // case far below what would push a weak phone into the low-memory killer.
        private const val MAX_ENTRIES = 48

        @Volatile
        private var instance: ArtworkCache? = null

        fun get(context: Context): ArtworkCache = instance ?: synchronized(this) {
            instance ?: ArtworkCache(context.applicationContext).also { instance = it }
        }

        fun keyOf(track: Track, reqSize: Int): String =
            "${track.artUri ?: track.uri}|$reqSize"
    }
}
