package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The typed-in facts about a supplier: the counterpart to [Supplier], which is
 * assembled from purchase history.
 *
 * Field for field the same as [CustomerRecord], and that is the point — these
 * rows travel between an iPhone and an Android phone in the same backup file, and
 * a shop's two account books should not have two different notions of a name.
 */
@Serializable
data class SupplierRecord(
    /** Identity. Changed only by an explicit rename, which rewrites the purchases. */
    val key: String,
    /** The spelling to show. Authoritative once a supplier is on the roster. */
    val name: String,
    val phone: String? = null,
    val place: String? = null,
    /**
     * What the shop already owed this supplier **before Stockbook** — the figure
     * carried over from the paper book on the day the app arrived.
     *
     * Never negative. A supplier the shop had paid ahead gets a payment recorded
     * instead, which is a thing with a date on it.
     */
    val openingBalance: Double = 0.0,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now()
) {
    companion object {
        fun of(
            name: String,
            phone: String? = null,
            place: String? = null,
            openingBalance: Double = 0.0,
            createdAt: Instant = Timestamps.now()
        ): SupplierRecord = SupplierRecord(
            key = Supplier.key(name),
            name = name.trim(),
            phone = CustomerRecord.tidied(phone),
            place = CustomerRecord.tidied(place),
            openingBalance = maxOf(0.0, openingBalance),
            createdAt = createdAt
        )
    }
}
