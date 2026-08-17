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
    @Environment(\.currency) private var currency

    @State private var mode: RestockMode = .quickAdd
    @State private var quantity = ""
    @State private var unitCost = ""
    /// What was typed into the supplier box, and who was actually chosen.
    @State private var supplier = ""
    @State private var supplierKey: String?
    @State private var settledNow = true
    @State private var paidText = ""

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
                SupplierPicker(
                    typed: $supplier,
                    chosenKey: $supplierKey
                )
            }

            HStack(spacing: 8) {
                NocturneField.number(label: Loc.howMany, text: $quantity)
                if isPurchase {
                    NocturneField.number(label: Loc.paidPerPiece, text: $unitCost)
                }
            }

            // Was it paid for at the door? Usually yes — the driver waits — so that
            // is the default, and the alternative is one tap rather than a number
            // somebody has to type to mean "nothing owed".
            if isPurchase {
                HStack(spacing: 6) {
                    ChoicePill(title: Loc.paidInFull, icon: Icon.money, selected: settledNow) {
                        settledNow = true
                    }
                    ChoicePill(title: Loc.partPayment, icon: Icon.partPayment, selected: !settledNow) {
                        settledNow = false
                    }
                }

                if !settledNow {
                    NocturneField.number(
                        label: Loc.paidNow,
                        text: $paidText,
                        prefix: currency.symbol.trimmed
                    )
                }
            }

            Text(note)
                .nocturneText(.meta)
                .padding(.top, 2)

            Button(actionLabel, action: confirm)
                .buttonStyle(.primaryBlock)
                .disabled(!canSave)
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
            return Loc.purchaseNote(billTotal: Money.text(billTotal, in: currency))
        }
        return Loc.quickAddNote(cost: Money.text(product.cost, in: currency))
    }

    private var actionLabel: String {
        guard isPurchase else { return Loc.addToStock(max(0, quantityValue)) }
        if supplierKey != nil { return Loc.recordPurchase }
        return supplier.isBlank ? Loc.whoDeliveredIt : Loc.chooseSupplierFromTheList
    }

    /// A purchase is a record against an account, so it needs the account. Quick
    /// add is not: it is a correction to a number on a shelf, and demanding a
    /// supplier for it would be asking who delivered the bag you just tipped in.
    private var canSave: Bool {
        isPurchase ? quantityValue > 0 && supplierKey != nil : true
    }

    /// Zero or empty quantity just closes the sheet — the owner opened it, then
    /// thought better of it, and that should not need a Cancel button.
    private func confirm() {
        if isPurchase {
            guard let key = supplierKey else { return }
            // A delivery is a record with an account behind it, not a number
            // added to a shelf — which is why this is no longer `restock` with a
            // supplier string that went nowhere.
            store.recordPurchase(
                product: product,
                supplierKey: key,
                quantity: quantityValue,
                unitCost: Money.parse(unitCost) ?? 0,
                paid: settledNow ? nil : (Money.parse(paidText) ?? 0)
            )
        } else {
            store.restock(product, quantity: quantityValue, mode: .quickAdd)
        }
        router.addStock = nil
    }
}

/// Who delivered it: type to filter the roster, then **choose**.
///
/// The cart's customer picker, on the other side of the counter and for the same
/// reason — a typed name is not an account, and a delivery filed against one that
/// does not exist is money the shop cannot see it owes. It cannot block the work
/// either: a name matching nobody offers to become a supplier in the same tap.
///
/// The list is drawn **below** the field here, unlike the cart's. This sheet
/// grows downwards with room under it; the cart's field sits on the bottom edge
/// of the screen with the keyboard beneath it.
private struct SupplierPicker: View {
    @Binding var typed: String
    @Binding var chosenKey: String?

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    private static let rowHeight: CGFloat = 35
    private static let maxListHeight: CGFloat = 150

    private var query: String { Supplier.key(for: typed) }

    private var matches: [Supplier] {
        guard chosenKey == nil else { return [] }
        return store.suppliers().filter { query.isEmpty || $0.key.contains(query) }
    }

    private var canCreate: Bool {
        chosenKey == nil && !query.isEmpty && !store.suppliers().contains { $0.key == query }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NocturneField(
                label: Loc.supplier,
                placeholder: Loc.whoDeliveredIt,
                text: Binding(get: { typed }, set: { typed = $0; chosenKey = nil }),
                // Marked until somebody is actually chosen, not merely until the
                // box has characters in it.
                isRequiredAndEmpty: chosenKey == nil,
                identifier: "purchase.supplier"
            )

            if !matches.isEmpty || canCreate {
                VStack(spacing: 0) {
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(matches) { candidate in
                                Button { choose(candidate) } label: {
                                    HStack(spacing: 8) {
                                        Glyph(Icon.customer, size: 13)
                                            .foregroundStyle(Nocturne.neutral500)
                                        Text(candidate.name)
                                            .font(NocturneType.inter(13.5))
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .lineLimit(1)
                                        Text(candidate.meta(in: currency, strings: Loc))
                                            .font(NocturneType.inter(11))
                                            .foregroundStyle(candidate.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                                    }
                                    .padding(.horizontal, 11)
                                    .frame(height: Self.rowHeight)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .frame(height: min(CGFloat(matches.count) * Self.rowHeight, Self.maxListHeight))
                    .scrollBounceBehavior(.basedOnSize)

                    // Outside the scrolling part: it is the way out when nobody
                    // matches, and must never be something to scroll for.
                    if canCreate {
                        Button {
                            guard let record = store.addSupplier(name: typed.trimmed),
                                  let created = store.supplier(key: record.key)
                            else { return }
                            choose(created)
                        } label: {
                            HStack(spacing: 8) {
                                Glyph(Icon.add, size: 13)
                                Text(Loc.addAsSupplier(typed.trimmed))
                                    .font(NocturneType.inter(13.5))
                                    .lineLimit(1)
                                Spacer(minLength: 0)
                            }
                            .foregroundStyle(Nocturne.accent)
                            .padding(.horizontal, 11)
                            .frame(height: Self.rowHeight)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 3)
                .frame(maxWidth: .infinity)
                .background(Nocturne.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            }
        }
    }

    private func choose(_ supplier: Supplier) {
        typed = supplier.name
        chosenKey = supplier.key
        dismissKeyboard()
    }
}
