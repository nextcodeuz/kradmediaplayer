package space.manus.kradusmedia.t004358040099515.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.manus.kradusmedia.t004358040099515.music.MusicViewModel
import space.manus.kradusmedia.t004358040099515.music.Track
import space.manus.kradusmedia.t004358040099515.ui.components.BrandHeader
import space.manus.kradusmedia.t004358040099515.ui.components.EmptyState
import space.manus.kradusmedia.t004358040099515.ui.components.PrimaryButton
import space.manus.kradusmedia.t004358040099515.ui.components.SectionHeading
import space.manus.kradusmedia.t004358040099515.ui.components.TrackRow
import space.manus.kradusmedia.t004358040099515.ui.dialogs.TrackEditorDialog
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of app/(tabs)/favorites.tsx — Sevimlilar. */
@Composable
fun FavoritesScreen(vm: MusicViewModel, onOpenLibrary: () -> Unit) {
    val tracks by vm.tracks.collectAsState()
    val folders by vm.folders.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val isHydrated by vm.isHydrated.collectAsState()
    val storageLoadError by vm.storageLoadError.collectAsState()

    var editorTrack by remember { mutableStateOf<Track?>(null) }
    val favorites = remember(tracks) {
        tracks.filter { it.favorite }.sortedByDescending { it.addedAt }
    }

    Column(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BrandHeader("SHAXSIY TANLOV", right = {
            IconButton(onClick = onOpenLibrary) {
                Icon(Icons.Filled.MusicNote, "Kutubxonani ochish", tint = C.text)
            }
        })

        if (favorites.isEmpty()) {
            EmptyState(
                when {
                    storageLoadError != null -> "Kutubxona ochilmadi"
                    !isHydrated -> "Kutubxona yuklanmoqda"
                    else -> "Hali sevimli trek yo'q"
                },
                storageLoadError
                    ?: if (!isHydrated) "Saqlangan sevimlilar o'qilmoqda; ular tayyor bo'lmaguncha o'zgartirilmaydi."
                    else "Kutubxonadagi yurak belgisini bosing — tanlagan treklaringiz shu sahifada qoladi."
            ) {
                if (storageLoadError != null) PrimaryButton("Qayta urinish", vm::retryLibraryLoad)
                else PrimaryButton("Kutubxonaga o'tish", onOpenLibrary)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SAQLANGAN TREKLAR", color = C.cyan, fontSize = 10.sp)
                        Text("Sevimlilar", color = C.text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("${favorites.size} ta trek", color = C.muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Favorite, null, tint = C.red, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shaxsiy to'plam", color = C.text, fontSize = 13.sp)
                            Text("Yurak belgisi bilan ajratgan musiqalaringiz.", color = C.muted, fontSize = 11.sp)
                        }
                        Text(favorites.size.toString().padStart(2, '0'), color = C.muted, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    SectionHeading("Saqlangan treklar", "${favorites.size} ta trek")
                    Spacer(Modifier.height(8.dp))
                }
                items(favorites, key = { it.id }) { track ->
                    TrackRow(
                        track, track.id == currentId, track.id == currentId && playing,
                        { vm.playTrack(track, favorites) },
                        { vm.toggleFavorite(track.id) },
                        { editorTrack = track }
                    )
                }
            }
        }
    }

    editorTrack?.let { track ->
        TrackEditorDialog(track, folders, { editorTrack = null }, vm::updateTrack, vm::removeTrack)
    }
}
