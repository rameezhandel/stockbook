import SwiftUI

/// The sales half of the book: who the shop sells to, then every bill it has
/// written, newest first.
///
/// The customers came first deliberately. This screen used to open on the bills
/// with a customer *filter* above them, which made a person something you
/// narrowed a list by rather than something you could go and look at. What is
/// owed is the question this half of the book exists to answer, and the people
/// are where the answer lives — so they are what it opens on, and the bills are
/// the ledger underneath.
///
/// Nothing on either list corrects anything. A bill entered wrong is **edited or
/// removed** from the document itself, and either puts its stock back where it
/// belongs. The row is a way in; the correction lives one tap further on.
struct BillsScreen: View {
    /// False inside the book, which carries one header for both halves.
    var showsHeader = true

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    private var bills: [Bill] { store.bills }

    var body: some View {
        VStack(spacing: 0) {
            if showsHeader {
                ScreenHeader(title: Loc.billsTitle, bottomPadding: 10)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: Metrics.rowGap) {
                    PartyList(
                        title: Loc.customersTitle,
                        rows: store.customers().map(\.directoryRow),
                        search: { store.customers(matching: $0).map(\.directoryRow) },
                        addTitle: Loc.addACustomer,
                        emptyMessage: Loc.noCustomersYet,
                        onAdd: { router.openNewCustomer() },
                        onOpen: { router.openCustomerScreen($0) }
                    )
                    .padding(.bottom, 20 - Metrics.rowGap)

                    HStack {
                        Kicker(Loc.billsTitle)
                        Spacer(minLength: 0)
                    }

                    if bills.isEmpty {
                        EmptyStateBox(
                            icon: Icon.bills,
                            message: Loc.noBillsEver,
                            actionTitle: Loc.startABill,
                            action: { router.startBill() }
                        )
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
            .scrollDismissesKeyboard(.interactively)
        }
    }
}
