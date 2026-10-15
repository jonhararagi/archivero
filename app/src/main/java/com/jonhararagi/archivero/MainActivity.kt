package com.jonhararagi.archivero

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

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
    var rootUri by remember { mutableStateOf<Uri?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var rootFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var currentFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DocumentFile?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var playerUri by remember { mutableStateOf<Uri?>(null) }
    var playerTitle by remember { mutableStateOf("Reproductor") }
    var playerIsVideo by remember { mutableStateOf(false) }
    val chooseMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                playerUri = uri
                playerTitle = uri.lastPathSegment?.substringAfterLast("/") ?: "Multimedia"
                playerIsVideo = mime.startsWith("video/")
            } else {
                try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "image/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (_: Exception) { message = "No hay una aplicación compatible para abrir este archivo." }
            }
        }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) { }
            rootUri = uri
            currentUri = uri
            rootFolder = DocumentFile.fromTreeUri(context, uri)
            currentFolder = rootFolder
            refresh++
        }
    }

    // Keep the actual DocumentFile while navigating. Child document URIs are not tree URIs,
    // so recreating them with fromTreeUri breaks folder navigation on Android SAF.
    val current = currentFolder
    val entries = remember(current, refresh) {
        current?.listFiles()?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })?.toList().orEmpty()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF315DA8), secondary = androidx.compose.ui.graphics.Color(0xFF5479B8))) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column { Text("Archivero", fontWeight = FontWeight.Bold); Text(if (current == null) "Tus archivos, en orden" else current.name ?: "Almacenamiento", style = MaterialTheme.typography.labelMedium) } },
                    navigationIcon = {
                        if (currentUri != null && rootUri != currentUri) {
                            IconButton(onClick = {
                                val parent = current?.parentFile
                                if (parent != null) { currentFolder = parent; currentUri = parent.uri }
                                else { currentFolder = rootFolder; currentUri = rootUri }
                                refresh++
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { chooseMedia.launch(arrayOf("audio/*", "video/*", "image/*")) }) { Icon(Icons.Default.PlayCircle, contentDescription = "Abrir música o video") }
                        IconButton(onClick = { if (rootUri == null) chooseFolder.launch(null) else refresh++ }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                    }
                )
            },
            floatingActionButton = {
                if (current != null) FloatingActionButton(onClick = { folderName = ""; showNewFolder = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Nueva carpeta")
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = true, onClick = { currentFolder = rootFolder; currentUri = rootUri; refresh++ }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Archivos") })
                    NavigationBarItem(selected = false, onClick = { chooseFolder.launch(null) }, icon = { Icon(Icons.Default.Storage, null) }, label = { Text("Ubicación") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Todo a mano", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Tus carpetas y documentos, sin complicaciones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (rootUri == null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Elige una carpeta para empezar", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Android te permite elegir qué ubicación quieres administrar. Archivero solo accederá a la carpeta que selecciones.")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { chooseFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta") }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${entries.size} elementos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showNewFolder = true }) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(4.dp)); Text("Nueva carpeta") }
                    }
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text("Esta carpeta está vacía")
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(entries, key = { it.uri.toString() }) { file ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().clickable {
                                        if (file.isDirectory) { currentFolder = file; currentUri = file.uri; refresh++ }
                                        else {
                                            try {
                                                val mime = context.contentResolver.getType(file.uri).orEmpty()
                                                if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                                                    playerUri = file.uri
                                                    playerTitle = file.name ?: "Multimedia"
                                                    playerIsVideo = mime.startsWith("video/")
                                                } else {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                                }
                                            } catch (_: Exception) { message = "No hay una aplicación compatible para abrir este archivo." }
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(file.name ?: "Sin nombre", fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text(if (file.isDirectory) "Carpeta" else formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { selected = file; showActions = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Acciones") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) AlertDialog(
        onDismissRequest = { showNewFolder = false },
        title = { Text("Nueva carpeta") },
        text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Nombre de la carpeta") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val name = folderName.trim()
            if (name.isNotEmpty() && current?.findFile(name) == null) {
                if (current?.createDirectory(name) != null) { refresh++; message = "Carpeta creada." } else message = "No se pudo crear la carpeta."
            } else message = "Escribe un nombre válido y que no esté en uso."
            showNewFolder = false
        }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancelar") } }
    )

    if (showActions && selected != null) AlertDialog(
        onDismissRequest = { showActions = false },
        title = { Text("Eliminar elemento") },
        text = { Text("¿Eliminar definitivamente “${selected?.name ?: "Archivo"}”? Esta acción no se puede deshacer.") },
        confirmButton = {
            TextButton(onClick = {
                val f = selected
                if (f != null) {
                    val parent = f.parentFile
                    if (f.delete()) { refresh++; message = "Elemento eliminado." }
                    else message = "Android no permitió eliminarlo."
                }
                showActions = false
            }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { showActions = false }) { Text("Cerrar") } }
    )


    if (playerUri != null) {
        Dialog(
            onDismissRequest = { /* Solo se cierra con la X. */ },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(if (playerIsVideo) "Reproductor de video" else "Reproductor de música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(playerTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        }
                        IconButton(onClick = { playerUri = null }) { Icon(Icons.Default.Close, contentDescription = "Cerrar reproductor") }
                    }
                    Spacer(Modifier.height(12.dp))
                    key(playerUri.toString()) {
                        val exoPlayer = remember(playerUri) {
                            ExoPlayer.Builder(context).build().apply {
                                setMediaItem(MediaItem.fromUri(playerUri!!))
                                prepare()
                                playWhenReady = true
                            }
                        }
                        DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { viewContext -> PlayerView(viewContext).apply { player = exoPlayer; useController = true; keepScreenOn = playerIsVideo } },
                            update = { it.player = exoPlayer }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("El reproductor permanece abierto al terminar la reproducción. Toca ✕ para cerrarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    message?.let { msg ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text("Archivero") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Aceptar") } })
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
