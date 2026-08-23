import SwiftUI

/// What the owner spent, and nothing else.
///
/// The third side of the book, and the one joined to nobody: no customer, no
/// supplier, no bill. The line under the total says so out loud, because a
/// shopkeeper writing down their petrol deserves to know at a glance that it
/// will not turn up on a customer's statement.
///
/// Same shape as the other two panes — a figure on top, a list underneath, and
/// every row a way in to correcting it.
struct ExpensesPane: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// Which span the total covers. Screen-local and not remembered across
    /// launches, for the reason Home's is not: the useful answer on opening the
    /// app is almost always this month.
    @State private var span: Span = .thisMonth

    private var expenses: [Expense] { store.expenses }

    /// The rendered page, waiting for the share sheet.
    @State private var file: StatementFile?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Metrics.rowGap) {
                totalCard
                    .padding(.bottom, 20 - Metrics.rowGap)

                // Beside the figure it summarises, and only where there is
                // something to summarise: a page saying nothing was spent is a
                // page nobody needs. The span the card is showing is the span the
                // page covers — one control for both, so the two can never
                // quietly disagree.
                if store.spentIn(span.period) > 0 {
                    Button(Loc.savedList, action: save)
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 40, fontSize: 13))
                        .padding(.bottom, 20 - Metrics.rowGap)
                }

                HStack {
                    Kicker(Loc.expensesTitle)
                    Spacer(minLength: 6)
                    Button(Loc.addAnExpense) { router.openNewExpense() }
                        .buttonStyle(GhostButtonStyle(fontSize: 12))
                }

                if expenses.isEmpty {
                    EmptyStateBox(
                        icon: Icon.expenses,
                        message: Loc.noExpensesYet,
                        actionTitle: Loc.addAnExpense,
                        action: { router.openNewExpense() }
                    )
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
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 18)
            .motion(Motion.list, value: expenses.count)
        }
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest
    /// outcome `StatementScreen` already settled on: there is no half-written
    /// page worth offering, and the figures are still on screen.
    private func save() {
        let period = span.period
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

    // MARK: The total

    private var totalCard: some View {
        // Read once: it is a walk over every expense, and the rolling animation
        // needs the same figure the label shows.
        let spent = store.spentIn(span.period)
        let text = Money.text(spent, in: currency)

        return VStack(alignment: .leading, spacing: 0) {
            Text(Loc.expenseInPeriod)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(text)
                .nocturneText(NocturneType.fittedNumber(text))
                .lineLimit(1)
                .rollingNumber(spent)
                .padding(.top, 3)
            Text(Loc.expensesArePrivate)
                .nocturneText(.meta)
                .padding(.top, 2)
                .padding(.bottom, 10)

            HStack(spacing: 6) {
                ForEach(Span.allCases) { candidate in
                    chip(candidate)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
    }

    private func chip(_ candidate: Span) -> some View {
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

    /// The three spans Home offers, for the same reason it offers them: picking
    /// two dates is a job for a document you are about to send somebody.
    ///
    /// Its label is resolved by the view rather than on the enum: `Loc` is
    /// main-actor isolated and a bare enum is not.
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
}

/// One expense. Tapping it opens the sheet it was written on, which is where it
/// is corrected or removed — the same rule a bill and a delivery follow.
private struct ExpenseRow: View {
    let expense: Expense

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(expense.note)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                Text(Loc.pickedDate(expense.spentAt))
                    .nocturneText(.meta)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(Money.text(expense.amount, in: currency))
                .font(NocturneType.inter(14))
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}
