package com.stockbook.core

import com.stockbook.core.model.Settings
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.PaymentReceiptDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The slip handed over when somebody settles up.
 *
 * The claim being pinned here is a narrow one and it is the whole feature: **the
 * balance on the receipt is the balance on the statement.** Not equal to it —
 * *the same figure*, lifted out of the same calculation. A customer holding a
 * receipt saying 550 and a statement saying 650 has no way to tell which of the
 * two the shop believes, and neither has the shop.
 *
 * The rest asserts the wording, which flips with direction: a shop paying its
 * own supplier receives nothing, and that page has to read from the right end.
 */
class PaymentReceiptTests {

    private fun at(day: Int): Instant = Instant.parse("2026-08-%02dT09:00:00Z".format(day))

    private val strings = Strings(AppLanguage.ENGLISH)
    private val settings = Settings(ownerName = "Al Salam Hardware")

    private fun shop(): Pair<StockbookStore, String> {
        val store = StockbookStore(InMemoryRepository())
        val lock = store.addProduct("Cisa lock", 500, 60.0, 95.0)
        return store to lock.uid
    }

    @Test
    fun `the balance on the receipt is the balance beside it on the statement`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        val payment = assertNotNull(store.recordPayment("ahmed", 300.0, receivedAt = at(12)))

        val receipt = assertNotNull(store.receiptForPayment(payment.id))

        assertEquals(650.0, receipt.balanceAfter, "950 billed, 300 paid")
        assertEquals(950.0, receipt.balanceBefore, "what the account stood at a minute earlier")
        assertEquals(300.0, receipt.amount)
        assertEquals("Ahmed", receipt.party.name)

