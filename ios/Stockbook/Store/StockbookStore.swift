import Foundation
import Observation

/// Every rule that changes data lives here, and the current shop lives here too.
///
/// Views read `products`, `bills` and `settings` directly and never mutate them —
/// the setters are private, so that is enforced rather than merely asked for.
/// Stock arithmetic, bill numbering, snapshotting and the void/restock rules are
/// all one layer, which is the layer the tests drive.
///
/// Persistence is a `StockbookRepository` and nothing here knows which one. The
/// whole shop is held in memory because it comfortably fits — 50–300 products —
/// which is what makes reads free and the storage seam cheap.
@MainActor
@Observable
final class StockbookStore {

    private(set) var products: [Product] = []
    private(set) var bills: [Bill] = []
    private(set) var settings: Settings = Settings()

    /// Set when the disk refuses a write. Nothing in the UI surfaces it yet;
    /// it exists so a failure is recorded rather than swallowed.
    private(set) var lastError: String?

    private let repository: StockbookRepository

    init(repository: StockbookRepository) {
        self.repository = repository
        reload()
    }

    private func reload() {
        do {
            let state = try repository.loadAll()
            products = state.products.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
            bills = state.bills.sorted { $0.createdAt > $1.createdAt }
            settings = state.settings
            L10n.use(settings.language)
        } catch {
            lastError = error.localizedDescription
        }
    }

    /// Writes are best-effort in the sense that a failure cannot roll back the
    /// in-memory change — but it is recorded rather than ignored.
    private func attempt(_ work: () throws -> Void) {
        do {
            try work()
        } catch {
            lastError = error.localizedDescription
        }
    }

    // MARK: - Settings

    func setOwnerName(_ name: String) {
        settings.ownerName = name.trimmed
        attempt { try repository.save(settings) }
    }

    /// The interface language. Applied to `L10n` in the same breath, because the
    /// two must never disagree — `RootView` rebuilds off `settings.language`
    /// while every string is read from `L10n`.
    func setLanguage(_ language: AppLanguage) {
        guard settings.language != language else { return }
        settings.language = language
        L10n.use(language)
        attempt { try repository.save(settings) }
    }

    /// The one currency the shop bills in.
    ///
    /// Nothing is converted. Amounts already saved keep their numbers and start
    /// being drawn with the new symbol, which is the honest behaviour for an app
    /// that holds no exchange rate — and the reason the Settings copy says so
    /// out loud before the tap.
    func setCurrency(_ currency: Currency) {
        guard settings.currencyCode != currency.code else { return }
        settings.currencyCode = currency.code
        attempt { try repository.save(settings) }
    }

    func completeSetup() {
        settings.setupCompleted = true
        attempt { try repository.save(settings) }
    }

    func markExported(at date: Date = .now) {
        settings.lastExportAt = date
        attempt { try repository.save(settings) }
    }

    // MARK: - Reading

    func product(uid: UUID) -> Product? {
        products.first { $0.uid == uid }
    }

    var liveBills: [Bill] { bills.filter { !$0.voided } }

    /// Case-insensitive substring match. In memory: at this catalogue size it is
    /// free, and it keeps the rule visible.
    func products(matching query: String) -> [Product] {
        let needle = query.trimmed.lowercased()
        guard !needle.isEmpty else { return products }
        return products.filter { $0.name.lowercased().contains(needle) }
    }

    // MARK: - Products

    /// Adds a product. Names are deduplicated case-insensitively — typing one
    /// that already exists is silently ignored, matching setup's behaviour, and
    /// the existing product comes back instead.
    @discardableResult
    func addProduct(name: String, stock: Int, cost: Double, price: Double) -> Product {
        let cleaned = name.trimmed
        if let existing = products.first(where: { $0.name.lowercased() == cleaned.lowercased() }) {
            return existing
        }
        let product = Product(name: cleaned, stock: max(0, stock), cost: max(0, cost), price: max(0, price))
        products.append(product)
        products.sort { $0.name.localizedCompare($1.name) == .orderedAscending }
        attempt { try repository.upsert(product) }
        return product
    }

    func update(_ product: Product, name: String, stock: Int, cost: Double, price: Double) {
        guard var updated = self.product(uid: product.uid) else { return }
        updated.name = name.trimmed
        updated.stock = max(0, stock)
        updated.cost = max(0, cost)
        updated.price = max(0, price)
        replace(updated)
    }

    /// Deletes a product. Bill lines keep their name and price snapshots, so
    /// history survives; only `productUID` is left dangling, which is exactly
    /// what it is optional for.
    func delete(_ product: Product) {
        products.removeAll { $0.uid == product.uid }
        attempt { try repository.delete(productUID: product.uid) }
    }

    private func replace(_ product: Product) {
        guard let index = products.firstIndex(where: { $0.uid == product.uid }) else { return }
        products[index] = product
        products.sort { $0.name.localizedCompare($1.name) == .orderedAscending }
        attempt { try repository.upsert(product) }
    }

    /// Whether a product editor's draft is complete enough to save: a name, a
    /// stock figure, a cost figure, and a selling price above zero.
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

        var snapshots: [BillLine] = []
        for line in lines {
            guard var product = product(uid: line.productUID) else { continue }
            let quantity = max(1, line.qty)
            snapshots.append(
                BillLine(productUID: product.uid, name: product.name, qty: quantity, price: line.price)
            )
            product.stock = max(0, product.stock - quantity)
            replace(product)
        }
        guard !snapshots.isEmpty else { return nil }

