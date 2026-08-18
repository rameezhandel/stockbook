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
import kotlin.test.assertTrue

/**
 * Money the shop gives back without money moving.
 *
 * The figures here are the whole feature. A credit note that reduced the balance
 * by the wrong amount, or that put stock back twice, is invisible on screen until
 * somebody counts a bin or asks a customer for money that was written off — so
 * every test below checks a **figure or a shelf count**, never merely that a call
 * returned non-null.
 */
class CreditNoteTests {

    private val english = Strings(AppLanguage.ENGLISH)

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aProduct() = addProduct("Cisa lock", 50, 60.0, 95.0)

    private fun StockbookStore.aCustomerOwing(): String {
        addCustomer("Ahmed")
        // 2000 billed, 0 paid — Ahmed owes 2000.
        saveBill(customer = "Ahmed", paid = 0.0, amount = 2000.0, invoiceNo = "1001")
        return "ahmed"
    }

    // --- What it does to the balance

    @Test
    fun `a credit note reduces what the customer owes`() {
        val store = store()
        val key = store.aCustomerOwing()
        assertEquals(2000.0, store.customer(key)?.owed)

        store.addCreditNote(customerKey = key, amount = 540.0, noteNo = "00130")

        assertEquals(1460.0, store.customer(key)?.owed, "2000 billed less 540 credited")
    }

    @Test
    fun `a credit note is not a payment`() {
        // The distinction the whole type exists for: both reduce the balance and
        // only one of them is cash. A statement that conflated them would tell
        // the owner they had taken money they never took.
        val store = store()
        val key = store.aCustomerOwing()

        store.addCreditNote(customerKey = key, amount = 540.0, noteNo = "00130")

        val statement = assertNotNull(store.statementForCustomer(key, StatementPeriod.thisMonth()))
        assertEquals(0.0, statement.received, "no money arrived")
        assertEquals(540.0, statement.credited)
        assertEquals(2000.0, statement.billed, "and the invoice still says what it said")
        assertEquals(1460.0, statement.closingBalance)
    }

    @Test
    fun `credit and cash both come off the closing balance`() {
        val store = store()
        val key = store.aCustomerOwing()

        store.recordPayment(key, 300.0)
        store.addCreditNote(customerKey = key, amount = 200.0, noteNo = "00130")

        val statement = assertNotNull(store.statementForCustomer(key, StatementPeriod.thisMonth()))
        assertEquals(300.0, statement.received)
        assertEquals(200.0, statement.credited)
        assertEquals(1500.0, statement.closingBalance, "2000 − 300 − 200")
        assertEquals(1500.0, store.customer(key)?.owed, "and the roster agrees with the document")
    }

