import Foundation

/// Stock arriving from a supplier: the mirror of a `Bill`, pointing the other way.
///
/// **One product per purchase.** A real delivery note often has five lines, and
/// this deliberately does not: the screen a purchase is entered from is the one
/// that puts a single product back on the shelf, and a five-line delivery entered
/// as five purchases is five true records rather than one convenient fiction. If
/// that ever becomes the wrong trade, the change is a `lines` array here — the
/// shape `Bill` already has — and nothing else moves.
///
/// A mistake is **edited or removed**, exactly as on a bill, and either takes the
/// stock back off the shelf.
struct Purchase: Codable, Equatable, Identifiable, Sendable {

    var id: UUID
    /// Whose delivery, by the key suppliers group under.
    var supplierKey: String
    /// Which product this restocked. `nil` once that product has been deleted —
    /// a purchase can outlive what it bought, as a bill line can — and `nil`
    /// from the start on a supplier bill that names no product at all.
    var productUID: UUID?
    /// The product's name **at the time of delivery**. History must not move.
    ///
    /// `nil` when the supplier's bill was entered as a figure rather than as
    /// stock arriving: a bill for a mixed load, or for something the shop does
    /// not keep a count of. `isItemised` is how the rest of the app tells them
    /// apart, because only one of the two moves the shelf.
    var name: String?
    var qty: Int
    /// What the shop paid per piece, as entered. Zero when no product was named.
    var unitCost: Double
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

    init(
        id: UUID = UUID(),
        supplierKey: String,
        productUID: UUID? = nil,
        name: String? = nil,
        qty: Int = 0,
        unitCost: Double = 0,
        total: Double,
        paid: Double? = nil,
        invoiceNo: String? = nil,
        createdAt: Date = .now
    ) {
        self.id = id
        self.supplierKey = supplierKey
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.unitCost = unitCost
        self.total = total
        self.paid = paid
        self.invoiceNo = CustomerRecord.tidied(invoiceNo)
        self.createdAt = createdAt
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
    var isItemised: Bool { !(name ?? "").isBlank }

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
