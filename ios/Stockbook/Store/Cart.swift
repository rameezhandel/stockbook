import Foundation
import Observation

/// The bill being typed right now.
///
/// Deliberately **not** persisted. The handoff separates persisted state
/// (products, bills, settings) from transient state (the cart, search strings,
/// sheet drafts, payment mode) — a half-typed bill is not history, and an
/// abandoned one should not survive a relaunch.
@Observable
final class Cart {

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
        /// True while this line's figures came from a camera and no human has
        /// looked at them yet.
        ///
        /// A misread 7 as a 1 on a price is a silently wrong bill, so the screen
        /// marks what it has not been told is right. Touching the line clears it —
        /// see `confirm(_:)`.
        var isUnconfirmed: Bool = false

        var id: UUID { productUID }
        var lineTotal: Double { Double(qty) * price }
        var isPriceOverridden: Bool { abs(price - basePrice) > 0.0001 }
    }

    enum PayMode {
        case full
        case part
    }

    var lines: [Line] = []
    var customer: String = ""
    var payMode: PayMode = .full
    /// Held as text so a half-typed amount is representable.
    var paidText: String = ""

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
            lines[index].isUnconfirmed = false
        } else {
            lines.append(Line(productUID: product.uid, name: product.name, qty: 1, price: product.price, basePrice: product.price))
        }
    }

    /// Fills the cart from a scanned bill.
    ///
    /// Replaces rather than appends: the owner pointed a camera at one piece of
    /// paper and expects to be looking at that piece of paper, not at it mixed
    /// into whatever was half-typed before.
    ///
    /// Every line arrives **unconfirmed**, and the customer name only if the cart
    /// has none — a name already typed by hand outranks one read off a photo.
    func fill(from outcome: ScanOutcome) {
        lines = outcome.matched.map { match in
            Line(
                productUID: match.product.uid,
                name: match.product.name,
                qty: match.quantity,
                price: match.price(fallback: match.product.price),
                basePrice: match.product.price,
                isUnconfirmed: true
            )
        }
        if customer.isBlank, let scanned = outcome.customer {
            customer = scanned
        }
    }

    /// The owner has looked at this line. Called by every edit on the cart screen,
    /// so the mark comes off the moment a human touches the figures.
    func confirm(_ uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].isUnconfirmed = false
    }

    /// The owner has read the paper and the bill matches it. Called when the scan
    /// card is dismissed, which is what dismissing it means.
    func confirmAll() {
        for index in lines.indices { lines[index].isUnconfirmed = false }
    }

    /// True while anything on the bill still has figures nobody has checked.
    var hasUnconfirmedLines: Bool { lines.contains { $0.isUnconfirmed } }

    func setQuantity(_ quantity: Int, for uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].qty = max(1, quantity)
        lines[index].isUnconfirmed = false
    }

    func setPrice(_ price: Double, for uid: UUID) {
        guard let index = lines.firstIndex(where: { $0.id == uid }) else { return }
        lines[index].price = max(0, price)
        lines[index].isUnconfirmed = false
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

    /// How many of a product are on the bill already. Zero when it is not.
    func quantity(forProduct uid: UUID) -> Int {
        lines.first { $0.productUID == uid }?.qty ?? 0
    }

    /// Live shelf count for a line. The cart deliberately does not carry one:
    /// stock changes elsewhere while a bill is open, and a stale number here is
    /// exactly the sort of quiet wrongness this app is meant to avoid.
    @MainActor
    func stock(for line: Line, in store: StockbookStore) -> Int {
        store.product(uid: line.productUID)?.stock ?? 0
    }

    /// The cart in the shape `StockbookStore.saveBill` wants.
    var draftLines: [DraftLine] {
        lines.map { DraftLine(productUID: $0.productUID, qty: $0.qty, price: $0.price) }
    }
}
