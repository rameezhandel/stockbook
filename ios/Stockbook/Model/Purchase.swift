import Foundation

/// One product on a delivery: what arrived, how many, and what the shop paid for
/// each. The mirror of `BillLine`, pointing the other way.
struct PurchaseLine: Codable, Equatable, Hashable, Sendable {
    /// Which product this was. `nil` because a line can outlive its product.
    var productUID: UUID?
    /// The product's name **at the time of delivery**. History must not move.
    var name: String
    /// At least 1.
    var qty: Int
    /// What the shop paid per piece, as entered.
    var unitCost: Double

    var lineTotal: Double { Double(qty) * unitCost }
}

/// Stock arriving from a supplier: the mirror of a `Bill`, pointing the other way.
///
/// **One delivery, one piece of paper, as many lines as the paper has.** It used
/// to hold a single product, on the argument that five lines entered as five
/// purchases were five true records rather than one convenient fiction — but that
/// escape was never open. The screen refuses a repeated invoice number, across
/// the whole book, because one number means one piece of paper. So a five-line
/// delivery note could not be entered at all: not as five records, which the
/// number rule forbids, and not as one, which the model had no room for. The
/// shape below is `Bill`'s, and it is the shape the old comment here said to
/// reach for.
///
/// A mistake is **edited or removed**, exactly as on a bill, and either takes the
/// stock back off the shelf — every line of it.
struct Purchase: Codable, Equatable, Identifiable, Sendable {

    var id: UUID
    /// Whose delivery, by the key suppliers group under.
    var supplierKey: String
    /// What arrived. Empty on a supplier bill entered as a figure rather than as
    /// stock: a mixed load, or something the shop keeps no count of.
    /// `isItemised` is how the rest of the app tells them apart, because only
    /// one of the two moves the shelf.
    var lines: [PurchaseLine]
    /// What the delivery came to — `qty × unitCost` where a product was named,
    /// and simply what was typed where one was not. Stored either way.
    var total: Double
    /// `nil` means settled on the spot — the common case at a counter where the
    /// driver waits for cash. A number means part paid, and the shop owes
    /// `total − paid`.
    var paid: Double?
    /// The number on the supplier's invoice — the same field as a bill's, pointing
    /// the other way.
    ///
    /// Optional here and required by the screen. The type has to be able to read a
    /// record that has none — a file written by another build, or a fixture — but
    /// nothing the owner enters can leave it empty.
    var invoiceNo: String?
    var createdAt: Date

    // --- Read from records written when a delivery held one product. Never
    // written again: `items` folds them into a single line so an older delivery
    // keeps the itemisation it was entered with. Dropping them instead would
    // have left the money right and quietly turned every delivery already in the
    // book into a bare figure.
    var productUID: UUID?
    var name: String?
    var qty: Int
    var unitCost: Double

    init(
        id: UUID = UUID(),
        supplierKey: String,
        lines: [PurchaseLine] = [],
        total: Double,
        paid: Double? = nil,
        invoiceNo: String? = nil,
        createdAt: Date = .now,
        productUID: UUID? = nil,
        name: String? = nil,
        qty: Int = 0,
        unitCost: Double = 0
    ) {
        self.id = id
        self.supplierKey = supplierKey
        self.lines = lines
        self.total = total
        self.paid = paid
        self.invoiceNo = CustomerRecord.tidied(invoiceNo)
        self.createdAt = createdAt
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.unitCost = unitCost
    }

    /// Every line, whichever shape the record was written in.
    ///
    /// The whole app reads this and never `lines` — the one place the two differ
    /// is a delivery entered before a delivery could have more than one product.
    var items: [PurchaseLine] {
        if !lines.isEmpty { return lines }
        guard let name, !name.isBlank else { return [] }
        return [PurchaseLine(productUID: productUID, name: name, qty: qty, unitCost: unitCost)]
    }

    /// Written by hand for the reason `Settings`, `ShopState`, `CustomerRecord`,
    /// `SupplierRecord` and `Bill` all have one: a default value does **not**
    /// make Swift's synthesised decoder tolerate a missing key — it throws.
    ///
    /// This became load-bearing the moment a purchase stopped having to name a
    /// product. `name` is absent from every supplier bill entered as a figure,
    /// and `qty` and `unitCost` are meaningless on one; the Kotlin side needed
    /// nothing because kotlinx.serialization falls back to the declared default,
    /// which is exactly the asymmetry that has cost this repo a broken load four
    /// times already.
    ///
    /// A `voided` key from the builds that marked a mistake rather than
    /// correcting it is simply not read: an unknown key is ignored, so a shop
    /// file written before voiding was removed still opens, whole.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        supplierKey = try container.decode(String.self, forKey: .supplierKey)
        lines = try container.decodeIfPresent([PurchaseLine].self, forKey: .lines) ?? []
        productUID = try container.decodeIfPresent(UUID.self, forKey: .productUID)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        qty = try container.decodeIfPresent(Int.self, forKey: .qty) ?? 0
        unitCost = try container.decodeIfPresent(Double.self, forKey: .unitCost) ?? 0
        total = try container.decode(Double.self, forKey: .total)
        paid = try container.decodeIfPresent(Double.self, forKey: .paid)
        invoiceNo = try container.decodeIfPresent(String.self, forKey: .invoiceNo)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
    }

    /// What the shop still owes on this delivery. Zero when settled.
    var balance: Double {
        guard let paid else { return 0 }
        return max(0, total - paid)
    }

    var isPartPaid: Bool { paid != nil }

    /// Whether this says what arrived, or only what it cost.
    ///
    /// Stock moves for the first and not the second, so editing or removing one
    /// has to reverse exactly what recording it did.
    var isItemised: Bool { !items.isEmpty }

    /// What the lines add up to. `total` is what was charged and is stored.
    var subtotal: Double { items.reduce(0) { $0 + $1.lineTotal } }

    /// What arrived, in the products' own words. Empty on a bill that named none.
    ///
    /// The same shape `Bill.summary` has, so a row of deliveries and a row of
    /// bills read the same way — and a row that needs the short form says
    /// `items(n)` beside it rather than instead of it, exactly as `BillRow` does.
    var summary: String { items.map(\.name).joined(separator: ", ") }

    /// What arrived, with the counts: `Cisa lock × 10, Key blank × 100`.
    ///
    /// `nil` rather than empty where the bill named nothing, because both places
    /// that show this — the statement on screen and the one that gets sent — drop
    /// the line entirely then. Interpolating regardless is how a supplier bill for
    /// a mixed load once read `null × 0` on a document somebody was handed.
    var described: String? {
        let lines = items
        guard !lines.isEmpty else { return nil }
        return lines.map { "\($0.name) × \($0.qty)" }.joined(separator: ", ")
    }

    /// What to call this delivery on a list or a statement.
    ///
    /// The supplier's number when it came with one, because that is what the
    /// supplier will say on the phone — otherwise the plain word, since a purchase
    /// has no counter of its own the way a bill does.
    func reference(_ strings: Strings) -> String {
        if let invoiceNo, !invoiceNo.isBlank { return invoiceNo }
        return strings.purchaseLabel
    }
}
