package com.stockbook.core

import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.DayLedgerDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The day's balances as they print.
 *
 * The arithmetic is `DayLedger`'s and tested there; what this pins is the
 * *page* — which columns exist, what they are called, and which cells are left
 * empty. It matters because the two apps draw the PDF with entirely different
 * graphics code, and this structure is the only thing making them agree.
 */
class DayLedgerDocumentTests {

    private val english = Strings(AppLanguage.ENGLISH)
    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private fun at(day: Int): Instant = Instant.parse("2026-08-%02dT09:00:00Z".format(day))

    private fun shop(): Pair<StockbookStore, String> {
        val store = StockbookStore(InMemoryRepository())
        store.setOwnerName("Tayba Trading")
        val lock = store.addProduct("Cisa lock", 500, 60.0, 95.0)
        return store to lock.uid
    }

    private fun document(store: StockbookStore, onlyMoved: Boolean = false): DayLedgerDocument {
        val ledger = store.dayLedger(at(12), zone).let { if (onlyMoved) it.movedOnly() else it }
        return DayLedgerDocument.forDay(ledger, store.settings, english, onlyMoved)
    }

    @Test
    fun `the five columns are named in the order they are drawn`() {
        val (store, _) = shop()
        store.addCustomer("Ahmed")

        assertEquals(
            listOf("Customers", "Invoice", "Received", "Old", "Current"),
            document(store).columnHeadings
        )
    }

    @Test
    fun `a quiet row carries its balances and nothing else`() {
        val (store, lock) = shop()
        store.addCustomer("Fatima")
        store.saveBill(listOf(DraftLine(lock, 4, 95.0)), "Fatima", paid = 0.0, createdAt = at(10))

        val row = assertNotNull(document(store).rows.firstOrNull { it.name == "Fatima" })

        // Empty rather than "0.00": an empty cell says nothing happened here, and
        // a zero is a figure somebody may go looking for.
        assertEquals("", row.invoiced)
        assertEquals("", row.received)
        assertEquals("380", row.oldBalance)
        assertEquals("380", row.currentBalance)
        assertNull(row.note)
    }

    @Test
    fun `a busy row shows both movement columns`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))

        val row = assertNotNull(document(store).rows.firstOrNull { it.name == "Ahmed" })

        assertEquals("950", row.invoiced)
        assertEquals("400", row.received)
        assertEquals("0", row.oldBalance)
        assertEquals("550", row.currentBalance)
    }

    /** What the five columns cannot hold has to be said in words, or the row does not add up. */
    @Test
    fun `a credit note is spelled out under the name`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        store.addCreditNote("ahmed", amount = 150.0, issuedAt = at(12))

        val row = assertNotNull(document(store).rows.firstOrNull { it.name == "Ahmed" })

        assertEquals("", row.received, "no money arrived")
        assertTrue(assertNotNull(row.note).contains("Credited"))
        assertEquals("950", row.oldBalance)
        assertEquals("800", row.currentBalance)
    }

    /**
     * The page has to say it was narrowed.
     *
     * A printed roll-call and a printed selection look identical on paper and
     * their totals differ. Without this line the owner files a sheet whose
     * figures do not tie to the shop's own and has no way to tell why.
     */
    @Test
    fun `a narrowed page says so, and a whole one says nothing`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima", openingBalance = 2000.0)
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))

        assertNull(document(store).filterNote)

        val narrowed = document(store, onlyMoved = true)
        assertEquals("Only accounts that moved on this day", narrowed.filterNote)
        assertEquals(1, narrowed.rows.size)
    }

    /** The figure under a column is the column added up, on a narrowed page too. */
    @Test
    fun `the totals are the totals of the rows printed`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.addCustomer("Fatima", openingBalance = 2000.0)
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 400.0, createdAt = at(12))

        assertEquals(listOf("950", "400", "2,000", "2,550"), document(store).totals)
        assertEquals(
            listOf("950", "400", "0", "550"),
            document(store, onlyMoved = true).totals,
            "Fatima is not on the page, so her balance is not in the total either"
        )
    }

    @Test
    fun `a shop with nobody on the book prints a line saying so`() {
        val (store, _) = shop()

        val document = document(store)

        assertTrue(document.isEmpty)
        assertEquals("No customers yet", document.emptyLine)
    }
}
