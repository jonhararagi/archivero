package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateDetectionTest {
    @Test
    fun keepsOnlyPositiveSizedCandidatesWithSharedSizes() {
        val candidates = listOf(
            SizedCandidate("a.jpg", 128L),
            SizedCandidate("b.jpg", 128L),
            SizedCandidate("unique.png", 256L),
            SizedCandidate("empty.bin", 0L),
            SizedCandidate("invalid.bin", -1L)
        )

        assertEquals(
            listOf("a.jpg", "b.jpg"),
            potentialDuplicateCandidates(candidates) { it }.map { it.value }
        )
    }

    @Test
    fun removesRepeatedUrisBeforeCountingSharedSizes() {
        val candidates = listOf(
            SizedCandidate("content://same/file", 128L),
            SizedCandidate("content://same/file", 128L),
            SizedCandidate("content://other/file", 128L),
            SizedCandidate("content://single/file", 99L)
        )

        assertEquals(
            listOf("content://same/file", "content://other/file"),
            potentialDuplicateCandidates(candidates) { it }.map { it.value }
        )
    }

    @Test
    fun returnsNoCandidatesForEmptyOrUniqueInput() {
        assertEquals(
            emptyList<SizedCandidate<String>>(),
            potentialDuplicateCandidates(listOf(SizedCandidate("solo", 40L))) { it }
        )
        assertEquals(
            emptyList<SizedCandidate<String>>(),
            potentialDuplicateCandidates(emptyList<SizedCandidate<String>>()) { it }
        )
    }
}
