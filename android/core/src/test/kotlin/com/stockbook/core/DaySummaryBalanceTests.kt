package com.stockbook.core

import com.stockbook.core.model.Customer
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.DaySummaryDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Where each account stood when the day closed, said under the row it appears on.
 *
 * Two claims are pinned here and both matter more than they look. The first is
 * that the figure is **that day's**, not today's: the page is usually opened on
 * a date in the past, and a balance quietly rolled forward to now would be the
 * one number on it a reader could not reconcile against anything.
 *
 * The second is that it is **the statement's figure**. A customer holding a
 * statement and an owner reading this page have to be looking at the same money,
 * and the only way to be sure of that is for there to be one calculation.
 */
class DaySummaryBalanceTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun on(day: Int, hour: Int = 9): Instant =
        Instant.parse("2026-08-%02dT%02d:00:00Z".format(day, hour))

    private fun store() = StockbookStore(InMemoryRepository()).also { it.setOwnerName("Al Salam Hardware") }

    private fun StockbookStore.page(day: Instant) =
        DaySummaryDocument.forDay(dayBook(day, zone), settings, strings)

    private fun DaySummaryDocument.row(name: String): DaySummaryDocument.Row =
        assertNotNull(sections.flatMap { it.rows }.firstOrNull { it.name == name }, "no row for $name")

    @Test
    fun `a bill on credit carries what the customer owes at the end of that day`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = on(20))
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, createdAt = on(22))

        val balance = assertNotNull(store.page(on(22)).row("Ahmed").balance)

        assertEquals("Closing balance", balance.label)
        assertEquals("SAR 1,500", balance.value, "the twentieth's thousand and today's five hundred")
    }

    /**
     * The figure on a past day's page is that day's, not today's.
     *
     * Opened on the twentieth, after a bill on the twenty-second exists, the row
     * has to say a thousand — the five hundred had not happened yet. Rolling the
     * balance forward is the mistake that makes a day page impossible to
     * reconcile against the cash box it was printed for.
     */
    @Test
    fun `a past day does not carry a balance from after it`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = on(20))
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, createdAt = on(22))

        val balance = assertNotNull(store.page(on(20)).row("Ahmed").balance)

        assertEquals("SAR 1,000", balance.value)
    }

    @Test
    fun `a receipt shows what is left after it`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = on(20))
        store.recordPayment(Customer.key("Ahmed"), 300.0, receivedAt = on(22))

        val balance = assertNotNull(store.page(on(22)).row("Ahmed").balance)

        assertEquals("SAR 700", balance.value)
    }

    /**
     * The same figure the statement prints. Not equal to it — the same
     * calculation, which is the only way two documents about one account cannot
     * drift apart.
     */
    @Test
    fun `the balance is the statement's closing balance for that span`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, createdAt = on(20))
        store.recordPayment(Customer.key("Ahmed"), 250.0, receivedAt = on(22))

        val statement = assertNotNull(
            store.statementForCustomer(
                "ahmed",
                com.stockbook.core.model.StatementPeriod.Custom(on(1), on(22))
            )
        )
        val balance = assertNotNull(store.page(on(22)).row("Ahmed").balance)

        assertEquals(com.stockbook.core.money.Money.text(statement.closingBalance, store.settings.currency), balance.value)
    }

    /** A delivery says what the shop owes that supplier, which is the mirror of it. */
    @Test
    fun `the supplier side carries a balance too`() {
        val store = store()
        store.addSupplier("Gulf Traders")
        store.recordPurchase(
            lines = emptyList(),
            supplierKey = "gulf traders",
            paid = 0.0,
            amount = 800.0,
            createdAt = on(22)
        )

        val balance = assertNotNull(store.page(on(22)).row("Gulf Traders").balance)

        assertEquals("SAR 800", balance.value)
    }

    /**
     * The owner's own spending is joined to nobody, so there is nothing for a
     * balance to be *of*. A line reading "Closing balance —" under Petrol
     * would invite the reader to wonder whose.
     */
    @Test
    fun `an expense has no balance because it has no account`() {
        val store = store()
        store.addExpense(30.0, "Petrol", spentAt = on(22))

        assertNull(store.page(on(22)).row("Petrol").balance)
    }

    /**
     * Three bills to one customer are three records of what was sold and one
     * answer to what they owe. Every row says it, so the figure is not one the
     * reader has to hunt for at the bottom of a run.
     */
    @Test
    fun `every row a customer appears on says the same closing figure`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 100.0, createdAt = on(22, hour = 9))
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 200.0, createdAt = on(22, hour = 11))

        val rows = store.page(on(22)).sections.flatMap { it.rows }.filter { it.name == "Ahmed" }

        assertEquals(2, rows.size)
        assertEquals(listOf("SAR 300", "SAR 300"), rows.map { assertNotNull(it.balance).value })
    }
}
