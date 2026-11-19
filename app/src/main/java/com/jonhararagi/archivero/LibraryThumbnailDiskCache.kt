package com.jonhararagi.archivero

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Small, regenerable disk cache for encoded thumbnails.
 *
 * All paths remain below the supplied app-private cache directory. Cache failures are misses,
 * never reasons to interrupt browsing or thumbnail generation.
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
        cleanupAbandonedTemporaries()
    }

    /**
     * Remove only this cache's own abandoned sibling temporaries. Valid .thumb entries,
     * unrelated temporary files and nested content are deliberately left untouched.
     */
    private fun cleanupAbandonedTemporaries() {
        val children = safe(null as Array<File>?) { directory.listFiles() } ?: return
        children.forEach { file ->
            val ownedTemporary = safe(false) {
                file.isFile && file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX)
            }
            if (ownedTemporary) deleteQuietly(file)
        }
    }

    @Synchronized
    fun read(key: String): ByteArray? {
        return try {
            val file = cacheFile(key) ?: return null
            if (!file.isFile) return null
            val expectedLength = file.length()
            if (expectedLength <= 0L ||
                expectedLength > maxEntryBytes.toLong() ||
                expectedLength > Int.MAX_VALUE
            ) {
                deleteQuietly(file)
                return null
            }

            val bytes = file.inputStream().use { it.readBytes() }
            if (bytes.size.toLong() != expectedLength || bytes.size > maxEntryBytes) {
                deleteQuietly(file)
                null
            } else {
                file.setLastModified(System.currentTimeMillis())
                bytes
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun write(key: String, bytes: ByteArray): Boolean {
        if (key.isBlank() || bytes.isEmpty()) return false
        if (bytes.size > maxEntryBytes || bytes.size.toLong() > maxBytes) return false

        var temporary: File? = null
        return try {
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) return false
            if (!directory.isDirectory) return false

            val destination = cacheFile(key) ?: return false
            val temporaryFile = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, directory)
            temporary = temporaryFile
            FileOutputStream(temporaryFile).use { output ->
                output.write(bytes)
                output.fd.sync()
            }

            try {
                Files.move(
                    temporaryFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            if (!destination.isFile || destination.length() != bytes.size.toLong()) {
                deleteQuietly(destination)
                false
            } else {
                trimToBounds()
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            temporary?.let(::deleteQuietly)
        }
    }

    @Synchronized
    fun remove(key: String) {
        try {
            cacheFile(key)?.let(::deleteQuietly)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Deleting a cache entry is best-effort and must not affect browsing.
        }
    }

    private fun cacheFile(key: String): File? {
        if (key.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory, "$digest.thumb")
    }

    /**
     * Keep only .thumb files within the configured limits. Failed listings/deletions are ignored;
     * they must not make the cache mandatory for the caller.
     */
    private fun trimToBounds() {
        val children = safe(null as Array<File>?) { directory.listFiles() } ?: return
        val files = children.filter { file ->
            safe(false) { file.isFile && file.name.endsWith(THUMB_SUFFIX) }
        }
        val sizes = files.associateWith { file ->
            safe(0L) { file.length().coerceAtLeast(0L) }
        }
        var remainingBytes = sizes.values.sum()
        var remainingEntries = files.size
        if (remainingBytes <= maxBytes && remainingEntries <= maxEntries) return

        val oldestFirst = files.sortedWith(
            compareBy<File> { safe(0L) { it.lastModified() } }.thenBy { safe("") { it.name } }
        )
        for (file in oldestFirst) {
            if (remainingBytes <= maxBytes && remainingEntries <= maxEntries) break
            if (deleteQuietly(file)) {
                remainingBytes = (remainingBytes - (sizes[file] ?: 0L)).coerceAtLeast(0L)
                remainingEntries--
            }
        }
    }

    private fun deleteQuietly(file: File): Boolean = safe(false) { file.delete() }

    private inline fun <T> safe(fallback: T, operation: () -> T): T = try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        fallback
    }

    internal companion object {
        const val DEFAULT_MAX_BYTES: Long = 48L * 1024L * 1024L
        const val DEFAULT_MAX_ENTRIES: Int = 2_048
        const val DEFAULT_MAX_ENTRY_BYTES: Int = 1 * 1024 * 1024

        private const val TEMP_PREFIX = "thumbnail-"
        private const val TEMP_SUFFIX = ".tmp"
        private const val THUMB_SUFFIX = ".thumb"
    }
}
