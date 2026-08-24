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
    fun `a book written before costs existed stops the chain and explains itself`() {
        // The bug an owner found on real data: the page ran Sold → 0 → 0 and
        // then took the month's expenses off nothing, printing "kept -1,150" as
        // though the shop had lost its rent. An absence is not a loss.
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        store.addExpense(1150.0, "Rent")
        val document = store.makeBackupDocument()
        store.replaceEverything(
            document.copy(
                bills = document.bills.map { bill ->
                    bill.copy(lines = bill.lines.map { it.copy(cost = null) })
                }
            )
        )

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
    fun `a bill entered as a total and one written too early are named apart`() {
        val store = store()
        val padlock = store.stocked("Padlock 40mm", cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 10, price = 30.0)), customer = "Ahmed", paid = null)
        val document = store.makeBackupDocument()
        store.replaceEverything(
            document.copy(
                bills = document.bills.map { bill ->
                    bill.copy(lines = bill.lines.map { it.copy(cost = null) })
                }
            )
        )
        store.saveBill(customer = "Khalid", paid = null, amount = 500.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 1, price = 30.0)), customer = "Saeed", paid = null)

        val page = store.page()

        assertEquals(
            listOf("1 bill entered as a total", "1 bill written before costs were recorded"),
            page.gap.map { it.label }
        )
        // One costable bill, so the chain runs its full length.
        assertEquals("What the shop kept", page.lines.last().label)
    }

    @Test
    fun `a quiet period states that and draws no chain of zeroes`() {
        val page = store().page()

        assertTrue(page.isEmpty)
        assertFalse(page.hasGap)
        assertEquals("Nothing sold in this period.", page.emptyLine)
    }
}
