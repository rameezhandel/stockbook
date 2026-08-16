import SwiftUI

/// Every bill ever saved, newest first — and the only correction path the app
/// has.
///
/// Nothing here is deleted. A bill entered wrong is *voided*, which puts its
/// stock back and leaves the record in place with a "voided" mark. Without that,
/// one mistyped bill puts the shelf and the app permanently out of step; with
/// deletion instead, the history quietly stops matching what actually happened.
struct BillsScreen: View {
    @EnvironmentObject private var store: StockbookStore
    @EnvironmentObject private var router: AppRouter

    private var bills: [Bill] { store.bills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Bills")

            ScrollView {
                LazyVStack(spacing: Metrics.rowGap) {
                    if bills.isEmpty {
                        EmptyStateBox(
                            icon: Icon.bills,
                            message: "Nothing sold yet. Every bill you save shows up here.",
                            actionTitle: "Start a bill",
                            action: { router.startBill() }
                        )
                        .padding(.top, 8)
                    }

                    ForEach(bills) { bill in
                        BillRow(
                            bill: bill,
                            showsVoidAction: true,
                            onVoid: { store.void(bill) }
                        )
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
        }
    }
}
