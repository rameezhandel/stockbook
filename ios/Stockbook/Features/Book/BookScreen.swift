import SwiftUI

/// The account book: every direction money moves.
///
/// **Sales** is what was sold and to whom; **Purchases** is what arrived and
/// from whom. Those two are mirror images in the domain — one `Statement.make`
/// serves both — so they belong beside each other rather than in two tabs.
///
/// **Expenses** is the odd one and sits here anyway. It is the owner's own
/// spending, tied to nobody, and it touches none of the arithmetic on the other
/// two chips. But it is money leaving, it is written down for the same reason
/// the others are, and the alternative was a fourth tab for something recorded
/// once a day — or Settings, which is where features go to be forgotten.
///
/// Chips rather than tabs, because the shop does not use these symmetrically: a
/// sale happens fifty times a day, a delivery arrives once a week. A tab bar is
/// weighted by how often a thumb goes there, not by how tidy the model is.
struct BookScreen: View {

    /// Which side is showing. `@SceneStorage` rather than `@State` so it
    /// survives a trip into a sheet and back — an owner who came here for
    /// suppliers should not be handed bills again on the way out.
    ///
    /// Stored as its raw string because `@SceneStorage` takes only the handful
    /// of types `AppStorage` does.
    @SceneStorage("book.side") private var stored = Side.sales.rawValue

    private var side: Side { Side(rawValue: stored) ?? .sales }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.bookTitle)

            HStack(spacing: 6) {
                ChoicePill(title: Loc.salesSide, icon: Icon.bills, selected: side == .sales) {
                    choose(.sales)
                }
                ChoicePill(title: Loc.purchasesSide, icon: Icon.items, selected: side == .purchases) {
                    choose(.purchases)
                }
                ChoicePill(title: Loc.expensesTitle, icon: Icon.expenses, selected: side == .expenses) {
                    choose(.expenses)
                }
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 10)

            // No `default` on purpose: a fourth side has to break this and be
            // placed deliberately, not fall through to whichever branch was last.
            switch side {
            case .sales:
                // The Bills screen exactly as it was, minus the header this one
                // now carries. Nothing about sales moved; it gained neighbours.
                BillsScreen(showsHeader: false)
            case .purchases:
                PurchasesPane()
            case .expenses:
                ExpensesPane()
            }
        }
    }

    private func choose(_ next: Side) {
        withAnimation(Metrics.quick) { stored = next.rawValue }
    }

    /// Which chip is on.
    private enum Side: String {
        case sales, purchases, expenses
    }
}
