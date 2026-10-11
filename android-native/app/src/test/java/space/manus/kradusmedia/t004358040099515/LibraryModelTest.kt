package space.manus.kradusmedia.t004358040099515

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import space.manus.kradusmedia.t004358040099515.music.INTERNET_FOLDER_NAME
import space.manus.kradusmedia.t004358040099515.music.normalizeLibrarySnapshot
import space.manus.kradusmedia.t004358040099515.music.parseLibrarySnapshot
import space.manus.kradusmedia.t004358040099515.music.trackToMap

/**
 * Kotlin port of lib/music/model.test.ts — same 7 cases, same expectations.
 * Guarantees the native port keeps every persistence/migration rule.
 */
class LibraryModelTest {

    private fun oldUrlTrack(id: String = "url-1", extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "id" to id,
            "title" to "Sample",
            "artist" to "Artist",
            "album" to "",
            "uri" to "https://audio.example/song.mp3",
            "source" to "url",
            "folderId" to null,
            "favorite" to true,
            "addedAt" to 123
        ) + extra

    @Test
    fun migratesOldUnfiledUrlTracksIntoOnePersistentInternetFolder() {
        val first = normalizeLibrarySnapshot(
            mapOf("tracks" to listOf(oldUrlTrack()), "folders" to emptyList<Any>(),
                "queueIds" to listOf("url-1"), "currentTrackId" to "url-1")
        )
        val internet = first.folders.filter { it.isInternet }
        assertEquals(1, internet.size)
        assertEquals(INTERNET_FOLDER_NAME, internet[0].name)
        assertEquals(internet[0].id, first.tracks[0].folderId)
        assertEquals(listOf("url-1"), first.queueIds)
        assertEquals("url-1", first.currentTrackId)

        val second = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to first.tracks.map { trackToMap(it) },
                "folders" to first.folders.map {
                    mapOf("id" to it.id, "name" to it.name, "color" to it.color,
                        "createdAt" to it.createdAt, "kind" to if (it.isInternet) "internet" else null)
                }
            )
        )
        assertEquals(1, second.folders.filter { it.isInternet }.size)
        assertEquals(internet[0].id, second.tracks[0].folderId)
    }

    @Test
    fun upgradesLegacyCurlyApostropheInternetFolderByNameWithoutDuplicating() {
        // Pre-fix regression: C++ used U+2019 while Kotlin used U+0027, so a
        // name-matched legacy folder was never found and a duplicate spawned.
        val legacyName = "Internetdan qo\u2019shilganlar"
        val snapshot = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to listOf(oldUrlTrack(extra = mapOf("folderId" to "f-internet"))),
                "folders" to listOf(
                    mapOf("id" to "f-internet", "name" to legacyName, "color" to "#4D6784",
                        "createdAt" to 1, "kind" to null)
                )
            )
        )
        val internet = snapshot.folders.filter { it.isInternet }
        assertEquals(1, internet.size)
        assertEquals("f-internet", internet[0].id)
        assertEquals("f-internet", snapshot.tracks[0].folderId)
    }

    @Test
    fun preservesUrlTrackMovedToNormalFolderAndKeepsDefaultFolder() {
        val snapshot = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to listOf(oldUrlTrack(extra = mapOf("folderId" to "folder-user"))),
                "folders" to listOf(
                    mapOf("id" to "folder-user", "name" to "Sokin kechalar",
                        "color" to "#53697A", "createdAt" to 100)
                )
            )
        )
        assertEquals("folder-user", snapshot.tracks[0].folderId)
        assertEquals(1, snapshot.folders.filter { it.isInternet }.size)
    }

    @Test
    fun rejectsCredentialAndNonHttpUrlsAndFakeDeviceFiles() {
        val snapshot = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to listOf(
                    oldUrlTrack(),
                    oldUrlTrack("url-credentials", mapOf("uri" to "https://user:secret@audio.example/song.mp3")),
                    oldUrlTrack("url-script", mapOf("uri" to "javascript:alert(1)")),
                    oldUrlTrack("not-a-device-file", mapOf("source" to "device", "uri" to "https://audio.example/song.mp3"))
                )
            )
        )
        assertEquals(listOf("url-1"), snapshot.tracks.map { it.id })
    }

    @Test
    fun clampsPlaybackPreferencesAndRemovesStaleQueueRefs() {
        val snapshot = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to listOf(oldUrlTrack()),
                "currentTrackId" to "missing",
                "queueIds" to listOf("url-1", "missing", "url-1"),
                "volume" to 3,
                "playbackRate" to 0.1
            )
        )
        assertNull(snapshot.currentTrackId)
        assertEquals(listOf("url-1"), snapshot.queueIds)
        assertEquals(1f, snapshot.volume)
        assertEquals(0.5f, snapshot.playbackRate)
    }

    @Test
    fun parsesValidLegacySnapshotAndPreservesUrlMetadata() {
        val json = """{"tracks":[{"id":"url-1","title":"Sample","artist":"Artist","album":"","uri":"https://audio.example/song.mp3","source":"url","folderId":null,"favorite":true,"addedAt":123}],"folders":[],"queueIds":["url-1"]}"""
        val snapshot = parseLibrarySnapshot(json)
        assertEquals("https://audio.example/song.mp3", snapshot.tracks[0].uri)
        assertEquals(snapshot.folders[0].id, snapshot.tracks[0].folderId)
        assertTrue(snapshot.folders[0].isInternet)
    }

    @Test
    fun rejectsCorruptJsonAndMissingArrays() {
        try {
            parseLibrarySnapshot("{broken")
            fail("expected throw")
        } catch (_: Exception) {
        }
        try {
            parseLibrarySnapshot("""{"tracks":"bad","folders":[]}""")
            fail("expected throw")
        } catch (_: Exception) {
        }
        try {
            parseLibrarySnapshot("""{"tracks":[],"folders":null}""")
            fail("expected throw")
        } catch (_: Exception) {
        }
    }

    @Test
    fun refusesNewerSchemaVersion() {
        try {
            parseLibrarySnapshot("""{"schemaVersion":3,"tracks":[],"folders":[]}""")
            fail("expected throw")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("yangiroq formatda") == true)
        }
    }

    @Test
    fun acceptsScannedContentDeviceTracksWithAlbumArt() {
        val snapshot = normalizeLibrarySnapshot(
            mapOf(
                "tracks" to listOf(
                    mapOf(
                        "id" to "lib-1", "title" to "Sevimli qo'shiq", "artist" to "Xonanda",
                        "album" to "Albom", "uri" to "content://media/external/audio/media/42",
                        "source" to "device", "folderId" to null, "favorite" to false,
                        "addedAt" to 5,
                        "albumArtUri" to "content://media/external/audio/albumart/7"
                    ),
                    // Remote URL masquerading as a device file is still rejected.
                    mapOf(
                        "id" to "lib-fake", "title" to "Fake", "artist" to "X",
                        "album" to "", "uri" to "https://audio.example/song.mp3",
                        "source" to "device", "folderId" to null, "favorite" to false,
                        "addedAt" to 6
                    )
                ),
                "folders" to emptyList<Any>()
            )
        )
        assertEquals(1, snapshot.tracks.size)
        assertEquals("content://media/external/audio/media/42", snapshot.tracks[0].uri)
        assertEquals("content://media/external/audio/albumart/7", snapshot.tracks[0].artUri)
    }

    @Test
    fun serializeRoundTripKeepsAlbumArt() {
        val original = space.manus.kradusmedia.t004358040099515.music.LibrarySnapshot(
            tracks = listOf(
                space.manus.kradusmedia.t004358040099515.music.Track(
                    id = "lib-9", title = "Art", artist = "A", album = "",
                    uri = "content://media/external/audio/media/9",
                    source = space.manus.kradusmedia.t004358040099515.music.TrackSource.DEVICE,
                    artUri = "content://media/external/audio/albumart/3"
                )
            )
        )
        val restored = parseLibrarySnapshot(
            space.manus.kradusmedia.t004358040099515.music.serializeLibrary(original)
        )
        assertEquals(1, restored.tracks.size)
        assertEquals("content://media/external/audio/albumart/3", restored.tracks[0].artUri)
    }
}
