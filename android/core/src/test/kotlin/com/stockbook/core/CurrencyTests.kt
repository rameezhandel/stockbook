package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.DraftLine
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.transfer.BackupDocument
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyTests {

    @Test
    fun `the symbol carries its own spacing`() {
        // Alphabetic codes get a space; glyphs do not. This is the whole reason
        // the spacing lives in the symbol rather than in Money.
        assertEquals("SAR 194", Money.text(194.0, Currency.SAR))
        assertEquals("₹194", Money.text(194.0, Currency.INR))
        assertEquals("$194", Money.text(194.0, Currency.USD))
    }

    @Test
    fun `minor units follow the currency`() {
        assertEquals("SAR 0.25", Money.text(0.25, Currency.SAR))
        // Three for the dinars and the rial. Rendering 0.125 as 0.13 in a shop
        // that bills in fils is an error, not a rounding preference.
        assertEquals("KWD 0.125", Money.text(0.125, Currency.KWD))
        assertEquals("BHD 0.125", Money.text(0.125, Currency.BHD))
        assertEquals("SAR 0.13", Money.text(0.125, Currency.SAR))
    }

    @Test
    fun `whole numbers never grow a decimal point`() {
        assertEquals("KWD 12", Money.text(12.0, Currency.KWD))
        assertEquals("KWD 1,240", Money.text(1240.0, Currency.KWD))
    }

    @Test
    fun `grouping is the app's rule, not the currency's`() {
        // An INR shop does not start seeing lakh grouping mid-bill.
        assertEquals("₹124,000", Money.text(124_000.0, Currency.INR))
        assertEquals("SAR 124,000", Money.text(124_000.0, Currency.SAR))
    }

    @Test
    fun `codes are unique, and so are symbols`() {
        val codes = Currency.supported.map { it.code }.toSet()
        val symbols = Currency.supported.map { it.symbol.trim() }.toSet()
        assertEquals(Currency.supported.size, codes.size)
        // Two currencies sharing a symbol would put an ambiguous mark on a bill,
        // which is the one place these strings are read by somebody with no
        // Settings screen in front of them.
        assertEquals(Currency.supported.size, symbols.size)
        assertTrue(Currency.default in Currency.supported)
    }

    @Test
    fun `an unknown code falls back rather than failing`() {
        assertEquals(Currency.default, Currency.named("ZZZ"))
        assertEquals(Currency.INR, Currency.named("INR"))
    }

    @Test
    fun `changing currency does not convert what is already saved`() {
        val store = StockbookStore(InMemoryRepository())
        val product = store.addProduct("Padlock", 5, 10.0, 25.0)
        store.saveBill(listOf(DraftLine(product.uid, 2, 25.0)), "Ahmed", null)

        store.setCurrency(Currency.INR)

        // The numbers are the owner's; only the symbol in front of them moved.
        assertEquals(50.0, store.bills.first().total)
        assertEquals(25.0, store.products.first().price)
        assertEquals("₹50", Money.text(50.0, store.settings.currency))
    }

    @Test
    fun `a backup carries its currency to the new phone`() {
        val store = StockbookStore(InMemoryRepository())
        store.setCurrency(Currency.SAR)

        // Unlike the language, the currency belongs to the numbers in the file:
        // those prices were entered in it.
        store.replaceEverything(
            BackupDocument(
                exportedAt = Instant.now(),
                ownerName = "Khalid",
                currencyCode = "INR",
                products = emptyList(),
                bills = emptyList()
            )
        )

        assertEquals(Currency.INR, store.settings.currency)
    }

    @Test
    fun `export names the currency by its code`() {
        val store = StockbookStore(InMemoryRepository())
        store.setCurrency(Currency.KWD)

        // The code, and only the code. The symbol used to be written beside it
        // for a build that could not read one; there is no such build.
        assertEquals("KWD", store.makeBackupDocument().currencyCode)
    }
}
