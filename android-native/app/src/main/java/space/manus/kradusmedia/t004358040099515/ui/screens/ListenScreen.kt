package space.manus.kradusmedia.t004358040099515.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import space.manus.kradusmedia.t004358040099515.music.MusicViewModel
import space.manus.kradusmedia.t004358040099515.music.Track
import space.manus.kradusmedia.t004358040099515.ui.components.BrandHeader
import space.manus.kradusmedia.t004358040099515.ui.components.CoverArt
import space.manus.kradusmedia.t004358040099515.ui.components.EmptyState
import space.manus.kradusmedia.t004358040099515.ui.components.KradusSlider
import space.manus.kradusmedia.t004358040099515.ui.components.PrimaryButton
import space.manus.kradusmedia.t004358040099515.ui.components.ProgressBar
import space.manus.kradusmedia.t004358040099515.ui.components.SectionHeading
import space.manus.kradusmedia.t004358040099515.ui.components.TrackRow
import space.manus.kradusmedia.t004358040099515.ui.dialogs.AboutDialog
import space.manus.kradusmedia.t004358040099515.ui.dialogs.TrackEditorDialog
import space.manus.kradusmedia.t004358040099515.ui.dialogs.UrlImportDialog
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of app/(tabs)/index.tsx — Tinglash. */
@Composable
fun ListenScreen(
    vm: MusicViewModel,
    onOpenPlayer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPickDevice: () -> Unit,
    onScanLibrary: () -> Unit
) {
    val tracks by vm.tracks.collectAsState()
    val folders by vm.folders.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val buffering by vm.buffering.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val volume by vm.volume.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val isHydrated by vm.isHydrated.collectAsState()
    val isImporting by vm.isImporting.collectAsState()
    val importError by vm.importError.collectAsState()
    val storageLoadError by vm.storageLoadError.collectAsState()
    val scanProgress by vm.scanProgress.collectAsState()
    val playbackError by vm.playbackError.collectAsState()

    var urlVisible by remember { mutableStateOf(false) }
    var editorTrack by remember { mutableStateOf<Track?>(null) }
    var aboutVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val current = tracks.firstOrNull { it.id == currentId }
    val recent = remember(tracks) { tracks.sortedByDescending { it.addedAt }.take(3) }
    val favoriteCount = remember(tracks) { tracks.count { it.favorite } }
    val folderCount = remember(tracks) { tracks.mapNotNull { it.folderId }.toSet().size }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandHeader("MP3 PLEYER", right = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.surface)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    storageLoadError != null -> C.red
                                    !isHydrated -> C.orange
                                    else -> C.green
                                }
                            )
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            storageLoadError != null -> "XOTIRA"
                            isHydrated -> "TAYYOR"
                            else -> "YUKLANMOQDA"
                        },
                        color = C.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { aboutVisible = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Info, "Ilova haqida", tint = C.muted, modifier = Modifier.size(19.dp))
                }
            }
        })

        Column {
            Text("SIZNING MUSIQANGIZ", color = C.cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("Musiqa\npleeri", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp)
            Text("Oddiy boshqaruv. Tartibli kutubxona.", color = C.muted, fontSize = 13.sp)
        }

        // Player card
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(C.surface)
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        playing -> "HOZIR IJRODA"
                        current != null -> "NAVBATDAGI TREK"
                        else -> "KRADUS PLAYER"
                    },
                    color = C.muted, fontSize = 10.sp
                )
                Text(if (current != null) "${positionMs / 1000} soniya" else "MP3", color = C.muted, fontSize = 10.sp)
            }
            Box(Modifier.fillMaxWidth().clickable(enabled = current != null, onClick = onOpenPlayer)) {
                CoverArt(current, 240.dp, Modifier.align(Alignment.Center))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current?.title ?: "Trek tanlanmagan", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(current?.artist ?: "Kutubxonangizdan musiqa tanlang", color = C.muted, fontSize = 12.sp, maxLines = 1)
                }
                current?.let {
                    IconButton(onClick = { vm.toggleFavorite(it.id) }) {
                        Icon(
                            if (it.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null, tint = if (it.favorite) C.red else C.muted
                        )
                    }
                }
            }
            ProgressBar(positionMs / 1000, durationMs / 1000, { vm.seekTo(it) }, isHydrated)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::playPrevious) {
                    Icon(Icons.Filled.SkipPrevious, "Oldingi trek", tint = C.muted, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.size(24.dp))
                IconButton(
                    onClick = { if (current != null) vm.togglePlayback() else onOpenLibrary() },
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(C.blue)
                ) {
                    if (buffering && current != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            if (current != null && playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.size(24.dp))
                IconButton(onClick = vm::playNext) {
                    Icon(Icons.Filled.SkipNext, "Keyingi trek", tint = C.muted, modifier = Modifier.size(30.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when (repeatMode.name) {
                        "ONE" -> "Bitta trek takrorlanadi"
                        "ALL" -> "Navbat takrorlanadi"
                        else -> "Takrorlash o'chiq"
                    },
                    color = C.muted, fontSize = 10.sp
                )
                Text(if (shuffle) "Aralash ijro" else "Tartibli ijro", color = C.muted, fontSize = 10.sp)
            }
        }

        // Volume card
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(C.surface).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = C.cyan, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(8.dp))
                Text("Ovoz balandligi", color = C.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${(volume * 100).toInt()}%", color = C.cyan, fontSize = 11.sp)
            }
            KradusSlider(volume, 1f, vm::setVolume, isHydrated)
        }

        storageLoadError?.let {
            Text(it, color = C.red, fontSize = 11.sp)
            PrimaryButton("Qayta urinish", vm::retryLibraryLoad)
        }
        importError?.let {
            Text(it, color = C.red, fontSize = 11.sp,
                modifier = Modifier.clickable { vm.clearImportError() }.padding(vertical = 2.dp))
        }
        playbackError?.let {
            Text(it, color = C.orange, fontSize = 11.sp,
                modifier = Modifier.clickable { vm.clearPlaybackError() }.padding(vertical = 2.dp))
        }

        if (tracks.isEmpty()) {
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(C.surface).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Headset, null, tint = C.cyan, modifier = Modifier.size(28.dp))
                Text(
                    when {
                        storageLoadError != null -> "Kutubxona ochilmadi"
                        isHydrated -> "Musiqa qo'shing"
                        else -> "Kutubxona yuklanmoqda"
                    },
                    color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "Tanlangan MP3 Kradus ilovasining yopiq xotirasiga nusxalanadi. Havolalar esa faqat manzil sifatida saqlanadi va ijro uchun internet kerak bo'ladi.",
                    color = C.muted, fontSize = 12.sp
                )
                PrimaryButton("Qurilmadan MP3 tanlash", onPickDevice, enabled = isHydrated, loading = isImporting)
                PrimaryButton("Havola orqali qo'shish", { urlVisible = true }, secondary = true, enabled = isHydrated)
                PrimaryButton(
                    if (scanProgress?.active == true) "Skanerlanmoqda… ${scanProgress?.done ?: 0}/${scanProgress?.total ?: 0}"
                    else "Telefondagi barcha musiqani qo'shish",
                    onScanLibrary, secondary = true,
                    enabled = isHydrated && scanProgress?.active != true,
                    loading = scanProgress?.active == true
                )
                Text(
                    "50 soniyalik demo trekni sinash", color = C.cyan, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = isHydrated) {
                        scope.launch {
                            if (!isHydrated) return@launch
                            try {
                                val demo = vm.addDemoTrack()
                                vm.playTrack(demo)
                            } catch (e: Exception) {
                                vm.setImportError(e.message ?: "Demo trekni qo‘shib bo‘lmadi.")
                            }
                        }
                    }.padding(8.dp)
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("${tracks.size}", "TREK", Modifier.weight(1f))
            StatCard("$folderCount", "PAPKA", Modifier.weight(1f))
            StatCard("$favoriteCount", "SEVIMLI", Modifier.weight(1f))
        }

        SectionHeading(
            "Yaqinda qo'shilgan",
            if (tracks.isNotEmpty()) "${tracks.size} ta trek kutubxonada" else "Kutubxona bo'sh",
            if (tracks.isNotEmpty()) "Barchasi" else null,
            if (tracks.isNotEmpty()) onOpenLibrary else null
        )
        if (recent.isNotEmpty()) {
            Column {
                recent.forEach { track ->
                    TrackRow(
                        track, track.id == currentId, track.id == currentId && playing,
                        { vm.playTrack(track, tracks) },
                        { vm.toggleFavorite(track.id) },
                        { editorTrack = track }
                    )
                }
            }
        } else {
            Text("Mahalliy MP3 ilovaning yopiq xotirasida qoladi.", color = C.muted, fontSize = 11.sp)
        }
    }

    if (urlVisible) {
        UrlImportDialog(
            onClose = { urlVisible = false },
            // Throws surface inside the dialog itself, so the user always
            // sees why a link was rejected instead of a silent close.
            onImport = { url, title, artist -> vm.addFromUrl(url, title, artist) }
        )
    }
    editorTrack?.let { track ->
        TrackEditorDialog(track, folders, { editorTrack = null }, vm::updateTrack, vm::removeTrack)
    }
    if (aboutVisible) {
        AboutDialog { aboutVisible = false }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.surface).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.muted, fontSize = 9.sp)
    }
}
