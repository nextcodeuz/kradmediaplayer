package space.manus.kradusmedia.t004358040099515.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.manus.kradusmedia.t004358040099515.R
import space.manus.kradusmedia.t004358040099515.ui.theme.C

const val APP_VERSION = "1.0.0"
const val AUTHOR_NAME = "@machine_dev"
private const val AUTHOR_URL = "https://t.me/machine_dev"

/** Ilova haqida: versiya, muallif va maxfiylik va'dasi. */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.kradus_logo),
                    contentDescription = "Kradus",
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("KRADUS MEDIA", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("MP3 pleyer · $APP_VERSION-versiya", color = C.muted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column {
                Text(
                    "Oddiy boshqaruv. Tartibli kutubxona. " +
                        "Barcha musiqa va sozlamalar faqat sizning qurilmangizda qoladi — " +
                        "hech qayerpa yuklanmaydi va kuzatilmaydi.",
                    color = C.muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dasturchi: ", color = C.muted, fontSize = 12.sp)
                    Text(
                        AUTHOR_NAME, color = C.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(AUTHOR_URL)
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }.padding(vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Yopish", color = C.cyan) }
        }
    )
}
