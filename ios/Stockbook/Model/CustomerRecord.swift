import Foundation

/// The typed-in facts about a customer.
///
/// The counterpart to `Customer`, which is assembled from history. This is the
/// part somebody actually sat down and entered: how the name is spelled, how to
/// ring them, where they are. The figures stay derived; only facts are stored,
/// which is the split `Customer` has documented since before this existed.
///
/// Identity is `key` — the same case- and whitespace-insensitive rule bills have
/// always been grouped by. That is what makes a roster additive: not one stored
/// bill changes, and a name written on a bill years ago still finds its customer.
struct CustomerRecord: Codable, Equatable, Identifiable, Sendable {

    /// Identity. Assigned from the name at creation, and changed only by an
    /// explicit rename — which also rewrites the name on that customer's bills,
    /// because a rename is a correction rather than a new person.
    var key: String

    /// The spelling to show. Authoritative once a customer is on the roster: it
    /// was typed on purpose, unlike the spelling that happened to be used at the
    /// counter on a busy afternoon.
    var name: String

    /// Optional throughout. A shop that knows a name and nothing else still has
    /// a customer, and demanding a phone number to save one would just teach the
    /// owner to type nonsense into the box.
    var phone: String?
    var place: String?

    /// What this customer already owed **before Stockbook** — the figure carried
    /// over from the paper book on the day the shop started using the app.
    ///
    /// Distinct from `Statement.openingBalance`, which is what they owed at the
    /// start of whichever period is on screen and is derived. This one is stored,
    /// predates every bill, and is therefore part of *every* period's
    /// brought-forward figure.
    ///
    /// Never negative. A customer somehow in credit gets a payment recorded
    /// instead, which is a thing with a date on it.
    var openingBalance: Double

    var createdAt: Date

    var id: String { key }

    init(
        name: String,
        phone: String? = nil,
        place: String? = nil,
        openingBalance: Double = 0,
        createdAt: Date = .now
    ) {
        self.name = name.trimmed
        self.key = Customer.key(for: name)
        self.phone = Self.tidied(phone)
        self.place = Self.tidied(place)
        self.openingBalance = max(0, openingBalance)
        self.createdAt = createdAt
    }

    /// Written by hand for the third time in this codebase, and for the same
    /// reason: a default value does **not** make Swift's synthesised decoder
    /// tolerate a missing key. Every customer already stored on a phone was
    /// written before `openingBalance` existed, so without this the roster —
    /// and with it the whole shop — would refuse to load on the first launch
    /// after the update.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        key = try container.decode(String.self, forKey: .key)
        name = try container.decode(String.self, forKey: .name)
        phone = try container.decodeIfPresent(String.self, forKey: .phone)
        place = try container.decodeIfPresent(String.self, forKey: .place)
        openingBalance = try container.decodeIfPresent(Double.self, forKey: .openingBalance) ?? 0
        createdAt = try container.decode(Date.self, forKey: .createdAt)
    }

    /// Rebuilds a record from a backup, keeping the key the **file** recorded
    /// rather than re-deriving it from the name. If the keying rule is ever
    /// loosened or tightened, re-deriving on import would silently re-file
    /// somebody's history against their bills; this cannot.
    init(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double,
        createdAt: Date
    ) {
        self.key = key
        self.name = name.trimmed
        self.phone = Self.tidied(phone)
        self.place = Self.tidied(place)
        self.openingBalance = max(0, openingBalance)
        self.createdAt = createdAt
    }

    /// A field the owner opened, thought better of and left blank is absent, not
    /// an empty string — otherwise "has a phone number" becomes true for a
    /// customer who has none.
    static func tidied(_ value: String?) -> String? {
        guard let value, !value.isBlank else { return nil }
        return value.trimmed
    }
}
