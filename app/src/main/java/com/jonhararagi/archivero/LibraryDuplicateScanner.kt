package com.jonhararagi.archivero

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A read-only exact-duplicate scan for catalog entries. Hashes are calculated on demand rather
 * than persisted, so a changed resource cannot silently retain a stale fingerprint.
 */
@Composable
internal fun LibraryDuplicateScanner(
    records: List<LibraryRecord>,
    recordsByUri: Map<String, LibraryRecord>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var duplicateGroups by remember { mutableStateOf<List<LibraryDuplicateGroup>>(emptyList()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startOrCancelScan() {
        if (scanning) {
            scanJob?.cancel()
            return
        }
        val snapshot = records
        if (snapshot.size < 2) return
        dialogOpen = true
        duplicateGroups = emptyList()
        progressText = "Preparando el análisis…"
        scanning = true
        scanJob = scope.launch {
            try {
                val groups = withContext(Dispatchers.IO) {
                    val sized = ArrayList<SizedCandidate<LibraryRecord>>()
                    snapshot.forEachIndexed { index, record ->
                        currentCoroutineContext().ensureActive()
                        val document = duplicateDocument(context, record.uri)
                        val size = try {
                            if (document != null && document.exists() && !document.isDirectory) {
                                document.length()
                            } else {
                                -1L
                            }
                        } catch (_: Exception) {
                            -1L
                        }
                        if (size > 0L) sized += SizedCandidate(record, size)
                        if ((index + 1) % 250 == 0 || index + 1 == snapshot.size) {
                            withContext(Dispatchers.Main) {
                                progressText = "Revisando acceso y tamaño: ${index + 1}/${snapshot.size}"
                            }
                        }
                    }

                    // Hash only positive-sized resources whose sizes collide. A size match alone
                    // never counts as a duplicate.
                    val candidates = potentialDuplicateCandidates(sized) { it.uri }
                    withContext(Dispatchers.Main) {
                        progressText = if (candidates.isEmpty()) {
                            "No hay archivos accesibles del mismo tamaño para comparar."
                        } else {
                            "Calculando SHA-256: 0/${candidates.size}"
                        }
                    }
                    val hashed = ArrayList<LibraryHashEntry>()
                    candidates.forEachIndexed { index, candidate ->
                        currentCoroutineContext().ensureActive()
                        val entry = try {
                            hashExactCandidate(context, candidate)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (entry != null) hashed += entry
                        if ((index + 1) % 10 == 0 || index + 1 == candidates.size) {
                            withContext(Dispatchers.Main) {
                                progressText = "Calculando SHA-256: ${index + 1}/${candidates.size}"
                            }
                        }
                    }
                    groupExactLibraryDuplicates(hashed)
                }
                duplicateGroups = groups
                progressText = if (groups.isEmpty()) {
                    "No se encontraron copias exactas entre los archivos legibles con tamaño conocido. Los archivos no se modificaron."
                } else {
                    "Se encontraron ${groups.size} grupos de copias exactas. Este análisis no elimina ni cambia archivos."
                }
            } catch (_: CancellationException) {
                progressText = "Análisis cancelado. No se modificó ningún archivo."
            } catch (error: Exception) {
                progressText = "No se pudo completar el análisis: ${error.message ?: "comprueba los permisos y el acceso a los recursos"}"
            } finally {
                scanning = false
                scanJob = null
            }
        }
    }

    OutlinedButton(
        onClick = { startOrCancelScan() },
        enabled = scanning || (enabled && records.size >= 2),
        modifier = modifier
    ) {
        if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.ContentCopy, contentDescription = null)
        Spacer(Modifier.size(7.dp))
        Text(if (scanning) "Cancelar análisis" else "Buscar duplicados exactos")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (scanning) scanJob?.cancel()
                dialogOpen = false
            },
            title = { Text("Duplicados exactos de la biblioteca") },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(progressText, style = MaterialTheme.typography.bodySmall)
                    if (!scanning && duplicateGroups.isEmpty()) {
                        Text(
                            "La comprobación usa SHA-256 completo y tamaño de archivo. Los recursos sin acceso o sin tamaño positivo se omiten; no se infiere duplicación a partir del nombre.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (duplicateGroups.isNotEmpty()) {
                        Text(
                            "${duplicateGroups.size} grupo(s) · ${duplicateGroups.sumOf { it.uris.size }} archivo(s) encontrados",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LazyColumn(Modifier.heightIn(max = 390.dp)) {
                            items(
                                duplicateGroups.take(100),
                                key = { group -> "${group.sizeBytes}:${group.sha256}:${group.uris.first()}" }
                            ) { group ->
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        "${group.uris.size} copias · ${group.sizeBytes} bytes cada archivo",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    group.uris.take(20).forEach { uri ->
                                        Text(
                                            "• ${recordsByUri[uri]?.displayName ?: "Recurso sin ficha visible"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (group.uris.size > 20) {
                                        Text(
                                            "y ${group.uris.size - 20} recurso(s) más en este grupo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "SHA-256: ${group.sha256.take(20)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (duplicateGroups.size > 100) {
                            Text(
                                "Se muestran los primeros 100 grupos; el total del análisis es ${duplicateGroups.size}.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (scanning) scanJob?.cancel()
                    dialogOpen = false
                }) { Text(if (scanning) "Cancelar análisis" else "Cerrar") }
            }
        )
    }
}

private fun duplicateDocument(context: Context, rawUri: String): DocumentFile? = runCatching {
    val uri = Uri.parse(rawUri)
    if (uri.scheme == "file") {
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return@runCatching null
        DocumentFile.fromFile(File(path))
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }
}.getOrNull()

private suspend fun hashExactCandidate(
    context: Context,
    candidate: SizedCandidate<LibraryRecord>
): LibraryHashEntry? {
    val document = duplicateDocument(context, candidate.value.uri) ?: return null
    if (!document.exists() || document.isDirectory) return null

    val sizeBefore = document.length()
    if (sizeBefore != candidate.size || sizeBefore <= 0L) return null
    // Android providers may return zero when the timestamp is unavailable. That case is handled
    // conservatively after reading; a changed or inconsistently reported timestamp invalidates it.
    val modifiedBefore = runCatching { document.lastModified() }.getOrDefault(-1L)
    val uri = Uri.parse(candidate.value.uri)
    val input = if (uri.scheme == "file") {
        val path = uri.path ?: throw IOException("La ruta local no está disponible.")
        FileInputStream(File(path))
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("Android no permitió leer el recurso.")
    }

    val readingContext = currentCoroutineContext()
    val hash = input.use { stream ->
        hashInputStreamSha256(stream, candidate.size) {
            readingContext.ensureActive()
        }
    }
    readingContext.ensureActive()

    val sizeAfter = runCatching { document.length() }.getOrDefault(-1L)
    val modifiedAfter = runCatching { document.lastModified() }.getOrDefault(-1L)
    if (!duplicateCandidateMetadataStable(
            expectedSizeBytes = candidate.size,
            sizeBeforeBytes = sizeBefore,
            sizeAfterBytes = sizeAfter,
            lastModifiedBeforeMillis = modifiedBefore,
            lastModifiedAfterMillis = modifiedAfter
        )
    ) {
        return null
    }
    return LibraryHashEntry(candidate.value.uri, candidate.size, hash)
}

internal data class LibraryHashEntry(
    val uri: String,
    val sizeBytes: Long,
    val sha256: String
)

internal data class LibraryDuplicateGroup(
    val sizeBytes: Long,
    val sha256: String,
    val uris: List<String>
)

/**
 * A group is an exact duplicate only when at least two distinct catalog URIs have the same
 * positive size and a valid full SHA-256 digest. Invalid/incomplete fingerprints are ignored.
 */
internal fun groupExactLibraryDuplicates(entries: Iterable<LibraryHashEntry>): List<LibraryDuplicateGroup> {
    val valid = entries.asSequence()
        .map { it.copy(uri = it.uri.trim(), sha256 = it.sha256.trim().lowercase(Locale.ROOT)) }
        .filter { it.uri.isNotEmpty() && it.sizeBytes > 0L && SHA256_PATTERN.matches(it.sha256) }
        .distinctBy { it.uri }
        .toList()
    return valid.groupBy { it.sizeBytes to it.sha256 }
        .values
        .filter { group -> group.map { it.uri }.distinct().size > 1 }
        .map { group ->
            LibraryDuplicateGroup(
                sizeBytes = group.first().sizeBytes,
                sha256 = group.first().sha256,
                uris = group.map { it.uri }.distinct().sorted()
            )
        }
        .sortedWith(compareByDescending<LibraryDuplicateGroup> { it.sizeBytes }.thenBy { it.uris.first() })
}

private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
