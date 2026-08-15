import Foundation
import SwiftData

/// Every rule that changes data lives here.
///
/// Views read with `@Query` — SwiftData keeps them fresh — but they never mutate
/// a model directly. Stock arithmetic, bill numbering, snapshotting and the
/// void/restock rules are all one layer, which is the layer the tests drive.
///
/// The catalogue is 50–300 products, so filtering and grouping happen in memory
/// rather than in predicates. That is a deliberate size-appropriate choice, not
/// an oversight: it keeps the search and suggestion rules readable and testable.
@MainActor
@Observable
final class StockbookStore {

    let context: ModelContext

    init(context: ModelContext) {
        self.context = context
    }

    // MARK: - Settings

    /// The single settings row, created on first access.
    func settings() -> ShopSettings {
        let existing = (try? context.fetch(FetchDescriptor<ShopSettings>())) ?? []
        if let first = existing.first { return first }

        let created = ShopSettings()
        context.insert(created)
        save()
        return created
    }

    func setOwnerName(_ name: String) {
        settings().ownerName = name.trimmed
        save()
    }

    func completeSetup() {
        settings().setupCompleted = true
        save()
    }

    // MARK: - Reading

    func allProducts() -> [Product] {
        let descriptor = FetchDescriptor<Product>(sortBy: [SortDescriptor(\.name)])
        return (try? context.fetch(descriptor)) ?? []
    }

    func product(uid: UUID) -> Product? {
        allProducts().first { $0.uid == uid }
    }

    func allBills() -> [Bill] {
        let descriptor = FetchDescriptor<Bill>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }

    // MARK: - Products

    /// Adds a product. Names are deduplicated case-insensitively — typing a name
    /// that already exists is silently ignored, matching setup's behaviour, and
    /// the existing product comes back instead.
    @discardableResult
    func addProduct(name: String, stock: Int, cost: Double, price: Double) -> Product {
        let cleaned = name.trimmed
        if let existing = allProducts().first(where: { $0.name.lowercased() == cleaned.lowercased() }) {
            return existing
        }
        let product = Product(name: cleaned, stock: max(0, stock), cost: max(0, cost), price: max(0, price))
        context.insert(product)
        save()
        return product
    }

    func update(_ product: Product, name: String, stock: Int, cost: Double, price: Double) {
        product.name = name.trimmed
        product.stock = max(0, stock)
        product.cost = max(0, cost)
        product.price = max(0, price)
        save()
    }

    /// Deletes a product. Bill lines keep their name and price snapshots, so
    /// history survives; only `productUID` is left dangling, which is exactly
    /// what it is optional for.
    func delete(_ product: Product) {
        context.delete(product)
        save()
    }

    /// Whether a product editor's draft is complete enough to save: a name, a
    /// stock figure, a cost figure, and a selling price above zero.
    ///
    /// `nonisolated` because it touches no state — it is a pure rule about four
    /// strings, and setup's draft struct needs it from outside the main actor.
    nonisolated static func isProductDraftComplete(name: String, stock: String, cost: String, price: String) -> Bool {
        !name.isBlank
            && !stock.isBlank
            && !cost.isBlank
            && (Money.parse(price) ?? 0) > 0
    }

    // MARK: - Billing

    /// Saves a bill.
    ///
    /// Snapshots each line's name and price, decrements stock (floored at zero),
    /// clamps a part payment to the total, and allocates the next bill number.
    @discardableResult
    func saveBill(lines: [DraftLine], customer: String, paid: Double?) -> Bill? {
        let name = customer.trimmed
        guard !lines.isEmpty, !name.isEmpty else { return nil }

        let settings = settings()
        let total = lines.reduce(0) { $0 + Double($1.qty) * $1.price }

        let bill = Bill(
            number: settings.nextBillNumber,
            total: total,
            paid: paid.map { min(max(0, $0), total) },
            who: name
        )
        context.insert(bill)

        for line in lines {
            let snapshot = BillLine(
                productUID: line.product.uid,
                name: line.product.name,
                qty: max(1, line.qty),
                price: line.price
            )
            snapshot.bill = bill
            bill.lines.append(snapshot)
            line.product.stock = max(0, line.product.stock - max(1, line.qty))
        }

        settings.nextBillNumber += 1
        save()
        return bill
    }

    /// Voids a bill and puts its stock back. Bills are never deleted.
    func void(_ bill: Bill) {
        guard !bill.voided else { return }
        let products = allProducts()
        for line in bill.lines {
            guard let uid = line.productUID,
                  let product = products.first(where: { $0.uid == uid }) else { continue }
            product.stock += line.qty
        }
        bill.voided = true
        save()
    }

    // MARK: - Customers

    /// Distinct customers from non-voided bills, **sorted by outstanding balance
    /// descending, then bill count descending** — the people who owe money come
    /// first because that is who the owner most needs to recognise at the counter.
    func customers() -> [CustomerSuggestion] {
        var book: [String: (count: Int, owed: Double)] = [:]
        for bill in allBills() where !bill.voided && !bill.who.isBlank {
            let key = bill.who.trimmed
            var entry = book[key] ?? (0, 0)
            entry.count += 1
            entry.owed += bill.balance
            book[key] = entry
        }
        return book
            .map { CustomerSuggestion(name: $0.key, billCount: $0.value.count, owed: $0.value.owed) }
            .sorted {
                $0.owed != $1.owed ? $0.owed > $1.owed : $0.billCount > $1.billCount
            }
    }

