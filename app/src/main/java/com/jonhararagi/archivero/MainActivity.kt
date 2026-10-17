package com.jonhararagi.archivero

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.OpenableColumns
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executor
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private enum class Screen { HOME, TYPES, FOLDERS, CLEAN }
private enum class CleanupMode { SCREENSHOTS, DUPLICATES, VIDEOS }
private data class DuplicateGroup(val hash: String, val files: List<DocumentFile>)
private data class PendingOpenRequest(val file: DocumentFile, val queue: List<DocumentFile>)
private data class FilteredFileLists(
    val folders: List<DocumentFile>,
    val matching: List<DocumentFile>,
    val duplicates: List<DuplicateGroup>,
    val cleanup: List<DocumentFile>
)
private data class ScanResult(
    val files: List<DocumentFile>,
    val visited: Int,
    val partial: Boolean,
    val unreadableDirectories: Int
)
private enum class Palette(val label: String, val primary: Color, val secondary: Color, val background: Color) {
    VERDE("Verde bosque", Color(0xFF285E55), Color(0xFF547D73), Color(0xFFF4F7F5)),
    VIOLETA("Violeta", Color(0xFF7048A8), Color(0xFF9575CD), Color(0xFFF7F2FC)),
    ROSA("Rosa", Color(0xFFAD476C), Color(0xFFD981A0), Color(0xFFFFF4F7)),
    AZUL("Azul", Color(0xFF285DA8), Color(0xFF668CC6), Color(0xFFF2F6FD)),
    AMBAR("Ámbar", Color(0xFF93600B), Color(0xFFC28B2C), Color(0xFFFFF8E9))
}
private enum class Kind(val title: String, val icon: ImageVector, val tint: Color, val extensions: Set<String>) {
    IMAGES("Imágenes", Icons.Default.Image, Color(0xFF7657B5), setOf("jpg","jpeg","jpe","jfif","jps","png","webp","gif","bmp","heic","heif","avif","tif","tiff","svg")),
    VIDEOS("Videos", Icons.Default.Movie, Color(0xFFB3261E), setOf("mp4","mkv","mov","avi","webm","3gp","m4v")),
    MUSIC("Música", Icons.Default.MusicNote, Color(0xFF00796B), setOf("mp3","wav","aac","m4a","ogg","flac","opus","wma")),
    DOCS("Documentos", Icons.Default.Description, Color(0xFF245CA6), setOf("pdf","doc","docx","txt","rtf","odt","xls","xlsx","ppt","pptx","csv","md","json","xml","html","htm","log")),
    APK("APK", Icons.Default.Android, Color(0xFF397447), setOf("apk","xapk","apks")),
    ZIP("Comprimidos", Icons.Default.FolderZip, Color(0xFF8A4F00), setOf("zip","rar","7z","tar","gz","bz2")),
    OTHER("Otros", Icons.Default.InsertDriveFile, Color(0xFF58616B), emptySet())
}
private data class Disk(val used: Long, val total: Long) {
    val free get() = (total - used).coerceAtLeast(0)
    companion object {
        fun read(): Disk? = runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            Disk((stat.totalBytes - stat.availableBytes).coerceAtLeast(0), stat.totalBytes)
        }.getOrNull()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArchiveroApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveroApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPaletteName by rememberSaveable {
        mutableStateOf(context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).getString("palette", Palette.VERDE.name) ?: Palette.VERDE.name)
    }
    val activePalette = runCatching { Palette.valueOf(selectedPaletteName) }.getOrDefault(Palette.VERDE)
    var showAppearance by remember { mutableStateOf(false) }
    var wallpaperUri by rememberSaveable {
        mutableStateOf(context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).getString("wallpaper", null))
    }
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scanStatus by rememberSaveable { mutableStateOf("") }
    var scanProgress by remember { mutableIntStateOf(0) }
    var cleanupModeName by rememberSaveable { mutableStateOf(CleanupMode.SCREENSHOTS.name) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var duplicateScanRunning by remember { mutableStateOf(false) }
    var duplicateScanProgress by remember { mutableIntStateOf(0) }
    var duplicateScanTotal by remember { mutableIntStateOf(0) }
    var duplicateScanStatus by rememberSaveable { mutableStateOf("") }
    var analyzeDuplicatesAfterScan by remember { mutableStateOf(false) }
    var cleanupSelectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkDeleteDialog by remember { mutableStateOf(false) }
    var fileOperationRunning by remember { mutableStateOf(false) }
    var showPlayerPanel by rememberSaveable { mutableStateOf(false) }
    var rootUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var hasAllFilesAccess by remember { mutableStateOf(Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var root by remember { mutableStateOf<DocumentFile?>(null) }
    var current by remember { mutableStateOf<DocumentFile?>(null) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedKind by rememberSaveable { mutableStateOf(Kind.IMAGES.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var indexed by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listedEntries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listingFailed by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var actionFile by remember { mutableStateOf<DocumentFile?>(null) }
    var pendingOpenRequest by remember { mutableStateOf<PendingOpenRequest?>(null) }
    var selectedOpenMethod by remember(pendingOpenRequest?.file?.uri?.toString()) {
        mutableStateOf(pendingOpenRequest?.let { request ->
            savedOpenMethod(context, request.file) ?: if (canOpenNatively(context, request.file)) "ARCHIVERO" else "EXTERNAL"
        } ?: "ARCHIVERO")
    }
    var deleteDialog by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var playerUri by remember { mutableStateOf<Uri?>(null) }
    var playbackUri by remember { mutableStateOf<Uri?>(null) }
    var playerTitle by remember { mutableStateOf("") }
    var playerVideo by remember { mutableStateOf(false) }
    var playerQueueItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var playbackRequestId by remember { mutableIntStateOf(0) }
    var queueBuildRequest by remember { mutableIntStateOf(0) }
    var loadingQueue by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var sleepTimerMinutes by rememberSaveable { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var favoriteUris by remember {
        mutableStateOf(context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet())?.toSet().orEmpty())
    }
    var playbackIsPlaying by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    val controllerFuture = remember(context) {
        MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))).buildAsync()
    }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    val mainExecutor = remember { Executor { command -> Handler(Looper.getMainLooper()).post(command) } }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener({ mediaController = runCatching { controllerFuture.get() }.getOrNull() }, mainExecutor)
        onDispose { MediaController.releaseFuture(controllerFuture); mediaController = null }
    }
    DisposableEffect(mediaController) {
        val controller = mediaController
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playbackIsPlaying = isPlaying }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabledValue: Boolean) { shuffleEnabled = shuffleModeEnabledValue }
            override fun onRepeatModeChanged(repeatModeValue: Int) { repeatMode = repeatModeValue }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Do not auto-chain failing tracks from inside Media3's error callback.
                // The user can try the next queue item explicitly from the player.
                playbackError = error.errorCodeName + ": " +
                    (error.message ?: "Android no pudo decodificar este archivo. Prueba otra copia o formato.")
                playbackIsPlaying = false
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playbackError = null
                val title = mediaItem?.mediaMetadata?.title?.toString()
                if (!title.isNullOrBlank()) playerTitle = title
                mediaItem?.localConfiguration?.uri?.let { uri ->
                    playbackUri = uri
                    playerUri = uri
                }
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(mediaController) {
        while (isActive) {
            mediaController?.let { controller ->
                playbackIsPlaying = controller.isPlaying
                shuffleEnabled = controller.shuffleModeEnabled
                repeatMode = controller.repeatMode
                playbackPosition = controller.currentPosition.coerceAtLeast(0L)
                playbackDuration = controller.duration.takeIf { it > 0L } ?: 0L
            }
            delay(450)
        }
    }
    LaunchedEffect(mediaController, playbackRequestId) {
        val controller = mediaController ?: return@LaunchedEffect
        val requested = playbackUri ?: return@LaunchedEffect
        if (playbackRequestId == 0) return@LaunchedEffect
        playbackError = null
        val items = playerQueueItems.distinctBy { it.localConfiguration?.uri.toString() }
        val selectedIndex = items.indexOfFirst { it.localConfiguration?.uri == requested }
        if (!playerVideo && selectedIndex >= 0) controller.setMediaItems(items, selectedIndex, 0L)
        else controller.setMediaItem(MediaItem.Builder().setUri(requested)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(playerTitle).setArtist("Archivero").build()).build())
        controller.prepare()
        controller.play()
    }
    fun startPlayback(uri: Uri, title: String, video: Boolean, queue: List<DocumentFile>) {
        if (video) {
            runCatching {
                val intent = Intent(context, VideoPlayerActivity::class.java)
                    .setData(uri)
                    .putExtra("displayName", title)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            }.onFailure { error ->
                message = "No se pudo abrir el vídeo «" + title + "»: " + (error.message ?: "Android rechazó el archivo.")
            }
            return
        }

        playerUri = uri
        playbackUri = uri
        playerTitle = title
        playerVideo = false
        showPlayerPanel = true
        playbackError = null
        loadingQueue = true
        queueBuildRequest++
        val requestId = queueBuildRequest
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    queue.asSequence()
                        .filter { file ->
                            !file.isDirectory &&
                                file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) in Kind.MUSIC.extensions
                        }
                        .distinctBy { it.uri.toString() }
                        .map { file ->
                            MediaItem.Builder().setUri(file.uri)
                                .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name ?: "Pista").setArtist("Archivero").build())
                                .build()
                        }
                        .toList()
                }
                if (requestId != queueBuildRequest) return@launch
                playerQueueItems = items
                if (items.none { it.localConfiguration?.uri == uri }) {
                    playbackError = "No se pudo añadir esta canción a la lista. Comprueba que el archivo siga disponible."
                    return@launch
                }
                playbackRequestId++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == queueBuildRequest) {
                    playbackError = "No se pudo preparar la lista de música: " + (error.message ?: "comprueba los permisos.")
                }
            } finally {
                if (requestId == queueBuildRequest) loadingQueue = false
            }
        }
    }
    fun requestOpen(file: DocumentFile, queue: List<DocumentFile>) {
        val saved = savedOpenMethod(context, file)
        if (saved == "EXTERNAL" || (saved == "ARCHIVERO" && !canOpenNatively(context, file))) {
            openDocument(file, context, false, { u, t, v -> startPlayback(u, t, v, queue) }, { message = it })
            return
        }
        if (saved == "ARCHIVERO" && canOpenNatively(context, file)) {
            openDocument(file, context, true, { u, t, v -> startPlayback(u, t, v, queue) }, { message = it })
            return
        }
        pendingOpenRequest = PendingOpenRequest(file, queue)
    }
    fun confirmOpenChoice(request: PendingOpenRequest, method: String, always: Boolean) {
        val actualMethod = if (method == "ARCHIVERO" && canOpenNatively(context, request.file)) "ARCHIVERO" else "EXTERNAL"
        if (always) {
            context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                .edit().putString(openPreferenceKey(context, request.file), actualMethod).apply()
        }
        pendingOpenRequest = null
        openDocument(
            request.file, context, actualMethod == "ARCHIVERO",
            { u, t, v -> startPlayback(u, t, v, request.queue) },
            { message = it }
        )
    }

    fun toggleFavorite(uri: Uri) {
        val next = favoriteUris.toMutableSet()
        if (!next.add(uri.toString())) next.remove(uri.toString())
        favoriteUris = next.toSet()
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).edit().putStringSet("favorites", next).apply()
    }
    LaunchedEffect(sleepTimerMinutes, mediaController) {
        val minutes = sleepTimerMinutes
        val controller = mediaController
        if (minutes > 0 && controller != null) {
            delay(minutes * 60_000L)
            controller.pause()
            sleepTimerMinutes = 0
        }
    }
    LaunchedEffect(selectedPaletteName) {
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).edit().putString("palette", selectedPaletteName).apply()
    }
    LaunchedEffect(wallpaperUri) {
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).edit().putString("wallpaper", wallpaperUri).apply()
        val saved = wallpaperUri
        wallpaperBitmap = if (saved == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(saved)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = (largest / 1600).coerceAtLeast(1) }
                context.contentResolver.openInputStream(uri).use { stream -> BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val disk = remember(refresh) { Disk.read() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) { }
            rootUri = uri
            currentUri = uri
            root = DocumentFile.fromTreeUri(context, uri)
            current = root
            indexed = emptyList()
            hasScanned = false
            scanStatus = ""
            query = ""
            refresh++
        }
    }
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            wallpaperUri = uri.toString()
            showAppearance = false
        }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            loadingQueue = true
            queueBuildRequest++
            val requestId = queueBuildRequest
            playbackError = null
            showQueue = false
            showPlayerPanel = true
            scope.launch {
                try {
                    val queue = withContext(Dispatchers.IO) {
                        uris.distinct().map { uri ->
                            try {
                                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: SecurityException) {
                            }
                            MediaItem.Builder().setUri(uri)
                                .setMediaMetadata(MediaMetadata.Builder().setTitle(displayName(context, uri)).setArtist("Archivero").build())
                                .build()
                        }
                    }
                    if (requestId != queueBuildRequest) return@launch
                    if (queue.isEmpty()) {
                        playbackError = "No se pudo añadir ninguna canción."
                        return@launch
                    }
                    playerQueueItems = queue
                    playbackUri = queue.first().localConfiguration?.uri
                    playerUri = playbackUri
                    playerTitle = queue.first().mediaMetadata.title?.toString() ?: "Pista"
                    playerVideo = false
                    playbackRequestId++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (requestId == queueBuildRequest) {
                        playbackError = "No se pudo preparar la lista: " + (error.message ?: "comprueba los permisos.")
                    }
                } finally {
                    if (requestId == queueBuildRequest) loadingQueue = false
                }
            }
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            startPlayback(uri, displayName(context, uri), true, emptyList())
        }
    }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(permissionRefresh) {
        hasAllFilesAccess = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (hasAllFilesAccess && root == null) {
            val storage = DocumentFile.fromFile(Environment.getExternalStorageDirectory())
            root = storage
            current = storage
            rootUri = storage.uri
            currentUri = storage.uri
            indexed = emptyList()
            hasScanned = false
        }
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    LaunchedEffect(rootUri, hasAllFilesAccess) {
        val saved = rootUri ?: return@LaunchedEffect
        if (root == null) {
            val restored = runCatching {
                if (saved.scheme == "file" && hasAllFilesAccess) DocumentFile.fromFile(Environment.getExternalStorageDirectory())
                else DocumentFile.fromTreeUri(context, saved)
            }.getOrNull()
            if (restored != null && restored.canRead()) { root = restored; current = restored; currentUri = restored.uri }
            else if (saved.scheme != "file") { rootUri = null; currentUri = null; message = "Android ya no permite acceder a esa ubicación. Elígela otra vez." }
        }
    }

    LaunchedEffect(currentUri, refresh) {
        val folderToList = current
        listingFailed = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                folderToList?.listFiles()
                    ?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase(Locale.ROOT) })
                    ?.toList().orEmpty()
            }
        }
        listedEntries = result.getOrElse { emptyList() }
        listingFailed = result.isFailure
    }
    val entries = listedEntries
    var folderEntries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var matchingFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var duplicateDisplayGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var cleanupFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var filteringFiles by remember { mutableStateOf(false) }
    var filterRequestId by remember { mutableIntStateOf(0) }
    val sourceFiles = remember(hasScanned, indexed, entries) {
        if (hasScanned) indexed else entries.filterNot { it.isDirectory }
    }
    val activeKind = runCatching { Kind.valueOf(selectedKind) }.getOrDefault(Kind.IMAGES)
    val activeCleanupMode = runCatching { CleanupMode.valueOf(cleanupModeName) }.getOrDefault(CleanupMode.SCREENSHOTS)
    var oldScreenshotFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var largeVideoFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    LaunchedEffect(sourceFiles) {
        val cleanupCandidates = withContext(Dispatchers.IO) {
            val screenshots = sourceFiles.filter { isOldScreenshot(it) }.sortedBy { it.lastModified() }
            val videos = sourceFiles.filter {
                it.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) in Kind.VIDEOS.extensions &&
                    it.length() >= 100L * 1024 * 1024
            }.sortedByDescending { it.length() }
            screenshots to videos
        }
        oldScreenshotFiles = cleanupCandidates.first
        largeVideoFiles = cleanupCandidates.second
    }
    LaunchedEffect(entries, sourceFiles, activeKind, query, duplicateGroups, activeCleanupMode, oldScreenshotFiles, largeVideoFiles) {
        filterRequestId++
        val request = filterRequestId
        filteringFiles = true
        try {
            val normalizedQuery = query.trim()
            val result = withContext(Dispatchers.IO) {
                val folders = entries.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                val matching = sourceFiles.filter { file ->
                    val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val kindMatches = if (activeKind == Kind.OTHER) {
                        Kind.values().filter { it != Kind.OTHER }.none { ext in it.extensions }
                    } else ext in activeKind.extensions
                    kindMatches && file.name.orEmpty().contains(normalizedQuery, ignoreCase = true)
                }
                val duplicates = duplicateGroups.mapNotNull { group ->
                    val visible = group.files.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                    if (visible.isEmpty()) null else group.copy(files = visible)
                }
                val cleanup = when (activeCleanupMode) {
                    CleanupMode.SCREENSHOTS -> oldScreenshotFiles
                    CleanupMode.DUPLICATES -> duplicates.flatMap { it.files }.distinctBy { it.uri.toString() }
                    CleanupMode.VIDEOS -> largeVideoFiles
                }.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                FilteredFileLists(folders, matching, duplicates, cleanup)
            }
            if (request == filterRequestId) {
                folderEntries = result.folders
                matchingFiles = result.matching
                duplicateDisplayGroups = result.duplicates
                cleanupFiles = result.cleanup
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (request == filterRequestId) filteringFiles = false
        }
    }
    val selectedCleanupFiles = remember(cleanupFiles, cleanupSelectedUris) { cleanupFiles.filter { it.uri.toString() in cleanupSelectedUris } }
    val selectedCleanupBytes = remember(selectedCleanupFiles) { selectedCleanupFiles.sumOf { it.length().coerceAtLeast(0L) } }

    fun scan() {
        val start = root
        if (start == null) { scanStatus = "Primero elige una carpeta para analizar."; return }
        if (scanning) return
        scope.launch {
            scanning = true
            scanProgress = 0
            scanStatus = "Preparando búsqueda..."
            try {
                val result = withContext(Dispatchers.IO) {
                    val found = mutableListOf<DocumentFile>()
                    val queue = ArrayDeque<Pair<DocumentFile, Int>>()
                    queue.add(start to 0)
                    var visited = 0
                    var depthLimitHit = false
                    var unreadableDirectories = 0
                    val maxEntries = 100_000
                    val maxDepth = 20
                    while (queue.isNotEmpty() && visited < maxEntries) {
                        val item = queue.removeFirst()
                        val children = try {
                            item.first.listFiles().toList()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            unreadableDirectories++
                            emptyList()
                        }
                        for (child in children) {
                            if (visited >= maxEntries) break
                            visited++
                            if (child.isDirectory) {
                                if (item.second < maxDepth && !child.name.orEmpty().startsWith(".")) queue.add(child to item.second + 1)
                                else if (item.second >= maxDepth) depthLimitHit = true
                            } else found.add(child)
                            if (visited % 1000 == 0) withContext(Dispatchers.Main) { scanProgress = visited }
                        }
                    }
                    ScanResult(
                        files = found,
                        visited = visited,
                        partial = visited >= maxEntries || queue.isNotEmpty() || depthLimitHit || unreadableDirectories > 0,
                        unreadableDirectories = unreadableDirectories
                    )
                }
                indexed = result.files
                hasScanned = true
                scanProgress = result.visited
                refresh++
                val unreadableDetail = if (result.unreadableDirectories > 0) {
                    " No se pudieron leer " + result.unreadableDirectories + " carpetas."
                } else ""
                scanStatus = if (result.partial) {
                    "Resultados parciales: " + result.files.size + " archivos revisados (" +
                        result.visited + " elementos). Para completar, analiza una carpeta más específica." + unreadableDetail
                } else {
                    "Búsqueda completa: " + result.files.size + " archivos encontrados."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                scanStatus = "La búsqueda se interrumpió sin borrar archivos. Comprueba los permisos e inténtalo con una carpeta más pequeña."
                message = scanStatus
            } finally {
                scanning = false
            }
        }
    }

    fun analyzeDuplicates() {
        if (!hasScanned) {
            analyzeDuplicatesAfterScan = true
            scan()
            duplicateScanStatus = "Primero se analiza la ubicación; después se compara el contenido de los archivos del mismo tamaño."
            return
        }
        if (duplicateScanRunning) return
        val candidates = sourceFiles
            .filter { !it.isDirectory && it.length() > 0L }
            .groupBy { it.length() }
            .filter { (size, files) -> size > 0L && files.size > 1 }
            .values.flatten()
            .distinctBy { it.uri.toString() }
        if (candidates.isEmpty()) {
            duplicateGroups = emptyList()
            duplicateScanProgress = 0
            duplicateScanTotal = 0
            duplicateScanStatus = "No hay archivos con el mismo tamaño para comparar. No se ha eliminado nada."
            return
        }
        duplicateScanRunning = true
        duplicateScanProgress = 0
        duplicateScanTotal = candidates.size
        duplicateScanStatus = "Comparando el contenido completo de ${candidates.size} archivos..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val hashes = linkedMapOf<String, MutableList<DocumentFile>>()
                var unreadable = 0
                for (index in candidates.indices) {
                    val file = candidates[index]
                    val hash = runCatching { sha256Content(context, file) }.getOrNull()
                    if (hash == null) {
                        unreadable++
                    } else {
                        hashes.getOrPut("${file.length()}|$hash") { mutableListOf() }.add(file)
                    }
                    if ((index + 1) % 5 == 0 || index == candidates.lastIndex) {
                        withContext(Dispatchers.Main) { duplicateScanProgress = index + 1 }
                    }
                }
                val confirmed = hashes.entries
                    .filter { it.value.size > 1 }
                    .map { entry ->
                        DuplicateGroup(
                            hash = entry.key.substringAfter('|'),
                            files = entry.value.sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
                        )
                    }
                    .sortedByDescending { group -> group.files.firstOrNull()?.length() ?: 0L }
                Triple(confirmed, unreadable, candidates.size)
            }
            duplicateGroups = result.first
            duplicateScanRunning = false
            cleanupSelectedUris = emptySet()
            duplicateScanStatus = buildString {
                append("Comparación finalizada: ${result.first.size} grupos con contenido idéntico confirmado por SHA-256. No se borró nada.")
                if (result.second > 0) append(" ${result.second} archivo(s) no se pudieron leer y no se clasificaron como duplicados.")
            }
        }
    }

    fun requestBulkDelete() {
        if (selectedCleanupFiles.isEmpty()) return
        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateGroups.any { group ->
                group.files.size > 1 && group.files.all { it.uri.toString() in cleanupSelectedUris }
            }) {
            message = "Para proteger tus archivos, deja al menos una copia sin marcar en cada grupo de duplicados."
            return
        }
        bulkDeleteDialog = true
    }

    fun confirmBulkDelete() {
        bulkDeleteDialog = false
        val selected = selectedCleanupFiles
        if (selected.isEmpty()) return
        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateGroups.any { group ->
                group.files.size > 1 && group.files.all { it.uri.toString() in selected.map { file -> file.uri.toString() }.toSet() }
            }) {
            message = "No se borró nada: deja al menos una copia de cada grupo de duplicados."
            return
        }
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                selected.map { file ->
                    file.uri.toString() to runCatching { file.delete() }.getOrDefault(false)
                }
            }
            val deletedUris = results.filter { it.second }.map { it.first }.toSet()
            val failed = results.size - deletedUris.size
            if (deletedUris.isNotEmpty()) {
                indexed = indexed.filterNot { it.uri.toString() in deletedUris }
                duplicateGroups = duplicateGroups.mapNotNull { group ->
                    val remaining = group.files.filterNot { it.uri.toString() in deletedUris }
                    if (remaining.size > 1) group.copy(files = remaining) else null
                }
                refresh++
            }
            cleanupSelectedUris = emptySet()
            scanStatus = "Limpieza revisada: ${deletedUris.size} eliminado(s); ${failed} no se pudieron eliminar."
            message = scanStatus
        }
    }

    LaunchedEffect(analyzeDuplicatesAfterScan, hasScanned, scanning) {
        if (analyzeDuplicatesAfterScan && hasScanned && !scanning) {
            analyzeDuplicatesAfterScan = false
            analyzeDuplicates()
        }
    }
    LaunchedEffect(screen, rootUri, hasScanned, scanning) {
        if (screen == Screen.CLEAN && root != null && !hasScanned && !scanning) scan()
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = activePalette.primary, secondary = activePalette.secondary,
        background = activePalette.background, surface = Color.White,
        surfaceVariant = activePalette.secondary.copy(alpha = 0.12f)
    )) {
        Box(Modifier.fillMaxSize()) {
            wallpaperBitmap?.let { image ->
                Image(bitmap = image, contentDescription = "Fondo personalizado de Archivero",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.28f)
            }
            Scaffold(
            containerColor = activePalette.background.copy(alpha = if (wallpaperBitmap != null) 0.78f else 1f),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderCopy, null, tint = Color.White)
                            }
                            Column {
                                Text("Archivero", fontWeight = FontWeight.ExtraBold)
                                Text(when (screen) {
                                    Screen.HOME -> "Tu espacio, bajo control"
                                    Screen.TYPES -> "Buscar por tipo"
                                    Screen.FOLDERS -> "Carpetas y ubicaciones"
                                    Screen.CLEAN -> "Recupera espacio"
                                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAppearance = true }) { Icon(Icons.Default.Palette, "Personalizar apariencia") }
                        IconButton(onClick = { if (rootUri == null) pickFolder.launch(null) else refresh++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = activePalette.background.copy(alpha = 0.88f))
                )
            },
            bottomBar = {
                Column {
                    if (playerUri != null && !showPlayerPanel && !playerVideo) {
                        Surface(
                            color = Color.White,
                            tonalElevation = 4.dp,
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { showPlayerPanel = true }.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(38.dp).background(activePalette.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.MusicNote, null, tint = activePalette.primary) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playerTitle.ifBlank { "Reproductor de música" }, maxLines = 1, fontWeight = FontWeight.Bold)
                                    Text(if (loadingQueue) "Preparando lista · toca para abrir" else if (playbackIsPlaying) "Reproduciendo · toca para abrir" else "En pausa · toca para abrir",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { mediaController?.let { controller -> if (controller.isPlaying) controller.pause() else controller.play() } }) {
                                    Icon(if (playbackIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        if (playbackIsPlaying) "Pausar música" else "Continuar música", tint = activePalette.primary)
                                }
                                IconButton(onClick = { showPlayerPanel = true }) { Icon(Icons.Default.OpenInFull, "Abrir reproductor") }
                            }
                        }
                    }
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME; query = "" }, { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") })
                        NavigationBarItem(screen == Screen.TYPES, { screen = Screen.TYPES; query = "" }, { Icon(Icons.Default.Category, null) }, label = { Text("Tipos") })
                        NavigationBarItem(screen == Screen.FOLDERS, { screen = Screen.FOLDERS; query = "" }, { Icon(Icons.Default.FolderOpen, null) }, label = { Text("Carpetas") })
                        NavigationBarItem(screen == Screen.CLEAN, { screen = Screen.CLEAN; query = ""; cleanupSelectedUris = emptySet() }, { Icon(Icons.Default.CleaningServices, null) }, label = { Text("Limpiar") })
                    }
                }
            },
            floatingActionButton = {
                if (screen == Screen.FOLDERS && current != null) FloatingActionButton(onClick = { folderName = ""; newFolderDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                    Icon(Icons.Default.CreateNewFolder, "Nueva carpeta")
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().then(if (screen == Screen.HOME && rootUri != null) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                if (rootUri == null) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp).background(Color(0xFFDDECE5), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderCopy, null, Modifier.size(52.dp), tint = Color(0xFF285E55))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Todo tu espacio, más simple", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Busca por tipo, explora carpetas y encuentra archivos grandes desde un solo lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { requestAllFilesAccess() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Permitir acceso a mis archivos")
                        }
                        Text("Se abrirá el ajuste oficial de Android. Actívalo una sola vez para navegar por Descargas, Documentos, Imágenes y otras carpetas. Archivero trabaja localmente y no envía tus archivos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { pickFolder.launch(null) }) { Text("O elegir una carpeta específica") }
                    }
                } else when (screen) {
                    Screen.HOME -> {
                        DiskCard(disk, root?.name ?: "Ubicación elegida", activePalette.primary) { pickFolder.launch(null) }
                        Spacer(Modifier.height(18.dp))
                        Header("¿Qué estás buscando?", "Accesos directos a tus archivos")
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.IMAGES, Modifier.weight(1f)) { selectedKind = Kind.IMAGES.name; screen = Screen.TYPES; if (!hasScanned) scan() }
                                CategoryTile(Kind.VIDEOS, Modifier.weight(1f)) { selectedKind = Kind.VIDEOS.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.MUSIC, Modifier.weight(1f)) {
                        selectedKind = Kind.MUSIC.name
                        screen = Screen.TYPES
                        playerVideo = false
                        showPlayerPanel = true
                        if (indexed.isEmpty()) scan()
                    }
                                CategoryTile(Kind.DOCS, Modifier.weight(1f)) { selectedKind = Kind.DOCS.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.APK, Modifier.weight(1f)) { selectedKind = Kind.APK.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
                                CategoryTile(Kind.ZIP, Modifier.weight(1f)) { selectedKind = Kind.ZIP.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile("Archivos grandes", "Desde 100 MB", Icons.Default.Storage, Modifier.weight(1f)) { screen = Screen.CLEAN; if (indexed.isEmpty()) scan() }
                            ActionTile("Buscar basura", "Revisar antes de borrar", Icons.Default.DeleteSweep, Modifier.weight(1f)) { screen = Screen.CLEAN; if (indexed.isEmpty()) scan() }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ManageSearch, null)
                            Spacer(Modifier.width(8.dp)); Text(if (scanning) "Analizando..." else "Buscar archivos en toda la ubicación")
                        }
                        if (scanning) Text("Revisados: $scanProgress elementos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scanStatus.isNotBlank()) Text(scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Screen.TYPES -> {
                        Header("Buscar por tipo", "Elige una categoría, luego filtra por nombre")
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(Kind.values().toList()) { kind ->
                                Card(onClick = {
                                    selectedKind = kind.name
                                    if (kind == Kind.MUSIC) {
                                        playerVideo = false
                                        showPlayerPanel = true
                                    }
                                }, colors = CardDefaults.cardColors(containerColor = if (activeKind == kind) MaterialTheme.colorScheme.secondaryContainer else Color.White)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        KindIcon(kind, 42); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(kind.title, fontWeight = FontWeight.Bold)
                                            Text(kind.extensions.take(5).joinToString(" · ").ifBlank { "Otros formatos" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (activeKind == kind) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                        }
                        SearchBox(query) { query = it }
                        if (scanning) Text("Buscando... $scanProgress elementos revisados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filteringFiles) Text("Filtrando archivos en segundo plano…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scanStatus.isNotBlank()) Text(scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        Text(matchingFiles.size.toString() + " archivos encontrados", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                        FileList(matchingFiles, { requestOpen(it, sourceFiles) }, { actionFile=it; actionMenu=true }, Modifier.weight(1f))
                        if (!hasScanned || scanStatus.startsWith("Resultados parciales")) TextButton(onClick = { scan() }, enabled = !scanning) { Text(if (scanning) "Analizando..." else "Volver a analizar ubicación") }
                    }
                    Screen.FOLDERS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Header(current?.name ?: "Carpetas", "Solo las ubicaciones que autorizaste") }
                            TextButton(onClick = { pickFolder.launch(null) }) { Icon(Icons.Default.Add, null); Text("Ubicación") }
                        }
                        if (currentUri != null && currentUri != rootUri) {
                            TextButton(onClick = {
                                val parent = current?.parentFile
                                if (parent != null) { current = parent; currentUri = parent.uri } else { current = root; currentUri = rootUri }
                                refresh++
                            }) { Icon(Icons.Default.ArrowBack, null); Text("Volver") }
                        }
                        SearchBox(query) { query = it }
                        Text(entries.size.toString() + " elementos" + if (filteringFiles) " · filtrando…" else "", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                        if (listingFailed) EmptyNotice("No se pudo leer esta carpeta", "Comprueba los permisos de Android.", Icons.Default.FolderOff)
                        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(folderEntries, key = { it.uri.toString() }) { file ->
                                DocumentRow(file, {
                                    if (file.isDirectory) { current = file; currentUri = file.uri; refresh++ }
                                    else requestOpen(file, entries)
                                }, { actionFile=file; deleteDialog=true })
                            }
                        }
                    }
                    Screen.CLEAN -> {
                        Header("Galería de limpieza inteligente", "Revisa cada archivo antes de borrarlo")
                        Spacer(Modifier.height(10.dp))
                        DiskCard(disk, root?.name ?: "Ubicación elegida", activePalette.primary) { pickFolder.launch(null) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ManageSearch, null)
                            Spacer(Modifier.width(8.dp)); Text(if (scanning) "Analizando ubicación..." else "Volver a analizar ubicación")
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = activeCleanupMode == CleanupMode.SCREENSHOTS,
                                onClick = { cleanupModeName = CleanupMode.SCREENSHOTS.name; cleanupSelectedUris = emptySet() },
                                label = { Text("Capturas") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = activeCleanupMode == CleanupMode.DUPLICATES,
                                onClick = { cleanupModeName = CleanupMode.DUPLICATES.name; cleanupSelectedUris = emptySet() },
                                label = { Text("Duplicados") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = activeCleanupMode == CleanupMode.VIDEOS,
                                onClick = { cleanupModeName = CleanupMode.VIDEOS.name; cleanupSelectedUris = emptySet() },
                                label = { Text("Vídeos grandes") },
                                modifier = Modifier.weight(1f)
                            )
                            if (activeCleanupMode == CleanupMode.DUPLICATES) {
                                OutlinedButton(
                                    onClick = { analyzeDuplicates() },
                                    enabled = !duplicateScanRunning && hasScanned,
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    if (duplicateScanRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Fingerprint, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text(if (duplicateScanRunning) "Comparando..." else "Comparar contenido")
                                }
                            } else {
                                Text(
                                    if (activeCleanupMode == CleanupMode.SCREENSHOTS) "Más de 90 días" else "Desde 100 MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1.3f).align(Alignment.CenterVertically)
                                )
                            }
                        }
                        Text(
                            when (activeCleanupMode) {
                                CleanupMode.SCREENSHOTS -> "Capturas de pantalla con más de 90 días. Si Android no informa la fecha, se dejan fuera."
                                CleanupMode.DUPLICATES -> "Solo se agrupan copias con el mismo tamaño y SHA-256 del contenido completo. El nombre no decide si son duplicados."
                                CleanupMode.VIDEOS -> "Vídeos de 100 MB o más, ordenados del más grande al más pequeño."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SearchBox(query) { query = it }
                        if (scanning) Text("Explorando: $scanProgress elementos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateScanRunning) {
                            LinearProgressIndicator(
                                progress = { if (duplicateScanTotal == 0) 0f else duplicateScanProgress.toFloat() / duplicateScanTotal.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Leyendo contenido: $duplicateScanProgress / $duplicateScanTotal archivos comparados", style = MaterialTheme.typography.labelSmall)
                        }
                        if (scanStatus.isNotBlank()) Text(scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateScanStatus.isNotBlank()) {
                            Text(duplicateScanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (cleanupFiles.isEmpty() && activeCleanupMode != CleanupMode.DUPLICATES) {
                            EmptyNotice(
                                if (activeCleanupMode == CleanupMode.SCREENSHOTS) "No encontramos capturas antiguas" else "No encontramos vídeos grandes",
                                "Prueba otra ubicación o vuelve a analizar los archivos.",
                                if (activeCleanupMode == CleanupMode.SCREENSHOTS) Icons.Default.ImageSearch else Icons.Default.Movie
                            )
                        }
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateDisplayGroups.isEmpty() && !duplicateScanRunning) {
                            EmptyNotice(
                                if (duplicateScanStatus.isBlank()) "Compara los archivos para empezar" else "No hay duplicados confirmados",
                                "Se comprueba el contenido completo. No se borra nada durante el análisis.",
                                Icons.Default.Fingerprint
                            )
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (activeCleanupMode == CleanupMode.DUPLICATES) {
                                items(duplicateDisplayGroups, key = { it.hash }) { group ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                            Text("${group.files.size} copias con contenido idéntico", fontWeight = FontWeight.Bold)
                                            Text("SHA-256: ${group.hash.take(20)}… · ${formatBytes(group.files.firstOrNull()?.length() ?: 0L)} cada una",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            group.files.forEach { file ->
                                                CleanupCandidateRow(
                                                    file = file,
                                                    checked = file.uri.toString() in cleanupSelectedUris,
                                                    subtitle = "${formatBytes(file.length())} · ${cleanupLocation(file)}",
                                                    onChecked = { checked ->
                                                        cleanupSelectedUris = if (checked) cleanupSelectedUris + file.uri.toString()
                                                        else cleanupSelectedUris - file.uri.toString()
                                                    },
                                                    onOpen = { requestOpen(file, sourceFiles) }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(cleanupFiles, key = { it.uri.toString() }) { file ->
                                    val subtitle = when (activeCleanupMode) {
                                        CleanupMode.SCREENSHOTS -> "Captura · ${formatDate(file.lastModified())} · ${formatBytes(file.length())} · ${cleanupLocation(file)}"
                                        CleanupMode.VIDEOS -> "Vídeo pesado · ${formatBytes(file.length())} · ${cleanupLocation(file)}"
                                        else -> formatBytes(file.length())
                                    }
                                    CleanupCandidateRow(
                                        file = file,
                                        checked = file.uri.toString() in cleanupSelectedUris,
                                        subtitle = subtitle,
                                        onChecked = { checked ->
                                            cleanupSelectedUris = if (checked) cleanupSelectedUris + file.uri.toString()
                                            else cleanupSelectedUris - file.uri.toString()
                                        },
                                        onOpen = { requestOpen(file, sourceFiles) }
                                    )
                                }
                            }
                        }
                        if (selectedCleanupFiles.isNotEmpty()) {
                            Button(
                                onClick = { requestBulkDelete() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Revisar borrado · ${selectedCleanupFiles.size} archivo(s) · ${formatBytes(selectedCleanupBytes)}")
                            }
                        }
                    }
                }
            }
            if (showAppearance) AlertDialog(
                onDismissRequest = { showAppearance = false },
                title = { Text("Personalizar Archivero") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Color de la aplicación", fontWeight = FontWeight.Bold)
                        Palette.values().forEach { palette ->
                            Row(Modifier.fillMaxWidth().clickable { selectedPaletteName = palette.name }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp).background(palette.primary, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(palette.label, modifier = Modifier.weight(1f))
                                if (selectedPaletteName == palette.name) Icon(Icons.Default.CheckCircle, null, tint = palette.primary)
                            }
                        }
                        OutlinedButton(onClick = { pickWallpaper.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Elegir una imagen de fondo")
                        }
                        if (wallpaperUri != null) TextButton(onClick = { wallpaperUri = null; wallpaperBitmap = null }) { Text("Quitar imagen de fondo") }
                    }
                },
                confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Listo") } }
            )
        }
    }
}

    if (fileOperationRunning) AlertDialog(
        onDismissRequest = { },
        title = { Text("Procesando archivos") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("La operación se realiza en segundo plano. Los archivos grandes pueden tardar.")
            }
        },
        confirmButton = { }
    )

    if (newFolderDialog) AlertDialog(
        onDismissRequest = { newFolderDialog = false }, title = { Text("Nueva carpeta") },
        text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Nombre de la carpeta") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val result = runCatching {
                require(folderName.trim().isNotEmpty()) { "Escribe un nombre para la carpeta." }
                val parent = current ?: error("No hay una carpeta abierta.")
                if (parent.findFile(folderName.trim()) != null) false else parent.createDirectory(folderName.trim()) != null
            }
            message = result.fold({ if (it) { refresh++; "Carpeta creada." } else "Ya existe o Android no permitió crearla." }, { it.message ?: "No se pudo crear la carpeta." })
            newFolderDialog = false
        }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { newFolderDialog = false }) { Text("Cancelar") } }
    )

    if (actionMenu && actionFile != null) AlertDialog(
        onDismissRequest = { actionMenu = false },
        title = { Text(actionFile?.name ?: "Acciones del archivo") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { renameValue = actionFile?.name.orEmpty(); renameDialog = true; actionMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DriveFileRenameOutline, null); Spacer(Modifier.width(8.dp)); Text("Renombrar") }
            TextButton(onClick = {
                val file = actionFile
                val parent = current
                if (fileOperationRunning) {
                    message = "Ya hay una operación de archivos en curso."
                } else if (file == null || parent == null) {
                    message = "Selecciona un archivo y una carpeta de destino."
                } else {
                    fileOperationRunning = true
                    actionMenu = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FileOperations.copy(context, file, parent) }
                        }
                        fileOperationRunning = false
                        message = result.fold(
                            { refresh++; "Copia creada: " + it },
                            { it.message ?: "No se pudo copiar el archivo." }
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copiar a carpeta abierta") }
            TextButton(onClick = {
                val file = actionFile
                val parent = current
                if (fileOperationRunning) {
                    message = "Ya hay una operación de archivos en curso."
                } else if (file == null || parent == null) {
                    message = "Selecciona un archivo y una carpeta de destino."
                } else {
                    fileOperationRunning = true
                    actionMenu = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FileOperations.move(context, file, parent) }
                        }
                        fileOperationRunning = false
                        message = result.fold(
                            { refresh++; indexed = indexed.filterNot { item -> item.uri == file.uri }; "Archivo movido: " + it },
                            { it.message ?: "No se pudo mover el archivo." }
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DriveFileMove, null); Spacer(Modifier.width(8.dp)); Text("Mover a carpeta abierta") }
            TextButton(onClick = {
                val file = actionFile
                val parent = current
                if (fileOperationRunning) {
                    message = "Ya hay una operación de archivos en curso."
                } else if (file == null || parent == null) {
                    message = "Selecciona un archivo y una carpeta de destino."
                } else {
                    fileOperationRunning = true
                    actionMenu = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FileOperations.compress(context, file, parent) }
                        }
                        fileOperationRunning = false
                        message = result.fold(
                            { refresh++; "Archivo comprimido: " + it },
                            { it.message ?: "No se pudo comprimir." }
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Comprimir en ZIP") }
            TextButton(onClick = {
                val file = actionFile
                val parent = current
                if (fileOperationRunning) {
                    message = "Ya hay una operación de archivos en curso."
                } else if (file == null || parent == null) {
                    message = "Selecciona un archivo ZIP y una carpeta de destino."
                } else {
                    fileOperationRunning = true
                    actionMenu = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FileOperations.extractZip(context, file, parent) }
                        }
                        fileOperationRunning = false
                        message = result.fold(
                            { count -> refresh++; "Extracción terminada: " + count + " archivos." },
                            { it.message ?: "No se pudo extraer el ZIP." }
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Extraer ZIP aquí") }
            TextButton(onClick = { deleteDialog = true; actionMenu = false }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { actionMenu = false }) { Text("Cerrar") } }
    )

    if (renameDialog && actionFile != null) AlertDialog(
        onDismissRequest = { renameDialog = false },
        title = { Text("Renombrar archivo") },
        text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Nuevo nombre") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val file = actionFile
            val result = runCatching {
                val name = renameValue.trim()
                require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/')) { "Escribe un nombre válido." }
                require(file != null) { "No hay archivo seleccionado." }
                require(file.renameTo(name)) { "Android no permitió renombrar. Puede faltar permiso." }
                name
            }
            message = result.fold({ refresh++; indexed = indexed.toList(); "Renombrado a: $it" }, { it.message ?: "No se pudo renombrar." })
            renameDialog = false
        }) { Text("Renombrar") } },
        dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancelar") } }
    )

    if (bulkDeleteDialog) AlertDialog(
        onDismissRequest = { bulkDeleteDialog = false },
        title = { Text("Revisar limpieza") },
        text = {
            Text("Vas a eliminar ${selectedCleanupFiles.size} archivo(s) seleccionados (${formatBytes(selectedCleanupBytes)}). Se revisarán los elementos marcados; esta acción puede ser permanente. Archivero nunca borra candidatos automáticamente.")
        },
        confirmButton = {
            TextButton(onClick = { confirmBulkDelete() }) { Text("Confirmar eliminación", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { bulkDeleteDialog = false }) { Text("Seguir revisando") } }
    )

    if (deleteDialog && actionFile != null) AlertDialog(
        onDismissRequest = { deleteDialog = false }, title = { Text("Eliminar elemento") },
        text = { Text("¿Eliminar " + (actionFile?.name ?: "este archivo") + "? La operación puede ser permanente. Revisa el nombre antes de continuar.") },
        confirmButton = { TextButton(onClick = {
            val uri = actionFile?.uri
            val result = runCatching { actionFile?.delete() == true }
            message = result.fold({ if (it) { refresh++; indexed = indexed.filterNot { f -> f.uri == uri }; "Elemento eliminado." } else "Android no permitió eliminarlo. Puede faltar permiso." }, { "No se pudo eliminar. Comprueba los permisos." })
            deleteDialog = false
        }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } }
    )

    if (showPlayerPanel) Dialog(
        onDismissRequest = { showPlayerPanel = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (playerVideo && playerUri != null) {
            Surface(Modifier.fillMaxSize(), color = activePalette.background) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reproductor de vídeo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(playerTitle, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { showPlayerPanel = false }) { Icon(Icons.Default.KeyboardArrowDown, "Minimizar reproductor") }
                    }
                    val controller = mediaController
                    if (controller == null) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    } else AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { player = controller; useController = true } },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        update = { view -> view.player = controller }
                    )
                    if (playbackError != null) {
                        Text("No se pudo reproducir: " + playbackError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { pickVideo.launch(arrayOf("video/*")) }) { Text("Elegir otro vídeo") }
                    }
                    TextButton(onClick = { showPlayerPanel = false }) { Text("Minimizar") }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).heightIn(max = 720.dp),
                    color = activePalette.background,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reproductor de música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Archivero · la música no se corta al minimizar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showPlayerPanel = false }) {
                                Icon(Icons.Default.KeyboardArrowDown, "Minimizar reproductor sin detener la música")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().height(76.dp).background(activePalette.primary.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.MusicNote, null, tint = activePalette.primary, modifier = Modifier.size(62.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(playerTitle.ifBlank { "Elige una canción" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { playbackUri?.let { toggleFavorite(it) } }, enabled = playbackUri != null) {
                                Icon(
                                    if (playbackUri?.toString() in favoriteUris) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    "Añadir o quitar de favoritos",
                                    tint = if (playbackUri?.toString() in favoriteUris) Color(0xFFD64D68) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }) {
                                Icon(Icons.Default.QueueMusic, null); Spacer(Modifier.width(6.dp)); Text("Añadir temas")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { showQueue = !showQueue }, enabled = playerQueueItems.isNotEmpty()) {
                                Icon(Icons.Default.PlaylistPlay, "Mostrar lista de reproducción")
                            }
                        }
                        Text(
                            if (loadingQueue) "Preparando canciones sin bloquear la aplicación…" else if (playerQueueItems.isEmpty()) "Elige canciones del teléfono" else "Lista de reproducción · ${playerQueueItems.size} canciones",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (playbackError != null) {
                            Text("No se pudo reproducir: $playbackError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        mediaController?.let { controller ->
                                            if (controller.hasNextMediaItem()) {
                                                controller.seekToNextMediaItem()
                                                controller.prepare()
                                                controller.play()
                                            }
                                        }
                                    },
                                    enabled = mediaController?.hasNextMediaItem() == true
                                ) { Text("Probar siguiente pista") }
                                TextButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }) { Text("Elegir otra") }
                            }
                        }
                        if (showQueue && playerQueueItems.isNotEmpty()) {
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 136.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(playerQueueItems, key = { it.localConfiguration?.uri.toString() }) { item ->
                                    TextButton(
                                        onClick = {
                                            val uri = item.localConfiguration?.uri ?: return@TextButton
                                            playbackUri = uri
                                            playerUri = uri
                                            playerTitle = item.mediaMetadata.title?.toString() ?: "Pista"
                                            playerVideo = false
                                            showPlayerPanel = true
                                            playbackRequestId++
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.MusicNote, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(item.mediaMetadata.title?.toString() ?: "Pista", maxLines = 1, modifier = Modifier.weight(1f))
                                        if (item.localConfiguration?.uri == playbackUri) Icon(Icons.Default.Equalizer, null)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = {
                                sleepTimerMinutes = when (sleepTimerMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
                            }) {
                                Icon(Icons.Default.Bedtime, null); Spacer(Modifier.width(6.dp))
                                Text(if (sleepTimerMinutes == 0) "Temporizador apagado" else "Pausa en $sleepTimerMinutes min")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val fraction = if (playbackDuration > 0L) (playbackPosition.toFloat() / playbackDuration).coerceIn(0f, 1f) else 0f
                        Slider(value = fraction,
                            onValueChange = { v -> if (playbackDuration > 0L) playbackPosition = (playbackDuration * v).toLong() },
                            onValueChangeFinished = { mediaController?.seekTo(playbackPosition) },
                            modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(playbackPosition), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(playbackDuration), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }) {
                                Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (shuffleEnabled) activePalette.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { mediaController?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Canción anterior", modifier = Modifier.size(30.dp)) }
                            FilledIconButton(onClick = { mediaController?.let { controller -> if (controller.isPlaying) controller.pause() else controller.play() } },
                                modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = activePalette.primary, contentColor = Color.White)) {
                                Icon(if (playbackIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (playbackIsPlaying) "Pausar" else "Reproducir", modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = { mediaController?.seekToNext() }) { Icon(Icons.Default.SkipNext, "Canción siguiente", modifier = Modifier.size(30.dp)) }
                            IconButton(onClick = {
                                mediaController?.let { controller -> controller.repeatMode = when (controller.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                } }
                            }) {
                                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    "Cambiar repetición", tint = if (repeatMode != Player.REPEAT_MODE_OFF) activePalette.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Desliza hacia abajo o toca la flecha para minimizar. La reproducción sigue activa y queda disponible en la notificación multimedia de Android.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    pendingOpenRequest?.let { request ->
        val nativeAvailable = canOpenNatively(context, request.file)
        AlertDialog(
            onDismissRequest = { pendingOpenRequest = null },
            title = { Text("¿Cómo quieres abrirlo?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(request.file.name ?: "Archivo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = nativeAvailable) { selectedOpenMethod = "ARCHIVERO" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOpenMethod == "ARCHIVERO",
                            onClick = { selectedOpenMethod = "ARCHIVERO" },
                            enabled = nativeAvailable
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Abrir con Archivero", fontWeight = FontWeight.SemiBold,
                                color = if (nativeAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (nativeAvailable) "Vista previa o reproductor integrado" else "Archivero no tiene un visor para este formato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedOpenMethod = "EXTERNAL" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOpenMethod == "EXTERNAL", onClick = { selectedOpenMethod = "EXTERNAL" })
                        Column(Modifier.weight(1f)) {
                            Text("Elegir aplicación instalada", fontWeight = FontWeight.SemiBold)
                            Text("Android mostrará las aplicaciones compatibles y podrá recordar la elegida",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingOpenRequest = null }) { Text("Cancelar") } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { confirmOpenChoice(request, selectedOpenMethod, false) }) { Text("Solo esta vez") }
                    Button(onClick = { confirmOpenChoice(request, selectedOpenMethod, true) }) { Text("Usar siempre") }
                }
            }
        )
    }

    message?.let { msg -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Archivero") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } }) }
}

@Composable
private fun DiskCard(disk: Disk?, location: String, accent: Color = Color(0xFF285E55), onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = accent), shape = RoundedCornerShape(23.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Almacenamiento del dispositivo", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                    Text(location, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = onChange) { Icon(Icons.Default.SwapHoriz, "Cambiar ubicación", tint = Color.White) }
            }
            if (disk == null) Text("No se pudo consultar el espacio del dispositivo.", color = Color.White)
            else {
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatBytes(disk.used), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp)); Text("usados", color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f)); Text((disk.used.toDouble() / disk.total.coerceAtLeast(1) * 100).toInt().toString() + "%", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (disk.used.toFloat() / disk.total.coerceAtLeast(1)).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFFBCE8D6), trackColor = Color.White.copy(alpha = .22f))
                Spacer(Modifier.height(8.dp))
                Row { Text(formatBytes(disk.free) + " libres", color = Color.White.copy(alpha = .9f)); Spacer(Modifier.weight(1f)); Text(formatBytes(disk.total) + " total", color = Color.White.copy(alpha = .9f)) }
            }
        }
    }
}
@Composable private fun Header(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun CategoryTile(kind: Kind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            KindIcon(kind, 40); Spacer(Modifier.width(9.dp))
            Column { Text(kind.title, fontWeight = FontWeight.Bold); Text("Explorar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun KindIcon(kind: Kind, size: Int) {
    Box(Modifier.size(size.dp).background(kind.tint.copy(alpha = .12f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        Icon(kind.icon, null, tint = kind.tint, modifier = Modifier.size((size * .52f).dp))
    }
}
@Composable private fun ActionTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Icon(icon, null, tint = Color(0xFF285E55), modifier = Modifier.size(25.dp)); Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SearchBox(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(query, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar por nombre...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Limpiar búsqueda") } })
}
@Composable private fun DocumentRow(file: DocumentFile, onClick: () -> Unit, onMore: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            val folder = file.isDirectory
            FileThumbnail(file = file, size = 52)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name ?: "Sin nombre", fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (folder) "Carpeta" else fileMetadataLabel(file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Más acciones") }
        }
    }
}
@Composable private fun FileList(files: List<DocumentFile>, onOpen: (DocumentFile) -> Unit, onMore: (DocumentFile) -> Unit, modifier: Modifier = Modifier) {
    if (files.isEmpty()) EmptyNotice("No encontramos archivos", "Prueba otra categoría o analiza la ubicación seleccionada.", Icons.Default.SearchOff)
    else LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { items(files, key = { it.uri.toString() }) { file -> DocumentRow(file, { onOpen(file) }, { onMore(file) }) } }
}
@Composable private fun EmptyNotice(title: String, subtitle: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun CleanupCandidateRow(
    file: DocumentFile,
    checked: Boolean,
    subtitle: String,
    onChecked: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        FileThumbnail(file = file, size = 44)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            Text(file.name ?: "Sin nombre", fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        IconButton(onClick = onOpen) { Icon(Icons.Default.Visibility, "Revisar archivo") }
    }
}
private fun fileMetadataLabel(file: DocumentFile): String {
    if (file.isDirectory) return "Carpeta"
    val extension = file.name.orEmpty().substringAfterLast('.', "").uppercase(Locale.ROOT)
    val size = formatBytes(file.length())
    return if (extension.isBlank()) size else "$extension · $size"
}

@Composable
private fun FileThumbnail(file: DocumentFile, size: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = remember(file.uri.toString(), file.name, file.lastModified(), file.length()) {
        file.uri.toString() + "|" + file.length() + "|" + file.lastModified()
    }
    var bitmap by remember(key) { mutableStateOf(ThumbnailCache.getCached(key)) }

    LaunchedEffect(key) {
        bitmap = withContext(Dispatchers.IO) { ThumbnailCache.load(context, file, key) }
    }

    val folder = file.isDirectory
    val extension = file.name.orEmpty().substringAfterLast('.', "").uppercase(Locale.ROOT).take(5)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(if (folder) Color(0xFFE2EFE8) else Color(0xFFE9EDF4)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !folder) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Miniatura de " + (file.name ?: "archivo"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(
                    if (folder) Icons.Default.Folder else fileIcon(file),
                    contentDescription = null,
                    tint = if (folder) Color(0xFF285E55) else Color(0xFF52657D),
                    modifier = Modifier.size((size * 0.48f).dp)
                )
                if (!folder && extension.isNotBlank()) {
                    Text(extension, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

private object ThumbnailCache : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)

    fun getCached(key: String): Bitmap? = get(key)

    fun load(context: Context, file: DocumentFile, key: String): Bitmap? {
        get(key)?.let { return it }
        if (file.isDirectory) return null

        val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val decoded = runCatching {
            when {
                extension in setOf("jpg", "jpeg", "jpe", "jfif", "jps", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff") ->
                    decodeImageThumbnail(context, file.uri)
                extension == "pdf" -> decodePdfThumbnail(context, file.uri)
                else -> null
            }
        }.getOrNull()
        if (decoded != null && !decoded.isRecycled) put(key, decoded)
        return decoded
    }

    private fun decodeImageThumbnail(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path, bounds)
        else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 192) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = if (uri.scheme == "file") {
            BitmapFactory.decodeFile(uri.path, options)
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } ?: return null

        val maxDimension = maxOf(decoded.width, decoded.height)
        if (maxDimension <= 192) return decoded
        val ratio = 192f / maxDimension
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun decodePdfThumbnail(context: Context, uri: Uri): Bitmap? {
        val descriptor = if (uri.scheme == "file") {
            ParcelFileDescriptor.open(File(uri.path ?: return null), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: return null
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount < 1) return null
                renderer.openPage(0).use { page ->
                    val scale = minOf(192f / page.width.coerceAtLeast(1), 192f / page.height.coerceAtLeast(1)).coerceAtMost(1f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val thumbnail = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(thumbnail).drawColor(android.graphics.Color.WHITE)
                    page.render(thumbnail, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return thumbnail
                }
            }
        }
    }
}

private fun sha256Content(context: Context, file: DocumentFile): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = context.contentResolver.openInputStream(file.uri)
        ?: throw IOException("Android no pudo leer el archivo")
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
private fun isScreenshot(file: DocumentFile): Boolean {
    val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext !in Kind.IMAGES.extensions) return false
    val metadata = "${file.name.orEmpty()} ${file.uri}".lowercase(Locale.ROOT)
    return listOf("screenshot", "screen_shot", "screen-shot", "screen capture", "captura", "screenshots").any { it in metadata }
}
private fun isOldScreenshot(file: DocumentFile): Boolean {
    val modified = file.lastModified()
    val cutoff = System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L
    return isScreenshot(file) && modified > 0L && modified < cutoff
}
private fun cleanupLocation(file: DocumentFile): String {
    val raw = Uri.decode(file.uri.toString())
    val afterPrimary = raw.substringAfter("primary:", "")
    val afterExternal = raw.substringAfter("external:", "")
    val tail = when {
        afterPrimary.isNotBlank() -> afterPrimary
        afterExternal.isNotBlank() -> afterExternal
        else -> raw.substringAfterLast('/')
    }
    val parent = tail.substringBeforeLast('/').trim('/')
    return if (parent.isBlank()) "Ubicación autorizada" else "Carpeta: ${parent.takeLast(58)}"
}
private fun formatDate(milliseconds: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(milliseconds))
private fun openPreferenceKey(context: Context, file: DocumentFile): String {
    val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = runCatching { context.contentResolver.getType(file.uri) }.getOrNull().orEmpty()
    val keyPart = extension.ifBlank { mime.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_") }.ifBlank { "unknown" }
    return "open_with_$keyPart"
}
private fun savedOpenMethod(context: Context, file: DocumentFile): String? {
    return context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
        .getString(openPreferenceKey(context, file), null)
        ?.takeIf { it == "ARCHIVERO" || it == "EXTERNAL" }
}
private fun canOpenNatively(context: Context, file: DocumentFile): Boolean {
    val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = runCatching { context.contentResolver.getType(file.uri) }.getOrNull().orEmpty()
    return extension in Kind.IMAGES.extensions ||
        extension in Kind.DOCS.extensions ||
        extension in setOf("md", "json", "xml", "html", "htm", "log") ||
        extension in Kind.MUSIC.extensions ||
        extension in Kind.VIDEOS.extensions ||
        mime.startsWith("audio/") || mime.startsWith("video/")
}
private fun openDocument(file: DocumentFile, context: Context, useNative: Boolean, onMedia: (Uri, String, Boolean) -> Unit, onError: (String) -> Unit) {
    val name = file.name ?: "archivo sin nombre"
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val extensionMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    val providedMime = runCatching { context.contentResolver.getType(file.uri) }.getOrNull().orEmpty()
    val mime = providedMime.ifBlank { extensionMime }
    val isAudio = extension in Kind.MUSIC.extensions || mime.startsWith("audio/")
    val isVideo = extension in Kind.VIDEOS.extensions || mime.startsWith("video/")

    if (useNative && (isAudio || isVideo)) {
        onMedia(file.uri, name, isVideo)
        return
    }

    val internallyPreviewable = extension in Kind.IMAGES.extensions ||
        extension in Kind.DOCS.extensions ||
        extension in setOf("md", "json", "xml", "html", "htm", "log")

    if (useNative && internallyPreviewable) {
        runCatching {
            val intent = Intent(context, FilePreviewActivity::class.java)
                .setData(file.uri)
                .putExtra("displayName", name)
                .putExtra("mimeType", mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }.onFailure {
            onError("No se pudo abrir la vista previa de «" + name + "». Comprueba los permisos del archivo e inténtalo otra vez.")
        }
        return
    }

    runCatching {
        val safeUri = uriForExternalViewer(context, file.uri)
        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(safeUri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewIntent.clipData = ClipData.newUri(context.contentResolver, name, safeUri)
        // Use Android's resolver directly so it can offer "Solo esta vez" / "Siempre".
        context.startActivity(viewIntent)
    }.onFailure { error ->
        onError(when (error) {
            is SecurityException -> "Android negó el permiso para abrir «" + name + "». Vuelve a elegir su carpeta."
            else -> "No hay una aplicación compatible para abrir «" + name + "» (" + mime + "). La vista previa integrada cubre imágenes, PDF y documentos de texto comunes."
        })
    }
}
internal fun uriForExternalViewer(context: Context, uri: Uri): Uri {
    if (uri.scheme != "file") return uri
    val path = uri.path ?: throw IOException("La ruta del archivo no está disponible.")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path)
    )
}

private fun displayName(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        ?: "Pista"
}

private fun fileIcon(file: DocumentFile): ImageVector {
    val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        ext in Kind.IMAGES.extensions -> Icons.Default.Image
        ext in Kind.VIDEOS.extensions -> Icons.Default.Movie
        ext in Kind.MUSIC.extensions -> Icons.Default.MusicNote
        ext in Kind.DOCS.extensions -> Icons.Default.Description
        ext in Kind.APK.extensions -> Icons.Default.Android
        ext in Kind.ZIP.extensions -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}
private fun isJunk(file: DocumentFile): Boolean {
    val name = file.name.orEmpty().lowercase(Locale.ROOT)
    val ext = name.substringAfterLast('.', "")
    return ext in setOf("tmp","temp","log","bak","old","cache","dmp") || name.startsWith(".") || name.contains("thumb")
}
private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60L, seconds % 60L)
}
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> bytes.toString() + " B"
    bytes < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
