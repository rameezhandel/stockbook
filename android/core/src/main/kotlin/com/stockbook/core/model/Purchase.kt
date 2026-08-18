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
 * A mistake is **edited or removed**, exactly as on a bill, and either takes the
 * stock back off the shelf.
 */
@Serializable
data class Purchase(
    /** A string rather than a UUID type, matching how `Product.uid` travels. */
    val id: String = UUID.randomUUID().toString(),
    /** Whose delivery, by the key suppliers group under. */
    val supplierKey: String,
    /**
     * Which product this restocked. Null once that product has been deleted — a
     * purchase can outlive what it bought, as a bill line can — and null from the
     * start on a supplier bill that names no product at all.
     */
    val productUid: String? = null,
    /**
     * The product's name **at the time of delivery**. History must not move.
     *
     * Null when the supplier's bill was entered as a figure rather than as
     * stock arriving: a bill for a mixed load, or for something the shop does
     * not keep a count of. [isItemised] is how the rest of the app tells them
     * apart, because only one of the two moves the shelf.
     */
    val name: String? = null,
    val qty: Int = 0,
    /** What the shop paid per piece, as entered. Zero when no product was named. */
    val unitCost: Double = 0.0,
    /**
     * What the delivery came to — `qty × unitCost` where a product was named, and
     * simply what was typed where one was not. Stored either way.
     */
    val total: Double,
    /**
     * `null` means settled on the spot — the common case at a counter where the
     * driver waits for cash. A number means part paid, and the shop owes
     * `total − paid`.
     */
    val paid: Double? = null,
    /**
     * The number on the supplier's invoice.
     *
     * The same field as a bill's, pointing the other way: what is written on the
     * paper that arrived with the stock, so the pile in the drawer can be matched
     * against what the app says was delivered.
     *
     * Nullable here and required by the screen. The type has to be able to read a
     * record that has none — a file written by another build, or a fixture — but
     * nothing the owner enters can leave it empty.
     */
    val invoiceNo: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now()
) {
    /** What the shop still owes on this delivery. Zero when settled. */
    val balance: Double
        get() {
            val paid = paid ?: return 0.0
            return maxOf(0.0, total - paid)
        }

    val isPartPaid: Boolean get() = paid != null

    /**
     * Whether this says what arrived, or only what it cost.
     *
     * Stock moves for the first and not the second, so editing or removing one
     * has to reverse exactly what recording it did.
     */
    val isItemised: Boolean get() = !name.isNullOrBlank()

    /**
     * What to call this delivery on a list or a statement.
     *
     * The supplier's number when it came with one, because that is what the
     * supplier will say on the phone — otherwise the plain word, since a purchase
     * has no counter of its own the way a bill does.
     */
    fun reference(strings: com.stockbook.core.text.Strings): String =
        invoiceNo?.takeIf { it.isNotBlank() } ?: strings.purchaseLabel
}
