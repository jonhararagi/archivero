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
            // Terms can be found across different metadata fields without being adjacent.
            assertEquals(1, restored.search(query = "cabello PRINCIPAL").size)
            assertTrue(restored.search(query = "cabello inexistente").isEmpty())
            assertEquals(1, restored.search(query = "example.invalid").size)
            assertEquals(1, restored.search(category = "imagen", requiredTags = setOf("anime", "cabello azul")).size)
            assertTrue(restored.search(requiredTags = setOf("anime", "paisaje")).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun catalogSearchBoundedResultsMatchFullOrderingWithFavoritesAndFilters() {
        val directory = Files.createTempDirectory("archivero-library-bounded-search").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val records = (0 until 180).map { index ->
                LibraryRecord(
                    uri = "content://large/catalog-search/$index",
                    displayName = "Ficha %03d".format(index),
                    category = if (index % 2 == 0) "Imagen" else "Documento",
                    tags = listOf(if (index % 3 == 0) "Anime" else "General"),
                    notes = if (index % 5 == 0) "Referencia anime" else "Archivo",
                    updatedAtMillis = index.toLong()
                )
            }
            records.forEach { catalog.upsert(it) }

            val favorites = records
                .filter { it.uri.substringAfterLast("/").toInt() % 7 == 0 }
                .mapTo(HashSet()) { it.uri }
            val limit = 17
            val order = compareByDescending<LibraryRecord> { it.uri in favorites }
                .thenBy { it.displayName.lowercase(java.util.Locale.ROOT) }
                .thenBy { it.uri }
            val expected = records
                .filter { item ->
                    librarySearchMatches(item.normalizedSearchText, librarySearchTerms("ficha")) &&
                        normalizeLibrarySearchText(item.category) == "imagen" &&
                        item.tags.any { normalizeLibrarySearchText(it) == "anime" }
                }
                .sortedWith(order)
                .take(limit)

            val actual = catalog.search(
                query = "ficha",
                category = "imagen",
                requiredTags = setOf("anime"),
                favorites = favorites,
                limit = limit
            )

            assertEquals(limit, actual.size)
            assertEquals(expected, actual)
            assertTrue(
                "Favorites must retain their priority in the bounded window.",
                actual.takeWhile { it.uri in favorites }.all { it.uri in favorites }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recordsSnapshotPreservesStorageOrderAndAllPreservesAlphabeticOrderAfterReopen() {
        val directory = Files.createTempDirectory("archivero-library-snapshot-order").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val zorro = LibraryRecord(
                uri = "content://snapshot-order/z",
                displayName = "Zorro",
                category = "Imagen",
                updatedAtMillis = 1L
            )
            val anime = LibraryRecord(
                uri = "content://snapshot-order/a",
                displayName = "Anime",
                category = "Imagen",
                updatedAtMillis = 2L
            )
            catalog.upsert(zorro)
            catalog.upsert(anime)

            assertEquals(listOf(zorro.uri, anime.uri), catalog.recordsSnapshot().map { it.uri })
            assertEquals(listOf(anime.uri, zorro.uri), catalog.all().map { it.uri })

            val reopened = DigitalLibraryCatalog(file)
            assertEquals(listOf(zorro.uri, anime.uri), reopened.recordsSnapshot().map { it.uri })
            assertEquals(listOf(anime.uri, zorro.uri), reopened.all().map { it.uri })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun normalizedSearchTextIsReusedAndCopiesReflectUpdatedMetadata() {
        val original = LibraryRecord(
            uri = "content://search-cache/item",
            displayName = "Referencia azul",
            category = "Imagen",
            tags = listOf("Anime"),
            notes = "Cabello y ojos"
        )
        val firstRead = original.normalizedSearchText
        assertTrue("The immutable record should reuse its cached search text.", firstRead === original.normalizedSearchText)
        assertTrue(firstRead.contains("cabello"))

        val edited = original.copy(
            displayName = "Referencia roja",
            tags = listOf("Manga"),
            notes = "Paisaje tranquilo"
        )
        assertTrue(edited.normalizedSearchText.contains("referencia roja"))
        assertTrue(edited.normalizedSearchText.contains("manga"))
        assertTrue(edited.normalizedSearchText.contains("paisaje"))
        assertFalse(edited.normalizedSearchText.contains("cabello"))
        assertTrue(original.normalizedSearchText.contains("referencia azul"))
        assertTrue(original.normalizedSearchText.contains("anime"))
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
    fun bulkProjectAssignmentPersistsAndPreservesOtherMetadata() {
        val directory = Files.createTempDirectory("archivero-library-bulk-project").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val firstUri = "content://bulk/project-first"
            val secondUri = "content://bulk/project-second"
            catalog.upsert(
                LibraryRecord(
                    firstUri, "Primera", "Imagen",
                    tags = listOf("anime"), notes = "mantener",
                    source = "https://example.invalid/ref", project = "Antiguo",
                    resourceKind = "Original", relatedUris = listOf("content://related/1"),
                    updatedAtMillis = 10L
                )
            )
            catalog.upsert(
                LibraryRecord(secondUri, "Segunda", "Documento", project = "Sin proyecto", updatedAtMillis = 20L)
            )

            val changed = catalog.assignProjectToRecords(listOf(firstUri, secondUri), "  KapiArt  ")

            assertEquals(2, changed)
            assertEquals("KapiArt", catalog.get(firstUri)?.project)
            assertEquals("KapiArt", catalog.get(secondUri)?.project)
            assertEquals(listOf("anime"), catalog.get(firstUri)?.tags)
            assertEquals("mantener", catalog.get(firstUri)?.notes)
            assertEquals("https://example.invalid/ref", catalog.get(firstUri)?.source)
            assertEquals("Original", catalog.get(firstUri)?.resourceKind)
            assertEquals(listOf("content://related/1"), catalog.get(firstUri)?.relatedUris)

            val reopened = DigitalLibraryCatalog(file)
            assertEquals("KapiArt", reopened.get(firstUri)?.project)
            assertEquals("KapiArt", reopened.get(secondUri)?.project)
            assertEquals(0, reopened.assignProjectToRecords(listOf(firstUri, secondUri), "KapiArt"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun bulkProjectAssignmentWithMissingUriDoesNotPartiallyChangeCatalog() {
        val directory = Files.createTempDirectory("archivero-library-bulk-project-missing").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val existingUri = "content://bulk/project-existing"
            catalog.upsert(LibraryRecord(existingUri, "Existente", "Documento", project = "Original"))

            try {
                catalog.assignProjectToRecords(
                    listOf(existingUri, "content://bulk/project-missing"),
                    "Nueva colección"
                )
                throw AssertionError("El lote con una URI inexistente debía rechazarse.")
            } catch (_: IllegalArgumentException) {
                // Expected: validation must finish before catalog writes.
            }

            assertEquals("Original", catalog.get(existingUri)?.project)
            assertEquals("Original", DigitalLibraryCatalog(file).get(existingUri)?.project)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun bulkProjectAssignmentRejectsInvalidNameWithoutChangingRecords() {
        val directory = Files.createTempDirectory("archivero-library-bulk-project-invalid").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val uri = "content://bulk/project-invalid"
            catalog.upsert(LibraryRecord(uri, "Existente", "Imagen", project = "Conservar"))

            val invalidNames = listOf("", " ", "nombre\u0001inválido", "x".repeat(257))
            invalidNames.forEach { invalid ->
                try {
                    catalog.assignProjectToRecords(listOf(uri), invalid)
                    throw AssertionError("Un nombre de proyecto inválido debía rechazarse.")
                } catch (_: IllegalArgumentException) {
                    // Expected.
                }
                assertEquals("Conservar", catalog.get(uri)?.project)
            }
            assertEquals("Conservar", DigitalLibraryCatalog(file).get(uri)?.project)
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
    fun schemaTwoSnapshotMigratesToVersionThreeAndPreservesMetadata() {
        val directory = Files.createTempDirectory("archivero-library-migrate-v2").toFile()
        try {
            val file = File(directory, "schema-v2.properties")
            val uri = "content://demo/schema-v2/record"
            val relatedUri = "content://demo/schema-v2/related"
            val properties = Properties().apply {
                setProperty("schema.version", "2")
                setProperty("records.count", "1")
                setProperty("record.0.uri", uri)
                setProperty("record.0.displayName", "Ficha esquema dos")
                setProperty("record.0.category", "Imagen")
                setProperty("record.0.notes", "No perder esta nota")
                setProperty("record.0.source", "https://example.invalid/v2")
                setProperty("record.0.updatedAtMillis", "170")
                setProperty("record.0.project", "AnimeArt")
                setProperty("record.0.resourceKind", "Referencia")
                setProperty("record.0.related.count", "1")
                setProperty("record.0.related.0.uri", relatedUri)
                setProperty("record.0.tag.count", "1")
                setProperty("record.0.tag.0", "anime")
            }
            file.outputStream().use { properties.store(it, "schema v2 compatibility fixture") }

            val catalog = DigitalLibraryCatalog(file)
            val record = catalog.get(uri)
            assertEquals("Ficha esquema dos", record?.displayName)
            assertEquals("No perder esta nota", record?.notes)
            assertEquals("AnimeArt", record?.project)
            assertEquals("Referencia", record?.resourceKind)
            assertEquals(listOf(relatedUri), record?.relatedUris)
            assertEquals(listOf("anime"), record?.tags)
            assertTrue(catalog.trashed().isEmpty())

            assertTrue(catalog.trashMetadata(uri))
            val saved = Properties()
            file.inputStream().use(saved::load)
            assertEquals("3", saved.getProperty("schema.version"))
            assertEquals("1", saved.getProperty("trash.count"))

            val reopened = DigitalLibraryCatalog(file)
            assertEquals("AnimeArt", reopened.trashed().single().record.project)
            assertEquals(listOf(relatedUri), reopened.trashed().single().record.relatedUris)
            assertTrue(reopened.restoreMetadata(uri))
            assertEquals("Ficha esquema dos", reopened.get(uri)?.displayName)
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
            assertEquals("3", savedProperties.getProperty("schema.version"))
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
        // Multi-term queries use AND semantics across name, tags, project and other metadata.
        assertEquals(listOf("Zorro"), filterLibraryRecords(records, query = "zorro azul").map { it.displayName })
        assertTrue(filterLibraryRecords(records, query = "zorro inexistente").isEmpty())
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
    fun libraryResultPagingAdvancesInFixedStepsAndStopsAtCap() {
        assertEquals(1_000, nextLibraryResultLimit(500))
        assertEquals(9_500, nextLibraryResultLimit(9_000))
        assertEquals(MAX_LIBRARY_RESULT_LIMIT, nextLibraryResultLimit(9_500))
        assertEquals(MAX_LIBRARY_RESULT_LIMIT, nextLibraryResultLimit(MAX_LIBRARY_RESULT_LIMIT))
        try {
            nextLibraryResultLimit(0)
            throw AssertionError("El límite cero debía rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
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
    fun trashedRecordSurvivesReopenAndCanBeRestoredWithoutChangingPhysicalFile() {
        val directory = Files.createTempDirectory("archivero-library-trash-restore").toFile()
        try {
            val physicalFile = File(directory, "reference.png")
            physicalFile.writeText("do not alter this resource")
            val uri = physicalFile.toURI().toString()
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val record = LibraryRecord(
                uri = uri,
                displayName = "Referencia recuperable",
                category = "Imagen",
                tags = listOf("anime"),
                notes = "Dato que debe sobrevivir",
                project = "KapiArt"
            )
            catalog.upsert(record)

            assertTrue(catalog.trashMetadata(uri))
            assertTrue(catalog.all().isEmpty())
            assertEquals(1, catalog.trashed().size)
            assertEquals(record.displayName, catalog.trashed().single().record.displayName)
            assertTrue(catalog.trashed().single().deletedAtMillis > 0L)
            assertTrue(physicalFile.exists())
            assertEquals("do not alter this resource", physicalFile.readText())

            val reopened = DigitalLibraryCatalog(file)
            assertTrue(reopened.all().isEmpty())
            assertEquals(record.notes, reopened.trashed().single().record.notes)
            assertTrue(reopened.restoreMetadata(uri))
            assertEquals(record.displayName, reopened.get(uri)?.displayName)
            assertEquals(listOf("anime"), reopened.get(uri)?.tags)
            assertTrue(reopened.trashed().isEmpty())
            assertTrue(physicalFile.exists())
            assertEquals("do not alter this resource", physicalFile.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun exportAndImportSnapshotPreserveBothActiveAndTrashedMetadata() {
        val directory = Files.createTempDirectory("archivero-library-trash-snapshot").toFile()
        try {
            val sourceFile = File(directory, "source.properties")
            val source = DigitalLibraryCatalog(sourceFile)
            val active = LibraryRecord("content://snapshot/active", "Activa", "Imagen", tags = listOf("uno"))
            val trashed = LibraryRecord("content://snapshot/trash", "En papelera", "Documento", tags = listOf("dos"))
            source.upsert(active)
            source.upsert(trashed)
            source.trashMetadata(trashed.uri)

            val export = File(directory, "portable.properties")
            source.exportSnapshot(export)
            val targetFile = File(directory, "target.properties")
            val target = DigitalLibraryCatalog(targetFile)
            val local = LibraryRecord("content://snapshot/local", "Local", "Otros")
            target.upsert(local)

            assertEquals(2, target.importSnapshot(export))
            assertEquals(setOf(active.uri, local.uri), target.all().map { it.uri }.toSet())
            assertEquals(trashed.uri, target.trashed().single().record.uri)

            val reopened = DigitalLibraryCatalog(targetFile)
            assertEquals(setOf(active.uri, local.uri), reopened.all().map { it.uri }.toSet())
            assertEquals(trashed.displayName, reopened.trashed().single().record.displayName)
            assertTrue(reopened.restoreMetadata(trashed.uri))
            assertEquals(trashed.displayName, reopened.get(trashed.uri)?.displayName)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun upsertOfTrashedUriReturnsItToActiveLibraryAndReplacesTrashEntry() {
        val directory = Files.createTempDirectory("archivero-library-trash-upsert").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val original = LibraryRecord("content://trash/upsert", "Original", "Imagen")
            catalog.upsert(original)
            assertTrue(catalog.trashMetadata(original.uri))

            catalog.upsert(original.copy(displayName = "Añadido otra vez"))
            assertEquals("Añadido otra vez", catalog.get(original.uri)?.displayName)
            assertTrue(catalog.trashed().isEmpty())
            assertEquals("Añadido otra vez", DigitalLibraryCatalog(file).get(original.uri)?.displayName)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun purgingTrashRemovesOnlyMetadataAndKeepsPhysicalFile() {
        val directory = Files.createTempDirectory("archivero-library-trash-purge").toFile()
        try {
            val physicalFile = File(directory, "keep.txt")
            physicalFile.writeText("physical content remains")
            val uri = physicalFile.toURI().toString()
            val catalog = DigitalLibraryCatalog(File(directory, "catalog.properties"))
            catalog.upsert(LibraryRecord(uri, "Archivo a purgar", "Documento"))
            assertTrue(catalog.trashMetadata(uri))

            assertTrue(catalog.purgeTrashedMetadata(uri))
            assertTrue(catalog.trashed().isEmpty())
            assertTrue(catalog.all().isEmpty())
            assertFalse(catalog.purgeTrashedMetadata(uri))
            assertTrue(physicalFile.exists())
            assertEquals("physical content remains", physicalFile.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun searchIgnoresAccentsButKeepsSpanishEnyeDistinctAcrossFieldsAndFilters() {
        val directory = Files.createTempDirectory("archivero-library-accent-search").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val catalog = DigitalLibraryCatalog(file)
            val record = LibraryRecord(
                uri = "content://demo/accent-search/1",
                displayName = "Canción azul",
                category = "Música",
                tags = listOf("referéncia"),
                notes = "Personaje niño",
                project = "Colección manga",
                resourceKind = "Edición"
            )
            catalog.upsert(record)

            assertEquals(
                listOf(record.uri),
                catalog.search(
                    query = "cancion personaje",
                    category = "musica",
                    requiredTags = setOf("referencia")
                ).map { it.uri }
            )
            assertEquals(
                listOf(record.uri),
                filterLibraryRecords(
                    records = catalog.all(),
                    query = "cancion coleccion",
                    category = "musica",
                    resourceKind = "edicion",
                    project = "coleccion manga"
                ).map { it.uri }
            )

            // Spanish ñ is a distinct letter: a search for "nino" must not match "niño".
            assertTrue(catalog.search(query = "nino").isEmpty())

            // Search normalization is derived only; it does not rewrite saved metadata.
            val reopened = DigitalLibraryCatalog(file).get(record.uri)
            assertEquals("Música", reopened?.category)
            assertEquals(listOf("referéncia"), reopened?.tags)
            assertEquals("Personaje niño", reopened?.notes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedSnapshotWriteLeavesMemoryAndExistingDiskSnapshotUnchanged() {
        val directory = Files.createTempDirectory("archivero-library-write-failure").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val uri = "content://demo/write-failure/keep"
            val original = LibraryRecord(
                uri = uri,
                displayName = "Original conservado",
                category = "Documento",
                notes = "La instantánea válida debe sobrevivir",
                updatedAtMillis = 123L
            )
            DigitalLibraryCatalog(file).upsert(original)

            val failingCatalog = DigitalLibraryCatalog(file) { _, _, _ ->
                throw IOException("Fallo de almacenamiento simulado para probar rollback")
            }

            try {
                failingCatalog.upsert(original.copy(displayName = "Cambio que no debe guardarse"))
                throw AssertionError("El guardado fallido debía propagar IOException.")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("Fallo de almacenamiento simulado"))
            }

            assertEquals("La instancia activa debe conservar la ficha anterior.", original, failingCatalog.get(uri))
            assertTrue(failingCatalog.trashed().isEmpty())

            try {
                failingCatalog.trashMetadata(uri)
                throw AssertionError("Mover a papelera con un guardado fallido debía propagarse.")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("Fallo de almacenamiento simulado"))
            }

            assertEquals(listOf(original), failingCatalog.all())
            assertTrue("La operación fallida no debe vaciar la papelera ni alterar la ficha.", failingCatalog.trashed().isEmpty())

            val reopened = DigitalLibraryCatalog(file)
            assertEquals("El disco debe seguir conteniendo la última instantánea válida.", original, reopened.get(uri))
            assertTrue(reopened.trashed().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
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

    @Test
    fun importPreviewCountsActiveTrashAndCollisionsWithoutChangingCatalog() {
        val directory = Files.createTempDirectory("archivero-library-import-preview").toFile()
        try {
            val targetFile = File(directory, "target.properties")
            val target = DigitalLibraryCatalog(targetFile)
            val activeCollision = LibraryRecord(
                "content://preview/active", "Nombre existente", "Imagen", updatedAtMillis = 10L
            )
            val trashCollision = LibraryRecord(
                "content://preview/trash", "Referencia guardada en papelera", "Documento", updatedAtMillis = 11L
            )
            target.upsert(activeCollision)
            target.upsert(trashCollision)
            target.trashMetadata(trashCollision.uri)

            val source = DigitalLibraryCatalog(File(directory, "source.properties"))
            source.upsert(activeCollision.copy(displayName = "Nombre entrante", updatedAtMillis = 20L))
            source.upsert(LibraryRecord("content://preview/new", "Ficha nueva", "Imagen", updatedAtMillis = 21L))
            source.upsert(trashCollision.copy(displayName = "Referencia importada", updatedAtMillis = 22L))
            source.trashMetadata(trashCollision.uri)
            val export = File(directory, "portable.properties")
            source.exportSnapshot(export)

            val preview = target.previewSnapshotImport(export)

            assertEquals(2, preview.activeRecordCount)
            assertEquals(1, preview.trashedRecordCount)
            assertEquals(2, preview.collisionCount)
            assertEquals(
                setOf(activeCollision.uri, trashCollision.uri),
                preview.collisionSamples.map { it.uri }.toSet()
            )
            assertEquals(
                "Nombre entrante",
                preview.collisionSamples.single { it.uri == activeCollision.uri }.incomingDisplayName
            )
            assertEquals(
                "Referencia guardada en papelera",
                preview.collisionSamples.single { it.uri == trashCollision.uri }.existingDisplayName
            )
            assertEquals(activeCollision, target.get(activeCollision.uri))
            assertEquals(trashCollision.displayName, target.trashed().single().record.displayName)
            assertTrue(target.get("content://preview/new") == null)
            assertEquals(1, DigitalLibraryCatalog(targetFile).all().size)

            assertEquals(3, target.importSnapshot(export, replaceExisting = true, expectedRevision = preview.catalogRevision))
            assertEquals("Nombre entrante", target.get(activeCollision.uri)?.displayName)
            assertEquals("Ficha nueva", target.get("content://preview/new")?.displayName)
            assertEquals("Referencia importada", target.trashed().single().record.displayName)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun importPreviewRevisionRejectsChangesMadeBeforeConfirmation() {
        val directory = Files.createTempDirectory("archivero-library-import-stale-preview").toFile()
        try {
            val targetFile = File(directory, "target.properties")
            val target = DigitalLibraryCatalog(targetFile)
            val keep = LibraryRecord("content://preview/stale/keep", "Conservar", "Documento")
            target.upsert(keep)

            val source = DigitalLibraryCatalog(File(directory, "source.properties"))
            val incoming = LibraryRecord("content://preview/stale/incoming", "Entrada", "Imagen")
            source.upsert(incoming)
            val export = File(directory, "portable.properties")
            source.exportSnapshot(export)
            val preview = target.previewSnapshotImport(export)

            val concurrent = LibraryRecord("content://preview/stale/concurrent", "Ficha nueva local", "Imagen")
            target.upsert(concurrent)
            val beforeConfirmation = target.all()

            try {
                target.importSnapshot(export, replaceExisting = true, expectedRevision = preview.catalogRevision)
                throw AssertionError("La confirmación con una vista previa obsoleta debía rechazarse.")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("cambió desde la vista previa"))
            }

            assertEquals(beforeConfirmation, target.all())
            assertEquals(keep, target.get(keep.uri))
            assertEquals(concurrent, target.get(concurrent.uri))
            assertEquals(null, target.get(incoming.uri))
            assertEquals(beforeConfirmation, DigitalLibraryCatalog(targetFile).all())
        } finally {
            directory.deleteRecursively()
        }
    }

}
