import SwiftUI

/// One line of a catalogue being entered, held as text while it is being typed.
///
/// The setup wizard's own draft, which is where this flow comes from — step 3
/// takes the names and step 4 takes the three numbers. A sheet has no steps, so
/// it does both at once: a name becomes a card, and the card carries its numbers.
private struct ProductDraft: Identifiable {
    let id = UUID()
    var name: String
    var stock = ""
    var cost = ""
    var price = ""

    var isComplete: Bool {
        StockbookStore.isProductDraftComplete(name: name, stock: stock, cost: cost, price: price)
    }
}

/// Enter products, or correct one.
///
/// **Creating takes as many as the owner has to hand**; correcting takes exactly
/// one. That is not a fork for its own sake: a shop enters a catalogue in
/// handfuls — a new supplier's line, a delivery of things never carried before —
/// and one sheet per product was one Save, one close and one re-open each time.
/// Correcting is the opposite errand: one product, whose name is already known,
/// whose price is being changed on purpose.
///
/// This sheet is the reference implementation of the app's validation rule:
/// **no error toasts, no red text**. An incomplete draft simply leaves the Save
/// button disabled and puts an accent border on whatever is still empty.
struct ProductEditorSheet: View {
    /// `nil` for "New product", which is the many-at-once half.
    let product: Product?

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart
    @Environment(\.currency) private var currency

    // Correcting one. No stock: the opening count is asked once, when the
    // product is created, and creating happens on the other half of this sheet.
    @State private var name = ""
    @State private var cost = ""
    @State private var price = ""
    @State private var loaded = false

    // Entering several.
    @State private var drafts: [ProductDraft] = []
    @State private var draftName = ""
    /// One `FocusState` for every box on the sheet, tagged per card. Declared
    /// here rather than per field for the reason the setup wizard declares its
    /// own here: a toolbar conditional on each field's focus is a toolbar per
    /// field, and a handful of products hung the screen.
    @FocusState private var draftFocus: String?

    private var isNew: Bool { product == nil }

    /// A correction has no count to check, so it stands in as one already
    /// there. The rule itself stays in the store, where both platforms read it.
    private var canSave: Bool {
        StockbookStore.isProductDraftComplete(name: name, stock: "0", cost: cost, price: price)
    }

