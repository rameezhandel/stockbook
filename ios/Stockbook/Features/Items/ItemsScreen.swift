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
