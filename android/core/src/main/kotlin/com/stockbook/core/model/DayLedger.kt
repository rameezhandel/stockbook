package com.stockbook.core.model

import java.time.Instant

/**
 * Every customer's position on one day: what they were invoiced, what they paid,
 * and what they owed either side of it.
 *
 * **The roll-call is the point.** A customer with nothing happening on the day
 * still gets a line, showing the balance they carried in and out unchanged. That
 * is what makes this checkable against a paper book — the owner reads down two
 * columns of names that are in the same order and the same length, rather than
 * hunting for who is missing. A page that listed only the three people billed
 * today would answer a question the [DayBook] already answers better.
 *
 * **The owner's own page, like [DayBook].** Every customer's balance is on it, so
 * it can no more be handed across the counter than the receivable list can. A
 * customer is shown their own [Statement] and nobody else's.
 *
 * The arithmetic across a row is `opening + invoiced − received − credited −
 * transferredOut + transferredIn = closing`, and it holds exactly. The last three
 * are usually zero, which is why the columns for them are drawn only on a day
 * that has any — see [hasCredits] and [hasTransfers].
 */
data class DayLedger(val day: Instant, val rows: List<Row>) {

    /**
     * One customer on one day.
     *
     * [invoiced] is what was billed, whatever was paid against it. [received] is
     * money that actually arrived — taken at the counter on the day's bills, plus
     * receipts against what was already owed. A bill for 1,000 with 400 handed
     * over puts 1,000 in one column and 400 in the other, which is what makes the
     * row balance.
     */
    data class Row(
        val name: String,
        val key: String,
        val invoiced: Double,
        val received: Double,
        /** Credited back on the day. Reduces the balance with no money moving. */
        val credited: Double,
        /** A balance that arrived from another account, and one that left for one. */
        val transferredIn: Double,
        val transferredOut: Double,
        /** What they owed as the day began. */
        val openingBalance: Double,
        /** And as it ended: `openingBalance` plus everything above. */
        val closingBalance: Double
    ) {
        /**
         * Whether anything happened to this account on the day.
         *
         * The quiet rows are most of the page and are drawn differently — the
         * figures they do carry are yesterday's, repeated, and printing them as
         * boldly as the day's real movements would bury the three lines the owner
         * opened this page to read.
         */
        val isQuiet: Boolean
            get() = invoiced == 0.0 && received == 0.0 &&
                credited == 0.0 && transferredIn == 0.0 && transferredOut == 0.0
    }

    val invoiced: Double get() = rows.sumOf { it.invoiced }
    val received: Double get() = rows.sumOf { it.received }
    val credited: Double get() = rows.sumOf { it.credited }
    val transferredIn: Double get() = rows.sumOf { it.transferredIn }
    val transferredOut: Double get() = rows.sumOf { it.transferredOut }

    /** What the shop was owed altogether as the day began, and as it ended. */
    val openingBalance: Double get() = rows.sumOf { it.openingBalance }
    val closingBalance: Double get() = rows.sumOf { it.closingBalance }

    /**
     * Whether the day had any credit note or transfer at all.
     *
     * Their columns are drawn only when one of these is true, for the reason the
     * statement draws those rows only when they are non-zero: a column of zeroes
     * teaches the eye to skip a region of the page, and on the day it finally
     * carries a figure the eye skips it then too.
     */
    val hasCredits: Boolean get() = rows.any { it.credited != 0.0 }
    val hasTransfers: Boolean get() = rows.any { it.transferredIn != 0.0 || it.transferredOut != 0.0 }

    /** Only the accounts something happened to — the day read as a day. */
    val busyRows: List<Row> get() = rows.filterNot { it.isQuiet }

    val isEmpty: Boolean get() = rows.isEmpty()
}
