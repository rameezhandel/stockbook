package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Settings
import com.stockbook.core.model.Statement
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.model.StatementRange
import com.stockbook.core.model.Supplier
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.RecordLine
import com.stockbook.core.store.SummaryLine
import java.time.Instant

/**
 * A titled list of things and figures, with a total under it: who owes the shop,
 * who it owes, or what it spent its money on.
 *
 * **Every page built here is the owner's own, and none of them may be shown to
 * anybody else.** A statement is handed across the counter on purpose — it says
 * what one person owes, to that person. These say what *everybody* owes, or
 * where the shop's own money went; letting Ahmed see that Khalid is four
 * thousand behind is not an untidy page, it is a breach, and the owner's
 * spending is private by a rule written into `Expense` itself. Nothing renders
 * any of them beside a customer's own documents, and each title says whose list
 * it is.
 *
 * **[asOf] carries what makes the figures true.** For money owed that is a day,
 * because a balance is true at a moment and meaningless over a span; for
 * spending it is the stretch of days it was spent over. Either way it is the
 * whole of what stops last month's printout reading as this morning's.
 *
 * Laid out here for the reason [StatementDocument] is: drawing is platform work,
 * but *what* is drawn is not, and two hand-written layouts drift the first time
 * either is corrected.
 */
data class SummaryDocument(
    val shopName: String,
    /**
     * The shop's address for the masthead, from [Settings.addressLines].
     *
     * Every page the app prints carries the same letterhead now — the shop's
     * name and where it is — so a sheet on a desk says whose it is without
     * anybody having to remember. The ledger book is the exception, and it is
     * drawn by a different writer.
     */
    val shopAddressLines: List<String>,
    /** What this is, said so nobody mistakes it for a statement. */
    val title: String,
    /** `As of 22 August 2026`, or `1 – 31 August 2026`. See above. */
    val asOf: String,
    val columnHeadings: List<String>,
    val rows: List<Row>,
    val totalLabel: String,
    val totalValue: String,
    /** Shown instead of the table when nobody owes anything. */
    val emptyLine: String,
    /**
     * One line under the total, where the page owes a fact the column cannot
     * carry.
     *
     * The payments page is the reason it exists: its column is money in, and what
     * the shop paid out over the same days belongs on the page but not in that
     * column — a total that is not what the rows above add up to is the figure
     * the first reader to check it stops trusting. Absent on every other page.
     */
    val footnote: String? = null
) {
    /**
     * One line of the table.
     *
     * [name] and [amount] are the two every page has: a customer and what they
     * owe, or a bill and what it came to. The middle two are what a **register**
     * needs and a balance list does not — the number on the paper and the day it
     * was written — and they are absent on the pages that state a position rather
     * than list records.
     *
     * [reference] is absent twice over: on the balance pages, which have no
     * paper behind a row, and on an expense, which is a receipt from somebody
     * else's shop and carries no number of the owner's.
     */
    data class Row(
        val name: String,
        val amount: String,
        val reference: String? = null,
        val date: String? = null,
        /**
         * How many records this line folds — `once`, `6 times` — on the pages
         * that fold any.
         *
         * The summary's middle cell, where a register's is the day. A row never
         * carries both: one page lists records and the other counts them, and
         * nothing folds a line that is already a single record.
         */
        val count: String? = null
    )

    /** Whether there is a table to draw at all. */
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {

        /**
         * Who owes the shop, from `StockbookStore.customers()`.
         *
         * @param customers **biggest debt first**, which is the order this
         *   document wants and the order the on-screen list already shows.
         *   Sorting again here would be a second opinion about which is right.
         */
        fun forReceivable(
            customers: List<Customer>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): SummaryDocument = make(
            parties = customers.map { it.name to it.owed },
            settings = settings,
            strings = strings,
            title = strings.receivableSummary,
            partyHeading = strings.columnCustomer,
            amountHeading = strings.receivableStat,
            totalLabel = strings.totalReceivable,
            emptyLine = strings.nothingReceivable,
            currency = currency,
            now = now
        )

        /**
         * Who the shop owes, from `StockbookStore.suppliers()`.
         *
         * The same page pointed the other way, and every word on it flips with
         * it. A payable list headed "Receivable" would be the most expensive
         * kind of wrong: one the owner acts on.
         */
        fun forPayable(
            suppliers: List<Supplier>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): SummaryDocument = make(
            parties = suppliers.map { it.name to it.owed },
            settings = settings,
            strings = strings,
            title = strings.payableSummary,
            partyHeading = strings.supplier,
            amountHeading = strings.payableStat,
            totalLabel = strings.totalPayable,
            emptyLine = strings.nothingPayable,
            currency = currency,
            now = now
        )

        /**
         * The ledger book's contents page: every customer and where they stand.
         *
         * Built from **the very statements that become the pages behind it**,
         * not from a second walk of the roster. A contents page naming a figure
         * the page it points at disagrees with is worse than no contents page,
         * and taking both from one list is the only way that cannot happen. The
         * order comes with them, so the index reads in the order the book is
         * filed.
         *
         * **Everybody, including the settled and the ones in credit** — unlike
         * [forReceivable], which is a chasing list and drops them. This one is
         * an index: a customer who has a page in the book and no line in the
         * contents is a customer the reader concludes is missing.
         */
        fun forLedgerBook(
            statements: List<Statement>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): SummaryDocument = SummaryDocument(
            shopName = settings.ownerName,
            shopAddressLines = settings.addressLines,
            title = strings.customerBalances,
            asOf = strings.asOfDate(strings.longDate(now)),
            columnHeadings = listOf(strings.columnCustomer, strings.balance),
            rows = statements.map {
                Row(it.party.name, Money.text(it.closingBalance, currency))
            },
            totalLabel = strings.ledgerTotal,
            // The arithmetic sum of the column above, credits and all — **not**
            // the shop's receivable, which counts only what is owed. A figure at
            // the foot of a column that is not what the column adds up to is a
            // figure the first reader to check it stops trusting, and this one
            // is printed under a hundred lines somebody may well add up.
            totalValue = Money.text(statements.sumOf { it.closingBalance }, currency),
            emptyLine = strings.ledgerNoCustomers
        )

        /**
         * Every bill written over a stretch of days: who it was for, its number,
         * the day, and what it came to.
         *
         * **A register, not a summary.** One line per bill rather than one per
         * customer, because a page gets printed to be checked — against the paper
         * book, against a customer's own copy, line by line — and a grouped page
         * cannot be checked against anything. Who bought the most is a question
         * the screen already answers.
         *
         * @param lines newest first, which the store already returns.
         */
        fun forSales(
            lines: List<RecordLine>,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = register(
            lines = lines,
            range = range,
            settings = settings,
            strings = strings,
            title = strings.salesReport,
            nameHeading = strings.columnCustomer,
            referenceHeading = strings.columnInvoiceReceipt,
            amountHeading = strings.soldInPeriod,
            totalLabel = strings.totalSoldLabel,
            emptyLine = strings.nothingSoldThen,
            currency = currency
        )

        /** The mirror: every purchase over the same span, and who it came from. */
        fun forPurchases(
            lines: List<RecordLine>,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = register(
            lines = lines,
            range = range,
            settings = settings,
            strings = strings,
            title = strings.purchaseReport,
            nameHeading = strings.supplier,
            referenceHeading = strings.columnInvoiceReceipt,
            amountHeading = strings.boughtInPeriod,
            totalLabel = strings.totalBoughtLabel,
            emptyLine = strings.nothingBoughtThen,
            currency = currency
        )

        /**
         * Every receipt over the span — and, under the total, what the shop paid
         * its suppliers over the same days.
         *
         * **Money in is the column; money out is a footnote.** Both directions in
         * one column would leave a total that is neither the sum of the rows nor
         * a figure the owner can check against anything, and signing the rows to
         * make it add up would print `SAR -900` beside a supplier's name — which
         * reads as a refund, and this app has no notion of one. So the page states
         * one column honestly and says the other fact in words.
         *
         * @param paidOut what went out over the same span, or zero. Left off the
         *   page entirely when nothing did, rather than printed as "0 paid to
         *   suppliers", which is a line that makes the reader stop and check.
         */
        fun forPayments(
            lines: List<RecordLine>,
            paidOut: Double,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = register(
            lines = lines,
            range = range,
            settings = settings,
            strings = strings,
            title = strings.paymentsReport,
            nameHeading = strings.columnCustomer,
            referenceHeading = strings.columnInvoiceReceipt,
            amountHeading = strings.receivedInPeriod,
            totalLabel = strings.totalReceivedLabel,
            emptyLine = strings.nothingReceivedThen,
            currency = currency,
            footnote = if (paidOut > 0) strings.alsoPaidOut(Money.text(paidOut, currency)) else null
        )

        /**
         * Every expense over the span: what it was for, the day, and the figure.
         *
         * Three columns rather than four. An expense is a receipt from somebody
         * else's shop and carries no number of the owner's, and a column of
         * dashes is a column that earns nothing.
         */
        fun forSpending(
            lines: List<RecordLine>,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = register(
            lines = lines,
            range = range,
            settings = settings,
            strings = strings,
            title = strings.expenseReport,
            nameHeading = strings.columnWhatItWentOn,
            referenceHeading = null,
            amountHeading = strings.expenseInPeriod,
            totalLabel = strings.totalSpentLabel,
            emptyLine = strings.nothingSpentThen,
            currency = currency
        )

        /**
         * Where a month's money went, folded by what it went on: `Petrol, 6
         * times, 780`.
         *
         * **The one page in the app that groups anything**, and the counterpart
         * to [forSpending] rather than a replacement for it. A register is
         * checked — against the receipts in a drawer, line by line — and a
         * folded page cannot be. This answers the other question, the one a
         * register buries under forty-seven rows: where did the month go.
         *
         * **By month, and the heading says so.** Every other page here is titled
         * with the two dates at the ends of its span; a summary is asked for one
         * month at a time and is titled with the month's name.
         *
         * @param lines from `StockbookStore.spendingIn`, **biggest first**,
         *   which is the order that makes the page an answer. Sorting again here
         *   would be a second opinion about which is right.
         * @param monthOf any instant inside the month those lines were folded
         *   from — the same one handed to [StatementPeriod.Month]. A date rather
         *   than the period itself because Swift's `StatementPeriod` is a flat
         *   enum with no `Month` type to name, and a twin that takes a different
         *   argument is a twin that drifts.
         */
        fun forSpendingSummary(
            lines: List<SummaryLine>,
            monthOf: Instant,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = summary(
            lines = lines,
            monthOf = monthOf,
            title = strings.expenseSummary,
            nameHeading = strings.columnWhatItWentOn,
            countHeading = strings.columnHowOften,
            amountHeading = strings.expenseInPeriod,
            countText = strings::timesSpent,
            totalLabel = strings.totalSpentLabel,
            emptyLine = strings.nothingSpentThatMonth,
            settings = settings,
            strings = strings,
            currency = currency
        )

        /**
         * What each customer bought that month: `Khalid Hardware, 4 bills, 12,300`.
         *
         * Folded **by person rather than by product**, and the reason is the way
         * this app is actually used. Entering a paper bill as a single figure is
         * ordinary here, and such a bill lists nothing — a page folded by product
         * would leave every one of those bills off, and be flattering or
         * alarming by whatever they came to. Every bill has a customer on it, so
         * every bill is on this page and the total is the month's takings.
         *
         * @param lines from `StockbookStore.salesByCustomerIn`.
         */
        fun forSalesSummary(
            lines: List<SummaryLine>,
            monthOf: Instant,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = summary(
            lines = lines,
            monthOf = monthOf,
            title = strings.salesSummary,
            nameHeading = strings.columnCustomer,
            countHeading = strings.columnHowMany,
            amountHeading = strings.columnInvoiceAmount,
            countText = strings::billsFolded,
            totalLabel = strings.totalSoldLabel,
            emptyLine = strings.nothingSoldThatMonth,
            settings = settings,
            strings = strings,
            currency = currency
        )

        /**
         * What was bought from each supplier that month.
         *
         * @param lines from `StockbookStore.purchasesBySupplierIn`.
         */
        fun forPurchaseSummary(
            lines: List<SummaryLine>,
            monthOf: Instant,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = summary(
            lines = lines,
            monthOf = monthOf,
            title = strings.purchaseSummary,
            nameHeading = strings.columnSupplier,
            countHeading = strings.columnHowMany,
            amountHeading = strings.columnBillAmount,
            countText = strings::purchasesFolded,
            totalLabel = strings.totalBoughtLabel,
            emptyLine = strings.nothingBoughtThatMonth,
            settings = settings,
            strings = strings,
            currency = currency
        )

        /**
         * What each customer paid that month, with what the shop paid out under
         * the total.
         *
         * **The one folded page with a footnote, for the reason [forPayments]
         * has one.** Money in and money out are two types and stay two types; a
         * column holding both would total to neither, and the first person to
         * add it up is the person who stops trusting the page. So the column is
         * receipts, and [paidOut] — from `StockbookStore.paidOutIn` over the same
         * month — is stated beneath it as a fact the column cannot carry.
         *
         * @param lines from `StockbookStore.receiptsByCustomerIn`.
         * @param paidOut what went to suppliers over the same month. Zero prints
         *   no footnote: a line saying nothing went out is noise on a page about
         *   what came in.
         */
        fun forPaymentsSummary(
            lines: List<SummaryLine>,
            paidOut: Double,
            monthOf: Instant,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): SummaryDocument = summary(
            lines = lines,
            monthOf = monthOf,
            title = strings.paymentsSummary,
            nameHeading = strings.columnCustomer,
            countHeading = strings.columnHowMany,
            amountHeading = strings.columnReceivedAmount,
            countText = strings::receiptsFolded,
            totalLabel = strings.totalReceivedLabel,
            emptyLine = strings.nothingReceivedThatMonth,
            settings = settings,
            strings = strings,
            currency = currency,
            footnote = if (paidOut > 0) strings.alsoPaidOut(Money.text(paidOut, currency)) else null
        )

        /**
         * The four pages that **fold a month into an answer**.
         *
         * Written once for the reason [register] is: they differ in their
         * wording and in nothing else, and a layout copied four times is a
         * layout that gets corrected three times.
         *
         * **By month, and the heading says so.** Every register here is titled
         * with the two dates at the ends of its span; a summary is asked for one
         * month at a time and is titled with the month's name.
         *
         * @param lines already **biggest first**, which is the order that makes
         *   the page an answer. Sorting again here would be a second opinion
         *   about which is right.
         * @param monthOf any instant inside the month those lines were folded
         *   from — the same one handed to [StatementPeriod.Month]. A date rather
         *   than the period itself because Swift's `StatementPeriod` is a flat
         *   enum with no `Month` type to name, and a twin that takes a different
         *   argument is a twin that drifts.
         * @param countText turns a line's count into the cell under
         *   [countHeading] — `6 times`, `4 bills`.
         */
        private fun summary(
            lines: List<SummaryLine>,
            monthOf: Instant,
            title: String,
            nameHeading: String,
            countHeading: String,
            amountHeading: String,
            countText: (Int) -> String,
            totalLabel: String,
            emptyLine: String,
            settings: Settings,
            strings: Strings,
            currency: Currency,
            footnote: String? = null
        ): SummaryDocument = SummaryDocument(
            shopName = settings.ownerName,
            shopAddressLines = settings.addressLines,
            title = title,
            asOf = strings.monthYear(monthOf),
            columnHeadings = listOf(nameHeading, countHeading, amountHeading),
            rows = lines.map {
                Row(
                    name = it.name,
                    amount = Money.text(it.total, currency),
                    count = countText(it.count)
                )
            },
            totalLabel = totalLabel,
            // Summed from the same figures the rows print, so the foot of the page
            // can never disagree with the page — and, because each fold puts every
            // record in the month on exactly one line, this is also what the store's
            // own figure for that month says. `SummaryDocumentTests` pins that.
            totalValue = Money.text(lines.sumOf { it.total }, currency),
            emptyLine = emptyLine,
            footnote = footnote
        )

        /**
         * The four pages that **list records over a stretch of days**.
         *
         * Money owed is a balance and true right now; what was sold, bought,
         * received or spent only means anything over a period, and the header
         * says which. Everything else about them differs in wording and in
         * whether there is a number to print, so that is all they differ in here.
         */
        private fun register(
            lines: List<RecordLine>,
            range: StatementRange,
            settings: Settings,
            strings: Strings,
            title: String,
            nameHeading: String,
            /** Null where the records carry no number — see [forSpending]. */
            referenceHeading: String?,
            amountHeading: String,
            totalLabel: String,
            emptyLine: String,
            currency: Currency,
            footnote: String? = null
        ): SummaryDocument = SummaryDocument(
            shopName = settings.ownerName,
            shopAddressLines = settings.addressLines,
            title = title,
            asOf = strings.dateSpan(
                strings.longDate(range.start),
                // The last day *inside* the range. A period that ends at
                // midnight on the 1st is an August statement titled "to 1
                // September", which nobody reads as August.
                strings.longDate(range.end.minusSeconds(1))
            ),
            columnHeadings = listOfNotNull(
                nameHeading,
                referenceHeading,
                strings.columnDate,
                amountHeading
            ),
            rows = lines.map {
                Row(
                    name = it.who,
                    amount = Money.text(it.amount, currency),
                    reference = if (referenceHeading == null) null else it.reference,
                    // The short form, not the long one. Forty rows of "22 August
                    // 2026" is a column three times wider than the fact in it.
                    date = strings.pickedDate(it.at)
                )
            },
            totalLabel = totalLabel,
            // Summed from the same figures the rows print, so the foot of the page
            // can never disagree with the page.
            totalValue = Money.text(lines.sumOf { it.amount }, currency),
            emptyLine = emptyLine,
            footnote = footnote
        )

        /**
         * The page itself, which does not care which way the money points.
         *
         * `Customer` and `Supplier` are separate types with the same two fields
         * that matter here, so they arrive already reduced to a name and a
         * figure rather than behind an interface neither of them asked for.
         */
        private fun make(
            parties: List<Pair<String, Double>>,
            settings: Settings,
            strings: Strings,
            title: String,
            partyHeading: String,
            amountHeading: String,
            totalLabel: String,
            emptyLine: String,
            currency: Currency,
            now: Instant
        ): SummaryDocument {
            // Only what is actually outstanding. Somebody in advance is not a
            // debtor, and a negative row on a chasing list is a line the owner
            // has to stop and think about every time they read it.
            val owing = parties.filter { it.second > 0 }

            return SummaryDocument(
                shopName = settings.ownerName,
            shopAddressLines = settings.addressLines,
                title = title,
                asOf = strings.asOfDate(strings.longDate(now)),
                columnHeadings = listOf(partyHeading, amountHeading),
                rows = owing.map { Row(it.first, Money.text(it.second, currency)) },
                totalLabel = totalLabel,
                // Summed from the same figures the rows print, so the foot of the
                // page can never disagree with the page. `outstanding()` and
                // `payable()` walk the same rosters to the same answers, and
                // `SummaryDocumentTests` pins them together.
                totalValue = Money.text(owing.sumOf { it.second }, currency),
                emptyLine = emptyLine
            )
        }
    }
}
