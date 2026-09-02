package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bills the sales list shows for a span.
 *
 * The claim worth pinning is that this uses **the same idea of a period as
 * everything else** — half-open bounds, whole days in the phone's own zone. A
 * screen that invented its own would put a bill written at ten to midnight on
 * the list for one month and the statement for another, and the owner would find
 * it by adding the two up and getting a figure that matches neither.
 */
class BillsInPeriodTests {

    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")

    private fun on(month: Int, day: Int, hour: Int = 9): Instant =
        Instant.parse("2026-%02d-%02dT%02d:00:00Z".format(month, day, hour))

    private fun store() = StockbookStore(InMemoryRepository())

    @Test
    fun `only the bills inside the span, newest first`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = on(7, 28))
        store.saveBill(customer = "Fatima", paid = null, amount = 200.0, createdAt = on(8, 3))
        store.saveBill(customer = "Khalid", paid = null, amount = 300.0, createdAt = on(8, 19))
        store.saveBill(customer = "Noura", paid = null, amount = 400.0, createdAt = on(9, 2))

        val august = store.billsIn(StatementPeriod.Month(on(8, 15)), zone)

        assertEquals(listOf("Khalid", "Fatima"), august.map { it.who }, "inside the month, newest first")
    }

    /**
     * The same half-open bounds the statement uses. A bill written at midnight on
     * the 1st belongs to exactly one month, and this and the statement have to
     * agree which.
     */
    @Test
    fun `the span is the statement's span, boundaries and all`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = on(8, 1, hour = 0))
        store.saveBill(customer = "Fatima", paid = null, amount = 200.0, createdAt = on(8, 31, hour = 23))

        val august = StatementPeriod.Month(on(8, 15))

        // In the phone's own zone on all three, deliberately. `billCountIn` and
        // `soldIn` take no zone and use the default; handing this one a different
        // zone would be comparing two Augusts, which is the very confusion the
        // test exists to rule out.
        assertEquals(store.billCountIn(august), store.billsIn(august).size)
        assertEquals(
            store.soldIn(august),
            store.billsIn(august).sumOf { it.total },
            "the list and the shop's own total cover the same bills"
        )
    }

    @Test
    fun `a chosen range covers whole days at both ends`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = on(8, 10, hour = 1))
        store.saveBill(customer = "Fatima", paid = null, amount = 200.0, createdAt = on(8, 12, hour = 20))
        store.saveBill(customer = "Khalid", paid = null, amount = 300.0, createdAt = on(8, 14))

        val picked = store.billsIn(StatementPeriod.Custom(on(8, 10), on(8, 12)), zone)

        assertEquals(listOf("Fatima", "Ahmed"), picked.map { it.who }, "both end days are inside")
    }

    /**
     * The other halves of the book, over the same span and by the same rule.
     *
     * Their own tests rather than trust by resemblance: several lists that must
     * agree about which days belong to a month is exactly the kind of agreement
     * that decays when one of them is corrected.
     */
    @Test
    fun `purchases and spending narrow the same way`() {
        val store = store()
        store.addSupplier("Gulf Traders")
        store.recordPurchase(emptyList(), "gulf traders", amount = 500.0, createdAt = on(7, 30))
        store.recordPurchase(emptyList(), "gulf traders", amount = 800.0, createdAt = on(8, 12))
        store.addExpense(40.0, "Petrol", spentAt = on(7, 30))
        store.addExpense(90.0, "Tea", spentAt = on(8, 12))

        val august = StatementPeriod.Month(on(8, 15))

        assertEquals(listOf(800.0), store.purchasesIn(august, zone).map { it.total })
        assertEquals(listOf("Tea"), store.expensesIn(august, zone).map { it.note })
    }

    /** Each list ties to the shop-wide figure for the same span. */
    @Test
    fun `the lists tie to the totals the shop already publishes`() {
        val store = store()
        store.addSupplier("Gulf Traders")
        store.recordPurchase(emptyList(), "gulf traders", amount = 800.0, createdAt = on(8, 12))
        store.addExpense(90.0, "Tea", spentAt = on(8, 12))

        val august = StatementPeriod.Month(on(8, 15))

        assertEquals(store.boughtIn(august), store.purchasesIn(august).sumOf { it.total })
        assertEquals(store.spentIn(august), store.expensesIn(august).sumOf { it.amount })
    }

    @Test
    fun `a span with nothing in it is empty rather than everything`() {
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0, createdAt = on(8, 10))

        assertTrue(store.billsIn(StatementPeriod.Month(on(5, 4)), zone).isEmpty())
        assertTrue(store.purchasesIn(StatementPeriod.Month(on(5, 4)), zone).isEmpty())
        assertTrue(store.expensesIn(StatementPeriod.Month(on(5, 4)), zone).isEmpty())
        assertTrue(store.paymentsIn(StatementPeriod.Month(on(5, 4)), zone).isEmpty())
        assertTrue(store.supplierPaymentsIn(StatementPeriod.Month(on(5, 4)), zone).isEmpty())
    }

    /** Money in, over the same span and by the same rule as everything else. */
    @Test
    fun `only the receipts inside the span, newest first`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.recordPayment("ahmed", 100.0, receivedAt = on(7, 28))
        store.recordPayment("fatima", 200.0, receivedAt = on(8, 3))
        store.recordPayment("ahmed", 300.0, receivedAt = on(8, 19))
        store.recordPayment("fatima", 400.0, receivedAt = on(9, 2))

        val august = store.paymentsIn(StatementPeriod.Month(on(8, 15)), zone)

        assertEquals(listOf(300.0, 200.0), august.map { it.amount }, "inside the month, newest first")
    }

    /** And money out, which is a separate series and stays one. */
    @Test
    fun `vouchers narrow the same way and stay apart from receipts`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addSupplier("Gulf Traders")
        store.recordPayment("ahmed", 100.0, receivedAt = on(8, 12))
        store.recordSupplierPayment("gulf traders", 700.0, paidAt = on(7, 30))
        store.recordSupplierPayment("gulf traders", 900.0, paidAt = on(8, 12))

        val august = StatementPeriod.Month(on(8, 15))

        assertEquals(listOf(900.0), store.supplierPaymentsIn(august, zone).map { it.amount })
        assertEquals(listOf(100.0), store.paymentsIn(august, zone).map { it.amount })
    }

    /**
     * The two figures the payments list is headed by.
     *
     * Deliberately not netted against each other anywhere: what came in and what
     * went out are two facts, and one number standing for both is a figure the
     * owner cannot check against anything they are holding.
     */
    @Test
    fun `the receipts and vouchers tie to their own totals`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addSupplier("Gulf Traders")
        store.recordPayment("ahmed", 250.0, receivedAt = on(8, 4))
        store.recordPayment("ahmed", 150.0, receivedAt = on(8, 20))
        store.recordSupplierPayment("gulf traders", 900.0, paidAt = on(8, 12))

        val august = StatementPeriod.Month(on(8, 15))

        assertEquals(400.0, store.receivedIn(august))
        assertEquals(900.0, store.paidOutIn(august))
        assertEquals(store.receivedIn(august), store.paymentsIn(august).sumOf { it.amount })
        assertEquals(store.paidOutIn(august), store.supplierPaymentsIn(august).sumOf { it.amount })
    }

    /**
     * A credit note is not a payment.
     *
     * Both reduce what a customer owes, which is exactly why this is worth
     * pinning: a list headed "Payments" that swept credits in would state that
     * the shop took money it never saw.
     */
    @Test
    fun `a credit note is not money received`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = null, amount = 500.0, createdAt = on(8, 2))
        store.recordPayment("ahmed", 200.0, receivedAt = on(8, 10))
        store.addCreditNote("ahmed", amount = 120.0, issuedAt = on(8, 11))

        val august = StatementPeriod.Month(on(8, 15))

        assertEquals(listOf(200.0), store.paymentsIn(august, zone).map { it.amount })
        assertEquals(200.0, store.receivedIn(august))
    }

    /**
     * The two directions as one list, newest first, each line saying which way
     * it went and who it was with.
     */
    @Test
    fun `the payment book carries both directions, newest first`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addSupplier("Gulf Traders")
        store.recordPayment("ahmed", 300.0, receivedAt = on(8, 4), paymentNo = "008455")
        store.recordSupplierPayment("gulf traders", 900.0, paidAt = on(8, 12))
        store.recordPayment("ahmed", 150.0, receivedAt = on(8, 20))

        val book = store.paymentBook(StatementPeriod.Month(on(8, 15)), zone)

        assertEquals(listOf(150.0, 900.0, 300.0), book.map { it.amount }, "newest first")
        assertEquals(listOf(true, false, true), book.map { it.incoming })
        assertEquals(listOf("Ahmed", "Gulf Traders", "Ahmed"), book.map { it.who }, "named, not keyed")
        assertEquals(listOf(null, null, "008455"), book.map { it.reference })
    }

    /**
     * Two slips written in the same second land in the same order on both
     * platforms.
     *
     * Not a contrived case: the owner settles with a supplier and takes a
     * customer's money at one counter, and both are typed with the same date.
     * Kotlin's sort is stable and Swift's is not, so a tie left to the sort
     * would hold still here and shuffle between reads there.
     */
    @Test
    fun `slips written in the same second break the tie on the id`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addSupplier("Gulf Traders")
        val moment = on(8, 12)
        store.recordPayment("ahmed", 300.0, receivedAt = moment)
        store.recordSupplierPayment("gulf traders", 900.0, paidAt = moment)

        val book = store.paymentBook(StatementPeriod.Month(on(8, 15)), zone)

        assertEquals(book.map { it.id }, book.map { it.id }.sorted(), "the tie is the id, ascending")
    }
}
