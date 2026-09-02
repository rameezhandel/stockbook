import SwiftUI

/// One result. Tapping it opens whatever the record is — a bill, a receipt, a
/// purchase, an expense.
///
/// **It says what kind of thing it is, because the list is mixed.** Every other
/// list in the app is one kind of record, and its rows can leave that unsaid.
/// Here a name and a figure without a word beside them would be four rows the
/// owner has to open to tell apart.
struct SearchRow: View {
    let hit: SearchHit

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(hit.who)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                // The kind first, then the number that was probably typed to get
                // here. A record with no number says only what it is, which is
                // still the thing the row has to establish.
                Text(hit.reference.map { "\(label) · \($0)" } ?? label)
                    .nocturneText(.meta)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(hit.amount, in: currency))
                    .font(NocturneType.inter(14))
                    .lineLimit(1)
                // The day, always. The lists in the book have a span above them
                // and can leave it out; a result could be from any year, which is
                // the point of searching rather than scrolling.
                Text(Loc.longDate(hit.at))
                    .nocturneText(.meta)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }

    /// What the row calls each kind. Singular: the plurals in `Strings` head
    /// lists.
    ///
    /// A `switch` with no `default`, so a seventh kind of record stops this
    /// compiling rather than quietly showing up unlabelled.
    private var label: String {
        switch hit.kind {
        case .bill: Loc.billLabel
        case .payment: Loc.paymentLabel
        case .creditNote: Loc.creditNoteLabel
        case .purchase: Loc.purchaseLabel
        case .supplierPayment: Loc.voucherLabel
        case .expense: Loc.expenseLabel
        }
    }
}
