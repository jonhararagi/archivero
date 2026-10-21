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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Recoverable metadata-only trash. Purge requires a second, explicit confirmation and never
 * deletes the resource addressed by the URI.
 */
@Composable
internal fun LibraryTrashDialog(
    isOpen: Boolean,
    records: List<TrashedLibraryRecord>,
    transferRunning: Boolean,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onPurge: (String) -> Unit
) {
    var pendingPurgeUri by remember(isOpen) { mutableStateOf<String?>(null) }
    if (!isOpen) return

    val pending = pendingPurgeUri
    if (pending != null) {
        val entry = records.firstOrNull { it.record.uri == pending }
        AlertDialog(
            onDismissRequest = { if (!transferRunning) pendingPurgeUri = null },
            title = { Text("Eliminar ficha permanentemente") },
            text = {
                Text(
                    "Se eliminarán solo los metadatos de \"${entry?.record?.displayName ?: "esta ficha"}\" de la papelera. El archivo físico y su contenido no se borrarán."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPurge(pending)
                        pendingPurgeUri = null
                    },
                    enabled = !transferRunning
                ) { Text("Eliminar ficha") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingPurgeUri = null },
                    enabled = !transferRunning
                ) { Text("Cancelar") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!transferRunning) onDismiss() },
        title = { Text("Papelera de la Biblioteca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Aquí se guardan fichas retiradas de la Biblioteca. Restaurarlas recupera sus metadatos; eliminar una ficha de esta papelera no borra el archivo original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (records.isEmpty()) {
                    Text("La papelera está vacía. Las fichas retiradas aparecerán aquí para que puedas restaurarlas.")
                } else {
                    Text("${records.size} ficha(s) recuperable(s)", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.heightIn(max = 430.dp)) {
                        items(records, key = { it.record.uri }) { entry ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(entry.record.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${entry.record.category} · ${entry.record.resourceKind}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Retirada: ${DateFormat.getDateTimeInstance().format(Date(entry.deletedAtMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entry.record.project.isNotBlank()) {
                                    Text("Proyecto: ${entry.record.project}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onRestore(entry.record.uri) },
                                        enabled = !transferRunning,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Default.Restore, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("Restaurar")
                                    }
                                    OutlinedButton(
                                        onClick = { pendingPurgeUri = entry.record.uri },
                                        enabled = !transferRunning,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Default.DeleteForever, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("Eliminar ficha")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !transferRunning) { Text("Cerrar") }
        }
    )
}
