import SwiftUI

/// Which product arrived.
///
/// The one step a delivery needs that a bill does not: a purchase carries a
/// single product, so it has to be named before anything else can be typed.
/// Searchable, because a shop with two hundred lines cannot scroll to the one on
/// the pallet.
///
/// Choosing a row hands straight over to the purchase sheet — the owner never
/// comes back here, which is why it closes rather than staying open behind.
struct WhichProductSheet: View {
    let onClose: () -> Void

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    @State private var query = ""

    private var matches: [Product] {
        let needle = query.trimmed.lowercased()
        guard !needle.isEmpty else { return store.products }
        return store.products.filter { $0.name.lowercased().contains(needle) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(title: Loc.whichProductArrived, onClose: onClose)

            NocturneField(placeholder: Loc.search, text: $query, fontSize: 14.5)
                .padding(.bottom, 10)

            if matches.isEmpty {
                EmptyStateBox(
                    icon: Icon.items,
                    message: store.products.isEmpty ? Loc.shelfEmpty : Loc.nothingMatches(query.trimmed)
                )
            } else {
                // Capped rather than filling the sheet: the list is the sheet's
                // whole content, and a sheet that reaches the status bar reads as
                // a screen the owner has to find their way out of.
                ScrollView {
                    LazyVStack(spacing: Metrics.rowGap) {
                        ForEach(matches) { product in
                            Button {
                                router.openDelivery(for: product)
                            } label: {
                                row(product)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(maxHeight: 320)
                .scrollBounceBehavior(.basedOnSize)
            }
        }
        .keyboardDoneButton()
    }

    private func row(_ product: Product) -> some View {
        HStack(spacing: 10) {
            Text(product.name)
                .nocturneText(.rowPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)
            Text(Loc.stockLabel(product.stock))
                .nocturneText(.meta)
            // The buying price, not the selling one: this list exists to start a
            // purchase, and that is the figure about to be typed over.
            Text(Money.text(product.cost, in: currency))
                .font(NocturneType.inter(14))
                .foregroundStyle(Nocturne.accent400)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}
