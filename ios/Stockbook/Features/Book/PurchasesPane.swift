import SwiftUI

/// What arrived, and from whom.
///
/// The sales half's mirror: every delivery over a span, newest first. The
/// supplier panel that used to sit on top of it now lives on the People tab, for
/// the reason `BillsScreen` gives. A wrong delivery is opened and corrected,
/// exactly as a wrong bill is.
struct PurchasesPane: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    /// The same span control the sales list carries, defaulting the same way and
    /// for the same reason: a year of deliveries is a list you scroll past.
    @SceneStorage("purchases.period") private var stored = PeriodChoice.thisMonth.rawValue

    @State private var from = Calendar.current.date(byAdding: .month, value: -1, to: .now) ?? .now
    @State private var to = Date.now

    private var choice: PeriodChoice { PeriodChoice(rawValue: stored) ?? .thisMonth }

    private var purchases: [Purchase] { store.purchasesIn(choice.period(from: from, to: to)) }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: Metrics.rowGap) {
                // The suppliers used to sit above this list. They have a tab
                // of their own now — see `PeopleScreen`.
                HStack {
                    Kicker(Loc.purchasesSide)
                    Spacer(minLength: 0)
                }

                PeriodPicker(
                    choice: Binding(get: { choice }, set: { stored = $0.rawValue }),
                    from: $from,
                    to: $to
                )
                .padding(.bottom, 10 - Metrics.rowGap)

                // Two different nothings. A shop that has taken no delivery ever
                // wants the button; one that took none in August wants to be told
                // so, because the deliveries it is looking for are on another chip.
                if purchases.isEmpty {
                    if store.purchases.isEmpty {
                        EmptyStateBox(
                            icon: Icon.addStock,
                            message: Loc.noDeliveriesYet,
                            actionTitle: Loc.recordDelivery,
                            action: { router.recordDelivery() }
                        )
                    } else {
                        EmptyStateBox(icon: Icon.addStock, message: Loc.nothingInThisPeriod)
                    }
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

/// One delivery.
private struct DeliveryRow: View {
    let purchase: Purchase
    let supplierName: String

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                // A supplier bill entered as a figure names no product, so the
                // row says what it is rather than showing a blank line where a
                // product name would be.
                Text(purchase.summary.isBlank ? Loc.supplierBillTitle : purchase.summary)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                // A delivery of one thing says what arrived and at what; several
                // say how many rather than repeating the arithmetic of each, since
                // a row has one line's worth of space. A supplier bill entered as
                // a figure has only the name to show, and "× 0" beside it would
                // read as a count the app lost.
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

    /// Who it came from, and then what arrived: the arithmetic for a delivery of
    /// one thing, a count for several, and nothing more for a bill entered as a
    /// figure.
    private var rowDetail: String {
        let items = purchase.items
        switch items.count {
        case 0: return supplierName
        case 1:
            return "\(supplierName) · \(Loc.perPiece(qty: items[0].qty, cost: Money.text(items[0].unitCost, in: currency)))"
        default:
            return "\(supplierName) · \(Loc.items(items.count))"
        }
    }
}
