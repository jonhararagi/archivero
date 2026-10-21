package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryResourceAccessAuditTest {
    @Test
    fun accumulatorCountsEveryStateAndCapsSamples() {
        val audit = LibraryResourceAccessAuditAccumulator(totalRecords = 7, sampleLimit = 1)
        audit.record(LibraryResourceAccessProbe("content://a", "Accesible A", LibraryResourceAccessState.AVAILABLE))
        audit.record(LibraryResourceAccessProbe("content://b", "Ausente B", LibraryResourceAccessState.NOT_VISIBLE))
        audit.record(LibraryResourceAccessProbe("content://c", "Ausente C", LibraryResourceAccessState.NOT_VISIBLE))
        audit.record(LibraryResourceAccessProbe("content://d", "Permiso D", LibraryResourceAccessState.NO_ACCESS))
        audit.record(LibraryResourceAccessProbe("content://e", "Permiso E", LibraryResourceAccessState.NO_ACCESS))
        audit.record(LibraryResourceAccessProbe("content://f", "Accesible F", LibraryResourceAccessState.AVAILABLE))
        audit.record(LibraryResourceAccessProbe("content://g", "Accesible G", LibraryResourceAccessState.AVAILABLE))

        val result = audit.result()

        assertEquals(7, result.totalRecords)
        assertEquals(7, result.checkedRecords)
        assertEquals(3, result.availableCount)
        assertEquals(2, result.notVisibleCount)
        assertEquals(2, result.noAccessCount)
        assertEquals(listOf("content://b"), result.notVisibleSamples.map { it.uri })
        assertEquals(listOf("content://d"), result.noAccessSamples.map { it.uri })
    }

    @Test
    fun emptyAuditIsValidAndHasNoIssues() {
        val result = LibraryResourceAccessAuditAccumulator(totalRecords = 0).result()
        assertEquals(0, result.checkedRecords)
        assertEquals(0, result.availableCount)
        assertTrue(result.notVisibleSamples.isEmpty())
        assertTrue(result.noAccessSamples.isEmpty())
    }

    @Test
    fun rejectsInvalidSampleLimit() {
        try {
            LibraryResourceAccessAuditAccumulator(totalRecords = 1, sampleLimit = 0)
            throw AssertionError("El límite de muestra cero debía rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
