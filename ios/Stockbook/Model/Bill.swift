import Foundation
import SwiftData

/// One sale.
///
/// Bills are never deleted — a mistake is *voided*, which puts the stock back
/// and leaves the record in place.
@Model
final class Bill {

    /// The human-facing number, shown as "Bill #7". Allocated from
    /// `ShopSettings.nextBillNumber` so it is stable and monotonic; SwiftData's
    /// own identifier is not something to put in front of a customer.
    var number: Int = 0

    @Relationship(deleteRule: .cascade, inverse: \BillLine.bill)
    var lines: [BillLine] = []

    /// Sum of `qty × price` at the moment of sale — **stored**, never recomputed
    /// from current product prices. Editing a product tomorrow must not rewrite
    /// what a customer paid today.
    var total: Double = 0

    /// `nil` means paid in full. A number means part paid, and the customer owes
    /// `total − paid`.
    var paid: Double?

    /// Customer name, trimmed. Required on every bill.
    var who: String = ""

    var createdAt: Date = Date.now

    var voided: Bool = false

    init(
        number: Int,
        lines: [BillLine] = [],
        total: Double,
        paid: Double?,
        who: String,
        createdAt: Date = .now,
        voided: Bool = false
    ) {
        self.number = number
        self.lines = lines
        self.total = total
        self.paid = paid
        self.who = who
        self.createdAt = createdAt
        self.voided = voided
    }

    /// What is still owed on this bill. Zero when paid in full or voided.
    var balance: Double {
        guard !voided, let paid else { return 0 }
        return max(0, total - paid)
    }

    var isPartPaid: Bool { !voided && paid != nil }

    /// `09:41`
    var timeLabel: String { Copy.time(createdAt) }

    /// The row's first line: the names on the bill, joined.
    var summary: String {
        lines.map(\.name).joined(separator: ", ")
    }
}

/// A single line on a bill.
///
/// `name` and `price` are **snapshots** taken at sale time. The product may be
/// renamed, repriced or deleted afterwards; history must not move.
@Model
final class BillLine {

    /// Which product this was, by the identity that survives export/import.
    /// Optional because a line can outlive its product.
    var productUID: UUID?

    /// The product's name *at the time of sale*.
    var name: String = ""

    /// At least 1.
    var qty: Int = 1

    /// The price actually charged — which may be an override, not the product's
    /// list price.
    var price: Double = 0

    var bill: Bill?

    init(productUID: UUID?, name: String, qty: Int, price: Double) {
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.price = price
    }

    var lineTotal: Double { Double(qty) * price }
}
