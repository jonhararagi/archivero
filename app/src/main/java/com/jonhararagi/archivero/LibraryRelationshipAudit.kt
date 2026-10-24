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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LibraryUnresolvedRelationship(
    val sourceUri: String,
    val sourceName: String,
    val targetUri: String
)

internal data class LibraryRelationshipAuditResult(
    val unresolvedRelationCount: Int,
    val unresolvedSamples: List<LibraryUnresolvedRelationship>,
    val unreferencedRecordCount: Int,
    val unreferencedSamples: List<LibraryRecord>
)

/**
 * Audits catalog links without opening, moving, changing, or deleting referenced resources.
 * Samples are capped so a catalog with millions of relationships cannot allocate a huge report.
 * The optional callback lets UI callers cooperate with coroutine cancellation.
 */
internal fun auditLibraryRelationships(
    records: Collection<LibraryRecord>,
    sampleLimit: Int = 100,
    checkActive: () -> Unit = {}
): LibraryRelationshipAuditResult {
    require(sampleLimit in 1..1_000) { "El límite de muestra debe estar entre 1 y 1.000." }
    val stableRecords = records.distinctBy { it.uri }.sortedWith(
        compareBy<LibraryRecord> { it.displayName.lowercase(java.util.Locale.ROOT) }
            .thenBy { it.uri }
    )
    val knownUris = stableRecords.mapTo(HashSet()) { it.uri }
    val incomingUris = HashSet<String>()
    val unresolvedSamples = ArrayList<LibraryUnresolvedRelationship>(sampleLimit)
    var unresolvedCount = 0

    stableRecords.forEachIndexed { index, owner ->
        if (index % 128 == 0) checkActive()
        owner.relatedUris.asSequence().distinct().sorted().forEach { targetUri ->
            if (targetUri in knownUris) {
                incomingUris.add(targetUri)
            } else {
                unresolvedCount++
                if (unresolvedSamples.size < sampleLimit) {
                    unresolvedSamples.add(
                        LibraryUnresolvedRelationship(
                            sourceUri = owner.uri,
                            sourceName = owner.displayName,
                            targetUri = targetUri
                        )
                    )
                }
            }
        }
    }

    val unreferenced = stableRecords.filter { it.uri !in incomingUris }
    return LibraryRelationshipAuditResult(
        unresolvedRelationCount = unresolvedCount,
        unresolvedSamples = unresolvedSamples,
        unreferencedRecordCount = unreferenced.size,
        unreferencedSamples = unreferenced.take(sampleLimit)
    )
}

@Composable
internal fun LibraryRelationshipAuditButton(
    records: List<LibraryRecord>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var auditing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LibraryRelationshipAuditResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var auditJob by remember { mutableStateOf<Job?>(null) }

    fun beginAudit() {
        if (auditing) return
        val snapshot = records
        dialogOpen = true
        auditing = true
        result = null
        errorMessage = null
        auditJob = scope.launch {
            try {
                val report = withContext(Dispatchers.Default) {
                    val coroutineContext = currentCoroutineContext()
                    auditLibraryRelationships(snapshot) { coroutineContext.ensureActive() }
                }
                result = report
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message ?: "No se pudieron revisar las relaciones."
            } finally {
                auditing = false
                auditJob = null
            }
        }
    }

    OutlinedButton(
        onClick = { beginAudit() },
        enabled = enabled && records.isNotEmpty() && !auditing,
        modifier = modifier
    ) {
        androidx.compose.material3.Icon(Icons.Default.Info, contentDescription = null)
        Spacer(Modifier.size(7.dp))
        Text("Revisar relaciones")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (auditing) auditJob?.cancel()
                dialogOpen = false
            },
            title = { Text("Integridad de las relaciones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se revisan solo las URI guardadas en el catálogo. El análisis no abre ni modifica archivos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (auditing) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Revisando relaciones…", style = MaterialTheme.typography.bodySmall)
                    } else if (errorMessage != null) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    } else {
                        val report = result
                        if (report != null) {
                            Text(
                                "Referencias sin ficha: ${report.unresolvedRelationCount}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (report.unresolvedRelationCount > 0) {
                                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                                    items(
                                        report.unresolvedSamples,
                                        key = { issue -> "${issue.sourceUri}::${issue.targetUri}" }
                                    ) { issue ->
                                        Column(
                                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(issue.sourceName, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Destino sin ficha: ${issue.targetUri.take(180)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (report.unresolvedRelationCount > report.unresolvedSamples.size) {
                                    Text(
                                        "Muestra de ${report.unresolvedSamples.size}; quedan ${report.unresolvedRelationCount - report.unresolvedSamples.size} relación(es) sin mostrar.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                "Fichas sin enlaces entrantes: ${report.unreferencedRecordCount}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Es informativo: una ficha independiente puede ser totalmente válida.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (report.unreferencedSamples.isNotEmpty()) {
                                LazyColumn(Modifier.heightIn(max = 150.dp)) {
                                    items(
                                        report.unreferencedSamples,
                                        key = { record -> record.uri }
                                    ) { record ->
                                        Column(
                                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        ) {
                                            Text(record.displayName, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                record.uri.take(180),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (report.unreferencedRecordCount > report.unreferencedSamples.size) {
                                    Text(
                                        "Muestra de ${report.unreferencedSamples.size} ficha(s); el total es ${report.unreferencedRecordCount}.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (report.unresolvedRelationCount == 0 && report.unreferencedRecordCount == 0) {
                                Text("No se encontraron relaciones sin ficha ni registros sin enlaces entrantes.")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (auditing) auditJob?.cancel()
                    dialogOpen = false
                }) { Text(if (auditing) "Cancelar" else "Cerrar") }
            }
        )
    }
}
