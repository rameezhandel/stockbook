import SwiftUI

/// One expense. Tapping it opens the sheet it was written on, which is where it
/// is corrected or removed — the same rule a bill and a delivery follow.
struct ExpenseRow: View {
    let expense: Expense

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(expense.note)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                // The date, and whatever the owner wrote beside the name, on one
                // meta line. Joined rather than stacked: a third line would make
                // an expense row taller than a bill's for the sake of an optional
                // afterthought, and most expenses have nothing here at all.
                Text(metaLine)
                    .nocturneText(.meta)
                    .lineLimit(1)
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

    private var metaLine: String {
        let date = Loc.pickedDate(expense.spentAt)
        guard let detail = expense.detail, !detail.isBlank else { return date }
        return "\(date) · \(detail)"
    }
}
