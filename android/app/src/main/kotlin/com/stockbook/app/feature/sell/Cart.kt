package com.stockbook.app.feature.sell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Product
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

    val isEmpty: Boolean get() = _lines.isEmpty()

    val total: Double get() = _lines.sumOf { it.lineTotal }

    val paidValue: Double get() = Money.parse(paidText) ?: 0.0

    val balance: Double
        get() = if (payMode == PayMode.FULL) 0.0 else (total - paidValue).coerceAtLeast(0.0)

    /** What gets stored: null for paid in full. */
    val paidForStorage: Double? get() = if (payMode == PayMode.FULL) null else paidValue

    /**
     * A bill needs something on it, and somebody **chosen** to give it to.
     *
     * Not merely a non-blank name: a name nobody picked from the list is a name
     * with no account behind it, so nothing could be owed to it or settled against
     * it later.
     */
    val canSave: Boolean get() = !isEmpty && customerKey != null

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

    fun clear() {
        _lines.clear()
        customer = ""
        customerKey = null
        payMode = PayMode.FULL
        paidText = ""
    }
}
