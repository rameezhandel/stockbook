import SwiftUI

/// The catalogue: what is on the shelf, what it cost, what it sells for.
struct ItemsScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    @State private var query = ""

    private var products: [Product] { store.products }
    private var lowStockAt: Int { store.settings.lowStockAt }
    private var filtered: [Product] { store.products(matching: query) }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.itemsTitle, subtitle: subtitle, bottomPadding: 10) {
                Button {
                    router.openNewProduct()
                } label: {
                    Label(Loc.add, systemImage: Icon.add)
                }
                .buttonStyle(.primaryCompact)
            }

            NocturneField(
                placeholder: Loc.search,
                text: $query,
                fontSize: 14.5
            )
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 10)

            ScrollView {
                LazyVStack(spacing: Metrics.rowGap) {
                    if filtered.isEmpty {
                        EmptyStateBox(
                            icon: Icon.items,
                            message: emptyMessage,
                            actionTitle: Loc.addAProduct,
                            action: { router.openNewProduct() }
                        )
                        .padding(.top, 8)
                    }

                    ForEach(filtered) { product in
                        Button {
                            router.openProduct(product)
                        } label: {
                            ProductRow(product: product, lowStockAt: lowStockAt)
                        }
                        .buttonStyle(.plain)
                        .transition(.opacity)
                    }

                    // Suppliers live under the shelves rather than in a tab of
                    // their own. This is where stock comes from, and where
                    // somebody is standing when "who did we get these from, and
                    // have we paid them?" comes up. The customer half sits under
                    // Bills for the same reason.
                    SupplierSection()
                        .padding(.top, 22)
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
                // Searching rewrites the list under the thumb; a product added
                // from the sheet arrives into it. Both read better moving.
                .motion(Motion.list, value: filtered.count)
            }
            // Nothing moves out of the keyboard's way any more, so the way back
            // to what it covers is to push it down.
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private var subtitle: String {
        guard !products.isEmpty else { return Loc.nothingAddedYet }
        let low = products.filter { $0.isLow(threshold: lowStockAt) }.count
        return Loc.itemsSubtitle(total: products.count, low: low)
    }

    private var emptyMessage: String {
        products.isEmpty ? Loc.shelfEmpty : Loc.nothingMatches(query.trimmed)
    }
}

private struct ProductRow: View {
    let product: Product
    let lowStockAt: Int

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(product.name)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text(Loc.buyAndMargin(
                    cost: Money.text(product.cost, in: currency),
                    margin: Money.text(product.marginPerPiece, in: currency)
                ))
                    .nocturneText(.meta)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(product.price, in: currency))
                    .font(NocturneType.inter(15))
                Text(Loc.stockLabel(product.stock))
                    .nocturneText(.meta)
                    .foregroundStyle(stockColor)
                    .rollingNumber(product.stock)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }

    /// Out of stock and running low share a colour — both are "look at me",
    /// and the design does not distinguish them.
    private var stockColor: Color {
        product.isLow(threshold: lowStockAt) ? Nocturne.accent400 : Nocturne.neutral500
    }
}

/// The supplier half of the book, folded under the shelves.
///
/// A mirror of the customer panel on Bills: pick one, see what the shop has
/// bought from them and what it still owes, and go on to a statement or a payment.
private struct SupplierSection: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    @State private var chosen: String = ""

    private var suppliers: [Supplier] { store.suppliers() }
    private var selected: Supplier? { suppliers.first { $0.key == chosen } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Kicker(Loc.suppliersTitle)
                Spacer(minLength: 6)
                Button(Loc.addASupplier) { router.openNewSupplier() }
                    .buttonStyle(GhostButtonStyle(fontSize: 12))
            }
            .padding(.bottom, 8)

            if suppliers.isEmpty {
                // Not an empty state with a call to action: a shop can run for
                // weeks before anybody records a delivery, and the way most
                // suppliers get added is the picker inside the purchase sheet.
                Text(Loc.noPurchasesYet).nocturneText(.meta)
            } else {
                picker
                if let selected {
                    summary(selected).padding(.top, 10)
                } else {
                    payableLine.padding(.top, 8)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var picker: some View {
        DropdownField(
            mark: nil,
            title: selected?.name ?? Loc.allSuppliers,
            accessibilityName: Loc.suppliersTitle
        ) {
            Picker("", selection: $chosen) {
                Text(Loc.allSuppliers).tag("")
                ForEach(suppliers) { supplier in
                    Text(supplier.name).tag(supplier.key)
                }
            }
        }
    }

    private var payableLine: some View {
        let owing = store.payable()
        return Text(
            owing.names.isEmpty
                ? Loc.nothingOwedOut
                : "\(Loc.owedToSuppliers): \(Money.text(owing.total, in: currency))"
        )
        .nocturneText(.meta)
        .foregroundStyle(owing.names.isEmpty ? Nocturne.neutral500 : Nocturne.accent400)
    }

    private func summary(_ supplier: Supplier) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 9) {
                Glyph(Icon.customer, size: 16)
                    .foregroundStyle(Nocturne.accent)
                VStack(alignment: .leading, spacing: 1) {
                    Text(supplier.name).nocturneText(.rowPrimary).lineLimit(1)
                    if let contact = contactLine(supplier) {
                        Text(contact).nocturneText(.meta).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                Button {
                    router.openSupplier(supplier)
                } label: {
                    Glyph(Icon.edit, size: 13)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.editSupplier)
            }
            .padding(.bottom, 10)

            HStack(spacing: 10) {
                figure(
                    label: Loc.boughtFromThem,
                    value: Money.text(supplier.total, in: currency),
                    detail: Loc.purchases(supplier.purchaseCount)
                )
                figure(
                    label: Loc.youOwe,
                    value: owedText(supplier),
                    detail: nil,
                    tint: supplier.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500
                )
            }

            HStack(spacing: 6) {
                Button(Loc.statement) { router.openStatement(forSupplier: supplier) }
                    .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
                // Offered only when there is something to settle. Paying a
                // supplier who is owed nothing is an advance — real, but not what
                // this button is for.
                if supplier.owed > 0 {
                    Button(Loc.recordAPayment) { router.supplierPaymentFor = supplier }
                        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
                }
            }
            .padding(.top, 11)
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
    }

    private func contactLine(_ supplier: Supplier) -> String? {
        let details = [supplier.phone, supplier.place].compactMap { $0 }
        return details.isEmpty ? nil : details.joined(separator: " · ")
    }

    private func owedText(_ supplier: Supplier) -> String {
        if supplier.owed > 0 { return Money.text(supplier.owed, in: currency) }
        if supplier.owed < 0 { return Loc.inAdvance(Money.text(-supplier.owed, in: currency)) }
        return Loc.nothingPending
    }

    private func figure(label: String, value: String, detail: String?, tint: Color = Nocturne.text) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(value)
                .font(NocturneType.inter(15))
                .foregroundStyle(tint)
                .lineLimit(1)
            if let detail {
                Text(detail).nocturneText(.meta)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
