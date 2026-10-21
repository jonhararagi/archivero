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
import androidx.compose.material.icons.filled.FolderCopy
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Batch assignment is limited to visible search/filter results and writes project metadata
 * atomically. The project name and sample changes are previewed before committing.
 */
@Composable
internal fun LibraryBulkProjectEditor(
    records: List<LibraryRecord>,
    catalog: DigitalLibraryCatalog?,
    enabled: Boolean,
    onUpdated: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var projectInput by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(records) {
        val currentUris = records.mapTo(HashSet()) { it.uri }
        selectedUris = selectedUris.intersect(currentUris)
    }

    val targetProject = projectInput.trim()
    val selectedRecords = records.filter { it.uri in selectedUris }
    val recordsThatWouldChange = selectedRecords.count { it.project != targetProject && targetProject.isNotEmpty() }

    fun toggleSelection(uri: String, checked: Boolean) {
        selectedUris = if (checked) selectedUris + uri else selectedUris - uri
        feedback = null
    }

    fun applyProject() {
        val currentCatalog = catalog
        if (currentCatalog == null) {
            feedback = "El índice de biblioteca no está disponible."
            return
        }
        if (selectedUris.isEmpty()) {
            feedback = "Selecciona al menos una ficha."
            return
        }
        if (targetProject.isBlank()) {
            feedback = "Escribe el nombre de una colección o proyecto."
            return
        }
        if (targetProject.length > 256 || targetProject.any(Char::isISOControl)) {
            feedback = "El nombre no puede superar 256 caracteres ni contener caracteres de control."
            return
        }

        applying = true
        feedback = null
        val targetUris = selectedUris.toList()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { currentCatalog.assignProjectToRecords(targetUris, targetProject) }
            }
            applying = false
            result.fold(
                { changed ->
                    dialogOpen = false
                    selectedUris = emptySet()
                    projectInput = ""
                    onUpdated(changed, targetProject)
                },
                { error -> feedback = error.message ?: "No se pudo asignar el proyecto." }
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
        androidx.compose.material3.Icon(Icons.Default.FolderCopy, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("Asignar colección")
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!applying) dialogOpen = false },
            title = { Text("Asignar proyecto o colección") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona fichas de los resultados visibles (máximo 500). La operación cambia solo sus metadatos y no mueve archivos.",
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
                    LazyColumn(Modifier.heightIn(max = 210.dp)) {
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
                                        "Actual: ${record.project.ifBlank { "Sin proyecto" }}",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = projectInput,
                        onValueChange = { projectInput = it; feedback = null },
                        label = { Text("Proyecto o colección destino") },
                        placeholder = { Text("Ej.: KapiArt, Personajes principales") },
                        singleLine = true,
                        supportingText = { Text("Máximo 256 caracteres. Se conserva el resto de la ficha.") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !applying
                    )
                    Text("Vista previa", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Destino: ${targetProject.ifBlank { "sin definir" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${recordsThatWouldChange} ficha(s) cambiarían de proyecto de ${selectedRecords.size} seleccionada(s).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    selectedRecords.take(4).forEach { record ->
                        Text(
                            "• ${record.displayName}: ${record.project.ifBlank { "Sin proyecto" }} → ${targetProject.ifBlank { "sin definir" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (feedback != null) {
                        Text(feedback.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { applyProject() },
                    enabled = !applying && enabled && selectedUris.isNotEmpty() &&
                        targetProject.isNotEmpty() && targetProject.length <= 256 &&
                        targetProject.none(Char::isISOControl)
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
