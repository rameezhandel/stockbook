import SwiftUI

/// One line of the delivery being entered, held as text while it is being typed.
///
/// Both numbers are held as **text**, so a half-typed value ("1." or "") is
/// representable and the field is never re-formatted under the thumb that is
/// typing into it — the lesson the bill's own line card records.
///
/// `fallbackCost` is what the product already costs, which is what the store
/// falls back to when the box is empty. Held here so the sheet works the total
/// out the way the store will: without it, clearing the box would show a total of
/// nothing over a Save about to write the old price.
@Observable
private final class DeliveryLine: Identifiable {
    let id = UUID()
    let productUID: UUID
    let name: String
    let fallbackCost: Double
    var qtyText: String
    var costText: String

    init(productUID: UUID, name: String, qty: Int, cost: Double, fallbackCost: Double, in currency: Currency) {
        self.productUID = productUID
        self.name = name
        self.fallbackCost = fallbackCost
        self.qtyText = String(qty)
        self.costText = cost > 0 ? Money.amount(cost, in: currency) : ""
    }

    var qty: Int { max(0, Int(qtyText.trimmed) ?? 0) }
    var cost: Double {
        let typed = Money.parse(costText) ?? 0
        return typed > 0 ? typed : fallbackCost
    }
    var lineTotal: Double { Double(qty) * cost }
}

/// A supplier's bill, and the shelf count beside it.
///
/// **Set count** is what the owner types after looking at a shelf — "there are
/// twelve", never "add twelve". It is the honest action to leave next to a count
/// that only moves for bills somebody itemised.
///
/// **Supplier bill** is the money side, and saying what arrived is optional on it
/// exactly the way saying what was sold is optional on a sale. Name a product
/// *and* a quantity and the stock arrives and the buying price takes over; name
/// neither and the bill is a figure against the account, which is what a mixed
/// load or a delivery charge actually is.
///
/// A delivery names a product and a count of it; a supplier's bill for a mixed
/// load names neither and is a figure against the account. `product` being nil is
/// what tells the two apart, and it is also the shape a saved one is **reopened**
/// in — `editing` carries the record being rewritten, and saving then moves the
/// shelf by the difference rather than recording a second delivery.
struct AddStockSheet: View {
    /// What arrived, where the shop keeps a count of it. Nil for a supplier bill
    /// that names no product — a mixed load, or something that never sits on a
    /// shelf. Nil is also what says which halves this sheet can show: there is no
    /// shelf to top up without one.
    let product: Product?
    /// Opened from the Delivery button rather than from a product's own sheet.
    var startInPurchase = false
    /// The delivery being corrected, or nil when this is a new one.
    var editing: Purchase?

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// Which half is showing. A plain flag rather than a borrowed domain enum:
    /// the store has no "modes" any more, only setStock and the two ways of
    /// recording a supplier's bill.
    @State private var supplierBill = false
    @State private var seeded = false
    /// What the owner counted on the shelf. Its own box, because "there are 12"
    /// and "add 12" are the same keystrokes and different shelves.
    @State private var count = ""
    /// What arrived, one entry per line on the paper.
    @State private var lines: [DeliveryLine] = []
    /// What has been typed into the product box while looking for the next line.
    @State private var productQuery = ""
    @State private var addingLine = false
    /// What the supplier's bill came to, where no product and count say what it
    /// is made of. Held as text so a half-typed figure is representable.
    @State private var amountText = ""
    /// What was typed into the supplier box, and who was actually chosen.
    @State private var supplier = ""
    @State private var supplierKey: String?
    @State private var settledNow = true
    @State private var paidText = ""
    /// The number on the supplier's invoice, and the day it arrived.
    @State private var invoiceNo = ""
    @State private var arrivedAt = Date.now

    /// The delivery half.
    ///
    /// Forced where there is no shelf to count, and where a saved delivery is
    /// being corrected: in neither case is Quick add something this sheet can do.
    /// Derived rather than left to `onAppear` to set the mode, or the first frame
    /// draws the wrong form and the sheet visibly changes its mind.
    private var isPurchase: Bool {
        product == nil || editing != nil || supplierBill
    }

    /// Only where there is a shelf to top up, and never while correcting: a
    /// delivery being rewritten is a delivery, and offering "Quick add" beside it
    /// would be offering to do something else entirely.
    private var showsModePills: Bool { product != nil && editing == nil }

