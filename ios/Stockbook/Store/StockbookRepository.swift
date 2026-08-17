import Foundation

/// Where the shop is kept.
///
/// The one seam between Stockbook's rules and its storage. `StockbookStore`
/// holds every rule and talks only to this; nothing above it knows whether the
/// shop lives in a JSON file, SQLite, Core Data or SwiftData.
///
/// **Why the writes are incremental rather than `save(wholeState:)`.** A
/// whole-state save would be trivial for a file and ruinous for a real database
/// — every bill would rewrite every row — so the cheap-to-implement version of
/// this protocol would have quietly ruled out the engines it exists to allow. A
/// file-backed implementation can still ignore the granularity and rewrite
/// everything; a database-backed one is not forced to.
///
/// Implementations do not validate. Stock floors, payment clamping and bill
/// numbering are rules, and rules live in the store — a repository that also
/// enforced them would mean two places to change and two places to disagree.
protocol StockbookRepository {

    /// The entire shop. Called once at launch.
    func loadAll() throws -> ShopState

    // MARK: Products

    /// Insert or update, matched on `uid`.
    func upsert(_ product: Product) throws
    func delete(productUID: UUID) throws

    // MARK: Bills

    /// Bills are only ever appended. Nothing rewrites history.
    func append(_ bill: Bill) throws

    /// Matched on `number`. In practice voiding uses this, and so does renaming a
    /// customer — which is the one other thing allowed to touch a saved bill.
    func update(_ bill: Bill) throws

    // MARK: Customers

    /// Insert or update, matched on `key`.
    func upsert(_ customer: CustomerRecord) throws

    /// Removes the roster entry only. The customer's bills are history and stay
    /// exactly where they are, which is why this takes a key rather than
    /// pretending to delete a person.
    func delete(customerKey: String) throws

    // MARK: Payments

    func append(_ payment: Payment) throws
    func delete(paymentID: UUID) throws

    // MARK: Suppliers

    /// Insert or update, matched on `key`.
    func upsert(_ supplier: SupplierRecord) throws

    /// Removes the roster entry only. The purchases are history and stay where
    /// they are, for the same reason a customer's bills do.
    func delete(supplierKey: String) throws

    // MARK: Purchases

    /// Appended, then only ever updated in place — voiding is the one thing that
    /// touches a saved delivery.
    func append(_ purchase: Purchase) throws
    func update(_ purchase: Purchase) throws

    // MARK: Money out

    func append(_ payment: SupplierPayment) throws
    func delete(supplierPaymentID: UUID) throws

    // MARK: Settings

    func save(_ settings: Settings) throws

    // MARK: Wholesale

    /// Import, and "start over". A swap, not a merge.
    func replaceAll(with state: ShopState) throws
}

/// Errors a repository may surface. Deliberately few: this app has no network,
/// no contention and no partial failure worth modelling.
enum RepositoryError: LocalizedError {
    case unreadable(underlying: Error)
    case unwritable(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .unreadable:
            "The shop's data could not be read from this phone."
        case .unwritable:
            "The shop's data could not be saved to this phone."
        }
    }
}
