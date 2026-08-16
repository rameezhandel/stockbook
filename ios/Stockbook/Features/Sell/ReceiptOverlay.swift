import SwiftUI

/// What the owner turns to face the customer. Full-screen and opaque — the bill
/// is saved, the stock has moved, and there is nothing left to edit here.
struct ReceiptOverlay: View {
    let bill: Bill

    @Environment(AppRouter.self) private var router
    @Environment(StockbookStore.self) private var store
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

            // No meta line here any more: the template below carries the
            // number, the time and the customer, and saying them twice on one
            // screen made the confirmation read like a form.
            Text(Loc.billSaved).font(NocturneType.inter(18, .medium))
            Spacer(minLength: 0)
        }
        .padding(.bottom, 18)
    }

    /// The same document the Bills tab opens, so the thing confirmed here and
    /// the thing looked up later can never drift apart.
    private var card: some View {
        ScrollView {
            BillTemplate(bill: bill, shopName: store.settings.ownerName)
        }
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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

}
