package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.model.Supplier
import com.stockbook.core.store.DayEntryKind
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One day of the shop, read back off the six records that carry a date.
 *
 * Most of what is asserted here is **the cash line**, because that is the figure
 * the owner checks against the drawer at closing time and the one place a
 * plausible mistake is expensive: counting a credit sale as takings, or a credit
 * note as money handed back, gives a page that looks right and does not
 * reconcile. The rest pins that a day is a day — that the record entered ten
 * minutes before midnight belongs to the day it was entered on and to no other.
 */
class DayBookTests {

    // Fixed rather than `systemDefault()`: a test that passes in Riyadh and fails
    // on a CI runner set to UTC teaches nothing about the code.
    private val zone: ZoneId = ZoneId.of("UTC")
    private val day: Instant = Instant.parse("2026-08-22T09:00:00Z")

    private fun at(hour: Int): Instant = Instant.parse("2026-08-22T%02d:00:00Z".format(hour))

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.book() = dayBook(day, zone)

    @Test
    fun `all six kinds of record reach the page`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))
        store.recordPayment(Customer.key("Ahmed"), 50.0, receivedAt = at(10))
        store.addCreditNote(Customer.key("Ahmed"), amount = 20.0, issuedAt = at(11))
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordSupplierBill(supplier.key, amount = 300.0, createdAt = at(12))
        store.recordSupplierPayment(supplier.key, 80.0, paidAt = at(13))
        store.addExpense(30.0, "Petrol", spentAt = at(14))

        val kinds = store.book().entries.map { it.kind }

        // In the order they happened, which is the order the store returns them
        // in — the document is what groups them, not this.
        assertEquals(
            listOf(
                DayEntryKind.BILL,
                DayEntryKind.PAYMENT,
                DayEntryKind.CREDIT_NOTE,
                DayEntryKind.DELIVERY,
                DayEntryKind.SUPPLIER_PAYMENT,
                DayEntryKind.EXPENSE
            ),
            kinds
        )
    }

    @Test
    fun `yesterday and tomorrow stay off today`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 10.0, createdAt = Instant.parse("2026-08-21T23:59:59Z"))
        store.saveBill(customer = "Ahmed", paid = null, amount = 20.0, createdAt = Instant.parse("2026-08-22T00:00:00Z"))
        store.saveBill(customer = "Ahmed", paid = null, amount = 30.0, createdAt = Instant.parse("2026-08-22T23:59:59Z"))
        store.saveBill(customer = "Ahmed", paid = null, amount = 40.0, createdAt = Instant.parse("2026-08-23T00:00:00Z"))

        val amounts = store.book().entries.map { it.amount }

        // Both ends of the day itself, and neither midnight belonging to two
        // days — the half-open range `StatementPeriod` already uses.
        assertEquals(listOf(20.0, 30.0), amounts)
    }

    @Test
    fun `money in is what was taken, not what was billed`() {
        val store = store()
        // Paid at the counter, part paid, and entirely on credit.
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))
        store.saveBill(customer = "Khalid", paid = 40.0, amount = 100.0, createdAt = at(10))
        store.saveBill(customer = "Saeed", paid = 0.0, amount = 100.0, createdAt = at(11))

        val book = store.book()

        // Three hundred sold, a hundred and forty in the drawer. A day book that
        // reported 300 here would have the owner hunting for money nobody paid.
        assertEquals(300.0, book.entries.sumOf { it.amount })
        assertEquals(140.0, book.moneyIn)
    }

    @Test
    fun `a receipt against an old bill is money in on the day it arrives`() {
        val store = store()
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 500.0,
            createdAt = Instant.parse("2026-07-04T09:00:00Z")
        )
        store.recordPayment(Customer.key("Ahmed"), 200.0, receivedAt = at(11))

        val book = store.book()

        // The bill is July's; only the receipt is today's.
        assertEquals(1, book.entries.size)
        assertEquals(200.0, book.moneyIn)
    }

    @Test
    fun `money out is deliveries settled, supplier payments and spending`() {
        val store = store()
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        // Settled on the spot, part paid, and taken entirely on the shop's account.
        store.recordSupplierBill(supplier.key, amount = 300.0, createdAt = at(9))
        store.recordSupplierBill(supplier.key, amount = 200.0, paid = 50.0, createdAt = at(10))
        store.recordSupplierBill(supplier.key, amount = 400.0, paid = 0.0, createdAt = at(11))
        store.recordSupplierPayment(supplier.key, 80.0, paidAt = at(12))
        store.addExpense(30.0, "Petrol", spentAt = at(13))

        assertEquals(300.0 + 50.0 + 80.0 + 30.0, store.book().moneyOut)
    }

    @Test
    fun `a credit note moves no money either way`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, createdAt = at(9))
        store.addCreditNote(Customer.key("Ahmed"), amount = 120.0, issuedAt = at(10))

        val book = store.book()
        val note = assertNotNull(book.entriesOf(DayEntryKind.CREDIT_NOTE).firstOrNull())

        // It is on the page — the owner wants to see it — and it is in neither
        // column. Nothing was taken and nothing was handed back.
        assertEquals(120.0, note.amount)
        assertEquals(0.0, note.settled)
        assertEquals(0.0, book.moneyIn)
        assertEquals(0.0, book.moneyOut)
    }

    @Test
    fun `net is what the day did to the cash box, and may be negative`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordSupplierBill(supplier.key, amount = 250.0, createdAt = at(10))

        val book = store.book()

        assertEquals(100.0, book.moneyIn)
        assertEquals(250.0, book.moneyOut)
        // A shop that restocked in the morning is a hundred and fifty down at
        // closing time, and the page has to be able to say so.
        assertEquals(-150.0, book.net)
    }

    @Test
    fun `an itemised bill carries what was on it`() {
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(
            lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = at(9)
        )

        val bill = assertNotNull(store.book().entriesOf(DayEntryKind.BILL).firstOrNull())
        val item = assertNotNull(bill.items.firstOrNull())

        assertEquals("Padlock 40mm", item.name)
        assertEquals(3, item.qty)
        assertEquals(90.0, item.amount)
    }

    @Test
    fun `a bill entered as a figure has nothing to list`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = at(9))

        // Ordinary, not exceptional: a shop entering the paper bill it already
        // wrote knows the total and has no reason to rebuild it.
        assertTrue(store.book().entriesOf(DayEntryKind.BILL).single().items.isEmpty())
    }

    @Test
    fun `a bill shows the number on the paper where there is one`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, invoiceNo = "6356", createdAt = at(9))
        store.saveBill(customer = "Khalid", paid = null, amount = 50.0, createdAt = at(10))

        val entries = store.book().entriesOf(DayEntryKind.BILL)

        assertEquals("6356", entries[0].reference)
        // No paper number, so the app's own counter travels instead — as a
        // number, because "Bill #7" is words and words live in `Strings`.
        assertEquals(null, entries[1].reference)
        assertEquals(2, entries[1].billNumber)
    }

    @Test
    fun `a person is spelled the way the rest of the app spells them`() {
        val store = store()
        // The roster spelling wins over whatever was typed in a hurry at the
        // counter, exactly as `customers()` decides it. A day book that named
        // somebody differently from the statement beside it is a page the owner
        // stops trusting.
        store.addCustomer("Ahmed Contracting")
        store.saveBill(customer = "ahmed contracting", paid = 0.0, amount = 100.0, createdAt = at(9))
        store.recordPayment(Customer.key("AHMED CONTRACTING"), 40.0, receivedAt = at(10))

        assertEquals(
            listOf("Ahmed Contracting", "Ahmed Contracting"),
            store.book().entries.map { it.who }
        )
    }

    @Test
    fun `a supplier is named, never keyed`() {
        val store = store()
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordSupplierPayment(supplier.key, 80.0, paidAt = at(9))

        assertEquals("Gulf Locks", store.book().entries.single().who)
        assertEquals(Supplier.key("Gulf Locks"), supplier.key)
    }

    @Test
    fun `an expense is named by what it went on, because it is joined to nobody`() {
        val store = store()
        store.addExpense(30.0, "Petrol", spentAt = at(9))

        val expense = store.book().entriesOf(DayEntryKind.EXPENSE).single()

        assertEquals("Petrol", expense.who)
        assertEquals(null, expense.reference)
    }

    @Test
    fun `a day nothing happened on is empty`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = Instant.parse("2026-08-20T09:00:00Z"))

        val book = store.book()

        assertTrue(book.isEmpty)
        assertEquals(0.0, book.moneyIn)
        assertEquals(0.0, book.moneyOut)
        assertEquals(0.0, book.net)
    }
}
