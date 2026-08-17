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
/// Purchases are never deleted. A mistake is *voided*, which takes the stock back
/// off the shelf and leaves the record in place, exactly as a bill's void puts it
/// back on.
struct Purchase: Codable, Equatable, Identifiable, Sendable {

    var id: UUID
    /// Whose delivery, by the key suppliers group under.
    var supplierKey: String
    /// Which product this restocked. `nil` once that product has been deleted —
    /// a purchase can outlive what it bought, as a bill line can.
    var productUID: UUID?
    /// The product's name **at the time of delivery**. History must not move.
    var name: String
    var qty: Int
    /// What the shop paid per piece, as entered.
    var unitCost: Double
    /// `qty × unitCost` at the time, stored rather than recomputed.
    var total: Double
    /// `nil` means settled on the spot — the common case at a counter where the
    /// driver waits for cash. A number means part paid, and the shop owes
    /// `total − paid`.
    var paid: Double?
    var createdAt: Date
    var voided: Bool

    init(
        id: UUID = UUID(),
        supplierKey: String,
        productUID: UUID?,
        name: String,
        qty: Int,
        unitCost: Double,
        total: Double,
        paid: Double? = nil,
        createdAt: Date = .now,
        voided: Bool = false
    ) {
        self.id = id
        self.supplierKey = supplierKey
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.unitCost = unitCost
        self.total = total
        self.paid = paid
        self.createdAt = createdAt
        self.voided = voided
    }

    /// What the shop still owes on this delivery. Zero when settled or voided.
    var balance: Double {
        guard !voided, let paid else { return 0 }
        return max(0, total - paid)
    }

    var isPartPaid: Bool { !voided && paid != nil }
}
