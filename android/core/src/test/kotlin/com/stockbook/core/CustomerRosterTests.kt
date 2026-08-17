package com.stockbook.core

import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Statement
import com.stockbook.core.model.CustomerRecord
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Product
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The roster: customers as stored records, merged with what history says.
 *
 * The rule under all of it is that neither source may be lost. Somebody entered
 * during setup who has never bought anything is a customer; a name typed at the
 * counter that nobody added is a customer too.
 */
class CustomerRosterTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun shopWithProduct(): Pair<StockbookStore, Product> {
        val store = store()
        return store to store.addProduct("Cisa lock", 100, 60.0, 95.0)
    }

    @Test
    fun `a customer added at setup exists before they have ever bought anything`() {
        val store = store()

        store.addCustomer("Ahmed Contracting", "0500 111 222", "Al Khobar")

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("Ahmed Contracting", customer.name)
        assertEquals("ahmed contracting", customer.key)
        assertEquals(0, customer.billCount)
        assertEquals(0.0, customer.owed)
        assertEquals("0500 111 222", customer.phone)
        assertEquals("Al Khobar", customer.place)
        assertTrue(customer.isOnRoster)
        assertFalse(customer.hasHistory)
    }

    @Test
    fun `adding the same person twice corrects them rather than duplicating them`() {
        val store = store()

        store.addCustomer("Ahmed Contracting", "0500 111 222")
        // Same person, typed differently and with a better phone number.
        store.addCustomer("  ahmed contracting ", "0500 999 888")

        assertEquals(1, store.customerRecords.size)
        assertEquals(1, store.customers().size)
        assertEquals("0500 999 888", store.customerRecords[0].phone)
    }

    @Test
    fun `a blank name is not a customer`() {
        val store = store()

        assertNull(store.addCustomer("   "))
        assertTrue(store.customerRecords.isEmpty())
    }

    @Test
    fun `a field left blank is absent not an empty string`() {
        val store = store()

        store.addCustomer("Sami", phone = "", place = "   ")

        val record = assertNotNull(store.customerRecords.firstOrNull())
        assertNull(record.phone)
        assertNull(record.place)
    }

    @Test
    fun `a name only ever typed on a bill is still a customer`() {
        val (store, product) = shopWithProduct()

        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Walk-in Sami", null)

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("Walk-in Sami", customer.name)
        assertEquals(1, customer.billCount)
        assertFalse(customer.isOnRoster)
    }

    @Test
    fun `the roster's spelling wins over whatever was typed at the counter`() {
        val (store, product) = shopWithProduct()
        store.addCustomer("Ahmed Contracting")

        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "ahmed CONTRACTING", null)

        // One person, and shown the way somebody deliberately typed it.
        assertEquals(1, store.customers().size)
        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("Ahmed Contracting", customer.name)
        assertEquals(1, customer.billCount)
    }

    @Test
    fun `removing a customer keeps their bills`() {
        val (store, product) = shopWithProduct()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", null)

        store.removeCustomer("ahmed")

        assertTrue(store.customerRecords.isEmpty())
        assertEquals(1, store.bills.size, "a bill is history and is never deleted")
        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals(1, customer.billCount)
        assertFalse(customer.isOnRoster)
    }

    @Test
    fun `correcting a spelling that keeps the same person keeps their bills together`() {
        val (store, product) = shopWithProduct()
        store.addCustomer("ahmed")
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "ahmed", null)

        store.updateCustomer("ahmed", "Ahmed", "0500 111 222", null)

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("Ahmed", customer.name)
        assertEquals("ahmed", customer.key, "case alone is not a different person")
        assertEquals(1, customer.billCount)
        assertEquals("0500 111 222", customer.phone)
    }

    /**
     * The one case where a saved bill is edited, and it has to be: the alternative
     * is a roster entry and its own bills never meeting again.
     */
    @Test
    fun `a real rename follows through onto the bills and payments`() {
        val (store, product) = shopWithProduct()
        store.addCustomer("Ahmed")
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", 0.0)
        store.recordPayment("ahmed", 40.0)

        store.updateCustomer("ahmed", "Ahmed Contracting", null, null)

        assertEquals(1, store.customers().size, "not one customer with the bills and another with the name")
        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("ahmed contracting", customer.key)
        assertEquals(1, customer.billCount)
        assertEquals("Ahmed Contracting", store.bills[0].who)
        assertEquals("ahmed contracting", store.payments[0].customerKey)
        // 95 owed, 40 paid.
        assertEquals(55.0, customer.owed)
    }

    @Test
    fun `renaming onto somebody already there merges rather than duplicating`() {
        val store = store()
        store.addCustomer("Ahmed")
        store.addCustomer("Ahmed Contracting")

        store.updateCustomer("ahmed", "Ahmed Contracting", "0500 111 222", null)

        assertEquals(1, store.customerRecords.size)
        assertEquals(1, store.customers().size)
    }
}

