package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import java.io.InputStream
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

    @Test
    fun handlesProvidersThatReturnZeroForBulkReads() {
        val data = byteArrayOf(0, 1, -1, 10, 13, 127, -128)
        val expected = hashInputStreamSha256(data.inputStream(), data.size.toLong())
        val input = object : InputStream() {
            private var bytesRead = 0
            private var returnZero = true

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                if (bytesRead >= data.size) return -1
                val count = minOf(length, data.size - bytesRead)
                data.copyInto(buffer, offset, bytesRead, bytesRead + count)
                bytesRead += count
                return count
            }

            override fun read(): Int {
                if (bytesRead >= data.size) return -1
                return data[bytesRead++].toInt() and 0xff
            }
        }

        assertEquals(expected, hashInputStreamSha256(input, data.size.toLong()))
    }

    @Test
    fun matchesKnownSha256Vector() {
        val hash = hashInputStreamSha256("abc".byteInputStream(), 3L)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

}
