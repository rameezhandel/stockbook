package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.BillText
import com.stockbook.core.text.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bill as something you can send somebody.
 *
 * Checked as text rather than by reading a screen, which is the point of having
 * it in one pure function: what the customer receives and what the owner is
 * looking at come from the same figures.
 */
class BillTextTests {

    private val english = Strings(AppLanguage.ENGLISH)
    private val riyal = Currency.SAR

    private fun store() = StockbookStore(InMemoryRepository())

    private fun text(store: StockbookStore, shopName: String = "Handel Hardware"): String {
        val bill = assertNotNull(store.bills.firstOrNull())
        return BillText.plainText(bill, shopName, riyal, english)
    }

    @Test
    fun `a paid bill reads as the document does`() {
        val store = store()
        val lock = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        val hinge = store.addProduct("Brass hinge", 100, 4.0, 7.5)
        store.saveBill(
            listOf(DraftLine(lock.uid, 2, 95.0), DraftLine(hinge.uid, 4, 7.5)),
            "Ahmed",
            null,
            invoiceNo = "A-1024"
        )

        val lines = text(store).lines()

        assertEquals("Handel Hardware", lines[0])
        assertEquals("A-1024", lines[1], "the number the owner wrote, not the app's counter")
        assertTrue(lines[3].contains("Ahmed"))
        assertTrue(lines.any { it.startsWith("Cisa lock") && it.contains("2 × SAR 95") && it.endsWith("SAR 190") })
        assertTrue(lines.any { it.startsWith("Brass hinge") && it.contains("4 × SAR 7.50") && it.endsWith("SAR 30") })
        assertTrue(lines.any { it == "Total: SAR 220" })
        assertTrue(lines.last() == english.paidInFullCash)
    }

    @Test
    fun `a part-paid bill says what is still owed`() {
        val store = store()
        val lock = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(lock.uid, 2, 95.0)), "Ahmed", 100.0)

        val text = text(store)

        assertTrue(text.contains(english.partPaidNote("SAR 100", "Ahmed", "SAR 90")), text)
    }

    @Test
    fun `a shop with no name has no letterhead`() {
        val store = store()
        val lock = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(lock.uid, 1, 95.0)), "Ahmed", null)

        // Not an empty first line: a blank where the shop's name goes reads as
        // something the app failed to fill in.
        assertEquals(english.billNumber(1), text(store, shopName = "  ").lines().first())
    }
}
