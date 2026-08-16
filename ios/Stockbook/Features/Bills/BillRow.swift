import SwiftUI

/// A bill as it appears on Today and on Bills.
struct BillRow: View {
    let bill: Bill
    var showsVoidAction = false
    var onVoid: (() -> Void)?

    @Environment(\.currencySymbol) private var symbol

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(bill.summary)
                        .nocturneText(.rowValue)
                        .foregroundStyle(bill.voided ? Nocturne.neutral500 : Nocturne.text)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(meta).nocturneText(.meta)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Text(Money.text(bill.total, symbol: symbol))
                    .font(NocturneType.inter(15))
                    .foregroundStyle(totalColor)
            }

            if showsVoidAction, !bill.voided, let onVoid {
                Button(Loc.voidAndRestock, action: onVoid)
                    .buttonStyle(GhostButtonStyle(fontSize: 11.5, tint: Nocturne.neutral500, horizontalPadding: 0))
                    .padding(.top, 5)
            }
        }
        .padding(12)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
    }

    /// `voided · Ahmed Contracting · 09:41 · 2 items · owes SAR 94`
    private var meta: String {
        var parts: [String] = []
        if bill.voided { parts.append(Loc.voided) }
        if !bill.who.isBlank { parts.append(bill.who) }
        parts.append(Loc.time(bill.createdAt))
        parts.append(Loc.items(bill.lines.count))
        if bill.isPartPaid, bill.balance > 0 {
            parts.append(Loc.owes(Money.text(bill.balance, symbol: symbol)))
        }
        return parts.joined(separator: " · ")
    }

    private var totalColor: Color {
        if bill.voided { return Nocturne.neutral500 }
        return bill.isPartPaid ? Nocturne.accent400 : Nocturne.text
    }
}
