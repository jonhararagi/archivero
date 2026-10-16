package com.jonhararagi.archivero

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

class FilePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val name = intent.getStringExtra("displayName") ?: uri?.lastPathSegment ?: "Archivo"
        val mime = intent.getStringExtra("mimeType").orEmpty()
        setContent {
            MaterialTheme {
                FilePreviewScreen(uri = uri, name = name, mime = mime, onBack = { finish() })
            }
        }
    }
}

private enum class PreviewMode { IMAGE, PDF, TEXT, LEGACY, UNSUPPORTED }

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun FilePreviewScreen(uri: Uri?, name: String, mime: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mode = when {
        extension == "pdf" -> PreviewMode.PDF
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") -> PreviewMode.IMAGE
        extension in setOf("doc", "xls", "ppt") -> PreviewMode.LEGACY
        extension in setOf("txt", "csv", "rtf", "md", "json", "xml", "html", "htm", "log", "docx", "xlsx", "pptx", "odt", "ods", "odp", "svg") -> PreviewMode.TEXT
        mime.startsWith("image/") -> PreviewMode.IMAGE
        else -> PreviewMode.UNSUPPORTED
    }
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var textContent by remember(uri) { mutableStateOf<String?>(null) }
    var previewError by remember(uri) { mutableStateOf<String?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    var pageCount by remember(uri) { mutableIntStateOf(0) }

    LaunchedEffect(uri, mode) {
        bitmap = null
        textContent = null
        previewError = null
        loading = true
        try {
            when (mode) {
                PreviewMode.IMAGE -> {
                    bitmap = withContext(Dispatchers.IO) {
                        if (uri == null) null else decodeImage(context, uri)
                    }
                    if (bitmap == null) previewError = "Android no pudo decodificar esta imagen. Puede usar un formato no compatible o estar dañada."
                }
                PreviewMode.TEXT -> {
                    textContent = withContext(Dispatchers.IO) {
                        if (uri == null) throw IOException("La ubicación del archivo no está disponible.")
                        readTextPreview(context, uri, extension)
                    }
                }
                PreviewMode.LEGACY -> {
                    textContent = "Este archivo usa un formato Office antiguo (" + extension.uppercase(Locale.ROOT) + "). Archivero puede previsualizar DOCX, XLSX, PPTX y formatos de texto, pero este formato binario antiguo no se puede interpretar de forma fiable sin un conversor."
                }
                PreviewMode.UNSUPPORTED -> previewError = "Este formato no tiene una vista previa integrada. Prueba «Abrir con otra aplicación»."
                PreviewMode.PDF -> Unit
            }
        } catch (error: Exception) {
            previewError = error.message ?: "No se pudo leer este archivo. Comprueba los permisos."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(uri, mode, pageIndex) {
        if (mode != PreviewMode.PDF) return@LaunchedEffect
        loading = true
        previewError = null
        try {
            val result = withContext(Dispatchers.IO) {
                if (uri == null) throw IOException("La ubicación del PDF no está disponible.")
                renderPdfPage(context, uri, pageIndex)
            }
            bitmap = result.first
            pageCount = result.second
        } catch (error: Exception) {
            bitmap = null
            previewError = error.message ?: "No se pudo abrir este PDF."
        } finally {
            loading = false
        }
    }

    fun openExternally() {
        if (uri == null) {
            previewError = "La ubicación del archivo no está disponible."
            return
        }
        val guessedMime = mime.ifBlank { guessMime(extension) }
        val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, guessedMime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(viewIntent, "Abrir con otra aplicación"))
        } catch (_: ActivityNotFoundException) {
            previewError = "No hay otra aplicación instalada que pueda abrir este formato. La vista previa integrada no pudo leer el archivo."
        } catch (error: Exception) {
            previewError = error.message ?: "Android no permitió abrir el archivo con otra aplicación."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver a Archivero")
                    }
                },
                actions = {
                    TextButton(onClick = { openExternally() }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Text(" Abrir con")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (mode == PreviewMode.IMAGE && bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF17191D))
                )
            } else if (mode == PreviewMode.PDF && bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Página " + (pageIndex + 1),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF303030))
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                        Text("Anterior")
                    }
                    Text("Página " + (pageIndex + 1) + " de " + pageCount)
                    TextButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex + 1 < pageCount) {
                        Text("Siguiente")
                    }
                }
            } else if ((mode == PreviewMode.TEXT || mode == PreviewMode.LEGACY) && textContent != null) {
                Text(
                    text = textContent!!,
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mode == PreviewMode.LEGACY) {
                    Button(onClick = { openExternally() }) { Text("Intentar abrir con otra aplicación") }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.height(8.dp))
                        Text(previewError ?: "No hay vista previa para este archivo.", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openExternally() }) { Text("Abrir con otra aplicación") }
                    }
                }
            }
            if (previewError != null && mode != PreviewMode.UNSUPPORTED && mode != PreviewMode.LEGACY) {
                Text(previewError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { openExternally() }) { Text("Probar otra aplicación") }
            }
        }
    }
}

