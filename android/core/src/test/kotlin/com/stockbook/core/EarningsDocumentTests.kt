package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.EarningsDocument
import com.stockbook.core.text.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The earnings page as it reads.
 *
 * `EarningsTests` pins the arithmetic; this pins the confession. **The page must
 * never be able to answer for part of a month while looking like it answered for
 * all of it**, so the two tests that matter are the one where the gap is shown
 * and the one where — there being nothing to admit — the chain shortens instead
 * of carrying two lines of zeroes.
 */
class EarningsDocumentTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private fun store() = StockbookStore(InMemoryRepository()).also { it.setOwnerName("Al Salam Hardware") }
    private fun period() = StatementPeriod.thisMonth()

    private fun StockbookStore.page(): EarningsDocument =
        EarningsDocument.make(earningsIn(period()), period().range(), settings, strings)

    private fun StockbookStore.stocked(name: String, cost: Double, price: Double) =
        addProduct(name, stock = 100, cost = cost, price = price)

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

    @Test
    fun `a shop that itemises everything reads sold, cost, earned, spent, kept`() {
        // The short chain: nothing to confess, so no "Not counted" pair and no
        // second total on the way down.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(45.0, "Petrol")

        val page = store.page()

        assertEquals(
            listOf("Sold", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"),
            page.lines.map { it.label }
        )
        assertEquals(
            listOf("SAR 300", "SAR 200", "SAR 100", "SAR 45", "SAR 55"),
            page.lines.map { it.value }
        )
        assertFalse(page.hasGap)
        assertNull(page.gapNote)
    }

    @Test
    fun `takings the page cannot answer for are taken off in front of the reader`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        val page = store.page()

        // Sold still matches Home. The subtraction happens on the page rather
        // than behind it.
        assertEquals(
            listOf("Sold", "Not counted", "Counted", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"),
            page.lines.map { it.label }
        )
        assertEquals("SAR 800", page.lines.first().value)
        assertEquals("SAR 500", page.lines[1].value)
        assertEquals("SAR 300", page.lines[2].value)

        assertTrue(page.hasGap)
        assertEquals(listOf("1 bill entered as a total"), page.gap.map { it.label })
        assertEquals(listOf("SAR 500"), page.gap.map { it.value })
    }

    @Test
    fun `the subtractions and the totals are marked apart`() {
        // What the renderer draws differently: a figure being taken away, and a
        // figure the lines above it add up to.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)

        val weights = store.page().lines.map { it.weight }

        assertEquals(
            listOf(
                EarningsDocument.Weight.PLAIN,
                EarningsDocument.Weight.MINUS,
                EarningsDocument.Weight.TOTAL,
                EarningsDocument.Weight.MINUS,
                EarningsDocument.Weight.TOTAL
            ),
            weights
        )
    }

    @Test
    fun `a month that lost money says so with a sign`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 2, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(400.0, "Rent")

        assertEquals("-SAR 380", store.page().lines.last().value)
    }

    @Test
    fun `credit notes are listed under the gap with a line saying why`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = 0.0)
        store.addCreditNote(Customer.key("Ahmed"), amount = 60.0)

        val page = store.page()

        assertEquals(listOf("1 credit note issued"), page.gap.map { it.label })
        assertEquals("Credit notes are not taken off the figures above.", page.gapNote)
        // And they really are not: the chain is untouched by the note.
        assertEquals("SAR 100", page.lines.first { it.label == "What the goods earned" }.value)
    }

    @Test
    fun `the page says whose it is, what it is, and which days`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 1, price = 30.0)), customer = "Ahmed", paid = null)

        val page = store.page()

        assertEquals("Al Salam Hardware", page.shopName)
        assertEquals("Earnings Summary", page.title)
        // Never the word that means one party's account.
        assertFalse(page.title.contains("Statement", ignoreCase = true))
        // The last day inside the range, not the midnight after it.
        assertEquals(
            strings.dateSpan(
                strings.longDate(period().range().start),
                strings.longDate(period().range().end.minusSeconds(1))
            ),
            page.onDate
        )
    }

    @Test
    fun `an old bill is costed at today's prices and the page says so`() {
        // The bug an owner found on real data: the page ran Sold → 0 → 0 and
        // then took the month's expenses off nothing, printing "kept -1,150" as
        // though the shop had lost its rent. The shelf still knows what a
        // padlock costs, so the page answers with that and names the guess
        // rather than refusing.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(1150.0, "Rent")
        store.stripCosts()

        val page = store.page()

        // The full chain, with nothing set aside: the estimate is counted.
        assertEquals(
            listOf("Sold", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"),
            page.lines.map { it.label }
        )
        assertEquals(
            listOf("SAR 300", "SAR 200", "SAR 100", "SAR 1,150", "-SAR 1,050"),
            page.lines.map { it.value }
        )
        // Counted, but the owner is told which part of it was guessed.
        assertEquals(listOf("1 bill costed at today's prices"), page.gap.map { it.label })
        assertEquals(
            "Some costs are estimated from today's buying prices, because those bills were written " +
                "before the app recorded them.",
            page.gapNote
        )
    }

    @Test
    fun `a book that cannot even be estimated stops the chain and explains itself`() {
        // The shelf is the last source of a figure for an old bill, so a product
        // deleted since takes it away for good. That bill is set aside whole and
        // the chain stops — an absence is not a loss.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(1150.0, "Rent")
        store.stripCosts()
        store.delete(requireNotNull(store.product(padlock.uid)))

        val page = store.page()

        // What was sold, what could not be costed, and then it stops.
        assertEquals(listOf("Sold", "Not counted", "Counted"), page.lines.map { it.label })
        // The reason names the right cause: these bills were itemised.
        assertEquals(listOf("1 bill written before costs were recorded"), page.gap.map { it.label })
        assertEquals(
            "No earnings figure yet — these bills were written before the app recorded what goods cost. " +
                "Bills from now on will count.",
            page.gapNote
        )
    }

    @Test
    fun `the three reasons a bill misses its cost are named apart`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        val hinge = store.stocked("Hinge 4in", cost = 5.0, price = 8.0)
        // One written before costs were kept whose product has since gone, and
        // one written before costs were kept that the shelf can still answer for.
        store.saveBill(lines = listOf(DraftLine(hinge.uid, qty = 10, price = 8.0)), customer = "Ahmed", paid = null)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Saeed", paid = null)
        store.stripCosts()
        store.delete(requireNotNull(store.product(hinge.uid)))
        // And one that was never itemised at all.
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)

        val page = store.page()

        assertEquals(
            listOf(
                "1 bill entered as a total",
                "1 bill written before costs were recorded",
                "1 bill costed at today's prices"
            ),
            page.gap.map { it.label }
        )
        assertEquals(listOf("SAR 500", "SAR 80", "SAR 300"), page.gap.map { it.value })
        // One bill can be costed, so the chain runs its full length — and the
        // caveat that reaches the reader is the one about the figure it printed.
        assertEquals("What the shop kept", page.lines.last().label)
        assertTrue(page.gapNote!!.startsWith("Some costs are estimated"))
    }

    @Test
    fun `a quiet period states that and draws no chain of zeroes`() {
        val page = store().page()

        assertTrue(page.isEmpty)
        assertFalse(page.hasGap)
        assertEquals("Nothing sold in this period.", page.emptyLine)
    }
}
