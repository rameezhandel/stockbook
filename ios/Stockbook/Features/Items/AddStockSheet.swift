import SwiftUI

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
    @State private var quantity = ""
    @State private var unitCost = ""
    /// What was typed into the product box, and which product was actually
    /// chosen. Only a choice counts: a typed name matching nothing is not a
    /// product, and stock cannot arrive against it.
    @State private var productText = ""
    @State private var chosenProduct: Product?
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

    private var quantityValue: Int {
        Int(quantity.trimmed) ?? Int(Money.parse(quantity) ?? 0)
    }

    /// Itemised means a product **and** a count of it. A product with no quantity
    /// is half an answer, and guessing the other half would put stock on the shelf
    /// nobody said arrived.
    private var isItemised: Bool { chosenProduct != nil && quantityValue > 0 }

    /// What the owner counted, or nil while the box says nothing readable — which
    /// is what stops an empty box reading as "there are none".
    private var countValue: Int? { Int(count.trimmed) }

    /// What the delivery is costed at, worked out the same way `recordPurchase`
    /// works it out — or the sheet shows a total the store does not save.
    private var costValue: Double {
        let typed = Money.parse(unitCost) ?? 0
        return typed > 0 ? typed : (chosenProduct?.cost ?? 0)
    }

    private var totalValue: Double {
        isItemised ? Double(quantityValue) * costValue : (Money.parse(amountText) ?? 0)
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
            if isPurchase, !isItemised {
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

            // Which product arrived, where the shop keeps a count of it. Optional,
            // and labelled so: a bill for a mixed load, or for a delivery charge,
            // names nothing and still owes money.
            if isPurchase, product == nil {
                ProductPicker(typed: $productText, chosen: $chosenProduct)
            }

            if chosenProduct != nil {
                HStack(spacing: 8) {
                    NocturneField.number(label: Loc.howMany, text: $quantity)
                    if isPurchase {
                        NocturneField.number(
                            label: Loc.paidPerPiece,
                            text: $unitCost,
                            // Marked only once it is the thing standing between
                            // the owner and a saved bill: with a quantity typed
                            // and no price on either the box or the product,
                            // there is no figure at all.
                            isRequiredAndEmpty: isItemised && totalValue <= 0
                        )
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

        // The product the sheet was opened for, or the one the delivery being
        // corrected named — nil when that product has since been deleted, which
        // is why a delivery can come back as a bare figure.
        chosenProduct = editing?.productUID.flatMap { store.product(uid: $0) } ?? product
        productText = chosenProduct?.name ?? ""

        guard let editing else { return }
        supplier = store.supplier(key: editing.supplierKey)?.name ?? editing.supplierKey
        supplierKey = editing.supplierKey
        invoiceNo = editing.invoiceNo ?? ""
        arrivedAt = editing.createdAt
        settledNow = editing.paid == nil
        paidText = editing.paid.map { Money.amount($0, in: currency) } ?? ""
        if editing.isItemised {
            quantity = String(editing.qty)
            unitCost = Money.amount(editing.unitCost, in: currency)
        } else {
            // A supplier bill entered as a figure has no lines to sum, so the
            // total goes back into the box it was typed into.
            amountText = Money.amount(editing.total, in: currency)
        }
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

    /// Zero or empty quantity just closes the sheet — the owner opened it, then
    /// thought better of it, and that should not need a Cancel button.
    private func confirm() {
        if isPurchase {
            guard let key = supplierKey else { return }
            let paid: Double? = settledNow ? nil : (Money.parse(paidText) ?? 0)
            if let editing {
                // Which of the two shapes this ends up as is the store's rule to
                // apply, not the sheet's: a quantity of zero makes it a figure
                // however the boxes were filled in.
                store.updatePurchase(
                    id: editing.id,
                    product: chosenProduct,
                    supplierKey: key,
                    quantity: quantityValue,
                    unitCost: Money.parse(unitCost) ?? 0,
                    paid: paid,
                    amount: Money.parse(amountText),
                    createdAt: arrivedAt,
                    invoiceNo: invoiceNo
                )
            } else if isItemised {
                // Stock arriving, against an account and a piece of paper — which
                // is why this is not `restock` with a supplier string that went
                // nowhere.
                store.recordPurchase(
                    product: chosenProduct,
                    supplierKey: key,
                    quantity: quantityValue,
                    unitCost: Money.parse(unitCost) ?? 0,
                    paid: paid,
                    createdAt: arrivedAt,
                    invoiceNo: invoiceNo
                )
            } else {
                // Money owed and nothing on the shelf to show for it. The same
                // record either way, deliberately: a statement should not care
                // which way a supplier's bill was entered.
                store.recordSupplierBill(
                    supplierKey: key,
                    amount: totalValue,
                    paid: paid,
                    createdAt: arrivedAt,
                    invoiceNo: invoiceNo
                )
            }
        }
        router.addStock = nil
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
/// have a product. It cannot create one either: a product carries a buying price,
/// a selling price and a count, and inventing all three from a delivery sheet is
/// how a catalogue fills up with half-made entries.
private struct ProductPicker: View {
    @Binding var typed: String
    @Binding var chosen: Product?

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    private static let rowHeight: CGFloat = 35
    private static let maxListHeight: CGFloat = 150

    private var matches: [Product] {
        guard chosen == nil else { return [] }
        return store.products(matching: typed)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NocturneField(
                label: Loc.whichProductArrived,
                placeholder: Loc.optionalField,
                text: Binding(get: { typed }, set: { typed = $0; chosen = nil }),
                identifier: "purchase.product"
            )

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
                                    // The buying price, not the selling one: this
                                    // list exists to start a delivery, and that is
                                    // the figure about to be typed over.
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
                .padding(.vertical, 3)
                .frame(maxWidth: .infinity)
                .background(Nocturne.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            }
        }
    }

    private func choose(_ product: Product) {
        typed = product.name
        chosen = product
        dismissKeyboard()
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
