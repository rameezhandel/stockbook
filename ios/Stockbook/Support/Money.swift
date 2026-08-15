import Foundation
import SwiftUI

/// Currency rendering. One rule, one place.
///
/// From the handoff: `SAR ` prefix with a trailing space, integers rendered
/// without decimals (`SAR 194`), non-integers to exactly two (`SAR 0.25`),
/// `en-US` grouping. The symbol is a single configurable constant so a shop in
/// another country only changes `ShopSettings.currencySymbol`.
enum Money {

    static let defaultSymbol = "SAR "

    /// Two formatters, each configured once, rather than one mutated per call —
    /// a shared `NumberFormatter` whose fraction digits change on every use is a
    /// data race waiting to be found.
    private static let wholeFormatter = makeFormatter(fractionDigits: 0)
    private static let decimalFormatter = makeFormatter(fractionDigits: 2)

    private static func makeFormatter(fractionDigits: Int) -> NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "en_US")
        f.usesGroupingSeparator = true
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        return f
    }

    /// `SAR 1,240` / `SAR 0.25`.
    static func text(_ value: Double, symbol: String = defaultSymbol) -> String {
        symbol + amount(value)
    }

    /// The number alone, no symbol.
    static func amount(_ value: Double) -> String {
        let rounded = (value * 100).rounded() / 100
        // `-0.0` would otherwise print as "-0".
        let normalised = rounded == 0 ? 0 : rounded

        let formatter = normalised.isIntegerValued ? wholeFormatter : decimalFormatter
        return formatter.string(from: NSNumber(value: normalised)) ?? String(normalised)
    }

    /// Parses a value the owner typed. Returns nil for anything that is not a
    /// number, so callers can tell "empty" from "zero".
    static func parse(_ input: String) -> Double? {
        let trimmed = input.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return Double(trimmed.replacingOccurrences(of: ",", with: ""))
    }
}

private extension Double {
    var isIntegerValued: Bool { self == rounded() && isFinite }
}

/// The currency symbol, injected once at the root from `ShopSettings` so no
/// screen has to reach into the store just to format a number.
private struct CurrencySymbolKey: EnvironmentKey {
    static let defaultValue = Money.defaultSymbol
}

extension EnvironmentValues {
    var currencySymbol: String {
        get { self[CurrencySymbolKey.self] }
        set { self[CurrencySymbolKey.self] = newValue }
    }
}
