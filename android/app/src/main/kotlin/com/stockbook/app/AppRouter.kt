package com.stockbook.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.model.Bill
import com.stockbook.core.model.CreditNote
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Expense
import com.stockbook.core.model.Product
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.Supplier
import com.stockbook.core.model.SupplierPayment
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
     * The expense sheet: the expense being corrected, or [creatingExpense] for a
     * new one. The same pair the product editor uses, for the same reason — the
     * sheet needs to know which of the two it is without a nullable meaning two
     * different things.
     */
    var expenseEditor by mutableStateOf<Expense?>(null)
    var creatingExpense by mutableStateOf(false)

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
     * The delivery being corrected, or null when the stock sheet is recording a
     * new one. A third door into that same sheet, deliberately: a correction typed
     * on a screen of its own is a screen that drifts away from the one the
     * delivery was entered on.
     */
    var editingPurchase by mutableStateOf<Purchase?>(null)
        private set

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

    /**
     * Which day the day summary is showing, or null when it is closed.
     *
     * The day itself rather than a flag, because the sheet steps between days
     * and the one it is on has to survive a recomposition. Opened on today from
     * the date at the top of Home.
     */
    var dayInView by mutableStateOf<java.time.Instant?>(null)

    /** The receipt, shown full-screen after a bill is saved. */
    var receipt by mutableStateOf<Bill?>(null)

    /**
     * A bill opened from history. Distinct from [receipt]: that one confirms
     * something that just happened, this one is a document being looked up.
     */
    var billDetail by mutableStateOf<Bill?>(null)

    /**
     * The bill being corrected, or null when Sell is writing a new one.
     *
     * Sell is the screen every bill in this shop was typed on, so it is the screen
     * a correction is typed on too — this is what tells it which of the two it is
     * doing, and which bill `updateBill` is being handed.
     */
    var editingBill by mutableStateOf<Bill?>(null)
        private set

    /**
     * Which tab Edit was tapped from, so finishing a correction goes back there.
     *
     * Without it the owner lands on a blank bill form after saving, which reads as
     * the app inviting them to write another one.
     */
    private var tabBeforeEditing = AppTab.TODAY

    /**
     * The customer editor sheet. Null closed; a customer means correct, and
     * [creatingCustomer] means create.
     */
    var customerEditor by mutableStateOf<Customer?>(null)
    var creatingCustomer by mutableStateOf(false)

    /** The record-a-payment sheet, for one customer. */
    var paymentFor by mutableStateOf<Customer?>(null)

    /**
     * The credit-note sheet, for one customer. Its sibling below carries the
     * note being corrected — nil for a new one, exactly as the bill editor does.
     */
    var creditNoteFor by mutableStateOf<Customer?>(null)
    var editingCreditNote by mutableStateOf<CreditNote?>(null)

    /**
     * The payment being corrected, if one is. Held beside [paymentFor] rather
     * than replacing it: the sheet needs the customer either way, because it
     * shows what will still be owed once the correction is saved.
     */
    var editingPayment by mutableStateOf<Payment?>(null)

    /**
     * A customer's statement, full screen. Held as a **key** rather than a
     * [Customer], because recording a payment while it is open changes every
     * derived figure on it — the screen has to re-read the customer, not show a
     * copy taken when it opened.
     */
    var statementFor by mutableStateOf<String?>(null)

    /**
     * One party's screen, full width: a customer key, or a supplier key with
     * [partyIsSupplier] set.
     *
     * A key rather than the `Customer` itself, for the same reason [statementFor]
     * is a key — taking a payment while the screen is open changes every figure on
     * it, so the screen has to re-read the person rather than show a copy taken
     * when it opened.
     *
     * One field and a flag rather than two fields, unlike the statements above:
     * the two statements really are two presentations because only one of them
     * can be open at a time *per side*, whereas a party screen is one place with a
     * direction. Both spellings work; this is the one that stops the two from
     * being open at once.
     */
    var partyFor by mutableStateOf<String?>(null)
    var partyIsSupplier by mutableStateOf(false)

    fun openCustomerScreen(key: String) {
        partyIsSupplier = false
        partyFor = key
    }

    fun openSupplierScreen(key: String) {
        partyIsSupplier = true
        partyFor = key
    }

    /**
     * The supplier editor sheet. The customer editor's mirror, kept as its own
     * pair of fields rather than one editor with a direction on it: the two
     * sheets say different words and gate on different figures.
     */
    var supplierEditor by mutableStateOf<Supplier?>(null)
    var creatingSupplier by mutableStateOf(false)

    /** The pay-a-supplier sheet. */
    var supplierPaymentFor by mutableStateOf<Supplier?>(null)

    /** The money-out twin of [editingPayment]. */
    var editingSupplierPayment by mutableStateOf<SupplierPayment?>(null)

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

    fun openNewExpense() {
        expenseEditor = null
        creatingExpense = true
    }

    fun openExpense(expense: Expense) {
        creatingExpense = false
        expenseEditor = expense
    }

    fun closeExpense() {
        expenseEditor = null
        creatingExpense = false
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

    /** Closes the stock sheet from any of the three ways it can be open. */
    fun closeAddStock() {
        addStock = null
        recordingDelivery = false
        editingPurchase = null
    }

    fun openBill(bill: Bill) {
        billDetail = bill
    }

    /**
     * Takes a bill out of its sheet and onto the form it was written on. The cart
     * is filled by the caller, which is the only thing here that knows one exists.
     */
    fun editBill(bill: Bill) {
        billDetail = null
        tabBeforeEditing = tab
        editingBill = bill
        // The picker is where the last bill left it, and a correction that opened
        // on a product list would hide the figures it came to change.
        pickingProducts = false
        tab = AppTab.SELL
    }

    /** Leaves a correction — saved or abandoned — and puts the owner back. */
    fun closeBillEditing() {
        if (editingBill == null) return
        editingBill = null
        pickingProducts = false
        tab = tabBeforeEditing
    }

    /** Opens a delivery on the sheet it was entered on, in place of its detail. */
    fun editPurchase(purchase: Purchase) {
        purchaseDetail = null
        editingPurchase = purchase
    }

    fun startBill() {
        tab = AppTab.SELL
    }

    fun closeOverlays() {
        expenseEditor = null
        creatingExpense = false
        partyFor = null
        productEditor = null
        creatingProduct = false
        addStock = null
        recordingDelivery = false
        purchaseDetail = null
        editingPurchase = null
        showingDebtors = false
        showingCreditors = false
        dayInView = null
        receipt = null
        billDetail = null
        editingBill = null
        showingBackup = false
        customerEditor = null
        creatingCustomer = false
        paymentFor = null
        editingPayment = null
        creditNoteFor = null
        editingCreditNote = null
        statementFor = null
        supplierEditor = null
        creatingSupplier = false
        supplierPaymentFor = null
        editingSupplierPayment = null
        supplierStatementFor = null
    }
}
