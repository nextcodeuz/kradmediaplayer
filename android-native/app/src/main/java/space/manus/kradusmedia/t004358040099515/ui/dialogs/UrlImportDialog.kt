package space.manus.kradusmedia.t004358040099515.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/**
 * Port of components/music/UrlImportModal.tsx — same validation messages,
 * same help copy, same 4096/240 limits.
 */
@Composable
fun UrlImportDialog(onClose: () -> Unit, onImport: (url: String, title: String, artist: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun submit() {
        try {
            val trimmed = url.trim()
            val parsed = java.net.URI(trimmed)
            val scheme = parsed.scheme?.lowercase()
            if (scheme != "https" && scheme != "http")
                throw IllegalArgumentException("Havola http:// yoki https:// bilan boshlansin.")
            if (parsed.host.isNullOrEmpty()) throw IllegalArgumentException("MP3 manzilini tekshiring.")
            if (parsed.userInfo != null)
                throw IllegalArgumentException("Login yoki parol saqlaydigan havola qo'shilmaydi.")
            if (trimmed.length > 4096) throw IllegalArgumentException("Havola juda uzun.")
            onImport(trimmed, title.trim(), artist.trim())
            onClose()
        } catch (e: Exception) {
            error = (e as? IllegalArgumentException)?.message ?: "Havolani tekshirib qayta kiriting."
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.surface,
        title = {
            Column {
                Text("MANBA QO'SHISH", color = C.cyan, fontSize = 10.sp)
                Text("MP3 havolasi", color = C.text, fontSize = 20.sp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Trek \"Internetdan qo'shilganlar\" papkasida saqlanadi. Fayl avtomatik yuklab olinmaydi — ijro internet talab qiladi. Login, parol yoki shaxsiy tokeni bor havolani kiritmang.",
                    color = C.muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                UrlField("MP3 URL", url, { url = it }, "https://sayt.uz/musiqa.mp3", 4096)
                Spacer(Modifier.height(8.dp))
                UrlField("Trek nomi (ixtiyoriy)", title, { title = it }, "Havoladan olinadi", 240)
                Spacer(Modifier.height(8.dp))
                UrlField("Ijrochi (ixtiyoriy)", artist, { artist = it }, "Internet manbasi", 240)
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = C.red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit) { Text("Kutubxonaga saqlash", color = C.cyan) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Yopish", color = C.muted) }
        }
    )
}

@Composable
private fun UrlField(label: String, value: String, onChange: (String) -> Unit, placeholder: String, max: Int) {
    Column {
        Text(label, color = C.text, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= max) onChange(it) },
            placeholder = { Text(placeholder, color = C.muted, fontSize = 13.sp) },
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
