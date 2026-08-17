package com.stockbook.core.store

import com.stockbook.core.model.Bill
import com.stockbook.core.model.CustomerRecord
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Product
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.Settings
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.SupplierPayment
import com.stockbook.core.model.SupplierRecord

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

    /** Insert or update, matched on [CustomerRecord.key]. */
    fun upsert(customer: CustomerRecord)

    /**
     * Removes the roster entry only. The customer's bills are history and stay
     * exactly where they are, which is why this takes a key rather than
     * pretending to delete a person.
     */
    fun deleteCustomer(key: String)

    fun append(payment: Payment)
    fun deletePayment(id: String)

    /** Insert or update, matched on [SupplierRecord.key]. */
    fun upsert(supplier: SupplierRecord)

    /**
     * Removes the roster entry only. The purchases are history and stay where
     * they are, for the same reason a customer's bills do.
     */
    fun deleteSupplier(key: String)

    fun append(purchase: Purchase)
    fun update(purchase: Purchase)

    fun append(payment: SupplierPayment)
    fun deleteSupplierPayment(id: String)

    fun save(settings: Settings)
    fun replaceAll(state: ShopState)
}
