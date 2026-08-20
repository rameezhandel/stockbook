import Foundation
import UniformTypeIdentifiers
import SwiftUI

/// Reading and writing the backup file.
///
/// The prototype faked the file layer; this is the real thing. Export writes a
/// dated JSON file and hands it to the OS (share sheet or Files); import reads
/// one back, **validates version and shape before the owner is asked to
/// confirm**, and only then replaces the database.
enum BackupService {

    // MARK: Encoding

    static func encoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    static func decoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    static func encode(_ document: BackupDocument) throws -> Data {
        try encoder().encode(document)
    }

    /// Decodes and validates. Version is checked *first*: a file from a future
    /// build may decode cleanly into the current shape while meaning something
    /// different, which is the failure mode worth being paranoid about when the
    /// result is a destructive whole-database replace.
    static func decode(_ data: Data) throws -> BackupDocument {
        struct VersionProbe: Decodable { let version: Int }

        guard let probe = try? decoder().decode(VersionProbe.self, from: data) else {
            throw BackupError.notStockbookData
        }
        guard probe.version <= BackupDocument.currentVersion else {
            throw BackupError.newerVersion(found: probe.version)
        }
        guard let document = try? decoder().decode(BackupDocument.self, from: data) else {
            throw BackupError.notStockbookData
        }
        return document
    }

    // MARK: Files

    /// Writes the archive to a temporary file named `stockbook-YYYY-MM-DD.zip`,
    /// ready to be handed to a share sheet or `.fileExporter`.
    ///
    /// The pictures are read from disk one at a time as the writer asks for
    /// them, so a shop with two hundred of them never holds more than one.
    static func writeToTemporaryFile(_ document: BackupDocument, photos: PhotoStore = PhotoStore()) throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(document.suggestedFilename)
        let archive = try BackupArchive.pack(document) { photos.bytes(id: $0) }
        try archive.write(to: url, options: .atomic)
        return url
    }

    /// Reads a file the owner picked with the document picker.
    ///
    /// Picked URLs live outside the app's sandbox, so access has to be scoped —
    /// forgetting this is the classic way an import silently fails on device
    /// while working fine in the simulator.
    static func read(from url: URL) throws -> BackupDocument {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let data = try? Data(contentsOf: url) else {
            throw BackupError.unreadable
        }
        // Either shape: an archive with the pictures in it, or a bare `.json`
        // from before they travelled. Decided by the first four bytes, not by
        // the extension — the document picker and the Files app both lie about
        // types.
        return try BackupArchive.document(data)
    }

    /// A rough on-disk size for the file chip in Settings ("8 products · 4 bills · 2 KB").
    /// Returns the number; the unit is `Strings`' business.
    static func sizeInKilobytes(of document: BackupDocument) -> Int {
        let bytes = (try? encode(document).count) ?? 0
        return max(1, Int((Double(bytes) / 1024).rounded()))
    }
}

/// The exported file, as a `FileDocument`, so `.fileExporter` can offer
/// "Save to Files" — which is what the Settings copy promises ("Written to Files").
struct BackupFile: FileDocument {
    /// Both, because both import: the archive this build writes, and the bare
    /// JSON written before the photographs travelled.
    static var readableContentTypes: [UTType] { [.zip, .json] }

    /// Written as an archive, always. One thing to export means one name for it.
    static var writableContentTypes: [UTType] { [.zip] }

    var document: BackupDocument

    init(document: BackupDocument) {
        self.document = document
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw BackupError.unreadable
        }
        document = try BackupArchive.document(data)
    }

    /// The pictures are found here rather than carried in: `PhotoStore` holds
    /// nothing but a directory URL, and one built at write time is guaranteed to
    /// be looking at the current app container — which is the whole reason that
    /// type rebuilds its paths every call rather than storing them.
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let photos = PhotoStore()
        let archive = try BackupArchive.pack(document) { photos.bytes(id: $0) }
        return FileWrapper(regularFileWithContents: archive)
    }
}
