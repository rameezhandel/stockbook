package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.money.Money
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.SummaryDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The owner's own list of who owes them.
 *
 * One thing here matters more than the rest: **the foot of the page must equal
 * the figure on Home.** The owner reads Receivable on the way past and prints
 * this to chase people; two numbers that disagree by a halala turn the whole
 * page into something to be checked by hand, which is the one thing it exists to
 * save.
 */
class SummaryDocumentTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val day: Instant = Instant.parse("2026-08-22T09:00:00Z")

    private fun store() = StockbookStore(InMemoryRepository())

    /** A customer owing exactly [amount], by way of an unpaid bill. */
    private fun StockbookStore.owing(name: String, amount: Double, invoiceNo: String) {
        saveBill(customer = name, paid = 0.0, amount = amount, invoiceNo = invoiceNo)
    }

    private fun StockbookStore.document() =
        SummaryDocument.forReceivable(customers(), settings, strings, now = day)

    @Test
    fun `the total is the figure Home shows`() {
        val store = store()
        store.owing("Ahmed Contracting", 450.0, "1")
        store.owing("Khalid Al-Amri", 1_200.5, "2")

        val document = store.document()

        assertEquals(Money.text(store.outstanding().second, store.settings.currency), document.totalValue)
        // Halalas only where there are any: `Money.text` drops a trailing
        // ".00", which is why the figures below are bare.
        assertEquals("SAR 1,650.50", document.totalValue)
    }

    @Test
    fun `one row each, biggest first`() {
        // The order `customers()` already returns, which is the order the list on
        // screen shows. Sorting again in the document would be a second opinion
        // about which of the two is right.
        val store = store()
        store.owing("Ahmed Contracting", 450.0, "1")
        store.owing("Khalid Al-Amri", 1_200.0, "2")
        store.owing("Saeed Stores", 80.0, "3")

        val rows = store.document().rows

        assertEquals(listOf("Khalid Al-Amri", "Ahmed Contracting", "Saeed Stores"), rows.map { it.name })
        assertEquals("SAR 1,200", rows.first().amount)
    }

    @Test
    fun `somebody who owes nothing is not on it`() {
        val store = store()
        store.owing("Ahmed Contracting", 450.0, "1")
        // Paid on the spot: a customer, and not a debtor.
        store.saveBill(customer = "Cash Sale", paid = null, amount = 200.0, invoiceNo = "2")

        val rows = store.document().rows

        assertEquals(listOf("Ahmed Contracting"), rows.map { it.name })
    }

    @Test
    fun `somebody in advance is not a debtor either`() {
        // A negative row on a chasing list is a line the owner has to stop and
        // think about every time they read it — and it would quietly reduce the
        // total below what is actually out there to collect.
        val store = store()
        store.owing("Ahmed Contracting", 450.0, "1")
        store.addCustomer("Paid Ahead", openingBalance = -300.0)

        val document = store.document()

        assertEquals(listOf("Ahmed Contracting"), document.rows.map { it.name })
        assertEquals("SAR 450", document.totalValue)
    }

    @Test
    fun `a shop nobody owes says so rather than printing an empty table`() {
        val store = store()
        store.saveBill(customer = "Cash Sale", paid = null, amount = 200.0, invoiceNo = "1")

        val document = store.document()

        assertTrue(document.isEmpty)
        assertTrue(document.rows.isEmpty())
        assertEquals("Nothing receivable.", document.emptyLine)
        assertEquals("SAR 0", document.totalValue)
    }

    @Test
    fun `the page says which day it is true for`() {
        // What is outstanding is true at a moment. Without this line a printout
        // from last month reads exactly like this morning's.
        val document = store().document()

        assertEquals("As of 22 August 2026", document.asOf)
    }

    @Test
    fun `it is titled as the owner's own list, never as a statement`() {
        // The rule this document exists under: it names everybody, so it is the
        // one page in the app that must never be turned round on the counter.
        val document = store().document()

        assertEquals("Receivable Amount Summary", document.title)
        assertTrue(!document.title.contains("Statement", ignoreCase = true))
    }

    @Test
    fun `it says receivable, the word Home says`() {
        // The same money called two things on two screens is the owner wondering
        // whether they are the same money. Home's card is the one that was named
        // first, so the page follows it rather than the other way round.
        val document = store().document()

        assertEquals(strings.receivableStat, "Receivable")
        assertTrue(document.title.contains(strings.receivableStat))
        assertEquals(listOf("Customer", strings.receivableStat), document.columnHeadings)
        assertEquals("Total Receivable", document.totalLabel)

        for (text in listOf(document.title, document.totalLabel, document.emptyLine)) {
            assertTrue(!text.contains("owe", ignoreCase = true), text)
        }
    }

    // --- The same page pointing the other way

    private fun StockbookStore.payableDocument() =
        SummaryDocument.forPayable(suppliers(), settings, strings, now = day)

    @Test
    fun `the payable page totals what the shop owes`() {
        val store = store()
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))
        store.recordSupplierBill(supplier.key, amount = 800.0, paid = 0.0, invoiceNo = "INV-1")

        val document = store.payableDocument()

        assertEquals(Money.text(store.payable().second, store.settings.currency), document.totalValue)
        assertEquals("SAR 800", document.totalValue)
        assertEquals(listOf("Al-Riyadh Hardware"), document.rows.map { it.name })
    }

    @Test
    fun `every word on the payable page points the other way`() {
        // A payable list headed "Receivable" would be the most expensive kind of
        // wrong: one the owner acts on.
        val document = store().payableDocument()

        assertEquals("Payable Amount Summary", document.title)
        assertEquals(listOf(strings.supplier, strings.payableStat), document.columnHeadings)
        assertEquals("Total Payable", document.totalLabel)
        assertEquals("Nothing payable.", document.emptyLine)

        for (text in listOf(document.title, document.totalLabel, document.emptyLine)) {
            assertTrue(!text.contains("receivable", ignoreCase = true), text)
        }
    }

    @Test
    fun `the two pages never share a word that names a direction`() {
        // One body builds both, so the only thing keeping them apart is which
        // strings each is handed. This is what notices if that ever slips.
        val store = store()
        val receivable = store.document()
        val payable = store.payableDocument()

        assertTrue(receivable.title != payable.title)
        assertTrue(receivable.totalLabel != payable.totalLabel)
        assertTrue(receivable.emptyLine != payable.emptyLine)
        assertTrue(receivable.columnHeadings != payable.columnHeadings)
    }

    @Test
    fun `the shop's own name heads it`() {
        val store = store()
        store.setOwnerName("Al Salam Hardware")

        assertEquals("Al Salam Hardware", store.document().shopName)
    }

    // --- Where the shop's own money went

    private fun StockbookStore.spendingDocument(period: StatementPeriod) =
        SummaryDocument.forSpending(spendingIn(period), period.range(), settings, strings)

    @Test
    fun `spending is grouped by what it went on, biggest first`() {
        // Forty-seven lines is a record. Three lines is an answer.
        val store = store()
        store.addExpense(60.0, "Petrol", day)
        store.addExpense(4.0, "Tea", day)
        store.addExpense(2_000.0, "Rent", day)
        store.addExpense(65.0, "Petrol", day)

        val document = store.spendingDocument(StatementPeriod.thisYear())

        assertEquals(listOf("Rent", "Petrol", "Tea"), document.rows.map { it.name })
        assertEquals(listOf("SAR 2,000", "SAR 125", "SAR 4"), document.rows.map { it.amount })
        assertEquals("SAR 2,129", document.totalValue)
    }

    @Test
    fun `how often sits beside what it came to`() {
        // "Petrol, 12 times, 780" is a different fact from "petrol 780", and it
        // is the one that says whether to look at the price or the habit.
        val store = store()
        store.addExpense(60.0, "Petrol", day)
        store.addExpense(65.0, "Petrol", day)
        store.addExpense(2_000.0, "Rent", day)

        val rows = store.spendingDocument(StatementPeriod.thisYear()).rows

        assertEquals("2 times", rows.first { it.name == "Petrol" }.detail)
        assertEquals("once", rows.first { it.name == "Rent" }.detail, "never \"1 times\"")
    }

    @Test
    fun `the same word in three casings is one line`() {
        // Worth more here than in the suggestion list: three lines for one thing
        // does not merely look untidy, it hides how much the shop spends on it.
        val store = store()
        store.addExpense(60.0, "petrol", day)
        store.addExpense(65.0, "PETROL", day.plusSeconds(60))
        store.addExpense(70.0, "Petrol", day.plusSeconds(120))

        val rows = store.spendingDocument(StatementPeriod.thisYear()).rows

        assertEquals(1, rows.size)
        assertEquals("Petrol", rows.single().name, "and the newest spelling is the one shown")
        assertEquals("SAR 195", rows.single().amount)
        assertEquals("3 times", rows.single().detail)
    }

    @Test
    fun `only what was spent inside the period`() {
        val store = store()
        store.addExpense(60.0, "Petrol", Instant.parse("2026-08-22T09:00:00Z"))
        store.addExpense(999.0, "Long ago", Instant.parse("2024-01-05T09:00:00Z"))

        val document = store.spendingDocument(StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))

        assertEquals(listOf("Petrol"), document.rows.map { it.name })
        assertEquals("SAR 60", document.totalValue)
    }

    @Test
    fun `the total is what the card on the pane shows`() {
        // The owner reads Expense on the pane and prints this from beside it.
        // Two figures a halala apart turn the page into something to re-check.
        val store = store()
        store.addExpense(60.0, "Petrol", day)
        store.addExpense(4.5, "Tea", day)
        val period = StatementPeriod.thisYear()

        assertEquals(
            Money.text(store.spentIn(period), store.settings.currency),
            store.spendingDocument(period).totalValue
        )
    }

    @Test
    fun `a period with nothing in it says so`() {
        val document = store().spendingDocument(StatementPeriod.thisYear())

        assertTrue(document.isEmpty)
        assertEquals("Nothing spent in this period.", document.emptyLine)
        assertEquals("SAR 0", document.totalValue)
    }

    @Test
    fun `the page is headed by the days it covers, and never called a statement`() {
        // An expense is joined to no party, so "statement" — which in this app
        // means one party's account — would be the wrong word for it.
        val store = store()
        store.addExpense(60.0, "Petrol", Instant.parse("2026-08-22T09:00:00Z"))

        val document = store.spendingDocument(StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))

        assertEquals("Expense Summary", document.title)
        assertTrue(!document.title.contains("Statement", ignoreCase = true))
        // The last day *inside* August, not midnight on the 1st of September.
        assertEquals("1 August 2026 to 31 August 2026", document.asOf)
    }
}
