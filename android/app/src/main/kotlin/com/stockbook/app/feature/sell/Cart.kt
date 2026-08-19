package com.stockbook.app.feature.sell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Product
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.DraftLine

/** Full payment, or part. */
enum class PayMode { FULL, PART }

/**
 * The bill being built.
 *
 * **Transient by design.** A half-typed bill is not history and must not survive
 * a relaunch, so nothing here touches the repository until Save. That is the
 * same boundary the iOS build draws, and the reason the cart is not part of the
 * store.
 */
class Cart {

    data class Line(
        val productUid: String,
        val name: String,
        val qty: Int,
        val price: Double,
        /** The product's list price, so an override can be undone. */
        val basePrice: Double
    ) {
        val lineTotal: Double get() = qty * price
        val isPriceOverridden: Boolean get() = price != basePrice
    }

    private val _lines = mutableStateListOf<Line>()
    val lines: List<Line> get() = _lines

    /**
     * Photographs of the paper bill, taken while it is being written.
     *
     * Ids, not pictures — the files are already on disk by the time one lands
     * here, written by `PhotoStore`. Held on the form rather than attached
     * straight away because on a new bill there is nothing to attach them to
     * yet: the bill does not exist until Save.
     *
     * A photograph taken against a bill that is then abandoned leaves a file
     * nothing refers to, which the sweep collects on the next launch. That is
     * the right way round — the alternative is deleting a picture the owner
     * might have meant to keep.
     */
    private val _photoIds = mutableStateListOf<String>()
    val photoIds: List<String> get() = _photoIds

    fun addPhoto(id: String) {
        if (id !in _photoIds) _photoIds.add(id)
    }

    fun removePhoto(id: String) {
        _photoIds.remove(id)
    }

    /**
     * The customer's name as it will be written on the bill.
     *
     * Set either by typing or by choosing from the list, but only a *choice*
     * counts — see [customerKey]. Assigned through [typeCustomer] and
     * [selectCustomer] rather than directly, so the two can never disagree.
     */
    var customer by mutableStateOf("")
        private set

    /**
     * The chosen customer's key, or null when nobody has been chosen yet.
     *
     * This is what gates saving. A typed name that matches nobody is not a
     * customer, and letting it through is how "Ahmed", "ahmed " and "Ahmd" became
     * three people with three balances — the thing the roster exists to stop.
     */
    var customerKey by mutableStateOf<String?>(null)
        private set
    var payMode by mutableStateOf(PayMode.FULL)
    var paidText by mutableStateOf("")

    /**
     * What the bill came to, typed rather than computed.
     *
     * The ordinary case in this shop: the paper bill was written before the app
     * was opened, so the figure is already known and rebuilding it product by
     * product to arrive at it is work for nothing. Held as text rather than a
     * number so a half-typed "45" is not a bill for forty-five riyals.
     *
     * Ignored the moment there are lines — see [total]. Two answers to "what did
     * it come to" is one too many, and the lines are the ones with arithmetic
     * behind them.
     */
    var amountText by mutableStateOf("")

    /**
     * The number written on the paper bill, when the shop wrote one. Free text:
     * bill books are numbered "1024" in some shops and "A-1024" in others.
     */
    var invoiceNo by mutableStateOf("")

    /**
     * When the sale happened, which is not always when it is being typed.
     *
     * A shop that writes bills in the book all day and enters them at closing
     * time would otherwise stamp the whole day at once — and the statements, which
     * are what somebody settles up against, would inherit that.
     */
    var soldAt by mutableStateOf(Timestamps.now())

    val isEmpty: Boolean get() = _lines.isEmpty()

    /**
     * What the bill comes to: the typed figure until something is on it, the sum
     * of the lines from then on.
     *
     * The same rule `StockbookStore.saveBill` applies to what it is handed, said
     * once more here because the screen has to show the figure it is about to
     * save. If these two ever disagree the owner is looking at one number and
     * saving another.
     */
    val total: Double
        get() = if (_lines.isEmpty()) typedAmount ?: 0.0 else _lines.sumOf { it.lineTotal }

