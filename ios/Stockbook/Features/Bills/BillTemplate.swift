import SwiftUI

/// One bill, drawn as the thing you turn round and show a customer.
///
/// The list rows and the cart show a bill in pieces — a summary line, a total, a
/// meta strip. This is the whole of it in one place: who wrote it, its number,
/// when, for whom, every line with its arithmetic visible, and what is still
/// owed. It is used both for the confirmation right after saving and for opening
/// any bill from history, so those two can never drift apart.
///
/// Every value here is the **snapshot taken at sale time**. A product renamed or
/// repriced since does not change what this says, which is the whole reason
/// `BillLine` carries its own name and price.
struct BillTemplate: View {
    let bill: Bill
    /// The shop's own name, from settings. Blank is fine — a shop that never
    /// entered one simply has no letterhead.
    var shopName: String = ""

    @Environment(\.currency) private var currency

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            heading

            FadedRule().padding(.vertical, 12)

            lines

            FadedRule().padding(.vertical, 12)

            totals
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.statRadius, style: .continuous))
        .hairline(radius: Metrics.statRadius)
    }

    // MARK: Letterhead

    private var heading: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !shopName.isBlank {
                Kicker(shopName).padding(.bottom, 5)
            }

            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(bill.reference(Loc))
                    .font(NocturneType.inter(20, .medium))
                Spacer(minLength: 8)
            }
            .padding(.bottom, 4)

            Text(Loc.billWhen(date: Loc.longDate(bill.createdAt), time: Loc.time(bill.createdAt)))
                .nocturneText(.meta)

            if !bill.who.isBlank {
                Text(Loc.billedTo(bill.who))
                    .nocturneText(.meta)
                    .padding(.top, 2)
            }
        }
    }

    // MARK: The items

    private var lines: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(bill.lines) { line in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(line.name)
                            .font(NocturneType.inter(14))
                        // The arithmetic stays visible. A customer querying a
                        // total is nearly always querying one line's quantity
                        // or its price, and this is the answer without anyone
                        // having to recompute it at the counter.
                        Text(Loc.quantityAtPrice(
                            quantity: line.qty,
                            price: Money.text(line.price, in: currency)
                        ))
                        .nocturneText(.meta)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Text(Money.text(line.lineTotal, in: currency))
                        .font(NocturneType.inter(14))
                }
            }
        }
    }

    // MARK: What it came to

    private var totals: some View {
        VStack(alignment: .leading, spacing: 0) {
            // What was knocked off, where anything was. The customer's own copy
            // of the bill is exactly where a discount belongs — it is the reason
            // the figure is what it is, and a shop that gave 10% away should get
            // the credit for it. The *statement* is the document that carries
            // only the total.
            if bill.isDiscounted {
                HStack {
                    Text(Loc.subtotalLabel).nocturneText(.meta)
                    Spacer()
                    Text(Money.text(bill.subtotal, in: currency))
                        .font(NocturneType.inter(12.5))
                        .foregroundStyle(Nocturne.neutral400)
                }
                HStack {
                    Text(Loc.discountOf(Money.amount(bill.discountPercent ?? 0, in: currency)))
                        .nocturneText(.meta)
                    Spacer()
                    Text("− " + Money.text(bill.discountAmount ?? 0, in: currency))
                        .font(NocturneType.inter(12.5))
                        .foregroundStyle(Nocturne.accent400)
                }
                .padding(.bottom, 6)
            }

            HStack(alignment: .firstTextBaseline) {
                Text(Loc.total)
                    .font(NocturneType.inter(13))
                    .foregroundStyle(Nocturne.neutral500)
                Spacer()
                Text(Money.text(bill.total, in: currency))
                    .font(NocturneType.inter(25, .medium))
                    .tracking(25 * -0.02)
            }

            Text(paymentNote)
                .font(NocturneType.inter(12.5))
                .foregroundStyle(Nocturne.accent400)
                .lineSpacing(3)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 7)
        }
    }

    /// `Paid in full, cash.` or `Paid SAR 100 · Ahmed owes SAR 94`.
    private var paymentNote: String {
        guard let paid = bill.paid else { return Loc.paidInFullCash }
        return Loc.partPaidNote(
            paid: Money.text(paid, in: currency),
            who: bill.who,
            balance: Money.text(bill.balance, in: currency)
        )
    }
}
