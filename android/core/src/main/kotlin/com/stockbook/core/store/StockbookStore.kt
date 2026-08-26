package com.stockbook.core.store

import com.stockbook.core.model.AppTheme
import com.stockbook.core.model.Bill
import com.stockbook.core.model.BalanceTransfer
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
import com.stockbook.core.model.PurchaseLine
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
import java.time.ZoneId

/** One line as the cart holds it, before it becomes history. */
data class DraftLine(
    val productUid: String,
    val qty: Int,
    /** What is being charged — the product's price unless overridden for this bill. */
    val price: Double
)

/**
 * What the shop spent on one thing over a stretch of days: what it was, how many
 * times, and what that came to.
 */
data class SpendLine(val what: String, val times: Int, val total: Double)

/**
 * What a stretch of trading actually left the shop with.
 *
 * Four figures and a confession. Takings, less what the goods on those bills
 * cost, is what the goods earned; less what the owner spent is what they kept.
 * The confession is [soldWithoutCost] — takings this cannot account for, because
 * the bill they came from listed no products and so carries no cost.
 *
 * **The gap is not an edge case.** Entering a paper bill as a single figure is
 * the ordinary way to use this app, and every such bill is revenue with no cost
 * behind it. A page that quietly answered for the rest of the month would be
 * flattering by exactly the amount it left out, so the amount it left out is on
 * the page.
 */
/**
 * What joining two accounts would move, worked out before anything is touched.
 *
 * The confirmation is the whole point of this type. A merge rewrites history —
 * it re-files somebody else's bills under this name — and there is no undo in
 * this app, so the owner is owed the figures *before* they agree rather than a
 * changed balance afterwards. It is also what the tests assert, which is how the
 * arithmetic and the sentence on screen are kept from drifting apart.
 *
 * One type for both sides of the book. A customer merge fills [bills],
 * [payments] and [creditNotes]; a supplier merge fills [deliveries] and
 * [payments]. A zero is a line the confirmation does not draw.
 */
data class MergePreview(
    /** The account that will be gone, by the name it is known by now. */
    val from: String,
    /** The one that survives. */
    val into: String,
    val bills: Int = 0,
    val payments: Int = 0,
    val creditNotes: Int = 0,
    val deliveries: Int = 0,
    /**
     * The two opening balances **added**.
     *
     * Two entries in the paper book for one firm are two real debts, and the
     * merge that used to happen by accident kept one and dropped the other. That
     * is the single figure most worth showing before the owner agrees.
     */
    val openingBalance: Double,
    /** What the survivor will owe once this is done. */
    val owed: Double
) {
    /** Whether there is any history to move at all. */
    val movesNothing: Boolean
        get() = bills == 0 && payments == 0 && creditNotes == 0 && deliveries == 0
}

data class Earnings(
    /**
     * Every bill in the period — the same figure Home shows, so the two can be
     * held side by side and agree.
     */
    val sold: Double,
    /**
     * How much of [sold] came from bills that listed no products at all.
     *
     * The owner's own choice, and a permanent one for those bills: a paper bill
     * entered as a single figure has nothing to cost. Itemising future ones is
     * the only thing that shrinks this.
     */
    val soldAsTotal: Double,
    val billsAsTotal: Int,
    /**
     * How much came from bills costed at **today's** buying price rather than at
     * the price recorded when they were sold.
     *
     * These are counted, and they are counted honestly labelled. A bill written
     * before the app kept costs has no figure of its own, so the only one
     * available is what the product costs now — near enough on a shelf whose
     * prices have not moved, and wrong by the drift where they have. Better than
     * a page that cannot answer at all, and only while the old book is still the
     * bulk of the shop's history.
     *
     * **Nothing is written back.** The line's stored cost stays absent, because
     * absent is the truth about it; this is an estimate made at the moment the
     * page is read, and it disappears from the page as costed bills replace the
     * old ones.
     */
    val soldEstimated: Double,
    val billsEstimated: Int,
    /**
     * And how much cannot be costed even by estimate, because a line names a
     * product that has since been deleted.
     *
     * Kept apart from [soldAsTotal] for the reason that one is kept apart at
     * all: the two ask different things of the owner. This one asks nothing —
     * there is no price left anywhere to use.
     */
    val soldBeforeCosts: Double,
    val billsBeforeCosts: Int,
    /** What the goods on the countable bills cost the shop, as at their sale. */
    val costOfGoods: Double,
    /**
     * What the goods a credit note brought **back** cost the shop.
     *
     * The mirror of [costOfGoods], and the reason a credit note can be taken off
     * the earnings honestly. A sale adds what was charged and takes off what the
     * goods cost; a return takes off what was credited and puts that cost back,
     * because those pieces are on the shelf again and were never really sold.
     *
     * Zero for a note written as a plain figure — "knock two hundred off" hands
     * nothing back — and that is not an approximation. It is why the whole
     * credited amount comes off such a note and only part of it comes off an
     * itemised one.
     */
    val goodsReturned: Double,
    /** What the owner spent over the same days. */
    val expenses: Double,
    /**
     * Credit notes written in the period, **disclosed and never subtracted**.
     *
     * `soldIn` counts bills and not notes, on the settled argument that a note
     * reduces what somebody *owes* without unselling the goods — and a month's
     * takings that shrank when a note was written weeks later is a figure nobody
     * can reconcile against the till. Netting them here and not there would put
     * two answers to "what did we sell" on two screens. So the owner is told the
     * notes exist and left to judge.
     */
    val credited: Double,
    val creditNotes: Int,
    /** Notes whose returned goods were valued at today's buying price. */
    val creditNotesEstimated: Int,
    /**
     * Notes with goods on them that could not be valued at all, because a line
     * names a product since deleted.
     *
     * Their goods are put back at nothing, which understates what the shop
     * earned rather than overstating it — the safe direction — and is said on the
     * page rather than left for the owner to find.
     *
     * A figure-only note is **not** one of these. It hands nothing back, so
     * nothing needs valuing and the full credit comes off correctly.
     */
    val creditNotesBeforeCosts: Int
) {
    /** Everything this page cannot account for, whichever of the two reasons. */
    val soldWithoutCost: Double get() = soldAsTotal + soldBeforeCosts
    val billsWithoutCost: Int get() = billsAsTotal + billsBeforeCosts

    /** Whether any of the costs on the page were guessed from the shelf as it stands now. */
    val hasEstimates: Boolean get() = billsEstimated > 0 || creditNotesEstimated > 0

    /** Takings this page can actually account for. */
    val counted: Double get() = sold - soldWithoutCost

    /**
     * Whether the period has takings but nothing costable in it.
     *
     * The state a shop is in the day cost-keeping arrives: every bill in the
     * book predates it, so the chain would run Sold → 0 → 0 and land on a
     * "kept" figure that is really just the month's expenses with a minus in
     * front. **That is not a loss, it is an absence**, and a page that prints
     * one as the other is worse than a page that admits it cannot say.
     */
    val nothingCostable: Boolean get() = sold > 0 && counted == 0.0

    /**
     * What the goods that actually left the shop cost it: what the sold ones
     * cost, less what the returned ones cost.
     *
     * The line the page draws. Netting the return in here rather than adding a
     * row of its own is what keeps every line on the page a figure the owner
     * recognises — and it is the truth about the figure: goods handed back are
     * goods the shop still has.
     */
    val netCostOfGoods: Double get() = costOfGoods - goodsReturned

    /** What the goods earned: what they sold for, less what they cost. */
    val goodsEarned: Double get() = counted - netCostOfGoods

    /**
     * And what was left after what was credited back and the owner's own
     * spending.
     *
     * [credited] comes off in full here, its goods having already been added
     * back through [netCostOfGoods]. Take the note off *and* put its stock back
     * and the arithmetic lands where it should: a customer credited 200 for
     * goods that cost 140 leaves the shop 60 worse off, not 200.
     */
    val kept: Double get() = goodsEarned - credited - expenses

    /** Whether anything was sold at all, countable or not. */
    val isEmpty: Boolean get() = sold == 0.0 && expenses == 0.0

    /**
     * Whether there is anything to confess.
     *
     * No longer true merely because a credit note exists: they are taken off the
     * figures now rather than listed beside them.
     */
    val hasGap: Boolean get() = billsWithoutCost > 0 || creditNotesBeforeCosts > 0
}

