package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.DaySummaryDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The day, laid out for printing.
 *
 * `DayBookTests` pins the arithmetic; this pins what the owner actually reads —
 * that a section is only there when it has something in it, that a bill nobody
 * paid for says so on its own row, and that the foot of the page states the same
 * three figures the book computed rather than a fourth opinion about them.
 */
class DaySummaryDocumentTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val zone: ZoneId = ZoneId.of("UTC")
    private val day: Instant = Instant.parse("2026-08-22T09:00:00Z")

    private fun at(hour: Int): Instant = Instant.parse("2026-08-22T%02d:00:00Z".format(hour))

    private fun store() = StockbookStore(InMemoryRepository()).also { it.setOwnerName("Al Salam Hardware") }

    private fun StockbookStore.page() =
        DaySummaryDocument.forDay(dayBook(day, zone), settings, strings)

    @Test
    fun `the page says whose it is, what it is, and which day`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))

        val page = store.page()

        assertEquals("Al Salam Hardware", page.shopName)
        assertEquals(strings.longDate(day), page.onDate)
        // The word matters. This names everybody who was billed and everything
        // the shop spent — it is not one party's account and must never carry
        // the word that means one.
        assertEquals("Day Summary", page.title)
        assertFalse(page.title.contains("Statement", ignoreCase = true))
    }

    @Test
    fun `sections come in the order the day is read, and only where there is something`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 100.0, createdAt = at(9))
        store.recordPayment(Customer.key("Ahmed"), 50.0, receivedAt = at(10))
        store.addExpense(30.0, "Petrol", spentAt = at(11))

        // No deliveries, no supplier payments, no credit notes that day — and so
        // no headings for them. A heading over nothing is a question the reader
        // has to answer themselves.
        assertEquals(listOf("Bills", "Received", "Expenses"), store.page().sections.map { it.heading })
    }

    @Test
    fun `a section totals what those things came to`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 100.0, createdAt = at(9))
        store.saveBill(customer = "Khalid", paid = null, amount = 250.5, createdAt = at(10))

        val bills = store.page().sections.single()

        assertEquals("Subtotal", bills.subtotalLabel)
        // What was sold, not what was collected for it. The cash foot is where
        // that question gets answered, once, for the whole day.
        assertEquals("SAR 350.50", bills.subtotalValue)
    }

    @Test
    fun `a bill still owed for says how much, and one paid in full says nothing`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 40.0, amount = 100.0, invoiceNo = "6356", createdAt = at(9))
        store.saveBill(customer = "Khalid", paid = null, amount = 50.0, invoiceNo = "6357", createdAt = at(10))

        val rows = store.page().sections.single().rows

        assertEquals("Invoice #6356 · SAR 60 on credit", rows[0].detail)
        assertEquals("Invoice #6357", rows[1].detail)
    }

    @Test
    fun `a credit note never says on credit`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, createdAt = at(9))
        store.addCreditNote(Customer.key("Ahmed"), amount = 120.0, noteNo = "22", issuedAt = at(10))

        val note = store.page().sections.last().rows.single()

        // It settles nothing by design. Marking the whole of it "on credit"
        // would be saying money is owed that was never going to be paid.
        assertEquals("Credit Note #22", note.detail)
    }

    @Test
    fun `a bill lists what was on it, under its own row`() {
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(
            lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = at(9)
        )

        val item = assertNotNull(store.page().sections.single().rows.single().items.firstOrNull())

        assertEquals("3 × Padlock 40mm", item.text)
        assertEquals("SAR 90", item.amount)
    }

    @Test
    fun `the foot states the day's cash, and the net may be negative`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordSupplierBill(supplier.key, amount = 250.0, createdAt = at(10))

        val cash = store.page().cash

        assertEquals(listOf("Money in", "Money out", "Net for the day"), cash.map { it.label })
        assertEquals(listOf("SAR 100", "SAR 250", "-SAR 150"), cash.map { it.value })
        // The one figure the eye should stop on, and the only line marked.
        assertEquals(listOf(false, false, true), cash.map { it.isNet })
    }

    @Test
    fun `money in is what the book says, never what the rows add up to`() {
        val store = store()
        // Three hundred sold, forty of it taken. A page whose foot read 300
        // would have the owner hunting for money nobody paid.
        store.saveBill(customer = "Ahmed", paid = 40.0, amount = 100.0, createdAt = at(9))
        store.saveBill(customer = "Khalid", paid = 0.0, amount = 200.0, createdAt = at(10))

        val page = store.page()

        assertEquals("SAR 300", page.sections.single().subtotalValue)
        assertEquals("SAR 40", page.cash.first().value)
    }

    @Test
    fun `a day nothing happened on states that and nothing else`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = Instant.parse("2026-08-20T09:00:00Z"))

        val page = store.page()

        assertTrue(page.isEmpty)
        assertEquals("Nothing was recorded on this day.", page.emptyLine)
        // No cash foot either: a page with no figures on it must not state a
        // cash position, even a zero one, for a day it knows nothing about.
        assertTrue(page.cash.isEmpty())
    }

    @Test
    fun `the paper is called what the statement calls it`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))
        store.recordPayment(Customer.key("Ahmed"), 50.0, paymentNo = "1024", receivedAt = at(10))
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordSupplierBill(supplier.key, amount = 300.0, invoiceNo = "88", createdAt = at(11))
        store.addExpense(30.0, "Petrol", spentAt = at(12))

        val details = store.page().sections.flatMap { it.rows }.map { it.detail }

        assertEquals(
            listOf(
                // No paper number on the bill, so the app's own counter — the
                // same fallback the statement uses.
                "Bill #1",
                "Payment #1024",
                "Purchase #88",
                // Joined to nobody, numbered by nobody.
                null
            ),
            details
        )
    }

    @Test
    fun `an expense is named by what it went on`() {
        val store = store()
        store.addExpense(30.0, "Petrol", spentAt = at(9))

        val row = store.page().sections.single().rows.single()

        assertEquals("Petrol", row.name)
        assertEquals("SAR 30", row.amount)
    }
}
