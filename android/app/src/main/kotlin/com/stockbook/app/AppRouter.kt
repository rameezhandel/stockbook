package com.stockbook.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Product
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

    var addStock by mutableStateOf<Product?>(null)

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
        receipt = null
        billDetail = null
        showingBackup = false
        customerEditor = null
        creatingCustomer = false
        paymentFor = null
        statementFor = null
    }
}
