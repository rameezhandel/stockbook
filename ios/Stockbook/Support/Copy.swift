import Foundation

/// Date formatting, and the small string helpers the rest of the app leans on.
///
/// Wording lives in `Strings`; this is only the machinery underneath it. Dates
/// are the one piece of copy the app does not write itself — the weekday and
/// month names come from the system for whichever language is in force.
enum Copy {

    /// The Today kicker: `TUESDAY, 11 AUGUST` / `ಮಂಗಳವಾರ, 11 ಆಗಸ್ಟ್`.
    /// Uppercasing is done by the `.kicker` type role, so this returns natural
    /// case — which matters, because Kannada has no upper case to apply.
    static func headerDate(_ date: Date = .now, locale: Locale) -> String {
        formatter("EEEE, d MMMM", locale).string(from: date)
    }

    /// `09:41` — the time stamped on a bill. Fixed 24-hour, both languages: a
    /// bill number and a time are read side by side and should not change shape
    /// with the interface language.
    static func time(_ date: Date) -> String {
        formatter("HH:mm", Locale(identifier: "en_US_POSIX")).string(from: date)
    }

    /// `28 July 2026` — the "saved" line on an import summary.
    static func longDate(_ date: Date, locale: Locale) -> String {
        formatter("d MMMM yyyy", locale).string(from: date)
    }

    /// `2026-08-11` — used to build the backup filename, so it is deliberately
    /// **not** localised: the file has to sort and parse the same everywhere.
    static func fileDate(_ date: Date) -> String {
        formatter("yyyy-MM-dd", Locale(identifier: "en_US_POSIX")).string(from: date)
    }

    /// Formatters are expensive to build and there are only a handful of
    /// (format, locale) pairs, so they are made once and kept.
    private static let cache = FormatterCache()

    private static func formatter(_ format: String, _ locale: Locale) -> DateFormatter {
        cache.formatter(format: format, locale: locale)
    }
}

/// A tiny lock-guarded cache. `DateFormatter` is thread-safe to *use* once
/// configured but not to configure, so the mutation happens behind a lock and
/// the formatter is never touched again afterwards.
private final class FormatterCache: @unchecked Sendable {
    private var storage: [String: DateFormatter] = [:]
    private let lock = NSLock()

    func formatter(format: String, locale: Locale) -> DateFormatter {
        let key = "\(locale.identifier)|\(format)"
        lock.lock()
        defer { lock.unlock() }
        if let existing = storage[key] { return existing }
        let made = DateFormatter()
        made.locale = locale
        made.dateFormat = format
        storage[key] = made
        return made
    }
}

extension String {
    /// `"Ahmed Al-Amri"` → `"Ahmed"`. Used by the dashboard greeting and the
    /// setup kicker.
    var firstName: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ", maxSplits: 1)
            .first
            .map(String.init) ?? ""
    }

    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var isBlank: Bool { trimmed.isEmpty }
}
