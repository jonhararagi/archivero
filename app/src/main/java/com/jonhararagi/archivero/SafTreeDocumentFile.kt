package com.jonhararagi.archivero

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.database.Cursor
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

private val SAF_CHILD_PROJECTION = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_FLAGS
)

/**
 * A DocumentFile for a child inside a picked SAF tree.
 *
 * DocumentFile.fromTreeUri() always resolves the tree root. Calling it with a child
 * document URI can silently turn that child back into the root, so nested navigation
 * and file actions must keep the selected tree grant AND the current document URI.
 */
internal class SafTreeDocumentFile(
    context: Context,
    parent: DocumentFile?,
    private val treeBaseUri: Uri,
    documentUri: Uri,
    cachedName: String? = null,
    cachedType: String? = null,
    private val cachedSize: Long? = null,
    private val cachedModified: Long? = null,
    private val cachedFlags: Long? = null
) : DocumentFile(parent) {
    private val appContext = context.applicationContext
    private var currentUri: Uri = documentUri
    private var nameCache: String? = cachedName
    private val typeCache: String? = cachedType

    override fun getUri(): Uri = currentUri

    override fun getName(): String? =
        nameCache ?: queryString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            ?: currentUri.lastPathSegment

    override fun getType(): String? =
        typeCache ?: queryString(DocumentsContract.Document.COLUMN_MIME_TYPE)

    override fun canRead(): Boolean = exists() && getType() != null

    override fun canWrite(): Boolean {
        val flags = cachedFlags ?: queryLong(DocumentsContract.Document.COLUMN_FLAGS) ?: 0L
        val writableFlags =
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong() or
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() or
            DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong() or
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()
        return exists() && (flags and writableFlags) != 0L
    }

    override fun exists(): Boolean =
        queryString(DocumentsContract.Document.COLUMN_DOCUMENT_ID) != null

    override fun isDirectory(): Boolean =
        getType() == DocumentsContract.Document.MIME_TYPE_DIR

    override fun isFile(): Boolean = exists() && !isDirectory()

    override fun lastModified(): Long =
        cachedModified ?: queryLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L

    override fun length(): Long =
        cachedSize ?: queryLong(DocumentsContract.Document.COLUMN_SIZE) ?: 0L

    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        val created = DocumentsContract.createDocument(
            appContext.contentResolver,
            currentUri,
            mimeType,
            displayName
        ) ?: return null
        return SafTreeDocumentFile(
            appContext, this, treeBaseUri, created,
            cachedName = displayName, cachedType = mimeType
        )
    }

    override fun createDirectory(displayName: String): DocumentFile? =
        createFile(DocumentsContract.Document.MIME_TYPE_DIR, displayName)

    override fun delete(): Boolean = runCatching {
        DocumentsContract.deleteDocument(appContext.contentResolver, currentUri)
    }.getOrDefault(false)

    override fun renameTo(displayName: String): Boolean {
        val renamed = runCatching {
            DocumentsContract.renameDocument(appContext.contentResolver, currentUri, displayName)
        }.getOrNull() ?: return false
        currentUri = renamed
        nameCache = displayName
        return true
    }

    override fun listFiles(): Array<DocumentFile> =
        querySafTreeChildren(
            appContext, this, treeBaseUri, currentUri, Int.MAX_VALUE, 0
        ).entries.toTypedArray()

    private fun queryString(column: String): String? = runCatching {
        appContext.contentResolver.query(currentUri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun queryLong(column: String): Long? = runCatching {
        appContext.contentResolver.query(currentUri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()
}

internal fun querySafTreeChildren(
    context: Context,
    parent: DocumentFile,
    treeUri: Uri,
    parentUri: Uri,
    limit: Int,
    offset: Int
): BoundedDocumentListing {
    require(limit > 0) { "El límite debe ser mayor que cero." }
    require(offset >= 0) { "El desplazamiento no puede ser negativo." }

    val parentDocumentId = try {
        DocumentsContract.getDocumentId(parentUri)
    } catch (error: IllegalArgumentException) {
        try {
            DocumentsContract.getTreeDocumentId(parentUri)
        } catch (_: IllegalArgumentException) {
            throw IOException("Android no pudo identificar esta carpeta.", error)
        }
    }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val resolver = context.contentResolver
    val cursor = try {
        resolver.query(childrenUri, SAF_CHILD_PROJECTION, null, null, null)
    } catch (_: Exception) {
        null
    } ?: try {
        // Some third-party document providers reject optional metadata columns. Retry
        // with only the required ID, while keeping the same bounded cursor traversal.
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )
    } catch (error: Exception) {
        throw IOException("El proveedor de Android no pudo enumerar esta carpeta.", error)
    } ?: throw IOException("El proveedor de Android no pudo enumerar esta carpeta.")

    val entries = ArrayList<DocumentFile>(minOf(limit, 512))
    var skipped = 0
    var truncated = false
    cursor.use { children ->
        val idIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        if (idIndex < 0) throw IOException("El proveedor no devolvió los identificadores de los archivos.")
        val nameIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val typeIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val flagsIndex = children.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

        while (children.moveToNext()) {
            val childId = children.getString(idIndex) ?: continue
            if (skipped < offset) {
                skipped++
                continue
            }
            if (entries.size >= limit) {
                truncated = true
                break
            }
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            val name = if (nameIndex >= 0 && !children.isNull(nameIndex)) children.getString(nameIndex) else null
            val type = if (typeIndex >= 0 && !children.isNull(typeIndex)) children.getString(typeIndex) else null
            val size = if (sizeIndex >= 0 && !children.isNull(sizeIndex)) children.getLong(sizeIndex) else null
            val modified = if (modifiedIndex >= 0 && !children.isNull(modifiedIndex)) children.getLong(modifiedIndex) else null
            val flags = if (flagsIndex >= 0 && !children.isNull(flagsIndex)) children.getLong(flagsIndex) else null
            entries.add(
                SafTreeDocumentFile(
                    context, parent, treeUri, childUri,
                    cachedName = name,
                    cachedType = type,
                    cachedSize = size,
                    cachedModified = modified,
                    cachedFlags = flags
                )
            )
        }
    }
    return BoundedDocumentListing(entries, truncated)
}
