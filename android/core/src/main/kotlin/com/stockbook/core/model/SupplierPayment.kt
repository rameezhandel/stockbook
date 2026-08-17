package com.stockbook.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Money the shop handed over to a supplier after the delivery.
 *
 * Its own record for the same reason [Payment] is: the purchase says what
 * happened on the day the stock arrived and must go on saying it, and settling up
 * three weeks later is a second event with its own date.
 *
 * A separate type from [Payment] rather than one type with a direction on it.
 * Money in and money out are not the same thing, and the file format's first rule
 * is never to repurpose a key — `customerKey` could not quietly start meaning
 * "whoever this is" without every older reader misreading a shop's debts as its
 * takings.
 */
@Serializable
data class SupplierPayment(
    val id: String = UUID.randomUUID().toString(),
    val supplierKey: String,
    /** Always positive; the store clamps it. A negative payment is a refund, and there is no such thing here. */
    val amount: Double,
    @Serializable(with = InstantSerializer::class)
    val paidAt: Instant = Timestamps.now(),
    /** "cash", "cheque 4471", "against last month" — absent rather than empty when skipped. */
    val note: String? = null
)
