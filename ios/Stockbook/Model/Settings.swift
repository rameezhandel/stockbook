import Foundation

/// The shop's own settings. One of these exists, always.
struct Settings: Codable, Equatable {

    /// The business owner's name, asked for as step 1 of setup and shown as
    /// "Hello, <first name>" on the dashboard.
    var ownerName: String = ""

    /// ISO 4217 code of the one currency the shop bills in, chosen during setup
    /// and changeable in Settings. Stored as the code rather than the symbol so
    /// a wrong symbol is a one-line fix here instead of a migration out of
    /// everyone's saved settings.
    var currencyCode: String = Currency.default.code

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

    /// Dark or light, chosen in Settings and never inferred from the phone —
    /// same reasoning as the language, and the same shape.
    var theme: AppTheme = .default

    /// When the owner last wrote a backup file. `nil` keeps the Today nudge
    /// shouting — with no server, a file is the only thing between this shop and
    /// a dropped phone.
    var lastExportAt: Date?

    /// First-run setup runs until this is true.
    var setupCompleted: Bool = false

    /// Next value for `Bill.number`.
    var nextBillNumber: Int = 1

    var currency: Currency { Currency.named(currencyCode) }

    var hasBackup: Bool { lastExportAt != nil }

    init() {}

    /// Written by hand, because the synthesised one refuses a file that is merely
    /// older than the code reading it.
    ///
    /// A default value on a property does **not** make the synthesised decoder
    /// tolerate a missing key — it throws. That is what makes this decoder the
    /// difference between adding a field and shipping a migration: every field
    /// here is read as "if present, else the default", so the next one — a credit
    /// limit, a supplier, whatever the purchases work needs — can be added
    /// without the shop already on somebody's phone becoming unreadable.
    ///
    /// It is not a compatibility shim for the past. There is no past — nothing has
    /// shipped — and every path that existed to read one has gone, along with the
    /// format versions that described it.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let fallback = Settings()
        ownerName = try container.decodeIfPresent(String.self, forKey: .ownerName) ?? fallback.ownerName
        lowStockAt = try container.decodeIfPresent(Int.self, forKey: .lowStockAt) ?? fallback.lowStockAt
        currencyCode = try container.decodeIfPresent(String.self, forKey: .currencyCode) ?? fallback.currencyCode
        language = try container.decodeIfPresent(AppLanguage.self, forKey: .language) ?? fallback.language
        theme = try container.decodeIfPresent(AppTheme.self, forKey: .theme) ?? fallback.theme
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

    /// The customer roster: typed-in facts only. Figures are derived from
    /// `bills` and `payments` every time they are asked for.
    var customers: [CustomerRecord] = []

    /// Money received after the bill was written.
    var payments: [Payment] = []

    var settings: Settings = Settings()

    static let empty = ShopState()

    init(
        products: [Product] = [],
        bills: [Bill] = [],
        customers: [CustomerRecord] = [],
        payments: [Payment] = [],
        settings: Settings = Settings()
    ) {
        self.products = products
        self.bills = bills
        self.customers = customers
        self.payments = payments
        self.settings = settings
    }

    /// Written by hand for the same reason `Settings` is, and it matters more
    /// here: this is the whole shop.
    ///
    /// A default value does **not** make the synthesised decoder tolerate a
    /// missing key — it throws. `customers` and `payments` arrived after v1
    /// shipped, so without this every shop already on a phone would fail to load
    /// the moment the owner took the update, and the app would come up empty
    /// beside an intact file it had decided it could not read.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        products = try container.decodeIfPresent([Product].self, forKey: .products) ?? []
        bills = try container.decodeIfPresent([Bill].self, forKey: .bills) ?? []
        customers = try container.decodeIfPresent([CustomerRecord].self, forKey: .customers) ?? []
        payments = try container.decodeIfPresent([Payment].self, forKey: .payments) ?? []
        settings = try container.decodeIfPresent(Settings.self, forKey: .settings) ?? Settings()
    }
}
