package com.stockbook.core

import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What the goods cost, captured on the bill that sold them.
 *
 * **The whole point is the second test.** A bill line records the price charged
 * and, until this existed, nothing about what the shop paid — so working out what
 * a sale earned meant reading `Product.cost`, which is the buying price *now*.
 * Raise a supplier's price next month and last March's figure silently changes:
 * the bill has not moved, the number under it has. `Bill.total` is stored rather
 * than recomputed for exactly this reason on the selling side, and
 * `PurchaseLine.unitCost` on the buying side; this is the third corner of the
 * same rule.
 *
 * There is no profit screen and these tests do not ask for one. They pin that
 * the figure needed to build one honestly is written down at the only moment it
 * is knowable, and survives a trip through the backup file.
 */
class BillCostTests {

    private fun store() = StockbookStore(InMemoryRepository())

    @Test
    fun `a sale records what the goods cost the shop`() {
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        val line = assertNotNull(store.bills.first().lines.firstOrNull())

        assertEquals(20.0, line.cost)
        assertEquals(90.0, line.lineTotal)
        assertEquals(60.0, line.lineCost)
    }

    @Test
    fun `a later price rise does not rewrite what an old sale cost`() {
        // The regression this whole field exists to prevent. March: bought at 20,
        // sold at 30. May: the supplier puts the price up. The March bill must
        // still say 20, because that is what the shop actually paid for the
        // padlocks it sold in March.
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        // A delivery at the new price, which is how `Product.cost` moves.
        val supplier = assertNotNull(store.addSupplier("Gulf Locks"))
        store.recordPurchase(
            product = assertNotNull(store.product(padlock.uid)),
            supplierKey = supplier.key,
            quantity = 10,
            unitCost = 25.0
        )

        assertEquals(25.0, assertNotNull(store.product(padlock.uid)).cost)
        // The shelf moved. The bill did not.
        assertEquals(20.0, store.bills.first().lines.first().cost)
        assertEquals(60.0, store.bills.first().lines.first().lineCost)
    }

    @Test
    fun `correcting a bill re-reads the shelf, because the sale is being restated`() {
        // Editing a bill is the owner saying "this is what the sale actually
        // was", and the line is written afresh from the shelf as it stands. That
        // is the same rule the name and the stock movement already follow.
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        val bill = assertNotNull(
            store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)
        )

        store.update(assertNotNull(store.product(padlock.uid)), name = "Padlock 40mm", cost = 22.0, price = 30.0)
        store.updateBill(
            number = bill.number,
            lines = listOf(DraftLine(padlock.uid, qty = 4, price = 30.0)),
            customer = "Ahmed",
            paid = null,
            createdAt = bill.createdAt
        )

        assertEquals(22.0, store.bill(bill.number)?.lines?.first()?.cost)
    }

    @Test
    fun `a line whose cost was never recorded says so, rather than saying zero`() {
        // What a bill written before this field existed looks like. Null and 0.0
        // are different answers: one is "nobody wrote it down", the other is
        // "these goods were free", and a page netting cost off takings has to
        // keep them apart or an old bill reads as pure profit.
        val line = com.stockbook.core.model.BillLine(name = "Padlock 40mm", qty = 3, price = 30.0)

        assertNull(line.cost)
        assertNull(line.lineCost)

        val free = com.stockbook.core.model.BillLine(name = "Sample", qty = 2, price = 5.0, cost = 0.0)
        assertEquals(0.0, free.lineCost)
    }

    @Test
    fun `a bill entered as a figure has no lines and so no cost to read`() {
        // Ordinary, not exceptional — it is how a shop enters the paper bill it
        // already wrote. Whatever eventually computes earnings has to say so
        // rather than treat the whole total as profit.
        val store = store()
        store.saveBill(customer = "Ahmed", paid = null, amount = 100.0)

        assertEquals(emptyList(), store.bills.first().lines)
    }

    @Test
    fun `the cost survives a trip through the backup file`() {
        // The fourth corner: a field that is written but not carried is a field
        // the owner loses on the way to a new phone. `paymentNo` once matched
        // three call sites out of four and would have dropped every receipt
        // number.
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        val json = BackupService.encode(store.makeBackupDocument())
        val restored = StockbookStore(InMemoryRepository())
        restored.replaceEverything(assertNotNull(BackupService.decode(json)))

        assertEquals(20.0, restored.bills.first().lines.first().cost)
    }

    @Test
    fun `an older backup restores with the cost absent rather than refusing to open`() {
        // Every file already written has no `cost` key. Reading one must leave
        // the line unable to answer, not fail — which is why the field defaults
        // rather than being required, and why the document version did not move.
        //
        // Written by the real exporter with the field taken back out, rather
        // than typed out here: a hand-built fixture only proves the decoder can
        // read what this test happens to know about the format, and it drifts
        // the first time anything else on a bill changes.
        val store = store()
        val padlock = store.addProduct("Padlock 40mm", stock = 10, cost = 20.0, price = 30.0)
        store.saveBill(lines = listOf(DraftLine(padlock.uid, qty = 3, price = 30.0)), customer = "Ahmed", paid = null)

        val current = store.makeBackupDocument()
        val older = current.copy(
            bills = current.bills.map { bill ->
                bill.copy(lines = bill.lines.map { it.copy(cost = null) })
            }
        )

        val document = assertNotNull(BackupService.decode(BackupService.encode(older)))
        val restored = StockbookStore(InMemoryRepository())
        restored.replaceEverything(document)

        val line = assertNotNull(restored.bills.firstOrNull()?.lines?.firstOrNull())
        assertEquals("Padlock 40mm", line.name)
        assertNull(line.cost)
    }
}
