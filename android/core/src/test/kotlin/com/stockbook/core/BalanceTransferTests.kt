package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.model.StatementPeriod
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
 * A balance moved between two accounts that are both real.
 *
 * The case is two branches of one contractor consolidating: both were rightly
 * invoiced, both keep their invoices, and only the outstanding figure moves.
 * Neither account is absorbed and no history is re-filed — that is the whole
 * point, and it is why this is the only way the app joins anything up.
 *
 * **The invariant that matters is that nothing is created or destroyed.** A
 * transfer moves money between two columns of the same book, so what the shop is
 * owed altogether cannot change — and a half-applied transfer is the one bug
 * here that would be invisible on both screens while quietly making the totals
 * wrong.
 */
class BalanceTransferTests {

    private fun store() = StockbookStore(InMemoryRepository())
    private fun thisMonth() = StatementPeriod.thisMonth()

    /** One contractor, entered as two branches, each with its own bill. */
    private fun shopWithTwoBranches(): StockbookStore {
        val store = store()
        val lock = store.addProduct("Cisa lock", 100, 60.0, 95.0)
        store.addCustomer("Ahmed Riyadh")
        store.addCustomer("Ahmed Jeddah")
        store.saveBill(listOf(DraftLine(lock.uid, 10, 95.0)), "Ahmed Riyadh", paid = 0.0)  // 950
        store.saveBill(listOf(DraftLine(lock.uid, 4, 95.0)), "Ahmed Jeddah", paid = 0.0)   // 380
        return store
    }

