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

internal data class DirectoryEntryOrderKey(
    val isDirectory: Boolean,
    val normalizedName: String,
    val uri: String
)

internal fun directoryEntryOrderKey(isDirectory: Boolean, name: String?, uri: String): DirectoryEntryOrderKey =
    DirectoryEntryOrderKey(
        isDirectory = isDirectory,
        normalizedName = name.orEmpty().lowercase(Locale.ROOT),
        uri = uri
    )

internal fun compareDirectoryEntryOrder(
    first: DirectoryEntryOrderKey,
    second: DirectoryEntryOrderKey
): Int {
    if (first.isDirectory != second.isDirectory) return if (first.isDirectory) -1 else 1
    val byName = first.normalizedName.compareTo(second.normalizedName)
    return if (byName != 0) byName else first.uri.compareTo(second.uri)
}

private data class DirectoryEntrySortValue<T>(
    val entry: T,
    val key: DirectoryEntryOrderKey
)

/** Sorts only the entries passed by the caller, calculating provider metadata once per entry. */
internal fun <T> sortDirectoryEntries(
    entries: Collection<T>,
    keyOf: (T) -> DirectoryEntryOrderKey,
    onKey: (DirectoryEntryOrderKey) -> Unit = {}
): List<T> {
    val decorated = entries.map { entry ->
        val key = keyOf(entry)
        onKey(key)
        DirectoryEntrySortValue(entry, key)
    }.toMutableList()
    decorated.sortWith(
        compareBy<DirectoryEntrySortValue<T>> { !it.key.isDirectory }
            .thenBy { it.key.normalizedName }
            .thenBy { it.key.uri }
    )
    return decorated.map { it.entry }
}

/**
 * Merges two already sorted, non-overlapping pages in O(n + m) time. Callers filter URI
 * duplicates before merging and retain sort keys for previously loaded entries.
 */
internal fun <T> mergeSortedDirectoryEntries(
    existing: List<T>,
    incoming: List<T>,
    keyOf: (T) -> DirectoryEntryOrderKey
): List<T> {
    val merged = ArrayList<T>(existing.size + incoming.size)
    var left = 0
    var right = 0
    while (left < existing.size && right < incoming.size) {
        if (compareDirectoryEntryOrder(keyOf(existing[left]), keyOf(incoming[right])) <= 0) {
            merged.add(existing[left++])
        } else {
            merged.add(incoming[right++])
        }
    }
    while (left < existing.size) merged.add(existing[left++])
    while (right < incoming.size) merged.add(incoming[right++])
    return merged
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
    val truncated: Boolean,
    /** Present only when a UI caller requests cached keys for merging successive sorted pages. */
    val sortKeys: Map<String, DirectoryEntryOrderKey> = emptyMap()
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
    offset: Int = 0,
    includeSortKeys: Boolean = false
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

    val sortKeys = if (includeSortKeys) LinkedHashMap<String, DirectoryEntryOrderKey>() else null
    val sorted = sortDirectoryEntries(
        entries = entries,
        keyOf = { file -> directoryEntryOrderKey(file.isDirectory, file.name, file.uri.toString()) },
        onKey = { key ->
            if (sortKeys != null && key.uri !in sortKeys) sortKeys[key.uri] = key
        }
    )
    return BoundedDocumentListing(sorted, truncated, sortKeys ?: emptyMap())
}
