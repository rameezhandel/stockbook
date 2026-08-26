package com.stockbook.core

import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The statement as it prints.
 *
 * The arithmetic is `Statement`'s and tested there; what this pins is the
 * *document* — which rows exist, what they are called, and which figures are
 * bracketed. It matters because the two apps draw the PDF with entirely
 * different graphics code, and this structure is the only thing making them
 * agree. A row added on one platform and not the other would be invisible until
 * somebody compared two printouts side by side.
 */
class StatementDocumentTests {

    private val english = Strings(AppLanguage.ENGLISH)

    private fun store() = StockbookStore(InMemoryRepository())

    private fun StockbookStore.aShop(): StockbookStore {
        setOwnerName("Tayba Trading Services Establishment")
        setShopAddress("4343 4343 Adi Ibn Rabi'ah,\nAl-Aziziyah District, 9373\nMadinah, Madinah 42376")
        return this
    }

    private fun document(store: StockbookStore, key: String = "ahmed"): StatementDocument {
        val statement = assertNotNull(store.statementForCustomer(key, StatementPeriod.thisMonth()))
        return StatementDocument.make(statement, store.settings, english)
    }

    // --- Who it is from and to

    @Test
    fun `the shop's address prints as the lines it was typed on`() {
        val store = store().aShop()
        store.addCustomer("Ahmed", phone = "0500 111 222", place = "Al Khobar")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")

        val document = document(store)

        assertEquals("Tayba Trading Services Establishment", document.shopName)
        assertEquals(
            listOf("4343 4343 Adi Ibn Rabi'ah,", "Al-Aziziyah District, 9373", "Madinah, Madinah 42376"),
            document.shopAddressLines
        )
    }

    @Test
    fun `a shop with no address prints no blank lines`() {
        // The block is skipped rather than drawn empty: a run of blank lines
        // under the shop's name reads as a printer fault.
        val store = store()
        store.setOwnerName("Khalid")
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")

        assertTrue(document(store).shopAddressLines.isEmpty())
    }

    @Test
    fun `the customer block carries what is known and nothing else`() {
        val store = store().aShop()
        store.addCustomer("Ahmed", phone = "0500 111 222", place = "Al Khobar")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")

        val document = document(store)

        assertEquals("Ahmed", document.partyName)
        assertEquals(listOf("Al Khobar", "0500 111 222"), document.partyLines)
    }

    @Test
    fun `a customer with no details listed shows only a name`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")