/** Payments, and the figure they exist to move. */
class PaymentTests {

    /** Bought 900, paid 500 at the counter: owes 400. */
    private fun shopWithDebt(): StockbookStore {
        val store = StockbookStore(InMemoryRepository())
        val product = store.addProduct("Cisa lock", 100, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 10, 90.0)), "Ahmed", 500.0)
        return store
    }

    @Test
    fun `a payment brings down what the customer owes`() {
        val store = shopWithDebt()
        assertEquals(400.0, assertNotNull(store.customers().firstOrNull()).owed)

        store.recordPayment("ahmed", 150.0)

        assertEquals(250.0, assertNotNull(store.customers().firstOrNull()).owed)
    }

    @Test
    fun `paying in full clears the outstanding total`() {
        val store = shopWithDebt()

        store.recordPayment("ahmed", 400.0)

        assertEquals(0.0, store.customers().firstOrNull()?.owed)
    }

    @Test
    fun `nothing typed is not a payment`() {
        val store = shopWithDebt()

        assertNull(store.recordPayment("ahmed", 0.0))
        assertNull(store.recordPayment("ahmed", -50.0))
        assertTrue(store.payments.isEmpty())
    }

    @Test
    fun `a payment with no customer goes nowhere`() {
        val store = shopWithDebt()

        assertNull(store.recordPayment("", 100.0))
    }

    @Test
    fun `a deleted payment puts the balance back`() {
        val store = shopWithDebt()
        val payment = assertNotNull(store.recordPayment("ahmed", 150.0))

        store.deletePayment(payment.id)

        assertEquals(400.0, assertNotNull(store.customers().firstOrNull()).owed)
        assertTrue(store.payments.isEmpty())
    }

    @Test
    fun `payments survive a relaunch`() {
        val repository = InMemoryRepository()
        val store = StockbookStore(repository)
        val product = store.addProduct("Cisa lock", 10, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Ahmed", 0.0)
        store.addCustomer("Ahmed", "0500 111 222")
        store.recordPayment("ahmed", 20.0, note = "cash")

        val reopened = StockbookStore(repository)

        assertEquals(1, reopened.payments.size)
        assertEquals("cash", reopened.payments[0].note)
        assertEquals(1, reopened.customerRecords.size)
        val customer = assertNotNull(reopened.customers().firstOrNull())
        assertEquals(75.0, customer.owed)
        assertEquals("0500 111 222", customer.phone)
    }

    @Test
    fun `a statement comes off the store for a real customer and null for a stranger`() {
        val store = shopWithDebt()
        store.recordPayment("ahmed", 100.0)

        val statement = assertNotNull(store.statementForCustomer("ahmed", StatementPeriod.thisYear()))
        assertEquals("ahmed", statement.party.key)
        assertEquals(300.0, statement.closingBalance)

        assertNull(store.statementForCustomer("nobody", StatementPeriod.thisYear()))
    }
}

/**
 * What happens to a shop that already exists when the update lands.
 *
 * Cheaper to guarantee here than on iOS: kotlinx.serialization applies a
 * property's default when a key is missing, where Swift's synthesised decoder
 * throws and needed one written by hand. The test is still worth having — the
 * guarantee is the same either way, and only a test says which behaviour is real.
 */
class RosterMigrationTests {

