package com.stockbook.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A half-open span of time: [start] counts, [end] does not.
 *
 * Written out rather than reusing an interval type whose containment includes its
 * end instant. With months laid end to end that puts midnight on the 1st in both
 * months, so a bill written then would appear on two statements and be counted
 * twice. Half-open removes the question.
 */
data class StatementRange(val start: Instant, val end: Instant) {
    operator fun contains(moment: Instant): Boolean = moment >= start && moment < end
}

/**
 * What span a statement covers.
 *
 * The quick choices carry a date *inside* the period rather than the period's
 * own bounds, so "this month" still means this month when the app is left open
 * across midnight on the 1st.
 */
sealed interface StatementPeriod {

    /** The whole calendar month containing [inside]. */
    data class Month(val inside: Instant) : StatementPeriod

    /** The whole calendar year containing [inside]. */
    data class Year(val inside: Instant) : StatementPeriod

    /** Whole days, inclusive of both ends as the owner would read them. */
    data class Custom(val from: Instant, val to: Instant) : StatementPeriod

    fun range(zone: ZoneId = ZoneId.systemDefault()): StatementRange = when (this) {
        is Month -> {
            val first = inside.atZone(zone).toLocalDate().withDayOfMonth(1)
            StatementRange(first.startOf(zone), first.plusMonths(1).startOf(zone))
        }
        is Year -> {
            val first = inside.atZone(zone).toLocalDate().withDayOfYear(1)
            StatementRange(first.startOf(zone), first.plusYears(1).startOf(zone))
        }
        is Custom -> {
            // Whichever way round they were picked. A range the owner dragged
            // backwards is still the range they meant.
            val low = minOf(from, to).atZone(zone).toLocalDate()
            val high = maxOf(from, to).atZone(zone).toLocalDate()
            StatementRange(low.startOf(zone), high.plusDays(1).startOf(zone))
        }
    }

    companion object {
        fun thisMonth(now: Instant = Timestamps.now()): StatementPeriod = Month(now)
        fun thisYear(now: Instant = Timestamps.now()): StatementPeriod = Year(now)

        /**
         * Through `LocalDate` rather than by subtracting from the instant:
         * `Instant` only supports exact durations, and a month is not one.
         */
        fun lastMonth(
            now: Instant = Timestamps.now(),
            zone: ZoneId = ZoneId.systemDefault()
        ): StatementPeriod = Month(
            now.atZone(zone).toLocalDate().minusMonths(1).atStartOfDay(zone).toInstant()
        )
    }
}

private fun LocalDate.startOf(zone: ZoneId): Instant = atStartOfDay(zone).toInstant()

/**
 * One customer's account over a period: what they bought, what they paid, and
 * what is left.
 *
 * A pure function of bills and payments — [make] takes them as arguments rather
 * than reaching for a store — because the arithmetic here is the whole feature
 * and it has to be checkable against literal values.
 *
 * **The opening balance is what makes this a statement** rather than a filtered
 * list of bills. Without it, a customer who owed 900 from March and paid 400 in
 * April reads as being 400 in credit.
 */
data class Statement(
    val customer: Customer,
    val period: StatementPeriod,
    val range: StatementRange,
    /**
     * Net owed the instant before [range]'s start: the customer's carried-over
     * opening balance, plus unpaid bills, less payments, from everything earlier.
     */
    val openingBalance: Double,
    /**
     * Bills and payments inside the period, oldest first — a statement reads
     * downwards, unlike every list in the app, which reads newest first.
     */
    val entries: List<Entry>,
    /** Sum of live bill totals in the period. What they bought. */
    val billed: Double,
    /**
     * Everything that came in during the period: paid at the counter on the bills
     * themselves, plus payments received afterwards.
     */
    val received: Double,
    /** `openingBalance + billed − received`. What they owe at the end of it. */
    val closingBalance: Double,
    /**
     * The running balance after each entry, parallel to [entries], so the
     * document can show a balance column without recomputing as it draws.
     */
    val runningBalances: List<Double>
) {

    /** A bill or a payment, in the order they happened. */
    sealed interface Entry {
        val date: Instant
        val id: String

        data class ForBill(val bill: Bill) : Entry {
            override val date: Instant get() = bill.createdAt
            override val id: String get() = "bill-${bill.number}"
        }

        data class ForPayment(val payment: Payment) : Entry {
            override val date: Instant get() = payment.receivedAt
            override val id: String get() = "payment-${payment.id}"
        }
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        fun make(
            customer: Customer,
            bills: List<Bill>,
            payments: List<Payment>,
            period: StatementPeriod,
            zone: ZoneId = ZoneId.systemDefault()
        ): Statement {
            val range = period.range(zone)

            // A voided bill did not happen: it contributes nothing to any figure.
            // It is still listed, because history is marked here rather than
            // hidden.
            // The customer's carried-over balance predates every bill, so it is
            // part of the brought-forward figure whatever period is being shown.
            val opening = customer.openingBalance +
                bills.filter { !it.voided && it.createdAt < range.start }.sumOf { it.balance } -
                payments.filter { it.receivedAt < range.start }.sumOf { it.amount }

            val billsInRange = bills.filter { it.createdAt in range }
            val paymentsInRange = payments.filter { it.receivedAt in range }

            val live = billsInRange.filterNot { it.voided }
            val billed = live.sumOf { it.total }
            // What the bill itself collected: its total less what is still owed.
            val atCounter = live.sumOf { it.total - it.balance }
            val received = atCounter + paymentsInRange.sumOf { it.amount }

            val entries = (billsInRange.map { Entry.ForBill(it) } + paymentsInRange.map { Entry.ForPayment(it) })
                .sortedBy { it.date }

            val running = mutableListOf<Double>()
            var balance = opening
            for (entry in entries) {
                when (entry) {
                    // A voided bill moves nothing, which is exactly what makes
                    // the running column readable beside it.
                    is Entry.ForBill -> if (!entry.bill.voided) balance += entry.bill.balance
                    is Entry.ForPayment -> balance -= entry.payment.amount
                }
                running.add(balance)
            }

            return Statement(
                customer = customer,
                period = period,
                range = range,
                openingBalance = opening,
                entries = entries,
                billed = billed,
                received = received,
                closingBalance = opening + billed - received,
                runningBalances = running
            )
        }
    }
}
