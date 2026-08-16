package com.stockbook.core.model

import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * Somebody the shop has billed.
 *
 * **Derived, not stored.** There is no customer record in the database — a bill
 * carries a name and nothing else — so this is assembled from history every time
 * it is asked for. At a few hundred bills that is free, and it means a customer
 * cannot go stale or be orphaned from their bills.
 *
 * When there is more to know about a customer than their name — a phone number,
 * an address, a credit limit — the shape that fits is a stored record keyed by
 * [key], merged onto this type in `StockbookStore.customers()`. The derived
 * figures below stay derived; only the typed-in facts get stored. That is why
 * callers are given a `Customer` rather than a bare name string today.
 */
data class Customer(
    /**
     * The spelling from their **most recent** bill. Correcting the case on a new
     * bill therefore corrects it everywhere it is shown, without rewriting what
     * older bills say — those record what was actually written at the time.
     */
    val name: String,
    /**
     * Identity: case- and whitespace-insensitive. `"ahmed "` and `"Ahmed"` are
     * one person, which is the only workable rule for a name typed fresh at a
     * counter for every bill.
     */
    val key: String,
    /** Live bills only. A voided bill did not happen. */
    val billCount: Int,
    /** What they have bought, across live bills. */
    val total: Double,
    /** What they still owe, across live bills. */
    val owed: Double
) {
    /** `owes SAR 40` when they owe, otherwise `3 bills`. */
    fun meta(currency: Currency, strings: Strings): String =
        if (owed > 0) strings.owes(Money.text(owed, currency)) else strings.bills(billCount)

    companion object {
        /**
         * The one place a name becomes an identity. Everything that groups,
         * matches or filters customers goes through here.
         */
        fun key(name: String): String = name.trim().lowercase()
    }
}