    /// The lines with a count on them. A product with no quantity is half an
    /// answer, and guessing the other half would put stock on the shelf nobody
    /// said arrived.
    private var liveLines: [DeliveryLine] { lines.filter { $0.qty > 0 } }

    /// Itemised means at least one line that says how many arrived.
    private var isItemised: Bool { !liveLines.isEmpty }

    /// What the owner counted, or nil while the box says nothing readable — which
    /// is what stops an empty box reading as "there are none".
    private var countValue: Int? { Int(count.trimmed) }

    /// What the delivery is costed at, worked out the same way `recordPurchase`
    /// works it out — or the sheet shows a total the store does not save.
    private var totalValue: Double {
        isItemised
            ? liveLines.reduce(0) { $0 + $1.lineTotal }
            : (Money.parse(amountText) ?? 0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.fieldGap) {
            SheetHeader(
                // "Add stock" over a box asking what you counted is the exact
                // sentence that would make somebody type the number they are
                // adding rather than the number on the shelf.
                title: isPurchase ? Loc.supplierBillTitle : Loc.setCount,
                subtitle: product.map { Loc.onShelfNow(product: $0.name, stock: $0.stock) }
            ) {
                router.addStock = nil
            }

            if showsModePills {
                modePills
            }

            if isPurchase {
                SupplierPicker(
                    typed: $supplier,
                    chosenKey: $supplierKey
                )

                // The paper that came with the stock, and the day it came. The
                // number is required here as it is on a bill: a delivery filed
                // under no number cannot be matched to the invoice in the drawer,
                // which is the one thing the supplier will quote on the phone.
                HStack(alignment: .bottom, spacing: 8) {
                    NocturneField(
                        label: Loc.invoiceNoField,
                        placeholder: Loc.invoiceNoHint,
                        text: $invoiceNo,
                        // Digits first, letters a tap away — the same keyboard
                        // every other typed number in the app gets.
                        keyboard: .numbersAndPunctuation,
                        isRequiredAndEmpty: invoiceNo.isBlank,
                        identifier: "purchase.invoiceNo"
                    )
                    NocturneDateField(
                        label: Loc.billDate,
                        date: $arrivedAt,
                        height: Metrics.inputHeight,
                        identifier: "purchase.arrivedAt"
                    )
                }

                if let clash {
                    Text(
                        Loc.invoiceNoAlreadyUsed(
                            who: store.supplier(key: clash.supplierKey)?.name ?? clash.supplierKey,
                            date: Loc.longDate(clash.createdAt)
                        )
                    )
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            // What the bill came to. Typed until a product and a quantity say what
            // it is made of, computed from then on — never both at once, or the
            // sheet is showing one figure and about to save another.
            if isPurchase {
                if isItemised {
                    itemisedTotal
                } else {
                    NocturneField.number(
                        label: Loc.amountField,
                        text: $amountText,
                        height: Metrics.tallInputHeight,
                        isRequiredAndEmpty: totalValue <= 0,
                        emphasis: .sellingPrice,
                        prefix: currency.symbol.trimmed,
                        fontSize: 17
                    )
                }
            }

            // What arrived, one card per line on the paper. Optional, all of it:
            // a bill for a mixed load, or for a delivery charge, names nothing and
            // still owes money.
            if isPurchase {
                ForEach(lines) { line in
                    DeliveryLineCard(line: line, onRemove: { remove(line) })
                }

                Group {
                    if addingLine {
                        ProductPicker(typed: $productQuery, onChoose: addLine)
                    } else {
                        Button(action: { addingLine = true }) {
                            Label(
                                lines.isEmpty ? Loc.addItems : Loc.addAnotherItem,
                                systemImage: Icon.add
                            )
                        }
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                    }
                }
            }

            // Was it paid for at the door? Usually yes — the driver waits — so that
            // is the default, and the alternative is one tap rather than a number
            // somebody has to type to mean "nothing owed".
            if isPurchase {
                HStack(spacing: 6) {
                    ChoicePill(title: Loc.paidInFull, icon: Icon.money, selected: settledNow) {
                        settledNow = true
                    }
                    ChoicePill(title: Loc.partPayment, icon: Icon.partPayment, selected: !settledNow) {
                        settledNow = false
                    }
                }

                if !settledNow {
                    NocturneField.number(
                        label: Loc.paidNow,
                        text: $paidText,
                        prefix: currency.symbol.trimmed
                    )
                }
            }

            if !isPurchase, let product {
                NocturneField.number(
                    label: Loc.inStock,
                    text: $count,
                    isRequiredAndEmpty: countValue == nil
                )
                // The whole of the difference between this and what it replaced,
                // said where the mistake would be made: "add 5" and "there are 5"
                // are the same keystrokes and different shelves.
                Text(Loc.setCountNote)
                    .nocturneText(.meta)
                    .padding(.top, 2)

                Button(Loc.setCount) {
                    // A count is *set*, never added to. Nothing here reaches
                    // `restock`, which is the function that would turn "there are
                    // twelve" into twelve more.
                    store.setStock(product, count: countValue ?? 0)
                    router.addStock = nil
                }
                .buttonStyle(.primaryBlock)
                .disabled(countValue == nil)
                .padding(.top, 6)
            } else {
                if !note.isEmpty {
                    Text(note)
                        .nocturneText(.meta)
                        .padding(.top, 2)
                }

                Button(actionLabel, action: confirm)
                    .buttonStyle(.primaryBlock)
                    .disabled(!canSave)
                    .padding(.top, 6)
            }
        }
        .keyboardDoneButton()
        .onAppear(perform: seed)
    }

    /// Once, and only once: re-seeding on every appearance would fight an owner
    /// who switched to Quick add, or who cleared a box the delivery had filled in.
    private func seed() {
        guard !seeded else { return }
        seeded = true

        // The Delivery button asked for the purchase half by name. The other two
        // ways of reaching it are derived in `isPurchase` rather than set here.
        if startInPurchase { supplierBill = true }

        // What the delivery said arrived, for as many of those products as are
        // still on the books — a purchase can outlive what it bought, and the
        // sheet cannot offer a quantity of something that no longer exists. A
        // delivery whose every product has gone comes back as a bill for a figure,
        // which is what saving it again would make it anyway.
        if let editing {
            lines = editing.items.compactMap { item in
                guard let uid = item.productUID, let onShelf = store.product(uid: uid) else { return nil }
                return DeliveryLine(
                    productUID: uid,
                    name: item.name,
                    qty: item.qty,
                    cost: item.unitCost,
                    fallbackCost: onShelf.cost,
                    in: currency
                )
            }
        } else if let product {
            // Opened from a product: that product is what the owner is looking
            // at, so it is line one already.
            lines = [
                DeliveryLine(
                    productUID: product.uid,
                    name: product.name,
                    qty: 1,
                    cost: product.cost,
                    fallbackCost: product.cost,
                    in: currency
                )
            ]
        }

        guard let editing else { return }
        supplier = store.supplier(key: editing.supplierKey)?.name ?? editing.supplierKey
        supplierKey = editing.supplierKey
        invoiceNo = editing.invoiceNo ?? ""
        arrivedAt = editing.createdAt
        settledNow = editing.paid == nil
        paidText = editing.paid.map { Money.amount($0, in: currency) } ?? ""
        if !editing.isItemised {
            // A supplier bill entered as a figure has no lines to sum, so the
            // total goes back into the box it was typed into.
            amountText = Money.amount(editing.total, in: currency)
        }
    }

    /// What the lines add up to, above the lines themselves.
    ///
    /// `CartView.itemisedTotal` without the discount rows — a delivery note has no
    /// discount to take off — and otherwise the same block in the same place, so
    /// the figure the shop is about to commit to reads the same size on both
    /// sides of the counter. The delivery sheet used to show no total at all once
    /// a product was named: what it came to was a sentence beside the Save
    /// button, which is not where anyone looks for a number.
    private var itemisedTotal: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .lastTextBaseline) {
                Text(Loc.total)
                    .font(NocturneType.inter(13))
                    .foregroundStyle(Nocturne.neutral500)
                Spacer()
                Text(Money.text(totalValue, in: currency))
                    .nocturneText(.bigNumber(26))
                    .rollingNumber(totalValue)
            }
            HStack {
                Text(Loc.fromItems(liveLines.count))
                    .nocturneText(.meta)
                Spacer()
                Button(Loc.removeItems) { lines.removeAll() }
                    .buttonStyle(.plain)
                    .font(NocturneType.inter(12))
                    .foregroundStyle(Nocturne.accent)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(Nocturne.accent700, radius: Metrics.controlRadius)
    }

    private var modePills: some View {
        HStack(spacing: 8) {
            pill(Loc.setCount, active: !isPurchase) { supplierBill = false }
            pill(Loc.supplierBillTitle, active: isPurchase) { supplierBill = true }
        }
    }

    private func pill(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(NocturneType.inter(13, .medium))
                .foregroundStyle(active ? Nocturne.accent : Nocturne.neutral500)
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.compactControlHeight)
                .hairline(active ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.pillRadius)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Said only where it is true: the buying price takes over when stock actually
    /// arrived, and a bill naming no product changes no price.
    private var note: String {
        guard isItemised else { return "" }
        return Loc.purchaseNote(billTotal: Money.text(totalValue, in: currency))
    }

    /// The delivery already filed under this number, whoever it came from. Across
    /// the whole book rather than per supplier: one number, one piece of paper.
    ///
    /// The delivery being corrected is excluded from its own check, or reopening
    /// INV-88 to change its date would be told INV-88 is already taken, by itself.
    private var clash: Purchase? {
        store.purchaseWithInvoiceNo(invoiceNo, exceptId: editing?.id)
    }

    private var actionLabel: String {
        if clash != nil { return Loc.changeTheInvoiceNo }
        if supplierKey == nil { return supplier.isBlank ? Loc.whoDeliveredIt : Loc.chooseSupplierFromTheList }
        if invoiceNo.isBlank { return Loc.enterBillNumber }
        if totalValue <= 0 { return Loc.enterAnAmount }
        return editing == nil ? Loc.recordPurchase : Loc.saveChanges
    }

    /// A supplier's bill is a record against an account, against a piece of paper
    /// and for a figure, so it needs all three. Quick add is none of them: it is a
    /// correction to a count on a shelf, and demanding a supplier would be asking
    /// who delivered the bag you just tipped in.
    private var canSave: Bool {
        guard isPurchase else { return true }
        return supplierKey != nil && !invoiceNo.isBlank && totalValue > 0 && clash == nil
    }

    /// Choosing something already on the note adds one more of it rather than a
    /// second card saying the same name — the rule the cart and the credit note
    /// both follow.
    private func addLine(_ product: Product) {
        if let existing = lines.first(where: { $0.productUID == product.uid }) {
            existing.qtyText = String(existing.qty + 1)
        } else {
            lines.append(
                DeliveryLine(
                    productUID: product.uid,
                    name: product.name,
                    qty: 1,
                    cost: product.cost,
                    fallbackCost: product.cost,
                    in: currency
                )
            )
        }
        productQuery = ""
        addingLine = false
    }

    private func remove(_ line: DeliveryLine) {
        lines.removeAll { $0.id == line.id }
    }

    /// Zero or empty quantity just closes the sheet — the owner opened it, then
    /// thought better of it, and that should not need a Cancel button.
    private func confirm() {
        if isPurchase {
            guard let key = supplierKey else { return }
            let paid: Double? = settledNow ? nil : (Money.parse(paidText) ?? 0)
            // Which of the two shapes this ends up as is the store's rule to
            // apply, not the sheet's: lines are stock arriving, no lines is a
            // figure against the account, and the amount is ignored where there is
            // arithmetic instead. Money owed with nothing on the shelf to show for
            // it is the same record deliberately, because a statement should not
            // care which way a supplier's bill was entered.
            let drafts = liveLines.map {
                DraftPurchaseLine(productUID: $0.productUID, qty: $0.qty, unitCost: $0.cost)
            }
            if let editing {
                store.updatePurchase(
                    id: editing.id,
                    lines: drafts,
                    supplierKey: key,
                    paid: paid,
                    amount: totalValue,
                    createdAt: arrivedAt,
                    invoiceNo: invoiceNo
                )
            } else {
                store.recordPurchase(
                    lines: drafts,
                    supplierKey: key,
                    paid: paid,
                    amount: totalValue,
                    createdAt: arrivedAt,
                    invoiceNo: invoiceNo
                )
            }
        }
        router.addStock = nil
    }
}

/// One line of the delivery: what it was, how many, and what each cost.
///
/// `ReturnedLineCard`'s shape on the credit note sheet, deliberately — the two are
/// the same act pointed in opposite directions, and a shopkeeper who has entered
/// one should recognise the other.
private struct DeliveryLineCard: View {
    @Bindable var line: DeliveryLine
    let onRemove: () -> Void

