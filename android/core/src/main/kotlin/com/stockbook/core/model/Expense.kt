package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Money the owner spent, written down so they can see where it went.
 *
 * Petrol, tea, a spare key blank bought in a hurry, the electricity bill. This
 * is a **private ledger and nothing more**: it is deliberately joined to nothing
 * else in the app.
 *
 * - It is not a purchase. A `Purchase` is stock arriving from a named supplier;
 *   it moves the shelf and it creates a debt. An expense moves neither.
 * - It does not touch what anybody owes. No customer, no supplier, no key.
 * - It is not on any statement, and cannot be. A statement is a document the
 *   owner may turn round and show a customer, and the owner's petrol is not that
 *   customer's business.
 * - It does not move "Sold", "Receivable" or "Payable". Those are the shop's
 *   position and this is the owner's spending; netting them would need a
 *   definition of profit that this app has not been asked for and would then
 *   have to be right about.
 *
 * Keeping it separate is the whole design. It means adding this could not break
 * a figure that already works, and it means the ledger can grow into something
 * more later without any of today's arithmetic having assumed otherwise.
 */
@Serializable
data class Expense(
    /**
     * Identity, machine-assigned and never typed.
     *
     * Unlike a bill, a receipt or a credit note, an expense carries **no typed
     * number**. Those numbers exist because there is a piece of paper in a
     * drawer with the same number on it, and the number is how the owner finds
     * it again. There is no such slip behind a tank of petrol.
     */
    val id: String = UUID.randomUUID().toString(),
    /** What it came to. */
    val amount: Double,
    /**
     * What it was for, in the owner's own words — "Petrol", "Tea for the shop",
     * "Van tyre".
     *
     * Free text rather than a list of kinds, chosen deliberately: a fixed list
     * would let the screen total by category, and it would also be a list
     * somebody has to maintain in two languages and that never quite fits the
     * next thing spent. Required, because an amount with nothing beside it is a
     * number nobody can account for a month later.
     */
    val note: String,
    /** The day the money went, which is not always the day it was written down. */
    @Serializable(with = InstantSerializer::class)
    val spentAt: Instant = Timestamps.now()
)
