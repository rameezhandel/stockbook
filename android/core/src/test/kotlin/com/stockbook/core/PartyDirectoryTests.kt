package com.stockbook.core

import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The directory: everybody the shop deals with, in the order somebody looks a
 * person up.
 *
 * Distinct from [StockbookStore.customers] and [StockbookStore.suppliers], which
 * hand their lists back biggest-debt-first — that is the order for a screen
 * asking *who owes me*, and exactly the wrong one for a screen asking *where is
 * Fatima*. Both orders are wanted and neither can be the other's default, which
 * is why there are two functions rather than a flag.
 */
class PartyDirectoryTests {

    private fun store() = StockbookStore(InMemoryRepository())

    private fun peopled(): StockbookStore {
        val store = store()
        store.addCustomer("Zainab Hardware", "0500 999 111", "Dammam")
        store.addCustomer("ahmed contracting", "0500 111 222", "Al Khobar")
        store.addCustomer("Fatima Noor", null, "Al Khobar")
        return store
    }

    @Test
    fun `an empty query is everybody`() {
        assertEquals(3, peopled().customers(matching = "").size)
    }

    @Test
    fun `the directory is sorted by name, whatever case it was typed in`() {
        // Not by what is owed: nobody here owes anything, and the order still has
        // to be useful. `ahmed` sorts first despite its lower-case a — sorting on
        // the raw string would put it last, behind every capitalised name.
        assertEquals(
            listOf("ahmed contracting", "Fatima Noor", "Zainab Hardware"),
            peopled().customers(matching = "").map { it.name }
        )
    }

    @Test
    fun `a query matches part of a name, in any case`() {
        assertEquals(
            listOf("Fatima Noor"),
            peopled().customers(matching = "noor").map { it.name }
        )
    }

    @Test
    fun `a query matches a phone number`() {
        // The other thing written on the paper. A shop owner holding a slip with a
        // number on it should not have to remember which spelling of the name
        // went with it.
        assertEquals(
            listOf("Zainab Hardware"),
            peopled().customers(matching = "999").map { it.name }
        )
    }

    @Test
    fun `whitespace is not a query`() {
        // The search box is empty far more often than it is full, and a stray
        // space typed with a customer waiting must not empty the screen.
        assertEquals(3, peopled().customers(matching = "   ").size)
    }

    @Test
    fun `a query that matches nobody returns nobody`() {
        assertTrue(peopled().customers(matching = "Ravi").isEmpty())
    }

    @Test
    fun `somebody with no phone is not matched by a numeric query`() {
        // Guards the null-handling: an absent phone must read as "no match", not
        // as an empty string that every query is a substring of.
        assertTrue(peopled().customers(matching = "0500").none { it.name == "Fatima Noor" })
    }

    @Test
    fun `suppliers have the same directory`() {
        val store = store()
        store.addSupplier("Riyadh Steel", "0555 000 111", "Riyadh")
        store.addSupplier("Al Fahad Trading", null, "Jeddah")

        assertEquals(
            listOf("Al Fahad Trading", "Riyadh Steel"),
            store.suppliers(matching = "").map { it.name }
        )
        assertEquals(
            listOf("Riyadh Steel"),
            store.suppliers(matching = "steel").map { it.name }
        )
    }

    @Test
    fun `the debt order is left alone`() {
        // The regression this guards: making the directory the default would have
        // silently reordered Today's banner and both owed sheets, which name the
        // biggest debtor first and would have started naming whoever came first
        // in the alphabet.
        val store = store()
        store.addCustomer("Zainab Hardware", openingBalance = 900.0)
        store.addCustomer("Ahmed Contracting", openingBalance = 100.0)

        assertEquals(listOf("Zainab Hardware", "Ahmed Contracting"), store.customers().map { it.name })
        assertEquals(listOf("Ahmed Contracting", "Zainab Hardware"), store.customers(matching = "").map { it.name })
    }
}
