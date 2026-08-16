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

    /// Writes the document to a temporary file named `stockbook-YYYY-MM-DD.json`,
    /// ready to be handed to a share sheet or `.fileExporter`.
    static func writeToTemporaryFile(_ document: BackupDocument) throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(document.suggestedFilename)
        try encode(document).write(to: url, options: .atomic)
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
        return try decode(data)
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
    static var readableContentTypes: [UTType] { [.json] }

    var document: BackupDocument

    init(document: BackupDocument) {
        self.document = document
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else {
            throw BackupError.unreadable
        }
        document = try BackupService.decode(data)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: try BackupService.encode(document))
    }
}
