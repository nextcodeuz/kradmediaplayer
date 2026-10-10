package space.manus.kradusmedia.t004358040099515.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.manus.kradusmedia.t004358040099515.R
import space.manus.kradusmedia.t004358040099515.music.ArtworkCache
import space.manus.kradusmedia.t004358040099515.music.Track
import space.manus.kradusmedia.t004358040099515.music.formatTime
import space.manus.kradusmedia.t004358040099515.ui.theme.C
import space.manus.kradusmedia.t004358040099515.ui.theme.colorFromText

@Composable
fun BrandHeader(subtitle: String, modifier: Modifier = Modifier, right: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.kradus_logo),
            contentDescription = "Kradus",
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("KRADUS MEDIA", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = C.muted, fontSize = 10.sp)
        }
        right?.invoke()
    }
}

@Composable
fun CoverArt(track: Track?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Two decode buckets: thumbnails vs hero art (smaller bitmaps = less memory).
    val reqSize = if (size <= 64.dp) 128 else 512
    var bitmap by remember(track?.uri, track?.artUri) {
        mutableStateOf<Bitmap?>(
            if (track != null) ArtworkCache.get(context).peek(track, reqSize) else null
        )
    }
    LaunchedEffect(track?.uri, track?.artUri) {
        if (track != null && bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                ArtworkCache.get(context).load(track, reqSize)
            }
        }
    }
    val initial = track?.title?.trim()?.firstOrNull()?.uppercase() ?: "K"
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(colorFromText(track?.title ?: "Kradus Media")),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(initial, color = Color(0xFFF0EEE9), fontSize = (size.value * 0.43f).sp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .width(size * 0.3f)
                    .height(2.dp)
                    .background(C.cyan)
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    isPlaying: Boolean,
    onPress: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPress)
            .background(if (active) C.blue.copy(alpha = 0.13f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(track, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    color = if (active) C.cyan else C.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPlaying) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = C.cyan, modifier = Modifier.size(14.dp))
                }
            }
            // Artist + album/source one tidy line instead of two cramped ones.
            Text(
                "${track.artist.ifBlank { "Noma'lum ijrochi" }} · ${track.album.ifBlank {
                    when (track.source.name) {
                        "URL" -> "Internet manbasi"
                        "DEMO" -> "Namoyish treki"
                        else -> "Mahalliy MP3"
                    }
                }}",
                color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (track.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (track.favorite) "Sevimlilardan olish" else "Sevimlilarga qo'shish",
                tint = if (track.favorite) C.red else C.muted
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.MoreHoriz, "Trekni tahrirlash", tint = C.muted)
        }
    }
}

@Composable
fun KradusSlider(
    value: Float,
    max: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    Slider(
        value = value.coerceIn(0f, max.coerceAtLeast(0.001f)),
        onValueChange = onChange,
        valueRange = 0f..max.coerceAtLeast(0.001f),
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = C.text,
            activeTrackColor = C.blue,
            inactiveTrackColor = C.line
        )
    )
}

@Composable
fun ProgressBar(valueSec: Long, maxSec: Long, onSeek: (Long) -> Unit, enabled: Boolean = true) {
    Column {
        KradusSlider(
            value = valueSec.toFloat(),
            max = maxSec.coerceAtLeast(1).toFloat(),
            onChange = { onSeek(it.toLong()) },
            enabled = enabled && maxSec > 0
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(valueSec), color = C.muted, fontSize = 11.sp)
            Text(formatTime(maxSec), color = C.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun PrimaryButton(
    title: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onPress,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) C.surface2 else C.blue,
            contentColor = Color.White,
            disabledContainerColor = C.surface2
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = C.text, strokeWidth = 2.dp)
        Text(if (loading) "Yuklanmoqda…" else title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun SectionHeading(title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, color = C.muted, fontSize = 12.sp) }
        }
        if (action != null && onAction != null) {
            Text(
                action, color = C.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAction).padding(8.dp)
            )
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, content: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.surface)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(description, color = C.muted, fontSize = 13.sp)
        content?.let { Spacer(Modifier.height(18.dp)); it() }
    }
}
