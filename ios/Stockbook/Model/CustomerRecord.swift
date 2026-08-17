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

    var createdAt: Date

    var id: String { key }

    init(name: String, phone: String? = nil, place: String? = nil, createdAt: Date = .now) {
        self.name = name.trimmed
        self.key = Customer.key(for: name)
        self.phone = Self.tidied(phone)
        self.place = Self.tidied(place)
        self.createdAt = createdAt
    }

    /// Rebuilds a record from a backup, keeping the key the **file** recorded
    /// rather than re-deriving it from the name. If the keying rule is ever
    /// loosened or tightened, re-deriving on import would silently re-file
    /// somebody's history against their bills; this cannot.
    init(key: String, name: String, phone: String?, place: String?, createdAt: Date) {
        self.key = key
        self.name = name.trimmed
        self.phone = Self.tidied(phone)
        self.place = Self.tidied(place)
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
