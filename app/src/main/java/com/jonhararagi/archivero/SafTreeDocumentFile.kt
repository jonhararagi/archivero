package com.jonhararagi.archivero

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
    val parentId = documentIdForTreeNode(parentUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    val resolver = context.contentResolver
    val cursor = runCatching {
        resolver.query(childrenUri, SAF_CHILD_PROJECTION, null, null, null)
    }.getOrNull() ?: runCatching {
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
    }.getOrNull() ?: throw IOException("El proveedor de Android no pudo enumerar esta carpeta.")

    val entries = ArrayList<DocumentFile>(minOf(limit, 512))
    var skipped = 0
    var truncated = false
    cursor.use { children ->
        val idColumn = children.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        if (idColumn < 0) throw IOException("El proveedor no devolvió identificadores de archivos.")
        while (children.moveToNext()) {
            val childId = children.getString(idColumn) ?: continue
            if (skipped < offset) { skipped++; continue }
            if (entries.size >= limit) { truncated = true; break }
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            entries.add(DocumentFile.fromSingleUri(context, childUri)
                ?: throw IOException("Android no pudo representar un archivo de esta carpeta."))
        }
    }
    return BoundedDocumentListing(entries, truncated)
}

private fun documentIdForTreeNode(uri: Uri): String = try {
    DocumentsContract.getDocumentId(uri)
} catch (error: IllegalArgumentException) {
    try { DocumentsContract.getTreeDocumentId(uri) }
    catch (_: IllegalArgumentException) { throw IOException("Android no pudo identificar esta carpeta.", error) }
}

internal fun findChildDocument(context: Context, parent: DocumentFile, displayName: String): DocumentFile? {
    if (!DocumentsContract.isTreeUri(parent.uri)) return parent.findFile(displayName)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        parent.uri, documentIdForTreeNode(parent.uri)
    )
    val resolver = context.contentResolver
    val cursor = runCatching {
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )
    }.getOrNull() ?: resolver.query(
        childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
    ) ?: throw IOException("Android no pudo buscar dentro de esta carpeta.")

    cursor.use { children ->
        val idColumn = children.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = children.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (idColumn < 0) throw IOException("El proveedor no devolvió identificadores de archivos.")
        while (children.moveToNext()) {
            val childId = children.getString(idColumn) ?: continue
            val candidateName = if (nameColumn >= 0 && !children.isNull(nameColumn)) children.getString(nameColumn) else null
            if (candidateName != null && candidateName != displayName) continue
            val childUri = DocumentsContract.buildDocumentUriUsingTree(parent.uri, childId)
            val candidate = DocumentFile.fromSingleUri(context, childUri) ?: continue
            if (candidateName == displayName || candidate.name == displayName) return candidate
        }
    }
    return null
}

internal fun createChildDocument(context: Context, parent: DocumentFile, mimeType: String, displayName: String): DocumentFile? {
    if (!DocumentsContract.isTreeUri(parent.uri)) return parent.createFile(mimeType, displayName)
    val created = DocumentsContract.createDocument(context.contentResolver, parent.uri, mimeType, displayName)
        ?: return null
    val treeQualified = runCatching {
        DocumentsContract.buildDocumentUriUsingTree(parent.uri, DocumentsContract.getDocumentId(created))
    }.getOrDefault(created)
    return DocumentFile.fromSingleUri(context, treeQualified)
}
