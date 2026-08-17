package com.stockbook.core.model

import com.stockbook.core.text.AppLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The shop's own settings. One of these exists, always.
 *
 * Every field has a default, and kotlinx.serialization uses those defaults for
 * keys a file does not carry — so a shop saved by an older build still opens.
 * The one field whose *shape* changed keeps its old key readable: see
 * [legacyCurrencySymbol].
 */
@Serializable
data class Settings(
    /**
     * The business owner's name, asked for as step 1 of setup and shown as
     * "Hello, <first name>" on the dashboard.
     */
    val ownerName: String = "",
    /**
     * ISO 4217 code of the one currency the shop bills in, chosen during setup
     * and changeable in Settings. Stored as the code rather than the symbol so a
     * wrong symbol is a one-line fix in `Currency` instead of a migration out of
     * everyone's saved settings.
     */
    val currencyCode: String = Currency.default.code,
    /**
     * What older builds wrote instead of [currencyCode]. Read, never written —
     * [resolved] folds it into the current shape and drops it.
     */
    @SerialName("currencySymbol")
    val legacyCurrencySymbol: String? = null,
    /** Stock at or below this count reads as "running low". */
    val lowStockAt: Int = 40,
    /**
     * The interface language. Chosen in Settings, never inferred from the phone:
     * the shop owner and the phone's owner are not always the same person, and a
     * shop that reads Kannada should not switch to English because somebody
     * changed a system setting.
     */
    val language: AppLanguage = AppLanguage.ENGLISH,
    /**
     * Dark or light, chosen in Settings and never inferred from the phone — same
     * reasoning as [language], and the same shape.
     */
    val theme: AppTheme = AppTheme.default,
    /**
     * When the owner last wrote a backup file. Null keeps the Today nudge
     * shouting — with no server, a file is the only thing between this shop and
     * a dropped phone.
     */
    @Serializable(with = InstantSerializer::class)
    val lastExportAt: Instant? = null,
    /** First-run setup runs until this is true. */
    val setupCompleted: Boolean = false,
    /** Next value for `Bill.number`. */
    val nextBillNumber: Int = 1
) {
    val currency: Currency get() = Currency.named(currencyCode)

    val hasBackup: Boolean get() = lastExportAt != null

    /**
     * Settings as this build understands them, with any older shape folded in.
     * Applied once on load, so nothing downstream has to know two shapes existed.
     */
    fun resolved(): Settings {
        val legacy = legacyCurrencySymbol ?: return copy(legacyCurrencySymbol = null)
        val recovered = Currency.matching(legacy)?.code
        return copy(
            currencyCode = recovered ?: currencyCode,
            legacyCurrencySymbol = null
        )
    }
}

/**
 * Everything the app persists, in one value.
 *
 * The whole shop fits comfortably in memory — the handoff pins the catalogue at
 * 50–300 products — which is what makes a value-typed domain and a swappable
 * repository affordable here. At a hundred times the size this would be the
 * wrong shape.
 */
@Serializable
data class ShopState(
    val products: List<Product> = emptyList(),
    val bills: List<Bill> = emptyList(),
    /**
     * The customer roster: typed-in facts only. Figures are derived from [bills]
     * and [payments] every time they are asked for.
     *
     * Arriving after v1 shipped cost nothing here: kotlinx.serialization applies
     * a property's default when the key is absent, so a shop file written before
     * this existed still loads. The iOS twin needed a hand-written decoder for
     * exactly this, because Swift's synthesised one throws on a missing key even
     * when the property has a default.
     */
    val customers: List<CustomerRecord> = emptyList(),
    /** Money received after the bill was written. */
    val payments: List<Payment> = emptyList(),
    val settings: Settings = Settings()
) {
    companion object {
        val EMPTY = ShopState()
    }
}
