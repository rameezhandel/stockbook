import Foundation

/// How long an account has gone without money moving, and when that is worth
/// saying out loud.
///
/// The twin of `LastPaid.kt`. Home already says who owes and how much. What it
/// could not say is how long, and on a book of contractors buying on credit that
/// is the difference between a customer and a bad debt: "Ahmed owes 1,200" reads
/// the same whether he bought last week or last March.
///
/// **Only money counts as paid.** A credit note reduces what somebody owes and a
/// balance transfer moves a figure between two accounts, and neither is a coin
/// changing hands. Letting either reset this clock would tell the owner they had
/// been paid by an account that has not paid them since spring — which is worse
/// than saying nothing, because they would stop chasing it.
///
/// **This is not invoice ageing and does not pretend to be.** Payments here are
/// taken against a person, not against a bill — that is how a counter works — so
/// the app genuinely does not know which slip a given payment cleared. Buckets of
/// 30, 60 and 90 days would need a rule invented for the purpose, usually "money
/// settles the oldest bill first", and the figures would look precise while
/// resting on an assumption nobody was asked about. One date the app actually
/// holds is worth more than four it had to guess.
enum LastPaid {

    /// Below this, nothing is said at all.
    ///
    /// Thirty days because that is the credit a hardware shop extends without
    /// thinking about it — a contractor who bought last week and has not paid is
    /// not late, they are a customer. Flagging them would put a line on the
    /// screen every day that means nothing, and the surest way to make the owner
    /// stop reading this line is to show it when there is no news.
    static let worthSayingAfterDays = 30

    /// Whole days since money last came in, counting from `since` where none ever
    /// has.
    ///
    /// Nil where there is no date to count from at all — an account carried over
    /// from the paper book as an opening balance and never traded with since has
    /// no history to date, and inventing one would be worse than staying quiet.
    ///
    /// Whole days, floored: a debt is not eleven and a half days old to anybody
    /// standing at a counter. A clock that has somehow run backwards — a phone
    /// whose date was wrong when a bill was written — floors at zero rather than
    /// reporting a negative age.
    /// Elapsed time, **not** calendar days, which is what the Kotlin twin's
    /// `Duration.toDays()` measures. `Calendar.dateComponents([.day])` would
    /// answer this in the phone's own time zone, so the same two dates either
    /// side of a daylight-saving change would be 59 days on one platform and 60
    /// on the other — a twin that disagrees only twice a year, in one hemisphere.
    static func daysSince(lastPaidAt: Date?, since: Date?, now: Date) -> Int? {
        guard let from = lastPaidAt ?? since else { return nil }
        let seconds = now.timeIntervalSince(from)
        return seconds <= 0 ? 0 : Int(seconds / 86_400)
    }

    /// Whether `daysSince` has crossed into being worth a line on the screen.
    static func worthSaying(_ days: Int?) -> Bool {
        guard let days else { return false }
        return days >= worthSayingAfterDays
    }
}
