package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Test

class FileOpenSupportTest {
    @Test
    fun uppercaseExtensionUsesSameMimeResolution() {
        assertEquals(
            "application/vnd.android.package-archive",
            resolveFileMimeType("INSTALADOR.APK", "application/octet-stream", "application/zip")
        )
    }

    @Test
    fun standaloneApkAlwaysUsesAndroidPackageMime() {
        assertEquals(
            "application/vnd.android.package-archive",
            resolveFileMimeType("instalador.apk", "application/octet-stream", "application/zip")
        )
    }

    @Test
    fun xapkIsHandledAsAnArchiveNotAStandaloneApk() {
        assertEquals(
            "application/zip",
            resolveFileMimeType("juego.xapk", "application/vnd.android.package-archive", "application/xapk-package-archive")
        )
    }

    @Test
    fun apksIsHandledAsAnArchiveNotAStandaloneApk() {
        assertEquals(
            "application/zip",
            resolveFileMimeType("paquete.apks", "application/vnd.android.package-archive", "application/vnd.android.package-archive")
        )
    }

    @Test
    fun specificProviderMimeTakesPrecedenceForOtherFiles() {
        assertEquals(
            "application/pdf",
            resolveFileMimeType("informe.bin", "application/pdf", "application/octet-stream")
        )
    }

    @Test
    fun extensionMimeReplacesGenericProviderMime() {
        assertEquals(
            "image/png",
            resolveFileMimeType("imagen.png", "application/octet-stream", "image/png")
        )
    }
}
