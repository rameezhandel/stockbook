import SwiftUI

/// Owns the objects everything else reads, and picks between setup and the app.
struct RootView: View {
    @State private var store: StockbookStore?
    @State private var router = AppRouter()
    @State private var cart = Cart()

    var body: some View {
        GeometryReader { proxy in
            Group {
                if let store {
                    AppRoot(store: store)
                        .environment(store)
                        .environment(router)
                        .environment(cart)
                } else {
                    Nocturne.bg
                }
            }
            // Measured once, at the root, and read by anything that needs to
            // position against the physical screen edge rather than the safe area.
            .environment(\.topSafeInset, proxy.safeAreaInsets.top)
            .environment(\.bottomSafeInset, proxy.safeAreaInsets.bottom)
        }
        .background(Nocturne.bg)
        // The keyboard must not resize the app, and this is the only level that
        // can say so.
        //
        // `AppShell` already asks for the same thing around the tab bar, but a
        // `GeometryReader` is itself laid out inside the keyboard's safe area:
        // when the keyboard appeared this one shrank, everything below was
        // measured into a shorter box, and the tab bar rode up with it. A child
        // cannot undo a parent that has already been resized.
        //
        // It also makes `bottomSafeInset` mean what its comment says. Measured
        // from a shrinking proxy it became the keyboard's height mid-edit,
        // rather than the distance to the physical bottom edge.
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .task {
            guard store == nil else { return }
            // A repository that cannot open its file is unrecoverable — there is
            // no server to fall back to — so this fails loudly rather than
            // running against a store the owner would type a day's bills into
            // and lose.
            let repository = try! JSONFileRepository(url: try! JSONFileRepository.defaultURL())
            let opened = StockbookStore(repository: repository)

            // Pictures the book no longer names, collected on the way in.
            //
            // Every path that strands one already sweeps for itself; this is the
            // net underneath, for the crash that happened between removing a
            // bill and removing its photograph. Cheap — a directory listing —
            // and it runs before anything can show those pictures.
            PhotoStore().sweep(keeping: opened.photoIDsInUse())

            store = opened
        }
    }
}

/// Setup gate plus the tab shell. Split out from `RootView` so the store is
/// non-optional from here down.
private struct AppRoot: View {
    let store: StockbookStore

    var body: some View {
        ZStack {
            Nocturne.bg.ignoresSafeArea()

            if store.settings.setupCompleted {
                AppShell()
            } else {
                SetupFlowView()
            }
        }
        .environment(\.currency, store.settings.currency)
        .environment(\.locale, store.settings.language.locale)
        .tint(Nocturne.accent)
        // Every string in the app is read from `L10n` at render time, and every
        // colour from `Nocturne`. Neither is observable, so nothing would redraw
        // on its own when the language or the theme changes. Keying the whole
        // tree on both rebuilds it instead — heavy-handed, and exactly right for
        // something that happens once and must leave nothing behind of the
        // language or the palette it replaced.
        .id("\(store.settings.language.rawValue)-\(store.settings.theme.rawValue)")
        // System-drawn things — the keyboard, the menu behind a dropdown, the
        // date picker — take their appearance from here rather than from the
        // phone. The build no longer pins `UIUserInterfaceStyle`, because pinning
        // it would win over this.
        .preferredColorScheme(store.settings.theme == .light ? .light : .dark)
    }
}

/// The four tabs, the tab bar, and the overlays that can sit above any of them.
private struct AppShell: View {
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart
    @Environment(StockbookStore.self) private var store

