import Foundation

/// Money the owner spent, written down so they can see where it went.
///
/// Petrol, tea, a spare key blank bought in a hurry, the electricity bill. This
/// is a **private ledger and nothing more**: it is deliberately joined to
/// nothing else in the app.
///
/// - It is not a purchase. A `Purchase` is stock arriving from a named supplier;
///   it moves the shelf and it creates a debt. An expense moves neither.
/// - It does not touch what anybody owes. No customer, no supplier, no key.
/// - It is not on any statement, and cannot be. A statement is a document the
///   owner may turn round and show a customer, and the owner's petrol is not
///   that customer's business.
/// - It does not move "Sold", "Receivable" or "Payable". Those are the shop's
///   position and this is the owner's spending; netting them would need a
///   definition of profit this app has not been asked for and would then have to
///   be right about.
///
/// Keeping it separate is the whole design. It means adding this could not break
/// a figure that already works.
struct Expense: Codable, Identifiable, Equatable, Hashable {
    /// Identity, machine-assigned and never typed.
    ///
    /// Unlike a bill, a receipt or a credit note, an expense carries **no typed
    /// number**. Those numbers exist because there is a piece of paper in a
    /// drawer with the same number on it, and the number is how the owner finds
    /// it again. There is no such slip behind a tank of petrol.
    var id: String = UUID().uuidString

    /// What it came to.
    var amount: Double

    /// What it was for, in the owner's own words — "Petrol", "Tea for the shop",
    /// "Van tyre".
    ///
    /// Free text rather than a list of kinds, chosen deliberately: a fixed list
    /// would let the screen total by category, and it would also be a list
    /// somebody has to maintain in two languages and that never quite fits the
    /// next thing spent. Required, because an amount with nothing beside it is a
    /// number nobody can account for a month later.
    var note: String

    /// Anything more the owner wants to remember about it — "Jeddah trip", "paid
    /// Abu Salem cash", "the one with the cracked casing".
    ///
    /// **Separate from `note`, and that separation is the point.** `note` is the
    /// short name the money went under, and `spendingIn` folds a month by it:
    /// typing "Petrol for the Jeddah trip on Tuesday" there would give that one
    /// tank a line of its own on the summary and hide it from the Petrol total.
    /// So the name stays short and groupable, and everything else goes here.
    ///
    /// Optional, and absent rather than empty when skipped — the same shape
    /// `Payment.note` and `CreditNote.reason` already have. Its absence changes
    /// no figure, which is why it does not bump the backup version.
    ///
    /// Declared **between `note` and `spentAt`** to match the Kotlin twin, and
    /// because the memberwise initialiser takes its arguments in declaration
    /// order — a field added at the end here would compile and then be filled
    /// from the wrong call sites. Being optional is also what lets the
    /// synthesised decoder read a shop file written before it existed; a
    /// defaulted *non*-optional would throw.
    var detail: String?

    /// The day the money went, which is not always the day it was written down.
    var spentAt: Date = .now
}
