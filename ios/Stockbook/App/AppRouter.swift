import Foundation
import Observation

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
@Observable
final class AppRouter {

    var tab: AppTab = .today

    /// Settings is reached from the Today gear, not from the tab bar.
    var showingSettings = false

    // MARK: Overlays

    /// The product editor sheet — `nil` closed, otherwise create or edit.
    var productEditor: ProductEditorTarget?

    /// The add-stock sheet.
    var addStock: AddStockTarget?

    /// The receipt, shown full-screen after a bill is saved.
    var receipt: Bill?

    /// A bill opened from history. Distinct from `receipt`: that one is a
    /// confirmation of something that just happened, this one is a document
    /// being looked up.
    var billDetail: Bill?

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

    func openBill(_ bill: Bill) {
        billDetail = bill
    }

    func startBill() {
        tab = .sell
    }

    func closeOverlays() {
        productEditor = nil
        addStock = nil
        receipt = nil
        billDetail = nil
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
