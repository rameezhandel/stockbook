package com.stockbook.core

import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Statement
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The date arithmetic behind "this month" and "this year".
 *
 * Pinned to a fixed UTC zone rather than the device's, because a month boundary
 * is exactly the kind of thing that passes in London and fails in Riyadh — and
 * this app is for a shop in Saudi Arabia read on a phone that may be set to
 * anything.
 */
class StatementPeriodTests {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
        LocalDateTime.of(year, month, day, hour, 0).atZone(utc).toInstant()

    @Test
    fun `a month runs from its first instant to the next month's exclusive`() {
        val range = StatementPeriod.Month(at(2026, 8, 17)).range(utc)

        assertEquals(at(2026, 8, 1, 0), range.start)
        assertEquals(at(2026, 9, 1, 0), range.end)
        assertTrue(at(2026, 8, 1, 0) in range)
        assertTrue(at(2026, 8, 31, 23) in range)
        // The load-bearing one. Midnight on the 1st belongs to September, and if
        // it belonged to both, a bill written then would be counted twice.
        assertFalse(at(2026, 9, 1, 0) in range)
        assertFalse(at(2026, 7, 31, 23) in range)
    }

    @Test
    fun `a year runs January to January`() {
        val range = StatementPeriod.Year(at(2026, 8, 17)).range(utc)

        assertEquals(at(2026, 1, 1, 0), range.start)
        assertEquals(at(2027, 1, 1, 0), range.end)
        assertTrue(at(2026, 12, 31, 23) in range)
        assertFalse(at(2027, 1, 1, 0) in range)
    }

    @Test
    fun `a chosen range covers both end days whole`() {
        val range = StatementPeriod.Custom(at(2026, 8, 3, 15), at(2026, 8, 5, 9)).range(utc)

        // Picked mid-afternoon on the 3rd, but the owner means the 3rd.
        assertEquals(at(2026, 8, 3, 0), range.start)
        assertTrue(at(2026, 8, 3, 0) in range)
        // And the whole of the 5th, not up to 9am on it.
        assertTrue(at(2026, 8, 5, 23) in range)
        assertFalse(at(2026, 8, 6, 0) in range)
    }

    @Test
    fun `a range picked backwards is still the range they meant`() {
        val forwards = StatementPeriod.Custom(at(2026, 8, 3), at(2026, 8, 5)).range(utc)
        val backwards = StatementPeriod.Custom(at(2026, 8, 5), at(2026, 8, 3)).range(utc)

        assertEquals(forwards, backwards)
    }

    @Test
    fun `last month is last month including from the first`() {
        val range = StatementPeriod.lastMonth(at(2026, 1, 1, 3), utc).range(utc)

        // From the small hours of New Year's Day, "last month" is December.
        assertEquals(at(2025, 12, 1, 0), range.start)
        assertEquals(at(2026, 1, 1, 0), range.end)
    }

    @Test
    fun `a month is found the same way whichever day of it you are on`() {
        val early = StatementPeriod.Month(at(2026, 8, 1, 0)).range(utc)
        val late = StatementPeriod.Month(at(2026, 8, 31, 23)).range(utc)
        assertEquals(early, late)
    }
}

