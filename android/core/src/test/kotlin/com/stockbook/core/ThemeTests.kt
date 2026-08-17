package com.stockbook.core

import com.stockbook.core.model.AppTheme
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.InMemoryRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppLanguage
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The theme is a preference of the phone, not a fact about the shop.
 *
 * Which makes it behave like the language and unlike the currency, and the three
 * rules below are the ones that difference produces. None of them can be seen by
 * looking at a screen — a theme that resets itself looks like a screen somebody
 * drew wrong.
 */
class ThemeTests {

    private fun store() = StockbookStore(InMemoryRepository())

    @Test
    fun `a shop opens dark until somebody says otherwise`() {
        assertEquals(AppTheme.DARK, store().settings.theme)
    }

    @Test
    fun `choosing a theme persists it`() {
        val repository = InMemoryRepository()
        val store = StockbookStore(repository)

        store.setTheme(AppTheme.LIGHT)

        assertEquals(AppTheme.LIGHT, store.settings.theme)
        assertEquals(AppTheme.LIGHT, repository.loadAll().settings.theme, message = "not written to disk")
    }

    @Test
    fun `a shop file written before themes existed still opens, in the dark`() {
        val before = """
            {
              "settings": { "ownerName": "Khalid Al-Amri", "currencyCode": "SAR", "setupCompleted": true }
            }
        """.trimIndent()

        val state = BackupService.json.decodeFromString<ShopState>(before)

        assertEquals(AppTheme.DARK, state.settings.theme)
        assertEquals("Khalid Al-Amri", state.settings.ownerName)
    }

    @Test
    fun `starting over keeps the theme`() {
        val store = store()
        store.setTheme(AppTheme.LIGHT)
        store.setOwnerName("Khalid")

        store.startOver()

        assertTrue(store.settings.ownerName.isEmpty())
        assertEquals(
            AppTheme.LIGHT,
            store.settings.theme,
            message = "wiping the shop is a data decision, not a decision about how it looks"
        )
    }

    @Test
    fun `importing another phone's shop does not repaint this one`() {
        val store = store()
        store.setTheme(AppTheme.LIGHT)
        store.setLanguage(AppLanguage.KANNADA)

        store.replaceEverything(
            BackupDocument(
                exportedAt = Instant.parse("2026-08-01T09:00:00Z"),
                ownerName = "Someone Else",
                currencySymbol = "SAR ",
                products = emptyList(),
                bills = emptyList()
            )
        )

        assertEquals(AppTheme.LIGHT, store.settings.theme)
        assertEquals(AppLanguage.KANNADA, store.settings.language)
        assertEquals("Someone Else", store.settings.ownerName, message = "the shop itself did come from the file")
    }
}