    @Environment(\.currency) private var currency

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(line.name)
                    .nocturneText(.rowPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                Text(Money.text(line.lineTotal, in: currency))
                    .font(NocturneType.inter(15))
                    .rollingNumber(line.lineTotal)
                Button(action: onRemove) {
                    Glyph(Icon.delete, size: 15)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.remove(line.name))
            }
            .padding(.bottom, 10)

            HStack(spacing: 8) {
                NocturneField.number(
                    label: Loc.howMany,
                    text: $line.qtyText,
                    height: Metrics.compactControlHeight,
                    // Marked while it is the thing standing between this line and
                    // the shelf: a line with no count on it is not saved at all,
                    // and the owner should see which one it is rather than wonder
                    // why the total is short.
                    isRequiredAndEmpty: line.qty <= 0,
                    fontSize: 13.5
                )
                NocturneField.number(
                    label: Loc.paidPerPiece,
                    text: $line.costText,
                    height: Metrics.compactControlHeight,
                    // Only where leaving it empty would leave no figure at all: an
                    // emptied box on a product that already has a cost means "the
                    // same as last time", which is a real answer.
                    isRequiredAndEmpty: line.qty > 0 && line.cost <= 0,
                    prefix: currency.symbol.trimmed,
                    fontSize: 13.5
                )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }
}

/// Who delivered it: type to filter the roster, then **choose**.
///
/// The cart's customer picker, on the other side of the counter and for the same
/// reason — a typed name is not an account, and a delivery filed against one that
/// does not exist is money the shop cannot see it owes. It cannot block the work
/// either: a name matching nobody offers to become a supplier in the same tap.
///
/// The list is drawn **below** the field here, unlike the cart's. This sheet
/// grows downwards with room under it; the cart's field sits on the bottom edge
/// of the screen with the keyboard beneath it.
/// Which product arrived: type to filter the shelf, then **choose**.
///
/// The supplier picker below, pointed at the catalogue, and optional where that
/// one is required — a supplier's bill always has a supplier, and does not always
/// have a product.
///
/// It **can** create one, which it once could not: the old note here said a
/// product carries a buying price, a selling price and a count, and that inventing
/// all three from a delivery sheet is how a catalogue fills up with half-made
/// entries. Only one of those three is invented. What it cost is the box on the
/// line below, the count is what the delivery is about to add, and the selling
/// price is left at nothing — a question a delivery note does not answer, and one
/// the Items screen already flags. Against that: a supplier's paper is exactly
/// where stock the shop has never carried turns up, and without this a new line
/// meant leaving the sheet and losing the half-typed delivery.
private struct ProductPicker: View {
    @Binding var typed: String
    let onChoose: (Product) -> Void

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    private static let rowHeight: CGFloat = 35
    private static let maxListHeight: CGFloat = 150

