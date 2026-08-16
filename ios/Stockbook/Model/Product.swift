import Foundation

/// One line of stock on the shelf.
///
/// A plain value type: no framework, no persistence, no identity beyond its own
/// `uid`. Storage is somebody else's problem — see `StockbookRepository`.
///
/// Everything in Stockbook sells **by the piece**. One product is one stock
/// count and one selling price. (An earlier design had pack/loose variants
/// sharing a count; the owner cut it.)
struct Product: Identifiable, Codable, Equatable {

    /// Stable identity, and the only one there is. It survives export, import
    /// and a change of storage engine — which a row id or an object reference
    /// would not.
    let uid: UUID

    var name: String

    /// Pieces on the shelf. Only ever changed by: setup, the product editor,
    /// saving a bill (decrement, floored at 0), voiding a bill (increment back),
    /// and restock (increment). See `StockbookStore`.
    var stock: Int

    /// The **latest** buying price per piece — not a weighted average. A
    /// purchase entry overwrites it. There is no inventory valuation layer here
    /// and the handoff asks for it to stay that way.
    var cost: Double

    /// Selling price per piece. Must be greater than zero.
    var price: Double

    var createdAt: Date

    var id: UUID { uid }

    init(
        uid: UUID = UUID(),
        name: String,
        stock: Int,
        cost: Double,
        price: Double,
        createdAt: Date = .now
    ) {
        self.uid = uid
        self.name = name
        self.stock = stock
        self.cost = cost
        self.price = price
        self.createdAt = createdAt
    }

    /// `price − cost`, floored at zero. The only profit figure the app shows.
    var marginPerPiece: Double { max(0, price - cost) }

    /// `out of stock` / `12 pc` — the shelf count wherever it is shown.
    var stockLabel: String { stock == 0 ? "out of stock" : "\(stock) pc" }

    func isLow(threshold: Int) -> Bool { stock <= threshold }
}
