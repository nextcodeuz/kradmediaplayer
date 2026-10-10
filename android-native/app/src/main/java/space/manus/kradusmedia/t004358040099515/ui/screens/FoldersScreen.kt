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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import space.manus.kradusmedia.t004358040099515.music.MusicFolder
import space.manus.kradusmedia.t004358040099515.music.MusicViewModel
import space.manus.kradusmedia.t004358040099515.music.Track
import space.manus.kradusmedia.t004358040099515.ui.components.BrandHeader
import space.manus.kradusmedia.t004358040099515.ui.components.EmptyState
import space.manus.kradusmedia.t004358040099515.ui.components.PrimaryButton
import space.manus.kradusmedia.t004358040099515.ui.components.SectionHeading
import space.manus.kradusmedia.t004358040099515.ui.components.TrackRow
import space.manus.kradusmedia.t004358040099515.ui.dialogs.FolderDeleteDialog
import space.manus.kradusmedia.t004358040099515.ui.dialogs.FolderEditDialog
import space.manus.kradusmedia.t004358040099515.ui.dialogs.TrackEditorDialog
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of app/(tabs)/folders.tsx — Papkalar. */
@Composable
fun FoldersScreen(vm: MusicViewModel) {
    val tracks by vm.tracks.collectAsState()
    val folders by vm.folders.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val isHydrated by vm.isHydrated.collectAsState()
    val storageLoadError by vm.storageLoadError.collectAsState()

    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var editingFolder by remember { mutableStateOf<MusicFolder?>(null) }
    var createVisible by remember { mutableStateOf(false) }
    var deleteFolderId by remember { mutableStateOf<String?>(null) }
    var editorTrack by remember { mutableStateOf<Track?>(null) }

    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
    val folderTracks = remember(selectedFolder, tracks) {
        selectedFolder?.let { f -> tracks.filter { it.folderId == f.id } } ?: emptyList()
    }
    val folderCounts = remember(tracks) {
        tracks.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
    }
    val unfiled = remember(tracks) { tracks.count { it.folderId == null } }
    val deletingFolder = folders.firstOrNull { it.id == deleteFolderId }

    // One scrollable column here too: headers and folder/track lists scroll
    // together so nothing is squeezed into a leftover strip.
    LazyColumn(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            BrandHeader("MUSIQA PAPKALARI", right = {
                if (selectedFolder != null) {
                    IconButton(onClick = { selectedFolderId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Papkalar ro'yxatiga qaytish", tint = C.text)
                    }
                } else {
                    IconButton(onClick = { if (isHydrated) createVisible = true }, enabled = isHydrated) {
                        Icon(Icons.Filled.Add, "Yangi papka", tint = C.text)
                    }
                }
            })
        }

        if (storageLoadError != null) {
            item {
                Text(storageLoadError!!, color = C.red, fontSize = 11.sp)
            }
        }

        if (selectedFolder != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderIcon(selectedFolder, 48.dp)
                    Spacer(Modifier.size(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedFolder.isInternet) "URL HAVOLALARI" else "PAPKA",
                            color = C.cyan, fontSize = 10.sp
                        )
                        Text(selectedFolder.name, color = C.text, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${folderTracks.size} ta trek", color = C.muted, fontSize = 12.sp)
                    }
                    if (!selectedFolder.isInternet) {
                        IconButton(onClick = { editingFolder = selectedFolder }) {
                            Icon(Icons.Filled.Edit, "Papkani tahrirlash", tint = C.text)
                        }
                        IconButton(onClick = { deleteFolderId = selectedFolder.id }) {
                            Icon(Icons.Filled.Delete, "Papkani o'chirish", tint = C.red)
                        }
                    }
                }
            }
            item { SectionHeading("Papkadagi treklar", "${folderTracks.size} ta trek") }
            if (folderTracks.isEmpty()) {
                item {
                    EmptyState("Papka bo'sh", "Kutubxonadagi trekni tahrir qilib shu papkaga ko'chiring.")
                }
            } else {
                items(folderTracks, key = { it.id }) { track ->
                    TrackRow(
                        track, track.id == currentId, track.id == currentId && playing,
                        { vm.playTrack(track, folderTracks) },
                        { vm.toggleFavorite(track.id) },
                        { editorTrack = track }
                    )
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("MUSIQANI TARTIBLASH", color = C.cyan, fontSize = 10.sp)
                    Text("Papkalar", color = C.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("${folders.size} ta papkada · ${tracks.size} ta trek", color = C.muted, fontSize = 12.sp)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.surface).padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shaxsiy kutubxonangizdagi papkalar", color = C.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(
                        "+ Yangi", color = C.cyan, fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = isHydrated) { createVisible = true }.padding(6.dp)
                    )
                }
            }
            if (folders.isEmpty()) {
                item {
                    EmptyState(
                        when {
                            storageLoadError != null -> "Kutubxona ochilmadi"
                            !isHydrated -> "Kutubxona yuklanmoqda"
                            else -> "Hali papka yo'q"
                        },
                        storageLoadError
                            ?: if (!isHydrated) "Saqlangan papkalar yuklanmoqda; import va tahrir hozircha bloklangan."
                            else "Yangi papka yarating yoki MP3 manzil qo'shing. URL musiqalari uchun alohida papka avtomatik yaratiladi."
                    ) {
                        if (storageLoadError != null) PrimaryButton("Qayta urinish", vm::retryLibraryLoad)
                        else PrimaryButton("Birinchi papkani yaratish", { createVisible = true }, enabled = isHydrated)
                    }
                }
            } else {
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder, folderCounts[folder.id] ?: 0,
                        onOpen = { selectedFolderId = folder.id },
                        onEdit = { editingFolder = folder }
                    )
                }
                if (unfiled > 0) {
                    item {
                        Text(
                            "$unfiled ta trek papkasiz. Trek ma'lumotlaridan papka tanlang.",
                            color = C.muted, fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (createVisible || editingFolder != null) {
        FolderEditDialog(
            initialName = editingFolder?.name ?: "",
            isEdit = editingFolder != null,
            onClose = { createVisible = false; editingFolder = null },
            onSave = { name ->
                try {
                    if (editingFolder != null) vm.renameFolder(editingFolder!!.id, name)
                    else vm.createFolder(name)
                } catch (_: Exception) {
                }
            }
        )
    }
    deletingFolder?.let { folder ->
        FolderDeleteDialog(folder.name, { deleteFolderId = null }) {
            vm.deleteFolder(folder.id)
            if (selectedFolderId == folder.id) selectedFolderId = null
        }
    }
    editorTrack?.let { track ->
        TrackEditorDialog(track, folders, { editorTrack = null }, vm::updateTrack, vm::removeTrack)
    }
}

@Composable
private fun FolderIcon(folder: MusicFolder, size: androidx.compose.ui.unit.Dp) {
    val base = try {
        Color(folder.color.toColorInt())
    } catch (_: Exception) {
        C.blue
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(base.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (folder.isInternet) Icons.Filled.Language else Icons.Filled.Folder,
            null, tint = base, modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
private fun FolderCard(folder: MusicFolder, count: Int, onOpen: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderIcon(folder, 42.dp)
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "$count ta trek" + if (folder.isInternet) " · havolalar shu yerga tushadi" else "",
                color = C.muted, fontSize = 10.sp
            )
        }
        if (!folder.isInternet) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, null, tint = C.muted)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = C.muted, modifier = Modifier.size(17.dp))
    }
    Spacer(Modifier.height(0.dp))
}
