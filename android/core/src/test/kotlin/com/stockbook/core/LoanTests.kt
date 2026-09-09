package com.stockbook.core

import com.stockbook.core.model.Loan
import com.stockbook.core.model.Statement
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DayEntryKind
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupService
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Strings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cash the shop lends a customer.
 *
 * Half of what is asserted here is that a loan is **not** the other things it
 * resembles — not a sale, not an expense, not a payment with a sign on it. A rule
 * of the form "these two must never meet" decays silently, which is why it is
 * written down as a test rather than left in a comment.
 */
class LoanTests {

    private val day: Instant = Instant.parse("2026-09-08T09:00:00Z")
    private val strings = Strings(AppLanguage.ENGLISH)

    private fun shop(): StockbookStore {
        val store = StockbookStore(InMemoryRepository())
        store.addCustomer("Ahmed")
        return store
    }

    // --- What it does to the balance

    /**
     * The whole feature in one assertion: lending 400 to somebody owing 800
     * leaves them owing 1,200.
     *
     * One debt, not two. Goods owed and cash lent land on the same balance,
     * because there is no second balance anywhere in this app to put it in and
     * an owner asking what Ahmed owes wants one number.
     */
    @Test
    fun `a loan adds to what the customer owes`() {
        val store = shop()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 800.0, createdAt = day)

        store.recordLoan("ahmed", 400.0, lentAt = day)

