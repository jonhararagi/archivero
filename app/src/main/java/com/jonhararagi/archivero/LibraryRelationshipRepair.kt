package com.jonhararagi.archivero

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class LibraryRelationshipRepairChange(
    val uri: String,
    val expectedRelatedUris: List<String>,
    val replacementRelatedUris: List<String>
)

internal data class LibraryRelationshipRepairSample(
    val uri: String,
    val displayName: String,
    val removedRelationCount: Int
)

internal data class LibraryRelationshipRepairPlan(
    val checkedRecordCount: Int,
    val activeUris: Set<String>,
    val retainedTargetUris: Set<String>,
    val relationshipFingerprint: String,
    val affectedRecordCount: Int,
    val removedRelationCount: Int,
    val samples: List<LibraryRelationshipRepairSample>,
    internal val changes: List<LibraryRelationshipRepairChange>
)

internal data class LibraryRelationshipRepairSummary(
    val changedRecordCount: Int,
    val removedRelationCount: Int
)

/**
 * Fingerprints active/trash identities and relation lists, but not unrelated display metadata.
 * A preview becomes stale if targets or relationships change before confirmation.
 */
internal fun libraryRelationshipFingerprint(
    records: Collection<LibraryRecord>,
    retainedTargetUris: Set<String>
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun addToken(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }

    records.sortedBy { it.uri }.forEach { record ->
        addToken("active")
        addToken(record.uri)
        addToken(record.relatedUris.size.toString())
        record.relatedUris.forEach(::addToken)
    }
    retainedTargetUris.sorted().forEach { uri ->
        addToken("retained")
        addToken(uri)
    }
    return digest.digest().joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

/**
 * Previews links whose target is neither an active ficha nor a trashed ficha.
 * This function never mutates the catalog. The user must confirm a generated plan.
 */
internal fun planLibraryRelationshipRepair(
    records: Collection<LibraryRecord>,
    retainedTargetUris: Set<String> = emptySet(),
    sampleLimit: Int = 4,
    checkActive: () -> Unit = {}
): LibraryRelationshipRepairPlan {
    require(sampleLimit in 1..1_000) { "El límite de muestra debe estar entre 1 y 1.000." }
    val stableRecords = records.toList().sortedWith(
        compareBy<LibraryRecord> { it.displayName.lowercase(java.util.Locale.ROOT) }
            .thenBy { it.uri }
    )
    val activeUris = stableRecords.mapTo(LinkedHashSet()) { it.uri }
    require(activeUris.size == stableRecords.size) { "La Biblioteca contiene URI duplicadas." }
    val retained = retainedTargetUris.toSet()
    require(activeUris.intersect(retained).isEmpty()) {
        "Una URI no puede estar activa y en la papelera a la vez."
    }
    val validTargets = activeUris + retained
    val changes = ArrayList<LibraryRelationshipRepairChange>()
    val samples = ArrayList<LibraryRelationshipRepairSample>(sampleLimit)
    var removedTotal = 0

    stableRecords.forEachIndexed { index, record ->
        if (index % 128 == 0) checkActive()
        val replacement = record.relatedUris.filter { it in validTargets }
        val removed = record.relatedUris.size - replacement.size
        if (removed > 0) {
            changes.add(
                LibraryRelationshipRepairChange(
                    uri = record.uri,
                    expectedRelatedUris = record.relatedUris.toList(),
                    replacementRelatedUris = replacement
                )
            )
            if (samples.size < sampleLimit) {
                samples.add(
                    LibraryRelationshipRepairSample(
                        uri = record.uri,
                        displayName = record.displayName,
                        removedRelationCount = removed
                    )
                )
            }
            removedTotal += removed
        }
    }

    return LibraryRelationshipRepairPlan(
        checkedRecordCount = stableRecords.size,
        activeUris = activeUris.toSet(),
        retainedTargetUris = retained,
        relationshipFingerprint = libraryRelationshipFingerprint(stableRecords, retained),
        affectedRecordCount = changes.size,
        removedRelationCount = removedTotal,
        samples = samples.toList(),
        changes = changes.toList()
    )
}

@Composable
internal fun LibraryRelationshipRepairButton(
    records: List<LibraryRecord>,
    trashedRecords: List<TrashedLibraryRecord>,
    catalog: DigitalLibraryCatalog?,
    enabled: Boolean,
    onRepaired: (LibraryRelationshipRepairSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val retainedTargetUris = remember(trashedRecords) {
        trashedRecords.mapTo(LinkedHashSet()) { it.record.uri }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<LibraryRelationshipRepairPlan?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    fun preparePreview() {
        if (preparing || applying) return
        val snapshot = records.toList()
        val retained = retainedTargetUris.toSet()
        dialogOpen = true
        preparing = true
        plan = null
        errorMessage = null
        previewJob = scope.launch {
            try {
                val outcome = withContext(Dispatchers.Default) {
                    val coroutineContext = currentCoroutineContext()
                    runCatching {
                        planLibraryRelationshipRepair(snapshot, retained) {
                            coroutineContext.ensureActive()
                        }
                    }
                }
                outcome.fold(
                    { preview -> plan = preview },
                    { error -> errorMessage = error.message ?: "No se pudo preparar la vista previa." }
                )
            } catch (_: CancellationException) {
                // Preview cancellation has no side effects.
            } finally {
                preparing = false
                previewJob = null
            }
        }
    }

    fun applyPreview() {
        val currentCatalog = catalog ?: return
        val preview = plan ?: return
        if (applying || preview.removedRelationCount == 0) return
        applying = true
        errorMessage = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { currentCatalog.applyRelationshipRepair(preview) }
            }
            applying = false
            outcome.fold(
                { summary ->
                    dialogOpen = false
                    plan = null
                    onRepaired(summary)
                },
                { error ->
                    plan = null
                    errorMessage = error.message
                        ?: "La Biblioteca cambió o no se pudo guardar; vuelve a preparar la vista previa."
                }
            )
        }
    }

    OutlinedButton(
        onClick = { preparePreview() },
        enabled = enabled && catalog != null && records.isNotEmpty() && !dialogOpen,
        modifier = modifier
    ) {
        androidx.compose.material3.Icon(Icons.Default.Build, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Reparar enlaces")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (preparing) previewJob?.cancel()
                if (!applying) dialogOpen = false
            },
            title = { Text("Reparar relaciones de la Biblioteca") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La reparación elimina únicamente enlaces de metadatos cuyo destino no está ni en la Biblioteca activa ni en la papelera. No borra fichas ni archivos físicos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (preparing || applying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                if (preparing) "Analizando relaciones…" else "Guardando cambios…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    errorMessage?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (!preparing && !applying && plan == null) {
                            TextButton(onClick = { preparePreview() }) { Text("Volver a preparar vista previa") }
                        }
                    }
                    plan?.let { preview ->
                        Text(
                            "Revisión completada: " + preview.checkedRecordCount + " fichas examinadas.",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (preview.removedRelationCount == 0) {
                            Text("No se encontraron enlaces que deban quitarse. Las referencias a fichas en la papelera se conservan.")
                        } else {
                            Text(
                                "Vista previa: se quitarán " + preview.removedRelationCount +
                                    " enlace(s) de " + preview.affectedRecordCount + " ficha(s).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LazyColumn(Modifier.heightIn(max = 170.dp)) {
                                items(preview.samples, key = { it.uri }) { sample ->
                                    Column(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(sample.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            sample.removedRelationCount.toString() + " enlace(s) sin destino",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (preview.affectedRecordCount > preview.samples.size) {
                                Text(
                                    "Muestra de " + preview.samples.size + " de " + preview.affectedRecordCount +
                                        " fichas afectadas; el recuento incluye todo el índice.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Los enlaces a fichas de la papelera se conservarán para que puedan recuperarse al restaurar el destino.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((plan?.removedRelationCount ?: 0) > 0) {
                        Button(
                            onClick = { applyPreview() },
                            enabled = !preparing && !applying && catalog != null
                        ) { Text(if (applying) "Guardando…" else "Aplicar reparación") }
                    }
                    TextButton(
                        onClick = {
                            if (preparing) previewJob?.cancel()
                            if (!applying) {
                                dialogOpen = false
                                plan = null
                            }
                        },
                        enabled = !applying
                    ) { Text(if (preparing) "Cancelar" else "Cerrar") }
                }
            }
        )
    }
}
