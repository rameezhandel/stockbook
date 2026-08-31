package com.stockbook.core

import com.stockbook.core.model.DayLedger
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every customer's position on one day.
 *
 * Two things are asserted over and over here. The first is the **roll-call**: a
 * customer nothing happened to still gets a line, because the page is read down
 * against a paper book and a list that skipped the quiet ones could not be. The
 * second is that **every row balances** — `opening + invoiced − received −
 * credited − transferredOut + transferredIn = closing` — which is the whole
 * claim the page makes and the one thing a reader cannot check for themselves.
 */
class DayLedgerTests {

    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")

    /** Midday, so a day's figures cannot slide either side of a boundary. */
    private fun at(day: Int): Instant =
        Instant.parse("2026-08-%02dT09:00:00Z".format(day))

    private fun store(): StockbookStore = StockbookStore(InMemoryRepository())

    private fun shop(): Pair<StockbookStore, String> {
        val store = store()
        val lock = store.addProduct("Cisa lock", 500, 60.0, 95.0)
        return store to lock.uid
    }

    private fun DayLedger.row(key: String): DayLedger.Row =
        assertNotNull(rows.firstOrNull { it.key == key }, "no line for $key")

    /** The claim the page makes, checked on every line of it. */
    private fun DayLedger.assertEveryRowBalances() {
        for (row in rows) {
            assertEquals(
                row.closingBalance,
                Math.round(
                    (row.openingBalance + row.invoiced - row.received -
                        row.credited + row.transferredIn - row.transferredOut) * 100
                ) / 100.0,
                "${row.name} does not add up"
            )
        }
    }

