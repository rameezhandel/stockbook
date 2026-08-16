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

    /// When the owner last wrote a backup file. `nil` keeps the Today nudge
    /// shouting — with no server, a file is the only thing between this shop and
    /// a dropped phone.
    var lastExportAt: Date?

    /// First-run setup runs until this is true.
    var setupCompleted: Bool = false

    /// Next value for `Bill.number`.
    var nextBillNumber: Int = 1

    var hasBackup: Bool { lastExportAt != nil }
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
