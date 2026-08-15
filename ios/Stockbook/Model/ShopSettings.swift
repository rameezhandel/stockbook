import Foundation
import SwiftData

/// The single settings row. Fetched — or created on first launch — by
/// `StockbookStore.settings()`; nothing else should instantiate one.
@Model
final class ShopSettings {

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

    init() {}

    var hasBackup: Bool { lastExportAt != nil }
}
