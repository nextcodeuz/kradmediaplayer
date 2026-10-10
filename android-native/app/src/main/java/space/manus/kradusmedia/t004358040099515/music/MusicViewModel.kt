package space.manus.kradusmedia.t004358040099515.music

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Kotlin port of lib/music/MusicProvider.tsx.
 *
 * Single source of truth for tracks / folders / queue / playback.
 * - 1-second position polling (mirrors expo-audio updateInterval: 1000)
 * - persist via LibraryStore debounce + serial queue
 * - notification permission asked at most once, only after user starts playback
 */
class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryStore(app)
    private val player = PlayerHolder.get(app)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId.asStateFlow()

    private val _queueIds = MutableStateFlow<List<String>>(emptyList())
    val queueIds: StateFlow<List<String>> = _queueIds.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _volume = MutableStateFlow(0.82f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playbackRate = MutableStateFlow(1f)
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    private val _isHydrated = MutableStateFlow(false)
    val isHydrated: StateFlow<Boolean> = _isHydrated.asStateFlow()

    private val _storageLoadError = MutableStateFlow<String?>(null)
    val storageLoadError: StateFlow<String?> = _storageLoadError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    // 1 Hz playback status (mirrors useAudioPlayerStatus @ 1000 ms).
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** One-shot: UI should request POST_NOTIFICATIONS (Android 13+) now. */
    private val _askNotification = MutableStateFlow(false)
    val askNotification: StateFlow<Boolean> = _askNotification.asStateFlow()
    private var notificationAsked = false

    /** Bulk-scan progress state for the Library screen. */
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    /** All-Files-Access granted (set by MainActivity; SDK 30+). */
    private val _allFilesGranted = MutableStateFlow(false)
    val allFilesGranted: StateFlow<Boolean> = _allFilesGranted.asStateFlow()

    fun setAllFilesGranted(granted: Boolean) {
        _allFilesGranted.value = granted
    }

    // ---------- background search/filter (never blocks UI, even at 30k tracks) ----------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _folderFilter = MutableStateFlow<String?>(null)
    val folderFilter: StateFlow<String?> = _folderFilter.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFolderFilter(id: String?) {
        _folderFilter.value = id
        if (id != null) _favoritesOnly.value = false
    }

    fun setFavoritesOnly(only: Boolean) {
        _favoritesOnly.value = only
        if (only) _folderFilter.value = null
    }

    fun clearFilters() {
        _folderFilter.value = null
        _favoritesOnly.value = false
    }

    @OptIn(FlowPreview::class)
    val visibleTracks: StateFlow<List<Track>> =
        combine(
            _tracks,
            _folders,
            _query.debounce(150),
            _folderFilter,
            _favoritesOnly
        ) { tracks, folders, q, folderId, favOnly ->
            filterTracks(tracks, folders, q, folderId, favOnly)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun filterTracks(
        tracks: List<Track>,
        folders: List<MusicFolder>,
        q: String,
        folderId: String?,
        favOnly: Boolean
    ): List<Track> {
        val needle = q.trim().lowercase()
        val folderNames = if (needle.isNotEmpty()) folders.associate { it.id to it.name } else emptyMap()
        return tracks
            .filter { !favOnly || it.favorite }
            .filter { folderId == null || it.folderId == folderId }
            .filter {
                if (needle.isEmpty()) true
                else ("${it.title} ${it.artist} ${it.album} " +
                    (it.folderId?.let { id -> folderNames[id] } ?: "")).lowercase().contains(needle)
            }
            .sortedByDescending { it.addedAt }
    }

    /** Last playback failure (bad file, lost permission, dead stream). Auto-skips. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var importLock = false
    private var loadedTrackId: String? = null
    private var pollJob: Job? = null
    private var loadAttempt = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            _buffering.value = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED && _repeatMode.value != RepeatMode.ONE) {
                playNext()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Never get stuck on a broken file: report once, skip forward.
            val title = currentTrack()?.title ?: "trek"
            _playbackError.value =
                "\"$title\" ni ijro etib bo'lmadi — keyingi trekka o'tkazildi."
            val queue = _queueIds.value.ifEmpty { _tracks.value.map { it.id } }
            if (queue.size > 1) {
                playNext()
            } else {
                try {
                    player.pause()
                } catch (_: Exception) {
                }
                // Nothing playable left: drop the foreground service too, so no
                // stale notification lingers. Next play restarts it.
                shutdownService()
            }
        }
    }

    init {
        player.addListener(listener)
        player.volume = _volume.value
        hydrate()
        startPolling()
    }

    // ---------- hydration ----------

    fun hydrate() {
        val requestId = ++loadAttempt
        _isHydrated.value = false
        _storageLoadError.value = null
        viewModelScope.launch {
            try {
                store.purgeLegacyTraces()
                val saved = store.loadLibrary()
                if (requestId != loadAttempt) return@launch
                _tracks.value = saved.tracks
                _folders.value = saved.folders
                _currentTrackId.value = saved.currentTrackId
                _queueIds.value = saved.queueIds
                _repeatMode.value = saved.repeatMode
                _shuffle.value = saved.shuffle
                _volume.value = saved.volume
                _playbackRate.value = saved.playbackRate
                player.volume = saved.volume
                player.repeatMode = if (saved.repeatMode == RepeatMode.ONE)
                    Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                PlayerHolder.applyRate(player, saved.playbackRate)
                // Preload current track paused (mirrors provider replace+pause effect).
                saved.currentTrackId?.let { id ->
                    saved.tracks.firstOrNull { it.id == id }?.let { track ->
                        loadedTrackId = track.id
                        PlayerHolder.loadTrack(player, track)
                        player.pause()
                    }
                }
                _isHydrated.value = true
            } catch (_: Exception) {
                if (requestId != loadAttempt) return@launch
                _storageLoadError.value =
                    "Saqlangan kutubxona o'qilmadi. Xotiradagi ma'lumotlar ustidan yozilmadi; qayta urining."
            }
        }
    }

    fun retryLibraryLoad() = hydrate()

    private fun snapshot() = LibrarySnapshot(
        tracks = _tracks.value,
        folders = _folders.value,
        currentTrackId = _currentTrackId.value,
        queueIds = _queueIds.value,
        repeatMode = _repeatMode.value,
        shuffle = _shuffle.value,
        volume = _volume.value,
        playbackRate = _playbackRate.value
    )

    private fun persist() {
        if (!_isHydrated.value) return
        store.scheduleSave(snapshot())
    }

    /** Call from Activity.onStop so the last state is flushed like AppState background. */
    fun flushOnBackground() = store.flush()

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                try {
                    _positionMs.value = player.currentPosition.coerceAtLeast(0)
                    _durationMs.value = player.duration.coerceAtLeast(0)
                    _playing.value = player.isPlaying
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---------- playback ----------

    fun currentTrack(): Track? =
        _currentTrackId.value?.let { id -> _tracks.value.firstOrNull { it.id == id } }

    private fun requestPlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || notificationAsked) return
        notificationAsked = true
        _askNotification.value = true
    }

    fun notificationAskConsumed() {
        _askNotification.value = false
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        if (!_isHydrated.value) return
        val ids = (if (!queue.isNullOrEmpty()) queue else _tracks.value).map { it.id }
        _queueIds.value = if (ids.contains(track.id)) ids else listOf(track.id) + ids
        _currentTrackId.value = track.id
        loadedTrackId = track.id
        PlayerHolder.loadTrack(player, track)
        player.volume = _volume.value
        player.repeatMode = if (_repeatMode.value == RepeatMode.ONE)
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        PlayerHolder.applyRate(player, _playbackRate.value)
        ensureService()
        requestPlaybackNotificationPermission()
        player.play()
        persist()
    }

    fun togglePlayback() {
        val current = currentTrack() ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE ||
                player.playbackState == Player.STATE_ENDED ||
                loadedTrackId != current.id
            ) {
                loadedTrackId = current.id
                PlayerHolder.loadTrack(player, current)
            }
            ensureService()
            requestPlaybackNotificationPermission()
            player.play()
        }
    }

    fun playNext() {
        val queue = _queueIds.value.ifEmpty { _tracks.value.map { it.id } }
        if (queue.isEmpty()) return
        val byId = _tracks.value.associateBy { it.id }
        val currentId = _currentTrackId.value
        if (_shuffle.value && queue.size > 1) {
            val options = queue.filter { it != currentId }
            val next = byId[options.random()] ?: return
            playTrack(next, queue.mapNotNull { byId[it] })
            return
        }
        var nextIndex = queue.indexOf(currentId) + 1
        if (nextIndex >= queue.size) {
            if (_repeatMode.value != RepeatMode.ALL) return
            nextIndex = 0
        }
        byId[queue[nextIndex]]?.let { playTrack(it, queue.mapNotNull { id -> byId[id] }) }
    }

    fun playPrevious() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        val queue = _queueIds.value.ifEmpty { _tracks.value.map { it.id } }
        if (queue.isEmpty()) return
        val byId = _tracks.value.associateBy { it.id }
        var prevIndex = queue.indexOf(_currentTrackId.value) - 1
        if (prevIndex < 0) {
            if (_repeatMode.value != RepeatMode.ALL) {
                player.seekTo(0)
                return
            }
            prevIndex = queue.size - 1
        }
        byId[queue[prevIndex]]?.let { playTrack(it, queue.mapNotNull { id -> byId[id] }) }
    }

    fun seekTo(seconds: Long) {
        if (!_isHydrated.value) return
        player.seekTo((seconds * 1000).coerceAtLeast(0))
    }

    fun setVolume(v: Float) {
        if (!_isHydrated.value) return
        val safe = v.coerceIn(0f, 1f)
        _volume.value = safe
        player.volume = safe
        persist()
    }

    fun setRepeatMode(mode: RepeatMode) {
        if (!_isHydrated.value) return
        _repeatMode.value = mode
        player.repeatMode = if (mode == RepeatMode.ONE)
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        persist()
    }

    fun toggleShuffle() {
        if (!_isHydrated.value) return
        _shuffle.update { !it }
        persist()
    }

    fun setPlaybackRate(rate: Float) {
        if (!_isHydrated.value) return
        val safe = rate.coerceIn(0.5f, 2f)
        _playbackRate.value = safe
        PlayerHolder.applyRate(player, safe)
        persist()
    }

    private fun ensureService() {
        // Keep playback alive in background via the media session service.
        try {
            val ctx = getApplication<Application>()
            val intent = android.content.Intent(ctx, KradusPlayerService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        } catch (_: Exception) {
        }
    }

    /** Stop the player service when nothing is playable (no stale notification). */
    private fun shutdownService() {
        try {
            val ctx = getApplication<Application>()
            ctx.stopService(android.content.Intent(ctx, KradusPlayerService::class.java))
        } catch (_: Exception) {
        }
    }

    // ---------- library mutations ----------

    fun clearImportError() {
        _importError.value = null
    }

    fun setImportError(message: String) {
        _importError.value = message
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    /**
     * Bulk import of a MediaStore scan: ONE state update (no per-track renders),
     * uri-deduped, order-preserving. Runs on Dispatchers.IO from the Activity.
     * Returns the number of newly added tracks.
     */
    suspend fun importFromLibrary(items: List<ScannedAudio>): Int {
        if (!_isHydrated.value) return 0
        _importError.value = null
        _playbackError.value = null
        _scanProgress.value = ScanProgress(active = true, done = 0, total = items.size)
        try {
            if (items.isEmpty()) {
                _importError.value = "Telefonda musiqa topilmadi."
                return 0
            }
            val existing = _tracks.value.map { it.uri }.toSet()
            // C++ hash-set dedupe for huge scans; Kotlin path otherwise (identical result).
            val fresh = dedupeFresh(items, existing)
            if (fresh.isEmpty()) {
                _importError.value = "Barcha treklar allaqachon kutubxonada."
                return 0
            }
            val now = System.currentTimeMillis()
            val added = ArrayList<Track>(fresh.size)
            fresh.forEachIndexed { index, s ->
                added.add(
                    Track(
                        id = makeId("lib"),
                        title = s.title.take(240),
                        artist = s.artist.take(240),
                        album = s.album.take(240),
                        uri = s.uri,
                        source = TrackSource.DEVICE,
                        folderId = null,
                        favorite = false,
                        // Keep scan order (DATE_ADDED DESC) stable under recent-sort.
                        addedAt = now - index,
                        artUri = s.artUri
                    )
                )
                if (index % 100 == 0) {
                    _scanProgress.value = ScanProgress(active = true, done = index, total = fresh.size)
                }
            }
            // Single atomic update: one recomposition + one debounced disk write.
            _tracks.value = added + _tracks.value
            if (_currentTrackId.value == null) _currentTrackId.value = added[0].id
            _queueIds.value = added.map { it.id } + _queueIds.value
            _scanProgress.value = ScanProgress(active = true, done = fresh.size, total = fresh.size)
            persist()
            return added.size
        } finally {
            _scanProgress.value = null
        }
    }

    /**
     * Port of importFromDevice: sequential copy of user-picked MP3s into the
     * app-private directory. Returns the number of imported tracks.
     */
    suspend fun importFromDevice(picks: List<Pair<Uri, Pair<String, String?>>>): Int {
        if (!_isHydrated.value || importLock) return 0
        importLock = true
        _importError.value = null
        _isImporting.value = true
        try {
            val nameOk = { name: String, mime: String? ->
                name.lowercase().endsWith(".mp3") ||
                    mime == "audio/mpeg" || mime == "audio/mp3"
            }
            val mp3 = picks.filter { (_, meta) -> nameOk(meta.first, meta.second) }
            if (mp3.isEmpty()) {
                _importError.value = "Tanlangan fayllar orasida MP3 topilmadi. Boshqa faylni tanlang."
                return 0
            }
            val imported = mutableListOf<Track>()
            var failed = 0
            for ((uri, meta) in mp3) {
                val id = makeId("track")
                val privateUri = store.copyIntoPrivateStorage(uri, meta.first, id)
                if (privateUri == null) {
                    failed++
                    continue
                }
                imported.add(
                    Track(
                        id = id,
                        title = fileTitle(meta.first),
                        artist = "Noma'lum ijrochi",
                        album = "",
                        uri = privateUri,
                        source = TrackSource.DEVICE,
                        folderId = null,
                        favorite = false,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
            if (imported.isEmpty()) {
                _importError.value = "Tanlangan MP3 fayllarini ilova xotirasiga nusxalab bo'lmadi."
                return 0
            }
            _tracks.value = imported + _tracks.value
            if (_currentTrackId.value == null) _currentTrackId.value = imported[0].id
            _queueIds.value = imported.map { it.id } + _queueIds.value
            if (failed > 0) _importError.value =
                "${imported.size} ta trek qo'shildi; $failed ta fayl nusxalanmadi."
            persist()
            return imported.size
        } finally {
            importLock = false
            _isImporting.value = false
        }
    }

    private fun ensureInternetFolder(): MusicFolder {
        val current = _folders.value
        val existing = current.firstOrNull { it.isInternet }
            ?: current.firstOrNull { it.name.lowercase() == INTERNET_FOLDER_NAME.lowercase() }
        if (existing != null) {
            if (!existing.isInternet) {
                val upgraded = existing.copy(isInternet = true)
                _folders.value = current.map { if (it.id == existing.id) upgraded else it }
                persist()
                return upgraded
            }
            return existing
        }
        val folder = MusicFolder(
            id = makeId("folder-internet"),
            name = INTERNET_FOLDER_NAME,
            color = FOLDER_COLORS[current.size % FOLDER_COLORS.size],
            createdAt = System.currentTimeMillis(),
            isInternet = true
        )
        _folders.value = current + folder
        persist()
        return folder
    }

    /** Port of addFromUrl (throws IllegalArgumentException with Uzbek messages). */
    fun addFromUrl(url: String, title: String = "", artist: String = "", folderId: String? = null): Track {
        if (!_isHydrated.value) throw IllegalArgumentException("Kutubxona yuklanmoqda. Bir oz kutib qayta urinib ko'ring.")
        val cleaned = url.trim()
        if (cleaned.isEmpty() || cleaned.length > 4096)
            throw IllegalArgumentException("MP3 havolasi bo'sh yoki juda uzun.")
        val parsed = try {
            java.net.URI(cleaned)
        } catch (_: Exception) {
            throw IllegalArgumentException("MP3 havolasi to'g'ri kiritilmagan.")
        }
        val scheme = parsed.scheme?.lowercase()
        if ((scheme != "https" && scheme != "http") || parsed.host.isNullOrEmpty())
            throw IllegalArgumentException("Havola http:// yoki https:// bilan boshlanishi kerak.")
        if (parsed.userInfo != null)
            throw IllegalArgumentException("Login yoki parol saqlaydigan havola qo'shilmaydi.")
        var fallback = parsed.path?.split("/")?.filter { it.isNotEmpty() }?.lastOrNull() ?: ""
        fallback = try {
            java.net.URLDecoder.decode(fallback, "UTF-8")
        } catch (_: Exception) {
            fallback
        }.replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "").replace(Regex("[-_]+"), " ")
        val destination = folderId?.let { id -> _folders.value.firstOrNull { it.id == id } }
            ?: ensureInternetFolder()
        val track = Track(
            id = makeId("url"),
            title = (title.trim().ifEmpty { fallback.ifEmpty { parsed.host } }).take(240),
            artist = artist.trim().ifEmpty { "Internet manbasi" }.take(240),
            album = "",
            uri = cleaned,
            source = TrackSource.URL,
            folderId = destination.id,
            favorite = false,
            addedAt = System.currentTimeMillis()
        )
        _tracks.value = listOf(track) + _tracks.value
        _queueIds.value = listOf(track.id) + _queueIds.value
        if (_currentTrackId.value == null) _currentTrackId.value = track.id
        persist()
        return track
    }

    fun addDemoTrack(): Track {
        if (!_isHydrated.value) throw IllegalStateException("Kutubxona yuklanmoqda.")
        _tracks.value.firstOrNull { it.source == TrackSource.DEMO }?.let { return it }
        val track = Track(
            id = makeId("demo"),
            title = "Kradus demo — 50 soniya",
            artist = "SampleLib",
            album = "Namuna",
            uri = DEMO_URL,
            source = TrackSource.DEMO,
            folderId = null,
            favorite = false,
            addedAt = System.currentTimeMillis()
        )
        _tracks.value = listOf(track) + _tracks.value
        _queueIds.value = listOf(track.id) + _queueIds.value
        persist()
        return track
    }

    fun createFolder(name: String): MusicFolder {
        if (!_isHydrated.value) throw IllegalStateException("Saqlangan papkalar avval yuklanishi kerak.")
        val cleaned = name.trim()
        if (cleaned.isEmpty()) throw IllegalArgumentException("Papka nomini kiriting.")
        val folder = MusicFolder(
            id = makeId("folder"),
            name = cleaned.take(100),
            color = FOLDER_COLORS[_folders.value.size % FOLDER_COLORS.size],
            createdAt = System.currentTimeMillis()
        )
        _folders.value = _folders.value + folder
        persist()
        return folder
    }

    fun renameFolder(id: String, name: String) {
        if (!_isHydrated.value) return
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        if (_folders.value.any { it.id == id && it.isInternet }) return
        _folders.value = _folders.value.map {
            if (it.id == id) it.copy(name = cleaned.take(100)) else it
        }
        persist()
    }

    fun deleteFolder(id: String) {
        if (!_isHydrated.value) return
        if (_folders.value.any { it.id == id && it.isInternet }) return
        val remaining = _folders.value.filter { it.id != id }
        val internetId = remaining.firstOrNull { it.isInternet }?.id
        _folders.value = remaining
        _tracks.value = _tracks.value.map { track ->
            if (track.folderId != id) track
            else track.copy(folderId = if (track.source == TrackSource.URL) internetId else null)
        }
        persist()
    }

    fun updateTrack(id: String, patch: TrackPatch) {
        if (!_isHydrated.value) return
        _tracks.value = _tracks.value.map { track ->
            if (track.id != id) track
            else track.copy(
                title = patch.title ?: track.title,
                artist = patch.artist ?: track.artist,
                album = patch.album ?: track.album,
                folderId = if (patch.clearFolder) null else (patch.folderId ?: track.folderId)
            )
        }
        persist()
    }

    fun removeTrack(id: String) {
        if (!_isHydrated.value) return
        val removed = _tracks.value.firstOrNull { it.id == id } ?: return
        if (_currentTrackId.value == id) {
            player.pause()
            player.clearMediaItems()
            loadedTrackId = null
            _currentTrackId.value = null
            shutdownService()
        }
        _tracks.value = _tracks.value.filter { it.id != id }
        _queueIds.value = _queueIds.value.filter { it != id }
        // Only our own private copies need file cleanup; shared MediaStore
        // tracks (content://) and streams must never raise a scary error here.
        if (removed.source == TrackSource.DEVICE && store.isManagedAudioFile(removed.uri)) {
            viewModelScope.launch {
                val ok = store.deleteManagedAudioFile(removed.uri)
                if (!ok) _importError.value =
                    "Trek ro'yxatdan o'chirildi, lekin uning mahalliy faylini tozalab bo'lmadi."
            }
        }
        persist()
    }

    fun toggleFavorite(id: String) {
        if (!_isHydrated.value) return
        _tracks.value = _tracks.value.map {
            if (it.id == id) it.copy(favorite = !it.favorite) else it
        }
        persist()
    }

    override fun onCleared() {
        player.removeListener(listener)
        pollJob?.cancel()
        store.flush()
        super.onCleared()
    }

    private fun dedupeFresh(items: List<ScannedAudio>, existing: Set<String>): List<ScannedAudio> {
        if (NativeBridge.available && items.size > 2000) {
            try {
                val indices = NativeBridge.mergeDedupeIndices(
                    existing.toTypedArray(),
                    items.map { it.uri }.toTypedArray()
                )
                if (indices != null) return indices.map { items[it] }
            } catch (_: Throwable) {
                // Fall through to Kotlin.
            }
        }
        return items.filter { it.uri !in existing }
    }
}

/** Bulk-scan progress state for the Library screen. */
data class ScanProgress(val active: Boolean, val done: Int = 0, val total: Int = 0)
