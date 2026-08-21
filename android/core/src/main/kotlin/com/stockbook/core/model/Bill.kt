package com.stockbook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One sale.
 *
 * A mistake is **edited or removed**, and either puts the stock back where it
 * belongs: this is the shop's own book, kept by the one person who writes in it,
 * and the record that outlives a correction is the paper bill in the book rather
 * than a crossed-out row in here.
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
     * unique and machine-assigned, or editing and history lookups lose their
     * handle. This is a label the owner recognises; that is a key.
     */
    val invoiceNo: String? = null,
    /**
     * Photographs of the paper bill, by id — **not** the pictures themselves.
     *
     * The bytes live on disk under the app's own storage, because this record is
     * rewritten every time stock moves and a photograph is a thousand times the
     * size of everything else in the book. An id here says a photograph was
     * taken; whether the file is still on *this* phone is a separate question,
     * asked of the disk. Nothing in this app may prune an id because its file is
     * missing — a book restored ahead of its pictures re-adopts them the moment
     * they arrive.
     *
     * A list rather than one, so a two-page invoice never forces a change to the
     * file format. Spelled `photoIDs` on the wire, the way Swift spells it, for
     * the same reason `productUID` is.
     */
    @SerialName("photoIDs")
    val photoIds: List<String> = emptyList(),
    /**
     * What this bill was for, in the owner's words — "3 keys cut on site",
     * "delivered to the villa", "replaced under warranty".
     *
     * **The owner's own reminder, and it stays that way.** It shows on the bill
     * when the bill is opened, and nowhere else: not on the statement, which is
     * a document the customer is handed, and not in the shared receipt text. The
     * same rule the payment note follows, for the same reason — a shopkeeper
     * should be able to write "argued about the price" without wondering who
     * else will read it.
     *
     * Absent rather than blank when there is none, so both builds write the same
     * bytes for a bill without one.
     */
    val note: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Timestamps.now()
) {
    /** What is still owed on this bill. Zero when paid in full. */
    val balance: Double
        get() {
            val paid = paid ?: return 0.0
            return maxOf(0.0, total - paid)
        }

    val isPartPaid: Boolean get() = paid != null

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
