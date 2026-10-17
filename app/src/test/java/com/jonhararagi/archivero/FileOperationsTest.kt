package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
