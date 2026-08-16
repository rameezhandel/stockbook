import Foundation
import Combine

/// Navigation state.
///
/// The handoff describes a flat router — `setup0 | setup1 | setup2 | home |
/// items | sell | bills | settings`, plus three overlays that can sit above any
/// screen — so that is what this is, rather than a `NavigationStack` per tab.
/// There is no drill-down anywhere in the app: every detail view is a bottom
/// sheet or a full-screen overlay.
///
/// Setup is not held here. Whether it has run is *persisted* state
/// (`ShopSettings.setupCompleted`), and `RootView` branches on it.
final class AppRouter: ObservableObject {

    @Published var tab: AppTab = .today

    /// Settings is reached from the Today gear, not from the tab bar.
    @Published var showingSettings = false

    // MARK: Overlays

    /// The product editor sheet — `nil` closed, otherwise create or edit.
    @Published var productEditor: ProductEditorTarget?

    /// The add-stock sheet.
    @Published var addStock: AddStockTarget?

    /// The receipt, shown full-screen after a bill is saved.
    @Published var receipt: Bill?

    // MARK: Intents
    //
    // Named for what the owner is doing, so call sites read as the design does.

    func openNewProduct() {
        productEditor = ProductEditorTarget(product: nil)
    }

    func openProduct(_ product: Product) {
        productEditor = ProductEditorTarget(product: product)
    }

    func openAddStock(for product: Product) {
        productEditor = nil
        addStock = AddStockTarget(product: product)
    }

    func startBill() {
        tab = .sell
    }

    func closeOverlays() {
        productEditor = nil
        addStock = nil
        receipt = nil
    }
}

/// Identifies the product editor sheet. `nil` product means "New product".
struct ProductEditorTarget: Identifiable {
    let product: Product?
    var id: String { product?.uid.uuidString ?? "new" }
}

struct AddStockTarget: Identifiable {
    let product: Product
    var id: UUID { product.uid }
}
