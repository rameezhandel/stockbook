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
    ///
    /// **One**, and the first one there has ever been. It reached 3 during
    /// development — a bump when payments arrived, another for opening balances —
    /// but nothing had shipped, so those numbers described files that exist
    /// nowhere. Carrying them forward would have meant three shapes of history to
    /// keep readable, all of them imaginary.
    ///
    /// It is still a version, and rule 2 still stands — and **2 is the rule being
    /// applied**, not abandoned. Suppliers, purchases and money paid out arrived
    /// after 1, and a reader that ignored them would not merely lose an address
    /// book: it would read this file and tell the owner the shop owes nobody
    /// anything. That is the payments case again, and the answer is the same one.
    /// Better that build refuses the file and says so.
    static let currentVersion = 2

    var version: Int = BackupDocument.currentVersion
    var exportedAt: Date
    var ownerName: String
    /// ISO 4217, and the only thing that says what the numbers in this file mean.
    var currencyCode: String
    var products: [ProductRecord]
    var bills: [BillRecord]

    /// Always written, empty or not — so `[]` in a file means a shop with no
    /// roster, and a *missing* key means a file this app did not write.
    ///
    /// The `= []` is the value for a document built in code, not a decoding
    /// fallback: Swift's synthesised decoder throws on a missing key whatever the
    /// property defaults to, which is precisely the answer wanted here. Kotlin's
    /// is tolerant by construction and would read such a file as an empty roster;
    /// the asymmetry is harmless while both builds write every key, and worth
    /// knowing about the day one of them stops.
    var customers: [CustomerRecordRow] = []
    var payments: [PaymentRow] = []

    /// The supplier roster, and the money going the other way.
    var suppliers: [SupplierRecordRow] = []
    var purchases: [PurchaseRow] = []
    var supplierPayments: [SupplierPaymentRow] = []

    struct CustomerRecordRow: Codable, Equatable {
        /// Written out rather than re-derived on import, so a future change to the
        /// keying rule cannot silently re-file everybody's history.
        var key: String
        var name: String
        var phone: String?
        var place: String?
        /// Carried over from the paper book, and zero for anyone who was not.
        var openingBalance: Double
        var createdAt: Date
    }

    struct PaymentRow: Codable, Equatable {
        var id: UUID
        var customerKey: String
        var amount: Double
        var receivedAt: Date
        var note: String?
    }

    struct SupplierRecordRow: Codable, Equatable {
        var key: String
        var name: String
        var phone: String?
        var place: String?
        var openingBalance: Double
        var createdAt: Date
    }

    struct PurchaseRow: Codable, Equatable {
        var id: UUID
        var supplierKey: String
        var productUID: UUID?
        var name: String
        var qty: Int
        var unitCost: Double
        var total: Double
        /// Absent for a delivery settled on the spot, exactly as on a bill.
        var paid: Double?
        var createdAt: Date
        var voided: Bool
    }

    struct SupplierPaymentRow: Codable, Equatable {
        var id: UUID
        var supplierKey: String
        var amount: Double
        var paidAt: Date
        var note: String?
    }

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
    func summaryLine(_ strings: Strings) -> String {
        [
            ownerName,
            strings.products(products.count),
            strings.bills(bills.count),
            strings.savedOn(strings.longDate(exportedAt))
        ].joined(separator: " · ")
    }

    /// `stockbook-2026-08-11.json`
    var suggestedFilename: String {
        "stockbook-\(Copy.fileDate(exportedAt)).json"
    }
}

/// Everything that can go wrong reading a file the owner picked. Each case
/// carries enough to say something true to the owner — "that is not a Stockbook
/// file" reads very differently from "that file is from a newer version". The
/// sentences themselves live in `Strings.backupError`, so they can be said in
/// either language.
enum BackupError: Error, Equatable {
    case unreadable
    case notStockbookData
    case newerVersion(found: Int)
}
