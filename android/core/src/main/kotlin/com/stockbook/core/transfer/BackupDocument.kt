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
    /**
     * The shop's printed address, absent rather than empty when there is none.
     *
     * Nullable rather than defaulted-blank so both builds write the same bytes:
     * `explicitNulls = false` drops a null here, and Swift's synthesised encoder
     * drops a nil optional, so a shop with no address produces an identical file
     * on either phone. It is also the one field in this document a reader may
     * find missing without concluding the file is not ours — see the Swift
     * twin, where that distinction is what makes it optional at all.
     */
    val shopAddress: String? = null,
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
    val supplierPayments: List<SupplierPaymentRow> = emptyList(),
    /** What has been credited back to customers. */
    val creditNotes: List<CreditNoteRow> = emptyList(),
    /**
     * The owner's own spending.
     *
     * **Does not bump [currentVersion]**, and the rule is worth restating because
     * this is the first field added since the rule was written down. A reader
     * built before expenses existed drops them and misreads nothing: an expense
     * is joined to no customer, no supplier and no bill, so no balance, no
     * statement and no month's takings moves by a riyal for its absence. What is
     * lost is the ledger itself, which is the "loses a label" side of the line,
     * not the "misreads a figure" side.
     */
    val expenses: List<ExpenseRow> = emptyList(),
    /** Balances moved between two real accounts. See [BalanceTransferRow]. */
    val balanceTransfers: List<BalanceTransferRow> = emptyList()
) {
    @Serializable
    data class ExpenseRow(
        val id: String,
        val amount: Double,
        /** What it was for, in the owner's words. Never empty. */
        val note: String,
        @Serializable(with = InstantSerializer::class)
        val spentAt: Instant
    )

    @Serializable
    data class CreditNoteRow(
        val id: String,
        val customerKey: String,
        val total: Double,
        /** The number the owner wrote on the paper note, on its own series. */
        val noteNo: String? = null,
        val reason: String? = null,
        @Serializable(with = InstantSerializer::class)
        val issuedAt: Instant,
        /** What came back, empty on a note that is only a figure. */
        val lines: List<LineRecord> = emptyList()
    )

    /**
     * A balance moved between two accounts, both of them real.
     *
     * **Bumps [currentVersion] to 4.** A reader that dropped these would show
     * *both* parties owing the wrong amount — the one that gave the balance up
     * still owing it, the one that took it on not — so it misreads a figure
     * rather than losing a label, which is the whole test for a bump.
     */
    @Serializable
    data class BalanceTransferRow(
        val id: String,
        val fromKey: String,
        val intoKey: String,
        /** Which side of the book both keys are on. Customers by default. */
        val isSupplier: Boolean = false,
        val amount: Double,
        val note: String? = null,
        @Serializable(with = InstantSerializer::class)
        val movedAt: Instant
    )

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
        /** The number on the receipt, absent where the shop wrote none. */
        val paymentNo: String? = null,
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
        /**
         * What arrived, one entry per product on the delivery note. Empty on a
         * supplier bill entered as a figure.
         *
         * Does not bump [currentVersion]. `total` and `paid` are still here and
         * still mean what they always did, and the shelf count lives on the
         * product rather than being replayed from deliveries — so a reader that
         * drops this shows every figure correctly and loses only the breakdown.
         */
        val lines: List<PurchaseLineRecord> = emptyList(),
        /**
         * The four fields a delivery had when it held one product, written by
         * builds before this one. Read, never written: [BackupService] folds them
         * into a single line, the same way [Purchase.items] does.
         */
        @SerialName("productUID")
        val productUid: String? = null,
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
        /** The number on the receipt, absent where the shop wrote none. */
        val paymentNo: String? = null,
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
        /**
         * Photographs of that paper, by id — the references, not the pictures.
         *
         * This file carries no image bytes. Written anyway, because an id that
         * survives the crossing is what lets a bill re-adopt its photograph the
         * day the pictures travel too; dropping it here would make that
         * impossible after the fact.
         *
         * Absent rather than empty on a bill with no photographs, so a shop that
         * has taken none writes exactly the bytes it always did — and the same
         * bytes the iPhone writes, where a nil optional is dropped by the
         * encoder. `encodeDefaults` is on here, so an empty list would be
         * written as `[]` and the two builds would stop agreeing.
         */
        @SerialName("photoIDs")
        val photoIds: List<String>? = null,
        /**
         * What the bill was for, in the owner's words. Absent when there is none.
         *
         * Does not bump [currentVersion]: a reader that drops it shows "Invoice
         * #1024" where the owner also wrote "3 keys cut on site" — a label lost,
         * not a figure misread.
         */
        val note: String? = null,
        /**
         * The percentage knocked off and what it came to, when the owner gave
         * one. Both absent otherwise.
         *
         * Neither bumps [currentVersion], and this is the case where that rule
         * pays off most clearly: `total` is already the discounted figure, so a
         * reader that drops these two shows exactly what the customer owes. What
         * it loses is the explanation of how the figure was reached — a label,
         * not a figure misread.
         */
        val discountPercent: Double? = null,
        val discountAmount: Double? = null,
        val lines: List<LineRecord> = emptyList()
    )

    @Serializable
    data class LineRecord(
        /** Absent when the product had already been deleted at export time. */
        @SerialName("productUID")
        val productUid: String? = null,
        val name: String,
        val qty: Int,
        val price: Double,
        /**
         * What one piece cost the shop at the moment of sale — see
         * [com.stockbook.core.model.BillLine.cost].
         *
         * Absent, not zero, on a line written before the field existed. A reader
         * that drops it loses the ability to say what a sale earned and misreads
         * no figure it does show, which is why this did not bump the document
         * version.
         */
        val cost: Double? = null
    )

    /**
     * A delivery's line. Its own record rather than [LineRecord] because the
     * money on it is what the shop **paid**, and calling that `price` in a file
     * a person can open would say the opposite of what it means.
     */
    @Serializable
    data class PurchaseLineRecord(
        /** Absent when the product had already been deleted at export time. */
        @SerialName("productUID")
        val productUid: String? = null,
        val name: String,
        val qty: Int,
        val unitCost: Double
    )

    /** `Khalid Al-Amri · 8 products · 4 bills · saved 28 July 2026` */
    fun summaryLine(strings: Strings): String = listOf(
        ownerName,
        strings.products(products.size),
        strings.bills(bills.size),
        strings.savedOn(strings.longDate(exportedAt))
    ).joinToString(" · ")

    /** `stockbook-2026-08-11.json` */
    /**
     * `stockbook-2026-08-20.zip`.
     *
     * A `.zip` since the photographs started travelling with the book. Import
     * still takes either — it sniffs the first bytes rather than the extension —
     * but there is one thing to export, so there is one name for it.
     */
    val suggestedFilename: String get() = "stockbook-${Dates.fileDate(exportedAt)}.zip"

    companion object {
        /**
         * The format this build writes: **three**.
         *
         * It reached 3 once before during development — a bump when payments
         * arrived, another for opening balances — and was reset, because nothing
         * had shipped and those numbers described files that exist nowhere.
         *
         * Rule 2 is what put it back. Suppliers, purchases and money paid out
         * arrived after 1, and a reader that ignored them would tell the owner
         * the shop owes nobody anything — that was 2. Credit notes are the same
         * failure in the other direction: a reader that dropped them would show
         * every credited customer owing more than they do, and the owner would
         * go and ask for money that was written off weeks ago. Better that build
         * refuses the file and says so.
         *
         * The shop address added alongside them did **not** bump it. A reader
         * that ignores an address prints a statement without one: a label lost,
         * not a figure misread. The rule is about meaning.
         */
        /**
         * **4** since balance transfers. A reader that dropped them shows both
         * parties owing the wrong amount and the shop's total receivable
         * unbalanced — the same class of misreading the credit notes were, and
         * the same answer.
         */
        const val currentVersion = 4

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
