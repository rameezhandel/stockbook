import Foundation

/// The typed-in facts about a supplier: the counterpart to `Supplier`, which is
/// assembled from purchase history.
///
/// Field for field the same as `CustomerRecord`, and that is the point — these
/// rows travel between an iPhone and an Android phone in the same backup file,
/// and a shop's two account books should not hold two different notions of a name.
struct SupplierRecord: Codable, Equatable, Identifiable, Sendable {

    /// Identity. Changed only by an explicit rename, which moves the purchases
    /// with it — they carry the key, so unlike a bill there is no spelling to
    /// rewrite.
    var key: String

    /// The spelling to show. Authoritative once a supplier is on the roster.
    var name: String

    var phone: String?
    var place: String?

    /// What the shop already owed this supplier **before Stockbook** — the figure
    /// carried over from the paper book on the day the app arrived.
    ///
    /// Never negative. A supplier the shop had paid ahead gets a payment recorded
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
        self.key = Supplier.key(for: name)
        self.phone = CustomerRecord.tidied(phone)
        self.place = CustomerRecord.tidied(place)
        self.openingBalance = max(0, openingBalance)
        self.createdAt = createdAt
    }

    /// Written by hand for the fourth time in this codebase, and for the reason
    /// the other three were: a default value does **not** make Swift's synthesised
    /// decoder tolerate a missing key.
    ///
    /// Nothing has shipped, so no stored file is missing these yet. That is not
    /// the case this guards — the next field added to a supplier is, and a shop
    /// that refuses to open after an update is the most expensive bug this app
    /// could have.
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
    /// rather than re-deriving it from the name — the same reason `CustomerRecord`
    /// has this initialiser.
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
        self.phone = CustomerRecord.tidied(phone)
        self.place = CustomerRecord.tidied(place)
        self.openingBalance = max(0, openingBalance)
        self.createdAt = createdAt
    }
}
