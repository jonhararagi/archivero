package com.jonhararagi.archivero

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Small, regenerable disk cache for encoded thumbnails.
 *
 * It stores only cache bytes in the supplied app-private cache directory. Keys are hashed before
 * becoming filenames, writes use a sibling temporary file and atomic replacement when supported,
 * and both entry count and total bytes are bounded.
 */
internal class LibraryThumbnailDiskCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryBytes: Int = DEFAULT_MAX_ENTRY_BYTES
) {
    init {
        require(maxBytes > 0L) { "El límite de caché debe ser positivo." }
        require(maxEntries > 0) { "El número máximo de entradas debe ser positivo." }
        require(maxEntryBytes > 0) { "El tamaño máximo por entrada debe ser positivo." }
    }

    @Synchronized
    fun read(key: String): ByteArray? {
        val file = cacheFile(key) ?: return null
        if (!file.isFile) return null
        val length = file.length()
        if (length <= 0L || length > maxEntryBytes.toLong() || length > Int.MAX_VALUE) {
            file.delete()
            return null
        }

        return try {
            val bytes = file.inputStream().use { it.readBytes() }
            if (bytes.size.toLong() != length) {
                file.delete()
                null
            } else {
                file.setLastModified(System.currentTimeMillis())
                bytes
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    @Synchronized
    fun write(key: String, bytes: ByteArray): Boolean {
        if (key.isBlank() || bytes.isEmpty()) return false
        if (bytes.size > maxEntryBytes || bytes.size.toLong() > maxBytes) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        if (!directory.isDirectory) return false

        val destination = cacheFile(key) ?: return false
        var temporary: File? = null
        return try {
            temporary = File.createTempFile("thumbnail-", ".tmp", directory)
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            if (!destination.isFile || destination.length() != bytes.size.toLong()) {
                destination.delete()
                false
            } else {
                trimToBounds()
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            temporary?.delete()
        }
    }

    @Synchronized
    fun remove(key: String) {
        cacheFile(key)?.delete()
    }

    private fun cacheFile(key: String): File? {
        if (key.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory, "$digest.thumb")
    }

    /**
     * The cache directory is private to this feature and is kept bounded after each successful
     * write. At most maxEntries thumbnail files are retained.
     */
    private fun trimToBounds() {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".thumb") }
            ?: return
        var remainingBytes = files.sumOf { it.length().coerceAtLeast(0L) }
        var remainingEntries = files.size
        if (remainingBytes <= maxBytes && remainingEntries <= maxEntries) return

        val oldestFirst = files.sortedWith(
            compareBy<File> { it.lastModified() }.thenBy { it.name }
        )
        for (file in oldestFirst) {
            if (remainingBytes <= maxBytes && remainingEntries <= maxEntries) break
            val fileBytes = file.length().coerceAtLeast(0L)
            if (file.delete()) {
                remainingBytes = (remainingBytes - fileBytes).coerceAtLeast(0L)
                remainingEntries--
            }
        }
    }

    internal companion object {
        const val DEFAULT_MAX_BYTES: Long = 48L * 1024L * 1024L
        const val DEFAULT_MAX_ENTRIES: Int = 2_048
        const val DEFAULT_MAX_ENTRY_BYTES: Int = 1 * 1024 * 1024
    }
}