    /** The exact shape written before customers and payments existed. */
    private val shopFileBeforeRoster = """
    {
      "bills": [
        {
          "createdAt": "2026-07-28T09:41:00Z",
          "lines": [{ "name": "Cisa lock", "price": 90.0, "qty": 2 }],
          "number": 1,
          "paid": 100.0,
          "total": 180.0,
          "voided": false,
          "who": "Ahmed Contracting"
        }
      ],
      "products": [
        { "cost": 60.0, "name": "Cisa lock", "price": 95.0, "stock": 12,
          "uid": "6c3e4e9a-1f44-4b0e-9e4b-6d8a2c1b7f01", "createdAt": "2026-07-01T08:00:00Z" }
      ],
      "settings": {
        "currencyCode": "SAR",
        "lowStockAt": 40,
        "nextBillNumber": 2,
        "ownerName": "Khalid Al-Amri",
        "setupCompleted": true
      }
    }
    """.trimIndent()

    @Test
    fun `a shop saved before the roster existed still opens with everything in it`() {
        val state = BackupService.json.decodeFromString<ShopState>(shopFileBeforeRoster)

        assertEquals(1, state.products.size)
        assertEquals(1, state.bills.size)
        assertEquals("Khalid Al-Amri", state.settings.ownerName)
        assertTrue(state.customers.isEmpty())
        assertTrue(state.payments.isEmpty())
    }

    @Test
    fun `their customers still exist derived from the bills as before`() {
        val state = BackupService.json.decodeFromString<ShopState>(shopFileBeforeRoster)
        val store = StockbookStore(InMemoryRepository(state))

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals("Ahmed Contracting", customer.name)
        assertEquals(1, customer.billCount)
        assertEquals(80.0, customer.owed, "180 billed, 100 paid")
        assertFalse(customer.isOnRoster)
        assertNull(customer.phone)
    }

    @Test
    fun `a backup carries the roster and the payments to the new phone`() {
        val store = StockbookStore(InMemoryRepository())
        store.setOwnerName("Khalid Al-Amri")
        val product = store.addProduct("Cisa lock", 12, 60.0, 95.0)
        store.saveBill(listOf(DraftLine(product.uid, 2, 90.0)), "Ahmed Contracting", 100.0)
        store.addCustomer("Ahmed Contracting", "0500 111 222", "Al Khobar")
        store.recordPayment("ahmed contracting", 30.0, note = "cash")

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))

        assertEquals(BackupDocument.currentVersion, document.version)
        assertEquals(1, document.customers?.size)
        assertEquals(1, document.payments?.size)

        val restored = StockbookStore(InMemoryRepository())
        restored.replaceEverything(document)

        val customer = assertNotNull(restored.customers().firstOrNull())
        assertEquals("Ahmed Contracting", customer.name)
        assertEquals("0500 111 222", customer.phone)
        assertEquals("Al Khobar", customer.place)
        assertTrue(customer.isOnRoster)
        // 180 billed, 100 at the counter, 30 after: 50 left.
        assertEquals(50.0, customer.owed)
    }

    @Test
    fun `an imported customer keeps the key the file recorded`() {
        val store = StockbookStore(InMemoryRepository())
        store.addCustomer("Ahmed Contracting")
        val document = store.makeBackupDocument()

        assertEquals("ahmed contracting", assertNotNull(document.customers.firstOrNull()).key)

        val restored = StockbookStore(InMemoryRepository())
        restored.replaceEverything(document)
        assertEquals("ahmed contracting", restored.customerRecords.firstOrNull()?.key)
    }

    @Test
    fun `starting over clears the roster and the payments too`() {
        val store = StockbookStore(InMemoryRepository())
        store.addCustomer("Ahmed")
        store.recordPayment("ahmed", 10.0)

        store.startOver()

        assertTrue(store.customerRecords.isEmpty())
        assertTrue(store.payments.isEmpty())
        assertTrue(store.customers().isEmpty())
    }

    /**
     * The rows an iPhone writes, read here. The whole point of the shared format
     * is that a shop can move between the two platforms, and these two field sets
     * are the newest chance to have got that wrong.
     */
    @Test
    fun `the roster rows an iPhone writes decode here unchanged`() {
        val fromAnIPhone = """
        {
          "version": 1,
          "exportedAt": "2026-08-17T09:41:00Z",
          "ownerName": "Khalid Al-Amri",
          "currencyCode": "SAR",
          "products": [],
          "bills": [],
          "customers": [
            { "key": "ahmed contracting", "name": "Ahmed Contracting",
              "phone": "0500 111 222", "place": "Al Khobar",
              "createdAt": "2026-08-01T06:00:00Z" }
          ],
          "payments": [
            { "id": "9F1C7B22-4A55-4C31-9B77-0E2D5A6C8B10", "customerKey": "ahmed contracting",
              "amount": 30.0, "receivedAt": "2026-08-16T11:00:00Z", "note": "cash" }
          ]
        }
        """.trimIndent()

        val document = BackupService.decode(fromAnIPhone)
        val customer = assertNotNull(document.customers.firstOrNull())
        val payment = assertNotNull(document.payments.firstOrNull())

        assertEquals("ahmed contracting", customer.key)
        assertEquals("Al Khobar", customer.place)
        assertEquals(30.0, payment.amount)
        assertEquals("cash", payment.note)

        val store = StockbookStore(InMemoryRepository())
        store.replaceEverything(document)
        assertEquals(1, store.customerRecords.size)
        assertEquals(1, store.payments.size)
    }
}