    @Test
    fun `a credit note appears on the statement in date order`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 1000.0,
            createdAt = Instant.parse("2026-08-01T09:00:00Z"),
            invoiceNo = "1001"
        )
        store.addCreditNote(
            customerKey = "ahmed",
            amount = 250.0,
            noteNo = "00130",
            issuedAt = Instant.parse("2026-08-05T09:00:00Z")
        )

        val statement = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )

        assertEquals(2, statement.entries.size)
        assertEquals(listOf(1000.0, 750.0), statement.runningBalances, "the balance column reads down")
    }

    @Test
    fun `a credit note from an earlier month is carried forward`() {
        // The reason a statement has an opening balance at all: a customer
        // credited in July must not look like they owe it again in August.
        val store = store()
        store.addCustomer("Ahmed")
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 1000.0,
            createdAt = Instant.parse("2026-07-10T09:00:00Z"),
            invoiceNo = "1001"
        )
        store.addCreditNote(
            customerKey = "ahmed",
            amount = 400.0,
            noteNo = "00130",
            issuedAt = Instant.parse("2026-07-20T09:00:00Z")
        )

        val august = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )

        assertEquals(600.0, august.openingBalance)
        assertEquals(0.0, august.credited, "July's note belongs to July")
        assertEquals(600.0, august.closingBalance)
    }

    // --- The shelf

    @Test
    fun `returned items go back on the shelf`() {
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 5, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            invoiceNo = "1001"
        )
        assertEquals(45, store.product(product.uid)?.stock, "the sale took five off")

        store.addCreditNote(
            customerKey = "ahmed",
            lines = listOf(DraftLine(product.uid, 2, 95.0)),
            noteNo = "00130"
        )

        assertEquals(47, store.product(product.uid)?.stock, "and two came back")
        assertEquals(190.0, store.creditNotes.first().total, "2 × 95, from the lines")
    }

    @Test
    fun `a credit note with no items moves no stock`() {
        // The mirror of a bill entered as a figure. An overcharge is not a return.
        val store = store()
        val product = store.aProduct()
        val key = store.aCustomerOwing()

        store.addCreditNote(customerKey = key, amount = 300.0, noteNo = "00130")

        assertEquals(50, store.product(product.uid)?.stock)
    }

    @Test
    fun `editing a credit note down leaves the right count on the shelf`() {
        // The bug this pins: without taking the old note's goods back first,
        // editing 5 to 3 would leave 8 on the shelf instead of 3.
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 10, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            invoiceNo = "1001"
        )
        assertEquals(40, store.product(product.uid)?.stock)

        val note = assertNotNull(
            store.addCreditNote(
                customerKey = "ahmed",
                lines = listOf(DraftLine(product.uid, 5, 95.0)),
                noteNo = "00130"
            )
        )
        assertEquals(45, store.product(product.uid)?.stock)

        store.updateCreditNote(
            id = note.id,
            customerKey = "ahmed",
            lines = listOf(DraftLine(product.uid, 3, 95.0)),
            noteNo = "00130",
            issuedAt = note.issuedAt
        )

        assertEquals(43, store.product(product.uid)?.stock, "40 on the shelf plus the 3 returned")
        assertEquals(285.0, store.creditNotes.first().total)
    }

    @Test
    fun `removing a credit note takes its goods back off the shelf`() {
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 5, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            invoiceNo = "1001"
        )
        val note = assertNotNull(
            store.addCreditNote(
                customerKey = "ahmed",
                lines = listOf(DraftLine(product.uid, 2, 95.0)),
                noteNo = "00130"
            )
        )
        assertEquals(47, store.product(product.uid)?.stock)

        store.deleteCreditNote(note.id)

        assertEquals(45, store.product(product.uid)?.stock)
        assertEquals(475.0, store.customer("ahmed")?.owed, "and the credit is gone from the balance")
    }

    // --- What it refuses

    @Test
    fun `a credit note for nothing is refused`() {
        val store = store()
        val key = store.aCustomerOwing()

        assertNull(store.addCreditNote(customerKey = key, amount = 0.0, noteNo = "00130"))
        assertNull(store.addCreditNote(customerKey = key, amount = null, noteNo = "00130"))
        assertNull(store.addCreditNote(customerKey = "", amount = 100.0, noteNo = "00130"))
        assertTrue(store.creditNotes.isEmpty())
    }

    @Test
    fun `a note number already used is found, whatever case it was typed in`() {
        val store = store()
        val key = store.aCustomerOwing()
        store.addCreditNote(customerKey = key, amount = 100.0, noteNo = "CN-0130")

        assertNotNull(store.creditNoteWithNo(" cn-0130 "))
        assertNull(store.creditNoteWithNo("CN-0131"))
        assertNull(store.creditNoteWithNo(""), "an empty box is not a clash with every blank note")
    }

    @Test
    fun `a credit note does not clash with a bill on the same number`() {
        // Its own series. "#00130" in a credit-note run has nothing to do with
        // invoice 00130, and refusing it would be the app inventing a rule the
        // shop's paper does not have.
        val store = store()
        val key = store.aCustomerOwing()

        store.addCreditNote(customerKey = key, amount = 100.0, noteNo = "1001")

        assertNotNull(store.creditNoteWithNo("1001"))
        assertEquals(1, store.creditNotes.size)
        assertNotNull(store.billWithInvoiceNo("1001"), "and the bill keeps its own number")
    }

    @Test
    fun `a note being edited does not clash with itself`() {
        val store = store()
        val key = store.aCustomerOwing()
        val note = assertNotNull(store.addCreditNote(customerKey = key, amount = 100.0, noteNo = "00130"))

        assertNull(store.creditNoteWithNo("00130", exceptId = note.id))
    }

    @Test
    fun `a note with no number is called what it is`() {
        val store = store()
        val key = store.aCustomerOwing()
        val note = assertNotNull(store.addCreditNote(customerKey = key, amount = 100.0))

        assertNull(note.noteNo)
        assertEquals(english.creditNoteLabel, note.reference(english))
    }

    // --- The file

    @Test
    fun `credit notes survive a backup round trip`() {
        val store = store()
        val product = store.aProduct()
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 5, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            invoiceNo = "1001"
        )
        store.addCreditNote(
            customerKey = "ahmed",
            lines = listOf(DraftLine(product.uid, 2, 95.0)),
            noteNo = "00130",
            reason = "returned, damaged"
        )

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))

        // A reader that dropped these would show every credited customer owing
        // more than they do, and send the owner to ask for money written off
        // weeks ago. That is a figure misread, which is what bumps the version.
        assertEquals(3, document.version)

        val restored = store()
        restored.replaceEverything(document)

        val note = restored.creditNotes.single()
        assertEquals("00130", note.noteNo)
        assertEquals(190.0, note.total)
        assertEquals("returned, damaged", note.reason)
        assertEquals(1, note.lines.size)
        assertEquals(285.0, restored.customer("ahmed")?.owed, "475 billed less 190 credited")
    }

    @Test
    fun `a file with no credit notes still reads`() {
        // Nothing in a fresh shop has one, and the key is absent rather than
        // empty in a file written before they existed.
        val document = BackupService.decode(
            """
            {
              "version": 3,
              "exportedAt": "2026-08-18T09:00:00Z",
              "ownerName": "Khalid",
              "currencyCode": "SAR"
            }
            """.trimIndent()
        )

        assertTrue(document.creditNotes.isEmpty())
    }
}
