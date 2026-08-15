import Foundation

/// Pluralisation and date formatting, kept together because both exist purely to
/// make the app's copy read correctly.
///
/// The prototype grew a `countWord` helper after plurals drifted out of step
/// across screens ("1 products"). Everything that counts something goes through
/// here.
enum Copy {

    /// `1 product` / `4 products`. Pass `plural` when it is not just `+ "s"`.
    static func count(_ n: Int, _ singular: String, plural: String? = nil) -> String {
        "\(n) " + word(n, singular, plural: plural)
    }

    /// The noun alone, correctly inflected.
    static func word(_ n: Int, _ singular: String, plural: String? = nil) -> String {
        n == 1 ? singular : (plural ?? singular + "s")
    }

    /// The Today kicker: `TUESDAY, 11 AUGUST`. Uppercasing is done by the
    /// `.kicker` type role, so this returns natural case.
    static func headerDate(_ date: Date = .now) -> String {
        headerDateFormatter.string(from: date)
    }

    /// `09:41` — the time stamped on a bill.
    static func time(_ date: Date) -> String {
        timeFormatter.string(from: date)
    }

    /// `28 July 2026` — the "saved" line on an import summary.
    static func longDate(_ date: Date) -> String {
        longDateFormatter.string(from: date)
    }

    /// `2026-08-11` — used to build the backup filename.
    static func fileDate(_ date: Date) -> String {
        fileDateFormatter.string(from: date)
    }

    private static let headerDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = "EEEE, d MMMM"
        return f
    }()

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "HH:mm"
        return f
    }()

    private static let longDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = "d MMMM yyyy"
        return f
    }()

    private static let fileDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
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
