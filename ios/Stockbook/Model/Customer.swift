import Foundation

/// Somebody the shop has billed.
///
/// **Derived, not stored.** There is no customer record in the database — a bill
/// carries a name and nothing else — so this is assembled from history every
/// time it is asked for. At 50–300 products and a few hundred bills that is
/// free, and it means a customer cannot go stale or be orphaned from their
/// bills.
///
/// When there is more to know about a customer than their name — a phone
/// number, an address, a credit limit — the shape that fits is a stored record
/// keyed by `key`, merged onto this type in `StockbookStore.customers()`. The
/// derived figures below stay derived; only the typed-in facts get stored. That
/// is why callers are given `Customer` rather than a bare name string today.
struct Customer: Identifiable, Hashable, Sendable {

    /// The spelling from their **most recent** bill. Correcting the case on a
    /// new bill therefore corrects it everywhere it is shown, without rewriting
    /// what older bills say — those record what was actually written at the time.
    let name: String

    /// Identity: case- and whitespace-insensitive. `"ahmed "` and `"Ahmed"` are
    /// one person, which is the only workable rule for a name typed fresh at a
    /// counter for every bill.
    let key: String

    /// Live bills only. A voided bill did not happen.
    let billCount: Int

    /// What they have bought, across live bills.
    let total: Double

    /// What they still owe: unpaid balances on live bills, **less every payment
    /// received since**. Can go negative when somebody pays ahead, and is left
    /// signed rather than floored — an advance is real money and hiding it would
    /// make the next statement look wrong.
    let owed: Double

    /// Typed-in facts, present only for a customer on the roster. A name that
    /// only ever appeared on a bill has none, and is still a customer.
    let phone: String?
    let place: String?

    /// Carried over from the paper book. Already included in `owed`; exposed so
    /// the editor shows what was typed rather than a figure with bills mixed in.
    let openingBalance: Double

    /// On the roster rather than merely seen on a bill. The two are shown
    /// identically; this exists so the editor knows whether it is adding or
    /// correcting.
    let isOnRoster: Bool

    var id: String { key }

    /// The one place a name becomes an identity. Everything that groups,
    /// matches or filters customers goes through here.
    static func key(for name: String) -> String {
        name.trimmed.lowercased()
    }

    /// `owes SAR 40` when they owe, `SAR 40 in advance` when they have paid ahead,
    /// otherwise `3 bills`.
    func meta(in currency: Currency, strings: Strings) -> String {
        if owed > 0 { return strings.owes(Money.text(owed, in: currency)) }
        if owed < 0 { return strings.inAdvance(Money.text(-owed, in: currency)) }
        return strings.bills(billCount)
    }

    /// A customer entered on the roster who has never been billed. Not an error
    /// — that is what the setup screen exists to create — but the difference
    /// between "no bills yet" and "nothing outstanding" is worth drawing.
    var hasHistory: Bool { billCount > 0 }
}
