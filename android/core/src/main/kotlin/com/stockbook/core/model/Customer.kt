package com.stockbook.core.model

import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings
import java.time.Instant

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
    val billCount: Int,
    /** What they have bought. */
    val total: Double,
    /**
     * What they still owe: unpaid balances on live bills, **less every payment
     * received since**. Can go negative when somebody pays ahead, and is left
     * signed rather than floored — an advance is real money and hiding it would
     * make the next statement look wrong.
     */
    val owed: Double,
    /**
     * Typed-in facts, present only for a customer on the roster. A name that only
     * ever appeared on a bill has none, and is still a customer.
     */
    val phone: String? = null,
    val place: String? = null,
    /**
     * Carried over from the paper book. Already included in [owed]; exposed so
     * the editor can show what was typed rather than a figure with bills mixed in.
     */
    val openingBalance: Double = 0.0,
    /**
     * On the roster rather than merely seen on a bill. The two are shown
     * identically; this exists so the editor knows whether it is adding or
     * correcting.
     */
    val isOnRoster: Boolean = false,
    /**
     * When money last came in from them — paid at the counter on a bill, or
     * handed over afterwards. Null where none ever has.
     *
     * Credit notes and balance transfers are deliberately not in here; see
     * [LastPaid], which is the whole rule.
     */
    val lastPaidAt: Instant? = null,
    /**
     * Their oldest bill, which is where the clock starts for somebody who has
     * never paid anything. Null for a customer who has only ever been an opening
     * balance.
     */
    val firstBilledAt: Instant? = null
) {
    /**
     * `owes SAR 40` when they owe, `SAR 40 in advance` when they have paid ahead,
     * otherwise `3 bills`.
     */
    fun meta(currency: Currency, strings: Strings): String = when {
        owed > 0 -> strings.owes(Money.text(owed, currency))
        owed < 0 -> strings.inAdvance(Money.text(-owed, currency))
        else -> strings.bills(billCount)
    }

    /**
     * A customer entered on the roster who has never been billed. Not an error —
     * that is what the setup screen exists to create — but the difference between
     * "no bills yet" and "nothing outstanding" is worth drawing.
     */
    val hasHistory: Boolean get() = billCount > 0

    /** Whether money has ever actually come in from them. */
    val hasEverPaid: Boolean get() = lastPaidAt != null

    /**
     * Days since money last came in, counting from their first bill where none
     * ever has. Null when there is nothing to count from — see [LastPaid].
     */
    fun quietDays(now: Instant): Long? = LastPaid.daysSince(lastPaidAt, firstBilledAt, now)

    /** For a statement, a customer is an account like any other. */
    val party: StatementParty
        get() = StatementParty(
            name = name,
            key = key,
            phone = phone,
            place = place,
            openingBalance = openingBalance,
            kind = StatementParty.Kind.CUSTOMER
        )

    companion object {
        /**
         * The one place a name becomes an identity. Everything that groups,
         * matches or filters customers goes through here.
         */
        fun key(name: String): String = name.trim().lowercase()
    }
}
