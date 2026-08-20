package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupError
import com.stockbook.core.transfer.BackupService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTests {

    private val exportedAt: Instant = Instant.parse("2026-07-28T09:41:00Z")

    @Test
    fun `a document survives the round trip`() {
        val store = StockbookStore(InMemoryRepository())
        store.setOwnerName("Khalid Al-Amri")
        val product = store.addProduct("Cisa lock", 20, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", paid = 100.0)

        val document = store.makeBackupDocument(exportedAt)
        val restored = BackupService.decode(BackupService.encode(document))

        assertEquals(document, restored)
    }

    @Test
    fun `the version is checked before the shape`() {
        // A file from a future build may decode cleanly into today's structs
        // while meaning something different, and the result of getting that
        // wrong is a destructive whole-database replace.
        val json = """{"version":99,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K","currencyCode":"SAR","products":[],"bills":[]}"""
        val error = assertFailsWith<BackupError.NewerVersion> { BackupService.decode(json) }
        assertEquals(99, error.found)
    }

    @Test
    fun `anything that is not a Stockbook file is refused`() {
        assertFailsWith<BackupError.NotStockbookData> { BackupService.decode("not json at all") }
        assertFailsWith<BackupError.NotStockbookData> { BackupService.decode("""{"hello":"world"}""") }
        assertFailsWith<BackupError.NotStockbookData> { BackupService.decode("""{"version":1}""") }
    }

    @Test
    fun `the summary line reads as a sentence`() {
        val document = BackupDocument(
            exportedAt = exportedAt,
            ownerName = "Khalid Al-Amri",
            currencyCode = "SAR",
            products = (1..8).map { BackupDocument.ProductRecord("uid-$it", "P$it", 1, 1.0, 2.0) },
            bills = emptyList()
        )
        assertEquals(
            "Khalid Al-Amri · 8 products · 0 bills · saved 28 July 2026",
            document.summaryLine(Strings(AppLanguage.ENGLISH))
        )
    }

    @Test
    fun `the filename is dated and never localised`() {
        val document = BackupDocument(
            exportedAt = exportedAt,
            ownerName = "K",
            currencyCode = "SAR"
        )
        assertEquals("stockbook-2026-07-28.zip", document.suggestedFilename)
        assertTrue(document.suggestedFilename.all { it.code < 128 })
    }
}

/**
 * The file is the only way data moves between phones, and one of those phones
 * may be an iPhone. These are the properties that make that work, and none of
 * them can be checked by looking at an Android screen.
 */
class CrossPlatformBackupTests {

    /** Exactly the shape the iOS build writes: sorted keys, ISO-8601, no nulls. */
    private val fromIPhone = """
        {
          "bills" : [
            {
              "createdAt" : "2026-07-28T09:41:00Z",
              "lines" : [
                {
                  "name" : "Cisa lock",
                  "price" : 95,
                  "productUID" : "8B7F0A2E-1C4D-4E5F-9A3B-6D2E7C8F1A05",
                  "qty" : 2
                }
              ],
              "number" : 1,
              "paid" : 100,
              "total" : 190,
              "voided" : false,
              "who" : "Ahmed Contracting"
            },
            {
              "createdAt" : "2026-07-28T10:15:00Z",
              "lines" : [
                {
                  "name" : "Padlock",
                  "price" : 25,
                  "productUID" : "1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D",
                  "qty" : 1
                }
              ],
              "number" : 2,
              "total" : 25,
              "voided" : false,
              "who" : "Sami"
            }
          ],
          "currencyCode" : "SAR",
          "customers" : [
          ],
          "exportedAt" : "2026-07-28T11:00:00Z",
          "ownerName" : "Khalid Al-Amri",
          "payments" : [
          ],
          "products" : [
            {
              "cost" : 60,
              "name" : "Cisa lock",
              "price" : 95,
              "stock" : 18,
              "uid" : "8B7F0A2E-1C4D-4E5F-9A3B-6D2E7C8F1A05"
            }
          ],
          "version" : 1
        }
    """.trimIndent()

    @Test
    fun `a backup written on an iPhone opens here`() {
        val document = BackupService.decode(fromIPhone)

        assertEquals("Khalid Al-Amri", document.ownerName)
        assertEquals(1, document.products.size)
        assertEquals(2, document.bills.size)
        // The key Swift spells `productUID`. Renaming it to fit Kotlin's
        // conventions would strand every line item on the way across.
        assertEquals("8B7F0A2E-1C4D-4E5F-9A3B-6D2E7C8F1A05", document.bills.first().lines.first().productUid)
        assertEquals(100.0, document.bills.first().paid)
        // An absent `paid` is paid in full, not zero paid.
        assertNull(document.bills[1].paid)
        // The code is what says what these numbers mean; nothing else does.
        assertEquals("SAR", document.currencyCode)
    }

    @Test
    fun `importing an iPhone backup rebuilds the shop`() {
        val store = StockbookStore(InMemoryRepository())
        store.replaceEverything(BackupService.decode(fromIPhone))

        assertEquals("Khalid Al-Amri", store.settings.ownerName)
        assertEquals(1, store.products.size)
        assertEquals(2, store.bills.size)
        assertEquals(3, store.settings.nextBillNumber, "the next bill must not reuse a number from the file")
        assertTrue(store.settings.setupCompleted)
        assertEquals(Currency.SAR, store.settings.currency)

        // The debtors list has to survive the crossing too.
        val ahmed = assertNotNull(store.customers().firstOrNull { it.name == "Ahmed Contracting" })
        assertEquals(90.0, ahmed.owed)
    }

    @Test
    fun `what we write is what an iPhone can read`() {
        val store = StockbookStore(InMemoryRepository())
        store.setOwnerName("Khalid")
        val product = store.addProduct("Cisa lock", 20, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", null)

        val text = BackupService.encode(store.makeBackupDocument(Instant.parse("2026-07-28T11:00:00Z")))

        for (key in listOf(
            "version", "exportedAt", "ownerName", "currencyCode",
            "uid", "stock", "cost", "price",
            "number", "createdAt", "total", "who", "productUID", "qty"
        )) {
            assertTrue(text.contains("\"$key\""), "the iPhone reader expects a `$key` key")
        }

        // Gone from both platforms together. A bill is edited or removed now, so
        // there is no state to carry — and a key written here that Swift no
        // longer declares is a file the iPhone would refuse outright.
        assertTrue(!text.contains("\"voided\""), "`voided` is not part of the format any more")

        // Paid in full is an *absent* key, not a null — Swift's decoder reads
        // "not present" as nil and would refuse an explicit null.
        assertTrue(!text.contains("\"paid\""), "a bill paid in full must omit `paid` entirely")
    }

    @Test
    fun `timestamps carry no fractional seconds`() {
        // Foundation's `.iso8601` strategy emits none and refuses to parse them,
        // so a millisecond here is a file the iPhone rejects.
        val store = StockbookStore(InMemoryRepository())
        val document = store.makeBackupDocument(Instant.parse("2026-07-28T11:00:00.123456Z"))

        val text = BackupService.encode(document)
        assertTrue(text.contains("2026-07-28T11:00:00Z"), text)
        assertTrue(!text.contains(".123"), "fractional seconds would be rejected on the way in")
    }
}