/**
 * What kind of thing happened, and — through [direction] — which way the money
 * moved when it did.
 *
 * Six kinds because six records carry a date, and a day that quietly left one
 * of them out would be a day the owner reconciles against the cash box and
 * cannot make balance.
 */
enum class DayEntryKind { BILL, PAYMENT, CREDIT_NOTE, DELIVERY, SUPPLIER_PAYMENT, EXPENSE }

/**
 * Which way each kind points: into the cash box, out of it, or neither.
 *
 * A `when` without an `else` on purpose. Add a seventh kind and this stops
 * compiling, which is the only reliable way to be asked whether it is money.
 *
 * **A credit note is neither.** It reduces what somebody owes without a coin
 * moving, and counting it as cash taken would overstate the day's takings by
 * exactly the amount the shop *gave back*.
 */
private val DayEntryKind.direction: Int
    get() = when (this) {
        DayEntryKind.BILL, DayEntryKind.PAYMENT -> 1
        DayEntryKind.DELIVERY, DayEntryKind.SUPPLIER_PAYMENT, DayEntryKind.EXPENSE -> -1
        DayEntryKind.CREDIT_NOTE -> 0
    }

/** One product on an itemised bill or delivery, as the day's page lists it. */
data class DayItem(val name: String, val qty: Int, val amount: Double)

/**
 * One thing that happened on one day, whichever of the six records it came from.
 *
 * Flattened to a common shape here rather than in the document, because the
 * question "what happened today" has one answer and two platforms both have to
 * give it. The alternative — six lists handed to a layout that decides how they
 * compare — is six chances for iOS and Android to disagree about a figure.
 */
data class DayEntry(
    val kind: DayEntryKind,
    /** The customer, the supplier, or — for the owner's own spending — what it went on. */
    val who: String,
    /** The number on the paper, when there is one: an invoice, a receipt, a credit note. */
    val reference: String? = null,
    /**
     * The app's own counter, on a bill that has no paper number. Carried rather
     * than resolved here because "Bill #7" is words, and words live in `Strings`.
     */
    val billNumber: Int? = null,
    /** What the whole thing came to. */
    val amount: Double,
    /**
     * What actually changed hands at the time — the part of [amount] that was
     * cash rather than credit. Equal to [amount] on a payment or an expense,
     * less on a bill part paid, zero on one written entirely on credit.
     */
    val settled: Double,
    /** What was on it, where the record says. Empty for a bill entered as a figure. */
    val items: List<DayItem> = emptyList(),
    val at: Instant
)

/**
 * One day of the shop, in the order it happened.
 *
 * **The owner's own page and nobody else's.** It names every customer billed
 * that day beside what the shop spent its money on, so it can no more be handed
 * across the counter than the receivable list can — and for the same two
 * reasons. Nothing here is ever called a statement.
 */
data class DayBook(val day: Instant, val entries: List<DayEntry>) {

    fun entriesOf(kind: DayEntryKind): List<DayEntry> = entries.filter { it.kind == kind }

    /**
     * What came into the cash box: taken at the counter on bills, plus receipts
     * against what was already owed.
     *
     * Summed from [DayEntry.settled] and never from [DayEntry.amount] — a bill
     * written on credit is a sale that took no money, and a day's takings that
     * counted it would be wrong by the whole of it.
     */
    val moneyIn: Double get() = sum(1)

    /** And what went out: paid to suppliers on the spot or since, and spent. */
    val moneyOut: Double get() = sum(-1)

    /** What the day did to the cash box, which may well be negative. */
    val net: Double get() = moneyIn - moneyOut

    val isEmpty: Boolean get() = entries.isEmpty()

    private fun sum(direction: Int): Double =
        entries.filter { it.kind.direction == direction }.sumOf { it.settled }
}

