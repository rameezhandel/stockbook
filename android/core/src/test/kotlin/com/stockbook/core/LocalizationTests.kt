package com.stockbook.core

import com.stockbook.core.model.Currency
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.AppTab
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupError
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The guarantees that make a second language safe to ship.
 *
 * None of this can be checked by looking at a screen — a missing translation
 * looks like an English word on a Kannada screen, which is exactly what a
 * reviewer who does not read Kannada will skim past.
 *
 * The iOS twin of this suite carries a hand-written list of every string, which
 * somebody has to remember to extend. Reflection makes that impossible to
 * forget: a string added in English only fails here the moment it exists.
 */
class LocalizationTests {

    private val english = Strings(AppLanguage.ENGLISH)
    private val kannada = Strings(AppLanguage.KANNADA)

    @Suppress("UNCHECKED_CAST")
    private val everyString: List<KProperty1<Strings, String>> =
        Strings::class.memberProperties
            .filter { it.returnType.classifier == String::class }
            .map { it as KProperty1<Strings, String> }
            .sortedBy { it.name }

    @Test
    fun `the table is found by reflection at all`() {
        // A guard on the guard: if this ever reads zero, every test below passes
        // vacuously and says nothing.
        assertTrue(everyString.size > 100, "found only ${everyString.size} strings")
    }

    @Test
    fun `every string is written in both languages`() {
        for (property in everyString) {
            val en = property.get(english)
            val kn = property.get(kannada)
            assertTrue(en.isNotEmpty(), property.name)
            assertTrue(kn.isNotEmpty(), property.name)
            // Identical text in both columns is the signature of a line that was
            // copied and never translated.
            assertNotEquals(en, kn, "untranslated: ${property.name} — “$en”")
        }
    }

    @Test
    fun `Kannada is written in Kannada`() {
        val kannadaRange = 0x0C80..0x0CFF
        for (property in everyString) {
            val text = property.get(kannada)
            assertTrue(
                text.any { it.code in kannadaRange },
                "no Kannada letters in ${property.name} — “$text”"
            )
        }
    }

    @Test
    fun `counts read correctly at zero, one and many`() {
        assertEquals("1 product", english.products(1))
        assertEquals("0 products", english.products(0))
        assertEquals("4 bills", english.bills(4))
        assertEquals("1 piece", english.pieces(1))
        assertTrue(english.stillNeedPrices(1).startsWith("1 item still needs"))
        assertTrue(english.stillNeedPrices(3).startsWith("3 items still need"))

        // Kannada does not inflect for number here; what matters is that the
        // count reaches the sentence at all.
        for (n in listOf(0, 1, 7)) {
            assertTrue(kannada.products(n).contains("$n"))
            assertTrue(kannada.bills(n).contains("$n"))
            assertTrue(kannada.pieces(n).contains("$n"))
        }
    }

    @Test
    fun `values interpolated into a sentence survive it`() {
        for (strings in listOf(english, kannada)) {
            assertTrue(strings.greeting("Khalid").contains("Khalid"))
            assertTrue(strings.stillOwes("Ahmed").contains("Ahmed"))
            // The banner names the biggest debtor even when several owe — the
            // whole point of it, and the part a count-only sentence dropped.
            assertTrue(strings.stillOweWithOthers("Ahmed", 2).contains("Ahmed"))
            assertTrue(strings.stillOweWithOthers("Ahmed", 2).contains("2"))
            assertTrue(strings.youOweWithOthers("Al Faisal", 1).contains("Al Faisal"))
            assertTrue(strings.onlyInStock(3).contains("3"))
            assertTrue(strings.usualPriceNote("SAR 20").contains("SAR 20"))
            assertTrue(strings.youMakeAPiece("SAR 30").contains("SAR 30"))
            assertTrue(strings.billNumber(7).contains("7"))
            assertTrue(strings.billedTo("Ahmed").contains("Ahmed"))
            assertTrue(strings.quantityAtPrice(2, "SAR 95").contains("SAR 95"))
            val replace = strings.replaceWarning(8, 4)
            assertTrue(replace.contains("8"))
            assertTrue(replace.contains("4"))
        }
    }

    @Test
    fun `every tab is named in both languages`() {
        for (tab in AppTab.entries) {
            assertNotEquals(english.tab(tab), kannada.tab(tab), tab.name)
        }
    }

    @Test
    fun `backup errors are said in both languages`() {
        val errors = listOf(BackupError.Unreadable, BackupError.NotStockbookData, BackupError.NewerVersion(99))
        for (error in errors) {
            assertTrue(english.backupError(error).isNotEmpty())
            assertNotEquals(english.backupError(error), kannada.backupError(error))
        }
        assertTrue(english.backupError(BackupError.NewerVersion(99)).contains("99"))
        assertTrue(kannada.backupError(BackupError.NewerVersion(99)).contains("99"))
    }

    @Test
    fun `currency names come from the system, in the language in force`() {
        assertEquals("Saudi Riyal", english.currencyName(Currency.SAR))
        assertTrue(english.currencyRow(Currency.SAR).startsWith("SAR · "))
    }
}
