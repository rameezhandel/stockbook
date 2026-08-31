import Foundation

/// Every customer's position on one day: what they were invoiced, what they paid,
/// and what they owed either side of it.
///
/// The twin of `DayLedger.kt`.
///
/// **The roll-call is the point.** A customer with nothing happening on the day
/// still gets a line, showing the balance they carried in and out unchanged. That
/// is what makes this checkable against a paper book — the owner reads down two
/// columns of names that are in the same order and the same length, rather than
/// hunting for who is missing. A page that listed only the three people billed
/// today would answer a question the day book already answers better.
///
/// **The owner's own page, like `DayBook`.** Every customer's balance is on it,
/// so it can no more be handed across the counter than the receivable list can. A
/// customer is shown their own `Statement` and nobody else's.
///
/// The arithmetic across a row is `opening + invoiced − received − credited −
/// transferredOut + transferredIn = closing`, and it holds exactly. The last
/// three are usually zero, which is why the columns for them are drawn only on a
/// day that has any — see `hasCredits` and `hasTransfers`.
struct DayLedger: Equatable {
    let day: Date
    let rows: [Row]

    /// One customer on one day.
    ///
    /// `invoiced` is what was billed, whatever was paid against it. `received` is
    /// money that actually arrived — taken at the counter on the day's bills,
    /// plus receipts against what was already owed. A bill for 1,000 with 400
    /// handed over puts 1,000 in one column and 400 in the other, which is what
    /// makes the row balance.
    struct Row: Equatable, Identifiable {
        let name: String
        let key: String
        let invoiced: Double
        let received: Double
        /// Credited back on the day. Reduces the balance with no money moving.
        let credited: Double
        /// A balance that arrived from another account, and one that left for one.
        let transferredIn: Double
        let transferredOut: Double
        /// What they owed as the day began.
        let openingBalance: Double
        /// And as it ended: `openingBalance` plus everything above.
        let closingBalance: Double

        var id: String { key }

        /// Whether anything happened to this account on the day.
        ///
        /// The quiet rows are most of the page and are drawn differently — the
        /// figures they do carry are yesterday's, repeated, and printing them as
        /// boldly as the day's real movements would bury the three lines the
        /// owner opened this page to read.
        var isQuiet: Bool {
            invoiced == 0 && received == 0 && credited == 0
                && transferredIn == 0 && transferredOut == 0
        }
    }

    var invoiced: Double { rows.reduce(0) { $0 + $1.invoiced } }
    var received: Double { rows.reduce(0) { $0 + $1.received } }
    var credited: Double { rows.reduce(0) { $0 + $1.credited } }
    var transferredIn: Double { rows.reduce(0) { $0 + $1.transferredIn } }
    var transferredOut: Double { rows.reduce(0) { $0 + $1.transferredOut } }

    /// What the shop was owed altogether as the day began, and as it ended.
    var openingBalance: Double { rows.reduce(0) { $0 + $1.openingBalance } }
    var closingBalance: Double { rows.reduce(0) { $0 + $1.closingBalance } }

    /// Whether the day had any credit note or transfer at all.
    ///
    /// Their columns are drawn only when one of these is true, for the reason the
    /// statement draws those rows only when they are non-zero: a column of zeroes
    /// teaches the eye to skip a region of the page, and on the day it finally
    /// carries a figure the eye skips it then too.
    var hasCredits: Bool { rows.contains { $0.credited != 0 } }
    var hasTransfers: Bool { rows.contains { $0.transferredIn != 0 || $0.transferredOut != 0 } }

    /// Only the accounts something happened to — the day read as a day.
    var busyRows: [Row] { rows.filter { !$0.isQuiet } }

    /// The same day narrowed to the accounts that moved.
    ///
    /// A whole `DayLedger` rather than a list, so that **every total on it is the
    /// total of what is being shown**. The screen used to filter the rows and go
    /// on totalling all of them, which put a figure at the foot of a column that
    /// the column did not add up to — the one thing a table of money must never
    /// do. Deriving the totals from `rows` means narrowing them cannot be
    /// forgotten anywhere.
    func movedOnly() -> DayLedger { DayLedger(day: day, rows: busyRows) }

    var isEmpty: Bool { rows.isEmpty }
}
