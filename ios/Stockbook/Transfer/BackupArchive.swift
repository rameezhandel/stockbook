import Foundation

/// The backup file as it actually leaves the phone: the document, plus every
/// photograph the document names.
///
/// ```
/// stockbook-2026-08-20.zip
/// ├── stockbook.json      ← byte-identical to what BackupService writes
/// └── photos/<id>.jpg
/// ```
///
/// The JSON entry is untouched, which is the point. Every existing test of the
/// format still tests the format, the cross-platform byte guarantee still holds,
/// and a bare `.json` from before this existed still imports — the reader sniffs
/// the `PK` magic bytes rather than trusting a file extension, because the
/// document picker and the Files app both lie about types.
///
/// Photograph ids were already in the document. Nothing about the data has to
/// migrate: a bill has always named its pictures, and this is the first version
/// where the pictures travel with the name.
///
/// The twin of `BackupArchive.kt`, and it must stay one.
enum BackupArchive {

    /// The document's entry. Named for the app rather than for the format.
    static let documentEntry = "stockbook.json"

    private static let photoFolder = "photos/"
    private static let photoSuffix = ".jpg"

    /// Where a photograph with this id lives inside the archive.
    static func photoEntry(_ id: String) -> String { "\(photoFolder)\(id)\(photoSuffix)" }

    /// The id back out of an entry name, or nil for anything else in there.
    ///
    /// Anything else is not an error. A future version may add a file beside
    /// these, and a reader that threw on the first thing it did not recognise
    /// would make that impossible — the version number is where incompatibility
    /// gets declared, not the file list.
    static func photoID(_ entryName: String) -> String? {
        guard entryName.hasPrefix(photoFolder), entryName.hasSuffix(photoSuffix) else { return nil }
        let id = String(entryName.dropFirst(photoFolder.count).dropLast(photoSuffix.count))
        // A name with a further slash in it is a directory we did not write, and
        // an empty one is `photos/.jpg`. Neither is an id.
        guard !id.isEmpty, !id.contains("/") else { return nil }
        return id
    }

    /// Every photograph id the document mentions, in the order the bills do and
    /// with no id twice.
    ///
    /// Read off the document rather than passed in, so the archive cannot
    /// disagree with the file it contains.
    static func photoIDs(in document: BackupDocument) -> [String] {
        var seen = Set<String>()
        return document.bills.flatMap { $0.photoIDs ?? [] }.filter { seen.insert($0).inserted }
    }

    /// Packs the document and its photographs into one archive.
    ///
    /// `photo` is asked for one id at a time and may answer nil — a picture the
    /// phone no longer holds is skipped rather than fatal. That is the same
    /// asymmetry the photo store already lives by: an id whose file is missing is
    /// never pruned from the book, because the file may yet arrive.
    static func pack(_ document: BackupDocument, photo: (String) -> Data?) throws -> Data {
        let json = try BackupService.encode(document)
        let ids = photoIDs(in: document)
        // Asked for one at a time, so each photograph is read from disk only when
        // the writer reaches it and is free again immediately after.
        return ZipArchive.write(count: ids.count + 1) { index in
            if index == 0 { return ZipArchive.Entry(name: documentEntry, bytes: json) }
            let id = ids[index - 1]
            guard let bytes = photo(id) else { return nil }
            return ZipArchive.Entry(name: photoEntry(id), bytes: bytes)
        }
    }

    /// The document out of either an archive or a bare JSON file.
    ///
    /// Reads nothing else. The import screen shows the owner what they are about
    /// to replace their shop with *before* they agree to it, and unpacking a
    /// hundred photographs to draw that summary would be work for a decision that
    /// may well be "cancel".
    static func document(_ bytes: Data) throws -> BackupDocument {
        guard ZipArchive.looksLikeZip(bytes) else {
            // A backup written before photographs travelled, or one somebody
            // pulled out of an archive by hand. Still ours, still readable.
            return try BackupService.decode(bytes)
        }

        // `try?` on a function that already returns an optional gives a double
        // optional; flattening it here rather than in the `guard` keeps what is
        // being tested legible.
        let found = (try? ZipArchive.entry(bytes, named: documentEntry)) ?? nil
        guard let json = found else { throw BackupError.notStockbookData }
        return try BackupService.decode(json)
    }

    /// Hands over each photograph in the archive, one at a time.
    ///
    /// Called **after** the owner has agreed to the import, never before: a book
    /// they look at and cancel must not leave pictures scattered across the phone
    /// that nothing in the shop refers to.
    ///
    /// Best effort by design. A damaged picture costs that picture and the ones
    /// after it, never the book — the bill keeps the id either way, so it can be
    /// re-adopted from another copy of the archive later. That is the same
    /// one-way rule the photo store's sweep already lives by.
    static func photos(_ bytes: Data, onPhoto: (String, Data) -> Void) {
        guard ZipArchive.looksLikeZip(bytes) else { return }
        try? ZipArchive.forEach(bytes) { name, data in
            if let id = photoID(name) { onPhoto(id, data) }
        }
    }

    /// Both halves in order, for the tests and for anything that has already
    /// decided to import.
    @discardableResult
    static func unpack(_ bytes: Data, onPhoto: (String, Data) -> Void) throws -> BackupDocument {
        let found = try document(bytes)
        photos(bytes, onPhoto: onPhoto)
        return found
    }
}
