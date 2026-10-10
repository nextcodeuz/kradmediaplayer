package space.manus.kradusmedia.t004358040099515

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.manus.kradusmedia.t004358040099515.music.TrackSource
import space.manus.kradusmedia.t004358040099515.music.parseNormalizedSnapshot

/**
 * Covers the typed fast path used for output of NativeBridge.normalizeJson.
 * The C++ side already normalized the document, so this parser must map it
 * exactly, coerce bad values instead of throwing, and keep full Unicode.
 */
class NativeSnapshotParseTest {

    private val normalized = """
        {"schemaVersion":2,
         "tracks":[
           {"id":"lib-1","title":"Sevimli qo'shiq 🎵","artist":"Xonanda","album":"Albom",
            "uri":"content://media/external/audio/media/42","source":"device","folderId":null,
            "favorite":true,"addedAt":1712345678901,"albumArtUri":"content://media/external/audio/albumart/7"},
           {"id":"url-1","title":"Online","artist":"Radio","album":"",
            "uri":"https://audio.example/song.mp3","source":"url","folderId":"folder-internet-sources",
            "favorite":false,"addedAt":1712345678000}
         ],
         "folders":[{"id":"folder-internet-sources","name":"Internetdan qo'shilganlar",
            "color":"#4D6784","createdAt":1,"kind":"internet"}],
         "currentTrackId":"lib-1","queueIds":["lib-1","url-1","lib-1","ghost"],
         "repeatMode":"all","shuffle":true,"volume":0.5,"playbackRate":1.5}
    """.trimIndent()

    @Test
    fun mapsNormalizedDocumentExactly() {
        val s = parseNormalizedSnapshot(normalized)
        assertEquals(2, s.tracks.size)
        assertEquals(1, s.folders.size)
        assertTrue(s.folders[0].isInternet)
        assertEquals("folder-internet-sources", s.tracks[1].folderId)
        assertEquals("lib-1", s.currentTrackId)
        // stale + duplicate queue refs removed, order preserved
        assertEquals(listOf("lib-1", "url-1"), s.queueIds)
        assertEquals(0.5f, s.volume)
        assertEquals(1.5f, s.playbackRate)
        assertEquals(TrackSource.URL, s.tracks[1].source)
        assertEquals("content://media/external/audio/albumart/7", s.tracks[0].artUri)
    }

    @Test
    fun preservesSupplementaryUnicodeInTitles() {
        val s = parseNormalizedSnapshot(normalized)
        assertEquals("Sevimli qo'shiq 🎵", s.tracks[0].title)
    }

    @Test
    fun coercesOutOfRangeNumbersAndDropsForeignFolders() {
        val json = """
            {"tracks":[{"id":"t","title":"T","uri":"https://a.example/x.mp3","source":"url",
              "folderId":"nope","addedAt":5}],
             "folders":[],"volume":9,"playbackRate":0.1}
        """.trimIndent()
        val s = parseNormalizedSnapshot(json)
        assertNull(s.tracks[0].folderId)
        assertEquals(1f, s.volume)
        assertEquals(0.5f, s.playbackRate)
    }

    @Test
    fun skipsInvalidSourceAndFakeDeviceRows() {
        val json = """
            {"tracks":[
              {"id":"web-1","title":"W","uri":"https://a.example/x.mp3","source":"web"},
              {"id":"fake","title":"F","uri":"https://a.example/x.mp3","source":"device"},
              {"id":"bad","title":"","uri":"https://a.example/x.mp3","source":"url"}
            ],"folders":[]}
        """.trimIndent()
        val s = parseNormalizedSnapshot(json)
        assertEquals(0, s.tracks.size)
    }

    @Test
    fun emptyDocumentYieldsEmptySnapshot() {
        val s = parseNormalizedSnapshot("{}")
        assertEquals(0, s.tracks.size)
        assertEquals(0, s.folders.size)
        assertNull(s.currentTrackId)
    }
}
