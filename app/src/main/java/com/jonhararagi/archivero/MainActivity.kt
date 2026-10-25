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
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

private const val MAX_LIBRARY_FAVORITES = 5_000
private enum class Screen { HOME, TYPES, FOLDERS, CLEAN, LIBRARY }
private enum class CleanupMode { SCREENSHOTS, DUPLICATES, SIMILAR_IMAGES, VIDEOS }
private data class DuplicateGroup(val hash: String, val files: List<DocumentFile>)
internal data class SizedCandidate<T>(val value: T, val size: Long)

/** Keeps only unique, positive-sized items whose size is shared with another item. */
internal fun <T, K> potentialDuplicateCandidates(
    candidates: Iterable<SizedCandidate<T>>,
    keyOf: (T) -> K
): List<SizedCandidate<T>> {
    val unique = candidates.asSequence()
        .filter { it.size > 0L }
        .distinctBy { keyOf(it.value) }
        .toList()
    val duplicateSizes = unique.groupingBy { it.size }.eachCount()
        .filterValues { it > 1 }
        .keys
    return unique.filter { it.size in duplicateSizes }
}


internal object ImageGallerySession {
    var files: List<DocumentFile> = emptyList()
    var index: Int = 0
}
private data class PendingOpenRequest(
    val file: DocumentFile,
    val queue: List<DocumentFile>,
    val imageGalleryPrepared: Boolean = false
)
private data class CleanupGroupRow(val key: String, val files: List<DocumentFile>, val similar: Boolean)
private data class FilteredFileLists(
    val folders: List<DocumentFile>,
    val matching: List<DocumentFile>,
    val duplicates: List<DuplicateGroup>,
    val similar: List<SimilarImageGroup>,
    val cleanup: List<DocumentFile>
)
private data class LibrarySearchSignature(
    val query: String,
    val recordsIdentity: Int,
    val recordCount: Int,
    val category: String,
    val resourceKind: String,
    val project: String,
    val sortMode: String,
    val favoriteUris: Set<String>,
    val favoritesOnly: Boolean
)
private data class ScanResult(
    val files: List<DocumentFile>,
    val visited: Int,
    val partial: Boolean,
    val unreadableDirectories: Int,
    val directoryLimitHit: Boolean
)
private enum class Palette(val label: String, val primary: Color, val secondary: Color, val background: Color) {
    CAPIBARA("Capibara kawaii", Color(0xFF674840), Color(0xFFB65C61), Color(0xFFFFF7F1)),
    CIBERPUNK("Ciberpunk", Color(0xFF39D5F2), Color(0xFFE35AC8), Color(0xFF10172A)),
    VERDE("Verde bosque", Color(0xFF285E55), Color(0xFF547D73), Color(0xFFF4F7F5)),
    VIOLETA("Violeta", Color(0xFF7048A8), Color(0xFF9575CD), Color(0xFFF7F2FC)),
    ROSA("Rosa", Color(0xFFAD476C), Color(0xFFD981A0), Color(0xFFFFF4F7)),
    AZUL("Azul", Color(0xFF285DA8), Color(0xFF668CC6), Color(0xFFF2F6FD)),
    AMBAR("Ámbar", Color(0xFF93600B), Color(0xFFC28B2C), Color(0xFFFFF8E9))
}
private enum class Kind(val title: String, val icon: ImageVector, val tint: Color, val extensions: Set<String>) {
    IMAGES("Imágenes", Icons.Default.Image, Color(0xFFB65C61), setOf("jpg","jpeg","jpe","jfif","jps","png","webp","gif","bmp","heic","heif","avif","tif","tiff","svg")),
    VIDEOS("Videos", Icons.Default.Movie, Color(0xFF956858), setOf("mp4","mkv","mov","avi","webm","3gp","m4v")),
    MUSIC("Música", Icons.Default.MusicNote, Color(0xFFAA7A45), setOf("mp3","wav","aac","m4a","ogg","flac","opus","wma")),
    DOCS("Documentos", Icons.Default.Description, Color(0xFF7D6359), setOf("pdf","doc","docx","txt","rtf","odt","xls","xlsx","ppt","pptx","csv","md","json","xml","html","htm","log")),
    APK("APK", Icons.Default.Android, Color(0xFF8B7064), setOf("apk","xapk","apks")),
    ZIP("Comprimidos", Icons.Default.FolderZip, Color(0xFFB8874C), setOf("zip","rar","7z","tar","gz","bz2")),
    OTHER("Otros", Icons.Default.InsertDriveFile, Color(0xFF89776E), emptySet())
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

internal fun libraryDocument(context: Context, rawUri: String): DocumentFile? = runCatching {
    val uri = Uri.parse(rawUri)
    if (uri.scheme == "file") {
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return@runCatching null
        DocumentFile.fromFile(File(path))
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }
}.getOrNull()

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
    var libraryCatalog by remember(context) { mutableStateOf<DigitalLibraryCatalog?>(null) }
    var libraryCatalogLoading by remember(context) { mutableStateOf(true) }
    var libraryStorageError by remember { mutableStateOf<String?>(null) }
    var libraryRecords by remember { mutableStateOf<List<LibraryRecord>>(emptyList()) }
    var libraryTrashRecords by remember { mutableStateOf<List<TrashedLibraryRecord>>(emptyList()) }
    var libraryTrashDialog by remember { mutableStateOf(false) }
    var visibleLibraryRecords by remember { mutableStateOf<List<LibraryRecord>>(emptyList()) }
    var libraryMatchesTruncated by remember { mutableStateOf(false) }
    var libraryResultLimit by rememberSaveable { mutableIntStateOf(LIBRARY_RESULTS_PAGE_SIZE) }
    var previousLibrarySearchSignature by remember { mutableStateOf<LibrarySearchSignature?>(null) }
    var librarySearching by remember { mutableStateOf(false) }
    var libraryGridView by rememberSaveable { mutableStateOf(true) }
    var libraryCategoryFilter by rememberSaveable { mutableStateOf("") }
    var libraryResourceKindFilter by rememberSaveable { mutableStateOf("") }
    var libraryProjectFilter by rememberSaveable { mutableStateOf("") }
    var libraryProjectFilterMenuExpanded by remember { mutableStateOf(false) }
    var librarySortMode by rememberSaveable { mutableStateOf("Nombre") }
    var libraryFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var librarySortMenuExpanded by remember { mutableStateOf(false) }
    var libraryRecentSearches by remember(context) {
        mutableStateOf(
            context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                .getString("library_recent_searches", "")
                .orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        )
    }
    var libraryFavoriteUris by remember(context) {
        mutableStateOf(
            context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                .getStringSet("library_favorites", emptySet())?.toSet().orEmpty()
        )
    }

