package com.jonhararagi.archivero

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

internal const val MAX_FOLDER_QUICK_ACCESS_ENTRIES = 100
internal const val MAX_FOLDER_QUICK_ACCESS_BYTES = 512 * 1024
private const val QUICK_ACCESS_HEADER = "ARCHIVERO_FOLDER_SHORTCUTS_V1"
private const val MAX_QUICK_ACCESS_URI_LENGTH = 8_192
private const val MAX_QUICK_ACCESS_NAME_LENGTH = 1_024

/** A saved shortcut records both the folder URI and the SAF tree grant used to reach it. */
internal data class FolderQuickAccessEntry(
    val uri: String,
    val rootUri: String,
    val displayName: String
)

internal fun toggleFolderQuickAccess(
    current: List<FolderQuickAccessEntry>,
    candidate: FolderQuickAccessEntry,
    limit: Int = MAX_FOLDER_QUICK_ACCESS_ENTRIES
): List<FolderQuickAccessEntry> {
    require(limit in 1..MAX_FOLDER_QUICK_ACCESS_ENTRIES) { "El límite de accesos rápidos no es válido." }
    val normalized = normalizeFolderQuickAccessEntry(candidate)
    val existing = current.map(::normalizeFolderQuickAccessEntry).distinctBy { it.uri }
    return if (existing.any { it.uri == normalized.uri }) {
        existing.filterNot { it.uri == normalized.uri }.take(limit)
    } else {
        (listOf(normalized) + existing.filterNot { it.uri == normalized.uri }).take(limit)
    }
}

internal fun removeFolderQuickAccess(
    current: List<FolderQuickAccessEntry>,
    uri: String
): List<FolderQuickAccessEntry> {
    val normalizedUri = uri.trim()
    return current.filterNot { it.uri == normalizedUri }
}

/** Versioned, line-safe format: URL-safe Base64 fields cannot contain the dot delimiter. */
internal fun encodeFolderQuickAccess(entries: List<FolderQuickAccessEntry>): String {
    require(entries.size <= MAX_FOLDER_QUICK_ACCESS_ENTRIES) {
        "Hay demasiados accesos rápidos para guardarlos."
    }
    val normalized = entries.map(::normalizeFolderQuickAccessEntry)
    require(normalized.map { it.uri }.distinct().size == normalized.size) {
        "La lista contiene accesos rápidos repetidos."
    }
    val encoder = Base64.getUrlEncoder().withoutPadding()
    fun encode(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    val serialized = buildString {
        append(QUICK_ACCESS_HEADER).append('\n')
        normalized.forEach { entry ->
            append(encode(entry.uri)).append('.')
                .append(encode(entry.rootUri)).append('.')
                .append(encode(entry.displayName)).append('\n')
        }
    }
    require(serialized.length <= MAX_FOLDER_QUICK_ACCESS_BYTES) {
        "Los accesos rápidos ocupan demasiado espacio para guardarse de forma segura."
    }
    return serialized
}

/** Invalid persisted data is rejected instead of silently dropping or partially restoring shortcuts. */
@Throws(IOException::class)
internal fun decodeFolderQuickAccess(serialized: String?): List<FolderQuickAccessEntry> {
    if (serialized.isNullOrBlank()) return emptyList()
    if (serialized.length > MAX_FOLDER_QUICK_ACCESS_BYTES) {
        throw IOException("La lista de accesos rápidos supera el tamaño máximo permitido.")
    }
    val lines = serialized.lineSequence().filter { it.isNotBlank() }.toList()
    if (lines.firstOrNull() != QUICK_ACCESS_HEADER) {
        throw IOException("El formato de accesos rápidos no es compatible.")
    }
    if (lines.size - 1 > MAX_FOLDER_QUICK_ACCESS_ENTRIES) {
        throw IOException("La lista de accesos rápidos supera el límite permitido.")
    }
    val decoder = Base64.getUrlDecoder()
    fun decode(value: String): String = try {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        throw IOException("Un acceso rápido contiene datos dañados.", error)
    }

    val entries = ArrayList<FolderQuickAccessEntry>(lines.size - 1)
    val seenUris = HashSet<String>()
    lines.drop(1).forEach { line ->
        val parts = line.split('.')
        if (parts.size != 3) throw IOException("Un acceso rápido está incompleto.")
        val entry = try {
            normalizeFolderQuickAccessEntry(
                FolderQuickAccessEntry(
                    uri = decode(parts[0]),
                    rootUri = decode(parts[1]),
                    displayName = decode(parts[2])
                )
            )
        } catch (error: IllegalArgumentException) {
            throw IOException("Un acceso rápido no es válido.", error)
        }
        if (!seenUris.add(entry.uri)) {
            throw IOException("La lista contiene accesos rápidos duplicados.")
        }
        entries += entry
    }
    return entries
}

private fun normalizeFolderQuickAccessEntry(entry: FolderQuickAccessEntry): FolderQuickAccessEntry {
    val uri = entry.uri.trim()
    val rootUri = entry.rootUri.trim()
    val displayName = entry.displayName.trim()
    require(uri.isNotEmpty() && uri.length <= MAX_QUICK_ACCESS_URI_LENGTH) {
        "La ubicación del acceso rápido no es válida."
    }
    require(rootUri.isNotEmpty() && rootUri.length <= MAX_QUICK_ACCESS_URI_LENGTH) {
        "La ubicación raíz del acceso rápido no es válida."
    }
    require(uri.substringBefore(':').lowercase(Locale.ROOT) in setOf("file", "content")) {
        "El tipo de ubicación del acceso rápido no es compatible."
    }
    require(rootUri.substringBefore(':').lowercase(Locale.ROOT) in setOf("file", "content")) {
        "El tipo de raíz del acceso rápido no es compatible."
    }
    require(uri.substringBefore(':').equals(rootUri.substringBefore(':'), ignoreCase = true)) {
        "La carpeta y la raíz del acceso rápido deben utilizar el mismo proveedor."
    }
    require(displayName.isNotEmpty() && displayName.length <= MAX_QUICK_ACCESS_NAME_LENGTH) {
        "El nombre del acceso rápido no es válido."
    }
    return FolderQuickAccessEntry(uri, rootUri, displayName)
}
