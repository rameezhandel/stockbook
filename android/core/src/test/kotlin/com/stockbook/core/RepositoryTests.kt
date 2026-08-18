package com.stockbook.core

import com.stockbook.core.model.Bill
import com.stockbook.core.model.BillLine
import com.stockbook.core.model.Product
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.JsonFileRepository
import com.stockbook.core.store.StockbookRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One contract, run against every implementation.
 *
 * This suite is the reason the storage seam is safe to swap. A new backing —
 * Room, SQLite, anything — adds one line here and inherits every guarantee,
 * rather than being trusted to have got them right on its own.
 */
class RepositoryTests {

    private fun each(work: (String, StockbookRepository) -> Unit) {
        work("in memory", InMemoryRepository())
        val directory = Files.createTempDirectory("stockbook").toFile()
        work("json file", JsonFileRepository(directory.resolve("shop.json")))
        directory.deleteRecursively()
    }

    private fun product(name: String = "Padlock") =
        Product(name = name, stock = 5, cost = 10.0, price = 25.0)

    @Test
    fun `an empty store opens rather than failing`() = each { name, repository ->
        val state = repository.loadAll()
        assertTrue(state.products.isEmpty(), name)
        assertTrue(state.bills.isEmpty(), name)
    }

    @Test
    fun `upsert adds then replaces`() = each { name, repository ->
        val first = product()
        repository.upsert(first)
        assertEquals(1, repository.loadAll().products.size, name)

        repository.upsert(first.copy(stock = 99))
        assertEquals(1, repository.loadAll().products.size, name)
        assertEquals(99, repository.loadAll().products.first().stock, name)
    }

    @Test
    fun `delete removes only its own product`() = each { name, repository ->
        val keep = product("Keep")
        val drop = product("Drop")
        repository.upsert(keep)
        repository.upsert(drop)

        repository.delete(drop.uid)

        assertEquals(listOf("Keep"), repository.loadAll().products.map { it.name }, name)
    }

    @Test
    fun `a bill can be appended then updated in place`() = each { name, repository ->
        val bill = Bill(
            number = 1,
            lines = listOf(BillLine(null, "Padlock", 1, 25.0)),
            total = 25.0,
            who = "Ahmed"
        )
        repository.append(bill)
        assertEquals(1, repository.loadAll().bills.size, name)

        repository.update(bill.copy(who = "Ahmed Contracting"))
        assertEquals(1, repository.loadAll().bills.size, name)
        assertEquals("Ahmed Contracting", repository.loadAll().bills.first().who, name)

        repository.deleteBill(bill.number)
        assertTrue(repository.loadAll().bills.isEmpty(), name)
    }

    @Test
    fun `replaceAll is a swap, not a merge`() = each { name, repository ->
        repository.upsert(product("Old"))
        repository.replaceAll(ShopState(products = listOf(product("New"))))

        assertEquals(listOf("New"), repository.loadAll().products.map { it.name }, name)
    }

    @Test
    fun `the file survives being closed and reopened`() {
        val directory = Files.createTempDirectory("stockbook").toFile()
        val file = directory.resolve("shop.json")
        val saved = product()

        JsonFileRepository(file).upsert(saved)

        // A fresh instance, so nothing is being answered from a cache.
        val reopened = JsonFileRepository(file).loadAll()
        assertEquals(1, reopened.products.size)
        assertEquals(saved.uid, reopened.products.first().uid)

        directory.deleteRecursively()
    }

    @Test
    fun `an unreadable file is not silently swallowed`() {
        val directory = Files.createTempDirectory("stockbook").toFile()
        val file = directory.resolve("shop.json")
        file.writeText("{ this is not json")

        var threw = false
        try {
            JsonFileRepository(file).loadAll()
        } catch (_: Exception) {
            threw = true
        }
        // Failing loudly beats opening an empty shop over the top of one that
        // exists — there is no server to recover the real contents from.
        assertTrue(threw)

        directory.deleteRecursively()
    }
}
