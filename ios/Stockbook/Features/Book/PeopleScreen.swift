import SwiftUI

/// Everybody the shop deals with: who owes it, and who it owes.
///
/// Its own tab because it is its own task. This was the top half of Reports, and
/// Reports was two screens sharing one scroll — a directory you come to in order
/// to **find somebody**, stacked on a ledger you come to in order to **browse
/// records**. Different verbs, and the chip row switched both at once, which is
/// why expenses — having no people — never fitted the pattern.
///
/// **One tab for both sides, not two.** A shop looks up a name; which side of the
/// counter that name is on is something it already knows. Chips rather than tabs
/// for the same reason the Book's were: the two are not used symmetrically, and a
/// supplier is looked up a fraction as often as a customer.
///
/// Nothing here corrects anything. A name is opened, and what can be done to it
/// lives on the party's own screen.
struct PeopleScreen: View {
    @Environment(AppRouter.self) private var router
    @Environment(StockbookStore.self) private var store

    /// Which side is showing. `@SceneStorage` rather than `@State` so it survives
    /// a trip into a party's screen and back — somebody who came here for
    /// suppliers should not be handed customers again on the way out.
    ///
    /// Stored as its raw string because `@SceneStorage` takes only the handful of
    /// types `AppStorage` does.
    @SceneStorage("people.side") private var stored = Side.customers.rawValue

    private var side: Side { Side(rawValue: stored) ?? .customers }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.tab(.people))

            HStack(spacing: 6) {
                ChoicePill(title: Loc.customersTitle, icon: Icon.customer, selected: side == .customers) {
                    choose(.customers)
                }
                ChoicePill(title: Loc.suppliersTitle, icon: Icon.items, selected: side == .suppliers) {
                    choose(.suppliers)
                }
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 12)

            ScrollView {
                // No `default` on purpose: a third kind of person has to break
                // this and be placed deliberately.
                switch side {
                case .customers:
                    PartyList(
                        title: Loc.customersTitle,
                        rows: store.customers().map(\.directoryRow),
                        search: { store.customers(matching: $0).map(\.directoryRow) },
                        addTitle: Loc.addACustomer,
                        emptyMessage: Loc.noCustomersYet,
                        onAdd: { router.openNewCustomer() },
                        onOpen: { router.openCustomerScreen($0) }
                    )
                case .suppliers:
                    PartyList(
                        title: Loc.suppliersTitle,
                        rows: store.suppliers().map(\.directoryRow),
                        search: { store.suppliers(matching: $0).map(\.directoryRow) },
                        addTitle: Loc.addASupplier,
                        emptyMessage: Loc.noSuppliersYet,
                        onAdd: { router.openNewSupplier() },
                        onOpen: { router.openSupplierScreen($0) }
                    )
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .padding(.horizontal, Metrics.screenPadding)
            // Back to the top when the side changes. Two hundred customers
            // scrolled halfway down, then three suppliers, lands the owner at the
            // bottom of a list they have not read a line of.
            .id(side)
        }
    }

    private func choose(_ next: Side) {
        withAnimation(Metrics.quick) { stored = next.rawValue }
    }

    /// Which side of the counter is showing.
    private enum Side: String {
        case customers, suppliers
    }
}
