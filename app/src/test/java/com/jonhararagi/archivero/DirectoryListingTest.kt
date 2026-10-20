package com.jonhararagi.archivero

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
}
