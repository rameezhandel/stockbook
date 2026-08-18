package com.stockbook.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Product
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.Supplier
import com.stockbook.core.text.AppTab

/**
 * Navigation state.
 *
 * The handoff describes a flat router plus overlays that can sit above any
 * screen, so that is what this is rather than a `NavHost` per tab. There is no
 * drill-down anywhere in this app: every detail view is a bottom sheet or a
 * full-screen overlay, which is also why nothing here needs a back stack.
 */
class AppRouter {
    var tab by mutableStateOf(AppTab.TODAY)

    /** Settings is reached from the Today gear, not from the tab bar. */
    var showingSettings by mutableStateOf(false)

    /** The export/import handoff, one level in from Settings. */
    var showingBackup by mutableStateOf(false)

    /** Null closed; a product means edit, `NEW_PRODUCT` means create. */
    var productEditor by mutableStateOf<Product?>(null)
    var creatingProduct by mutableStateOf(false)

    /**
     * The stock sheet, opened from a product: it can count that product's shelf
     * or file a supplier's bill against it.
     */
    var addStock by mutableStateOf<Product?>(null)

    /**
     * Set by the Items header's Delivery button: the same sheet with no product
     * named, which is a supplier bill and nothing else.
     *
     * Naming the product used to be the *first* question a delivery asked, in a
     * sheet of its own. It is optional now — a bill for a mixed load names
     * nothing and still owes money — so the question moved inside, where it can
     * be left alone.
     */
    var recordingDelivery by mutableStateOf(false)

    /** A delivery opened from the book, the way a bill is opened from history. */
    var purchaseDetail by mutableStateOf<Purchase?>(null)

    /**
     * The two Today banners, opened into a list of everybody behind them.
     *
     * A banner saying "3 customers still owe" is a fact the owner can do nothing
     * with; these turn it into the names, and each name into the payment sheet.
     */
    /**
     * Whether Sell is showing the product picker rather than the bill form.
     *
     * Screen-local by rights, and here because the shell has to know: the picker
     * carries its own bottom bar, and the tab bar underneath it would be a second
     * one stacked on the first. Reset when Sell leaves the screen, so coming back
     * lands on the form rather than wherever the last visit ended.
     */
    var pickingProducts by mutableStateOf(false)

    var showingDebtors by mutableStateOf(false)
    var showingCreditors by mutableStateOf(false)

    /** The receipt, shown full-screen after a bill is saved. */
    var receipt by mutableStateOf<Bill?>(null)

    /**
     * A bill opened from history. Distinct from [receipt]: that one confirms
     * something that just happened, this one is a document being looked up.
     */
    var billDetail by mutableStateOf<Bill?>(null)

    /**
     * The customer editor sheet. Null closed; a customer means correct, and
     * [creatingCustomer] means create.
     */
    var customerEditor by mutableStateOf<Customer?>(null)
    var creatingCustomer by mutableStateOf(false)

    /** The record-a-payment sheet, for one customer. */
    var paymentFor by mutableStateOf<Customer?>(null)

    /**
     * A customer's statement, full screen. Held as a **key** rather than a
     * [Customer], because recording a payment while it is open changes every
     * derived figure on it — the screen has to re-read the customer, not show a
     * copy taken when it opened.
     */
    var statementFor by mutableStateOf<String?>(null)

    /**
     * The supplier editor sheet. The customer editor's mirror, kept as its own
     * pair of fields rather than one editor with a direction on it: the two
     * sheets say different words and gate on different figures.
     */
    var supplierEditor by mutableStateOf<Supplier?>(null)
    var creatingSupplier by mutableStateOf(false)

    /** The pay-a-supplier sheet. */
    var supplierPaymentFor by mutableStateOf<Supplier?>(null)

    /**
     * A supplier's statement, full screen — a key for the same reason
     * [statementFor] is one, and a separate field so the screen knows which side
     * of the book it is drawing without being told twice.
     */
    var supplierStatementFor by mutableStateOf<String?>(null)

    fun openNewSupplier() {
        supplierEditor = null
        creatingSupplier = true
    }

    fun openSupplier(supplier: Supplier) {
        creatingSupplier = false
        supplierEditor = supplier
    }

    fun openSupplierStatement(supplier: Supplier) {
        supplierStatementFor = supplier.key
    }

    fun closeSupplierEditor() {
        supplierEditor = null
        creatingSupplier = false
    }

    fun openNewCustomer() {
        customerEditor = null
        creatingCustomer = true
    }

    fun openCustomer(customer: Customer) {
        creatingCustomer = false
        customerEditor = customer
    }

    fun openStatement(customer: Customer) {
        statementFor = customer.key
    }

    fun closeCustomerEditor() {
        customerEditor = null
        creatingCustomer = false
    }

    fun openNewProduct() {
        productEditor = null
        creatingProduct = true
    }

    fun openProduct(product: Product) {
        creatingProduct = false
        productEditor = product
    }

    fun openAddStock(product: Product) {
        productEditor = null
        creatingProduct = false
        addStock = product
    }

    /** Closes the stock sheet from either of the two ways it can be open. */
    fun closeAddStock() {
        addStock = null
        recordingDelivery = false
    }

    fun openBill(bill: Bill) {
        billDetail = bill
    }

    fun startBill() {
        tab = AppTab.SELL
    }

    fun closeOverlays() {
        productEditor = null
        creatingProduct = false
        addStock = null
        recordingDelivery = false
        purchaseDetail = null
        showingDebtors = false
        showingCreditors = false
        receipt = null
        billDetail = null
        showingBackup = false
        customerEditor = null
        creatingCustomer = false
        paymentFor = null
        statementFor = null
        supplierEditor = null
        creatingSupplier = false
        supplierPaymentFor = null
        supplierStatementFor = null
    }
}
