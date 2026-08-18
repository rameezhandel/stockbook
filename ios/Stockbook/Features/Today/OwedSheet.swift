import SwiftUI

/// Everybody who owes the shop money, from the banner that says how many there
/// are.
///
/// The banner is where the owner notices the debt and the payment sheet is where
/// it gets collected; before this there was no route between the two, and the way
/// to take Ahmed's cash was to remember to go and find Ahmed in the Book. One tap
/// on the thing you just read is the shortest that route can be.
struct WhoOwesYouSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    let onClose: () -> Void

    var body: some View {
        // Read straight off the store rather than snapshotted into `@State`: it
        // is `@Observable`, so a payment taken from inside this sheet redraws the
        // row it settled. Android has to key this on the shop state by hand.
        OwedList(
            title: Loc.whoOwesYou,
            rows: store.customers().filter { $0.owed > 0 }.map { customer in
                OwedRow(id: customer.key, name: customer.name, amount: customer.owed) {
                    router.paymentFor = customer
                    onClose()
                }
            },
            onClose: onClose
        )
    }
}

/// The same sheet for money going the other way. One body, two entry points, as
/// with the payment sheets themselves: what a debt *is* does not change with its
/// direction.
struct WhoYouOweSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    let onClose: () -> Void

    var body: some View {
        OwedList(
            title: Loc.whoYouOwe,
            rows: store.suppliers().filter { $0.owed > 0 }.map { supplier in
                OwedRow(id: supplier.key, name: supplier.name, amount: supplier.owed) {
                    router.supplierPaymentFor = supplier
                    onClose()
                }
            },
            onClose: onClose
        )
    }
}

/// One name, what is outstanding against it, and the way to settle it.
private struct OwedRow: Identifiable {
    let id: String
    let name: String
    let amount: Double
    let onTake: () -> Void
}

private struct OwedList: View {
    let title: String
    let rows: [OwedRow]
    let onClose: () -> Void

    @Environment(\.currency) private var currency

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: title,
                subtitle: Money.text(rows.reduce(0) { $0 + $1.amount }, in: currency),
                onClose: onClose
            )

            // The banner that opens this sheet only appears when somebody owes, so
            // an empty list here means the last of it was settled while the sheet
            // was open. Worth saying rather than leaving a blank sheet behind.
            if rows.isEmpty {
                Text(Loc.settledUp)
                    .nocturneText(.meta)
                    .padding(.vertical, 14)
            } else {
                // A plain stack rather than a lazy one: the sheet already scrolls,
                // and a shop with more debtors than fit in it has a bigger problem
                // than this screen. Sorted by what is owed — `customers()` and
                // `suppliers()` both hand them over that way.
                VStack(spacing: Metrics.rowGap) {
                    ForEach(rows) { row in
                        HStack(spacing: 9) {
                            Glyph(Icon.customer, size: 13)
                                .foregroundStyle(Nocturne.neutral500)
                            VStack(alignment: .leading, spacing: 0) {
                                Text(row.name)
                                    .nocturneText(.rowPrimary)
                                    .lineLimit(1)
                                Text(Money.text(row.amount, in: currency))
                                    .font(NocturneType.inter(11.5))
                                    .foregroundStyle(Nocturne.accent400)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)

                            // Named rather than a chevron: the row goes somewhere
                            // specific, and "Take payment" is the sentence the
                            // owner is already halfway through when they tap it.
                            // The whole row takes the tap too — the button is
                            // where the eye lands, not the only place that works.
                            Button(Loc.takePayment, action: row.onTake)
                                .buttonStyle(.ghost)
                        }
                        .padding(.leading, 12)
                        .padding(.trailing, 6)
                        .padding(.vertical, 4)
                        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                        .contentShape(Rectangle())
                        .onTapGesture(perform: row.onTake)
                    }
                }
                .padding(.bottom, 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
