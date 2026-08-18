import Foundation

/// Somebody the shop buys from.
///
/// The mirror of `Customer`, and deliberately the same shape: assembled from
/// purchase history rather than stored whole, merged with the typed-in facts in
/// `SupplierRecord`, identified by the same case- and whitespace-insensitive key.
///
/// What differs is only the direction of the money. `owed` here is what the
/// **shop** owes, not what is owed to it, which is why a positive figure reads as
/// a debt on this side of the counter and a payment is money going out.
struct Supplier: Identifiable, Hashable, Sendable {

    /// The roster's spelling where there is one, otherwise the latest delivery's.
    let name: String
    let key: String
    let purchaseCount: Int
    /// What the shop has bought from them.
    let total: Double
    /// What the shop still owes: unpaid balances on their purchases, less every
    /// payment made since. Signed, like a customer's — paying a supplier ahead is
    /// real money and hiding it would make the next statement wrong.
    let owed: Double

    let phone: String?
    let place: String?
    /// Carried over from the paper book. Already included in `owed`.
    let openingBalance: Double
    let isOnRoster: Bool

    var id: String { key }

    init(
        name: String,
        key: String,
        purchaseCount: Int,
        total: Double,
        owed: Double,
        phone: String? = nil,
        place: String? = nil,
        openingBalance: Double = 0,
        isOnRoster: Bool = false
    ) {
        self.name = name
        self.key = key
        self.purchaseCount = purchaseCount
        self.total = total
        self.owed = owed
        self.phone = phone
        self.place = place
        self.openingBalance = openingBalance
        self.isOnRoster = isOnRoster
    }

    /// A supplier's name becomes an identity by exactly the rule a customer's
    /// does, and there is one implementation of it on purpose: two rules that
    /// agree today are two rules that can stop agreeing, and the day they do, a
    /// delivery lands against a supplier who does not exist.
    static func key(for name: String) -> String {
        Customer.key(for: name)
    }

    /// `owes SAR 40` — from the shop's side — or `2 purchases`.
    func meta(in currency: Currency, strings: Strings) -> String {
        if owed > 0 { return strings.owes(Money.text(owed, in: currency)) }
        if owed < 0 { return strings.inAdvance(Money.text(-owed, in: currency)) }
        return strings.purchases(purchaseCount)
    }

    var hasHistory: Bool { purchaseCount > 0 }

    /// For a statement, a supplier is an account like any other.
    var party: StatementParty {
        StatementParty(
            name: name,
            key: key,
            phone: phone,
            place: place,
            openingBalance: openingBalance,
            kind: .supplier
        )
    }
}
