package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Stock arriving from a supplier: the mirror of a [Bill], pointing the other way.
 *
 * **One product per purchase.** A real delivery note often has five lines, and
 * this deliberately does not: the screen a purchase is entered from is the one
 * that puts a single product back on the shelf, and a five-line delivery entered
 * as five purchases is five true records rather than one convenient fiction. If
 * that ever becomes the wrong trade, the change is a `lines` list here — the same
 * shape [Bill] already has — and nothing else moves.
 *
 * Purchases are never deleted. A mistake is *voided*, which takes the stock back
 * off the shelf and leaves the record in place, exactly as a bill's void puts it
 * back on.
 */
@Serializable
data class Purchase(
    /** A string rather than a UUID type, matching how `Product.uid` travels. */
    val id: String = UUID.randomUUID().toString(),
    /** Whose delivery, by the key suppliers group under. */
    val supplierKey: String,
    /**
     * Which product this restocked. Null once that product has been deleted — a
     * purchase can outlive what it bought, as a bill line can.
     */
    val productUid: String? = null,
    /** The product's name **at the time of delivery**. History must not move. */
    val name: String,
    val qty: Int,
    /** What the shop paid per piece, as entered. */
    val unitCost: Double,
    /** `qty × unitCost` at the time, stored rather than recomputed. */
    val total: Double,
    /**
     * `null` means settled on the spot — the common case at a counter where the
     * driver waits for cash. A number means part paid, and the shop owes
     * `total − paid`.
     */
    val paid: Double? = null,
    /**
     * The number on the supplier's invoice, when it came with one.
     *
     * The same field as a bill's, pointing the other way: what is written on the
     * paper that arrived with the stock, so the pile in the drawer can be matched
     * against what the app says was delivered.
     */
    val invoiceNo: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now(),
    val voided: Boolean = false
) {
    /** What the shop still owes on this delivery. Zero when settled or voided. */
    val balance: Double
        get() {
            if (voided) return 0.0
            val paid = paid ?: return 0.0
            return maxOf(0.0, total - paid)
        }

    val isPartPaid: Boolean get() = !voided && paid != null
}