        assertTrue(document(store).partyLines.isEmpty())
    }

    // --- The summary box

    @Test
    fun `the summary reads opening, billed, received, due`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 200.0, amount = 1000.0, invoiceNo = "06011")

        val document = document(store)

        assertEquals(
            listOf(english.openingBalance, english.billedInPeriod, english.receivedInPeriod),
            document.summaryRows.map { it.label }
        )
        assertEquals("SAR 0", document.summaryRows[0].value)
        assertEquals("SAR 1,000", document.summaryRows[1].value)
        assertEquals("SAR 200", document.summaryRows[2].value)
        assertEquals("SAR 800", document.closingValue)
        assertEquals(english.balanceDue, document.closingLabel)
    }

    @Test
    fun `money coming off is marked as a deduction`() {
        // What puts a figure in brackets when it is drawn. A bare minus sign in
        // front of a currency symbol reads as a typo on a printed page.
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 200.0, amount = 1000.0, invoiceNo = "06011")

        val document = document(store)

        assertEquals(listOf(false, false, true), document.summaryRows.map { it.deduction })
    }

    @Test
    fun `credit notes get their own summary row, and only when there are some`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 1000.0, invoiceNo = "06011")

        assertTrue(
            document(store).summaryRows.none { it.label == english.creditNotes },
            "no row before there is one to show"
        )

        store.addCreditNote(customerKey = "ahmed", amount = 540.0, noteNo = "00130")
        val document = document(store)

        val credited = assertNotNull(document.summaryRows.firstOrNull { it.label == english.creditNotes })
        assertEquals("SAR 540", credited.value)
        assertTrue(credited.deduction)
        assertEquals("SAR 460", document.closingValue, "1000 billed less 540 credited")
    }

    /**
     * The gap this closes was real: the rows existed on Android and not on iOS,
     * so an iPhone printed a statement whose closing balance had moved with no
     * line saying why. On a document handed across a counter that is the worst
     * kind of wrong — it looks like an arithmetic mistake by the shop.
     */
    @Test
    fun `a moved balance gets its own summary row at each end`() {
        val store = store().aShop()
        store.addCustomer("Ahmed Jeddah")
        store.addCustomer("Ahmed Riyadh")
        store.saveBill(customer = "Ahmed Jeddah", paid = 0.0, amount = 1000.0, invoiceNo = "06011")

        assertTrue(
            document(store, "ahmed jeddah").summaryRows.none {
                it.label == english.transferredInLabel || it.label == english.transferredOutLabel
            },
            "no rows before anything has moved"
        )

        assertNotNull(
            store.transferBalance(fromKey = "ahmed jeddah", intoKey = "ahmed riyadh", amount = 600.0)
        )

        // The end it left: a deduction, and the closing figure follows it down.
        val left = document(store, "ahmed jeddah")
        val out = assertNotNull(left.summaryRows.firstOrNull { it.label == english.transferredOutLabel })
        assertEquals("SAR 600", out.value)
        assertTrue(out.deduction)
        assertEquals("SAR 400", left.closingValue, "1000 billed less 600 moved away")

        // The end it arrived at: a charge, not a deduction, because the account
        // now owes it.
        val arrived = document(store, "ahmed riyadh")
        val into = assertNotNull(arrived.summaryRows.firstOrNull { it.label == english.transferredInLabel })
        assertEquals("SAR 600", into.value)
        assertTrue(!into.deduction)
        assertEquals("SAR 600", arrived.closingValue)
    }

    @Test
    fun `a supplier statement says bought and paid out rather than billed and received`() {
        val store = store().aShop()
        val supplier = assertNotNull(store.addSupplier("Al Faisal"))
        val product = store.addProduct("Cisa lock", 0, 60.0, 95.0)
        store.recordPurchase(product, supplier.key, quantity = 10, unitCost = 60.0, paid = 0.0, invoiceNo = "INV-88")

        val statement = assertNotNull(store.statementForSupplier(supplier.key, StatementPeriod.thisMonth()))
        val document = StatementDocument.make(statement, store.settings, english)

        assertEquals(english.purchasedInPeriod, document.summaryRows[1].label)
        assertEquals(english.paidOutInPeriod, document.summaryRows[2].label)
    }

    // --- The activity table

    @Test
    fun `every row names the paper it came from`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 1000.0,
            createdAt = Instant.parse("2026-08-01T09:00:00Z"),
            invoiceNo = "06011"
        )
        store.recordPayment("ahmed", 300.0, receivedAt = Instant.parse("2026-08-03T09:00:00Z"), paymentNo = "R-1")
        store.addCreditNote(
            customerKey = "ahmed",
            amount = 200.0,
            noteNo = "00130",
            issuedAt = Instant.parse("2026-08-05T09:00:00Z")
        )

        val statement = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )
        val document = StatementDocument.make(statement, store.settings, english)

        // The kind of document, then its number. "06011" alone tells somebody
        // checking against their own file nothing about what 06011 *is*, and the
        // three books are numbered separately.
        assertEquals(
            listOf("Invoice #06011", "Payment #R-1", "Credit Note #00130"),
            document.activityRows.map { it.details.substringBefore(" · ") }
        )
        // The running balance reads down, which is the column's whole job.
        assertEquals(listOf("SAR 1,000", "SAR 700", "SAR 500"), document.activityRows.map { it.balance })

        // The charge is in one column and what came off is in the other. That is
        // what replaced the brackets.
        assertEquals(listOf("SAR 1,000", "", ""), document.activityRows.map { it.charge })
        assertEquals(listOf("", "SAR 300", "SAR 200"), document.activityRows.map { it.settled })
    }

    @Test
    fun `an itemised bill still prints as one row`() {
        // A statement lists documents, not what was on them. The bill itself is
        // where somebody looks for the lines.
        val store = store().aShop()
        val product = store.addProduct("Cisa lock", 50, 60.0, 95.0)
        store.addCustomer("Ahmed")
        store.saveBill(
            listOf(DraftLine(product.uid, 2, 95.0)),
            customer = "Ahmed",
            paid = 0.0,
            invoiceNo = "06011"
        )

        val document = document(store)

        assertEquals(1, document.activityRows.size)
        assertTrue(document.activityRows.single().details.startsWith("Invoice #06011 · "))
        assertEquals("SAR 190", document.activityRows.single().charge)
    }

    @Test
    fun `a record with no number of its own is still named`() {
        // A cell holding only a date is unreadable, and it matters more than it
        // used to: a payment and a credit note both land in the same money
        // column, so the word in this cell is the only thing telling them apart.
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")
        store.recordPayment("ahmed", 100.0)
        store.addCreditNote(customerKey = "ahmed", amount = 50.0)

        val named = document(store).activityRows.map { it.details.substringBefore(" · ") }

        assertTrue(english.paymentLabel in named, named.toString())
        assertTrue(english.creditNoteLabel in named, named.toString())
    }

    @Test
    fun `dates in the table are numeric so the column lines up`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 500.0,
            createdAt = Instant.parse("2026-05-19T09:00:00Z"),
            invoiceNo = "06011"
        )

        val statement = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-05-10T00:00:00Z")))
        )
        val document = StatementDocument.make(statement, store.settings, english)

        assertEquals("Invoice #06011 · 19/05/2026", document.activityRows.single().details)
    }

    @Test
    fun `the column headings are the four the table draws`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 500.0, invoiceNo = "06011")

        assertEquals(
            listOf("Invoice / Receipt", "Invoice amount", "Received amount", "Balance"),
            document(store).columnHeadings
        )
    }

    @Test
    fun `a bill paid at the counter shows both what it charged and what it took`() {
        // The row that was wrong. A bill settled at the till charges and receives
        // in the same moment, so the balance beside it does not move — and with
        // only the charge printed, the page read as an arithmetic mistake. It was
        // one: the money handed over was missing from it.
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = null, amount = 155.0, invoiceNo = "12")

        val row = document(store).activityRows.single()

        assertEquals("SAR 155", row.charge)
        assertEquals("SAR 155", row.settled, "paid in full at the counter, and the page has to say so")
        assertEquals("SAR 0", row.balance)
    }

    @Test
    fun `a bill part paid at the counter shows the part`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 100.0, amount = 155.0, invoiceNo = "12")

        val row = document(store).activityRows.single()

        assertEquals("SAR 155", row.charge)
        assertEquals("SAR 100", row.settled)
        assertEquals("SAR 55", row.balance, "and the balance moves by the difference")
    }

    @Test
    fun `a bill on credit leaves the received column empty`() {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 155.0, invoiceNo = "12")

        val row = document(store).activityRows.single()

        assertEquals("SAR 155", row.charge)
        assertEquals("", row.settled)
        assertEquals("SAR 155", row.balance)
    }

    @Test
    fun `a supplier's statement says paid where a customer's says received`() {
        // The same table serves both. "Received amount" against money the shop
        // handed over is backwards, and a statement is the page the other side
        // reads most carefully.
        val store = store().aShop()
        val supplier = assertNotNull(store.addSupplier("Al-Riyadh Hardware"))
        store.recordSupplierBill(supplier.key, amount = 800.0, paid = 0.0, invoiceNo = "INV-1")

        val statement = assertNotNull(
            store.statementForSupplier(supplier.key, StatementPeriod.thisYear())
        )
        val document = StatementDocument.make(statement, store.settings, english)

        assertEquals(
            listOf("Bill / Receipt", "Bill amount", "Paid amount", "Balance"),
            document.columnHeadings
        )
    }

    @Test
    fun `a finished month is titled with its own last day`() {
        // Not the exclusive end. Saying "till 1 September" on an August statement
        // claims a day it does not include.
        val document = august(printedOn = Instant.parse("2026-11-02T09:00:00Z"))

        assertTrue(document.summaryTitle.contains("31 August 2026"), document.summaryTitle)
    }

    @Test
    fun `the month running now is titled with today`() {
        // A statement printed on the 18th and headed "till 31 August" claims a
        // fortnight that has not happened, and the customer reading it would
        // take the balance as final with a week of deliveries still to come.
        val document = august(printedOn = Instant.parse("2026-08-18T09:00:00Z"))

        assertTrue(document.summaryTitle.contains("18 August 2026"), document.summaryTitle)
    }

    @Test
    fun `a period picked ahead of today is dated from its own first day`() {
        // Rather than from a moment before it began. Nothing can be in it yet,
        // and "till 2 August" on a September statement reads as a bug.
        val document = august(printedOn = Instant.parse("2026-06-02T09:00:00Z"))

        assertTrue(document.summaryTitle.contains("1 August 2026"), document.summaryTitle)
    }

    /** One August bill, and a statement for August printed on whatever day. */
    private fun august(printedOn: Instant): StatementDocument {
        val store = store().aShop()
        store.addCustomer("Ahmed")
        store.saveBill(
            customer = "Ahmed",
            paid = 0.0,
            amount = 500.0,
            createdAt = Instant.parse("2026-08-10T09:00:00Z"),
            invoiceNo = "06011"
        )

        val statement = assertNotNull(
            store.statementForCustomer("ahmed", StatementPeriod.Month(Instant.parse("2026-08-10T00:00:00Z")))
        )
        return StatementDocument.make(statement, store.settings, english, now = printedOn)
    }
}
