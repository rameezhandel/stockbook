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
    /// **Three**.
    ///
    /// It reached 3 once before during development — a bump when payments
    /// arrived, another for opening balances — and was reset, because nothing had
    /// shipped and those numbers described files that exist nowhere.
    ///
    /// Rule 2 is what put it back. Suppliers, purchases and money paid out
    /// arrived after 1, and a reader that ignored them would tell the owner the
    /// shop owes nobody anything — that was 2. Credit notes are the same failure
    /// in the other direction: a reader that dropped them would show every
    /// credited customer owing more than they do, and the owner would go and ask
    /// for money that was written off weeks ago. Better that build refuses the
    /// file and says so.
    ///
    /// Two things added alongside them did **not** bump it. Invoice numbers: a
    /// reader that ignores them shows "Bill #7" where the owner wrote "1024".
    /// The shop address: a statement prints without one. Both are a label lost
    /// rather than a figure misread, and the rule is about meaning.
    static let currentVersion = 3

    var version: Int = BackupDocument.currentVersion
    var exportedAt: Date
    var ownerName: String
    /// The shop's printed address.
    ///
    /// Optional **so that a file without the key still reads**, which is the one
    /// tolerance the customer/payment rows below deliberately do not have. The
    /// difference is what a missing key would mean: no `customers` key marks a
    /// file this app did not write, whereas no address is simply a shop that had
    /// not typed one. Swift omits a nil optional on the way out, matching
    /// Kotlin's `explicitNulls = false`, and Kotlin reads a missing key as its
    /// `""` default — so neither build needs the other to have written it.
    var shopAddress: String?
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

    /// What has been credited back to customers.
    ///
    /// Read through the hand-written decoder below rather than the synthesised
    /// one, because unlike every array above it this key **can** be legitimately
    /// missing: a version-2 file predates credit notes entirely, and version 2 is
    /// older than this build rather than newer, so it is accepted on import.
    var creditNotes: [CreditNoteRow] = []

    struct CreditNoteRow: Codable, Equatable {
        var id: UUID
        var customerKey: String
        var total: Double
        /// The number the owner wrote on the paper note, on its own series.
        var noteNo: String?
        var reason: String?
        var issuedAt: Date
        /// What came back, empty on a note that is only a figure.
        var lines: [LineRecord] = []
    }

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
        /// The number on the receipt, absent where the shop wrote none.
        ///
        /// Optional, so the synthesised decoder reaches for `decodeIfPresent`
        /// and a file written before receipts were numbered still reads. That
        /// is *only* true of optionals — a defaulted non-optional would throw,
        /// which is how the credit-note array once broke every older file.
        var paymentNo: String?
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
        /// Absent on a supplier bill that named no product.
        var name: String?
        var qty: Int
        var unitCost: Double
        var total: Double
        /// Absent for a delivery settled on the spot, exactly as on a bill.
        var paid: Double?
        /// The number on the supplier's invoice.
        var invoiceNo: String?
        var createdAt: Date

        /// Spelled out, unlike its five sibling rows, only because the decoder
        /// below is: any initialiser written in a struct's own body suppresses
        /// the memberwise one, and `makeBackupDocument` builds these by hand.
        init(
            id: UUID,
            supplierKey: String,
            productUID: UUID? = nil,
            name: String? = nil,
            qty: Int = 0,
            unitCost: Double = 0,
            total: Double,
            paid: Double? = nil,
            invoiceNo: String? = nil,
            createdAt: Date
        ) {
            self.id = id
            self.supplierKey = supplierKey
            self.productUID = productUID
            self.name = name
            self.qty = qty
            self.unitCost = unitCost
            self.total = total
            self.paid = paid
            self.invoiceNo = invoiceNo
            self.createdAt = createdAt
        }

        /// The one row that reads its own keys, because it is the one row whose
        /// keys can be missing from a file this app did not write.
        ///
        /// A supplier bill entered as a figure carries no product, so `name` is
        /// absent and `qty` and `unitCost` are nothing. Kotlin's decoder falls
        /// back to the declared default for each; Swift's synthesised one throws
        /// on the missing key however the property is defaulted. A throw here
        /// does not lose a field, it loses the file: `BackupService.decode`
        /// turns any decoding failure into `.notStockbookData`, and the owner is
        /// told the backup they are holding is not a Stockbook file.
        ///
        /// The rest of the document is deliberately *not* tolerant: a missing
        /// `customers` or `purchases` array means a file this app did not write,
        /// and refusing it is the right answer. This is about keys inside a row
        /// that is genuinely there.
        ///
        /// The `voided` key an older build wrote here is read by neither side any
        /// more. An unknown key is ignored rather than refused, so a backup taken
        /// before voiding was removed still imports.
        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(UUID.self, forKey: .id)
            supplierKey = try container.decode(String.self, forKey: .supplierKey)
            productUID = try container.decodeIfPresent(UUID.self, forKey: .productUID)
            name = try container.decodeIfPresent(String.self, forKey: .name)
            qty = try container.decodeIfPresent(Int.self, forKey: .qty) ?? 0
            unitCost = try container.decodeIfPresent(Double.self, forKey: .unitCost) ?? 0
            total = try container.decode(Double.self, forKey: .total)
            paid = try container.decodeIfPresent(Double.self, forKey: .paid)
            invoiceNo = try container.decodeIfPresent(String.self, forKey: .invoiceNo)
            createdAt = try container.decode(Date.self, forKey: .createdAt)
        }
    }

    struct SupplierPaymentRow: Codable, Equatable {
        var id: UUID
        var supplierKey: String
        var amount: Double
        /// The number on the receipt, absent where the shop wrote none.
        var paymentNo: String?
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
        /// The number on the paper bill. Absent when the shop wrote none.
        var invoiceNo: String?
        /// Photographs of that paper, by id — the references, not the pictures.
        ///
        /// This file carries no image bytes. Written anyway, because an id that
        /// survives the crossing is what lets a bill re-adopt its photograph the
        /// day the pictures travel too; dropping it here would make that
        /// impossible after the fact.
        ///
        /// Optional rather than a defaulted array, so the synthesised decoder
        /// reaches for `decodeIfPresent` and every file written before
        /// photographs still reads. A defaulted non-optional array would throw —
        /// which is exactly how the credit-note array once broke every older
        /// backup.
        var photoIDs: [String]?
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
        "stockbook-\(Copy.fileDate(exportedAt)).zip"
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

extension BackupDocument {

    /// Written by hand for exactly one key.
    ///
    /// Swift's synthesised decoder throws on a missing key however the property
    /// is defaulted, and for most of this document that is precisely right: no
    /// `customers` or `purchases` array means a file this app did not write, and
    /// refusing it is the correct answer. `creditNotes` is the exception. It
    /// arrived with version 3, a version-2 file has no such key, and version 2 is
    /// *older* than this build — so it decodes rather than being rejected, and
    /// throwing here would have made every backup taken before credit notes
    /// unreadable. The test that caught this decodes a literal version-2 file.
    ///
    /// Everything else keeps the strictness it had, spelled out rather than
    /// inherited, so the next person adding a key has to decide which kind it is.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        version = try container.decode(Int.self, forKey: .version)
        exportedAt = try container.decode(Date.self, forKey: .exportedAt)
        ownerName = try container.decode(String.self, forKey: .ownerName)
        shopAddress = try container.decodeIfPresent(String.self, forKey: .shopAddress)
        currencyCode = try container.decode(String.self, forKey: .currencyCode)
        products = try container.decode([ProductRecord].self, forKey: .products)
        bills = try container.decode([BillRecord].self, forKey: .bills)
        customers = try container.decode([CustomerRecordRow].self, forKey: .customers)
        payments = try container.decode([PaymentRow].self, forKey: .payments)
        suppliers = try container.decode([SupplierRecordRow].self, forKey: .suppliers)
        purchases = try container.decode([PurchaseRow].self, forKey: .purchases)
        supplierPayments = try container.decode([SupplierPaymentRow].self, forKey: .supplierPayments)
        creditNotes = try container.decodeIfPresent([CreditNoteRow].self, forKey: .creditNotes) ?? []
    }
}
