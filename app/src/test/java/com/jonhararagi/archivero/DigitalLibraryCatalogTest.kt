package com.jonhararagi.archivero

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalLibraryCatalogTest {
    @Test
    fun persistsMetadataAndSearchesAcrossNameTagsNotesAndSource() {
        val directory = Files.createTempDirectory("archivero-library-search").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            catalog.upsert(
                LibraryRecord(
                    uri = "content://demo/anime/42",
                    displayName = "  Referencia - cabello azul  ",
                    category = "Imagen",
                    tags = listOf("Anime", "cabello azul", "anime", ""),
                    notes = "Paleta para personaje principal",
                    source = "https://example.invalid/referencia",
                    updatedAtMillis = 123L
                )
            )

            val restored = DigitalLibraryCatalog(file)
            assertEquals("Referencia - cabello azul", restored.get("content://demo/anime/42")?.displayName)
            assertEquals(listOf("Anime", "cabello azul"), restored.get("content://demo/anime/42")?.tags)
            assertEquals(1, restored.search(query = "PERSONAJE").size)
            assertEquals(1, restored.search(query = "example.invalid").size)
            assertEquals(1, restored.search(category = "imagen", requiredTags = setOf("anime", "cabello azul")).size)
            assertTrue(restored.search(requiredTags = setOf("anime", "paisaje")).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun bulkTaggingMergesCaseInsensitivelyAndPersists() {
        val directory = Files.createTempDirectory("archivero-library-bulk-tags").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val firstUri = "content://bulk/first"
            val secondUri = "content://bulk/second"
            catalog.upsert(LibraryRecord(firstUri, "Primera", "Imagen", tags = listOf("Anime"), updatedAtMillis = 10L))
            catalog.upsert(LibraryRecord(secondUri, "Segunda", "Imagen", tags = listOf("Paisaje"), updatedAtMillis = 20L))

            val changed = catalog.addTagsToRecords(
                listOf(firstUri, secondUri),
                listOf("cabello azul", "anime", "cabello azul")
            )

            assertEquals(2, changed)
            assertEquals(listOf("Anime", "cabello azul"), catalog.get(firstUri)?.tags)
            assertEquals(listOf("Paisaje", "cabello azul", "anime"), catalog.get(secondUri)?.tags)
            val restored = DigitalLibraryCatalog(file)
            assertEquals(listOf("Anime", "cabello azul"), restored.get(firstUri)?.tags)
            assertEquals(listOf("Paisaje", "cabello azul", "anime"), restored.get(secondUri)?.tags)
            assertEquals(0, restored.addTagsToRecords(listOf(firstUri, secondUri), listOf("ANIME", "CABELLO AZUL")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun bulkTaggingWithMissingUriDoesNotPartiallyChangeCatalog() {
        val directory = Files.createTempDirectory("archivero-library-bulk-missing").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val existingUri = "content://bulk/existing"
            catalog.upsert(LibraryRecord(existingUri, "Existente", "Documento"))

            try {
                catalog.addTagsToRecords(listOf(existingUri, "content://bulk/missing"), listOf("revisar"))
                throw AssertionError("El lote con una URI inexistente debía rechazarse.")
            } catch (_: IllegalArgumentException) {
                // Expected. The operation is atomic with respect to catalog metadata.
            }

            assertTrue(catalog.get(existingUri)?.tags.orEmpty().isEmpty())
            assertTrue(DigitalLibraryCatalog(file).get(existingUri)?.tags.orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun bulkTaggingLimitFailureDoesNotWriteEarlierTargets() {
        val directory = Files.createTempDirectory("archivero-library-bulk-limit").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val firstUri = "content://bulk/would-change"
            val fullUri = "content://bulk/full"
            catalog.upsert(LibraryRecord(firstUri, "Primera", "Imagen"))
            catalog.upsert(
                LibraryRecord(
                    fullUri,
                    "Completa",
                    "Imagen",
                    tags = (1..100).map { "tag-$it" }
                )
            )

            try {
                catalog.addTagsToRecords(listOf(firstUri, fullUri), listOf("tag-nueva"))
                throw AssertionError("El límite de 100 etiquetas debía rechazar todo el lote.")
            } catch (_: IllegalArgumentException) {
                // Expected. No partially updated snapshot is written.
            }

            assertTrue(catalog.get(firstUri)?.tags.orEmpty().isEmpty())
            assertEquals(100, catalog.get(fullUri)?.tags?.size)
            val restored = DigitalLibraryCatalog(file)
            assertTrue(restored.get(firstUri)?.tags.orEmpty().isEmpty())
            assertEquals(100, restored.get(fullUri)?.tags?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun removeMetadataDoesNotDeleteTheReferencedPhysicalFile() {
        val directory = Files.createTempDirectory("archivero-library-remove").toFile()
        try {
            val physicalFile = File(directory, "important.txt")
            physicalFile.writeText("keep this content")
            val catalog = DigitalLibraryCatalog(File(directory, "catalog.properties"))
            val uri = physicalFile.toURI().toString()
            catalog.upsert(LibraryRecord(uri, physicalFile.name, "TXT", notes = "Test"))
            
            assertTrue(catalog.removeMetadata(uri))
            assertFalse(catalog.removeMetadata(uri))
            assertTrue("Quitar la ficha no debe borrar el archivo físico.", physicalFile.exists())
            assertEquals("keep this content", physicalFile.readText())
            assertNull(catalog.get(uri))
            assertTrue(DigitalLibraryCatalog(File(directory, "catalog.properties")).all().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun snapshotRoundTripWorksAndCollisionsAreRejectedWithoutMutation() {
        val directory = Files.createTempDirectory("archivero-library-backup").toFile()
        try {
            val active = DigitalLibraryCatalog(File(directory, "active.properties"))
            val record = LibraryRecord(
                "content://demo/item/1", "Guía de color", "Documento",
                tags = listOf("colores", "anime"), notes = "Notas",
                source = "https://example.invalid", updatedAtMillis = 456L
            )
            active.upsert(record)
            val backup = File(directory, "export.properties")
            active.exportSnapshot(backup)

            val restored = DigitalLibraryCatalog(File(directory, "restored.properties"))
            assertEquals(1, restored.importSnapshot(backup))
            assertEquals(record, restored.get(record.uri))
            assertEquals(1, restored.all().size)

            val before = restored.all()
            try {
                restored.importSnapshot(backup)
                throw AssertionError("La importación con colisión debía rechazarse.")
            } catch (_: IOException) {
                // Expected: don't overwrite metadata silently.
            }
            assertEquals(before, restored.all())
            assertEquals(1, restored.importSnapshot(backup, replaceExisting = true))
            assertEquals(before, restored.all())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptImportFailsWithoutReplacingTheExistingCatalog() {
        val directory = Files.createTempDirectory("archivero-library-corrupt").toFile()
        try {
            val catalogFile = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(catalogFile)
            val keep = LibraryRecord("content://demo/keep", "Mantener", "Documento")
            catalog.upsert(keep)

            val corrupt = File(directory, "corrupt.properties")
            corrupt.writeText("schema.version=1\nrecords.count=1\nrecord.0.uri=content://incomplete\n")
            try {
                catalog.importSnapshot(corrupt)
                throw AssertionError("La importación incompleta debía rechazarse.")
            } catch (_: IOException) {
                // Expected: validation must finish before any active-catalog write.
            }

            assertEquals(listOf(keep), catalog.all())
            assertEquals(listOf(keep), DigitalLibraryCatalog(catalogFile).all())
            assertNotNull(catalog.get(keep.uri))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedSnapshotIsRejectedWithoutReplacingTheActiveCatalog() {
        val directory = Files.createTempDirectory("archivero-library-size-limit").toFile()
        try {
            val activeFile = File(directory, "active.properties")
            val catalog = DigitalLibraryCatalog(activeFile)
            val keep = LibraryRecord("content://demo/keep", "Conservar", "Documento")
            catalog.upsert(keep)

            val oversized = File(directory, "oversized.properties")
            RandomAccessFile(oversized, "rw").use { it.setLength(MAX_LIBRARY_SNAPSHOT_BYTES + 1L) }
            try {
                catalog.importSnapshot(oversized)
                throw AssertionError("La instantánea excesiva debía rechazarse.")
            } catch (_: IOException) {
                // Expected: size validation happens before Properties parses the file.
            }
            assertEquals(listOf(keep), catalog.all())
            assertEquals(listOf(keep), DigitalLibraryCatalog(activeFile).all())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacyVersionOneSnapshotMigratesWithoutLosingExistingMetadata() {
        val directory = Files.createTempDirectory("archivero-library-migrate-v1").toFile()
        try {
            val legacyFile = File(directory, "legacy.properties")
            val legacyUri = "content://demo/legacy/1"
            val properties = Properties().apply {
                setProperty("schema.version", "1")
                setProperty("records.count", "1")
                setProperty("record.0.uri", legacyUri)
                setProperty("record.0.displayName", "Referencia antigua")
                setProperty("record.0.category", "Imagen")
                setProperty("record.0.notes", "Conservar esta nota")
                setProperty("record.0.source", "https://example.invalid/original")
                setProperty("record.0.updatedAtMillis", "777")
                setProperty("record.0.tag.count", "1")
                setProperty("record.0.tag.0", "anime")
            }
            legacyFile.outputStream().use { properties.store(it, "legacy v1 fixture") }

            val migrated = DigitalLibraryCatalog(legacyFile)
            val original = migrated.get(legacyUri)
            assertNotNull(original)
            assertEquals("Referencia antigua", original?.displayName)
            assertEquals("Conservar esta nota", original?.notes)
            assertEquals(listOf("anime"), original?.tags)
            assertEquals("", original?.project)
            assertEquals("Sin clasificar", original?.resourceKind)
            assertTrue(original?.relatedUris.orEmpty().isEmpty())

            val relatedUri = "content://demo/related/2"
            migrated.upsert(
                original!!.copy(
                    project = "KapiArt",
                    resourceKind = "Original",
                    relatedUris = listOf(relatedUri, legacyUri, relatedUri)
                )
            )

            val savedProperties = Properties()
            legacyFile.inputStream().use(savedProperties::load)
            assertEquals("2", savedProperties.getProperty("schema.version"))
            assertEquals("KapiArt", savedProperties.getProperty("record.0.project"))
            assertEquals("Original", savedProperties.getProperty("record.0.resourceKind"))
            assertEquals("1", savedProperties.getProperty("record.0.related.count"))
            assertEquals(relatedUri, savedProperties.getProperty("record.0.related.0.uri"))

            val reopened = DigitalLibraryCatalog(legacyFile)
            assertEquals("KapiArt", reopened.get(legacyUri)?.project)
            assertEquals("Original", reopened.get(legacyUri)?.resourceKind)
            assertEquals(listOf(relatedUri), reopened.get(legacyUri)?.relatedUris)
            assertEquals(1, reopened.search(query = "kapiart").size)
            assertEquals(1, reopened.search(query = "original", category = "Imagen").size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun combinedLibraryFiltersAndSortAreStableAndRespectTheLimit() {
        val records = listOf(
            LibraryRecord(
                uri = "content://library/1", displayName = "Zorro", category = "Imagen",
                tags = listOf("azul"), updatedAtMillis = 100L, project = "Anime",
                resourceKind = "Original"
            ),
            LibraryRecord(
                uri = "content://library/2", displayName = "Akari", category = "Documento",
                tags = listOf("guía"), updatedAtMillis = 300L, project = "Anime",
                resourceKind = "Referencia"
            ),
            LibraryRecord(
                uri = "content://library/3", displayName = "Mika", category = "Imagen",
                tags = listOf("rojo"), updatedAtMillis = 200L, project = "Manga",
                resourceKind = "Editado"
            )
        )
        assertEquals(listOf("Mika", "Zorro"), filterLibraryRecords(records, category = "imagen").map { it.displayName })
        assertEquals(listOf("Zorro"), filterLibraryRecords(records, query = "ANIME", category = "Imagen", resourceKind = "original").map { it.displayName })
        assertEquals(listOf("Akari", "Mika", "Zorro"), filterLibraryRecords(records, sortMode = "Recientes").map { it.displayName })
        assertEquals(listOf("Akari", "Zorro", "Mika"), filterLibraryRecords(records, sortMode = "Proyecto").map { it.displayName })
        assertEquals(listOf("Mika"), filterLibraryRecords(records, project = "manga").map { it.displayName })
        assertEquals(
            listOf("Zorro"),
            filterLibraryRecords(
                records,
                query = "azul",
                category = "imagen",
                resourceKind = "original",
                project = "ANIME"
            ).map { it.displayName }
        )
        assertEquals(1, filterLibraryRecords(records, limit = 1).size)
        assertEquals(
            listOf("Mika", "Zorro", "Akari"),
            filterLibraryRecords(
                records,
                favorites = setOf("content://library/1", "content://library/3")
            ).map { it.displayName }
        )
        assertEquals(
            listOf("Mika", "Zorro"),
            filterLibraryRecords(
                records,
                favorites = setOf("content://library/1", "content://library/3"),
                onlyFavorites = true
            ).map { it.displayName }
        )
        assertTrue(
            filterLibraryRecords(records, favorites = emptySet(), onlyFavorites = true).isEmpty()
        )
        assertTrue(filterLibraryRecords(records, query = "no-existe").isEmpty())
    }

    @Test
    fun boundedFilteringReturnsTheSameFirstPageAsFullOrdering() {
        val records = (0 until 5_000).reversed().map { index ->
            LibraryRecord(
                uri = "content://large/$index",
                displayName = "Ficha %05d".format(index),
                category = if (index % 2 == 0) "Imagen" else "Documento",
                tags = listOf(if (index % 3 == 0) "anime" else "general"),
                updatedAtMillis = index.toLong(),
                project = if (index % 4 == 0) "KapiArt" else "Anime",
                resourceKind = if (index % 2 == 0) "Original" else "Referencia"
            )
        }
        val favorites = records.filter { it.uri.hashCode() % 11 == 0 }.mapTo(HashSet()) { it.uri }

        listOf("Nombre", "Recientes", "Proyecto").forEach { sortMode ->
            val expected = when (sortMode) {
                "Recientes" -> records.sortedWith(
                    compareByDescending<LibraryRecord> { it.uri in favorites }
                        .thenByDescending { it.updatedAtMillis }
                        .thenBy { it.displayName.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.uri }
                )
                "Proyecto" -> records.sortedWith(
                    compareByDescending<LibraryRecord> { it.uri in favorites }
                        .thenBy { it.project.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.displayName.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.uri }
                )
                else -> records.sortedWith(
                    compareByDescending<LibraryRecord> { it.uri in favorites }
                        .thenBy { it.displayName.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.uri }
                )
            }.take(37)

            assertEquals(
                expected,
                filterLibraryRecords(
                    records = records,
                    query = "ficha",
                    sortMode = sortMode,
                    favorites = favorites,
                    limit = 37
                )
            )
        }

        assertEquals(
            records.filter { it.project == "KapiArt" }
                .sortedBy { it.displayName.lowercase(java.util.Locale.ROOT) }
                .take(19),
            filterLibraryRecords(records, project = "kapiart", limit = 19)
        )
    }

    @Test
    fun rejectsInvalidRecordsAndResultLimits() {
        val directory = Files.createTempDirectory("archivero-library-validation").toFile()
        try {
            val catalog = DigitalLibraryCatalog(File(directory, "catalog.properties"))
            try {
                catalog.upsert(LibraryRecord("content://demo/empty-name", " ", "Imagen"))
                throw AssertionError("El nombre vacío debía rechazarse.")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
            try {
                catalog.search(limit = 0)
                throw AssertionError("El límite cero debía rechazarse.")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
            assertTrue(catalog.all().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