        // The same figure, read out of the other document. This is the assertion
        // the feature exists to keep true.
        val statement = assertNotNull(
            store.statementForCustomer("ahmed", com.stockbook.core.model.StatementPeriod.Custom(at(1), at(20)))
        )
        val row = statement.entries.indexOfFirst { it.id == "payment-${payment.id}" }
        assertEquals(statement.runningBalances[row], receipt.balanceAfter)
    }

    /**
     * A receipt written for money that arrived before anything was billed.
     *
     * The balance goes negative, and it must be allowed to: this app reads a
     * negative balance as money held in advance, and a receipt that clamped it at
     * zero would be telling somebody who paid ahead that they are square.
     */
    @Test
    fun `money paid in advance leaves the account in credit`() {
        val (store, _) = shop()
        store.addCustomer("Fatima")
        val payment = assertNotNull(store.recordPayment("fatima", 200.0, receivedAt = at(3)))

        val receipt = assertNotNull(store.receiptForPayment(payment.id))

        assertEquals(0.0, receipt.balanceBefore)
        assertEquals(-200.0, receipt.balanceAfter)
    }

    @Test
    fun `a payment that is not there has no receipt`() {
        val (store, _) = shop()
        store.addCustomer("Ahmed")
        val payment = assertNotNull(store.recordPayment("ahmed", 50.0, receivedAt = at(4)))

        store.deletePayment(payment.id)

        assertNull(store.receiptForPayment(payment.id), "the record is gone, so the slip is too")
        assertNull(store.receiptForPayment("not-an-id"))
    }

    /**
     * The range the statement is read over is stretched to hold the payment.
     *
     * Two ways it would otherwise miss: the shop's earliest record does not look
     * at supplier payments at all, and nothing stops a date being picked in the
     * future. Either would leave the payment outside its own statement and hand
     * back nothing.
     */
    @Test
    fun `a supplier voucher works when the payment is the only record in the shop`() {
        val (store, _) = shop()
        store.addSupplier("Gulf Traders")
        val payment = assertNotNull(store.recordSupplierPayment("gulf traders", 700.0, paidAt = at(6)))

        val receipt = assertNotNull(store.receiptForSupplierPayment(payment.id))

        assertTrue(receipt.party.isSupplier)
        assertEquals(700.0, receipt.amount)
        assertEquals(-700.0, receipt.balanceAfter, "paid ahead of any delivery")
    }

    @Test
    fun `a payment dated ahead of today is still inside its own statement`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 4, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        val ahead = Instant.now().plusSeconds(60 * 60 * 24 * 30)
        val payment = assertNotNull(store.recordPayment("ahmed", 100.0, receivedAt = ahead))

        val receipt = assertNotNull(store.receiptForPayment(payment.id))

        assertEquals(280.0, receipt.balanceAfter)
    }

    // --- The document

    @Test
    fun `the customer's slip is a receipt and reads from their end`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        val payment = assertNotNull(
            store.recordPayment("ahmed", 300.0, receivedAt = at(12), note = "cheque 4471", paymentNo = "008455")
        )

        val document = PaymentReceiptDocument.make(
            assertNotNull(store.receiptForPayment(payment.id)),
            settings,
            strings
        )

        assertEquals("Payment Receipt", document.docType)
        assertEquals("Received from:", document.addressedToLabel)
        assertEquals("Amount received", document.amountLabel)
        assertEquals("008455", document.receiptValue)
        assertEquals("Al Salam Hardware", document.shopName)
        assertEquals("cheque 4471", document.noteValue)
        assertEquals(strings.paymentNote, document.noteLabel)
    }

    @Test
    fun `the supplier's slip is a voucher and reads from the other end`() {
        val (store, _) = shop()
        store.addSupplier("Gulf Traders")
        val payment = assertNotNull(store.recordSupplierPayment("gulf traders", 700.0, paidAt = at(6)))

        val document = PaymentReceiptDocument.make(
            assertNotNull(store.receiptForSupplierPayment(payment.id)),
            settings,
            strings
        )

        assertEquals("Payment Voucher", document.docType)
        assertEquals("Paid to:", document.addressedToLabel)
        assertEquals("Amount paid", document.amountLabel)
        assertEquals("Paid on", document.dateLabel)
    }

    /**
     * Three lines, whatever happened. The statement leaves out what did not
     * happen; a receipt cannot, because it is read on its own — a slip missing
     * the previous balance is one somebody has to fetch a statement to
     * understand.
     */
    @Test
    fun `the summary is always previous balance, this receipt, and what is left`() {
        val (store, lock) = shop()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(lock, 10, 95.0)), "Ahmed", paid = 0.0, createdAt = at(10))
        val payment = assertNotNull(store.recordPayment("ahmed", 300.0, receivedAt = at(12)))

        val document = PaymentReceiptDocument.make(
            assertNotNull(store.receiptForPayment(payment.id)),
            settings,
            strings
        )

        assertEquals(2, document.summaryRows.size)
        assertEquals("Previous balance", document.summaryRows[0].label)
        assertEquals("SAR 950", document.summaryRows[0].value)
        assertEquals("Amount received", document.summaryRows[1].label)
        assertTrue(document.summaryRows[1].deduction, "the one line that comes off")
        assertEquals("Balance now", document.closingLabel)
        assertEquals("SAR 650", document.closingValue)
    }

    /**
     * A payment entered before the receipt field existed still prints a document.
     * An empty box on a numbered slip reads as a printing fault.
     */
    @Test
    fun `a payment with no number says so rather than leaving a gap`() {
        val (store, _) = shop()
        store.addCustomer("Ahmed")
        val payment = assertNotNull(store.recordPayment("ahmed", 50.0, receivedAt = at(4)))

        val document = PaymentReceiptDocument.make(
            assertNotNull(store.receiptForPayment(payment.id)),
            settings,
            strings
        )

        assertEquals("—", document.receiptValue)
        assertNull(document.noteLabel, "no note, so no label for one")
        assertNull(document.noteValue)
    }

    @Test
    fun `the shop's address is printed line by line, blanks dropped`() {
        val (store, _) = shop()
        store.addCustomer("Ahmed")
        val payment = assertNotNull(store.recordPayment("ahmed", 50.0, receivedAt = at(4)))

        val document = PaymentReceiptDocument.make(
            assertNotNull(store.receiptForPayment(payment.id)),
            settings.copy(shopAddress = "King Fahd Road\n\nAl Khobar"),
            strings
        )

        assertEquals(listOf("King Fahd Road", "Al Khobar"), document.shopAddressLines)
    }
}
