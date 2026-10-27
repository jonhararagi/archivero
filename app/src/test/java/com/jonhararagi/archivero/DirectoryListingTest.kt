package com.jonhararagi.archivero

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class DirectorySortFixture(
    val name: String,
    val isDirectory: Boolean,
    val uri: String
)

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
    fun mergingLoadedPagesPreservesGlobalDirectoryFirstAlphabeticalOrder() {
        val firstPage = sortDirectoryEntries(
            listOf(
                DirectorySortFixture("zeta.txt", false, "content://files/zeta"),
                DirectorySortFixture("same.txt", false, "content://files/same-z"),
                DirectorySortFixture("Folder B", true, "content://folders/b")
            ),
            keyOf = { directoryEntryOrderKey(it.isDirectory, it.name, it.uri) }
        )
        val secondPage = sortDirectoryEntries(
            listOf(
                DirectorySortFixture("Alpha.txt", false, "content://files/alpha"),
                DirectorySortFixture("Folder A", true, "content://folders/a"),
                DirectorySortFixture("SAME.TXT", false, "content://files/same-a")
            ),
            keyOf = { directoryEntryOrderKey(it.isDirectory, it.name, it.uri) }
        )
        val keys = (firstPage + secondPage).associate { it.uri to directoryEntryOrderKey(it.isDirectory, it.name, it.uri) }

        val merged = mergeSortedDirectoryEntries(firstPage, secondPage) { keys.getValue(it.uri) }

        assertEquals(
            listOf("Folder A", "Folder B", "Alpha.txt", "SAME.TXT", "same.txt", "zeta.txt"),
            merged.map { it.name }
        )
        assertEquals(
            listOf("content://files/same-a", "content://files/same-z"),
            merged.filter { it.name.equals("same.txt", ignoreCase = true) }.map { it.uri }
        )
    }

    @Test
    fun directorySortUsesUriAsStableTieBreakerForSameNamedEntries() {
        val sorted = sortDirectoryEntries(
            listOf(
                DirectorySortFixture("duplicate.png", false, "content://z"),
                DirectorySortFixture("duplicate.png", false, "content://a"),
                DirectorySortFixture("Folder", true, "content://folder"),
                DirectorySortFixture("a.txt", false, "content://file")
            ),
            keyOf = { directoryEntryOrderKey(it.isDirectory, it.name, it.uri) }
        )
        assertEquals(
            listOf("content://folder", "content://file", "content://a", "content://z"),
            sorted.map { it.uri }
        )
    }

}
