package com.jonhararagi.archivero

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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

internal enum class LibraryResourceAccessState {
    AVAILABLE,
    NOT_VISIBLE,
    NO_ACCESS
}

internal data class LibraryResourceAccessProbe(
    val uri: String,
    val displayName: String,
    val state: LibraryResourceAccessState
)

internal data class LibraryResourceAccessAuditResult(
    val totalRecords: Int,
    val checkedRecords: Int,
    val availableCount: Int,
    val notVisibleCount: Int,
    val noAccessCount: Int,
    val notVisibleSamples: List<LibraryResourceAccessProbe>,
    val noAccessSamples: List<LibraryResourceAccessProbe>
)

/** Incremental accumulator keeps report memory bounded even when the catalog is large. */
internal class LibraryResourceAccessAuditAccumulator(
    private val totalRecords: Int,
    private val sampleLimit: Int = 100
) {
    private var checkedRecords = 0
    private var availableCount = 0
    private var notVisibleCount = 0
    private var noAccessCount = 0
    private val notVisibleSamples = ArrayList<LibraryResourceAccessProbe>()
    private val noAccessSamples = ArrayList<LibraryResourceAccessProbe>()

    init {
        require(totalRecords >= 0) { "La cantidad de fichas no puede ser negativa." }
        require(sampleLimit in 1..1_000) { "El límite de muestra debe estar entre 1 y 1.000." }
    }

    fun record(probe: LibraryResourceAccessProbe) {
        checkedRecords++
        when (probe.state) {
            LibraryResourceAccessState.AVAILABLE -> availableCount++
            LibraryResourceAccessState.NOT_VISIBLE -> {
                notVisibleCount++
                if (notVisibleSamples.size < sampleLimit) notVisibleSamples.add(probe)
            }
            LibraryResourceAccessState.NO_ACCESS -> {
                noAccessCount++
                if (noAccessSamples.size < sampleLimit) noAccessSamples.add(probe)
            }
        }
    }

    fun result(): LibraryResourceAccessAuditResult = LibraryResourceAccessAuditResult(
        totalRecords = totalRecords,
        checkedRecords = checkedRecords,
        availableCount = availableCount,
        notVisibleCount = notVisibleCount,
        noAccessCount = noAccessCount,
        notVisibleSamples = notVisibleSamples.toList(),
        noAccessSamples = noAccessSamples.toList()
    )
}

/**
 * Checks only provider metadata and readability flags. It never reads resource contents or changes
 * the catalog/files. "Not visible" is intentionally not called "deleted": some providers may hide
 * resources when permissions or document-tree visibility changed.
 */
@Composable
internal fun LibraryResourceAccessAuditButton(
    records: List<LibraryRecord>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<LibraryResourceAccessAuditResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startOrCancelScan() {
        if (scanning) {
            scanJob?.cancel()
            return
        }
        val snapshot = records
        dialogOpen = true
        scanning = true
        report = null
        errorMessage = null
        progressText = "Preparando la revisión…"
        scanJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val accumulator = LibraryResourceAccessAuditAccumulator(snapshot.size)
                    snapshot.forEachIndexed { index, record ->
                        currentCoroutineContext().ensureActive()
                        val document: DocumentFile? = libraryDocument(context, record.uri)
                        val state = probeLibraryResourceAccess(document)
                        accumulator.record(
                            LibraryResourceAccessProbe(
                                uri = record.uri,
                                displayName = record.displayName,
                                state = state
                            )
                        )
                        if ((index + 1) % 50 == 0 || index + 1 == snapshot.size) {
                            withContext(Dispatchers.Main) {
                                progressText = "Revisando recursos: ${index + 1}/${snapshot.size}"
                            }
                        }
                    }
                    accumulator.result()
                }
                report = result
                progressText = "Revisión completa. No se modificaron fichas ni archivos."
            } catch (_: CancellationException) {
                progressText = "Revisión cancelada. No se modificó ningún recurso."
            } catch (error: Exception) {
                errorMessage = error.message ?: "No se pudo completar la revisión de acceso."
            } finally {
                scanning = false
                scanJob = null
            }
        }
    }

    OutlinedButton(
        onClick = { startOrCancelScan() },
        enabled = scanning || (enabled && records.isNotEmpty()),
        modifier = modifier
    ) {
        if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text(if (scanning) "Cancelar revisión" else "Revisar acceso")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (scanning) scanJob?.cancel()
                dialogOpen = false
            },
            title = { Text("Acceso a recursos de la Biblioteca") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Consulta si Android y el proveedor de documentos dejan ver y leer cada recurso. No se lee su contenido ni se modifica ningún archivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (scanning) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(progressText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (errorMessage != null) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    val summary = report
                    if (!scanning && summary != null) {
                        Text(
                            "${summary.checkedRecords}/${summary.totalRecords} fichas revisadas · ${summary.availableCount} accesibles",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "No visible para el proveedor: ${summary.notVisibleCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (summary.notVisibleCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (summary.notVisibleCount > 0) {
                            Text(
                                "Podría faltar el recurso o el proveedor podría ocultarlo. Comprueba permisos antes de sacar conclusiones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyColumn(Modifier.heightIn(max = 145.dp)) {
                                items(summary.notVisibleSamples, key = { it.uri }) { probe ->
                                    Column(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(probe.displayName, style = MaterialTheme.typography.bodySmall)
                                        Text(probe.uri.take(180), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (summary.notVisibleCount > summary.notVisibleSamples.size) {
                                Text(
                                    "Se muestran ${summary.notVisibleSamples.size} de ${summary.notVisibleCount}.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            "Sin acceso o lectura: ${summary.noAccessCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (summary.noAccessCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (summary.noAccessCount > 0) {
                            LazyColumn(Modifier.heightIn(max = 145.dp)) {
                                items(summary.noAccessSamples, key = { it.uri }) { probe ->
                                    Column(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(probe.displayName, style = MaterialTheme.typography.bodySmall)
                                        Text(probe.uri.take(180), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (summary.noAccessCount > summary.noAccessSamples.size) {
                                Text(
                                    "Se muestran ${summary.noAccessSamples.size} de ${summary.noAccessCount}.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (summary.notVisibleCount == 0 && summary.noAccessCount == 0) {
                            Text("Todos los recursos comprobados son visibles y legibles en este momento.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (scanning) scanJob?.cancel()
                    dialogOpen = false
                }) { Text(if (scanning) "Cancelar revisión" else "Cerrar") }
            }
        )
    }
}

internal fun probeLibraryResourceAccess(document: DocumentFile?): LibraryResourceAccessState {
    if (document == null) return LibraryResourceAccessState.NO_ACCESS
    val exists = runCatching { document.exists() }
    if (exists.isFailure) return LibraryResourceAccessState.NO_ACCESS
    if (!exists.getOrDefault(false)) return LibraryResourceAccessState.NOT_VISIBLE
    return if (runCatching { document.canRead() }.getOrDefault(false)) {
        LibraryResourceAccessState.AVAILABLE
    } else {
        LibraryResourceAccessState.NO_ACCESS
    }
}
