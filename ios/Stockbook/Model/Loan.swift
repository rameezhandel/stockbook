import Foundation

/// Cash the shop handed **to** a customer, which they are expected to bring back.
///
/// A van serving the same builders every week ends up lending to them, and until
/// now the book had nowhere to put it: `Payment` is money coming in, a credit
/// note moves an account without any money at all, and an expense is joined to
/// nobody. So the four hundred riyals the owner lent Ahmed on Tuesday existed
/// only in the owner's head, while every other figure in the shop was written
/// down.
///
/// **It is money out that makes a debt** — the exact mirror of `Payment`, which
/// is money in that settles one. Lending four hundred to somebody who owes eight
/// leaves them owing twelve, and there is no second balance: goods owed and cash
/// lent are one debt on one account, each line saying which it was.
///
/// Three things it deliberately is not:
///
/// - **Not a negative `Payment`.** Money in and money out are two types in this
///   file and stay two types; the format's first rule is never to repurpose a key
///   or to make one record mean its opposite with a sign on it.
/// - **Not a sale.** Nothing left the shelf and the shop earned nothing. It never
///   touches what was sold, what the goods cost, or what the month earned — cash
///   moved from one pocket to another and is owed back at face value.
/// - **Not a supplier payment.** That settles what the shop owes somebody it buys
///   from. This creates what somebody owes the shop. A person who is both is two
///   records under two keys, which is how this app has always held them.
///
/// Coming back is nothing new: a repayment is an ordinary `Payment` against the
/// same customer, because that is what it is — money in, settling what is owed.
///
/// It attaches to a **customer, not a bill**, the same rule payments and credit
/// notes follow. There is no number either: an invoice, a receipt and a credit
/// note each come out of a numbered book the shop keeps, and a hand of cash
/// across a counter comes out of no book at all. `note` is where "for the school
/// fees" goes if the owner wants it there.
struct Loan: Codable, Equatable, Identifiable, Sendable {

    let id: UUID

    /// Who took it, by the same key bills and payments group under.
    var customerKey: String

    /// Always positive. The initialiser clamps it. A negative loan would be a
    /// payment, and a payment is its own type.
    var amount: Double

    var lentAt: Date

    /// "for the school fees", "against next month's work" — whatever the owner
    /// wants to remember about an arrangement that has no paperwork behind it.
    /// Optional, and absent rather than empty when skipped.
    var note: String?

    init(
        id: UUID = UUID(),
        customerKey: String,
        amount: Double,
        lentAt: Date = .now,
        note: String? = nil
    ) {
        self.id = id
        self.customerKey = customerKey
        self.amount = max(0, amount)
        self.lentAt = lentAt
        self.note = CustomerRecord.tidied(note)
    }
}
