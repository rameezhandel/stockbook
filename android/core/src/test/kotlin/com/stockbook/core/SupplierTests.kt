package com.stockbook.core

import com.stockbook.core.model.Product
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.model.Supplier
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.transfer.BackupService
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The supplier side of the book: who the shop buys from, and what it owes them.
 *
 * Where a test here mirrors one in `CustomerRosterTests`, that is deliberate. The
 * two halves are the same arithmetic pointed in opposite directions, and the two
 * bugs the customer half shipped — a payment dropped for somebody with no
 * history, and a total that ignored payments entirely — are exactly the ones this
 * half would have shipped too.
 */
class SupplierTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aProduct(stock: Int = 0): Product =
        addProduct("Cisa lock", stock, 60.0, 95.0)

    // --- The roster

    @Test
    fun `a supplier entered by hand exists before anything has been delivered`() {
        val store = store()
        store.addSupplier("Al Faisal Hardware", phone = "0500 111 222", place = "Dammam")

        val supplier = assertNotNull(store.suppliers().firstOrNull())
        assertEquals("al faisal hardware", supplier.key)
        assertEquals("Dammam", supplier.place)
        assertEquals(0, supplier.purchaseCount)
        assertFalse(supplier.hasHistory)
        assertTrue(supplier.isOnRoster)
    }

    @Test
    fun `adding the same supplier twice corrects them rather than duplicating them`() {
        val store = store()
        store.addSupplier("Al Faisal Hardware")
        store.addSupplier("AL FAISAL hardware", phone = "0500 111 222")

        assertEquals(1, store.suppliers().size)
        assertEquals("AL FAISAL hardware", store.suppliers().first().name, "the later spelling wins")
        assertEquals("0500 111 222", store.suppliers().first().phone)
    }

    @Test
    fun `a blank name is not a supplier`() {
        val store = store()
        assertNull(store.addSupplier("   "))
        assertTrue(store.suppliers().isEmpty())
    }

    @Test
    fun `a rename brings the purchases and the payments with it`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        store.recordPurchase(product, record.key, quantity = 10, unitCost = 60.0, paid = 0.0)
        store.recordSupplierPayment(record.key, 200.0)

        store.updateSupplier(record.key, "Al Faisal Hardware", null, null)

        val supplier = assertNotNull(store.suppliers().singleOrNull())
        assertEquals("al faisal hardware", supplier.key)
        assertEquals(1, store.purchasesForSupplier(supplier.key).size)
        assertEquals(1, store.supplierPaymentsFor(supplier.key).size)
        // 600 delivered, nothing paid on the day, 200 paid since.
        assertEquals(400.0, supplier.owed)
    }

    // --- Deliveries

    @Test
    fun `a delivery puts stock on the shelf and takes the new buying price`() {
        val store = store()
        val product = store.aProduct(stock = 4)
        val record = assertNotNull(store.addSupplier("Al Faisal"))

        val purchase = assertNotNull(
            store.recordPurchase(product, record.key, quantity = 10, unitCost = 62.5)
        )

        assertEquals(14, assertNotNull(store.product(product.uid)).stock)
        assertEquals(62.5, assertNotNull(store.product(product.uid)).cost, "cost is latest paid")
        assertEquals(625.0, purchase.total)
        assertNull(purchase.paid, "settled on the spot")
        assertEquals(0.0, purchase.balance)
    }

    @Test
    fun `a delivery not paid for lands on what the shop owes`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal"))

        store.recordPurchase(product, record.key, quantity = 10, unitCost = 60.0, paid = 100.0)

        assertEquals(500.0, assertNotNull(store.supplier(record.key)).owed)
        assertEquals(listOf("Al Faisal") to 500.0, store.payable())
    }

    @Test
    fun `a delivery cannot be overpaid`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal"))

        val purchase = assertNotNull(
            store.recordPurchase(product, record.key, quantity = 2, unitCost = 50.0, paid = 400.0)
        )

        assertEquals(100.0, purchase.paid, "clamped to the total")
        assertEquals(0.0, purchase.balance)
    }

    @Test
    fun `nothing delivered is not a purchase`() {
        val store = store()
        val product = store.aProduct(stock = 3)
        val record = assertNotNull(store.addSupplier("Al Faisal"))

        assertNull(store.recordPurchase(product, record.key, quantity = 0, unitCost = 60.0))
        assertNull(store.recordPurchase(product, "", quantity = 5, unitCost = 60.0), "and neither is nobody")
        assertEquals(3, assertNotNull(store.product(product.uid)).stock)
        assertTrue(store.purchases.isEmpty())
    }

    // --- Voiding

    @Test
    fun `voiding a delivery takes the stock back off the shelf`() {
        val store = store()
        val product = store.aProduct(stock = 2)
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(
            store.recordPurchase(product, record.key, quantity = 10, unitCost = 60.0, paid = 0.0)
        )

        store.deletePurchase(purchase.id)

        assertEquals(2, assertNotNull(store.product(product.uid)).stock)
        assertTrue(store.purchases.isEmpty(), "removed outright rather than marked")
        assertEquals(0.0, assertNotNull(store.supplier(record.key)).owed, "and owed for by nobody")
        assertEquals(0, assertNotNull(store.supplier(record.key)).purchaseCount)
    }

    @Test
    fun `removing twice does not remove the stock twice`() {
        val store = store()
        val product = store.aProduct(stock = 5)
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(store.recordPurchase(product, record.key, quantity = 3, unitCost = 60.0))

        store.deletePurchase(purchase.id)
        store.deletePurchase(purchase.id)

        assertEquals(5, assertNotNull(store.product(product.uid)).stock)
    }

    @Test
    fun `stock already sold on floors at zero rather than going negative`() {
        val store = store()
        val product = store.aProduct(stock = 0)
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        val purchase = assertNotNull(store.recordPurchase(product, record.key, quantity = 4, unitCost = 60.0))
        // All four sold before anybody noticed the delivery was wrong.
        store.saveBill(listOf(com.stockbook.core.store.DraftLine(product.uid, 4, 95.0)), "Ahmed", null)

        store.deletePurchase(purchase.id)

        assertEquals(0, assertNotNull(store.product(product.uid)).stock)
    }

    // --- Money out

    @Test
    fun `a payment brings down what the shop owes`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        store.recordPurchase(product, record.key, quantity = 10, unitCost = 60.0, paid = 0.0)

        store.recordSupplierPayment(record.key, 250.0, note = "cash")

        assertEquals(350.0, assertNotNull(store.supplier(record.key)).owed)
        assertEquals("cash", store.supplierPaymentsFor(record.key).first().note)
    }

    /**
     * The bug the customer side shipped, written down before it can be shipped
     * here: a supplier entered with what the paper book says is owed, paid off
     * before a single delivery has been recorded through the app.
     */
    @Test
    fun `a payment to a supplier who has never delivered still counts`() {
        val store = store()
        val record = assertNotNull(store.addSupplier("Al Faisal", openingBalance = 1000.0))

        store.recordSupplierPayment(record.key, 400.0)

        assertEquals(600.0, assertNotNull(store.supplier(record.key)).owed)
    }

    @Test
    fun `a deleted payment puts the balance back`() {
        val store = store()
        val record = assertNotNull(store.addSupplier("Al Faisal", openingBalance = 1000.0))
        val payment = assertNotNull(store.recordSupplierPayment(record.key, 400.0))

        store.deleteSupplierPayment(payment.id)

        assertEquals(1000.0, assertNotNull(store.supplier(record.key)).owed)
    }

    @Test
    fun `paying ahead reads as an advance rather than as nothing`() {
        val store = store()
        val record = assertNotNull(store.addSupplier("Al Faisal", openingBalance = 100.0))

        store.recordSupplierPayment(record.key, 250.0)

        assertEquals(-150.0, assertNotNull(store.supplier(record.key)).owed)
        assertEquals(emptyList<String>() to 0.0, store.payable(), "an advance is not a debt")
    }

    /** The mirror of the Today banner's bug: a total that ignored payments. */
    @Test
    fun `what the shop owes counts payments and carried-over balances`() {
        val store = store()
        val product = store.aProduct()
        val paid = assertNotNull(store.addSupplier("Settled Up"))
        val owing = assertNotNull(store.addSupplier("Still Owed", openingBalance = 300.0))

        store.recordPurchase(product, paid.key, quantity = 5, unitCost = 60.0, paid = 0.0)
        store.recordSupplierPayment(paid.key, 300.0)

        val (names, total) = store.payable()
        assertEquals(listOf("Still Owed"), names, "a supplier paid in full is not owed anything")
        assertEquals(300.0, total)
        assertEquals(owing.key, "still owed")
    }

    // --- Statements

    @Test
    fun `a supplier statement carries the balance forward and reads downwards`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal", openingBalance = 700.0))
        store.recordPurchase(
            product, record.key, quantity = 5, unitCost = 60.0, paid = 0.0,
            createdAt = Instant.parse("2026-08-04T08:00:00Z")
        )
        store.recordSupplierPayment(record.key, 200.0, paidAt = Instant.parse("2026-08-20T08:00:00Z"))

        val statement = assertNotNull(
            store.statementForSupplier(record.key, StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )

        assertEquals(700.0, statement.openingBalance, "carried over from the paper book")
        assertEquals(300.0, statement.billed)
        assertEquals(200.0, statement.received)
        assertEquals(800.0, statement.closingBalance)
        assertEquals(2, statement.entries.size)
        assertEquals(statement.closingBalance, statement.runningBalances.last())
        assertTrue(statement.party.isSupplier)
    }

    @Test
    fun `a statement for a supplier nobody has heard of is null`() {
        assertNull(store().statementForSupplier("nobody", StatementPeriod.thisMonth()))
    }

    // --- The file

    @Test
    fun `a backup carries the supplier side to the new phone`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal", phone = "0500 111 222", openingBalance = 700.0))
        val purchase = assertNotNull(
            store.recordPurchase(product, record.key, quantity = 5, unitCost = 60.0, paid = 100.0)
        )
        store.recordSupplierPayment(record.key, 200.0, note = "cash")

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))

        // The bump is the rule being applied, not abandoned: a reader that dropped
        // these would say the shop owes nobody.
        assertEquals(2, document.version)
        assertEquals(1, document.suppliers.size)
        assertEquals(1, document.purchases.size)
        assertEquals(1, document.supplierPayments.size)

        val restored = store()
        restored.replaceEverything(document)

        val supplier = assertNotNull(restored.supplier(record.key))
        assertEquals("0500 111 222", supplier.phone)
        assertEquals(700.0, supplier.openingBalance)
        // 700 carried over + 300 delivered − 100 paid on the day − 200 paid since.
        assertEquals(700.0, supplier.owed)
        assertEquals(purchase.id, restored.purchases.first().id)
        assertEquals("cash", restored.supplierPayments.first().note)
    }

    @Test
    fun `starting over clears the supplier side too`() {
        val store = store()
        val product = store.aProduct()
        val record = assertNotNull(store.addSupplier("Al Faisal"))
        store.recordPurchase(product, record.key, quantity = 5, unitCost = 60.0, paid = 0.0)
        store.recordSupplierPayment(record.key, 50.0)

        store.startOver()

        assertTrue(store.suppliers().isEmpty())
        assertTrue(store.purchases.isEmpty())
        assertTrue(store.supplierPayments.isEmpty())
    }

    @Test
    fun `the two sides of the book do not leak into each other`() {
        val store = store()
        val product = store.aProduct(stock = 10)
        val supplier = assertNotNull(store.addSupplier("Ahmed"))
        store.addCustomer("Ahmed", openingBalance = 500.0)
        store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0, paid = 0.0)

        // One name, two accounts, pointing opposite ways. The keys match, which is
        // exactly why this has to be checked.
        assertEquals(Supplier.key("Ahmed"), com.stockbook.core.model.Customer.key("Ahmed"))
        assertEquals(300.0, assertNotNull(store.supplier("ahmed")).owed, "what the shop owes")
        assertEquals(500.0, assertNotNull(store.customer("ahmed")).owed, "what Ahmed owes the shop")
        assertEquals(0, assertNotNull(store.customer("ahmed")).billCount)
    }
}
