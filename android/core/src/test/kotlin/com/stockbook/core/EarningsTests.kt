package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
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
 * What a stretch of trading left the shop with.
 *
 * Two things here are worth more than the arithmetic. **The discount needs no
 * apportioning** — `Bill.total` is stored after it, so a bill's takings less its
 * lines' cost is exactly what that bill earned, and the test below proves it to
 * the halala. And **a bill that cannot answer is set aside whole**, never
 * half-counted: subtracting part of a bill's cost from all of its takings would
 * flatter the figure by the difference, which is the one direction a page like
 * this must never be wrong in.
 */
class EarningsTests {

    private val zone = java.time.ZoneId.systemDefault()
    private fun store() = StockbookStore(InMemoryRepository())
    private fun thisMonth() = StatementPeriod.thisMonth()

    /**
     * Every bill **and credit note** in the book as an older file would have
     * restored it: itemised, but with no cost on any line.
     *
     * The notes matter as much as the bills. A return is costed the same way, so
     * a helper that stripped only one of the two would leave every
     * old-book test quietly half-modern.
     */
    private fun StockbookStore.stripCosts() {
        val document = makeBackupDocument()
        replaceEverything(
            document.copy(
                bills = document.bills.map { bill ->
                    bill.copy(lines = bill.lines.map { it.copy(cost = null) })
                },
                creditNotes = document.creditNotes.map { note ->
                    note.copy(lines = note.lines.map { it.copy(cost = null) })
                }
            )
        )
    }

    /** A product bought at [cost] and sold at [price]. */
    private fun StockbookStore.stocked(name: String, cost: Double, price: Double) =
        addProduct(name, stock = 100, cost = cost, price = price)

    @Test
    fun `what the goods earned is what they sold for less what they cost`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        val handle = store.stocked("Door handle", cost = 35.0, price = 50.0)
        store.saveBill(
            lines = listOf(
                DraftLine(padlock.uid, qty = 3, price = 30.0),
                DraftLine(handle.uid, qty = 2, price = 50.0)
            ),
            customer = "Ahmed",
            paid = null
        )

        val earnings = store.earningsIn(thisMonth())

