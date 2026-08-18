package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The number on the paper, and the day the thing actually happened.
 *
 * Both sides of the book carry an invoice number now, and both are the same kind
 * of thing: a label the owner recognises, not a key. The app's own `Bill.number`
 * stays what identity is built on — these tests exist partly to pin that
 * difference down, because conflating the two is how a duplicate typed number
 * would start overwriting history.
 */
class InvoiceNumberTests {

    private val english = Strings(AppLanguage.ENGLISH)

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aProduct() = addProduct("Cisa lock", 50, 60.0, 95.0)

    // --- Sales

    @Test
    fun `a bill keeps the number written on the paper`() {
        val store = store()
        val product = store.aProduct()

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null, invoiceNo = "A-1024")
        )

        assertEquals("A-1024", bill.invoiceNo)
        assertEquals("A-1024", bill.reference(english), "the paper's number is what shows")
        assertEquals(1, bill.number, "and the app's own counter is untouched")
    }

    @Test
    fun `a bill with no paper behind it falls back to the app's own number`() {
        val store = store()
        val product = store.aProduct()

        val bill = assertNotNull(store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null))

        assertNull(bill.invoiceNo)
        assertEquals(english.billNumber(1), bill.reference(english))
    }

    @Test
    fun `a blank invoice number is absent, not an empty string`() {
        val store = store()
        val product = store.aProduct()

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null, invoiceNo = "   ")
        )

        assertNull(bill.invoiceNo, "otherwise \"has an invoice number\" is true for a bill with none")
        assertEquals(english.billNumber(1), bill.reference(english))
    }

    @Test
    fun `two bills may carry the same paper number without colliding`() {
        // Bill books get reused, and a shop with two of them will eventually
        // write 1024 twice. That must not touch identity, which is why the paper
        // number is a label and `number` is the key.
        val store = store()
        val product = store.aProduct()

        val first = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null, invoiceNo = "1024")
        )
        val second = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Sami", null, invoiceNo = "1024")
        )

        assertEquals(2, store.bills.size)
        assertEquals(listOf(2, 1), store.bills.map { it.number }, "distinct, and newest first")
        assertEquals("1024", first.invoiceNo)
        assertEquals("1024", second.invoiceNo)
    }

    // --- The day it happened

    @Test
    fun `a bill can be entered for the day it actually happened`() {
        val store = store()
        val product = store.aProduct()
        val yesterday = Instant.parse("2026-08-16T09:30:00Z")

        val bill = assertNotNull(
            store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null, createdAt = yesterday)
        )

        assertEquals(yesterday, bill.createdAt)
    }

    @Test
    fun `a backdated bill lands in the period it belongs to`() {
        // The reason the date matters at all: a statement is the document somebody
        // settles up against, and a bill entered at closing time under today's
        // date would appear in the wrong month at the turn of one.
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 2, 95.0)),
            "Ahmed",
            0.0,
            createdAt = Instant.parse("2026-07-20T09:30:00Z")
        )

        val july = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-07-10T00:00:00Z")))
        )
        val august = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )

        assertEquals(190.0, july.billed)
        assertEquals(0.0, august.billed)
        assertEquals(190.0, august.openingBalance, "and July's debt is carried forward, not lost")
    }

    // --- Deliveries

    @Test
    fun `a delivery keeps the supplier's invoice number`() {
        val store = store()
        val product = store.aProduct()
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))

        val purchase = assertNotNull(
            store.recordPurchase(product, supplier.key, quantity = 10, unitCost = 60.0, invoiceNo = "INV-88")
        )

        assertEquals("INV-88", purchase.invoiceNo)
    }

    // --- The file

    @Test
    fun `both numbers survive a backup round trip`() {
        val store = store()
        val product = store.aProduct()
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null, invoiceNo = "A-1024")
        store.recordPurchase(product, supplier.key, quantity = 5, unitCost = 60.0, invoiceNo = "INV-88")

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))

        // No version bump: a reader that drops these shows "Bill #1" where the
        // owner wrote 1024. A label lost, not a figure misread.
        assertEquals(2, document.version)

        val restored = store()
        restored.replaceEverything(document)
        assertEquals("A-1024", restored.bills.first().invoiceNo)
        assertEquals("INV-88", restored.purchases.first().invoiceNo)
    }
}