    @Test
    fun `every customer gets a line, including the ones nothing happened to`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.addCustomer("Khalid")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(12))

        val ledger = store.dayLedger(at(12), zone)

        assertEquals(3, ledger.rows.size, "the roll-call is the point of the page")
        assertEquals(listOf("Ahmed", "Fatima", "Khalid"), ledger.rows.map { it.name }, "in name order")
        assertEquals(1, ledger.busyRows.size)
        assertTrue(ledger.row("fatima").isQuiet)
        assertFalse(ledger.row("ahmed").isQuiet)
        ledger.assertEveryRowBalances()
    }

    @Test
    fun `a quiet customer carries yesterday's balance straight through`() {
        val (store, lock) = shop()
        store.addCustomer("Fatima")
        store.saveBill(listOf(DraftLine(lock, 4, 95.0)), "Fatima", paid = 0.0, createdAt = at(10))

        val ledger = store.dayLedger(at(12), zone)
        val fatima = ledger.row("fatima")

        assertTrue(fatima.isQuiet)
        assertEquals(0.0, fatima.invoiced)
        assertEquals(0.0, fatima.received)
        assertEquals(380.0, fatima.openingBalance, "owed from the tenth")
        assertEquals(380.0, fatima.closingBalance, "and still owed at the end of the twelfth")
    }

    /**
     * The row the whole form is for: billed and part-paid on the same day.
     *
     * The bill goes in one column at its full value and the money in the other,
     * which is what lets the two be read against each other. Netting them would
     * lose the fact that a sale happened at all.
     */
    @Test
    fun `a bill part paid at the counter fills both columns`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))

        val ahmed = store.dayLedger(at(12), zone).row("ahmed")

        assertEquals(950.0, ahmed.invoiced)
        assertEquals(400.0, ahmed.received)
        assertEquals(0.0, ahmed.openingBalance)
        assertEquals(550.0, ahmed.closingBalance)
    }

    @Test
    fun `a receipt against an older bill is money received today`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        store.recordPayment("ahmed", 300.0, receivedAt = at(12))

        val ahmed = store.dayLedger(at(12), zone).row("ahmed")

        assertEquals(0.0, ahmed.invoiced, "nothing was sold today")
        assertEquals(300.0, ahmed.received)
        assertEquals(950.0, ahmed.openingBalance)
        assertEquals(650.0, ahmed.closingBalance)
    }

    @Test
    fun `an opening balance from the paper book is owed on the first day`() {
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 1200.0)

        val ahmed = store.dayLedger(at(12), zone).row("ahmed")

        assertEquals(1200.0, ahmed.openingBalance)
        assertEquals(1200.0, ahmed.closingBalance)
        assertTrue(ahmed.isQuiet, "carried over is not something that happened today")
    }

    /**
     * Tomorrow's bill must not appear in today's opening figure.
     *
     * Working the opening balance backwards from what somebody owes *now* is the
     * shape that gets this wrong, and it gets it wrong silently: a customer
     * billed since would read as having owed that money all along.
     */
    @Test
    fun `a bill written later does not reach back into today`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        store.saveBill(listOf(DraftLine(lock, 8, 95.0)), "Ahmed", paid = 0.0, createdAt = at(20))

        val ahmed = store.dayLedger(at(12), zone).row("ahmed")

        assertEquals(190.0, ahmed.openingBalance, "only the tenth's bill")
        assertEquals(190.0, ahmed.closingBalance)
        assertEquals(0.0, ahmed.invoiced)
    }

    /**
     * A credit note is not a receipt, and the page says so in its own column.
     *
     * Folding it into "received" would be the easy way to keep five columns
     * adding up, and it would tell the owner money arrived that never did.
     */
    @Test
    fun `a credit note gets its own column and the row still balances`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        store.addCreditNote("ahmed", amount = 150.0, issuedAt = at(12))

        val ledger = store.dayLedger(at(12), zone)
        val ahmed = ledger.row("ahmed")

        assertTrue(ledger.hasCredits, "the column is drawn on a day that has one")
        assertEquals(0.0, ahmed.received, "no money arrived")
        assertEquals(150.0, ahmed.credited)
        assertEquals(950.0, ahmed.openingBalance)
        assertEquals(800.0, ahmed.closingBalance)
        ledger.assertEveryRowBalances()
    }

    @Test
    fun `a balance moved between two accounts shows on both lines`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed Jeddah")
        store.addCustomer("Ahmed Riyadh")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed Jeddah", paid = 0.0, createdAt = at(10))
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 950.0, movedAt = at(12))

        val ledger = store.dayLedger(at(12), zone)

        assertTrue(ledger.hasTransfers)
        val jeddah = ledger.row("ahmed jeddah")
        assertEquals(950.0, jeddah.transferredOut)
        assertEquals(950.0, jeddah.openingBalance)
        assertEquals(0.0, jeddah.closingBalance)

        val riyadh = ledger.row("ahmed riyadh")
        assertEquals(950.0, riyadh.transferredIn)
        assertEquals(0.0, riyadh.openingBalance)
        assertEquals(950.0, riyadh.closingBalance)

        // Nothing was created or destroyed by moving it.
        assertEquals(ledger.openingBalance, ledger.closingBalance)
        ledger.assertEveryRowBalances()
    }

    @Test
    fun `the columns that are usually empty are only announced when they are not`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed", paid = 95.0, createdAt = at(12))

        val ledger = store.dayLedger(at(12), zone)

        assertFalse(ledger.hasCredits)
        assertFalse(ledger.hasTransfers)
    }

    @Test
    fun `the totals are the columns added up`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed", openingBalance = 100.0)
        store.addCustomer("Fatima")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Fatima", paid = 0.0, createdAt = at(12))

        val ledger = store.dayLedger(at(12), zone)

        assertEquals(1140.0, ledger.invoiced, "950 and 190")
        assertEquals(400.0, ledger.received)
        assertEquals(100.0, ledger.openingBalance, "Ahmed's carried-over figure alone")
        assertEquals(840.0, ledger.closingBalance, "100 + 1140 − 400")
        ledger.assertEveryRowBalances()
    }

    /**
     * A name that only ever appeared on a bill is a customer too, and the page
     * that left them out would not tie to what the shop is owed.
     */
    @Test
    fun `somebody never added to the roster still gets a line`() {
        val (store, lock) = shop()
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Walk-in Sami", paid = 0.0, createdAt = at(12))

        val sami = store.dayLedger(at(12), zone).row("walk-in sami")

        assertEquals(95.0, sami.invoiced)
        assertEquals(95.0, sami.closingBalance)
    }

    /**
     * The bug the filter shipped with.
     *
     * The screen narrowed the rows and went on totalling all of them, so the foot
     * of a column showed a figure the column above it did not add up to. On a
     * page of money that is the one thing that must never happen: every figure on
     * it stops being trustworthy, including the ones that were right.
     */
    @Test
    fun `narrowing to what moved narrows the totals with it`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima", openingBalance = 2000.0)
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))

        val whole = store.dayLedger(at(12), zone)
        val moved = whole.movedOnly()

        assertEquals(2, whole.rows.size)
        assertEquals(1, moved.rows.size, "only Ahmed moved")

        // Invoiced and received are the same either way — a quiet row contributes
        // nothing to them. The balances are the ones that must not be.
        assertEquals(whole.invoiced, moved.invoiced)
        assertEquals(whole.received, moved.received)
        assertEquals(2000.0, whole.openingBalance, "Ahmed nil, Fatima carried over")
        assertEquals(0.0, moved.openingBalance, "Fatima is not on this page and her balance must not be either")
        assertEquals(2550.0, whole.closingBalance)
        assertEquals(550.0, moved.closingBalance)

        // And what it still is: the columns of the rows actually shown.
        assertEquals(moved.rows.sumOf { it.openingBalance }, moved.openingBalance)
        assertEquals(moved.rows.sumOf { it.closingBalance }, moved.closingBalance)
        moved.assertEveryRowBalances()
    }

    @Test
    fun `narrowing a day where nothing moved leaves an empty page, not a wrong one`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))

        val moved = store.dayLedger(at(12), zone).movedOnly()

        assertTrue(moved.isEmpty)
        assertEquals(0.0, moved.closingBalance, "no rows, no total")
    }

    @Test
    fun `a day with nothing on it is every customer, unchanged`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))

        val ledger = store.dayLedger(at(12).plus(5, ChronoUnit.DAYS), zone)

        assertEquals(1, ledger.rows.size)
        assertTrue(ledger.busyRows.isEmpty())
        assertEquals(0.0, ledger.invoiced)
        assertEquals(190.0, ledger.closingBalance, "still owed, just not today's doing")
    }
}
