import Foundation
import SwiftUI

/// Currency rendering. One rule, one place.
///
/// From the handoff: the symbol prefixes the amount, whole numbers render
/// without decimals (`SAR 194`), and anything else renders to exactly the
/// currency's minor units (`SAR 0.25`, `KWD 0.125`). Which currency that is
/// comes from `Settings`; see `Currency`.
///
/// **Grouping is `en_US` for every currency.** It is the app's own rule rather
/// than the locale's, so the same number reads the same way whatever the shop
/// bills in and whichever language it reads — a shop that switches to Kannada
/// does not start seeing its riyals grouped in lakhs.
enum Money {

    /// One formatter per fraction-digit count, each configured once, rather than
    /// one mutated per call — a shared `NumberFormatter` whose fraction digits
    /// change on every use is a data race waiting to be found.
    private static let formatters: [Int: NumberFormatter] = {
        var made: [Int: NumberFormatter] = [:]
        for digits in 0...3 {
            made[digits] = makeFormatter(fractionDigits: digits)
        }
        return made
    }()

    private static func makeFormatter(fractionDigits: Int) -> NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "en_US")
        f.usesGroupingSeparator = true
        f.minimumFractionDigits = fractionDigits
        f.maximumFractionDigits = fractionDigits
        return f
    }

    /// `SAR 1,240` / `SAR 0.25` / `₹12`.
    static func text(_ value: Double, in currency: Currency = .default) -> String {
        currency.symbol + amount(value, in: currency)
    }

    /// The number alone, no symbol.
    static func amount(_ value: Double, in currency: Currency = .default) -> String {
        let scale = pow(10.0, Double(currency.fractionDigits))
        let rounded = (value * scale).rounded() / scale
        // `-0.0` would otherwise print as "-0".
        let normalised = rounded == 0 ? 0 : rounded

        let digits = normalised.isIntegerValued ? 0 : currency.fractionDigits
        let formatter = formatters[digits] ?? makeFormatter(fractionDigits: digits)
        return formatter.string(from: NSNumber(value: normalised)) ?? String(normalised)
    }

    /// Parses a value the owner typed. Returns nil for anything that is not a
    /// number, so callers can tell "empty" from "zero".
    /// What a percentage off comes to, rounded to the currency's smallest unit.
    ///
    /// Rounded **here**, once, rather than left as a fraction and rounded again
    /// wherever it is drawn: the figure is subtracted from the subtotal to make
    /// the bill's stored total, and a total that does not equal
    /// `subtotal − discount` to the last halala is a document nobody can check
    /// by hand. A percentage at or below zero is not a discount and comes to
    /// nothing; above a hundred it is capped, because a bill cannot go negative.
    static func discount(_ subtotal: Double, percent: Double, in currency: Currency = .default) -> Double {
        guard percent > 0, subtotal > 0 else { return 0 }
        let scale = pow(10.0, Double(currency.fractionDigits))
        let off = subtotal * (min(percent, 100) / 100)
        return (off * scale).rounded() / scale
    }

    static func parse(_ input: String) -> Double? {
        let trimmed = input.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return Double(trimmed.replacingOccurrences(of: ",", with: ""))
    }
}

private extension Double {
    var isIntegerValued: Bool { self == rounded() && isFinite }
}

/// The shop's currency, injected once at the root from `Settings` so no screen
/// has to reach into the store just to format a number.
private struct CurrencyKey: EnvironmentKey {
    static let defaultValue = Currency.default
}

extension EnvironmentValues {
    var currency: Currency {
        get { self[CurrencyKey.self] }
        set { self[CurrencyKey.self] = newValue }
    }
}
