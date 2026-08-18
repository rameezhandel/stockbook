import Foundation

/// Money a customer handed over **after** the bill was written.
///
/// Its own record rather than an edit to the bill, for one reason: the bill says
/// what happened at the counter that day and must go on saying it. A customer who
/// clears 400 three weeks later has not changed that bill — they have added a
/// second event, with its own date, and a statement is unreadable without both.
///
/// Payments are attached to a **customer, not a bill.** That is how a shop like
/// this is actually settled: somebody hands over what they can against what they
/// owe, not against invoice #7 specifically. Allocating it to particular bills
/// would be a fiction the owner would then have to maintain.
struct Payment: Codable, Equatable, Identifiable, Sendable {

    let id: UUID

    /// Whose payment, by the same key bills group under.
    var customerKey: String

    /// Always positive. The store clamps it; nothing here can express a negative
    /// payment, because that is a refund and this app has no notion of one.
    var amount: Double

    /// The number on the receipt the shop wrote when it took the money.
    ///
    /// Its own series, like a credit note's and unlike a bill's — a receipt book
    /// is numbered separately from an invoice book, and "876" in one has nothing
    /// to do with "876" in the other. Typed rather than suggested, as every
    /// number in this app is.
    var paymentNo: String?

    var receivedAt: Date

    /// "cash", "cheque 4471", "part settlement" — whatever the owner wants to
    /// remember. Optional, and absent rather than empty when skipped.
    var note: String?

    init(
        id: UUID = UUID(),
        customerKey: String,
        amount: Double,
        paymentNo: String? = nil,
        receivedAt: Date = .now,
        note: String? = nil
    ) {
        self.id = id
        self.customerKey = customerKey
        self.amount = max(0, amount)
        self.paymentNo = paymentNo
        self.receivedAt = receivedAt
        self.note = CustomerRecord.tidied(note)
    }
}
