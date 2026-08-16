package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.RestockMode
import com.stockbook.core.store.StockbookStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules the handoff is specific about — the ones where a plausible-looking
 * alternative implementation would be wrong.
 *
 * A direct port of the iOS suite, assertion for assertion. The two apps share a
 * file format and a shop; they had better share their arithmetic.
 */
class StoreTests {

    private fun makeStore() = StockbookStore(InMemoryRepository())

    // --- Products

    @Test
    fun `duplicate names are ignored case-insensitively`() {
        val store = makeStore()
        val first = store.addProduct("Padlock", stock = 10, cost = 5.0, price = 9.0)
        val second = store.addProduct("  padlock ", stock = 99, cost = 1.0, price = 2.0)

        assertEquals(first.uid, second.uid)
        assertEquals(1, store.products.size)
        assertEquals(10, store.product(first.uid)?.stock, "the existing product must not be overwritten")
    }

    @Test
    fun `a draft needs a name, a stock figure, a cost figure and a price above zero`() {
        assertTrue(StockbookStore.isProductDraftComplete("Deadbolt", "0", "0", "12"))
        assertFalse(StockbookStore.isProductDraftComplete("", "1", "1", "1"))
        assertFalse(StockbookStore.isProductDraftComplete("Deadbolt", "", "1", "1"))
        assertFalse(StockbookStore.isProductDraftComplete("Deadbolt", "1", "", "1"))
        assertFalse(
            StockbookStore.isProductDraftComplete("Deadbolt", "1", "1", "0"),
            "a selling price of zero is not a selling price"
        )
    }

    // --- Billing

    @Test
    fun `saving a bill snapshots the line and decrements stock`() {
        val store = makeStore()
        val product = store.addProduct("Cisa lock", stock = 20, cost = 60.0, price = 95.0)

        val bill = assertNotNull(
            store.saveBill(
                lines = listOf(DraftLine(product.uid, qty = 3, price = 95.0)),
                customer = "  Ahmed Contracting ",
                paid = null
            )
        )

        assertEquals(1, bill.number)
        assertEquals(285.0, bill.total)
        assertNull(bill.paid, "null means paid in full")
        assertEquals("Ahmed Contracting", bill.who, "the name is trimmed")
        assertEquals(17, store.product(product.uid)?.stock)
        assertEquals("Cisa lock", bill.lines.first().name)
    }

