package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSimilarityTest {
    @Test
    fun perceptualDistanceCountsChangedBits() {
        assertEquals(0, perceptualHashDistance(0L, 0L))
        assertEquals(1, perceptualHashDistance(0L, 1L))
        assertEquals(3, perceptualHashDistance(0L, 0b10101L))
    }

    @Test
    fun groupsSimilarImagesButLeavesDistantImagesAlone() {
        val groups = groupSimilarImageHashes(
            listOf(
                ImageHashSample("a", 0b00000000L),
                ImageHashSample("b", 0b00000101L),
                ImageHashSample("c", 0b11111111L),
                ImageHashSample("d", 0b11111110L)
            ),
            maxDistance = 3
        )

        assertEquals(2, groups.size)
        assertTrue(groups.any { group -> group.map { it.uri }.toSet() == setOf("a", "b") })
        assertTrue(groups.any { group -> group.map { it.uri }.toSet() == setOf("c", "d") })
    }

    @Test
    fun doesNotGroupHashesBeyondTheThreshold() {
        val groups = groupSimilarImageHashes(
            listOf(ImageHashSample("a", 0L), ImageHashSample("b", 0b1111L)),
            maxDistance = 2
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun doesNotChainTogetherDistantEndpointsThroughAnIntermediateImage() {
        val groups = groupSimilarImageHashes(
            listOf(
                ImageHashSample("endpoint-a", 0L),
                ImageHashSample("middle", 0b1111L),
                ImageHashSample("endpoint-c", 0b11111111L)
            ),
            maxDistance = 4
        )

        assertEquals(1, groups.size)
        assertEquals(setOf("endpoint-a", "middle"), groups.single().map { it.uri }.toSet())
        assertEquals(8, perceptualHashDistance(0L, 0b11111111L))
    }

    @Test
    fun groupingIsDeterministicRegardlessOfInputOrder() {
        val samples = listOf(
            ImageHashSample("late", 0b11111110L),
            ImageHashSample("first", 0L),
            ImageHashSample("middle", 0b1111L),
            ImageHashSample("near-first", 1L)
        )

        val forward = groupSimilarImageHashes(samples, maxDistance = 4)
            .map { it.map(ImageHashSample::uri) }
        val reverse = groupSimilarImageHashes(samples.reversed(), maxDistance = 4)
            .map { it.map(ImageHashSample::uri) }

        assertEquals(forward, reverse)
    }

    @Test
    fun rejectsInvalidSimilarityThresholds() {
        listOf(-1, 65).forEach { threshold ->
            try {
                groupSimilarImageHashes(
                    listOf(ImageHashSample("a", 0L), ImageHashSample("b", 0L)),
                    maxDistance = threshold
                )
                throw AssertionError("El umbral inválido debía rechazarse.")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun summaryWarnsWhenCandidateLimitMakesResultsPartial() {
        val summary = similarImageScanSummary(
            SimilarImageScanResult(groups = emptyList(), scanned = 2_500, unreadable = 3, truncated = true)
        )

        assertTrue(summary.contains("Análisis parcial"))
        assertTrue(summary.contains("(2500)"))
        assertTrue(summary.contains("3 recurso(s)"))
    }

    @Test
    fun summaryDescribesVisualSimilarityWithoutCallingItExactDuplication() {
        val summary = similarImageScanSummary(
            SimilarImageScanResult(
                groups = listOf(SimilarImageGroup(10L, emptyList())),
                scanned = 2,
                unreadable = 0,
                truncated = false
            )
        )

        assertTrue(summary.contains("fotos parecidas"))
        assertTrue(!summary.contains("duplicados exactos"))
    }

    @Test
    fun summaryReportsNoGroupsClearly() {
        val summary = similarImageScanSummary(
            SimilarImageScanResult(groups = emptyList(), scanned = 4, unreadable = 0, truncated = false)
        )

        assertTrue(summary.contains("No se encontraron grupos"))
        assertTrue(summary.contains("4 candidatas"))
    }

    @Test
    fun handlesEmptyOrSingleSampleLists() {
        assertTrue(groupSimilarImageHashes(emptyList()).isEmpty())
        assertTrue(groupSimilarImageHashes(listOf(ImageHashSample("only", 42L))).isEmpty())
    }
}
