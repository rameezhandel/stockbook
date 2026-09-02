import SwiftUI

/// One slip, either direction. Tapping it opens the receipt it was written on.
///
/// **The direction is a word, not a sign.** Both figures are money that moved
/// and both are positive; `SAR -900` beside a supplier's name would read as a
/// refund, which this app has no notion of. So the amount says how much and the
/// line under it says which way — "Received" or "Paid" — and the two are tinted
/// apart so a mixed list can be scanned without reading every line.
struct PaymentRow: View {
    let entry: PaymentEntry

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.who)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                // The number on the slip is the whole reason to scroll this list
                // — an owner holding receipt 008455 is trying to remember who
                // paid it — so it leads. A payment taken without one has nothing
                // to match against and shows the day instead, which is the next
                // thing somebody would search by.
                Text(entry.reference.map { Loc.paymentRef($0) } ?? Loc.longDate(entry.at))
                    .nocturneText(.meta)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(entry.amount, in: currency))
                    .font(NocturneType.inter(14))
                    .lineLimit(1)
                Text(entry.incoming ? Loc.receivedInPeriod : Loc.paidOutInPeriod)
                    .nocturneText(.meta)
                    .foregroundStyle(entry.incoming ? Nocturne.accent : Nocturne.accent400)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }
}
