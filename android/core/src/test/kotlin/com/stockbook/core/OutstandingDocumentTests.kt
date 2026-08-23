package com.stockbook.core

import com.stockbook.core.money.Money
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.OutstandingDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
class OutstandingDocumentTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val day: Instant = Instant.parse("2026-08-22T09:00:00Z")

    private fun store() = StockbookStore(InMemoryRepository())

    /** A customer owing exactly [amount], by way of an unpaid bill. */
    private fun StockbookStore.owing(name: String, amount: Double, invoiceNo: String) {
        saveBill(customer = name, paid = 0.0, amount = amount, invoiceNo = invoiceNo)
    }

    private fun StockbookStore.document() =
        OutstandingDocument.make(customers(), settings, strings, now = day)

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

    @Test
    fun `the shop's own name heads it`() {
        val store = store()
        store.setOwnerName("Al Salam Hardware")

        assertEquals("Al Salam Hardware", store.document().shopName)
    }
}
