package com.jonhararagi.archivero

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * File operations run from Dispatchers.IO. Writes clean up partially-created destinations
 * on failure, and archive extraction never overwrites an existing file.
 */
internal object FileOperations {
    private const val MAX_ARCHIVE_ENTRIES = 5_000
    private const val MAX_ARCHIVE_ENTRY_BYTES = 256L * 1024 * 1024
    private const val MAX_ARCHIVE_TOTAL_BYTES = 512L * 1024 * 1024
    private const val MAX_ARCHIVE_DEPTH = 20

    fun copy(context: Context, source: DocumentFile, parent: DocumentFile): String {
        requireUsableSource(source)
        requireWritableDirectory(parent)
        val original = source.name ?: "archivo"
        val (base, extension) = splitName(original)
        val candidate = uniqueName(parent) { index ->
            val suffix = if (index == 1) " (copia)" else " (copia " + index + ")"
            base + suffix + extension
        }
        val destination = createFile(context, parent, source, candidate)
        copyContent(context, source, destination)
        return destination.name ?: candidate
    }

    fun move(context: Context, source: DocumentFile, parent: DocumentFile): String {
        requireUsableSource(source)
        requireWritableDirectory(parent)
        require(source.uri != parent.uri) { "Elige una carpeta de destino diferente." }
        val original = source.name ?: "archivo"
        val (base, extension) = splitName(original)
        val candidate = uniqueName(parent) { index ->
            if (index == 1) original else base + " (" + index + ")" + extension
        }
        val destination = createFile(context, parent, source, candidate)

        // If copying fails, remove any partial destination. If deleting the source fails
        // after a successful copy, preserve the complete copy so no data is lost.
        copyContent(context, source, destination)
        if (!source.delete()) {
            throw IOException(
                "El archivo se copió como '" + (destination.name ?: candidate) +
                    "', pero Android no permitió borrar el original. Se conservaron ambos archivos."
            )
        }
        return destination.name ?: candidate
    }

