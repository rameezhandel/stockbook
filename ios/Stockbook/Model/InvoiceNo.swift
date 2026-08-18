import Foundation

/// The rule for the number written on a bill: matching.
///
/// "A-1024", "a-1024 " and "A-1024" are one paper, so comparison is on a
/// trimmed, lowercased key — the same rule customer names group under, for the
/// same reason. It touches nothing about identity: a bill is identified by
/// `Bill.number`, the app's own counter, which nobody types and nothing here
/// can change.
///
/// The number is always typed by the owner, never suggested — a bill book
/// skips numbers, gets reused, or starts partway through, and a guessed next
/// value would be the app inventing a run the paper does not have.
enum InvoiceNo {

    /// What two numbers are compared as. Blank in means blank out.
    static func key(_ raw: String?) -> String {
        (raw ?? "").trimmed.lowercased()
    }
}
