package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOperationsTest {
    @Test
    fun acceptsNestedRelativePath() {
        assertEquals(
            listOf("Documentos", "Trabajo", "informe.pdf"),
            safeZipPathSegments("Documentos/Trabajo/informe.pdf")
        )
    }

    @Test
    fun normalizesWindowsSeparators() {
        assertEquals(
            listOf("fotos", "verano", "imagen.jpg"),
            safeZipPathSegments("fotos\\verano\\imagen.jpg")
        )
    }

    @Test
    fun removesCurrentDirectoryMarkersWithoutChangingDestination() {
        assertEquals(
            listOf("documentos", "notas.txt"),
            safeZipPathSegments("./documentos/./notas.txt")
        )
    }

    @Test
    fun rejectsUnixAbsolutePaths() {
        assertThrows(IllegalArgumentException::class.java) {
            safeZipPathSegments("/etc/passwd")
        }
    }

    @Test
    fun rejectsWindowsAbsolutePaths() {
        assertThrows(IllegalArgumentException::class.java) {
            safeZipPathSegments("C:\\Users\\Public\\secreto.txt")
        }
    }

    @Test
    fun rejectsParentDirectoryTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            safeZipPathSegments("documentos/../../secreto.txt")
        }
    }

    @Test
    fun rejectsNullCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            safeZipPathSegments("mal\u0000nombre.txt")
        }
    }

    @Test
    fun rejectsPathsDeeperThanTwentySegments() {
        val tooDeep = (1..21).joinToString("/") { "d$it" } + "/archivo.txt"
        assertThrows(IllegalArgumentException::class.java) {
            safeZipPathSegments(tooDeep)
        }
    }

    @Test
    fun boundsDirectoryListingAndReportsWhenMoreEntriesExist() {
        val directory = Files.createTempDirectory("archivero-listing-test").toFile()
        try {
            (1..12).forEach { File(directory, "archivo-$it.txt").writeText("dato") }
            val result = listFilesBounded(directory, 5)
            assertEquals(5, result.files.size)
            assertTrue(result.truncated)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun doesNotMarkAnExactSizedDirectoryAsTruncated() {
        val directory = Files.createTempDirectory("archivero-listing-exact-test").toFile()
        try {
            (1..5).forEach { File(directory, "archivo-$it.txt").writeText("dato") }
            val result = listFilesBounded(directory, 5)
            assertEquals(5, result.files.size)
            assertFalse(result.truncated)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun traversesLargeDirectoriesInNonOverlappingPages() {
        val directory = Files.createTempDirectory("archivero-listing-pages-test").toFile()
        try {
            (1..12).forEach { File(directory, "archivo-$it.txt").writeText("dato") }

            val first = listFilesBounded(directory, 5, 0)
            val second = listFilesBounded(directory, 5, first.files.size)
            val third = listFilesBounded(directory, 5, first.files.size + second.files.size)
            val allNames = (first.files + second.files + third.files).map { it.name }

            assertTrue(first.truncated)
            assertTrue(second.truncated)
            assertFalse(third.truncated)
            assertEquals(2, third.files.size)
            assertEquals(12, allNames.toSet().size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNegativeDirectoryPageOffset() {
        val directory = Files.createTempDirectory("archivero-listing-offset-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                listFilesBounded(directory, 5, -1)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun returnsAnEmptyNonTruncatedPageWhenOffsetIsPastTheEnd() {
        val directory = Files.createTempDirectory("archivero-listing-empty-page-test").toFile()
        try {
            (1..3).forEach { File(directory, "archivo-$it.txt").writeText("dato") }
            val result = listFilesBounded(directory, 5, 10)
            assertTrue(result.files.isEmpty())
            assertFalse(result.truncated)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonPositiveDirectoryPageSize() {
        val directory = Files.createTempDirectory("archivero-listing-limit-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                listFilesBounded(directory, 0)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

}
