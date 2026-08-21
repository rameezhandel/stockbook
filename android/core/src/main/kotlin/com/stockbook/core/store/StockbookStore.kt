package com.stockbook.core.store

import com.stockbook.core.model.AppTheme
import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.CreditNote
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.CustomerRecord
import com.stockbook.core.model.Expense
import com.stockbook.core.model.InvoiceNo
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Product
import com.stockbook.core.model.SupplierRecord
import com.stockbook.core.model.SupplierPayment
import com.stockbook.core.model.Supplier
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.Settings
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Statement
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.transfer.BackupDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** One line as the cart holds it, before it becomes history. */
data class DraftLine(
    val productUid: String,
    val qty: Int,
    /** What is being charged — the product's price unless overridden for this bill. */
    val price: Double
)

/**
 * Every rule that changes data lives here, and the current shop lives here too.
 *
 * Screens read [state] and never mutate it — the setters are private, so that is
 * enforced rather than merely asked for. Stock arithmetic, bill numbering,
 * snapshotting and the stock rules are all one layer, which is the layer the
 * tests drive. None of it knows Android exists.
 */
class StockbookStore(private val repository: StockbookRepository) {

    private val _state = MutableStateFlow(ShopState.EMPTY)
    val state: StateFlow<ShopState> = _state.asStateFlow()

    /**
     * Set when the disk refuses a write. Nothing in the UI surfaces it yet; it
     * exists so a failure is recorded rather than swallowed.
     */
    var lastError: String? = null
        private set

    val products: List<Product> get() = _state.value.products
    val bills: List<Bill> get() = _state.value.bills

    /**
     * The roster: typed-in facts about customers. Named for the record rather
     * than the person because [customers] is what screens want — that one merges
     * these with what history says.
     */
    val customerRecords: List<CustomerRecord> get() = _state.value.customers

    /** Money received after the bill, newest first. */
    val payments: List<Payment> get() = _state.value.payments

    val supplierRecords: List<SupplierRecord> get() = _state.value.suppliers

    /** Stock that arrived, newest first. */
    val purchases: List<Purchase> get() = _state.value.purchases

    /** Money paid out after the delivery, newest first. */
    val supplierPayments: List<SupplierPayment> get() = _state.value.supplierPayments

    /** What has been credited back to customers, newest first. */
    val creditNotes: List<CreditNote> get() = _state.value.creditNotes

    /** The owner's own spending, newest first. */
    val expenses: List<Expense> get() = _state.value.expenses

    val settings: Settings get() = _state.value.settings


    init {
        reload()
    }

    private fun reload() {
        try {
            val loaded = repository.loadAll()
            _state.value = loaded.copy(
                products = loaded.products.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
                bills = loaded.bills.sortedByDescending { it.createdAt }
            )
        } catch (error: Exception) {
            lastError = error.message
        }
    }

    /**
     * Writes are best-effort in the sense that a failure cannot roll back the
     * in-memory change — but it is recorded rather than ignored.
     */
    private fun attempt(work: () -> Unit) {
        try {
            work()
        } catch (error: Exception) {
            lastError = error.message
        }
    }

    private fun updateSettings(change: (Settings) -> Settings) {
        val updated = change(settings)
        _state.value = _state.value.copy(settings = updated)
        attempt { repository.save(updated) }
    }

    // --- Settings

    fun setOwnerName(name: String) = updateSettings { it.copy(ownerName = name.trim()) }

    /**
     * Trailing blank lines go; the rest is kept exactly as typed, line breaks
     * included — the owner is laying out how their own address prints.
     */
    fun setShopAddress(address: String) =
        updateSettings { it.copy(shopAddress = address.trim()) }

    fun setLanguage(language: AppLanguage) {
        if (settings.language == language) return
        updateSettings { it.copy(language = language) }
    }

    /**
     * Dark or light. Stored here; the palette itself is applied by the UI layer,
     * which is the only part of the app that knows what a colour is.
     */
    fun setTheme(theme: AppTheme) {
        if (settings.theme == theme) return
        updateSettings { it.copy(theme = theme) }
    }

    /**
     * The one currency the shop bills in.
     *
     * Nothing is converted. Amounts already saved keep their numbers and start
     * being drawn with the new symbol, which is the honest behaviour for an app
     * that holds no exchange rate — and the reason the Settings copy says so out
     * loud before the tap.
     */
    fun setCurrency(currency: Currency) {
        if (settings.currencyCode == currency.code) return
        updateSettings { it.copy(currencyCode = currency.code) }
    }

    fun completeSetup() = updateSettings { it.copy(setupCompleted = true) }

    fun markExported(at: Instant = Timestamps.now()) = updateSettings { it.copy(lastExportAt = at) }

    // --- Products

    /**
     * Adds a product. Names are deduplicated case-insensitively — typing one
     * that already exists is silently ignored, matching setup's behaviour, and
     * the existing product comes back instead.
     */
    fun addProduct(name: String, stock: Int, cost: Double, price: Double): Product {
        val cleaned = name.trim()
        products.firstOrNull { it.name.equals(cleaned, ignoreCase = true) }?.let { return it }

        val product = Product(
            name = cleaned,
            stock = maxOf(0, stock),
            cost = maxOf(0.0, cost),
            price = maxOf(0.0, price)
        )
        _state.value = _state.value.copy(
            products = (products + product).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        )
        attempt { repository.upsert(product) }
        return product
    }

    /**
     * Corrects what a product **is** — its name and its two prices.
     *
     * Deliberately cannot touch the count. Editing a product used to set stock
     * as well, which made it a second, unlabelled [setStock] sitting one
     * keystroke away from the price boxes: fixing a miscount could rewrite a
     * selling price, and neither field said whether the number was absolute or
     * something to add.
     *
     * The count now moves for a stated reason and by one route each — arriving
     * as a delivery, leaving on a bill, or corrected through [setStock], which
     * says out loud that it is what was counted on the shelf. Taking the
     * parameter away rather than ignoring it is what stops the two drifting back
     * together.
     */
    fun update(product: Product, name: String, cost: Double, price: Double) {
        val updated = product.copy(
            name = name.trim(),
            cost = maxOf(0.0, cost),
            price = maxOf(0.0, price)
        )
        replace(updated)
    }

    fun delete(product: Product) {
        _state.value = _state.value.copy(products = products.filterNot { it.uid == product.uid })
        attempt { repository.delete(product.uid) }
    }

    fun product(uid: String?): Product? = uid?.let { id -> products.firstOrNull { it.uid == id } }

    fun productsMatching(query: String): List<Product> {
        val needle = query.trim()
        if (needle.isEmpty()) return products
        return products.filter { it.name.contains(needle, ignoreCase = true) }
    }

    private fun replace(product: Product) {
        _state.value = _state.value.copy(
            products = products
                .map { if (it.uid == product.uid) product else it }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        )
        attempt { repository.upsert(product) }
    }

    // --- Bills

