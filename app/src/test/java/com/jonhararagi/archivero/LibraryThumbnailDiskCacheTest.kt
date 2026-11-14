package com.jonhararagi.archivero

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(null, cache.read("one"))
        assertArrayEquals(byteArrayOf(2), cache.read("two"))
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
