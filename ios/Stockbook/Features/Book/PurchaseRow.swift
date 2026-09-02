import SwiftUI

/// One purchase. Tapping it opens the document, which is where it can be
/// changed.
///
/// A file of its own, beside `BillRow`, since the pane it used to live at the
/// bottom of folded into `BookScreen`.
struct PurchaseRow: View {
    let purchase: Purchase
    let supplierName: String

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                // A supplier bill entered as a figure names no product, so the
                // row says what it is rather than showing a blank line where a
                // product name would be.
                Text(purchase.summary.isBlank ? Loc.supplierBillTitle : purchase.summary)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                // A purchase of one thing says what arrived and at what; several
                // say how many rather than repeating the arithmetic of each, since
                // a row has one line's worth of space. A supplier bill entered as
                // a figure has only the name to show, and "× 0" beside it would
                // read as a count the app lost.
                Text(rowDetail)
                .nocturneText(.meta)
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .trailing, spacing: 2) {
                Text(Money.text(purchase.total, in: currency))
                    .font(NocturneType.inter(14))
                Text(
                    purchase.balance > 0
                        ? Loc.owes(Money.text(purchase.balance, in: currency))
                        : Loc.longDate(purchase.createdAt)
                )
                .nocturneText(.meta)
                .foregroundStyle(purchase.balance > 0 ? Nocturne.accent400 : Nocturne.neutral500)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .contentShape(Rectangle())
    }

    /// Who it came from, and then what arrived: the arithmetic for a purchase of
    /// one thing, a count for several, and nothing more for a bill entered as a
    /// figure.
    private var rowDetail: String {
        let items = purchase.items
        switch items.count {
        case 0: return supplierName
        case 1:
            return "\(supplierName) · \(Loc.perPiece(qty: items[0].qty, cost: Money.text(items[0].unitCost, in: currency)))"
        default:
            return "\(supplierName) · \(Loc.items(items.count))"
        }
    }
}