    /// Suggestions for the customer field: filtered by what has been typed,
    /// excluding an exact match, capped at four.
    func customerSuggestions(matching typed: String, limit: Int = 4) -> [CustomerSuggestion] {
        let query = typed.trimmed.lowercased()
        return customers()
            .filter { candidate in
                let name = candidate.name.lowercased()
                guard name != query else { return false }
                return query.isEmpty || name.contains(query)
            }
            .prefix(limit)
            .map { $0 }
    }

    /// The Today banner: who still owes, and how much in total. Counts **distinct
    /// customers, not bills** — two unpaid bills from one person is one person.
    func outstanding() -> (names: [String], total: Double) {
        var names: [String] = []
        var total: Double = 0
        for bill in allBills() where bill.isPartPaid && bill.balance > 0 {
            total += bill.balance
            let name = bill.who.trimmed
            if !name.isEmpty, !names.contains(name) { names.append(name) }
        }
        return (names, total)
    }

    // MARK: - Restock

    /// Adds stock. A zero or negative quantity is a no-op — the sheet treats
    /// "nothing typed" as "close without doing anything".
    func restock(_ product: Product, quantity: Int, mode: RestockMode, unitCost: Double? = nil) {
        guard quantity > 0 else { return }
        product.stock += quantity
        if case .purchase = mode, let unitCost, unitCost > 0 {
            product.cost = unitCost
        }
        save()
    }

    // MARK: - Whole-database operations

    /// Wipes everything and sends the owner back to setup step 1.
    func startOver() {
        for bill in allBills() { context.delete(bill) }
        for product in allProducts() { context.delete(product) }
        let settings = settings()
        settings.ownerName = ""
        settings.setupCompleted = false
        settings.nextBillNumber = 1
        settings.lastExportAt = nil
        save()
    }

    /// Replaces the entire database with the contents of a backup.
    ///
    /// This is a **swap, not a merge** — the handoff is explicit, and the UI
    /// gates it behind an explicit warning naming what is about to be lost.
    func replaceEverything(with document: BackupDocument) {
        for bill in allBills() { context.delete(bill) }
        for product in allProducts() { context.delete(product) }

        for record in document.products {
            context.insert(
                Product(
                    uid: record.uid,
                    name: record.name,
                    stock: max(0, record.stock),
                    cost: max(0, record.cost),
                    price: max(0, record.price)
                )
            )
        }

        var highestNumber = 0
        for record in document.bills {
            let bill = Bill(
                number: record.number,
                total: record.total,
                paid: record.paid,
                who: record.who,
                createdAt: record.createdAt,
                voided: record.voided
            )
            context.insert(bill)
            for line in record.lines {
                let stored = BillLine(productUID: line.productUID, name: line.name, qty: line.qty, price: line.price)
                stored.bill = bill
                bill.lines.append(stored)
            }
            highestNumber = max(highestNumber, record.number)
        }

        let settings = settings()
        settings.ownerName = document.ownerName
        settings.currencySymbol = document.currencySymbol
        settings.nextBillNumber = highestNumber + 1
        settings.setupCompleted = true
        // The imported file is a copy of *another* phone's backup, not a backup
        // of this one — the nudge stays on until this phone writes its own.
        settings.lastExportAt = nil
        save()
    }

    /// Snapshots the whole database into a backup document.
    func makeBackupDocument(at date: Date = .now) -> BackupDocument {
        let settings = settings()
        return BackupDocument(
            exportedAt: date,
            ownerName: settings.ownerName,
            currencySymbol: settings.currencySymbol,
            products: allProducts().map {
                BackupDocument.ProductRecord(uid: $0.uid, name: $0.name, stock: $0.stock, cost: $0.cost, price: $0.price)
            },
            bills: allBills().map { bill in
                BackupDocument.BillRecord(
                    number: bill.number,
                    createdAt: bill.createdAt,
                    total: bill.total,
                    paid: bill.paid,
                    who: bill.who,
                    voided: bill.voided,
                    lines: bill.lines.map {
                        BackupDocument.LineRecord(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price)
                    }
                )
            }
        )
    }

    func markExported(at date: Date = .now) {
        settings().lastExportAt = date
        save()
    }

    // MARK: - Persistence

    private func save() {
        guard context.hasChanges else { return }
        try? context.save()
    }
}

/// One line as the cart holds it, before it becomes history.
struct DraftLine {
    let product: Product
    var qty: Int
    /// What is being charged — the product's price unless the owner overrode it
    /// for this bill.
    var price: Double
}

/// A name the owner has billed before, with the two facts that decide where it
/// ranks in the suggestion list.
struct CustomerSuggestion: Identifiable, Equatable {
    let name: String
    let billCount: Int
    let owed: Double

    var id: String { name }

    /// `owes SAR 40` when they owe, otherwise `3 bills`.
    func meta(symbol: String) -> String {
        owed > 0 ? "owes " + Money.text(owed, symbol: symbol) : Copy.count(billCount, "bill")
    }
}

enum RestockMode {
    /// Topping up the bin. The buying price is left alone.
    case quickAdd
    /// A supplier delivery. A cost above zero becomes the buying price used from
    /// now on.
    case purchase
}
