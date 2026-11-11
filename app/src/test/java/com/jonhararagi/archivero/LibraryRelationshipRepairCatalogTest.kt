package com.jonhararagi.archivero

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LibraryRelationshipRepairCatalogTest {
    private fun record(uri: String, name: String, related: List<String> = emptyList()) =
        LibraryRecord(
            uri = uri,
            displayName = name,
            category = "Referencia",
            notes = "Conservar estas notas",
            relatedUris = related
        )

    @Test
    fun appliesRepairOnceAndPreservesReferencesToTrashedRecords() {
        val directory = Files.createTempDirectory("archivero-relationship-repair").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val a = "content://library/a"
            val b = "content://library/b"
            val trashed = "content://library/trashed"
            val missing = "content://library/missing"
            val catalog = DigitalLibraryCatalog(file)
            catalog.upsert(record(a, "A", listOf(b, trashed, missing)))
            catalog.upsert(record(b, "B"))
            catalog.upsert(record(trashed, "Trashed target"))
            catalog.trashMetadata(trashed)

            val trashedUris = catalog.trashed().mapTo(LinkedHashSet()) { it.record.uri }
            val plan = planLibraryRelationshipRepair(catalog.recordsSnapshot(), trashedUris)
            val summary = catalog.applyRelationshipRepair(plan)

            assertEquals(1, summary.changedRecordCount)
            assertEquals(1, summary.removedRelationCount)
            assertEquals(listOf(b, trashed), catalog.get(a)?.relatedUris)
            assertEquals("Conservar estas notas", catalog.get(a)?.notes)

            val reopened = DigitalLibraryCatalog(file)
            assertEquals(listOf(b, trashed), reopened.get(a)?.relatedUris)
            assertEquals(setOf(trashed), reopened.trashed().mapTo(LinkedHashSet()) { it.record.uri })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsPreviewWhenCatalogGainsPreviouslyMissingTarget() {
        val directory = Files.createTempDirectory("archivero-relationship-stale").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val a = "content://library/a"
            val missing = "content://library/missing"
            val catalog = DigitalLibraryCatalog(file)
            catalog.upsert(record(a, "A", listOf(missing)))
            val preview = planLibraryRelationshipRepair(catalog.recordsSnapshot())
            catalog.upsert(record(missing, "Newly added target"))

            try {
                catalog.applyRelationshipRepair(preview)
                fail("El plan anterior debía rechazarse al cambiar la Biblioteca.")
            } catch (_: IOException) {
                // Expected.
            }

            assertEquals(listOf(missing), catalog.get(a)?.relatedUris)
            assertEquals(listOf(missing), DigitalLibraryCatalog(file).get(a)?.relatedUris)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedSnapshotWriteLeavesMemoryAndDiskUnchanged() {
        val directory = Files.createTempDirectory("archivero-relationship-rollback").toFile()
        try {
            val file = File(directory, "catalog.properties")
            val uri = "content://library/a"
            val original = DigitalLibraryCatalog(file)
            original.upsert(record(uri, "A", listOf("content://library/missing")))

            val failing = DigitalLibraryCatalog(file) { _, _, _ ->
                throw IOException("simulated write failure")
            }
            val preview = planLibraryRelationshipRepair(failing.recordsSnapshot())
            try {
                failing.applyRelationshipRepair(preview)
                fail("Se esperaba un error de escritura.")
            } catch (_: IOException) {
                // Expected.
            }

            assertEquals(listOf("content://library/missing"), failing.get(uri)?.relatedUris)
            assertEquals(
                listOf("content://library/missing"),
                DigitalLibraryCatalog(file).get(uri)?.relatedUris
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
