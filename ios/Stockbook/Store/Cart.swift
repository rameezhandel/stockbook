import Foundation
import Combine

/// The bill being typed right now.
///
/// Deliberately **not** persisted. The handoff separates persisted state
/// (products, bills, settings) from transient state (the cart, search strings,
/// sheet drafts, payment mode) — a half-typed bill is not history, and an
/// abandoned one should not survive a relaunch.
final class Cart: ObservableObject {

    struct Line: Identifiable {
        let productUID: UUID
        /// Snapshot of the name at the moment it was added — enough to render
        /// the row without holding a copy of the product that could go stale.
        let name: String
        var qty: Int
        /// What is being charged. Prefilled from the product's selling price and
        /// editable — the whole point of the cart screen.
        var price: Double
        /// The prefill, kept so the screen can tell an override from a match and
        /// offer "Reset". The product's own price is never touched by an override.
        let basePrice: Double

        var id: UUID { productUID }
        var lineTotal: Double { Double(qty) * price }
        var isPriceOverridden: Bool { abs(price - basePrice) > 0.0001 }
    }

    enum PayMode {
        case full
        case part
    }

    @Published var lines: [Line] = []
    @Published var customer: String = ""
    @Published var payMode: PayMode = .full
    /// Held as text so a half-typed amount is representable.
    @Published var paidText: String = ""

    var isEmpty: Bool { lines.isEmpty }

    var total: Double {
        lines.reduce(0) { $0 + $1.lineTotal }
    }

    /// The amount taken now: the full total, or the clamped part payment.
    var paidNow: Double {
        switch payMode {
        case .full: total
        case .part: min(max(0, Money.parse(paidText) ?? 0), total)
        }
    }

    var balance: Double { max(0, total - paidNow) }

    /// What gets stored on the bill: `nil` for paid in full.
    var paidForStorage: Double? {
        payMode == .full ? nil : paidNow
    }

    /// The one gate on this screen: a bill cannot be saved without a customer.
    var canSave: Bool { !lines.isEmpty && !customer.isBlank }

    // MARK: Mutation

    /// Adds one piece at the product's current selling price, or increments the
    /// line if it is already in the cart.
    func add(_ product: Product) {
        if let index = lines.firstIndex(where: { $0.productUID == product.uid }) {
            lines[index].qty += 1
        } else {
            lines.append(Line(productUID: product.uid, name: product.name, qty: 1, price: product.price, basePrice: product.price))
        }
    }

    func setQuantity(_ quantity: Int, for uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].qty = max(1, quantity)
    }

    func setPrice(_ price: Double, for uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].price = max(0, price)
    }

    func resetPrice(for uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].price = lines[index].basePrice
    }

    func remove(_ uid: UUID) {
        lines.removeAll { $0.id == uid }
    }

    /// Drops any line whose product has been deleted from the catalogue.
    func dropLine(for product: Product) {
        remove(product.uid)
    }

    /// True when the cart no longer matches the catalogue — the product behind a
    /// line was deleted while the bill was open.
    func prune(against products: [Product]) {
        let known = Set(products.map(\.uid))
        lines.removeAll { !known.contains($0.productUID) }
    }

    func clear() {
        lines = []
        customer = ""
        payMode = .full
        paidText = ""
    }

    /// Live shelf count for a line. The cart deliberately does not carry one:
    /// stock changes elsewhere while a bill is open, and a stale number here is
    /// exactly the sort of quiet wrongness this app is meant to avoid.
    func stock(for line: Line, in store: StockbookStore) -> Int {
        store.product(uid: line.productUID)?.stock ?? 0
    }

    /// The cart in the shape `StockbookStore.saveBill` wants.
    var draftLines: [DraftLine] {
        lines.map { DraftLine(productUID: $0.productUID, qty: $0.qty, price: $0.price) }
    }
}
