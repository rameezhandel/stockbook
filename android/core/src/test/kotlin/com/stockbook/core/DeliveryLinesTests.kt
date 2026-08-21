package com.stockbook.core

import com.stockbook.core.model.Purchase
import com.stockbook.core.model.PurchaseLine
import com.stockbook.core.store.DraftPurchaseLine
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
import kotlin.test.assertTrue

/**
 * A delivery note with more than one product on it.
 *
 * The rule underneath all of this: **one number, one piece of paper, as many
 * lines as the paper has.** The screen refuses a repeated invoice number across
 * the whole book, which is why a five-line delivery could not be entered as five
 * records — and until `Purchase` had lines, it could not be entered at all.
 *
 * The shelf is the part that has to be exactly right. Recording a delivery puts
 * every line on, correcting one moves the shelf by the difference, and removing
 * one takes every line back off. A line that is wrong here is stock the shop
 * does not have.
 */
class DeliveryLinesTests {

    private val strings = Strings(AppLanguage.ENGLISH)
    private val day: Instant = Instant.parse("2026-08-13T09:00:00Z")

    private fun store() = StockbookStore(InMemoryRepository())

    @Test
    fun `every line lands on the shelf`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 10, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(
                lines = listOf(
                    DraftPurchaseLine(locks.uid, 10, 62.0),
                    DraftPurchaseLine(keys.uid, 100, 2.5)
                ),
                supplierKey = supplier.key,
                paid = 0.0,
                invoiceNo = "8842"
            )
        )

        assertEquals(12, store.product(locks.uid)?.stock)
        assertEquals(110, store.product(keys.uid)?.stock)
        assertEquals(870.0, purchase.total, "10 × 62 + 100 × 2.5")
        assertEquals(2, purchase.items.size)
        assertTrue(purchase.isItemised)
    }

    @Test
    fun `each line sets its own product's cost`() {
        // Latest paid, not a weighted average — the same rule a single-product
        // delivery always followed, now applied per line.
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 10, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        store.recordPurchase(
            lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
            supplierKey = supplier.key,
            invoiceNo = "8842"
        )

        assertEquals(62.0, store.product(locks.uid)?.cost)
        assertEquals(2.5, store.product(keys.uid)?.cost)
    }

    @Test
    fun `a line with no cost keeps what the product already cost`() {
        // The sheet leaves the box empty where the price has not moved since last
        // time. Reading that as free would rewrite the product's cost to nothing
        // and make every margin in the shop look enormous.
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 5, 0.0)),
                supplierKey = supplier.key,
                invoiceNo = "8842"
            )
        )

        assertEquals(60.0, store.product(locks.uid)?.cost)
        assertEquals(300.0, purchase.total)
    }

    @Test
    fun `the same product twice on one note counts twice`() {
        // Two boxes at two prices is an ordinary thing on a supplier's paper. The
        // shelf is re-read between lines, so the second does not overwrite a
        // count captured before the first.
        val store = store()
        val locks = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        store.recordPurchase(
            lines = listOf(DraftPurchaseLine(locks.uid, 6, 60.0), DraftPurchaseLine(locks.uid, 4, 65.0)),
            supplierKey = supplier.key,
            invoiceNo = "8842"
        )

        assertEquals(10, store.product(locks.uid)?.stock, "six then four, not four")
        assertEquals(65.0, store.product(locks.uid)?.cost, "the last line sets it")
    }

    @Test
    fun `a correction moves the shelf by the difference`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 10, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
                supplierKey = supplier.key,
                invoiceNo = "8842"
            )
        )

        // The paper said 8 locks, not 10, and the keys were never on it.
        store.updatePurchase(
            id = purchase.id,
            lines = listOf(DraftPurchaseLine(locks.uid, 8, 62.0)),
            supplierKey = supplier.key,
            createdAt = day,
            invoiceNo = "8842"
        )

        assertEquals(10, store.product(locks.uid)?.stock, "2 + 8, not 2 + 10 + 8")
        assertEquals(10, store.product(keys.uid)?.stock, "the dropped line gave its stock back")
        assertEquals(496.0, store.purchases.single().total)
    }

    @Test
    fun `removing a delivery takes every line back off`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 10, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
                supplierKey = supplier.key,
                invoiceNo = "8842"
            )
        )
        store.deletePurchase(purchase.id)

        assertEquals(2, store.product(locks.uid)?.stock)
        assertEquals(10, store.product(keys.uid)?.stock)
        assertTrue(store.purchases.isEmpty())
    }

    @Test
    fun `what a supplier is owed is the whole note, once`() {
        // The reason this feature exists. Five lines under one number used to be
        // five records, each carrying part of one payment — an apportionment
        // the owner invented and the supplier would not recognise.
        val store = store()
        val locks = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 0, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        store.recordPurchase(
            lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
            supplierKey = supplier.key,
            paid = 500.0,
            invoiceNo = "8842"
        )

        assertEquals(370.0, store.purchases.single().balance, "870 owed, 500 handed over")
        assertEquals(370.0, store.payable().second)
    }

    @Test
    fun `one number covers one delivery, however many lines`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 0, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
                supplierKey = supplier.key,
                invoiceNo = "8842"
            )
        )

        // Still one record under 8842, so the screen's clash check still finds
        // exactly one thing and the owner is not told the paper is a duplicate
        // of itself.
        assertEquals(1, store.purchases.size)
        assertEquals(purchase.id, store.purchaseWithInvoiceNo("8842")?.id)
        assertNull(store.purchaseWithInvoiceNo("8842", exceptId = purchase.id))
    }

    @Test
    fun `a bill with no stock on it is still a bill with no stock on it`() {
        val store = store()
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordSupplierBill(supplier.key, amount = 800.0, paid = 0.0, invoiceNo = "INV-88")
        )

        assertTrue(purchase.items.isEmpty())
        assertTrue(!purchase.isItemised)
        assertEquals(800.0, purchase.total)
    }

    // --- What a row and a statement call it

    @Test
    fun `the summary names one product and counts several`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 0, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val one = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0)),
                supplierKey = supplier.key,
                invoiceNo = "1"
            )
        )
        val many = assertNotNull(
            store.recordPurchase(
                lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
                supplierKey = supplier.key,
                invoiceNo = "2"
            )
        )
        val figure = assertNotNull(store.recordSupplierBill(supplier.key, amount = 800.0, invoiceNo = "3"))

        assertEquals("Cisa lock", one.summary(strings))
        assertEquals("2 items", many.summary(strings))
        assertEquals(strings.purchaseLabel, figure.summary(strings))
    }

    // --- Deliveries recorded when a delivery held one product

    @Test
    fun `an older delivery still says what arrived`() {
        val old = Purchase(
            supplierKey = "al-riyadh hardware",
            total = 600.0,
            productUid = "abc",
            name = "Cisa lock",
            qty = 10,
            unitCost = 60.0
        )

        assertEquals(listOf(PurchaseLine("abc", "Cisa lock", 10, 60.0)), old.items)
        assertTrue(old.isItemised)
        assertEquals("Cisa lock", old.summary(strings))
    }

    @Test
    fun `correcting an older delivery rewrites it into the new shape`() {
        // Otherwise the four old fields would sit under the new lines saying
        // something different, waiting for something to believe them.
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))
        val purchase = assertNotNull(
            store.recordPurchase(locks, supplier.key, quantity = 10, unitCost = 60.0, invoiceNo = "8842")
        )

        store.updatePurchase(
            id = purchase.id,
            lines = listOf(DraftPurchaseLine(locks.uid, 8, 60.0)),
            supplierKey = supplier.key,
            createdAt = day,
            invoiceNo = "8842"
        )

        val corrected = store.purchases.single()
        assertNull(corrected.name)
        assertEquals(0, corrected.qty)
        assertEquals(listOf(PurchaseLine(locks.uid, "Cisa lock", 8, 60.0)), corrected.lines)
        assertEquals(10, store.product(locks.uid)?.stock)
    }

    @Test
    fun `the one-product way in is the one-line case`() {
        // Forty call sites use it, and it is the shape of most deliveries. It
        // must produce exactly what handing over a single line produces.
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))

        val purchase = assertNotNull(
            store.recordPurchase(locks, supplier.key, quantity = 5, unitCost = 62.0, invoiceNo = "8842")
        )

        assertEquals(listOf(PurchaseLine(locks.uid, "Cisa lock", 5, 62.0)), purchase.lines)
        assertEquals(310.0, purchase.total)
        assertEquals(7, store.product(locks.uid)?.stock)
    }

    // --- Getting to a new phone

    @Test
    fun `the lines survive export and import`() {
        val store = store()
        val locks = store.addProduct("Cisa lock", 2, 60.0, 95.0)
        val keys = store.addProduct("Key blank", 10, 3.0, 6.0)
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))
        store.recordPurchase(
            lines = listOf(DraftPurchaseLine(locks.uid, 10, 62.0), DraftPurchaseLine(keys.uid, 100, 2.5)),
            supplierKey = supplier.key,
            paid = 500.0,
            invoiceNo = "8842"
        )

        val fresh = store()
        fresh.replaceEverything(BackupService.decode(BackupService.encode(store.makeBackupDocument())))

        val purchase = fresh.purchases.single()
        assertEquals(2, purchase.items.size)
        assertEquals("Cisa lock", purchase.items.first().name)
        assertEquals(100, purchase.items.last().qty)
        assertEquals(870.0, purchase.total)
        assertEquals(370.0, purchase.balance)
    }

    @Test
    fun `an older file's delivery arrives as a line`() {
        // The shape every build before this one wrote. It has to keep its
        // itemisation, or a restore would quietly turn every delivery in the
        // book into a bare figure.
        val text = """
            {"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR",
             "purchases":[{"id":"p1","supplierKey":"al-riyadh hardware","productUID":"abc",
              "name":"Cisa lock","qty":10,"unitCost":60.0,"total":600.0,
              "createdAt":"2026-08-13T12:00:00Z"}]}
        """.trimIndent()

        val store = store()
        store.replaceEverything(BackupService.decode(text))

        val purchase = store.purchases.single()
        assertEquals(listOf(PurchaseLine("abc", "Cisa lock", 10, 60.0)), purchase.items)
        assertEquals(600.0, purchase.total)
    }

    @Test
    fun `a reader that drops the lines still shows what is owed`() {
        // Why this did not bump the format version. `total` and `paid` are
        // untouched, and the shelf count lives on the product rather than being
        // replayed from deliveries — so every figure survives and only the
        // breakdown is lost.
        val text = """
            {"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR",
             "purchases":[{"id":"p1","supplierKey":"al-riyadh hardware","total":870.0,"paid":500.0,
              "createdAt":"2026-08-13T12:00:00Z"}]}
        """.trimIndent()

        val document = BackupService.decode(text)
        val purchase = document.purchases.single()

        assertEquals(870.0, purchase.total)
        assertEquals(500.0, purchase.paid)
        assertTrue(purchase.lines.isEmpty())
    }
}
