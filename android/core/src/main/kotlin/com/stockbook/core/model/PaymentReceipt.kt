package com.stockbook.core.model

import java.time.Instant

/**
 * One payment as a thing on its own: who handed the money over, how much, and
 * where their account stood the moment after it.
 *
 * **Not stored.** A payment is stored; this is derived from it and from the
 * whole history behind it, exactly as a [Statement] is. Nothing here goes in the
 * backup file.
 *
 * [balanceAfter] is lifted straight out of that account's statement — it is the
 * figure printed beside this payment in the balance column, not a second sum of
 * the same events. A receipt and a statement disagreeing about what somebody
 * owes is the one failure this document cannot survive, and the only way to be
 * sure they cannot is for there to be one calculation.
 *
 * [balanceBefore] is that figure plus the payment, which is what it was: a
 * payment settles and settles nothing else.
 */
data class PaymentReceipt(
    /** Whose account, and which way it runs — the receipt is worded from this. */
    val party: StatementParty,
    /**
     * The payment this was derived from.
     *
     * Carried so a screen showing the slip can act on the record behind it —
     * deleting a payment needs the payment, and everything else here is a figure
     * read out of a statement. [StatementParty.isSupplier] says which of the two
     * stores to ask.
     */
    val paymentId: String,
    /**
     * The number the shop wrote on the slip, or null on a record from before
     * receipt numbers were typed. The document says so rather than leaving a
     * gap.
     */
    val paymentNo: String?,
    val amount: Double,
    /** Received on, for a customer; paid on, for a supplier. */
    val at: Instant,
    val note: String?,
    val balanceBefore: Double,
    val balanceAfter: Double
)
