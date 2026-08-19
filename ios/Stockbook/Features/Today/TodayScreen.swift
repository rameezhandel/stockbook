import SwiftUI

/// The home screen: what the shop sold, what is owed each way, who owes money,
/// and what the shelf is running short of.
struct TodayScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    private var settings: Settings { store.settings }

    /// Which span the sales card is showing. Screen-local and not remembered
    /// across launches: the useful answer on opening the app in the morning is
    /// almost always this month, and a screen that came back showing last March
    /// would be quietly lying about "Sold".
    @State private var span: Span = .thisMonth

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(
                kicker: Loc.headerDate(.now),
                title: greeting
            ) {
                Button {
                    router.showingSettings = true
                } label: {
                    Glyph(Icon.settings, size: 18)
                }
                .buttonStyle(.iconOnly)
                .accessibilityLabel(Loc.settings)
            }

            ScrollView {
                // Both sides read once here rather than inside the banners: each
                // is a walk over every bill or delivery, and the first banner's
                // spacing depends on whether the second one is there.
                let owed = store.outstanding()
                let payable = store.payable()

                VStack(spacing: 0) {
                    // What the shop turned over, over a span the owner picks. The
                    // two cards below are balances and answer "where do I stand";
                    // this one answers "how did we do", which is a different
                    // question and the only one on this screen with a period
                    // attached to it.
                    soldCard
                    statCards(owed, payable)
                    // Money first, then what to do, then what needs attention.
                    quickActions
                    owedBanner(owed, followedByPayable: !payable.names.isEmpty)
                    payableBanner(payable)
                    runningLow
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.top, 4)
                .padding(.bottom, 18)
            }
        }
    }

    private var greeting: String {
        let first = settings.ownerName.firstName
        return first.isEmpty ? Loc.today : Loc.greeting(first)
    }

    // MARK: Stats

    /// The three spans Home offers.
    ///
    /// The statement screen's first three chips, minus its custom range: picking
    /// two dates is a job for the document you are about to send somebody, not
    /// for a glance on the way past. The period arithmetic is `StatementPeriod`'s
    /// either way, so "this month" means the same thing on both screens.
    ///
    /// Carries the period and nothing else. Its label is resolved by the view
    /// rather than here: `Loc` is main-actor isolated and a bare enum is not, so
    /// reading a string from inside it does not compile — the Kotlin twin takes
    /// `Strings` as a parameter for the same separation, arrived at from the
    /// other direction.
    private enum Span: CaseIterable, Identifiable {
        case thisMonth, lastMonth, thisYear

        var id: Self { self }

        var period: StatementPeriod {
            switch self {
            case .thisMonth: .thisMonth()
            case .lastMonth: .lastMonth()
            case .thisYear: .thisYear()
            }
        }
    }

    private func label(for span: Span) -> String {
        switch span {
        case .thisMonth: Loc.thisMonth
        case .lastMonth: Loc.lastMonth
        case .thisYear: Loc.thisYear
        }
    }

    private var soldCard: some View {
        // Read once: it is a walk over every bill, and the rolling animation
        // needs the same figure the label shows.
        let sold = store.soldIn(span.period)

        return VStack(alignment: .leading, spacing: 0) {
            Text(Loc.soldInPeriod)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(Money.text(sold, in: currency))
                .nocturneText(.bigNumber(26))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .rollingNumber(sold)
                .padding(.top, 3)
                .padding(.bottom, 10)

            HStack(spacing: 6) {
                ForEach(Span.allCases) { candidate in
                    spanChip(candidate)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.statRadius, style: .continuous))
        .hairline(radius: Metrics.statRadius)
        .padding(.bottom, Metrics.cardGap)
    }

    /// The statement screen's chip, at the size a card has room for.
    private func spanChip(_ candidate: Span) -> some View {
        let selected = candidate == span
        return Button {
            withAnimation(Metrics.quick) { span = candidate }
        } label: {
            Text(label(for: candidate))
                .font(NocturneType.inter(11.5))
                .foregroundStyle(selected ? Nocturne.accent : Nocturne.neutral500)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .background(
                    selected ? Nocturne.primaryPressed : Color.clear,
                    in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
                )
                .hairline(selected ? Nocturne.accent : Nocturne.divider, radius: Metrics.controlRadius)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func statCards(_ owed: (names: [String], total: Double), _ payable: (names: [String], total: Double)) -> some View {
        HStack(spacing: Metrics.cardGap) {
            StatCard(
                label: Loc.receivableStat,
                value: Money.text(owed.total, in: currency),
                gradient: true
            )
            StatCard(label: Loc.payableStat, value: Money.text(payable.total, in: currency))
        }
        .padding(.bottom, Metrics.cardGap)
    }

    // MARK: Starting something

    /// The three things the owner begins from nothing, on the screen they land
    /// on.
    ///
    /// Home was entirely reports: every card answered a question and not one of
    /// them could be pressed. Two of these were also genuinely far away — a
    /// delivery was reachable only from the Items header, a new customer only
    /// from the filter at the top of the Book — which is a long way round for the
    /// two things that happen when a van pulls up outside.
    ///
    /// A bill is the exception: Sell is already a tab, so this saves no taps. It
    /// is here because a row of two would look like something was missing, and
    /// because starting a bill is what the owner came to do.
    private var quickActions: some View {
        HStack(spacing: Metrics.cardGap) {
            quickAction(Loc.startABill, icon: Icon.sell) { router.startBill() }
            quickAction(Loc.itemsRecordDelivery, icon: Icon.addStock) { router.recordDelivery() }
            quickAction(Loc.addACustomer, icon: Icon.customer) { router.openNewCustomer() }
        }
        .padding(.bottom, 18)
    }

    private func quickAction(_ title: String, icon: String, run: @escaping () -> Void) -> some View {
        Button(action: run) {
            VStack(spacing: 7) {
                Glyph(icon, size: 18)
                    .foregroundStyle(Nocturne.accent)
                // Two lines allowed, because "Add a customer" does not fit on one
                // at a third of the width and Kannada is longer again.
                Text(title)
                    .font(NocturneType.inter(11.5))
                    .foregroundStyle(Nocturne.neutral400)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .padding(.horizontal, 6)
            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .hairline(radius: Metrics.cardRadius)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: Owed

    @ViewBuilder
    private func owedBanner(_ owed: (names: [String], total: Double), followedByPayable: Bool) -> some View {
        if !owed.names.isEmpty {
            banner(
                note: owedNote(names: owed.names),
                amount: owed.total,
                icon: Icon.owed,
                // The banner is where the debt gets noticed; the list behind it is
                // where it gets collected. Without this tap the only route to
                // Ahmed's cash was to remember to go and find Ahmed in the Book.
                action: { router.showingDebtors = true }
            )
            // Tightened when the second banner follows, so the pair reads as one
            // block of money rather than two unrelated notices.
            .padding(.bottom, followedByPayable ? 6 : 18)
        }
    }

    /// The other direction, and only when there is one.
    ///
    /// A shop owner's own bills matter as much as the ones owed to them, but a
    /// banner saying "you owe nothing" every day teaches people to stop reading
    /// banners.
    @ViewBuilder
    private func payableBanner(_ payable: (names: [String], total: Double)) -> some View {
        if !payable.names.isEmpty {
            banner(
                note: payable.names.count == 1
                    ? Loc.youOweOne(payable.names[0])
                    : Loc.youOweWithOthers(payable.names[0], others: payable.names.count - 1),
                amount: payable.total,
                icon: Icon.items,
                action: { router.showingCreditors = true }
            )
            .padding(.bottom, 18)
        }
    }

    /// One banner body, both directions — what a debt looks like does not change
    /// with who owes it.
    private func banner(note: String, amount: Double, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Glyph(icon, size: 19)
                    .foregroundStyle(Nocturne.accent400)
                Text(note)
                    .font(NocturneType.inter(12.5))
                    .foregroundStyle(Nocturne.neutral400)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(Money.text(amount, in: currency))
                    .font(NocturneType.inter(16))
                    .foregroundStyle(Nocturne.accent400)
                    .rollingNumber(amount)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 12)
            .background(Nocturne.surface)
            .clipShape(.rect(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: Metrics.cardRadius, topTrailingRadius: Metrics.cardRadius))
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(Nocturne.accent)
                    .frame(width: 2)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// One name reads as a name; several read as a count of **people**, not bills.
    /// The biggest debtor by name — `outstanding()` is sorted by what is owed —
    /// and the rest as a count.
    private func owedNote(names: [String]) -> String {
        names.count == 1
            ? Loc.stillOwes(oneName: names[0])
            : Loc.stillOweWithOthers(names[0], others: names.count - 1)
    }

    // MARK: Running low

    /// What the shelf is short of, and a tap straight to restocking it.
    ///
    /// This used to be the three most recent bills — which is the Reports tab,
    /// one tap away, on the screen the owner lands on. Nothing else in the app
    /// volunteers that something is running out, and it is the one thing here
    /// they can act on while standing at the counter.
    private var runningLow: some View {
        // Emptiest first: the shelf closest to costing a sale.
        let low = store.products
            .filter { $0.isLow(threshold: store.settings.lowStockAt) }
            .sorted { $0.stock < $1.stock }

        return VStack(spacing: 0) {
            HStack {
                Kicker(Loc.runningLow)
                Spacer()
                Button(Loc.all) { router.tab = .items }
                    .buttonStyle(.ghost)
            }
            .padding(.bottom, 9)

            if store.products.isEmpty {
                EmptyStateBox(
                    message: Loc.shelfEmpty,
                    actionTitle: Loc.addAProduct,
                    action: { router.openNewProduct() }
                )
            } else if low.isEmpty {
                // Said rather than left blank. An empty space here reads as a
                // section that failed to load; one line reads as good news.
                Text(Loc.nothingRunningLow)
                    .nocturneText(.meta)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                VStack(spacing: Metrics.rowGap) {
                    ForEach(low.prefix(4)) { product in
                        Button {
                            router.openAddStock(for: product)
                        } label: {
                            lowStockRow(product)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    /// One product the shelf is short of.
    ///
    /// The count is drawn in the accent, not the neutral it wears on the Items
    /// screen: everything in this list is by definition low, so the colour is the
    /// point rather than a warning some rows carry and others do not.
    private func lowStockRow(_ product: Product) -> some View {
        HStack(spacing: 8) {
            Text(product.name)
                .nocturneText(.rowPrimary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(Loc.stockLabel(product.stock))
                .font(NocturneType.inter(13))
                .foregroundStyle(Nocturne.accent400)
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}
