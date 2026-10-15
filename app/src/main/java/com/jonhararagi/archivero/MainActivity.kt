package com.jonhararagi.archivero

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.provider.Settings
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private enum class Screen { HOME, TYPES, FOLDERS, CLEAN }
private enum class Kind(val title: String, val icon: ImageVector, val tint: Color, val extensions: Set<String>) {
    IMAGES("Imágenes", Icons.Default.Image, Color(0xFF7657B5), setOf("jpg","jpeg","png","webp","gif","bmp","heic","heif","svg")),
    VIDEOS("Videos", Icons.Default.Movie, Color(0xFFB3261E), setOf("mp4","mkv","mov","avi","webm","3gp","m4v")),
    MUSIC("Música", Icons.Default.MusicNote, Color(0xFF00796B), setOf("mp3","wav","aac","m4a","ogg","flac","opus","wma")),
    DOCS("Documentos", Icons.Default.Description, Color(0xFF245CA6), setOf("pdf","doc","docx","txt","rtf","odt","xls","xlsx","ppt","pptx","csv")),
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
    var hasScanned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var actionFile by remember { mutableStateOf<DocumentFile?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var playerUri by remember { mutableStateOf<Uri?>(null) }
    var playerTitle by remember { mutableStateOf("") }
    var playerVideo by remember { mutableStateOf(false) }
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
            query = ""
            refresh++
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

    val entriesResult = remember(current, refresh) {
        runCatching { current?.listFiles()?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })?.toList().orEmpty() }
    }
    val entries = entriesResult.getOrElse { emptyList() }
    val sourceFiles = if (hasScanned) indexed else entries.filterNot { it.isDirectory }
    val activeKind = Kind.valueOf(selectedKind)
    val matchingFiles = sourceFiles.filter { file ->
        val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val kindMatches = if (activeKind == Kind.OTHER) Kind.values().filter { it != Kind.OTHER }.none { ext in it.extensions } else ext in activeKind.extensions
        kindMatches && file.name.orEmpty().contains(query.trim(), ignoreCase = true)
    }
    val cleanupFiles = sourceFiles.filter { isJunk(it) || it.length() >= 100L * 1024 * 1024 }
        .filter { it.name.orEmpty().contains(query.trim(), ignoreCase = true) }.sortedByDescending { it.length() }

    fun scan() {
        val start = root
        if (start == null) { message = "Primero elige una carpeta para analizar."; return }
        if (scanning) return
        scope.launch {
            scanning = true
            val result = withContext(Dispatchers.IO) {
                val found = mutableListOf<DocumentFile>()
                val queue = ArrayDeque<Pair<DocumentFile, Int>>()
                queue.add(start to 0)
                var visited = 0
                while (queue.isNotEmpty() && visited < 5000) {
                    val item = queue.removeFirst()
                    val children = runCatching { item.first.listFiles().toList() }.getOrElse { emptyList() }
                    for (child in children) {
                        if (visited >= 5000) break
                        visited++
                        if (child.isDirectory) {
                            if (item.second < 8 && !child.name.orEmpty().startsWith(".")) queue.add(child to item.second + 1)
                        } else found.add(child)
                    }
                }
                Triple(found, visited, visited >= 5000)
            }
            indexed = result.first
            hasScanned = true
            scanning = false
            refresh++
            message = if (result.third) "Análisis parcial: se alcanzaron 5.000 elementos por seguridad. Elige una carpeta más concreta para completar la búsqueda."
                else "Análisis terminado: " + result.first.size + " archivos en la ubicación autorizada."
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF285E55), secondary = Color(0xFF547D73),
        background = Color(0xFFF4F7F5), surface = Color.White,
        surfaceVariant = Color(0xFFE7EFEB)
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
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
                    actions = { IconButton(onClick = { if (rootUri == null) pickFolder.launch(null) else refresh++ }) { Icon(Icons.Default.Refresh, "Actualizar") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME; query = "" }, { Icon(Icons.Default.Home, null) }, label = { Text("Inicio") })
                    NavigationBarItem(screen == Screen.TYPES, { screen = Screen.TYPES; query = "" }, { Icon(Icons.Default.Category, null) }, label = { Text("Tipos") })
                    NavigationBarItem(screen == Screen.FOLDERS, { screen = Screen.FOLDERS; query = "" }, { Icon(Icons.Default.FolderOpen, null) }, label = { Text("Carpetas") })
                    NavigationBarItem(screen == Screen.CLEAN, { screen = Screen.CLEAN; query = "" }, { Icon(Icons.Default.CleaningServices, null) }, label = { Text("Limpiar") })
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
                        DiskCard(disk, root?.name ?: "Ubicación elegida") { pickFolder.launch(null) }
                        Spacer(Modifier.height(18.dp))
                        Header("¿Qué estás buscando?", "Accesos directos a tus archivos")
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.IMAGES, Modifier.weight(1f)) { selectedKind = Kind.IMAGES.name; screen = Screen.TYPES; if (!hasScanned) scan() }
                                CategoryTile(Kind.VIDEOS, Modifier.weight(1f)) { selectedKind = Kind.VIDEOS.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryTile(Kind.MUSIC, Modifier.weight(1f)) { selectedKind = Kind.MUSIC.name; screen = Screen.TYPES; if (indexed.isEmpty()) scan() }
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
                            Spacer(Modifier.width(8.dp)); Text(if (scanning) "Analizando..." else "Analizar toda esta ubicación")
                        }
                    }
                    Screen.TYPES -> {
                        Header("Buscar por tipo", "Elige una categoría, luego filtra por nombre")
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(Kind.values().toList()) { kind ->
                                Card(onClick = { selectedKind = kind.name }, colors = CardDefaults.cardColors(containerColor = if (activeKind == kind) MaterialTheme.colorScheme.secondaryContainer else Color.White)) {
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
                        Text(matchingFiles.size.toString() + " archivos encontrados", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                        FileList(matchingFiles, { openDocument(it, context, { u,t,v -> playerUri=u; playerTitle=t; playerVideo=v }, { message=it }) }, { actionFile=it; actionMenu=true }, Modifier.weight(1f))
                        if (!hasScanned) TextButton(onClick = { scan() }, enabled = !scanning) { Text(if (scanning) "Analizando..." else "Analizar ubicación") }
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
                        Text(entries.size.toString() + " elementos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                        if (entriesResult.isFailure) EmptyNotice("No se pudo leer esta carpeta", "Comprueba los permisos de Android.", Icons.Default.FolderOff)
                        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(entries.filter { it.name.orEmpty().contains(query, true) }, key = { it.uri.toString() }) { file ->
                                DocumentRow(file, {
                                    if (file.isDirectory) { current = file; currentUri = file.uri; refresh++ }
                                    else openDocument(file, context, { u,t,v -> playerUri=u; playerTitle=t; playerVideo=v }, { message=it })
                                }, { actionFile=file; deleteDialog=true })
                            }
                        }
                    }
                    Screen.CLEAN -> {
                        Header("Recupera espacio", "Detecta candidatos, tú decides qué borrar")
                        Spacer(Modifier.height(10.dp))
                        DiskCard(disk, root?.name ?: "Ubicación elegida") { pickFolder.launch(null) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth(), enabled = !scanning) {
                            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ManageSearch, null)
                            Spacer(Modifier.width(8.dp)); Text(if (scanning) "Analizando..." else "Analizar archivos")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Archivos grandes y candidatos a basura", fontWeight = FontWeight.Bold)
                        Text("Temporales, registros y archivos de 100 MB o más. No se borra nada automáticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SearchBox(query) { query = it }
                        FileList(cleanupFiles, { openDocument(it, context, { u,t,v -> playerUri=u; playerTitle=t; playerVideo=v }, { message=it }) }, { actionFile=it; deleteDialog=true }, Modifier.weight(1f))
                        if (!hasScanned) TextButton(onClick = { scan() }, enabled = !scanning) { Text("Analizar ubicación") }
                    }
                }
            }
        }
    }

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
                val result = runCatching {
                    require(file != null && !file.isDirectory) { "Selecciona un archivo." }
                    require(parent != null && parent.canWrite()) { "Elige una carpeta de destino con permiso de escritura." }
                    val original = file.name ?: "archivo"
                    val base = original.substringBeforeLast('.', original)
                    val ext = original.substringAfterLast('.', "")
                    var candidate = "$base (copia)" + if (ext.isNotBlank()) ".$ext" else ""
                    var n = 2
                    while (parent.findFile(candidate) != null) { candidate = "$base (copia $n)" + if (ext.isNotBlank()) ".$ext" else ""; n++ }
                    val dest = parent.createFile(context.contentResolver.getType(file.uri) ?: "application/octet-stream", candidate) ?: error("Android no permitió crear la copia.")
                    context.contentResolver.openInputStream(file.uri).use { input ->
                        requireNotNull(input) { "No se pudo leer el archivo original." }
                        context.contentResolver.openOutputStream(dest.uri, "w").use { output ->
                            requireNotNull(output) { "No se pudo escribir la copia." }
                            input.copyTo(output)
                        }
                    }
                    dest.name ?: candidate
                }
                message = result.fold({ refresh++; "Copia creada: $it" }, { it.message ?: "No se pudo copiar el archivo." })
                actionMenu = false
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copiar a carpeta abierta") }
            TextButton(onClick = {
                val file = actionFile
                val result = runCatching {
                    require(file != null && !file.isDirectory) { "Selecciona un archivo para mover." }
                    val parent = current ?: error("No hay carpeta de destino.")
                    require(parent.canWrite()) { "No hay permiso de escritura en la carpeta abierta." }
                    require(file.uri != parent.uri) { "Elige una carpeta de destino diferente." }
                    val original = file.name ?: "archivo"
                    val base = original.substringBeforeLast('.', original)
                    val ext = original.substringAfterLast('.', "")
                    var candidate = original
                    var n = 2
                    while (parent.findFile(candidate) != null) { candidate = "$base ($n)" + if (ext.isNotBlank()) ".$ext" else ""; n++ }
                    val dest = parent.createFile(context.contentResolver.getType(file.uri) ?: "application/octet-stream", candidate) ?: error("No se pudo crear el archivo de destino.")
                    try {
                        context.contentResolver.openInputStream(file.uri).use { input ->
                            requireNotNull(input) { "No se pudo leer el archivo original." }
                            context.contentResolver.openOutputStream(dest.uri, "w").use { output ->
                                requireNotNull(output) { "No se pudo escribir el destino." }
                                input.copyTo(output)
                            }
                        }
                        require(file.delete()) { "El archivo se copió, pero Android no permitió borrar el original. No se perdió información." }
                    } catch (e: Exception) {
                        if (dest.length() == 0L) dest.delete()
                        throw e
                    }
                    candidate
                }
                message = result.fold({ refresh++; indexed = indexed.filterNot { f -> f.uri == actionFile?.uri }; "Archivo movido: $it" }, { it.message ?: "No se pudo mover el archivo." })
                actionMenu = false
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DriveFileMove, null); Spacer(Modifier.width(8.dp)); Text("Mover a carpeta abierta") }
            TextButton(onClick = {
                val file = actionFile
                val result = runCatching {
                    require(file != null && !file.isDirectory) { "Selecciona un archivo para comprimir." }
                    val parent = current ?: error("No hay carpeta de destino.")
                    require(parent.canWrite()) { "No hay permiso de escritura en la carpeta abierta." }
                    val original = file.name ?: "archivo"
                    val zipName = original.substringBeforeLast('.', original) + ".zip"
                    var candidate = zipName
                    var n = 2
                    while (parent.findFile(candidate) != null) { candidate = zipName.removeSuffix(".zip") + " ($n).zip"; n++ }
                    val archive = parent.createFile("application/zip", candidate) ?: error("No se pudo crear el ZIP.")
                    try {
                        context.contentResolver.openInputStream(file.uri).use { input ->
                            requireNotNull(input) { "No se pudo leer el archivo original." }
                            context.contentResolver.openOutputStream(archive.uri, "w").use { output ->
                                requireNotNull(output) { "No se pudo escribir el ZIP." }
                                ZipOutputStream(output).use { zip ->
                                    zip.putNextEntry(ZipEntry(original.substringAfterLast('/')))
                                    input.copyTo(zip)
                                    zip.closeEntry()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        archive.delete()
                        throw e
                    }
                    candidate
                }
                message = result.fold({ refresh++; "Archivo comprimido: $it" }, { it.message ?: "No se pudo comprimir." })
                actionMenu = false
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Comprimir en ZIP") }
            TextButton(onClick = {
                val file = actionFile
                val result = runCatching {
                    require(file != null && !file.isDirectory) { "Selecciona un archivo ZIP." }
                    require(file.name.orEmpty().endsWith(".zip", true)) { "La extracción está disponible para archivos ZIP." }
                    val parent = current ?: error("No hay carpeta de destino.")
                    require(parent.canWrite()) { "No hay permiso de escritura en la carpeta abierta." }
                    var count = 0
                    context.contentResolver.openInputStream(file.uri).use { raw ->
                        requireNotNull(raw) { "No se pudo leer el ZIP." }
                        ZipInputStream(raw).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val safe = entry.name.replace(92.toChar(), '/')
                                require(!safe.startsWith("/") && safe.split('/').none { it == ".." }) { "El ZIP contiene una ruta insegura y se canceló." }
                                if (safe.isNotBlank()) {
                                    val parts = safe.split('/').filter { it.isNotBlank() }
                                    var dir = parent
                                    for (part in parts.dropLast(if (entry.isDirectory) 0 else 1)) {
                                        dir = dir.findFile(part)?.takeIf { it.isDirectory } ?: dir.createDirectory(part) ?: error("No se pudo crear una carpeta de destino.")
                                    }
                                    if (entry.isDirectory) {
                                        if (dir.findFile(parts.last()) == null) dir.createDirectory(parts.last())
                                    } else {
                                        val name = parts.last()
                                        require(dir.findFile(name) == null) { "Ya existe '$name'. No se sobrescribieron archivos." }
                                        val dest = dir.createFile("application/octet-stream", name) ?: error("No se pudo crear '$name'.")
                                        context.contentResolver.openOutputStream(dest.uri, "w").use { output ->
                                            requireNotNull(output) { "No se pudo escribir '$name'." }
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            while (true) {
                                                val read = zip.read(buffer)
                                                if (read < 0) break
                                                output.write(buffer, 0, read)
                                            }
                                        }
                                        count++
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    count
                }
                message = result.fold({ refresh++; "Extracción terminada: $it archivos." }, { it.message ?: "No se pudo extraer el ZIP." })
                actionMenu = false
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

    if (playerUri != null) Dialog(onDismissRequest = { playerUri = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(if (playerVideo) "Reproductor de video" else "Reproductor de música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(playerTitle, maxLines = 2) }
                    IconButton(onClick = { playerUri = null }) { Icon(Icons.Default.Close, "Cerrar reproductor") }
                }
                key(playerUri.toString()) {
                    val exo = remember(playerUri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(playerUri!!)); prepare(); playWhenReady = true } }
                    DisposableEffect(exo) { onDispose { exo.release() } }
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exo; useController = true } }, modifier = Modifier.fillMaxWidth().weight(1f), update = { view -> view.player = exo })
                }
            }
        }
    }

    message?.let { msg -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Archivero") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } }) }
}

@Composable
private fun DiskCard(disk: Disk?, location: String, onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF285E55)), shape = RoundedCornerShape(23.dp)) {
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
            Box(Modifier.size(42.dp).background(if (folder) Color(0xFFE2EFE8) else Color(0xFFE9EDF4), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (folder) Icons.Default.Folder else fileIcon(file), null, tint = if (folder) Color(0xFF285E55) else Color(0xFF52657D))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(file.name ?: "Sin nombre", fontWeight = FontWeight.SemiBold, maxLines = 1); Text(if (folder) "Carpeta" else formatBytes(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun openDocument(file: DocumentFile, context: Context, onMedia: (Uri, String, Boolean) -> Unit, onError: (String) -> Unit) {
    try {
        val mime = context.contentResolver.getType(file.uri).orEmpty()
        if (mime.startsWith("audio/") || mime.startsWith("video/")) onMedia(file.uri, file.name ?: "Multimedia", mime.startsWith("video/"))
        else context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: Exception) { onError("No se pudo abrir el archivo. Puede faltar una aplicación compatible o el permiso de acceso.") }
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
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> bytes.toString() + " B"
    bytes < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