        assertEquals(1_200.0, store.customer("ahmed")?.owed)
    }

    /** Repayment is an ordinary payment. Nothing new was needed for it. */
    @Test
    fun `an ordinary payment settles a loan`() {
        val store = shop()
        store.recordLoan("ahmed", 400.0, lentAt = day)

        store.recordPayment("ahmed", 400.0, receivedAt = day.plusSeconds(86_400))

        assertEquals(0.0, store.customer("ahmed")?.owed)
    }

    /**
     * Somebody the owner has only ever lent money to still appears.
     *
     * The seeding trap: applying loans with `book[key]?.let` alone would drop a
     * customer who has no bill and no roster entry, without a sound. That is not
     * a rare case — it is the first loan to a new face, and it is exactly how the
     * credit notes were once stranded by a rename.
     */
    @Test
    fun `a customer known only by a loan is still on the books`() {
        val store = StockbookStore(InMemoryRepository())

        store.recordLoan("khalid", 250.0, lentAt = day)

        val khalid = store.customers().firstOrNull { it.key == "khalid" }
        assertNotNull(khalid, "somebody who has only ever borrowed is still a customer")
        assertEquals(250.0, khalid.owed)
    }

    // --- What it is not

    /**
     * Not a sale. Nothing left the shelf and the shop earned nothing.
     *
     * Half of `ExpenseTests` asserts absences for this reason and so does this:
     * a loan that crept into takings would flatter every month it happened in.
     */
    @Test
    fun `a loan is not a sale and earns nothing`() {
        val store = shop()
        val period = StatementPeriod.Month(day)

        store.recordLoan("ahmed", 400.0, lentAt = day)

        assertEquals(0.0, store.soldIn(period), "not takings")
        assertEquals(0.0, store.earningsIn(period).sold, "not revenue")
        assertEquals(0.0, store.spentIn(period), "and not the owner's spending either")
        assertEquals(0, store.billCountIn(period), "no bill was written")
    }

    /**
     * Not an expense. An expense is gone; this is expected back.
     *
     * They are both money out and they must not be added together anywhere — the
     * shop that treated a loan as spending would report a month's costs it never
     * had.
     */
    @Test
    fun `lending stands apart from spending`() {
        val store = shop()
        val period = StatementPeriod.Month(day)
        store.addExpense(60.0, "Petrol", day)
        store.recordLoan("ahmed", 400.0, lentAt = day)

        assertEquals(60.0, store.spentIn(period))
        assertEquals(400.0, store.lentIn(period))
    }

    /** Neither zero nor a negative figure is a loan. A negative one is a payment. */
    @Test
    fun `a loan has to be money, and has to be for somebody`() {
        val store = shop()

        assertNull(store.recordLoan("ahmed", 0.0, lentAt = day))
        assertNull(store.recordLoan("ahmed", -400.0, lentAt = day))
        assertNull(store.recordLoan("", 400.0, lentAt = day))
        assertTrue(store.loans.isEmpty())
    }

    // --- On the statement

    /**
     * A loan charges the account and settles nothing — the mirror of a payment,
     * which settles and charges nothing.
     */
    @Test
    fun `the statement shows a loan as a charge that settles nothing`() {
        val store = shop()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 800.0, createdAt = day)
        store.recordLoan("ahmed", 400.0, lentAt = day.plusSeconds(3_600))

        val statement = store.statementForCustomer("ahmed", StatementPeriod.Month(day))
        assertNotNull(statement)

        val loan = statement.entries.filterIsInstance<Statement.Entry.ForLoan>().single()
        assertEquals(400.0, loan.charge)
        assertEquals(0.0, loan.settledAtOnce)
        assertEquals(1_200.0, statement.closingBalance)
    }

    /**
     * **Billed says what was billed.** Eight hundred of goods and four hundred
     * lent is not "billed 1,200" — the customer never got an invoice for the
     * cash, and would be right to query one.
     *
     * This is why a loan is its own `Entry.Kind` rather than another TRADE entry.
     */
    @Test
    fun `a loan is kept out of what was billed`() {
        val store = shop()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 800.0, createdAt = day)
        store.recordLoan("ahmed", 400.0, lentAt = day.plusSeconds(3_600))

        val statement = store.statementForCustomer("ahmed", StatementPeriod.Month(day))!!

        assertEquals(800.0, statement.billed, "goods only")
        assertEquals(400.0, statement.lent, "and the cash on its own line")
        assertEquals(0.0, statement.received)
    }

    /** No number to print, because a loan comes out of no numbered book. */
    @Test
    fun `a loan row is named rather than numbered`() {
        val store = shop()
        store.recordLoan("ahmed", 400.0, lentAt = day)
        val statement = store.statementForCustomer("ahmed", StatementPeriod.Month(day))!!

        val row = statement.entries.single()
        assertEquals("Loan", com.stockbook.core.text.StatementDocument.reference(row, strings))
    }

    // --- On the day

    /**
     * Money out of the cash box on the day it left it, however certain the owner
     * is of getting it back. The day's net is what the box did.
     */
    @Test
    fun `a loan is money out on the day it was handed over`() {
        val store = shop()
        store.recordPayment("ahmed", 100.0, receivedAt = day)
        store.recordLoan("ahmed", 400.0, lentAt = day.plusSeconds(60))

        val book = store.dayBook(day)

        assertTrue(DayEntryKind.LOAN in book.entries.map { it.kind })
        assertEquals(100.0, book.moneyIn)
        assertEquals(400.0, book.moneyOut)
        assertEquals(-300.0, book.net)
    }

    // --- Correcting one

    @Test
    fun `a loan can be corrected and removed, and the balance follows`() {
        val store = shop()
        val loan = store.recordLoan("ahmed", 400.0, lentAt = day)!!

        store.updateLoan(loan.id, amount = 250.0, lentAt = day)
        assertEquals(250.0, store.customer("ahmed")?.owed)

        store.deleteLoan(loan.id)
        assertEquals(0.0, store.customer("ahmed")?.owed)
        assertTrue(store.loans.isEmpty())
    }

    // --- Across the wire

    /**
     * A loan survives export and import, and the file says version 5.
     *
     * The bump is the point: a reader built before loans existed would drop them
     * and show every borrower owing less than they do, so it must refuse the file
     * instead. That is the credit-note rule pointing the other way.
     */
    @Test
    fun `loans travel in the backup, which is version 5`() {
        val store = shop()
        store.saveBill(customer = "Ahmed", paid = 0.0, amount = 800.0, createdAt = day)
        store.recordLoan("ahmed", 400.0, lentAt = day, note = "for the school fees")

        val document = store.makeBackupDocument(day)
        assertEquals(5, document.version)

        val restored = StockbookStore(InMemoryRepository())
        restored.replaceEverything(BackupService.decode(BackupService.encode(document)))

        val loan: Loan = restored.loans.single()
        assertEquals(400.0, loan.amount)
        assertEquals("ahmed", loan.customerKey)
        assertEquals("for the school fees", loan.note)
        assertEquals(1_200.0, restored.customer("ahmed")?.owed, "and the balance comes back with it")
    }

    // --- Finding one

    @Test
    fun `a loan can be searched for by the borrower's name`() {
        val store = shop()
        store.recordLoan("ahmed", 400.0, lentAt = day)

        val hit = store.search("ahmed").single { it.kind == DayEntryKind.LOAN }

        assertEquals(400.0, hit.amount)
        assertEquals("Ahmed", hit.who)
        assertNull(hit.reference, "there is no number to show")
    }
}
