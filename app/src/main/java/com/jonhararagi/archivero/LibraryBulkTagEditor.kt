package com.jonhararagi.archivero

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Batch tag editing is constrained to the currently visible (already capped) library results.
 * The user reviews selection and a preview before one atomic catalog write.
 */
@Composable
internal fun LibraryBulkTagEditor(
    records: List<LibraryRecord>,
    catalog: DigitalLibraryCatalog?,
    enabled: Boolean,
    onUpdated: (Int, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tagInput by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(records) {
        val currentUris = records.mapTo(HashSet()) { it.uri }
        selectedUris = selectedUris.intersect(currentUris)
    }

    val requestedTags = remember(tagInput) {
        tagInput.split(",")
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val selectedRecords = records.filter { it.uri in selectedUris }
    val recordsThatWouldChange = selectedRecords.count { record ->
        requestedTags.any { requested ->
            record.tags.none { existing -> existing.equals(requested, ignoreCase = true) }
        }
    }

    fun toggleSelection(uri: String, checked: Boolean) {
        selectedUris = if (checked) selectedUris + uri else selectedUris - uri
        feedback = null
    }

    fun applyTags() {
        val currentCatalog = catalog
        if (currentCatalog == null) {
            feedback = "El índice de biblioteca no está disponible."
            return
        }
        if (selectedUris.isEmpty()) {
            feedback = "Selecciona al menos una ficha."
            return
        }
        if (requestedTags.isEmpty()) {
            feedback = "Escribe al menos una etiqueta separada por comas."
            return
        }
        if (requestedTags.size > 100) {
            feedback = "Puedes aplicar hasta 100 etiquetas en una operación."
            return
        }
        applying = true
        feedback = null
        val targetUris = selectedUris.toList()
        val tags = requestedTags.toList()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { currentCatalog.addTagsToRecords(targetUris, tags) }
            }
            applying = false
            result.fold(
                { changed ->
                    dialogOpen = false
                    selectedUris = emptySet()
                    tagInput = ""
                    onUpdated(changed, tags)
                },
                { error -> feedback = error.message ?: "No se pudieron guardar las etiquetas." }
            )
        }
    }

    OutlinedButton(
        onClick = {
            feedback = null
            dialogOpen = true
        },
        enabled = enabled && records.isNotEmpty(),
        modifier = modifier
    ) {
        androidx.compose.material3.Icon(Icons.Default.LocalOffer, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Etiquetar varios")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!applying) dialogOpen = false },
            title = { Text("Etiquetado por lotes") },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Solo se muestran los resultados visibles del filtro actual (máximo 500). Selecciona fichas y revisa la vista previa antes de guardar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedUris.size} seleccionada(s)", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    selectedUris = records.mapTo(LinkedHashSet()) { it.uri }
                                    feedback = null
                                },
                                enabled = records.isNotEmpty() && !applying
                            ) { Text("Todas") }
                            TextButton(
                                onClick = {
                                    selectedUris = emptySet()
                                    feedback = null
                                },
                                enabled = selectedUris.isNotEmpty() && !applying
                            ) { Text("Ninguna") }
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(records, key = { it.uri }) { record ->
                            val checked = record.uri in selectedUris
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !applying) { toggleSelection(record.uri, !checked) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { value -> toggleSelection(record.uri, value) },
                                    enabled = !applying
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(record.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${record.category} · ${record.tags.size} etiqueta(s)",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it; feedback = null },
                        label = { Text("Etiquetas nuevas") },
                        placeholder = { Text("anime, referencia, personaje") },
                        supportingText = { Text("Separadas por comas; se conservan las etiquetas existentes.") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !applying
                    )
                    Text("Vista previa", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Se añadirán: ${requestedTags.joinToString(", ").ifBlank { "ninguna todavía" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${recordsThatWouldChange} ficha(s) recibirían una o más etiquetas nuevas de ${selectedRecords.size} seleccionada(s).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    selectedRecords.take(3).forEach { record ->
                        val added = requestedTags.filter { newTag ->
                            record.tags.none { existing -> existing.equals(newTag, ignoreCase = true) }
                        }
                        Text(
                            "• ${record.displayName}: +${added.joinToString(", ").ifBlank { "sin cambios" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (requestedTags.size > 100) {
                        Text("Superas el límite de 100 etiquetas.", color = MaterialTheme.colorScheme.error)
                    }
                    if (feedback != null) {
                        Text(feedback.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { applyTags() },
                    enabled = !applying && enabled && selectedUris.isNotEmpty() &&
                        requestedTags.isNotEmpty() && requestedTags.size <= 100
                ) {
                    if (applying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Aplicar a ${selectedUris.size}")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }, enabled = !applying) { Text("Cancelar") }
            }
        )
    }
}
