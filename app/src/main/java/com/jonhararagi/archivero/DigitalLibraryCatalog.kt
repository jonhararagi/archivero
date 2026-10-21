package com.jonhararagi.archivero

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Properties

/**
 * Metadata for a library entry. The URI identifies a resource; Archivero never copies or deletes
 * the resource when this record is saved or removed.
 */
internal data class LibraryRecord(
    val uri: String,
    val displayName: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val source: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Small, dependency-free, offline metadata catalog.
 *
 * Records are kept in a versioned Properties snapshot under the app's private files directory.
 * Writes use a sibling temporary file and an atomic replace where the filesystem supports it.
 * A malformed snapshot fails loudly instead of silently starting with an empty catalog.
 */
internal class DigitalLibraryCatalog(private val storageFile: File) {
    private var records: LinkedHashMap<String, LibraryRecord> = loadSnapshotIfPresent(storageFile)

    @Synchronized
    fun get(uri: String): LibraryRecord? = records[validateUri(uri)]

    @Synchronized
    fun all(): List<LibraryRecord> = records.values.sortedWith(
        compareBy<LibraryRecord> { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }
    )

    /**
     * Searches across visible name, category, tags, notes and provenance. Required tags use AND
     * semantics: every requested tag must be present. This method only reads in-memory metadata.
     */
    @Synchronized
    fun search(
        query: String = "",
        category: String? = null,
        requiredTags: Set<String> = emptySet(),
        favorites: Set<String> = emptySet(),
        limit: Int = DEFAULT_RESULT_LIMIT
    ): List<LibraryRecord> {
        require(limit in 1..MAX_RESULT_LIMIT) { "El límite de resultados no es válido." }
        val needle = query.trim().lowercase(Locale.ROOT)
        val categoryNeedle = category?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
        val tagNeedles = normalizeTags(requiredTags.toList()).map { it.lowercase(Locale.ROOT) }.toSet()
        return records.values.asSequence()
            .filter { item ->
                (needle.isEmpty() || searchableText(item).contains(needle)) &&
                    (categoryNeedle == null || item.category.equals(categoryNeedle, ignoreCase = true)) &&
                    tagNeedles.all { wanted -> item.tags.any { it.equals(wanted, ignoreCase = true) } }
            }
            .sortedWith(
                compareByDescending<LibraryRecord> { it.uri in favorites }
                    .thenBy { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.uri }
            )
            .take(limit)
            .toList()
    }

    /** Saves metadata only. Existing records for the same URI are intentionally updated. */
    @Synchronized
    fun upsert(record: LibraryRecord): LibraryRecord {
        val normalized = normalizeRecord(record)
        val updated = LinkedHashMap(records)
        updated[normalized.uri] = normalized
        persist(storageFile, updated.values)
        records = updated
        return normalized
    }

    /** Removes a catalog entry only. It never calls ContentResolver or deletes a physical file. */
    @Synchronized
    fun removeMetadata(uri: String): Boolean {
        val key = validateUri(uri)
        if (!records.containsKey(key)) return false
        val updated = LinkedHashMap(records)
        updated.remove(key)
        persist(storageFile, updated.values)
        records = updated
        return true
    }

    /** Writes a portable metadata-only snapshot without moving any referenced resources. */
    @Synchronized
    @Throws(IOException::class)
    fun exportSnapshot(destination: File) {
        if (sameFile(destination, storageFile)) {
            throw IOException("La exportación debe guardarse en otro archivo.")
        }
        persist(destination, records.values)
    }

    /**
     * Imports a complete, validated snapshot. URI collisions are rejected by default so an
     * import cannot overwrite existing metadata silently. Set replaceExisting=true explicitly
     * when replacement is intended. A failed import leaves the current catalog untouched.
     */
    @Synchronized
    @Throws(IOException::class)
    fun importSnapshot(source: File, replaceExisting: Boolean = false): Int {
        if (sameFile(source, storageFile)) {
            throw IOException("La importación ya es la biblioteca activa.")
        }
        val imported = loadSnapshot(source)
        val collisions = imported.keys.intersect(records.keys)
        if (collisions.isNotEmpty() && !replaceExisting) {
            throw IOException("La importación coincide con ${collisions.size} ficha(s) existente(s); no se modificó la biblioteca.")
        }
        val updated = LinkedHashMap(records)
        imported.forEach { (uri, item) -> updated[uri] = item }
        persist(storageFile, updated.values)
        records = updated
        return imported.size
    }

    private fun searchableText(item: LibraryRecord): String = sequenceOf(
        item.displayName, item.category, item.notes, item.source, item.tags.joinToString(" ")
    ).joinToString(" ").lowercase(Locale.ROOT)

    companion object {
        private const val SCHEMA_VERSION = "1"
        private const val DEFAULT_RESULT_LIMIT = 500
        private const val MAX_RESULT_LIMIT = 10_000
        private const val MAX_RECORDS = 100_000
        private const val MAX_TAGS = 100
        private const val MAX_TAG_LENGTH = 64
        private const val MAX_NAME_LENGTH = 1_024
        private const val MAX_CATEGORY_LENGTH = 128
        private const val MAX_NOTES_LENGTH = 20_000
        private const val MAX_SOURCE_LENGTH = 4_096

        private fun validateUri(uri: String): String {
            val candidate = uri.trim()
            require(candidate.isNotEmpty()) { "La URI del recurso no puede estar vacía." }
            require(candidate.length <= 4_096 && candidate.none { it == '\u0000' || it.isISOControl() }) {
                "La URI del recurso no es válida."
            }
            return candidate
        }

        private fun normalizeTags(tags: List<String>): List<String> {
            require(tags.size <= MAX_TAGS) { "Una ficha admite hasta $MAX_TAGS etiquetas." }
            val seen = HashSet<String>()
            return tags.asSequence()
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotEmpty() }
                .onEach {
                    require(it.length <= MAX_TAG_LENGTH && it.none(Char::isISOControl)) {
                        "Una etiqueta supera el tamaño permitido o contiene caracteres de control."
                    }
                }
                .filter { seen.add(it.lowercase(Locale.ROOT)) }
                .toList()
        }

        private fun normalizeRecord(record: LibraryRecord): LibraryRecord {
            val uri = validateUri(record.uri)
            val name = record.displayName.trim()
            val category = record.category.trim()
            val notes = record.notes.trim()
            val source = record.source.trim()
            require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
                "El nombre debe contener entre 1 y $MAX_NAME_LENGTH caracteres."
            }
            require(category.isNotEmpty() && category.length <= MAX_CATEGORY_LENGTH) {
                "La categoría no es válida."
            }
            require(notes.length <= MAX_NOTES_LENGTH) { "Las notas son demasiado largas." }
            require(source.length <= MAX_SOURCE_LENGTH) { "La procedencia es demasiado larga." }
            require(record.updatedAtMillis >= 0L) { "La fecha de modificación no es válida." }
            return record.copy(
                uri = uri,
                displayName = name,
                category = category,
                tags = normalizeTags(record.tags),
                notes = notes,
                source = source
            )
        }

        private fun loadSnapshotIfPresent(file: File): LinkedHashMap<String, LibraryRecord> =
            if (!file.exists()) LinkedHashMap() else loadSnapshot(file)

        @Throws(IOException::class)
        private fun loadSnapshot(file: File): LinkedHashMap<String, LibraryRecord> {
            if (!file.isFile || !file.canRead()) throw IOException("No se puede leer el archivo de metadatos.")
            val properties = Properties()
            try {
                file.inputStream().use(properties::load)
            } catch (error: Exception) {
                throw IOException("El archivo de metadatos está dañado o no se puede leer.", error)
            }
            if (properties.getProperty("schema.version") != SCHEMA_VERSION) {
                throw IOException("La versión del archivo de metadatos no es compatible.")
            }
            val count = properties.getProperty("records.count")?.toIntOrNull()
                ?: throw IOException("El archivo de metadatos no contiene un recuento válido.")
            if (count !in 0..MAX_RECORDS) throw IOException("El archivo de metadatos excede el límite permitido.")

            val loaded = LinkedHashMap<String, LibraryRecord>(count)
            try {
                repeat(count) { index ->
                    val prefix = "record.$index."
                    fun required(field: String): String = properties.getProperty(prefix + field)
                        ?: throw IOException("Falta el campo ${prefix + field}.")
                    val tagCount = required("tag.count").toIntOrNull()
                        ?: throw IOException("La cantidad de etiquetas no es válida.")
                    if (tagCount !in 0..MAX_TAGS) throw IOException("La ficha contiene demasiadas etiquetas.")
                    val tags = (0 until tagCount).map { tagIndex -> required("tag.$tagIndex") }
                    val record = normalizeRecord(
                        LibraryRecord(
                            uri = required("uri"),
                            displayName = required("displayName"),
                            category = required("category"),
                            tags = tags,
                            notes = required("notes"),
                            source = required("source"),
                            updatedAtMillis = required("updatedAtMillis").toLongOrNull()
                                ?: throw IOException("La fecha de modificación no es válida.")
                        )
                    )
                    if (loaded.put(record.uri, record) != null) {
                        throw IOException("El archivo contiene fichas duplicadas para la misma URI.")
                    }
                }
            } catch (error: IllegalArgumentException) {
                throw IOException("El archivo de metadatos contiene una ficha no válida.", error)
            }
            return loaded
        }

        @Throws(IOException::class)
        private fun persist(destination: File, records: Collection<LibraryRecord>) {
            if (records.size > MAX_RECORDS) throw IOException("La biblioteca excede el límite de fichas.")
            val normalized = records.map(::normalizeRecord)
            if (normalized.map { it.uri }.toSet().size != normalized.size) {
                throw IOException("No se puede guardar un archivo con URI duplicadas.")
            }
            val properties = Properties().apply {
                setProperty("schema.version", SCHEMA_VERSION)
                setProperty("records.count", normalized.size.toString())
                normalized.forEachIndexed { index, item ->
                    val prefix = "record.$index."
                    setProperty(prefix + "uri", item.uri)
                    setProperty(prefix + "displayName", item.displayName)
                    setProperty(prefix + "category", item.category)
                    setProperty(prefix + "notes", item.notes)
                    setProperty(prefix + "source", item.source)
                    setProperty(prefix + "updatedAtMillis", item.updatedAtMillis.toString())
                    setProperty(prefix + "tag.count", item.tags.size.toString())
                    item.tags.forEachIndexed { tagIndex, tag ->
                        setProperty(prefix + "tag.$tagIndex", tag)
                    }
                }
            }

            val parent = destination.absoluteFile.parentFile
                ?: throw IOException("No se pudo determinar la carpeta de destino.")
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("No se pudo crear la carpeta de metadatos.")
            }
            val temporary = try {
                Files.createTempFile(parent.toPath(), ".archivero-library-", ".tmp")
            } catch (error: Exception) {
                throw IOException("No se pudo preparar el guardado seguro de metadatos.", error)
            }
            try {
                temporary.toFile().outputStream().use { properties.store(it, "Archivero digital library metadata") }
                try {
                    Files.move(
                        temporary,
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (error: Exception) {
                runCatching { Files.deleteIfExists(temporary) }
                if (error is IOException) throw error
                throw IOException("No se pudieron guardar los metadatos; se conserva la versión anterior.", error)
            }
        }

        private fun sameFile(first: File, second: File): Boolean =
            runCatching { first.canonicalFile == second.canonicalFile }
                .getOrDefault(first.absoluteFile == second.absoluteFile)
    }
}
