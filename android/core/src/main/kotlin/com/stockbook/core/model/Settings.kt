package com.stockbook.core.model

import com.stockbook.core.text.AppLanguage
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The shop's own settings. One of these exists, always.
 *
 * Every field has a default, and kotlinx.serialization uses those defaults for
 * keys a file does not carry — so a shop saved before a field existed still
 * opens, which is what lets the next field (a credit limit, whatever purchases
 * need) be added without a migration. Swift needs a hand-written decoder to
 * manage the same thing; here it is the default behaviour, and the two builds
 * have to agree, so neither may rely on a key being present.
 */
@Serializable
data class Settings(
    /**
     * The business owner's name, asked for as step 1 of setup and shown as
     * "Hello, <first name>" on the dashboard.
     */
    val ownerName: String = "",
    /**
     * The shop's postal address, as it should be printed at the top of a
     * statement.
     *
     * Free text with line breaks in it, not structured fields: an address in
     * Madinah does not have the same parts as one in Bengaluru, and the owner
     * knows how theirs is written. Nothing parses this — it is copied onto the
     * document exactly as typed.
     */
    val shopAddress: String = "",
    /**
     * ISO 4217 code of the one currency the shop bills in, chosen during setup
     * and changeable in Settings. Stored as the code rather than the symbol so a
     * wrong symbol is a one-line fix in `Currency` instead of a migration out of
     * everyone's saved settings.
     */
    val currencyCode: String = Currency.default.code,
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
    /** The supplier roster: the customer roster's mirror, for money going out. */
    val suppliers: List<SupplierRecord> = emptyList(),
    /** Stock arriving, one product at a time. */
    val purchases: List<Purchase> = emptyList(),
    /** Money paid to a supplier after the delivery. */
    val supplierPayments: List<SupplierPayment> = emptyList(),
    val settings: Settings = Settings()
) {
    companion object {
        val EMPTY = ShopState()
    }
}