    fun compress(context: Context, source: DocumentFile, parent: DocumentFile): String {
        requireUsableSource(source)
        requireWritableDirectory(parent)
        val original = source.name ?: "archivo"
        val (base, _) = splitName(original)
        val candidate = uniqueName(parent) { index ->
            val suffix = if (index == 1) "" else " (" + index + ")"
            base + suffix + ".zip"
        }
        val archive = parent.createFile("application/zip", candidate)
            ?: throw IOException("Android no permitió crear el archivo ZIP.")

        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IOException("No se pudo leer el archivo original.")
            input.use { inputStream ->
                val output = context.contentResolver.openOutputStream(archive.uri, "w")
                    ?: throw IOException("No se pudo escribir el archivo ZIP.")
                output.use { outputStream ->
                    ZipOutputStream(outputStream).use { zip ->
                        zip.putNextEntry(ZipEntry(original))
                        inputStream.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
        } catch (error: Exception) {
            deletePartialOrReport(archive, error)
            throw error
        }
        return archive.name ?: candidate
    }

    fun extractZip(context: Context, archive: DocumentFile, parent: DocumentFile): Int {
        requireUsableSource(archive)
        require(archive.name.orEmpty().endsWith(".zip", ignoreCase = true)) {
            "La extracción está disponible para archivos ZIP."
        }
        requireWritableDirectory(parent)

        val createdFiles = mutableListOf<DocumentFile>()
        val createdDirectories = mutableListOf<DocumentFile>()
        var extractedFiles = 0
        var visitedEntries = 0
        var totalBytes = 0L

        try {
            val input = context.contentResolver.openInputStream(archive.uri)
                ?: throw IOException("No se pudo leer el archivo ZIP.")
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    visitedEntries++
                    require(visitedEntries <= MAX_ARCHIVE_ENTRIES) {
                        "El ZIP supera el límite de " + MAX_ARCHIVE_ENTRIES + " entradas. Se canceló la extracción."
                    }

                    val parts = safeZipPathSegments(entry.name)
                    if (parts.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }

                    var directory = parent
                    val directoryParts = if (entry.isDirectory) parts else parts.dropLast(1)
                    for (part in directoryParts) {
                        val existing = directory.findFile(part)
                        directory = if (existing != null) {
                            require(existing.isDirectory) {
                                "No se puede extraer '" + entry.name + "': ya existe un archivo con el nombre de una carpeta."
                            }
                            existing
                        } else {
                            (directory.createDirectory(part)
                                ?: throw IOException("No se pudo crear la carpeta '" + part + "'."))
                                .also { createdDirectories.add(it) }
                        }
                    }

                    if (!entry.isDirectory) {
                        val name = parts.last()
                        require(directory.findFile(name) == null) {
                            "Ya existe '" + name + "'. No se sobrescribieron archivos; se canceló la extracción."
                        }
                        val destination = directory.createFile("application/octet-stream", name)
                            ?: throw IOException("No se pudo crear '" + name + "'.")
                        createdFiles.add(destination)

                        val output = context.contentResolver.openOutputStream(destination.uri, "w")
                            ?: throw IOException("No se pudo escribir '" + name + "'.")
                        output.use { outputStream ->
                            var entryBytes = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                entryBytes += count
                                totalBytes += count
                                require(entryBytes <= MAX_ARCHIVE_ENTRY_BYTES) {
                                    "El archivo '" + entry.name + "' supera el límite de 256 MiB. Se canceló la extracción."
                                }
                                require(totalBytes <= MAX_ARCHIVE_TOTAL_BYTES) {
                                    "El ZIP supera el límite total de 512 MiB descomprimidos. Se canceló la extracción."
                                }
                                outputStream.write(buffer, 0, count)
                            }
                        }
                        extractedFiles++
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            var filesRemoved = true
            for (created in createdFiles.asReversed()) {
                if (!runCatching { created.delete() }.getOrDefault(false)) filesRemoved = false
            }
            var directoriesRemoved = true
            for (created in createdDirectories.asReversed()) {
                if (!runCatching { created.delete() }.getOrDefault(false)) directoriesRemoved = false
            }
            if (!filesRemoved || !directoriesRemoved) {
                throw IOException(
                    (error.message ?: "Falló la extracción") +
                        ". Android no permitió retirar todos los elementos parciales; revisa la carpeta de destino.",
                    error
                )
            }
            throw error
        }

        return extractedFiles
    }

    private fun requireUsableSource(source: DocumentFile) {
        require(!source.isDirectory) { "Selecciona un archivo, no una carpeta." }
        require(source.exists() && source.canRead()) { "No se puede leer el archivo de origen." }
    }

    private fun requireWritableDirectory(parent: DocumentFile) {
        require(parent.isDirectory && parent.exists() && parent.canWrite()) {
            "Elige una carpeta de destino con permiso de escritura."
        }
    }

    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    }

    private fun uniqueName(parent: DocumentFile, build: (Int) -> String): String {
        var index = 1
        while (true) {
            val candidate = build(index)
            if (parent.findFile(candidate) == null) return candidate
            index++
            if (index > 100_000) throw IOException("No se encontró un nombre de destino disponible.")
        }
    }

    private fun createFile(
        context: Context,
        parent: DocumentFile,
        source: DocumentFile,
        name: String
    ): DocumentFile {
        val mime = context.contentResolver.getType(source.uri) ?: "application/octet-stream"
        return parent.createFile(mime, name)
            ?: throw IOException("Android no permitió crear '" + name + "'.")
    }

    private fun copyContent(context: Context, source: DocumentFile, destination: DocumentFile) {
        try {
            val input = context.contentResolver.openInputStream(source.uri)
                ?: throw IOException("No se pudo leer el archivo original.")
            input.use { inputStream ->
                val output = context.contentResolver.openOutputStream(destination.uri, "w")
                    ?: throw IOException("No se pudo escribir la copia.")
                output.use { outputStream -> inputStream.copyTo(outputStream) }
            }
        } catch (error: Exception) {
            deletePartialOrReport(destination, error)
            throw error
        }
    }

    private fun deletePartialOrReport(destination: DocumentFile, cause: Exception) {
        val removed = runCatching { destination.delete() }.getOrDefault(false)
        if (!removed) {
            throw IOException(
                (cause.message ?: "La operación falló") +
                    ". Android no permitió eliminar el archivo parcial '" +
                    (destination.name ?: "sin nombre") + "'; revísalo manualmente.",
                cause
            )
        }
    }
}

internal fun safeZipPathSegments(entryName: String): List<String> {
    val normalized = entryName.replace('\\', '/')
    require(!normalized.startsWith("/") && !normalized.matches(Regex("^[A-Za-z]:.*"))) {
        "No se permiten rutas absolutas."
    }
    val parts = normalized.split('/').filter { it.isNotBlank() && it != "." }
    require(parts.none { it == ".." || it.contains('\u0000') }) {
        "No se permiten segmentos de ruta inseguros."
    }
    require(parts.size <= 20) { "La ruta excede la profundidad permitida." }
    return parts
}