private fun decodeImage(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IOException("Android no pudo leer la imagen.")
    val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    var sample = 1
    while (largest / sample > 2200) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Android no pudo cargar los datos de la imagen.")
}

private fun renderPdfPage(context: Context, uri: Uri, requestedPage: Int): Pair<Bitmap, Int> {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("Android no permite leer este PDF.")
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount < 1) throw IOException("El PDF no contiene páginas.")
            val safeIndex = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val scale = minOf(1800f / page.width, 2200f / page.height).coerceAtMost(2f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap to renderer.pageCount
            }
        }
    }
}

private fun readTextPreview(context: Context, uri: Uri, extension: String): String {
    val archiveExtensions = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")
    if (extension in archiveExtensions) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Android no pudo leer el documento.")
        val pieces = mutableListOf<String>()
        var truncated = false
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relevant = when (extension) {
                    "docx" -> entry.name == "word/document.xml"
                    "xlsx" -> entry.name == "xl/sharedStrings.xml" || entry.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))
                    "pptx" -> entry.name.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))
                    else -> entry.name == "content.xml"
                }
                if (relevant && !entry.isDirectory) {
                    val bytes = readLimited(zip, 450_000)
                    val raw = String(bytes, StandardCharsets.UTF_8)
                    val readable = xmlToReadable(raw)
                    if (readable.isNotBlank()) pieces.add(entry.name + ":\n" + readable)
                    if (bytes.size >= 450_000) {
                        truncated = true
                        break
                    }
                }
            }
        }
        if (pieces.isEmpty()) throw IOException("No se encontró texto que Archivero pueda mostrar en este documento.")
        return pieces.joinToString("\n\n") + if (truncated) "\n\n[Vista parcial: el documento es demasiado grande para mostrarlo de una vez.]" else ""
    }

    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Android no pudo leer el documento.")
    var result = String(readLimited(input, 600_000), StandardCharsets.UTF_8)
    input.close()
    if (extension == "rtf") {
        result = result.replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
            .replace(Regex("""\\[a-zA-Z]+-?[0-9]* ?"""), " ")
            .replace("{", "").replace("}", "")
    } else if (extension == "svg") {
        result = xmlToReadable(result)
    }
    if (result.isBlank()) throw IOException("Este archivo no contiene texto que se pueda mostrar.")
    return result + if (result.length >= 600_000) "\n\n[Vista parcial del archivo]" else ""
}

private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var remaining = limit
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count > 0) {
            output.write(buffer, 0, count)
            remaining -= count
        }
    }
    return output.toByteArray()
}

private fun xmlToReadable(xml: String): String =
    xml.replace(Regex("</(?:w:p|a:p|text:p)[^>]*>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex("\\n\\s*\\n+"), "\n")
        .trim()

private fun guessMime(extension: String): String = when (extension) {
    "pdf" -> "application/pdf"
    "doc", "docx" -> "application/msword"
    "xls", "xlsx" -> "application/vnd.ms-excel"
    "ppt", "pptx" -> "application/vnd.ms-powerpoint"
    "txt", "md", "log" -> "text/plain"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "xml", "svg" -> "application/xml"
    "html", "htm" -> "text/html"
    else -> "*/*"
}
