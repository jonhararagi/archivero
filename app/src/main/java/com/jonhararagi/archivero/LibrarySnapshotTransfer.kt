package com.jonhararagi.archivero

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Fingerprint of the exact bytes consumed from a snapshot stream. */
internal data class LibraryStreamFingerprint(
    val byteCount: Long,
    val sha256: String
)

internal enum class LibrarySnapshotVerification {
    VERIFIED,
    UNAVAILABLE,
    MISMATCH
}

internal data class LibrarySnapshotExportResult(
    val bytesWritten: Long,
    val verification: LibrarySnapshotVerification
)

/**
 * Copies a bounded stream and fingerprints the exact bytes written.
 *
 * The caller owns both streams. A zero-byte bulk read is followed by a single-byte read to
 * guarantee progress with document providers that do not fully respect InputStream's contract.
 */
internal fun copyAndFingerprintLibraryStream(
    input: InputStream,
    output: OutputStream,
    maximumBytes: Long
): LibraryStreamFingerprint = consumeAndFingerprintLibraryStream(input, output, maximumBytes)

/** Fingerprints a bounded stream, suitable for read-back verification after export. */
internal fun fingerprintLibraryStream(
    input: InputStream,
    maximumBytes: Long
): LibraryStreamFingerprint = consumeAndFingerprintLibraryStream(input, null, maximumBytes)

private fun consumeAndFingerprintLibraryStream(
    input: InputStream,
    output: OutputStream?,
    maximumBytes: Long
): LibraryStreamFingerprint {
    require(maximumBytes >= 0L) { "El límite de bytes no puede ser negativo." }
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var totalBytes = 0L

    fun ensureWithinLimit(additionalBytes: Long) {
        if (additionalBytes > maximumBytes - totalBytes) {
            throw IOException("La instantánea supera el límite seguro de $maximumBytes bytes.")
        }
    }

    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) {
            val single = input.read()
            if (single < 0) break
            ensureWithinLimit(1L)
            output?.write(single)
            digest.update(single.toByte())
            totalBytes++
            continue
        }

        ensureWithinLimit(count.toLong())
        output?.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        totalBytes += count
    }

    val hex = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return LibraryStreamFingerprint(totalBytes, hex)
}
