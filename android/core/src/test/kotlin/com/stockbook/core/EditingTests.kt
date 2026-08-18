package com.stockbook.core

import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Correcting a document, rather than marking it.
 *
 * A bill entered wrongly is **edited or removed**: this is the shop's own book,
 * kept by the one person who writes in it, and the record that outlives a
 * correction is the paper bill rather than a crossed-out row in the app.
 *
 * All of the risk is in the shelf. An edit has to move stock by the *difference*
 * between what the bill used to say and what it says now, and getting that wrong
 * is invisible until somebody counts a bin — which is why every case below
 * checks the count rather than only the figures.
 */
class EditingTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aProduct(stock: Int = 50) =
        addProduct("Cisa lock", stock, 60.0, 95.0)

    private val day = Instant.parse("2026-08-18T09:00:00Z")

    // --- Editing a bill

    @Test
    fun `editing a typed figure changes what is owed and leaves the shelf alone`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(
            store.saveBill(customer = "Ahmed", paid = null, amount = 450.0, invoiceNo = "A-1024")
        )

        val edited = assertNotNull(
            store.updateBill(
                number = bill.number,
                customer = "Ahmed",
                paid = 100.0,
                amount = 400.0,
                createdAt = day,
                invoiceNo = "A-1024"
            )
        )

        assertEquals(400.0, edited.total)
        assertEquals(300.0, edited.balance)
        assertEquals(bill.number, edited.number, "the same bill, not a new one")
        assertEquals(1, store.bills.size)
        assertEquals(50, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `editing a quantity upwards takes only the difference off the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null))
        assertEquals(48, assertNotNull(store.product(product.uid)).stock)

        store.updateBill(
            number = bill.number,
            lines = listOf(DraftLine(product.uid, 5, 95.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = day
        )

        assertEquals(45, assertNotNull(store.product(product.uid)).stock, "three more went out, not five")
    }

    @Test
    fun `editing a quantity downwards puts the difference back`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 5, 95.0)), "Ahmed", null))

        store.updateBill(
            number = bill.number,
            lines = listOf(DraftLine(product.uid, 2, 95.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = day
        )

        assertEquals(48, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `dropping the items altogether gives all the stock back`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 4, 95.0)), "Ahmed", null))

        val edited = assertNotNull(
            store.updateBill(
                number = bill.number,
                customer = "Ahmed",
                paid = null,
                amount = 380.0,
                createdAt = day
            )
        )

        assertFalse(edited.isItemised)
        assertEquals(380.0, edited.total)
        assertEquals(50, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `itemising a typed bill takes the stock off`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(customer = "Ahmed", paid = null, amount = 380.0))

        val edited = assertNotNull(
            store.updateBill(
                number = bill.number,
                lines = listOf(DraftLine(product.uid, 4, 95.0)),
                customer = "Ahmed",
                paid = null,
                createdAt = day
            )
        )

        assertTrue(edited.isItemised)
        assertEquals(380.0, edited.total, "and the sum takes over from the typed figure")
        assertEquals(46, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `an edit that would not be a bill changes nothing at all`() {
        // Half-applying an edit is the worst outcome available: the stock would
        // have moved for a bill that was never saved, and nothing on screen would
        // say so.
        val store = store()
        val product = store.aProduct(stock = 50)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null))

        assertNull(
            store.updateBill(number = bill.number, customer = "   ", paid = null, amount = 100.0, createdAt = day)
        )
        assertNull(
            store.updateBill(number = bill.number, customer = "Ahmed", paid = null, amount = 0.0, createdAt = day)
        )

        assertEquals(48, assertNotNull(store.product(product.uid)).stock)
        assertEquals(190.0, store.bills.first().total)
    }

    @Test
    fun `editing an unknown bill does nothing`() {
        val store = store()
        assertNull(store.updateBill(number = 99, customer = "Ahmed", paid = null, amount = 100.0, createdAt = day))
    }

    @Test
    fun `a bill does not clash with its own number`() {
        // Without this, opening 1024 to fix its date would be told 1024 is taken.
        val store = store()
        val bill = assertNotNull(store.saveBill(customer = "Ahmed", paid = null, amount = 450.0, invoiceNo = "1024"))
        store.saveBill(customer = "Sami", paid = null, amount = 200.0, invoiceNo = "1025")

        assertNull(store.billWithInvoiceNo("1024", exceptNumber = bill.number))
        assertEquals("Sami", store.billWithInvoiceNo("1025", exceptNumber = bill.number)?.who)
    }

    @Test
    fun `moving a bill to another customer moves the debt with it`() {
        val store = store()
        val bill = assertNotNull(store.saveBill(customer = "Ahmed", paid = 0.0, amount = 450.0))

        store.updateBill(number = bill.number, customer = "Sami", paid = 0.0, amount = 450.0, createdAt = day)

        assertNull(store.customers().firstOrNull { it.key == "ahmed" })
        assertEquals(450.0, assertNotNull(store.customers().firstOrNull { it.key == "sami" }).owed)
    }

    // --- Removing a bill

    @Test
    fun `removing a bill puts its stock back and leaves the others alone`() {
        val store = store()
        val product = store.aProduct(stock = 50)
        val first = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null))
        val second = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 3, 95.0)), "Sami", null))
        assertEquals(45, assertNotNull(store.product(product.uid)).stock)

        store.deleteBill(second.number)

        assertEquals(48, assertNotNull(store.product(product.uid)).stock)
        assertEquals(listOf(first.number), store.bills.map { it.number })
    }

    // --- The other side of the book

    @Test
    fun `editing a delivery moves the shelf by the difference`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(
            store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0)
        )
        assertEquals(15, assertNotNull(store.product(product.uid)).stock)

        val edited = assertNotNull(
            store.updatePurchase(
                id = purchase.id,
                product = product,
                supplierKey = supplier.key,
                quantity = 8,
                unitCost = 62.0,
                createdAt = day
            )
        )

        assertEquals(18, assertNotNull(store.product(product.uid)).stock)
        assertEquals(496.0, edited.total)
        assertEquals(62.0, assertNotNull(store.product(product.uid)).cost, "latest paid still takes over")
    }

    @Test
    fun `editing a delivery down to a bare figure gives back everything it added`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(
            store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0)
        )

        val edited = assertNotNull(
            store.updatePurchase(
                id = purchase.id,
                product = null,
                supplierKey = supplier.key,
                paid = 0.0,
                amount = 300.0,
                createdAt = day
            )
        )

        assertFalse(edited.isItemised)
        assertEquals(10, assertNotNull(store.product(product.uid)).stock)
        assertEquals(300.0, assertNotNull(store.supplier(supplier.key)).owed)
    }

    @Test
    fun `a supplier bill does not clash with its own number`() {
        val store = store()
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(
            store.recordSupplierBill(supplier.key, amount = 800.0, invoiceNo = "INV-88")
        )

        assertNull(store.purchaseWithInvoiceNo("INV-88", exceptId = purchase.id))
    }

    @Test
    fun `an edit that would not be a delivery changes nothing`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(
            store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0)
        )

        assertNull(
            store.updatePurchase(id = purchase.id, product = null, supplierKey = supplier.key, createdAt = day)
        )
        assertNull(
            store.updatePurchase(id = purchase.id, product = null, supplierKey = "", amount = 50.0, createdAt = day)
        )

        assertEquals(15, assertNotNull(store.product(product.uid)).stock)
        assertEquals(300.0, store.purchases.first().total)
    }
}
