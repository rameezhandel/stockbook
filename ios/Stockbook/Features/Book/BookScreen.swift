import SwiftUI

/// The account book: every record the shop keeps, one span at a time.
///
/// **One ledger, not three panes.** This screen used to be a switch between
/// three separate views, each of which owned its own idea of which days it was
/// showing: the sales and purchase lists carried a four-chip period picker, and
/// expenses carried a *different* three-chip one buried inside its total card.
/// So "this month" was three pieces of state, the expenses side could not be
/// asked for two dates at all, and switching chips silently threw away the span
/// the owner had just chosen.
///
/// Now the span is asked once, above everything, and every record type answers
/// over it. Changing what you are looking at no longer changes when.
///
/// The chips pick the **kind of record**: what was sold, what arrived, what
/// actually changed hands, and what the owner spent. Sales and Purchases are
/// mirror images in the domain — one `Statement.make` serves both. Payments is
/// the one list this app had no screen for at all: a receipt could only be
/// reached through the customer it belonged to, which is no help to an owner
/// holding receipt 008455 and trying to remember who paid it. Expenses is the
/// odd one, tied to nobody and touching neither side's arithmetic; it sits here
/// anyway, because it is money leaving and it is written down for the same
/// reason.
///
/// Chips rather than tabs, because the shop does not use these symmetrically: a
/// sale happens fifty times a day, a load of stock arrives once a week.
struct BookScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// The rendered page, waiting for the share sheet. One for both documents
    /// this screen makes — the whole ledger book and the spending summary — since
    /// only one of them can be on its way to the chooser at a time.
    @State private var file: StatementFile?

    /// Which kind of record is showing. `@SceneStorage` rather than `@State` so
    /// it survives a trip into a document and back — an owner who came here for
    /// purchases should not be handed bills again on the way out.
    ///
    /// Stored as its raw string because `@SceneStorage` takes only the handful of
    /// types `AppStorage` does.
    @SceneStorage("book.side") private var storedSide = Side.sales.rawValue

    /// **The span, asked once for all four.** This month by default: the book is
    /// a year of rows before long, and the reason to open it is almost always
    /// something written recently.
    ///
    /// Saved for the same reason the side is: a list that quietly reset to this
    /// month every time a document was closed would make a stretch of days
    /// impossible to read through.
    @SceneStorage("book.period") private var storedPeriod = PeriodChoice.thisMonth.rawValue

    @State private var from = Calendar.current.date(byAdding: .month, value: -1, to: .now) ?? .now
    @State private var to = Date.now

    private var side: Side { Side(rawValue: storedSide) ?? .sales }
    private var choice: PeriodChoice { PeriodChoice(rawValue: storedPeriod) ?? .thisMonth }
    private var period: StatementPeriod { choice.period(from: from, to: to) }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.bookTitle) {
                // Every customer's whole history, printed once and filed. The one
                // thing this screen hands to a printer, so it lives here rather
                // than in Settings, which is where features go to be forgotten.
                Button(action: saveLedgerBook) {
                    Glyph(Icon.bills, size: 18)
                }
                .buttonStyle(.iconOnly)
                .accessibilityLabel(Loc.ledgerBook)
            }

            NocturneField(
                placeholder: Loc.searchRecords,
                text: $query,
                height: 40,
                fontSize: 13.5
            )
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 10)

            // Fixed above the scroll, not inside it. This row is the switch, and
            // a switch that scrolls out of reach makes the owner flick back up to
            // change what they are looking at.
            //
            // No icons on it. Four pills across a phone leaves each about 77pt,
            // and "Purchases" with a glyph beside it needs more than that — the
            // label is what the owner is reading anyway.
            //
            // The whole row goes while a search is running, and so does everything
            // else the span controls. Leaving them on screen would have the owner
            // reading "Sales · This month" over a list of results that is neither.
            if !searching {
                HStack(spacing: 6) {
                    ForEach(Side.allCases) { candidate in
                        ChoicePill(title: label(for: candidate), selected: side == candidate) {
                            choose(candidate)
                        }
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 10)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: Metrics.rowGap) {
                    if searching {
                        results
                    } else {
                        book
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
            .scrollDismissesKeyboard(.interactively)
            // Back to the top when the kind of record changes. Without it,
            // switching from forty bills to five expenses lands the owner at the
            // bottom of a list they have not read a line of — the offset is the
            // old list's, and the new one is only long enough to be clamped to its
            // end. Re-identifying the scroll view is what discards that offset.
            .id(side)
        }
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    // MARK: The book

    @ViewBuilder private var book: some View {
        // Span first, then the figure it adds up to, then the rows behind the
        // figure. The picker used to sit below the total on the expenses side and
        // above it on the other two, which meant the same page read in two
        // directions depending on a chip.
        PeriodPicker(
            choice: Binding(get: { choice }, set: { storedPeriod = $0.rawValue }),
            from: $from,
            to: $to
        )
        .padding(.bottom, 12 - Metrics.rowGap)

        totalCard
            .padding(.bottom, 20 - Metrics.rowGap)

        HStack {
            Kicker(listTitle)
            Spacer(minLength: 6)
            // Expenses are the one record with no other way in. A bill starts on
            // the Sell tab and a purchase from the shelf, but nothing else in the
            // app writes down the owner's own spending, so the list carries its
            // own pen.
            if side == .expenses {
                Button(Loc.addAnExpense) { router.openNewExpense() }
                    .buttonStyle(GhostButtonStyle(fontSize: 12))
            }
        }

        rows
    }

    // MARK: The results

    @ViewBuilder private var results: some View {
        let hits = self.hits
        if hits.isEmpty {
            EmptyStateBox(icon: Icon.bills, message: Loc.nothingMatches)
        }
        ForEach(hits) { hit in
            Button {
                open(hit)
            } label: {
                SearchRow(hit: hit)
            }
            .buttonStyle(.plain)
        }
    }

    /// Opens whatever a result is.
    ///
    /// The one place in the app that turns a `SearchHit` back into a record.
    /// Routing is the app's business rather than the store's, so the handle comes
    /// across as an id and is looked up here — a bill by its number, which is what
    /// a bill's identity is, and everything else by its own.
    ///
    /// Nothing opens when the record has gone, which is the honest outcome the
    /// receipt lookup already settled on. A `switch` with no `default`, so a
    /// seventh kind of record has to be given a way in rather than silently doing
    /// nothing when tapped.
    private func open(_ hit: SearchHit) {
        switch hit.kind {
        case .bill:
            if let bill = store.bills.first(where: { String($0.number) == hit.id }) {
                router.openBill(bill)
            }
        case .payment:
            if let id = UUID(uuidString: hit.id), let receipt = store.receipt(forPayment: id) {
                router.showReceipt(receipt, justSaved: false)
            }
        case .supplierPayment:
            if let id = UUID(uuidString: hit.id), let receipt = store.receipt(forSupplierPayment: id) {
                router.showReceipt(receipt, justSaved: false)
            }
        case .creditNote:
            if let id = UUID(uuidString: hit.id),
               let note = store.creditNotes.first(where: { $0.id == id }) {
                router.editingCreditNote = note
            }
        case .purchase:
            if let id = UUID(uuidString: hit.id),
               let purchase = store.purchases.first(where: { $0.id == id }) {
                router.purchaseDetail = purchase
            }
        case .expense:
            if let expense = store.expenses.first(where: { $0.id == hit.id }) {
                router.openExpense(expense)
            }
        }
    }

    private func choose(_ next: Side) {
        withAnimation(Metrics.quick) { storedSide = next.rawValue }
    }

    // MARK: The total

    /// What the span came to, whatever is being counted.
    ///
    /// One card for all four sides rather than one the expenses pane kept to
    /// itself. Its chips went with it: the span is chosen above this now, so the
    /// figure and the rows under it can no longer be showing two different months
    /// — which is exactly what they were doing before the expenses list learned
    /// to narrow.
    private var totalCard: some View {
        // Read once: it is a walk over the whole book, and the rolling animation
        // needs the same figure the label shows.
        let total = self.total
        let text = Money.text(total, in: currency)

        return VStack(alignment: .leading, spacing: 0) {
            Text(totalLabel)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(text)
                .nocturneText(NocturneType.fittedNumber(text))
                .lineLimit(1)
                .rollingNumber(total)
                .padding(.top, 3)
            if let totalNote {
                Text(totalNote)
                    .nocturneText(.meta)
                    .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
        // The span the total covers is the span the page covers, so the button
        // that makes it lives in the total's own corner. Spending is the only one
        // of the four with a page to make; a sales summary is a document this app
        // does not have yet.
        //
        // An overlay rather than a row above the figure, because a tap target on
        // the label's own line would push the figure a third of the card down to
        // make room for it.
        .overlay(alignment: .topTrailing) {
            if side == .expenses, total > 0 {
                Button(action: saveSpending) { Glyph(Icon.share, size: 15) }
                    .buttonStyle(.iconOnly)
                    .foregroundStyle(Nocturne.accent)
                    .accessibilityLabel(Loc.sharePdf)
                    .padding(2)
            }
        }
    }

    private var total: Double {
        switch side {
        case .sales: store.soldIn(period)
        case .purchases: store.boughtIn(period)
        case .payments: store.receivedIn(period)
        case .expenses: store.spentIn(period)
        }
    }

    private var totalLabel: String {
        switch side {
        case .sales: Loc.soldInPeriod
        case .purchases: Loc.boughtInPeriod
        case .payments: Loc.receivedInPeriod
        case .expenses: Loc.expenseInPeriod
        }
    }

    /// A word under the figure, on the two sides that owe one.
    ///
    /// Spending needs saying out loud: a shopkeeper writing down their petrol
    /// deserves to know at a glance that it will not turn up on a customer's
    /// statement. Payments carry the other direction, because what came in is the
    /// headline and what went out is the fact beside it — netting the two into
    /// one number would give the owner a figure they cannot check against
    /// anything they are holding.
    private var totalNote: String? {
        switch side {
        case .expenses: Loc.expensesArePrivate
        case .payments: Loc.alsoPaidOut(Money.text(store.paidOutIn(period), in: currency))
        case .sales, .purchases: nil
        }
    }

    private var listTitle: String {
        switch side {
        case .sales: Loc.billsTitle
        case .purchases: Loc.purchasesSide
        case .payments: Loc.paymentsSide
        case .expenses: Loc.expensesTitle
        }
    }

    private func label(for candidate: Side) -> String {
        switch candidate {
        case .sales: Loc.salesSide
        case .purchases: Loc.purchasesSide
        case .payments: Loc.paymentsSide
        case .expenses: Loc.expensesTitle
        }
    }

    // MARK: The rows

    /// Nothing on these lists corrects anything. A record entered wrong is opened
    /// first, and edited or removed from inside the document — which is the only
    /// place the owner can see what they are about to change.
    ///
    /// No `default` on any of these: a fourth kind of record has to break the
    /// switch and be placed deliberately, not fall through to whichever branch
    /// was last.
    @ViewBuilder private var rows: some View {
        switch side {
        case .sales:
            let bills = store.billsIn(period)
            // Two different nothings, and they need different words. A shop that
            // has never written a bill wants the button; a shop that wrote none in
            // August wants to be told that rather than invited to start one,
            // because the bills it is looking for are on another span.
            if bills.isEmpty {
                if store.bills.isEmpty {
                    EmptyStateBox(
                        icon: Icon.bills,
                        message: Loc.noBillsEver,
                        actionTitle: Loc.startABill,
                        action: { router.startBill() }
                    )
                } else {
                    EmptyStateBox(icon: Icon.bills, message: Loc.nothingInThisPeriod)
                }
            }
            ForEach(bills) { bill in
                Button {
                    router.openBill(bill)
                } label: {
                    BillRow(bill: bill)
                }
                .buttonStyle(.plain)
            }

        case .purchases:
            let purchases = store.purchasesIn(period)
            if purchases.isEmpty {
                if store.purchases.isEmpty {
                    EmptyStateBox(
                        icon: Icon.addStock,
                        message: Loc.noPurchasesRecorded,
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
                    PurchaseRow(
                        purchase: purchase,
                        supplierName: store.supplier(key: purchase.supplierKey)?.name ?? purchase.supplierKey
                    )
                }
                .buttonStyle(.plain)
            }

        case .payments:
            let slips = store.paymentBook(period)
            // No button on this one. A payment is taken against somebody's
            // account, so it starts from the person — there is nothing sensible
            // for a button here to open without asking who first.
            if slips.isEmpty {
                EmptyStateBox(
                    icon: Icon.owed,
                    message: store.payments.isEmpty && store.supplierPayments.isEmpty
                        ? Loc.noPaymentsEver
                        : Loc.nothingInThisPeriod
                )
            }
            ForEach(slips) { slip in
                Button {
                    open(slip)
                } label: {
                    PaymentRow(entry: slip)
                }
                .buttonStyle(.plain)
            }

        case .expenses:
            let expenses = store.expensesIn(period)
            if expenses.isEmpty {
                if store.expenses.isEmpty {
                    EmptyStateBox(
                        icon: Icon.expenses,
                        message: Loc.noExpensesYet,
                        actionTitle: Loc.addAnExpense,
                        action: { router.openNewExpense() }
                    )
                } else {
                    EmptyStateBox(icon: Icon.expenses, message: Loc.nothingInThisPeriod)
                }
            }
            ForEach(expenses) { expense in
                Button {
                    router.openExpense(expense)
                } label: {
                    ExpenseRow(expense: expense)
                }
                .buttonStyle(.plain)
            }
        }
    }

    /// The receipt the slip was written on, which the app already draws — the
    /// same page the owner was shown the moment they took the money.
    ///
    /// Nothing opens when the record has gone, which is the honest outcome the
    /// receipt lookup already settled on.
    private func open(_ slip: PaymentEntry) {
        let receipt = slip.incoming
            ? store.receipt(forPayment: slip.id)
            : store.receipt(forSupplierPayment: slip.id)
        if let receipt { router.showReceipt(receipt, justSaved: false) }
    }

    // MARK: The documents

    /// Every customer's whole history as one document, a page each.
    ///
    /// Drawn through the same routine that draws a single statement, so a sheet
    /// pulled out of this book is the same page that customer would have been
    /// handed — the same geometry and the same figures, in one ink rather than
    /// two.
    ///
    /// A failure leaves `file` nil and nothing opens, which is the honest outcome
    /// the other pages already settled on.
    private func saveLedgerBook() {
        // A hundred pages printed at once: the band is worth its toner on the one
        // sheet a customer is handed, and not a hundred times into a folder, so
        // the whole book takes the monochrome treatment.
        //
        // The contents page is built from the same list the pages are, which is
        // what stops a line in the index and the sheet it points at ever stating
        // different balances.
        let book = store.ledgerBook()
        let pages = book.map {
            StatementDocument.make(statement: $0, settings: store.settings, strings: Loc)
        }
        guard !pages.isEmpty,
              let url = try? StatementPDF.writeLedgerBook(
                  index: SummaryDocument.forLedgerBook(
                      statements: book,
                      settings: store.settings,
                      strings: Loc
                  ),
                  pages: pages,
                  fileName: Loc.ledgerBookFileName(Copy.fileDate(.now))
              )
        else { return }
        file = StatementFile(url: url)
    }

    /// The span's spending, broken down by what it went on.
    ///
    /// A failure leaves `file` nil and nothing opens, for the reason above: there
    /// is no half-written page worth offering, and the figures are still on
    /// screen.
    private func saveSpending() {
        let period = self.period
        let document = SummaryDocument.forSpending(
            lines: store.spendingIn(period),
            range: period.range(),
            settings: store.settings,
            strings: Loc
        )
        guard let url = try? SummaryPDF.write(
            document,
            fileName: Loc.expenseFileName(date: Copy.fileDate(.now))
        ) else { return }
        file = StatementFile(url: url)
    }

    /// Which kind of record is showing.
    private enum Side: String, CaseIterable, Identifiable {
        case sales, purchases, payments, expenses

        var id: Self { self }
    }
}
