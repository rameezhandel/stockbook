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

    /**
     * The last day this statement can honestly say it covers.
     *
     * For a finished month that is the month's own last day. For the month
     * running now it is **today**: a statement printed on the 18th and headed
     * "till 31 August" claims a fortnight that has not happened, and the
     * customer reading it would take the balance as final when a week of
     * deliveries is still to come.
     *
     * Clamped at both ends, so a period picked entirely in the future is dated
     * from its own first day rather than from a moment before it began.
     */
    fun asOf(now: Instant = Timestamps.now()): Instant =
        now.coerceIn(start, end.minusSeconds(1))
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
 * Who a statement is for.
 *
 * A statement is an **account**, and the shop keeps two kinds: customers, who owe
 * it money, and suppliers, whom it owes. The arithmetic is identical — opening
 * balance, charges, settlements, closing balance — so it lives in one place and
 * takes one of these rather than a `Customer` or a `Supplier`.
 *
 * [kind] exists for the wording alone. "Billed" and "Received" are the wrong
 * words for a delivery note, and a screen cannot infer which it is holding from
 * figures that look the same either way.
 */
data class StatementParty(
    val name: String,
    val key: String,
    val phone: String? = null,
    val place: String? = null,
    /** Carried over from the paper book, in whichever direction this account runs. */
    val openingBalance: Double = 0.0,
    val kind: Kind
) {
    enum class Kind { CUSTOMER, SUPPLIER }

    val isSupplier: Boolean get() = kind == Kind.SUPPLIER
}

/**
 * One account over a period: what was bought, what was paid, and what is left.
 *
 * A pure function of the events — [make] takes them as arguments rather than
 * reaching for a store — because the arithmetic here is the whole feature and it
 * has to be checkable against literal values.
 *
 * **The opening balance is what makes this a statement** rather than a filtered
 * list. Without it, a customer who owed 900 from March and paid 400 in April
 * reads as being 400 in credit.
 *
 * Both directions run through this type. For a customer the figures mean what
 * they owe the shop; for a supplier, what the shop owes them. Nothing in the sums
 * below distinguishes the two, which is exactly why there is one of them.
 */
