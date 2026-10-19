package com.jonhararagi.archivero

import java.util.Locale

/**
 * Chooses a MIME type that matches the container actually being opened.
 * XAPK/APKS are bundles, not a standalone Android package accepted by the
 * platform package installer.
 */
internal fun resolveFileMimeType(
    fileName: String,
    providedMime: String?,
    extensionMime: String?
): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    return when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "xapk", "apks" -> "application/zip"
        else -> providedMime
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: extensionMime?.takeIf { it.isNotBlank() }
            ?: providedMime.orEmpty()
    }
}