/** The repository contract, extended to the two new record types. */
class RosterRepositoryTests {

    @Test
    fun `a customer is stored once per key however many times it is written`() {
        val repository = InMemoryRepository()

        repository.upsert(CustomerRecord.of("Ahmed", "0500 111 222"))
        repository.upsert(CustomerRecord.of("  ahmed ", "0500 999 888"))

        assertEquals(1, repository.loadAll().customers.size)
        assertEquals("0500 999 888", repository.loadAll().customers[0].phone)

        repository.deleteCustomer("ahmed")
        assertTrue(repository.loadAll().customers.isEmpty())
    }

    @Test
    fun `payments append and delete by id`() {
        val repository = InMemoryRepository()
        val first = Payment(customerKey = "ahmed", amount = 100.0)
        val second = Payment(customerKey = "ahmed", amount = 50.0)

        repository.append(first)
        repository.append(second)
        assertEquals(2, repository.loadAll().payments.size)

        repository.deletePayment(first.id)
        assertEquals(listOf(50.0), repository.loadAll().payments.map { it.amount })
    }
}

/**
 * The balance a customer brought over from the paper book.
 *
 * The reason this matters: a shop that starts using Stockbook on Monday has
 * customers who already owe from Sunday, and without this every one of them
 * starts at zero — which is the app telling the owner they are owed nothing.
 */
class OpeningBalanceTests {