    /**
     * Saves a bill and moves the stock.
     *
     * Line names and prices are **snapshotted** here: the product may be renamed,
     * repriced or deleted tomorrow, and what somebody paid today must not move
     * with it. Stock floors at zero — overselling is allowed, because the
     * customer is standing there and the count may simply be wrong, but the
     * shelf never goes negative.
     */
    fun saveBill(
        /**
         * What was sold, when the owner said. Empty is the ordinary case for a
         * shop entering a paper bill it has already written: the total is known,
         * and rebuilding it product by product to arrive at it is work for
         * nothing. An itemised bill moves the shelf; a total does not.
         */
        lines: List<DraftLine> = emptyList(),
        customer: String,
        paid: Double?,
        /**
         * What the bill came to, for a bill with no lines on it. Ignored when
         * there are lines — their sum is the total, and a typed figure beside it
         * is a second answer to a question that already has one.
         */
        amount: Double? = null,
        /**
         * When the sale happened, which is not always when it was typed. A shop
         * that writes bills in the paper book all day and enters them at closing
         * time would otherwise have every one of them stamped 9pm — and the
         * customer statements, which are the documents somebody settles up
         * against, would inherit that.
         */
        createdAt: Instant = Timestamps.now(),
        /** The number on the paper bill, when the shop wrote one. */
        invoiceNo: String? = null,
        /** Photographs of that paper, by id. The files are the app's to keep. */
        photoIds: List<String> = emptyList(),
        /** What the bill was for. Prints on the customer's statement. */
        note: String? = null
    ): Bill? {
        val name = customer.trim()
        if (name.isEmpty()) return null

        val snapshots = snapshot(lines)
        // Only an itemised bill moves the shelf. Nothing else in the app can take
        // stock off it, which is why a shop that types totals is told the count is
        // its own to keep straight.
        for (line in snapshots) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = maxOf(0, product.stock - line.qty)))
        }

        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        // A bill for nothing is not a bill. Either something was sold or a figure
        // was typed; neither is the same as a blank saved by accident.
        if (total <= 0) return null
        val bill = Bill(
            number = settings.nextBillNumber,
            lines = snapshots,
            total = total,
            // Paying the whole amount is paid in full, not a part payment of the
            // total — otherwise the receipt says somebody owes zero.
            paid = paid?.takeIf { it < total }?.coerceIn(0.0, total),
            who = name,
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            photoIds = photoIds.distinct(),
            note = CustomerRecord.tidied(note),
            createdAt = createdAt
        )

        val nextSettings = settings.copy(nextBillNumber = settings.nextBillNumber + 1)
        _state.value = _state.value.copy(
            bills = listOf(bill) + bills,
            settings = nextSettings
        )
        attempt {
            repository.append(bill)
            repository.save(nextSettings)
        }
        return bill
    }

    /**
     * The bill already carrying this number, if any.
     *
     * The check behind the screen's refusal to save a second bill on the same
     * number. It lives here rather than on the screen because the delivery side
     * asks the same question, and one answer means one rule.
     *
     * A removed bill takes its number with it, and a bill being edited does not
     * clash with itself — see [exceptNumber]. Between them, that is what makes
     * correcting a bill possible at all: neither the old record nor the bill in
     * front of the owner may hold the paper's number hostage.
     */
    /**
     * One bill, by the app's own number.
     *
     * The sheets re-read through this rather than holding the copy they were
     * opened with, so a bill edited behind an open screen does not go on showing
     * what it used to say.
     */
    fun bill(number: Int): Bill? = bills.firstOrNull { it.number == number }

    fun billWithInvoiceNo(invoiceNo: String?, exceptNumber: Int? = null): Bill? {
        val key = InvoiceNo.key(invoiceNo)
        if (key.isEmpty()) return null
        // `exceptNumber` is what makes editing possible: without it, opening bill
        // 1024 to change its date would be told 1024 is already taken, by itself.
        return bills.firstOrNull { it.number != exceptNumber && InvoiceNo.key(it.invoiceNo) == key }
    }

    /** The same question on the other side of the book. */
    fun purchaseWithInvoiceNo(invoiceNo: String?, exceptId: String? = null): Purchase? {
        val key = InvoiceNo.key(invoiceNo)
        if (key.isEmpty()) return null
        return purchases.firstOrNull { it.id != exceptId && InvoiceNo.key(it.invoiceNo) == key }
    }

    /**
     * The lines as they will be stored: names and prices taken **now**, so that
     * renaming or repricing a product tomorrow cannot rewrite what somebody paid
     * today. Reads products; moves no stock, which is what lets both saving and
     * editing decide separately what the shelf owes.
     */
    private fun snapshot(lines: List<DraftLine>): List<BillLine> =
        lines.mapNotNull { line ->
            val product = product(line.productUid) ?: return@mapNotNull null
            BillLine(
                productUid = product.uid,
                name = product.name,
                qty = maxOf(1, line.qty),
                price = line.price
            )
        }

    /**
     * Rewrites a bill, and moves the shelf by the difference.
     *
     * The old lines go back on and the new ones come off, so an edit that drops a
     * line, changes a quantity or abandons itemising altogether leaves the count
     * exactly where entering the corrected bill from scratch would have.
     *
     * Nothing is touched unless the result would be a valid bill: a blank name or
     * a total of zero returns null with the stock still where it was, rather than
     * half-applying an edit and leaving the shelf to explain it.
     */
    fun updateBill(
        number: Int,
        lines: List<DraftLine> = emptyList(),
        customer: String,
        paid: Double?,
        amount: Double? = null,
        createdAt: Instant,
        invoiceNo: String? = null,
        note: String? = null
        // Photographs are deliberately not a parameter here. They are added and
        // removed one at a time by [attachPhoto] and [detachPhoto], so an edit
        // form that knows nothing about them cannot wipe them by omission.
    ): Bill? {
        val existing = bills.firstOrNull { it.number == number } ?: return null
        val name = customer.trim()
        if (name.isEmpty()) return null

        val snapshots = snapshot(lines)
        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (total <= 0) return null

        // Reverse, then apply. In that order, because a bill that kept the same
        // product with a smaller quantity would otherwise floor at zero on the way
        // down and come back wrong.
        for (line in existing.lines) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = product.stock + line.qty))
        }
        for (line in snapshots) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = maxOf(0, product.stock - line.qty)))
        }

        val updated = existing.copy(
            lines = snapshots,
            total = total,
            paid = paid?.takeIf { it < total }?.coerceIn(0.0, total),
            who = name,
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            note = CustomerRecord.tidied(note),
            createdAt = createdAt
        )
        _state.value = _state.value.copy(
            bills = bills.map { if (it.number == number) updated else it }
        )
        attempt { repository.update(updated) }
        return updated
    }

    /**
     * Removes a bill and puts its stock back.
     *
     * Gone rather than marked: this is the shop's own book, and a bill entered by
     * mistake is a line the owner would have scribbled out of the paper one. The
     * number it carried becomes free again, which is what makes re-entering it
     * work.
     */
    fun deleteBill(number: Int) {
        val existing = bills.firstOrNull { it.number == number } ?: return
        for (line in existing.lines) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = product.stock + line.qty))
        }
        _state.value = _state.value.copy(bills = bills.filterNot { it.number == number })
        attempt { repository.deleteBill(number) }
    }

    // --- Photographs of the paper
    //
    // Ids only. The files are the platform's to write, because an image codec is
    // not domain work, and they are kept out of [ShopState] because this whole
    // record is rewritten every time stock moves.

    /** Records that a photograph belongs to this bill. Adding the same one twice is a no-op. */
    fun attachPhoto(billNumber: Int, photoId: String): Bill? {
        val id = photoId.trim()
        if (id.isEmpty()) return null
        val existing = bills.firstOrNull { it.number == billNumber } ?: return null
        if (id in existing.photoIds) return existing
        return replaceBill(existing.copy(photoIds = existing.photoIds + id))
    }

    /**
     * Forgets a photograph.
     *
     * Deleting the file is the caller's job and happens after this, so a crash in
     * between leaves a file nothing points at — which the sweep collects — rather
     * than a bill pointing at nothing.
     */
    fun detachPhoto(billNumber: Int, photoId: String): Bill? {
        val existing = bills.firstOrNull { it.number == billNumber } ?: return null
        if (photoId !in existing.photoIds) return existing
        return replaceBill(existing.copy(photoIds = existing.photoIds - photoId))
    }

    /**
     * Every photograph the book still refers to.
     *
     * What the sweep is allowed to keep, and the only direction cleanup ever
     * runs: files nothing refers to are deleted, but an id whose file is missing
     * is **never** deleted. A book restored ahead of its pictures has to re-adopt
     * them the moment they arrive, and pruning would sever that permanently.
     */
    fun photoIdsInUse(): Set<String> = bills.flatMap { it.photoIds }.toSet()

    private fun replaceBill(updated: Bill): Bill {
        _state.value = _state.value.copy(
            bills = bills.map { if (it.number == updated.number) updated else it }
        )
        attempt { repository.update(updated) }
        return updated
    }

    // --- Customers

    /**
     * Distinct customers from the bills, **sorted by outstanding balance
     * descending, then bill count descending** — the people who owe money come
     * first because that is who the owner most needs to recognise at the counter.
     *
     * Grouped by [Customer.key], so case and stray spaces do not split one person
     * into two. [bills] is newest-first, so the first spelling seen is the most
     * recent one and that is the one shown.
     */
    fun customers(): List<Customer> {
        data class Tally(val name: String, var count: Int, var total: Double, var owed: Double)

        val book = LinkedHashMap<String, Tally>()
        for (bill in bills) {
            if (bill.who.isBlank()) continue
            val key = Customer.key(bill.who)
            val tally = book.getOrPut(key) { Tally(bill.who.trim(), 0, 0.0, 0.0) }
            tally.count += 1
            tally.total += bill.total
            tally.owed += bill.balance
        }

        // The roster and history are merged, not chosen between. Somebody entered
        // during setup who has never bought anything is a customer with no bills;
        // a name typed at the counter that nobody added is a customer too.
        val roster = customerRecords.associateBy { it.key }
        for (record in roster.values) {
            book.getOrPut(record.key) { Tally(record.name, 0, 0.0, 0.0) }
        }

        // What they brought over from the paper book. Added after the tallies so a
        // customer with an opening balance and no bills still shows what they owe.
        for (record in roster.values) {
            book[record.key]?.let { it.owed += record.openingBalance }
        }

        // Payments come off what is owed, and this has to run **after** every
        // customer is in the book — roster entries included.
        //
        // It used to run straight after the bills, which meant `book[key]` was
        // still null for anyone who had never been billed, and their payment was
        // dropped without a sound. On a fresh shop that is the ordinary case, not
        // an edge one: a customer is entered with what they owed from the old
        // book, and the first thing that ever happens to them is paying it off.
        for (payment in payments) {
            book[payment.customerKey]?.let { it.owed -= payment.amount }
        }

        // Credited goods and figures come off what is owed exactly as payments
        // do. They are kept apart on a statement because only one of them is
        // cash — but a customer who was credited 540 owes 540 less, and every
        // screen that asks what somebody owes has to say so.
        for (note in creditNotes) {
            book[note.customerKey]?.let { it.owed -= note.total }
        }

        return book.map { (key, tally) ->
            val record = roster[key]
            Customer(
                // Where a roster entry exists its spelling wins: it was typed on
                // purpose rather than in a hurry with a customer waiting.
                name = record?.name ?: tally.name,
                key = key,
                billCount = tally.count,
                total = tally.total,
                // Rounded because netting payments off balances in binary
                // floating point otherwise leaves a customer owing 0.000000001
                // and the UI saying they owe money.
                owed = Math.round(tally.owed * 100) / 100.0,
                phone = record?.phone,
                place = record?.place,
                openingBalance = record?.openingBalance ?: 0.0,
                isOnRoster = record != null
            )
        }.sortedWith(compareByDescending<Customer> { it.owed }.thenByDescending { it.billCount })
    }

    /** One customer by key, roster figures and all. */
    fun customer(key: String): Customer? = customers().firstOrNull { it.key == key }

    /**
     * The customer directory: everybody, filtered by what has been typed, in the
     * order somebody looks a person up.
     *
     * A separate function rather than a flag on [customers], because the two
     * orders answer different questions and neither can be the other's default.
     * [customers] is biggest-debt-first, which is what Today's banner and the
     * owed sheets are built on. This one is by name, because a screen you go to
     * in order to find Fatima is no use sorted by what Fatima happens to owe.
     */
    fun customers(matching: String): List<Customer> =
        customers()
            .filter { partyMatches(it.name, it.phone, matching) }
            .sortedBy { it.name.lowercase() }

    /**
     * Adds a customer to the roster. A key already present is updated rather than
     * duplicated — typing a name that is already there is a correction, not a
     * second person. Returns null for a blank name.
     */
    fun addCustomer(
        name: String,
        phone: String? = null,
        place: String? = null,
        openingBalance: Double = 0.0
    ): CustomerRecord? {
        if (name.isBlank()) return null
        val fresh = CustomerRecord.of(name, phone, place, openingBalance)
        val existing = customerRecords.firstOrNull { it.key == fresh.key }
        val record = existing?.copy(
            name = fresh.name,
            phone = fresh.phone,
            place = fresh.place,
            openingBalance = fresh.openingBalance
        ) ?: fresh
        _state.value = _state.value.copy(
            customers = if (existing == null) {
                customerRecords + record
            } else {
                customerRecords.map { if (it.key == record.key) record else it }
            }
        )
        attempt { repository.upsert(record) }
        return record
    }

    /**
     * Corrects the facts about a customer already on the roster.
     *
     * A name changed enough to change its key is a **rename**, and a rename
     * rewrites `who` on that customer's bills. That is the one case where a saved
     * bill is edited, and it is right: the alternative is the roster saying
     * "Ahmed Contracting" while their bills are filed under "ahmed" and the two
     * never meeting again. What a bill records about *money* stays untouchable.
     */
    fun updateCustomer(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0.0
    ) {
        if (name.isBlank()) return
        val existing = customerRecords.firstOrNull { it.key == key } ?: return
        val newKey = Customer.key(name)
        val record = existing.copy(
            key = newKey,
            name = name.trim(),
            phone = CustomerRecord.tidied(phone),
            place = CustomerRecord.tidied(place),
            openingBalance = maxOf(0.0, openingBalance)
        )

        if (newKey == key) {
            _state.value = _state.value.copy(
                customers = customerRecords.map { if (it.key == key) record else it }
            )
            attempt { repository.upsert(record) }
            return
        }

        // Renamed. Move the roster entry, then bring the bills and payments with
        // it so nothing is left filed under a name that no longer exists. A
        // rename onto somebody already there merges: one person, not two.
        val movedBills = bills.map { if (Customer.key(it.who) == key) it.copy(who = record.name) else it }
        val movedPayments = payments.map { if (it.customerKey == key) it.copy(customerKey = newKey) else it }
        _state.value = _state.value.copy(
            bills = movedBills,
            payments = movedPayments,
            customers = customerRecords.filterNot { it.key == key || it.key == newKey } + record
        )

        attempt {
            repository.deleteCustomer(key)
            repository.deleteCustomer(newKey)
            repository.upsert(record)
            for (bill in movedBills) if (bill.who == record.name) repository.update(bill)
            for (payment in movedPayments) {
                if (payment.customerKey == newKey) {
                    repository.deletePayment(payment.id)
                    repository.append(payment)
                }
            }
        }
    }

    /**
     * Takes a customer off the roster. Their bills and payments stay: this
     * forgets the address book entry, not the trading history.
     */
    fun removeCustomer(key: String) {
        _state.value = _state.value.copy(customers = customerRecords.filterNot { it.key == key })
        attempt { repository.deleteCustomer(key) }
    }

    // --- Payments

    /**
     * Records money handed over after the bill.
     *
     * Not allocated to a particular bill, because that is not how a counter
     * works: somebody pays what they can against what they owe. A zero or
     * negative amount is a no-op rather than an error — the sheet treats an empty
     * box as "close without doing anything", the same as restock.
     */
    fun recordPayment(
        customerKey: String,
        amount: Double,
        receivedAt: Instant = Timestamps.now(),
        note: String? = null,
        paymentNo: String? = null
    ): Payment? {
        if (amount <= 0 || customerKey.isEmpty()) return null
        val payment = Payment(
            customerKey = customerKey,
            amount = amount,
            paymentNo = paymentNo?.trim()?.takeIf { it.isNotEmpty() },
            receivedAt = receivedAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(
            payments = (payments + payment).sortedByDescending { it.receivedAt }
        )
        attempt { repository.append(payment) }
        return payment
    }

    /**
     * Corrects a payment that was written down wrong.
     *
     * Every part of it, because every part can be mistyped: the amount, the day
     * the money actually arrived, the note, and the number off the receipt book.
     *
     * This did not exist while a payment was an amount and a date — deleting and
     * re-entering was the honest answer to a record with two fields. A receipt
     * number changed that: a wrong one is spotted weeks later, reconciling
     * against the paper book, and re-entering by then means re-picking the
     * original date and hoping. That is how a statement starts claiming money
     * arrived on the day it was corrected.
     */
    fun updatePayment(
        id: String,
        amount: Double,
        receivedAt: Instant,
        note: String? = null,
        paymentNo: String? = null
    ): Payment? {
        val existing = payments.firstOrNull { it.id == id } ?: return null
        if (amount <= 0) return null

        val updated = existing.copy(
            amount = amount,
            paymentNo = paymentNo?.trim()?.takeIf { it.isNotEmpty() },
            receivedAt = receivedAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(
            payments = payments
                .map { if (it.id == id) updated else it }
                .sortedByDescending { it.receivedAt }
        )
        attempt { repository.replaceAll(_state.value) }
        return updated
    }

    fun deletePayment(id: String) {
        _state.value = _state.value.copy(payments = payments.filterNot { it.id == id })
        attempt { repository.deletePayment(id) }
    }

    fun paymentsForCustomer(key: String): List<Payment> = payments.filter { it.customerKey == key }

    /**
     * The receipt already carrying this number, if any.
     *
     * Its own series: a receipt numbered 1024 does not clash with invoice 1024,
     * and refusing it would be the app inventing a rule the shop's paper does
     * not have. The same question [billWithInvoiceNo] and [creditNoteWithNo] ask
     * of theirs.
     */
    fun paymentWithNo(paymentNo: String?, exceptId: String? = null): Payment? {
        val key = InvoiceNo.key(paymentNo)
        if (key.isEmpty()) return null
        return payments.firstOrNull { it.id != exceptId && InvoiceNo.key(it.paymentNo) == key }
    }

    // --- Credit notes

    /**
     * Credits a customer's account, and puts anything returned back on the shelf.
     *
     * The shelf moves only for an itemised note, which is [saveBill]'s rule read
     * backwards: what a bill took off, a credit note for the same goods puts
     * back, and a note that is only a figure moves nothing. A shop that types
     * totals keeps its own count either way.
     *
     * Null for a note that credits nothing — the mirror of a bill for nothing,
     * and refused for the same reason.
     */
    fun addCreditNote(
        customerKey: String,
        lines: List<DraftLine> = emptyList(),
        amount: Double? = null,
        noteNo: String? = null,
        reason: String? = null,
        issuedAt: Instant = Timestamps.now()
    ): CreditNote? {
        if (customerKey.isEmpty()) return null

        val snapshots = snapshot(lines)
        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (total <= 0) return null

        val note = CreditNote(
            customerKey = customerKey,
            lines = snapshots,
            total = total,
            noteNo = noteNo?.trim()?.takeIf { it.isNotEmpty() },
            reason = CustomerRecord.tidied(reason),
            issuedAt = issuedAt
        )

        putBackStock(note)
        _state.value = _state.value.copy(
            creditNotes = (creditNotes + note).sortedByDescending { it.issuedAt }
        )
        attempt { repository.replaceAll(_state.value) }
        return note
    }

    /**
     * Corrects one, shelf and all.
     *
     * Takes back whatever the old note returned before applying the new one, so
     * a note edited from 5 pieces to 3 leaves 3 on the shelf rather than 8 —
     * the same take-back-first order [updatePurchase] needs, for the same reason.
     */
    fun updateCreditNote(
        id: String,
        customerKey: String,
        lines: List<DraftLine> = emptyList(),
        amount: Double? = null,
        noteNo: String? = null,
        reason: String? = null,
        issuedAt: Instant
    ): CreditNote? {
        val existing = creditNotes.firstOrNull { it.id == id } ?: return null
        if (customerKey.isEmpty()) return null

        val snapshots = snapshot(lines)
        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (total <= 0) return null

        takeBackCreditedStock(existing)

        val updated = existing.copy(
            customerKey = customerKey,
            lines = snapshots,
            total = total,
            noteNo = noteNo?.trim()?.takeIf { it.isNotEmpty() },
            reason = CustomerRecord.tidied(reason),
            issuedAt = issuedAt
        )
        putBackStock(updated)

        _state.value = _state.value.copy(
            creditNotes = creditNotes
                .map { if (it.id == id) updated else it }
                .sortedByDescending { it.issuedAt }
        )
        attempt { repository.replaceAll(_state.value) }
        return updated
    }

    /** Removes one, taking back anything it had put on the shelf. */
    fun deleteCreditNote(id: String) {
        val existing = creditNotes.firstOrNull { it.id == id } ?: return
        takeBackCreditedStock(existing)
        _state.value = _state.value.copy(creditNotes = creditNotes.filterNot { it.id == id })
        attempt { repository.replaceAll(_state.value) }
    }

    // --- The owner's own spending

    /**
     * Writes down money the owner spent.
     *
     * Returns null rather than saving nonsense: an amount at or below zero is
     * not an expense, and a blank note is a figure nobody can account for a
     * month later. Both are refused here rather than in the sheet, so the rule
     * holds however the store is reached.
     *
     * Newest first, like every other list in this store, so the screen never
     * has to sort.
     */
    fun addExpense(amount: Double, note: String, spentAt: Instant = Timestamps.now()): Expense? {
        val what = note.trim()
        if (amount <= 0 || what.isEmpty()) return null

        val expense = Expense(amount = amount, note = what, spentAt = spentAt)
        _state.value = _state.value.copy(expenses = listOf(expense) + expenses)
        attempt { repository.replaceAll(_state.value) }
        return expense
    }

    /** Corrects one. Same rules as writing it: a correction cannot make it invalid. */
    fun updateExpense(id: String, amount: Double, note: String, spentAt: Instant): Expense? {
        val existing = expenses.firstOrNull { it.id == id } ?: return null
        val what = note.trim()
        if (amount <= 0 || what.isEmpty()) return null

        val updated = existing.copy(amount = amount, note = what, spentAt = spentAt)
        _state.value = _state.value.copy(
            expenses = expenses.map { if (it.id == id) updated else it }
        )
        attempt { repository.replaceAll(_state.value) }
        return updated
    }

    /**
     * Removes one outright.
     *
     * Nothing to put back and nothing to recalculate — which is the dividend of
     * an expense being attached to nothing. Deleting a bill has to return its
     * stock and free its number; this is a line disappearing from a private
     * list.
     */
    fun deleteExpense(id: String) {
        if (expenses.none { it.id == id }) return
        _state.value = _state.value.copy(expenses = expenses.filterNot { it.id == id })
        attempt { repository.replaceAll(_state.value) }
    }

    /**
     * What the owner spent inside [period].
     *
     * The same notion of a period as [soldIn] and [boughtIn] — there is one idea
     * of "this month" in this app and [StatementPeriod] is it. Deliberately *not*
     * netted against either of them: this figure stands beside the shop's, never
     * inside it.
     */
    fun spentIn(period: StatementPeriod): Double {
        val range = period.range()
        return expenses.filter { it.spentAt in range }.sumOf { it.amount }
    }

    /**
     * The note already carrying this number, if any — the same question
     * [billWithInvoiceNo] asks, on a series of its own.
     */
    fun creditNoteWithNo(noteNo: String?, exceptId: String? = null): CreditNote? {
        val key = InvoiceNo.key(noteNo)
        if (key.isEmpty()) return null
        return creditNotes.firstOrNull { it.id != exceptId && InvoiceNo.key(it.noteNo) == key }
    }

    fun creditNotesForCustomer(key: String): List<CreditNote> =
        creditNotes.filter { it.customerKey == key }

    /** Returned goods go back on the shelf. Nothing moves for a note with no lines. */
    private fun putBackStock(note: CreditNote) {
        for (line in note.lines) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = maxOf(0, product.stock + line.qty)))
        }
    }

    private fun takeBackCreditedStock(note: CreditNote) {
        for (line in note.lines) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = maxOf(0, product.stock - line.qty)))
        }
    }

    // --- What the shop turned over

    /**
     * Everything billed inside [period], whoever it was billed to.
     *
     * The shop-wide twin of a statement's `billed`, and deliberately the same
     * notion of a period — there is one idea of "this month" in this app and
     * [StatementPeriod] is it, half-open bounds and all, so a bill written at
     * midnight on the 1st lands in exactly one month here as it does there.
     *
     * Bills only. A credit note reduces what somebody *owes*; it does not unsell
     * the goods, and a month's takings that quietly shrank when a note was
     * written weeks later would be a figure nobody could reconcile against the
     * till. The statement is where the two are netted.
     */
    fun soldIn(period: StatementPeriod): Double {
        val range = period.range()
        return bills.filter { it.createdAt in range }.sumOf { it.total }
    }

    /** The other side of the counter, over the same span. */
    fun boughtIn(period: StatementPeriod): Double {
        val range = period.range()
        return purchases.filter { it.createdAt in range }.sumOf { it.total }
    }

    /** How many bills the shop wrote in [period]. */
    fun billCountIn(period: StatementPeriod): Int {
        val range = period.range()
        return bills.count { it.createdAt in range }
    }

    // --- Statements

    /**
     * One customer's account over a period.
     *
     * The arithmetic is in [Statement.make], which takes plain lists — this only
     * decides which lists. That is what keeps the figures testable without a
     * store, a repository or a screen.
     */
    fun statementForCustomer(key: String, period: StatementPeriod): Statement? {
        val customer = customer(key) ?: return null
        return Statement.make(
            customer = customer,
            bills = billsForCustomer(key),
            payments = paymentsForCustomer(key),
            creditNotes = creditNotesForCustomer(key),
            period = period
        )
    }

    /** Every bill for one customer. */
    fun billsForCustomer(key: String): List<Bill> =
        bills.filter { Customer.key(it.who) == key }

    /**
     * Suggestions for the customer field: filtered by what has been typed,
     * excluding an exact match, capped at four.
     */
    fun customerSuggestions(typed: String, limit: Int = 4): List<Customer> {
        val query = Customer.key(typed)
        return customers()
            .filter { it.key != query && (query.isEmpty() || it.key.contains(query)) }
            .take(limit)
    }

    /**
     * The Today banner: who still owes, and how much in total. Counts **distinct
     * customers, not bills** — two unpaid bills from one person is one person,
     * however they capitalised it the second time.
     */
    fun outstanding(): Pair<List<String>, Double> {
        // Derived from [customers] rather than from bills directly, which is the
        // only way this figure can be right. Walking bills alone ignored both
        // payments received and balances carried over from the paper book — so a
        // customer who had settled up in full went on being named here, and one
        // who owed from before the app existed never was.
        val owing = customers().filter { it.owed > 0 }
        return owing.map { it.name } to owing.sumOf { it.owed }
    }

    // --- Suppliers

    /**
     * Every supplier, roster and history merged — the mirror of [customers], and
     * the same three-step order for the same hard-won reason.
     *
     * Purchases first, then roster entries, then carried-over balances, and
     * **payments last**. On the customer side the payments loop once ran second,
     * which silently dropped every payment from somebody who had never been
     * billed. On a fresh shop that is the ordinary case, not an edge one: a
     * supplier is entered with what the paper book says is owed, and the first
     * thing that ever happens to them is being paid.
     */
    fun suppliers(): List<Supplier> {
        data class Tally(val name: String, var count: Int, var total: Double, var owed: Double)

        val book = LinkedHashMap<String, Tally>()
        for (purchase in purchases) {
            if (purchase.supplierKey.isBlank()) continue
            val tally = book.getOrPut(purchase.supplierKey) { Tally(purchase.supplierKey, 0, 0.0, 0.0) }
            tally.count += 1
            tally.total += purchase.total
            tally.owed += purchase.balance
        }

        val roster = supplierRecords.associateBy { it.key }
        for (record in roster.values) {
            book.getOrPut(record.key) { Tally(record.name, 0, 0.0, 0.0) }
        }

        for (record in roster.values) {
            book[record.key]?.let { it.owed += record.openingBalance }
        }

        for (payment in supplierPayments) {
            book[payment.supplierKey]?.let { it.owed -= payment.amount }
        }

        return book.map { (key, tally) ->
            val record = roster[key]
            Supplier(
                // A purchase stores only the key, so a supplier who is somehow not
                // on the roster shows as the key itself rather than as nothing.
                name = record?.name ?: tally.name,
                key = key,
                purchaseCount = tally.count,
                total = tally.total,
                // Rounded for the same reason customers are: netting payments off
                // balances in binary floating point otherwise leaves 0.000000001
                // owed and a screen saying money is outstanding.
                owed = Math.round(tally.owed * 100) / 100.0,
                phone = record?.phone,
                place = record?.place,
                openingBalance = record?.openingBalance ?: 0.0,
                isOnRoster = record != null
            )
        }.sortedWith(compareByDescending<Supplier> { it.owed }.thenByDescending { it.purchaseCount })
    }

    fun supplier(key: String): Supplier? = suppliers().firstOrNull { it.key == key }

    /** The supplier directory. [customers] with a `matching` argument, mirrored. */
    fun suppliers(matching: String): List<Supplier> =
        suppliers()
            .filter { partyMatches(it.name, it.phone, matching) }
            .sortedBy { it.name.lowercase() }

    /**
     * Whether one person answers to what has been typed.
     *
     * Name and phone, because those are the two things written on the paper the
     * owner is holding. A blank query matches everybody — the box is empty far
     * more often than it is full, and a stray space must not empty the screen.
     */
    private fun partyMatches(name: String, phone: String?, query: String): Boolean {
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) return true
        if (name.lowercase().contains(wanted)) return true
        // Not `orEmpty()`: an absent phone must read as "no match" rather than as
        // an empty string that every query is a substring of.
        return phone?.lowercase()?.contains(wanted) == true
    }

    /**
     * Adds a supplier to the roster. A key already there is corrected rather than
     * duplicated. Returns null for a blank name.
     */
    fun addSupplier(
        name: String,
        phone: String? = null,
        place: String? = null,
        openingBalance: Double = 0.0
    ): SupplierRecord? {
        if (name.isBlank()) return null
        val fresh = SupplierRecord.of(name, phone, place, openingBalance)
        val existing = supplierRecords.firstOrNull { it.key == fresh.key }
        val record = existing?.copy(
            name = fresh.name,
            phone = fresh.phone,
            place = fresh.place,
            openingBalance = fresh.openingBalance
        ) ?: fresh
        _state.value = _state.value.copy(
            suppliers = if (existing == null) {
                supplierRecords + record
            } else {
                supplierRecords.map { if (it.key == record.key) record else it }
            }
        )
        attempt { repository.upsert(record) }
        return record
    }

    /**
     * Corrects a supplier. A changed name that produces a different key is a
     * **rename**, and the purchases move with it — they carry the key, so unlike a
     * bill there is no spelling to rewrite, which makes this the simpler half of
     * the pair.
     */
    fun updateSupplier(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0.0
    ) {
        if (name.isBlank()) return
        val existing = supplierRecords.firstOrNull { it.key == key } ?: return
        val newKey = Supplier.key(name)
        val record = existing.copy(
            key = newKey,
            name = name.trim(),
            phone = CustomerRecord.tidied(phone),
            place = CustomerRecord.tidied(place),
            openingBalance = maxOf(0.0, openingBalance)
        )

        val others = supplierRecords.filterNot { it.key == key || it.key == newKey }
        _state.value = _state.value.copy(
            suppliers = others + record,
            purchases = purchases.map { if (it.supplierKey == key) it.copy(supplierKey = newKey) else it },
            supplierPayments = supplierPayments.map {
                if (it.supplierKey == key) it.copy(supplierKey = newKey) else it
            }
        )
        // Disk follows memory, in the same order: the old roster entry goes, the
        // corrected one lands, and every moved purchase and payment is rewritten
        // under the new key. A rename onto somebody already there merges — one
        // supplier, not two — which is why the old key is deleted either way.
        attempt {
            repository.deleteSupplier(key)
            repository.deleteSupplier(newKey)
            repository.upsert(record)
            purchases.filter { it.supplierKey == newKey }.forEach { repository.update(it) }
            supplierPayments.filter { it.supplierKey == newKey }.forEach { payment ->
                repository.deleteSupplierPayment(payment.id)
                repository.append(payment)
            }
        }
    }

    fun removeSupplier(key: String) {
        _state.value = _state.value.copy(suppliers = supplierRecords.filterNot { it.key == key })
        attempt { repository.deleteSupplier(key) }
    }

    // --- Purchases

    /**
     * Records a delivery: stock goes on the shelf, the buying price becomes what
     * was just paid, and what is still owed lands on the supplier's account.
     *
     * `paid == null` means settled on the spot. A quantity of zero or less is a
     * no-op, exactly as [restock] treats one.
     */
    fun recordPurchase(
        /**
         * What arrived, where the shop keeps a count of it. Null for a supplier
         * bill entered as a figure — a mixed load, or something that never sits
         * on a shelf. Only a named product moves stock.
         */
        product: Product?,
        supplierKey: String,
        quantity: Int = 0,
        unitCost: Double = 0.0,
        paid: Double? = null,
        /** What the bill came to, where no product was named. */
        amount: Double? = null,
        createdAt: Instant = Timestamps.now(),
        /** The number on the supplier's invoice. */
        invoiceNo: String? = null
    ): Purchase? {
        if (supplierKey.isBlank()) return null

        // Itemised only when a product was named *and* a count came with it: a
        // product with no quantity is half an answer, and guessing the other half
        // would put stock on the shelf nobody said arrived.
        val current = product?.let { this.product(it.uid) }?.takeIf { quantity > 0 }
        val cost = when {
            current == null -> 0.0
            unitCost > 0 -> unitCost
            else -> current.cost
        }
        val total = if (current == null) amount ?: 0.0 else quantity * cost
        if (total <= 0) return null

        val purchase = Purchase(
            supplierKey = supplierKey,
            productUid = current?.uid,
            name = current?.name,
            qty = if (current == null) 0 else quantity,
            unitCost = cost,
            total = total,
            // Clamped to the total: a delivery cannot be overpaid, and a typo
            // that says so would put the shop permanently in credit.
            paid = paid?.let { maxOf(0.0, minOf(it, total)) },
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            createdAt = createdAt
        )
        _state.value = _state.value.copy(purchases = listOf(purchase) + purchases)
        attempt { repository.append(purchase) }
        // Cost is "latest paid", not a weighted average: the new figure simply
        // takes over. Nothing to take over when no product was named.
        if (current != null) {
            replace(current.copy(stock = current.stock + quantity, cost = cost))
        }
        return purchase
    }

    /**
     * A supplier's bill with no stock on it: a figure, a date and a number.
     *
     * The same record as a delivery, and deliberately so — it is money owed to
     * the same account, and a statement should not care which way it was entered.
     */
    fun recordSupplierBill(
        supplierKey: String,
        amount: Double,
        paid: Double? = null,
        createdAt: Instant = Timestamps.now(),
        invoiceNo: String? = null
    ): Purchase? = recordPurchase(
        product = null,
        supplierKey = supplierKey,
        paid = paid,
        amount = amount,
        createdAt = createdAt,
        invoiceNo = invoiceNo
    )

    /**
     * Rewrites a supplier's bill, and moves the shelf by the difference.
     *
     * The mirror of [updateBill]: what the old one put on the shelf comes off,
     * and what the new one says arrived goes on. A delivery edited down to a bare
     * figure gives back everything it added.
     */
    fun updatePurchase(
        id: String,
        product: Product?,
        supplierKey: String,
        quantity: Int = 0,
        unitCost: Double = 0.0,
        paid: Double? = null,
        amount: Double? = null,
        createdAt: Instant,
        invoiceNo: String? = null
    ): Purchase? {
        val existing = purchases.firstOrNull { it.id == id } ?: return null
        if (supplierKey.isBlank()) return null

        val current = product?.let { this.product(it.uid) }?.takeIf { quantity > 0 }
        val cost = when {
            current == null -> 0.0
            unitCost > 0 -> unitCost
            else -> current.cost
        }
        val total = if (current == null) amount ?: 0.0 else quantity * cost
        if (total <= 0) return null

        // Reverse the old, then apply the new — the same order as on a bill, and
        // for the same reason.
        takeBackStock(existing)
        if (current != null) {
            // Re-read: `current` was captured before the line above moved the
            // shelf, and adding to that stale count would silently undo the
            // taking-back on any delivery that kept the same product.
            val onShelf = this.product(current.uid) ?: current
            replace(onShelf.copy(stock = onShelf.stock + quantity, cost = cost))
        }

        val updated = existing.copy(
            supplierKey = supplierKey,
            productUid = current?.uid,
            name = current?.name,
            qty = if (current == null) 0 else quantity,
            unitCost = cost,
            total = total,
            paid = paid?.let { maxOf(0.0, minOf(it, total)) },
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            createdAt = createdAt
        )
        _state.value = _state.value.copy(
            purchases = purchases.map { if (it.id == id) updated else it }
        )
        attempt { repository.update(updated) }
        return updated
    }

    /** Removes a supplier's bill and takes its stock back off the shelf. */
    fun deletePurchase(id: String) {
        val purchase = purchases.firstOrNull { it.id == id } ?: return
        takeBackStock(purchase)
        _state.value = _state.value.copy(purchases = purchases.filterNot { it.id == id })
        attempt { repository.deletePurchase(id) }
    }

    /**
     * Unwinds what a delivery put on the shelf. Only an itemised one put anything
     * there, so only that one has any to take back.
     */
    private fun takeBackStock(purchase: Purchase) {
        purchase.productUid?.takeIf { purchase.isItemised }?.let { uid ->
            product(uid)?.let { product ->
                // Floored at zero. The stock may already have been sold, and a
                // negative shelf count is a worse lie than an optimistic one.
                replace(product.copy(stock = maxOf(0, product.stock - purchase.qty)))
            }
        }
    }

    fun purchasesForSupplier(key: String): List<Purchase> = purchases.filter { it.supplierKey == key }

    // --- Money out

    fun recordSupplierPayment(
        supplierKey: String,
        amount: Double,
        paidAt: Instant = Timestamps.now(),
        note: String? = null,
        paymentNo: String? = null
    ): SupplierPayment? {
        if (amount <= 0 || supplierKey.isEmpty()) return null
        val payment = SupplierPayment(
            supplierKey = supplierKey,
            amount = amount,
            paymentNo = paymentNo?.trim()?.takeIf { it.isNotEmpty() },
            paidAt = paidAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(supplierPayments = listOf(payment) + supplierPayments)
        attempt { repository.append(payment) }
        return payment
    }

    /** The same question [paymentWithNo] asks, on the money-out receipt book. */
    fun supplierPaymentWithNo(paymentNo: String?, exceptId: String? = null): SupplierPayment? {
        val key = InvoiceNo.key(paymentNo)
        if (key.isEmpty()) return null
        return supplierPayments.firstOrNull { it.id != exceptId && InvoiceNo.key(it.paymentNo) == key }
    }

    /** The same correction, on the money-out side — see [updatePayment]. */
    fun updateSupplierPayment(
        id: String,
        amount: Double,
        paidAt: Instant,
        note: String? = null,
        paymentNo: String? = null
    ): SupplierPayment? {
        val existing = supplierPayments.firstOrNull { it.id == id } ?: return null
        if (amount <= 0) return null

        val updated = existing.copy(
            amount = amount,
            paymentNo = paymentNo?.trim()?.takeIf { it.isNotEmpty() },
            paidAt = paidAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(
            supplierPayments = supplierPayments
                .map { if (it.id == id) updated else it }
                .sortedByDescending { it.paidAt }
        )
        attempt { repository.replaceAll(_state.value) }
        return updated
    }

    fun deleteSupplierPayment(id: String) {
        _state.value = _state.value.copy(supplierPayments = supplierPayments.filterNot { it.id == id })
        attempt { repository.deleteSupplierPayment(id) }
    }

    fun supplierPaymentsFor(key: String): List<SupplierPayment> =
        supplierPayments.filter { it.supplierKey == key }

    /** One supplier's account over a period. */
    fun statementForSupplier(key: String, period: StatementPeriod): Statement? {
        val supplier = supplier(key) ?: return null
        return Statement.make(
            supplier = supplier,
            purchases = purchasesForSupplier(key),
            payments = supplierPaymentsFor(key),
            period = period
        )
    }

    /**
     * The other side of [outstanding]: who the shop owes, and how much in total.
     * Derived from [suppliers] for the same reason — walking purchases alone would
     * ignore both payments made and balances carried over from the paper book.
     */
    fun payable(): Pair<List<String>, Double> {
        val owing = suppliers().filter { it.owed > 0 }
        return owing.map { it.name } to owing.sumOf { it.owed }
    }

    // --- Restock

    /**
     * Sets the shelf count to what was actually counted.
     *
     * The honest half of keeping stock at all: bills move the count only where
     * they were itemised, so the figure is a running tally rather than a
     * measurement, and this is how the owner tells it the truth after looking at
     * the shelf. A count is *set*, never added to — "there are twelve" is what
     * somebody says after counting, and asking them to work out the difference
     * is asking them to do arithmetic the app can do.
     */
    fun setStock(product: Product, count: Int) {
        val current = this.product(product.uid) ?: return
        replace(current.copy(stock = maxOf(0, count)))
    }

    // --- Whole-database operations

    /** Wipes everything and sends the owner back to setup step 1. */
    fun startOver() {
        // Everything goes except the language and the theme. Wiping the shop is a
        // data decision; being handed setup in a language you cannot read — or in
        // a colour scheme you turned off — is not one the owner asked for.
        val fresh = Settings(language = settings.language, theme = settings.theme)
        _state.value = ShopState(settings = fresh)
        attempt { repository.replaceAll(ShopState(settings = fresh)) }
    }

    /**
     * Replaces the entire database with the contents of a backup.
     *
     * A **swap, not a merge** — the handoff is explicit, and the UI gates it
     * behind a warning naming what is about to be lost.
     */
    fun replaceEverything(document: BackupDocument) {
        val restored = Settings(
            ownerName = document.ownerName,
            // Part of the shop's identity on paper, so it travels with it.
            shopAddress = document.shopAddress.orEmpty(),
            // Currency, unlike language, is a property of the numbers in the
            // file: those prices were entered in it.
            currencyCode = document.currencyCode,
            // The language and the theme belong to the person holding this phone,
            // not to the file — a backup carried over from a shop that reads
            // English must not switch this one. Neither is written into a backup
            // for the same reason, so the document has nothing to take them from.
            language = settings.language,
            theme = settings.theme,
            // The imported file is a copy of *another* phone's backup, not a
            // backup of this one — the nudge stays on until this phone writes
            // its own.
            lastExportAt = null,
            setupCompleted = true,
            nextBillNumber = (document.bills.maxOfOrNull { it.number } ?: 0) + 1
        )

        val state = ShopState(
            products = document.products.map {
                Product(
                    uid = it.uid,
                    name = it.name,
                    stock = maxOf(0, it.stock),
                    cost = maxOf(0.0, it.cost),
                    price = maxOf(0.0, it.price)
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
            bills = document.bills.map { record ->
                Bill(
                    number = record.number,
                    lines = record.lines.map {
                        BillLine(
                            productUid = it.productUid,
                            name = it.name,
                            qty = it.qty,
                            price = it.price
                        )
                    },
                    total = record.total,
                    paid = record.paid,
                    who = record.who,
                    invoiceNo = record.invoiceNo,
                    photoIds = record.photoIds.orEmpty(),
                    note = record.note,
                    createdAt = record.createdAt
                )
            }.sortedByDescending { it.createdAt },
            customers = document.customers.map {
                CustomerRecord(
                    // The key comes from the file, not from re-deriving it: see
                    // `BackupDocument.CustomerRecordRow.key`.
                    key = it.key,
                    name = it.name,
                    phone = it.phone,
                    place = it.place,
                    openingBalance = it.openingBalance,
                    createdAt = it.createdAt
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
            payments = document.payments.map {
                Payment(
                    id = it.id,
                    customerKey = it.customerKey,
                    amount = it.amount,
                    paymentNo = it.paymentNo,
                    receivedAt = it.receivedAt,
                    note = it.note
                )
            }.sortedByDescending { it.receivedAt },
            suppliers = document.suppliers.map {
                SupplierRecord(
                    key = it.key,
                    name = it.name,
                    phone = it.phone,
                    place = it.place,
                    openingBalance = it.openingBalance,
                    createdAt = it.createdAt
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
            purchases = document.purchases.map {
                Purchase(
                    id = it.id,
                    supplierKey = it.supplierKey,
                    productUid = it.productUid,
                    name = it.name,
                    qty = it.qty,
                    unitCost = it.unitCost,
                    total = it.total,
                    paid = it.paid,
                    invoiceNo = it.invoiceNo,
                    createdAt = it.createdAt
                )
            }.sortedByDescending { it.createdAt },
            supplierPayments = document.supplierPayments.map {
                SupplierPayment(
                    id = it.id,
                    supplierKey = it.supplierKey,
                    amount = it.amount,
                    paymentNo = it.paymentNo,
                    paidAt = it.paidAt,
                    note = it.note
                )
            }.sortedByDescending { it.paidAt },
            creditNotes = document.creditNotes.map { row ->
                CreditNote(
                    id = row.id,
                    customerKey = row.customerKey,
                    lines = row.lines.map { BillLine(it.productUid, it.name, it.qty, it.price) },
                    total = row.total,
                    noteNo = row.noteNo,
                    reason = row.reason,
                    issuedAt = row.issuedAt
                )
            }.sortedByDescending { it.issuedAt },
            expenses = document.expenses.map {
                Expense(id = it.id, amount = it.amount, note = it.note, spentAt = it.spentAt)
            }.sortedByDescending { it.spentAt },
            settings = restored
        )

        attempt { repository.replaceAll(state) }
        _state.value = state
    }

    /** Snapshots the whole database into a backup document. */
    fun makeBackupDocument(at: Instant = Timestamps.now()): BackupDocument = BackupDocument(
        exportedAt = at,
        ownerName = settings.ownerName,
        // Absent rather than blank, so the two builds write the same bytes for a
        // shop that has never typed one.
        shopAddress = settings.shopAddress.ifBlank { null },
        expenses = expenses.map {
            BackupDocument.ExpenseRow(
                id = it.id,
                amount = it.amount,
                note = it.note,
                spentAt = it.spentAt
            )
        },
        creditNotes = creditNotes.map { note ->
            BackupDocument.CreditNoteRow(
                id = note.id,
                customerKey = note.customerKey,
                total = note.total,
                noteNo = note.noteNo,
                reason = note.reason,
                issuedAt = note.issuedAt,
                lines = note.lines.map {
                    BackupDocument.LineRecord(it.productUid, it.name, it.qty, it.price)
                }
            )
        },
        currencyCode = settings.currencyCode,
        products = products.map {
            BackupDocument.ProductRecord(it.uid, it.name, it.stock, it.cost, it.price)
        },
        bills = bills.map { bill ->
            BackupDocument.BillRecord(
                number = bill.number,
                createdAt = bill.createdAt,
                total = bill.total,
                paid = bill.paid,
                who = bill.who,
                invoiceNo = bill.invoiceNo,
                photoIds = bill.photoIds.takeIf { it.isNotEmpty() },
                note = bill.note,
                lines = bill.lines.map {
                    BackupDocument.LineRecord(it.productUid, it.name, it.qty, it.price)
                }
            )
        },
        customers = customerRecords.map {
            BackupDocument.CustomerRecordRow(
                key = it.key,
                name = it.name,
                phone = it.phone,
                place = it.place,
                openingBalance = it.openingBalance,
                createdAt = it.createdAt
            )
        },
        payments = payments.map {
            BackupDocument.PaymentRow(
                id = it.id,
                customerKey = it.customerKey,
                amount = it.amount,
                paymentNo = it.paymentNo,
                receivedAt = it.receivedAt,
                note = it.note
            )
        },
        suppliers = supplierRecords.map {
            BackupDocument.SupplierRecordRow(
                key = it.key,
                name = it.name,
                phone = it.phone,
                place = it.place,
                openingBalance = it.openingBalance,
                createdAt = it.createdAt
            )
        },
        purchases = purchases.map {
            BackupDocument.PurchaseRow(
                id = it.id,
                supplierKey = it.supplierKey,
                productUid = it.productUid,
                name = it.name,
                qty = it.qty,
                unitCost = it.unitCost,
                total = it.total,
                paid = it.paid,
                invoiceNo = it.invoiceNo,
                createdAt = it.createdAt
            )
        },
        supplierPayments = supplierPayments.map {
            BackupDocument.SupplierPaymentRow(
                id = it.id,
                supplierKey = it.supplierKey,
                amount = it.amount,
                paymentNo = it.paymentNo,
                paidAt = it.paidAt,
                note = it.note
            )
        }
    )

    companion object {
        /**
         * The shared completeness rule behind the product editor and setup step
         * 3. "Filled in" rather than "parses to a number", because a half-typed
         * value must not make the gate flicker.
         */
        fun isProductDraftComplete(name: String, stock: String, cost: String, price: String): Boolean =
            name.isNotBlank() &&
                stock.isNotBlank() &&
                cost.isNotBlank() &&
                (Money.parse(price) ?: 0.0) > 0
    }
}
