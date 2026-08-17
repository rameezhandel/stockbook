package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Money a customer handed over **after** the bill was written.
 *
 * Its own record rather than an edit to the bill, for one reason: the bill says
 * what happened at the counter that day and must go on saying it. A customer who
 * clears 400 three weeks later has not changed that bill — they have added a
 * second event, with its own date, and a statement is unreadable without both.
 *
 * Payments attach to a **customer, not a bill.** That is how a shop like this is
 * actually settled: somebody hands over what they can against what they owe, not
 * against invoice #7 specifically. Allocating it to particular bills would be a
 * fiction the owner would then have to maintain.
 */
@Serializable
data class Payment(
    /** A string rather than a UUID type, matching how `Product.uid` travels. */
    val id: String = UUID.randomUUID().toString(),
    /** Whose payment, by the same key bills group under. */
    val customerKey: String,
    /**
     * Always positive. The store clamps it; nothing here expresses a negative
     * payment, because that is a refund and this app has no notion of one.
     */
    val amount: Double,
    @Serializable(with = InstantSerializer::class)
    val receivedAt: Instant = Timestamps.now(),
    /**
     * "cash", "cheque 4471", "part settlement" — whatever the owner wants to
     * remember. Absent rather than empty when skipped.
     */
    val note: String? = null
)
