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

    /// The roster: typed-in facts about customers. Named for the record rather
    /// than the person because `customers()` is the thing views want — that one
    /// merges these with what history says.
    private(set) var customerRecords: [CustomerRecord] = []

    /// Money received after the bill, newest first.
    private(set) var payments: [Payment] = []

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
            customerRecords = state.customers.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
            payments = state.payments.sorted { $0.receivedAt > $1.receivedAt }
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
    ///
    /// Grouped by `Customer.key`, so case and stray spaces do not split one
    /// person into two. `bills` is newest-first, so the first spelling seen is
    /// the most recent one and that is the one shown.
    ///
    /// **The roster and history are merged, not chosen between.** Somebody
    /// entered during setup who has never bought anything is a customer with no
    /// bills; a name typed at the counter that nobody ever added to the roster is
    /// a customer too. Losing either would be worse than showing both.
    ///
    /// Where a roster entry exists its spelling wins, because it was typed on
    /// purpose rather than in a hurry with a customer waiting.
    func customers() -> [Customer] {
        var order: [String] = []
        var book: [String: (name: String, count: Int, total: Double, owed: Double)] = [:]

        for bill in bills where !bill.voided && !bill.who.isBlank {
            let key = Customer.key(for: bill.who)
            if var entry = book[key] {
                entry.count += 1
                entry.total += bill.total
                entry.owed += bill.balance
                book[key] = entry
            } else {
                order.append(key)
                book[key] = (bill.who.trimmed, 1, bill.total, bill.balance)
            }
        }

        let roster = Dictionary(uniqueKeysWithValues: customerRecords.map { ($0.key, $0) })
        for record in customerRecords where book[record.key] == nil {
            order.append(record.key)
            book[record.key] = (record.name, 0, 0, 0)
        }

        // What they brought over from the paper book. Added after the tallies so a
        // customer with an opening balance and no bills still shows what they owe.
        for record in customerRecords {
            if var entry = book[record.key] {
                entry.owed += record.openingBalance
                book[record.key] = entry
            }
        }

        // Payments come off what is owed, and this has to run **after** every
        // customer is in the book — roster entries included.
        //
        // It used to run straight after the bills, which meant the lookup missed
        // for anyone who had never been billed, and their payment was dropped
        // without a sound. On a fresh shop that is the ordinary case, not an edge
        // one: a customer is entered with what they owed from the old book, and
        // the first thing that ever happens to them is paying it off.
        for payment in payments {
            if var entry = book[payment.customerKey] {
                entry.owed -= payment.amount
                book[payment.customerKey] = entry
            }
        }

        return order
            .compactMap { key -> Customer? in
                guard let entry = book[key] else { return nil }
                let record = roster[key]
                return Customer(
                    name: record?.name ?? entry.name,
                    key: key,
                    billCount: entry.count,
                    total: entry.total,
                    // Rounded because netting payments off balances in binary
                    // floating point otherwise leaves a customer owing
                    // 0.000000001 and the UI saying they owe money.
                    owed: (entry.owed * 100).rounded() / 100,
                    phone: record?.phone,
                    place: record?.place,
                    openingBalance: record?.openingBalance ?? 0,
                    isOnRoster: record != nil
                )
            }
            .sorted { $0.owed != $1.owed ? $0.owed > $1.owed : $0.billCount > $1.billCount }
    }

    /// One customer by key, roster figures and all.
    func customer(key: String) -> Customer? {
        customers().first { $0.key == key }
    }

    /// Adds a customer to the roster. A key already present is updated rather
    /// than duplicated — typing a name that is already there is a correction, not
    /// a second person.
    ///
    /// Returns nil for a blank name, the way the product editor treats an empty
    /// form: nothing typed means nothing to do.
    @discardableResult
    func addCustomer(
        name: String,
        phone: String? = nil,
        place: String? = nil,
        openingBalance: Double = 0
    ) -> CustomerRecord? {
        guard !name.isBlank else { return nil }
        let record = CustomerRecord(name: name, phone: phone, place: place, openingBalance: openingBalance)
        if let index = customerRecords.firstIndex(where: { $0.key == record.key }) {
            var existing = customerRecords[index]
            existing.name = record.name
            existing.phone = record.phone
            existing.place = record.place
            existing.openingBalance = record.openingBalance
            customerRecords[index] = existing
            attempt { try repository.upsert(existing) }
            return existing
        }
        customerRecords.append(record)
        attempt { try repository.upsert(record) }
        return record
    }

    /// Corrects the facts about a customer already on the roster.
    ///
    /// A name changed enough to change its key is a **rename**, and a rename
    /// rewrites `who` on that customer's bills. That is the one case where a
    /// saved bill is edited, and it is right: the alternative is the roster
    /// saying "Ahmed Contracting" while their bills are filed under "ahmed" and
    /// the two never meeting again. What a bill records about *money* is still
    /// untouchable.
    func updateCustomer(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0
    ) {
        guard !name.isBlank, let index = customerRecords.firstIndex(where: { $0.key == key }) else { return }
        let newKey = Customer.key(for: name)

        var record = customerRecords[index]
        record.name = name.trimmed
        record.phone = CustomerRecord.tidied(phone)
        record.place = CustomerRecord.tidied(place)
        record.openingBalance = max(0, openingBalance)

        guard newKey != key else {
            customerRecords[index] = record
            attempt { try repository.upsert(record) }
            return
        }

        // Renamed. Move the roster entry, then bring the bills and payments with
        // it so nothing is left filed under a name that no longer exists.
        record.key = newKey
        customerRecords.remove(at: index)
        if let clash = customerRecords.firstIndex(where: { $0.key == newKey }) {
            // Renamed onto somebody who is already there: one person, not two.
            customerRecords.remove(at: clash)
            attempt { try repository.delete(customerKey: newKey) }
        }
        customerRecords.append(record)
        attempt {
            try repository.delete(customerKey: key)
            try repository.upsert(record)
        }

        for (index, bill) in bills.enumerated() where Customer.key(for: bill.who) == key {
            var moved = bill
            moved.who = record.name
            bills[index] = moved
            attempt { try repository.update(moved) }
        }

        for (index, payment) in payments.enumerated() where payment.customerKey == key {
            var moved = payment
            moved.customerKey = newKey
            payments[index] = moved
            // Payments are appended, not updated, so the move is a delete and a
            // re-append of the same record — its `id` is unchanged either way.
            attempt {
                try repository.delete(paymentID: payment.id)
                try repository.append(moved)
            }
        }
    }

    /// Takes a customer off the roster. Their bills and payments stay: this
    /// forgets the address book entry, not the trading history.
    func removeCustomer(key: String) {
        customerRecords.removeAll { $0.key == key }
        attempt { try repository.delete(customerKey: key) }
    }

    // MARK: - Payments

    /// Records money handed over after the bill.
    ///
    /// Not allocated to a particular bill, because that is not how a counter
    /// works: somebody pays what they can against what they owe. A zero or
    /// negative amount is a no-op rather than an error — the sheet treats an
    /// empty box as "close without doing anything", the same as restock.
    @discardableResult
    func recordPayment(
        customerKey: String,
        amount: Double,
        receivedAt: Date = .now,
        note: String? = nil
    ) -> Payment? {
        guard amount > 0, !customerKey.isEmpty else { return nil }
        let payment = Payment(
            customerKey: customerKey,
            amount: amount,
            receivedAt: receivedAt,
            note: note
        )
        payments.append(payment)
        payments.sort { $0.receivedAt > $1.receivedAt }
        attempt { try repository.append(payment) }
        return payment
    }

    func deletePayment(id: UUID) {
        payments.removeAll { $0.id == id }
        attempt { try repository.delete(paymentID: id) }
    }

    func payments(forCustomer key: String) -> [Payment] {
        self.payments.filter { $0.customerKey == key }
    }

    // MARK: - Statements

    /// One customer's account over a period.
    ///
    /// The arithmetic is in `Statement.make`, which takes plain arrays — this only
    /// decides which arrays. That is what keeps the figures testable without a
    /// store, a repository or a screen.
    func statement(forCustomer key: String, period: StatementPeriod) -> Statement? {
        guard let customer = customer(key: key) else { return nil }
        return Statement.make(
            customer: customer,
            bills: bills(forCustomer: key),
            payments: payments(forCustomer: key),
            period: period
        )
    }

    /// Every bill for one customer, voided ones included — history is never
    /// hidden, only marked.
    func bills(forCustomer key: String) -> [Bill] {
        bills.filter { Customer.key(for: $0.who) == key }
    }

    /// Suggestions for the customer field: filtered by what has been typed,
    /// excluding an exact match, capped at four.
    func customerSuggestions(matching typed: String, limit: Int = 4) -> [Customer] {
        let query = Customer.key(for: typed)
        return customers()
            .filter { candidate in
                guard candidate.key != query else { return false }
                return query.isEmpty || candidate.key.contains(query)
            }
            .prefix(limit)
            .map { $0 }
    }

    /// The Today banner: who still owes, and how much in total. Counts **distinct
    /// customers, not bills** — two unpaid bills from one person is one person,
    /// however they capitalised it the second time.
    func outstanding() -> (names: [String], total: Double) {
        // Derived from `customers()` rather than from bills directly, which is the
        // only way this figure can be right. Walking bills alone ignored both
        // payments received and balances carried over from the paper book — so a
        // customer who had settled up in full went on being named here, and one
        // who owed from before the app existed never was.
        let owing = customers().filter { $0.owed > 0 }
        return (owing.map(\.name), owing.reduce(0) { $0 + $1.owed })
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
        customerRecords = []
        payments = []
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
            customers: document.customers?.map {
                CustomerRecord(
                    key: $0.key,
                    name: $0.name,
                    phone: $0.phone,
                    place: $0.place,
                    openingBalance: $0.openingBalance ?? 0,
                    createdAt: $0.createdAt
                )
            } ?? [],
            payments: document.payments?.map {
                Payment(
                    id: $0.id,
                    customerKey: $0.customerKey,
                    amount: $0.amount,
                    receivedAt: $0.receivedAt,
                    note: $0.note
                )
            } ?? [],
            settings: restored
        )

        attempt { try repository.replaceAll(with: state) }
        products = state.products.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        bills = state.bills.sorted { $0.createdAt > $1.createdAt }
        customerRecords = state.customers.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        payments = state.payments.sorted { $0.receivedAt > $1.receivedAt }
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
            },
            customers: customerRecords.map {
                BackupDocument.CustomerRecordRow(
                    key: $0.key,
                    name: $0.name,
                    phone: $0.phone,
                    place: $0.place,
                    openingBalance: $0.openingBalance,
                    createdAt: $0.createdAt
                )
            },
            payments: payments.map {
                BackupDocument.PaymentRow(
                    id: $0.id,
                    customerKey: $0.customerKey,
                    amount: $0.amount,
                    receivedAt: $0.receivedAt,
                    note: $0.note
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

enum RestockMode {
    /// Topping up the bin. The buying price is left alone.
    case quickAdd
    /// A supplier delivery. A cost above zero becomes the buying price used from
    /// now on.
    case purchase
}
