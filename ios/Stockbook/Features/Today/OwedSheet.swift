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

    /// The rendered list, waiting for the share sheet. Rendered on the tap rather
    /// than when the view is built, because what is owed changes underneath it.
    @State private var file: StatementFile?

    var body: some View {
        // Read straight off the store rather than snapshotted into `@State`: it
        // is `@Observable`, so a payment taken from inside this sheet redraws the
        // row it settled. Android has to key this on the shop state by hand.
        OwedList(
            // The card that opens this sheet draws the same string. One word,
            // one string: a sheet with a title of its own is a title that
            // drifts from the card the thumb just touched.
            title: Loc.receivableStat,
            rows: store.customers().filter { $0.owed > 0 }.map(row),
            total: store.customers().reduce(0) { $0 + $1.owed },
            search: { query in store.customers(matching: query).map(row) },
            // The list is the document, so the button that makes it belongs
            // here. Only where there is something to chase: a page saying
            // nobody owes anything is a page nobody needs.
            onSave: store.customers().contains { $0.owed > 0 } ? save : nil,
            action: Loc.takePayment,
            onClose: onClose
        )
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    /// One customer as a row. Written once because the list reads only those who
    /// owe and the search box above it reads the whole roster — a customer who
    /// owes nothing still walks in to pay a deposit, or to settle a bill the
    /// moment it is written.
    private func row(_ customer: Customer) -> OwedRow {
        OwedRow(id: customer.key, name: customer.name, amount: customer.owed) {
            router.paymentFor = customer
            onClose()
        }
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest
    /// outcome and the one `StatementScreen` already settled on: there is no
    /// half-written page worth offering, and the list itself is still on screen.
    private func save() {
        let document = SummaryDocument.forReceivable(
            customers: store.customers(),
            settings: store.settings,
            strings: Loc
        )
        guard let url = try? SummaryPDF.write(
            document,
            fileName: Loc.receivableFileName(date: Copy.fileDate(.now))
        ) else { return }
        file = StatementFile(url: url)
    }
}

/// The same sheet for money going the other way. One body, two entry points, as
/// with the payment sheets themselves: what a debt *is* does not change with its
/// direction.
struct WhoYouOweSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    let onClose: () -> Void

    @State private var file: StatementFile?

    var body: some View {
        OwedList(
            title: Loc.payableStat,
            rows: store.suppliers().filter { $0.owed > 0 }.map(row),
            total: store.suppliers().reduce(0) { $0 + $1.owed },
            search: { query in store.suppliers(matching: query).map(row) },
            onSave: store.suppliers().contains { $0.owed > 0 } ? save : nil,
            // Money leaving, not arriving. "Take payment" beside a supplier the
            // shop owes describes the wrong direction entirely, and it is the one
            // word on this sheet a hurried thumb reads before tapping.
            action: Loc.makePayment,
            onClose: onClose
        )
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    private func row(_ supplier: Supplier) -> OwedRow {
        OwedRow(id: supplier.key, name: supplier.name, amount: supplier.owed) {
            router.supplierPaymentFor = supplier
            onClose()
        }
    }

    private func save() {
        let document = SummaryDocument.forPayable(
            suppliers: store.suppliers(),
            settings: store.settings,
            strings: Loc
        )
        guard let url = try? SummaryPDF.write(
            document,
            fileName: Loc.payableFileName(date: Copy.fileDate(.now))
        ) else { return }
        file = StatementFile(url: url)
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
    /// What is outstanding altogether, passed in rather than summed from `rows`.
    ///
    /// The subtitle answers "how much is out there", and a search that narrowed
    /// it to one name would leave the sheet's headline figure quietly following
    /// the typing.
    let total: Double
    /// Everybody, by name or phone, matching what has been typed.
    let search: (String) -> [OwedRow]
    /// Makes a page of this list. Absent where there is no list worth making.
    let onSave: (() -> Void)?
    /// What the row's button says. One body serves both directions, and the
    /// direction is the whole of what this word carries.
    let action: String
    let onClose: () -> Void

    @Environment(\.currency) private var currency

    @State private var query = ""

    private var searching: Bool { !query.isBlank }

    /// Searching leaves the debt list behind entirely: it answers from the whole
    /// roster, so somebody who owes nothing today can still be found and paid.
    private var shown: [OwedRow] { searching ? search(query) : rows }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: title,
                subtitle: Money.text(total, in: currency),
                onClose: onClose
            )

            // Always, and directly under the title. This sheet was twice built
            // with a rule deciding when the box was worth showing — longer than
            // five names, or somebody missing from the list — and both times
            // the owner opened it, saw no box, and had to ask where it had
            // gone. A control that appears only under conditions the owner
            // cannot see is a control they cannot learn, and the cost of
            // drawing one unnecessary field is nothing beside that.
            NocturneField(
                placeholder: Loc.search,
                text: $query,
                height: 40,
                fontSize: 13.5
            )
            .padding(.bottom, Metrics.rowGap)

            if let onSave {
                Button(Loc.sharePdf, action: onSave)
                    .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 40, fontSize: 13))
                    .padding(.bottom, 12)
            }

            // The banner that opens this sheet only appears when somebody owes, so
            // an empty list here means the last of it was settled while the sheet
            // was open. Worth saying rather than leaving a blank sheet behind —
            // and while searching the answer is about the typing, not the debt.
            if shown.isEmpty {
                Text(searching ? Loc.nobodyMatches : Loc.settledUp)
                    .nocturneText(.meta)
                    .padding(.vertical, 14)
            } else {
                // A plain stack rather than a lazy one: the sheet already scrolls,
                // and a shop with more debtors than fit in it has a bigger problem
                // than this screen. Sorted by what is owed — `customers()` and
                // `suppliers()` both hand them over that way.
                VStack(spacing: Metrics.rowGap) {
                    ForEach(shown) { row in
                        HStack(spacing: 9) {
                            Glyph(Icon.customer, size: 13)
                                .foregroundStyle(Nocturne.neutral500)
                            VStack(alignment: .leading, spacing: 0) {
                                Text(row.name)
                                    .nocturneText(.rowPrimary)
                                    .lineLimit(1)
                                Text(Money.text(row.amount, in: currency))
                                    .font(NocturneType.inter(11.5))
                                    // Accent is the colour of an outstanding
                                    // figure. A name the search turned up who
                                    // owes nothing reads in the ordinary grey,
                                    // so nothing on the row says "debt" when
                                    // there is none.
                                    .foregroundStyle(row.amount > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)

                            // Named rather than a chevron: the row goes somewhere
                            // specific, and "Take payment" — or "Make payment",
                            // on the other side — is the sentence the owner is
                            // already halfway through when they tap it. The whole
                            // row takes the tap too: the button is where the eye
                            // lands, not the only place that works.
                            Button(action, action: row.onTake)
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
