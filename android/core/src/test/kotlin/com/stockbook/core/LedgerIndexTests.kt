package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.Strings
import com.stockbook.core.text.SummaryDocument
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ledger book's contents page: every customer, and where they stand.
 *
 * The claim being pinned is that **the index and the pages behind it are one
 * document**. Both come from the same `ledgerBook()` list, in the same order,
 * with the same figures — a contents page naming a balance the page it points at
 * disagrees with is worse than no contents page at all, and the only way that
 * cannot happen is for there to be one list.
 *
 * The second claim is that it is an **index and not a chasing list**. The
 * receivable summary drops anybody who does not owe; this one cannot, because a
 * customer with a page in the book and no line in the contents reads as a
 * customer who was left out.
 */
class LedgerIndexTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val now: Instant = Instant.parse("2026-08-22T09:00:00Z")

    private fun at(day: Int): Instant = Instant.parse("2026-08-%02dT09:00:00Z".format(day))

    private fun store() = StockbookStore(InMemoryRepository()).also { it.setOwnerName("Al Salam Hardware") }

    private fun StockbookStore.index(): SummaryDocument =
        SummaryDocument.forLedgerBook(ledgerBook(), settings, strings, now = now)

    @Test
    fun `the index says whose book it is and what it lists`() {
        val store = store()
        store.addCustomer("Ahmed")

        val index = store.index()

        assertEquals("Al Salam Hardware", index.shopName)
        assertEquals("Customer Balances", index.title)
        assertEquals("As of ${strings.longDate(now)}", index.asOf)
        assertEquals(listOf("Customer", "Balance"), index.columnHeadings)
    }

    /**
     * The whole roll-call, not the debtors. A name with a page in the book and
     * no line in the contents is a name the reader concludes is missing.
     */
    @Test
    fun `everybody is listed, including the settled and the ones in credit`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.addCustomer("Khalid")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = at(10))
        // Settled up: billed and paid in full.
        store.saveBill(customer = "Fatima", paid = null, amount = 400.0, createdAt = at(11))
        // Paid ahead of any bill, so in credit.
        store.recordPayment(Customer.key("Khalid"), 250.0, receivedAt = at(12))

        val index = store.index()

        assertEquals(3, index.rows.size)
        assertEquals(listOf("Ahmed", "Fatima", "Khalid"), index.rows.map { it.name })
        assertEquals("SAR 1,000", index.rows[0].amount)
        assertEquals("SAR 0", index.rows[1].amount, "settled up still gets a line")
        // Exactly as that customer's own page states it — `Money.text`, sign and
        // all. The index matching the page matters more here than a prettier
        // bracket would.
        assertEquals("SAR -250", index.rows[2].amount, "and so does money held in advance")
    }

    /**
     * The index and the pages are one list read twice. Same names, same order,
     * same figures — checked against the statement pages themselves rather than
     * against literals, because it is the agreement that matters.
     */
    @Test
    fun `every line matches the page it points at`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = at(10))
        store.recordPayment(Customer.key("Ahmed"), 300.0, receivedAt = at(12))
        store.saveBill(customer = "Fatima", paid = 0.0, amount = 250.0, createdAt = at(11))

        val book = store.ledgerBook()
        val index = SummaryDocument.forLedgerBook(book, store.settings, strings, now = now)
        val pages = book.map { StatementDocument.make(it, store.settings, strings, now = now) }

        assertEquals(pages.size, index.rows.size)
        for ((line, page) in index.rows.zip(pages)) {
            assertEquals(page.partyName, line.name)
            assertEquals(page.closingValue, line.amount, "the contents disagrees with ${page.partyName}'s page")
        }
    }

    /**
     * The foot is what the column adds up to, credits included — not the shop's
     * receivable, which counts only what is owed. A total that is not the sum of
     * the lines above it is the figure a reader stops trusting the page over.
     */
    @Test
    fun `the total is the column, not the receivable`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Khalid")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = at(10))
        store.recordPayment(Customer.key("Khalid"), 250.0, receivedAt = at(12))

        val index = store.index()

        assertEquals("Total", index.totalLabel)
        assertEquals("SAR 750", index.totalValue, "a thousand owed less two hundred and fifty held")
        // The chasing list is the other document, and it says something else on
        // purpose. Both are right; they answer different questions.
        val receivable = SummaryDocument.forReceivable(store.customers(), store.settings, strings, now = now)
        assertEquals("SAR 1,000", receivable.totalValue)
        assertEquals(1, receivable.rows.size, "Khalid is not a debtor")
    }

    @Test
    fun `a shop with no customers has an index that says so`() {
        val index = store().index()

        assertTrue(index.isEmpty)
        assertEquals(strings.ledgerNoCustomers, index.emptyLine)
    }
}
