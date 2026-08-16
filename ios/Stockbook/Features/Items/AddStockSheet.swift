import SwiftUI

/// Putting stock back on the shelf, two ways.
///
/// **Quick add** is the common case — you tipped a bag into the bin and the
/// count is now higher. **Purchase entry** is a supplier delivery, and it is the
/// only path that changes the buying price: cost here is "latest paid", not a
/// weighted average, so the new figure simply takes over.
struct AddStockSheet: View {
    let product: Product

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currencySymbol) private var symbol

    @State private var mode: RestockMode = .quickAdd
    @State private var quantity = ""
    @State private var unitCost = ""
    @State private var supplier = ""

    private var isPurchase: Bool {
        if case .purchase = mode { return true }
        return false
    }

    private var quantityValue: Int {
        Int(quantity.trimmed) ?? Int(Money.parse(quantity) ?? 0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.fieldGap) {
            SheetHeader(
                title: Loc.addStock,
                subtitle: Loc.onShelfNow(product: product.name, stock: product.stock)
            ) {
                router.addStock = nil
            }

            modePills

            if isPurchase {
                NocturneField(
                    label: Loc.supplier,
                    placeholder: Loc.whoDeliveredIt,
                    text: $supplier
                )
            }

            HStack(spacing: 8) {
                NocturneField.number(label: Loc.howMany, text: $quantity)
                if isPurchase {
                    NocturneField.number(label: Loc.paidPerPiece, text: $unitCost)
                }
            }

            Text(note)
                .nocturneText(.meta)
                .padding(.top, 2)

            Button(actionLabel, action: confirm)
                .buttonStyle(.primaryBlock)
                .padding(.top, 6)
        }
        .keyboardDoneButton()
    }

    private var modePills: some View {
        HStack(spacing: 8) {
            pill(Loc.quickAdd, active: !isPurchase) { mode = .quickAdd }
            pill(Loc.purchaseEntry, active: isPurchase) { mode = .purchase }
        }
    }

    private func pill(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(NocturneType.inter(13, .medium))
                .foregroundStyle(active ? Nocturne.accent : Nocturne.neutral500)
                .frame(maxWidth: .infinity)
                .frame(height: Metrics.compactControlHeight)
                .hairline(active ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.pillRadius)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// The note is the only place the two modes explain themselves, so it states
    /// the consequence rather than restating the mode.
    private var note: String {
        if isPurchase {
            let cost = Money.parse(unitCost) ?? 0
            let billTotal = Double(max(0, quantityValue)) * cost
            return Loc.purchaseNote(billTotal: Money.text(billTotal, symbol: symbol))
        }
        return Loc.quickAddNote(cost: Money.text(product.cost, symbol: symbol))
    }

    private var actionLabel: String {
        isPurchase ? Loc.recordPurchase : Loc.addToStock(max(0, quantityValue))
    }

    /// Zero or empty quantity just closes the sheet — the owner opened it, then
    /// thought better of it, and that should not need a Cancel button.
    private func confirm() {
        store.restock(
            product,
            quantity: quantityValue,
            mode: mode,
            unitCost: isPurchase ? Money.parse(unitCost) : nil
        )
        router.addStock = nil
    }
}
