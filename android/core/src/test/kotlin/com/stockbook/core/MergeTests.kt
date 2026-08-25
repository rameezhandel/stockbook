package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One firm entered twice, joined on purpose.
 *
 * The counts are the least of it. What these tests are really for is the money:
 * the merge this replaces happened by accident on a rename, threw one opening
 * balance away and left the credit notes filed under a key nothing pointed at,
 * and the test that covered it asserted row counts alone — which is exactly why
 * none of that was noticed. **Every test here checks a figure.**
 */
class MergeTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun shopWithProduct(): Pair<StockbookStore, String> {
        val store = store()
        return store to store.addProduct("Cisa lock", 100, 60.0, 95.0).uid
    }

    /** Ahmed, entered twice: once as himself and once as the firm. */
    private fun shopWithADuplicate(): StockbookStore {
        val (store, lock) = shopWithProduct()
        store.addCustomer("Ahmed", phone = "0500 111 222", openingBalance = 300.0)
        store.addCustomer("Ahmed Contracting", place = "Riyadh", openingBalance = 700.0)
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0)              // 190
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed Contracting", paid = 0.0)  // 95
        store.recordPayment("ahmed", 40.0)
        store.addCreditNote("ahmed", amount = 50.0)
        return store
    }

    @Test
    fun `the preview says what will move before anything moves`() {
        val store = shopWithADuplicate()

        val preview = assertNotNull(store.previewCustomerMerge("ahmed", "ahmed contracting"))

        assertEquals("Ahmed", preview.from)
        assertEquals("Ahmed Contracting", preview.into)
        assertEquals(1, preview.bills)
        assertEquals(1, preview.payments)
        assertEquals(1, preview.creditNotes)
        assertEquals(1000.0, preview.openingBalance, "300 and 700 added, never one of them chosen")
        // 1000 opening + 285 billed - 40 paid - 50 credited.
        assertEquals(1195.0, preview.owed)
        assertFalse(preview.movesNothing)

        // And it is a preview: the book is exactly as it was.
        assertEquals(2, store.customers().size)
        // 300 opening + 190 billed - 40 paid - 50 credited.
        assertEquals(400.0, assertNotNull(store.customer("ahmed")).owed)
    }

    @Test
    fun `merging brings the bills, the payments and the credit notes across`() {
        val store = shopWithADuplicate()
        val expected = assertNotNull(store.previewCustomerMerge("ahmed", "ahmed contracting")).owed

        assertTrue(store.mergeCustomer("ahmed", "ahmed contracting"))

        val ahmed = assertNotNull(store.customers().singleOrNull())
        assertEquals("Ahmed Contracting", ahmed.name)
        assertEquals(2, ahmed.billCount)
        // The figure the owner was shown is the figure they end up with. Nothing
        // else in this suite matters as much as these two lines agreeing.
        assertEquals(expected, ahmed.owed)
        assertEquals(1195.0, ahmed.owed)

        // Nothing is left filed under a name that no longer exists — the credit
        // note especially, which the accidental merge used to strand.
        assertTrue(store.bills.all { Customer.key(it.who) == "ahmed contracting" })
        assertTrue(store.payments.all { it.customerKey == "ahmed contracting" })
        assertTrue(store.creditNotes.all { it.customerKey == "ahmed contracting" })
    }

    @Test
    fun `the two opening balances are added, never one of them dropped`() {
        // The bug that made this feature necessary, on its own so it cannot be
        // lost among the others.
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 300.0)
        store.addCustomer("Ahmed Contracting", openingBalance = 700.0)

        store.mergeCustomer("ahmed", "ahmed contracting")

        val ahmed = assertNotNull(store.customer("ahmed contracting"))
        assertEquals(1000.0, ahmed.openingBalance)
        assertEquals(1000.0, ahmed.owed)
    }

    @Test
    fun `the surviving details win, and fill in from the other only where blank`() {
        val store = shopWithADuplicate()

        store.mergeCustomer("ahmed", "ahmed contracting")

        val ahmed = assertNotNull(store.customer("ahmed contracting"))
        assertEquals("Riyadh", ahmed.place, "its own")
        assertEquals("0500 111 222", ahmed.phone, "it had none, so the other's rather than nothing")
    }

    @Test
    fun `a name that has only ever appeared on bills can be merged away`() {
        // The common duplicate: a name typed at the counter in a hurry that
        // nobody ever added to the roster.
        val (store, lock) = shopWithProduct()
        store.addCustomer("Ahmed Contracting")
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "ahmed cont", paid = 0.0)

        assertTrue(store.mergeCustomer("ahmed cont", "ahmed contracting"))

        val ahmed = assertNotNull(store.customers().singleOrNull())
        assertEquals("Ahmed Contracting", ahmed.name)
        assertEquals(95.0, ahmed.owed)
        assertEquals("Ahmed Contracting", store.bills[0].who)
    }

    @Test
    fun `merging into a name that has only ever appeared on bills keeps the opening balance`() {
        // The other way round, and the one that could quietly lose money: the
        // roster entry is the one going, so its opening balance has to travel
        // rather than be deleted with it.
        val (store, lock) = shopWithProduct()
        store.addCustomer("Ahmed", openingBalance = 300.0)
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed Contracting", paid = 0.0)

        assertTrue(store.mergeCustomer("ahmed", "ahmed contracting"))

        val ahmed = assertNotNull(store.customers().singleOrNull())
        assertEquals("Ahmed Contracting", ahmed.name)
        assertEquals(300.0, ahmed.openingBalance)
        assertEquals(395.0, ahmed.owed)
    }

    @Test
    fun `a customer cannot be merged into themselves or into somebody who is not there`() {
        val store = shopWithADuplicate()

        assertNull(store.previewCustomerMerge("ahmed", "ahmed"))
        assertFalse(store.mergeCustomer("ahmed", "ahmed"))
        assertNull(store.previewCustomerMerge("ahmed", "nobody"))
        assertFalse(store.mergeCustomer("ahmed", "nobody"))
        assertFalse(store.mergeCustomer("nobody", "ahmed"))
        assertFalse(store.mergeCustomer("", "ahmed"))

        assertEquals(2, store.customers().size)
    }

    @Test
    fun `what the shop is owed altogether does not change`() {
        // The property that says the merge moved money rather than making or
        // losing any: two accounts joined owe what the two owed.
        val store = shopWithADuplicate()
        val before = store.customers().sumOf { it.owed }

        store.mergeCustomer("ahmed", "ahmed contracting")

        assertEquals(before, store.customers().sumOf { it.owed })
    }

    @Test
    fun `the merge survives being written down and read back`() {
        val store = shopWithADuplicate()
        store.mergeCustomer("ahmed", "ahmed contracting")

        val reopened = StockbookStore(InMemoryRepository()).also {
            it.replaceEverything(store.makeBackupDocument())
        }

        val ahmed = assertNotNull(reopened.customers().singleOrNull())
        assertEquals(1195.0, ahmed.owed)
        assertEquals(1, reopened.creditNotes.size)
        assertEquals("ahmed contracting", reopened.creditNotes[0].customerKey)
    }

    // --- Suppliers

    @Test
    fun `a supplier merge moves the deliveries and the payments`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 4, 60.0, 95.0)
        val faisal = assertNotNull(store.addSupplier("Al Faisal", openingBalance = 200.0))
        val hardware = assertNotNull(store.addSupplier("Al Faisal Hardware", openingBalance = 100.0))
        store.recordPurchase(product, faisal.key, quantity = 10, unitCost = 60.0, paid = 0.0)
        store.recordSupplierPayment(faisal.key, 150.0)

        val preview = assertNotNull(store.previewSupplierMerge(faisal.key, hardware.key))
        assertEquals(1, preview.deliveries)
        assertEquals(1, preview.payments)
        assertEquals(0, preview.bills, "a supplier has none, and the line is not drawn")
        assertEquals(300.0, preview.openingBalance)
        // 300 opening + 600 delivered - 150 paid.
        assertEquals(750.0, preview.owed)

        assertTrue(store.mergeSupplier(faisal.key, hardware.key))

        val supplier = assertNotNull(store.suppliers().singleOrNull())
        assertEquals("Al Faisal Hardware", supplier.name)
        assertEquals(750.0, supplier.owed)
        assertEquals(1, store.purchasesForSupplier(supplier.key).size)
        assertEquals(1, store.supplierPaymentsFor(supplier.key).size)
    }

    @Test
    fun `the shelf is untouched by a supplier merge`() {
        // A merge is about who a delivery came from, never about what arrived.
        val store = store()
        val product = store.addProduct("Cisa lock", 4, 60.0, 95.0)
        val faisal = assertNotNull(store.addSupplier("Al Faisal"))
        assertNotNull(store.addSupplier("Al Faisal Hardware"))
        store.recordPurchase(product, faisal.key, quantity = 10, unitCost = 62.5)
        val stock = assertNotNull(store.product(product.uid)).stock

        store.mergeSupplier(faisal.key, "al faisal hardware")

        val after = assertNotNull(store.product(product.uid))
        assertEquals(stock, after.stock)
        assertEquals(62.5, after.cost)
    }
}
