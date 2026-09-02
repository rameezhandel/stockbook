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
