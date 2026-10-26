package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRelationshipRepairTest {
    private fun record(uri: String, name: String, related: List<String> = emptyList()) =
        LibraryRecord(
            uri = uri,
            displayName = name,
            category = "Referencia",
            relatedUris = related
        )

    @Test
    fun previewRemovesOnlyTargetsThatAreNeitherActiveNorTrashed() {
        val a = "content://library/a"
        val b = "content://library/b"
        val trashed = "content://library/trashed"
        val missing = "content://library/missing"
        val original = record(a, "A", listOf(b, trashed, missing))
        val records = listOf(original, record(b, "B"))

        val plan = planLibraryRelationshipRepair(records, setOf(trashed))

        assertEquals(2, plan.checkedRecordCount)
        assertEquals(1, plan.affectedRecordCount)
        assertEquals(1, plan.removedRelationCount)
        assertEquals(listOf(b, trashed), plan.changes.single().replacementRelatedUris)
        assertEquals(original.relatedUris, records.first().relatedUris)
        assertEquals("A", plan.samples.single().displayName)
    }

    @Test
    fun previewCountsAllChangesButCapsSamples() {
        val records = (0 until 5).map { index ->
            record("content://library/" + index, "Record " + index, listOf("content://missing/" + index))
        }

        val plan = planLibraryRelationshipRepair(records, sampleLimit = 2)

        assertEquals(5, plan.affectedRecordCount)
        assertEquals(5, plan.removedRelationCount)
        assertEquals(2, plan.samples.size)
    }

    @Test
    fun noOpPreviewPreservesReferencesToTrashedTargets() {
        val active = "content://library/active"
        val trashed = "content://library/trash"
        val plan = planLibraryRelationshipRepair(
            listOf(record(active, "Active", listOf(trashed))),
            retainedTargetUris = setOf(trashed)
        )

        assertEquals(0, plan.affectedRecordCount)
        assertEquals(0, plan.removedRelationCount)
        assertTrue(plan.changes.isEmpty())
        assertFalse(plan.activeUris.contains(trashed))
    }

    @Test
    fun rejectsOverlappingActiveAndTrashedUris() {
        val uri = "content://library/a"
        try {
            planLibraryRelationshipRepair(listOf(record(uri, "A")), retainedTargetUris = setOf(uri))
            throw AssertionError("Una URI activa no debería figurar también en la papelera.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
