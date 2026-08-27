package com.stockbook.core

import com.stockbook.core.model.LastPaid
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How long an account has gone without money moving.
 *
 * Home has always said who owes and how much, and never how long. What this
 * pins is that the clock is started and stopped by **money only** — the two
 * things that reduce a balance without anybody paying, a credit note and a
 * balance transfer, must leave it running. Getting that wrong tells the owner
 * they were paid by somebody who has not paid them since spring, and they stop
 * chasing it.
 */
class LastPaidTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private val now: Instant = Instant.parse("2026-08-27T09:00:00Z")
    private fun daysAgo(days: Long): Instant = now.minus(days, ChronoUnit.DAYS)

    private fun shopWithProduct(): Pair<StockbookStore, String> {
        val store = store()
        val lock = store.addProduct("Cisa lock", 100, 60.0, 95.0)
        return store to lock.uid
    }

    // --- The rule on its own

    @Test
    fun `with no date to count from it says nothing rather than guessing`() {
        assertNull(LastPaid.daysSince(lastPaidAt = null, since = null, now = now))
    }

    @Test
    fun `never paid counts from the day the trading started`() {
        assertEquals(40, LastPaid.daysSince(lastPaidAt = null, since = daysAgo(40), now = now))
    }

    @Test
    fun `a payment overrides the start date`() {
        assertEquals(
            5,
            LastPaid.daysSince(lastPaidAt = daysAgo(5), since = daysAgo(400), now = now),
            "the clock restarts when money comes in, whatever came before"
        )
    }

    @Test
    fun `a clock that has run backwards floors at zero`() {
        // A phone whose date was wrong when a bill was written. Nothing useful
        // to say, but "-3 days ago" on a counter screen is worse than "today".
        assertEquals(0, LastPaid.daysSince(lastPaidAt = now.plus(3, ChronoUnit.DAYS), since = null, now = now))
    }

    @Test
    fun `nothing is said until it is worth saying`() {
        assertFalse(LastPaid.worthSaying(null))
        assertFalse(LastPaid.worthSaying(0))
        assertFalse(LastPaid.worthSaying(LastPaid.WORTH_SAYING_AFTER_DAYS - 1))
        assertTrue(LastPaid.worthSaying(LastPaid.WORTH_SAYING_AFTER_DAYS))
    }

    // --- What the store works out from a real book

    @Test
    fun `paying at the counter counts as being paid`() {
        // A shop whose customers settle on the spot writes no payment rows at
        // all. Reading only those would call every one of them a non-payer.
        val (store, lock) = shopWithProduct()
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed", paid = 95.0, createdAt = daysAgo(3))

        val ahmed = assertNotNull(store.customer("ahmed"))
        assertTrue(ahmed.hasEverPaid)
        assertEquals(3, ahmed.quietDays(now))
    }

    @Test
    fun `a bill nobody paid anything on does not restart the clock`() {
        val (store, lock) = shopWithProduct()
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed", paid = 0.0, createdAt = daysAgo(60))

        val ahmed = assertNotNull(store.customer("ahmed"))
        assertFalse(ahmed.hasEverPaid, "nothing has come in from them yet")
        assertEquals(60, ahmed.quietDays(now), "counted from the bill, which is all there is")
    }

    @Test
    fun `the most recent money is the one that counts`() {
        val (store, lock) = shopWithProduct()
        store.saveBill(listOf(DraftLine(lock, 5, 95.0)), "Ahmed", paid = 0.0, createdAt = daysAgo(90))
        store.recordPayment("ahmed", 100.0, receivedAt = daysAgo(70))
        store.recordPayment("ahmed", 100.0, receivedAt = daysAgo(45))

        assertEquals(45, assertNotNull(store.customer("ahmed")).quietDays(now))
    }

    /**
     * The one that would have been wrong in the worst way.
     *
     * A credit note reduces what somebody owes and no money changes hands. If it
     * reset this clock, a customer written off in part would read as having just
     * paid — and the owner would stop chasing the rest.
     */
    @Test
    fun `a credit note is not a payment`() {
        val (store, lock) = shopWithProduct()
        store.saveBill(listOf(DraftLine(lock, 5, 95.0)), "Ahmed", paid = 0.0, createdAt = daysAgo(80))
        store.addCreditNote("ahmed", amount = 200.0, issuedAt = daysAgo(2))

        val ahmed = assertNotNull(store.customer("ahmed"))
        assertFalse(ahmed.hasEverPaid)
        assertEquals(80, ahmed.quietDays(now), "still eighty days without a coin")
    }

    /** The same rule, for the other thing that moves a balance without money. */
    @Test
    fun `a balance transfer is not a payment`() {
        val (store, lock) = shopWithProduct()
        store.addCustomer("Ahmed Riyadh")
        store.saveBill(listOf(DraftLine(lock, 5, 95.0)), "Ahmed Jeddah", paid = 0.0, createdAt = daysAgo(80))
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 200.0, movedAt = daysAgo(2))

        val jeddah = assertNotNull(store.customer("ahmed jeddah"))
        assertFalse(jeddah.hasEverPaid)
        assertEquals(80, jeddah.quietDays(now))

        // And the end it arrived at was not paid either — it was charged.
        val riyadh = assertNotNull(store.customer("ahmed riyadh"))
        assertFalse(riyadh.hasEverPaid)
    }

    @Test
    fun `an opening balance alone has no date and says nothing`() {
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 500.0)

        val ahmed = assertNotNull(store.customer("ahmed"))
        assertNull(ahmed.quietDays(now), "carried over from the paper book with no date to count from")
    }

    // --- The other side of the book

    @Test
    fun `a supplier is how long since the shop paid them`() {
        val (store, _) = shopWithProduct()
        val product = assertNotNull(store.products.firstOrNull())
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        store.recordPurchase(product, supplier.key, quantity = 10, unitCost = 40.0, paid = 0.0, createdAt = daysAgo(50))

        val unpaid = assertNotNull(store.supplier(supplier.key))
        assertFalse(unpaid.hasEverPaid)
        assertEquals(50, unpaid.quietDays(now))

        store.recordSupplierPayment(supplier.key, 200.0, paidAt = daysAgo(10))

        val paid = assertNotNull(store.supplier(supplier.key))
        assertTrue(paid.hasEverPaid)
        assertEquals(10, paid.quietDays(now))
    }
}
