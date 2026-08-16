import SwiftUI

/// What the owner turns to face the customer. Full-screen and opaque — the bill
/// is saved, the stock has moved, and there is nothing left to edit here.
struct ReceiptOverlay: View {
    let bill: Bill

    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency
    @Environment(\.topSafeInset) private var topInset
    @Environment(\.bottomSafeInset) private var bottomInset

    /// The check pops rather than fades: it is the one moment in the app worth
    /// a flourish, and the overshoot is what makes it read as confirmation.
    @State private var checkScale: CGFloat = 0.4

    var body: some View {
        VStack(spacing: 0) {
            header
            card
            actions
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.top, max(8, 66 - topInset))
        .padding(.bottom, max(bottomInset, 24))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Nocturne.bg.ignoresSafeArea())
        .onAppear {
            withAnimation(.spring(response: 0.34, dampingFraction: 0.52)) {
                checkScale = 1
            }
        }
    }

    private var header: some View {
        HStack(spacing: 11) {
            Glyph(Icon.confirm, size: 18)
                .foregroundStyle(Nocturne.accent)
                .frame(width: 36, height: 36)
                .overlay(Circle().strokeBorder(Nocturne.accent, lineWidth: 1))
                .scaleEffect(checkScale)

            VStack(alignment: .leading, spacing: 2) {
                Text(Loc.billSaved).font(NocturneType.inter(18, .medium))
                Text(meta).nocturneText(.meta)
            }
            Spacer(minLength: 0)
        }
        .padding(.bottom, 18)
    }

    private var card: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(bill.lines) { line in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(line.name)
                            .font(NocturneType.inter(14))
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(line.qty) × \(Money.text(line.price, in: currency))")
                            .nocturneText(.meta)
                        Text(Money.text(line.lineTotal, in: currency))
                            .font(NocturneType.inter(14))
                    }
                    .padding(.vertical, 7)
                }

                FadedRule()
                    .padding(.vertical, 9)

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
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 7)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.statRadius, style: .continuous))
    }

    private var actions: some View {
        HStack(spacing: 8) {
            Button(Loc.seeBills) {
                router.receipt = nil
                router.tab = .bills
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 46))

            // The cart was already cleared on save, so this lands on an empty
            // new bill — the next customer is usually already waiting.
            Button(Loc.nextCustomer) {
                router.receipt = nil
                router.tab = .sell
            }
            .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 46))
        }
        .padding(.top, 14)
    }

    /// `Bill #1 · 09:41 · Ahmed Contracting`
    private var meta: String {
        Loc.receiptMeta(number: bill.number, time: Loc.time(bill.createdAt), who: bill.who)
    }

    /// `Paid in full, cash.` or `Paid SAR 100 · Ahmed Contracting owes SAR 94`
    private var paymentNote: String {
        guard let paid = bill.paid else { return Loc.paidInFullCash }
        return Loc.partPaidNote(
            paid: Money.text(paid, in: currency),
            who: bill.who,
            balance: Money.text(bill.balance, in: currency)
        )
    }
}
