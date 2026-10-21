package com.jonhararagi.archivero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val MAX_SIMILAR_IMAGE_CANDIDATES = 2_500
internal const val SIMILAR_IMAGE_DISTANCE_THRESHOLD = 8

internal data class ImageHashSample(val uri: String, val hash: Long)
internal data class SimilarImageGroup(val hash: Long, val files: List<DocumentFile>)
internal data class SimilarImageScanResult(
    val groups: List<SimilarImageGroup>,
    val scanned: Int,
    val unreadable: Int,
    val truncated: Boolean
)

/** User-facing summary that never treats perceptual similarity as proof of duplication. */
internal fun similarImageScanSummary(result: SimilarImageScanResult): String {
    val base = when {
        result.truncated ->
            "Análisis parcial: se alcanzó el límite de candidatas (${result.scanned})."
        result.groups.isEmpty() ->
            "No se encontraron grupos de fotos parecidas en ${result.scanned} candidatas."
        else ->
            "Se encontraron ${result.groups.size} grupos de fotos parecidas en ${result.scanned} candidatas."
    }
    return if (result.unreadable > 0) {
        "$base ${result.unreadable} recurso(s) no se pudieron leer o inspeccionar."
    } else {
        base
    }
}

internal val RASTER_IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "jpe", "jfif", "jps", "png", "webp", "gif", "bmp",
    "heic", "heif", "avif", "tif", "tiff"
)

internal fun isRasterImageFile(file: DocumentFile): Boolean =
    !file.isDirectory &&
        file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) in RASTER_IMAGE_EXTENSIONS

internal fun perceptualHashDistance(first: Long, second: Long): Int =
    java.lang.Long.bitCount(first xor second)

/**
 * Groups visually similar hashes with a deterministic complete-link rule.
 *
 * Every pair in a returned group must be within maxDistance. This avoids the false confidence
 * of single-link/connected-component grouping, where a chain of near matches can join two images
 * whose hashes are far apart. The greedy partition is stable because input is ordered first.
 */
internal fun groupSimilarImageHashes(
    samples: List<ImageHashSample>,
    maxDistance: Int = SIMILAR_IMAGE_DISTANCE_THRESHOLD
): List<List<ImageHashSample>> {
    require(maxDistance in 0..64) { "La distancia de comparación debe estar entre 0 y 64." }
    if (samples.size < 2) return emptyList()

    val ordered = samples.sortedWith(
        compareBy<ImageHashSample> { it.hash }.thenBy { it.uri }
    )
    val clusters = ArrayList<MutableList<ImageHashSample>>()

    ordered.forEach { sample ->
        val compatible = clusters.firstOrNull { cluster ->
            cluster.all { member ->
                perceptualHashDistance(sample.hash, member.hash) <= maxDistance
            }
        }
        if (compatible == null) {
            clusters.add(mutableListOf(sample))
        } else {
            compatible.add(sample)
        }
    }

    return clusters
        .filter { it.size > 1 }
        .map { it.toList() }
        .sortedWith(
            compareByDescending<List<ImageHashSample>> { it.size }
                .thenBy { it.first().hash }
                .thenBy { it.first().uri }
        )
}
/**
 * Hashes only tiny previews and holds no full-resolution images after processing. Candidate
 * discovery is cancellable and stops once the configured cap is proven to be exceeded.
 */
internal suspend fun findSimilarImages(
    context: Context,
    files: List<DocumentFile>,
    maxCandidates: Int = MAX_SIMILAR_IMAGE_CANDIDATES
): SimilarImageScanResult {
    require(maxCandidates > 0) { "El límite debe ser positivo." }
    val rasterFiles = ArrayList<DocumentFile>(minOf(files.size, maxCandidates))
    val seenUris = HashSet<String>()
    var unreadable = 0
    for (file in files) {
        currentCoroutineContext().ensureActive()
        try {
            if (isRasterImageFile(file) && file.length() > 0L && seenUris.add(file.uri.toString())) {
                rasterFiles.add(file)
                if (rasterFiles.size > maxCandidates) break
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unreadable++
        }
    }
    val truncated = rasterFiles.size > maxCandidates
    val candidates = rasterFiles.take(maxCandidates)
    val samples = ArrayList<ImageHashSample>(candidates.size)
    val readableFiles = HashMap<String, DocumentFile>(candidates.size)

    for (file in candidates) {
        currentCoroutineContext().ensureActive()
        val hash = try {
            perceptualHashForUri(context, file.uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (hash == null) {
            unreadable++
        } else {
            val uri = file.uri.toString()
            samples.add(ImageHashSample(uri, hash))
            readableFiles[uri] = file
        }
    }

    val groups = groupSimilarImageHashes(samples).mapNotNull { cluster ->
        val groupedFiles = cluster.mapNotNull { readableFiles[it.uri] }
        if (groupedFiles.size < 2) null
        else SimilarImageGroup(cluster.first().hash, groupedFiles)
    }.sortedByDescending { it.files.size }

    return SimilarImageScanResult(groups, candidates.size, unreadable, truncated)
}

private fun perceptualHashForUri(context: Context, uri: Uri): Long? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decodeForUri(context, uri, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > 128) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = decodeForUri(context, uri, options) ?: return null
    val tiny = try {
        Bitmap.createScaledBitmap(decoded, 9, 8, true)
    } catch (error: Throwable) {
        decoded.recycle()
        throw error
    }
    if (tiny !== decoded) decoded.recycle()

    try {
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(tiny.getPixel(x, y))
                val right = luminance(tiny.getPixel(x + 1, y))
                hash = hash shl 1
                if (left > right) hash = hash or 1L
            }
        }
        return hash
    } finally {
        tiny.recycle()
    }
}

private fun luminance(color: Int): Int {
    val red = color shr 16 and 0xff
    val green = color shr 8 and 0xff
    val blue = color and 0xff
    return (red * 299 + green * 587 + blue * 114) / 1000
}

private fun decodeForUri(context: Context, uri: Uri, options: BitmapFactory.Options): Bitmap? {
    if (uri.scheme == "file") return BitmapFactory.decodeFile(uri.path, options)
    val stream: InputStream = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Android no pudo leer la imagen.")
    return stream.use { BitmapFactory.decodeStream(it, null, options) }
}
