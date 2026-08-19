package com.stockbook.core

import com.stockbook.core.model.PhotoPolicy
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Photographs of the paper bill, as far as the book is concerned.
 *
 * The book holds **ids, never pictures**. What is pinned here is the rule that
 * makes that safe: cleanup runs one way only. Files nothing refers to may be
 * deleted; an id whose file is missing may not. Get that backwards and a book
 * restored ahead of its pictures loses the link to them permanently, silently,
 * and at the exact moment the owner is least able to notice.
 */
class BillPhotoTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aBill(vararg photos: String) =
        assertNotNull(
            saveBill(
                customer = "Ahmed",
                paid = null,
                amount = 500.0,
                invoiceNo = "06011",
                photoIds = photos.toList()
            )
        )

    // --- Carrying them

    @Test
    fun `a bill keeps the photographs it was saved with`() {
        val bill = store().aBill("a", "b")

        assertEquals(listOf("a", "b"), bill.photoIds)
    }

    @Test
    fun `a bill with no photograph carries an empty list, not a null`() {
        // Every reading site walks the list. An absent list would make each of
        // them ask a question that has one answer everywhere.
        assertTrue(store().aBill().photoIds.isEmpty())
    }

    @Test
    fun `the same photograph is not attached twice`() {
        val store = store()
        val bill = store.aBill()

        store.attachPhoto(bill.number, "a")
        store.attachPhoto(bill.number, "a")

        assertEquals(listOf("a"), assertNotNull(store.bill(bill.number)).photoIds)
    }

    @Test
    fun `attaching and detaching move only the ids`() {
        val store = store()
        val bill = store.aBill("a")

        store.attachPhoto(bill.number, "b")
        assertEquals(listOf("a", "b"), assertNotNull(store.bill(bill.number)).photoIds)

        store.detachPhoto(bill.number, "a")
        assertEquals(listOf("b"), assertNotNull(store.bill(bill.number)).photoIds)
    }

    @Test
    fun `detaching something that was never there changes nothing`() {
        val store = store()
        val bill = store.aBill("a")

        store.detachPhoto(bill.number, "somebody else's")

        assertEquals(listOf("a"), assertNotNull(store.bill(bill.number)).photoIds)
    }

    @Test
    fun `editing a bill leaves its photographs alone`() {
        // The edit form knows nothing about photographs, and must not be able to
        // wipe them by not mentioning them. This is why `updateBill` takes no
        // photo argument at all.
        val store = store()
        val bill = store.aBill("a", "b")

        store.updateBill(
            number = bill.number,
            customer = "Ahmed",
            paid = 100.0,
            amount = 900.0,
            createdAt = bill.createdAt,
            invoiceNo = "06011"
        )

        assertEquals(listOf("a", "b"), assertNotNull(store.bill(bill.number)).photoIds)
    }

    // --- What the sweep may take

    @Test
    fun `the book reports every photograph it still refers to`() {
        val store = store()
        store.aBill("a", "b")
        store.aBill("c")

        assertEquals(setOf("a", "b", "c"), store.photoIdsInUse())
    }

    @Test
    fun `deleting a bill releases its photographs`() {
        // Which is what lets the sweep collect the files afterwards. Until the
        // bill is gone the files are still spoken for.
        val store = store()
        val bill = store.aBill("a")
        store.aBill("b")

        store.deleteBill(bill.number)

        assertEquals(setOf("b"), store.photoIdsInUse())
    }

    @Test
    fun `restoring a file replaces the whole set`() {
        // `replaceEverything` is a swap, not a merge, so every photograph that
        // was on this phone is stranded by an import. The sweep is what collects
        // them, and this is the figure it works from.
        val store = store()
        store.aBill("mine")

        val incoming = store()
        incoming.aBill("theirs")
        store.replaceEverything(incoming.makeBackupDocument())

        assertEquals(setOf("theirs"), store.photoIdsInUse())
    }

    @Test
    fun `an id whose picture is missing is still an id`() {
        // The rule the whole design rests on. Nothing here can prune a reference
        // because a file is absent — the file being absent is a question for the
        // disk, asked every time the picture is shown, and answered "not on this
        // phone" rather than "there was never a photograph".
        val store = store()
        val bill = store.aBill("a picture this phone has never had")

        assertEquals(1, assertNotNull(store.bill(bill.number)).photoIds.size)
        assertTrue("a picture this phone has never had" in store.photoIdsInUse())
    }

    // --- The file

    @Test
    fun `photograph ids survive a backup round trip`() {
        val store = store()
        store.aBill("a", "b")

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))

        // No version bump. A reader that drops these shows a bill with no
        // photograph where the owner took one: a picture lost, not a figure
        // misread — the same rule that let invoice numbers in.
        assertEquals(BackupDocument.currentVersion, document.version)

        val restored = store()
        restored.replaceEverything(document)
        assertEquals(listOf("a", "b"), restored.bills.first().photoIds)
    }

    @Test
    fun `a backup written before photographs still opens`() {
        // The absent key reads as no photographs rather than as a broken file.
        val json = """
            {
              "version": 3,
              "exportedAt": "2026-07-28T11:00:00Z",
              "ownerName": "Khalid Al-Amri",
              "currencyCode": "SAR",
              "bills": [
                { "number": 1, "createdAt": "2026-07-28T09:00:00Z", "total": 95, "who": "Ahmed" }
              ]
            }
        """.trimIndent()

        val document = BackupService.decode(json)

        assertTrue(document.bills.first().photoIds.isNullOrEmpty())
    }

    @Test
    fun `a bill with no photograph writes no key at all`() {
        // Absent, not `[]`. A shop that has never taken one must write exactly
        // the bytes it always did — and the same bytes the iPhone writes, which
        // drops a nil optional. An empty array on one side and a missing key on
        // the other is how the two builds stop producing identical files.
        val store = store()
        store.aBill()

        assertTrue("photoIDs" !in BackupService.encode(store.makeBackupDocument()))
    }

    @Test
    fun `the wire spells it the way Swift does`() {
        // Like `productUID` before it. The two apps read each other's files, and
        // a key renamed to suit Kotlin's conventions would strand every
        // photograph on the way across.
        val store = store()
        store.aBill("a")

        assertTrue("\"photoIDs\"" in BackupService.encode(store.makeBackupDocument()))
    }

    // --- What a stored photograph is

    @Test
    fun `both phones agree on what they are storing`() {
        assertEquals(1600, PhotoPolicy.maxEdge)
        assertEquals(60, PhotoPolicy.qualityOutOfHundred)
        assertEquals("jpg", PhotoPolicy.fileExtension)
    }

    @Test
    fun `an id names exactly one file, and the name gives it back`() {
        val id = PhotoPolicy.newId()

        assertEquals("$id.jpg", PhotoPolicy.fileName(id))
        assertEquals(id, PhotoPolicy.idFromFileName(PhotoPolicy.fileName(id)))
    }

    @Test
    fun `ids are not reused`() {
        assertTrue(PhotoPolicy.newId() != PhotoPolicy.newId())
    }

    @Test
    fun `a file this app did not write is not claimed`() {
        // The sweep deletes by name. Anything that is not one of ours is left
        // where it is rather than tidied away by an app that did not put it
        // there.
        assertNull(PhotoPolicy.idFromFileName("shop.json"))
        assertNull(PhotoPolicy.idFromFileName(".jpg"))
    }

    @Test
    fun `a photograph taken before the bill was saved still lands on it`() {
        // The order the sell screen works in: the picture is taken while the
        // bill is being written, so it exists before the bill does.
        val store = store()
        val id = PhotoPolicy.newId()

        val bill = assertNotNull(
            store.saveBill(
                customer = "Ahmed",
                paid = null,
                amount = 500.0,
                createdAt = Instant.parse("2026-08-19T09:00:00Z"),
                invoiceNo = "06011",
                photoIds = listOf(id)
            )
        )

        assertEquals(listOf(id), bill.photoIds)
    }
}
