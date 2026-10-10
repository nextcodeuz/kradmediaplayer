package space.manus.kradusmedia.t004358040099515.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.manus.kradusmedia.t004358040099515.music.MusicViewModel
import space.manus.kradusmedia.t004358040099515.music.RepeatMode
import space.manus.kradusmedia.t004358040099515.music.TrackSource
import space.manus.kradusmedia.t004358040099515.ui.components.CoverArt
import space.manus.kradusmedia.t004358040099515.ui.components.EmptyState
import space.manus.kradusmedia.t004358040099515.ui.components.KradusSlider
import space.manus.kradusmedia.t004358040099515.ui.components.PrimaryButton
import space.manus.kradusmedia.t004358040099515.ui.components.ProgressBar
import space.manus.kradusmedia.t004358040099515.ui.theme.C

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

/** Port of app/player.tsx — full-screen player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(vm: MusicViewModel, onClose: () -> Unit, onOpenLibrary: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val buffering by vm.buffering.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val volume by vm.volume.collectAsState()
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val rate by vm.playbackRate.collectAsState()
    val isHydrated by vm.isHydrated.collectAsState()
    val storageLoadError by vm.storageLoadError.collectAsState()
    val playbackError by vm.playbackError.collectAsState()

    val current = tracks.firstOrNull { it.id == currentId }

    Column(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(C.surface2)
            ) {
                Icon(Icons.Filled.Close, "Pleyerni yopish", tint = C.text)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KRADUS MEDIA", color = C.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("HOZIR IJRODA", color = C.cyan, fontSize = 9.sp)
            }
            current?.let {
                IconButton(onClick = { vm.toggleFavorite(it.id) }) {
                    Icon(
                        if (it.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null, tint = if (it.favorite) C.red else C.text
                    )
                }
            } ?: Spacer(Modifier.size(42.dp))
        }

        if (current == null) {
            EmptyState(
                if (storageLoadError != null) "Kutubxona ochilmadi"
                else if (isHydrated) "Ijro uchun trek tanlang" else "Kutubxona yuklanmoqda",
                storageLoadError
                    ?: if (isHydrated) "Kutubxonadan MP3 qo'shing yoki mavjud trekni tanlang."
                    else "Saqlangan ma'lumotlar o'qilmoqda; hozircha import bloklangan."
            ) {
                if (storageLoadError != null) PrimaryButton("Qayta urinish", vm::retryLibraryLoad)
                else PrimaryButton("Kutubxonani ochish", onOpenLibrary, enabled = isHydrated)
            }
            return
        }

        CoverArt(current, 280.dp, Modifier.align(Alignment.CenterHorizontally))
        playbackError?.let {
            Text(it, color = C.orange, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.clearPlaybackError() })
        }
        Column {
            Text(
                when (current.source) {
                    TrackSource.URL -> "Internet havolasi"
                    TrackSource.DEMO -> "Demo trek"
                    else -> "Qurilmadagi MP3"
                },
                color = C.cyan, fontSize = 10.sp
            )
            Text(current.title, color = C.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                current.artist.ifBlank { "Noma'lum ijrochi" } + if (current.album.isNotBlank()) " · ${current.album}" else "",
                color = C.muted, fontSize = 12.sp, maxLines = 1
            )
        }

        ProgressBar(positionMs / 1000, durationMs / 1000, { vm.seekTo(it) }, isHydrated)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) {
                Icon(Icons.Filled.Shuffle, "Aralash ijro", tint = if (shuffle) C.cyan else C.muted)
            }
            IconButton(onClick = vm::playPrevious) {
                Icon(Icons.Filled.SkipPrevious, "Oldingi trek", tint = C.text, modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = vm::togglePlayback,
                modifier = Modifier.size(58.dp).clip(CircleShape).background(C.blue)
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null, tint = Color.White, modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = vm::playNext) {
                Icon(Icons.Filled.SkipNext, "Keyingi trek", tint = C.text, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = {
                vm.setRepeatMode(
                    when (repeatMode) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                )
            }) {
                Icon(Icons.Filled.Repeat, "Takrorlash", tint = if (repeatMode != RepeatMode.OFF) C.cyan else C.muted)
            }
        }

        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(C.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OVOZ", color = C.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("${(volume * 100).toInt()}%", color = C.text, fontSize = 11.sp)
            }
            KradusSlider(volume, 1f, vm::setVolume, isHydrated)
            Text("TEZLIK", color = C.muted, fontSize = 9.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SPEEDS.forEach { speed ->
                    val active = rate == speed
                    Text(
                        "${speed}×", color = if (active) Color.White else C.muted, fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (active) C.blue else C.surface2)
                            .clickable { vm.setPlaybackRate(speed) }
                            .padding(horizontal = 9.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Text(
            when {
                buffering -> "Yuklanmoqda"
                playing -> "Ijroda"
                else -> when (current.source) {
                    TrackSource.URL -> "Internet havolasi"
                    TrackSource.DEMO -> "Demo trek"
                    else -> "Qurilmadagi MP3"
                }
            } + " · Bildirishnoma fon boshqaruvi uchun; rad etilsa ham ijro davom etadi",
            color = C.muted, fontSize = 9.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
