package space.manus.kradusmedia.t004358040099515

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.manus.kradusmedia.t004358040099515.music.MusicViewModel
import space.manus.kradusmedia.t004358040099515.music.isAllFilesGranted
import space.manus.kradusmedia.t004358040099515.music.scanAllAudio
import space.manus.kradusmedia.t004358040099515.music.scanDeviceFiles
import space.manus.kradusmedia.t004358040099515.ui.components.CoverArt
import space.manus.kradusmedia.t004358040099515.ui.screens.FavoritesScreen
import space.manus.kradusmedia.t004358040099515.ui.screens.FoldersScreen
import space.manus.kradusmedia.t004358040099515.ui.screens.LibraryScreen
import space.manus.kradusmedia.t004358040099515.ui.screens.ListenScreen
import space.manus.kradusmedia.t004358040099515.ui.screens.PlayerScreen
import space.manus.kradusmedia.t004358040099515.ui.theme.C
import space.manus.kradusmedia.t004358040099515.ui.theme.KradusTheme

class MainActivity : ComponentActivity() {

    private val vm: MusicViewModel by viewModels()

    /** System document picker only — no broad storage permission (mirrors Expo port). */
    private val pickAudio = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        // File IO off the main thread: no UI jank while copying MP3s.
        lifecycleScope.launch(Dispatchers.IO) {
            val picks = uris.map { uri ->
                var name = "track.mp3"
                var mime: String? = contentResolver.getType(uri)
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx)?.let { name = it }
                }
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                uri to (name to mime)
            }
            vm.importFromDevice(picks)
        }
    }

    /** Media notification permission — requested only after user starts playback. */
    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.notificationAskConsumed() }

    // ---------- full-library scan permission (user-initiated only) ----------

    private val libraryPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestLibraryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            doLibraryScan()
        } else {
            vm.setImportError(
                "Ruxsat berilmadi — telefon musiqasini skanerlab bo'lmadi. " +
                    "Ruxsatsiz qo'shish uchun \"Qurilmadan\" tugmasini ishlating."
            )
        }
    }

    /** Entry point for the Library screen's scan button. */
    fun ensureMediaAndScan() {
        if (ContextCompat.checkSelfPermission(this, libraryPermission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            doLibraryScan()
        } else {
            requestLibraryPermission.launch(libraryPermission)
        }
    }

    private fun doLibraryScan() {
        vm.clearImportError()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // MediaStore + (if All-Files-Access) native dir scan, deduped.
                val items = scanDeviceFiles(this@MainActivity)
                vm.importFromLibrary(items)
            } catch (_: Exception) {
                vm.setImportError("Telefon musiqasini o'qib bo'lmadi. Qayta urinib ko'ring.")
            }
        }
    }

    // ---------- All-Files-Access (Android 11+, user-initiated only) ----------

    private val allFilesSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.setAllFilesGranted(isAllFilesGranted(this))
    }

    /** Opens system Settings so the USER can grant full file access. */
    fun ensureAllFilesAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val uri = Uri.parse("package:$packageName")
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    uri
                )
                allFilesSettings.launch(intent)
            }
        } catch (_: Exception) {
            try {
                val fallback = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                )
                allFilesSettings.launch(fallback)
            } catch (_: Exception) {
                vm.setImportError("Sozlamalarni ochib bo'lmadi.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KradusTheme {
                val askNotification by vm.askNotification.collectAsState()
                LaunchedEffect(askNotification) {
                    if (askNotification && Build.VERSION.SDK_INT >= 33) {
                        vm.notificationAskConsumed()
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
                KradusRoot(
                    vm = vm,
                    onPickDevice = {
                        pickAudio.launch(arrayOf("audio/mpeg", "audio/x-mpeg", "audio/mp3", "audio/*"))
                    },
                    onScanLibrary = { ensureMediaAndScan() },
                    onManageFiles = { ensureAllFilesAccess() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh after returning from system Settings screens.
        vm.setAllFilesGranted(isAllFilesGranted(this))
    }

    override fun onStop() {
        vm.flushOnBackground()
        super.onStop()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("listen", "Tinglash", Icons.Filled.PlayCircle),
    Tab("library", "Kutubxona", Icons.Filled.MusicNote),
    Tab("folders", "Papkalar", Icons.Filled.Album),
    Tab("favorites", "Sevimlilar", Icons.Filled.Favorite)
)

@Composable
private fun KradusRoot(vm: MusicViewModel, onPickDevice: () -> Unit, onScanLibrary: () -> Unit, onManageFiles: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val tracks by vm.tracks.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val playing by vm.playing.collectAsState()
    val current = remember(tracks, currentId) { tracks.firstOrNull { it.id == currentId } }

    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            if (currentRoute != "player") {
                Column(Modifier.background(C.bg)) {
                    // Persistent mini-player (port of MusicTabBar mini).
                    current?.let { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { nav.navigate("player") }
                                .background(C.surface)
                                .padding(8.dp)
                                .heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArt(track, 42.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = C.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    track.artist.ifBlank { "Noma'lum ijrochi" },
                                    color = C.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = vm::togglePlayback,
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(C.surface2)
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (playing) "Pauza" else "Ijro etish",
                                    tint = C.text
                                )
                            }
                        }
                    }
                    // Bottom tabs: every tab gets a full 48dp+ touch target and
                    // breathing room, so nothing looks squeezed on small phones.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(C.bg)
                            .padding(top = 6.dp, bottom = 10.dp)
                    ) {
                        TABS.forEach { tab ->
                            val focused = currentRoute == tab.route
                            Column(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        nav.navigate(tab.route) {
                                            popUpTo("listen") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(tab.icon, tab.label, tint = if (focused) C.cyan else C.muted, modifier = Modifier.size(23.dp))
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tab.label,
                                    color = if (focused) C.cyan else C.muted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "listen",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable("listen") {
                ListenScreen(
                    vm,
                    onOpenPlayer = { nav.navigate("player") },
                    onOpenLibrary = { nav.navigate("library") },
                    onPickDevice = onPickDevice,
                    onScanLibrary = onScanLibrary
                )
            }
            composable("library") { LibraryScreen(vm, onPickDevice, onScanLibrary, onManageFiles) }
            composable("folders") { FoldersScreen(vm) }
            composable("favorites") {
                FavoritesScreen(vm, onOpenLibrary = { nav.navigate("library") })
            }
            composable("player") {
                PlayerScreen(
                    vm,
                    onClose = { nav.popBackStack() },
                    onOpenLibrary = {
                        nav.popBackStack()
                        nav.navigate("library")
                    }
                )
            }
        }
    }
}
