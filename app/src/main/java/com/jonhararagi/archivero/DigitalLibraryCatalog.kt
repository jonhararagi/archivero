package com.jonhararagi.archivero

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import java.util.Properties

internal const val MAX_LIBRARY_SNAPSHOT_BYTES: Long = 64L * 1024L * 1024L
internal const val LIBRARY_RESULTS_PAGE_SIZE: Int = 500
internal const val MAX_LIBRARY_RESULT_LIMIT: Int = 10_000

/** Moves the visible result window forward without ever exceeding the supported result cap. */
internal fun nextLibraryResultLimit(currentLimit: Int): Int {
    require(currentLimit in 1..MAX_LIBRARY_RESULT_LIMIT) { "El límite de resultados actual no es válido." }
    return minOf(currentLimit + LIBRARY_RESULTS_PAGE_SIZE, MAX_LIBRARY_RESULT_LIMIT)
}

/**
 * Makes searches accent-insensitive while keeping Spanish ñ distinct from n.
 * The text stays derived in memory; stored names, notes, and tags are not rewritten.
 */
internal fun normalizeLibrarySearchText(value: String): String {
    val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed.replace(Regex("(?<!n)\\p{M}+|(?<=n)(?!\\u0303)\\p{M}+"), "")
}

/**
 * Splits a free-text query into whitespace-separated terms. Every term must occur somewhere in
 * the searchable metadata, but terms need not be adjacent or appear in the same metadata field.
 */
internal fun librarySearchTerms(query: String): List<String> =
    normalizeLibrarySearchText(query.trim()).split(Regex("\\s+")).filter { it.isNotBlank() }

/** The searchable text is normalized to lowercase and search-accent form by callers. */
internal fun librarySearchMatches(searchableText: String, terms: List<String>): Boolean =
    terms.all { term -> searchableText.contains(term) }

/**
 * Combined, stable UI filtering for the virtualized library. A small result cap prevents the
 * presentation layer from materializing every match just to show the first screen.
 */
internal fun filterLibraryRecords(
    records: Collection<LibraryRecord>,
    query: String = "",
    category: String = "",
    resourceKind: String = "",
    sortMode: String = "Nombre",
    favorites: Set<String> = emptySet(),
    onlyFavorites: Boolean = false,
    limit: Int = 500,
    project: String = ""
): List<LibraryRecord> {
    require(limit in 1..MAX_LIBRARY_RESULT_LIMIT) { "El límite de resultados no es válido." }
    val queryTerms = librarySearchTerms(query)
    val categoryNeedle = normalizeLibrarySearchText(category.trim())
    val kindNeedle = normalizeLibrarySearchText(resourceKind.trim())
    val projectNeedle = normalizeLibrarySearchText(project.trim())
    val order = when (sortMode) {
        "Recientes" -> compareByDescending<LibraryRecord> { it.uri in favorites }
            .thenByDescending { it.updatedAtMillis }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }
        "Proyecto" -> compareByDescending<LibraryRecord> { it.uri in favorites }
            .thenBy { it.project.lowercase(Locale.ROOT) }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }
        else -> compareByDescending<LibraryRecord> { it.uri in favorites }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }
    }

    // Keep only the best N matches in a bounded max-heap. This avoids sorting and allocating
    // every matching record when the catalog is large and the screen needs only its first page.
    val best = PriorityQueue<LibraryRecord>(limit, order.reversed())
    records.forEach { record ->
        val matches = librarySearchMatches(record.normalizedSearchText, queryTerms) &&
            (categoryNeedle.isEmpty() || normalizeLibrarySearchText(record.category).contains(categoryNeedle)) &&
            (kindNeedle.isEmpty() || normalizeLibrarySearchText(record.resourceKind).contains(kindNeedle)) &&
            (projectNeedle.isEmpty() || normalizeLibrarySearchText(record.project.trim()) == projectNeedle) &&
            (!onlyFavorites || record.uri in favorites)
        if (!matches) return@forEach

        if (best.size < limit) {
            best.add(record)
        } else {
            val worst = best.peek()
            if (worst != null && order.compare(record, worst) < 0) {
                best.poll()
                best.add(record)
            }
        }
    }
    return best.toList().sortedWith(order)
}

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
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val project: String = "",
    val resourceKind: String = "Sin clasificar",
    val relatedUris: List<String> = emptyList()
) {
    /**
     * A record is treated as an immutable value. The normalized search document is cached only in
     * memory, not persisted; creating a modified copy automatically gets a fresh cache.
     */
    internal val normalizedSearchText: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        sequenceOf(
            displayName, category, project, resourceKind, tags.joinToString(" "),
            notes, source, relatedUris.joinToString(" ")
        ).joinToString(" ").let(::normalizeLibrarySearchText)
    }
}