/** What a statement says, which is the whole feature. */
class StatementTests {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int): Instant =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(utc).toInstant()

    private val ahmed = Customer(
        name = "Ahmed Contracting",
        key = "ahmed contracting",
        billCount = 0,
        total = 0.0,
        owed = 0.0,
        isOnRoster = true
    )

    private fun bill(
        number: Int,
        on: Instant,
        total: Double,
        paid: Double? = null
    ) = Bill(
        number = number,
        lines = listOf(BillLine(productUid = null, name = "Cisa lock", qty = 1, price = total)),
        total = total,
        paid = paid,
        who = "Ahmed Contracting",
        createdAt = on
    )

    private fun payment(amount: Double, on: Instant) =
        Payment(customerKey = "ahmed contracting", amount = amount, receivedAt = on)

    private fun august(bills: List<Bill>, payments: List<Payment> = emptyList()): Statement =
        Statement.make(
            customer = ahmed,
            bills = bills,
            payments = payments,
            period = StatementPeriod.Month(at(2026, 8, 10)),
            zone = utc
        )

    @Test
    fun `a bill paid in full leaves nothing owed`() {
        val statement = august(listOf(bill(1, at(2026, 8, 4), 900.0)))

        assertEquals(900.0, statement.billed)
        assertEquals(900.0, statement.received)
        assertEquals(0.0, statement.closingBalance)
        assertEquals(0.0, statement.openingBalance)
    }

    @Test
    fun `a part payment at the counter leaves the rest owed`() {
        val statement = august(listOf(bill(1, at(2026, 8, 4), 900.0, paid = 500.0)))

        assertEquals(900.0, statement.billed)
        assertEquals(500.0, statement.received)
        assertEquals(400.0, statement.closingBalance)
    }

    /** The reason payments exist. Without them this figure could never come down. */
    @Test
    fun `a payment received later clears the balance`() {
        val statement = august(
            listOf(bill(1, at(2026, 8, 4), 900.0, paid = 500.0)),
            listOf(payment(400.0, at(2026, 8, 20)))
        )

        assertEquals(900.0, statement.received)
        assertEquals(0.0, statement.closingBalance)
        assertEquals(2, statement.entries.size)
    }

    /** The difference between a statement and a filtered list of bills. */
    @Test
    fun `what was owed before the period is brought forward`() {
        val statement = august(
            listOf(
                bill(1, at(2026, 3, 2), 900.0, paid = 0.0),
                bill(2, at(2026, 8, 4), 100.0, paid = 0.0)
            )
        )

        assertEquals(900.0, statement.openingBalance)
        assertEquals(100.0, statement.billed, "March is not in August's figures")
        assertEquals(1000.0, statement.closingBalance)
        val numbers = statement.entries.mapNotNull { (it as? Statement.Entry.ForBill)?.bill?.number }
        assertEquals(listOf(2), numbers)
    }

    @Test
    fun `a payment before the period comes off the brought-forward figure`() {
        val statement = august(
            listOf(bill(1, at(2026, 3, 2), 900.0, paid = 0.0)),
            listOf(payment(400.0, at(2026, 7, 30)))
        )

        assertEquals(500.0, statement.openingBalance)
        assertEquals(500.0, statement.closingBalance)
        assertTrue(statement.isEmpty, "nothing happened in August")
    }

    @Test
    fun `entries read downwards oldest first`() {
        val statement = august(
            listOf(bill(2, at(2026, 8, 20), 100.0), bill(1, at(2026, 8, 4), 200.0)),
            listOf(payment(50.0, at(2026, 8, 10)))
        )

        val dates = statement.entries.map { it.date }
        // Every other list in this app is newest-first. A statement is a
        // document, and a document reads down the page.
        assertEquals(dates.sorted(), dates)
    }

    /**
     * An invariant, not an example: the two are computed by different routes —
     * one accumulates per entry, the other is opening + billed − received — and a
     * statement whose column disagrees with its own total is worthless.
     */
    @Test
    fun `the running balance lands exactly on the closing balance`() {
        val statement = august(
            listOf(
                bill(1, at(2026, 3, 2), 900.0, paid = 0.0),
                bill(2, at(2026, 8, 4), 250.0, paid = 100.0),
                bill(3, at(2026, 8, 6), 80.0),
                bill(4, at(2026, 8, 9), 400.0, paid = 0.0)
            ),
            listOf(payment(200.0, at(2026, 8, 12)), payment(25.0, at(2026, 8, 28)))
        )

        assertEquals(statement.entries.size, statement.runningBalances.size)
        assertEquals(statement.closingBalance, statement.runningBalances.last())
    }

    @Test
    fun `paying more than owed shows as an advance rather than a wrong total`() {
        val statement = august(
            listOf(bill(1, at(2026, 8, 4), 100.0, paid = 0.0)),
            listOf(payment(250.0, at(2026, 8, 20)))
        )

        assertEquals(-150.0, statement.closingBalance)
        // And the customer row says it in words rather than showing a minus sign.
        val paidAhead = ahmed.copy(billCount = 1, total = 100.0, owed = -150.0)
        val meta = paidAhead.meta(Currency.default, Strings(AppLanguage.ENGLISH))
        assertTrue(meta.contains("in advance"), meta)
    }

    @Test
    fun `a customer with no history at all yields an empty statement`() {
        val statement = august(emptyList())

        assertTrue(statement.isEmpty)
        assertEquals(0.0, statement.openingBalance)
        assertEquals(0.0, statement.closingBalance)
        assertTrue(statement.runningBalances.isEmpty())
    }
}
