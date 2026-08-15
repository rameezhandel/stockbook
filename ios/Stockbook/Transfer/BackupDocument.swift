import Foundation

/// The export/import file format.
///
/// This is the *only* way data moves between phones, so it is a real,
/// versioned, self-describing document rather than an implementation detail:
/// one JSON file holding the whole database.
///
/// Compatibility rules, in order of importance:
/// 1. Never repurpose a key. Add new ones and give them defaults.
/// 2. Bump `version` when a reader written against the old shape would
///    misinterpret the new one, and reject unknown-but-higher versions on import
///    rather than guessing.
/// 3. Products carry a `uid` so bill lines can point at them across devices.
struct BackupDocument: Codable, Equatable {

    /// The format this build writes.
    static let currentVersion = 1

    var version: Int = BackupDocument.currentVersion
    var exportedAt: Date
    var ownerName: String
    var currencySymbol: String
    var products: [ProductRecord]
    var bills: [BillRecord]

    struct ProductRecord: Codable, Equatable {
        var uid: UUID
        var name: String
        var stock: Int
        var cost: Double
        var price: Double
    }

    struct BillRecord: Codable, Equatable {
        var number: Int
        var createdAt: Date
        var total: Double
        /// Absent for a bill paid in full.
        var paid: Double?
        var who: String
        var voided: Bool
        var lines: [LineRecord]
    }

    struct LineRecord: Codable, Equatable {
        /// Absent when the product had already been deleted at export time.
        var productUID: UUID?
        var name: String
        var qty: Int
        var price: Double
    }

    // MARK: Summaries shown in the UI

    /// `Khalid Al-Amri · 8 products · 4 bills · saved 28 July 2026`
    var summaryLine: String {
        [
            ownerName,
            Copy.count(products.count, "product"),
            Copy.count(bills.count, "bill"),
            "saved " + Copy.longDate(exportedAt)
        ].joined(separator: " · ")
    }

    /// `stockbook-2026-08-11.json`
    var suggestedFilename: String {
        "stockbook-\(Copy.fileDate(exportedAt)).json"
    }
}

/// Everything that can go wrong reading a file the owner picked. Each case
/// carries enough to say something true to the owner — "that is not a Stockbook
/// file" reads very differently from "that file is from a newer version".
enum BackupError: LocalizedError, Equatable {
    case unreadable
    case notStockbookData
    case newerVersion(found: Int)

    var errorDescription: String? {
        switch self {
        case .unreadable:
            "That file could not be opened."
        case .notStockbookData:
            "That is not a Stockbook backup file."
        case .newerVersion(let found):
            "That backup was written by a newer version of Stockbook (format \(found)). Update this phone first."
        }
    }
}
