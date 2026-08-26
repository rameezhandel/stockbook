import Foundation

/// A balance moved from one account to another, both of them real.
///
/// **Nothing is absorbed, and the app deliberately offers no way to absorb it.**
/// A merge — one row disappearing with its history re-filed under another — was
/// built here once and taken out again: on this book two accounts are two real
/// branches, not one firm entered twice. Both are genuine, both were rightly
/// invoiced, and from today they settle through one of them. Two branches of one
/// contractor consolidating is the case this exists for.
///
/// That is why **the invoices do not move.** The Jeddah branch's copy of invoice
/// #1042 says Jeddah; re-filing it under Riyadh would put this book out of step
/// with paper the customer is holding. Only the outstanding figure moves, and it
/// moves as a matching pair — off one statement, onto the other — so the two can
/// be reconciled against each other.
///
/// **No number, unlike every other record here.** An invoice, a receipt and a
/// credit note are each numbered because a slip exists in a drawer to match the
/// number to. Nothing was written for this: it is the owner's own adjustment
/// between two of their own accounts. What it does need is `note`, because a line
/// on a statement that the customer cannot account for is worse than no line.
///
/// The Kotlin twin is `BalanceTransfer.kt`.
struct BalanceTransfer: Codable, Equatable, Identifiable, Sendable {

    let id: UUID

    /// The account the balance leaves.
    var fromKey: String

    /// The account it lands on.
    var intoKey: String

    /// Which side of the book both keys belong to.
    ///
    /// Stored rather than derived. A customer and a supplier can share a name — a
    /// firm you both buy from and sell to is ordinary — so their keys are
    /// identical strings, and asking "is there a supplier with this key" would
    /// answer yes for a transfer between two customers.
    var isSupplier: Bool

    /// Always positive, and the store clamps it. Direction is carried by which key
    /// is which, not by a sign — a negative amount would be the same transfer
    /// written backwards, and two ways to say one thing is one way too many.
    ///
    /// **May exceed what is owed.** The app already reads a negative balance as
    /// money held in advance, and refusing would block a legitimate shuffle of a
    /// prepayment.
    var amount: Double

    /// Why it was moved. Required by the form, for the reason above.
    var note: String?

    var movedAt: Date

    init(
        id: UUID = UUID(),
        fromKey: String,
        intoKey: String,
        isSupplier: Bool = false,
        amount: Double,
        note: String? = nil,
        movedAt: Date = .now
    ) {
        self.id = id
        self.fromKey = fromKey
        self.intoKey = intoKey
        self.isSupplier = isSupplier
        self.amount = amount
        self.note = note
        self.movedAt = movedAt
    }
}
