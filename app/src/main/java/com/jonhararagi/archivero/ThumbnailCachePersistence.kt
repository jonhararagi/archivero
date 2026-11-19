package com.jonhararagi.archivero

import java.util.concurrent.CancellationException

internal fun canPersistThumbnail(size: Long, lastModified: Long): Boolean =
    size >= 0L && lastModified > 0L

internal data class ThumbnailCacheIdentity(
    val value: String,
    val canPersist: Boolean
)

internal fun thumbnailCacheIdentity(
    uri: String,
    name: String,
    size: Long,
    lastModified: Long
): ThumbnailCacheIdentity = ThumbnailCacheIdentity(
    value = "thumbnail-v1|360|$uri|$name|$size|$lastModified",
    canPersist = canPersistThumbnail(size, lastModified)
)

/**
 * JVM-testable cache read/decode/source fallback. Invalid cache bytes are removed; generation from
 * the original resource is still attempted. Encoding and persistence are optional optimizations.
 */
internal fun <T : Any> loadThumbnailFromCacheOrSource(
    diskCache: LibraryThumbnailDiskCache?,
    key: String,
    decodeCached: (ByteArray) -> T?,
    generateFromSource: () -> T?,
    encodeForCache: (T) -> ByteArray?
): T? {
    if (diskCache != null) {
        val cachedBytes = diskCache.read(key)
        if (cachedBytes != null) {
            val decoded = try {
                decodeCached(cachedBytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (decoded != null) return decoded
            diskCache.remove(key)
        }
    }

    val generated = try {
        generateFromSource()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } ?: return null

    if (diskCache != null) {
        val encoded = try {
            encodeForCache(generated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (encoded != null) diskCache.write(key, encoded)
    }
    return generated
}
