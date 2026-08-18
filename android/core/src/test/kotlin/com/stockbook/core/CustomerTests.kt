package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Customers are derived from bills and identified by name, so how that name is
 * compared *is* the feature. A shop where "ahmed" and "Ahmed" are two people has
 * a broken debtors list.
 */
class CustomerTests {

    private fun makeStore() = StockbookStore(InMemoryRepository())

    @Test
    fun `case and stray spaces do not split one person in two`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)

        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "Ahmed", null)
        store.saveBill(listOf(DraftLine(hinge.uid, 2, 10.0)), "  ahmed ", null)
        store.saveBill(listOf(DraftLine(hinge.uid, 3, 10.0)), "AHMED", null)

        val customers = store.customers()
        assertEquals(1, customers.size)
        assertEquals(3, customers.first().billCount)
        assertEquals(60.0, customers.first().total)
    }

    @Test
    fun `the most recent spelling is the one shown`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)

        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "ahmed", null)
        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "Ahmed Al-Amri", null)

        // Correcting the capitalisation on a new bill corrects it everywhere it
        // is shown, without rewriting what the older bill records.
        assertEquals("Ahmed Al-Amri", store.customers().first().name)
        assertEquals("ahmed", store.bills.last().who)
    }

    @Test
    fun `filtering by customer ignores case`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)

        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "Ahmed", null)
        val second = assertNotNull(store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "ahmed", null))
        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "Sami", null)

        store.deleteBill(second.number)

        // Removed outright: it is not history any more, so it is not listed.
        assertEquals(1, store.billsForCustomer(Customer.key("AHMED")).size)
        assertEquals(1, store.billsForCustomer(Customer.key("sami")).size)
    }

    @Test
    fun `a removed bill is neither a sale nor a debt`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)

        store.saveBill(listOf(DraftLine(hinge.uid, 5, 10.0)), "Ahmed", paid = 20.0)
        val mistake = assertNotNull(
            store.saveBill(listOf(DraftLine(hinge.uid, 9, 10.0)), "Ahmed", paid = 0.0)
        )

        store.deleteBill(mistake.number)

        val ahmed = store.customers().first()
        assertEquals(1, ahmed.billCount)
        assertEquals(50.0, ahmed.total)
        assertEquals(30.0, ahmed.owed)
    }

    @Test
    fun `the owed banner counts one person however they were capitalised`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)

        store.saveBill(listOf(DraftLine(hinge.uid, 10, 10.0)), "Ahmed", paid = 60.0)
        store.saveBill(listOf(DraftLine(hinge.uid, 10, 10.0)), "AHMED", paid = 16.0)

        val (names, total) = store.outstanding()
        assertEquals(1, names.size)
        assertEquals(124.0, total)
    }

    @Test
    fun `suggestions match without regard to case`() {
        val store = makeStore()
        val hinge = store.addProduct("Hinge", 100, 3.0, 10.0)
        store.saveBill(listOf(DraftLine(hinge.uid, 1, 10.0)), "Ahmed Contracting", null)

        assertEquals(listOf("Ahmed Contracting"), store.customerSuggestions("AHM").map { it.name })
        assertTrue(
            store.customerSuggestions("  ahmed contracting ").isEmpty(),
            "an exact match differing only in case is still an exact match"
        )
    }

    @Test
    fun `the key is the one place a name becomes an identity`() {
        assertEquals(Customer.key("Ahmed"), Customer.key("  AHMED  "))
        assertNotEquals(Customer.key("Ahmed"), Customer.key("Ahmad"))
        assertTrue(Customer.key("   ").isEmpty())
    }
}
