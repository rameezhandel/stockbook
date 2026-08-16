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
    @Environment(\.currencySymbol) private var symbol

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
            SheetHeader(title: isNew ? "New product" : "Edit product") {
                router.productEditor = nil
            }

            NocturneField(
                label: "Product name",
                placeholder: "e.g. 4 inch hinge",
                text: $name,
                height: Metrics.tallInputHeight,
                isRequiredAndEmpty: name.isBlank,
                fontSize: 15
            )

            HStack(spacing: 8) {
                NocturneField.number(
                    label: "In stock",
                    text: $stock,
                    isRequiredAndEmpty: stock.isBlank
                )
                NocturneField.number(
                    label: "Buying price",
                    text: $cost,
                    isRequiredAndEmpty: cost.isBlank
                )
            }

            NocturneField.number(
                label: "Selling price",
                text: $price,
                isRequiredAndEmpty: (Money.parse(price) ?? 0) <= 0,
                emphasis: .sellingPrice
            )

            Text(marginNote)
                .nocturneText(.meta)
                .padding(.top, 2)

            HStack(spacing: 8) {
                if let product {
                    Button("Add stock") {
                        router.openAddStock(for: product)
                    }
                    .buttonStyle(.secondary)
                }

                Button("Save", action: save)
                    .buttonStyle(.primaryBlock)
                    .disabled(!canSave)
            }
            .padding(.top, 6)

            if let product {
                Button("Remove this product") {
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
        guard sell > buy else { return "Set a selling price above the buying price." }
        return "You make \(Money.text(sell - buy, symbol: symbol)) a piece."
    }

    private func loadDraft() {
        guard !loaded else { return }
        loaded = true
        guard let product else { return }
        name = product.name
        stock = String(product.stock)
        cost = Money.amount(product.cost)
        price = Money.amount(product.price)
    }

    private func save() {
        guard canSave else { return }
        let stockValue = Int(stock.trimmed) ?? Int(Money.parse(stock) ?? 0)
        let costValue = Money.parse(cost) ?? 0
        let priceValue = Money.parse(price) ?? 0

        if let product {
            store.update(product, name: name, stock: stockValue, cost: costValue, price: priceValue)
        } else {
            store.addProduct(name: name, stock: stockValue, cost: costValue, price: priceValue)
        }
        router.productEditor = nil
    }
}
