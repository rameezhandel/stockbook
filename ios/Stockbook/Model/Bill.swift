import Foundation

/// One sale.
///
/// Bills are never deleted. A mistake is *voided*, which puts the stock back and
/// leaves the record in place — without that, one mistyped bill puts the shelf
/// and the app permanently out of step.
struct Bill: Identifiable, Codable, Equatable {

    /// The human-facing number, shown as "Bill #7". Allocated from
    /// `Settings.nextBillNumber`, so it is stable, monotonic, and something you
    /// can say to a customer.
    let number: Int

    var lines: [BillLine]

    /// Sum of `qty × price` at the moment of sale — **stored**, never recomputed
    /// from current product prices. Editing a product tomorrow must not rewrite
    /// what somebody paid today.
    var total: Double

    /// `nil` means paid in full. A number means part paid, and the customer owes
    /// `total − paid`.
    var paid: Double?

    /// The number printed on the paper bill, when the shop writes one.
    ///
    /// A string, not an int: bill books are numbered "1024" in some shops and
    /// "A-1024" in others, and neither is arithmetic. Distinct from `number`,
    /// which is this app's own counter and its identity — that one has to stay
    /// unique and machine-assigned, or voiding and history lookups lose their
    /// handle. This is a label the owner recognises; that is a key.
    var invoiceNo: String?

    /// Customer name, trimmed. Required on every bill.
    var who: String

    var createdAt: Date

    var voided: Bool

    var id: Int { number }

    init(
        number: Int,
        lines: [BillLine],
        total: Double,
        paid: Double?,
        who: String,
        invoiceNo: String? = nil,
        createdAt: Date = .now,
        voided: Bool = false
    ) {
        self.number = number
        self.lines = lines
        self.total = total
        self.paid = paid
        self.who = who
        self.invoiceNo = CustomerRecord.tidied(invoiceNo)
        self.createdAt = createdAt
        self.voided = voided
    }

    /// Written by hand for the same reason `Settings` is: a default does not make
    /// the synthesised decoder tolerate a missing key, and every bill already
    /// stored was written before `invoiceNo` existed.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        number = try container.decode(Int.self, forKey: .number)
        lines = try container.decode([BillLine].self, forKey: .lines)
        total = try container.decode(Double.self, forKey: .total)
        paid = try container.decodeIfPresent(Double.self, forKey: .paid)
        who = try container.decode(String.self, forKey: .who)
        invoiceNo = try container.decodeIfPresent(String.self, forKey: .invoiceNo)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        voided = try container.decodeIfPresent(Bool.self, forKey: .voided) ?? false
    }

    /// What is still owed on this bill. Zero when paid in full or voided.
    var balance: Double {
        guard !voided, let paid else { return 0 }
        return max(0, total - paid)
    }

    var isPartPaid: Bool { !voided && paid != nil }

    /// What to call this bill on screen: the paper's number where there is one,
    /// and the app's own otherwise. One number, never both — two numbers on a
    /// document is how somebody reads out the wrong one over the phone.
    func reference(_ strings: Strings) -> String {
        if let invoiceNo, !invoiceNo.isBlank { return invoiceNo }
        return strings.billNumber(number)
    }

    /// The row's first line: the names on the bill, joined.
    var summary: String { lines.map(\.name).joined(separator: ", ") }
}

/// A single line on a bill.
///
/// `name` and `price` are **snapshots** taken at sale time. The product may be
/// renamed, repriced or deleted afterwards; history must not move.
struct BillLine: Identifiable, Codable, Equatable {

    /// Which product this was. Optional because a line can outlive its product.
    var productUID: UUID?

    /// The product's name *at the time of sale*.
    var name: String

    /// At least 1.
    var qty: Int

    /// The price actually charged — which may be an override, not the list price.
    var price: Double

    /// Lines are identified by position within their bill; nothing outside a
    /// bill ever refers to one.
    var id: String { "\(productUID?.uuidString ?? "none")-\(name)-\(qty)-\(price)" }

    init(productUID: UUID?, name: String, qty: Int, price: Double) {
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.price = price
    }

    var lineTotal: Double { Double(qty) * price }
}
