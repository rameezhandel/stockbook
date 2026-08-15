import SwiftUI
import SwiftData

/// The catalogue: what is on the shelf, what it cost, what it sells for.
struct ItemsScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    @Query(sort: \Product.name) private var products: [Product]
    @Query private var settingsRows: [ShopSettings]

    @State private var query = ""

    private var lowStockAt: Int { settingsRows.first?.lowStockAt ?? 40 }

    /// Case-insensitive substring match. In-memory: 50–300 products is nothing,
    /// and it keeps the rule visible.
    private var filtered: [Product] {
        let needle = query.trimmed.lowercased()
        guard !needle.isEmpty else { return products }
        return products.filter { $0.name.lowercased().contains(needle) }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Items", subtitle: subtitle, bottomPadding: 10) {
                Button {
                    router.openNewProduct()
                } label: {
                    Label("Add", systemImage: Icon.add)
                }
                .buttonStyle(.primaryCompact)
            }

            NocturneField(
                placeholder: "Search",
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
                            actionTitle: "Add a product",
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
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
        }
    }

    private var subtitle: String {
        guard !products.isEmpty else { return "nothing added yet" }
        let low = products.filter { $0.isLow(threshold: lowStockAt) }.count
        return "\(Copy.count(products.count, "product")) · \(low) running low"
    }

    private var emptyMessage: String {
        products.isEmpty
            ? "Nothing on the shelf yet. Add your first product."
            : "Nothing matches “\(query.trimmed)”."
    }
}

private struct ProductRow: View {
    let product: Product
    let lowStockAt: Int

    @Environment(\.currencySymbol) private var symbol

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(product.name)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text("buy \(Money.text(product.cost, symbol: symbol)) · you make \(Money.text(product.marginPerPiece, symbol: symbol))")
                    .nocturneText(.meta)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(product.price, symbol: symbol))
                    .font(NocturneType.inter(15))
                Text(product.stockLabel)
                    .nocturneText(.meta)
                    .foregroundStyle(stockColor)
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