        // 90 + 100 sold; 60 + 70 cost.
        assertEquals(190.0, earnings.sold)
        assertEquals(130.0, earnings.costOfGoods)
        assertEquals(60.0, earnings.goodsEarned)
    }

    @Test
    fun `a discount comes off the earnings without being apportioned`() {
        // The reason this needs no share-out across lines: `Bill.total` is
        // already stored net of the discount, so takings less cost is exact.
        // Apportioning would reach the same answer by a longer route and pick up
        // a rounding error on the way.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(
            lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)),
            customer = "Ahmed",
            paid = null,
            discountPercent = 10.0
        )

        val earnings = store.earningsIn(thisMonth())

        // 90 less 10% is 81 charged; the goods still cost 60.
        assertEquals(81.0, earnings.sold)
        assertEquals(60.0, earnings.costOfGoods)
        assertEquals(21.0, earnings.goodsEarned)
    }

    @Test
    fun `a bill entered as a figure is set aside, and the page says how much`() {
        // The ordinary way to enter a paper bill, and the whole reason this page
        // carries a confession.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        val earnings = store.earningsIn(thisMonth())

        assertEquals(590.0, earnings.sold)
        assertEquals(500.0, earnings.soldWithoutCost)
        assertEquals(1, earnings.billsWithoutCost)
        // Only the itemised bill is answered for.
        assertEquals(90.0, earnings.counted)
        assertEquals(60.0, earnings.costOfGoods)
        assertEquals(30.0, earnings.goodsEarned)
        assertTrue(earnings.hasGap)
    }

    @Test
    fun `a bill part recorded and part estimated uses each line's best figure`() {
        // Not "half counted" — every line gets a figure, the recorded one where
        // there is one and the shelf's where there is not. The bill only falls
        // out when a line has neither, which is what the deleted-product test
        // below covers.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        val handle = store.stocked("Door handle", cost = 35.0, price = 50.0)
        val bill = assertNotNull(
            store.saveBill(
                lines = listOf(
                    DraftLine(padlock.uid, qty = 3, price = 30.0),
                    DraftLine(handle.uid, qty = 2, price = 50.0)
                ),
                customer = "Ahmed",
                paid = null
            )
        )
        // One line as an older file would have left it.
        val document = store.makeBackupDocument()
        store.replaceEverything(
            document.copy(
                bills = document.bills.map { row ->
                    row.copy(lines = row.lines.mapIndexed { index, line ->
                        if (index == 0) line.copy(cost = null) else line
                    })
                }
            )
        )

        val earnings = store.earningsIn(thisMonth())

        assertEquals(bill.total, earnings.counted)
        // 3 padlocks estimated at 20, 2 handles recorded at 35.
        assertEquals(130.0, earnings.costOfGoods)
        assertTrue(earnings.hasEstimates)
        assertEquals(1, earnings.billsEstimated)
    }

    @Test
    fun `what the shop kept is what the goods earned less what it spent`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(45.0, "Petrol")

        val earnings = store.earningsIn(thisMonth())

        assertEquals(100.0, earnings.goodsEarned)
        assertEquals(45.0, earnings.expenses)
        assertEquals(55.0, earnings.kept)
    }

    @Test
    fun `a month that spent more than it earned is negative, and says so`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 2, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(400.0, "Rent")

        // 20 earned, 400 spent. The page has to be able to say a month lost
        // money — the alternative is a figure the owner cannot act on.
        assertEquals(-380.0, store.earningsIn(thisMonth()).kept)
    }

    @Test
    fun `stock bought and not sold is not a cost yet`() {
        // A hundred padlocks in and three out is not a loss. This is why the
        // figure is the cost of what was *sold*, and why `boughtIn` has no part
        // in it.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordPurchase(
            product = assertNotNull(store.product(padlock.uid)),
            supplierKey = supplier.key,
            quantity = 100,
            unitCost = 20.0
        )
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        val earnings = store.earningsIn(thisMonth())

        assertEquals(2000.0, store.boughtIn(thisMonth()))
        // Three sold, so three costed.
        assertEquals(60.0, earnings.costOfGoods)
        assertEquals(30.0, earnings.goodsEarned)
    }

    @Test
    fun `a price rise after the sale does not move what the month earned`() {
        // The field this page was waiting for, seen from up here.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        val before = store.earningsIn(thisMonth()).goodsEarned
        store.update(assertNotNull(store.product(padlock.uid)), name = "Padlock 40mm", cost = 26.0, price = 30.0)

        assertEquals(before, store.earningsIn(thisMonth()).goodsEarned)
        assertEquals(30.0, before)
    }

    @Test
    fun `a credit note written as a figure comes off in full`() {
        // Nothing was handed back, so nothing goes back on the shelf and the
        // whole credit is a real loss. Not an approximation — it is why a
        // figure-only note needs no special case and nothing disclosed.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(com.stockbook.core.model.Customer.key("Ahmed"), amount = 30.0)

        val earnings = store.earningsIn(thisMonth())

        // Sold still counts bills and not notes, so it cannot disagree with Home.
        assertEquals(90.0, earnings.sold)
        assertEquals(0.0, earnings.goodsReturned)
        assertEquals(60.0, earnings.costOfGoods)
        assertEquals(30.0, earnings.goodsEarned, "90 sold less 60 cost")
        assertEquals(30.0, earnings.credited)
        assertEquals(1, earnings.creditNotes)
        // 30 earned on the goods, 30 given straight back.
        assertEquals(0.0, earnings.kept)
        // Nothing to confess: it is taken off the page rather than listed beside it.
        assertFalse(earnings.hasGap)
        assertEquals(0, earnings.creditNotesBeforeCosts)
    }

    @Test
    fun `a credit note with goods on it puts their cost back`() {
        // The pair of the test above, and the reason the two cannot share one
        // rule blindly: 30 credited on goods that cost the shop 20 leaves it 10
        // worse off, not 30. Those pieces are on the shelf again.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(
            com.stockbook.core.model.Customer.key("Ahmed"),
            lines = listOf(DraftLine(padlock.uid, qty = 1, price = 30.0))
        )

        val earnings = store.earningsIn(thisMonth())

        assertEquals(90.0, earnings.sold, "a return does not unsell the bill")
        assertEquals(60.0, earnings.costOfGoods)
        assertEquals(20.0, earnings.goodsReturned, "one padlock back, at what it cost")
        assertEquals(40.0, earnings.netCostOfGoods)
        assertEquals(50.0, earnings.goodsEarned)
        assertEquals(30.0, earnings.credited)
        // 50 earned, 30 credited: 20 kept, which is the 30 profit on the two
        // still sold less the 10 the returned one was marked up by.
        assertEquals(20.0, earnings.kept)
        assertFalse(earnings.hasGap)
    }

    @Test
    fun `a return whose product is gone is put back at nothing and said to be`() {
        // The one case that leaves the figure low. Better low than high, and
        // better said than either.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(
            com.stockbook.core.model.Customer.key("Ahmed"),
            lines = listOf(DraftLine(padlock.uid, qty = 1, price = 30.0))
        )
        store.stripCosts()
        store.delete(assertNotNull(store.product(padlock.uid)))

        val earnings = store.earningsIn(thisMonth())

        assertEquals(0.0, earnings.goodsReturned)
        assertEquals(1, earnings.creditNotesBeforeCosts)
        assertTrue(earnings.hasGap)
    }

    @Test
    fun `an old return is valued at today's price, and counted as an estimate`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(
            com.stockbook.core.model.Customer.key("Ahmed"),
            lines = listOf(DraftLine(padlock.uid, qty = 1, price = 30.0))
        )
        store.stripCosts()

        val earnings = store.earningsIn(thisMonth())

        assertEquals(20.0, earnings.goodsReturned, "the shelf still knows")
        assertEquals(1, earnings.creditNotesEstimated)
        assertEquals(0, earnings.creditNotesBeforeCosts)
        assertTrue(earnings.hasEstimates)
    }

    @Test
    fun `sold matches what Home shows, so the two screens cannot disagree`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        assertEquals(store.soldIn(thisMonth()), store.earningsIn(thisMonth()).sold)
    }

    @Test
    fun `another month's trading stays in another month`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(
            lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = Instant.now().atZone(zone).toLocalDate().minusMonths(2).atTime(12, 0).atZone(zone).toInstant()
        )

        val earnings = store.earningsIn(thisMonth())

        assertEquals(0.0, earnings.sold)
        assertTrue(earnings.isEmpty)
        assertFalse(earnings.hasGap)
    }

    @Test
    fun `a book written before costs existed is costed at today's prices`() {
        // Raised by the owner on real data: every bill predated the cost field,
        // so the page could answer nothing. It now falls back to what the
        // product costs today — near enough on a shelf whose prices have not
        // moved, and the page says which figures rest on it.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(45.0, "Rent")
        store.stripCosts()

        val earnings = store.earningsIn(thisMonth())

        assertFalse(earnings.nothingCostable)
        assertEquals(300.0, earnings.counted)
        assertEquals(200.0, earnings.costOfGoods)
        assertEquals(100.0, earnings.goodsEarned)
        assertEquals(55.0, earnings.kept)
        // Counted, and counted out loud.
        assertTrue(earnings.hasEstimates)
        assertEquals(1, earnings.billsEstimated)
        assertEquals(300.0, earnings.soldEstimated)
    }

    @Test
    fun `nothing is written back, so the estimate follows the shelf`() {
        // The bill's own cost stays absent, because absent is the truth about
        // it. The consequence is deliberate: reprice the product and the
        // estimate moves, which is exactly what an estimate should do and
        // exactly what a recorded cost must never do.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.stripCosts()

        assertEquals(100.0, store.earningsIn(thisMonth()).goodsEarned)
        store.update(assertNotNull(store.product(padlock.uid)), name = "Padlock 40mm", cost = 25.0, price = 30.0)

        assertEquals(50.0, store.earningsIn(thisMonth()).goodsEarned)
        // And the line itself still knows nothing, which is what keeps a real
        // recorded cost from ever being overwritten by a guess.
        assertNull(store.bills.first().lines.first().cost)
    }

    @Test
    fun `a recorded cost always wins over today's price`() {
        // The whole point of the field. An estimate is only ever a fallback, and
        // a bill that carries its own figure must be immune to the shelf.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.update(assertNotNull(store.product(padlock.uid)), name = "Padlock 40mm", cost = 25.0, price = 30.0)

        val earnings = store.earningsIn(thisMonth())

        assertEquals(200.0, earnings.costOfGoods)
        assertFalse(earnings.hasEstimates)
    }

    @Test
    fun `a line whose product is gone cannot be estimated either`() {
        // The shelf is the only source left for an old bill, so a deleted
        // product takes the last figure with it. That bill goes back to being
        // set aside — and the whole bill, never half of it.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.stripCosts()
        store.delete(assertNotNull(store.product(padlock.uid)))

        val earnings = store.earningsIn(thisMonth())

        assertTrue(earnings.nothingCostable)
        assertEquals(1, earnings.billsBeforeCosts)
        assertEquals(0, earnings.billsEstimated)
    }

    @Test
    fun `a bill entered as a total is still beyond estimating`() {
        // No lines, so nothing to look up. This one never becomes countable,
        // which is why it stays named apart from the rest.
        val store = store()
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        val earnings = store.earningsIn(thisMonth())

        assertTrue(earnings.nothingCostable)
        assertEquals(1, earnings.billsAsTotal)
        assertEquals(0, earnings.billsEstimated)
    }

    @Test
    fun `a quiet period is empty rather than zero-shaped`() {
        assertTrue(store().earningsIn(thisMonth()).isEmpty)
    }
}
