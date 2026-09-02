package com.stockbook.core

import com.stockbook.core.store.DayEntryKind
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding one piece of paper, when the owner knows what is printed on it and
 * nothing else.
 *
 * The question the four lists in the book cannot answer. Each of them narrows to
 * a span, and somebody holding receipt 008455 does not know which month it was
 * written in — that is the reason they are looking it up.
 */
class SearchTests {

    private fun on(month: Int, day: Int): Instant =
        Instant.parse("2026-%02d-%02dT09:00:00Z".format(month, day))

    private fun store() = StockbookStore(InMemoryRepository())

    /** A shop with one of each of the six kinds in it. */
    private fun shop(): StockbookStore {
        val store = store()
        store.addCustomer("Ahmed Al Harbi")
        store.addSupplier("Gulf Traders")
        store.saveBill(
            customer = "Ahmed Al Harbi",
            paid = null,
            amount = 500.0,
            createdAt = on(3, 2),
            invoiceNo = "1207"
        )
        store.recordPayment("ahmed al harbi", 300.0, receivedAt = on(4, 9), paymentNo = "008455")
        store.addCreditNote("ahmed al harbi", amount = 120.0, noteNo = "CN-14", issuedAt = on(5, 1))
        store.recordPurchase(
            emptyList(),
            "gulf traders",
            amount = 800.0,
            createdAt = on(6, 3),
            invoiceNo = "GT-902"
        )
        store.recordSupplierPayment("gulf traders", 250.0, paidAt = on(7, 7), paymentNo = "V-31")
        store.addExpense(90.0, "Petrol", spentAt = on(8, 11))
        return store
    }

    @Test
    fun `a receipt number finds the receipt`() {
        val hits = shop().search("008455")

        assertEquals(1, hits.size)
        assertEquals(DayEntryKind.PAYMENT, hits.single().kind)
        assertEquals(300.0, hits.single().amount)
        assertEquals("Ahmed Al Harbi", hits.single().who, "named, not keyed")
    }

    /**
     * The leading zeros are decoration on the slip, not part of the number.
     * `InvoiceNo` already settled that for the duplicate check, and search has to
     * agree with it or the two disagree about what "the same number" means.
     */
    @Test
    fun `a number is found without its leading zeros`() {
        assertEquals(1, shop().search("8455").size)
    }

    /** Every kind is reachable, or the search is one nobody can trust. */
    @Test
    fun `all six kinds turn up`() {
        val store = shop()

        assertEquals(DayEntryKind.BILL, store.search("1207").single().kind)
        assertEquals(DayEntryKind.PAYMENT, store.search("008455").single().kind)
        assertEquals(DayEntryKind.CREDIT_NOTE, store.search("CN-14").single().kind)
        assertEquals(DayEntryKind.PURCHASE, store.search("GT-902").single().kind)
        assertEquals(DayEntryKind.SUPPLIER_PAYMENT, store.search("V-31").single().kind)
        assertEquals(DayEntryKind.EXPENSE, store.search("petrol").single().kind)
    }

    /** A name pulls up everything filed under it, whichever list it lives on. */
    @Test
    fun `a name finds that person's whole trail`() {
        val kinds = shop().search("ahmed").map { it.kind }.toSet()

        assertEquals(setOf(DayEntryKind.BILL, DayEntryKind.PAYMENT, DayEntryKind.CREDIT_NOTE), kinds)
    }

    /** Case is not something the owner should have to get right. */
    @Test
    fun `matching ignores case on both sides`() {
        assertEquals(1, shop().search("gt-902").size)
        assertEquals(1, shop().search("PETROL").size)
    }

    @Test
    fun `an amount finds what it was for`() {
        val hits = shop().search("800")

        assertEquals(DayEntryKind.PURCHASE, hits.single().kind)
    }

    /**
     * The whole point of the ordering.
     *
     * A shop that sold something for 8,455 riyals and also wrote receipt 008455
     * must still be handed the receipt first — finding it third is the search
     * failing at the one job it was added for.
     */
    @Test
    fun `an exact number beats an amount that happens to match`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = null, amount = 8455.0, createdAt = on(9, 1))
        store.recordPayment("ahmed", 300.0, receivedAt = on(3, 1), paymentNo = "008455")

        val hits = store.search("008455")

        assertEquals(2, hits.size, "both matched")
        assertEquals(DayEntryKind.PAYMENT, hits.first().kind, "the slip with that number leads")
    }

    /** After the exact match, the most recent — which is how the lists read too. */
    @Test
    fun `the rest come newest first`() {
        val dates = shop().search("a").map { it.at }

        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun `nothing typed finds nothing, rather than everything`() {
        assertTrue(shop().search("").isEmpty())
        assertTrue(shop().search("   ").isEmpty())
    }

    @Test
    fun `a query nothing answers to comes back empty`() {
        assertTrue(shop().search("zzzz").isEmpty())
    }

    /**
     * A single letter must not build a page per record. The cap is what stops a
     * shop with four thousand bills drawing all of them on a keystroke.
     */
    @Test
    fun `the results are capped`() {
        val store = store()
        store.addCustomer("Ahmed")
        repeat(60) { store.saveBill(customer = "Ahmed", paid = null, amount = 10.0, createdAt = on(3, 2)) }

        assertEquals(40, store.search("ahmed").size)
        assertEquals(5, store.search("ahmed", limit = 5).size)
    }
}