    /** The figure in the amount box, or null when there is nothing readable in it. */
    val typedAmount: Double? get() = Money.parse(amountText)

    val paidValue: Double get() = Money.parse(paidText) ?: 0.0

    val balance: Double
        get() = if (payMode == PayMode.FULL) 0.0 else (total - paidValue).coerceAtLeast(0.0)

    /** What gets stored: null for paid in full. */
    val paidForStorage: Double? get() = if (payMode == PayMode.FULL) null else paidValue

    /**
     * A bill needs a figure, somebody **chosen** to give it to, and a number.
     *
     * A figure rather than a line: what was sold is optional, and a bill saying
     * only that Ahmed owes 450 is the shape of this shop. What it may never be is
     * a bill for nothing — [total] above zero is the whole of that test, however
     * the figure was arrived at.
     *
     * Not merely a non-blank name: a name nobody picked from the list is a name
     * with no account behind it, so nothing could be owed to it or settled against
     * it later.
     *
     * The number is required because the shop writes one on every bill it hands
     * over, and a record with none cannot be matched to the paper it came from —
     * which is the whole reason for keeping the number at all. Always typed,
     * never suggested: a guessed next value is the app inventing a run the paper
     * does not have.
     */
    val canSave: Boolean get() = customerKey != null && invoiceNo.isNotBlank() && total > 0

    /** Typed into the field. Invalidates any earlier choice, deliberately. */
    fun typeCustomer(text: String) {
        customer = text
        // Choosing Ahmed and then editing the text must not save a bill against
        // Ahmed's account under a name that is no longer his.
        customerKey = null
    }

    /** Chosen from the list. Takes the roster's spelling, not whatever was typed. */
    fun selectCustomer(chosen: Customer) {
        customer = chosen.name
        customerKey = chosen.key
    }

    val draftLines: List<DraftLine>
        get() = _lines.map { DraftLine(it.productUid, it.qty, it.price) }

    fun quantity(productUid: String): Int =
        _lines.firstOrNull { it.productUid == productUid }?.qty ?: 0

    /** Adding a product already on the bill increments it rather than repeating it. */
    fun add(product: Product) {
        val index = _lines.indexOfFirst { it.productUid == product.uid }
        if (index >= 0) {
            _lines[index] = _lines[index].copy(qty = _lines[index].qty + 1)
        } else {
            _lines.add(
                Line(
                    productUid = product.uid,
                    name = product.name,
                    qty = 1,
                    price = product.price,
                    basePrice = product.price
                )
            )
        }
    }

    /** Dropping to zero removes the line — a bill cannot carry nothing of something. */
    fun setQuantity(quantity: Int, productUid: String) {
        val index = _lines.indexOfFirst { it.productUid == productUid }
        if (index < 0) return
        if (quantity <= 0) _lines.removeAt(index)
        else _lines[index] = _lines[index].copy(qty = quantity)
    }

    fun setPrice(price: Double, productUid: String) {
        val index = _lines.indexOfFirst { it.productUid == productUid }
        if (index < 0) return
        _lines[index] = _lines[index].copy(price = price.coerceAtLeast(0.0))
    }

    fun resetPrice(productUid: String) {
        val index = _lines.indexOfFirst { it.productUid == productUid }
        if (index < 0) return
        _lines[index] = _lines[index].copy(price = _lines[index].basePrice)
    }

    fun remove(productUid: String) {
        _lines.removeAll { it.productUid == productUid }
    }

    /**
     * Takes every line off, leaving the rest of the bill as it was typed.
     *
     * Behind "Remove items": the way back from an itemised bill to one that is
     * simply a figure. Deliberately not [clear] — the customer, the number and
     * the date were right before the owner started adding products and are still
     * right after they stop.
     */
    fun removeLines() {
        _lines.clear()
    }

