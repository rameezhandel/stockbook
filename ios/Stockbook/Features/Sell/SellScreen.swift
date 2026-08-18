import SwiftUI

/// The bill being written: a form, with a product picker behind one button.
///
/// **A form, not a cart.** A bill here is a number, a date, somebody and a
/// figure — the paper book was written first, so the total is already known and
/// rebuilding it product by product to arrive at it is work for nothing. Saying
/// what was sold is optional, and the only thing it buys is the shelf moving.
///
/// Add items is therefore a genuine mode, entered and left by tapping, rather
/// than the derived state it used to be. An empty cart no longer means the
/// picker: it is what the ordinary bill in this shop looks like.
struct SellScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart

    private var products: [Product] { store.products }

    @State private var query = ""
    /// Set by "Add items", cleared by "Done adding". On the router rather than in
    /// here because the shell draws the tab bar and must not stack it under the
    /// picker's own bottom bar.
    private var browsing: Bool { router.pickingProducts }

    private var showsPicker: Bool {
        browsing || !query.isBlank
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

            // Which of the two is showing is derived, so the swap is the only
            // signal that a tap changed anything. It fades rather than cuts.
            Group {
                if showsPicker {
                    // The search box belongs to the picker rather than to the
                    // screen: a form for typing a figure should not open with a
                    // box asking for a product name.
                    NocturneField(placeholder: Loc.addAProductPlaceholder, text: $query, fontSize: 14.5)
                        .padding(.horizontal, Metrics.screenPadding)
                        .padding(.bottom, 10)

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
        // Leaving Sell puts the picker away. It used to happen for free, when
        // this was a `@State` that died with the screen.
        .onDisappear { router.pickingProducts = false }
    }

    private var cartCountLabel: String {
        cart.isEmpty ? "" : Loc.lines(cart.lines.count)
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
        router.pickingProducts = true
        query = ""
    }

    private func closePicker() {
        router.pickingProducts = false
        query = ""
    }

    /// Saves a new bill, or rewrites the one being corrected.
    ///
    /// `amount` is passed either way. The store ignores it when there are lines —
    /// one rule, in one place, rather than a screen deciding which of two figures
    /// is the real one.
    ///
    /// A correction closes onto the corrected document rather than the receipt
    /// overlay: the owner came from that sheet and is checking the change landed,
    /// not confirming a sale that has just happened.
    private func save() {
        if let number = cart.editing {
            guard let corrected = store.updateBill(
                number: number,
                lines: cart.draftLines,
                customer: cart.customer,
                paid: cart.paidForStorage,
                amount: cart.typedAmount,
                createdAt: cart.soldAt,
                invoiceNo: cart.invoiceNo
            ) else { return }

            cart.clear()
            closePicker()
            router.tab = .book
            router.openBill(corrected)
            return
        }

        guard let bill = store.saveBill(
            lines: cart.draftLines,
            customer: cart.customer,
            paid: cart.paidForStorage,
            amount: cart.typedAmount,
            createdAt: cart.soldAt,
            invoiceNo: cart.invoiceNo
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
            .scrollDismissesKeyboard(.interactively)

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