/** Metadata kept in the recoverable library trash; the referenced physical file is never moved. */
internal data class TrashedLibraryRecord(
    val record: LibraryRecord,
    val deletedAtMillis: Long
)

private data class LibraryCatalogSnapshot(
    val records: LinkedHashMap<String, LibraryRecord>,
    val trashedRecords: LinkedHashMap<String, TrashedLibraryRecord>
)

/**
 * Small, dependency-free, offline metadata catalog.
 *
 * Records are kept in a versioned Properties snapshot under the app's private files directory.
 * Writes use a sibling temporary file and an atomic replace where the filesystem supports it.
 * A malformed snapshot fails loudly instead of silently starting with an empty catalog.
 */
internal class DigitalLibraryCatalog(
    private val storageFile: File,
    /**
     * Optional deterministic seam for exercising failed snapshot writes in transaction tests.
     * Production callers use the normal atomic file writer.
     */
    private val snapshotWriter: ((File, Collection<LibraryRecord>, Collection<TrashedLibraryRecord>) -> Unit)? = null
) {
    private var records: LinkedHashMap<String, LibraryRecord> = LinkedHashMap()
    private var trashedRecords: LinkedHashMap<String, TrashedLibraryRecord> = LinkedHashMap()

    init {
        val snapshot = loadSnapshotIfPresent(storageFile)
        records = snapshot.records
        trashedRecords = snapshot.trashedRecords
    }

    /** Read-only list of recoverable metadata entries, newest deletion first. */
    @Synchronized
    fun trashed(): List<TrashedLibraryRecord> = trashedRecords.values.sortedWith(
        compareByDescending<TrashedLibraryRecord> { it.deletedAtMillis }
            .thenBy { it.record.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.record.uri }
    )

    @Synchronized
    fun get(uri: String): LibraryRecord? = records[validateUri(uri)]

    @Synchronized
    fun all(): List<LibraryRecord> = records.values.sortedWith(
        compareBy<LibraryRecord> { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }
    )

    /**
     * Returns a detached snapshot in storage order, without sorting the full collection.
     * Use it when the caller applies its own filtering/sorting; use all() for alphabetic order.
     */
    @Synchronized
    fun recordsSnapshot(): List<LibraryRecord> = records.values.toList()

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
        val queryTerms = librarySearchTerms(query)
        val categoryNeedle = category?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeLibrarySearchText)
        val tagNeedles = normalizeTags(requiredTags.toList()).map(::normalizeLibrarySearchText).toSet()
        val order = compareByDescending<LibraryRecord> { it.uri in favorites }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
            .thenBy { it.uri }

        // Keep only the best N matches instead of sorting and materializing every match.
        // The scan remains O(catalog size), while selection uses O(limit) memory and O(log limit)
        // heap work per matching record.
        val best = PriorityQueue<LibraryRecord>(limit, order.reversed())
        records.values.forEach { item ->
            val matches = librarySearchMatches(searchableText(item), queryTerms) &&
                (categoryNeedle == null || normalizeLibrarySearchText(item.category) == categoryNeedle) &&
                tagNeedles.all { wanted -> item.tags.any { normalizeLibrarySearchText(it) == wanted } }
            if (!matches) return@forEach

            if (best.size < limit) {
                best.add(item)
            } else {
                val worst = best.peek()
                if (worst != null && order.compare(item, worst) < 0) {
                    best.poll()
                    best.add(item)
                }
            }
        }
        return best.toList().sortedWith(order)
    }

    /** Saves metadata only. Existing records for the same URI are intentionally updated. */
    @Synchronized
    fun upsert(record: LibraryRecord): LibraryRecord {
        val normalized = normalizeRecord(record)
        val updated = LinkedHashMap(records)
        val updatedTrash = LinkedHashMap(trashedRecords)
        updated[normalized.uri] = normalized
        // Adding the same URI again is an intentional return to the active library, not a second
        // entry with the same identity in the recycle bin.
        updatedTrash.remove(normalized.uri)
        writeSnapshot(storageFile, updated.values, updatedTrash.values)
        records = updated
        trashedRecords = updatedTrash
        return normalized
    }

    /**
     * Adds tags to multiple existing entries in one catalog transaction.
     *
     * All URIs and tags are validated before the persisted snapshot is replaced. If a URI is
     * missing, a tag is invalid, or any target would exceed the tag limit, no entry is written.
     * Returns the number of entries whose tag set actually changed.
     */
    @Synchronized
    @Throws(IOException::class)
    fun addTagsToRecords(uris: Collection<String>, tagsToAdd: List<String>): Int {
        require(uris.isNotEmpty()) { "Selecciona al menos una ficha." }
        val targets = uris.map(::validateUri).distinct()
        require(targets.size <= MAX_RECORDS) { "La selección supera el límite de fichas." }
        val additions = normalizeTags(tagsToAdd)
        require(additions.isNotEmpty()) { "Escribe al menos una etiqueta nueva." }

        val missing = targets.filterNot { it in records }
        require(missing.isEmpty()) {
            "No se puede aplicar el lote: ${missing.size} ficha(s) ya no existen en el catálogo."
        }

        val changedAt = System.currentTimeMillis().coerceAtLeast(0L)
        val changedRecords = ArrayList<LibraryRecord>()
        targets.forEach { uri ->
            val current = records.getValue(uri)
            val mergedTags = current.tags.toMutableList()
            val seen = mergedTags.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
            additions.forEach { tag ->
                if (seen.add(tag.lowercase(Locale.ROOT))) {
                    require(mergedTags.size < MAX_TAGS) {
                        "La operación excedería el límite de $MAX_TAGS etiquetas en la ficha '${current.displayName}'."
                    }
                    mergedTags.add(tag)
                }
            }
            if (mergedTags != current.tags) {
                changedRecords += normalizeRecord(
                    current.copy(tags = mergedTags, updatedAtMillis = changedAt)
                )
            }
        }

        if (changedRecords.isEmpty()) return 0
        val updated = LinkedHashMap(records)
        changedRecords.forEach { updated[it.uri] = it }
        writeSnapshot(storageFile, updated.values, trashedRecords.values)
        records = updated
        return changedRecords.size
    }

    /**
     * Assigns one existing project/collection name to several existing fichas in a single write.
     * Validation is completed before the snapshot changes, so one missing URI or invalid name
     * cannot leave a partially assigned batch.
     */
    @Synchronized
    @Throws(IOException::class)
    fun assignProjectToRecords(uris: Collection<String>, projectName: String): Int {
        require(uris.isNotEmpty()) { "Selecciona al menos una ficha." }
        val targets = uris.map(::validateUri).distinct()
        require(targets.size <= MAX_RECORDS) { "La selección supera el límite de fichas." }

        val normalizedProject = projectName.trim()
        require(normalizedProject.isNotEmpty()) { "El nombre del proyecto o colección no puede estar vacío." }
        require(
            normalizedProject.length <= MAX_PROJECT_LENGTH &&
                normalizedProject.none(Char::isISOControl)
        ) { "El nombre del proyecto supera el límite o contiene caracteres de control." }

        val missing = targets.filterNot { it in records }
        require(missing.isEmpty()) {
            "No se puede aplicar el lote: ${missing.size} ficha(s) ya no existen en el catálogo."
        }

        val changedAt = System.currentTimeMillis().coerceAtLeast(0L)
        val changedRecords = targets.mapNotNull { uri ->
            val current = records.getValue(uri)
            if (current.project == normalizedProject) null
            else normalizeRecord(current.copy(project = normalizedProject, updatedAtMillis = changedAt))
        }
        if (changedRecords.isEmpty()) return 0

        val updated = LinkedHashMap(records)
        changedRecords.forEach { updated[it.uri] = it }
        writeSnapshot(storageFile, updated.values, trashedRecords.values)
        records = updated
        return changedRecords.size
    }

    /** Moves a ficha to the recoverable metadata trash. The physical file is never touched. */
    @Synchronized
    @Throws(IOException::class)
    fun trashMetadata(uri: String): Boolean {
        val key = validateUri(uri)
        val record = records[key] ?: return false
        val updatedRecords = LinkedHashMap(records)
        val updatedTrash = LinkedHashMap(trashedRecords)
        updatedRecords.remove(key)
        updatedTrash[key] = TrashedLibraryRecord(record, System.currentTimeMillis().coerceAtLeast(0L))
        writeSnapshot(storageFile, updatedRecords.values, updatedTrash.values)
        records = updatedRecords
        trashedRecords = updatedTrash
        return true
    }

    /** Restores a trashed ficha without overwriting an active entry or touching the resource. */
    @Synchronized
    @Throws(IOException::class)
    fun restoreMetadata(uri: String): Boolean {
        val key = validateUri(uri)
        val trashed = trashedRecords[key] ?: return false
        if (key in records) {
            throw IOException("Ya existe una ficha activa con esa URI; no se sobrescribió.")
        }
        val updatedRecords = LinkedHashMap(records)
        val updatedTrash = LinkedHashMap(trashedRecords)
        updatedRecords[key] = normalizeRecord(trashed.record)
        updatedTrash.remove(key)
        writeSnapshot(storageFile, updatedRecords.values, updatedTrash.values)
        records = updatedRecords
        trashedRecords = updatedTrash
        return true
    }

    /** Permanently removes only the metadata entry from the trash, never the physical file. */
    @Synchronized
    @Throws(IOException::class)
    fun purgeTrashedMetadata(uri: String): Boolean {
        val key = validateUri(uri)
        if (key !in trashedRecords) return false
        val updatedTrash = LinkedHashMap(trashedRecords)
        updatedTrash.remove(key)
        writeSnapshot(storageFile, records.values, updatedTrash.values)
        trashedRecords = updatedTrash
        return true
    }

    /** Permanently removes an active catalog entry only; never deletes a physical file. */
    @Synchronized
    @Throws(IOException::class)
    fun removeMetadata(uri: String): Boolean {
        val key = validateUri(uri)
        if (!records.containsKey(key)) return false
        val updated = LinkedHashMap(records)
        updated.remove(key)
        writeSnapshot(storageFile, updated.values, trashedRecords.values)
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
        writeSnapshot(destination, records.values, trashedRecords.values)
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
        val importedUris = imported.records.keys + imported.trashedRecords.keys
        val existingUris = records.keys + trashedRecords.keys
        val collisions = importedUris.intersect(existingUris.toSet())
        if (collisions.isNotEmpty() && !replaceExisting) {
            throw IOException("La importación coincide con ${collisions.size} ficha(s) existente(s); no se modificó la biblioteca.")
        }

        val updatedRecords = LinkedHashMap(records)
        val updatedTrash = LinkedHashMap(trashedRecords)
        if (replaceExisting) importedUris.forEach { uri ->
            updatedRecords.remove(uri)
            updatedTrash.remove(uri)
        }
        imported.records.forEach { (uri, item) ->
            updatedRecords[uri] = item
            updatedTrash.remove(uri)
        }
        imported.trashedRecords.forEach { (uri, item) ->
            updatedRecords.remove(uri)
            updatedTrash[uri] = item
        }
        writeSnapshot(storageFile, updatedRecords.values, updatedTrash.values)
        records = updatedRecords
        trashedRecords = updatedTrash
        return importedUris.size
    }

    private fun writeSnapshot(
        destination: File,
        records: Collection<LibraryRecord>,
        trashedRecords: Collection<TrashedLibraryRecord>
    ) {
        val writer = snapshotWriter
        if (writer == null) {
            persist(destination, records, trashedRecords)
        } else {
            writer(destination, records, trashedRecords)
        }
    }

    private fun searchableText(item: LibraryRecord): String = item.normalizedSearchText

    companion object {
        private const val SCHEMA_VERSION = "3"
        private const val PREVIOUS_SCHEMA_VERSION = "2"
        private const val LEGACY_SCHEMA_VERSION = "1"
        private const val DEFAULT_RESOURCE_KIND = "Sin clasificar"
        private const val DEFAULT_RESULT_LIMIT = 500
        private const val MAX_RESULT_LIMIT = 10_000
        private const val MAX_RECORDS = 100_000
        private const val MAX_TAGS = 100
        private const val MAX_TAG_LENGTH = 64
        private const val MAX_NAME_LENGTH = 1_024
        private const val MAX_CATEGORY_LENGTH = 128
        private const val MAX_NOTES_LENGTH = 20_000
        private const val MAX_SOURCE_LENGTH = 4_096
        private const val MAX_PROJECT_LENGTH = 256
        private const val MAX_RESOURCE_KIND_LENGTH = 64
        private const val MAX_RELATED_URIS = 100

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
            val project = record.project.trim()
            val resourceKind = record.resourceKind.trim().ifEmpty { DEFAULT_RESOURCE_KIND }
            require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
                "El nombre debe contener entre 1 y $MAX_NAME_LENGTH caracteres."
            }
            require(category.isNotEmpty() && category.length <= MAX_CATEGORY_LENGTH) {
                "La categoría no es válida."
            }
            require(notes.length <= MAX_NOTES_LENGTH) { "Las notas son demasiado largas." }
            require(source.length <= MAX_SOURCE_LENGTH) { "La procedencia es demasiado larga." }
            require(project.length <= MAX_PROJECT_LENGTH) { "El nombre del proyecto es demasiado largo." }
            require(resourceKind.length <= MAX_RESOURCE_KIND_LENGTH) { "El tipo de recurso es demasiado largo." }
            require(record.relatedUris.size <= MAX_RELATED_URIS) {
                "Una ficha puede relacionar hasta $MAX_RELATED_URIS recursos."
            }
            require(record.updatedAtMillis >= 0L) { "La fecha de modificación no es válida." }
            val related = LinkedHashSet<String>()
            record.relatedUris.forEach { rawRelated ->
                val relatedUri = validateUri(rawRelated)
                if (relatedUri != uri) related.add(relatedUri)
            }
            require(related.size <= MAX_RELATED_URIS) {
                "Una ficha puede relacionar hasta $MAX_RELATED_URIS recursos distintos."
            }
            return record.copy(
                uri = uri,
                displayName = name,
                category = category,
                tags = normalizeTags(record.tags),
                notes = notes,
                source = source,
                project = project,
                resourceKind = resourceKind,
                relatedUris = related.toList()
            )
        }

        private fun loadSnapshotIfPresent(file: File): LibraryCatalogSnapshot =
            if (!file.exists()) {
                LibraryCatalogSnapshot(LinkedHashMap(), LinkedHashMap())
            } else {
                loadSnapshot(file)
            }

        @Throws(IOException::class)
        private fun loadSnapshot(file: File): LibraryCatalogSnapshot {
            if (!file.isFile || !file.canRead()) throw IOException("No se puede leer el archivo de metadatos.")
            if (file.length() > MAX_LIBRARY_SNAPSHOT_BYTES) {
                throw IOException("La instantánea supera el límite seguro de 64 MiB.")
            }
            val properties = Properties()
            try {
                file.inputStream().use(properties::load)
            } catch (error: Exception) {
                throw IOException("El archivo de metadatos está dañado o no se puede leer.", error)
            }
            val schemaVersion = properties.getProperty("schema.version")
            if (schemaVersion !in setOf(LEGACY_SCHEMA_VERSION, PREVIOUS_SCHEMA_VERSION, SCHEMA_VERSION)) {
                throw IOException("La versión del archivo de metadatos no es compatible.")
            }
            val currentFieldsAvailable = schemaVersion == PREVIOUS_SCHEMA_VERSION || schemaVersion == SCHEMA_VERSION

            fun readCount(field: String): Int {
                val raw = properties.getProperty(field)
                val count = raw?.toIntOrNull()
                    ?: throw IOException("El recuento '$field' no es válido.")
                if (count !in 0..MAX_RECORDS) throw IOException("El archivo de metadatos excede el límite permitido.")
                return count
            }

            fun readRecord(prefix: String): LibraryRecord {
                fun required(field: String): String = properties.getProperty(prefix + field)
                    ?: throw IOException("Falta el campo ${prefix + field}.")
                val tagCount = required("tag.count").toIntOrNull()
                    ?: throw IOException("La cantidad de etiquetas no es válida.")
                if (tagCount !in 0..MAX_TAGS) throw IOException("La ficha contiene demasiadas etiquetas.")
                val tags = (0 until tagCount).map { tagIndex -> required("tag.$tagIndex") }
                return normalizeRecord(
                    LibraryRecord(
                        uri = required("uri"),
                        displayName = required("displayName"),
                        category = required("category"),
                        tags = tags,
                        notes = required("notes"),
                        source = required("source"),
                        updatedAtMillis = required("updatedAtMillis").toLongOrNull()
                            ?: throw IOException("La fecha de modificación no es válida."),
                        project = if (!currentFieldsAvailable) "" else required("project"),
                        resourceKind = if (!currentFieldsAvailable) DEFAULT_RESOURCE_KIND else required("resourceKind"),
                        relatedUris = if (!currentFieldsAvailable) emptyList() else {
                            val relatedCount = required("related.count").toIntOrNull()
                                ?: throw IOException("La cantidad de recursos relacionados no es válida.")
                            if (relatedCount !in 0..MAX_RELATED_URIS) {
                                throw IOException("La ficha contiene demasiadas referencias relacionadas.")
                            }
                            (0 until relatedCount).map { relatedIndex -> required("related.$relatedIndex.uri") }
                        }
                    )
                )
            }

            val recordCount = readCount("records.count")
            val trashCount = if (schemaVersion == SCHEMA_VERSION) readCount("trash.count") else 0
            if (recordCount.toLong() + trashCount.toLong() > MAX_RECORDS) {
                throw IOException("La biblioteca y su papelera exceden el límite permitido.")
            }
            val loaded = LinkedHashMap<String, LibraryRecord>(recordCount)
            val loadedTrash = LinkedHashMap<String, TrashedLibraryRecord>(trashCount)
            try {
                repeat(recordCount) { index ->
                    val record = readRecord("record.$index.")
                    if (loaded.put(record.uri, record) != null) {
                        throw IOException("El archivo contiene fichas duplicadas para la misma URI.")
                    }
                }
                repeat(trashCount) { index ->
                    val prefix = "trash.$index."
                    val deletedAt = properties.getProperty(prefix + "deletedAtMillis")?.toLongOrNull()
                        ?: throw IOException("La fecha de eliminación de papelera no es válida.")
                    if (deletedAt < 0L) throw IOException("La fecha de eliminación de papelera no es válida.")
                    val record = readRecord(prefix + "record.")
                    if (record.uri in loaded || loadedTrash.put(record.uri, TrashedLibraryRecord(record, deletedAt)) != null) {
                        throw IOException("La instantánea contiene una URI repetida entre la biblioteca y la papelera.")
                    }
                }
            } catch (error: IllegalArgumentException) {
                throw IOException("El archivo de metadatos contiene una ficha no válida.", error)
            }
            return LibraryCatalogSnapshot(loaded, loadedTrash)
        }

        @Throws(IOException::class)
        private fun persist(
            destination: File,
            records: Collection<LibraryRecord>,
            trashedRecords: Collection<TrashedLibraryRecord>
        ) {
            if (records.size.toLong() + trashedRecords.size.toLong() > MAX_RECORDS) {
                throw IOException("La biblioteca y su papelera exceden el límite permitido.")
            }
            val normalized = records.map(::normalizeRecord)
            val normalizedTrash = trashedRecords.map { entry ->
                if (entry.deletedAtMillis < 0L) throw IOException("La fecha de eliminación de papelera no es válida.")
                TrashedLibraryRecord(normalizeRecord(entry.record), entry.deletedAtMillis)
            }
            val activeUris = normalized.map { it.uri }
            val trashUris = normalizedTrash.map { it.record.uri }
            if (activeUris.toSet().size != activeUris.size || trashUris.toSet().size != trashUris.size) {
                throw IOException("No se puede guardar una instantánea con URI duplicadas.")
            }
            if (activeUris.toSet().intersect(trashUris.toSet()).isNotEmpty()) {
                throw IOException("Una URI no puede estar a la vez en la biblioteca y en la papelera.")
            }

            val properties = Properties().apply {
                setProperty("schema.version", SCHEMA_VERSION)
                setProperty("records.count", normalized.size.toString())
                normalized.forEachIndexed { index, item ->
                    writeRecord(this, "record.$index.", item)
                }
                setProperty("trash.count", normalizedTrash.size.toString())
                normalizedTrash.forEachIndexed { index, entry ->
                    val prefix = "trash.$index."
                    setProperty(prefix + "deletedAtMillis", entry.deletedAtMillis.toString())
                    writeRecord(this, prefix + "record.", entry.record)
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
                if (temporary.toFile().length() > MAX_LIBRARY_SNAPSHOT_BYTES) {
                    throw IOException("La instantánea supera el límite seguro de 64 MiB.")
                }
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

        private fun writeRecord(properties: Properties, prefix: String, item: LibraryRecord) {
            properties.setProperty(prefix + "uri", item.uri)
            properties.setProperty(prefix + "displayName", item.displayName)
            properties.setProperty(prefix + "category", item.category)
            properties.setProperty(prefix + "notes", item.notes)
            properties.setProperty(prefix + "source", item.source)
            properties.setProperty(prefix + "updatedAtMillis", item.updatedAtMillis.toString())
            properties.setProperty(prefix + "project", item.project)
            properties.setProperty(prefix + "resourceKind", item.resourceKind)
            properties.setProperty(prefix + "related.count", item.relatedUris.size.toString())
            item.relatedUris.forEachIndexed { relatedIndex, relatedUri ->
                properties.setProperty(prefix + "related.$relatedIndex.uri", relatedUri)
            }
            properties.setProperty(prefix + "tag.count", item.tags.size.toString())
            item.tags.forEachIndexed { tagIndex, tag ->
                properties.setProperty(prefix + "tag.$tagIndex", tag)
            }
        }

        private fun sameFile(first: File, second: File): Boolean =
            runCatching { first.canonicalFile == second.canonicalFile }
                .getOrDefault(first.absoluteFile == second.absoluteFile)
    }
}
