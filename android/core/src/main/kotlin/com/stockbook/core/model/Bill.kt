package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One sale.
 *
 * Bills are never deleted. A mistake is *voided*, which puts the stock back and
 * leaves the record in place — without that, one mistyped bill puts the shelf
 * and the app permanently out of step.
 */
@Serializable
data class Bill(
    /**
     * The human-facing number, shown as "Bill #7". Allocated from
     * `Settings.nextBillNumber`, so it is stable, monotonic, and something you
     * can say to a customer.
     */
    val number: Int,
    val lines: List<BillLine>,
    /**
     * Sum of `qty × price` at the moment of sale — **stored**, never recomputed
     * from current product prices. Editing a product tomorrow must not rewrite
     * what somebody paid today.
     */
    val total: Double,
    /**
     * `null` means paid in full. A number means part paid, and the customer owes
     * `total − paid`.
     */
    val paid: Double? = null,
    /** Customer name, trimmed. Required on every bill. */
    val who: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now(),
    val voided: Boolean = false
) {
    /** What is still owed on this bill. Zero when paid in full or voided. */
    val balance: Double
        get() {
            if (voided) return 0.0
            val paid = paid ?: return 0.0
            return maxOf(0.0, total - paid)
        }

    val isPartPaid: Boolean get() = !voided && paid != null

    /** The row's first line: the names on the bill, joined. */
    val summary: String get() = lines.joinToString(", ") { it.name }
}

/**
 * A single line on a bill.
 *
 * [name] and [price] are **snapshots** taken at sale time. The product may be
 * renamed, repriced or deleted afterwards; history must not move.
 */
@Serializable
data class BillLine(
    /** Which product this was. Null because a line can outlive its product. */
    val productUid: String? = null,
    /** The product's name *at the time of sale*. */
    val name: String,
    /** At least 1. */
    val qty: Int,
    /** The price actually charged — which may be an override, not the list price. */
    val price: Double
) {
    val lineTotal: Double get() = qty * price
}
