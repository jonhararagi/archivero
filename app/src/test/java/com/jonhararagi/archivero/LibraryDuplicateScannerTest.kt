package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDuplicateScannerTest {
    @Test
    fun groupsOnlyDifferentUrisWithSamePositiveSizeAndFullSha256() {
        val lowerHash = "a".repeat(64)
        val entries = listOf(
            LibraryHashEntry("content://library/a", 120L, lowerHash),
            LibraryHashEntry("content://library/b", 120L, lowerHash.uppercase()),
            // A repeated URI must not inflate the number of copies.
            LibraryHashEntry("content://library/a", 120L, lowerHash),
            // Same digest but a different size is not grouped.
            LibraryHashEntry("content://library/c", 121L, lowerHash),
            // Same size is not enough when the content hash differs.
            LibraryHashEntry("content://library/d", 120L, "b".repeat(64)),
            LibraryHashEntry("content://library/empty", 0L, lowerHash),
            LibraryHashEntry("content://library/bad-hash", 120L, "not-a-sha256")
        )

        val groups = groupExactLibraryDuplicates(entries)

        assertEquals(1, groups.size)
        assertEquals(120L, groups.single().sizeBytes)
        assertEquals(lowerHash, groups.single().sha256)
        assertEquals(listOf("content://library/a", "content://library/b"), groups.single().uris)
    }

    @Test
    fun ignoresInvalidOrSingleFingerprintEntries() {
        val groups = groupExactLibraryDuplicates(
            listOf(
                LibraryHashEntry(" ", 12L, "a".repeat(64)),
                LibraryHashEntry("content://library/a", -1L, "a".repeat(64)),
                LibraryHashEntry("content://library/b", 12L, "wrong"),
                LibraryHashEntry("content://library/c", 12L, "c".repeat(64))
            )
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun ordersGroupsDeterministicallyBySizeThenUri() {
        val smallHash = "d".repeat(64)
        val largeHash = "e".repeat(64)
        val groups = groupExactLibraryDuplicates(
            listOf(
                LibraryHashEntry("content://z", 10L, smallHash),
                LibraryHashEntry("content://a", 10L, smallHash),
                LibraryHashEntry("content://large-z", 99L, largeHash),
                LibraryHashEntry("content://large-a", 99L, largeHash)
            )
        )

        assertEquals(listOf(99L, 10L), groups.map { it.sizeBytes })
        assertEquals(listOf("content://large-a", "content://large-z"), groups.first().uris)
    }
}
