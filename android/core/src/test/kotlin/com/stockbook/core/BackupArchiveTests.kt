package com.stockbook.core

import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupArchive
import com.stockbook.core.transfer.BackupError
import com.stockbook.core.transfer.BackupService
import com.stockbook.core.transfer.ZipArchive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pictures travelling with the book.
 *
 * What is being pinned here is not the ZIP — `ZipArchiveTests` does that against
 * a real implementation — but the two promises around it: that the document
 * inside is byte-for-byte what the plain export always was, and that a file
 * written before archives existed still opens.
 */
class BackupArchiveTests {

    private fun shopWithAPhotographedBill(): Pair<StockbookStore, String> {
        val store = StockbookStore(InMemoryRepository())
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)
        val bill = store.saveBill(
            listOf(com.stockbook.core.store.DraftLine(product.uid, 1, 95.0)),
            customer = "Ahmed",
            paid = 95.0,
            invoiceNo = "1024"
        )!!
        val id = "photo-one"
        store.attachPhoto(bill.number, id)
        return store to id
    }

    @Test
    fun `the document inside the archive is the file we have always written`() {
        // The whole reason the JSON is an entry rather than a new shape: every
        // existing test of the format, and the cross-platform byte guarantee,
        // keep testing the same bytes.
        val (store, _) = shopWithAPhotographedBill()
        val document = store.makeBackupDocument()

        val archive = BackupArchive.pack(document) { null }
        val inside = ZipArchive.read(archive).single { it.name == BackupArchive.documentEntry }

        assertEquals(BackupService.encode(document), inside.bytes.decodeToString())
    }

    @Test
    fun `a photograph goes in and comes back out unchanged`() {
        val (store, id) = shopWithAPhotographedBill()
        // Bytes that are not text, because a JPEG is not text and nothing on this
        // path may quietly decode one.
        val picture = ByteArray(4096) { (it * 13 % 256).toByte() }

        val archive = BackupArchive.pack(store.makeBackupDocument()) { asked ->
            if (asked == id) picture else null
        }

        val restored = mutableMapOf<String, ByteArray>()
        BackupArchive.unpack(archive) { photoId, data -> restored[photoId] = data }

        assertContentEquals(picture, restored[id])
    }

    @Test
    fun `a picture the phone no longer holds is skipped, not fatal`() {
        // The photo store's rule, kept: an id whose file is missing is never
        // pruned from the book, because the file may yet arrive. Export has to
        // live by the same rule or it would refuse to run on a shop that has
        // ever lost one.
        val (store, _) = shopWithAPhotographedBill()

        val archive = BackupArchive.pack(store.makeBackupDocument()) { null }

        val restored = mutableListOf<String>()
        val document = BackupArchive.unpack(archive) { id, _ -> restored += id }

        assertTrue(restored.isEmpty())
        // And the id is still in the book, waiting.
        assertEquals(listOf("photo-one"), document.bills.single().photoIds)
    }

    @Test
    fun `a bare json file still imports`() {
        // Every backup taken before this existed. The reader sniffs the bytes
        // rather than the extension, because the document picker lies about
        // types.
        val (store, _) = shopWithAPhotographedBill()
        val json = BackupService.encode(store.makeBackupDocument()).toByteArray()

        val document = BackupArchive.unpack(json) { _, _ -> error("a json file has no photographs in it") }

        assertEquals("Ahmed", document.bills.single().who)
    }

    @Test
    fun `an archive with no document in it is not ours`() {
        val archive = ZipArchive.write(sequenceOf(ZipArchive.Entry("photos/a.jpg", byteArrayOf(1, 2, 3))))

        assertFailsWith<BackupError.NotStockbookData> { BackupArchive.unpack(archive) { _, _ -> } }
    }

    @Test
    fun `a damaged archive is refused as not ours rather than crashing`() {
        val (store, _) = shopWithAPhotographedBill()
        val archive = BackupArchive.pack(store.makeBackupDocument()) { ByteArray(64) { 7 } }
        archive[archive.size - 3] = 0x7f

        assertFailsWith<BackupError.NotStockbookData> { BackupArchive.unpack(archive) { _, _ -> } }
    }

    @Test
    fun `nothing is written to disk before the document has been accepted`() {
        // A file from a future build decodes far enough to be recognised and then
        // has to be refused. Scattering its photographs across the phone on the
        // way to refusing it would leave rubbish behind that nothing references.
        val (store, _) = shopWithAPhotographedBill()
        val document = store.makeBackupDocument()
        val future = BackupService.encode(document).replace("\"version\": 3", "\"version\": 99")
        val archive = ZipArchive.write(
            sequenceOf(
                ZipArchive.Entry(BackupArchive.documentEntry, future.toByteArray()),
                ZipArchive.Entry(BackupArchive.photoEntry("photo-one"), ByteArray(8))
            )
        )

        assertFailsWith<BackupError.NewerVersion> {
            BackupArchive.unpack(archive) { _, _ -> error("no photograph may be written before the version check") }
        }
    }

    @Test
    fun `a damaged photograph costs the photograph, never the book`() {
        // Which way round this fails is the whole question. The book is the part
        // that cannot be replaced; a picture of a bill can be taken again, and
        // the id stays on the bill either way so it can be re-adopted later.
        //
        // Getting it backwards is easy and was: finding the document by walking
        // every entry checked every entry's CRC on the way past, so one bad
        // picture refused the whole import.
        val json = BackupService.encode(shopWithAPhotographedBill().first.makeBackupDocument()).toByteArray()
        val archive = ZipArchive.write(
            sequenceOf(
                ZipArchive.Entry(BackupArchive.documentEntry, json),
                ZipArchive.Entry("photos/x.jpg", ByteArray(64) { 9 })
            )
        )
        // A 30-byte local header and a 14-byte name, then the document; then the
        // second header, 30 bytes and a 12-byte name. Aimed, so the test cannot
        // pass by damaging something else.
        archive[30 + 14 + json.size + 30 + 12] = 0x7f

        val restored = mutableListOf<String>()
        val document = BackupArchive.unpack(archive) { id, _ -> restored += id }

        assertTrue(restored.isEmpty())
        assertEquals("Ahmed", document.bills.single().who)
        assertEquals(listOf("photo-one"), document.bills.single().photoIds)
    }

    @Test
    fun `entry names map to ids and back`() {
        assertEquals("photos/abc.jpg", BackupArchive.photoEntry("abc"))
        assertEquals("abc", BackupArchive.photoID("photos/abc.jpg"))
    }

    @Test
    fun `anything else in the archive is ignored rather than refused`() {
        // Room for a later version to put a file beside these. Incompatibility is
        // declared by the version number, never by the file list.
        assertNull(BackupArchive.photoID("stockbook.json"))
        assertNull(BackupArchive.photoID("photos/nested/a.jpg"))
        assertNull(BackupArchive.photoID("photos/.jpg"))
        assertNull(BackupArchive.photoID("photos/a.png"))
    }

    @Test
    fun `one picture on two bills is stored once`() {
        val store = StockbookStore(InMemoryRepository())
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)
        val first = store.saveBill(
            listOf(com.stockbook.core.store.DraftLine(product.uid, 1, 95.0)),
            customer = "Ahmed", paid = 95.0, invoiceNo = "1"
        )!!
        val second = store.saveBill(
            listOf(com.stockbook.core.store.DraftLine(product.uid, 1, 95.0)),
            customer = "Fatima", paid = 95.0, invoiceNo = "2"
        )!!
        store.attachPhoto(first.number, "shared")
        store.attachPhoto(second.number, "shared")

        var asked = 0
        val archive = BackupArchive.pack(store.makeBackupDocument()) { asked += 1; ByteArray(16) }

        assertEquals(1, asked)
        assertEquals(1, ZipArchive.read(archive).count { BackupArchive.photoID(it.name) != null })
    }
}
