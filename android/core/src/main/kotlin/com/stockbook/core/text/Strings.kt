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
    val soldInPeriod: String get() = pick("Sold", "ಮಾರಾಟ")
    val receivableStat: String get() = pick("Receivable", "ಬರಬೇಕಾದ ಬಾಕಿ")
    val payableStat: String get() = pick("Payable", "ಕೊಡಬೇಕಾದ ಬಾಕಿ")
    val billsStat: String get() = pick("Bills", "ಬಿಲ್‌ಗಳು")
    // What Home shows under the two banners.
    //
    // Not recent bills: that list is the Reports tab, and repeating it on the
    // screen the owner lands on spends the best space in the app on something
    // one tap away. What belongs here is what they can *do* something about
    // while standing at the counter — and the shelf running out is the one thing
    // nothing else in the app volunteers.
    val runningLow: String get() = pick("Running low", "ಕಡಿಮೆ ಆಗುತ್ತಿದೆ")
    val nothingRunningLow: String get() = pick("Nothing running low.", "ಯಾವುದೂ ಕಡಿಮೆ ಆಗಿಲ್ಲ.")

    fun greeting(firstName: String): String =
            pick("Hello, $firstName", "ನಮಸ್ಕಾರ, $firstName")

    // One name reads as a name; several read as a count of **people**.
    fun stillOwes(name: String): String =
            pick("$name still owes", "$name ಇನ್ನೂ ಬಾಕಿ ಕೊಡಬೇಕು")

    /**
     * The same banner when more than one person owes.
     *
     * **Names the biggest debtor**, then counts the rest. A bare "3 customers
     * still owe" is a figure with nobody attached to it: the owner has to open
     * the list to learn anything they can act on, which is the hunt this banner
     * exists to save them. The list is already sorted by what is owed, so the
     * first name is the one worth saying.
     */
    fun stillOweWithOthers(name: String, others: Int): String = pick(
        if (others == 1) "$name and 1 other still owe" else "$name and $others others still owe",
        "$name ಮತ್ತು ಇನ್ನೂ $others ಜನ ಬಾಕಿ ಕೊಡಬೇಕು"
    )

    // --- Items

    val itemsTitle: String get() = pick("Items", "ಸಾಮಾನುಗಳು")
    val search: String get() = pick("Search", "ಹುಡುಕಿ")
    val nothingAddedYet: String get() = pick("nothing added yet", "ಇನ್ನೂ ಏನೂ ಸೇರಿಸಿಲ್ಲ")
    /**
     * The Items header's own name for [recordDelivery] — same sheet, same
     * action, and now the same word. Kept as its own key because the two are
     * different buttons on different screens, and one of them may yet need to
     * read differently.
     */
    val itemsRecordDelivery: String get() = pick("Inventory", "ದಾಸ್ತಾನು")

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

    // The other way into the same sheet the Items header opens, so it is called
    // the same thing. Two buttons that do one job under two names is how somebody
    // concludes there are two jobs.
    val recordDelivery: String get() = pick("Inventory", "ದಾಸ್ತಾನು")
    val whichProductArrived: String get() = pick("What arrived?", "ಏನು ಬಂತು?")

    /**
     * The way out of the product list when nothing in it matches, said the same
     * way `addAsSupplier` says it.
     *
     * The delivery sheet needs one because a supplier's note is where genuinely
     * new stock usually appears. Without it, a five-line delivery of things the
     * shop has never carried is ten sheets: leave, add the product, come back,
     * find your place, repeat.
     */
    fun addAsProduct(name: String): String =
            pick("Add “$name” as a product", "“$name” ಅನ್ನು ಸಾಮಾನಾಗಿ ಸೇರಿಸಿ")
    val noDeliveriesYet: String get() = pick("No deliveries yet", "ಇನ್ನೂ ಡೆಲಿವರಿ ಇಲ್ಲ")
    val deliveryDetail: String get() = pick("Inventory", "ದಾಸ್ತಾನು")

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

    // --- Photographs of the paper bill
    //
    // "Photo", never "attachment" or "image". The owner is taking a picture of a
    // piece of paper, and the word for that is the one they already use.

    val addPhoto: String get() = pick("Add photo", "ಫೋಟೋ ಸೇರಿಸಿ")
    val takePhoto: String get() = pick("Take a photo", "ಫೋಟೋ ತೆಗೆಯಿರಿ")
    val chooseFromPhotos: String get() = pick("Choose from photos", "ಫೋಟೋಗಳಿಂದ ಆರಿಸಿ")
    val removePhoto: String get() = pick("Remove photo", "ಫೋಟೋ ತೆಗೆದುಹಾಕಿ")
    val billPhotos: String get() = pick("Photo of the bill", "ಬಿಲ್‌ನ ಫೋಟೋ")
    val photoLabel: String get() = pick("Photo", "ಫೋಟೋ")

    /** Shown in place of a picture whose file is not on this phone. */
    val photoNotOnThisPhone: String get() = pick("Not on this phone", "ಈ ಫೋನಿನಲ್ಲಿ ಇಲ್ಲ")

    val couldNotReadThatPhoto: String
        get() = pick("That photo could not be read", "ಆ ಫೋಟೋ ಓದಲು ಆಗಲಿಲ್ಲ")

    val noCameraOnThisPhone: String
        get() = pick("No camera on this phone", "ಈ ಫೋನಿನಲ್ಲಿ ಕ್ಯಾಮೆರಾ ಇಲ್ಲ")

    /** `2 photos` — how many pictures a bill carries, on the row that opens them. */
    fun photos(count: Int): String = when {
        count == 1 -> pick("1 photo", "1 ಫೋಟೋ")
        else -> pick("$count photos", "$count ಫೋಟೋಗಳು")
    }

    // The Settings row. Storage that grows where nobody can see it is what gets
    // an app deleted, so the figure is shown plainly and the missing ones are
    // named — an incomplete transfer should be visible now, not discovered in a
    // year.
    val photoStorage: String get() = pick("Photos", "ಫೋಟೋಗಳು")

    fun photosOnThisPhone(count: Int, size: String): String =
            pick("$count on this phone · $size", "ಈ ಫೋನಿನಲ್ಲಿ $count · $size")

    fun photosMissing(count: Int): String =
            pick("$count missing", "$count ಇಲ್ಲ")

    val removeAllPhotos: String get() = pick("Remove all photos", "ಎಲ್ಲಾ ಫೋಟೋ ತೆಗೆದುಹಾಕಿ")

    val removeAllPhotosNote: String
        get() = pick(
            "The bills stay. Only the pictures go, and they cannot be got back.",
            "ಬಿಲ್‌ಗಳು ಉಳಿಯುತ್ತವೆ. ಫೋಟೋಗಳು ಮಾತ್ರ ಹೋಗುತ್ತವೆ, ಮತ್ತೆ ಸಿಗುವುದಿಲ್ಲ."
        )

    /**
     * Said on the backup screen. This was the opposite sentence until the
     * archive arrived — a warning, in accent, that the pictures stayed behind.
     */
    val photosTravelWithTheBook: String
        get() = pick(
            "Photos of your bills go in the file too.",
            "ನಿಮ್ಮ ಬಿಲ್‌ಗಳ ಫೋಟೋಗಳೂ ಈ ಫೈಲಿನಲ್ಲಿ ಹೋಗುತ್ತವೆ."
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
    val noCustomersYet: String get() = pick("No customers yet", "ಇನ್ನೂ ಗ್ರಾಹಕರು ಇಲ್ಲ")
    val noSuppliersYet: String get() = pick("No suppliers yet", "ಇನ್ನೂ ಪೂರೈಕೆದಾರರು ಇಲ್ಲ")

    /** A search that found nobody. Said for customers and suppliers alike. */
    val nobodyMatches: String get() = pick("Nobody by that name", "ಆ ಹೆಸರಿನವರು ಯಾರೂ ಇಲ್ಲ")
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

    /** The mirror of [stillOweWithOthers]: the largest creditor, then the rest. */
    fun youOweWithOthers(name: String, others: Int): String = pick(
        if (others == 1) "You owe $name and 1 other" else "You owe $name and $others others",
        "$name ಮತ್ತು ಇನ್ನೂ $others ಜನರಿಗೆ ನೀವು ಕೊಡಬೇಕು"
    )
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
    /**
     * Said under the name box the moment what is typed belongs to somebody else.
     *
     * Names the account it would have collided with, because "that name is
     * taken" leaves the owner hunting for who took it — and on a book of firms
     * with similar names that is the whole question.
     */
    fun nameAlreadyUsed(name: String): String =
            pick(
                "$name is already on your list",
                "$name ಈಗಾಗಲೇ ನಿಮ್ಮ ಪಟ್ಟಿಯಲ್ಲಿದೆ"
            )

    // Said when removing a roster entry, because "remove" beside somebody's
    // name reads like deleting them and their history.
    val removeCustomerNote: String
        get() = pick(
            "Their bills stay. This only takes them off the customer list.",
            "ಅವರ ಬಿಲ್‌ಗಳು ಹಾಗೇ ಉಳಿಯುತ್ತವೆ. ಇದು ಅವರನ್ನು ಗ್ರಾಹಕರ ಪಟ್ಟಿಯಿಂದ ಮಾತ್ರ ತೆಗೆಯುತ್ತದೆ."
        )


    // --- Merging two accounts

    /**
     * The way in, from the account that will be the one to go.
     *
     * Worded from where the owner is standing: they are looking at the duplicate
     * when they notice it, so this account merges *into* the one they pick.
     */
    val mergeIntoAnotherCustomer: String
        get() = pick("Merge into another customer", "ಇನ್ನೊಬ್ಬ ಗ್ರಾಹಕರೊಂದಿಗೆ ಸೇರಿಸಿ")
    val mergeIntoAnotherSupplier: String
        get() = pick("Merge into another supplier", "ಇನ್ನೊಬ್ಬ ಪೂರೈಕೆದಾರರೊಂದಿಗೆ ಸೇರಿಸಿ")
    val mergeAccounts: String get() = pick("Merge accounts", "ಖಾತೆಗಳನ್ನು ಸೇರಿಸಿ")
    /** Says which way round it goes, above the list of who it could go into. */
    fun mergeChoose(name: String): String =
            pick(
                "Choose the account to keep. $name will be merged into it.",
                "ಇಟ್ಟುಕೊಳ್ಳಬೇಕಾದ ಖಾತೆ ಆರಿಸಿ. $name ಅದರೊಳಗೆ ಸೇರುತ್ತದೆ."
            )
    fun mergeConfirm(from: String, into: String): String =
            pick("Merge $from into $into?", "$from ಅನ್ನು $into ಒಳಗೆ ಸೇರಿಸಬೇಕೆ?")
    fun billsMoving(n: Int): String =
            pick(if (n == 1) "1 bill moves across" else "$n bills move across", "$n ಬಿಲ್ ವರ್ಗಾವಣೆ")
    fun paymentsMoving(n: Int): String =
            pick(if (n == 1) "1 payment moves across" else "$n payments move across", "$n ಪಾವತಿ ವರ್ಗಾವಣೆ")
    fun creditNotesMoving(n: Int): String =
            pick(
                if (n == 1) "1 credit note moves across" else "$n credit notes move across",
                "$n ಕ್ರೆಡಿಟ್ ನೋಟ್ ವರ್ಗಾವಣೆ"
            )
    fun deliveriesMoving(n: Int): String =
            pick(
                if (n == 1) "1 delivery moves across" else "$n deliveries move across",
                "$n ಡೆಲಿವರಿ ವರ್ಗಾವಣೆ"
            )
    /** The figure the owner is really agreeing to, so it is named rather than implied. */
    fun willOwe(name: String): String = pick("$name will owe", "$name ಬಾಕಿ ಇರುತ್ತದೆ")
    fun willBeGone(name: String): String = pick("$name will be gone", "$name ಇರುವುದಿಲ್ಲ")
    /**
     * Said on the confirmation, because it is true and because the only way back
     * is a file the owner has to have exported already.
     */
    val mergeCannotBeUndone: String
        get() = pick(
            "This cannot be undone. Export a backup first if you want a way back.",
            "ಇದನ್ನು ಹಿಂತಿರುಗಿಸಲಾಗದು. ಹಿಂದಿರುಗುವ ದಾರಿ ಬೇಕಿದ್ದರೆ ಮೊದಲು ಬ್ಯಾಕಪ್ ಉಳಿಸಿ."
        )
    val nobodyToMergeWith: String
        get() = pick("There is nobody else to merge with", "ಸೇರಿಸಲು ಬೇರೆ ಯಾರೂ ಇಲ್ಲ")

    // --- Payments

    val recordAPayment: String get() = pick("Record a payment", "ಪಾವತಿ ದಾಖಲಿಸಿ")

    /**
     * The same sheet, opened on one that already exists.
     *
     * "Correct", not "Edit": the owner is fixing something written down wrong,
     * which is a different act from changing their mind — and the word the paper
     * book would use.
     */
    val correctAPayment: String get() = pick("Correct a payment", "ಪಾವತಿ ಸರಿಪಡಿಸಿ")
    val amountReceived: String get() = pick("Amount received", "ಸ್ವೀಕರಿಸಿದ ಮೊತ್ತ")
    val receivedOn: String get() = pick("Received on", "ಸ್ವೀಕರಿಸಿದ ದಿನ")
    val paymentNote: String get() = pick("Note", "ಟಿಪ್ಪಣಿ")
    val paymentNoteExample: String get() = pick("cash, cheque…", "ನಗದು, ಚೆಕ್…")
    val paymentNoField: String get() = pick("Receipt no.", "ರಸೀದಿ ಸಂಖ್ಯೆ")
    val paymentNoHint: String get() = pick("e.g. 008455", "ಉದಾ. 008455")
    val enterPaymentNumber: String get() = pick("Enter a receipt number", "ರಸೀದಿ ಸಂಖ್ಯೆ ಬರೆಯಿರಿ")
    val changeThePaymentNo: String get() = pick("Change the receipt number", "ರಸೀದಿ ಸಂಖ್ಯೆ ಬದಲಾಯಿಸಿ")

    fun paymentNoAlreadyUsed(date: String): String =
            pick("Already used on $date", "$date ರಂದು ಬಳಸಲಾಗಿದೆ")

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

    // --- Credit notes

    val creditNoteLabel: String get() = pick("Credit note", "ಕ್ರೆಡಿಟ್ ನೋಟ್")
    val creditNotes: String get() = pick("Credit notes", "ಕ್ರೆಡಿಟ್ ನೋಟ್‌ಗಳು")
    val issueACreditNote: String get() = pick("Issue a credit note", "ಕ್ರೆಡಿಟ್ ನೋಟ್ ಕೊಡಿ")
    val editCreditNote: String get() = pick("Edit credit note", "ಕ್ರೆಡಿಟ್ ನೋಟ್ ಬದಲಾಯಿಸಿ")
    val creditNoteNo: String get() = pick("Credit note no.", "ಕ್ರೆಡಿಟ್ ನೋಟ್ ಸಂಖ್ಯೆ")
    val creditNoteNoHint: String get() = pick("e.g. 00130", "ಉದಾ. 00130")
    val amountCredited: String get() = pick("Amount credited", "ಕೊಟ್ಟ ರಿಯಾಯಿತಿ")
    val creditedOn: String get() = pick("Credited on", "ಕೊಟ್ಟ ದಿನ")
    val creditReason: String get() = pick("Reason", "ಕಾರಣ")
    val creditReasonExample: String
        get() = pick("returned goods, overcharge…", "ವಾಪಸ್ ಬಂದ ಸಾಮಾನು, ಹೆಚ್ಚು ಬಿಲ್…")
    val saveCreditNote: String get() = pick("Save credit note", "ಕ್ರೆಡಿಟ್ ನೋಟ್ ಉಳಿಸಿ")
    val removeCreditNote: String get() = pick("Remove this credit note", "ಈ ಕ್ರೆಡಿಟ್ ನೋಟ್ ತೆಗೆದುಹಾಕಿ")
    val enterCreditNoteNumber: String get() = pick("Enter a note number", "ನೋಟ್ ಸಂಖ್ಯೆ ಬರೆಯಿರಿ")
    val changeTheCreditNoteNo: String get() = pick("Change the note number", "ನೋಟ್ ಸಂಖ್ಯೆ ಬದಲಾಯಿಸಿ")
    val itemsReturned: String get() = pick("Items returned", "ವಾಪಸ್ ಬಂದ ಸಾಮಾನು")
    val addReturnedItems: String get() = pick("Add returned items", "ವಾಪಸ್ ಬಂದ ಸಾಮಾನು ಸೇರಿಸಿ")
    val noCreditNotesYet: String get() = pick("No credit notes yet.", "ಇನ್ನೂ ಕ್ರೆಡಿಟ್ ನೋಟ್ ಇಲ್ಲ.")

    // The one thing to know about a credit note: it is not money.
    val creditNoteNotAPayment: String
        get() = pick(
            "Reduces what this customer owes without any money changing hands. Returned goods go back on the shelf.",
            "ಹಣ ಬರದೆಯೇ ಈ ಗ್ರಾಹಕರ ಬಾಕಿ ಕಡಿಮೆ ಆಗುತ್ತದೆ. ವಾಪಸ್ ಬಂದ ಸಾಮಾನು ಮತ್ತೆ ದಾಸ್ತಾನಿಗೆ ಸೇರುತ್ತದೆ."
        )

    fun creditNoteAlreadyUsed(date: String): String =
        pick("Already used on $date", "$date ರಂದು ಬಳಸಲಾಗಿದೆ")

    // --- Statement

    val statement: String get() = pick("Statement", "ಖಾತೆ ವಿವರ")
    val thisMonth: String get() = pick("This month", "ಈ ತಿಂಗಳು")
    val lastMonth: String get() = pick("Last month", "ಕಳೆದ ತಿಂಗಳು")
    val thisYear: String get() = pick("This year", "ಈ ವರ್ಷ")
    val chooseDates: String get() = pick("Choose dates", "ದಿನಾಂಕ ಆರಿಸಿ")
    val fromDate: String get() = pick("From", "ಇಂದ")
    val toDate: String get() = pick("To", "ವರೆಗೆ")
    /**
     * What the account stood at when the period began.
     *
     * The same words the customer editor's own field uses — the figure typed
     * there *is* this row on the first statement, and calling it two things was
     * asking the owner to work that out for themselves.
     */
    val openingBalance: String get() = pick("Opening balance", "ಪ್ರಾರಂಭಿಕ ಬಾಕಿ")
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
    /**
     * The heading while the product list is up.
     *
     * "New bill" over a list of products describes the errand rather than the
     * screen — and the screen is the one thing a heading is for. The bill is
     * still there underneath, and one tap away.
     */
    val selectProduct: String get() = pick("Select product", "ಸಾಮಾನು ಆರಿಸಿ")
    val cartEmpty: String get() = pick("empty", "ಖಾಲಿ")
    /**
     * The box above the product list. It **filters** the list; it does not add
     * anything, and "Add a product…" read as though typing into it would.
     */
    val searchAProduct: String get() = pick("Search a product", "ಸಾಮಾನು ಹುಡುಕಿ")
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
    // --- A percentage off the whole bill

    val discountField: String get() = pick("Discount %", "ರಿಯಾಯಿತಿ %")
    val discountHint: String get() = pick("e.g. 10", "ಉದಾ. 10")
    val subtotalLabel: String get() = pick("Subtotal", "ಮೊತ್ತ")

    /** `Discount 10%` — the label on the bill's own deduction line. */
    fun discountOf(percent: String): String = pick("Discount $percent%", "ರಿಯಾಯಿತಿ $percent%")

    /** `SAR 25 off` — what the percentage came to, said while it is being typed. */
    fun discountComesTo(amount: String): String = pick("$amount off", "$amount ಕಡಿತ")

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
    /**
     * The note on a bill: the owner's own reminder of what it was for.
     *
     * One flat word, and the same one on the form and on the opened bill. It
     * used to ask "What was it for?" with a line underneath saying the customer
     * would never see it — but the box now sits at the foot of the form, past
     * the money and under the owner's thumb, which says the same thing without
     * two lines of ceremony in the middle of the paperwork.
     */
    val billNote: String get() = pick("NOTE:", "ಟಿಪ್ಪಣಿ:")

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

    val takePayment: String get() = pick("Take payment", "ಪಾವತಿ ಪಡೆಯಿರಿ")
    /**
     * The same row on the other side of the book. Money leaving, not arriving —
     * "Take payment" beside a supplier the shop owes describes the wrong
     * direction entirely, and it is the one word on that sheet a hurried thumb
     * reads before tapping.
     */
    val makePayment: String get() = pick("Make payment", "ಪಾವತಿ ಮಾಡಿ")


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

    /**
     * The count on a product being created, and only then.
     *
     * Named for what it is rather than "In stock", because that read as a live
     * figure this sheet could keep setting — which is exactly what it used to
     * be, and what made it an unlabelled second [setCount]. This one is the
     * shelf on day one, carried over from the paper book: the same idea as a
     * customer's opening balance, and absent for the same reason once the app
     * itself is the record.
     */
    val openingStock: String get() = pick("Opening stock", "ಆರಂಭಿಕ ದಾಸ್ತಾನು")

    val openingStockNote: String
        get() = pick(
            "What is on the shelf today. After this, stock moves on a delivery, a bill, or a recount.",
            "ಇಂದು ಶೆಲ್ಫಿನಲ್ಲಿ ಇರುವುದು. ನಂತರ ದಾಸ್ತಾನು ಡೆಲಿವರಿ, ಬಿಲ್ ಅಥವಾ ಮರು-ಎಣಿಕೆಯಿಂದ ಬದಲಾಗುತ್ತದೆ."
        )
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


    // --- The printed statement
    //
    // Its own words rather than the screen's. A document somebody files beside
    // their supplier statements should read like one.

    /**
     * What a row in the Transaction column is called: the kind of document, then
     * its number.
     *
     * A bare "06011" tells somebody checking their own file nothing about *what*
     * 06011 is, and the three books are numbered separately — invoice 130 and
     * credit note 130 are different pieces of paper. The `#` goes in here rather
     * than into the stored number, so what the owner typed stays what they typed.
     */
    fun invoiceRef(no: String): String = pick("Invoice #$no", "ಬಿಲ್ #$no")
    fun creditNoteRef(no: String): String = pick("Credit Note #$no", "ಕ್ರೆಡಿಟ್ ನೋಟ್ #$no")
    fun paymentRef(no: String): String = pick("Payment #$no", "ಪಾವತಿ #$no")
    fun deliveryRef(no: String): String = pick("Delivery #$no", "ಡೆಲಿವರಿ #$no")

    val accountStatementFor: String get() = pick("Account statement for:", "ಖಾತೆ ವಿವರ — ಇವರಿಗೆ:")
    val accountActivity: String get() = pick("Account Activity", "ಖಾತೆ ವ್ಯವಹಾರ")
    val balanceDue: String get() = pick("Balance Due", "ಕೊಡಬೇಕಾದ ಬಾಕಿ")
    /**
     * The activity table's four headings.
     *
     * The middle two flip with the direction. Money the shop is owed was
     * *received*; money it owes was *paid*. One pair of words for both would be
     * backwards on one of the two documents, and a statement is the page a
     * customer or a supplier reads most carefully.
     */
    val columnInvoiceReceipt: String get() = pick("Invoice / Receipt", "ಬಿಲ್ / ರಸೀದಿ")
    val columnBillReceipt: String get() = pick("Bill / Receipt", "ಬಿಲ್ / ರಸೀದಿ")
    val columnInvoiceAmount: String get() = pick("Invoice amount", "ಬಿಲ್ ಮೊತ್ತ")
    val columnBillAmount: String get() = pick("Bill amount", "ಬಿಲ್ ಮೊತ್ತ")
    val columnReceivedAmount: String get() = pick("Received amount", "ಬಂದ ಮೊತ್ತ")
    val columnPaidAmount: String get() = pick("Paid amount", "ಕೊಟ್ಟ ಮೊತ್ತ")

    /**
     * `Invoice #6356 · 19/05/2026` — one cell holding what a row is and when it
     * happened, which used to be two columns.
     *
     * The kind stays in front of the number. A credit note and a payment both
     * land in the same money column, so without the word the customer cannot
     * tell which of the two took the money off their account.
     */
    fun referenceOn(reference: String, date: String): String = "$reference · $date"
    val columnBalance: String get() = pick("Balance", "ಉಳಿಕೆ")

    // --- The owner's own list of who owes them
    //
    // Worded so it can never be mistaken for a statement. A statement is handed
    // to the person it is about; this names everybody, and is for the owner.

    /**
     * **Receivable**, the word Home already uses for this figure — not "owed".
     * The same money called two things on two screens is the owner wondering
     * whether they are the same money.
     */
    val receivableSummary: String get() = pick("Receivable Amount Summary", "ಬರಬೇಕಾದ ಬಾಕಿ ಸಾರಾಂಶ")
    val payableSummary: String get() = pick("Payable Amount Summary", "ಕೊಡಬೇಕಾದ ಬಾಕಿ ಸಾರಾಂಶ")
    /** The day the list was made. A balance is true at a moment, not over a span. */
    fun asOfDate(date: String): String = pick("As of $date", "$date ರಂತೆ")
    val columnCustomer: String get() = pick("Customer", "ಗ್ರಾಹಕ")
    val totalReceivable: String get() = pick("Total Receivable", "ಒಟ್ಟು ಬರಬೇಕಾದ ಬಾಕಿ")
    val totalPayable: String get() = pick("Total Payable", "ಒಟ್ಟು ಕೊಡಬೇಕಾದ ಬಾಕಿ")
    val nothingReceivable: String get() = pick("Nothing receivable.", "ಬರಬೇಕಾದ ಬಾಕಿ ಇಲ್ಲ.")
    val nothingPayable: String get() = pick("Nothing payable.", "ಕೊಡಬೇಕಾದ ಬಾಕಿ ಇಲ್ಲ.")

    /**
     * The shop's own spending over a stretch of days, broken down by what it
     * went on. Never called a *statement*: that word means one party's account,
     * and an expense is joined to no party at all.
     */
    val expenseSummary: String get() = pick("Expense Summary", "ಖರ್ಚಿನ ಸಾರಾಂಶ")
    val columnWhatItWentOn: String get() = pick("What it went on", "ಯಾವುದಕ್ಕೆ")
    val totalSpentLabel: String get() = pick("Total spent", "ಒಟ್ಟು ಖರ್ಚು")
    val nothingSpentThen: String get() = pick("Nothing spent in this period.", "ಈ ಅವಧಿಯಲ್ಲಿ ಖರ್ಚು ಇಲ್ಲ.")
    /** `once` reads better than `1 times`, and a shop buys plenty of things once. */
    fun timesSpent(n: Int): String = pick(if (n == 1) "once" else "$n times", "$n ಸಲ")
    /** Not translated: a file name is read by a file manager, not by a shopkeeper. */
    fun receivableFileName(date: String): String = "receivable-$date.pdf"
    fun payableFileName(date: String): String = "payable-$date.pdf"
    fun expenseFileName(date: String): String = "expenses-$date.pdf"
    /**
     * The button under every page this app makes: the statement, the receivable
     * and payable lists, the expense summary, the day.
     *
     * **One word for one action, and the true one.** These buttons said "Save
     * list" for a while, which was wrong twice over — nothing is saved until the
     * owner picks somewhere in the chooser, and half of what they open is not a
     * list. It says *PDF* rather than *statement* for the reason the titles do:
     * only one of these pages is a statement.
     */
    val sharePdf: String get() = pick("Share PDF", "PDF ಹಂಚಿಕೊಳ್ಳಿ")

    // --- One day of the shop
    //
    // Every customer billed that day, beside what the shop spent its own money
    // on. The owner's page, exactly as the three above are, and *summary* for
    // the same reason: a statement is one party's account, and this is the
    // whole counter's.

    val daySummary: String get() = pick("Day Summary", "ದಿನದ ಸಾರಾಂಶ")
    /** Section headings. `Bills`, `Received`, `Credit notes` and `Expenses` are already words this app owns. */
    val deliveriesTitle: String get() = pick("Deliveries", "ಡೆಲಿವರಿಗಳು")
    val paidToSuppliers: String get() = pick("Paid to suppliers", "ಸರಬರಾಜುದಾರರಿಗೆ ಪಾವತಿ")
    /** `3 × Padlock 40mm` — a product under the row it was sold on. */
    fun itemLine(qty: Int, name: String): String = "$qty × $name"
    /**
     * What is still owed on a bill or a delivery, said beside it.
     *
     * The page shows what was sold; this is what of it has not been paid for,
     * and without it a busy day on credit reads as a busy day of takings.
     */
    fun onCreditAmount(amount: String): String = pick("$amount on credit", "$amount ಬಾಕಿ")
    /**
     * What the day did to the cash box.
     *
     * *In* is what was taken at the counter plus what came in against older
     * bills — never what was billed. *Out* is what was paid for stock and what
     * was spent. A credit note is in neither: it reduces a debt without a coin
     * moving.
     */
    val moneyInLabel: String get() = pick("Money in", "ಬಂದ ಹಣ")
    val moneyOutLabel: String get() = pick("Money out", "ಹೋದ ಹಣ")
    val netForTheDay: String get() = pick("Net for the day", "ದಿನದ ನಿವ್ವಳ")
    val nothingOnThisDay: String get() = pick("Nothing was recorded on this day.", "ಈ ದಿನ ಯಾವುದೂ ದಾಖಲಾಗಿಲ್ಲ.")
    val previousDay: String get() = pick("Previous day", "ಹಿಂದಿನ ದಿನ")
    val nextDay: String get() = pick("Next day", "ಮುಂದಿನ ದಿನ")
    fun dayFileName(date: String): String = "day-$date.pdf"

    // --- What the trading left the shop with
    //
    // The owner's page and nobody else's: it says what the shop makes. Every
    // line names exactly what it counted, because the one thing this page must
    // never do is flatter — a figure read as the whole truth about a month is
    // worse than no figure at all.

    val earningsSummary: String get() = pick("Earnings Summary", "ಗಳಿಕೆಯ ಸಾರಾಂಶ")
    /** What the goods on the bills cost the shop, as at the day each was sold. */
    val costOfGoods: String get() = pick("Cost of goods", "ಸಾಮಾನಿನ ಬೆಲೆ")
    /** Takings less what the goods cost. Not called profit: rent and wages are not in it. */
    val goodsEarned: String get() = pick("What the goods earned", "ಸಾಮಾನಿನಿಂದ ಬಂದ ಗಳಿಕೆ")
    /** And what was left after the owner's own spending came off. */
    val shopKept: String get() = pick("What the shop kept", "ಅಂಗಡಿಗೆ ಉಳಿದದ್ದು")
    /**
     * Takings the page cannot answer for, because the bill listed no products.
     *
     * Entering a paper bill as one figure is the ordinary way to use this app,
     * so this is not an edge case and is not hidden in a footnote.
     */
    val notCounted: String get() = pick("Not counted", "ಲೆಕ್ಕಕ್ಕೆ ಸಿಗದ್ದು")
    /** What is left of the takings once the uncountable bills are set aside. */
    val countedSales: String get() = pick("Counted", "ಲೆಕ್ಕಕ್ಕೆ ಸಿಕ್ಕಿದ್ದು")
    fun billsAsTotal(n: Int): String =
            pick(
                if (n == 1) "1 bill entered as a total" else "$n bills entered as a total",
                "$n ಬಿಲ್ ಒಟ್ಟು ಮೊತ್ತವಾಗಿ ದಾಖಲು"
            )
    /**
     * Bills that *were* itemised but carry no cost, because they were written
     * before the app kept one.
     *
     * Worded away from [billsAsTotal] on purpose: telling somebody to itemise
     * bills they already itemised is the kind of advice that makes an app feel
     * broken.
     */
    fun billsBeforeCosts(n: Int): String =
            pick(
                if (n == 1) "1 bill written before costs were recorded"
                else "$n bills written before costs were recorded",
                "$n ಬಿಲ್ ಬೆಲೆ ದಾಖಲಿಸುವ ಮೊದಲು ಬರೆದದ್ದು"
            )
    /**
     * Bills counted at today's buying price, because they carry none of their
     * own.
     *
     * Counted rather than set aside — an estimate beats no answer while the old
     * book is most of the history — and named so the owner knows which part of
     * the figure rests on it.
     */
    fun billsEstimated(n: Int): String =
            pick(
                if (n == 1) "1 bill costed at today's prices" else "$n bills costed at today's prices",
                "$n ಬಿಲ್ ಇಂದಿನ ಬೆಲೆಯಲ್ಲಿ ಲೆಕ್ಕ"
            )
    /** Why part of the answer is a guess, said where any of it is. */
    val costsEstimated: String
        get() = pick(
            "Some costs are estimated from today's buying prices, because those bills were written before the app recorded them.",
            "ಕೆಲವು ಬೆಲೆಗಳನ್ನು ಇಂದಿನ ಖರೀದಿ ಬೆಲೆಯಿಂದ ಅಂದಾಜಿಸಲಾಗಿದೆ — ಆ ಬಿಲ್‌ಗಳನ್ನು ಬೆಲೆ ದಾಖಲಿಸುವ ಮೊದಲು ಬರೆಯಲಾಗಿದೆ."
        )
    /**
     * Said when the period has takings but nothing in it can be costed.
     *
     * The state every existing shop is in on the day this arrives, and the one
     * message that matters then: nothing is broken, and the figures fill in from
     * here rather than needing to be fixed.
     */
    val nothingCostableYet: String
        get() = pick(
            "No earnings figure yet — these bills were written before the app recorded what goods cost. Bills from now on will count.",
            "ಇನ್ನೂ ಗಳಿಕೆಯ ಲೆಕ್ಕ ಇಲ್ಲ — ಸಾಮಾನಿನ ಬೆಲೆ ದಾಖಲಿಸುವ ಮೊದಲು ಈ ಬಿಲ್‌ಗಳನ್ನು ಬರೆಯಲಾಗಿದೆ. ಇನ್ನು ಮುಂದಿನ ಬಿಲ್‌ಗಳು ಲೆಕ್ಕಕ್ಕೆ ಬರುತ್ತವೆ."
        )
    /** What was credited back over the period, taken off the earnings. */
    val creditedLabel: String get() = pick("Credited", "ಕ್ರೆಡಿಟ್")
    /**
     * Notes with goods on them that could not be valued, because a line names a
     * product since deleted.
     *
     * Not a figure-only note — one of those hands nothing back, so there is
     * nothing to value and the whole credit comes off correctly.
     */
    fun creditNotesBeforeCosts(n: Int): String =
            pick(
                if (n == 1) "1 credit note whose returned goods could not be valued"
                else "$n credit notes whose returned goods could not be valued",
                "$n ಕ್ರೆಡಿಟ್ ನೋಟ್‌ನ ವಾಪಸಾದ ಸಾಮಾನಿನ ಬೆಲೆ ತಿಳಿದಿಲ್ಲ"
            )
    /** Why that leaves the earnings low rather than merely uncertain. */
    val returnsNotValued: String
        get() = pick(
            "Those goods were put back at nothing, so the earnings above are lower than the truth.",
            "ಆ ಸಾಮಾನನ್ನು ಶೂನ್ಯ ಬೆಲೆಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ, ಹಾಗಾಗಿ ಮೇಲಿನ ಗಳಿಕೆ ನಿಜಕ್ಕಿಂತ ಕಡಿಮೆ."
        )
    val nothingSoldThen: String get() = pick("Nothing sold in this period.", "ಈ ಅವಧಿಯಲ್ಲಿ ಮಾರಾಟ ಇಲ್ಲ.")
    fun earningsFileName(date: String): String = "earnings-$date.pdf"

    fun accountSummaryTill(date: String): String =
            pick("Account summary till $date", "$date ವರೆಗಿನ ಖಾತೆ ಸಾರಾಂಶ")

    fun statementFileName(name: String, date: String): String = "statement-$name-$date.pdf"

    val shopAddress: String get() = pick("Shop address", "ಅಂಗಡಿ ವಿಳಾಸ")
    val shopAddressHint: String
        get() = pick("Street, district, city", "ರಸ್ತೆ, ಬಡಾವಣೆ, ಊರು")
    val shopAddressNote: String
        get() = pick(
            "Printed at the top of a statement, exactly as typed here.",
            "ಖಾತೆ ವಿವರದ ಮೇಲ್ಭಾಗದಲ್ಲಿ, ಇಲ್ಲಿ ಬರೆದ ಹಾಗೆಯೇ ಮುದ್ರಿತವಾಗುತ್ತದೆ."
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
    val restoreFromBackup: String get() = pick("Restore from a backup file", "ಬ್ಯಾಕಪ್ ಫೈಲ್‌ನಿಂದ ಶುರು ಮಾಡಿ")
    val useThisBackup: String get() = pick("Use this backup", "ಈ ಬ್ಯಾಕಪ್ ಬಳಸಿ")

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


    // --- The owner's own spending

    val expensesTitle: String get() = pick("Expenses", "ಖರ್ಚುಗಳು")
    val addAnExpense: String get() = pick("Add an expense", "ಖರ್ಚು ಸೇರಿಸಿ")
    val newExpense: String get() = pick("New expense", "ಹೊಸ ಖರ್ಚು")
    val editExpense: String get() = pick("Edit expense", "ಖರ್ಚು ಬದಲಿಸಿ")

    /** The field label. A question, because the answer is a phrase not a category. */
    val expenseWhatFor: String get() = pick("What was it for?", "ಯಾವುದಕ್ಕೆ?")
    val expenseWhatForHint: String get() = pick("Petrol, tea, rent…", "ಪೆಟ್ರೋಲ್, ಚಹಾ, ಬಾಡಿಗೆ…")
    val expenseSpentOn: String get() = pick("Spent on", "ಖರ್ಚು ಮಾಡಿದ ದಿನ")

    val noExpensesYet: String get() = pick("Nothing written down yet", "ಇನ್ನೂ ಏನೂ ಬರೆದಿಲ್ಲ")

    /**
     * The figure over a stretch of days — the card above the list, and the line
     * beside Sold on Today. Says *expense*, never *profit*: this ledger is
     * beside the shop's figures and never inside them.
     *
     * One word in both places on purpose. Two names for one number invites the
     * owner to wonder whether they are the same number.
     */
    val expenseInPeriod: String get() = pick("Expense", "ಖರ್ಚು")

    val saveExpense: String get() = pick("Save expense", "ಖರ್ಚು ಉಳಿಸಿ")
    val enterWhatItWasFor: String get() = pick("Say what it was for", "ಯಾವುದಕ್ಕೆ ಎಂದು ಬರೆಯಿರಿ")
    val removeExpense: String get() = pick("Remove this expense", "ಈ ಖರ್ಚು ತೆಗೆದುಹಾಕಿ")

    /** Said before removing one. Short, because nothing else depends on it. */
    val removeExpenseNote: String
        get() = pick(
            "It disappears from the list. Nothing else changes.",
            "ಪಟ್ಟಿಯಿಂದ ಮಾಯವಾಗುತ್ತದೆ. ಬೇರೇನೂ ಬದಲಾಗುವುದಿಲ್ಲ."
        )

    /**
     * The line under the total, said once on the screen so nobody has to wonder.
     */
    val expensesArePrivate: String
        get() = pick(
            "Yours alone — never on a bill or a statement.",
            "ನಿಮಗಷ್ಟೇ — ಬಿಲ್ ಅಥವಾ ಖಾತೆ ವಿವರದಲ್ಲಿ ಬರುವುದಿಲ್ಲ."
        )

    // --- Dates

    // `TUESDAY, 11 AUGUST` — uppercased by the `.kicker` type role.
    fun headerDate(date: java.time.Instant): String = Dates.headerDate(date, language.locale)

    // `09:41` — always 24-hour, in both languages.
    fun time(date: java.time.Instant): String = Dates.time(date)

    // `28 July 2026`
    fun longDate(date: java.time.Instant): String = Dates.longDate(date, language.locale)

    /** `19/05/2026`, for the statement table's narrow date column. */
    fun shortDate(date: java.time.Instant): String = Dates.shortDate(date)

    /** `Aug 13, 2026` — the date shown in a date field the owner can change. */
    fun pickedDate(date: java.time.Instant): String = Dates.pickedDate(date, language.locale)

}