    private val utc: java.time.ZoneId = java.time.ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int): java.time.Instant =
        java.time.LocalDate.of(year, month, day).atTime(12, 0).atZone(utc).toInstant()

    private fun store() = StockbookStore(InMemoryRepository())

    @Test
    fun `a customer who owed from before shows it with no bills at all`() {
        val store = store()

        store.addCustomer("Ahmed", openingBalance = 5000.0)

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals(5000.0, customer.owed)
        assertEquals(5000.0, customer.openingBalance)
        assertEquals(0, customer.billCount, "owing money is not the same as having bought something here")
    }

    @Test
    fun `it is never negative`() {
        val store = store()

        store.addCustomer("Ahmed", openingBalance = -200.0)

        assertEquals(0.0, assertNotNull(store.customers().firstOrNull()).openingBalance)
    }

    @Test
    fun `bills and payments stack on top of it`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 100, 60.0, 95.0)
        store.addCustomer("Ahmed", openingBalance = 1000.0)
        // Bought 190, paid nothing at the counter.
        store.saveBill(listOf(DraftLine(product.uid, 2, 95.0)), "Ahmed", 0.0)
        store.recordPayment("ahmed", 300.0)

        // 1000 carried over + 190 billed − 300 paid.
        assertEquals(890.0, assertNotNull(store.customers().firstOrNull()).owed)
    }

    @Test
    fun `correcting it in the editor corrects what they owe`() {
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 5000.0)

        store.updateCustomer("ahmed", "Ahmed", null, null, openingBalance = 500.0)

        assertEquals(500.0, assertNotNull(store.customers().firstOrNull()).owed)
    }

    /**
     * It predates every bill, so it belongs to every period's brought-forward —
     * including a period that contains nothing else at all.
     */
    @Test
    fun `every statement period carries it forward`() {
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 700.0)

        val statement = assertNotNull(store.statementForCustomer("ahmed", StatementPeriod.thisMonth()))

        assertEquals(700.0, statement.openingBalance)
        assertEquals(700.0, statement.closingBalance)
        assertTrue(statement.isEmpty, "nothing happened this month, and they still owe")
    }

    @Test
    fun `the running balance still lands on the closing balance`() {
        val customer = Customer(
            name = "Ahmed", key = "ahmed", billCount = 0, total = 0.0, owed = 0.0,
            openingBalance = 1000.0, isOnRoster = true
        )
        val bill = Bill(
            number = 1,
            lines = listOf(BillLine(productUid = null, name = "Cisa lock", qty = 1, price = 250.0)),
            total = 250.0, paid = 0.0, who = "Ahmed", createdAt = at(2026, 8, 4)
        )
        val payment = Payment(customerKey = "ahmed", amount = 400.0, receivedAt = at(2026, 8, 20))

        val statement = Statement.make(
            customer = customer,
            bills = listOf(bill),
            payments = listOf(payment),
            period = StatementPeriod.Month(at(2026, 8, 10)),
            zone = utc
        )

        assertEquals(1000.0, statement.openingBalance)
        // 1000 + 250 − 400.
        assertEquals(850.0, statement.closingBalance)
        assertEquals(statement.closingBalance, statement.runningBalances.last())
    }

    /**
     * The Today banner. It used to walk bills directly, which meant it ignored
     * payments *and* opening balances — naming somebody who had settled up and
     * missing somebody who owed from before the app.
     */
    @Test
    fun `the outstanding banner counts what customers actually owe`() {
        val store = store()
        val product = store.addProduct("Cisa lock", 100, 60.0, 95.0)
        store.addCustomer("Ahmed", openingBalance = 1000.0)
        store.saveBill(listOf(DraftLine(product.uid, 1, 95.0)), "Sami", 0.0)
        // Sami settles up in full; Ahmed has never bought anything here.
        store.recordPayment("sami", 95.0)

        val (names, total) = store.outstanding()

        assertEquals(listOf("Ahmed"), names, "Sami has paid; Ahmed owes from the old book")
        assertEquals(1000.0, total)
    }

    @Test
    fun `a backup carries it, and a v2 file without it reads as nothing owed`() {
        val store = store()
        store.addCustomer("Ahmed", openingBalance = 5000.0)

        val document = BackupService.decode(BackupService.encode(store.makeBackupDocument()))
        assertEquals(BackupDocument.currentVersion, document.version)
        assertEquals(5000.0, assertNotNull(document.customers.firstOrNull()).openingBalance)

        val restored = store()
        restored.replaceEverything(document)
        assertEquals(5000.0, assertNotNull(restored.customers().firstOrNull()).owed)
    }

    /**
     * A row with no `openingBalance` reads as nothing owed rather than failing.
     *
     * Not a file from the past — there are none — but the shape a *future* field
     * will make of today's rows, and the reason every row field here carries a
     * default. iOS is stricter and would refuse this file; both builds write the
     * key always, so the difference stays theoretical.
     */
    @Test
    fun `a customer row without the field reads as nothing owed`() {
        val file = """
        {
          "version": 1,
          "exportedAt": "2026-08-17T09:41:00Z",
          "ownerName": "Khalid",
          "currencyCode": "SAR",
          "products": [],
          "bills": [],
          "customers": [
            { "key": "ahmed", "name": "Ahmed", "createdAt": "2026-08-01T06:00:00Z" }
          ]
        }
        """.trimIndent()

        val document = BackupService.decode(file)

        assertEquals(0.0, assertNotNull(document.customers.firstOrNull()).openingBalance)
    }
}
