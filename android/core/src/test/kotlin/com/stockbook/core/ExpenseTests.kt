package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The owner's own spending.
 *
 * Half of this suite is about what an expense does **not** do. That is the
 * feature: it was built as a ledger joined to nothing, so that adding it could
 * not move a figure that already worked. Rules like that decay silently unless
 * something asserts them, because the day somebody nets expenses into "Sold" is
 * the day a shop's takings stop reconciling against the till and nobody knows
 * why.
 */
class ExpenseTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private val august: Instant = Instant.parse("2026-08-13T12:00:00Z")

    /** A day in order, so "which was used last" is a fact rather than a guess. */
    private fun at(day: Long): Instant = august.plusSeconds(day * 86_400)

    @Test
    fun `an expense is written down`() {
        val store = store()

        val expense = assertNotNull(store.addExpense(120.0, "Petrol", august))

        assertEquals(120.0, expense.amount)
        assertEquals("Petrol", expense.note)
        assertEquals(listOf(expense), store.expenses)
    }

    @Test
    fun `newest first, like every other list here`() {
        val store = store()
        store.addExpense(10.0, "Tea", Instant.parse("2026-08-01T12:00:00Z"))
        store.addExpense(20.0, "Petrol", Instant.parse("2026-08-02T12:00:00Z"))

        assertEquals(listOf("Petrol", "Tea"), store.expenses.map { it.note })
    }

    @Test
    fun `a blank note is refused`() {
        // An amount with nothing beside it is a number nobody can account for a
        // month later. Refused in the store rather than the sheet, so the rule
        // holds however the store is reached.
        val store = store()

        assertNull(store.addExpense(120.0, "   ", august))
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `nothing and less than nothing are refused`() {
        val store = store()

        assertNull(store.addExpense(0.0, "Petrol", august))
        assertNull(store.addExpense(-5.0, "Petrol", august))
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `the note is trimmed on the way in`() {
        val store = store()

        val expense = assertNotNull(store.addExpense(10.0, "  Petrol  ", august))

        assertEquals("Petrol", expense.note)
    }

    @Test
    fun `a correction replaces it in place`() {
        val store = store()
        val original = assertNotNull(store.addExpense(120.0, "Petrol", august))

        val corrected = assertNotNull(store.updateExpense(original.id, 140.0, "Petrol — Dammam", august))

        assertEquals(original.id, corrected.id)
        assertEquals(140.0, corrected.amount)
        assertEquals(1, store.expenses.size)
        assertEquals("Petrol — Dammam", store.expenses.single().note)
    }

    @Test
    fun `a correction cannot make it invalid`() {
        val store = store()
        val original = assertNotNull(store.addExpense(120.0, "Petrol", august))

        assertNull(store.updateExpense(original.id, 0.0, "Petrol", august))
        assertNull(store.updateExpense(original.id, 120.0, "", august))
        assertEquals(120.0, store.expenses.single().amount)
    }

    @Test
    fun `correcting something that is not there does nothing`() {
        val store = store()

        assertNull(store.updateExpense("no-such-id", 10.0, "Petrol", august))
        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `removing one leaves nothing behind`() {
        // Nothing to put back and nothing to recalculate, which is the dividend
        // of an expense being attached to nothing. Deleting a bill has to return
        // its stock and free its number.
        val store = store()
        val expense = assertNotNull(store.addExpense(120.0, "Petrol", august))

        store.deleteExpense(expense.id)

        assertTrue(store.expenses.isEmpty())
    }

    @Test
    fun `a total over a period counts only what falls inside it`() {
        val store = store()
        store.addExpense(100.0, "Petrol", Instant.parse("2026-08-02T12:00:00Z"))
        store.addExpense(40.0, "Tea", Instant.parse("2026-08-28T12:00:00Z"))
        store.addExpense(999.0, "Rent", Instant.parse("2026-07-15T12:00:00Z"))

        val august = StatementPeriod.Custom(
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T23:59:59Z")
        )

        assertEquals(140.0, store.spentIn(august))
    }

    // --- What an expense deliberately does not touch

    @Test
    fun `spending does not change what the shop sold`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)
        store.saveBill(listOf(com.stockbook.core.store.DraftLine(product.uid, 1, 95.0)), "Ahmed", 95.0, invoiceNo = "1")
        val soldBefore = store.soldIn(StatementPeriod.thisYear())

        store.addExpense(500.0, "Van tyre", august)

        assertEquals(soldBefore, store.soldIn(StatementPeriod.thisYear()))
    }

    @Test
    fun `spending does not change what anybody owes`() {
        val store = store()
        store.addCustomer("Ahmed Contracting", openingBalance = 300.0)
        store.addSupplier("Riyadh Steel", openingBalance = 200.0)

        store.addExpense(500.0, "Petrol", august)

        assertEquals(300.0, store.outstanding().second)
        assertEquals(200.0, store.payable().second)
    }

    @Test
    fun `spending never reaches a statement`() {
        // A statement is a document the owner may turn round and show a
        // customer, and the owner's petrol is not that customer's business.
        val store = store()
        store.addCustomer("Ahmed Contracting", openingBalance = 300.0)
        store.addExpense(500.0, "Petrol", august)

        val statement = assertNotNull(
            store.statementForCustomer("ahmed contracting", StatementPeriod.thisYear())
        )

        assertTrue(statement.entries.none { it.toString().contains("Petrol") })
        assertEquals(300.0, statement.closingBalance)
    }

    // --- Getting to a new phone

    @Test
    fun `spending survives export and import`() {
        // A backup field has four call sites — export and restore, on each
        // platform — and `paymentNo` once matched three of them, which would
        // have dropped every receipt number on the way to a new phone. This
        // covers the two on this side; the Swift suite covers its own.
        val store = store()
        store.addExpense(120.0, "Petrol", august)
        store.addExpense(40.0, "Tea for the shop", Instant.parse("2026-08-14T12:00:00Z"))

        val text = com.stockbook.core.transfer.BackupService.encode(store.makeBackupDocument())
        val fresh = store()
        fresh.replaceEverything(com.stockbook.core.transfer.BackupService.decode(text))

        assertEquals(
            listOf("Tea for the shop" to 40.0, "Petrol" to 120.0),
            fresh.expenses.map { it.note to it.amount }
        )
    }

    @Test
    fun `a file written before expenses existed still opens`() {
        // The reason this field did not bump the format version: absence has to
        // read as "no expenses", not as a broken file.
        val text = """{"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR"}"""

        val document = com.stockbook.core.transfer.BackupService.decode(text)

        assertTrue(document.expenses.isEmpty())
    }

    @Test
    fun `spending does not move the shelf`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)

        store.addExpense(500.0, "Van tyre", august)

        assertEquals(10, store.product(product.uid)?.stock)
    }

    // --- What the shop keeps buying

    /**
     * The suggestions under the "What was it for?" box.
     *
     * Nothing new is stored for these: they are the notes already on the
     * expenses. The tests below are about the ordering and the collapsing,
     * because those are the two things that decide whether the list is a
     * shortcut or a nuisance.
     */
    @Test
    fun `what gets bought most is offered first`() {
        val store = store()
        store.addExpense(60.0, "Petrol", at(1))
        store.addExpense(4.0, "Tea", at(2))
        store.addExpense(65.0, "Petrol", at(3))
        store.addExpense(20.0, "Rent", at(4))
        store.addExpense(70.0, "Petrol", at(5))
        store.addExpense(5.0, "Tea", at(6))

        // Petrol three times, tea twice, rent once. Rent is the most recent of
        // the three to be entered only once, and still comes last.
        assertEquals(listOf("Petrol", "Tea", "Rent"), store.expenseNotes())
    }

    @Test
    fun `a tie on how often goes to whichever was used last`() {
        val store = store()
        store.addExpense(4.0, "Tea", at(1))
        store.addExpense(60.0, "Petrol", at(2))

        assertEquals(listOf("Petrol", "Tea"), store.expenseNotes())
    }

    @Test
    fun `the same word in three casings is one suggestion`() {
        // Otherwise the list fills with the same thing spelled three ways inside
        // a month, which is the mess this feature exists to prevent.
        val store = store()
        store.addExpense(60.0, "petrol", at(1))
        store.addExpense(65.0, "PETROL", at(2))
        store.addExpense(70.0, "Petrol", at(3))

        // And the spelling offered is the newest: an owner who has started
        // writing "Petrol" should not be handed back the "petrol" they left
        // behind in March.
        assertEquals(listOf("Petrol"), store.expenseNotes())
    }

    @Test
    fun `typing filters on any part of the word`() {
        val store = store()
        store.addExpense(60.0, "Petrol", at(1))
        store.addExpense(4.0, "Tea", at(2))
        store.addExpense(30.0, "Shop rent", at(3))

        assertEquals(listOf("Petrol"), store.expenseNotes("pet"))
        assertEquals(listOf("Petrol"), store.expenseNotes("TROL"), "and it does not care about case")
        assertEquals(listOf("Shop rent"), store.expenseNotes("rent"), "or where in the word it falls")
        assertTrue(store.expenseNotes("zzz").isEmpty())
    }

    @Test
    fun `a shop that has spent nothing is offered nothing`() {
        assertTrue(store().expenseNotes().isEmpty())
    }

    @Test
    fun `only a handful is offered`() {
        // A shortcut for the few things a shop buys constantly, not a directory
        // of everything it has ever bought. That is the expenses list itself.
        val store = store()
        for (index in 1..12) store.addExpense(10.0, "Thing $index", at(index.toLong()))

        assertEquals(6, store.expenseNotes().size)
    }
}
