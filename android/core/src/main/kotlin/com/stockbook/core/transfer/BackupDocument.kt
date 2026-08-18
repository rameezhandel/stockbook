package com.stockbook.core.transfer

import com.stockbook.core.model.InstantSerializer
import com.stockbook.core.text.Dates
import com.stockbook.core.text.Strings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The export/import file format.
 *
 * This is the *only* way data moves between phones — including **between an
 * iPhone and an Android phone**, which is why every field name below matches the
 * iOS build's byte for byte. `productUID` is spelled the way Swift spells it for
 * exactly that reason; renaming it to fit Kotlin's conventions would quietly
 * strand every line item on the way across.
 *
 * Compatibility rules, in order of importance:
 * 1. Never repurpose a key. Add new ones and give them defaults.
 * 2. Bump [currentVersion] when a reader written against the old shape would
 *    misinterpret the new one, and reject unknown-but-higher versions on import
 *    rather than guessing.
 * 3. Products carry a `uid` so bill lines can point at them across devices.
 */
@Serializable
data class BackupDocument(
    val version: Int = currentVersion,
    @Serializable(with = InstantSerializer::class)
    val exportedAt: Instant,
    val ownerName: String,
    /** ISO 4217, and the only thing that says what the numbers in this file mean. */
    val currencyCode: String,
    val products: List<ProductRecord> = emptyList(),
    val bills: List<BillRecord> = emptyList(),
    /**
     * Always written, empty or not. Kotlin's decoder would also accept a file
     * with neither key and read it as an empty roster; Swift's would refuse it.
     * The asymmetry is harmless while both builds write every key, and worth
     * knowing about the day one of them stops.
     */
    val customers: List<CustomerRecordRow> = emptyList(),
    val payments: List<PaymentRow> = emptyList(),
    /** The supplier roster, and the money going the other way. */
    val suppliers: List<SupplierRecordRow> = emptyList(),
    val purchases: List<PurchaseRow> = emptyList(),
    val supplierPayments: List<SupplierPaymentRow> = emptyList()
) {
    @Serializable
    data class CustomerRecordRow(
        /**
         * Written out rather than re-derived on import, so a future change to the
         * keying rule cannot silently re-file everybody's history.
         */
        val key: String,
        val name: String,
        val phone: String? = null,
        val place: String? = null,
        /** Carried over from the paper book, and zero for anyone who was not. */
        val openingBalance: Double = 0.0,
        @Serializable(with = InstantSerializer::class)
        val createdAt: Instant
    )

    @Serializable
    data class PaymentRow(
        val id: String,
        val customerKey: String,
        val amount: Double,
        @Serializable(with = InstantSerializer::class)
        val receivedAt: Instant,
        val note: String? = null
    )

    @Serializable
    data class SupplierRecordRow(
        val key: String,
        val name: String,
        val phone: String? = null,
        val place: String? = null,
        val openingBalance: Double = 0.0,
        @Serializable(with = InstantSerializer::class)
        val createdAt: Instant
    )

    @Serializable
    data class PurchaseRow(
        val id: String,
        val supplierKey: String,
        @SerialName("productUID")
        val productUid: String? = null,
        /** Absent on a supplier bill that named no product. */
        val name: String? = null,
        val qty: Int = 0,
        val unitCost: Double = 0.0,
        val total: Double,
        /** Absent for a delivery settled on the spot, exactly as on a bill. */
        val paid: Double? = null,
        /** The number on the supplier's invoice. */
        val invoiceNo: String? = null,
        @Serializable(with = InstantSerializer::class)
        val createdAt: Instant,
    )

    @Serializable
    data class SupplierPaymentRow(
        val id: String,
        val supplierKey: String,
        val amount: Double,
        @Serializable(with = InstantSerializer::class)
        val paidAt: Instant,
        val note: String? = null
    )

    @Serializable
    data class ProductRecord(
        val uid: String,
        val name: String,
        val stock: Int,
        val cost: Double,
        val price: Double
    )

    @Serializable
    data class BillRecord(
        val number: Int,
        @Serializable(with = InstantSerializer::class)
        val createdAt: Instant,
        val total: Double,
        /** Absent for a bill paid in full. */
        val paid: Double? = null,
        val who: String,
        /** The number on the paper bill. Absent when the shop wrote none. */
        val invoiceNo: String? = null,
        val lines: List<LineRecord> = emptyList()
    )

    @Serializable
    data class LineRecord(
        /** Absent when the product had already been deleted at export time. */
        @SerialName("productUID")
        val productUid: String? = null,
        val name: String,
        val qty: Int,
        val price: Double
    )

    /** `Khalid Al-Amri · 8 products · 4 bills · saved 28 July 2026` */
    fun summaryLine(strings: Strings): String = listOf(
        ownerName,
        strings.products(products.size),
        strings.bills(bills.size),
        strings.savedOn(strings.longDate(exportedAt))
    ).joinToString(" · ")

    /** `stockbook-2026-08-11.json` */
    val suggestedFilename: String get() = "stockbook-${Dates.fileDate(exportedAt)}.json"

    companion object {
        /**
         * The format this build writes: **one**, and the first there has ever
         * been.
         *
         * It reached 3 during development — a bump when payments arrived, another
         * for opening balances — but nothing had shipped, so those numbers
         * described files that exist nowhere. Carrying them forward would have
         * meant three shapes of history to keep readable, all imaginary.
         *
         * It is still a version, and rule 2 still stands — and **2 is the rule
         * being applied**, not abandoned. Suppliers, purchases and money paid out
         * arrived after 1, and a reader that ignored them would not merely lose an
         * address book: it would read this file and tell the owner the shop owes
         * nobody anything. That is the payments case again, and the answer is the
         * same. Better that build refuses the file and says so.
         */
        const val currentVersion = 2

        // The invoice numbers added after 2 do **not** bump it. A reader that
        // ignores them shows "Bill #7" where the owner wrote "1024" on the paper:
        // a label lost, not a figure misread. The rule is about meaning.
    }
}

/**
 * Everything that can go wrong reading a file the owner picked. Each case
 * carries enough to say something true to the owner — "that is not a Stockbook
 * file" reads very differently from "that file is from a newer version". The
 * sentences themselves live in `Strings.backupError`, so they can be said in
 * either language.
 */
sealed class BackupError : Exception() {
    data object Unreadable : BackupError() {
        private fun readResolve(): Any = Unreadable
    }

    data object NotStockbookData : BackupError() {
        private fun readResolve(): Any = NotStockbookData
    }

    data class NewerVersion(val found: Int) : BackupError()
}
