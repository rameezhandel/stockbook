package com.stockbook.core.model

import java.time.Duration
import java.time.Instant

/**
 * How long an account has gone without money moving, and when that is worth
 * saying out loud.
 *
 * Home already says who owes and how much. What it could not say is how long,
 * and on a book of contractors buying on credit that is the difference between a
 * customer and a bad debt: "Ahmed owes 1,200" reads the same whether he bought
 * last week or last March.
 *
 * **Only money counts as paid.** A credit note reduces what somebody owes and a
 * balance transfer moves a figure between two accounts, and neither is a coin
 * changing hands. Letting either reset this clock would tell the owner they had
 * been paid by an account that has not paid them since spring — which is worse
 * than saying nothing, because they would stop chasing it.
 *
 * **This is not invoice ageing and does not pretend to be.** Payments here are
 * taken against a person, not against a bill — that is how a counter works — so
 * the app genuinely does not know which slip a given payment cleared. Buckets of
 * 30, 60 and 90 days would need a rule invented for the purpose, usually "money
 * settles the oldest bill first", and the figures would look precise while
 * resting on an assumption nobody was asked about. One date the app actually
 * holds is worth more than four it had to guess.
 *
 * One implementation for both sides of the book, for the reason [Customer.key]
 * and [Supplier.key] share one: two rules that agree today are two rules that can
 * stop agreeing, and money owed to the shop and money owed by it deserve the same
 * answer to the same question.
 */
object LastPaid {

    /**
     * Below this, nothing is said at all.
     *
     * Thirty days because that is the credit a hardware shop extends without
     * thinking about it — a contractor who bought last week and has not paid is
     * not late, they are a customer. Flagging them would put a line on the screen
     * every day that means nothing, and the surest way to make the owner stop
     * reading this line is to show it when there is no news.
     */
    const val WORTH_SAYING_AFTER_DAYS = 30L

    /**
     * Whole days since money last came in, counting from [since] where none ever
     * has.
     *
     * Null where there is no date to count from at all — an account carried over
     * from the paper book as an opening balance and never traded with since has
     * no history to date, and inventing one would be worse than staying quiet.
     *
     * Whole days, floored: a debt is not eleven and a half days old to anybody
     * standing at a counter. A clock that has somehow run backwards — a phone
     * whose date was wrong when a bill was written — floors at zero rather than
     * reporting a negative age.
     */
    fun daysSince(lastPaidAt: Instant?, since: Instant?, now: Instant): Long? {
        val from = lastPaidAt ?: since ?: return null
        val days = Duration.between(from, now).toDays()
        return if (days < 0) 0 else days
    }

    /** Whether [daysSince] has crossed into being worth a line on the screen. */
    fun worthSaying(days: Long?): Boolean = days != null && days >= WORTH_SAYING_AFTER_DAYS
}
