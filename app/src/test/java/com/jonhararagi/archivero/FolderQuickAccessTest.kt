package com.jonhararagi.archivero

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FolderQuickAccessTest {
    @Test
    fun roundTripPreservesOrderUnicodeAndNamesWithLineBreaks() {
        val entries = listOf(
            FolderQuickAccessEntry(
                uri = "content://com.android.externalstorage.documents/tree/primary%3APictures/document/primary%3APictures%2FAnime",
                rootUri = "content://com.android.externalstorage.documents/tree/primary%3APictures",
                displayName = "Anime / ilustración\nreferencias"
            ),
            FolderQuickAccessEntry(
                uri = "file:///storage/emulated/0/Download",
                rootUri = "file:///storage/emulated/0",
                displayName = "Descargas ñ"
            )
        )

        assertEquals(entries, decodeFolderQuickAccess(encodeFolderQuickAccess(entries)))
    }

    @Test
    fun toggleAddsToFrontAndSecondToggleRemovesWithoutDuplicates() {
        val first = FolderQuickAccessEntry("file:///storage/a", "file:///storage", "A")
        val second = FolderQuickAccessEntry("file:///storage/b", "file:///storage", "B")

        val added = toggleFolderQuickAccess(listOf(first), second)
        assertEquals(listOf(second, first), added)
        assertEquals(listOf(first), toggleFolderQuickAccess(added, second))
        assertEquals(emptyList<FolderQuickAccessEntry>(), toggleFolderQuickAccess(listOf(first, first), first))
    }

    @Test
    fun removingShortcutKeepsOtherEntriesAndOrder() {
        val a = FolderQuickAccessEntry("file:///storage/a", "file:///storage", "A")
        val b = FolderQuickAccessEntry("file:///storage/b", "file:///storage", "B")
        val c = FolderQuickAccessEntry("file:///storage/c", "file:///storage", "C")

        assertEquals(listOf(a, c), removeFolderQuickAccess(listOf(a, b, c), b.uri))
    }

    @Test
    fun capsStoredShortcutsAndRejectsMalformedData() {
        val entries = (1..(MAX_FOLDER_QUICK_ACCESS_ENTRIES + 1)).map { i ->
            FolderQuickAccessEntry("file:///storage/$i", "file:///storage", "Carpeta $i")
        }
        try {
            encodeFolderQuickAccess(entries)
            throw AssertionError("El límite de accesos rápidos debía aplicarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            decodeFolderQuickAccess("esto no es un acceso rápido")
            throw AssertionError("El formato incorrecto debía rechazarse.")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun rejectsDuplicateUrisInPersistedData() {
        val duplicate = "ARCHIVERO_FOLDER_SHORTCUTS_V1\n" +
            "ZmlsZTovLy9zdG9yYWdlL2E.ZmlsZTovLy9zdG9yYWdl.QQ\n" +
            "ZmlsZTovLy9zdG9yYWdlL2E.ZmlsZTovLy9zdG9yYWdl.QQ\n"
        assertFalse(duplicate.isBlank())
        try {
            decodeFolderQuickAccess(duplicate)
            throw AssertionError("Los URI duplicados debían rechazarse.")
        } catch (_: IOException) {
            // Expected.
        }
    }

    @Test
    fun rejectsUnsupportedUriSchemes() {
        try {
            encodeFolderQuickAccess(
                listOf(FolderQuickAccessEntry("https://example.invalid/folder", "file:///storage", "No local"))
            )
            throw AssertionError("No se deben guardar ubicaciones de red como rutas locales.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun toggleKeepsNewestShortcutsAndEnforcesValidLimit() {
        val existing = (1..MAX_FOLDER_QUICK_ACCESS_ENTRIES).map { index ->
            FolderQuickAccessEntry("file:///storage/$index", "file:///storage", "Carpeta $index")
        }
        val newest = FolderQuickAccessEntry("file:///storage/newest", "file:///storage", "Nueva")
        val updated = toggleFolderQuickAccess(existing, newest)

        assertEquals(MAX_FOLDER_QUICK_ACCESS_ENTRIES, updated.size)
        assertEquals(newest, updated.first())
        assertFalse(updated.any { it.uri == existing.last().uri })

        try {
            toggleFolderQuickAccess(existing, newest, limit = 0)
            throw AssertionError("Un límite cero debía rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsFolderAndRootFromDifferentProviders() {
        try {
            encodeFolderQuickAccess(
                listOf(
                    FolderQuickAccessEntry(
                        "content://provider/document/1",
                        "file:///storage/emulated/0",
                        "Carpeta"
                    )
                )
            )
            throw AssertionError("La raíz y la carpeta no deben usar proveedores distintos.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsSerializedStateAboveTheMemoryBudget() {
        val oversized = FolderQuickAccessEntry(
            uri = "file:///" + "a".repeat(8_000),
            rootUri = "file:///storage",
            displayName = "Carpeta"
        )
        try {
            encodeFolderQuickAccess(List(MAX_FOLDER_QUICK_ACCESS_ENTRIES) { oversized.copy(uri = oversized.uri + it) })
            throw AssertionError("La serialización excesiva debía rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            decodeFolderQuickAccess("x".repeat(MAX_FOLDER_QUICK_ACCESS_BYTES + 1))
            throw AssertionError("La lectura de un estado demasiado grande debía rechazarse.")
        } catch (_: IOException) {
            // Expected.
        }
    }

}
