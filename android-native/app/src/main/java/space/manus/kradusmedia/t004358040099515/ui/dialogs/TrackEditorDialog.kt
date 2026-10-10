package space.manus.kradusmedia.t004358040099515.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import space.manus.kradusmedia.t004358040099515.music.MusicFolder
import space.manus.kradusmedia.t004358040099515.music.Track
import space.manus.kradusmedia.t004358040099515.music.TrackPatch
import space.manus.kradusmedia.t004358040099515.music.TrackSource
import space.manus.kradusmedia.t004358040099515.ui.components.CoverArt
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of components/music/TrackEditorModal.tsx. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackEditorDialog(
    track: Track,
    folders: List<MusicFolder>,
    onClose: () -> Unit,
    onSave: (id: String, patch: TrackPatch) -> Unit,
    onRemove: (id: String) -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var folderId by remember(track.id) { mutableStateOf(track.folderId) }
    var confirmRemove by remember(track.id) { mutableStateOf(false) }

    fun save() {
        if (title.trim().isEmpty()) return
        onSave(
            track.id,
            TrackPatch(
                title = title.trim().take(240),
                artist = artist.trim().take(240).ifEmpty { "Noma'lum ijrochi" },
                album = album.trim().take(240),
                folderId = folderId,
                clearFolder = folderId == null && track.source != TrackSource.URL
            )
        )
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.surface,
        title = {
            Column {
                Text("TREK MA'LUMOTI", color = C.cyan, fontSize = 10.sp)
                Text("Tahrirlash", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(track, 48.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (track.source) {
                                TrackSource.URL -> "Internet havolasi"
                                TrackSource.DEMO -> "Namoyish treki"
                                else -> "Qurilmadagi MP3"
                            },
                            color = C.muted, fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                EditorField("Trek nomi", title, { title = it })
                Spacer(Modifier.height(8.dp))
                EditorField("Ijrochi", artist, { artist = it })
                Spacer(Modifier.height(8.dp))
                EditorField("Albom", album, { album = it })
                Spacer(Modifier.height(10.dp))
                Text("Papka", color = C.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (track.source != TrackSource.URL) {
                        FolderChip("Papkasiz", folderId == null) { folderId = null }
                    }
                    folders.forEach { folder ->
                        FolderChip(folder.name, folderId == folder.id) { folderId = folder.id }
                    }
                }
                if (track.source == TrackSource.URL) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Havolali trek papkada turadi; boshqa papkaga ko'chirishingiz mumkin.",
                        color = C.muted, fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (confirmRemove) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(C.bg)
                            .padding(11.dp)
                    ) {
                        Text("Trekni o'chirish?", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (track.source == TrackSource.DEVICE)
                                "Trek va Kradus ichida saqlangan nusxa o'chadi. Bu amalni bekor qilib bo'lmaydi."
                            else "Trek kutubxonadan va saqlangan navbatdan o'chadi.",
                            color = C.muted, fontSize = 11.sp
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { confirmRemove = false }) {
                                Text("Bekor", color = C.muted)
                            }
                            TextButton(onClick = { onRemove(track.id); onClose() }) {
                                Text("Trekni o'chirish", color = C.red)
                            }
                        }
                    }
                } else {
                    Text(
                        "Trekni kutubxonadan o'chirish",
                        color = C.red, fontSize = 12.sp,
                        modifier = Modifier.clickable { confirmRemove = true }.padding(6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = title.trim().isNotEmpty()) {
                Text("Saqlash", color = C.cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Yopish", color = C.muted) }
        }
    )
}

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = C.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 240) onChange(it) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = C.text, unfocusedTextColor = C.text,
                focusedBorderColor = C.blue, unfocusedBorderColor = C.line,
                cursorColor = C.cyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (selected) C.cyan else C.muted, fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) C.blue.copy(alpha = 0.18f) else C.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
