import Foundation

/// Money the shop handed over to a supplier after the delivery.
///
/// Its own record for the same reason `Payment` is: the purchase says what
/// happened on the day the stock arrived and must go on saying it, and settling
/// up three weeks later is a second event with its own date.
///
/// A separate type from `Payment` rather than one type with a direction on it.
/// Money in and money out are not the same thing, and the file format's first
/// rule is never to repurpose a key — `customerKey` could not quietly start
/// meaning "whoever this is" without every older reader misreading a shop's debts
/// as its takings.
struct SupplierPayment: Codable, Equatable, Identifiable, Sendable {

    var id: UUID
    var supplierKey: String
    /// Always positive; the initialiser clamps it. A negative payment is a
    /// refund, and there is no such thing here.
    var amount: Double
    /// The number on the receipt, on its own series — see `Payment.paymentNo`.
    var paymentNo: String?
    var paidAt: Date
    /// "cash", "cheque 4471", "against last month" — absent rather than empty
    /// when skipped.
    var note: String?

    init(
        id: UUID = UUID(),
        supplierKey: String,
        amount: Double,
        paymentNo: String? = nil,
        paidAt: Date = .now,
        note: String? = nil
    ) {
        self.id = id
        self.supplierKey = supplierKey
        self.amount = max(0, amount)
        self.paymentNo = paymentNo
        self.paidAt = paidAt
        self.note = CustomerRecord.tidied(note)
    }
}
