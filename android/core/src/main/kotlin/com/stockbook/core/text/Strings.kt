package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.transfer.BackupError

/**
 * Every user-facing string in Stockbook, in both languages, side by side.
 *
 * There is no resource file and no key lookup. A missing translation is a
 * compile error rather than an English word appearing on a Kannada screen, and a
 * reviewer can read both languages of a sentence on one line without holding a
 * key in their head.
 *
 * Rules for anything added here:
 * - **No sentence built by joining fragments.** Word order differs between the
 *   two languages, so every phrase with a number or a name in it is one function
 *   taking that number or name.
 * - **Counts are written out, not pluralised by rule.** Kannada does not add an
 *   "s", and a shared pluraliser only ever produces one language correctly.
 *
 * Kept deliberately identical to the iOS build's table, so the two apps say the
 * same things and a correction lands in both.
 */
class Strings(val language: AppLanguage) {

    private fun pick(english: String, kannada: String): String = when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.KANNADA -> kannada
    }


    // --- Counted nouns

    fun products(n: Int): String =
            pick(if (n == 1) "1 product" else "$n products", "$n ಸಾಮಾನು")

    fun bills(n: Int): String =
            pick(if (n == 1) "1 bill" else "$n bills", "$n ಬಿಲ್")

    fun lines(n: Int): String =
            pick(if (n == 1) "1 line" else "$n lines", "$n ಸಾಲು")

    fun items(n: Int): String =
            pick(if (n == 1) "1 item" else "$n items", "$n ಸಾಮಾನು")

    fun purchases(n: Int): String =
            pick(if (n == 1) "1 purchase" else "$n purchases", "$n ಖರೀದಿ")

    fun pieces(n: Int): String =
            pick(if (n == 1) "1 piece" else "$n pieces", "$n ನಗ")

    fun customers(n: Int): String =
            pick(if (n == 1) "1 customer" else "$n customers", "$n ಗ್ರಾಹಕರು")

    // --- Shared actions

    val done: String get() = pick("Done", "ಆಯಿತು")
    val save: String get() = pick("Save", "ಉಳಿಸಿ")
    val cancel: String get() = pick("Cancel", "ರದ್ದು")
    val add: String get() = pick("Add", "ಸೇರಿಸಿ")
    val all: String get() = pick("All", "ಎಲ್ಲಾ")
    val share: String get() = pick("Share", "ಹಂಚಿಕೊಳ್ಳಿ")
    val back: String get() = pick("Back", "ಹಿಂದೆ")
    val continueAction: String get() = pick("Continue", "ಮುಂದೆ")
    // The keyboard toolbar, when there is another box after this one.
    val next: String get() = pick("Next", "ಮುಂದಿನದು")
    val startABill: String get() = pick("Start a bill", "ಬಿಲ್ ಶುರು ಮಾಡಿ")
    val addAProduct: String get() = pick("Add a product", "ಸಾಮಾನು ಸೇರಿಸಿ")

    fun remove(name: String): String =
            pick("Remove $name", "$name ತೆಗೆದುಹಾಕಿ")

    // --- Tabs

    fun tab(tab: AppTab): String = when (tab) {
        AppTab.TODAY -> pick("Home", "ಮುಖಪುಟ")
        AppTab.ITEMS -> pick("Items", "ಸಾಮಾನು")
        AppTab.SELL -> pick("Sales", "ಮಾರಾಟಗಳು")
        AppTab.BOOK -> pick("Reports", "ವರದಿಗಳು")
    }

    // --- Today

    val today: String get() = pick("Today", "ಇಂದು")
    val settings: String get() = pick("Settings", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು")
    val receivableStat: String get() = pick("To collect", "ಬರಬೇಕಾದ ಬಾಕಿ")
    val payableStat: String get() = pick("To pay", "ಕೊಡಬೇಕಾದ ಬಾಕಿ")
    val billsStat: String get() = pick("Bills", "ಬಿಲ್‌ಗಳು")
    val recentBills: String get() = pick("Recent bills", "ಇತ್ತೀಚಿನ ಬಿಲ್‌ಗಳು")
    val noBillsToday: String get() = pick("No bills yet today.", "ಇಂದು ಇನ್ನೂ ಒಂದೂ ಬಿಲ್ ಆಗಿಲ್ಲ.")

    fun greeting(firstName: String): String =
            pick("Hello, $firstName", "ನಮಸ್ಕಾರ, $firstName")

    // One name reads as a name; several read as a count of **people**.
    fun stillOwes(name: String): String =
            pick("$name still owes", "$name ಇನ್ನೂ ಬಾಕಿ ಕೊಡಬೇಕು")

    fun stillOwe(n: Int): String =
            pick("${customers(n)} still owe", "$n ಗ್ರಾಹಕರು ಇನ್ನೂ ಬಾಕಿ ಕೊಡಬೇಕು")

    // --- Items

    val itemsTitle: String get() = pick("Items", "ಸಾಮಾನುಗಳು")
    val search: String get() = pick("Search", "ಹುಡುಕಿ")
    val nothingAddedYet: String get() = pick("nothing added yet", "ಇನ್ನೂ ಏನೂ ಸೇರಿಸಿಲ್ಲ")

    fun itemsSubtitle(total: Int, low: Int): String =
            pick("${products(total)} · $low running low", "$total ಸಾಮಾನು · $low ಕಡಿಮೆ ಆಗುತ್ತಿದೆ")

    val shelfEmpty: String get() =
            pick(
                "Nothing on the shelf yet. Add your first product.",
                "ಇನ್ನೂ ಯಾವ ಸಾಮಾನೂ ಇಲ್ಲ. ಮೊದಲ ಸಾಮಾನು ಸೇರಿಸಿ."
            )

    fun nothingMatches(query: String): String =
            pick("Nothing matches “$query”.", "“$query” ಗೆ ಏನೂ ಸಿಗಲಿಲ್ಲ.")

    fun buyAndMargin(cost: String, margin: String): String =
            pick("buy $cost · you make $margin", "ಖರೀದಿ $cost · ಲಾಭ $margin")

    // `out of stock` / `12 pc` — the shelf count wherever it is shown.
    fun stockLabel(stock: Int): String =
        if (stock == 0) pick("out of stock", "ಸ್ಟಾಕ್ ಇಲ್ಲ")
        else pick("$stock pc", "$stock ನಗ")

    // --- Bills

    val billsTitle: String get() = pick("Bills", "ಬಿಲ್‌ಗಳು")

    // --- The book: both halves of the account, side by side

    val bookTitle: String get() = pick("Reports", "ವರದಿಗಳು")
    val salesSide: String get() = pick("Sales", "ಮಾರಾಟ")
    val purchasesSide: String get() = pick("Purchases", "ಖರೀದಿ")

    // On the Items header. Says "delivery" rather than "purchase" because that is
    // the word for the thing arriving at the door.
    val recordDelivery: String get() = pick("Delivery", "ಡೆಲಿವರಿ")
    val whichProductArrived: String get() = pick("What arrived?", "ಏನು ಬಂತು?")
    val noDeliveriesYet: String get() = pick("No deliveries yet", "ಇನ್ನೂ ಡೆಲಿವರಿ ಇಲ್ಲ")
    val deliveryDetail: String get() = pick("Delivery", "ಡೆಲಿವರಿ")

    // `12 × SAR 60` — what a delivery row says under the product's name.
    fun perPiece(qty: Int, cost: String): String = "$qty × $cost"

    val noBillsEver: String get() =
            pick(
                "Nothing sold yet. Every bill you save shows up here.",
                "ಇನ್ನೂ ಏನೂ ಮಾರಾಟ ಆಗಿಲ್ಲ. ನೀವು ಉಳಿಸಿದ ಪ್ರತಿ ಬಿಲ್ ಇಲ್ಲಿ ಕಾಣಿಸುತ್ತದೆ."
            )

    // A mistake is corrected on the document itself: opened, changed, or taken
    // out. Removing says what it does to the shelf, because that is the part
    // that surprises people.
    val editBill: String get() = pick("Edit", "ಬದಲಾಯಿಸಿ")
    val saveChanges: String get() = pick("Save changes", "ಬದಲಾವಣೆ ಉಳಿಸಿ")
    val removeBill: String get() = pick("Remove this bill", "ಈ ಬಿಲ್ ತೆಗೆದುಹಾಕಿ")

    val removeBillNote: String
        get() = pick(
            "Gone for good, and anything on it goes back on the shelf.",
            "ಶಾಶ್ವತವಾಗಿ ಹೋಗುತ್ತದೆ, ಮತ್ತು ಅದರಲ್ಲಿನ ಸಾಮಾನು ಶೆಲ್ಫಿಗೆ ವಾಪಸ್."
        )

    fun owes(amount: String): String =
            pick("owes $amount", "$amount ಬಾಕಿ")

    val allCustomers: String get() = pick("All customers", "ಎಲ್ಲಾ ಗ್ರಾಹಕರು")
    val customerLabel: String get() = pick("Customer", "ಗ್ರಾಹಕ")
    val transactions: String get() = pick("Bought", "ಖರೀದಿಸಿದ್ದು")
    val pendingPayment: String get() = pick("Pending", "ಬಾಕಿ")
    val nothingPending: String get() = pick("Nothing", "ಏನೂ ಇಲ್ಲ")

    // Somebody who has paid ahead. The mirror of `owes`, and worth saying
    // plainly rather than showing a negative amount the owner has to interpret.
    fun inAdvance(amount: String): String =
            pick("$amount in advance", "$amount ಮುಂಗಡ")

    // --- Customers

    val customersTitle: String get() = pick("Customers", "ಗ್ರಾಹಕರು")
    val addACustomer: String get() = pick("Add a customer", "ಗ್ರಾಹಕರನ್ನು ಸೇರಿಸಿ")
    val newCustomer: String get() = pick("New customer", "ಹೊಸ ಗ್ರಾಹಕ")
    val editCustomer: String get() = pick("Edit customer", "ಗ್ರಾಹಕರ ವಿವರ ಬದಲಿಸಿ")
    val customerPhone: String get() = pick("Phone", "ಫೋನ್")
    val customerPlace: String get() = pick("Place", "ಸ್ಥಳ")
    val optionalField: String get() = pick("Optional", "ಬೇಕಿದ್ದರೆ")
    val saveCustomer: String get() = pick("Save customer", "ಗ್ರಾಹಕರನ್ನು ಉಳಿಸಿ")
    val removeFromCustomers: String get() = pick("Remove from customers", "ಗ್ರಾಹಕರ ಪಟ್ಟಿಯಿಂದ ತೆಗೆಯಿರಿ")
    val tapAgainToRemove: String get() = pick("Tap again to remove", "ತೆಗೆಯಲು ಇನ್ನೊಮ್ಮೆ ಒತ್ತಿ")
    val noBillsYet: String get() = pick("No bills yet", "ಇನ್ನೂ ಬಿಲ್ ಇಲ್ಲ")
    val openingBalanceField: String get() = pick("Opening balance", "ಪ್ರಾರಂಭಿಕ ಬಾಕಿ")

    // The save gate when a name has been typed but nobody picked. Says what to do,
    // not what went wrong.
    val chooseFromTheList: String get() = pick("Choose a customer from the list", "ಪಟ್ಟಿಯಿಂದ ಗ್ರಾಹಕರನ್ನು ಆರಿಸಿ")

    // The way a customer who is not on the roster yet gets onto it, without
    // leaving the bill.
    fun addAsCustomer(name: String): String =
            pick("Add “$name” as a customer", "“$name” ಅನ್ನು ಗ್ರಾಹಕರಾಗಿ ಸೇರಿಸಿ")

    // --- Suppliers
    //
    // The customer block above, pointing the other way. Every line here has a
    // counterpart there, and the words differ only where the direction of the
    // money does: a supplier is somebody *the shop* owes.

    val suppliersTitle: String get() = pick("Suppliers", "ಪೂರೈಕೆದಾರರು")
    val allSuppliers: String get() = pick("All suppliers", "ಎಲ್ಲಾ ಪೂರೈಕೆದಾರರು")
    val addASupplier: String get() = pick("Add a supplier", "ಪೂರೈಕೆದಾರರನ್ನು ಸೇರಿಸಿ")
    val newSupplier: String get() = pick("New supplier", "ಹೊಸ ಪೂರೈಕೆದಾರ")
    val editSupplier: String get() = pick("Edit supplier", "ಪೂರೈಕೆದಾರರ ವಿವರ ಬದಲಿಸಿ")
    val saveSupplier: String get() = pick("Save supplier", "ಪೂರೈಕೆದಾರರನ್ನು ಉಳಿಸಿ")
    val removeFromSuppliers: String get() = pick("Remove from suppliers", "ಪೂರೈಕೆದಾರರ ಪಟ್ಟಿಯಿಂದ ತೆಗೆಯಿರಿ")

    // Says what removal does *not* do, because "remove" beside a name reads like
    // deleting the deliveries too — and it does not.
    val removeSupplierNote: String
        get() = pick(
            "Their purchases stay. Only the saved details go.",
            "ಅವರ ಖರೀದಿಗಳು ಉಳಿಯುತ್ತವೆ. ಉಳಿಸಿದ ವಿವರಗಳಷ್ಟೇ ಹೋಗುತ್ತವೆ."
        )
    val supplierNameExample: String get() = pick("Al Faisal Hardware", "ಅಲ್ ಫೈಸಲ್ ಹಾರ್ಡ್‌ವೇರ್")
    val noPurchasesYet: String get() = pick("No purchases yet", "ಇನ್ನೂ ಖರೀದಿ ಇಲ್ಲ")
    val purchaseLabel: String get() = pick("Purchase", "ಖರೀದಿ")
    val paidOn: String get() = pick("Paid on", "ಪಾವತಿಸಿದ ದಿನ")
    val paymentNotAgainstOnePurchase: String
        get() = pick(
            "Paid against the account, not one delivery.",
            "ಒಂದು ಡೆಲಿವರಿಗೆ ಅಲ್ಲ, ಖಾತೆಗೆ ಪಾವತಿ."
        )
    val boughtFromThem: String get() = pick("Bought", "ಖರೀದಿಸಿದ್ದು")

    // What the shop owes, as opposed to what it is owed. Deliberately not the
    // same word as a customer's "Pending", because a shop owner reading a screen
    // fast needs the two totals to be tellable apart at a glance.
    val youOwe: String get() = pick("You owe", "ನೀವು ಕೊಡಬೇಕು")
    val owedToSuppliers: String get() = pick("Owed to suppliers", "ಪೂರೈಕೆದಾರರಿಗೆ ಬಾಕಿ")

    // The Today banner, pointing outwards. Named like `stillOwes` beside it, so
    // the two lines read as a pair rather than as two unrelated notices.
    fun youOweOne(name: String): String = pick("You owe $name", "$name ಅವರಿಗೆ ನೀವು ಕೊಡಬೇಕು")

    fun youOweMany(n: Int): String =
            pick("You owe $n suppliers", "$n ಪೂರೈಕೆದಾರರಿಗೆ ನೀವು ಕೊಡಬೇಕು")
    val nothingOwedOut: String get() = pick("Nothing owed out", "ಕೊಡಬೇಕಾದ್ದು ಏನೂ ಇಲ್ಲ")

    val chooseSupplierFromTheList: String
        get() = pick("Choose a supplier from the list", "ಪಟ್ಟಿಯಿಂದ ಪೂರೈಕೆದಾರರನ್ನು ಆರಿಸಿ")

    fun addAsSupplier(name: String): String =
            pick("Add “$name” as a supplier", "“$name” ಅನ್ನು ಪೂರೈಕೆದಾರರಾಗಿ ಸೇರಿಸಿ")

    // The same two actions on the other side of the book, where removing takes
    // stock back off the shelf rather than putting it on.
    val removeSupplierBill: String get() = pick("Remove this bill", "ಈ ಬಿಲ್ ತೆಗೆದುಹಾಕಿ")

    val removeSupplierBillNote: String
        get() = pick(
            "Gone for good, and anything on it comes back off the shelf.",
            "ಶಾಶ್ವತವಾಗಿ ಹೋಗುತ್ತದೆ, ಮತ್ತು ಅದರಲ್ಲಿನ ಸಾಮಾನು ಶೆಲ್ಫಿನಿಂದ ಹಿಂತೆಗೆಯಲಾಗುತ್ತದೆ."
        )

    // Says which balance this is, because the statement has one of its own with a
    // different meaning — that one is derived, this one is typed in once.
    val openingBalanceNote: String
        get() = pick(
            "What they already owed you before Stockbook, from the old book. Leave it empty if nothing.",
            "ಸ್ಟಾಕ್‌ಬುಕ್ ಶುರು ಮಾಡುವ ಮೊದಲು ಹಳೆಯ ಪುಸ್ತಕದಲ್ಲಿ ಅವರು ಕೊಡಬೇಕಿದ್ದ ಮೊತ್ತ. ಏನೂ ಇಲ್ಲದಿದ್ದರೆ ಖಾಲಿ ಬಿಡಿ."
        )
    val enterCustomerNameFirst: String get() = pick("Enter a name", "ಹೆಸರು ಬರೆಯಿರಿ")

    // Said when removing a roster entry, because "remove" beside somebody's
    // name reads like deleting them and their history.
    val removeCustomerNote: String
        get() = pick(
            "Their bills stay. This only takes them off the customer list.",
            "ಅವರ ಬಿಲ್‌ಗಳು ಹಾಗೇ ಉಳಿಯುತ್ತವೆ. ಇದು ಅವರನ್ನು ಗ್ರಾಹಕರ ಪಟ್ಟಿಯಿಂದ ಮಾತ್ರ ತೆಗೆಯುತ್ತದೆ."
        )

    // --- Payments

    val recordAPayment: String get() = pick("Record a payment", "ಪಾವತಿ ದಾಖಲಿಸಿ")
    val amountReceived: String get() = pick("Amount received", "ಸ್ವೀಕರಿಸಿದ ಮೊತ್ತ")
    val receivedOn: String get() = pick("Received on", "ಸ್ವೀಕರಿಸಿದ ದಿನ")
    val paymentNote: String get() = pick("Note", "ಟಿಪ್ಪಣಿ")
    val paymentNoteExample: String get() = pick("cash, cheque…", "ನಗದು, ಚೆಕ್…")
    val savePayment: String get() = pick("Save payment", "ಪಾವತಿ ಉಳಿಸಿ")
    val paymentLabel: String get() = pick("Payment", "ಪಾವತಿ")
    val deleteThisPayment: String get() = pick("Delete this payment", "ಈ ಪಾವತಿಯನ್ನು ಅಳಿಸಿ")
    val enterAnAmount: String get() = pick("Enter an amount", "ಮೊತ್ತ ಬರೆಯಿರಿ")

    // The one thing to know about payments in this app.
    val paymentNotAgainstOneBill: String
        get() = pick(
            "Recorded against the customer, not one bill — the way money actually arrives at a counter.",
            "ಒಂದು ಬಿಲ್‌ಗೆ ಅಲ್ಲ, ಗ್ರಾಹಕರ ಖಾತೆಗೆ ದಾಖಲಾಗುತ್ತದೆ — ಕೌಂಟರಿನಲ್ಲಿ ಹಣ ಬರುವುದು ಹಾಗೆಯೇ."
        )

    // --- Statement

    val statement: String get() = pick("Statement", "ಖಾತೆ ವಿವರ")
    val thisMonth: String get() = pick("This month", "ಈ ತಿಂಗಳು")
    val lastMonth: String get() = pick("Last month", "ಕಳೆದ ತಿಂಗಳು")
    val thisYear: String get() = pick("This year", "ಈ ವರ್ಷ")
    val chooseDates: String get() = pick("Choose dates", "ದಿನಾಂಕ ಆರಿಸಿ")
    val fromDate: String get() = pick("From", "ಇಂದ")
    val toDate: String get() = pick("To", "ವರೆಗೆ")
    val openingBalance: String get() = pick("Brought forward", "ಹಿಂದಿನ ಬಾಕಿ")
    val billedInPeriod: String get() = pick("Billed", "ಬಿಲ್ ಮಾಡಿದ್ದು")
    val receivedInPeriod: String get() = pick("Received", "ಸ್ವೀಕರಿಸಿದ್ದು")

    // The same two figures on a supplier's statement. "Billed" and "Received" are
    // the customer's words and read backwards on a delivery note.
    val purchasedInPeriod: String get() = pick("Purchased", "ಖರೀದಿಸಿದ್ದು")
    val paidOutInPeriod: String get() = pick("Paid", "ಪಾವತಿಸಿದ್ದು")
    val closingBalance: String get() = pick("Balance due", "ಉಳಿದ ಬಾಕಿ")
    val nothingInThisPeriod: String get() = pick("Nothing in this period", "ಈ ಅವಧಿಯಲ್ಲಿ ಏನೂ ಇಲ್ಲ")
    val settledUp: String get() = pick("Settled up", "ಎಲ್ಲಾ ಪಾವತಿ ಆಗಿದೆ")

    // `28 July 2026 to 27 August 2026` — the span a statement covers, written
    // out because a date range abbreviated with a dash is read wrong often
    // enough to matter on a document somebody may hand to a customer.
    fun dateSpan(from: String, to: String): String =
            pick("$from to $to", "$from ಇಂದ $to ವರೆಗೆ")


    // --- Sell

    val newBill: String get() = pick("New bill", "ಹೊಸ ಬಿಲ್")
    val cartEmpty: String get() = pick("empty", "ಖಾಲಿ")
    val addAProductPlaceholder: String get() = pick("Add a product…", "ಸಾಮಾನು ಸೇರಿಸಿ…")
    val doneAdding: String get() = pick("Done adding", "ಸೇರಿಸಿ ಆಯಿತು")
    val addAnotherItem: String get() = pick("Add another item", "ಇನ್ನೊಂದು ಸಾಮಾನು ಸೇರಿಸಿ")
    val oneFewer: String get() = pick("One fewer", "ಒಂದು ಕಡಿಮೆ")
    val oneMore: String get() = pick("One more", "ಒಂದು ಹೆಚ್ಚು")

    fun matchingQuery(query: String): String =
            pick("Matching “$query”", "“$query” ಗೆ ಹೊಂದುವುದು")

    fun allProductsHint(n: Int): String =
            pick("All $n products — tap to add", "ಎಲ್ಲಾ $n ಸಾಮಾನು — ಸೇರಿಸಲು ಒತ್ತಿ")

    fun noProductMatches(query: String): String =
            pick("No product matches “$query”.", "“$query” ಗೆ ಯಾವ ಸಾಮಾನೂ ಸಿಗಲಿಲ್ಲ.")

    val noProductsYet: String get() =
            pick("You haven't added any products yet.", "ನೀವು ಇನ್ನೂ ಯಾವ ಸಾಮಾನೂ ಸೇರಿಸಿಲ್ಲ.")

    fun onBillAccessibility(name: String, quantity: Int): String =
            pick("$name, $quantity on the bill", "$name, ಬಿಲ್‌ನಲ್ಲಿ $quantity")

    // --- Cart

    fun onlyInStock(stock: Int): String =
            pick("only $stock in stock", "ಸ್ಟಾಕ್‌ನಲ್ಲಿ $stock ಮಾತ್ರ")

    fun piecesInStock(stock: Int): String =
            pick("pieces · $stock in stock", "ನಗ · ಸ್ಟಾಕ್‌ನಲ್ಲಿ $stock")

    fun usualPriceNote(price: String): String =
            pick(
                "Usual price $price — changed for this bill only",
                "ಎಂದಿನ ಬೆಲೆ $price — ಈ ಬಿಲ್‌ಗೆ ಮಾತ್ರ ಬದಲಾಗಿದೆ"
            )

    val reset: String get() = pick("Reset", "ಮೊದಲಿನಂತೆ")
    val customerName: String get() = pick("Customer name", "ಗ್ರಾಹಕರ ಹೆಸರು")
    val paidInFull: String get() = pick("Paid in full", "ಪೂರ್ತಿ ಪಾವತಿ")
    val partPayment: String get() = pick("Part payment", "ಭಾಗಶಃ ಪಾವತಿ")
    val paidNow: String get() = pick("Paid now", "ಈಗ ಕೊಟ್ಟದ್ದು")
    val total: String get() = pick("Total", "ಒಟ್ಟು")
    val balance: String get() = pick("Balance", "ಬಾಕಿ")
    val saveBill: String get() = pick("Save bill", "ಬಿಲ್ ಉಳಿಸಿ")
    val enterCustomerName: String get() = pick("Enter a customer name", "ಗ್ರಾಹಕರ ಹೆಸರು ಬರೆಯಿರಿ")

    // --- Receipt

    val billSaved: String get() = pick("Bill saved", "ಬಿಲ್ ಉಳಿಸಲಾಗಿದೆ")
    val billDetailTitle: String get() = pick("Bill details", "ಬಿಲ್ ವಿವರ")
    val seeBills: String get() = pick("See bills", "ಬಿಲ್‌ಗಳನ್ನು ನೋಡಿ")
    val nextCustomer: String get() = pick("Next customer", "ಮುಂದಿನ ಗ್ರಾಹಕರು")

    // --- The paper behind the record

    // Said the same way on both sides of the book: the number written on the
    // piece of paper, whoever wrote it.
    val invoiceNoField: String get() = pick("Invoice no.", "ಬಿಲ್ ಸಂಖ್ಯೆ")
    val invoiceNoHint: String get() = pick("From the book", "ಪುಸ್ತಕದಿಂದ")
    val billDate: String get() = pick("Date", "ದಿನಾಂಕ")

    // Two numbers the same is two records the shop cannot tell apart later, so
    // the clash is named — whose it is and when — rather than merely reported.
    fun invoiceNoAlreadyUsed(who: String, date: String): String =
        pick("Already used — $who, $date", "ಈಗಾಗಲೇ ಬಳಸಲಾಗಿದೆ — $who, $date")

    val changeTheInvoiceNo: String get() = pick("Change the number", "ಸಂಖ್ಯೆ ಬದಲಿಸಿ")

    // Both sides of the book refuse to save without a number: a record with none
    // cannot be matched to the paper it came from, which is the whole reason for
    // keeping the number at all.
    val enterBillNumber: String get() = pick("Enter the bill number", "ಬಿಲ್ ಸಂಖ್ಯೆ ನಮೂದಿಸಿ")

    // --- Entering a bill

    // The figure the bill came to, typed rather than computed. The ordinary case:
    // the paper bill was written first and already says what it came to.
    val amountField: String get() = pick("Amount", "ಮೊತ್ತ")
    val addItems: String get() = pick("Add items", "ಸಾಮಾನುಗಳನ್ನು ಸೇರಿಸಿ")
    val removeItems: String get() = pick("Remove items", "ಸಾಮಾನುಗಳನ್ನು ತೆಗೆಯಿರಿ")

    // Shown under the total once the bill has been itemised, because the figure
    // stops being typed at that moment and starts being a sum.
    fun fromItems(n: Int): String = pick("from ${items(n)}", "$n ಸಾಮಾನುಗಳಿಂದ")

    val supplierBillTitle: String get() = pick("Supplier bill", "ಸರಬರಾಜುದಾರರ ಬಿಲ್")

    // --- The shelf, corrected by hand

    val setCount: String get() = pick("Set count", "ಎಣಿಕೆ ನಮೂದಿಸಿ")

    val setCountNote: String
        get() = pick(
            "What you counted on the shelf — not how many to add.",
            "ಶೆಲ್ಫಿನಲ್ಲಿ ನೀವು ಎಣಿಸಿದ ಸಂಖ್ಯೆ — ಸೇರಿಸಬೇಕಾದ ಸಂಖ್ಯೆ ಅಲ್ಲ."
        )

    // --- Collecting, from wherever the debt was noticed

    val whoOwesYou: String get() = pick("Who owes you", "ನಿಮಗೆ ಬಾಕಿ ಇರುವವರು")
    val whoYouOwe: String get() = pick("Who you owe", "ನೀವು ಕೊಡಬೇಕಾದವರು")
    val takePayment: String get() = pick("Take payment", "ಪಾವತಿ ಪಡೆಯಿರಿ")


    fun billNumber(number: Int): String =
            pick("Bill #$number", "ಬಿಲ್ #$number")

    fun billWhen(date: String, time: String): String =
            pick("$date · $time", "$date · $time")

    fun billedTo(name: String): String =
            pick("Billed to $name", "$name ಅವರಿಗೆ")

    // `2 × SAR 95` — the arithmetic behind a line, kept visible.
    fun quantityAtPrice(quantity: Int, price: String): String =
            pick("$quantity × $price", "$quantity × $price")

    val paidInFullCash: String get() = pick("Paid in full, cash.", "ಪೂರ್ತಿ ಪಾವತಿ, ನಗದು.")

    fun partPaidNote(paid: String, who: String, balance: String): String =
            pick(
                "Paid $paid · $who owes $balance",
                "$paid ಕೊಟ್ಟಿದ್ದಾರೆ · $who ಅವರಿಂದ $balance ಬಾಕಿ"
            )

    // --- Product editor

    val newProduct: String get() = pick("New product", "ಹೊಸ ಸಾಮಾನು")
    val editProduct: String get() = pick("Edit product", "ಸಾಮಾನು ಬದಲಿಸಿ")
    val productName: String get() = pick("Product name", "ಸಾಮಾನಿನ ಹೆಸರು")
    val productNameExample: String get() = pick("e.g. 4 inch hinge", "ಉದಾ. 4 ಇಂಚಿನ ಹಿಂಜ್")
    val inStock: String get() = pick("In stock", "ದಾಸ್ತಾನು")
    val buyingPrice: String get() = pick("Buying price", "ಖರೀದಿ ಬೆಲೆ")
    val sellingPrice: String get() = pick("Selling price", "ಮಾರಾಟ ಬೆಲೆ")
    val addStock: String get() = pick("Add stock", "ದಾಸ್ತಾನು ಸೇರಿಸಿ")
    val removeThisProduct: String get() = pick("Remove this product", "ಈ ಸಾಮಾನನ್ನು ತೆಗೆದುಹಾಕಿ")

    val setPriceAboveCost: String get() =
            pick(
                "Set a selling price above the buying price.",
                "ಖರೀದಿ ಬೆಲೆಗಿಂತ ಹೆಚ್ಚಿನ ಮಾರಾಟ ಬೆಲೆ ಹಾಕಿ."
            )

    fun youMakeAPiece(margin: String): String =
            pick("You make $margin a piece.", "ಪ್ರತಿ ನಗಕ್ಕೆ $margin ಲಾಭ.")

    // --- Add stock

    fun onShelfNow(product: String, stock: Int): String =
            pick(
                "$product — ${pieces(stock)} on the shelf now",
                "$product — ಈಗ ಅಂಗಡಿಯಲ್ಲಿ $stock ನಗ"
            )

    val supplier: String get() = pick("Supplier", "ಸರಬರಾಜುದಾರ")
    val whoDeliveredIt: String get() = pick("Who delivered it", "ಯಾರು ತಂದುಕೊಟ್ಟರು")
    val howMany: String get() = pick("How many", "ಎಷ್ಟು")
    val paidPerPiece: String get() = pick("Paid per piece", "ಪ್ರತಿ ನಗಕ್ಕೆ ಕೊಟ್ಟದ್ದು")
    val recordPurchase: String get() = pick("Record purchase", "ಖರೀದಿ ದಾಖಲಿಸಿ")

    fun purchaseNote(billTotal: String): String =
            pick(
                "Bill total $billTotal. This becomes the buying price used from now on.",
                "ಬಿಲ್ ಒಟ್ಟು $billTotal. ಇನ್ನು ಮುಂದೆ ಇದೇ ಖರೀದಿ ಬೆಲೆ ಆಗುತ್ತದೆ."
            )


    // --- Setup

    val welcomeToStockbook: String get() = pick("Welcome to Stockbook", "ಸ್ಟಾಕ್‌ಬುಕ್‌ಗೆ ಸ್ವಾಗತ")

    val welcomeBody: String get() =
            pick(
                "Everything stays on this phone — no account, no signal needed. First, what should we call you?",
                "ಎಲ್ಲವೂ ಈ ಫೋನಿನಲ್ಲಿಯೇ ಇರುತ್ತದೆ — ಖಾತೆ ಬೇಡ, ನೆಟ್‌ವರ್ಕ್ ಬೇಡ. ಮೊದಲು, ನಿಮ್ಮನ್ನು ಏನೆಂದು ಕರೆಯೋಣ?"
            )

    val yourName: String get() = pick("Your name", "ನಿಮ್ಮ ಹೆಸರು")
    val businessOwnerName: String get() = pick("Business owner name", "ಅಂಗಡಿ ಮಾಲೀಕರ ಹೆಸರು")
    val yourShelves: String get() = pick("Your shelves", "ನಿಮ್ಮ ಅಂಗಡಿ")
    val whatDoYouStock: String get() = pick("What do you stock?", "ನೀವು ಏನು ಮಾರುತ್ತೀರಿ?")

    val stockNamesBody: String get() =
            pick(
                "Names only for now. Prices and counts come next, and you can add or remove items any time after.",
                "ಸದ್ಯಕ್ಕೆ ಹೆಸರುಗಳು ಮಾತ್ರ. ಬೆಲೆ ಮತ್ತು ಎಣಿಕೆ ಮುಂದಿನ ಹಂತದಲ್ಲಿ. ಆಮೇಲೆ ಯಾವಾಗ ಬೇಕಾದರೂ ಸೇರಿಸಬಹುದು, ತೆಗೆಯಬಹುದು."
            )

    val commonHardwareLines: String get() = pick("Common hardware lines", "ಸಾಮಾನ್ಯ ಹಾರ್ಡ್‌ವೇರ್ ಸಾಮಾನು")
    val nothingAddedYetKicker: String get() = pick("Nothing added yet", "ಇನ್ನೂ ಏನೂ ಸೇರಿಸಿಲ್ಲ")

    fun addedCount(n: Int): String =
            pick("Added · $n", "ಸೇರಿಸಿದ್ದು · $n")

    val nextStockAndPrices: String get() = pick("Next — stock & prices", "ಮುಂದೆ — ದಾಸ್ತಾನು ಮತ್ತು ಬೆಲೆ")
    val stockAndPrices: String get() = pick("Stock and prices", "ದಾಸ್ತಾನು ಮತ್ತು ಬೆಲೆ")

    val stockAndPricesBody: String get() =
            pick(
                "All three are needed for every item — the count on the shelf, what you paid, what you charge.",
                "ಪ್ರತಿ ಸಾಮಾನಿಗೂ ಮೂರೂ ಬೇಕು — ಅಂಗಡಿಯಲ್ಲಿ ಎಷ್ಟಿದೆ, ನೀವು ಎಷ್ಟು ಕೊಟ್ಟಿರಿ, ಎಷ್ಟಕ್ಕೆ ಮಾರುತ್ತೀರಿ."
            )

    val youPay: String get() = pick("You pay", "ನೀವು ಕೊಡುವುದು")
    val youSell: String get() = pick("You sell", "ನೀವು ಮಾರುವುದು")
    val nextCustomers: String get() = pick("Next — your customers", "ಮುಂದೆ — ನಿಮ್ಮ ಗ್ರಾಹಕರು")
    val whoDoYouSellTo: String get() = pick("Who buys on account?", "ಯಾರು ಖಾತೆಯಲ್ಲಿ ಖರೀದಿಸುತ್ತಾರೆ?")

    // Says out loud that this step is skippable, because a setup screen that
    // looks compulsory is where an owner gives up and types nonsense.
    val customersSetupBody: String
        get() = pick(
            "The regulars who pay later, so their names are ready at the counter and you can print a statement. Skip this — you can add anybody while writing a bill.",
            "ನಂತರ ಪಾವತಿಸುವ ನಿಯಮಿತ ಗ್ರಾಹಕರು — ಕೌಂಟರಿನಲ್ಲಿ ಹೆಸರು ಸಿದ್ಧವಿರುತ್ತದೆ ಮತ್ತು ಖಾತೆ ವಿವರ ತೆಗೆಯಬಹುದು. ಇದನ್ನು ಬಿಟ್ಟುಬಿಡಬಹುದು — ಬಿಲ್ ಬರೆಯುವಾಗಲೂ ಯಾರನ್ನಾದರೂ ಸೇರಿಸಬಹುದು."
        )

    val customerNameExample: String get() = pick("Ahmed Contracting", "ಅಹ್ಮದ್ ಕಂಟ್ರಾಕ್ಟಿಂಗ್")
    val noCustomersYetKicker: String get() = pick("Nobody added yet", "ಇನ್ನೂ ಯಾರನ್ನೂ ಸೇರಿಸಿಲ್ಲ")

    val whoDoYouBuyFrom: String get() = pick("Who do you buy from?", "ನೀವು ಯಾರಿಂದ ಖರೀದಿಸುತ್ತೀರಿ?")

    // The supplier half of the same step, and the same promise: skippable.
    val suppliersSetupBody: String
        get() = pick(
            "The people who deliver to you, so their names are ready when stock arrives. Skip this — you can add anybody while entering a delivery.",
            "ನಿಮಗೆ ಸಾಮಾನು ತಲುಪಿಸುವವರು — ದಾಸ್ತಾನು ಬಂದಾಗ ಹೆಸರು ಸಿದ್ಧವಿರುತ್ತದೆ. ಇದನ್ನು ಬಿಟ್ಟುಬಿಡಬಹುದು — ಡೆಲಿವರಿ ದಾಖಲಿಸುವಾಗಲೂ ಸೇರಿಸಬಹುದು."
        )

    val noSuppliersYetKicker: String get() = pick("Nobody added yet", "ಇನ್ನೂ ಯಾರನ್ನೂ ಸೇರಿಸಿಲ್ಲ")

    // The mirror of `openingBalanceNote`: money owed *out* rather than in.
    val supplierOpeningNote: String
        get() = pick(
            "What you already owed them before Stockbook, from the old book. Leave it empty if nothing.",
            "ಸ್ಟಾಕ್‌ಬುಕ್ ಶುರು ಮಾಡುವ ಮೊದಲು ಹಳೆಯ ಪುಸ್ತಕದಲ್ಲಿ ನೀವು ಅವರಿಗೆ ಕೊಡಬೇಕಿದ್ದ ಮೊತ್ತ. ಏನೂ ಇಲ್ಲದಿದ್ದರೆ ಖಾಲಿ ಬಿಡಿ."
        )

    val openTheShop: String get() = pick("Open the shop", "ಅಂಗಡಿ ತೆರೆಯಿರಿ")

    val allSet: String get() =
            pick(
                "All set — stock and both prices filled in.",
                "ಎಲ್ಲಾ ಸಿದ್ಧ — ದಾಸ್ತಾನು ಮತ್ತು ಎರಡೂ ಬೆಲೆ ತುಂಬಿದೆ."
            )

    fun stillNeedPrices(n: Int): String =
        pick(
            if (n == 1) "1 item still needs stock, buying and selling price."
            else "$n items still need stock, buying and selling price.",
            "$n ಸಾಮಾನಿಗೆ ಇನ್ನೂ ದಾಸ್ತಾನು, ಖರೀದಿ ಮತ್ತು ಮಾರಾಟ ಬೆಲೆ ಬೇಕು."
        )

    // --- Settings

    val thisPhone: String get() = pick("This phone", "ಈ ಫೋನ್")
    val businessOwner: String get() = pick("Business owner", "ಅಂಗಡಿ ಮಾಲೀಕರು")
    val productsStat: String get() = pick("Products", "ಸಾಮಾನುಗಳು")
    val customersStat: String get() = pick("Customers", "ಗ್ರಾಹಕರು")
    val languageSection: String get() = pick("Language", "ಭಾಷೆ")
    val themeSection: String get() = pick("Theme", "ಥೀಮ್")
    val themeDark: String get() = pick("Dark", "ಕಪ್ಪು")
    val themeLight: String get() = pick("Light", "ಬಿಳಿ")
    val languageAndCurrency: String get() = pick("Language and currency", "ಭಾಷೆ ಮತ್ತು ಹಣ")
    val notBackedUpYet: String get() = pick("Nothing backed up yet", "ಇನ್ನೂ ಬ್ಯಾಕಪ್ ಆಗಿಲ್ಲ")

    fun backedUpOn(date: String): String =
            pick("Backed up $date", "$date ರಂದು ಬ್ಯಾಕಪ್ ಆಗಿದೆ")
    val currencySection: String get() = pick("Currency", "ಹಣದ ಬಗೆ")

    // `Saudi Riyal` — from the system, so it arrives already in the language
    // in force rather than being another column to keep translated.
    fun currencyName(currency: Currency): String =
        runCatching { java.util.Currency.getInstance(currency.code).getDisplayName(language.locale) }
            .getOrNull() ?: currency.code

    // `SAR · Saudi Riyal` — the menu row.
    fun currencyRow(currency: Currency): String =
            "${currency.code} · ${currencyName(currency)}"

    // Trimmed to the half that cannot be discovered by trying it. Changing the
    // language explains itself the moment it happens; changing the currency
    // does not say what became of the numbers.
    val currencyNote: String get() =
            pick(
                "Changing the currency converts nothing — saved amounts keep the numbers you entered, and only the symbol in front of them changes.",
                "ಹಣದ ಬಗೆ ಬದಲಿಸಿದರೆ ಯಾವುದೂ ಪರಿವರ್ತನೆ ಆಗುವುದಿಲ್ಲ — ಉಳಿಸಿದ ಮೊತ್ತಗಳ ಸಂಖ್ಯೆ ಹಾಗೆಯೇ ಇರುತ್ತದೆ, ಮುಂದಿನ ಚಿಹ್ನೆ ಮಾತ್ರ ಬದಲಾಗುತ್ತದೆ."
            )

    val setupCurrencyNote: String get() =
            pick(
                "What you bill in. You can change it later in Settings.",
                "ನೀವು ಯಾವ ಹಣದಲ್ಲಿ ಬಿಲ್ ಮಾಡುತ್ತೀರಿ. ಆಮೇಲೆ ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಬದಲಿಸಬಹುದು."
            )


    val moveToAnotherPhone: String get() = pick("Move to another phone", "ಇನ್ನೊಂದು ಫೋನಿಗೆ ಸಾಗಿಸಿ")

    val moveToAnotherPhoneNote: String get() =
            pick(
                "Stockbook never uploads anything, so a new phone gets your shop from a file you carry across. Export here, then import on the other phone.",
                "ಸ್ಟಾಕ್‌ಬುಕ್ ಯಾವುದನ್ನೂ ಇಂಟರ್ನೆಟ್‌ಗೆ ಕಳಿಸುವುದಿಲ್ಲ. ಹಾಗಾಗಿ ಹೊಸ ಫೋನಿಗೆ ನಿಮ್ಮ ಅಂಗಡಿ ಫೈಲ್ ಮೂಲಕವೇ ಹೋಗಬೇಕು. ಇಲ್ಲಿ ಫೈಲ್ ಮಾಡಿ, ಆ ಫೋನಿನಲ್ಲಿ ತರಿಸಿಕೊಳ್ಳಿ."
            )

    val exportEverything: String get() = pick("Export everything", "ಎಲ್ಲವನ್ನೂ ಫೈಲ್‌ಗೆ ಉಳಿಸಿ")

    val exportNoteFirstTime: String get() =
            pick(
                "Writes one file with every product, price, stock count and bill.",
                "ಎಲ್ಲಾ ಸಾಮಾನು, ಬೆಲೆ, ದಾಸ್ತಾನು ಮತ್ತು ಬಿಲ್ ಇರುವ ಒಂದು ಫೈಲ್ ಬರೆಯುತ್ತದೆ."
            )

    val exportNoteAfterBackup: String get() =
            pick(
                "Written to Files. Send it to the other phone however you like — AirDrop, WhatsApp, a memory card.",
                "ಫೈಲ್ಸ್‌ನಲ್ಲಿ ಉಳಿಸಲಾಗಿದೆ. ಇನ್ನೊಂದು ಫೋನಿಗೆ ನಿಮಗೆ ಬೇಕಾದ ಹಾಗೆ ಕಳಿಸಿ — ಏರ್‌ಡ್ರಾಪ್, ವಾಟ್ಸಾಪ್, ಮೆಮೊರಿ ಕಾರ್ಡ್."
            )

    val writeAFreshFile: String get() = pick("Write a fresh file", "ಹೊಸ ಫೈಲ್ ಬರೆಯಿರಿ")
    val createBackupFile: String get() = pick("Create backup file", "ಬ್ಯಾಕಪ್ ಫೈಲ್ ಮಾಡಿ")
    val importABackupFile: String get() = pick("Import a backup file", "ಬ್ಯಾಕಪ್ ಫೈಲ್ ತರಿಸಿ")
    val chooseAFile: String get() = pick("Choose a file", "ಫೈಲ್ ಆರಿಸಿ")
    val replaceEverything: String get() = pick("Replace everything", "ಎಲ್ಲವನ್ನೂ ಬದಲಿಸಿ")

    val importNoteIdle: String get() =
            pick(
                "Pick a file exported from another phone. Its contents take over from what is here now.",
                "ಇನ್ನೊಂದು ಫೋನಿನಿಂದ ಉಳಿಸಿದ ಫೈಲ್ ಆರಿಸಿ. ಅದರಲ್ಲಿರುವುದು ಈಗ ಇಲ್ಲಿ ಇರುವುದರ ಜಾಗ ತೆಗೆದುಕೊಳ್ಳುತ್ತದೆ."
            )

    val importNoteDone: String get() =
            pick(
                "Imported. Everything from that file is now on this phone.",
                "ತರಿಸಲಾಗಿದೆ. ಆ ಫೈಲಿನಲ್ಲಿದ್ದ ಎಲ್ಲವೂ ಈಗ ಈ ಫೋನಿನಲ್ಲಿದೆ."
            )

    // Names what is about to be lost, in the owner's own numbers.
    fun replaceWarning(productCount: Int, billCount: Int): String =
            pick(
                "This replaces the ${products(productCount)} and ${bills(billCount)} already on this phone. It cannot be undone.",
                "ಈ ಫೋನಿನಲ್ಲಿ ಈಗಾಗಲೇ ಇರುವ $productCount ಸಾಮಾನು ಮತ್ತು $billCount ಬಿಲ್ ಇದರಿಂದ ಬದಲಾಗುತ್ತವೆ. ಇದನ್ನು ವಾಪಸ್ ಪಡೆಯಲು ಆಗುವುದಿಲ್ಲ."
            )

    val startAgain: String get() = pick("Start again", "ಮತ್ತೆ ಶುರು")
    val startOver: String get() = pick("Start over", "ಮತ್ತೆ ಶುರು ಮಾಡಿ")


    // --- Backup file

    fun savedOn(date: String): String =
            pick("saved $date", "$date ರಂದು ಉಳಿಸಿದ್ದು")

    fun fileSize(kilobytes: Int): String =
            pick("$kilobytes KB", "$kilobytes KB")

    fun backupError(error: BackupError): String = when (error) {
        is BackupError.Unreadable ->
            pick("That file could not be opened.", "ಆ ಫೈಲ್ ತೆರೆಯಲು ಆಗಲಿಲ್ಲ.")
        is BackupError.NotStockbookData ->
            pick(
                "That is not a Stockbook backup file.",
                "ಅದು ಸ್ಟಾಕ್‌ಬುಕ್ ಬ್ಯಾಕಪ್ ಫೈಲ್ ಅಲ್ಲ."
            )
        is BackupError.NewerVersion ->
            pick(
                "That backup was written by a newer version of Stockbook (format ${error.found}). Update this phone first.",
                "ಆ ಬ್ಯಾಕಪ್ ಸ್ಟಾಕ್‌ಬುಕ್‌ನ ಹೊಸ ಆವೃತ್ತಿಯಿಂದ ಬರೆದದ್ದು (ಫಾರ್ಮ್ಯಾಟ್ ${error.found}). ಮೊದಲು ಈ ಫೋನಿನ ಆ್ಯಪ್ ಅಪ್‌ಡೇಟ್ ಮಾಡಿ."
            )
    }

    // --- Dates

    // `TUESDAY, 11 AUGUST` — uppercased by the `.kicker` type role.
    fun headerDate(date: java.time.Instant): String = Dates.headerDate(date, language.locale)

    // `09:41` — always 24-hour, in both languages.
    fun time(date: java.time.Instant): String = Dates.time(date)

    // `28 July 2026`
    fun longDate(date: java.time.Instant): String = Dates.longDate(date, language.locale)

}
