package com.stockbook.core.model

import kotlinx.serialization.Serializable

/**
 * One line of stock on the shelf.
 *
 * A plain value: no framework, no persistence, no identity beyond its own [uid].
 * Storage is somebody else's problem — see `StockbookRepository`.
 *
 * Everything in Stockbook sells **by the piece**. One product is one stock count
 * and one selling price. (An earlier design had pack/loose variants sharing a
 * count; the owner cut it.)
 */
@Serializable
data class Product(
    /**
     * Stable identity, and the only one there is. It survives export, import and
     * a change of storage engine — which a row id would not.
     */
    val uid: String = newUid(),
    val name: String,
    /**
     * Pieces on the shelf. Only ever changed by: setup, the product editor,
     * saving a bill (decrement, floored at 0), voiding a bill (increment back),
     * and restock. See `StockbookStore`.
     */
    val stock: Int,
    /**
     * The **latest** buying price per piece — not a weighted average. A purchase
     * entry overwrites it. There is no inventory valuation layer here and the
     * handoff asks for it to stay that way.
     */
    val cost: Double,
    /** Selling price per piece. Must be greater than zero. */
    val price: Double,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant = Timestamps.now()
) {
    /** `price − cost`, floored at zero. The only profit figure the app shows. */
    val marginPerPiece: Double get() = maxOf(0.0, price - cost)

    fun isLow(threshold: Int): Boolean = stock <= threshold

    companion object {
        fun newUid(): String = java.util.UUID.randomUUID().toString()
    }
}
