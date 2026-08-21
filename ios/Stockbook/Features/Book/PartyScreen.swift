import SwiftUI

/// One customer or one supplier: what they are worth, what is outstanding, the
/// things that can be done about it, and every document between them and the
/// shop.
///
/// This is where the party card that used to hide behind a dropdown at the top
/// of the Book now lives. The card itself is barely changed — what changed is
/// that a customer is a place you can go rather than an option you have to
/// select. Before this, correcting somebody's phone number meant Book, chip,
/// dropdown, pick, pencil; and the only route from Today's "Ahmed still owes"
/// banner to Ahmed was a sheet built specially to work around the fact that
/// Ahmed had no screen.
///
/// One screen for both sides of the book, exactly as `StatementScreen` is one
/// screen: the domain treats a customer and a supplier as the same shape pointed
/// in opposite directions, and two screens here would drift apart the first time
/// either was corrected.
struct PartyScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// Whose account: a customer key, or a supplier key with `isSupplier` set.
    let partyKey: String
    var isSupplier = false
    let onClose: () -> Void

    // Read through the store on every draw rather than carried in: taking a
    // payment from this screen has to move the figure above it, and a copy handed
    // over when the screen opened would sit there saying what was owed a minute
    // ago. `StockbookStore` is `@Observable`, so this redraws on its own.
    private var customer: Customer? { isSupplier ? nil : store.customer(key: partyKey) }
    private var supplier: Supplier? { isSupplier ? store.supplier(key: partyKey) : nil }

    private var name: String { customer?.name ?? supplier?.name ?? partyKey }
    private var contact: String? { customer?.contactLine ?? supplier?.contactLine }
    private var owed: Double { customer?.owed ?? supplier?.owed ?? 0 }

    private var bills: [Bill] { isSupplier ? [] : store.bills(forCustomer: partyKey) }
    private var purchases: [Purchase] { isSupplier ? store.purchases(forSupplier: partyKey) : [] }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: name, subtitle: contact) {
                Button(Loc.done, action: onClose)
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5))
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: Metrics.rowGap) {
                    accountCard.padding(.bottom, 20 - Metrics.rowGap)

                    HStack {
                        Kicker(isSupplier ? Loc.purchasesSide : Loc.billsTitle)
                        Spacer(minLength: 0)
                    }

                    if bills.isEmpty, purchases.isEmpty {
                        EmptyStateBox(
                            icon: isSupplier ? Icon.addStock : Icon.bills,
                            message: isSupplier ? Loc.noDeliveriesYet : Loc.noBillsEver,
                            actionTitle: isSupplier ? Loc.recordDelivery : Loc.startABill,
                            action: {
                                onClose()
                                if isSupplier { router.recordDelivery() } else { router.startBill() }
                            }
                        )
                    }

                    ForEach(bills) { bill in
                        Button {
                            router.openBill(bill)
                        } label: {
                            BillRow(bill: bill)
                        }
                        .buttonStyle(.plain)
                    }

                    ForEach(purchases) { purchase in
                        Button {
                            router.purchaseDetail = purchase
                        } label: {
                            PartyDeliveryRow(purchase: purchase, supplierName: name)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 24)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Nocturne.bg.ignoresSafeArea())
    }

    // MARK: The account

    /// The two figures and the things that can be done about them.
    private var accountCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                figure(
                    label: isSupplier ? Loc.boughtFromThem : Loc.transactions,
                    value: Money.text(customer?.total ?? supplier?.total ?? 0, in: currency),
                    detail: isSupplier
                        ? Loc.purchases(supplier?.purchaseCount ?? 0)
                        : Loc.bills(customer?.billCount ?? 0)
                )
                figure(
                    label: isSupplier ? Loc.youOwe : Loc.pendingPayment,
                    value: owedText,
                    detail: nil,
                    tint: owed > 0 ? Nocturne.accent400 : Nocturne.neutral500
                )
                // Editing is where a phone number gets added to somebody who has
                // only ever been a name on a bill.
                Button {
                    if let supplier { router.openSupplier(supplier) }
                    if let customer { router.openCustomer(customer) }
                } label: {
                    Glyph(Icon.edit, size: 13)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isSupplier ? Loc.editSupplier : Loc.editCustomer)
            }

            // The statement across the whole width, and the things that write to
            // the account beneath it. The statement is the one that only *reads*.
            Button(Loc.statement) {
                if let supplier { router.openStatement(forSupplier: supplier) }
                if let customer { router.openStatement(for: customer) }
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
            .padding(.top, 11)

            HStack(spacing: 6) {
                // Always offered, including to somebody who is owed nothing. Money
                // goes over a counter in instalments and sometimes ahead of the
                // bill, and the payment sheet has always said "SAR 200 in advance"
                // when it does. This was gated on `owed > 0` on the supplier side,
                // which meant settling up in full took the button away.
                Button(Loc.recordAPayment) {
                    if let supplier { router.supplierPaymentFor = supplier }
                    if let customer { router.paymentFor = customer }
                }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))

                // Absent rather than present and dead on the supplier side: the
                // shop does not write itself a credit note.
                if let customer {
                    Button(Loc.issueACreditNote) {
                        router.creditNoteFor = CreditNoteTarget(customer: customer)
                    }
                    .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
                }
            }
            .padding(.top, 6)
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
    }

    private var owedText: String {
        if owed > 0 { return Money.text(owed, in: currency) }
        if owed < 0 { return Loc.inAdvance(Money.text(-owed, in: currency)) }
        return Loc.nothingPending
    }

    private func figure(
        label: String,
        value: String,
        detail: String?,
        tint: Color = Nocturne.text
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(value)
                .font(NocturneType.inter(17))
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .rollingNumber(value)
            if let detail {
                Text(detail).nocturneText(.meta)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// One delivery under the supplier it came from.
///
/// A near-twin of the row on the Purchases list, which is private to that file
/// and says the supplier's name on every line — worth repeating rather than
/// sharing, because under one supplier that line would say the same name all the
/// way down and the useful thing to show instead is when it arrived.
private struct PartyDeliveryRow: View {
    let purchase: Purchase
    let supplierName: String

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(purchase.summary.isBlank ? Loc.supplierBillTitle : purchase.summary)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                Text(rowDetail)
                .nocturneText(.meta)
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(purchase.total, in: currency))
                    .font(NocturneType.inter(14))
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

    /// A delivery of one thing shows the arithmetic behind it; several show how
    /// many, since a row has one line's worth of space. A bill entered as a figure
    /// has none to show, so it shows the day it arrived instead.
    private var rowDetail: String {
        let items = purchase.items
        switch items.count {
        case 0: return Loc.longDate(purchase.createdAt)
        case 1: return Loc.perPiece(qty: items[0].qty, cost: Money.text(items[0].unitCost, in: currency))
        default: return Loc.items(items.count)
        }
    }
}
