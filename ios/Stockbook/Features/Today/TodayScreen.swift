import SwiftUI

/// The home screen: what is owed each way, who owes money, and the last few
/// bills.
struct TodayScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    private var bills: [Bill] { store.bills }
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
                    owedBanner(owed, followedByPayable: !payable.names.isEmpty)
                    payableBanner(payable)
                    recentBills
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
                    : Loc.youOweMany(payable.names.count),
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
    private func owedNote(names: [String]) -> String {
        names.count == 1
            ? Loc.stillOwes(oneName: names[0])
            : Loc.stillOwe(customerCount: names.count)
    }

    // MARK: Recent bills

    private var recentBills: some View {
        VStack(spacing: 0) {
            HStack {
                Kicker(Loc.recentBills)
                Spacer()
                Button(Loc.all) { router.tab = .book }
                    .buttonStyle(.ghost)
            }
            .padding(.bottom, 9)

            if bills.isEmpty {
                EmptyStateBox(
                    message: Loc.noBillsToday,
                    actionTitle: Loc.startABill,
                    action: { router.startBill() }
                )
            } else {
                VStack(spacing: Metrics.rowGap) {
                    ForEach(Array(bills.prefix(3))) { bill in
                        Button {
                            router.openBill(bill)
                        } label: {
                            BillRow(bill: bill)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}
