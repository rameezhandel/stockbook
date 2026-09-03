import SwiftUI

/// Which span a screen is showing.
///
/// Held as the *choice* rather than as a `StatementPeriod`, because a period
/// carries a date: `.month(.now)` built for the chip would never equal the
/// `.month(.now)` built a moment earlier and stored, so no chip would ever look
/// selected. The period is derived from this instead, which also means "this
/// month" is still this month if the app is left open past midnight on the 1st.
enum PeriodChoice: String, Equatable {
    case thisMonth, lastMonth, thisYear, dates

    /// The span itself, given whatever two dates the picker is holding.
    ///
    /// Here rather than in each screen, so a statement and a list of bills
    /// headed "this month" are never two different months.
    func period(from: Date, to: Date) -> StatementPeriod {
        switch self {
        case .thisMonth: .thisMonth()
        case .lastMonth: .lastMonth()
        case .thisYear: .thisYear()
        case .dates: .custom(from: from, to: to)
        }
    }
}

/// The row of spans, and the two dates underneath when the owner is choosing
/// them.
///
/// Shared rather than written per screen: the statement and the sales list both
/// ask the same question, and two pickers that must look and behave the same are
/// two pickers that will not, the first time either is corrected.
struct PeriodPicker: View {
    @Binding var choice: PeriodChoice
    @Binding var from: Date
    @Binding var to: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Three taps that answer almost every question, and a fourth for the
            // month-end that does not start on the 1st.
            FlowLayout(spacing: 6) {
                chip(Loc.thisMonth, isOn: choice == .thisMonth) { choice = .thisMonth }
                chip(Loc.lastMonth, isOn: choice == .lastMonth) { choice = .lastMonth }
                chip(Loc.thisYear, isOn: choice == .thisYear) { choice = .thisYear }
                chip(Loc.chooseDates, isOn: choice == .dates) { choice = .dates }
            }

            if choice == .dates {
                dateRangeCard
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(NocturneType.inter(12))
                .foregroundStyle(isOn ? Nocturne.bg : Nocturne.accent)
                .padding(.horizontal, 11)
                .padding(.vertical, 5)
                .frame(minHeight: 32)
                .background(
                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                        .fill(isOn ? Nocturne.accent : Color.clear)
                )
                .hairline(Nocturne.accent, radius: 7)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var dateRangeCard: some View {
        // Side by side, not stacked. A span is one fact with two ends, and
        // reading it down a column made two settings out of it — the second of
        // which the owner could scroll past without noticing they had left it on
        // today.
        //
        // The label goes above each picker rather than beside it: `22 Aug 2026`
        // and its own label do not both fit in half a card's width, and the label
        // is the smaller thing.
        HStack(alignment: .top, spacing: 12) {
            dateBox(Loc.fromDate, selection: $from)
            dateBox(Loc.toDate, selection: $to)
        }
        .font(NocturneType.inter(13))
        .tint(Nocturne.accent)
        .padding(12)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
        // No `onChange` needed: the period is derived, so moving either picker
        // rebuilds whatever is below on the next pass. Whichever way round they
        // were dragged, `StatementPeriod` sorts out.
    }

    /// One end of the span: the label over the day, in half the card's width.
    private func dateBox(_ label: String, selection: Binding<Date>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .nocturneText(.meta)
            DatePicker("", selection: selection, displayedComponents: .date)
                .labelsHidden()
                .datePickerStyle(.compact)
                .font(NocturneType.inter(13))
                .tint(Nocturne.accent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