    var body: some View {
        @Bindable var router = router

        ZStack {
            VStack(spacing: 0) {
                Group {
                    switch router.tab {
                    case .today: TodayScreen()
                    case .items: ItemsScreen()
                    case .sell: SellScreen()
                    case .book: BookScreen()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .transition(.opacity)
                .motion(Motion.screen, value: router.tab)

                // The picker screen hides the tab bar behind its own sticky
                // footer once the cart has something in it; every other screen
                // shows it.
                if showsTabBar {
                    StockbookTabBar(selection: $router.tab)
                }
            }
            // The keyboard overlays the app; it never shoves it upwards. This
            // is the one place that has to say so — the tab bar lives here,
            // above all four screens, so protecting the screens individually
            // left the bar itself being pushed to the top of the display the
            // moment a search field took focus.
            .ignoresSafeArea([.container, .keyboard], edges: .bottom)

            if router.showingSettings {
                SettingsScreen()
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }

            if router.showingBackup {
                BackupScreen()
                    .transition(.move(edge: .trailing))
                    .zIndex(2)
            }

            if let receipt = router.receipt {
                ReceiptOverlay(bill: receipt)
                    .transition(.opacity)
                    .zIndex(3)
            }

            // One person, full screen, over whichever half of the book they were
            // opened from. Below the statement, which is reached *from* here and
            // has to sit on top of it.
            if let key = router.partyFor {
                PartyScreen(partyKey: key, isSupplier: router.partyIsSupplier) {
                    router.partyFor = nil
                }
                .transition(.move(edge: .trailing))
                .zIndex(3.5)
            }

            // A document rather than a sheet: it runs to a page, and it is the
            // one screen here the owner may turn round and show a customer.
            if let key = router.statementFor {
                StatementScreen(partyKey: key) { router.statementFor = nil }
                    .transition(.move(edge: .trailing))
                    .zIndex(4)
            }

            if let key = router.supplierStatementFor {
                StatementScreen(partyKey: key, isSupplier: true) { router.supplierStatementFor = nil }
                    .transition(.move(edge: .trailing))
                    .zIndex(4)
            }
        }
        .animation(Metrics.quick, value: router.showingSettings)
        .animation(Metrics.quick, value: router.showingBackup)
        .animation(Metrics.quick, value: router.receipt?.number)
        .animation(Metrics.quick, value: router.partyFor)
        .animation(Metrics.quick, value: router.statementFor)
        .animation(Metrics.quick, value: router.supplierStatementFor)
        .nocturneSheet(item: $router.expenseEditor) { target in
            ExpenseSheet(editing: target.expense) { router.expenseEditor = nil }
        }
        .nocturneSheet(item: $router.productEditor) { target in
            ProductEditorSheet(product: target.product)
        }
        .nocturneSheet(item: $router.addStock) { target in
            AddStockSheet(
                product: target.product,
                startInPurchase: router.startingPurchase,
                editing: target.purchase
            )
        }
        // Which product arrived. A purchase carries one product, so something has
        // to name it, and asking here is fewer taps than making the owner find the
        // product first.
        .nocturneSheet(item: $router.purchaseDetail) { purchase in
            PurchaseSheet(purchase: purchase) { router.purchaseDetail = nil }
        }
        .nocturneSheet(item: $router.billDetail) { bill in
            BillSheet(bill: bill)
        }
        .nocturneSheet(item: $router.customerEditor) { target in
            CustomerEditorSheet(existing: target.customer) { router.customerEditor = nil }
        }
        .nocturneSheet(item: $router.paymentFor) { customer in
            RecordPaymentSheet(customer: customer, editing: router.editingPayment) {
                router.paymentFor = nil
                router.editingPayment = nil
            }
        }
        // The payment sheet's sibling: the same act with no money in it. The
        // customer is re-read so the sheet's "what will be left" line is not a
        // stale copy taken when it opened.
        .nocturneSheet(item: $router.creditNoteFor) { target in
            CreditNoteSheet(
                customer: store.customer(key: target.customer.key) ?? target.customer,
                editing: target.note
            ) { router.creditNoteFor = nil }
        }
        .nocturneSheet(item: $router.supplierEditor) { target in
            SupplierEditorSheet(existing: target.supplier) { router.supplierEditor = nil }
        }
        .nocturneSheet(item: $router.supplierPaymentFor) { supplier in
            PaySupplierSheet(supplier: supplier, editing: router.editingSupplierPayment) {
                router.supplierPaymentFor = nil
                router.editingSupplierPayment = nil
            }
        }
        .nocturneSheet(isPresented: $router.showingDebtors) {
            WhoOwesYouSheet { router.showingDebtors = false }
        }
        .nocturneSheet(isPresented: $router.showingCreditors) {
            WhoYouOweSheet { router.showingCreditors = false }
        }
        // One day of the shop, from the date at the top of Home. Presented on
        // *whether* there is a day rather than keyed on which one: stepping to
        // yesterday changes what the sheet shows, and a sheet keyed on the day
        // would dismiss and re-present itself on every arrow.
        .nocturneSheet(
            isPresented: Binding(
                get: { router.dayInView != nil },
                set: { if !$0 { router.dayInView = nil } }
            )
        ) {
            DaySheet(
                day: router.dayInView ?? .now,
                onDay: { router.dayInView = $0 },
                onClose: { router.dayInView = nil }
            )
        }
    }

    /// Hidden only under Sell's product picker, which carries its own bottom
    /// bar. The bill form keeps the tab bar: it is a form now rather than a till,
    /// and a screen with no way off it is worse than a tall one.
    private var showsTabBar: Bool {
        router.tab != .sell || !router.pickingProducts
    }
}
