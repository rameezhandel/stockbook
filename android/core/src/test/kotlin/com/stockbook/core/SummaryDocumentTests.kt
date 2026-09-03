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
        SummaryDocument.forSpending(expensesRegisterIn(period), period.range(), settings, strings)

    /**
     * The page lists what was spent, one line each, newest first.
     *
     * **It used to group by what the money went on.** A printed page is checked
     * against something — the receipts in a drawer, the paper book — and a line
     * reading "Petrol, 3 times, 195" cannot be checked against anything. Where
     * the money went is still a question worth asking, and `spendingIn` still
     * answers it; it is just not what gets printed.
     */
    @Test
    fun `every expense is its own line, newest first`() {
        val store = store()
        store.addExpense(60.0, "Petrol", day)
        store.addExpense(4.0, "Tea", day.plusSeconds(60))

        val document = store.spendingDocument(StatementPeriod.thisYear())

        assertEquals(listOf("Tea", "Petrol"), document.rows.map { it.name })
        assertEquals(listOf("SAR 4", "SAR 60"), document.rows.map { it.amount })
        assertEquals("SAR 64", document.totalValue)
    }

    /** Every register line says when, because that is how a page is checked. */
    @Test
    fun `each line carries the day it was written`() {
        val store = store()
        store.addExpense(60.0, "Petrol", day)

        val row = store.spendingDocument(StatementPeriod.thisYear()).rows.single()

        assertEquals(strings.pickedDate(day), row.date)
        // An expense is a receipt from somebody else's shop. There is no number
        // of the owner's to print, so the column is not there at all.
        assertEquals(null, row.reference)
        assertEquals(3, store.spendingDocument(StatementPeriod.thisYear()).columnHeadings.size)
    }

    @Test
    fun `spendingIn still folds the month into an answer`() {
        // Forty-seven lines is a record. Three lines is an answer. Nothing prints
        // this today, and it is the only thing in the app that groups anything —
        // so it is tested here rather than left to rot.
        val store = store()
        store.addExpense(60.0, "Petrol", day)
        store.addExpense(4.0, "Tea", day)
        store.addExpense(2_000.0, "Rent", day)
        store.addExpense(65.0, "Petrol", day)

        val lines = store.spendingIn(StatementPeriod.thisYear())

        assertEquals(listOf("Rent", "Petrol", "Tea"), lines.map { it.what })
        assertEquals(listOf(2_000.0, 125.0, 4.0), lines.map { it.total })
    }

    @Test
    fun `the same word in three casings is one folded line`() {
        // Worth more than tidiness: three lines for one thing hides how much the
        // shop spends on it.
        val store = store()
        store.addExpense(60.0, "petrol", day)
        store.addExpense(65.0, "PETROL", day.plusSeconds(60))
        store.addExpense(70.0, "Petrol", day.plusSeconds(120))

        val lines = store.spendingIn(StatementPeriod.thisYear())

        assertEquals(1, lines.size)
        assertEquals("Petrol", lines.single().what, "and the newest spelling is the one shown")
        assertEquals(195.0, lines.single().total)
        assertEquals(3, lines.single().times)
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

        assertEquals("Expense Report", document.title)
        assertTrue(!document.title.contains("Statement", ignoreCase = true))
        // The last day *inside* August, not midnight on the 1st of September.
        assertEquals("1 August 2026 to 31 August 2026", document.asOf)
    }

    // --- The three registers the book gained beside the expense page

    private fun trading(): StockbookStore {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.addSupplier("Gulf Traders")
        store.saveBill(customer = "Ahmed", paid = null, amount = 300.0, createdAt = day, invoiceNo = "1207")
        store.saveBill(customer = "Ahmed", paid = null, amount = 200.0, createdAt = day)
        store.saveBill(customer = "Fatima", paid = null, amount = 900.0, createdAt = day)
        store.recordPurchase(emptyList(), "gulf traders", amount = 800.0, createdAt = day, invoiceNo = "GT-902")
        store.recordPayment("ahmed", 250.0, receivedAt = day, paymentNo = "008455")
        store.recordSupplierPayment("gulf traders", 600.0, paidAt = day)
        return store
    }

    /**
     * One line per bill, with the number on the paper and the day beside it.
     *
     * Two bills to Ahmed are two lines, not one line saying "2 bills" — a page is
     * printed to be checked against something, and a folded row cannot be.
     */
    @Test
    fun `the sales page lists every bill, not every customer`() {
        val store = trading()
        val month = StatementPeriod.Month(day)

        val page = SummaryDocument.forSales(
            store.salesRegisterIn(month, strings), month.range(), store.settings, strings
        )

        assertEquals(3, page.rows.size, "one per bill")
        assertEquals(listOf("Fatima", "Ahmed", "Ahmed"), page.rows.map { it.name })
        assertEquals("1207", page.rows.last().reference, "the number the owner typed")
        assertEquals(strings.pickedDate(day), page.rows.first().date)
        assertEquals(
            listOf(strings.columnCustomer, strings.columnInvoiceReceipt, strings.columnDate, strings.soldInPeriod),
            page.columnHeadings
        )
    }

    /** A bill with no invoice number still has one to print: the app's own. */
    @Test
    fun `a bill with nothing typed on it falls back to the app's number`() {
        val store = trading()
        val month = StatementPeriod.Month(day)

        val page = SummaryDocument.forSales(
            store.salesRegisterIn(month, strings), month.range(), store.settings, strings
        )

        assertTrue(page.rows.all { !it.reference.isNullOrBlank() }, "every row is identifiable")
    }

    /**
     * The foot of every one of these pages is the figure the card above the list
     * was showing. Two answers to one question is the whole thing they exist to
     * avoid.
     */
    @Test
    fun `each page's total is the shop's own figure for that span`() {
        val store = trading()
        val month = StatementPeriod.Month(day)
        val range = month.range()

        val sales = SummaryDocument.forSales(
            store.salesRegisterIn(month, strings), range, store.settings, strings
        )
        val purchases = SummaryDocument.forPurchases(
            store.purchasesRegisterIn(month, strings), range, store.settings, strings
        )
        val payments = SummaryDocument.forPayments(
            store.receiptsRegisterIn(month, strings), store.paidOutIn(month), range, store.settings, strings
        )
        val spending = SummaryDocument.forSpending(
            store.expensesRegisterIn(month), range, store.settings, strings
        )

        val currency = store.settings.currency
        assertEquals(Money.text(store.soldIn(month), currency), sales.totalValue)
        assertEquals(Money.text(store.boughtIn(month), currency), purchases.totalValue)
        assertEquals(Money.text(store.receivedIn(month), currency), payments.totalValue)
        assertEquals(Money.text(store.spentIn(month), currency), spending.totalValue)
    }

    /**
     * Money out is stated, but never inside a column of money in.
     *
     * A total that is not what the rows above add up to is the figure the first
     * reader to check it stops trusting — so what the shop paid its suppliers
     * goes under the total, in words.
     */
    @Test
    fun `the payments page states what went out without counting it in`() {
        val store = trading()
        val month = StatementPeriod.Month(day)

        val page = SummaryDocument.forPayments(
            store.receiptsRegisterIn(month, strings), store.paidOutIn(month), month.range(), store.settings, strings
        )

        assertEquals(1, page.rows.size, "the vouchers are not rows")
        assertEquals(Money.text(250.0, store.settings.currency), page.totalValue)
        assertNotNull(page.footnote)
        assertTrue(page.footnote!!.contains("600"), "and says what went out: ${page.footnote}")
    }

    /** No footnote where nothing went out — "0 paid to suppliers" makes the reader stop. */
    @Test
    fun `a span with no vouchers has no footnote`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.recordPayment("ahmed", 250.0, receivedAt = day)
        val month = StatementPeriod.Month(day)

        val page = SummaryDocument.forPayments(
            store.receiptsRegisterIn(month, strings), store.paidOutIn(month), month.range(), store.settings, strings
        )

        assertEquals(null, page.footnote)
    }

    /** Nothing in the span is a sentence, not an empty table. */
    @Test
    fun `an empty span says so on each of the three`() {
        val store = trading()
        val quiet = StatementPeriod.Month(Instant.parse("2026-02-10T09:00:00Z"))
        val range = quiet.range()

        assertTrue(
            SummaryDocument.forSales(
                store.salesRegisterIn(quiet, strings), range, store.settings, strings
            ).isEmpty
        )
        assertTrue(
            SummaryDocument.forPurchases(
                store.purchasesRegisterIn(quiet, strings), range, store.settings, strings
            ).isEmpty
        )
        assertTrue(
            SummaryDocument.forPayments(
                store.receiptsRegisterIn(quiet, strings), store.paidOutIn(quiet), range, store.settings, strings
            ).isEmpty
        )
    }

    // --- The letterhead every printed page now carries

    /**
     * The shop's name and address reach the page.
     *
     * Eight of the app's pages printed with no letterhead at all: a sheet in a
     * folder that did not say whose shop it came from. The band draws these two
     * fields, so a document that does not carry them is a page that cannot have
     * one.
     */
    @Test
    fun `every page carries the shop's name and address`() {
        val store = store()
        store.setOwnerName("Al Faisal Hardware")
        store.setShopAddress("King Abdulaziz Road\n\nAl Madinah")
        store.addExpense(60.0, "Petrol", day)
        store.addCustomer("Ahmed")
        val month = StatementPeriod.Month(day)

        for (page in listOf(
            store.spendingDocument(month),
            SummaryDocument.forSales(store.salesRegisterIn(month, strings), month.range(), store.settings, strings),
            SummaryDocument.forReceivable(store.customers(), store.settings, strings)
        )) {
            assertEquals("Al Faisal Hardware", page.shopName)
            // Blank lines dropped, each line trimmed — the owner types an address
            // as an address, not as a list.
            assertEquals(listOf("King Abdulaziz Road", "Al Madinah"), page.shopAddressLines)
        }
    }

    /** A shop that never typed one prints a name and no address, not an empty line. */
    @Test
    fun `an address nobody typed is no lines at all`() {
        val store = store()
        store.addExpense(60.0, "Petrol", day)

        assertEquals(emptyList(), store.spendingDocument(StatementPeriod.Month(day)).shopAddressLines)
    }
}
