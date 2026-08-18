import Foundation

/// The rules for the number written on a bill.
///
/// Two of them, and both exist because the number is typed by a person:
///
/// - **Matching.** "A-1024", "a-1024 " and "A-1024" are one paper, so comparison
///   is on a trimmed, lowercased key — the same rule customer names group under,
///   for the same reason.
/// - **Suggesting.** A bill book runs 1024, 1025, 1026, and the app should offer
///   the next one rather than make somebody type it. The run of digits at the
///   *end* is what moves; anything in front of it is the book's prefix and stays,
///   as does the width, so "A-0099" becomes "A-0100" and not "A-100".
///
/// Neither rule touches identity. A bill is identified by `Bill.number`, the
/// app's own counter, which nobody types and nothing here can change.
enum InvoiceNo {

    /// What two numbers are compared as. Blank in means blank out.
    static func key(_ raw: String?) -> String {
        (raw ?? "").trimmed.lowercased()
    }

    /// The number after this one, or nil when there is no run of digits to move.
    ///
    /// "1024" → "1025", "A-1024" → "A-1025", "0099" → "0100", "INV" → nil.
    static func next(after previous: String?) -> String? {
        let text = (previous ?? "").trimmed
        guard !text.isEmpty else { return nil }

        var start = text.endIndex
        while start > text.startIndex {
            let before = text.index(before: start)
            guard text[before].isNumber, text[before].isASCII else { break }
            start = before
        }
        guard start < text.endIndex else { return nil }

        let digits = String(text[start...])
        // A bill book that has reached the width of an Int is not a real one, but
        // the arithmetic still must not overflow.
        guard let value = Int(digits) else { return nil }
        let grown = String(value + 1)
        let padded = String(repeating: "0", count: max(0, digits.count - grown.count)) + grown
        return String(text[text.startIndex..<start]) + padded
    }
}
