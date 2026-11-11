package com.jonhararagi.archivero

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

internal const val MAX_DIRECTORY_ENTRIES = 5_000
internal const val MAX_SCAN_ENTRIES = 100_000

/**
 * Advances the offset by raw entries consumed from a provider, not by the visible URI count.
 * The UI deduplicates overlapping pages, so using its visible count can otherwise repeat pages.
 */
internal fun nextDirectoryPageOffset(currentOffset: Int, entriesRead: Int): Int {
    require(currentOffset >= 0) { "El desplazamiento actual no puede ser negativo." }
    require(entriesRead >= 0) { "La cantidad de entradas leídas no puede ser negativa." }
    require(currentOffset <= Int.MAX_VALUE - entriesRead) { "El desplazamiento de la carpeta excede el límite permitido." }
    return currentOffset + entriesRead
}

internal data class BoundedFileListing(
    val files: List<File>,
    val truncated: Boolean
)

/**
 * Enumerates a filesystem directory incrementally. Unlike File.listFiles(), this does not
 * allocate an array containing every child of a very large directory.
 */
internal fun listFilesBounded(directory: File, limit: Int, offset: Int = 0): BoundedFileListing {
    require(limit > 0) { "El límite debe ser mayor que cero." }
    require(offset >= 0) { "El desplazamiento no puede ser negativo." }
    if (!directory.isDirectory) throw IOException("La ubicación no es una carpeta legible.")

    val files = ArrayList<File>(minOf(limit, 512))
    var truncated = false
    Files.newDirectoryStream(directory.toPath()).use { stream ->
        val iterator = stream.iterator()
        var skipped = 0
        while (iterator.hasNext()) {
            val child = iterator.next().toFile()
            if (skipped < offset) {
                skipped++
                continue
            }
            if (files.size >= limit) {
                truncated = true
                break
            }
            files.add(child)
        }
    }
    return BoundedFileListing(files, truncated)
}

internal data class BoundedDocumentListing(
    val entries: List<DocumentFile>,
    val truncated: Boolean
)

/**
 * Uses streaming enumeration for file:// folders and a cursor for SAF tree folders.
 * This keeps both the filesystem and document-provider paths from constructing an unbounded
 * in-memory list. Unknown providers use DocumentFile's fallback API.
 */
internal fun listDocumentChildrenBounded(
    context: Context,
    directory: DocumentFile,
    limit: Int,
    offset: Int = 0
): BoundedDocumentListing {
    require(limit > 0) { "El límite debe ser mayor que cero." }
    require(offset >= 0) { "El desplazamiento no puede ser negativo." }
    val uri = directory.uri
    val entries = ArrayList<DocumentFile>(minOf(limit, 512))
    var truncated = false

    when {
        uri.scheme == "file" -> {
            val path = uri.path ?: throw IOException("La ruta de la carpeta no está disponible.")
            val bounded = listFilesBounded(File(path), limit, offset)
            entries.addAll(bounded.files.map { DocumentFile.fromFile(it) })
            truncated = bounded.truncated
        }

        DocumentsContract.isTreeUri(uri) -> {
            // Query bounded pages, and wrap each child with its exact document URI.
            // fromTreeUri() here would reset every child to the selected tree root.
            val bounded = querySafTreeChildren(context, directory, uri, uri, limit, offset)
            entries.addAll(bounded.entries)
            truncated = bounded.truncated
        }

        else -> {
            // DocumentFile.listFiles() materializes the full child array before callers can
            // apply a limit. Refuse this uncommon path rather than risk an OOM in a huge folder.
            // Ask the user to reselect the directory through the SAF tree picker so the cursor
            // based, bounded path above can be used.
            throw IOException(
                "Este proveedor no permite enumerar esta carpeta por lotes de forma segura. " +
                    "Vuelve a elegirla mediante «Seleccionar carpeta» para conceder acceso al árbol completo."
            )
        }
    }

    val sorted = entries.sortedWith(
        compareBy<DocumentFile> { !it.isDirectory }
            .thenBy { it.name.orEmpty().lowercase(Locale.ROOT) }
    )
    return BoundedDocumentListing(sorted, truncated)
}
