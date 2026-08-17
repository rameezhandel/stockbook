import Foundation

/// The one currency the shop bills in.
///
/// One at a time, always — the app never converts, never holds a rate, and never
/// shows two currencies on one screen. The symbol is a prefix on a number and
/// nothing more.
///
/// Identified by its ISO 4217 code, which is what gets persisted and exported.
/// The symbol and the minor-unit count are derived from the table below, so a
/// wrong symbol is fixed in one place rather than migrated out of everyone's
/// saved settings.
struct Currency: Identifiable, Hashable, Sendable {

    /// ISO 4217. The stored value.
    let code: String

    /// What prefixes every amount, **including its own spacing**. Alphabetic
    /// codes read as `SAR 194`; a glyph reads as `₹194`, which is how each is
    /// written in the places they are used.
    let symbol: String

    /// Digits after the point when an amount is not whole. Two almost
    /// everywhere; the Gulf dinars and the rial are three, and rendering 0.125
    /// as `0.13` in a shop that bills in fils is a real error, not a rounding
    /// preference.
    let fractionDigits: Int

    var id: String { code }

    private init(_ code: String, _ symbol: String, fractionDigits: Int = 2) {
        self.code = code
        self.symbol = symbol
        self.fractionDigits = fractionDigits
    }

    // MARK: The table

    static let sar = Currency("SAR", "SAR ")
    static let aed = Currency("AED", "AED ")
    static let qar = Currency("QAR", "QAR ")
    static let kwd = Currency("KWD", "KWD ", fractionDigits: 3)
    static let bhd = Currency("BHD", "BHD ", fractionDigits: 3)
    static let omr = Currency("OMR", "OMR ", fractionDigits: 3)
    static let inr = Currency("INR", "₹")
    static let pkr = Currency("PKR", "PKR ")
    static let bdt = Currency("BDT", "৳")
    static let lkr = Currency("LKR", "LKR ")
    static let npr = Currency("NPR", "NPR ")
    static let usd = Currency("USD", "$")
    static let eur = Currency("EUR", "€")
    static let gbp = Currency("GBP", "£")

    /// What the picker offers, in the order it offers them: the Gulf first,
    /// because that is where this shop is, then the countries its customers and
    /// suppliers come from, then the three everyone recognises.
    static let supported: [Currency] = [
        .sar, .aed, .qar, .kwd, .bhd, .omr,
        .inr, .pkr, .bdt, .lkr, .npr,
        .usd, .eur, .gbp
    ]

    static let `default` = Currency.sar

    /// The stored code back into a currency. An unknown code — a file from a
    /// build with a longer table — falls back rather than failing: showing the
    /// wrong symbol beats refusing to open the shop.
    static func named(_ code: String) -> Currency {
        supported.first { $0.code == code } ?? .default
    }
}
