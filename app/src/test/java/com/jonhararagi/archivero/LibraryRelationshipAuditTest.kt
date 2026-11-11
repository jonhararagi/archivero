package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRelationshipAuditTest {
    @Test
    fun distinguishesMissingTargetsFromRecordsWithoutIncomingLinks() {
        val records = listOf(
            LibraryRecord(
                uri = "content://library/a",
                displayName = "Referencia A",
                category = "Imagen",
                relatedUris = listOf("content://library/b", "content://missing/one")
            ),
            LibraryRecord(
                uri = "content://library/b",
                displayName = "Personaje B",
                category = "Personaje"
            ),
            LibraryRecord(
                uri = "content://library/c",
                displayName = "Independiente C",
                category = "Documento",
                relatedUris = listOf("content://missing/two")
            )
        )

        val report = auditLibraryRelationships(records)

        assertEquals(2, report.unresolvedRelationCount)
        assertEquals(
            listOf("content://missing/two", "content://missing/one"),
            report.unresolvedSamples.map { it.targetUri }
        )
        assertEquals(2, report.unreferencedRecordCount)
        assertEquals(
            listOf("content://library/c", "content://library/a"),
            report.unreferencedSamples.map { it.uri }
        )
    }

    @Test
    fun deduplicatesCatalogUrisAndCapsReportSamples() {
        val records = buildList {
            add(
                LibraryRecord(
                    uri = "content://library/source",
                    displayName = "Origen",
                    category = "Imagen",
                    relatedUris = (1..10).map { "content://missing/$it" }
                )
            )
            add(LibraryRecord("content://library/source", "Duplicado de URI", "Imagen"))
        }

        val report = auditLibraryRelationships(records, sampleLimit = 3)

        assertEquals(10, report.unresolvedRelationCount)
        assertEquals(3, report.unresolvedSamples.size)
        assertEquals(1, report.unreferencedRecordCount)
        assertEquals(1, report.unreferencedSamples.size)
    }

    @Test
    fun referencesToTrashedTargetsArePreservedInsteadOfReportedAsBroken() {
        val trashedUri = "content://library/trashed"
        val records = listOf(
            LibraryRecord(
                uri = "content://library/source",
                displayName = "Referencia",
                category = "Imagen",
                relatedUris = listOf(trashedUri)
            )
        )

        val report = auditLibraryRelationships(records, retainedTargetUris = setOf(trashedUri))

        assertEquals(0, report.unresolvedRelationCount)
        // A trashed destination is not broken, but it is not counted as an active incoming edge.
        assertEquals(1, report.unreferencedRecordCount)
    }

    @Test
    fun rejectsOutOfRangeSampleLimit() {
        try {
            auditLibraryRelationships(emptyList(), sampleLimit = 0)
            throw AssertionError("El límite de muestra cero debía rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(auditLibraryRelationships(emptyList()).unresolvedSamples.isEmpty())
    }
}
