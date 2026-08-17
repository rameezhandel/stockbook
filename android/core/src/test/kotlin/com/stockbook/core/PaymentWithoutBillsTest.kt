package com.stockbook.core

import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A payment from somebody who has never been billed here.
 *
 * The case every other payment test missed, because each of them sold the
 * customer something first. On a fresh shop the ordinary path is the opposite:
 * a customer is entered with what they owed from the old book, and the first
 * thing that ever happens to them is a payment.
 */
class PaymentWithoutBillsTest {

    @Test
    fun `a payment counts for a customer who has no bills at all`() {
        val store = StockbookStore(InMemoryRepository())
        store.addCustomer("Ahmed", openingBalance = 1000.0)

        store.recordPayment("ahmed", 400.0)

        val customer = assertNotNull(store.customers().firstOrNull())
        assertEquals(600.0, customer.owed)
    }

    @Test
    fun `and it reaches the statement and the outstanding banner too`() {
        val store = StockbookStore(InMemoryRepository())
        store.addCustomer("Ahmed", openingBalance = 1000.0)

        store.recordPayment("ahmed", 1000.0)

        assertEquals(0.0, assertNotNull(store.customers().firstOrNull()).owed)
        val (names, total) = store.outstanding()
        assertEquals(emptyList(), names, "they have settled up")
        assertEquals(0.0, total)
    }
}
