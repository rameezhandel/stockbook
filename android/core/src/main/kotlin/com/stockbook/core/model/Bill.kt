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
    /**
     * What was on the bill, when the owner said.
     *
     * **May be empty.** A shop writing bills in a paper book already knows the
     * total, and rebuilding it line by line to arrive at a figure it can read off
     * the paper is work for nothing. An itemised bill moves the shelf count; one
     * entered as a total does not, and [isItemised] is how everything downstream
     * tells the two apart.
     */
    val lines: List<BillLine> = emptyList(),
    /**
     * What the bill came to — **stored**, never recomputed. On an itemised bill
     * it is the sum of `qty × price` at the moment of sale, so editing a product
     * tomorrow cannot rewrite what somebody paid today. On a bill entered as a
     * total it is simply what was typed.
     */
    val total: Double,
    /**
     * `null` means paid in full. A number means part paid, and the customer owes
     * `total − paid`.
     */
    val paid: Double? = null,
    /** Customer name, trimmed. Required on every bill. */
    val who: String,
    /**
     * The number printed on the paper bill, when the shop writes one.
     *
     * A string, not an int: bill books are numbered "1024" in some shops and
     * "A-1024" in others, and neither is arithmetic. Distinct from [number],
     * which is this app's own counter and its identity — that one has to stay
     * unique and machine-assigned, or voiding and history lookups lose their
     * handle. This is a label the owner recognises; that is a key.
     */
    val invoiceNo: String? = null,
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

    /**
     * Whether this bill says what was sold, or only what it came to.
     *
     * The one question the rest of the app asks about a bill's lines: stock moves
     * for an itemised bill and not for a typed total, and a document with nothing
     * to list has to say so rather than print an empty table.
     */
    val isItemised: Boolean get() = lines.isNotEmpty()

    /** The row's first line: the names on the bill, joined. Blank when there are none. */
    val summary: String get() = lines.joinToString(", ") { it.name }

    /**
     * What to call this bill on screen: the paper's number where there is one,
     * and the app's own otherwise. One number, never both — two numbers on a
     * document is how somebody reads out the wrong one over the phone.
     */
    fun reference(strings: com.stockbook.core.text.Strings): String =
        invoiceNo?.takeIf { it.isNotBlank() } ?: strings.billNumber(number)
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
