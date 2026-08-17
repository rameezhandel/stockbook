import SwiftUI

/// The account book, both halves of it.
///
/// **Sales** is what was sold and to whom; **Purchases** is what arrived and from
/// whom. The two are mirror images in the domain — one `Statement.make` serves
/// both — so they belong beside each other rather than in two tabs.
///
/// Two chips rather than two tabs, because the shop does not use the halves
/// symmetrically: a sale happens fifty times a day and a delivery arrives once a
/// week. A tab bar is weighted by how often a thumb goes there, not by how tidy
/// the model is.
struct BookScreen: View {

    /// Which half is showing. `@SceneStorage` rather than `@State` so it survives
    /// a trip into a sheet and back — an owner who came here for suppliers should
    /// not be handed bills again on the way out.
    @SceneStorage("book.showingSales") private var showingSales = true

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.bookTitle)

            HStack(spacing: 6) {
                ChoicePill(title: Loc.salesSide, icon: Icon.bills, selected: showingSales) {
                    withAnimation(Metrics.quick) { showingSales = true }
                }
                ChoicePill(title: Loc.purchasesSide, icon: Icon.items, selected: !showingSales) {
                    withAnimation(Metrics.quick) { showingSales = false }
                }
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 10)

            if showingSales {
                // The Bills screen exactly as it was, minus the header this one
                // now carries. Nothing about sales moved; it gained a neighbour.
                BillsScreen(showsHeader: false)
            } else {
                PurchasesPane()
            }
        }
    }
}
