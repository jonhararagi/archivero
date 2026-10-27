package com.jonhararagi.archivero

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Reads a resource incrementally and returns SHA-256 only when exactly the expected byte count
 * was observed. The caller owns and closes the stream. checkActive lets Android callers cancel
 * long scans without introducing a coroutine dependency into this utility.
 */
internal fun hashInputStreamSha256(
    input: InputStream,
    expectedSizeBytes: Long,
    checkActive: () -> Unit = {}
): String {
    require(expectedSizeBytes >= 0L) { "El tamaño esperado no puede ser negativo." }
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var bytesRead = 0L
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (count.toLong() > expectedSizeBytes - bytesRead) {
            throw IOException("El recurso contiene más bytes que el tamaño informado; se descartó el hash.")
        }
        bytesRead += count.toLong()
        digest.update(buffer, 0, count)
    }
    if (bytesRead != expectedSizeBytes) {
        throw IOException(
            "Lectura incompleta del recurso: se esperaban $expectedSizeBytes bytes y se recibieron $bytesRead."
        )
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

/**
 * Requires byte length to remain unchanged. When the provider reports timestamps, both reads must
 * report the same positive timestamp; when both report zero, timestamps are treated as unsupported.
 * An inconsistent/failed timestamp query invalidates the fingerprint rather than guessing.
 */
internal fun duplicateCandidateMetadataStable(
    expectedSizeBytes: Long,
    sizeBeforeBytes: Long,
    sizeAfterBytes: Long,
    lastModifiedBeforeMillis: Long,
    lastModifiedAfterMillis: Long
): Boolean {
    if (expectedSizeBytes <= 0L ||
        sizeBeforeBytes != expectedSizeBytes ||
        sizeAfterBytes != expectedSizeBytes
    ) {
        return false
    }
    return if (lastModifiedBeforeMillis == 0L && lastModifiedAfterMillis == 0L) {
        true
    } else {
        lastModifiedBeforeMillis > 0L &&
            lastModifiedAfterMillis > 0L &&
            lastModifiedBeforeMillis == lastModifiedAfterMillis
    }
}
