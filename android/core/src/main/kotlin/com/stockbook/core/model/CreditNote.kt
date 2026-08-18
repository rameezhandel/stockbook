package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * A document that reduces what a customer owes, with **no money changing hands**.
 *
 * Goods came back, the shop overcharged, damage was agreed, something was
 * knocked off after the fact. Three things it deliberately is not:
 *
 * - **Not a payment.** No cash moved. A statement that showed the two as one
 *   line would tell the owner they had taken money they never took, which is the
 *   figure a shop reconciles its till against.
 * - **Not an edit to the bill.** The customer is holding the original invoice,
 *   and a bill from a closed month has already been read, totalled and reported
 *   against. A credit note is a second event with its own date, exactly as a
 *   payment is.
 * - **Not a refund.** That is cash going back out of the till, and this app has
 *   no notion of one.
 *
 * It attaches to a **customer, not a bill** — the same rule payments follow, for
 * the same reason: allocation against particular invoices is a fiction the owner
 * would then have to maintain. [reason] is where "against 06011" goes if they
 * want it there.
 */
@Serializable
data class CreditNote(
    /**
     * Identity, machine-assigned and never typed — the same split [noteNo] is
     * the other half of, and the same one `Bill.number` makes against
     * `Bill.invoiceNo`.
     */
    val id: String = UUID.randomUUID().toString(),
    /** Whose account this credits, by the key bills and payments group under. */
    val customerKey: String,
    /**
     * What came back, when anything did.
     *
     * **May be empty**, and usually is: most credit notes are a figure agreed
     * across a counter rather than a pile of returned stock. Empty or not is
     * what decides whether the shelf moves — the same rule a bill follows, in
     * the opposite direction, and [isItemised] is how everything downstream
     * tells the two apart.
     */
    val lines: List<BillLine> = emptyList(),
    /**
     * What the note comes to — **stored**, never recomputed, for the reason
     * `Bill.total` is: repricing a product tomorrow must not rewrite what was
     * credited today.
     */
    val total: Double,
    /**
     * The number the owner writes on the paper credit note.
     *
     * Its own series, unrelated to the bill book: shops number credit notes
     * separately, and "#00130" in a credit-note run has nothing to do with
     * invoice 00130. Typed rather than suggested, as every number in this app
     * now is.
     */
    val noteNo: String? = null,
    /** "returned 2 locks", "overcharged on 06011", "damage agreed". */
    val reason: String? = null,
    @Serializable(with = InstantSerializer::class)
    val issuedAt: Instant = Timestamps.now()
) {
    /**
     * Whether this note says what came back, or only what it came to. Stock
     * moves for the first and not the second.
     */
    val isItemised: Boolean get() = lines.isNotEmpty()

    /** What a statement calls it: the paper's number, or its own plain word. */
    fun reference(strings: com.stockbook.core.text.Strings): String =
        noteNo?.takeIf { it.isNotBlank() } ?: strings.creditNoteLabel
}
