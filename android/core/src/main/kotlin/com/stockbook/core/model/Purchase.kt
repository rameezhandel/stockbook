package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * One product on a delivery: what arrived, how many, and what the shop paid for
 * each. The mirror of [BillLine], pointing the other way.
 */
@Serializable
data class PurchaseLine(
    /** Which product this was. Null because a line can outlive its product. */
    val productUid: String? = null,
    /** The product's name **at the time of delivery**. History must not move. */
    val name: String,
    /** At least 1. */
    val qty: Int,
    /** What the shop paid per piece, as entered. */
    val unitCost: Double
) {
    val lineTotal: Double get() = qty * unitCost
}

/**
 * Stock arriving from a supplier: the mirror of a [Bill], pointing the other way.
 *
 * **One delivery, one piece of paper, as many lines as the paper has.** It used
 * to hold a single product, on the argument that five lines entered as five
 * purchases were five true records rather than one convenient fiction — but that
 * escape was never open. The screen refuses a repeated invoice number, across
 * the whole book, because one number means one piece of paper. So a five-line
 * delivery note could not be entered at all: not as five records, which the
 * number rule forbids, and not as one, which the model had no room for. The
 * shape below is [Bill]'s, and it is the shape the old comment here said to
 * reach for.
 *
 * A mistake is **edited or removed**, exactly as on a bill, and either takes the
 * stock back off the shelf — every line of it.
 */
@Serializable
data class Purchase(
    /** A string rather than a UUID type, matching how `Product.uid` travels. */
    val id: String = UUID.randomUUID().toString(),
    /** Whose delivery, by the key suppliers group under. */
    val supplierKey: String,
    /**
     * What arrived. Empty on a supplier bill entered as a figure rather than as
     * stock: a mixed load, or something the shop keeps no count of. [isItemised]
     * is how the rest of the app tells them apart, because only one of the two
     * moves the shelf.
     */
    val lines: List<PurchaseLine> = emptyList(),
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
    val createdAt: Instant = Timestamps.now(),

    // --- Read from records written when a delivery held one product. Never
    // written again: [items] is what the app reads, and it folds these into a
    // single line so an older delivery keeps the itemisation it was entered with.
    // Dropping them instead would have left the money right and quietly turned
    // every delivery already in the book into a bare figure.
    val productUid: String? = null,
    val name: String? = null,
    val qty: Int = 0,
    val unitCost: Double = 0.0
) {
    /**
     * Every line, whichever shape the record was written in.
     *
     * The whole app reads this and never [lines] — the one place the two differ
     * is a delivery entered before a delivery could have more than one product.
     */
    val items: List<PurchaseLine>
        get() = when {
            lines.isNotEmpty() -> lines
            !name.isNullOrBlank() -> listOf(PurchaseLine(productUid, name, qty, unitCost))
            else -> emptyList()
        }

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
    val isItemised: Boolean get() = items.isNotEmpty()

    /** What the lines add up to. [total] is what was charged and is stored. */
    val subtotal: Double get() = items.sumOf { it.lineTotal }

    /**
     * What arrived, in the products' own words. Empty on a bill that named none.
     *
     * The same shape [Bill.summary] has, so a row of deliveries and a row of
     * bills read the same way — and a row that needs the short form says
     * `items(n)` beside it rather than instead of it, exactly as `BillRow` does.
     */
    val summary: String get() = items.joinToString(", ") { it.name }

    /**
     * What arrived, with the counts: `Cisa lock × 10, Key blank × 100`.
     *
     * Null rather than empty where the bill named nothing, because both places
     * that show this — the statement on screen and the one that gets sent — drop
     * the line entirely then. Interpolating regardless is how a supplier bill for
     * a mixed load once read `null × 0` on a document somebody was handed.
     */
    val described: String?
        get() = items.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.name} × ${it.qty}" }

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
