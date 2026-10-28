package com.jonhararagi.archivero

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySnapshotTransferTest {
    @Test
    fun copyPreservesBytesAndUsesKnownSha256Vector() {
        val content = "abc".toByteArray(Charsets.UTF_8)
        val destination = ByteArrayOutputStream()

        val copied = copyAndFingerprintLibraryStream(
            ByteArrayInputStream(content), destination, maximumBytes = content.size.toLong()
        )

        assertEquals("abc", destination.toString("UTF-8"))
        assertEquals(3L, copied.byteCount)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", copied.sha256)
        assertEquals(
            copied,
            fingerprintLibraryStream(ByteArrayInputStream(destination.toByteArray()), maximumBytes = 3L)
        )
    }

    @Test
    fun emptySnapshotCanBeFingerprintedAtZeroByteLimit() {
        val empty = fingerprintLibraryStream(ByteArrayInputStream(byteArrayOf()), maximumBytes = 0L)

        assertEquals(0L, empty.byteCount)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty.sha256)
    }

    @Test
    fun rejectsContentsBeyondLimitBeforeWritingOverflowingChunk() {
        val destination = ByteArrayOutputStream()
        try {
            copyAndFingerprintLibraryStream(
                ByteArrayInputStream("four".toByteArray(Charsets.UTF_8)),
                destination,
                maximumBytes = 3L
            )
            throw AssertionError("El flujo que supera el límite debía rechazarse.")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("límite seguro"))
        }

        assertEquals(0, destination.size())
    }

    @Test
    fun bulkReadReturningZeroStillMakesProgress() {
        val source = object : ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8)) {
            private var returnZeroOnce = true

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZeroOnce) {
                    returnZeroOnce = false
                    return 0
                }
                return super.read(buffer, offset, length)
            }
        }
        val destination = ByteArrayOutputStream()

        val copied = copyAndFingerprintLibraryStream(source, destination, maximumBytes = 3L)

        assertEquals("abc", destination.toString("UTF-8"))
        assertEquals(3L, copied.byteCount)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            copied.sha256
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLimit() {
        fingerprintLibraryStream(ByteArrayInputStream(byteArrayOf()), maximumBytes = -1L)
    }
}
