package com.stockbook.core.model

import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * Somebody the shop buys from.
 *
 * The mirror of [Customer], and deliberately the same shape: assembled from
 * purchase history rather than stored whole, merged with the typed-in facts in
 * [SupplierRecord], identified by the same case- and whitespace-insensitive key.
 *
 * What differs is only the direction of the money. [owed] here is what the *shop*
 * owes, not what is owed to it, which is why a positive figure reads as a debt on
 * this side of the counter and a payment is money going out.
 */
data class Supplier(
    /** The roster's spelling where there is one, otherwise the latest delivery's. */
    val name: String,
    val key: String,
    val purchaseCount: Int,
    /** What the shop has bought from them. */
    val total: Double,
    /**
     * What the shop still owes them: unpaid balances on live purchases, less
     * every payment made since. Signed, like a customer's: paying a supplier
     * ahead is real money and hiding it would make the next statement wrong.
     */
    val owed: Double,
    val phone: String? = null,
    val place: String? = null,
    /** Carried over from the paper book. Already included in [owed]. */
    val openingBalance: Double = 0.0,
    val isOnRoster: Boolean = false
) {
    /** `owes SAR 40` — from the shop's side — or `2 purchases`. */
    fun meta(currency: Currency, strings: Strings): String = when {
        owed > 0 -> strings.owes(Money.text(owed, currency))
        owed < 0 -> strings.inAdvance(Money.text(-owed, currency))
        else -> strings.purchases(purchaseCount)
    }

    val hasHistory: Boolean get() = purchaseCount > 0

    /** For a statement, a supplier is an account like any other. */
    val party: StatementParty
        get() = StatementParty(
            name = name,
            key = key,
            phone = phone,
            place = place,
            openingBalance = openingBalance,
            kind = StatementParty.Kind.SUPPLIER
        )

    companion object {
        /**
         * A supplier's name becomes an identity by exactly the rule a customer's
         * does, and there is one implementation of it on purpose: two rules that
         * agree today are two rules that can stop agreeing, and the day they do,
         * a delivery lands against a supplier who does not exist.
         */
        fun key(name: String): String = Customer.key(name)
    }
}
