package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * A balance moved from one account to another, both of them real.
 *
 * **Nothing is absorbed, and the app deliberately offers no way to absorb it.**
 * A merge — one row disappearing with its history re-filed under another — was
 * built here once and taken out again: on this book two accounts are two real
 * branches, not one firm entered twice. Both are genuine, both were rightly
 * invoiced, and from today they settle through one of them. Two branches of one
 * contractor consolidating is the case this exists for.
 *
 * That is why **the invoices do not move.** The Jeddah branch's copy of invoice
 * #1042 says Jeddah; re-filing it under Riyadh would put this book out of step
 * with paper the customer is holding. Only the outstanding figure moves, and it
 * moves as a matching pair — off one statement, onto the other — so the two can
 * be reconciled against each other.
 *
 * **No number, unlike every other record here.** An invoice, a receipt and a
 * credit note are each numbered because a slip exists in a drawer to match the
 * number to. Nothing was written for this: it is the owner's own adjustment
 * between two of their own accounts. What it does need is [note], because a line
 * on a statement that the customer cannot account for is worse than no line.
 *
 * Customers and suppliers both use this. A key is a key, and the shop's two
 * sides are the same arithmetic pointed in opposite directions — which is why
 * [isSupplier] is stored rather than guessed from whether the key happens to
 * match somebody.
 */
@Serializable
data class BalanceTransfer(
    /** A string rather than a UUID type, matching how `Payment.id` travels. */
    val id: String = UUID.randomUUID().toString(),
    /** The account the balance leaves. */
    val fromKey: String,
    /** The account it lands on. */
    val intoKey: String,
    /**
     * Which side of the book both keys belong to.
     *
     * Stored rather than derived. A customer and a supplier can share a name —
     * a firm you both buy from and sell to is ordinary — so their keys are
     * identical strings, and asking "is there a supplier with this key" would
     * answer yes for a transfer between two customers.
     */
    val isSupplier: Boolean = false,
    /**
     * Always positive, and the store clamps it. Direction is carried by which
     * key is which, not by a sign — a negative amount would be the same transfer
     * written backwards, and two ways to say one thing is one way too many.
     *
     * **May exceed what is owed.** The app already understands a negative
     * balance as money held in advance, and refusing would block a legitimate
     * shuffle of a prepayment.
     */
    val amount: Double,
    /** Why it was moved. Required by the form, for the reason above. */
    val note: String? = null,
    @Serializable(with = InstantSerializer::class)
    val movedAt: Instant = Instant.now()
)
