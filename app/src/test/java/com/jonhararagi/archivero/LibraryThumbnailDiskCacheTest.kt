package com.jonhararagi.archivero

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryThumbnailDiskCacheTest {
    @Test
    fun thumbnailSurvivesCacheInstanceRecreation() = withTempDirectory { directory ->
        val expected = byteArrayOf(1, 2, 3, 4, 5)
        val firstCache = LibraryThumbnailDiskCache(directory)
        assertTrue(firstCache.write("content://provider/photo.jpg|100|200", expected))

        val reopenedCache = LibraryThumbnailDiskCache(directory)
        assertArrayEquals(expected, reopenedCache.read("content://provider/photo.jpg|100|200"))
    }

    @Test
    fun rejectsEntriesLargerThanConfiguredLimit() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory, maxBytes = 16L, maxEntries = 4, maxEntryBytes = 4)
        assertFalse(cache.write("oversized", byteArrayOf(1, 2, 3, 4, 5)))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun enforcesTotalByteAndEntryCountLimits() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory, maxBytes = 6L, maxEntries = 2, maxEntryBytes = 6)
        assertTrue(cache.write("first", byteArrayOf(1, 2, 3, 4)))
        assertTrue(cache.write("second", byteArrayOf(5, 6, 7, 8)))

        val entries = directory.listFiles().orEmpty().filter { it.name.endsWith(".thumb") }
        assertTrue(entries.size <= 2)
        assertTrue(entries.sumOf { it.length() } <= 6L)
        assertEquals(1, entries.size)
    }

    @Test
    fun hashesKeysInsteadOfUsingThemAsFilePaths() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory)
        assertTrue(cache.write("../../outside/thumbnail", byteArrayOf(9, 8, 7)))

        val entries = directory.listFiles().orEmpty().filter { it.name.endsWith(".thumb") }
        assertEquals(1, entries.size)
        assertEquals(directory.canonicalFile, entries.single().parentFile.canonicalFile)
        assertTrue(cache.read("../../outside/thumbnail")!!.contentEquals(byteArrayOf(9, 8, 7)))
    }

    @Test
    fun removeInvalidatesOnlyTheRequestedEntry() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory)
        assertTrue(cache.write("one", byteArrayOf(1)))
        assertTrue(cache.write("two", byteArrayOf(2)))

        cache.remove("one")

        assertNull(cache.read("one"))
        assertArrayEquals(byteArrayOf(2), cache.read("two"))
    }

    @Test
    fun initializationRemovesOnlyAbandonedThumbnailTemporaries() = withTempDirectory { directory ->
        val abandoned = File(directory, "thumbnail-abandoned.tmp").apply { writeBytes(byteArrayOf(1)) }
        val validThumbnail = File(directory, "entry.thumb").apply { writeBytes(byteArrayOf(2)) }
        val unrelated = File(directory, "provider.tmp").apply { writeBytes(byteArrayOf(3)) }

        LibraryThumbnailDiskCache(directory)

        assertFalse(abandoned.exists())
        assertTrue(validThumbnail.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun preservesFilesAndDirectoriesNotOwnedByThumbnailCache() = withTempDirectory { directory ->
        val unrelatedTemp = File(directory, "upload-session.tmp").apply { writeText("keep") }
        val lookalike = File(directory, "thumbnail-backup.tmp.copy").apply { writeText("keep") }
        val nested = File(directory, "nested").apply { mkdirs() }
        val nestedTemp = File(nested, "thumbnail-nested.tmp").apply { writeText("keep") }

        LibraryThumbnailDiskCache(directory)

        assertTrue(unrelatedTemp.exists())
        assertTrue(lookalike.exists())
        assertTrue(nestedTemp.exists())
    }

    @Test
    fun overwritesSameKeyWithoutLeavingOldBytes() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory)
        val oldBytes = byteArrayOf(1, 2, 3)
        val newBytes = byteArrayOf(9, 8, 7, 6)

        assertTrue(cache.write("same-key", oldBytes))
        assertTrue(cache.write("same-key", newBytes))

        assertArrayEquals(newBytes, cache.read("same-key"))
        val entries = directory.listFiles().orEmpty().filter { it.name.endsWith(".thumb") }
        assertEquals(1, entries.size)
        assertArrayEquals(newBytes, entries.single().readBytes())
        assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("thumbnail-") && it.name.endsWith(".tmp") })
    }

    @Test
    fun invalidatesBytesThatCannotBeDecoded() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory)
        assertTrue(cache.write("corrupt", byteArrayOf(0, 1, 2, 3)))

        val result = loadThumbnailFromCacheOrSource(
            diskCache = cache,
            key = "corrupt",
            decodeCached = { null },
            generateFromSource = { null },
            encodeForCache = { null }
        )

        assertNull(result)
        assertNull(cache.read("corrupt"))
    }

    @Test
    fun regeneratesAndPersistsAfterCorruptCacheEntry() = withTempDirectory { directory ->
        val cache = LibraryThumbnailDiskCache(directory)
        assertTrue(cache.write("regenerate", byteArrayOf(0, 1, 2)))

        val generated = loadThumbnailFromCacheOrSource(
            diskCache = cache,
            key = "regenerate",
            decodeCached = { bytes ->
                bytes.takeIf { it.contentEquals("fresh-preview".toByteArray()) }
                    ?.let { String(it) }
            },
            generateFromSource = { "fresh-preview" },
            encodeForCache = { it.toByteArray() }
        )

        assertEquals("fresh-preview", generated)
        assertArrayEquals("fresh-preview".toByteArray(), cache.read("regenerate"))
        val reopened = loadThumbnailFromCacheOrSource(
            diskCache = LibraryThumbnailDiskCache(directory),
            key = "regenerate",
            decodeCached = { String(it) },
            generateFromSource = { throw AssertionError("No debía regenerar una entrada válida") },
            encodeForCache = { it.toByteArray() }
        )
        assertEquals("fresh-preview", reopened)
    }

    @Test
    fun metadataPolicyKeepsUnknownValuesInMemoryOnly() {
        assertFalse(canPersistThumbnail(size = -1L, lastModified = 123L))
        assertFalse(canPersistThumbnail(size = 12L, lastModified = 0L))
        assertFalse(canPersistThumbnail(size = 12L, lastModified = -1L))
        assertTrue(canPersistThumbnail(size = 12L, lastModified = 1L))
        assertTrue(canPersistThumbnail(size = 0L, lastModified = 1L))
    }

    @Test
    fun invalidCacheLocationIsARecoverableMiss() = withTempDirectory { parent ->
        val invalidDirectory = File(parent, "not-a-directory").apply { writeText("occupied") }
        val cache = LibraryThumbnailDiskCache(invalidDirectory)

        assertNull(cache.read("key"))
        assertFalse(cache.write("key", byteArrayOf(1, 2, 3)))
        cache.remove("key")
        assertEquals("occupied", invalidDirectory.readText())
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = createTempDir(prefix = "archivero-thumb-cache-test-")
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
