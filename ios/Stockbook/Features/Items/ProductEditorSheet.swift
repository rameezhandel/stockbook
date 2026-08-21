import SwiftUI

/// Create or edit one product.
///
/// This sheet is the reference implementation of the app's validation rule:
/// **no error toasts, no red text**. An incomplete draft simply leaves the Save
/// button disabled and puts an accent border on whatever is still empty.
struct ProductEditorSheet: View {
    /// `nil` for "New product".
    let product: Product?

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart
    @Environment(\.currency) private var currency

    @State private var name = ""
    @State private var stock = ""
    @State private var cost = ""
    @State private var price = ""
    @State private var loaded = false

    private var isNew: Bool { product == nil }

    private var canSave: Bool {
        StockbookStore.isProductDraftComplete(name: name, stock: stock, cost: cost, price: price)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.fieldGap) {
            SheetHeader(title: isNew ? Loc.newProduct : Loc.editProduct) {
                router.productEditor = nil
            }

            NocturneField(
                label: Loc.productName,
                placeholder: Loc.productNameExample,
                text: $name,
                height: Metrics.tallInputHeight,
                isRequiredAndEmpty: name.isBlank,
                fontSize: 15
            )

            // The count is asked for once, when the product is created, and
            // never again from this sheet.
            //
            // It used to sit here on every edit too, which made it a second,
            // unlabelled "Set count" one keystroke from the price boxes: fixing
            // a miscount could rewrite a selling price, and "In stock" said
            // nothing about whether the number was absolute or something to add.
            // Afterwards the shelf moves for a stated reason — a delivery in, a
            // bill out, or a recount through Set count, which says what it is.
            HStack(spacing: 8) {
                if isNew {
                    NocturneField.number(
                        label: Loc.openingStock,
                        text: $stock,
                        isRequiredAndEmpty: stock.isBlank
                    )
                }
                NocturneField.number(
                    label: Loc.buyingPrice,
                    text: $cost,
                    isRequiredAndEmpty: cost.isBlank
                )
            }

            if isNew {
                Text(Loc.openingStockNote).nocturneText(.meta)
            }

            NocturneField.number(
                label: Loc.sellingPrice,
                text: $price,
                isRequiredAndEmpty: (Money.parse(price) ?? 0) <= 0,
                emphasis: .sellingPrice
            )

            Text(marginNote)
                .nocturneText(.meta)
                .padding(.top, 2)

            HStack(spacing: 8) {
                if let product {
                    Button(Loc.addStock) {
                        router.openAddStock(for: product)
                    }
                    .buttonStyle(.secondary)
                }

                Button(Loc.save, action: save)
                    .buttonStyle(.primaryBlock)
                    .disabled(!canSave)
            }
            .padding(.top, 6)

            if let product {
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
        .onAppear(perform: loadDraft)
        .keyboardDoneButton()
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
        stock = String(product.stock)
        cost = Money.amount(product.cost, in: currency)
        price = Money.amount(product.price, in: currency)
    }

    private func save() {
        guard canSave else { return }
        let costValue = Money.parse(cost) ?? 0
        let priceValue = Money.parse(price) ?? 0

        if let product {
            store.update(product, name: name, cost: costValue, price: priceValue)
        } else {
            store.addProduct(
                name: name,
                stock: Int(stock.trimmed) ?? Int(Money.parse(stock) ?? 0),
                cost: costValue,
                price: priceValue
            )
        }
        router.productEditor = nil
    }
}
