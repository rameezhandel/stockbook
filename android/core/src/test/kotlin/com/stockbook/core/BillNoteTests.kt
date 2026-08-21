package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a bill was for, in the owner's words.
 *
 * The owner's own reminder, and half this suite is about keeping it that way.
 * It shows on the bill when the bill is opened and nowhere else — not on the
 * statement, which is a document the customer is handed. That is a rule of the
 * form "these two must never meet", which decays silently unless something
 * asserts it: a note quietly appearing on a statement would only be discovered
 * by a shopkeeper who had already handed one over.
 */
class BillNoteTests {

    private fun store() = StockbookStore(InMemoryRepository())
    private val strings = Strings(AppLanguage.ENGLISH)

    private fun StockbookStore.billFor(note: String?) = saveBill(
        customer = "Ahmed Contracting",
        paid = null,
        amount = 250.0,
        invoiceNo = "1024",
        note = note
    )

    @Test
    fun `a bill remembers what it was for`() {
        val store = store()

        val bill = assertNotNull(store.billFor("3 keys cut on site"))

        assertEquals("3 keys cut on site", bill.note)
    }

    @Test
    fun `a blank note is absent, not empty`() {
        // Both builds must write the same bytes for a bill without one, and an
        // empty string is not the same JSON as no key at all.
        val store = store()

        assertNull(assertNotNull(store.billFor("   ")).note)
        assertNull(assertNotNull(store.billFor(null)).note)
    }

    @Test
    fun `the note is trimmed on the way in`() {
        val store = store()

        assertEquals("Delivered", assertNotNull(store.billFor("  Delivered  ")).note)
    }

    @Test
    fun `a correction can add, change and clear the note`() {
        val store = store()
        val bill = assertNotNull(store.billFor(null))

        store.updateBill(
            number = bill.number,
            customer = "Ahmed Contracting",
            paid = null,
            amount = 250.0,
            createdAt = bill.createdAt,
            invoiceNo = "1024",
            note = "Delivered to the villa"
        )
        assertEquals("Delivered to the villa", store.bills.single().note)

        store.updateBill(
            number = bill.number,
            customer = "Ahmed Contracting",
            paid = null,
            amount = 250.0,
            createdAt = bill.createdAt,
            invoiceNo = "1024",
            note = ""
        )
        assertNull(store.bills.single().note, "clearing it must remove it, not blank it")
    }

    // --- Where it must not go

    private fun documentFor(note: String?): StatementDocument {
        val store = store()
        store.billFor(note)
        val statement = assertNotNull(
            store.statementForCustomer("ahmed contracting", StatementPeriod.thisYear())
        )
        return StatementDocument.make(statement, store.settings, strings)
    }

    @Test
    fun `the note never reaches the statement`() {
        // The statement is what the owner turns round and shows the customer.
        // A note saying "argued about the price" belongs to the till.
        val document = documentFor("Argued about the price")

        val row = document.activityRows.single()
        assertTrue(row.transaction.contains("1024"), row.transaction)
        assertFalse(
            document.activityRows.any { it.transaction.contains("Argued", ignoreCase = true) },
            "the note must not be folded into the reference"
        )
    }

    @Test
    fun `nothing anywhere on the document carries it`() {
        // Belt and braces, and cheap: the summary rows, the headings and the
        // closing line are all reachable from here, and a future change that
        // routed the note into any of them would pass the test above.
        val document = documentFor("Argued about the price")
        val everything = buildList {
            addAll(document.summaryRows.map { "${it.label} ${it.value}" })
            addAll(document.activityRows.map { "${it.date} ${it.transaction} ${it.amount} ${it.balance}" })
            add("${document.closingLabel} ${document.closingValue}")
            add(document.partyName)
            addAll(document.partyLines)
        }

        assertTrue(everything.none { it.contains("Argued", ignoreCase = true) }, everything.toString())
    }

    // --- Getting to a new phone

    @Test
    fun `the note survives export and import`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)
        store.saveBill(
            lines = listOf(DraftLine(product.uid, 1, 95.0)),
            customer = "Ahmed Contracting",
            paid = null,
            invoiceNo = "1024",
            note = "Fitted on site"
        )

        val fresh = store()
        fresh.replaceEverything(BackupService.decode(BackupService.encode(store.makeBackupDocument())))

        assertEquals("Fitted on site", fresh.bills.single().note)
    }

    @Test
    fun `a bill written before notes existed still opens`() {
        val text = """
            {"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR",
             "bills":[{"number":1,"createdAt":"2026-08-13T12:00:00Z","total":250.0,"who":"Ahmed"}]}
        """.trimIndent()

        val document = BackupService.decode(text)

        assertNull(document.bills.single().note)
    }
}
