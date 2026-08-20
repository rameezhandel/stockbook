package com.stockbook.core.transfer

/**
 * The backup file as it actually leaves the phone: the document, plus every
 * photograph the document names.
 *
 * ```
 * stockbook-2026-08-20.zip
 * ├── stockbook.json      ← byte-identical to what BackupService writes
 * └── photos/<id>.jpg
 * ```
 *
 * The JSON entry is untouched, which is the point. Every existing test of the
 * format still tests the format, the cross-platform byte guarantee still holds,
 * and a bare `.json` from before this existed still imports — the reader sniffs
 * the `PK` magic bytes rather than trusting a file extension, because the
 * document picker and the Files app both lie about types.
 *
 * Photograph ids were already in the document. Nothing about the data has to
 * migrate: a bill has always named its pictures, and this is the first version
 * where the pictures travel with the name.
 */
object BackupArchive {

    /** The document's entry. Named for the app rather than for the format. */
    const val documentEntry = "stockbook.json"

    private const val photoFolder = "photos/"
    private const val photoSuffix = ".jpg"

    /** Where a photograph with this id lives inside the archive. */
    fun photoEntry(id: String): String = "$photoFolder$id$photoSuffix"

    /**
     * The id back out of an entry name, or null for anything else in there.
     *
     * Anything else is not an error. A future version may add a file beside
     * these, and a reader that threw on the first thing it did not recognise
     * would make that impossible — the version number is where incompatibility
     * gets declared, not the file list.
     */
    fun photoID(entryName: String): String? {
        if (!entryName.startsWith(photoFolder) || !entryName.endsWith(photoSuffix)) return null
        val id = entryName.substring(photoFolder.length, entryName.length - photoSuffix.length)
        // A name with a further slash in it is a directory we did not write, and
        // an empty one is `photos/.jpg`. Neither is an id.
        return id.takeIf { it.isNotEmpty() && !it.contains('/') }
    }

    /**
     * Every photograph id the document mentions, in the order the bills do and
     * with no id twice.
     *
     * Read off the document rather than passed in, so the archive cannot
     * disagree with the file it contains.
     */
    fun photoIDs(document: BackupDocument): List<String> =
        document.bills.flatMap { it.photoIds.orEmpty() }.distinct()

    /**
     * Packs the document and its photographs into one archive.
     *
     * [photo] is asked for one id at a time and may answer null — a picture the
     * phone no longer holds is skipped rather than fatal. That is the same
     * asymmetry the photo store already lives by: an id whose file is missing is
     * never pruned from the book, because the file may yet arrive.
     */
    fun pack(document: BackupDocument, photo: (String) -> ByteArray?): ByteArray {
        val json = ZipArchive.Entry(documentEntry, BackupService.encode(document).toByteArray(Charsets.UTF_8))
        // A sequence, so each photograph is read from disk only when the writer
        // reaches it and is free again immediately after.
        val pictures = photoIDs(document).asSequence().mapNotNull { id ->
            photo(id)?.let { ZipArchive.Entry(photoEntry(id), it) }
        }
        return ZipArchive.write(sequenceOf(json) + pictures)
    }

    /**
     * The document out of either an archive or a bare JSON file.
     *
     * Reads nothing else. The import screen shows the owner what they are about
     * to replace their shop with *before* they agree to it, and unpacking a
     * hundred photographs to draw that summary would be work for a decision that
     * may well be "cancel".
     */
    fun document(bytes: ByteArray): BackupDocument {
        if (!ZipArchive.looksLikeZip(bytes)) {
            // A backup written before photographs travelled, or one somebody
            // pulled out of an archive by hand. Still ours, still readable.
            return BackupService.decode(bytes.toString(Charsets.UTF_8))
        }

        val json = runCatching { ZipArchive.entry(bytes, documentEntry) }
            .getOrElse { throw BackupError.NotStockbookData }
            ?: throw BackupError.NotStockbookData
        return BackupService.decode(json.toString(Charsets.UTF_8))
    }

    /**
     * Hands over each photograph in the archive, one at a time.
     *
     * Called **after** the owner has agreed to the import, never before: a book
     * they look at and cancel must not leave pictures scattered across the phone
     * that nothing in the shop refers to.
     *
     * Best effort by design. A damaged picture costs that picture and the ones
     * after it, never the book — the bill keeps the id either way, so it can be
     * re-adopted from another copy of the archive later. That is the same
     * one-way rule the photo store's sweep already lives by.
     */
    fun photos(bytes: ByteArray, onPhoto: (id: String, data: ByteArray) -> Unit) {
        if (!ZipArchive.looksLikeZip(bytes)) return
        runCatching {
            ZipArchive.forEach(bytes) { name, data ->
                photoID(name)?.let { onPhoto(it, data) }
            }
        }
    }

    /**
     * Both halves in order, for the tests and for anything that has already
     * decided to import.
     */
    fun unpack(bytes: ByteArray, onPhoto: (id: String, data: ByteArray) -> Unit): BackupDocument {
        val document = document(bytes)
        photos(bytes, onPhoto)
        return document
    }
}
