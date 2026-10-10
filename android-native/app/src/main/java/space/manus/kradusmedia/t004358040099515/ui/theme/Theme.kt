package space.manus.kradusmedia.t004358040099515.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Restrained charcoal palette — port of components/music/ui.tsx `C`. */
object C {
    val bg = Color(0xFF14171B)
    val surface = Color(0xFF1D2227)
    val surface2 = Color(0xFF272D33)
    val line = Color(0xFF363D43)
    val text = Color(0xFFF0EEE9)
    val muted = Color(0xFFA7ADB2)
    val blue = Color(0xFF386AA3)
    val cyan = Color(0xFFA2B4C4)
    val purple = Color(0xFF928A9D)
    val green = Color(0xFF849786)
    val orange = Color(0xFFBA9868)
    val red = Color(0xFFBB7778)
}

private val KradusScheme = darkColorScheme(
    primary = C.blue,
    onPrimary = Color.White,
    background = C.bg,
    onBackground = C.text,
    surface = C.surface,
    onSurface = C.text,
    surfaceVariant = C.surface2,
    onSurfaceVariant = C.muted,
    outline = C.line,
    error = C.red
)

@Composable
fun KradusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KradusScheme, content = content)
}

/** Flat album-sleeve placeholder colors (no gradients / glow). */
val ART_COLORS = listOf(
    Color(0xFF3B5063), Color(0xFF514A42), Color(0xFF40544B),
    Color(0xFF4B4554), Color(0xFF45545B), Color(0xFF5C4B3F)
)

fun colorFromText(value: String): Color {
    var hash = 0
    for (ch in value) hash = hash * 31 + ch.code
    return ART_COLORS[kotlin.math.abs(hash) % ART_COLORS.size]
}
