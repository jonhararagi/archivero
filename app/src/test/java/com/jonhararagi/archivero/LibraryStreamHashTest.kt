package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStreamHashTest {
    @Test
    fun reportsStandardSha256ForBinaryStream() {
        val data = byteArrayOf(0, 1, -1, 10, 13, 127, -128)
        val hash = hashInputStreamSha256(data.inputStream(), data.size.toLong())
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun rejectsNegativeExpectedByteCount() {
        try {
            hashInputStreamSha256(byteArrayOf().inputStream(), expectedSizeBytes = -1L)
            throw AssertionError("El tamaño negativo debe rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
