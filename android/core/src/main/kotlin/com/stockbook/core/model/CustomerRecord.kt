package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The typed-in facts about a customer.
 *
 * The counterpart to [Customer], which is assembled from history. This is the
 * part somebody actually sat down and entered: how the name is spelled, how to
 * ring them, where they are. The figures stay derived; only facts are stored,
 * which is the split [Customer] has documented since before this existed.
 *
 * Identity is [key] — the same case- and whitespace-insensitive rule bills have
 * always been grouped by. That is what makes a roster additive: not one stored
 * bill changes, and a name written on a bill years ago still finds its customer.
 *
 * Kept field-for-field with the iOS `CustomerRecord`, because these rows travel
 * between an iPhone and an Android phone in the backup file.
 */
@Serializable
data class CustomerRecord(
    /**
     * Identity. Assigned from the name at creation, and changed only by an
     * explicit rename — which also rewrites the name on that customer's bills,
     * because a rename is a correction rather than a new person.
     */
    val key: String,
    /**
     * The spelling to show. Authoritative once a customer is on the roster: it
     * was typed on purpose, unlike the spelling that happened to be used at the
     * counter on a busy afternoon.
     */
    val name: String,
    /**
     * Optional throughout. A shop that knows a name and nothing else still has a
     * customer, and demanding a phone number to save one would just teach the
     * owner to type nonsense into the box.
     */
    val phone: String? = null,
    val place: String? = null,
    /**
     * What this customer already owed **before Stockbook** — the figure carried
     * over from the paper book on the day the shop started using the app.
     *
     * Distinct from `Statement.openingBalance`, which is what they owed at the
     * start of whichever period is on screen and is derived. This one is stored,
     * predates every bill, and is therefore part of *every* period's
     * brought-forward figure.
     *
     * Never negative. A customer who was somehow in credit gets a payment
     * recorded instead, which is a thing with a date on it.
     */
    val openingBalance: Double = 0.0,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now()
) {
    companion object {
        /** Builds a record from what somebody typed, deriving the key. */
        fun of(
            name: String,
            phone: String? = null,
            place: String? = null,
            openingBalance: Double = 0.0,
            createdAt: Instant = Timestamps.now()
        ): CustomerRecord = CustomerRecord(
            key = Customer.key(name),
            name = name.trim(),
            phone = tidied(phone),
            place = tidied(place),
            openingBalance = maxOf(0.0, openingBalance),
            createdAt = createdAt
        )

        /**
         * A field the owner opened, thought better of and left blank is absent,
         * not an empty string — otherwise "has a phone number" becomes true for a
         * customer who has none.
         */
        fun tidied(value: String?): String? {
            val trimmed = value?.trim()
            return if (trimmed.isNullOrEmpty()) null else trimmed
        }
    }
}
