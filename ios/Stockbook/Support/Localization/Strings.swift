import Foundation

/// Every user-facing string in Stockbook, in both languages, side by side.
///
/// There is no `.strings` file and no key lookup. A missing translation is a
/// compile error rather than an English word appearing on a Kannada screen, and
/// a reviewer can read both languages of a sentence on one line without holding
/// a key in their head.
///
/// Rules for anything added here:
/// - **No sentence built by joining fragments.** Word order differs between the
///   two languages, so every phrase with a number or a name in it is one
///   function taking that number or name — never `Loc.only + n + Loc.inStock`.
/// - **Counts are written out, not pluralised by rule.** Kannada does not add
///   an "s", and a shared pluraliser only ever produces one language correctly.
struct Strings {
    let language: AppLanguage

    private func pick(_ english: String, _ kannada: String) -> String {
        switch language {
        case .english: english
        case .kannada: kannada
        }
    }

    // MARK: - Counted nouns

    func products(_ n: Int) -> String {
        pick(n == 1 ? "1 product" : "\(n) products", "\(n) ಸಾಮಾನು")
    }

    func bills(_ n: Int) -> String {
        pick(n == 1 ? "1 bill" : "\(n) bills", "\(n) ಬಿಲ್")
    }

    func lines(_ n: Int) -> String {
        pick(n == 1 ? "1 line" : "\(n) lines", "\(n) ಸಾಲು")
    }

    func purchases(_ n: Int) -> String {
        pick(n == 1 ? "1 purchase" : "\(n) purchases", "\(n) ಖರೀದಿ")
    }

    func items(_ n: Int) -> String {
        pick(n == 1 ? "1 item" : "\(n) items", "\(n) ಸಾಮಾನು")
    }

    func pieces(_ n: Int) -> String {
        pick(n == 1 ? "1 piece" : "\(n) pieces", "\(n) ನಗ")
    }

    func customers(_ n: Int) -> String {
        pick(n == 1 ? "1 customer" : "\(n) customers", "\(n) ಗ್ರಾಹಕರು")
    }

    // MARK: - Shared actions

    var done: String { pick("Done", "ಆಯಿತು") }
    var save: String { pick("Save", "ಉಳಿಸಿ") }
    var cancel: String { pick("Cancel", "ರದ್ದು") }
    var add: String { pick("Add", "ಸೇರಿಸಿ") }
    var all: String { pick("All", "ಎಲ್ಲಾ") }
    var share: String { pick("Share", "ಹಂಚಿಕೊಳ್ಳಿ") }
    var back: String { pick("Back", "ಹಿಂದೆ") }
    var continueAction: String { pick("Continue", "ಮುಂದೆ") }
    /// The keyboard toolbar, when there is another box after this one.
    var next: String { pick("Next", "ಮುಂದಿನದು") }
    var startABill: String { pick("Start a bill", "ಬಿಲ್ ಶುರು ಮಾಡಿ") }
    var addAProduct: String { pick("Add a product", "ಸಾಮಾನು ಸೇರಿಸಿ") }

    func remove(_ name: String) -> String {
        pick("Remove \(name)", "\(name) ತೆಗೆದುಹಾಕಿ")
    }

    // MARK: - Tabs

    func tab(_ tab: AppTab) -> String {
        switch tab {
        case .today: pick("Today", "ಇಂದು")
        case .items: pick("Items", "ಸಾಮಾನು")
        case .sell: pick("Sell", "ಮಾರಾಟ")
        case .bills: pick("Bills", "ಬಿಲ್")
        }
    }

    // MARK: - Today