    private var matches: [Product] { store.products(matching: typed) }

    /// Only on an exact-name miss, so it never offers to create a second "Cisa
    /// lock" while the first one is sitting in the list above it.
    private var canCreate: Bool {
        let needle = typed.trimmed.lowercased()
        return !needle.isEmpty && !store.products.contains { $0.name.trimmed.lowercased() == needle }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NocturneField(
                label: Loc.whichProductArrived,
                placeholder: Loc.optionalField,
                text: $typed,
                identifier: "purchase.product"
            )

            if !matches.isEmpty || canCreate {
                VStack(spacing: 0) {
                    if !matches.isEmpty {
                        ScrollView {
                            VStack(spacing: 0) {
                                ForEach(matches) { product in
                                    Button { choose(product) } label: {
                                        HStack(spacing: 8) {
                                            Glyph(Icon.items, size: 13)
                                                .foregroundStyle(Nocturne.neutral500)
                                            Text(product.name)
                                                .font(NocturneType.inter(13.5))
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                .lineLimit(1)
                                            Text(Loc.stockLabel(product.stock))
                                                .font(NocturneType.inter(11))
                                                .foregroundStyle(Nocturne.neutral500)
                                            // The buying price, not the selling
                                            // one: this list exists to start a
                                            // delivery, and that is the figure
                                            // about to be typed over.
                                            Text(Money.text(product.cost, in: currency))
                                                .font(NocturneType.inter(11))
                                                .foregroundStyle(Nocturne.accent400)
                                        }
                                        .padding(.horizontal, 11)
                                        .frame(height: Self.rowHeight)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .frame(height: min(CGFloat(matches.count) * Self.rowHeight, Self.maxListHeight))
                        .scrollBounceBehavior(.basedOnSize)
                    }

                    // Outside the scrolling part: it is the way out when nothing
                    // matches, and must never be something to scroll for.
                    if canCreate {
                        Button(action: create) {
                            HStack(spacing: 8) {
                                Glyph(Icon.add, size: 12)
                                    .foregroundStyle(Nocturne.accent)
                                Text(Loc.addAsProduct(typed.trimmed))
                                    .font(NocturneType.inter(13.5))
                                    .foregroundStyle(Nocturne.accent)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 11)
                            .frame(height: Self.rowHeight)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 3)
                .frame(maxWidth: .infinity)
                .background(Nocturne.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            }
        }
    }

    private func choose(_ product: Product) {
        typed = ""
        dismissKeyboard()
        onChoose(product)
    }

    /// No cost and no selling price: the line this becomes carries what it cost,
    /// and what it sells for is not a question a delivery note answers.
    private func create() {
        choose(store.addProduct(name: typed.trimmed, stock: 0, cost: 0, price: 0))
    }
}

private struct SupplierPicker: View {
    @Binding var typed: String
    @Binding var chosenKey: String?

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    private static let rowHeight: CGFloat = 35
    private static let maxListHeight: CGFloat = 150

    private var query: String { Supplier.key(for: typed) }

    private var matches: [Supplier] {
        guard chosenKey == nil else { return [] }
        return store.suppliers().filter { query.isEmpty || $0.key.contains(query) }
    }

    private var canCreate: Bool {
        chosenKey == nil && !query.isEmpty && !store.suppliers().contains { $0.key == query }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NocturneField(
                label: Loc.supplier,
                placeholder: Loc.whoDeliveredIt,
                text: Binding(get: { typed }, set: { typed = $0; chosenKey = nil }),
                // Marked until somebody is actually chosen, not merely until the
                // box has characters in it.
                isRequiredAndEmpty: chosenKey == nil,
                identifier: "purchase.supplier"
            )

            if !matches.isEmpty || canCreate {
                VStack(spacing: 0) {
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(matches) { candidate in
                                Button { choose(candidate) } label: {
                                    HStack(spacing: 8) {
                                        Glyph(Icon.customer, size: 13)
                                            .foregroundStyle(Nocturne.neutral500)
                                        Text(candidate.name)
                                            .font(NocturneType.inter(13.5))
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .lineLimit(1)
                                        Text(candidate.meta(in: currency, strings: Loc))
                                            .font(NocturneType.inter(11))
                                            .foregroundStyle(candidate.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                                    }
                                    .padding(.horizontal, 11)
                                    .frame(height: Self.rowHeight)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .frame(height: min(CGFloat(matches.count) * Self.rowHeight, Self.maxListHeight))
                    .scrollBounceBehavior(.basedOnSize)

                    // Outside the scrolling part: it is the way out when nobody
                    // matches, and must never be something to scroll for.
                    if canCreate {
                        Button {
                            guard let record = store.addSupplier(name: typed.trimmed),
                                  let created = store.supplier(key: record.key)
                            else { return }
                            choose(created)
                        } label: {
                            HStack(spacing: 8) {
                                Glyph(Icon.add, size: 13)
                                Text(Loc.addAsSupplier(typed.trimmed))
                                    .font(NocturneType.inter(13.5))
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .foregroundStyle(Nocturne.accent)
                            .padding(.horizontal, 11)
                            .frame(height: Self.rowHeight)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 3)
                .frame(maxWidth: .infinity)
                .background(Nocturne.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            }
        }
    }

    private func choose(_ supplier: Supplier) {
        typed = supplier.name
        chosenKey = supplier.key
        dismissKeyboard()
    }
}
