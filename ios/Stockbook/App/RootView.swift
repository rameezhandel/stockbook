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
        .task {
            guard store == nil else { return }
            // A repository that cannot open its file is unrecoverable — there is
            // no server to fall back to — so this fails loudly rather than
            // running against a store the owner would type a day's bills into
            // and lose.
            let repository = try! JSONFileRepository(url: try! JSONFileRepository.defaultURL())
            store = StockbookStore(repository: repository)
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
        .environment(\.currencySymbol, store.settings.currencySymbol)
        .environment(\.locale, store.settings.language.locale)
        .tint(Nocturne.accent)
        // Every string in the app is read from `L10n` at render time, and
        // `L10n` is not observable, so nothing would redraw on its own when the
        // language changes. Keying the whole tree on the language rebuilds it
        // instead — heavy-handed, and exactly right for something that happens
        // once and must leave nothing behind in the old language.
        .id(store.settings.language)
    }
}

/// The four tabs, the tab bar, and the overlays that can sit above any of them.
private struct AppShell: View {
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart

    var body: some View {
        @Bindable var router = router

        ZStack {
            VStack(spacing: 0) {
                Group {
                    switch router.tab {
                    case .today: TodayScreen()
                    case .items: ItemsScreen()
                    case .sell: SellScreen()
                    case .bills: BillsScreen()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // The picker screen hides the tab bar behind its own sticky
                // footer once the cart has something in it; every other screen
                // shows it.
                if showsTabBar {
                    StockbookTabBar(selection: $router.tab)
                }
            }
            .ignoresSafeArea(.container, edges: .bottom)

            if router.showingSettings {
                SettingsScreen()
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }

            if let receipt = router.receipt {
                ReceiptOverlay(bill: receipt)
                    .transition(.opacity)
                    .zIndex(2)
            }
        }
        .animation(Metrics.quick, value: router.showingSettings)
        .animation(Metrics.quick, value: router.receipt?.number)
        .nocturneSheet(item: $router.productEditor) { target in
            ProductEditorSheet(product: target.product)
        }
        .nocturneSheet(item: $router.addStock) { target in
            AddStockSheet(product: target.product)
        }
    }

    private var showsTabBar: Bool {
        router.tab != .sell || cart.isEmpty
    }
}
