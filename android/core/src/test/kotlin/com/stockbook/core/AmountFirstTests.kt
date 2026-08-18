package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A bill is a number, a date, somebody and a figure. Saying what was sold is
 * optional.
 *
 * This is the shape of the shop rather than a shortcut: the bill is written in a
 * paper book first, so the total is already known, and rebuilding it product by
 * product to arrive at a figure that can be read off the paper is work for
 * nothing.
 *
 * The whole cost of that decision is one rule, and it is what these tests pin
 * down: **the shelf moves only for what was itemised.** Anything else would be
 * the app inventing stock movements nobody described.
 */
class AmountFirstTests {

    private val english = Strings(AppLanguage.ENGLISH)

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aProduct(stock: Int = 50) =
        addProduct("Cisa lock", stock, 60.0, 95.0)

    // --- Sales

    @Test
    fun `a bill can be a figure and nothing else`() {
        val store = store()

        val bill = assertNotNull(
            store.saveBill(customer = "Ahmed", paid = null, amount = 450.0, invoiceNo = "A-1024")
        )

        assertEquals(450.0, bill.total)
        assertTrue(bill.lines.isEmpty())
        assertFalse(bill.isItemised)
        assertEquals("A-1024", bill.reference(english))
    }

    @Test
    fun `a bill with no items leaves the shelf alone`() {
        // The point of the whole trade: nothing here says a lock left the shop,
        // so nothing may claim one did.
        val store = store()
        val product = store.aProduct(stock = 50)

        store.saveBill(customer = "Ahmed", paid = null, amount = 450.0)

        assertEquals(50, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `an itemised bill still moves the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 50)

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null)
        )

        assertTrue(bill.isItemised)
        assertEquals(190.0, bill.total)
        assertEquals(48, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `items win over a typed figure`() {
        // Two answers to "what did it come to" is one too many. The lines are the
        // ones with arithmetic behind them, so they are the ones that count.
        val store = store()
        val product = store.aProduct()

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null, amount = 9_999.0)
        )

        assertEquals(190.0, bill.total)
    }

    @Test
    fun `a bill for nothing is not a bill`() {
        val store = store()

        assertNull(store.saveBill(customer = "Ahmed", paid = null), "no lines and no figure")
        assertNull(store.saveBill(customer = "Ahmed", paid = null, amount = 0.0))
        assertNull(store.saveBill(customer = "", paid = null, amount = 450.0), "and somebody is still required")
        assertTrue(store.bills.isEmpty())
    }

    @Test
    fun `part payment works the same either way`() {
        val store = store()

        val bill = assertNotNull(store.saveBill(customer = "Ahmed", paid = 100.0, amount = 450.0))

        assertEquals(350.0, bill.balance)
        assertEquals(350.0, store.customers().first { it.key == "ahmed" }.owed)
    }

    @Test
    fun `voiding a bill with no items takes nothing off the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(customer = "Ahmed", paid = 0.0, amount = 450.0))

        store.deleteBill(bill.number)

        assertEquals(50, assertNotNull(store.product(product.uid)).stock, "nothing went out, so nothing comes back")
        // Ahmed was never on the roster and his one bill is now void, so he may
        // not be listed at all — what matters is that nothing is owed either way.
        assertEquals(0.0, store.customers().firstOrNull { it.key == "ahmed" }?.owed ?: 0.0)
    }

    // --- Supplier bills

    @Test
    fun `a supplier bill can be a figure and nothing else`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))

        val purchase = assertNotNull(
            store.recordSupplierBill(supplier.key, amount = 800.0, paid = 0.0, invoiceNo = "INV-88")
        )

        assertEquals(800.0, purchase.total)
        assertFalse(purchase.isItemised)
        assertNull(purchase.name)
        assertEquals(50, assertNotNull(store.product(product.uid)).stock, "and no stock arrived")
        assertEquals(800.0, store.suppliers().first().owed)
    }

    @Test
    fun `a delivery with a product on it still fills the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))

        val purchase = assertNotNull(
            store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 62.0)
        )

        assertTrue(purchase.isItemised)
        assertEquals(310.0, purchase.total)
        val restocked = assertNotNull(store.product(product.uid))
        assertEquals(15, restocked.stock)
        assertEquals(62.0, restocked.cost, "latest paid takes over, as it always has")
    }

    @Test
    fun `a product with no quantity is half an answer`() {
        // Rejected rather than guessed: putting an invented count on the shelf is
        // exactly the sort of quiet wrongness the shelf rule exists to prevent.
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))

        assertNull(store.recordPurchase(product, supplier.key, quantity = 0, unitCost = 60.0))
        assertEquals(10, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `voiding a supplier bill with no product takes nothing off the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(store.recordSupplierBill(supplier.key, amount = 800.0, paid = 0.0))

        store.deletePurchase(purchase.id)

        assertEquals(10, assertNotNull(store.product(product.uid)).stock)
        assertEquals(0.0, store.suppliers().first().owed)
    }

    // --- The shelf, corrected by hand

    @Test
    fun `a counted shelf is set, not adjusted`() {
        val store = store()
        val product = store.aProduct(stock = 50)

        store.setStock(product, 12)

        assertEquals(12, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `a shelf cannot be counted below nothing`() {
        val store = store()
        val product = store.aProduct(stock = 50)

        store.setStock(product, -3)

        assertEquals(0, assertNotNull(store.product(product.uid)).stock)
    }

    // --- Everything downstream

    @Test
    fun `both kinds of bill appear on a statement`() {
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", 0.0)
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 450.0)

        val statement = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(java.time.Instant.now()))
        )

        assertEquals(2, statement.entries.size)
        assertEquals(640.0, statement.billed)
        assertEquals(640.0, statement.closingBalance)
    }

    @Test
    fun `both kinds survive a backup round trip`() {
        val store = store()
        val product = store.aProduct()
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null, invoiceNo = "A-1")
        store.saveBill(customer = "Sami", paid = null, amount = 450.0, invoiceNo = "A-2")
        store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0, invoiceNo = "INV-1")
        store.recordSupplierBill(supplier.key, amount = 800.0, invoiceNo = "INV-2")

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))
        val restored = store()
        restored.replaceEverything(document)

        val bills = restored.bills.sortedBy { it.number }
        assertTrue(bills[0].isItemised)
        assertFalse(bills[1].isItemised)
        assertEquals(450.0, bills[1].total)

        val purchases = restored.purchases.sortedBy { it.invoiceNo }
        assertTrue(purchases[0].isItemised)
        assertFalse(purchases[1].isItemised)
        assertEquals(800.0, purchases[1].total)
    }
}
