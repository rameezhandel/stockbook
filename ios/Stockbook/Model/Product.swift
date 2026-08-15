import Foundation
import SwiftData

/// One line of stock on the shelf.
///
/// Everything in Stockbook sells **by the piece** — no units, no packs, no
/// fractional quantities. One product is one stock count and one selling price.
/// (An earlier design had pack/loose variants sharing a count; the owner cut it.)
@Model
final class Product {

    /// A stable identity that survives export and import. `persistentModelID`
    /// cannot: it is local to one store, and a backup has to be readable on a
    /// different phone.
    var uid: UUID = UUID()

    var name: String = ""

    /// Pieces on the shelf. Only ever changed by: setup, the product editor,
    /// saving a bill (decrement, floored at 0), voiding a bill (increment back),
    /// and restock (increment). See `StockbookStore`.
    var stock: Int = 0

    /// The **latest** buying price per piece — not a weighted average. A
    /// purchase entry overwrites it. There is no inventory valuation layer here
    /// and the handoff asks for it to stay that way.
    var cost: Double = 0

    /// Selling price per piece. Must be greater than zero.
    var price: Double = 0

    var createdAt: Date = Date.now

    init(uid: UUID = UUID(), name: String, stock: Int, cost: Double, price: Double, createdAt: Date = .now) {
        self.uid = uid
        self.name = name
        self.stock = stock
        self.cost = cost
        self.price = price
        self.createdAt = createdAt
    }

    /// `price − cost`, floored at zero. The only profit figure the app shows.
    var marginPerPiece: Double { max(0, price - cost) }

    func isLow(threshold: Int) -> Bool { stock <= threshold }
}
