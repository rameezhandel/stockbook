import SwiftUI

/// Every bill ever saved, newest first — and the only correction path the app
/// has.
///
/// Nothing here is deleted. A bill entered wrong is *voided*, which puts its
/// stock back and leaves the record in place with a "voided" mark. Without that,
/// one mistyped bill puts the shelf and the app permanently out of step; with
/// deletion instead, the history quietly stops matching what actually happened.
struct BillsScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    private var bills: [Bill] { store.bills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.billsTitle)

            ScrollView {
                LazyVStack(spacing: Metrics.rowGap) {
                    if bills.isEmpty {
                        EmptyStateBox(
                            icon: Icon.bills,
                            message: Loc.noBillsEver,
                            actionTitle: Loc.startABill,
                            action: { router.startBill() }
                        )
                        .padding(.top, 8)
                    }

                    ForEach(bills) { bill in
                        Button {
                            router.openBill(bill)
                        } label: {
                            BillRow(bill: bill)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
        }
    }
}