        let total = snapshots.reduce(0) { $0 + $1.lineTotal }
        let bill = Bill(
            number: settings.nextBillNumber,
            lines: snapshots,
            total: total,
            paid: paid.map { min(max(0, $0), total) },
            who: name
        )

        bills.insert(bill, at: 0)
        settings.nextBillNumber += 1
        attempt {
            try repository.append(bill)
            try repository.save(settings)
        }
        return bill
    }

    /// Voids a bill and puts its stock back. Bills are never deleted.
    func void(_ bill: Bill) {
        guard let index = bills.firstIndex(where: { $0.number == bill.number }), !bills[index].voided else { return }

        for line in bills[index].lines {
            guard let uid = line.productUID, var product = product(uid: uid) else { continue }
            product.stock += line.qty
            replace(product)
        }
        bills[index].voided = true
        let voided = bills[index]
        attempt { try repository.update(voided) }
    }

    // MARK: - Customers

    /// Distinct customers from non-voided bills, **sorted by outstanding balance
    /// descending, then bill count descending** — the people who owe money come
    /// first because that is who the owner most needs to recognise at the counter.
    func customers() -> [CustomerSuggestion] {
        var book: [String: (count: Int, owed: Double)] = [:]
        for bill in bills where !bill.voided && !bill.who.isBlank {
            let key = bill.who.trimmed
            var entry = book[key] ?? (0, 0)
            entry.count += 1
            entry.owed += bill.balance
            book[key] = entry
        }
        return book
            .map { CustomerSuggestion(name: $0.key, billCount: $0.value.count, owed: $0.value.owed) }
            .sorted { $0.owed != $1.owed ? $0.owed > $1.owed : $0.billCount > $1.billCount }
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
        for bill in bills where bill.isPartPaid && bill.balance > 0 {
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
        guard quantity > 0, var updated = self.product(uid: product.uid) else { return }
        updated.stock += quantity
        if case .purchase = mode, let unitCost, unitCost > 0 {
            updated.cost = unitCost
        }
        replace(updated)
    }

    // MARK: - Whole-database operations

    /// Wipes everything and sends the owner back to setup step 1.
    func startOver() {
        products = []
        bills = []
        // Everything goes except the language. Wiping the shop is a data
        // decision; being handed setup in a language you cannot read is not one
        // the owner asked for.
        var fresh = Settings()
        fresh.language = settings.language
        settings = fresh
        attempt { try repository.replaceAll(with: ShopState(settings: fresh)) }
    }

    /// Replaces the entire database with the contents of a backup.
    ///
    /// A **swap, not a merge** — the handoff is explicit, and the UI gates it
    /// behind a warning naming what is about to be lost.
    func replaceEverything(with document: BackupDocument) {
        var restored = Settings()
        // The language belongs to the person holding this phone, not to the
        // file — a backup carried over from a shop that reads English must not
        // switch this one.
        restored.language = settings.language
        restored.ownerName = document.ownerName
        // Currency, unlike language, is a property of the numbers in the file:
        // those prices were entered in it.
        restored.currencyCode = document.currencyCode
            ?? Currency.matching(symbol: document.currencySymbol)?.code
            ?? Currency.default.code
        restored.nextBillNumber = (document.bills.map(\.number).max() ?? 0) + 1
        restored.setupCompleted = true
        // The imported file is a copy of *another* phone's backup, not a backup
        // of this one — the nudge stays on until this phone writes its own.
        restored.lastExportAt = nil

        let state = ShopState(
            products: document.products.map {
                Product(uid: $0.uid, name: $0.name, stock: max(0, $0.stock), cost: max(0, $0.cost), price: max(0, $0.price))
            },
            bills: document.bills.map { record in
                Bill(
                    number: record.number,
                    lines: record.lines.map { BillLine(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price) },
                    total: record.total,
                    paid: record.paid,
                    who: record.who,
                    createdAt: record.createdAt,
                    voided: record.voided
                )
            },
            settings: restored
        )

        attempt { try repository.replaceAll(with: state) }
        products = state.products.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        bills = state.bills.sorted { $0.createdAt > $1.createdAt }
        settings = restored
    }

    /// Snapshots the whole database into a backup document.
    func makeBackupDocument(at date: Date = .now) -> BackupDocument {
        BackupDocument(
            exportedAt: date,
            ownerName: settings.ownerName,
            currencySymbol: settings.currency.symbol,
            currencyCode: settings.currencyCode,
            products: products.map {
                BackupDocument.ProductRecord(uid: $0.uid, name: $0.name, stock: $0.stock, cost: $0.cost, price: $0.price)
            },
            bills: bills.map { bill in
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
}

/// One line as the cart holds it, before it becomes history.
struct DraftLine {
    let productUID: UUID
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
    func meta(in currency: Currency, strings: Strings) -> String {
        owed > 0 ? strings.owes(Money.text(owed, in: currency)) : strings.bills(billCount)
    }
}

enum RestockMode {
    /// Topping up the bin. The buying price is left alone.
    case quickAdd
    /// A supplier delivery. A cost above zero becomes the buying price used from
    /// now on.
    case purchase
}
