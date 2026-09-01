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
    @Environment(AppRouter.self) private var router
    @Environment(StockbookStore.self) private var store

    /// The rendered book, waiting for the share sheet.
    @State private var file: StatementFile?

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
            // Every customer's position on one day. It belongs on this tab
            // rather than beside the day's transactions on Home: it is a list of
            // people, read down, and this is the screen the owner comes to when
            // the question is about people rather than about today.
            ScreenHeader(title: Loc.bookTitle) {
                HStack(spacing: 0) {
                    // Every customer's whole history, printed once and filed.
                    // Beside the day page rather than buried in Settings: they
                    // are the two things this screen can hand to a printer, and
                    // one of them being somewhere else is how the other is never
                    // found.
                    Button(action: saveLedgerBook) {
                        Glyph(Icon.bills, size: 18)
                    }
                    .buttonStyle(.iconOnly)
                    .accessibilityLabel(Loc.ledgerBook)

                    Button {
                        router.ledgerDay = .now
                    } label: {
                        Glyph(Icon.customer, size: 18)
                    }
                    .buttonStyle(.iconOnly)
                    .accessibilityLabel(Loc.dayBalances)
                }
            }

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
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }

    private func choose(_ next: Side) {
        withAnimation(Metrics.quick) { stored = next.rawValue }
    }

    /// Every customer's whole history as one document, a page each.
    ///
    /// Drawn through the same routine that draws a single statement, so a sheet
    /// pulled out of this book is exactly the statement that customer would have
    /// been handed.
    ///
    /// A failure leaves `file` nil and nothing opens, which is the honest outcome
    /// the other pages already settled on.
    private func saveLedgerBook() {
        let pages = store.ledgerBook().map {
            StatementDocument.make(statement: $0, settings: store.settings, strings: Loc)
        }
        guard !pages.isEmpty,
              let url = try? StatementPDF.write(
                  pages,
                  fileName: Loc.ledgerBookFileName(Copy.fileDate(.now))
              )
        else { return }
        file = StatementFile(url: url)
    }

    /// Which chip is on.
    private enum Side: String {
        case sales, purchases, expenses
    }
}
