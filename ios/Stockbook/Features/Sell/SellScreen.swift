import SwiftUI

/// The billing flow: a product picker and a cart, sharing one search field and
/// one header.
///
/// Which of the two is showing is derived, never stored as a mode the user can
/// get stuck in — the picker appears when the cart is empty, when there is text
/// in the search box, or when "Add another item" was tapped. Anything that
/// empties all three conditions drops you back to the cart.
struct SellScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart

    private var products: [Product] { store.products }

    @State private var query = ""
    /// Set by "Add another item" — the one case where the picker is showing even
    /// though the cart is full and nothing has been typed.
    @State private var browsing = false

    private var showsPicker: Bool {
        cart.isEmpty || !query.isBlank || browsing
    }

    private var matches: [Product] { store.products(matching: query) }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "New bill", bottomPadding: 10) {
                Text(cartCountLabel)
                    .font(NocturneType.inter(12))
                    .foregroundStyle(Nocturne.neutral500)
            }

            if showsPicker, !pickerHint.isEmpty {
                Text(pickerHint)
                    .nocturneText(.meta)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Metrics.screenPadding)
                    .padding(.bottom, 8)
            }

            // Shared between both states: in the cart it sits empty, and typing
            // into it is what re-opens the picker.
            NocturneField(placeholder: "Add a product…", text: $query, fontSize: 14.5)
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 10)

            if showsPicker {
                ProductPicker(
                    products: matches,
                    hasAnyProducts: !products.isEmpty,
                    query: query.trimmed,
                    onPick: add(_:),
                    onAddProduct: { router.openNewProduct() },
                    onDoneAdding: closePicker
                )
            } else {
                CartView(
                    onBrowse: openPicker,
                    onSave: save
                )
            }
        }
    }

    private var cartCountLabel: String {
        cart.isEmpty ? "empty" : Copy.count(cart.lines.count, "line")
    }

    private var pickerHint: String {
        guard !products.isEmpty else { return "" }
        if !query.isBlank { return "Matching “\(query.trimmed)”" }
        return "All \(products.count) products — tap to add"
    }

    // MARK: Actions

    /// Adds a piece and clears the search, so the next product can be typed
    /// straight away without reaching for the clear button.
    private func add(_ product: Product) {
        cart.add(product)
        query = ""
    }

    private func openPicker() {
        browsing = true
        query = ""
    }

    private func closePicker() {
        browsing = false
        query = ""
    }

    private func save() {
        guard let bill = store.saveBill(
            lines: cart.draftLines,
            customer: cart.customer,
            paid: cart.paidForStorage
        ) else { return }

        cart.clear()
        closePicker()
        router.receipt = bill
    }
}

/// The browse-and-search list. Tapping a row adds one piece at the product's
/// current selling price; tapping one already in the cart increments it.
private struct ProductPicker: View {
    let products: [Product]
    let hasAnyProducts: Bool
    let query: String
    let onPick: (Product) -> Void
    let onAddProduct: () -> Void
    let onDoneAdding: () -> Void

    @Environment(Cart.self) private var cart
    @Environment(\.currencySymbol) private var symbol
    @Environment(\.bottomSafeInset) private var bottomInset

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(spacing: 5) {
                    if products.isEmpty {
                        EmptyStateBox(
                            message: emptyMessage,
                            actionTitle: "Add a product",
                            action: onAddProduct
                        )
                        .padding(.top, 8)
                    }

                    ForEach(products) { product in
                        Button {
                            onPick(product)
                        } label: {
                            HStack(spacing: 10) {
                                Text(product.name)
                                    .font(NocturneType.inter(14))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .lineLimit(1)
                                Text(product.stockLabel)
                                    .nocturneText(.meta)
                                Text(Money.text(product.price, symbol: symbol))
                                    .font(NocturneType.inter(14))
                                    .foregroundStyle(Nocturne.accent400)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 11)
                            .frame(maxWidth: .infinity)
                            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }

            // With a cart in progress the tab bar is hidden, so this footer is
            // the only way back to it.
            if !cart.isEmpty {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(Copy.count(cart.lines.count, "line"))
                            .nocturneText(.meta)
                        Text(Money.text(cart.total, symbol: symbol))
                            .font(NocturneType.inter(19, .medium))
                    }
                    Spacer(minLength: 12)
                    Button("Done adding", action: onDoneAdding)
                        .buttonStyle(PrimaryButtonStyle(height: 44))
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.top, 10)
                .padding(.bottom, max(bottomInset, 24))
                .background(Nocturne.surface)
                .overlay(alignment: .top) {
                    Rectangle().fill(Nocturne.neutral800).frame(height: 1)
                }
            }
        }
    }

    private var emptyMessage: String {
        hasAnyProducts
            ? "No product matches “\(query)”."
            : "You haven't added any products yet."
    }
}
