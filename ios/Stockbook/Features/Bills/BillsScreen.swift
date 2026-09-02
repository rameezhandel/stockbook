import SwiftUI

/// The sales half of the book: every bill the shop has written over a span,
/// newest first.
///
/// **The customers used to sit on top of this.** They have a tab of their own now
/// — see `PeopleScreen`. Finding a person and browsing records are two tasks with
/// two verbs, and stacking them made a scroll you had to go past to reach either.
/// The chip row above this pane was switching both halves at once, which is why
/// expenses, having no people, never fitted the pattern.
///
/// Nothing on this list corrects anything. A bill entered wrong is **edited or
/// removed** from the document itself, and either puts its stock back where it
/// belongs. The row is a way in; the correction lives one tap further on.
struct BillsScreen: View {
    /// False inside the book, which carries one header for both halves.
    var showsHeader = true

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    /// **This month by default.** The whole book is a year of rows before long,
    /// and the reason to open this list is almost always something written
    /// recently — so it opens on the span that answers that, and the other three
    /// chips are there for the question it does not.
    ///
    /// `@SceneStorage` rather than `@State` so the span survives opening a bill
    /// and coming back: a list that quietly reset to this month every time a
    /// document was closed would make a stretch of days impossible to read
    /// through. Stored as its raw string, because `@SceneStorage` takes only the
    /// handful of types `AppStorage` does.
    @SceneStorage("bills.period") private var stored = PeriodChoice.thisMonth.rawValue

    @State private var from = Calendar.current.date(byAdding: .month, value: -1, to: .now) ?? .now
    @State private var to = Date.now

    private var choice: PeriodChoice { PeriodChoice(rawValue: stored) ?? .thisMonth }

    private var bills: [Bill] { store.billsIn(choice.period(from: from, to: to)) }

    var body: some View {
        VStack(spacing: 0) {
            if showsHeader {
                ScreenHeader(title: Loc.billsTitle, bottomPadding: 10)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: Metrics.rowGap) {
                    // The customers used to sit above this list. They have a tab
                    // of their own now — finding a person and browsing records
                    // are two tasks, and stacking them made one scroll you had to
                    // go past to reach either.
                    HStack {
                        Kicker(Loc.billsTitle)
                        Spacer(minLength: 0)
                    }

                    PeriodPicker(
                        choice: Binding(get: { choice }, set: { stored = $0.rawValue }),
                        from: $from,
                        to: $to
                    )
                    .padding(.bottom, 10 - Metrics.rowGap)

                    // Two different nothings, and they need different words. A
                    // shop that has never written a bill wants the button; a shop
                    // that wrote none in August wants to be told that rather than
                    // invited to start one, because the bills it is looking for
                    // are on another chip.
                    if bills.isEmpty {
                        if store.bills.isEmpty {
                            EmptyStateBox(
                                icon: Icon.bills,
                                message: Loc.noBillsEver,
                                actionTitle: Loc.startABill,
                                action: { router.startBill() }
                            )
                        } else {
                            EmptyStateBox(icon: Icon.bills, message: Loc.nothingInThisPeriod)
                        }
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
