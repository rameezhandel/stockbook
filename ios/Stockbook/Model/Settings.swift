import Foundation

/// The shop's own settings. One of these exists, always.
struct Settings: Codable, Equatable {

    /// The business owner's name, asked for as step 1 of setup and shown as
    /// "Hello, <first name>" on the dashboard.
    var ownerName: String = ""

    /// `"SAR "` by default, including the trailing space.
    var currencySymbol: String = Money.defaultSymbol

    /// Stock at or below this count reads as "running low".
    var lowStockAt: Int = 40

    /// The interface language. Chosen in Settings, never inferred from the
    /// phone: the shop owner and the phone's owner are not always the same
    /// person, and a shop that reads Kannada should not switch to English
    /// because someone changed a system setting.
    ///
    /// Decoded with a default so a backup written before this existed still
    /// reads.
    var language: AppLanguage = .english

    /// When the owner last wrote a backup file. `nil` keeps the Today nudge
    /// shouting — with no server, a file is the only thing between this shop and
    /// a dropped phone.
    var lastExportAt: Date?

    /// First-run setup runs until this is true.
    var setupCompleted: Bool = false

    /// Next value for `Bill.number`.
    var nextBillNumber: Int = 1

    var hasBackup: Bool { lastExportAt != nil }

    init() {}

    /// Written by hand, because the synthesised one is wrong for a file already
    /// sitting on somebody's phone.
    ///
    /// A default value on a property does **not** make the synthesised decoder
    /// tolerate a missing key — it throws. Every field added after v1 shipped is
    /// therefore a field that would refuse to read an existing shop, so all of
    /// them are decoded as "if present, else the default". `language` is the
    /// first; the rest are here so the next one is a one-line change.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let fallback = Settings()
        ownerName = try container.decodeIfPresent(String.self, forKey: .ownerName) ?? fallback.ownerName
        currencySymbol = try container.decodeIfPresent(String.self, forKey: .currencySymbol) ?? fallback.currencySymbol
        lowStockAt = try container.decodeIfPresent(Int.self, forKey: .lowStockAt) ?? fallback.lowStockAt
        language = try container.decodeIfPresent(AppLanguage.self, forKey: .language) ?? fallback.language
        lastExportAt = try container.decodeIfPresent(Date.self, forKey: .lastExportAt)
        setupCompleted = try container.decodeIfPresent(Bool.self, forKey: .setupCompleted) ?? fallback.setupCompleted
        nextBillNumber = try container.decodeIfPresent(Int.self, forKey: .nextBillNumber) ?? fallback.nextBillNumber
    }
}

/// Everything the app persists, in one value.
///
/// The whole shop fits comfortably in memory — the handoff pins the catalogue at
/// 50–300 products — which is what makes a value-typed domain and a swappable
/// repository affordable here. At a hundred times the size this would be the
/// wrong shape.
struct ShopState: Codable, Equatable {
    var products: [Product] = []
    var bills: [Bill] = []
    var settings: Settings = Settings()

    static let empty = ShopState()
}