    /**
     * Fills the form from a bill that already exists, so a correction is typed on
     * the same screen the bill was.
     *
     * Everything the document carries comes back: what was on it, who it was for,
     * the figure where it had no lines, the day and the number.
     *
     * A line whose product has since been deleted is **dropped here**, because
     * `StockbookStore.saveBill` would drop it on the way in anyway: showing it and
     * then saving without it is the one thing worse than showing the bill a line
     * short, since the total on screen would not be the total that got saved.
     */
    fun fill(bill: Bill, products: List<Product>, currency: Currency) {
        // Nothing worth keeping is not worth restoring: a form the owner has not
        // touched should come back empty rather than with a stale prefilled
        // number in it.
        stashed = if (_lines.isNotEmpty() || customerKey != null || typedAmount != null) snapshot() else null

        _lines.clear()
        for (line in bill.lines) {
            val product = products.firstOrNull { it.uid == line.productUid } ?: continue
            _lines.add(
                Line(
                    productUid = product.uid,
                    // The product's name and list price as they stand today — the
                    // name because saving snapshots it again from the product, and
                    // the price because "usual price" needs something to reset to.
                    // What was actually charged stays the bill's own figure, so an
                    // edit cannot silently reprice a line nobody touched.
                    name = product.name,
                    qty = line.qty,
                    price = line.price,
                    basePrice = product.price
                )
            )
        }

        // Only where nothing was carried across to add up — which includes a bill
        // whose every product has since been deleted, and which is therefore a
        // figure now whatever it used to be. A typed amount sitting behind lines is
        // the second answer this form refuses to hold.
        amountText = if (_lines.isEmpty()) Money.amount(bill.total, currency) else ""
        invoiceNo = bill.invoiceNo.orEmpty()
        soldAt = bill.createdAt
        customer = bill.who
        // Chosen rather than typed: this name is already on a bill, so it already
        // has an account behind it, and making the owner re-pick it from the list
        // to change a date would be the form doubting its own history.
        customerKey = Customer.key(bill.who)
        payMode = if (bill.paid == null) PayMode.FULL else PayMode.PART
        paidText = bill.paid?.let { Money.amount(it, currency) }.orEmpty()
        // The ones already on it, so a correction can take one off as well as add
        // one. What the form ends up holding is reconciled against the bill on
        // the way out.
        _photoIds.clear()
        _photoIds.addAll(bill.photoIds)
    }

    /**
     * Everything the form was holding, so a correction can borrow the screen and
     * give it back.
     */
    private data class Draft(
        val lines: List<Line>,
        val amountText: String,
        val invoiceNo: String,
        val soldAt: java.time.Instant,
        val customer: String,
        val customerKey: String?,
        val payMode: PayMode,
        val paidText: String,
        val photoIds: List<String>
    )

    /**
     * The half-typed bill a correction interrupted, if there was one.
     *
     * Editing reuses this form, which used to mean the bill in progress was thrown
     * away the moment somebody tapped Edit — a real loss, with no warning and no
     * way back. It is put here instead and restored when the correction ends,
     * however it ends.
     */
    private var stashed: Draft? = null

    private fun snapshot() = Draft(
        lines = _lines.toList(),
        amountText = amountText,
        invoiceNo = invoiceNo,
        soldAt = soldAt,
        customer = customer,
        customerKey = customerKey,
        payMode = payMode,
        paidText = paidText,
        photoIds = _photoIds.toList()
    )

    private fun restore(draft: Draft) {
        _lines.clear()
        _lines.addAll(draft.lines)
        amountText = draft.amountText
        invoiceNo = draft.invoiceNo
        soldAt = draft.soldAt
        customer = draft.customer
        customerKey = draft.customerKey
        payMode = draft.payMode
        paidText = draft.paidText
        _photoIds.clear()
        _photoIds.addAll(draft.photoIds)
    }

    /**
     * Puts back whatever a correction interrupted, or empties the form when it
     * interrupted nothing.
     *
     * Called however a correction ends — saved, or abandoned — because the owner
     * who had half a bill typed wants it back either way.
     */
    fun release() {
        val draft = stashed
        stashed = null
        if (draft == null) clear() else restore(draft)
    }

    fun clear() {
        _lines.clear()
        amountText = ""
        invoiceNo = ""
        soldAt = Timestamps.now()
        customer = ""
        customerKey = null
        payMode = PayMode.FULL
        paidText = ""
        _photoIds.clear()
    }
}
