package com.jonhararagi.archivero

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level checks for the minimum supported Android API and a current API.
 * These complement JVM tests; they do not replace OEM/physical-device validation.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilitySmokeTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun libraryMetadataCanBeWrittenAndReopenedInAndroidStorage() {
        val directory = File(context.cacheDir, "archivero-compat-" + System.nanoTime())
        assertTrue("Test directory should be created", directory.mkdirs())
        try {
            val catalogFile = File(directory, "library.properties")
            val uri = "content://archivero.compat/" + System.nanoTime()
            val record = LibraryRecord(
                uri = uri,
                displayName = "Referencia de compatibilidad",
                category = "Documento",
                tags = listOf("Android", "compatibilidad"),
                notes = "Persistencia local en el dispositivo"
            )

            DigitalLibraryCatalog(catalogFile).upsert(record)
            val reopened = DigitalLibraryCatalog(catalogFile)

            assertEquals(record, reopened.get(record.uri))
            assertEquals(1, reopened.search("compatibilidad").size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun localDirectoryPagesStayBoundedAndDoNotRepeatEntries() {
        val directory = File(context.cacheDir, "archivero-pages-" + System.nanoTime())
        assertTrue("Test directory should be created", directory.mkdirs())
        try {
            (1..5).forEach { index ->
                File(directory, "entry-$index.txt").writeText("entry $index")
            }

            val parent = DocumentFile.fromFile(directory)
            val first = listDocumentChildrenBounded(context, parent, limit = 2, offset = 0)
            val second = listDocumentChildrenBounded(context, parent, limit = 2, offset = first.entries.size)
            val third = listDocumentChildrenBounded(
                context, parent, limit = 2, offset = first.entries.size + second.entries.size
            )

            assertEquals(2, first.entries.size)
            assertTrue(first.truncated)
            assertEquals(2, second.entries.size)
            assertTrue(second.truncated)
            assertEquals(1, third.entries.size)
            assertFalse(third.truncated)

            val firstUris = first.entries.map { it.uri.toString() }.toSet()
            val secondUris = second.entries.map { it.uri.toString() }.toSet()
            val thirdUris = third.entries.map { it.uri.toString() }.toSet()
            assertTrue(firstUris.intersect(secondUris).isEmpty())
            assertTrue(firstUris.intersect(thirdUris).isEmpty())
            assertTrue(secondUris.intersect(thirdUris).isEmpty())
            assertEquals(5, firstUris.union(secondUris).union(thirdUris).size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun apkBundleMimeTypesRemainPlatformCompatible() {
        assertEquals(
            "application/vnd.android.package-archive",
            resolveFileMimeType("installer.APK", "application/octet-stream", "application/zip")
        )
        assertEquals(
            "application/zip",
            resolveFileMimeType("split.apks", "application/vnd.android.package-archive", "application/zip")
        )
    }
}
