package com.jonhararagi.archivero

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
import androidx.compose.material.icons.filled.FilterCenterFocus
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only visual-similarity scan for the currently visible catalog results.
 * Similarity is a review hint, never proof that two files are duplicates.
 */
@Composable
internal fun LibrarySimilarImageScanner(
    records: List<LibraryRecord>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordsByUri = remember(records) { records.associateBy { it.uri } }
    var scanning by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var similarGroups by remember { mutableStateOf<List<SimilarImageGroup>>(emptyList()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startOrCancelScan() {
        if (scanning) {
            scanJob?.cancel()
            return
        }
        val snapshot = records
        if (snapshot.size < 2) return
        dialogOpen = true
        similarGroups = emptyList()
        progressText = "Preparando ${snapshot.size} ficha(s) visibles…"
        scanning = true
        scanJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val documents = ArrayList<DocumentFile>(snapshot.size)
                    snapshot.forEachIndexed { index, record ->
                        currentCoroutineContext().ensureActive()
                        libraryDocument(context, record.uri)?.let(documents::add)
                        if ((index + 1) % 50 == 0 || index + 1 == snapshot.size) {
                            withContext(Dispatchers.Main) {
                                progressText = "Preparando recursos: ${index + 1}/${snapshot.size}"
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        progressText = "Analizando miniaturas perceptuales…"
                    }
                    findSimilarImages(context, documents)
                }
                similarGroups = result.groups
                progressText = similarImageScanSummary(result)
            } catch (_: CancellationException) {
                progressText = "Análisis cancelado. No se modificó ningún archivo ni ficha."
            } catch (error: Exception) {
                progressText = "No se pudo completar el análisis: ${error.message ?: "comprueba los permisos de los recursos"}"
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
        else Icon(Icons.Default.FilterCenterFocus, contentDescription = null)
        Spacer(Modifier.size(7.dp))
        Text(if (scanning) "Cancelar análisis" else "Buscar fotos parecidas en fichas visibles")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (scanning) scanJob?.cancel()
                dialogOpen = false
            },
            title = { Text("Fotos visualmente parecidas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(progressText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Se analiza únicamente el conjunto de fichas visibles tras aplicar los filtros. La comparación usa una huella perceptual de miniaturas: puede encontrar versiones reescaladas o recomprimidas, pero también agrupar imágenes distintas. Revisa cada grupo manualmente. Nada se borra, mueve ni modifica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (similarGroups.isNotEmpty()) {
                        Text(
                            "${similarGroups.size} grupo(s) · ${similarGroups.sumOf { it.files.size }} archivo(s)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LazyColumn(Modifier.heightIn(max = 330.dp)) {
                            items(
                                similarGroups.take(100),
                                key = { group -> group.files.firstOrNull()?.uri?.toString() ?: "empty-${group.hash}" }
                            ) { group ->
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text("${group.files.size} imagen(es) parecidas", style = MaterialTheme.typography.titleSmall)
                                    group.files.take(20).forEach { file ->
                                        val name = recordsByUri[file.uri.toString()]?.displayName
                                            ?: file.name
                                            ?: file.uri.lastPathSegment
                                            ?: "Imagen sin nombre"
                                        Text("• $name", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (group.files.size > 20) {
                                        Text(
                                            "y ${group.files.size - 20} recurso(s) más en este grupo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (similarGroups.size > 100) {
                            Text(
                                "Se muestran los primeros 100 grupos; el análisis encontró ${similarGroups.size}.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (!scanning) {
                        Text(
                            if (progressText.startsWith("Análisis cancelado")) "Puedes volver a iniciar el análisis cuando lo necesites."
                            else "No hay grupos que mostrar en este conjunto de resultados.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (scanning) scanJob?.cancel()
                    dialogOpen = false
                }) {
                    Text(if (scanning) "Cancelar análisis" else "Cerrar")
                }
            }
        )
    }
}
