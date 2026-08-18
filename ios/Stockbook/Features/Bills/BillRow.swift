import SwiftUI

/// A bill as it appears on Today and on Bills. Tapping one opens `BillSheet`.
///
/// The row carries no destructive action of its own: correcting or removing a
/// bill lives inside the opened document, so reaching either costs a deliberate
/// tap first, and the list stays a list.
struct BillRow: View {
    let bill: Bill

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(bill.summary)
                    .nocturneText(.rowValue)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Text(meta).nocturneText(.meta)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(Money.text(bill.total, in: currency))
                .font(NocturneType.inter(15))
                .foregroundStyle(totalColor)

            Glyph(Icon.openRow, size: 12)
                .foregroundStyle(Nocturne.neutral600)
        }
        .padding(12)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }

    /// `Ahmed Contracting · 09:41 · 2 items · owes SAR 94`
    private var meta: String {
        var parts: [String] = []
        if !bill.who.isBlank { parts.append(bill.who) }
        parts.append(Loc.time(bill.createdAt))
        parts.append(Loc.items(bill.lines.count))
        if bill.isPartPaid, bill.balance > 0 {
            parts.append(Loc.owes(Money.text(bill.balance, in: currency)))
        }
        return parts.joined(separator: " · ")
    }

    private var totalColor: Color {
        bill.isPartPaid ? Nocturne.accent400 : Nocturne.text
    }
}
