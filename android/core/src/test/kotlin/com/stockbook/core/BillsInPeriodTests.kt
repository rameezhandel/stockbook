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
     * The other two halves of the book, over the same span and by the same rule.
     *
     * Their own tests rather than trust by resemblance: three lists that must
     * agree about which days belong to a month is exactly the kind of agreement
     * that decays when one of them is corrected.
     */
    @Test
    fun `deliveries and spending narrow the same way`() {
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
    }
}
