package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * What the shop turned over across a span, for the figure Home shows.
 *
 * Almost all of this is about **boundaries**. The arithmetic is a sum and could
 * hardly be wrong; what can be wrong is which bills are counted, and a bill that
 * lands in two months — or in neither — is a figure the owner cannot reconcile
 * against anything. That is why these tests pin the edges of a month rather than
 * its middle.
 */
class TurnoverTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun august(day: Int, hour: Int = 12) =
        Instant.parse("2026-08-%02dT%02d:00:00Z".format(day, hour))

    private fun StockbookStore.sold(at: Instant, amount: Double, who: String = "Ahmed", no: String) {
        saveBill(customer = who, paid = 0.0, amount = amount, createdAt = at, invoiceNo = no)
    }

    private val august = StatementPeriod.Month(august(15))

    @Test
    fun `a month's sales are the bills written in it`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.sold(august(3), 1000.0, no = "1")
        store.sold(august(17), 450.0, no = "2")

        assertEquals(1450.0, store.soldIn(august))
        assertEquals(2, store.billCountIn(august))
    }

    @Test
    fun `bills outside the month are left out`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.sold(Instant.parse("2026-07-31T23:00:00Z"), 900.0, no = "1")
        store.sold(august(10), 500.0, no = "2")
        store.sold(Instant.parse("2026-09-01T01:00:00Z"), 700.0, no = "3")

        assertEquals(500.0, store.soldIn(august), "July and September belong to July and September")
    }

    @Test
    fun `a bill on the turn of the month lands in exactly one of them`() {
        // The half-open range is what guarantees this. Counted at both ends, a
        // bill written at midnight on the 1st would appear on two months'
        // takings, and the year would not add up.
        val store = store()
        store.addCustomer("Ahmed")
        val firstOfAugust = august(day = 1, hour = 0)
        store.sold(firstOfAugust, 300.0, no = "1")

        val july = StatementPeriod.Month(Instant.parse("2026-07-15T12:00:00Z"))

        assertEquals(300.0, store.soldIn(august))
        assertEquals(0.0, store.soldIn(july))
    }

    @Test
    fun `a year is the sum of its months`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.sold(Instant.parse("2026-02-10T12:00:00Z"), 100.0, no = "1")
        store.sold(august(10), 250.0, no = "2")
        store.sold(Instant.parse("2026-12-30T12:00:00Z"), 400.0, no = "3")
        store.sold(Instant.parse("2025-12-30T12:00:00Z"), 999.0, no = "4")

        val year = StatementPeriod.Year(august(15))

        assertEquals(750.0, store.soldIn(year), "and last year stays in last year")
    }

    @Test
    fun `an itemised bill counts what it came to`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 2, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            createdAt = august(4),
            invoiceNo = "1"
        )

        assertEquals(190.0, store.soldIn(august))
    }

    @Test
    fun `a credit note does not unsell the month`() {
        // The rule worth being deliberate about. A credit note reduces what
        // somebody owes; it does not undo the sale, and a month's takings that
        // quietly shrank weeks later would be a figure nobody could reconcile
        // against the till. The statement is where the two are netted.
        val store = store()
        store.addCustomer("Ahmed")
        store.sold(august(4), 1000.0, no = "1")
        store.addCreditNote(customerKey = "ahmed", amount = 400.0, noteNo = "CN-1", issuedAt = august(20))

        assertEquals(1000.0, store.soldIn(august))
        assertEquals(600.0, assertNotNull(store.customer("ahmed")).owed, "but the balance does come down")
    }

    @Test
    fun `a payment does not change what was sold`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.sold(august(4), 1000.0, no = "1")
        store.recordPayment("ahmed", 600.0, receivedAt = august(20))

        assertEquals(1000.0, store.soldIn(august), "money arriving is not a second sale")
    }

    @Test
    fun `removing a bill takes it out of the month`() {
        val store = store()
        store.addCustomer("Ahmed")
        val bill = assertNotNull(
            store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, createdAt = august(4), invoiceNo = "1")
        )
        store.sold(august(6), 200.0, no = "2")

        store.deleteBill(bill.number)

        assertEquals(200.0, store.soldIn(august))
        assertEquals(1, store.billCountIn(august))
    }

    @Test
    fun `nothing sold is zero rather than nothing`() {
        // The card has to draw something. Zero is a figure; absent is not.
        assertEquals(0.0, store().soldIn(august))
        assertEquals(0, store().billCountIn(august))
    }

    @Test
    fun `what was bought is counted the same way`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        store.recordPurchase(
            product,
            supplier.key,
            quantity = 10,
            unitCost = 60.0,
            createdAt = august(5),
            invoiceNo = "INV-1"
        )
        store.recordPurchase(
            product,
            supplier.key,
            quantity = 5,
            unitCost = 60.0,
            createdAt = Instant.parse("2026-07-05T12:00:00Z"),
            invoiceNo = "INV-2"
        )

        assertEquals(600.0, store.boughtIn(august))
    }
}
