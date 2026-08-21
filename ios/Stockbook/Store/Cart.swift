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

        var id: UUID { productUID }
        var lineTotal: Double { Double(qty) * price }
        var isPriceOverridden: Bool { abs(price - basePrice) > 0.0001 }
    }

    enum PayMode {
        case full
        case part
    }

    var lines: [Line] = []

    /// The customer's name as it will be written on the bill.
    ///
    /// Set through `typeCustomer` and `selectCustomer` rather than assigned, so
    /// the name and `customerKey` can never disagree.
    private(set) var customer: String = ""

    /// The chosen customer's key, or nil when nobody has been chosen yet.
    ///
    /// This is what gates saving. A typed name that matches nobody is not a
    /// customer, and letting it through is how "Ahmed", "ahmed " and "Ahmd" become
    /// three people with three balances — the thing the roster exists to stop.
    private(set) var customerKey: String?

    var payMode: PayMode = .full
    /// Held as text so a half-typed amount is representable.
    var paidText: String = ""

    /// What the bill came to, typed rather than computed.
    ///
    /// The ordinary case in this shop: the paper bill was written before the app
    /// was opened, so the figure is already known and rebuilding it product by
    /// product to arrive at it is work for nothing. Held as text rather than a
    /// number so a half-typed "45" is not a bill for forty-five riyals.
    ///
    /// Ignored the moment there are lines — see `total`. Two answers to "what did
    /// it come to" is one too many, and the lines are the ones with arithmetic
    /// behind them.
    var amountText: String = ""

    /// The number of the bill being corrected, or nil when this is a new one.
    ///
    /// It decides which store call Save makes and it is passed to the duplicate
    /// check as `exceptNumber` — without that, opening bill 1024 to fix its date
    /// would be told 1024 is already taken, by itself.
    private(set) var editing: Int?

    var isEditing: Bool { editing != nil }

    /// The number written on the paper bill, when the shop wrote one. Free text:
    /// bill books are numbered "1024" in some shops and "A-1024" in others.
    var invoiceNo: String = ""

    /// What the bill was for, in the owner's words.
    ///
    /// Optional, and the only free text on this form that the customer ever
    /// reads: it prints under the invoice reference on their statement.
    var note: String = ""

    /// When the sale happened, which is not always when it is being typed. A shop
    /// entering the day's book at closing time would otherwise stamp the lot at
    /// once, and the statements would inherit it.
    var soldAt: Date = .now

    /// Photographs of the paper bill, taken while it is being written.
    ///
    /// Ids, not pictures — the files are already on disk by the time one lands
    /// here, written by `PhotoStore`. Held on the form rather than attached
    /// straight away because on a new bill there is nothing to attach them to
    /// yet: the bill does not exist until Save.
    ///
    /// A photograph taken against a bill that is then abandoned leaves a file
    /// nothing refers to, which the sweep collects on the next launch. That is
    /// the right way round — the alternative is deleting a picture the owner
    /// might have meant to keep.
    private(set) var photoIDs: [String] = []

    func addPhoto(_ id: String) {
        guard !photoIDs.contains(id) else { return }
        photoIDs.append(id)
    }

    func removePhoto(_ id: String) {
        photoIDs.removeAll { $0 == id }
    }

    var isEmpty: Bool { lines.isEmpty }

    /// What the bill comes to: the typed figure until something is on it, the sum
    /// of the lines from then on.
    ///
    /// The same rule `StockbookStore.saveBill` applies to what it is handed, said
    /// once more here because the screen has to show the figure it is about to
    /// save. If these two ever disagree the owner is looking at one number and
    /// saving another.
    var total: Double {
        lines.isEmpty ? (typedAmount ?? 0) : lines.reduce(0) { $0 + $1.lineTotal }
    }

    /// The figure in the amount box, or nil when there is nothing readable in it.
    var typedAmount: Double? { Money.parse(amountText) }

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

    /// The gate on this screen: a bill needs a figure, somebody **chosen** to
    /// give it to, and a number.
    ///
    /// A figure rather than a line: what was sold is optional, and a bill saying
    /// only that Ahmed owes 450 is the shape of this shop. What it may never be is
    /// a bill for nothing — `total` above zero is the whole of that test, however
    /// the figure was arrived at.
    ///
    /// Not merely a non-blank name: a name nobody picked from the list is a name
    /// with no account behind it, so nothing could be owed against it or settled
    /// off it later.
    ///
    /// The number is required because the shop writes one on every bill it hands
    /// over, and a record with none cannot be matched to the paper it came from —
    /// which is the whole reason for keeping the number at all. Always typed,
    /// never suggested: a guessed next value is the app inventing a run the
    /// paper does not have.
    var canSave: Bool { customerKey != nil && !invoiceNo.isBlank && total > 0 }

    // MARK: Mutation

    /// Typed into the field. Invalidates any earlier choice, deliberately.
    func typeCustomer(_ text: String) {
        customer = text
        // Choosing Ahmed and then editing the text must not save a bill against
        // Ahmed's account under a name that is no longer his.
        customerKey = nil
    }

    /// Chosen from the list. Takes the roster's spelling, not whatever was typed.
    func selectCustomer(_ chosen: Customer) {
        customer = chosen.name
        customerKey = chosen.key
    }

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

    /// Empties the bill of items, putting the figure back in the owner's hands.
    /// The way back from a total the app worked out to one they type.
    func removeLines() {
        lines = []
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

    /// Fills the cart from a bill already saved, so it can be corrected on the
    /// form it was typed on.
    ///
    /// A line whose product has since been deleted is **dropped**, because there
    /// is nothing left to price it against — and because `saveBill` and
    /// `updateBill` would drop it anyway. Better it is missing from the form the
    /// owner is looking at than missing only from what gets saved.
    @MainActor
    func load(_ bill: Bill, in store: StockbookStore) {
        // Nothing worth keeping is not worth restoring: a form the owner has not
        // touched should come back empty rather than with a stale prefilled
        // number in it.
        stashed = (!lines.isEmpty || customerKey != nil || typedAmount != nil) ? snapshot() : nil

        editing = bill.number
        lines = bill.lines.compactMap { line in
            guard let uid = line.productUID, let product = store.product(uid: uid) else { return nil }
            return Line(
                productUID: uid,
                name: line.name,
                qty: line.qty,
                price: line.price,
                basePrice: product.price
            )
        }
        // A bill entered as a figure has no lines to sum, so the total goes back
        // into the box it was typed into.
        amountText = bill.isItemised ? "" : Money.amount(bill.total, in: store.settings.currency)
        customer = bill.who
        customerKey = Customer.key(for: bill.who)
        payMode = bill.paid == nil ? .full : .part
        paidText = bill.paid.map { Money.amount($0, in: store.settings.currency) } ?? ""
        invoiceNo = bill.invoiceNo ?? ""
        note = bill.note ?? ""
        soldAt = bill.createdAt
        // The ones already on it, so a correction can take one off as well as add
        // one. What the form ends up holding is reconciled against the bill on
        // the way out.
        photoIDs = bill.photoIDs
    }

    /// Everything the form was holding, so a correction can borrow the screen and
    /// give it back.
    private struct Draft {
        let lines: [Line]
        let amountText: String
        let invoiceNo: String
        let note: String
        let soldAt: Date
        let customer: String
        let customerKey: String?
        let payMode: PayMode
        let paidText: String
        let photoIDs: [String]
    }

    /// The half-typed bill a correction interrupted, if there was one.
    ///
    /// Editing reuses this form, which used to mean the bill in progress was
    /// thrown away the moment somebody tapped Edit — a real loss, with no warning
    /// and no way back. It is kept here instead and restored when the correction
    /// ends, however it ends.
    private var stashed: Draft?

    private func snapshot() -> Draft {
        Draft(
            lines: lines,
            amountText: amountText,
            invoiceNo: invoiceNo,
            note: note,
            soldAt: soldAt,
            customer: customer,
            customerKey: customerKey,
            payMode: payMode,
            paidText: paidText,
            photoIDs: photoIDs
        )
    }

    /// Puts back whatever a correction interrupted, or empties the form when it
    /// interrupted nothing.
    ///
    /// Called however a correction ends — saved, or abandoned — because the owner
    /// who had half a bill typed wants it back either way.
    func release() {
        guard let draft = stashed else {
            clear()
            return
        }
        stashed = nil
        lines = draft.lines
        amountText = draft.amountText
        invoiceNo = draft.invoiceNo
        note = draft.note
        soldAt = draft.soldAt
        customer = draft.customer
        customerKey = draft.customerKey
        payMode = draft.payMode
        paidText = draft.paidText
        photoIDs = draft.photoIDs
        editing = nil
    }

    func clear() {
        stashed = nil
        lines = []
        amountText = ""
        customer = ""
        customerKey = nil
        payMode = .full
        paidText = ""
        invoiceNo = ""
        note = ""
        soldAt = .now
        photoIDs = []
        editing = nil
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
