package com.jonhararagi.archivero

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryListingTest {
    @Test
    fun filesystemPagesAreBoundedAndDoNotRepeatEntries() {
        val directory = Files.createTempDirectory("archivero-pages").toFile()
        try {
            (1..5).forEach { index ->
                File(directory, "entry-$index.txt").writeText("entry $index")
            }

            val first = listFilesBounded(directory, limit = 2, offset = 0)
            val second = listFilesBounded(directory, limit = 2, offset = 2)
            val third = listFilesBounded(directory, limit = 2, offset = 4)

            assertEquals(2, first.files.size)
            assertTrue(first.truncated)
            assertEquals(2, second.files.size)
            assertTrue(second.truncated)
            assertEquals(1, third.files.size)
            assertFalse(third.truncated)

            val firstUris = first.files.map { it.absolutePath }.toSet()
            val secondUris = second.files.map { it.absolutePath }.toSet()
            val thirdUris = third.files.map { it.absolutePath }.toSet()
            assertTrue(firstUris.intersect(secondUris).isEmpty())
            assertTrue(firstUris.intersect(thirdUris).isEmpty())
            assertTrue(secondUris.intersect(thirdUris).isEmpty())
            assertEquals(5, firstUris.union(secondUris).union(thirdUris).size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPageSize() {
        val directory = Files.createTempDirectory("archivero-pages-invalid-limit").toFile()
        try {
            listFilesBounded(directory, limit = 0, offset = 0)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeOffset() {
        val directory = Files.createTempDirectory("archivero-pages-invalid-offset").toFile()
        try {
            listFilesBounded(directory, limit = 2, offset = -1)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun listingBeyondTheEndIsEmptyAndNotTruncated() {
        val directory = Files.createTempDirectory("archivero-pages-empty").toFile()
        try {
            File(directory, "only.txt").writeText("only")
            val page = listFilesBounded(directory, limit = 2, offset = 4)
            assertTrue(page.files.isEmpty())
            assertFalse(page.truncated)
        } finally {
            directory.deleteRecursively()
        }
    }
    @Test
    fun pageOffsetAdvancesByRawProviderRowsEvenWhenVisibleUrisAreDeduplicated() {
        val alreadyVisible = listOf("content://folder/a", "content://folder/b")
        val rawNextPage = listOf("content://folder/b", "content://folder/c", "content://folder/d")
        val visibleAfterMerge = (alreadyVisible + rawNextPage).distinct()

        assertEquals(4, visibleAfterMerge.size)
        // The next offset must include all 3 provider rows, including the repeated URI.
        assertEquals(5, nextDirectoryPageOffset(currentOffset = 2, entriesRead = rawNextPage.size))
        assertEquals(10_000, nextDirectoryPageOffset(currentOffset = 5_000, entriesRead = 5_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePageOffsetAdvance() {
        nextDirectoryPageOffset(currentOffset = 3, entriesRead = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPageOffsetOverflow() {
        nextDirectoryPageOffset(currentOffset = Int.MAX_VALUE, entriesRead = 1)
    }

}
