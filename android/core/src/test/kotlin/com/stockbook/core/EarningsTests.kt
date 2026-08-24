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
     * Every bill in the book as an older file would have restored it: itemised,
     * but with no cost on any line.
     */
    private fun StockbookStore.stripCosts() {
        val document = makeBackupDocument()
        replaceEverything(
            document.copy(
                bills = document.bills.map { bill ->
                    bill.copy(lines = bill.lines.map { it.copy(cost = null) })
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
    fun `a bill with one costless line is set aside whole, not half counted`() {
        // The flattering bug this prevents: taking one line's cost off the whole
        // bill's takings. A bill from an older book can look like this, and the
        // wrong answer here is always too high.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        val bill = assertNotNull(
            store.saveBill(
                lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)),
                customer = "Ahmed",
                paid = null
            )
        )
        // A line as an older file would have restored it.
        store.replaceEverything(
            store.makeBackupDocument().let { document ->
                document.copy(
                    bills = document.bills.map { row ->
                        row.copy(lines = row.lines.map { it.copy(cost = null) })
                    }
                )
            }
        )

        val earnings = store.earningsIn(thisMonth())

        assertEquals(bill.total, earnings.sold)
        assertEquals(bill.total, earnings.soldWithoutCost)
        assertEquals(0.0, earnings.counted)
        assertEquals(0.0, earnings.goodsEarned)
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
    fun `credit notes are disclosed and never subtracted`() {
        // `soldIn` counts bills and not notes, so netting them here would put two
        // answers to "what did we sell" on two screens. The owner is told
        // instead.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(com.stockbook.core.model.Customer.key("Ahmed"), amount = 30.0)

        val earnings = store.earningsIn(thisMonth())

        assertEquals(90.0, earnings.sold)
        assertEquals(30.0, earnings.goodsEarned)
        assertEquals(30.0, earnings.credited)
        assertEquals(1, earnings.creditNotes)
        assertTrue(earnings.hasGap)
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
    fun `a book written before costs existed reports an absence, not a loss`() {
        // Found by the owner on real data the day this shipped. Every bill
        // predates the cost field, so nothing can be costed — and the page was
        // running the chain anyway: earnings of zero, then the month's expenses
        // subtracted from it, landing on "kept -1,150" as though the shop had
        // lost its rent. It had not. The page simply could not say.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(1150.0, "Rent")
        store.stripCosts()

        val earnings = store.earningsIn(thisMonth())

        assertTrue(earnings.nothingCostable)
        assertEquals(300.0, earnings.sold)
        assertEquals(0.0, earnings.counted)
        // The figures are still computable; the page is what must not print
        // them. `EarningsDocumentTests` holds that end.
        assertEquals(300.0, earnings.soldBeforeCosts)
        assertEquals(1, earnings.billsBeforeCosts)
        assertEquals(0, earnings.billsAsTotal)
    }

    @Test
    fun `the two reasons a bill cannot be costed are counted apart`() {
        // One asks the owner to itemise the next bill; the other asks nothing of
        // them at all and fixes itself as the shop trades. Telling somebody to
        // itemise a bill they already itemised is how an app earns a reputation.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.stripCosts()
        // And a fresh one entered as a figure, which will never be costable.
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        val earnings = store.earningsIn(thisMonth())

        assertEquals(1, earnings.billsBeforeCosts)
        assertEquals(300.0, earnings.soldBeforeCosts)
        assertEquals(1, earnings.billsAsTotal)
        assertEquals(500.0, earnings.soldAsTotal)
        assertEquals(2, earnings.billsWithoutCost)
        assertEquals(800.0, earnings.soldWithoutCost)
    }

    @Test
    fun `one costable bill is enough to answer, alongside older ones`() {
        // The shape a shop is in a week after this arrives: the old book cannot
        // be costed, the new bills can, and the page answers for what it can.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.stripCosts()
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 5, price = 30.0)), customer = "Khalid", paid = null)

        val earnings = store.earningsIn(thisMonth())

        assertFalse(earnings.nothingCostable)
        assertEquals(150.0, earnings.counted)
        assertEquals(100.0, earnings.costOfGoods)
        assertEquals(50.0, earnings.goodsEarned)
    }

    @Test
    fun `a quiet period is empty rather than zero-shaped`() {
        assertTrue(store().earningsIn(thisMonth()).isEmpty)
    }
}
