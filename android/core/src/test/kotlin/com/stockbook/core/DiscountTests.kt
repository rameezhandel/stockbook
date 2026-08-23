package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.money.Money
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A percentage off the whole bill.
 *
 * The rule everything here protects: **`total` is what was charged**, stored and
 * never recomputed, and the discount is a label saying how that figure was
 * arrived at. Nothing downstream was taught about discounts — a statement, a
 * balance, what a customer owes and a month's takings all read `total` and were
 * already right. These tests are what say that is still true.
 */
class DiscountTests {

    private fun store() = StockbookStore(InMemoryRepository())
    private val strings = Strings(AppLanguage.ENGLISH)

    /**
     * `paid = 0.0`, not null: in this app a null `paid` means **paid in full**,
     * so a bill written that way leaves the customer owing nothing and the tests
     * below about what is outstanding would all read zero and prove nothing.
     */
    private fun StockbookStore.billOf(amount: Double, percent: Double?) = saveBill(
        customer = "Ahmed Contracting",
        paid = 0.0,
        amount = amount,
        invoiceNo = "1024",
        discountPercent = percent
    )

    @Test
    fun `a percentage comes off the total`() {
        val bill = assertNotNull(store().billOf(250.0, 10.0))

        assertEquals(225.0, bill.total)
        assertEquals(250.0, bill.subtotal)
        assertEquals(25.0, bill.discountAmount)
        assertEquals(10.0, bill.discountPercent)
        assertTrue(bill.isDiscounted)
    }

    @Test
    fun `subtotal minus discount is the total, to the last halala`() {
        // The reason the money is stored rather than recomputed from the
        // percentage: 10% off 249.99 is 24.999, and a document whose three
        // figures do not add up is one nobody can check by hand.
        val bill = assertNotNull(store().billOf(249.99, 10.0))

        assertEquals(25.0, bill.discountAmount, "rounded once, when the bill was saved")
        assertEquals(224.99, bill.total)
        assertEquals(bill.subtotal, bill.total + bill.discountAmount!!)
    }

    @Test
    fun `no discount leaves both fields absent`() {
        // Absent rather than zero, so a shop that never discounts writes exactly
        // the bytes it always did — and the same bytes the iPhone writes.
        for (percent in listOf(null, 0.0, -5.0)) {
            val bill = assertNotNull(store().billOf(250.0, percent))
            assertEquals(250.0, bill.total)
            assertNull(bill.discountAmount, "percent = $percent")
            assertNull(bill.discountPercent, "percent = $percent")
            assertTrue(!bill.isDiscounted)
        }
    }

    @Test
    fun `a discount over a hundred per cent does not make a bill negative`() {
        val bill = assertNotNull(store().billOf(250.0, 500.0))

        assertEquals(0.0, bill.total)
        assertEquals(250.0, bill.discountAmount)
    }

    @Test
    fun `everything given away is still a bill`() {
        // Checked on the subtotal rather than the total: a line given away is a
        // line that left the shelf, and the shop should have the record.
        val store = store()
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)

        val bill = assertNotNull(
            store.saveBill(
                lines = listOf(DraftLine(product.uid, 2, 95.0)),
                customer = "Ahmed",
                paid = null,
                invoiceNo = "1",
                discountPercent = 100.0
            )
        )

        assertEquals(0.0, bill.total)
        assertEquals(8, store.product(product.uid)?.stock, "the shelf moved even though nothing was charged")
    }

    @Test
    fun `the discount applies to what the lines add up to`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 10, 60.0, 100.0)

        val bill = assertNotNull(
            store.saveBill(
                lines = listOf(DraftLine(product.uid, 3, 100.0)),
                customer = "Ahmed",
                paid = null,
                // Ignored where there are lines: their sum is the subtotal.
                amount = 9_999.0,
                invoiceNo = "1",
                discountPercent = 20.0
            )
        )

        assertEquals(300.0, bill.subtotal)
        assertEquals(240.0, bill.total)
    }

    @Test
    fun `a correction can add and remove a discount`() {
        val store = store()
        val bill = assertNotNull(store.billOf(250.0, null))

        store.updateBill(
            number = bill.number,
            customer = "Ahmed Contracting",
            paid = 0.0,
            amount = 250.0,
            createdAt = bill.createdAt,
            invoiceNo = "1024",
            discountPercent = 10.0
        )
        assertEquals(225.0, store.bills.single().total)

        store.updateBill(
            number = bill.number,
            customer = "Ahmed Contracting",
            paid = 0.0,
            amount = 250.0,
            createdAt = bill.createdAt,
            invoiceNo = "1024",
            discountPercent = null
        )
        assertEquals(250.0, store.bills.single().total, "removing it puts the figure back")
        assertNull(store.bills.single().discountAmount)
    }

    // --- What reads `total` and is therefore already right

    @Test
    fun `the customer owes the discounted figure`() {
        val store = store()
        store.billOf(250.0, 10.0)

        assertEquals(225.0, store.outstanding().second)
        assertEquals(225.0, assertNotNull(store.customer("ahmed contracting")).owed)
    }

    @Test
    fun `the month's takings are the discounted figure`() {
        val store = store()
        store.billOf(250.0, 10.0)

        assertEquals(225.0, store.soldIn(StatementPeriod.thisYear()))
    }

    @Test
    fun `the statement shows what was charged and says nothing about the discount`() {
        // Option 1, decided deliberately: the customer's statement carries the
        // figure they owe, not the arithmetic behind it. That is why this
        // feature needed no change to the activity table or to either PDF
        // renderer.
        val store = store()
        store.billOf(250.0, 10.0)
        val statement = assertNotNull(
            store.statementForCustomer("ahmed contracting", StatementPeriod.thisYear())
        )
        val document = StatementDocument.make(statement, store.settings, strings)

        val row = document.activityRows.single()
        // A bill is a charge, so the figure is in the invoice column and the
        // received column beside it is empty.
        assertEquals(Money.text(225.0, Currency.SAR), row.charge)
        assertEquals("", row.settled)
        assertTrue(!row.details.contains("%"), row.details)
        assertEquals(Money.text(225.0, Currency.SAR), document.closingValue)
    }

    // --- Getting to a new phone

    @Test
    fun `the discount survives export and import`() {
        val store = store()
        store.billOf(250.0, 10.0)

        val fresh = store()
        fresh.replaceEverything(BackupService.decode(BackupService.encode(store.makeBackupDocument())))

        val bill = fresh.bills.single()
        assertEquals(225.0, bill.total)
        assertEquals(25.0, bill.discountAmount)
        assertEquals(10.0, bill.discountPercent)
    }

    @Test
    fun `a reader that drops the discount still shows what is owed`() {
        // Why this did not bump the format version. The figure survives; only
        // the explanation of how it was reached is lost.
        val text = """
            {"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR",
             "bills":[{"number":1,"createdAt":"2026-08-13T12:00:00Z","total":225.0,"who":"Ahmed"}]}
        """.trimIndent()

        val document = BackupService.decode(text)

        assertEquals(225.0, document.bills.single().total)
        assertNull(document.bills.single().discountAmount)
    }
}
