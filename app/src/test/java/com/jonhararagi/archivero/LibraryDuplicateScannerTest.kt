package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDuplicateScannerTest {

    @Test
    fun hashesIncrementallyAndMatchesKnownSha256Vector() {
        val hash = hashInputStreamSha256("abc".toByteArray().inputStream(), expectedSizeBytes = 3L)

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash
        )
    }

    @Test
    fun rejectsTruncatedStreamInsteadOfProducingFingerprint() {
        try {
            hashInputStreamSha256(byteArrayOf(1, 2, 3).inputStream(), expectedSizeBytes = 4L)
            throw AssertionError("Una lectura truncada no debe producir un hash válido.")
        } catch (_: java.io.IOException) {
            // Expected.
        }
    }

    @Test
    fun rejectsStreamLongerThanReportedSize() {
        try {
            hashInputStreamSha256(byteArrayOf(1, 2, 3).inputStream(), expectedSizeBytes = 2L)
            throw AssertionError("Una lectura más larga que el tamaño informado debe rechazarse.")
        } catch (_: java.io.IOException) {
            // Expected.
        }
    }

    @Test
    fun hashesEmptyStreamCorrectlyButDuplicateMetadataRejectsEmptyResource() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hashInputStreamSha256(byteArrayOf().inputStream(), expectedSizeBytes = 0L)
        )
        assertEquals(false, duplicateCandidateMetadataStable(0L, 0L, 0L, 0L, 0L))
    }

    @Test
    fun cooperativelyChecksCancellationForEveryReadChunk() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val bytes = ByteArray(150_000) { (it % 251).toByte() }

        val hash = hashInputStreamSha256(
            bytes.inputStream(),
            expectedSizeBytes = bytes.size.toLong()
        ) { calls.incrementAndGet() }

        assertEquals(64, hash.length)
        assertTrue("La cancelación debe comprobarse en cada lectura", calls.get() >= 3)
    }

    @Test
    fun changingOrInconsistentlyReportedMetadataInvalidatesHash() {
        assertTrue(duplicateCandidateMetadataStable(10L, 10L, 10L, 42L, 42L))
        assertTrue(duplicateCandidateMetadataStable(10L, 10L, 10L, 0L, 0L))
        assertEquals(false, duplicateCandidateMetadataStable(10L, 10L, 10L, 42L, 43L))
        assertEquals(false, duplicateCandidateMetadataStable(10L, 10L, 10L, 0L, 42L))
        assertEquals(false, duplicateCandidateMetadataStable(10L, 10L, 10L, -1L, -1L))
        assertEquals(false, duplicateCandidateMetadataStable(10L, 10L, 9L, 42L, 42L))
    }

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
