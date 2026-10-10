package space.manus.kradusmedia.t004358040099515.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.manus.kradusmedia.t004358040099515.ui.theme.C

/** Port of the FolderModal in app/(tabs)/folders.tsx. */
@Composable
fun FolderEditDialog(
    initialName: String,
    isEdit: Boolean,
    onClose: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.surface,
        title = {
            Text(
                if (isEdit) "Papka nomini o'zgartirish" else "Yangi papka",
                color = C.text, fontSize = 18.sp
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Papka nomini tanlang.", color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    placeholder = { Text("Masalan: Sokin kechalar", color = C.muted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = C.text, unfocusedTextColor = C.text,
                        focusedBorderColor = C.blue, unfocusedBorderColor = C.line,
                        cursorColor = C.cyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name); onClose() }, enabled = name.trim().isNotEmpty()) {
                Text("Saqlash", color = C.cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Bekor qilish", color = C.muted) }
        }
    )
}

/** Port of the ConfirmDeleteModal in app/(tabs)/folders.tsx. */
@Composable
fun FolderDeleteDialog(name: String, onClose: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.surface,
        title = { Text("Papkani o'chirish?", color = C.text, fontSize = 18.sp) },
        text = {
            Text(
                "\"$name\" o'chadi. Ichidagi treklar kutubxonada qoladi va papkasiz holatga o'tadi.",
                color = C.muted, fontSize = 12.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { onDelete(); onClose() }) { Text("O'chirish", color = C.red) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Qoldirish", color = C.muted) }
        }
    )
}