    var today: String { pick("Today", "ಇಂದು") }
    var settings: String { pick("Settings", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು") }
    var soldToday: String { pick("Sold today", "ಇಂದಿನ ಮಾರಾಟ") }
    var billsStat: String { pick("Bills", "ಬಿಲ್‌ಗಳು") }
    var recentBills: String { pick("Recent bills", "ಇತ್ತೀಚಿನ ಬಿಲ್‌ಗಳು") }
    var noBillsToday: String { pick("No bills yet today.", "ಇಂದು ಇನ್ನೂ ಒಂದೂ ಬಿಲ್ ಆಗಿಲ್ಲ.") }
    var saveFile: String { pick("Save file", "ಫೈಲ್ ಉಳಿಸಿ") }

    func greeting(_ firstName: String) -> String {
        pick("Hello, \(firstName)", "ನಮಸ್ಕಾರ, \(firstName)")
    }

    /// One name reads as a name; several read as a count of **people**.
    func stillOwes(oneName name: String) -> String {
        pick("\(name) still owes", "\(name) ಇನ್ನೂ ಬಾಕಿ ಕೊಡಬೇಕು")
    }

    func stillOwe(customerCount n: Int) -> String {
        pick("\(customers(n)) still owe", "\(n) ಗ್ರಾಹಕರು ಇನ್ನೂ ಬಾಕಿ ಕೊಡಬೇಕು")
    }

    var backupWrittenNote: String {
        pick(
            "Backup written. Copy it somewhere safe — everything lives on this phone only.",
            "ಬ್ಯಾಕಪ್ ಆಗಿದೆ. ಅದನ್ನು ಸುರಕ್ಷಿತ ಜಾಗಕ್ಕೆ ನಕಲಿಸಿ — ಎಲ್ಲಾ ಮಾಹಿತಿ ಈ ಫೋನಿನಲ್ಲಿ ಮಾತ್ರ ಇದೆ."
        )
    }

    var backupMissingNote: String {
        pick(
            "Nothing backed up yet. Everything lives on this phone only.",
            "ಇನ್ನೂ ಬ್ಯಾಕಪ್ ಆಗಿಲ್ಲ. ಎಲ್ಲಾ ಮಾಹಿತಿ ಈ ಫೋನಿನಲ್ಲಿ ಮಾತ್ರ ಇದೆ."
        )
    }

    // MARK: - Items

    var itemsTitle: String { pick("Items", "ಸಾಮಾನುಗಳು") }
    var search: String { pick("Search", "ಹುಡುಕಿ") }
    var nothingAddedYet: String { pick("nothing added yet", "ಇನ್ನೂ ಏನೂ ಸೇರಿಸಿಲ್ಲ") }

    func itemsSubtitle(total: Int, low: Int) -> String {
        pick("\(products(total)) · \(low) running low", "\(total) ಸಾಮಾನು · \(low) ಕಡಿಮೆ ಆಗುತ್ತಿದೆ")
    }

    var shelfEmpty: String {
        pick(
            "Nothing on the shelf yet. Add your first product.",
            "ಇನ್ನೂ ಯಾವ ಸಾಮಾನೂ ಇಲ್ಲ. ಮೊದಲ ಸಾಮಾನು ಸೇರಿಸಿ."
        )
    }

    func nothingMatches(_ query: String) -> String {
        pick("Nothing matches “\(query)”.", "“\(query)” ಗೆ ಏನೂ ಸಿಗಲಿಲ್ಲ.")
    }

    func buyAndMargin(cost: String, margin: String) -> String {
        pick("buy \(cost) · you make \(margin)", "ಖರೀದಿ \(cost) · ಲಾಭ \(margin)")
    }

    /// `out of stock` / `12 pc` — the shelf count wherever it is shown.
    func stockLabel(_ stock: Int) -> String {
        stock == 0
            ? pick("out of stock", "ಸ್ಟಾಕ್ ಇಲ್ಲ")
            : pick("\(stock) pc", "\(stock) ನಗ")
    }

    // MARK: - Bills

    var billsTitle: String { pick("Bills", "ಬಿಲ್‌ಗಳು") }

    var noBillsEver: String {
        pick(
            "Nothing sold yet. Every bill you save shows up here.",
            "ಇನ್ನೂ ಏನೂ ಮಾರಾಟ ಆಗಿಲ್ಲ. ನೀವು ಉಳಿಸಿದ ಪ್ರತಿ ಬಿಲ್ ಇಲ್ಲಿ ಕಾಣಿಸುತ್ತದೆ."
        )
    }

    var voidAndRestock: String {
        pick("Void & put stock back", "ರದ್ದು ಮಾಡಿ, ದಾಸ್ತಾನು ವಾಪಸ್")
    }

    var voided: String { pick("voided", "ರದ್ದಾಗಿದೆ") }

    func owes(_ amount: String) -> String {
        pick("owes \(amount)", "\(amount) ಬಾಕಿ")
    }

    var allCustomers: String { pick("All customers", "ಎಲ್ಲಾ ಗ್ರಾಹಕರು") }
    var customerLabel: String { pick("Customer", "ಗ್ರಾಹಕ") }
    var transactions: String { pick("Bought", "ಖರೀದಿಸಿದ್ದು") }
    var pendingPayment: String { pick("Pending", "ಬಾಕಿ") }
    var nothingPending: String { pick("Nothing", "ಏನೂ ಇಲ್ಲ") }

    /// Somebody who has paid ahead. The mirror of `owes`, and worth saying
    /// plainly rather than showing a negative amount the owner has to interpret.
    func inAdvance(_ amount: String) -> String {
        pick("\(amount) in advance", "\(amount) ಮುಂಗಡ")
    }

    // MARK: - Customers

    var customersTitle: String { pick("Customers", "ಗ್ರಾಹಕರು") }
    var addACustomer: String { pick("Add a customer", "ಗ್ರಾಹಕರನ್ನು ಸೇರಿಸಿ") }
    var newCustomer: String { pick("New customer", "ಹೊಸ ಗ್ರಾಹಕ") }
    var editCustomer: String { pick("Edit customer", "ಗ್ರಾಹಕರ ವಿವರ ಬದಲಿಸಿ") }
    var customerPhone: String { pick("Phone", "ಫೋನ್") }
    var customerPlace: String { pick("Place", "ಸ್ಥಳ") }
    var optionalField: String { pick("Optional", "ಬೇಕಿದ್ದರೆ") }
    var saveCustomer: String { pick("Save customer", "ಗ್ರಾಹಕರನ್ನು ಉಳಿಸಿ") }
    var removeFromCustomers: String { pick("Remove from customers", "ಗ್ರಾಹಕರ ಪಟ್ಟಿಯಿಂದ ತೆಗೆಯಿರಿ") }
    var tapAgainToRemove: String { pick("Tap again to remove", "ತೆಗೆಯಲು ಇನ್ನೊಮ್ಮೆ ಒತ್ತಿ") }
    var noBillsYet: String { pick("No bills yet", "ಇನ್ನೂ ಬಿಲ್ ಇಲ್ಲ") }
    var openingBalanceField: String { pick("Opening balance", "ಪ್ರಾರಂಭಿಕ ಬಾಕಿ") }

    /// The save gate when a name has been typed but nobody picked. Says what to
    /// do, not what went wrong.
    ///
    /// Added alongside the Android picker so the two tables stay identical. The
    /// iOS cart still takes free text and will use these when the picker is
    /// brought over.
    var chooseFromTheList: String { pick("Choose a customer from the list", "ಪಟ್ಟಿಯಿಂದ ಗ್ರಾಹಕರನ್ನು ಆರಿಸಿ") }

    /// The way a customer who is not on the roster yet gets onto it, without
    /// leaving the bill.
    func addAsCustomer(_ name: String) -> String {
        pick("Add “\(name)” as a customer", "“\(name)” ಅನ್ನು ಗ್ರಾಹಕರಾಗಿ ಸೇರಿಸಿ")
    }

    /// Says which balance this is, because the statement has one of its own with
    /// a different meaning — that one is derived, this one is typed in once.
    // MARK: Suppliers
    //
    // The customer block above, pointing the other way. Every line here has a
    // counterpart there, and the words differ only where the direction of the
    // money does: a supplier is somebody *the shop* owes.

    var suppliersTitle: String { pick("Suppliers", "ಪೂರೈಕೆದಾರರು") }
    var allSuppliers: String { pick("All suppliers", "ಎಲ್ಲಾ ಪೂರೈಕೆದಾರರು") }
    var addASupplier: String { pick("Add a supplier", "ಪೂರೈಕೆದಾರರನ್ನು ಸೇರಿಸಿ") }
    var newSupplier: String { pick("New supplier", "ಹೊಸ ಪೂರೈಕೆದಾರ") }
    var editSupplier: String { pick("Edit supplier", "ಪೂರೈಕೆದಾರರ ವಿವರ ಬದಲಿಸಿ") }
    var saveSupplier: String { pick("Save supplier", "ಪೂರೈಕೆದಾರರನ್ನು ಉಳಿಸಿ") }
    var removeFromSuppliers: String { pick("Remove from suppliers", "ಪೂರೈಕೆದಾರರ ಪಟ್ಟಿಯಿಂದ ತೆಗೆಯಿರಿ") }
    var supplierNameExample: String { pick("Al Faisal Hardware", "ಅಲ್ ಫೈಸಲ್ ಹಾರ್ಡ್‌ವೇರ್") }
    var noPurchasesYet: String { pick("No purchases yet", "ಇನ್ನೂ ಖರೀದಿ ಇಲ್ಲ") }
    var purchaseLabel: String { pick("Purchase", "ಖರೀದಿ") }
    var boughtFromThem: String { pick("Bought", "ಖರೀದಿಸಿದ್ದು") }

    /// What the shop owes, as opposed to what it is owed. Deliberately not the
    /// same word as a customer's "Pending", because a shop owner reading a screen
    /// fast needs the two totals to be tellable apart at a glance.
    var youOwe: String { pick("You owe", "ನೀವು ಕೊಡಬೇಕು") }
    var owedToSuppliers: String { pick("Owed to suppliers", "ಪೂರೈಕೆದಾರರಿಗೆ ಬಾಕಿ") }
    var nothingOwedOut: String { pick("Nothing owed out", "ಕೊಡಬೇಕಾದ್ದು ಏನೂ ಇಲ್ಲ") }

    var chooseSupplierFromTheList: String {
        pick("Choose a supplier from the list", "ಪಟ್ಟಿಯಿಂದ ಪೂರೈಕೆದಾರರನ್ನು ಆರಿಸಿ")
    }

    func addAsSupplier(_ name: String) -> String {
        pick("Add “\(name)” as a supplier", "“\(name)” ಅನ್ನು ಪೂರೈಕೆದಾರರಾಗಿ ಸೇರಿಸಿ")
    }

    /// A delivery entered wrongly is voided rather than edited, exactly as a bill
    /// is — and voiding it takes the stock back off the shelf, which the button
    /// says out loud because it is the part that surprises people.
    var voidAndRemoveStock: String { pick("Void and remove stock", "ರದ್ದು ಮಾಡಿ ದಾಸ್ತಾನು ಹಿಂತೆಗೆಯಿರಿ") }
    var purchaseVoidedNote: String {
        pick("Voided. The stock went back off the shelf.", "ರದ್ದಾಗಿದೆ. ದಾಸ್ತಾನು ಹಿಂತೆಗೆಯಲಾಗಿದೆ.")
    }

    var openingBalanceNote: String {
        pick(
            "What they already owed you before Stockbook, from the old book. Leave it empty if nothing.",
            "ಸ್ಟಾಕ್‌ಬುಕ್ ಶುರು ಮಾಡುವ ಮೊದಲು ಹಳೆಯ ಪುಸ್ತಕದಲ್ಲಿ ಅವರು ಕೊಡಬೇಕಿದ್ದ ಮೊತ್ತ. ಏನೂ ಇಲ್ಲದಿದ್ದರೆ ಖಾಲಿ ಬಿಡಿ."
        )
    }
    var enterCustomerNameFirst: String { pick("Enter a name", "ಹೆಸರು ಬರೆಯಿರಿ") }

    /// Said when removing a roster entry, because "remove" beside somebody's
    /// name reads like deleting them and their history.
    var removeCustomerNote: String {
        pick(
            "Their bills stay. This only takes them off the customer list.",
            "ಅವರ ಬಿಲ್‌ಗಳು ಹಾಗೇ ಉಳಿಯುತ್ತವೆ. ಇದು ಅವರನ್ನು ಗ್ರಾಹಕರ ಪಟ್ಟಿಯಿಂದ ಮಾತ್ರ ತೆಗೆಯುತ್ತದೆ."
        )
    }

    // MARK: - Payments

    var recordAPayment: String { pick("Record a payment", "ಪಾವತಿ ದಾಖಲಿಸಿ") }
    var amountReceived: String { pick("Amount received", "ಸ್ವೀಕರಿಸಿದ ಮೊತ್ತ") }
    var receivedOn: String { pick("Received on", "ಸ್ವೀಕರಿಸಿದ ದಿನ") }
    var paymentNote: String { pick("Note", "ಟಿಪ್ಪಣಿ") }
    var paymentNoteExample: String { pick("cash, cheque…", "ನಗದು, ಚೆಕ್…") }
    var savePayment: String { pick("Save payment", "ಪಾವತಿ ಉಳಿಸಿ") }
    var paymentLabel: String { pick("Payment", "ಪಾವತಿ") }
    var deleteThisPayment: String { pick("Delete this payment", "ಈ ಪಾವತಿಯನ್ನು ಅಳಿಸಿ") }
    var enterAnAmount: String { pick("Enter an amount", "ಮೊತ್ತ ಬರೆಯಿರಿ") }

    /// The one thing to know about payments in this app.
    var paymentNotAgainstOneBill: String {
        pick(
            "Recorded against the customer, not one bill — the way money actually arrives at a counter.",
            "ಒಂದು ಬಿಲ್‌ಗೆ ಅಲ್ಲ, ಗ್ರಾಹಕರ ಖಾತೆಗೆ ದಾಖಲಾಗುತ್ತದೆ — ಕೌಂಟರಿನಲ್ಲಿ ಹಣ ಬರುವುದು ಹಾಗೆಯೇ."
        )
    }

    // MARK: - Statement

    var statement: String { pick("Statement", "ಖಾತೆ ವಿವರ") }
    var thisMonth: String { pick("This month", "ಈ ತಿಂಗಳು") }
    var lastMonth: String { pick("Last month", "ಕಳೆದ ತಿಂಗಳು") }
    var thisYear: String { pick("This year", "ಈ ವರ್ಷ") }
    var chooseDates: String { pick("Choose dates", "ದಿನಾಂಕ ಆರಿಸಿ") }
    var fromDate: String { pick("From", "ಇಂದ") }
    var toDate: String { pick("To", "ವರೆಗೆ") }
    var openingBalance: String { pick("Brought forward", "ಹಿಂದಿನ ಬಾಕಿ") }
    var billedInPeriod: String { pick("Billed", "ಬಿಲ್ ಮಾಡಿದ್ದು") }
    var receivedInPeriod: String { pick("Received", "ಸ್ವೀಕರಿಸಿದ್ದು") }

    /// The same two figures on a supplier's statement. "Billed" and "Received"
    /// are the customer's words and read backwards on a delivery note.
    var purchasedInPeriod: String { pick("Purchased", "ಖರೀದಿಸಿದ್ದು") }
    var paidOutInPeriod: String { pick("Paid", "ಪಾವತಿಸಿದ್ದು") }
    var closingBalance: String { pick("Balance due", "ಉಳಿದ ಬಾಕಿ") }
    var nothingInThisPeriod: String { pick("Nothing in this period", "ಈ ಅವಧಿಯಲ್ಲಿ ಏನೂ ಇಲ್ಲ") }
    var settledUp: String { pick("Settled up", "ಎಲ್ಲಾ ಪಾವತಿ ಆಗಿದೆ") }

    /// `28 July 2026 to 27 August 2026` — the span a statement covers, written
    /// out because a date range abbreviated with a dash is read wrong often
    /// enough to matter on a document somebody may hand to a customer.
    func dateSpan(from: String, to: String) -> String {
        pick("\(from) to \(to)", "\(from) ಇಂದ \(to) ವರೆಗೆ")
    }

    // MARK: - Sell

    var newBill: String { pick("New bill", "ಹೊಸ ಬಿಲ್") }
    var cartEmpty: String { pick("empty", "ಖಾಲಿ") }
    var addAProductPlaceholder: String { pick("Add a product…", "ಸಾಮಾನು ಸೇರಿಸಿ…") }
    var doneAdding: String { pick("Done adding", "ಸೇರಿಸಿ ಆಯಿತು") }
    var addAnotherItem: String { pick("Add another item", "ಇನ್ನೊಂದು ಸಾಮಾನು ಸೇರಿಸಿ") }
    var oneFewer: String { pick("One fewer", "ಒಂದು ಕಡಿಮೆ") }
    var oneMore: String { pick("One more", "ಒಂದು ಹೆಚ್ಚು") }

    func matchingQuery(_ query: String) -> String {
        pick("Matching “\(query)”", "“\(query)” ಗೆ ಹೊಂದುವುದು")
    }

    func allProductsHint(_ n: Int) -> String {
        pick("All \(n) products — tap to add", "ಎಲ್ಲಾ \(n) ಸಾಮಾನು — ಸೇರಿಸಲು ಒತ್ತಿ")
    }

    func noProductMatches(_ query: String) -> String {
        pick("No product matches “\(query)”.", "“\(query)” ಗೆ ಯಾವ ಸಾಮಾನೂ ಸಿಗಲಿಲ್ಲ.")
    }

    var noProductsYet: String {
        pick("You haven't added any products yet.", "ನೀವು ಇನ್ನೂ ಯಾವ ಸಾಮಾನೂ ಸೇರಿಸಿಲ್ಲ.")
    }

    func onBillAccessibility(name: String, quantity: Int) -> String {
        pick("\(name), \(quantity) on the bill", "\(name), ಬಿಲ್‌ನಲ್ಲಿ \(quantity)")
    }

    // MARK: - Cart

    func onlyInStock(_ stock: Int) -> String {
        pick("only \(stock) in stock", "ಸ್ಟಾಕ್‌ನಲ್ಲಿ \(stock) ಮಾತ್ರ")
    }

    func piecesInStock(_ stock: Int) -> String {
        pick("pieces · \(stock) in stock", "ನಗ · ಸ್ಟಾಕ್‌ನಲ್ಲಿ \(stock)")
    }

    func usualPriceNote(_ price: String) -> String {
        pick(
            "Usual price \(price) — changed for this bill only",
            "ಎಂದಿನ ಬೆಲೆ \(price) — ಈ ಬಿಲ್‌ಗೆ ಮಾತ್ರ ಬದಲಾಗಿದೆ"
        )
    }

    var reset: String { pick("Reset", "ಮೊದಲಿನಂತೆ") }
    var customerName: String { pick("Customer name", "ಗ್ರಾಹಕರ ಹೆಸರು") }
    var paidInFull: String { pick("Paid in full", "ಪೂರ್ತಿ ಪಾವತಿ") }
    var partPayment: String { pick("Part payment", "ಭಾಗಶಃ ಪಾವತಿ") }
    var paidNow: String { pick("Paid now", "ಈಗ ಕೊಟ್ಟದ್ದು") }
    var total: String { pick("Total", "ಒಟ್ಟು") }
    var balance: String { pick("Balance", "ಬಾಕಿ") }
    var saveBill: String { pick("Save bill", "ಬಿಲ್ ಉಳಿಸಿ") }
    var enterCustomerName: String { pick("Enter a customer name", "ಗ್ರಾಹಕರ ಹೆಸರು ಬರೆಯಿರಿ") }

    // MARK: - Receipt

    var billSaved: String { pick("Bill saved", "ಬಿಲ್ ಉಳಿಸಲಾಗಿದೆ") }
    var billDetailTitle: String { pick("Bill details", "ಬಿಲ್ ವಿವರ") }
    var seeBills: String { pick("See bills", "ಬಿಲ್‌ಗಳನ್ನು ನೋಡಿ") }
    var nextCustomer: String { pick("Next customer", "ಮುಂದಿನ ಗ್ರಾಹಕರು") }

    func billNumber(_ number: Int) -> String {
        pick("Bill #\(number)", "ಬಿಲ್ #\(number)")
    }

    func billWhen(date: String, time: String) -> String {
        pick("\(date) · \(time)", "\(date) · \(time)")
    }

    func billedTo(_ name: String) -> String {
        pick("Billed to \(name)", "\(name) ಅವರಿಗೆ")
    }

    /// `2 × SAR 95` — the arithmetic behind a line, kept visible.
    func quantityAtPrice(quantity: Int, price: String) -> String {
        pick("\(quantity) × \(price)", "\(quantity) × \(price)")
    }

    var voidedNote: String {
        pick(
            "This bill was voided. The stock went back on the shelf and nothing is owed on it.",
            "ಈ ಬಿಲ್ ರದ್ದಾಗಿದೆ. ದಾಸ್ತಾನು ವಾಪಸ್ ಹೋಗಿದೆ, ಇದರ ಮೇಲೆ ಯಾವ ಬಾಕಿಯೂ ಇಲ್ಲ."
        )
    }

    var paidInFullCash: String { pick("Paid in full, cash.", "ಪೂರ್ತಿ ಪಾವತಿ, ನಗದು.") }

    func partPaidNote(paid: String, who: String, balance: String) -> String {
        pick(
            "Paid \(paid) · \(who) owes \(balance)",
            "\(paid) ಕೊಟ್ಟಿದ್ದಾರೆ · \(who) ಅವರಿಂದ \(balance) ಬಾಕಿ"
        )
    }

    // MARK: - Product editor

    var newProduct: String { pick("New product", "ಹೊಸ ಸಾಮಾನು") }
    var editProduct: String { pick("Edit product", "ಸಾಮಾನು ಬದಲಿಸಿ") }
    var productName: String { pick("Product name", "ಸಾಮಾನಿನ ಹೆಸರು") }
    var productNameExample: String { pick("e.g. 4 inch hinge", "ಉದಾ. 4 ಇಂಚಿನ ಹಿಂಜ್") }
    var inStock: String { pick("In stock", "ದಾಸ್ತಾನು") }
    var buyingPrice: String { pick("Buying price", "ಖರೀದಿ ಬೆಲೆ") }
    var sellingPrice: String { pick("Selling price", "ಮಾರಾಟ ಬೆಲೆ") }
    var addStock: String { pick("Add stock", "ದಾಸ್ತಾನು ಸೇರಿಸಿ") }
    var removeThisProduct: String { pick("Remove this product", "ಈ ಸಾಮಾನನ್ನು ತೆಗೆದುಹಾಕಿ") }

    var setPriceAboveCost: String {
        pick(
            "Set a selling price above the buying price.",
            "ಖರೀದಿ ಬೆಲೆಗಿಂತ ಹೆಚ್ಚಿನ ಮಾರಾಟ ಬೆಲೆ ಹಾಕಿ."
        )
    }

    func youMakeAPiece(_ margin: String) -> String {
        pick("You make \(margin) a piece.", "ಪ್ರತಿ ನಗಕ್ಕೆ \(margin) ಲಾಭ.")
    }

    // MARK: - Add stock

    func onShelfNow(product: String, stock: Int) -> String {
        pick(
            "\(product) — \(pieces(stock)) on the shelf now",
            "\(product) — ಈಗ ಅಂಗಡಿಯಲ್ಲಿ \(stock) ನಗ"
        )
    }

    var quickAdd: String { pick("Quick add", "ಬೇಗ ಸೇರಿಸಿ") }
    var purchaseEntry: String { pick("Purchase entry", "ಖರೀದಿ ನಮೂದು") }
    var supplier: String { pick("Supplier", "ಸರಬರಾಜುದಾರ") }
    var whoDeliveredIt: String { pick("Who delivered it", "ಯಾರು ತಂದುಕೊಟ್ಟರು") }
    var howMany: String { pick("How many", "ಎಷ್ಟು") }
    var paidPerPiece: String { pick("Paid per piece", "ಪ್ರತಿ ನಗಕ್ಕೆ ಕೊಟ್ಟದ್ದು") }
    var recordPurchase: String { pick("Record purchase", "ಖರೀದಿ ದಾಖಲಿಸಿ") }

    func addToStock(_ quantity: Int) -> String {
        pick("Add \(quantity) to stock", "ದಾಸ್ತಾನಿಗೆ \(quantity) ಸೇರಿಸಿ")
    }

    func purchaseNote(billTotal: String) -> String {
        pick(
            "Bill total \(billTotal). This becomes the buying price used from now on.",
            "ಬಿಲ್ ಒಟ್ಟು \(billTotal). ಇನ್ನು ಮುಂದೆ ಇದೇ ಖರೀದಿ ಬೆಲೆ ಆಗುತ್ತದೆ."
        )
    }

    func quickAddNote(cost: String) -> String {
        pick(
            "Topping up the bin. Buying price stays at \(cost).",
            "ದಾಸ್ತಾನು ತುಂಬಿಸುತ್ತಿದ್ದೀರಿ. ಖರೀದಿ ಬೆಲೆ \(cost) ಆಗಿಯೇ ಇರುತ್ತದೆ."
        )
    }

    // MARK: - Setup

    var welcomeToStockbook: String { pick("Welcome to Stockbook", "ಸ್ಟಾಕ್‌ಬುಕ್‌ಗೆ ಸ್ವಾಗತ") }

    var welcomeBody: String {
        pick(
            "Everything stays on this phone — no account, no signal needed. First, what should we call you?",
            "ಎಲ್ಲವೂ ಈ ಫೋನಿನಲ್ಲಿಯೇ ಇರುತ್ತದೆ — ಖಾತೆ ಬೇಡ, ನೆಟ್‌ವರ್ಕ್ ಬೇಡ. ಮೊದಲು, ನಿಮ್ಮನ್ನು ಏನೆಂದು ಕರೆಯೋಣ?"
        )
    }

    var yourName: String { pick("Your name", "ನಿಮ್ಮ ಹೆಸರು") }
    var businessOwnerName: String { pick("Business owner name", "ಅಂಗಡಿ ಮಾಲೀಕರ ಹೆಸರು") }
    var yourShelves: String { pick("Your shelves", "ನಿಮ್ಮ ಅಂಗಡಿ") }
    var whatDoYouStock: String { pick("What do you stock?", "ನೀವು ಏನು ಮಾರುತ್ತೀರಿ?") }

    var stockNamesBody: String {
        pick(
            "Names only for now. Prices and counts come next, and you can add or remove items any time after.",
            "ಸದ್ಯಕ್ಕೆ ಹೆಸರುಗಳು ಮಾತ್ರ. ಬೆಲೆ ಮತ್ತು ಎಣಿಕೆ ಮುಂದಿನ ಹಂತದಲ್ಲಿ. ಆಮೇಲೆ ಯಾವಾಗ ಬೇಕಾದರೂ ಸೇರಿಸಬಹುದು, ತೆಗೆಯಬಹುದು."
        )
    }

    var commonHardwareLines: String { pick("Common hardware lines", "ಸಾಮಾನ್ಯ ಹಾರ್ಡ್‌ವೇರ್ ಸಾಮಾನು") }
    var nothingAddedYetKicker: String { pick("Nothing added yet", "ಇನ್ನೂ ಏನೂ ಸೇರಿಸಿಲ್ಲ") }

    func addedCount(_ n: Int) -> String {
        pick("Added · \(n)", "ಸೇರಿಸಿದ್ದು · \(n)")
    }

    var nextStockAndPrices: String { pick("Next — stock & prices", "ಮುಂದೆ — ದಾಸ್ತಾನು ಮತ್ತು ಬೆಲೆ") }
    var stockAndPrices: String { pick("Stock and prices", "ದಾಸ್ತಾನು ಮತ್ತು ಬೆಲೆ") }

    var stockAndPricesBody: String {
        pick(
            "All three are needed for every item — the count on the shelf, what you paid, what you charge.",
            "ಪ್ರತಿ ಸಾಮಾನಿಗೂ ಮೂರೂ ಬೇಕು — ಅಂಗಡಿಯಲ್ಲಿ ಎಷ್ಟಿದೆ, ನೀವು ಎಷ್ಟು ಕೊಟ್ಟಿರಿ, ಎಷ್ಟಕ್ಕೆ ಮಾರುತ್ತೀರಿ."
        )
    }

    var youPay: String { pick("You pay", "ನೀವು ಕೊಡುವುದು") }
    var youSell: String { pick("You sell", "ನೀವು ಮಾರುವುದು") }
    var nextCustomers: String { pick("Next — your customers", "ಮುಂದೆ — ನಿಮ್ಮ ಗ್ರಾಹಕರು") }
    var yourCustomers: String { pick("Your customers", "ನಿಮ್ಮ ಗ್ರಾಹಕರು") }
    var whoDoYouSellTo: String { pick("Who buys on account?", "ಯಾರು ಖಾತೆಯಲ್ಲಿ ಖರೀದಿಸುತ್ತಾರೆ?") }

    /// Says out loud that this step is skippable, because a setup screen that
    /// looks compulsory is where an owner gives up and types nonsense.
    var customersSetupBody: String {
        pick(
            "The regulars who pay later, so their names are ready at the counter and you can print a statement. Skip this — you can add anybody while writing a bill.",
            "ನಂತರ ಪಾವತಿಸುವ ನಿಯಮಿತ ಗ್ರಾಹಕರು — ಕೌಂಟರಿನಲ್ಲಿ ಹೆಸರು ಸಿದ್ಧವಿರುತ್ತದೆ ಮತ್ತು ಖಾತೆ ವಿವರ ತೆಗೆಯಬಹುದು. ಇದನ್ನು ಬಿಟ್ಟುಬಿಡಬಹುದು — ಬಿಲ್ ಬರೆಯುವಾಗಲೂ ಯಾರನ್ನಾದರೂ ಸೇರಿಸಬಹುದು."
        )
    }

    var customerNameExample: String { pick("Ahmed Contracting", "ಅಹ್ಮದ್ ಕಂಟ್ರಾಕ್ಟಿಂಗ್") }
    var noCustomersYetKicker: String { pick("Nobody added yet", "ಇನ್ನೂ ಯಾರನ್ನೂ ಸೇರಿಸಿಲ್ಲ") }

    var openTheShop: String { pick("Open the shop", "ಅಂಗಡಿ ತೆರೆಯಿರಿ") }

    var allSet: String {
        pick(
            "All set — stock and both prices filled in.",
            "ಎಲ್ಲಾ ಸಿದ್ಧ — ದಾಸ್ತಾನು ಮತ್ತು ಎರಡೂ ಬೆಲೆ ತುಂಬಿದೆ."
        )
    }

    func stillNeedPrices(_ n: Int) -> String {
        pick(
            n == 1
                ? "1 item still needs stock, buying and selling price."
                : "\(n) items still need stock, buying and selling price.",
            "\(n) ಸಾಮಾನಿಗೆ ಇನ್ನೂ ದಾಸ್ತಾನು, ಖರೀದಿ ಮತ್ತು ಮಾರಾಟ ಬೆಲೆ ಬೇಕು."
        )
    }

    // MARK: - Settings

    var thisPhone: String { pick("This phone", "ಈ ಫೋನ್") }
    var businessOwner: String { pick("Business owner", "ಅಂಗಡಿ ಮಾಲೀಕರು") }
    var productsStat: String { pick("Products", "ಸಾಮಾನುಗಳು") }
    var customersStat: String { pick("Customers", "ಗ್ರಾಹಕರು") }
    var languageSection: String { pick("Language", "ಭಾಷೆ") }
    var themeSection: String { pick("Theme", "ಥೀಮ್") }
    var themeDark: String { pick("Dark", "ಕಪ್ಪು") }
    var themeLight: String { pick("Light", "ಬಿಳಿ") }
    var languageAndCurrency: String { pick("Language and currency", "ಭಾಷೆ ಮತ್ತು ಹಣ") }
    var notBackedUpYet: String { pick("Nothing backed up yet", "ಇನ್ನೂ ಬ್ಯಾಕಪ್ ಆಗಿಲ್ಲ") }

    func backedUpOn(_ date: String) -> String {
        pick("Backed up \(date)", "\(date) ರಂದು ಬ್ಯಾಕಪ್ ಆಗಿದೆ")
    }
    var currencySection: String { pick("Currency", "ಹಣದ ಬಗೆ") }

    /// `Saudi Riyal` — from the system, so it arrives already in the language
    /// in force rather than being another column to keep translated.
    func currencyName(_ currency: Currency) -> String {
        language.locale.localizedString(forCurrencyCode: currency.code) ?? currency.code
    }

    /// `SAR · Saudi Riyal` — the menu row.
    func currencyRow(_ currency: Currency) -> String {
        "\(currency.code) · \(currencyName(currency))"
    }

    /// Trimmed to the half that cannot be discovered by trying it. Changing the
    /// language explains itself the moment it happens; changing the currency
    /// does not say what became of the numbers.
    var currencyNote: String {
        pick(
            "Changing the currency converts nothing — saved amounts keep the numbers you entered, and only the symbol in front of them changes.",
            "ಹಣದ ಬಗೆ ಬದಲಿಸಿದರೆ ಯಾವುದೂ ಪರಿವರ್ತನೆ ಆಗುವುದಿಲ್ಲ — ಉಳಿಸಿದ ಮೊತ್ತಗಳ ಸಂಖ್ಯೆ ಹಾಗೆಯೇ ಇರುತ್ತದೆ, ಮುಂದಿನ ಚಿಹ್ನೆ ಮಾತ್ರ ಬದಲಾಗುತ್ತದೆ."
        )
    }

    var setupCurrencyNote: String {
        pick(
            "What you bill in. You can change it later in Settings.",
            "ನೀವು ಯಾವ ಹಣದಲ್ಲಿ ಬಿಲ್ ಮಾಡುತ್ತೀರಿ. ಆಮೇಲೆ ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಬದಲಿಸಬಹುದು."
        )
    }


    var moveToAnotherPhone: String { pick("Move to another phone", "ಇನ್ನೊಂದು ಫೋನಿಗೆ ಸಾಗಿಸಿ") }

    var moveToAnotherPhoneNote: String {
        pick(
            "Stockbook never uploads anything, so a new phone gets your shop from a file you carry across. Export here, then import on the other phone.",
            "ಸ್ಟಾಕ್‌ಬುಕ್ ಯಾವುದನ್ನೂ ಇಂಟರ್ನೆಟ್‌ಗೆ ಕಳಿಸುವುದಿಲ್ಲ. ಹಾಗಾಗಿ ಹೊಸ ಫೋನಿಗೆ ನಿಮ್ಮ ಅಂಗಡಿ ಫೈಲ್ ಮೂಲಕವೇ ಹೋಗಬೇಕು. ಇಲ್ಲಿ ಫೈಲ್ ಮಾಡಿ, ಆ ಫೋನಿನಲ್ಲಿ ತರಿಸಿಕೊಳ್ಳಿ."
        )
    }

    var exportEverything: String { pick("Export everything", "ಎಲ್ಲವನ್ನೂ ಫೈಲ್‌ಗೆ ಉಳಿಸಿ") }

    var exportNoteFirstTime: String {
        pick(
            "Writes one file with every product, price, stock count and bill.",
            "ಎಲ್ಲಾ ಸಾಮಾನು, ಬೆಲೆ, ದಾಸ್ತಾನು ಮತ್ತು ಬಿಲ್ ಇರುವ ಒಂದು ಫೈಲ್ ಬರೆಯುತ್ತದೆ."
        )
    }

    var exportNoteAfterBackup: String {
        pick(
            "Written to Files. Send it to the other phone however you like — AirDrop, WhatsApp, a memory card.",
            "ಫೈಲ್ಸ್‌ನಲ್ಲಿ ಉಳಿಸಲಾಗಿದೆ. ಇನ್ನೊಂದು ಫೋನಿಗೆ ನಿಮಗೆ ಬೇಕಾದ ಹಾಗೆ ಕಳಿಸಿ — ಏರ್‌ಡ್ರಾಪ್, ವಾಟ್ಸಾಪ್, ಮೆಮೊರಿ ಕಾರ್ಡ್."
        )
    }

    var writeAFreshFile: String { pick("Write a fresh file", "ಹೊಸ ಫೈಲ್ ಬರೆಯಿರಿ") }
    var createBackupFile: String { pick("Create backup file", "ಬ್ಯಾಕಪ್ ಫೈಲ್ ಮಾಡಿ") }
    var importABackupFile: String { pick("Import a backup file", "ಬ್ಯಾಕಪ್ ಫೈಲ್ ತರಿಸಿ") }
    var chooseAFile: String { pick("Choose a file", "ಫೈಲ್ ಆರಿಸಿ") }
    var replaceEverything: String { pick("Replace everything", "ಎಲ್ಲವನ್ನೂ ಬದಲಿಸಿ") }

    var importNoteIdle: String {
        pick(
            "Pick a file exported from another phone. Its contents take over from what is here now.",
            "ಇನ್ನೊಂದು ಫೋನಿನಿಂದ ಉಳಿಸಿದ ಫೈಲ್ ಆರಿಸಿ. ಅದರಲ್ಲಿರುವುದು ಈಗ ಇಲ್ಲಿ ಇರುವುದರ ಜಾಗ ತೆಗೆದುಕೊಳ್ಳುತ್ತದೆ."
        )
    }

    var importNoteDone: String {
        pick(
            "Imported. Everything from that file is now on this phone.",
            "ತರಿಸಲಾಗಿದೆ. ಆ ಫೈಲಿನಲ್ಲಿದ್ದ ಎಲ್ಲವೂ ಈಗ ಈ ಫೋನಿನಲ್ಲಿದೆ."
        )
    }

    /// Names what is about to be lost, in the owner's own numbers.
    func replaceWarning(productCount: Int, billCount: Int) -> String {
        pick(
            "This replaces the \(products(productCount)) and \(bills(billCount)) already on this phone. It cannot be undone.",
            "ಈ ಫೋನಿನಲ್ಲಿ ಈಗಾಗಲೇ ಇರುವ \(productCount) ಸಾಮಾನು ಮತ್ತು \(billCount) ಬಿಲ್ ಇದರಿಂದ ಬದಲಾಗುತ್ತವೆ. ಇದನ್ನು ವಾಪಸ್ ಪಡೆಯಲು ಆಗುವುದಿಲ್ಲ."
        )
    }

    var startAgain: String { pick("Start again", "ಮತ್ತೆ ಶುರು") }
    var startOver: String { pick("Start over", "ಮತ್ತೆ ಶುರು ಮಾಡಿ") }


    // MARK: - Backup file

    func savedOn(_ date: String) -> String {
        pick("saved \(date)", "\(date) ರಂದು ಉಳಿಸಿದ್ದು")
    }

    func fileSize(kilobytes: Int) -> String {
        pick("\(kilobytes) KB", "\(kilobytes) KB")
    }

    func backupError(_ error: BackupError) -> String {
        switch error {
        case .unreadable:
            pick("That file could not be opened.", "ಆ ಫೈಲ್ ತೆರೆಯಲು ಆಗಲಿಲ್ಲ.")
        case .notStockbookData:
            pick(
                "That is not a Stockbook backup file.",
                "ಅದು ಸ್ಟಾಕ್‌ಬುಕ್ ಬ್ಯಾಕಪ್ ಫೈಲ್ ಅಲ್ಲ."
            )
        case .newerVersion(let found):
            pick(
                "That backup was written by a newer version of Stockbook (format \(found)). Update this phone first.",
                "ಆ ಬ್ಯಾಕಪ್ ಸ್ಟಾಕ್‌ಬುಕ್‌ನ ಹೊಸ ಆವೃತ್ತಿಯಿಂದ ಬರೆದದ್ದು (ಫಾರ್ಮ್ಯಾಟ್ \(found)). ಮೊದಲು ಈ ಫೋನಿನ ಆ್ಯಪ್ ಅಪ್‌ಡೇಟ್ ಮಾಡಿ."
            )
        }
    }

    // MARK: - Dates

    /// `TUESDAY, 11 AUGUST` — uppercased by the `.kicker` type role.
    func headerDate(_ date: Date) -> String {
        Copy.headerDate(date, locale: language.locale)
    }

    /// `09:41` — always 24-hour, in both languages.
    func time(_ date: Date) -> String {
        Copy.time(date)
    }

    /// `28 July 2026`
    func longDate(_ date: Date) -> String {
        Copy.longDate(date, locale: language.locale)
    }
}
