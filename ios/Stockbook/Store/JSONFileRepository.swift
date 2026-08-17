import Foundation

/// The shop as one JSON file in Application Support.
///
/// Chosen over SwiftData, Core Data and SQLite because of what this app actually
/// is: 50–300 products, one user, no queries, no reporting layer, and an export
/// format that is already the whole database serialised. A query engine would
/// have nothing to do here, and every database option costs a schema, a
/// migration story and a minimum OS version.
///
/// It also has no floor. SwiftData needs iOS 17, which needs Xcode 15, which
/// needs a Mac new enough to run Ventura — a chain that turned out to matter.
///
/// **Durability.** Every mutation rewrites the file, atomically: written to a
/// neighbouring temporary file and then renamed, so a crash mid-write leaves the
/// previous version intact rather than a half-written one. At this size the
/// rewrite is a fraction of a millisecond. At a hundred times the size it would
/// be the wrong design, and the protocol above is how you would leave.
final class JSONFileRepository: StockbookRepository {

    private let url: URL
    private let fileManager: FileManager

    /// Held in memory because every write rewrites the whole document anyway;
    /// re-reading from disk first would be pure cost.
    private var state: ShopState

    /// The default location: Application Support, which iOS backs up and does
    /// not purge. (Caches would be purged; Documents would expose the file in
    /// the Files app, and this is not a document the owner should edit by hand —
    /// the export in Settings is.)
    static func defaultURL(fileManager: FileManager = .default) throws -> URL {
        let directory = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return directory.appendingPathComponent("stockbook.json")
    }

    init(url: URL, fileManager: FileManager = .default) throws {
        self.url = url
        self.fileManager = fileManager
        self.state = try Self.read(from: url, fileManager: fileManager)
    }

    // MARK: Reading

    private static func read(from url: URL, fileManager: FileManager) throws -> ShopState {
        // First launch: no file is not an error, it is an empty shop.
        guard fileManager.fileExists(atPath: url.path) else { return .empty }
        do {
            let data = try Data(contentsOf: url)
            return try Self.decoder.decode(ShopState.self, from: data)
        } catch {
            throw RepositoryError.unreadable(underlying: error)
        }
    }

    func loadAll() throws -> ShopState { state }

    // MARK: Writing

    private func persist() throws {
        do {
            let data = try Self.encoder.encode(state)
            // .atomic writes to a temporary file and renames, so an interrupted
            // write cannot leave a truncated shop behind.
            try data.write(to: url, options: [.atomic])
        } catch {
            throw RepositoryError.unwritable(underlying: error)
        }
    }

    func upsert(_ product: Product) throws {
        if let index = state.products.firstIndex(where: { $0.uid == product.uid }) {
            state.products[index] = product
        } else {
            state.products.append(product)
        }
        try persist()
    }

    func delete(productUID: UUID) throws {
        state.products.removeAll { $0.uid == productUID }
        try persist()
    }

    func append(_ bill: Bill) throws {
        state.bills.append(bill)
        try persist()
    }

    func update(_ bill: Bill) throws {
        guard let index = state.bills.firstIndex(where: { $0.number == bill.number }) else { return }
        state.bills[index] = bill
        try persist()
    }

    func upsert(_ customer: CustomerRecord) throws {
        if let index = state.customers.firstIndex(where: { $0.key == customer.key }) {
            state.customers[index] = customer
        } else {
            state.customers.append(customer)
        }
        try persist()
    }

    func delete(customerKey: String) throws {
        state.customers.removeAll { $0.key == customerKey }
        try persist()
    }

    func append(_ payment: Payment) throws {
        state.payments.append(payment)
        try persist()
    }

    func delete(paymentID: UUID) throws {
        state.payments.removeAll { $0.id == paymentID }
        try persist()
    }

    func upsert(_ supplier: SupplierRecord) throws {
        if let index = state.suppliers.firstIndex(where: { $0.key == supplier.key }) {
            state.suppliers[index] = supplier
        } else {
            state.suppliers.append(supplier)
        }
        try persist()
    }

    func delete(supplierKey: String) throws {
        state.suppliers.removeAll { $0.key == supplierKey }
        try persist()
    }

    func append(_ purchase: Purchase) throws {
        state.purchases.append(purchase)
        try persist()
    }

    func update(_ purchase: Purchase) throws {
        guard let index = state.purchases.firstIndex(where: { $0.id == purchase.id }) else { return }
        state.purchases[index] = purchase
        try persist()
    }

    func append(_ payment: SupplierPayment) throws {
        state.supplierPayments.append(payment)
        try persist()
    }

    func delete(supplierPaymentID: UUID) throws {
        state.supplierPayments.removeAll { $0.id == supplierPaymentID }
        try persist()
    }

    func save(_ settings: Settings) throws {
        state.settings = settings
        try persist()
    }

    func replaceAll(with newState: ShopState) throws {
        state = newState
        try persist()
    }

    // MARK: Coding

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        // Sorted keys keep the file diffable, which matters the one time someone
        // has to look inside it to work out what went wrong.
        encoder.outputFormatting = [.sortedKeys]
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}

/// A repository that keeps the shop in memory and forgets it. For tests and
/// previews — the same contract, none of the disk.
final class InMemoryRepository: StockbookRepository {

    private var state: ShopState

    init(state: ShopState = .empty) {
        self.state = state
    }

    func loadAll() throws -> ShopState { state }

    func upsert(_ product: Product) throws {
        if let index = state.products.firstIndex(where: { $0.uid == product.uid }) {
            state.products[index] = product
        } else {
            state.products.append(product)
        }
    }

    func delete(productUID: UUID) throws {
        state.products.removeAll { $0.uid == productUID }
    }

    func append(_ bill: Bill) throws { state.bills.append(bill) }

    func update(_ bill: Bill) throws {
        guard let index = state.bills.firstIndex(where: { $0.number == bill.number }) else { return }
        state.bills[index] = bill
    }

    func upsert(_ customer: CustomerRecord) throws {
        if let index = state.customers.firstIndex(where: { $0.key == customer.key }) {
            state.customers[index] = customer
        } else {
            state.customers.append(customer)
        }
    }

    func delete(customerKey: String) throws {
        state.customers.removeAll { $0.key == customerKey }
    }

    func append(_ payment: Payment) throws { state.payments.append(payment) }

    func delete(paymentID: UUID) throws {
        state.payments.removeAll { $0.id == paymentID }
    }

    func upsert(_ supplier: SupplierRecord) throws {
        if let index = state.suppliers.firstIndex(where: { $0.key == supplier.key }) {
            state.suppliers[index] = supplier
        } else {
            state.suppliers.append(supplier)
        }
    }

    func delete(supplierKey: String) throws {
        state.suppliers.removeAll { $0.key == supplierKey }
    }

    func append(_ purchase: Purchase) throws { state.purchases.append(purchase) }

    func update(_ purchase: Purchase) throws {
        guard let index = state.purchases.firstIndex(where: { $0.id == purchase.id }) else { return }
        state.purchases[index] = purchase
    }

    func append(_ payment: SupplierPayment) throws { state.supplierPayments.append(payment) }

    func delete(supplierPaymentID: UUID) throws {
        state.supplierPayments.removeAll { $0.id == supplierPaymentID }
    }

    func save(_ settings: Settings) throws { state.settings = settings }

    func replaceAll(with newState: ShopState) throws { state = newState }
}