    @Test
    fun `stock floors at zero rather than going negative`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 2, cost = 3.0, price = 10.0)

        // Overselling is allowed — the customer is standing there and the count
        // may simply be wrong — but the shelf never goes below empty.
        store.saveBill(listOf(DraftLine(product.uid, qty = 9, price = 10.0)), "Sami", null)

        assertEquals(0, store.product(product.uid)?.stock)
    }

    @Test
    fun `paying the whole total is paid in full, not a part payment`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 10, cost = 3.0, price = 10.0)

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, qty = 2, price = 10.0)), "Sami", paid = 20.0)
        )

        assertNull(bill.paid, "otherwise the receipt says somebody owes zero")
        assertFalse(bill.isPartPaid)
        assertEquals(0.0, bill.balance)
    }

    @Test
    fun `a part payment clamps to the total and leaves a balance`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 10, cost = 3.0, price = 10.0)

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, qty = 5, price = 10.0)), "Sami", paid = 20.0)
        )

        assertEquals(20.0, bill.paid)
        assertEquals(30.0, bill.balance)
        assertTrue(bill.isPartPaid)
    }

    @Test
    fun `a bill with no customer is not a bill`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 10, cost = 3.0, price = 10.0)

        assertNull(store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "   ", null))
        assertNull(store.saveBill(emptyList(), "Sami", null))
        assertTrue(store.bills.isEmpty())
    }

    @Test
    fun `bill numbers are stable and monotonic`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 100, cost = 3.0, price = 10.0)

        val first = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "A", null))
        val second = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "B", null))
        store.void(second)
        val third = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "C", null))

        assertEquals(1, first.number)
        assertEquals(2, second.number)
        assertEquals(3, third.number, "voiding must not hand a number back out")
    }

    @Test
    fun `history does not move when a product is repriced or renamed`() {
        val store = makeStore()
        val product = store.addProduct("Cisa lock", stock = 20, cost = 60.0, price = 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null)

        store.update(store.product(product.uid)!!, name = "Cisa lock v2", stock = 20, cost = 70.0, price = 120.0)

        // Asserted against the store, not a copy taken before the edit — a stale
        // value type would pass this test without proving anything.
        val bill = store.bills.first()
        assertEquals("Cisa lock", bill.lines.first().name)
        assertEquals(95.0, bill.lines.first().price)
        assertEquals(190.0, bill.total)
    }

    @Test
    fun `voiding puts the stock back and is idempotent`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 10, cost = 3.0, price = 10.0)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 4, 10.0)), "Sami", null))
        assertEquals(6, store.product(product.uid)?.stock)

        store.void(bill)
        assertEquals(10, store.product(product.uid)?.stock)

        store.void(bill)
        assertEquals(10, store.product(product.uid)?.stock, "voiding twice must not restock twice")
        assertTrue(store.bills.first().voided)
        assertEquals(1, store.bills.size, "a voided bill is still history")
    }

    // --- Customers

    @Test
    fun `the owed banner counts distinct people, not bills`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 100, cost = 3.0, price = 10.0)

        store.saveBill(listOf(DraftLine(product.uid, 10, 10.0)), "Ahmed Contracting", paid = 60.0)
        store.saveBill(listOf(DraftLine(product.uid, 10, 10.0)), "Ahmed Contracting", paid = 16.0)

        val (names, total) = store.outstanding()
        assertEquals(listOf("Ahmed Contracting"), names)
        assertEquals(124.0, total)
    }

    @Test
    fun `suggestions rank debtors first, then frequency`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 100, cost = 3.0, price = 10.0)

        store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Frequent", null)
        store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Frequent", null)
        store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Rare", null)
        store.saveBill(listOf(DraftLine(product.uid, 5, 10.0)), "Debtor", paid = 10.0)

        assertEquals(listOf("Debtor", "Frequent", "Rare"), store.customerSuggestions("").map { it.name })
    }

    @Test
    fun `suggestions filter by what is typed and drop an exact match`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 100, cost = 3.0, price = 10.0)
        store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Ahmed Contracting", null)
        store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Sami", null)

        assertEquals(listOf("Ahmed Contracting"), store.customerSuggestions("ahm").map { it.name })
        assertTrue(
            store.customerSuggestions("Ahmed Contracting").isEmpty(),
            "no point suggesting exactly what has been typed"
        )
    }

    @Test
    fun `a voided bill leaves the customer book`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 100, cost = 3.0, price = 10.0)
        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 1, 10.0)), "Sami", paid = 0.0))

        store.void(bill)

        assertTrue(store.customers().isEmpty())
        assertTrue(store.outstanding().first.isEmpty())
    }

    // --- Restock

    @Test
    fun `quick add raises stock and leaves the buying price alone`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 4, cost = 3.0, price = 10.0)

        store.restock(product, quantity = 6, mode = RestockMode.QUICK_ADD, unitCost = 99.0)

        assertEquals(10, store.product(product.uid)?.stock)
        assertEquals(3.0, store.product(product.uid)?.cost, "quick add is not a purchase")
    }

    @Test
    fun `a purchase overwrites the buying price with the latest paid`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 4, cost = 3.0, price = 10.0)

        store.restock(product, quantity = 6, mode = RestockMode.PURCHASE, unitCost = 5.0)

        assertEquals(10, store.product(product.uid)?.stock)
        assertEquals(5.0, store.product(product.uid)?.cost, "cost is latest paid, not a weighted average")
    }

    @Test
    fun `nothing typed closes the sheet without touching anything`() {
        val store = makeStore()
        val product = store.addProduct("Hinge", stock = 4, cost = 3.0, price = 10.0)

        store.restock(product, quantity = 0, mode = RestockMode.PURCHASE, unitCost = 5.0)

        assertEquals(4, store.product(product.uid)?.stock)
        assertEquals(3.0, store.product(product.uid)?.cost)
    }

    // --- Whole-database

    @Test
    fun `starting over keeps the language and nothing else`() {
        val store = makeStore()
        store.setOwnerName("Khalid")
        store.setCurrency(Currency.INR)
        store.addProduct("Hinge", 4, 3.0, 10.0)
        store.completeSetup()

        store.startOver()

        assertTrue(store.products.isEmpty())
        assertTrue(store.bills.isEmpty())
        assertEquals("", store.settings.ownerName)
        assertFalse(store.settings.setupCompleted)
        assertEquals(Currency.default.code, store.settings.currencyCode)
    }
}