/** One line of a delivery as the sheet holds it, before it becomes history. */
data class DraftPurchaseLine(
    val productUid: String,
    val qty: Int,
    /** What the shop paid per piece. Zero falls back to the product's own cost. */
    val unitCost: Double = 0.0
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

    val balanceTransfers: List<BalanceTransfer> get() = _state.value.balanceTransfers

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
        /** What the bill was for. The owner's own reminder. */
        note: String? = null,
        /**
         * A percentage knocked off the whole bill, when the owner gave one.
         *
         * Applied here rather than by the screen, so the arithmetic that decides
         * what a customer owes lives in one tested place — and so the stored
         * total, the stored discount and the subtotal can never disagree.
         */
        discountPercent: Double? = null
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

        val subtotal = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        // A bill for nothing is not a bill. Either something was sold or a figure
        // was typed; neither is the same as a blank saved by accident. Checked on
        // the subtotal, so a 100% discount still saves a bill rather than
        // silently doing nothing — a line given away is a line that left the
        // shelf, and the shop should have the record.
        if (subtotal <= 0) return null

        val off = Money.discount(subtotal, discountPercent ?: 0.0, settings.currency)
        val total = subtotal - off
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
            discountPercent = discountPercent?.takeIf { off > 0 },
            discountAmount = off.takeIf { it > 0 },
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
                price = line.price,
                // Taken here, from the shelf, at the moment of sale — the only
                // moment it is knowable. `Product.cost` is "what it costs now"
                // and moves every time a delivery is priced, so a line that read
                // it later would answer with a figure from after the sale.
                cost = product.cost
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
        note: String? = null,
        discountPercent: Double? = null
        // Photographs are deliberately not a parameter here. They are added and
        // removed one at a time by [attachPhoto] and [detachPhoto], so an edit
        // form that knows nothing about them cannot wipe them by omission.
    ): Bill? {
        val existing = bills.firstOrNull { it.number == number } ?: return null
        val name = customer.trim()
        if (name.isEmpty()) return null

        val snapshots = snapshot(lines)
        val subtotal = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (subtotal <= 0) return null

        val off = Money.discount(subtotal, discountPercent ?: 0.0, settings.currency)
        val total = subtotal - off

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
            discountPercent = discountPercent?.takeIf { off > 0 },
            discountAmount = off.takeIf { it > 0 },
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

        val transfers = balanceTransfers.filterNot { it.isSupplier }
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

        // Both ends of every transfer, before either is asked for.
        //
        // `book[key]?.let` below drops anything not already in it **without a
        // sound** — the shape that stranded the credit notes on a merge. A party
        // reached only by a transfer has no bill and may have no roster entry,
        // so seeding is what keeps the two halves of one transfer from being
        // separated, which would leave the shop's total receivable wrong.
        for (transfer in transfers) {
            book.getOrPut(transfer.fromKey) { Tally(transfer.fromKey, 0, 0.0, 0.0) }
            book.getOrPut(transfer.intoKey) { Tally(transfer.intoKey, 0, 0.0, 0.0) }
        }
        for (transfer in transfers) {
            book[transfer.fromKey]?.let { it.owed -= transfer.amount }
            book[transfer.intoKey]?.let { it.owed += transfer.amount }
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
     * The customer a typed name would land on, where that is somebody other than
     * [exceptKey].
     *
     * Identity in this book is the name, so two accounts cannot share one. The
     * question this answers — *is that name already taken?* — is the whole of the
     * gate on renaming, and the form asks it while the owner types so the answer
     * arrives before the tap rather than after it.
     *
     * [exceptKey] is the account being edited. Passing it is what lets somebody
     * correct a phone number without the form objecting that the name they are
     * keeping already exists — and what lets a name that has only ever appeared
     * on bills be promoted onto the roster under its own spelling.
     */
    fun customerClashing(name: String, exceptKey: String? = null): Customer? {
        val key = Customer.key(name)
        if (key.isEmpty() || key == exceptKey) return null
        return customers().firstOrNull { it.key == key }
    }

    /**
     * Corrects the facts about a customer already on the roster.
     *
     * **Refuses, returning false, where the new name belongs to somebody else.**
     * It used to merge the two, and merging is defensible when the name is the
     * identity — but it happened on a keystroke, with no warning and no undo,
     * and it took the other account's opening balance with it. A mistyped
     * correction fused two companies' books and quietly changed what each of them
     * owed. Deliberately joining two accounts is a thing worth building; doing it
     * by accident is not, and the two are told apart by asking first.
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
    ): Boolean {
        if (name.isBlank()) return false
        val existing = customerRecords.firstOrNull { it.key == key } ?: return false
        val newKey = Customer.key(name)
        // The gate. Checked here and not only in the form, because a rename that
        // silently swallowed another account is the kind of thing that must be
        // impossible rather than merely discouraged.
        if (customerClashing(name, exceptKey = key) != null) return false
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
            return true
        }

        // Renamed, and onto a name nothing else answers to — the gate above saw
        // to that. Move the roster entry, then bring **everything filed under the
        // old key** with it.
        //
        // All four kinds, and the list has twice been short. Credit notes were
        // missing here for months: rename a credited customer and the note was
        // left under a key nothing pointed at, so it stopped coming off what they
        // owed and their balance silently rose by the credited amount. Balance
        // transfers would have been the same story, and were caught only because
        // a test asked what the statement calls the other end after a rename.
        //
        // The shape to distrust is `book[key]?.let { … }` in `customers()`: a
        // record whose key no longer exists is skipped **in silence**, so a
        // stranded row is never an error, only a wrong figure.
        _state.value = _state.value.copy(
            bills = bills.map { if (Customer.key(it.who) == key) it.copy(who = record.name) else it },
            payments = payments.map { if (it.customerKey == key) it.copy(customerKey = newKey) else it },
            creditNotes = creditNotes.map {
                if (it.customerKey == key) it.copy(customerKey = newKey) else it
            },
            balanceTransfers = balanceTransfers.map { moveTransfer(it, key, newKey, isSupplier = false) },
            customers = customerRecords.filterNot { it.key == key || it.key == newKey } + record
        )

        // Written whole rather than record by record. A rename now touches four
        // kinds at once and is rare and deliberate; half of one on disk is the
        // outcome worth spending a full rewrite to avoid, exactly as for a merge.
        attempt { repository.replaceAll(_state.value) }
        return true
    }

    /**
     * One transfer with [old] rewritten to [new] at whichever end it appears.
     *
     * Both ends, because a rename or a merge can touch either — and the two ends
     * of one transfer must never be separated, or the shop's total owed stops
     * balancing while both screens look fine.
     */
    private fun moveTransfer(
        transfer: BalanceTransfer,
        old: String,
        new: String,
        isSupplier: Boolean
    ): BalanceTransfer =
        if (transfer.isSupplier != isSupplier) {
            transfer
        } else {
            transfer.copy(
                fromKey = if (transfer.fromKey == old) new else transfer.fromKey,
                intoKey = if (transfer.intoKey == old) new else transfer.intoKey
            )
        }

    /**
     * Takes a customer off the roster. Their bills and payments stay: this
     * forgets the address book entry, not the trading history.
     */
    fun removeCustomer(key: String) {
        _state.value = _state.value.copy(customers = customerRecords.filterNot { it.key == key })
        attempt { repository.deleteCustomer(key) }
    }

    /**
     * What joining [from] into [into] would move. Null where either name is not a
     * customer, or where they are the same one.
     */
    fun previewCustomerMerge(from: String, into: String): MergePreview? {
        if (from == into) return null
        val goes = customer(from) ?: return null
        val stays = customer(into) ?: return null
        return MergePreview(
            from = goes.name,
            into = stays.name,
            bills = bills.count { Customer.key(it.who) == from },
            payments = payments.count { it.customerKey == from },
            creditNotes = creditNotes.count { it.customerKey == from },
            // Both `owed` figures already carry their opening balance, their
            // bills, their payments and their credit notes, so the survivor owes
            // exactly the sum. Rounded for the reason `customers()` rounds.
            openingBalance = goes.openingBalance + stays.openingBalance,
            owed = Math.round((goes.owed + stays.owed) * 100) / 100.0
        )
    }

    /**
     * Files one customer's whole history under another and takes the first off
     * the roster. **One firm entered twice becomes one account.**
     *
     * Deliberate, unlike the merge a rename used to do by accident — and it has
     * to move everything that accident forgot. Bills carry a *name* and so are
     * rewritten; payments and credit notes carry a key and are re-filed; the two
     * opening balances are **added**, because two entries in the paper book for
     * one firm are two debts really owed.
     *
     * Bills already handed across a counter said the old name. The app shows the
     * new one from here on, which is the point of merging and is the same thing a
     * rename has always done — the invoice number is untouched, so a slip in
     * somebody's file can still be found.
     *
     * Written through [StockbookRepository.replaceAll] rather than record by
     * record: credit notes are already saved that way, and a merge is rare,
     * deliberate and touches four kinds of record at once. Half a merge on disk
     * is the one outcome worth spending a whole rewrite to avoid.
     */
    fun mergeCustomer(from: String, into: String): Boolean {
        if (from == into || from.isEmpty() || into.isEmpty()) return false
        if (customer(from) == null) return false
        val stays = customer(into) ?: return false

        val leaving = customerRecords.firstOrNull { it.key == from }
        val kept = customerRecords.firstOrNull { it.key == into }
        val survivor = when {
            // Both on the roster: the surviving entry keeps its own name and
            // takes what the other was carrying. Its contact details win, and
            // fall back to the other's only where it has none — a blank field is
            // not a decision the owner made.
            kept != null -> kept.copy(
                openingBalance = kept.openingBalance + (leaving?.openingBalance ?: 0.0),
                phone = kept.phone ?: leaving?.phone,
                place = kept.place ?: leaving?.place
            )
            // Only the one going has an entry. It moves across under the
            // survivor's name rather than being deleted, or its opening balance
            // — a real debt — would go with it.
            leaving != null -> leaving.copy(key = into, name = stays.name)
            // Neither is on the roster: two names that have only ever appeared on
            // bills. There is nothing to keep, and the bills below are the whole
            // of the merge.
            else -> null
        }

        _state.value = _state.value.copy(
            bills = bills.map { if (Customer.key(it.who) == from) it.copy(who = stays.name) else it },
            payments = payments.map { if (it.customerKey == from) it.copy(customerKey = into) else it },
            creditNotes = creditNotes.map {
                if (it.customerKey == from) it.copy(customerKey = into) else it
            },
            balanceTransfers = balanceTransfers.map { moveTransfer(it, from, into, isSupplier = false) },
            customers = customerRecords.filterNot { it.key == from || it.key == into } +
                listOfNotNull(survivor)
        )
        attempt { repository.replaceAll(_state.value) }
        return true
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

    /**
     * What the owner has called an expense before, most-used first.
     *
     * A shop buys petrol every week and a fan belt once. Typing "Petrol" fifty
     * times a year is fifty chances to spell it three ways, and a ledger with
     * "Petrol", "petrol" and "Petrol " in it cannot be read as one thing.
     *
     * **Nothing new is stored for this.** These are the notes already on the
     * expenses themselves — the memory existed, it was simply never read back.
     * No new field, nothing added to the backup, no format version to bump.
     *
     * Grouped case-insensitively, and the spelling shown is the **most recent**
     * one: an owner who has started writing "Petrol" should be offered that
     * rather than the "petrol" they abandoned in March. Ties on how often go to
     * whichever was used last.
     *
     * @param matching what has been typed so far. Empty offers the usual few,
     *   which is the whole point of the list appearing before a key is pressed.
     * @param limit kept small on purpose. This is a shortcut for the handful of
     *   things a shop buys constantly, not a directory of everything it has ever
     *   bought — that is what the expenses list itself is.
     */
    fun expenseNotes(matching: String = "", limit: Int = 6): List<String> {
        val needle = matching.trim().lowercase()
        return expenses
            .filter { it.note.isNotBlank() }
            .groupBy { it.note.trim().lowercase() }
            .filterKeys { needle.isEmpty() || it.contains(needle) }
            .values
            .map { group ->
                val newest = group.maxBy { it.spentAt }
                Triple(newest.note.trim(), group.size, newest.spentAt)
            }
            .sortedWith(
                compareByDescending<Triple<String, Int, Instant>> { it.second }
                    .thenByDescending { it.third }
            )
            .take(limit)
            .map { it.first }
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
     * The same money, broken down by what it went on. Biggest first.
     *
     * A shop asking where last month went does not want forty-seven lines, it
     * wants "petrol 780, rent 2,000, tea 110". Grouping is what turns a list
     * into an answer.
     *
     * Grouped the way [expenseNotes] groups — case-insensitively, showing the
     * most recent spelling — so "Petrol", "petrol" and "PETROL" are one line
     * rather than three. That collapsing is worth as much here as it is in the
     * suggestion list, arguably more: three lines for one thing does not just
     * look untidy, it hides how much the shop actually spends on it.
     */
    fun spendingIn(period: StatementPeriod): List<SpendLine> {
        val range = period.range()
        return expenses
            .filter { it.spentAt in range && it.note.isNotBlank() }
            .groupBy { it.note.trim().lowercase() }
            .values
            .map { group ->
                SpendLine(
                    what = group.maxBy { it.spentAt }.note.trim(),
                    times = group.size,
                    total = group.sumOf { it.amount }
                )
            }
            .sortedWith(compareByDescending<SpendLine> { it.total }.thenBy { it.what })
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

    /**
     * What [period] left the shop with, and what it could not account for.
     *
     * **Cost comes off the bill's own lines, never off the shelf.** Each line
     * carries what one piece cost at the moment it was sold; `Product.cost` is
     * what it costs *now* and would rewrite last March every time a supplier put
     * a price up. It is also why this is not `boughtIn`: a hundred padlocks
     * delivered in March and three sold is not a March loss, and only the three
     * belong here.
     *
     * **The discount needs no apportioning.** `Bill.total` is stored after the
     * discount, so a bill's takings less its lines' cost is exactly what that
     * bill earned — no share-out across lines, and no rounding drift from doing
     * one.
     *
     * A bill is countable only when it lists products **and every line knows its
     * cost**. One line that does not is enough to set the whole bill aside:
     * counting the rest would subtract part of the cost from all of the takings,
     * which flatters the answer rather than admitting it cannot give one.
     */
    fun earningsIn(period: StatementPeriod): Earnings {
        val range = period.range()
        val inPeriod = bills.filter { it.createdAt in range }

        // What one line cost, preferring what was recorded at the sale and
        // falling back to what the product costs today. Null only where there is
        // no figure to be had at all — the product has been deleted, so even the
        // shelf cannot answer.
        fun unitCost(line: BillLine): Double? = line.cost ?: product(line.productUid)?.cost

        val asTotal = inPeriod.filterNot { it.isItemised }
        val itemised = inPeriod.filter { it.isItemised }
        val (costable, beforeCosts) = itemised.partition { bill ->
            bill.lines.all { unitCost(it) != null }
        }
        // Counted, but on today's prices rather than the day's. Labelled as such
        // on the page, and self-clearing: every bill written from now on carries
        // its own figure.
        val estimated = costable.filter { bill -> bill.lines.any { it.cost == null } }

        val notes = creditNotes.filter { it.issuedAt in range }

        return Earnings(
            sold = inPeriod.sumOf { it.total },
            soldAsTotal = asTotal.sumOf { it.total },
            billsAsTotal = asTotal.size,
            soldEstimated = estimated.sumOf { it.total },
            billsEstimated = estimated.size,
            soldBeforeCosts = beforeCosts.sumOf { it.total },
            billsBeforeCosts = beforeCosts.size,
            costOfGoods = costable.sumOf { bill ->
                bill.lines.sumOf { line -> line.qty * (unitCost(line) ?: 0.0) }
            },
            // A return is a sale run backwards, so it is costed the same way and
            // with the same fallback. A line whose product has since been
            // deleted puts nothing back, which understates what the shop earned
            // rather than overstating it — and the page says so.
            goodsReturned = notes.sumOf { note ->
                note.lines.sumOf { line -> line.qty * (unitCost(line) ?: 0.0) }
            },
            expenses = spentIn(period),
            credited = notes.sumOf { it.total },
            creditNotes = notes.size,
            // A note written as a plain figure hands nothing back, so there is
            // nothing to value and it belongs in neither count. The whole credit
            // comes off it, correctly, with nothing to disclose.
            creditNotesEstimated = notes.count { note ->
                note.lines.isNotEmpty() &&
                    note.lines.all { unitCost(it) != null } &&
                    note.lines.any { it.cost == null }
            },
            creditNotesBeforeCosts = notes.count { note ->
                note.lines.isNotEmpty() && note.lines.any { unitCost(it) == null }
            }
        )
    }

    /**
     * Everything that happened on one day, oldest first.
     *
     * The one place in this app that reads all six dated records together, and
     * the reason it exists: `soldIn` answers what was billed and `spentIn` what
     * was spent, but a shopkeeper closing up wants the day itself — what was
     * sold, what came in against it, what arrived, what went out — on one page
     * they can hold beside the cash box.
     *
     * Through [StatementPeriod.Custom] with the same date at both ends, which
     * already resolves to one whole day in the phone's own zone. There is one
     * idea of a span in this app and inventing a second for a single day is how
     * a bill written at ten to midnight starts landing on two of them.
     *
     * Names are read from [customers] and [suppliers] rather than from the
     * rosters directly, so a person is spelled here exactly as every other
     * screen spells them — roster spelling where there is one, the most recent
     * bill's otherwise. Both walk the whole book, which is work this does not
     * need but correctness this cannot do without: a day book naming somebody
     * differently from the statement it sits beside is a day book the owner
     * stops trusting.
     */
    fun dayBook(day: Instant, zone: ZoneId = ZoneId.systemDefault()): DayBook {
        val range = StatementPeriod.Custom(day, day).range(zone)
        val customerName = customers().associate { it.key to it.name }
        val supplierName = suppliers().associate { it.key to it.name }

        val entries = buildList {
            for (bill in bills.filter { it.createdAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.BILL,
                        // Through the same map the other five kinds go through,
                        // and not `bill.who` — that is the spelling typed at the
                        // counter, and one page carrying "ahmed contracting" on
                        // the bill and "Ahmed Contracting" on his payment reads
                        // as two people.
                        who = customerName[Customer.key(bill.who)] ?: bill.who,
                        reference = bill.invoiceNo,
                        billNumber = bill.number,
                        amount = bill.total,
                        // What the customer actually handed over. `balance` is
                        // zero on a bill paid in full, so this is the whole of
                        // it; on one written on credit it is nothing.
                        settled = bill.total - bill.balance,
                        items = bill.lines.map { DayItem(it.name, it.qty, it.lineTotal) },
                        at = bill.createdAt
                    )
                )
            }
            for (payment in payments.filter { it.receivedAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.PAYMENT,
                        who = customerName[payment.customerKey] ?: payment.customerKey,
                        reference = payment.paymentNo,
                        amount = payment.amount,
                        settled = payment.amount,
                        at = payment.receivedAt
                    )
                )
            }
            for (note in creditNotes.filter { it.issuedAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.CREDIT_NOTE,
                        who = customerName[note.customerKey] ?: note.customerKey,
                        reference = note.noteNo,
                        amount = note.total,
                        // Credited, not paid. Nothing left the cash box.
                        settled = 0.0,
                        items = note.lines.map { DayItem(it.name, it.qty, it.lineTotal) },
                        at = note.issuedAt
                    )
                )
            }
            for (purchase in purchases.filter { it.createdAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.DELIVERY,
                        who = supplierName[purchase.supplierKey] ?: purchase.supplierKey,
                        reference = purchase.invoiceNo,
                        amount = purchase.total,
                        settled = purchase.total - purchase.balance,
                        // `items`, never `lines` — a delivery entered before a
                        // delivery could hold more than one product keeps its
                        // itemisation only through here.
                        items = purchase.items.map { DayItem(it.name, it.qty, it.lineTotal) },
                        at = purchase.createdAt
                    )
                )
            }
            for (payment in supplierPayments.filter { it.paidAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.SUPPLIER_PAYMENT,
                        who = supplierName[payment.supplierKey] ?: payment.supplierKey,
                        reference = payment.paymentNo,
                        amount = payment.amount,
                        settled = payment.amount,
                        at = payment.paidAt
                    )
                )
            }
            for (expense in expenses.filter { it.spentAt in range }) {
                add(
                    DayEntry(
                        kind = DayEntryKind.EXPENSE,
                        // An expense is joined to nobody, so what it went on is
                        // the only name it has.
                        who = expense.note,
                        amount = expense.amount,
                        settled = expense.amount,
                        at = expense.spentAt
                    )
                )
            }
        }

        return DayBook(day = day, entries = entries.sortedBy { it.at })
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
            transfers = transferEntriesFor(key, isSupplier = false),
            period = period
        )
    }


    // --- Moving a balance between two accounts

    /**
     * Moves [amount] of what one account owes onto another, both of them real.
     *
     * **Not [mergeCustomer].** That one says two rows were always the same firm
     * and re-files the loser's history under the survivor. This says both are
     * genuine — two branches of one contractor, say — and only the outstanding
     * figure moves. The invoices stay where they were issued, because the copy
     * in the customer's file says which branch it went to.
     *
     * Refused between an account and itself, and where either side is unknown.
     * An amount larger than what is owed is allowed: the app already reads a
     * negative balance as money held in advance.
     */
    fun transferBalance(
        fromKey: String,
        intoKey: String,
        amount: Double,
        isSupplier: Boolean = false,
        note: String? = null,
        movedAt: Instant = Timestamps.now()
    ): BalanceTransfer? {
        if (fromKey == intoKey || fromKey.isEmpty() || intoKey.isEmpty()) return null
        if (amount <= 0) return null
        val known: (String) -> Boolean =
            if (isSupplier) { key -> supplier(key) != null } else { key -> customer(key) != null }
        if (!known(fromKey) || !known(intoKey)) return null

        val transfer = BalanceTransfer(
            fromKey = fromKey,
            intoKey = intoKey,
            isSupplier = isSupplier,
            amount = amount,
            note = CustomerRecord.tidied(note),
            movedAt = movedAt
        )
        _state.value = _state.value.copy(
            balanceTransfers = (balanceTransfers + transfer).sortedByDescending { it.movedAt }
        )
        attempt { repository.replaceAll(_state.value) }
        return transfer
    }

    /**
     * Removes one. A mistake is edited or removed, not voided — and unlike a
     * bill there is no stock to give back, so this is the whole of it.
     */
    fun deleteBalanceTransfer(id: String) {
        _state.value = _state.value.copy(balanceTransfers = balanceTransfers.filterNot { it.id == id })
        attempt { repository.replaceAll(_state.value) }
    }

    /**
     * One account's transfers as statement entries, each already knowing which
     * end it is and what the account at the other end is called.
     *
     * The name is resolved here rather than stored on the record, so a party
     * renamed afterwards reads correctly and there is no second copy to drift.
     */
    fun transferEntriesFor(key: String, isSupplier: Boolean): List<Statement.Entry.ForTransfer> {
        val names = if (isSupplier) {
            suppliers().associate { it.key to it.name }
        } else {
            customers().associate { it.key to it.name }
        }
        return balanceTransfers
            .filter { it.isSupplier == isSupplier && (it.fromKey == key || it.intoKey == key) }
            .map { transfer ->
                val outgoing = transfer.fromKey == key
                val other = if (outgoing) transfer.intoKey else transfer.fromKey
                Statement.Entry.ForTransfer(
                    transfer = transfer,
                    outgoing = outgoing,
                    otherName = names[other] ?: other
                )
            }
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

        val transfers = balanceTransfers.filter { it.isSupplier }
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

        // Both ends seeded before either is asked for, exactly as on the
        // customer side and for the same reason: `book[key]?.let` drops what is
        // not already there without a sound, and half a transfer is a book that
        // no longer balances.
        for (transfer in transfers) {
            book.getOrPut(transfer.fromKey) { Tally(transfer.fromKey, 0, 0.0, 0.0) }
            book.getOrPut(transfer.intoKey) { Tally(transfer.intoKey, 0, 0.0, 0.0) }
        }
        for (transfer in transfers) {
            book[transfer.fromKey]?.let { it.owed -= transfer.amount }
            book[transfer.intoKey]?.let { it.owed += transfer.amount }
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

    /** The supplier a typed name would land on. The twin of [customerClashing]. */
    fun supplierClashing(name: String, exceptKey: String? = null): Supplier? {
        val key = Supplier.key(name)
        if (key.isEmpty() || key == exceptKey) return null
        return suppliers().firstOrNull { it.key == key }
    }

    /**
     * Corrects a supplier. A changed name that produces a different key is a
     * **rename**, and the purchases move with it — they carry the key, so unlike a
     * bill there is no spelling to rewrite, which makes this the simpler half of
     * the pair.
     *
     * **Refuses, returning false, where the new name belongs to somebody else**,
     * for the reason [updateCustomer] gives: a rename that swallows another
     * account is a merge, and a merge nobody asked for is data loss.
     */
    fun updateSupplier(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0.0
    ): Boolean {
        if (name.isBlank()) return false
        val existing = supplierRecords.firstOrNull { it.key == key } ?: return false
        val newKey = Supplier.key(name)
        if (supplierClashing(name, exceptKey = key) != null) return false
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
            },
            balanceTransfers = balanceTransfers.map { moveTransfer(it, key, newKey, isSupplier = true) }
        )
        // Disk follows memory, in the same order: the old roster entry goes, the
        // corrected one lands, and every moved purchase and payment is rewritten
        // under the new key.
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
        return true
    }

    fun removeSupplier(key: String) {
        _state.value = _state.value.copy(suppliers = supplierRecords.filterNot { it.key == key })
        attempt { repository.deleteSupplier(key) }
    }

    /** What joining [from] into [into] would move. The twin of [previewCustomerMerge]. */
    fun previewSupplierMerge(from: String, into: String): MergePreview? {
        if (from == into) return null
        val goes = supplier(from) ?: return null
        val stays = supplier(into) ?: return null
        return MergePreview(
            from = goes.name,
            into = stays.name,
            payments = supplierPayments.count { it.supplierKey == from },
            deliveries = purchases.count { it.supplierKey == from },
            openingBalance = goes.openingBalance + stays.openingBalance,
            owed = Math.round((goes.owed + stays.owed) * 100) / 100.0
        )
    }

    /**
     * The twin of [mergeCustomer], and the simpler half: a delivery carries the
     * supplier's key rather than their name, so there is no spelling to rewrite.
     */
    fun mergeSupplier(from: String, into: String): Boolean {
        if (from == into || from.isEmpty() || into.isEmpty()) return false
        if (supplier(from) == null) return false
        val stays = supplier(into) ?: return false

        val leaving = supplierRecords.firstOrNull { it.key == from }
        val kept = supplierRecords.firstOrNull { it.key == into }
        val survivor = when {
            kept != null -> kept.copy(
                openingBalance = kept.openingBalance + (leaving?.openingBalance ?: 0.0),
                phone = kept.phone ?: leaving?.phone,
                place = kept.place ?: leaving?.place
            )
            leaving != null -> leaving.copy(key = into, name = stays.name)
            else -> null
        }

        _state.value = _state.value.copy(
            purchases = purchases.map { if (it.supplierKey == from) it.copy(supplierKey = into) else it },
            supplierPayments = supplierPayments.map {
                if (it.supplierKey == from) it.copy(supplierKey = into) else it
            },
            balanceTransfers = balanceTransfers.map { moveTransfer(it, from, into, isSupplier = true) },
            suppliers = supplierRecords.filterNot { it.key == from || it.key == into } +
                listOfNotNull(survivor)
        )
        attempt { repository.replaceAll(_state.value) }
        return true
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
        /** Every product on the delivery note. Empty for a bill entered as a figure. */
        lines: List<DraftPurchaseLine>,
        supplierKey: String,
        paid: Double? = null,
        /** What the bill came to, where no product was named. */
        amount: Double? = null,
        createdAt: Instant = Timestamps.now(),
        /** The number on the supplier's invoice. */
        invoiceNo: String? = null
    ): Purchase? {
        if (supplierKey.isBlank()) return null

        val snapshots = snapshotDelivery(lines)
        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (total <= 0) return null

        val purchase = Purchase(
            supplierKey = supplierKey,
            lines = snapshots,
            total = total,
            // Clamped to the total: a delivery cannot be overpaid, and a typo
            // that says so would put the shop permanently in credit.
            paid = paid?.let { maxOf(0.0, minOf(it, total)) },
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            createdAt = createdAt
        )
        _state.value = _state.value.copy(purchases = listOf(purchase) + purchases)
        attempt { repository.append(purchase) }
        putOnShelf(snapshots)
        return purchase
    }

    /**
     * The one-product delivery, which is what most of them are.
     *
     * Kept as a way in rather than folded into the caller, because a delivery of
     * one thing is the common case and `recordPurchase(listOf(DraftPurchaseLine(…)))`
     * says the same thing three words longer at every call site.
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
        amount: Double? = null,
        createdAt: Instant = Timestamps.now(),
        invoiceNo: String? = null
    ): Purchase? = recordPurchase(
        lines = draftOf(product, quantity, unitCost),
        supplierKey = supplierKey,
        paid = paid,
        amount = amount,
        createdAt = createdAt,
        invoiceNo = invoiceNo
    )

    /**
     * Itemised only when a product was named **and** a count came with it: a
     * product with no quantity is half an answer, and guessing the other half
     * would put stock on the shelf nobody said arrived.
     */
    private fun draftOf(product: Product?, quantity: Int, unitCost: Double): List<DraftPurchaseLine> =
        if (product == null || quantity <= 0) {
            emptyList()
        } else {
            listOf(DraftPurchaseLine(product.uid, quantity, unitCost))
        }

    /**
     * Names and costs each line from the shelf, dropping any product that is no
     * longer there. The mirror of [snapshot], which does the same for a bill.
     *
     * A zero cost falls back to what the product already cost: the sheet leaves
     * the box empty when the price has not changed since last time, and reading
     * that as free would rewrite the product's cost to nothing.
     */
    private fun snapshotDelivery(lines: List<DraftPurchaseLine>): List<PurchaseLine> =
        lines.mapNotNull { line ->
            val product = product(line.productUid) ?: return@mapNotNull null
            if (line.qty <= 0) return@mapNotNull null
            PurchaseLine(
                productUid = product.uid,
                name = product.name,
                qty = line.qty,
                unitCost = if (line.unitCost > 0) line.unitCost else product.cost
            )
        }

    /**
     * Puts a delivery's lines on the shelf.
     *
     * Re-read one line at a time rather than mapped in one pass: a delivery note
     * may name the same product twice — two boxes at two prices is an ordinary
     * thing on a supplier's paper — and a stale count captured before the first
     * line would silently swallow the second.
     *
     * Cost is "latest paid", not a weighted average: the new figure simply takes
     * over, so the last line for a product is the one that sets it.
     */
    private fun putOnShelf(lines: List<PurchaseLine>) {
        for (line in lines) {
            val uid = line.productUid ?: continue
            val onShelf = product(uid) ?: continue
            replace(onShelf.copy(stock = onShelf.stock + line.qty, cost = line.unitCost))
        }
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
        lines: List<DraftPurchaseLine>,
        supplierKey: String,
        paid: Double? = null,
        amount: Double? = null,
        createdAt: Instant,
        invoiceNo: String? = null
    ): Purchase? {
        val existing = purchases.firstOrNull { it.id == id } ?: return null
        if (supplierKey.isBlank()) return null

        val snapshots = snapshotDelivery(lines)
        val total = if (snapshots.isEmpty()) amount ?: 0.0 else snapshots.sumOf { it.lineTotal }
        if (total <= 0) return null

        // Reverse the old, then apply the new — the same order as on a bill, and
        // for the same reason. `putOnShelf` re-reads each product, so a line the
        // edit kept is not added to a count captured before it was taken off.
        takeBackStock(existing)
        putOnShelf(snapshots)

        val updated = existing.copy(
            supplierKey = supplierKey,
            lines = snapshots,
            total = total,
            paid = paid?.let { maxOf(0.0, minOf(it, total)) },
            invoiceNo = CustomerRecord.tidied(invoiceNo),
            createdAt = createdAt,
            // A record written when a delivery held one product is rewritten into
            // the new shape. Left in place they would be a second answer to what
            // arrived, and `items` prefers `lines` — so the old figures would sit
            // there unread, waiting to be believed by something.
            productUid = null,
            name = null,
            qty = 0,
            unitCost = 0.0
        )
        _state.value = _state.value.copy(
            purchases = purchases.map { if (it.id == id) updated else it }
        )
        attempt { repository.update(updated) }
        return updated
    }

    /** The one-product correction, the way in for a screen that has one product. */
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
    ): Purchase? = updatePurchase(
        id = id,
        lines = draftOf(product, quantity, unitCost),
        supplierKey = supplierKey,
        paid = paid,
        amount = amount,
        createdAt = createdAt,
        invoiceNo = invoiceNo
    )

    /** Removes a supplier's bill and takes its stock back off the shelf. */
    fun deletePurchase(id: String) {
        val purchase = purchases.firstOrNull { it.id == id } ?: return
        takeBackStock(purchase)
        _state.value = _state.value.copy(purchases = purchases.filterNot { it.id == id })
        attempt { repository.deletePurchase(id) }
    }

    /**
     * Unwinds what a delivery put on the shelf, line by line. Only an itemised
     * one put anything there, so only that one has any to take back.
     *
     * Reads [Purchase.items] rather than `lines`, so a delivery recorded when a
     * delivery held one product still gives its stock back.
     */
    private fun takeBackStock(purchase: Purchase) {
        for (line in purchase.items) {
            val uid = line.productUid ?: continue
            val product = product(uid) ?: continue
            // Floored at zero. The stock may already have been sold, and a
            // negative shelf count is a worse lie than an optimistic one.
            replace(product.copy(stock = maxOf(0, product.stock - line.qty)))
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
            transfers = transferEntriesFor(key, isSupplier = true),
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
                            price = it.price,
                            cost = it.cost
                        )
                    },
                    total = record.total,
                    paid = record.paid,
                    who = record.who,
                    invoiceNo = record.invoiceNo,
                    photoIds = record.photoIds.orEmpty(),
                    note = record.note,
                    discountPercent = record.discountPercent,
                    discountAmount = record.discountAmount,
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
                    lines = it.lines.map { line ->
                        PurchaseLine(
                            productUid = line.productUid,
                            name = line.name,
                            qty = line.qty,
                            unitCost = line.unitCost
                        )
                    },
                    total = it.total,
                    paid = it.paid,
                    invoiceNo = it.invoiceNo,
                    createdAt = it.createdAt,
                    // Carried through rather than dropped, so a file written by
                    // an older build keeps what arrived on its deliveries.
                    // `items` prefers `lines`, so on any file written since, the
                    // four below are absent and read as nothing.
                    productUid = it.productUid,
                    name = it.name,
                    qty = it.qty,
                    unitCost = it.unitCost
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
                    lines = row.lines.map { BillLine(it.productUid, it.name, it.qty, it.price, it.cost) },
                    total = row.total,
                    noteNo = row.noteNo,
                    reason = row.reason,
                    issuedAt = row.issuedAt
                )
            }.sortedByDescending { it.issuedAt },
            expenses = document.expenses.map {
                Expense(id = it.id, amount = it.amount, note = it.note, spentAt = it.spentAt)
            }.sortedByDescending { it.spentAt },
            balanceTransfers = document.balanceTransfers.map {
                BalanceTransfer(
                    id = it.id,
                    fromKey = it.fromKey,
                    intoKey = it.intoKey,
                    isSupplier = it.isSupplier,
                    amount = it.amount,
                    note = it.note,
                    movedAt = it.movedAt
                )
            }.sortedByDescending { it.movedAt },
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
        balanceTransfers = balanceTransfers.map {
            BackupDocument.BalanceTransferRow(
                id = it.id,
                fromKey = it.fromKey,
                intoKey = it.intoKey,
                isSupplier = it.isSupplier,
                amount = it.amount,
                note = it.note,
                movedAt = it.movedAt
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
                    BackupDocument.LineRecord(it.productUid, it.name, it.qty, it.price, it.cost)
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
                discountPercent = bill.discountPercent,
                discountAmount = bill.discountAmount,
                lines = bill.lines.map {
                    BackupDocument.LineRecord(it.productUid, it.name, it.qty, it.price, it.cost)
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
                // `items`, not `lines`: a delivery recorded when a delivery held
                // one product travels in the new shape rather than the old one,
                // so the file coming out has exactly one way of saying what
                // arrived.
                lines = it.items.map { line ->
                    BackupDocument.PurchaseLineRecord(
                        productUid = line.productUid,
                        name = line.name,
                        qty = line.qty,
                        unitCost = line.unitCost
                    )
                },
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
