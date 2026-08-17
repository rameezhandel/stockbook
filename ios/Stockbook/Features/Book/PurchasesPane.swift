import SwiftUI

/// What arrived, and from whom.
///
/// The sales half's mirror: the supplier panel on top — pick one and see what is
/// owed to them — and every delivery underneath, newest first. A wrong delivery is
/// opened and voided, never deleted, exactly as a wrong bill is.
struct PurchasesPane: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    private var purchases: [Purchase] { store.purchases }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: Metrics.rowGap) {
                SupplierSection()
                    .padding(.bottom, 20)

                HStack {
                    Kicker(Loc.purchasesSide)
                    Spacer(minLength: 0)
                }

                if purchases.isEmpty {
                    EmptyStateBox(
                        icon: Icon.addStock,
                        message: Loc.noDeliveriesYet,
                        actionTitle: Loc.recordDelivery,
                        action: { router.recordingDelivery = true }
                    )
                }

                ForEach(purchases) { purchase in
                    Button {
                        router.purchaseDetail = purchase
                    } label: {
                        DeliveryRow(
                            purchase: purchase,
                            supplierName: store.supplier(key: purchase.supplierKey)?.name ?? purchase.supplierKey
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 18)
            .motion(Motion.list, value: purchases.count)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

/// One delivery. Voided ones stay in the list, muted and marked — history is
/// never hidden here, only struck through.
private struct DeliveryRow: View {
    let purchase: Purchase
    let supplierName: String

    @Environment(\.currency) private var currency

    private var muted: Bool { purchase.voided }

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(purchase.name)
                    .nocturneText(.rowPrimary)
                    .foregroundStyle(muted ? Nocturne.neutral500 : Nocturne.text)
                    .lineLimit(1)
                Text(
                    muted
                        ? Loc.voided
                        : "\(supplierName) · \(Loc.perPiece(qty: purchase.qty, cost: Money.text(purchase.unitCost, in: currency)))"
                )
                .nocturneText(.meta)
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(purchase.total, in: currency))
                    .font(NocturneType.inter(14))
                    .foregroundStyle(muted ? Nocturne.neutral500 : Nocturne.text)
                Text(
                    purchase.balance > 0
                        ? Loc.owes(Money.text(purchase.balance, in: currency))
                        : Loc.longDate(purchase.createdAt)
                )
                .nocturneText(.meta)
                .foregroundStyle(purchase.balance > 0 ? Nocturne.accent400 : Nocturne.neutral500)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}

/// The supplier panel: a mirror of the customer panel on the sales side. Pick
/// one, see what the shop has bought from them and what it still owes, and go on
/// to a statement or a payment.
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
