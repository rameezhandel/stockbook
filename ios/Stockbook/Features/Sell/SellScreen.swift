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
            ScreenHeader(title: Loc.newBill, bottomPadding: 10) {
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
            NocturneField(placeholder: Loc.addAProductPlaceholder, text: $query, fontSize: 14.5)
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 10)

            // Which of the two is showing is derived, so the swap is the only
            // signal that a tap changed anything. It fades rather than cuts.
            Group {
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
            .transition(.opacity)
            .motion(Motion.screen, value: showsPicker)
        }
    }

    private var cartCountLabel: String {
        cart.isEmpty ? Loc.cartEmpty : Loc.lines(cart.lines.count)
    }

    private var pickerHint: String {
        guard !products.isEmpty else { return "" }
        if !query.isBlank { return Loc.matchingQuery(query.trimmed) }
        return Loc.allProductsHint(products.count)
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
    @Environment(\.currency) private var currency
    @Environment(\.bottomSafeInset) private var bottomInset

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(spacing: 5) {
                    if products.isEmpty {
                        EmptyStateBox(
                            message: emptyMessage,
                            actionTitle: Loc.addAProduct,
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

                                // Browsing keeps you on this list, so without a
                                // mark here a tap produces no visible result and
                                // the same item gets added twice.
                                let onBill = cart.quantity(forProduct: product.uid)
                                if onBill > 0 {
                                    HStack(spacing: 3) {
                                        Glyph(Icon.confirm, size: 11)
                                        Text("\(onBill)")
                                            .font(NocturneType.inter(11.5))
                                            .contentTransition(.numericText())
                                    }
                                    .foregroundStyle(Nocturne.accent)
                                    // The list does not move when a row is
                                    // tapped, so this mark is the whole of the
                                    // feedback. It arrives with a pop.
                                    .transition(.scale(scale: 0.4).combined(with: .opacity))
                                }

                                Text(Loc.stockLabel(product.stock))
                                    .nocturneText(.meta)
                                    .lineLimit(1)
                                Text(Money.text(product.price, in: currency))
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
                        .motion(Motion.pop, value: cart.quantity(forProduct: product.uid))
                        .accessibilityLabel(
                            cart.quantity(forProduct: product.uid) > 0
                                ? Loc.onBillAccessibility(
                                    name: product.name,
                                    quantity: cart.quantity(forProduct: product.uid)
                                  )
                                : product.name
                        )
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
                        Text(Loc.lines(cart.lines.count))
                            .nocturneText(.meta)
                        Text(Money.text(cart.total, in: currency))
                            .font(NocturneType.inter(19, .medium))
                            .rollingNumber(cart.total)
                    }
                    Spacer(minLength: 12)
                    Button(Loc.doneAdding, action: onDoneAdding)
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
        hasAnyProducts ? Loc.noProductMatches(query) : Loc.noProductsYet
    }
}
