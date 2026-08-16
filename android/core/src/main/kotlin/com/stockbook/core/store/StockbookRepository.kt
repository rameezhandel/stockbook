package com.stockbook.core.store

import com.stockbook.core.model.Bill
import com.stockbook.core.model.Product
import com.stockbook.core.model.Settings
import com.stockbook.core.model.ShopState

/**
 * The one seam between the shop's rules and the disk.
 *
 * Writes are **incremental** on purpose. A whole-state save is trivial for a
 * file and ruinous for a real database, so the easy version of this interface
 * would quietly rule out the engines it exists to permit. Swapping in Room or
 * SQLite means writing one class and adding a line to `RepositoryTests`, which
 * runs the same contract suite against every implementation so no backing can
 * rot unnoticed.
 */
interface StockbookRepository {
    fun loadAll(): ShopState
    fun upsert(product: Product)
    fun delete(productUid: String)
    fun append(bill: Bill)
    fun update(bill: Bill)
    fun save(settings: Settings)
    fun replaceAll(state: ShopState)
}
