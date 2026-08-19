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

    /// The export/import handoff, one level in from Settings. Kept here rather
    /// than as `@State` on the settings screen so "Start over" and a database
    /// replace can close everything from one place.
    var showingBackup = false

    // MARK: Overlays

    /// The product editor sheet — `nil` closed, otherwise create or edit.
    var productEditor: ProductEditorTarget?

    /// The add-stock sheet.
    var addStock: AddStockTarget?

    /// The receipt, shown full-screen after a bill is saved.
    /// Whether Sell is showing the product picker rather than the bill form.
    ///
    /// Screen-local by rights, and here because the shell has to know: the picker
    /// carries its own bottom bar, and the tab bar underneath it would be a
    /// second one stacked on the first. Cleared when Sell goes away, so coming
    /// back lands on the form rather than wherever the last visit ended.
    var pickingProducts = false

    var receipt: Bill?

    /// A bill opened from history. Distinct from `receipt`: that one is a
    /// confirmation of something that just happened, this one is a document
    /// being looked up.
    var billDetail: Bill?

    /// The customer editor sheet — `nil` closed. Carries the customer being
    /// corrected, or `.creating` for a new one.
    var customerEditor: CustomerEditorTarget?

    /// The record-a-payment sheet, for one customer.
    var paymentFor: Customer?

    /// The payment being corrected, if one is. Held beside `paymentFor` rather
    /// than replacing it: the sheet needs the customer either way, because it
    /// shows what will still be owed once the correction is saved.
    var editingPayment: Payment?

    /// The credit-note sheet. Carries the customer it is against, and — when one
    /// is being corrected rather than written — the note itself.
    var creditNoteFor: CreditNoteTarget?

    /// A customer's statement, full screen. Held as a **key** rather than a
    /// `Customer`, because recording a payment while it is open changes every
    /// derived figure on it — the screen has to re-read the customer, not show a
    /// copy taken when it opened.
    var statementFor: String?

    /// A delivery opened from the book, the way a bill is opened from history.
    var purchaseDetail: Purchase?

    /// Set when the add-stock sheet should open on its purchase half.
    var startingPurchase = false

    /// The supplier editor sheet. The customer editor's mirror, kept as its own
    /// pair of fields rather than one editor with a direction on it: the two
    /// sheets say different words and gate on different figures.
    var supplierEditor: SupplierEditorTarget?

    /// The pay-a-supplier sheet.
    var supplierPaymentFor: Supplier?

    /// The money-out twin of `editingPayment`.
    var editingSupplierPayment: SupplierPayment?

    /// The two lists behind Today's banners: everyone who owes the shop, and
    /// everyone the shop owes. Booleans rather than carried values — the sheets
    /// read the whole list off the store, and settling one debtor from inside one
    /// must not close it.
    var showingDebtors = false
    var showingCreditors = false

    /// A supplier's statement, full screen — a key for the same reason
    /// `statementFor` is one, and a separate field so the screen knows which side
    /// of the book it is drawing without being told twice.
    var supplierStatementFor: String?

    // MARK: Intents
    //
    // Named for what the owner is doing, so call sites read as the design does.

    func openNewProduct() {
        productEditor = ProductEditorTarget(product: nil)
    }

    func openNewCustomer() {
        customerEditor = CustomerEditorTarget(customer: nil)
    }

    func openCustomer(_ customer: Customer) {
        customerEditor = CustomerEditorTarget(customer: customer)
    }

    func openStatement(for customer: Customer) {
        statementFor = customer.key
    }

    func openNewSupplier() {
        supplierEditor = SupplierEditorTarget(supplier: nil)
    }

    func openSupplier(_ supplier: Supplier) {
        supplierEditor = SupplierEditorTarget(supplier: supplier)
    }

    func openStatement(forSupplier supplier: Supplier) {
        supplierStatementFor = supplier.key
    }

    func openProduct(_ product: Product) {
        productEditor = ProductEditorTarget(product: product)
    }

    func openAddStock(for product: Product) {
        productEditor = nil
        startingPurchase = false
        addStock = AddStockTarget(product: product)
    }

    /// The Items header's Delivery button, and the Book's empty state.
    ///
    /// No product is named: one is optional on a supplier's bill, and asking for
    /// it first was the app insisting on the answer to a question the paper often
    /// does not have. The sheet offers the catalogue inside itself instead.
    func recordDelivery() {
        startingPurchase = true
        addStock = AddStockTarget(product: nil)
    }

    func openDelivery(for product: Product) {
        startingPurchase = true
        addStock = AddStockTarget(product: product)
    }

    /// Reopens a saved delivery on the sheet it was entered on.
    ///
    /// The product comes from the store rather than from the purchase's snapshot,
    /// because the sheet needs a live one to price against — and it is nil for a
    /// supplier bill that named none, or one whose product has since been deleted.
    func editDelivery(_ purchase: Purchase, product: Product?) {
        purchaseDetail = nil
        startingPurchase = true
        addStock = AddStockTarget(product: product, purchase: purchase)
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
        showingBackup = false
        customerEditor = nil
        paymentFor = nil
        editingPayment = nil
        creditNoteFor = nil
        statementFor = nil
        supplierEditor = nil
        supplierPaymentFor = nil
        editingSupplierPayment = nil
        supplierStatementFor = nil
        purchaseDetail = nil
        startingPurchase = false
        showingDebtors = false
        showingCreditors = false
    }
}

/// Identifies the supplier editor sheet. `nil` supplier means "New supplier".
struct SupplierEditorTarget: Identifiable {
    let supplier: Supplier?
    var id: String { supplier?.key ?? "new" }
}

/// Identifies the product editor sheet. `nil` product means "New product".
struct ProductEditorTarget: Identifiable {
    let product: Product?
    var id: String { product?.uid.uuidString ?? "new" }
}

/// Identifies the add-stock sheet.
///
/// `product` is nil for a supplier bill that names none — which the sheet can be
/// opened on only by correcting one, since nothing on iOS writes one yet.
/// `purchase` is the delivery being corrected, nil for a new one.
struct AddStockTarget: Identifiable {
    let product: Product?
    var purchase: Purchase? = nil
    var id: String { purchase?.id.uuidString ?? product?.uid.uuidString ?? "new" }
}

/// Identifies the credit-note sheet: who it is against, and the note being
/// corrected where there is one.
///
/// The two travel together because the sheet needs both, and a note with no
/// customer behind it is not a thing this app can draw.
struct CreditNoteTarget: Identifiable {
    let customer: Customer
    var note: CreditNote? = nil
    var id: String { note?.id.uuidString ?? "new-\(customer.key)" }
}

/// Identifies the customer editor sheet. `nil` customer means "New customer".
struct CustomerEditorTarget: Identifiable {
    let customer: Customer?
    var id: String { customer?.key ?? "new" }
}