    fun reconcileLibraryFavorites(
        records: List<LibraryRecord>,
        trashed: List<TrashedLibraryRecord> = emptyList()
    ) {
        val knownUris = records.mapTo(HashSet()) { it.uri }
        trashed.forEach { knownUris.add(it.record.uri) }
        val retained = libraryFavoriteUris.intersect(knownUris)
        if (retained != libraryFavoriteUris) {
            libraryFavoriteUris = retained
            context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                .edit().putStringSet("library_favorites", retained).apply()
        }
    }
    var libraryEditDialog by remember { mutableStateOf(false) }
    var libraryEditUri by remember { mutableStateOf<String?>(null) }
    var libraryEditName by remember { mutableStateOf("") }
    var libraryEditCategory by remember { mutableStateOf("Otros") }
    var libraryEditTags by remember { mutableStateOf("") }
    var libraryEditNotes by remember { mutableStateOf("") }
    var libraryEditSource by remember { mutableStateOf("") }
    var libraryEditProject by remember { mutableStateOf("") }
    var libraryEditResourceKind by remember { mutableStateOf("Sin clasificar") }
    var libraryEditRelatedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var libraryRelatedQuery by remember { mutableStateOf("") }
    var libraryResourceKindExpanded by remember { mutableStateOf(false) }
    var libraryTransferRunning by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        libraryCatalogLoading = true
        val catalogResult = withContext(Dispatchers.IO) {
            runCatching { DigitalLibraryCatalog(File(context.filesDir, "digital-library.properties")) }
        }
        val catalog = catalogResult.getOrNull()
        if (catalog == null) {
            libraryStorageError = catalogResult.exceptionOrNull()?.message ?: "No se pudo abrir el índice de biblioteca."
        } else {
            libraryCatalog = catalog
            val recordsResult = withContext(Dispatchers.IO) {
                runCatching { catalog.recordsSnapshot() to catalog.trashed() }
            }
            recordsResult.fold(
                { (records, trash) ->
                    libraryRecords = records
                    libraryTrashRecords = trash
                    reconcileLibraryFavorites(records, trash)
                    libraryStorageError = null
                },
                { error -> libraryStorageError = error.message ?: "No se pudo leer el índice de biblioteca." }
            )
        }
        libraryCatalogLoading = false
    }

    val libraryRecordsByUri = remember(libraryRecords) { libraryRecords.associateBy { it.uri } }
    val libraryKnownUris = remember(libraryRecords) { libraryRecords.mapTo(HashSet()) { it.uri } }
    val libraryProjects = remember(libraryRecords) {
        libraryRecords.asSequence()
            .map { it.project.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
            .toList()
    }

    val savedPaletteName = context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).getString("palette", null)
    // Cyberpunk is the first-run identity; preserve any palette the user explicitly selected.
    val initialPaletteName = savedPaletteName ?: Palette.CIBERPUNK.name
    var selectedPaletteName by rememberSaveable { mutableStateOf(initialPaletteName) }
    val activePalette = runCatching { Palette.valueOf(selectedPaletteName) }.getOrDefault(Palette.CIBERPUNK)
    var showAppearance by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var wallpaperUri by rememberSaveable {
        mutableStateOf(context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).getString("wallpaper", null))
    }
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scanStatus by rememberSaveable { mutableStateOf("") }
    var scanProgress by remember { mutableIntStateOf(0) }
    var cleanupModeName by rememberSaveable { mutableStateOf(CleanupMode.SCREENSHOTS.name) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var similarImageGroups by remember { mutableStateOf<List<SimilarImageGroup>>(emptyList()) }
    var similarScanRunning by remember { mutableStateOf(false) }
    var similarScanStatus by rememberSaveable { mutableStateOf("") }
    var analyzeSimilarAfterScan by remember { mutableStateOf(false) }
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
    var folderHistory by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var root by remember { mutableStateOf<DocumentFile?>(null) }
    var current by remember { mutableStateOf<DocumentFile?>(null) }
    val folderQuickAccessLoad = remember(context) {
        runCatching {
            decodeFolderQuickAccess(
                context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                    .getString("folder_quick_access", null)
            )
        }
    }
    var folderQuickAccess by remember(context) {
        mutableStateOf(folderQuickAccessLoad.getOrDefault(emptyList()))
    }
    var folderQuickAccessError by remember(context) {
        mutableStateOf(folderQuickAccessLoad.exceptionOrNull()?.message)
    }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedKind by rememberSaveable { mutableStateOf(Kind.IMAGES.name) }
    var choosingKind by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query, libraryRecords, libraryCategoryFilter, libraryResourceKindFilter, libraryProjectFilter, librarySortMode, libraryFavoriteUris, libraryFavoritesOnly, libraryResultLimit) {
        librarySearching = true
        delay(150)
        val signature = LibrarySearchSignature(
            query = query,
            recordsIdentity = System.identityHashCode(libraryRecords),
            recordCount = libraryRecords.size,
            category = libraryCategoryFilter,
            resourceKind = libraryResourceKindFilter,
            project = libraryProjectFilter,
            sortMode = librarySortMode,
            favoriteUris = libraryFavoriteUris,
            favoritesOnly = libraryFavoritesOnly
        )
        val filterChanged = signature != previousLibrarySearchSignature
        previousLibrarySearchSignature = signature
        if (filterChanged && libraryResultLimit != LIBRARY_RESULTS_PAGE_SIZE) {
            // New filters/catalog should begin at the first page, not keep a stale later offset.
            libraryResultLimit = LIBRARY_RESULTS_PAGE_SIZE
            librarySearching = false
            return@LaunchedEffect
        }
        val resultLimit = if (filterChanged) LIBRARY_RESULTS_PAGE_SIZE else libraryResultLimit
        val requestedLimit = minOf(resultLimit + 1, MAX_LIBRARY_RESULT_LIMIT)
        val matches = withContext(Dispatchers.Default) {
            filterLibraryRecords(
                records = libraryRecords,
                query = query,
                category = libraryCategoryFilter,
                resourceKind = libraryResourceKindFilter,
                sortMode = librarySortMode,
                project = libraryProjectFilter,
                favorites = libraryFavoriteUris,
                onlyFavorites = libraryFavoritesOnly,
                limit = requestedLimit
            )
        }
        visibleLibraryRecords = matches.take(resultLimit)
        libraryMatchesTruncated = matches.size > resultLimit
        librarySearching = false
    }

    var refresh by remember { mutableIntStateOf(0) }
    var indexed by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listedEntries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var listingFailed by remember { mutableStateOf(false) }
    var listingErrorMessage by remember { mutableStateOf("Comprueba los permisos de Android e inténtalo otra vez.") }
    var listingTruncated by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
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
    fun requestOpen(file: DocumentFile, queue: List<DocumentFile>, imageGalleryPrepared: Boolean = false) {
        val saved = savedOpenMethod(context, file)
        if (saved == "EXTERNAL" || (saved == "ARCHIVERO" && !canOpenNatively(context, file))) {
            openDocument(file, context, false, { u, t, v -> startPlayback(u, t, v, queue) }, { message = it }, queue, imageGalleryPrepared)
            return
        }
        if (saved == "ARCHIVERO" && canOpenNatively(context, file)) {
            openDocument(file, context, true, { u, t, v -> startPlayback(u, t, v, queue) }, { message = it }, queue, imageGalleryPrepared)
            return
        }
        pendingOpenRequest = PendingOpenRequest(file, queue, imageGalleryPrepared)
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
            { message = it },
            request.queue,
            request.imageGalleryPrepared
        )
    }

    fun toggleFavorite(uri: Uri) {
        val next = favoriteUris.toMutableSet()
        if (!next.add(uri.toString())) next.remove(uri.toString())
        favoriteUris = next.toSet()
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE).edit().putStringSet("favorites", next).apply()
    }

    fun editLibraryMetadata(file: DocumentFile) {
        val catalog = libraryCatalog
        if (catalog == null) {
            message = "No se pudo abrir el índice de biblioteca. No se ha sobrescrito ni recreado."
            return
        }
        val uri = file.uri.toString()
        val existing = catalog.get(uri)
        val extension = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val detectedCategory = Kind.values().firstOrNull { extension in it.extensions }?.title ?: "Otros"
        libraryEditUri = uri
        libraryEditName = existing?.displayName ?: file.name.orEmpty().ifBlank { "Recurso sin nombre" }
        libraryEditCategory = existing?.category ?: detectedCategory
        libraryEditTags = existing?.tags?.joinToString(", ").orEmpty()
        libraryEditNotes = existing?.notes.orEmpty()
        libraryEditSource = existing?.source.orEmpty()
        libraryEditProject = existing?.project.orEmpty()
        libraryEditResourceKind = existing?.resourceKind ?: "Sin clasificar"
        libraryEditRelatedUris = existing?.relatedUris.orEmpty().toSet()
        libraryRelatedQuery = ""
        libraryEditDialog = true
        actionMenu = false
    }

    fun refreshLibrary() {
        val catalog = libraryCatalog ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { catalog.recordsSnapshot() to catalog.trashed() }
            }
            result.fold(
                { (records, trash) ->
                    libraryRecords = records
                    libraryTrashRecords = trash
                    reconcileLibraryFavorites(records, trash)
                    libraryStorageError = null
                },
                { error -> libraryStorageError = error.message ?: "No se pudo leer el índice de biblioteca." }
            )
        }
    }

    fun restoreLibraryRecord(uri: String) {
        val catalog = libraryCatalog ?: run {
            message = "El índice de biblioteca no está disponible."
            return
        }
        libraryTransferRunning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { catalog.restoreMetadata(uri) } }
            libraryTransferRunning = false
            result.fold(
                { restored ->
                    refreshLibrary()
                    message = if (restored) {
                        "Ficha restaurada en la Biblioteca. El archivo físico no se movió."
                    } else {
                        "La ficha ya no estaba en la papelera."
                    }
                },
                { error -> message = "No se pudo restaurar la ficha: ${error.message ?: "error de almacenamiento"}" }
            )
        }
    }

    fun purgeLibraryRecord(uri: String) {
        val catalog = libraryCatalog ?: run {
            message = "El índice de biblioteca no está disponible."
            return
        }
        libraryTransferRunning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { catalog.purgeTrashedMetadata(uri) } }
            libraryTransferRunning = false
            result.fold(
                { removed ->
                    refreshLibrary()
                    message = if (removed) {
                        "Ficha eliminada permanentemente de la papelera. El archivo físico no se borró."
                    } else {
                        "La ficha ya no estaba en la papelera."
                    }
                },
                { error -> message = "No se pudo quitar la ficha de la papelera: ${error.message ?: "error de almacenamiento"}" }
            )
        }
    }

    fun toggleLibraryFavorite(uri: String) {
        val next = libraryFavoriteUris.toMutableSet()
        if (!next.add(uri)) {
            next.remove(uri)
        } else if (next.size > MAX_LIBRARY_FAVORITES) {
            message = "La biblioteca admite hasta $MAX_LIBRARY_FAVORITES favoritos."
            return
        }
        libraryFavoriteUris = next.toSet()
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
            .edit().putStringSet("library_favorites", next).apply()
    }

    fun rememberLibrarySearch() {
        val term = query.trim().replace('\n', ' ')
        if (term.isBlank()) return
        val next = (listOf(term) + libraryRecentSearches.filterNot { it.equals(term, ignoreCase = true) }).take(8)
        libraryRecentSearches = next
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
            .edit().putString("library_recent_searches", next.joinToString("\n")).apply()
    }

    fun clearLibraryRecentSearches() {
        libraryRecentSearches = emptyList()
        context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
            .edit().remove("library_recent_searches").apply()
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
            runCatching { decodeWallpaperBitmap(context, Uri.parse(saved), 1600) }.getOrNull()
        }
    }
    val disk = remember(refresh) { Disk.read() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) { }
            rootUri = uri
            currentUri = uri
            folderHistory = emptyList()
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
    val exportLibrary = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { destination ->
        if (destination != null) {
            val catalog = libraryCatalog
            if (catalog == null) {
                message = "No se puede exportar porque el índice de biblioteca no está disponible."
            } else {
                libraryTransferRunning = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val snapshot = File.createTempFile("archivero-library-export-", ".properties", context.cacheDir)
                            try {
                                catalog.exportSnapshot(snapshot)
                                val output = context.contentResolver.openOutputStream(destination, "wt")
                                    ?: throw IOException("Android no permitió escribir el archivo de exportación.")
                                output.use { stream -> snapshot.inputStream().use { input -> input.copyTo(stream) } }
                                snapshot.length()
                            } finally {
                                snapshot.delete()
                            }
                        }
                    }
                    libraryTransferRunning = false
                    result.fold(
                        { bytes -> message = "Exportación completada: $bytes bytes de metadatos. Los archivos originales no se copiaron." },
                        { error -> message = "No se pudo exportar la biblioteca: ${error.message ?: "error de almacenamiento"}" }
                    )
                }
            }
        }
    }

    val importLibrary = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) {
            val catalog = libraryCatalog
            if (catalog == null) {
                message = "No se puede importar porque el índice de biblioteca no está disponible."
            } else {
                libraryTransferRunning = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val snapshot = File.createTempFile("archivero-library-import-", ".properties", context.cacheDir)
                            try {
                                val input = context.contentResolver.openInputStream(source)
                                    ?: throw IOException("Android no permitió leer el archivo seleccionado.")
                                input.use { incoming ->
                                    snapshot.outputStream().use { outgoing ->
                                        val buffer = ByteArray(8 * 1024)
                                        var totalBytes = 0L
                                        while (true) {
                                            val count = incoming.read(buffer)
                                            if (count < 0) break
                                            if (count == 0) continue
                                            totalBytes += count
                                            if (totalBytes > MAX_LIBRARY_SNAPSHOT_BYTES) {
                                                throw IOException("La instantánea supera el límite seguro de 64 MiB.")
                                            }
                                            outgoing.write(buffer, 0, count)
                                        }
                                    }
                                }
                                catalog.importSnapshot(snapshot)
                            } finally {
                                snapshot.delete()
                            }
                        }
                    }
                    libraryTransferRunning = false
                    result.fold(
                        { count ->
                            refreshLibrary()
                            message = "Importación completada: $count entrada(s) de biblioteca/papelera. Los conflictos se rechazan sin sobrescribir metadatos."
                        },
                        { error -> message = "No se importó la biblioteca: ${error.message ?: "archivo inválido o conflicto"}. La biblioteca activa se conserva." }
                    )
                }
            }
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
            folderHistory = emptyList()
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

    fun saveFolderQuickAccess(next: List<FolderQuickAccessEntry>): Boolean {
        if (folderQuickAccessError != null) {
            message = "Los accesos rápidos guardados están dañados. Restablécelos expresamente antes de modificarlos."
            return false
        }
        val serialized = runCatching { encodeFolderQuickAccess(next) }
        return serialized.fold(
            { value ->
                runCatching {
                    context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                        .edit().putString("folder_quick_access", value).apply()
                }.fold(
                    {
                        folderQuickAccess = next
                        true
                    },
                    { error ->
                        message = error.message ?: "No se pudieron guardar los accesos rápidos."
                        false
                    }
                )
            },
            { error ->
                message = error.message ?: "No se pudieron guardar los accesos rápidos."
                false
            }
        )
    }

    fun toggleCurrentFolderQuickAccess() {
        val folder = current
        if (folder == null || !runCatching { folder.isDirectory }.getOrDefault(false)) {
            message = "Abre una carpeta antes de guardarla en accesos rápidos."
            return
        }
        val uri = (currentUri ?: folder.uri).toString()
        val rootString = rootUri?.toString().orEmpty()
        if (uri.isBlank() || rootString.isBlank()) {
            message = "No se pudo determinar la ubicación autorizada de esta carpeta."
            return
        }
        val entry = FolderQuickAccessEntry(
            uri = uri,
            rootUri = rootString,
            displayName = folder.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Carpeta sin nombre"
        )
        val removing = folderQuickAccess.any { it.uri == entry.uri }
        runCatching { toggleFolderQuickAccess(folderQuickAccess, entry) }.fold(
            { updated ->
                if (saveFolderQuickAccess(updated)) {
                    message = if (removing) "Se quitó \"${entry.displayName}\" de accesos rápidos."
                    else "Se guardó \"${entry.displayName}\" en accesos rápidos."
                }
            },
            { error -> message = error.message ?: "No se pudo actualizar el acceso rápido." }
        )
    }

    fun openFolderQuickAccess(entry: FolderQuickAccessEntry) {
        try {
            val savedRootUri = Uri.parse(entry.rootUri)
            val savedFolderUri = Uri.parse(entry.uri)
            val savedRoot: DocumentFile
            val savedFolder: DocumentFile
            if (savedRootUri.scheme.equals("file", ignoreCase = true)) {
                if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess) {
                    message = "Android ya no permite el acceso al almacenamiento. Reactiva el permiso y vuelve a abrir el acceso rápido."
                    return
                }
                val rootPath = savedRootUri.path?.takeIf { it.isNotBlank() }
                    ?: throw IOException("La ruta raíz guardada no está disponible.")
                val folderPath = savedFolderUri.path?.takeIf { it.isNotBlank() }
                    ?: throw IOException("La ruta de la carpeta guardada no está disponible.")
                val rootFile = File(rootPath).canonicalFile
                val folderFile = File(folderPath).canonicalFile
                val rootPathCanonical = rootFile.toPath()
                val folderPathCanonical = folderFile.toPath()
                if (folderPathCanonical != rootPathCanonical && !folderPathCanonical.startsWith(rootPathCanonical)) {
                    throw IOException("La carpeta guardada no pertenece a la raíz autorizada. Se conservó el acceso rápido.")
                }
                savedRoot = DocumentFile.fromFile(rootFile)
                savedFolder = DocumentFile.fromFile(folderFile)
            } else {
                if (!DocumentsContract.isTreeUri(savedRootUri) || !DocumentsContract.isTreeUri(savedFolderUri)) {
                    throw IOException("La ubicación guardada no corresponde a un árbol de documentos de Android.")
                }
                val savedTreeId = runCatching { DocumentsContract.getTreeDocumentId(savedRootUri) }
                    .getOrElse { throw IOException("No se pudo validar la raíz SAF guardada.", it) }
                val folderTreeId = runCatching { DocumentsContract.getTreeDocumentId(savedFolderUri) }
                    .getOrElse { throw IOException("No se pudo validar la carpeta SAF guardada.", it) }
                if (savedTreeId != folderTreeId) {
                    throw IOException("La carpeta guardada no pertenece al árbol autorizado. Se conservó el acceso rápido.")
                }
                savedRoot = DocumentFile.fromTreeUri(context, savedRootUri)
                    ?: throw IOException("Android no pudo reconstruir la ubicación autorizada.")
                savedFolder = if (savedFolderUri == savedRootUri) {
                    savedRoot
                } else {
                    DocumentFile.fromSingleUri(context, savedFolderUri)
                        ?: throw IOException("Android no pudo reconstruir la carpeta guardada.")
                }
            }
            if (!savedRoot.exists() || !savedRoot.isDirectory || !savedRoot.canRead()) {
                throw IOException("No hay acceso a la ubicación raíz guardada. Selecciona la carpeta de nuevo.")
            }
            if (!savedFolder.exists() || !savedFolder.isDirectory || !savedFolder.canRead()) {
                throw IOException("La carpeta ya no está disponible o se revocó su permiso. El acceso rápido se conserva.")
            }
            rootUri = savedRootUri
            root = savedRoot
            current = savedFolder
            currentUri = if (savedFolderUri == savedRootUri) savedRootUri else savedFolder.uri
            folderHistory = emptyList()
            indexed = emptyList()
            hasScanned = false
            scanStatus = ""
            query = ""
            choosingKind = false
            screen = Screen.FOLDERS
            refresh++
        } catch (error: Exception) {
            message = error.message ?: "No se pudo abrir este acceso rápido. Comprueba el permiso de la carpeta."
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
        val requestedUri = currentUri
        val requestedRefresh = refresh
        listingFailed = false
        listingErrorMessage = "Comprueba los permisos de Android e inténtalo otra vez."
        listingTruncated = false
        loadingMore = false
        listedEntries = emptyList()
        val result: Result<BoundedDocumentListing> = withContext(Dispatchers.IO) {
            try {
                Result.success(
                    folderToList?.let {
                        listDocumentChildrenBounded(context, it, MAX_DIRECTORY_ENTRIES)
                    } ?: BoundedDocumentListing(emptyList(), false)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        if (currentUri != requestedUri || refresh != requestedRefresh) return@LaunchedEffect
        val listing = result.getOrNull()
        listedEntries = listing?.entries ?: emptyList()
        listingTruncated = listing?.truncated ?: false
        listingFailed = result.isFailure
        listingErrorMessage = result.exceptionOrNull()?.message
            ?: "Comprueba los permisos de Android e inténtalo otra vez."
    }
    val entries = listedEntries
    var folderEntries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var matchingFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var duplicateDisplayGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var similarDisplayGroups by remember { mutableStateOf<List<SimilarImageGroup>>(emptyList()) }
    var cleanupFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var filteringFiles by remember { mutableStateOf(false) }
    var filterRequestId by remember { mutableIntStateOf(0) }
    var sourceFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    LaunchedEffect(hasScanned, indexed, entries) {
        // The recursive scan already stores files only, so reuse that list without copying
        // or querying each provider again. Only the current-folder list needs type resolution.
        sourceFiles = if (hasScanned) {
            indexed
        } else {
            withContext(Dispatchers.IO) {
                entries.filter { file -> runCatching { !file.isDirectory }.getOrDefault(true) }
            }
        }
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
    LaunchedEffect(entries, sourceFiles, activeKind, query, duplicateGroups, similarImageGroups, activeCleanupMode, oldScreenshotFiles, largeVideoFiles) {
        filterRequestId++
        val request = filterRequestId
        filteringFiles = true
        try {
            val normalizedQuery = query.trim()
            val result = withContext(Dispatchers.IO) {
                val folders = entries.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                val matching = sourceFiles.asSequence().filter { file ->
                    val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val kindMatches = if (activeKind == Kind.OTHER) {
                        Kind.values().filter { it != Kind.OTHER }.none { ext in it.extensions }
                    } else ext in activeKind.extensions
                    kindMatches && file.name.orEmpty().contains(normalizedQuery, ignoreCase = true)
                }.distinctBy { it.uri.toString() }.let { found ->
                    if (activeKind == Kind.IMAGES) {
                        found.sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }.toList()
                    } else found.toList()
                }
                val duplicates = duplicateGroups.mapNotNull { group ->
                    val visible = group.files.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                    if (visible.isEmpty()) null else group.copy(files = visible)
                }
                val similars = similarImageGroups.mapNotNull { group ->
                    val visible = group.files.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                    if (visible.isEmpty()) null else group.copy(files = visible)
                }
                val cleanup = when (activeCleanupMode) {
                    CleanupMode.SCREENSHOTS -> oldScreenshotFiles
                    CleanupMode.DUPLICATES -> duplicates.flatMap { it.files }.distinctBy { it.uri.toString() }
                    CleanupMode.SIMILAR_IMAGES -> similars.flatMap { it.files }.distinctBy { it.uri.toString() }
                    CleanupMode.VIDEOS -> largeVideoFiles
                }.filter { it.name.orEmpty().contains(normalizedQuery, ignoreCase = true) }
                FilteredFileLists(folders, matching, duplicates, similars, cleanup)
            }
            if (request == filterRequestId) {
                folderEntries = result.folders
                matchingFiles = result.matching
                duplicateDisplayGroups = result.duplicates
                similarDisplayGroups = result.similar
                cleanupFiles = result.cleanup
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            if (request == filterRequestId) filteringFiles = false
        }
    }
    val selectedCleanupFiles = remember(cleanupFiles, cleanupSelectedUris) { cleanupFiles.filter { it.uri.toString() in cleanupSelectedUris } }

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
                    val maxEntries = MAX_SCAN_ENTRIES
                    val maxDepth = 20
                    var directoryLimitHit = false
                    while (queue.isNotEmpty() && visited < maxEntries) {
                        val item = queue.removeFirst()
                        val childListing = try {
                            listDocumentChildrenBounded(context, item.first, MAX_DIRECTORY_ENTRIES)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            unreadableDirectories++
                            null
                        }
                        if (childListing == null) continue
                        if (childListing.truncated) directoryLimitHit = true
                        for (child in childListing.entries) {
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
                        partial = visited >= maxEntries || queue.isNotEmpty() || depthLimitHit ||
                            unreadableDirectories > 0 || directoryLimitHit,
                        unreadableDirectories = unreadableDirectories,
                        directoryLimitHit = directoryLimitHit
                    )
                }
                indexed = result.files
                hasScanned = true
                scanProgress = result.visited
                refresh++
                val unreadableDetail = if (result.unreadableDirectories > 0) {
                    " No se pudieron leer " + result.unreadableDirectories + " carpetas."
                } else ""
                val directoryLimitDetail = if (result.directoryLimitHit) {
                    " Algunas carpetas superaron ${MAX_DIRECTORY_ENTRIES} elementos y se limitaron para proteger memoria."
                } else ""
                scanStatus = if (result.partial) {
                    "Resultados parciales: " + result.files.size + " archivos revisados (" +
                        result.visited + " elementos). Para completar, analiza una carpeta más específica." +
                        unreadableDetail + directoryLimitDetail
                } else {
                    "Búsqueda completa."
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
        duplicateScanRunning = true
        duplicateScanProgress = 0
        duplicateScanTotal = sourceFiles.size
        duplicateScanStatus = "Preparando candidatos en segundo plano..."
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val sized = ArrayList<SizedCandidate<DocumentFile>>(minOf(sourceFiles.size, 4_096))
                    var unreadable = 0

                    // Querying DocumentFile metadata can block on SAF providers. Keep both
                    // metadata reads and candidate grouping off the Compose/UI thread.
                    for ((index, file) in sourceFiles.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        try {
                            if (!file.isDirectory) {
                                val size = file.length()
                                if (size > 0L) sized.add(SizedCandidate(file, size))
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            unreadable++
                        }
                        if ((index + 1) % 2_000 == 0 || index == sourceFiles.lastIndex) {
                            withContext(Dispatchers.Main) { duplicateScanProgress = index + 1 }
                        }
                    }

                    val candidates = potentialDuplicateCandidates(sized) { it.uri.toString() }
                    if (candidates.isEmpty()) {
                        return@withContext Triple(emptyList<DuplicateGroup>(), unreadable, 0)
                    }
                    withContext(Dispatchers.Main) {
                        duplicateScanTotal = candidates.size
                        duplicateScanProgress = 0
                        duplicateScanStatus = "Comparando el contenido completo de ${candidates.size} archivos..."
                    }

                    val hashes = linkedMapOf<String, MutableList<DocumentFile>>()
                    for (index in candidates.indices) {
                        currentCoroutineContext().ensureActive()
                        val candidate = candidates[index]
                        val file = candidate.value
                        val hash = try {
                            sha256Content(context, file)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (hash == null) {
                            unreadable++
                        } else {
                            hashes.getOrPut("${candidate.size}|${hash}") { mutableListOf() }.add(file)
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
                        .sortedByDescending { group -> group.files.firstOrNull()?.let { runCatching { it.length() }.getOrDefault(0L) } ?: 0L }
                    Triple(confirmed, unreadable, candidates.size)
                }
                duplicateGroups = result.first
                cleanupSelectedUris = emptySet()
                duplicateScanStatus = buildString {
                    when {
                        result.third == 0 -> append("No hay archivos con el mismo tamaño para comparar. No se ha eliminado nada.")
                        result.first.isEmpty() -> append("No se encontraron copias exactas.")
                        else -> append("Revisa las copias exactas. No se borró nada.")
                    }
                    if (result.second > 0) append(" Algunos archivos no se pudieron leer y se omitieron.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                duplicateGroups = emptyList()
                duplicateScanStatus = "La comparación se interrumpió sin borrar archivos. Comprueba los permisos e inténtalo de nuevo."
            } finally {
                duplicateScanRunning = false
            }
        }
    }

    fun analyzeSimilarImages() {
        if (!hasScanned) {
            analyzeSimilarAfterScan = true
            similarScanStatus = "Primero se analiza la ubicación."
            scan()
            return
        }
        if (similarScanRunning) return
        similarScanRunning = true
        similarScanStatus = "Preparando una selección acotada en segundo plano..."
        scope.launch {
            try {
                // DocumentFile metadata can query SAF providers. The cancellable scanner
                // discovers candidates on Dispatchers.IO and stops once the cap is exceeded.
                val result = withContext(Dispatchers.IO) { findSimilarImages(context, sourceFiles) }
                similarImageGroups = result.groups
                cleanupSelectedUris = emptySet()
                similarScanStatus = when {
                    result.scanned == 0 ->
                        "No hay imágenes compatibles para comparar."
                    result.groups.isEmpty() && result.truncated ->
                        "No se encontró ningún grupo en la selección analizada. Prueba una carpeta más específica."
                    result.groups.isEmpty() ->
                        "No se encontraron fotos parecidas en esta ubicación."
                    result.truncated ->
                        "Se analizó una selección limitada por seguridad. Para continuar, elige una carpeta más concreta."
                    result.unreadable > 0 ->
                        "Hay imágenes que Android no pudo leer; se dejaron fuera de los resultados."
                    else ->
                        "Revisa las fotos agrupadas. No se borró nada."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                similarImageGroups = emptyList()
                similarScanStatus = "La búsqueda de fotos parecidas se interrumpió. Prueba una carpeta más pequeña."
            } finally {
                similarScanRunning = false
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
        if (activeCleanupMode == CleanupMode.SIMILAR_IMAGES && similarImageGroups.any { group ->
                group.files.size > 1 && group.files.all { it.uri.toString() in cleanupSelectedUris }
            }) {
            message = "Deja al menos una foto sin marcar en cada grupo parecido."
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
        if (activeCleanupMode == CleanupMode.SIMILAR_IMAGES && similarImageGroups.any { group ->
                group.files.size > 1 && group.files.all { it.uri.toString() in selected.map { file -> file.uri.toString() }.toSet() }
            }) {
            message = "No se borró nada: deja al menos una foto sin marcar en cada grupo parecido."
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
                similarImageGroups = similarImageGroups.mapNotNull { group ->
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
    LaunchedEffect(analyzeSimilarAfterScan, hasScanned, scanning) {
        if (analyzeSimilarAfterScan && hasScanned && !scanning) {
            analyzeSimilarAfterScan = false
            analyzeSimilarImages()
        }
    }
    BackHandler {
        when {
            showExitDialog -> showExitDialog = false
            showAppearance -> showAppearance = false
            showPlayerPanel -> showPlayerPanel = false
            screen == Screen.TYPES && !choosingKind -> { choosingKind = true; query = "" }
            screen == Screen.FOLDERS && folderHistory.isNotEmpty() -> {
                val parent = folderHistory.last()
                folderHistory = folderHistory.dropLast(1)
                current = parent
                currentUri = parent.uri
                refresh++
            }
            screen != Screen.HOME -> { screen = Screen.HOME; choosingKind = false; query = "" }
            else -> showExitDialog = true
        }
    }

    val isCyberpunk = activePalette == Palette.CIBERPUNK
    val isCapibara = activePalette == Palette.CAPIBARA
    MaterialTheme(colorScheme = if (isCyberpunk) androidx.compose.material3.darkColorScheme(
        primary = activePalette.primary,
        onPrimary = Color(0xFF07131C),
        secondary = activePalette.secondary,
        onSecondary = Color(0xFF1A1020),
        background = activePalette.background,
        onBackground = Color(0xFFF1F5FF),
        surface = Color(0xFF192238),
        onSurface = Color(0xFFF1F5FF),
        surfaceVariant = Color(0xFF242F49),
        onSurfaceVariant = Color(0xFFBAC6DE),
        primaryContainer = Color(0xFF153B4B),
        onPrimaryContainer = Color(0xFFD9F8FF),
        secondaryContainer = Color(0xFF422642),
        onSecondaryContainer = Color(0xFFFFDDF6)
    ) else lightColorScheme(
        primary = activePalette.primary,
        onPrimary = if (isCapibara) Color(0xFFFFFCF9) else activePalette.background,
        secondary = activePalette.secondary,
        onSecondary = if (isCapibara) Color(0xFFFFFCF9) else activePalette.background,
        tertiary = if (isCapibara) Color(0xFFC18A73) else activePalette.secondary,
        onTertiary = if (isCapibara) Color(0xFF3F2D28) else activePalette.background,
        background = activePalette.background,
        onBackground = if (isCapibara) Color(0xFF4D3832) else Color(0xFF17182B),
        surface = if (isCapibara) Color(0xFFFFFCF9) else Color.White,
        onSurface = if (isCapibara) Color(0xFF4D3832) else Color(0xFF17182B),
        surfaceVariant = if (isCapibara) Color(0xFFF2E3DB) else activePalette.secondary.copy(alpha = 0.12f),
        onSurfaceVariant = if (isCapibara) Color(0xFF80665D) else Color(0xFF292A40),
        primaryContainer = if (isCapibara) Color(0xFFEAD8CD) else activePalette.primary.copy(alpha = 0.12f),
        onPrimaryContainer = if (isCapibara) Color(0xFF4D3832) else Color(0xFF17182B),
        secondaryContainer = if (isCapibara) Color(0xFFF8DDE0) else activePalette.secondary.copy(alpha = 0.12f),
        onSecondaryContainer = if (isCapibara) Color(0xFF6C3942) else Color(0xFF292A40),
        outline = if (isCapibara) Color(0xFFD8BEB2) else activePalette.secondary.copy(alpha = 0.42f),
        outlineVariant = if (isCapibara) Color(0xFFEAD8CF) else activePalette.secondary.copy(alpha = 0.24f)
    )) {
        Box(Modifier.fillMaxSize().background(activePalette.background)) {
            wallpaperBitmap?.let { image ->
                Image(bitmap = image, contentDescription = "Fondo personalizado de Archivero",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.28f)
            }
            Scaffold(
            containerColor = when {
                wallpaperBitmap != null -> activePalette.background.copy(alpha = 0.78f)
                isCyberpunk -> Color.Transparent
                else -> activePalette.background
            },
            contentColor = if (isCyberpunk) Color(0xFFF3F4FF) else MaterialTheme.colorScheme.onBackground,
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
                                    Screen.TYPES -> if (choosingKind) "Buscar por tipo" else activeKind.title
                                    Screen.FOLDERS -> "Carpetas y ubicaciones"
                                    Screen.CLEAN -> "Recupera espacio"
                                    Screen.LIBRARY -> "Fichas y referencias"
                                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAppearance = true }) { Icon(Icons.Default.Palette, "Personalizar apariencia") }
                        IconButton(onClick = { if (rootUri == null) pickFolder.launch(null) else refresh++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = activePalette.background.copy(alpha = 0.88f),
                        titleContentColor = if (isCyberpunk) Color(0xFFF3F4FF) else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (isCyberpunk) Color(0xFFF3F4FF) else MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = if (isCyberpunk) activePalette.secondary else activePalette.primary
                    )
                )
            },
            bottomBar = {
                Column {
                    if (playerUri != null && !showPlayerPanel && !playerVideo) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
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
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME; choosingKind = false; query = "" }, { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") })
                        NavigationBarItem(screen == Screen.TYPES, { screen = Screen.TYPES; choosingKind = true; query = "" }, { Icon(Icons.Default.Category, null) }, label = { Text("Tipos") })
                        NavigationBarItem(screen == Screen.FOLDERS, { screen = Screen.FOLDERS; choosingKind = false; folderHistory = emptyList(); current = root; currentUri = rootUri; query = ""; refresh++ }, { Icon(Icons.Default.FolderOpen, null) }, label = { Text("Carpetas") })
                        NavigationBarItem(screen == Screen.CLEAN, { screen = Screen.CLEAN; query = ""; cleanupSelectedUris = emptySet() }, { Icon(Icons.Default.CleaningServices, null) }, label = { Text("Limpiar") })
                        NavigationBarItem(screen == Screen.LIBRARY, { screen = Screen.LIBRARY; choosingKind = false; query = "" }, { Icon(Icons.Default.Bookmark, null) }, label = { Text("Biblioteca") })
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
                if (rootUri == null && screen != Screen.LIBRARY) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.width(180.dp).height(200.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0xFFF7DEDB))
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mascota_chibi_fiel),
                                contentDescription = "Mascota capibara kawaii de Archivero",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(25.dp)),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
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
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = if (isCapibara) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAD0C7)) else null,
                            colors = CardDefaults.cardColors(containerColor = when {
                                isCyberpunk -> MaterialTheme.colorScheme.surface
                                isCapibara -> Color(0xFFFFF0EA)
                                else -> Color(0xFFFFEAF3)
                            })
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("¡Hola, explorador!", color = if (isCyberpunk || isCapibara) MaterialTheme.colorScheme.onSurface else Color(0xFF51365F), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                    Text("Tus archivos, ordenados con cariño.", color = if (isCyberpunk || isCapibara) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF684B70), style = MaterialTheme.typography.bodyMedium)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = RoundedCornerShape(50.dp)
                                    ) {
                                        Text(
                                            when {
                                                isCyberpunk -> "ARCHIVERO · MODO CIBERPUNK"
                                                isCapibara -> "♡ MODO CAPIBARA KAWAII ♡"
                                                else -> "✦ MODO KAWAII ✦"
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier.width(100.dp).height(112.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFF7DEDB))
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.mascota_chibi_fiel),
                                        contentDescription = "Mascota capibara original de Archivero",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)),
                                        contentScale = ContentScale.Crop,
                                        alignment = Alignment.TopCenter
                                    )
                                }
                            }
                        }
                        DiskCard(disk, root?.name ?: "Ubicación elegida", if (isCyberpunk) Color(0xFF124155) else activePalette.primary) { pickFolder.launch(null) }
                        Spacer(Modifier.height(18.dp))
                        Header("¿Qué estás buscando?", "Accesos directos a tus archivos")
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.IMAGES, Modifier.weight(1f)) { selectedKind = Kind.IMAGES.name; screen = Screen.TYPES; choosingKind = false; query = ""; if (!hasScanned && !scanning) scan() }
                                CategoryTile(Kind.VIDEOS, Modifier.weight(1f)) { selectedKind = Kind.VIDEOS.name; screen = Screen.TYPES; choosingKind = false; query = ""; if (indexed.isEmpty() && !scanning) scan() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.MUSIC, Modifier.weight(1f)) {
                                    selectedKind = Kind.MUSIC.name
                                    screen = Screen.TYPES
                                    choosingKind = false
                                    query = ""
                                    if (indexed.isEmpty() && !scanning) scan()
                                }
                                CategoryTile(Kind.DOCS, Modifier.weight(1f)) { selectedKind = Kind.DOCS.name; screen = Screen.TYPES; choosingKind = false; query = ""; if (indexed.isEmpty() && !scanning) scan() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.APK, Modifier.weight(1f)) { selectedKind = Kind.APK.name; screen = Screen.TYPES; choosingKind = false; query = ""; if (indexed.isEmpty() && !scanning) scan() }
                                CategoryTile(Kind.ZIP, Modifier.weight(1f)) { selectedKind = Kind.ZIP.name; screen = Screen.TYPES; choosingKind = false; query = ""; if (indexed.isEmpty() && !scanning) scan() }
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
                        if (scanning) Text("Buscando archivos...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (scanStatus.isNotBlank()) Text(scanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Screen.TYPES -> {
                        if (choosingKind) {
                            Header("Buscar por tipo", "Elige una categoría para abrir directamente sus archivos")
                            Spacer(Modifier.height(10.dp))
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(Kind.values().toList()) { kind ->
                                    Card(
                                        onClick = {
                                            selectedKind = kind.name
                                            choosingKind = false
                                            query = ""
                                            if (indexed.isEmpty() && !scanning) scan()
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (activeKind == kind) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            KindIcon(kind, 42)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(kind.title, fontWeight = FontWeight.Bold)
                                                Text(
                                                    kind.extensions.take(5).joinToString(" · ").ifBlank { "Otros formatos" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (activeKind == kind) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Header(activeKind.title, if (activeKind == Kind.IMAGES) "Tu galería" else "Archivos de este tipo")
                                }
                                TextButton(onClick = { choosingKind = true; query = "" }) {
                                    Icon(Icons.Default.Category, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tipo")
                                }
                            }
                            SearchBox(query) { query = it }
                            if (scanning && activeKind != Kind.IMAGES) Text("Buscando archivos...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (filteringFiles && activeKind != Kind.IMAGES) Text("Actualizando...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (scanStatus.startsWith("Resultados parciales")) Text("Algunos archivos necesitan otro análisis desde una carpeta más específica.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                            if (activeKind == Kind.IMAGES) {
                                ImageGalleryGrid(
                                    files = matchingFiles,
                                    onOpen = { requestOpen(it, matchingFiles, imageGalleryPrepared = true) },
                                    onMore = { actionFile = it; actionMenu = true },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                FileList(matchingFiles, { requestOpen(it, sourceFiles) }, { actionFile=it; actionMenu=true }, Modifier.weight(1f))
                            }
                            if (!hasScanned || scanStatus.startsWith("Resultados parciales")) {
                                TextButton(onClick = { scan() }, enabled = !scanning) {
                                    Text(if (scanning) "Analizando..." else "Volver a analizar ubicación")
                                }
                            }
                        }
                    }
                    Screen.FOLDERS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Header(current?.name ?: "Carpetas", "Solo las ubicaciones que autorizaste") }
                            val currentIsQuickAccess = folderQuickAccess.any { it.uri == (currentUri?.toString() ?: current?.uri?.toString()) }
                            IconButton(onClick = { toggleCurrentFolderQuickAccess() }, enabled = current != null) {
                                Icon(
                                    if (currentIsQuickAccess) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                                    contentDescription = if (currentIsQuickAccess) "Quitar de accesos rápidos" else "Guardar en accesos rápidos"
                                )
                            }
                            TextButton(onClick = { pickFolder.launch(null) }) { Icon(Icons.Default.Add, null); Text("Ubicación") }
                        }
                        if (currentUri != null && currentUri != rootUri) {
                            TextButton(onClick = {
                                if (folderHistory.isNotEmpty()) {
                                    val parent = folderHistory.last()
                                    folderHistory = folderHistory.dropLast(1)
                                    current = parent
                                    currentUri = parent.uri
                                } else {
                                    current = root
                                    currentUri = rootUri
                                }
                                refresh++
                            }) { Icon(Icons.Default.ArrowBack, null); Text("Volver") }
                        }
                        SearchBox(query) { query = it }
                        if (folderQuickAccessError != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "No se pudieron recuperar los accesos rápidos guardados: ${folderQuickAccessError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                context.getSharedPreferences("archivero_settings", Context.MODE_PRIVATE)
                                                    .edit().remove("folder_quick_access").commit()
                                            }
                                        }
                                        result.fold(
                                            { saved ->
                                                if (saved) {
                                                    folderQuickAccess = emptyList()
                                                    folderQuickAccessError = null
                                                    message = "Se restableció la lista de accesos rápidos dañada. No se modificaron archivos ni la biblioteca."
                                                } else {
                                                    message = "Android no pudo guardar el restablecimiento de accesos rápidos. Los datos originales se conservan."
                                                }
                                            },
                                            { error -> message = error.message ?: "No se pudieron restablecer los accesos rápidos." }
                                        )
                                    }
                                }) { Text("Restablecer accesos rápidos dañados") }
                            }
                        }
                        if (folderQuickAccess.isNotEmpty()) {
                            Text("Accesos rápidos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(folderQuickAccess, key = { it.uri }) { shortcut ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(
                                            onClick = { openFolderQuickAccess(shortcut) },
                                            label = {
                                                Text(
                                                    shortcut.displayName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 160.dp)
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                                        )
                                        IconButton(
                                            onClick = {
                                                if (saveFolderQuickAccess(removeFolderQuickAccess(folderQuickAccess, shortcut.uri))) message = "Se quitó \"${shortcut.displayName}\" de accesos rápidos."
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Quitar ${shortcut.displayName} de accesos rápidos")
                                        }
                                    }
                                }
                            }
                        }
                        if (listingTruncated) {
                            Text(
                                "Se han cargado ${entries.size} elementos. Hay más en esta carpeta; puedes traer otro lote sin abrir una lista ilimitada de golpe.",
                                color = if (isCyberpunk) activePalette.secondary else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = {
                                    val folderToPage = current
                                    val pageUri = currentUri
                                    val pageRefresh = refresh
                                    val offset = entries.size
                                    if (folderToPage != null && !loadingMore) {
                                        loadingMore = true
                                        scope.launch {
                                            try {
                                                val nextPage = withContext(Dispatchers.IO) {
                                                    listDocumentChildrenBounded(
                                                        context, folderToPage, MAX_DIRECTORY_ENTRIES, offset
                                                    )
                                                }
                                                if (currentUri == pageUri && refresh == pageRefresh &&
                                                    current?.uri == folderToPage.uri) {
                                                    listedEntries = (listedEntries + nextPage.entries)
                                                        .distinctBy { it.uri.toString() }
                                                    listingTruncated = nextPage.truncated
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (error: Exception) {
                                                if (currentUri == pageUri && refresh == pageRefresh) {
                                                    message = "No se pudo cargar el siguiente lote. Los archivos ya mostrados se conservan. Comprueba el permiso de la carpeta e inténtalo otra vez."
                                                }
                                            } finally {
                                                if (currentUri == pageUri && refresh == pageRefresh) loadingMore = false
                                            }
                                        }
                                    }
                                },
                                enabled = !loadingMore && current != null
                            ) {
                                if (loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.ExpandMore, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (loadingMore) "Cargando lote…" else "Cargar siguientes ${MAX_DIRECTORY_ENTRIES}")
                            }
                        }
                        if (listingFailed) EmptyNotice("No se pudo leer esta carpeta", listingErrorMessage, Icons.Default.FolderOff)
                        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(folderEntries, key = { it.uri.toString() }) { file ->
                                DocumentRow(file, {
                                    if (file.isDirectory) {
                                        current?.let { folderHistory = folderHistory + it }
                                        current = file
                                        currentUri = file.uri
                                        refresh++
                                    } else requestOpen(file, entries)
                                }, { actionFile=file; deleteDialog=true })
                            }
                        }
                    }
                    Screen.CLEAN -> {
                        Header("Limpiar archivos", "Revisa las sugerencias antes de eliminar")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ManageSearch, null)
                            Spacer(Modifier.width(8.dp))
                            Text(when {
                                scanning -> "Analizando ubicación..."
                                hasScanned -> "Volver a analizar ubicación"
                                else -> "Analizar ubicación"
                            })
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
                                label = { Text("Copias exactas") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = activeCleanupMode == CleanupMode.SIMILAR_IMAGES,
                                onClick = { cleanupModeName = CleanupMode.SIMILAR_IMAGES.name; cleanupSelectedUris = emptySet() },
                                label = { Text("Fotos parecidas") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = activeCleanupMode == CleanupMode.VIDEOS,
                                onClick = { cleanupModeName = CleanupMode.VIDEOS.name; cleanupSelectedUris = emptySet() },
                                label = { Text("Vídeos grandes") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (activeCleanupMode == CleanupMode.DUPLICATES || activeCleanupMode == CleanupMode.SIMILAR_IMAGES) {
                            OutlinedButton(
                                onClick = { if (activeCleanupMode == CleanupMode.DUPLICATES) analyzeDuplicates() else analyzeSimilarImages() },
                                enabled = if (activeCleanupMode == CleanupMode.DUPLICATES) !duplicateScanRunning && hasScanned else !similarScanRunning && hasScanned,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (duplicateScanRunning || similarScanRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(if (activeCleanupMode == CleanupMode.DUPLICATES) Icons.Default.Fingerprint else Icons.Default.FilterCenterFocus, null)
                                Spacer(Modifier.width(6.dp))
                                Text(when {
                                    duplicateScanRunning -> "Comparando copias..."
                                    similarScanRunning -> "Buscando fotos parecidas..."
                                    activeCleanupMode == CleanupMode.DUPLICATES -> "Buscar copias exactas"
                                    else -> "Buscar fotos parecidas"
                                })
                            }
                        }
                        Text(
                            when (activeCleanupMode) {
                                CleanupMode.SCREENSHOTS -> "Capturas antiguas sugeridas para revisión. Archivero no puede saber si aún las necesitas."
                                CleanupMode.DUPLICATES -> "Compara el contenido real de los archivos, no solo su nombre."
                                CleanupMode.SIMILAR_IMAGES -> "Busca fotos visualmente parecidas aunque tengan otra compresión. Revisa antes de borrar."
                                CleanupMode.VIDEOS -> "Videos grandes que puedes revisar antes de liberar espacio."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SearchBox(query) { query = it }
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateScanRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (activeCleanupMode == CleanupMode.SIMILAR_IMAGES && similarScanRunning) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Analizando miniaturas; no se borrará nada durante la búsqueda.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (scanStatus.startsWith("Resultados parciales") || scanStatus.startsWith("La búsqueda se interrumpió")) {
                            Text("El análisis fue parcial. Puedes revisar una carpeta más específica.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateScanStatus.isNotBlank()) {
                            Text(duplicateScanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activeCleanupMode == CleanupMode.SIMILAR_IMAGES && similarScanStatus.isNotBlank()) {
                            Text(similarScanStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (cleanupFiles.isEmpty() && activeCleanupMode != CleanupMode.DUPLICATES && activeCleanupMode != CleanupMode.SIMILAR_IMAGES) {
                            EmptyNotice(
                                when (activeCleanupMode) {
                                    CleanupMode.SCREENSHOTS -> "No hay capturas antiguas para revisar"
                                    CleanupMode.VIDEOS -> "No hay videos grandes para revisar"
                                    else -> "No hay sugerencias"
                                },
                                "Prueba otra ubicación o analiza una carpeta más específica.",
                                if (activeCleanupMode == CleanupMode.SCREENSHOTS) Icons.Default.ImageSearch else Icons.Default.Movie
                            )
                        }
                        if (activeCleanupMode == CleanupMode.DUPLICATES && duplicateDisplayGroups.isEmpty() && !duplicateScanRunning) {
                            EmptyNotice(
                                if (duplicateScanStatus.isBlank()) "Busca copias exactas" else "No hay copias exactas confirmadas",
                                "Nada se elimina durante la búsqueda.",
                                Icons.Default.Fingerprint
                            )
                        }
                        if (activeCleanupMode == CleanupMode.SIMILAR_IMAGES && similarDisplayGroups.isEmpty() && !similarScanRunning) {
                            EmptyNotice(
                                if (similarScanStatus.isBlank()) "Busca fotos parecidas" else "No se encontraron fotos parecidas",
                                "Las sugerencias se revisan antes de eliminar.",
                                Icons.Default.FilterCenterFocus
                            )
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (activeCleanupMode == CleanupMode.DUPLICATES || activeCleanupMode == CleanupMode.SIMILAR_IMAGES) {
                                val groups = if (activeCleanupMode == CleanupMode.DUPLICATES) {
                                    duplicateDisplayGroups.map { CleanupGroupRow(it.hash, it.files, false) }
                                } else {
                                    similarDisplayGroups.map { CleanupGroupRow(it.hash.toString(), it.files, true) }
                                }
                                items(groups, key = { it.key }) { group ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                            Text(if (group.similar) "Fotos parecidas" else "Copias idénticas", fontWeight = FontWeight.Bold)
                                            group.files.forEach { file ->
                                                CleanupCandidateRow(
                                                    file = file,
                                                    checked = file.uri.toString() in cleanupSelectedUris,
                                                    subtitle = "",
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
                                    CleanupCandidateRow(
                                        file = file,
                                        checked = file.uri.toString() in cleanupSelectedUris,
                                        subtitle = "",
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
                                Text("Revisar eliminación")
                            }
                        }
                    }
                    Screen.LIBRARY -> {
                        Header("Biblioteca digital", "Fichas locales para clasificar tus recursos")
                        Text(
                            "Las fichas guardan etiquetas, notas y procedencia. No copian, mueven ni eliminan el archivo original.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportLibrary.launch("archivero-biblioteca.properties") },
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (libraryTransferRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.FileDownload, null)
                                Spacer(Modifier.width(5.dp))
                                Text("Exportar")
                            }
                            OutlinedButton(
                                onClick = { importLibrary.launch(arrayOf("*/*")) },
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (libraryTransferRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.FileUpload, null)
                                Spacer(Modifier.width(5.dp))
                                Text("Importar")
                            }
                        }
                        OutlinedButton(
                            onClick = { libraryTrashDialog = true },
                            enabled = libraryCatalog != null && !libraryTransferRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Papelera (${libraryTrashRecords.size})")
                        }
                        LibraryTrashDialog(
                            isOpen = libraryTrashDialog,
                            records = libraryTrashRecords,
                            transferRunning = libraryTransferRunning,
                            onDismiss = { libraryTrashDialog = false },
                            onRestore = { uri -> restoreLibraryRecord(uri) },
                            onPurge = { uri -> purgeLibraryRecord(uri) }
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LibraryBulkTagEditor(
                                records = visibleLibraryRecords,
                                catalog = libraryCatalog,
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                onUpdated = { changedCount, tags ->
                                    refreshLibrary()
                                    message = if (changedCount == 0) {
                                        "Las fichas seleccionadas ya tenían esas etiquetas; no se modificó el índice."
                                    } else {
                                        "Se añadieron ${tags.joinToString(", ")} a ${changedCount} ficha(s). Los archivos originales no se modificaron."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            LibraryDuplicateScanner(
                                records = libraryRecords,
                                recordsByUri = libraryRecordsByUri,
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LibrarySimilarImageScanner(
                            records = visibleLibraryRecords,
                            enabled = libraryCatalog != null && !libraryTransferRunning,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LibraryBulkProjectEditor(
                            records = visibleLibraryRecords,
                            catalog = libraryCatalog,
                            enabled = libraryCatalog != null && !libraryTransferRunning,
                            onUpdated = { changedCount, projectName ->
                                refreshLibrary()
                                message = if (changedCount == 0) {
                                    "Las fichas seleccionadas ya pertenecían a «$projectName»; no se modificó el índice."
                                } else {
                                    "Se asignó «$projectName» a $changedCount ficha(s). Los archivos físicos no se movieron."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LibraryRelationshipAuditButton(
                                records = libraryRecords,
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                modifier = Modifier.weight(1f)
                            )
                            LibraryResourceAccessAuditButton(
                                records = libraryRecords,
                                enabled = libraryCatalog != null && !libraryTransferRunning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (libraryTransferRunning) {
                            Text("Procesando instantánea de metadatos…", style = MaterialTheme.typography.bodySmall)
                        }
                        SearchBox(
                            query = query,
                            onChange = { query = it },
                            onSearch = { rememberLibrarySearch() }
                        )
                        if (query.isBlank() && libraryRecentSearches.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Búsquedas recientes", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { clearLibraryRecentSearches() }) { Text("Borrar historial") }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                libraryRecentSearches.forEach { term ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { query = term },
                                        label = { Text(term, maxLines = 1) }
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = libraryCategoryFilter,
                                onValueChange = { libraryCategoryFilter = it },
                                label = { Text("Categoría") },
                                placeholder = { Text("Todas") },
                                singleLine = true,
                                modifier = Modifier.width(150.dp),
                                trailingIcon = {
                                    if (libraryCategoryFilter.isNotBlank()) IconButton(onClick = { libraryCategoryFilter = "" }) {
                                        Icon(Icons.Default.Close, "Quitar filtro de categoría")
                                    }
                                }
                            )
                            OutlinedTextField(
                                value = libraryResourceKindFilter,
                                onValueChange = { libraryResourceKindFilter = it },
                                label = { Text("Tipo de recurso") },
                                placeholder = { Text("Todos") },
                                singleLine = true,
                                modifier = Modifier.width(160.dp),
                                trailingIcon = {
                                    if (libraryResourceKindFilter.isNotBlank()) IconButton(onClick = { libraryResourceKindFilter = "" }) {
                                        Icon(Icons.Default.Close, "Quitar filtro de tipo")
                                    }
                                }
                            )
                            Box {
                                OutlinedButton(onClick = { libraryProjectFilterMenuExpanded = true }) {
                                    Icon(Icons.Default.FolderCopy, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(libraryProjectFilter.ifBlank { "Colecciones" }, maxLines = 1)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = libraryProjectFilterMenuExpanded,
                                    onDismissRequest = { libraryProjectFilterMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Todos los proyectos") },
                                        onClick = {
                                            libraryProjectFilter = ""
                                            libraryProjectFilterMenuExpanded = false
                                        }
                                    )
                                    libraryProjects.forEach { project ->
                                        DropdownMenuItem(
                                            text = { Text(project, maxLines = 1) },
                                            onClick = {
                                                libraryProjectFilter = project
                                                libraryProjectFilterMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Box {
                                OutlinedButton(onClick = { librarySortMenuExpanded = true }) {
                                    Icon(Icons.Default.Sort, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(librarySortMode)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(
                                    expanded = librarySortMenuExpanded,
                                    onDismissRequest = { librarySortMenuExpanded = false }
                                ) {
                                    listOf("Nombre", "Recientes", "Proyecto").forEach { sort ->
                                        DropdownMenuItem(
                                            text = { Text(sort) },
                                            onClick = {
                                                librarySortMode = sort
                                                librarySortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            FilterChip(
                                selected = libraryFavoritesOnly,
                                onClick = { libraryFavoritesOnly = !libraryFavoritesOnly },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (libraryFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Solo favoritos")
                                    }
                                }
                            )
                            TextButton(onClick = {
                                libraryCategoryFilter = ""
                                libraryResourceKindFilter = ""
                                libraryProjectFilter = ""
                                librarySortMode = "Nombre"
                                libraryFavoritesOnly = false
                            }) { Text("Restablecer") }
                        }
                        if (libraryCatalogLoading) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Cargando índice de biblioteca…")
                            }
                        } else if (libraryStorageError != null) {
                            EmptyNotice(
                                "Índice de biblioteca no disponible",
                                "No se ha recreado ni sobrescrito el índice existente. Detalle: ${libraryStorageError}",
                                Icons.Default.Warning
                            )
                        } else {
                            if (librarySearching) {
                                Text(
                                    "Buscando en etiquetas, notas y metadatos…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${if (libraryMatchesTruncated) visibleLibraryRecords.size.toString() + "+" else visibleLibraryRecords.size.toString()} ficha(s) visibles · almacenamiento local",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            if (libraryRecords.isEmpty()) {
                                EmptyNotice(
                                    "Tu biblioteca empieza aquí",
                                    "Abre el menú de un archivo y elige «Añadir a biblioteca» para guardar etiquetas, notas o la fuente de referencia.",
                                    Icons.Default.Bookmark
                                )
                            } else if (visibleLibraryRecords.isEmpty()) {
                                EmptyNotice("Sin coincidencias", "Prueba con otro nombre, etiqueta, nota o fuente.", Icons.Default.SearchOff)
                            } else {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { libraryGridView = false }, enabled = libraryGridView) {
                                        Icon(Icons.Default.ViewList, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lista")
                                    }
                                    TextButton(onClick = { libraryGridView = true }, enabled = !libraryGridView) {
                                        Icon(Icons.Default.GridView, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cuadrícula")
                                    }
                                }
                                if (libraryGridView) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(164.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        gridItems(visibleLibraryRecords, key = { it.uri }) { record ->
                                            LibraryRecordCard(
                                                record = record,
                                                grid = true,
                                                transferRunning = libraryTransferRunning,
                                                libraryRecordsByUri = libraryRecordsByUri,
                                                isFavorite = record.uri in libraryFavoriteUris,
                                                onToggleFavorite = { toggleLibraryFavorite(record.uri) },
                                                onOpen = { document ->
                                                    requestOpen(document, listOf(document))
                                                },
                                                onEdit = { document ->
                                                    if (document != null) {
                                                        editLibraryMetadata(document)
                                                    } else {
                                                        libraryEditUri = record.uri
                                                        libraryEditName = record.displayName
                                                        libraryEditCategory = record.category
                                                        libraryEditTags = record.tags.joinToString(", ")
                                                        libraryEditNotes = record.notes
                                                        libraryEditSource = record.source
                                                        libraryEditProject = record.project
                                                        libraryEditResourceKind = record.resourceKind
                                                        libraryEditRelatedUris = record.relatedUris.toSet()
                                                        libraryRelatedQuery = ""
                                                        libraryEditDialog = true
                                                    }
                                                },
                                                onRemove = {
                                                    val catalog = libraryCatalog
                                                    if (catalog == null) {
                                                        message = "El índice de biblioteca no está disponible."
                                                    } else {
                                                        libraryTransferRunning = true
                                                        scope.launch {
                                                            val result = withContext(Dispatchers.IO) {
                                                                runCatching { catalog.trashMetadata(record.uri) }
                                                            }
                                                            libraryTransferRunning = false
                                                            result.fold(
                                                                { removed ->
                                                                    if (removed) {
                                                                        refreshLibrary()
                                                                        message = "Ficha movida a la Papelera. Puedes restaurarla; el archivo físico no se borró."
                                                                    } else {
                                                                        message = "La ficha ya no estaba en el catálogo."
                                                                    }
                                                                },
                                                                { error -> message = "No se pudo quitar la ficha: ${error.message ?: "error de almacenamiento"}" }
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(visibleLibraryRecords, key = { it.uri }) { record ->
                                            LibraryRecordCard(
                                                record = record,
                                                grid = false,
                                                transferRunning = libraryTransferRunning,
                                                libraryRecordsByUri = libraryRecordsByUri,
                                                isFavorite = record.uri in libraryFavoriteUris,
                                                onToggleFavorite = { toggleLibraryFavorite(record.uri) },
                                                onOpen = { document ->
                                                    requestOpen(document, listOf(document))
                                                },
                                                onEdit = { document ->
                                                    if (document != null) {
                                                        editLibraryMetadata(document)
                                                    } else {
                                                        libraryEditUri = record.uri
                                                        libraryEditName = record.displayName
                                                        libraryEditCategory = record.category
                                                        libraryEditTags = record.tags.joinToString(", ")
                                                        libraryEditNotes = record.notes
                                                        libraryEditSource = record.source
                                                        libraryEditProject = record.project
                                                        libraryEditResourceKind = record.resourceKind
                                                        libraryEditRelatedUris = record.relatedUris.toSet()
                                                        libraryRelatedQuery = ""
                                                        libraryEditDialog = true
                                                    }
                                                },
                                                onRemove = {
                                                    val catalog = libraryCatalog
                                                    if (catalog == null) {
                                                        message = "El índice de biblioteca no está disponible."
                                                    } else {
                                                        libraryTransferRunning = true
                                                        scope.launch {
                                                            val result = withContext(Dispatchers.IO) {
                                                                runCatching { catalog.trashMetadata(record.uri) }
                                                            }
                                                            libraryTransferRunning = false
                                                            result.fold(
                                                                { removed ->
                                                                    if (removed) {
                                                                        refreshLibrary()
                                                                        message = "Ficha movida a la Papelera. Puedes restaurarla; el archivo físico no se borró."
                                                                    } else {
                                                                        message = "La ficha ya no estaba en el catálogo."
                                                                    }
                                                                },
                                                                { error -> message = "No se pudo quitar la ficha: ${error.message ?: "error de almacenamiento"}" }
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                if (libraryMatchesTruncated && libraryResultLimit < MAX_LIBRARY_RESULT_LIMIT) {
                                    Text(
                                        "Se muestran ${visibleLibraryRecords.size} fichas. Carga la siguiente página para continuar sin dibujar toda la biblioteca a la vez.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    OutlinedButton(
                                        onClick = { libraryResultLimit = nextLibraryResultLimit(libraryResultLimit) },
                                        enabled = !librarySearching,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cargar ${minOf(LIBRARY_RESULTS_PAGE_SIZE, MAX_LIBRARY_RESULT_LIMIT - libraryResultLimit)} fichas más")
                                    }
                                } else if (libraryResultLimit == MAX_LIBRARY_RESULT_LIMIT &&
                                    visibleLibraryRecords.size == MAX_LIBRARY_RESULT_LIMIT
                                ) {
                                    Text(
                                        "Se alcanzó el límite de 10.000 fichas para esta vista. Añade filtros o una búsqueda para acotar los resultados.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showExitDialog) AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("¿Salir de Archivero?") },
                text = { Text(if (playbackIsPlaying) "La música puede continuar en segundo plano. ¿Quieres cerrar la ventana de Archivero?" else "Se cerrará la ventana de Archivero. Tus archivos no se modificarán.") },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        (context as? ComponentActivity)?.finishAndRemoveTask()
                    }) { Text("Salir") }
                },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Seguir aquí") } }
            )
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
                if (findChildDocument(context, parent, folderName.trim()) != null) false else createChildDocument(context, parent, DocumentsContract.Document.MIME_TYPE_DIR, folderName.trim()) != null
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
            TextButton(onClick = { actionFile?.let { editLibraryMetadata(it) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BookmarkAdd, null)
                Spacer(Modifier.width(8.dp))
                Text(if (actionFile?.uri?.toString()?.let { libraryCatalog?.get(it) } != null) "Editar ficha de biblioteca" else "Añadir a biblioteca")
            }
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

    if (libraryEditDialog) AlertDialog(
        onDismissRequest = { libraryEditDialog = false },
        title = { Text("Ficha de biblioteca") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Esta ficha solo guarda metadatos locales. No cambia el archivo original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = libraryEditName,
                    onValueChange = { libraryEditName = it },
                    label = { Text("Nombre visible") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = libraryEditCategory,
                    onValueChange = { libraryEditCategory = it },
                    label = { Text("Categoría o proyecto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = libraryEditTags,
                    onValueChange = { libraryEditTags = it },
                    label = { Text("Etiquetas separadas por coma") },
                    supportingText = { Text("Ej.: anime, cabello azul, personaje principal") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = libraryEditProject,
                    onValueChange = { libraryEditProject = it },
                    label = { Text("Proyecto, personaje o colección") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Tipo de recurso", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(onClick = { libraryResourceKindExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(libraryEditResourceKind, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = libraryResourceKindExpanded,
                        onDismissRequest = { libraryResourceKindExpanded = false }
                    ) {
                        listOf("Sin clasificar", "Original", "Derivado", "Editado", "Generado", "Referencia", "Personaje").forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind) },
                                onClick = {
                                    libraryEditResourceKind = kind
                                    libraryResourceKindExpanded = false
                                }
                            )
                        }
                    }
                }
                Text("Recursos relacionados", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Vincula otras fichas de esta biblioteca. La relación guarda identificadores, no duplica los archivos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = libraryRelatedQuery,
                    onValueChange = { libraryRelatedQuery = it },
                    label = { Text("Buscar fichas para relacionar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val relatedCandidates = libraryRecords.asSequence()
                    .filter { it.uri != libraryEditUri }
                    .filter {
                        libraryRelatedQuery.isBlank() ||
                            sequenceOf(it.displayName, it.category, it.project, it.tags.joinToString(" "))
                                .any { value -> value.contains(libraryRelatedQuery.trim(), ignoreCase = true) }
                    }
                    .take(12)
                    .toList()
                if (relatedCandidates.isEmpty()) {
                    Text(
                        "No hay coincidencias. Puedes guardar la ficha sin relaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    relatedCandidates.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                libraryEditRelatedUris = if (candidate.uri in libraryEditRelatedUris) {
                                    libraryEditRelatedUris - candidate.uri
                                } else if (libraryEditRelatedUris.size < 100) {
                                    libraryEditRelatedUris + candidate.uri
                                } else {
                                    message = "Cada ficha admite hasta 100 relaciones."
                                    libraryEditRelatedUris
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = candidate.uri in libraryEditRelatedUris,
                                onCheckedChange = { checked ->
                                    libraryEditRelatedUris = if (checked) {
                                        if (libraryEditRelatedUris.size < 100) libraryEditRelatedUris + candidate.uri
                                        else {
                                            message = "Cada ficha admite hasta 100 relaciones."
                                            libraryEditRelatedUris
                                        }
                                    } else libraryEditRelatedUris - candidate.uri
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(candidate.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(candidate.project, candidate.category).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                val unresolvedRelations = libraryEditRelatedUris.count { it !in libraryKnownUris }
                Text(
                    "Seleccionadas: ${libraryEditRelatedUris.size}" +
                        if (unresolvedRelations > 0) " · Referencias que ya no están en la biblioteca: $unresolvedRelations" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unresolvedRelations > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = libraryEditNotes,
                    onValueChange = { libraryEditNotes = it },
                    label = { Text("Notas") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = libraryEditSource,
                    onValueChange = { libraryEditSource = it },
                    label = { Text("Procedencia o enlace original") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !libraryTransferRunning,
                onClick = {
                    val catalog = libraryCatalog
                    val uri = libraryEditUri
                    if (catalog == null || uri == null) {
                        message = "No se pudo guardar la ficha porque el índice no está disponible. No se modificó el archivo."
                    } else {
                        libraryTransferRunning = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    catalog.upsert(
                                        LibraryRecord(
                                            uri = uri,
                                            displayName = libraryEditName,
                                            category = libraryEditCategory,
                                            tags = libraryEditTags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                            notes = libraryEditNotes,
                                            source = libraryEditSource,
                                            updatedAtMillis = System.currentTimeMillis(),
                                            project = libraryEditProject,
                                            resourceKind = libraryEditResourceKind,
                                            relatedUris = libraryEditRelatedUris.toList()
                                        )
                                    )
                                }
                            }
                            libraryTransferRunning = false
                            result.fold(
                                {
                                    refreshLibrary()
                                    libraryEditDialog = false
                                    message = "Ficha guardada en la biblioteca local. El archivo original no se modificó."
                                },
                                { error -> message = "No se pudo guardar la ficha: ${error.message ?: "comprueba los campos y el almacenamiento"}" }
                            )
                        }
                    }
                }
            ) {
                if (libraryTransferRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Guardar ficha")
            }
        },
        dismissButton = { TextButton(onClick = { libraryEditDialog = false }) { Text("Cancelar") } }
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
        title = { Text("Confirmar eliminación") },
        text = {
            Text("Se eliminarán los archivos que marcaste. Esta acción puede ser permanente. Archivero nunca borra candidatos automáticamente; revisa las marcas antes de continuar.")
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
            // Keep the music player on an opaque, full-screen surface. A partial-height
            // bottom sheet left the previous tab visible behind it and made the layout
            // look like two screens stacked together.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = activePalette.background,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reproductor de música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Archivero · la música no se corta al minimizar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showPlayerPanel = false }) {
                                Icon(Icons.Default.ArrowBack, "Volver a los archivos sin detener la música")
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
                                    tint = if (playbackUri?.toString() in favoriteUris) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Usa Atrás o la flecha para volver a la lista de archivos. La música sigue activa y queda disponible en la notificación multimedia de Android.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun DiskCard(disk: Disk?, location: String, accent: Color = Color(0xFF80594E), onChange: () -> Unit) {
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
                LinearProgressIndicator(progress = { (disk.used.toFloat() / disk.total.coerceAtLeast(1)).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFFF4C7C7), trackColor = Color.White.copy(alpha = .22f))
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
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
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
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)); Spacer(Modifier.height(7.dp))
            Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SearchBox(
    query: String,
    onSearch: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Buscar por nombre...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, "Limpiar búsqueda")
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = if (onSearch != null) androidx.compose.ui.text.input.ImeAction.Search
            else androidx.compose.ui.text.input.ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch?.invoke() }
        )
    )
}
@Composable private fun DocumentRow(file: DocumentFile, onClick: () -> Unit, onMore: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            val folder = file.isDirectory
            FileThumbnail(file = file, size = 52)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name ?: "Sin nombre", fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (folder) Text("Carpeta", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Más acciones") }
        }
    }
}
@Composable private fun FileList(files: List<DocumentFile>, onOpen: (DocumentFile) -> Unit, onMore: (DocumentFile) -> Unit, modifier: Modifier = Modifier) {
    if (files.isEmpty()) EmptyNotice("No encontramos archivos", "Prueba otra categoría o analiza la ubicación seleccionada.", Icons.Default.SearchOff)
    else LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { items(files, key = { it.uri.toString() }) { file -> DocumentRow(file, { onOpen(file) }, { onMore(file) }) } }
}
@Composable
private fun ImageGalleryGrid(
    files: List<DocumentFile>,
    onOpen: (DocumentFile) -> Unit,
    onMore: (DocumentFile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) {
        EmptyNotice("La galería está vacía", "Elige otra ubicación o analiza tus archivos.", Icons.Default.ImageSearch)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            gridItems(files, key = { it.uri.toString() }) { file ->
                Card(
                    onClick = { onOpen(file) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211F22))
                ) {
                    Box(Modifier.fillMaxSize()) {
                        GalleryThumbnail(file, Modifier.fillMaxSize())
                        IconButton(
                            onClick = { onMore(file) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                .size(34.dp).background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Acciones", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private enum class LibraryResourceState { LOADING, AVAILABLE, NOT_FOUND, NO_ACCESS }

private data class LibraryResourceDetails(
    val state: LibraryResourceState = LibraryResourceState.LOADING,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val modifiedMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

/**
 * A virtualized, metadata-aware catalog card. All potentially blocking document-provider queries
 * and image bounds reads happen in a LaunchedEffect on Dispatchers.IO.
 */
@Composable
private fun LibraryRecordCard(
    record: LibraryRecord,
    grid: Boolean,
    transferRunning: Boolean,
    libraryRecordsByUri: Map<String, LibraryRecord>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: (DocumentFile) -> Unit,
    onEdit: (DocumentFile?) -> Unit,
    onRemove: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val document = remember(context, record.uri) { libraryDocument(context, record.uri) }
    var details by remember(record.uri) { mutableStateOf(LibraryResourceDetails()) }

    LaunchedEffect(record.uri, document) {
        details = LibraryResourceDetails()
        details = withContext(Dispatchers.IO) {
            inspectLibraryResource(context, record.uri, document)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        if (grid) {
            Column(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (document != null) {
                        FileThumbnail(file = document, size = 104)
                    } else {
                        Box(
                            Modifier.size(104.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BrokenImage, null, Modifier.size(34.dp))
                        }
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                            .size(34.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                            tint = if (isFavorite) Color(0xFFFFD166) else Color.White
                        )
                    }
                }
                Text(record.displayName, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(record.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (record.project.isNotBlank()) Text("Proyecto: " + record.project, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text("Tipo: " + record.resourceKind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.relatedUris.isNotEmpty()) {
                    val knownRelated = record.relatedUris.count { it in libraryRecordsByUri }
                    val unresolved = record.relatedUris.size - knownRelated
                    Text(
                        "Relacionados: $knownRelated" + if (unresolved > 0) " · Sin ficha: $unresolved" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unresolved > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LibraryTechnicalDetails(details)
                if (record.tags.isNotEmpty()) {
                    Text("Etiquetas: " + record.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
                if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = document != null && details.state == LibraryResourceState.AVAILABLE,
                        onClick = { document?.let(onOpen) },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("Abrir") }
                    TextButton(onClick = { onEdit(document) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Editar") }
                    IconButton(onClick = onRemove, enabled = !transferRunning) {
                        Icon(Icons.Default.BookmarkRemove, "Mover a papelera")
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(11.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (document != null) {
                    FileThumbnail(file = document, size = 72)
                } else {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.BrokenImage, null, Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(record.displayName, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(record.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (record.project.isNotBlank()) Text("Proyecto: " + record.project, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("Tipo: " + record.resourceKind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.relatedUris.isNotEmpty()) {
                        val knownRelated = record.relatedUris.count { it in libraryRecordsByUri }
                        val unresolved = record.relatedUris.size - knownRelated
                        Text(
                            "Relacionados: $knownRelated" + if (unresolved > 0) " · Sin ficha: $unresolved" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unresolved > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LibraryTechnicalDetails(details)
                    if (record.tags.isNotEmpty()) {
                        Text("Etiquetas: " + record.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (record.source.isNotBlank()) {
                        Text("Procedencia: " + record.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = if (isFavorite) Color(0xFFE4A11B) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            enabled = document != null && details.state == LibraryResourceState.AVAILABLE,
                            onClick = { document?.let(onOpen) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(3.dp)); Text("Abrir") }
                        TextButton(
                            onClick = { onEdit(document) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(3.dp)); Text("Editar") }
                        TextButton(
                            onClick = onRemove,
                            enabled = !transferRunning,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Icon(Icons.Default.BookmarkRemove, null); Spacer(Modifier.width(3.dp)); Text("Quitar ficha") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTechnicalDetails(details: LibraryResourceDetails) {
    when (details.state) {
        LibraryResourceState.LOADING -> Text(
            "Consultando metadatos…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LibraryResourceState.NOT_FOUND -> Text(
            "Recurso no encontrado. La ficha se conserva.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        LibraryResourceState.NO_ACCESS -> Text(
            "Sin acceso al recurso. Revisa los permisos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        LibraryResourceState.AVAILABLE -> {
            val detailsLine = buildList {
                details.mimeType?.takeIf { it.isNotBlank() }?.let(::add)
                details.sizeBytes?.let { add(formatBytes(it)) }
                if (details.width != null && details.height != null) add("${details.width} × ${details.height} px")
                details.modifiedMillis?.let {
                    add("Modificado " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)))
                }
            }
            if (detailsLine.isNotEmpty()) {
                Text(
                    detailsLine.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            } else {
                Text("Metadatos técnicos no disponibles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun inspectLibraryResource(
    context: Context,
    rawUri: String,
    document: DocumentFile?
): LibraryResourceDetails {
    if (document == null) return LibraryResourceDetails(state = LibraryResourceState.NO_ACCESS)
    val exists = runCatching { document.exists() }
    if (exists.isFailure) return LibraryResourceDetails(state = LibraryResourceState.NO_ACCESS)
    if (exists.getOrDefault(false).not()) return LibraryResourceDetails(state = LibraryResourceState.NOT_FOUND)

    val mimeType = runCatching { document.type }.getOrNull()
    val size = runCatching { document.length() }.getOrDefault(-1L).takeIf { it >= 0L }
    val modified = runCatching { document.lastModified() }.getOrDefault(0L).takeIf { it > 0L }
    val name = runCatching { document.name }.getOrNull().orEmpty()
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val isImage = mimeType?.startsWith("image/", ignoreCase = true) == true || extension in Kind.IMAGES.extensions
    var width: Int? = null
    var height: Int? = null
    if (isImage) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val dimensionRead = runCatching {
            val uri = Uri.parse(rawUri)
            if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path, bounds)
            else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
        }.getOrNull()
        width = dimensionRead?.first
        height = dimensionRead?.second
    }
    return LibraryResourceDetails(
        state = LibraryResourceState.AVAILABLE,
        mimeType = mimeType ?: extension.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT),
        sizeBytes = size,
        modifiedMillis = modified,
        width = width,
        height = height
    )
}

@Composable
private fun GalleryThumbnail(file: DocumentFile, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // SAF metadata queries can touch a document provider. Keep them off the Compose/UI thread.
    val fileUri = file.uri.toString()
    var bitmap by remember(fileUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            ThumbnailCache.load(context, file, thumbnailCacheKey(file))
        }
    }
    Box(modifier.background(Color(0xFF292528)), contentAlignment = Alignment.Center) {
        if (bitmap != null && !bitmap!!.isRecycled) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable private fun EmptyNotice(title: String, subtitle: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.width(86.dp).height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF7DEDB))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.mascota_chibi_fiel),
                contentDescription = "Mascota capibara de Archivero",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }
        Spacer(Modifier.height(4.dp))
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        IconButton(onClick = onOpen) { Icon(Icons.Default.Visibility, "Revisar archivo") }
    }
}

@Composable
private fun FileThumbnail(file: DocumentFile, size: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Resolve provider metadata and decode previews away from the UI thread.
    val fileUri = file.uri.toString()
    var bitmap by remember(fileUri) { mutableStateOf<Bitmap?>(null) }
    var folder by remember(fileUri) { mutableStateOf(false) }
    var extension by remember(fileUri) { mutableStateOf("") }

    LaunchedEffect(file) {
        val loaded = withContext(Dispatchers.IO) {
            val isDirectory = runCatching { file.isDirectory }.getOrDefault(false)
            val name = runCatching { file.name }.getOrNull().orEmpty()
            val preview = if (isDirectory) null else ThumbnailCache.load(context, file, thumbnailCacheKey(file))
            Triple(preview, isDirectory, name.substringAfterLast('.', "").uppercase(Locale.ROOT).take(5))
        }
        bitmap = loaded.first
        folder = loaded.second
        extension = loaded.third
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(if (folder) Color(0xFFF2E3DB) else Color(0xFFFFF0EA)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !folder) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = if (extension.isNotBlank()) "Miniatura de archivo " + extension else "Miniatura de archivo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(
                    if (folder) Icons.Default.Folder else fileIconForExtension(extension),
                    contentDescription = null,
                    tint = if (folder) Color(0xFF674840) else Color(0xFF80665D),
                    modifier = Modifier.size((size * 0.48f).dp)
                )
                if (!folder && extension.isNotBlank()) {
                }
            }
        }
    }
}

private fun thumbnailCacheKey(file: DocumentFile): String {
    val uri = file.uri.toString()
    val length = runCatching { file.length() }.getOrDefault(-1L)
    val modified = runCatching { file.lastModified() }.getOrDefault(-1L)
    return "$uri|$length|$modified"
}

private object ThumbnailCache : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)

    fun load(context: Context, file: DocumentFile, key: String): Bitmap? {
        get(key)?.let { return it }
        if (runCatching { file.isDirectory }.getOrDefault(true)) return null

        val extension = runCatching { file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) }.getOrDefault("")
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
        while (largest / sample > 360) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = if (uri.scheme == "file") {
            BitmapFactory.decodeFile(uri.path, options)
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } ?: return null

        val maxDimension = maxOf(decoded.width, decoded.height)
        if (maxDimension <= 360) return decoded
        val ratio = 360f / maxDimension
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

private suspend fun sha256Content(context: Context, file: DocumentFile): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = context.contentResolver.openInputStream(file.uri)
        ?: throw IOException("Android no pudo leer el archivo")
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
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
    return listOf(
        "screenshot", "screen_shot", "screen-shot", "screen capture",
        "captura", "screenshots", "captura_de_pantalla", "captura-de-pantalla"
    ).any { it in metadata }
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
private fun openDocument(file: DocumentFile, context: Context, useNative: Boolean, onMedia: (Uri, String, Boolean) -> Unit, onError: (String) -> Unit, galleryFiles: List<DocumentFile> = emptyList(), imageGalleryPrepared: Boolean = false) {
    val name = file.name ?: "archivo sin nombre"
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val extensionMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    val providedMime = runCatching { context.contentResolver.getType(file.uri) }.getOrNull().orEmpty()
    val mime = resolveFileMimeType(name, providedMime, extensionMime)
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
            if (isRasterImageFile(file)) {
                // The image-category list is filtered, deduplicated, and sorted by the
                // background filtering job. Never repeat O(n log n) gallery preparation
                // on the UI thread when a folder has a very large number of images.
                val gallery = if (imageGalleryPrepared) galleryFiles else {
                    galleryFiles.asSequence()
                        .filter { isRasterImageFile(it) }
                        .distinctBy { it.uri.toString() }
                        .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
                        .toList()
                }
                ImageGallerySession.files = if (gallery.isEmpty()) listOf(file) else gallery
                ImageGallerySession.index = ImageGallerySession.files.indexOfFirst { it.uri == file.uri }.coerceAtLeast(0)
                intent.putExtra("imageGallery", true)
            }
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
            is SecurityException -> if (extension == "apk") {
                "Android bloqueó el instalador. Revisa Ajustes > Instalar apps desconocidas para Archivero y vuelve a intentarlo."
            } else "Android negó el permiso para abrir «" + name + "». Vuelve a elegir su carpeta."
            else -> when (extension) {
                "apk" -> "No se encontró un instalador compatible para este APK. Archivero no instala nada sin la confirmación de Android."
                "xapk", "apks" -> "«" + name + "» es un paquete que requiere un instalador compatible con XAPK/APKS. Archivero no lo instala directamente."
                else -> "No hay una aplicación compatible para abrir «" + name + "» (" + mime + "). La vista previa integrada cubre imágenes, PDF y documentos de texto comunes."
            }
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

private fun decodeWallpaperBitmap(context: Context, uri: Uri, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (uri.scheme == "file") {
        BitmapFactory.decodeFile(uri.path, bounds)
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = if (uri.scheme == "file") {
        BitmapFactory.decodeFile(uri.path, options)
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } ?: return null
    return decoded.asImageBitmap()
}

private fun fileIconForExtension(extension: String): ImageVector {
    val ext = extension.substringAfterLast('.', "").lowercase(Locale.ROOT)
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
