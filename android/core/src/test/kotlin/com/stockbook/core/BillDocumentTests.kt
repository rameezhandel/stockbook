package com.stockbook.core

import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.BillDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bill as the paper the customer walks out with.
 *
 * The claims worth pinning are the ones a customer would notice and argue about:
 * that the arithmetic behind every line is on the page, that a discount is shown
 * rather than quietly folded into the total, and that a part-paid bill says so —
 * with the figure still owed and the name of who owes it.
 *
 * It is also the document that replaced a plain-text bill, so it has to carry
 * everything that text carried. Nothing here may be less than what was sent
 * before.
 */
class BillDocumentTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val at: Instant = Instant.parse("2026-09-02T13:49:00Z")

    private fun store() = StockbookStore(InMemoryRepository()).also {
        it.setOwnerName("Al Salam Hardware")
        it.setShopAddress("King Fahd Road\n\nAl Khobar")
    }

    private fun StockbookStore.page(): BillDocument {
        val bill = assertNotNull(bills.firstOrNull())
        return BillDocument.make(bill, settings, strings, customer = customer("ahmed"))
    }

    @Test
    fun `the letterhead is the shop, and the page says what it is`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at, invoiceNo = "5678")

        val page = store.page()

        assertEquals("Al Salam Hardware", page.shopName)
        assertEquals(listOf("King Fahd Road", "Al Khobar"), page.shopAddressLines)
        assertEquals("Invoice", page.docType)
        assertEquals("Billed to:", page.addressedToLabel)
        assertEquals("Ahmed", page.partyName)
    }

    /**
     * One number, never both. Two numbers on a document is how somebody reads out
     * the wrong one over the phone — the rule `Bill.reference` already keeps, and
     * this page takes it from there rather than deciding again.
     */
    @Test
    fun `the paper's own number wins, and the app's stands in where there is none`() {
        val typed = store()
        typed.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at, invoiceNo = "5678")
        assertEquals("5678", typed.page().reference)

        val untyped = store()
        untyped.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)
        assertEquals("Bill #1", untyped.page().reference)
    }

    @Test
    fun `an itemised bill shows the arithmetic behind every line`() {
        val store = store()
        val lock = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        val hinge = store.addProduct("Brass hinge", 100, 4.0, 7.5)
        store.saveBill(
            listOf(DraftLine(lock.uid, 2, 95.0), DraftLine(hinge.uid, 4, 7.5)),
            "Ahmed",
            paid = null,
            createdAt = at
        )

        val page = store.page()

        assertTrue(page.isItemised)
        assertEquals(2, page.lines.size)
        assertEquals("Cisa lock", page.lines[0].name)
        assertEquals("2 × SAR 95", page.lines[0].detail)
        assertEquals("SAR 190", page.lines[0].amount)
        assertEquals("4 × SAR 7.50", page.lines[1].detail)
        assertEquals("SAR 30", page.lines[1].amount)
        assertEquals("SAR 220", page.totalValue)
    }

    /**
     * A bill copied out of the paper book is a figure and nothing else, and the
     * page has to be a page anyway. The shape of this shop, not an edge case.
     */
    @Test
    fun `a bill entered as a figure has no lines and still prints`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)

        val page = store.page()

        assertFalse(page.isItemised)
        assertTrue(page.lines.isEmpty())
        assertEquals("Total", page.totalLabel)
        assertEquals("SAR 235", page.totalValue)
    }

    /**
     * The customer's own copy is exactly where a discount belongs: it is the
     * reason the figure is what it is, and a shop that gave ten per cent away
     * should get the credit for it.
     */
    @Test
    fun `a discount is shown, and only where one was given`() {
        val plain = store()
        plain.saveBill(customer = "Ahmed", paid = null, amount = 200.0, createdAt = at)
        assertTrue(plain.page().summaryRows.isEmpty(), "no discount, so no line about one")

        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 200.0, createdAt = at, discountPercent = 10.0)

        val rows = store.page().summaryRows

        assertEquals(2, rows.size)
        assertEquals("Subtotal", rows[0].label)
        assertEquals("SAR 200", rows[0].value)
        assertEquals("Discount 10%", rows[1].label)
        assertEquals("SAR 20", rows[1].value)
        assertTrue(rows[1].deduction, "the one line that comes off")
        assertEquals("SAR 180", store.page().totalValue)
    }

    @Test
    fun `a bill settled at the counter says so`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)

        assertEquals("Paid in full, cash.", store.page().paymentNote)
    }

    /**
     * The line the customer checks. It names what was handed over, who still owes
     * and how much — a part-paid bill that merely said "part paid" would send
     * them back to the counter to ask.
     */
    @Test
    fun `a part paid bill names what is left and who owes it`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 25.0, amount = 235.0, createdAt = at)

        val note = store.page().paymentNote

        assertTrue(note.contains("SAR 25"), note)
        assertTrue(note.contains("SAR 210"), note)
        assertTrue(note.contains("Ahmed"), note)
    }

    /** The roster's own place and phone, where the roster knows them. */
    @Test
    fun `the customer's details come from the roster, and are left out when there are none`() {
        val known = store()
        known.addCustomer("Ahmed", phone = "0501234567", place = "Al Khobar")
        known.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)
        assertEquals(listOf("Al Khobar", "0501234567"), known.page().partyLines)

        val stranger = store()
        stranger.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)
        assertTrue(stranger.page().partyLines.isEmpty())
    }

    @Test
    fun `the date carries the time, because two bills a day to one customer is ordinary`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 235.0, createdAt = at)

        val page = store.page()

        assertEquals("Date", page.dateLabel)
        assertEquals(
            strings.billWhen(strings.longDate(at), strings.time(at)),
            page.dateValue
        )
    }
}