data class Statement(
    val party: StatementParty,
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
    /** Sum of live charges in the period: bills billed, or deliveries taken. */
    val billed: Double,
    /**
     * Everything settled during the period **with money**: paid on the bill or
     * delivery itself, plus payments made afterwards.
     *
     * Credit notes are not in here, deliberately — see [credited]. Both reduce
     * what is owed and only one of them is cash, and this is the figure a shop
     * reconciles its till against.
     */
    val received: Double,
    /**
     * Credited back over the period, with no money changing hands.
     *
     * Its own line on the document rather than folded into [billed] as a
     * negative charge: the owner needs to see what was invoiced and what was
     * given back as two facts, not as one net figure that hides both.
     */
    val credited: Double,
    /**
     * A balance that arrived from another account over the period, and one that
     * left for another.
     *
     * Their own lines rather than folded into [billed] and [received], for the
     * reason [credited] has its own: a transfer in is not something the shop
     * invoiced, and a transfer out is not money it took. Netting either into a
     * trading figure would make that figure mean two things.
     */
    val transferredIn: Double,
    val transferredOut: Double,
    /**
     * `openingBalance + billed + transferredIn − received − credited −
     * transferredOut`. What they owe at the end of it.
     */
    val closingBalance: Double,
    /**
     * The running balance after each entry, parallel to [entries], so the
     * document can show a balance column without recomputing as it draws.
     */
    val runningBalances: List<Double>
) {

    /**
     * What happened, in the order it happened.
     *
     * Four cases rather than a neutral row of numbers, because the document has
     * to name the product on a delivery and show a payment's note. Being a sealed hierarchy is also what makes adding the supplier side
     * safe: every `when` over it stopped compiling until it had been thought
     * about.
     */
    sealed interface Entry {
        val date: Instant
        val id: String

        /** What the account is charged, and what it settles at the same moment. */
        val charge: Double
        val settledAtOnce: Double

        /**
         * Which total this entry belongs in.
         *
         * The running balance treats all three identically — a charge is a
         * charge and a settlement is a settlement — but the totals must keep
         * them apart, because they answer different questions. [TRADE] is what
         * the shop reconciles against its till. [CREDIT_NOTE] reduced the
         * balance with no money moving. [TRANSFER] did not touch this shop's
         * money at all; it moved a figure between two of its own accounts.
         *
         * An enum rather than the boolean this replaces: a third bucket cannot
         * be expressed by one flag, and two flags could both be true.
         */
        enum class Kind { TRADE, CREDIT_NOTE, TRANSFER }

        val kind: Kind get() = Kind.TRADE

        data class ForBill(val bill: Bill) : Entry {
            override val date: Instant get() = bill.createdAt
            override val id: String get() = "bill-${bill.number}"
            override val charge: Double get() = bill.total
            override val settledAtOnce: Double get() = bill.total - bill.balance
        }

        data class ForPayment(val payment: Payment) : Entry {
            override val date: Instant get() = payment.receivedAt
            override val id: String get() = "payment-${payment.id}"
            override val charge: Double get() = 0.0
            override val settledAtOnce: Double get() = payment.amount
        }

        data class ForCreditNote(val note: CreditNote) : Entry {
            override val date: Instant get() = note.issuedAt
            override val id: String get() = "credit-note-${note.id}"
            override val charge: Double get() = 0.0
            override val settledAtOnce: Double get() = note.total
            override val kind: Kind get() = Kind.CREDIT_NOTE
        }

        data class ForPurchase(val purchase: Purchase) : Entry {
            override val date: Instant get() = purchase.createdAt
            override val id: String get() = "purchase-${purchase.id}"
            override val charge: Double get() = purchase.total
            override val settledAtOnce: Double get() = purchase.total - purchase.balance
        }

        /**
         * A balance moved to or from another account, seen from one end of it.
         *
         * One case rather than two, because it is one event: the same record
         * appears on both statements and has to say the same amount on each,
         * charged on one and settled on the other. [outgoing] is which end this
         * is.
         */
        data class ForTransfer(
            val transfer: BalanceTransfer,
            val outgoing: Boolean,
            /**
             * The account at the other end, spelled as the roster spells it.
             *
             * Resolved when the statement is built rather than stored on the
             * record, so a party renamed afterwards reads correctly here and
             * there is no second copy of a name to drift. `DayBook` names people
             * the same way and for the same reason.
             */
            val otherName: String
        ) : Entry {
            override val date: Instant get() = transfer.movedAt
            override val id: String get() = "transfer-${transfer.id}"
            override val charge: Double get() = if (outgoing) 0.0 else transfer.amount
            override val settledAtOnce: Double get() = if (outgoing) transfer.amount else 0.0
            override val kind: Kind get() = Kind.TRANSFER
        }

        data class ForSupplierPayment(val payment: SupplierPayment) : Entry {
            override val date: Instant get() = payment.paidAt
            override val id: String get() = "supplier-payment-${payment.id}"
            override val charge: Double get() = 0.0
            override val settledAtOnce: Double get() = payment.amount
        }
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {

        /**
         * One customer's account: bills charge it, payments settle it, credit
         * notes reduce it without settling anything.
         */
        fun make(
            customer: Customer,
            bills: List<Bill>,
            payments: List<Payment>,
            creditNotes: List<CreditNote> = emptyList(),
            transfers: List<Entry.ForTransfer> = emptyList(),
            period: StatementPeriod,
            zone: ZoneId = ZoneId.systemDefault()
        ): Statement = make(
            party = customer.party,
            entries = bills.map { Entry.ForBill(it) } +
                payments.map { Entry.ForPayment(it) } +
                creditNotes.map { Entry.ForCreditNote(it) } +
                transfers,
            period = period,
            zone = zone
        )

        /**
         * One supplier's account: deliveries charge it, payments out settle it.
         *
         * The same call as the customer one, with the words meaning the opposite
         * side of the counter. Nothing was copied to get here — that is the whole
         * point of [StatementParty].
         */
        fun make(
            supplier: Supplier,
            purchases: List<Purchase>,
            payments: List<SupplierPayment>,
            transfers: List<Entry.ForTransfer> = emptyList(),
            period: StatementPeriod,
            zone: ZoneId = ZoneId.systemDefault()
        ): Statement = make(
            party = supplier.party,
            entries = purchases.map { Entry.ForPurchase(it) } +
                payments.map { Entry.ForSupplierPayment(it) } +
                transfers,
            period = period,
            zone = zone
        )

        /**
         * The arithmetic, once.
         *
         * Everything above hands this the same three things: who the account is,
         * everything that ever happened on it, and the period to report.
         */
        private fun make(
            party: StatementParty,
            entries: List<Entry>,
            period: StatementPeriod,
            zone: ZoneId
        ): Statement {
            val range = period.range(zone)
            val ordered = entries.sortedBy { it.date }

            // What was carried over predates every entry, so it is part of the
            // brought-forward figure whatever period is being shown.
            val opening = party.openingBalance +
                ordered.filter { it.date < range.start }.sumOf { it.charge - it.settledAtOnce }

            val inRange = ordered.filter { it.date in range }
            // Split by where each figure came from, not by how big it was. All
            // of them still move the running balance below, together.
            val trade = inRange.filter { it.kind == Entry.Kind.TRADE }
            val billed = trade.sumOf { it.charge }
            val received = trade.sumOf { it.settledAtOnce }
            val credited = inRange.filter { it.kind == Entry.Kind.CREDIT_NOTE }.sumOf { it.settledAtOnce }
            val transfers = inRange.filter { it.kind == Entry.Kind.TRANSFER }
            val transferredIn = transfers.sumOf { it.charge }
            val transferredOut = transfers.sumOf { it.settledAtOnce }

            val running = mutableListOf<Double>()
            var balance = opening
            for (entry in inRange) {
                balance += entry.charge - entry.settledAtOnce
                running.add(balance)
            }

            return Statement(
                party = party,
                period = period,
                range = range,
                openingBalance = opening,
                entries = inRange,
                billed = billed,
                received = received,
                credited = credited,
                transferredIn = transferredIn,
                transferredOut = transferredOut,
                closingBalance = opening + billed + transferredIn - received - credited - transferredOut,
                runningBalances = running
            )
        }
    }
}
