package com.jonhararagi.archivero

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale

internal const val MAX_DIRECTORY_ENTRIES = 5_000
internal const val MAX_SCAN_ENTRIES = 100_000

internal data class BoundedFileListing(
    val files: List<File>,
    val truncated: Boolean
)

/**
 * Enumerates a filesystem directory incrementally. Unlike File.listFiles(), this does not
 * allocate an array containing every child of a very large directory.
 */
internal fun listFilesBounded(directory: File, limit: Int): BoundedFileListing {
    require(limit > 0) { "El límite debe ser mayor que cero." }
    if (!directory.isDirectory) throw IOException("La ubicación no es una carpeta legible.")

    val files = ArrayList<File>(minOf(limit, 512))
    var truncated = false
    Files.newDirectoryStream(directory.toPath()).use { stream ->
        val iterator = stream.iterator()
        while (iterator.hasNext()) {
            if (files.size >= limit) {
                truncated = true
                break
            }
            files.add(iterator.next().toFile())
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
    limit: Int
): BoundedDocumentListing {
    require(limit > 0) { "El límite debe ser mayor que cero." }
    val uri = directory.uri
    val entries = ArrayList<DocumentFile>(minOf(limit, 512))
    var truncated = false

    when {
        uri.scheme == "file" -> {
            val path = uri.path ?: throw IOException("La ruta de la carpeta no está disponible.")
            val bounded = listFilesBounded(File(path), limit)
            entries.addAll(bounded.files.map { DocumentFile.fromFile(it) })
            truncated = bounded.truncated
        }

        DocumentsContract.isTreeUri(uri) -> {
            val documentId = try {
                DocumentsContract.getDocumentId(uri)
            } catch (error: IllegalArgumentException) {
                throw IOException("Android no pudo identificar esta carpeta.", error)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("El proveedor de Android no pudo enumerar esta carpeta.")

            cursor.use { children ->
                while (children.moveToNext()) {
                    if (entries.size >= limit) {
                        truncated = true
                        break
                    }
                    val childId = children.getString(0) ?: continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId)
                    BoundedTreeDocumentFile(directory, context, childUri).let(entries::add)
                }
            }
        }

        else -> {
            // Compatibility fallback for providers that do not expose a SAF tree URI.
            val all = directory.listFiles()
            truncated = all.size > limit
            entries.addAll(all.take(limit))
        }
    }

    val sorted = entries.sortedWith(
        compareBy<DocumentFile> { !it.isDirectory }
            .thenBy { it.name.orEmpty().lowercase(Locale.ROOT) }
    )
    return BoundedDocumentListing(sorted, truncated)
}


/**
 * DocumentFile.fromTreeUri() is intended to wrap a tree root, not an arbitrary child URI.
 * This adapter preserves the actual child URI and parent chain while delegating metadata and
 * basic file operations to Android's single-document wrapper. Child enumeration stays bounded.
 */
private class BoundedTreeDocumentFile(
    parent: DocumentFile?,
    private val context: Context,
    private val uri: Uri
) : DocumentFile(parent) {
    private fun single(): DocumentFile? = DocumentFile.fromSingleUri(context, uri)

    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        val created = runCatching {
            DocumentsContract.createDocument(context.contentResolver, uri, mimeType, displayName)
        }.getOrNull() ?: return null
        return BoundedTreeDocumentFile(this, context, created)
    }

    override fun createDirectory(displayName: String): DocumentFile? =
        createFile(DocumentsContract.Document.MIME_TYPE_DIR, displayName)

    override fun getName(): String? = single()?.name
    override fun getType(): String? = single()?.type
    override fun isDirectory(): Boolean = single()?.isDirectory == true
    override fun isFile(): Boolean = single()?.isFile == true
    override fun isVirtual(): Boolean = runCatching {
        DocumentsContract.isVirtualDocument(context.contentResolver, uri)
    }.getOrDefault(false)
    override fun lastModified(): Long = single()?.lastModified() ?: 0L
    override fun length(): Long = single()?.length() ?: 0L
    override fun canRead(): Boolean = single()?.canRead() == true
    override fun canWrite(): Boolean = single()?.canWrite() == true
    override fun delete(): Boolean = single()?.delete() == true
    override fun exists(): Boolean = single()?.exists() == true
    override fun getUri(): Uri = uri
    override fun renameTo(displayName: String): Boolean = single()?.renameTo(displayName) == true

    override fun findFile(displayName: String): DocumentFile? =
        listFiles().firstOrNull { it.name == displayName }

    override fun listFiles(): Array<DocumentFile> = runCatching {
        listDocumentChildrenBounded(context, this, MAX_DIRECTORY_ENTRIES).entries.toTypedArray()
    }.getOrDefault(emptyArray())
}
