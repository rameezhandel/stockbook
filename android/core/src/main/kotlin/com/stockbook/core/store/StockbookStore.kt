package com.stockbook.core.store

import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.CustomerRecord
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Product
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
    fun saveBill(lines: List<DraftLine>, customer: String, paid: Double?): Bill? {
        val name = customer.trim()
        if (lines.isEmpty() || name.isEmpty()) return null

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
            replace(product.copy(stock = maxOf(0, product.stock - quantity)))
        }
        if (snapshots.isEmpty()) return null

        val total = snapshots.sumOf { it.lineTotal }
        val bill = Bill(
            number = settings.nextBillNumber,
            lines = snapshots,
            total = total,
            // Paying the whole amount is paid in full, not a part payment of the
            // total — otherwise the receipt says somebody owes zero.
            paid = paid?.takeIf { it < total }?.coerceIn(0.0, total),
            who = name
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

        // Payments come off what is owed. Without this the Bills filter, the
        // Today banner and every statement would go on claiming money that is
        // already in the till.
        for (payment in payments) {
            book[payment.customerKey]?.let { it.owed -= payment.amount }
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

    // --- Restock

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
        // Everything goes except the language. Wiping the shop is a data
        // decision; being handed setup in a language you cannot read is not one
        // the owner asked for.
        val fresh = Settings(language = settings.language)
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
            currencyCode = document.currencyCode
                ?: Currency.matching(document.currencySymbol)?.code
                ?: Currency.default.code,
            // The language belongs to the person holding this phone, not to the
            // file — a backup carried over from a shop that reads English must
            // not switch this one.
            language = settings.language,
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
                    createdAt = record.createdAt,
                    voided = record.voided
                )
            }.sortedByDescending { it.createdAt },
            customers = document.customers.orEmpty().map {
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
            payments = document.payments.orEmpty().map {
                Payment(
                    id = it.id,
                    customerKey = it.customerKey,
                    amount = it.amount,
                    receivedAt = it.receivedAt,
                    note = it.note
                )
            }.sortedByDescending { it.receivedAt },
            settings = restored
        )

        attempt { repository.replaceAll(state) }
        _state.value = state
    }

    /** Snapshots the whole database into a backup document. */
    fun makeBackupDocument(at: Instant = Timestamps.now()): BackupDocument = BackupDocument(
        exportedAt = at,
        ownerName = settings.ownerName,
        currencySymbol = settings.currency.symbol,
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