    @Test
    fun `the balance moves off one account and onto the other`() {
        val store = shopWithTwoBranches()

        assertNotNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0))

        assertEquals(0.0, assertNotNull(store.customer("ahmed jeddah")).owed)
        assertEquals(1330.0, assertNotNull(store.customer("ahmed riyadh")).owed, "950 of its own and 380 arrived")
    }

    @Test
    fun `what the shop is owed altogether does not change`() {
        // The invariant. A transfer moves a figure between two columns of one
        // book; it cannot make or lose money, and a half-applied one would be
        // invisible on both screens while making this wrong.
        val store = shopWithTwoBranches()
        val before = store.customers().sumOf { it.owed }

        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0)

        assertEquals(before, store.customers().sumOf { it.owed })
    }

    @Test
    fun `the invoices stay where they were issued`() {
        // The line this feature is built on. The Jeddah branch's copy of its
        // invoice says Jeddah, and rewriting it would put this book out of step
        // with paper the customer is holding.
        val store = shopWithTwoBranches()

        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0)

        assertEquals(1, store.billsForCustomer("ahmed jeddah").size)
        assertEquals(1, store.billsForCustomer("ahmed riyadh").size)
        // And both accounts are still there. Nobody was absorbed.
        assertEquals(2, store.customers().size)
    }

    @Test
    fun `it shows on both statements, as a charge on one and a settlement on the other`() {
        val store = shopWithTwoBranches()
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0, note = "Consolidating on Riyadh")

        val leaving = assertNotNull(store.statementForCustomer("ahmed jeddah", thisMonth()))
        val arriving = assertNotNull(store.statementForCustomer("ahmed riyadh", thisMonth()))

        // Its own totals on each side, kept out of what was billed and received.
        assertEquals(380.0, leaving.transferredOut)
        assertEquals(0.0, leaving.transferredIn)
        assertEquals(380.0, leaving.billed, "its own bill, and not the transfer")
        assertEquals(0.0, leaving.received, "no money changed hands")
        assertEquals(0.0, leaving.closingBalance)

        assertEquals(380.0, arriving.transferredIn)
        assertEquals(950.0, arriving.billed, "its own bill, and not the transfer")
        assertEquals(1330.0, arriving.closingBalance)
    }

    @Test
    fun `a transfer is neither a payment nor a credit note`() {
        // The reason it has its own line: `received` is what the shop reconciles
        // against its till, and `credited` is goods or money given back. A
        // transfer is neither, and folding it into either would make that figure
        // mean two things.
        val store = shopWithTwoBranches()
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0)

        val leaving = assertNotNull(store.statementForCustomer("ahmed jeddah", thisMonth()))

        assertEquals(0.0, leaving.received)
        assertEquals(0.0, leaving.credited)
        assertEquals(380.0, leaving.transferredOut)
    }

    @Test
    fun `the statement names the account at the other end`() {
        val store = shopWithTwoBranches()
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0)

        val entry = assertNotNull(
            store.transferEntriesFor("ahmed jeddah", isSupplier = false).singleOrNull()
        )

        assertTrue(entry.outgoing)
        assertEquals("Ahmed Riyadh", entry.otherName)
        // Resolved from the roster, so a rename afterwards reads correctly here
        // rather than leaving a stale copy on the record.
        store.updateCustomer("ahmed riyadh", "Ahmed Riyadh Branch", null, null)
        assertEquals(
            "Ahmed Riyadh Branch",
            assertNotNull(store.transferEntriesFor("ahmed jeddah", isSupplier = false).singleOrNull()).otherName
        )
    }

    @Test
    fun `an account cannot transfer to itself, or to somebody who is not there`() {
        val store = shopWithTwoBranches()

        assertNull(store.transferBalance("ahmed jeddah", "ahmed jeddah", 100.0))
        assertNull(store.transferBalance("ahmed jeddah", "nobody", 100.0))
        assertNull(store.transferBalance("nobody", "ahmed jeddah", 100.0))
        assertNull(store.transferBalance("", "ahmed jeddah", 100.0))
        assertNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", 0.0))
        assertNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", -50.0))

        assertTrue(store.balanceTransfers.isEmpty())
    }

    @Test
    fun `more than is owed is allowed, and leaves the account in advance`() {
        // The app already reads a negative balance as money held in advance, and
        // refusing would block a legitimate shuffle of a prepayment.
        val store = shopWithTwoBranches()

        assertNotNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", 500.0))

        assertEquals(-120.0, assertNotNull(store.customer("ahmed jeddah")).owed, "380 owed, 500 moved")
        assertEquals(1450.0, assertNotNull(store.customer("ahmed riyadh")).owed)
    }

    @Test
    fun `removing one puts both balances back`() {
        val store = shopWithTwoBranches()
        val before = store.customers().associate { it.key to it.owed }
        val transfer = assertNotNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0))

        store.deleteBalanceTransfer(transfer.id)

        assertEquals(before, store.customers().associate { it.key to it.owed })
        assertTrue(store.balanceTransfers.isEmpty())
    }

    @Test
    fun `a transfer survives being written down and read back`() {
        val store = shopWithTwoBranches()
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0, note = "Consolidating on Riyadh")

        val reopened = StockbookStore(InMemoryRepository()).also {
            it.replaceEverything(store.makeBackupDocument())
        }

        assertEquals(1, reopened.balanceTransfers.size)
        assertEquals("Consolidating on Riyadh", reopened.balanceTransfers[0].note)
        assertEquals(0.0, assertNotNull(reopened.customer("ahmed jeddah")).owed)
        assertEquals(1330.0, assertNotNull(reopened.customer("ahmed riyadh")).owed)
    }

    @Test
    fun `a customer transfer leaves the supplier side alone`() {
        // Both sides share a key space — a firm the shop both buys from and
        // sells to has the same key on each — so the record says which side it
        // belongs to rather than letting the keys decide.
        val store = shopWithTwoBranches()
        store.addSupplier("Ahmed Jeddah", openingBalance = 700.0)

        store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0)

        assertEquals(700.0, assertNotNull(store.supplier("ahmed jeddah")).owed)
    }

    // --- Suppliers

    @Test
    fun `the supplier side moves the same way`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 4, 60.0, 95.0)
        val north = assertNotNull(store.addSupplier("Gulf Locks North"))
        assertNotNull(store.addSupplier("Gulf Locks South", openingBalance = 200.0))
        store.recordPurchase(product, north.key, quantity = 10, unitCost = 60.0, paid = 0.0) // 600 owed out

        val before = store.suppliers().sumOf { it.owed }
        assertNotNull(
            store.transferBalance("gulf locks north", "gulf locks south", 600.0, isSupplier = true)
        )

        assertEquals(0.0, assertNotNull(store.supplier("gulf locks north")).owed)
        assertEquals(800.0, assertNotNull(store.supplier("gulf locks south")).owed)
        assertEquals(before, store.suppliers().sumOf { it.owed }, "what the shop owes out is unchanged")
        // The delivery stays with the branch it arrived from.
        assertEquals(1, store.purchasesForSupplier("gulf locks north").size)
    }

    @Test
    fun `the shelf is untouched by a supplier transfer`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 4, 60.0, 95.0)
        val north = assertNotNull(store.addSupplier("Gulf Locks North"))
        assertNotNull(store.addSupplier("Gulf Locks South"))
        store.recordPurchase(product, north.key, quantity = 10, unitCost = 62.5)
        val stock = assertNotNull(store.product(product.uid)).stock

        store.transferBalance("gulf locks north", "gulf locks south", 100.0, isSupplier = true)

        assertEquals(stock, assertNotNull(store.product(product.uid)).stock)
        assertEquals(62.5, assertNotNull(store.product(product.uid)).cost)
    }

    @Test
    fun `a supplier transfer cannot name a customer`() {
        val store = shopWithTwoBranches()

        assertNull(store.transferBalance("ahmed jeddah", "ahmed riyadh", 380.0, isSupplier = true))
        assertFalse(store.balanceTransfers.any { it.isSupplier })
    }
}
