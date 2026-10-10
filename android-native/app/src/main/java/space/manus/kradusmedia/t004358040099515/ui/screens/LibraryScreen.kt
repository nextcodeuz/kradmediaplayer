package space.manus.kradusmedia.t004358040099515.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import space.manus.kradusmedia.t004358040099515.ui.components.EmptyState
import space.manus.kradusmedia.t004358040099515.ui.components.PrimaryButton
import space.manus.kradusmedia.t004358040099515.ui.components.SectionHeading
import space.manus.kradusmedia.t004358040099515.ui.components.TrackRow
import space.manus.kradusmedia.t004358040099515.ui.dialogs.TrackEditorDialog
import space.manus.kradusmedia.t004358040099515.ui.dialogs.UrlImportDialog
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of app/(tabs)/library.tsx — Kutubxona. */
@Composable
fun LibraryScreen(
    vm: MusicViewModel,
    onPickDevice: () -> Unit,
    onScanLibrary: () -> Unit,
    onManageFiles: () -> Unit
) {
    val tracks by vm.tracks.collectAsState()
    val folders by vm.folders.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val isHydrated by vm.isHydrated.collectAsState()
    val isImporting by vm.isImporting.collectAsState()
    val importError by vm.importError.collectAsState()
    val storageLoadError by vm.storageLoadError.collectAsState()
    val scanProgress by vm.scanProgress.collectAsState()
    val playbackError by vm.playbackError.collectAsState()
    val allFilesGranted by vm.allFilesGranted.collectAsState()
    // Background-filtered list (150 ms debounce, Dispatchers.Default) — typing
    // never freezes, even with tens of thousands of tracks.
    val visible by vm.visibleTracks.collectAsState()
    val search by vm.query.collectAsState()
    val folderFilter by vm.folderFilter.collectAsState()
    val favoritesOnly by vm.favoritesOnly.collectAsState()

    var urlVisible by remember { mutableStateOf(false) }
    var editorTrack by remember { mutableStateOf<Track?>(null) }
    val scope = rememberCoroutineScope()

    // One scrollable column: header, search, actions AND tracks scroll together,
    // so the track list always uses the full screen height instead of being
    // squeezed into whatever space the fixed header leaves over.
    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            BrandHeader("KUTUBXONA", right = {
                IconButton(onClick = onPickDevice, enabled = isHydrated && !isImporting) {
                    Icon(Icons.Filled.Add, "MP3 qo'shish", tint = C.text)
                }
            })
        }
        item {
            Column {
                Text("MUSIQALARINGIZ", color = C.cyan, fontSize = 10.sp)
                Text("Kutubxona", color = C.text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} ta trek · ${folders.size} ta papka", color = C.muted, fontSize = 12.sp)
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = vm::setQuery,
                placeholder = { Text("Nom, ijrochi yoki papka", color = C.muted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.muted) },
                trailingIcon = {
                    IconButton(onClick = { vm.setFavoritesOnly(!favoritesOnly) }) {
                        Icon(
                            if (favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null, tint = if (favoritesOnly) C.red else C.muted
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = C.text, unfocusedTextColor = C.text,
                    focusedBorderColor = C.line, unfocusedBorderColor = C.line
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    FilterChip("Barchasi", folderFilter == null && !favoritesOnly) {
                        vm.clearFilters()
                    }
                }
                items(folders, key = { it.id }) { folder ->
                    FilterChip(folder.name, folderFilter == folder.id) {
                        vm.setFolderFilter(folder.id)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ImportButton("Qurilmadan", Modifier.weight(1f), isHydrated && !isImporting, onPickDevice)
                ImportButton("Havoladan", Modifier.weight(1f), isHydrated) { urlVisible = true }
            ImportButton("Demo", Modifier.weight(1f), isHydrated) {
                scope.launch {
                    try {
                        val demo = vm.addDemoTrack()
                        vm.playTrack(demo)
                    } catch (e: Exception) {
                        vm.setImportError(e.message ?: "Demo trekni qo‘shib bo‘lmadi.")
                    }
                }
            }
            }
        }

        if (importError != null) {
            item {
                Text(importError!!, color = C.red, fontSize = 11.sp,
                    modifier = Modifier.clickable { vm.clearImportError() }.padding(vertical = 2.dp))
            }
        }
        if (playbackError != null) {
            item {
                Text(playbackError!!, color = C.orange, fontSize = 11.sp,
                    modifier = Modifier.clickable { vm.clearPlaybackError() }.padding(vertical = 2.dp))
            }
        }

        // Full-device scan: every song on the phone, with cover art, in one bulk import.
        item {
            PrimaryButton(
                if (scanProgress?.active == true) "Skanerlanmoqda… ${scanProgress?.done ?: 0}/${scanProgress?.total ?: 0}"
                else "Telefon musiqasini skanerlash (barchasi)",
                onScanLibrary,
                secondary = true,
                enabled = isHydrated && scanProgress?.active != true,
                loading = scanProgress?.active == true
            )
        }
        item {
            Text(
                "Ruxsat bersangiz, telefondagi barcha musiqa ablojkasi bilan qo'shiladi. " +
                    "Ruxsatsiz qo'shish uchun yuqoridagi \"Qurilmadan\" tugmasini ishlating.",
                color = C.muted, fontSize = 10.sp
            )
        }
        // Optional full file access (Android 11+): finds non-indexed MP3s via native scan.
        if (android.os.Build.VERSION.SDK_INT >= 30 && !allFilesGranted) {
            item {
                PrimaryButton(
                    "Barcha fayllarga to'liq kirish",
                    onManageFiles,
                    secondary = true,
                    enabled = isHydrated
                )
            }
            item {
                Text(
                    "Sozlamalar ochiladi — \"Barcha fayllarga kirish\" ni yoqing. " +
                        "Shunda indekslanmagan papkalardagi MP3lar ham topiladi.",
                    color = C.muted, fontSize = 10.sp
                )
            }
        }

        item {
            SectionHeading(
                if (search.isNotEmpty() || folderFilter != null || favoritesOnly) "Natijalar" else "Barcha treklar",
                "${visible.size} ta trek"
            )
            Spacer(Modifier.height(8.dp))
        }
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    when {
                        storageLoadError != null -> "Kutubxona ochilmadi"
                        !isHydrated -> "Kutubxona yuklanmoqda"
                        search.isNotEmpty() -> "Trek topilmadi"
                        favoritesOnly -> "Sevimli treklar hali yo'q"
                        else -> "Kutubxona bo'sh"
                    },
                    storageLoadError
                        ?: if (!isHydrated) "Saqlangan treklar o'qilmoqda; ma'lumotlar tayyor bo'lmaguncha import bloklangan."
                        else "Qurilmadan MP3 tanlang yoki URL manzil qo'shing. Havola qurilmaga yuklab olinmaydi; oqimli ijro internet talab qiladi."
                ) {
                    if (storageLoadError != null) {
                        PrimaryButton("Qayta urinish", vm::retryLibraryLoad)
                    } else if (search.isEmpty() && !favoritesOnly) {
                        PrimaryButton(
                            if (isHydrated) "MP3 faylni tanlash" else "Kutubxona yuklanmoqda…",
                            onPickDevice, enabled = isHydrated
                        )
                    }
                }
            }
        } else {
            items(visible, key = { it.id }) { track ->
                TrackRow(
                    track, track.id == currentId, track.id == currentId && playing,
                    { vm.playTrack(track, visible) },
                    { vm.toggleFavorite(track.id) },
                    { editorTrack = track }
                )
            }
        }
    }

    if (urlVisible) {
        UrlImportDialog({ urlVisible = false }) { url, title, artist ->
            vm.addFromUrl(url, title, artist)
        }
    }
    editorTrack?.let { track ->
        TrackEditorDialog(track, folders, { editorTrack = null }, vm::updateTrack, vm::removeTrack)
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (active) C.cyan else C.muted, fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) C.blue.copy(alpha = 0.20f) else C.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    )
}

@Composable
private fun ImportButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Text(
        if (label == "Qurilmadan" && !enabled) "Yuklanmoqda…" else label,
        color = C.text, fontSize = 10.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp)
    )
}