    /// Nothing half-written gets saved. A card with a name and no price is a
    /// product the shop cannot sell, so it holds the Save rather than slipping
    /// through — and its own empty box is already wearing the accent border that
    /// says which one.
    private var canSaveDrafts: Bool {
        !drafts.isEmpty && drafts.allSatisfy(\.isComplete)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.fieldGap) {
            SheetHeader(title: isNew ? Loc.newProduct : Loc.editProduct) {
                router.productEditor = nil
            }

            if isNew {
                newProducts
            } else {
                oneProduct
            }
        }
        .onAppear(perform: loadDraft)
        .keyboardDoneButton()
    }

    /// Correcting one product: the name, what it costs, what it sells for, and
    /// the two things only an existing product has — a way to record a delivery
    /// against it, and a way to remove it.
    ///
    /// No opening stock. The count is asked for once, when the product is
    /// created, and never again from here. It used to sit on every edit too,
    /// which made it a second, unlabelled "Set count" one keystroke from the
    /// price boxes: fixing a miscount could rewrite a selling price, and "In
    /// stock" said nothing about whether the number was absolute or something to
    /// add. Afterwards the shelf moves for a stated reason — a delivery in, a
    /// bill out, or a recount through Set count, which says what it is.
    @ViewBuilder
    private var oneProduct: some View {
        NocturneField(
            label: Loc.productName,
            placeholder: Loc.productNameExample,
            text: $name,
            height: Metrics.tallInputHeight,
            isRequiredAndEmpty: name.isBlank,
            fontSize: 15
        )

        NocturneField.number(
            label: Loc.buyingPrice,
            text: $cost,
            isRequiredAndEmpty: cost.isBlank
        )

        NocturneField.number(
            label: Loc.sellingPrice,
            text: $price,
            isRequiredAndEmpty: (Money.parse(price) ?? 0) <= 0,
            emphasis: .sellingPrice
        )

        Text(marginNote)
            .nocturneText(.meta)
            .padding(.top, 2)

        if let product {
            HStack(spacing: 8) {
                Button(Loc.addStock) {
                    router.openAddStock(for: product)
                }
                .buttonStyle(.secondary)

                Button(Loc.save, action: save)
                    .buttonStyle(.primaryBlock)
                    .disabled(!canSave)
            }
            .padding(.top, 6)

            Button(Loc.removeThisProduct) {
                cart.dropLine(for: product)
                store.delete(product)
                router.productEditor = nil
            }
            .buttonStyle(.ghostMuted)
            .frame(maxWidth: .infinity)
            .padding(.top, 2)
        }
    }

    /// Entering a catalogue: a name becomes a card, the card carries its three
    /// numbers, and one Save writes the lot.
    ///
    /// The name box stays at the top rather than travelling to the end of the
    /// list, so the rhythm is type-return-type-return without the thumb moving.
    @ViewBuilder
    private var newProducts: some View {
        HStack(spacing: 8) {
            NocturneField(
                placeholder: Loc.productNameExample,
                text: $draftName,
                height: Metrics.tallInputHeight,
                fontSize: 15,
                identifier: "product.draftName",
                onSubmit: { addDraft() }
            )
            Button(action: addDraft) {
                Glyph(Icon.add, size: 18)
            }
            .buttonStyle(PrimaryButtonStyle(height: Metrics.tallInputHeight))
            .disabled(draftName.isBlank)
            .accessibilityLabel(Loc.newProduct)
        }

        Kicker(drafts.isEmpty ? Loc.nothingAddedYetKicker : Loc.addedCount(drafts.count))

        // Indexed rather than bound directly, because each card needs to know
        // where it sits to tag its three boxes — the same reason setup step 4
        // indexes its own.
        ForEach(drafts.indices, id: \.self) { index in
            draftCard(index: index, draft: $drafts[index])
        }

        if drafts.isEmpty {
            Text(Loc.openingStockNote).nocturneText(.meta)
        }

        Button(Loc.save, action: saveDrafts)
            .buttonStyle(.primaryBlock)
            .disabled(!canSaveDrafts)
            .padding(.top, 6)
    }

    private func draftCard(index: Int, draft: Binding<ProductDraft>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Text(draft.wrappedValue.name)
                    .nocturneText(.rowPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                Button {
                    drafts.remove(at: index)
                } label: {
                    Glyph(Icon.close, size: 15)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.remove(draft.wrappedValue.name))
            }
            .padding(.bottom, 10)

            HStack(alignment: .top, spacing: 8) {
                NocturneField.number(
                    label: Loc.openingStock,
                    text: draft.stock,
                    height: Metrics.compactControlHeight,
                    isRequiredAndEmpty: draft.wrappedValue.stock.isBlank,
                    // Marked once the box has been visited and left empty rather
                    // than on arrival: a handful of cards is a dozen outlined
                    // boxes otherwise, which reads as a dozen mistakes before the
                    // owner has made one.
                    requiredMarking: .afterTouch,
                    fontSize: 13.5,
                    focusTag: "stock-\(index)",
                    focus: $draftFocus
                )
                NocturneField.number(
                    label: Loc.buyingPrice,
                    text: draft.cost,
                    height: Metrics.compactControlHeight,
                    isRequiredAndEmpty: draft.wrappedValue.cost.isBlank,
                    requiredMarking: .afterTouch,
                    fontSize: 13.5,
                    focusTag: "cost-\(index)",
                    focus: $draftFocus
                )
                NocturneField.number(
                    label: Loc.sellingPrice,
                    text: draft.price,
                    height: Metrics.compactControlHeight,
                    isRequiredAndEmpty: (Money.parse(draft.wrappedValue.price) ?? 0) <= 0,
                    requiredMarking: .afterTouch,
                    emphasis: .sellingPrice,
                    fontSize: 13.5,
                    focusTag: "price-\(index)",
                    focus: $draftFocus
                )
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    /// A name already on the list is not a mistake worth interrupting anybody
    /// for — the same rule the setup wizard follows, and the same rule
    /// `addProduct` itself follows against the catalogue.
    private func addDraft() {
        let cleaned = draftName.trimmed
        guard !cleaned.isEmpty else { return }
        draftName = ""
        guard !drafts.contains(where: { $0.name.lowercased() == cleaned.lowercased() }) else { return }
        drafts.append(ProductDraft(name: cleaned))
    }

    private func saveDrafts() {
        guard canSaveDrafts else { return }
        for draft in drafts {
            store.addProduct(
                name: draft.name,
                stock: Int(draft.stock.trimmed) ?? Int(Money.parse(draft.stock) ?? 0),
                cost: Money.parse(draft.cost) ?? 0,
                price: Money.parse(draft.price) ?? 0
            )
        }
        router.productEditor = nil
    }

    /// `You make SAR 30 a piece.` — or a nudge when the sums do not work.
    private var marginNote: String {
        let sell = Money.parse(price) ?? 0
        let buy = Money.parse(cost) ?? 0
        guard sell > buy else { return Loc.setPriceAboveCost }
        return Loc.youMakeAPiece(Money.text(sell - buy, in: currency))
    }

    private func loadDraft() {
        guard !loaded else { return }
        loaded = true
        guard let product else { return }
        name = product.name
        cost = Money.amount(product.cost, in: currency)
        price = Money.amount(product.price, in: currency)
    }

    /// Correcting only. A new product is written by `saveDrafts`, which may be
    /// writing several.
    private func save() {
        guard canSave, let product else { return }
        store.update(
            product,
            name: name,
            cost: Money.parse(cost) ?? 0,
            price: Money.parse(price) ?? 0
        )
        router.productEditor = nil
    }
}
