package com.stockbook.core.store

import com.stockbook.core.model.AppTheme
import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.CustomerRecord
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

enum class RestockMode {
    /** Topping up the bin. The buying price is left alone. */
    QUICK_ADD,

    /** A supplier delivery. A cost above zero becomes the buying price from now on. */
    PURCHASE
}

/**
 * Every rule that changes data lives here, and the current shop lives here too.
 *
 * Screens read [state] and never mutate it — the setters are private, so that is
 * enforced rather than merely asked for. Stock arithmetic, bill numbering,
 * snapshotting and the void/restock rules are all one layer, which is the layer
 * the tests drive. None of it knows Android exists.
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

    /** Deliveries that actually happened. Voided ones are history, not stock. */
    val livePurchases: List<Purchase> get() = purchases.filterNot { it.voided }

    val settings: Settings get() = _state.value.settings

    /** Bills that actually happened. Voided ones are history, not sales. */
    val liveBills: List<Bill> get() = bills.filterNot { it.voided }

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

    fun update(product: Product, name: String, stock: Int, cost: Double, price: Double) {
        val updated = product.copy(
            name = name.trim(),
            stock = maxOf(0, stock),
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
        invoiceNo: String? = null
    ): Bill? {
        val name = customer.trim()
        if (name.isEmpty()) return null

        val snapshots = mutableListOf<BillLine>()
        for (line in lines) {
            val product = product(line.productUid) ?: continue
            val quantity = maxOf(1, line.qty)
            snapshots.add(
                BillLine(
                    productUid = product.uid,
                    name = product.name,
                    qty = quantity,
                    price = line.price
                )
            )
            // Only an itemised bill moves the shelf. Nothing else in the app can
            // take stock off it, which is why a shop that types totals is told
            // the count is its own to keep straight.
            replace(product.copy(stock = maxOf(0, product.stock - quantity)))
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
     * **Voided bills are ignored.** Voiding and re-entering is how a bill typed
     * wrong gets corrected, and the wrong one must not hold the paper's number
     * hostage afterwards.
     */
    fun billWithInvoiceNo(invoiceNo: String?): Bill? {
        val key = InvoiceNo.key(invoiceNo)
        if (key.isEmpty()) return null
        return bills.firstOrNull { !it.voided && InvoiceNo.key(it.invoiceNo) == key }
    }

    /** The same question on the other side of the book. */
    fun purchaseWithInvoiceNo(invoiceNo: String?): Purchase? {
        val key = InvoiceNo.key(invoiceNo)
        if (key.isEmpty()) return null
        return purchases.firstOrNull { !it.voided && InvoiceNo.key(it.invoiceNo) == key }
    }

    /**
     * What to put in the bill-number field before anything is typed: one past the
     * last number the shop wrote.
     *
     * Null when there is nothing to go on — no bills yet, or the last number has
     * no digits in it. Blank is the honest answer there: the first number belongs
     * to the shop's own bill book, and guessing "1" would be the app inventing a
     * run the paper does not have.
     */
    fun nextInvoiceNo(): String? =
        InvoiceNo.next(bills.firstOrNull { !it.voided && !it.invoiceNo.isNullOrBlank() }?.invoiceNo)

    /** Voids a bill and puts its stock back. Bills are never deleted. */
    fun void(bill: Bill) {
        val existing = bills.firstOrNull { it.number == bill.number } ?: return
        if (existing.voided) return

        for (line in existing.lines) {
            val product = product(line.productUid) ?: continue
            replace(product.copy(stock = product.stock + line.qty))
        }
        val voided = existing.copy(voided = true)
        _state.value = _state.value.copy(
            bills = bills.map { if (it.number == voided.number) voided else it }
        )
        attempt { repository.update(voided) }
    }

    // --- Customers

    /**
     * Distinct customers from non-voided bills, **sorted by outstanding balance
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
            if (bill.voided || bill.who.isBlank()) continue
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
        note: String? = null
    ): Payment? {
        if (amount <= 0 || customerKey.isEmpty()) return null
        val payment = Payment(
            customerKey = customerKey,
            amount = amount,
            receivedAt = receivedAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(
            payments = (payments + payment).sortedByDescending { it.receivedAt }
        )
        attempt { repository.append(payment) }
        return payment
    }

    fun deletePayment(id: String) {
        _state.value = _state.value.copy(payments = payments.filterNot { it.id == id })
        attempt { repository.deletePayment(id) }
    }

    fun paymentsForCustomer(key: String): List<Payment> = payments.filter { it.customerKey == key }

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
            period = period
        )
    }

    /**
     * Every bill for one customer, voided ones included — history is never
     * hidden, only marked.
     */
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
            if (purchase.voided || purchase.supplierKey.isBlank()) continue
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
     * Voids a delivery and takes its stock back off the shelf.
     *
     * The mirror of voiding a bill, which puts stock back on. Idempotent: voiding
     * twice must not remove the stock twice, which is the bug this rule exists to
     * prevent on both sides.
     */
    fun voidPurchase(id: String) {
        val purchase = purchases.firstOrNull { it.id == id } ?: return
        if (purchase.voided) return
        val voided = purchase.copy(voided = true)
        _state.value = _state.value.copy(
            purchases = purchases.map { if (it.id == id) voided else it }
        )
        attempt { repository.update(voided) }

        // Only an itemised delivery put stock on the shelf, so only that one has
        // any to take back off.
        purchase.productUid?.takeIf { purchase.isItemised }?.let { uid ->
            product(uid)?.let { product ->
                // Floored at zero. The stock may already have been sold, and a
                // negative shelf count is a worse lie than an optimistic one.
                replace(product.copy(stock = maxOf(0, product.stock - purchase.qty)))
            }
        }
    }

    fun purchasesForSupplier(key: String): List<Purchase> = purchases.filter { it.supplierKey == key }

    fun purchasesForProduct(uid: String): List<Purchase> = purchases.filter { it.productUid == uid }

    // --- Money out

    fun recordSupplierPayment(
        supplierKey: String,
        amount: Double,
        paidAt: Instant = Timestamps.now(),
        note: String? = null
    ): SupplierPayment? {
        if (amount <= 0 || supplierKey.isEmpty()) return null
        val payment = SupplierPayment(
            supplierKey = supplierKey,
            amount = amount,
            paidAt = paidAt,
            note = CustomerRecord.tidied(note)
        )
        _state.value = _state.value.copy(supplierPayments = listOf(payment) + supplierPayments)
        attempt { repository.append(payment) }
        return payment
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

    /**
     * Adds stock. A zero or negative quantity is a no-op — the sheet treats
     * "nothing typed" as "close without doing anything".
     */
    fun restock(product: Product, quantity: Int, mode: RestockMode, unitCost: Double? = null) {
        if (quantity <= 0) return
        val current = this.product(product.uid) ?: return
        val cost = if (mode == RestockMode.PURCHASE && unitCost != null && unitCost > 0) {
            unitCost
        } else {
            current.cost
        }
        replace(current.copy(stock = current.stock + quantity, cost = cost))
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
                    createdAt = record.createdAt,
                    voided = record.voided
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
                    createdAt = it.createdAt,
                    voided = it.voided
                )
            }.sortedByDescending { it.createdAt },
            supplierPayments = document.supplierPayments.map {
                SupplierPayment(
                    id = it.id,
                    supplierKey = it.supplierKey,
                    amount = it.amount,
                    paidAt = it.paidAt,
                    note = it.note
                )
            }.sortedByDescending { it.paidAt },
            settings = restored
        )

        attempt { repository.replaceAll(state) }
        _state.value = state
    }

    /** Snapshots the whole database into a backup document. */
    fun makeBackupDocument(at: Instant = Timestamps.now()): BackupDocument = BackupDocument(
        exportedAt = at,
        ownerName = settings.ownerName,
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
                voided = bill.voided,
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
                createdAt = it.createdAt,
                voided = it.voided
            )
        },
        supplierPayments = supplierPayments.map {
            BackupDocument.SupplierPaymentRow(
                id = it.id,
                supplierKey = it.supplierKey,
                amount = it.amount,
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
