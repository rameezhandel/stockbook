import SwiftUI
import SwiftData

@main
struct StockbookApp: App {

    private let container = ModelStack.makeContainer()

    init() {
        NocturneType.registerBundledFonts()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                // Dark-only by design; the build settings force it too, but this
                // keeps previews and any future multi-window case honest.
                .preferredColorScheme(.dark)
        }
        .modelContainer(container)
    }
}
