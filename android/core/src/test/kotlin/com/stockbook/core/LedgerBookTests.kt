package com.stockbook.core

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
 * Every customer's whole history, one statement each.
 *
 * The book the owner prints once and files. What this pins is that it leaves
 * **nothing and nobody out**: every name on the book gets a statement, and every
 * statement runs from the first record in the shop to now. A ledger book that
 * quietly skipped an account, or started after somebody's oldest bill, is a book
 * that cannot be reconciled against the paper one it replaces — and the reader
 * has no way to notice.
 */
class LedgerBookTests {

    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private fun at(month: Int, day: Int): Instant =
        Instant.parse("2026-%02d-%02dT09:00:00Z".format(month, day))

    private fun shop(): Pair<StockbookStore, String> {
        val store = StockbookStore(InMemoryRepository())
        val lock = store.addProduct("Cisa lock", 500, 60.0, 95.0)
        return store to lock.uid
    }

    private fun List<com.stockbook.core.model.Statement>.forName(name: String) =
        assertNotNull(firstOrNull { it.party.name == name }, "no page for $name")

    @Test
    fun `every customer gets a page, including the ones with no history`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.addCustomer("Khalid")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(8, 12))

        val book = store.ledgerBook(zone)

        assertEquals(3, book.size, "a name with no bills is still an account in the book")
        assertEquals(listOf("Ahmed", "Fatima", "Khalid"), book.map { it.party.name }, "in name order")
        assertTrue(book.forName("Fatima").isEmpty, "nothing to show, but the page exists")
    }

    /**
     * The range has to reach back past the oldest record in the shop.
     *
     * A book that started at the beginning of this month would put every older
     * bill into an opening figure and show none of them — which looks like a
     * complete history and is not one.
     */
    @Test
    fun `the period reaches back to the first record and forward to now`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(1, 3))
        store.saveBill(listOf(DraftLine(lock, 1, 95.0)), "Ahmed", paid = 0.0, createdAt = at(8, 20))

        val ahmed = store.ledgerBook(zone).forName("Ahmed")

        assertEquals(2, ahmed.entries.size, "both bills are entries, not an opening figure")
        assertEquals(0.0, ahmed.openingBalance, "nothing predates the first bill")
        assertEquals(285.0, ahmed.closingBalance)
    }

    @Test
    fun `a balance carried over from the paper book opens the page`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed", openingBalance = 1000.0)
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(8, 12))

        val ahmed = store.ledgerBook(zone).forName("Ahmed")

        // The carried figure has no date of its own, so it cannot be an entry —
        // it belongs in the opening balance, which is where a statement puts
        // everything from before the range.
        assertEquals(1000.0, ahmed.openingBalance)
        assertEquals(1, ahmed.entries.size)
        assertEquals(1190.0, ahmed.closingBalance)
    }

    @Test
    fun `each page carries only that customer's history`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima")
        store.saveBill(listOf(DraftLine(lock, 2, 95.0)), "Ahmed", paid = 0.0, createdAt = at(8, 12))
        store.saveBill(listOf(DraftLine(lock, 4, 95.0)), "Fatima", paid = 0.0, createdAt = at(8, 13))
        store.recordPayment("fatima", 100.0, receivedAt = at(8, 14))

        val book = store.ledgerBook(zone)

        assertEquals(1, book.forName("Ahmed").entries.size)
        assertEquals(190.0, book.forName("Ahmed").closingBalance)
        assertEquals(2, book.forName("Fatima").entries.size, "her bill and her payment")
        assertEquals(280.0, book.forName("Fatima").closingBalance)
    }

    @Test
    fun `credit notes and moved balances are on the page too`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed Jeddah")
        store.addCustomer("Ahmed Riyadh")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed Jeddah", paid = 0.0, createdAt = at(8, 10))
        store.addCreditNote("ahmed jeddah", amount = 150.0, issuedAt = at(8, 11))
        store.transferBalance("ahmed jeddah", "ahmed riyadh", 300.0, movedAt = at(8, 12))

        val book = store.ledgerBook(zone)
        val jeddah = book.forName("Ahmed Jeddah")

        assertEquals(3, jeddah.entries.size, "the bill, the note and the transfer out")
        assertEquals(500.0, jeddah.closingBalance, "950 less 150 credited less 300 moved")

        val riyadh = book.forName("Ahmed Riyadh")
        assertEquals(1, riyadh.entries.size, "the transfer arriving")
        assertEquals(300.0, riyadh.closingBalance)
    }

    /**
     * The whole book has to tie to what the shop is owed.
     *
     * This is the one figure a reader can check without adding up a hundred
     * pages, and if it disagrees the book is worthless.
     */
    @Test
    fun `the closing balances add up to what the shop is owed`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed", openingBalance = 500.0)
        store.addCustomer("Fatima")
        store.addCustomer("Khalid")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(8, 12))
        store.saveBill(listOf(DraftLine(lock, 4, 95.0)), "Fatima", paid = 0.0, createdAt = at(8, 13))
        store.recordPayment("fatima", 80.0, receivedAt = at(8, 14))

        val book = store.ledgerBook(zone)
        val owed = store.customers().sumOf { it.owed }

        assertEquals(owed, book.sumOf { it.closingBalance })
    }

    @Test
    fun `a shop with nobody on the book produces no pages`() {
        val (store, _) = shop()

        assertTrue(store.ledgerBook(zone).isEmpty())
    }
}
