import Foundation
import Observation

/// Every rule that changes data lives here, and the current shop lives here too.
///
/// Views read `products`, `bills` and `settings` directly and never mutate them —
/// the setters are private, so that is enforced rather than merely asked for.
/// Stock arithmetic, bill numbering, snapshotting and the stock rules are all one
/// layer, which is the layer the tests drive.
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

    private(set) var supplierRecords: [SupplierRecord] = []

    /// Stock that arrived, newest first.
    private(set) var purchases: [Purchase] = []

    /// Money paid out after the delivery, newest first.
    private(set) var supplierPayments: [SupplierPayment] = []

    /// What has been credited back to customers, newest first.
    private(set) var creditNotes: [CreditNote] = []

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
            supplierRecords = state.suppliers.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
            purchases = state.purchases.sorted { $0.createdAt > $1.createdAt }
            supplierPayments = state.supplierPayments.sorted { $0.paidAt > $1.paidAt }
            creditNotes = state.creditNotes.sorted { $0.issuedAt > $1.issuedAt }
            settings = state.settings
            L10n.use(settings.language)
            Nocturne.use(settings.theme)
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

    /// Trailing blank lines go; the rest is kept exactly as typed, line breaks
    /// included — the owner is laying out how their own address prints.
    func setShopAddress(_ address: String) {
        settings.shopAddress = address.trimmed
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

    /// Dark or light. Applied to `Nocturne` in the same breath and for the same
    /// reason as the language: `RootView` rebuilds off `settings.theme` while
    /// every colour is read from `Nocturne`, so the two must never disagree.
    func setTheme(_ theme: AppTheme) {
        guard settings.theme != theme else { return }
        settings.theme = theme
        Nocturne.use(theme)
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

    /// Saves a bill and moves the stock.
    ///
    /// Line names and prices are **snapshotted** here: the product may be
    /// renamed, repriced or deleted tomorrow, and what somebody paid today must
    /// not move with it. Stock floors at zero — overselling is allowed, because
    /// the customer is standing there and the count may simply be wrong, but the
    /// shelf never goes negative.
    @discardableResult
    func saveBill(
        /// What was sold, when the owner said. Empty is the ordinary case for a
        /// shop entering a paper bill it has already written: the total is
        /// known, and rebuilding it product by product to arrive at it is work
        /// for nothing. An itemised bill moves the shelf; a total does not.
        lines: [DraftLine] = [],
        customer: String,
        paid: Double?,
        /// What the bill came to, for a bill with no lines on it. Ignored when
        /// there are lines — their sum is the total, and a typed figure beside
        /// it is a second answer to a question that already has one.
        amount: Double? = nil,
        /// When the sale happened, which is not always when it was typed. A shop
        /// that writes bills in the paper book all day and enters them at closing
        /// time would otherwise have every one stamped 9pm — and the statements,
        /// which are what somebody settles up against, would inherit that.
        createdAt: Date = .now,
        /// The number on the paper bill, when the shop wrote one.
        invoiceNo: String? = nil,
        /// Photographs of that paper, by id. The files are the app's to keep.
        photoIDs: [String] = []
    ) -> Bill? {
        let name = customer.trimmed
        guard !name.isEmpty else { return nil }

        let snapshots = snapshot(lines)
        // Only an itemised bill moves the shelf. Nothing else in the app can take
        // stock off it, which is why a shop that types totals is told the count is
        // its own to keep straight.
        for line in snapshots {
            guard let uid = line.productUID, var product = product(uid: uid) else { continue }
            product.stock = max(0, product.stock - line.qty)
            replace(product)
        }

        // Two answers to "what did it come to" is one too many. The lines are the
        // ones with arithmetic behind them, so where there are any they win and
        // the typed figure is ignored.
        let itemised = snapshots.reduce(0) { $0 + $1.lineTotal }
        let total = snapshots.isEmpty ? (amount ?? 0) : itemised
        // A bill for nothing is not a bill. Either something was sold or a figure
        // was typed; neither is the same as a blank saved by accident.
        guard total > 0 else { return nil }
        let bill = Bill(
            number: settings.nextBillNumber,
            lines: snapshots,
            total: total,
            // Paying the whole amount is paid in full, not a part payment of the
            // total — otherwise the receipt says somebody owes zero. Clamped as
            // well, since a figure above the total is a typo, not a credit.
            paid: paid.flatMap { $0 < total ? min(max(0, $0), total) : nil },
            who: name,
            invoiceNo: invoiceNo,
            photoIDs: photoIDs.reduce(into: []) { seen, id in
                if !seen.contains(id) { seen.append(id) }
            },
            createdAt: createdAt
        )

        bills.insert(bill, at: 0)
        settings.nextBillNumber += 1
        attempt {
            try repository.append(bill)
            try repository.save(settings)
        }
        return bill
    }

    /// The bill already carrying this number, if any.
    ///
    /// The check behind the screen's refusal to save a second bill on the same
    /// number. It lives here rather than on the screen because the delivery side
    /// asks the same question, and one answer means one rule.
    ///
    /// `exceptNumber` is what makes editing possible: without it, opening bill
    /// 1024 to change its date would be told 1024 is already taken, by itself.
    func billWithInvoiceNo(_ invoiceNo: String?, exceptNumber: Int? = nil) -> Bill? {
        let key = InvoiceNo.key(invoiceNo)
        guard !key.isEmpty else { return nil }
        return bills.first { $0.number != exceptNumber && InvoiceNo.key($0.invoiceNo) == key }
    }

    /// The same question on the other side of the book.
    func purchaseWithInvoiceNo(_ invoiceNo: String?, exceptId: UUID? = nil) -> Purchase? {
        let key = InvoiceNo.key(invoiceNo)
        guard !key.isEmpty else { return nil }
        return purchases.first { $0.id != exceptId && InvoiceNo.key($0.invoiceNo) == key }
    }

    /// The lines as they will be stored: names and prices taken **now**, so that
    /// renaming or repricing a product tomorrow cannot rewrite what somebody paid
    /// today. Reads products; moves no stock, which is what lets both saving and
    /// editing decide separately what the shelf owes.
    private func snapshot(_ lines: [DraftLine]) -> [BillLine] {
        lines.compactMap { line -> BillLine? in
            guard let product = self.product(uid: line.productUID) else { return nil }
            return BillLine(
                productUID: product.uid,
                name: product.name,
                qty: max(1, line.qty),
                price: line.price
            )
        }
    }

    /// Rewrites a bill, and moves the shelf by the difference.
    ///
    /// The old lines go back on and the new ones come off, so an edit that drops a
    /// line, changes a quantity or abandons itemising altogether leaves the count
    /// exactly where entering the corrected bill from scratch would have.
    ///
    /// Nothing is touched unless the result would be a valid bill: a blank name or
    /// a total of zero returns nil with the stock still where it was, rather than
    /// half-applying an edit and leaving the shelf to explain it.
    @discardableResult
    func updateBill(
        number: Int,
        lines: [DraftLine] = [],
        customer: String,
        paid: Double?,
        amount: Double? = nil,
        createdAt: Date,
        invoiceNo: String? = nil
        // Photographs are deliberately not a parameter here. They are added and
        // removed one at a time by `attachPhoto`/`detachPhoto`, so an edit form
        // that knows nothing about them cannot wipe them by omission.
    ) -> Bill? {
        // The index is taken once and stays valid: nothing below writes to `bills`
        // until the last line, and `replace` moves products rather than bills.
        guard let index = bills.firstIndex(where: { $0.number == number }) else { return nil }
        let existing = bills[index]
        let name = customer.trimmed
        guard !name.isEmpty else { return nil }

        let snapshots = snapshot(lines)
        let total = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard total > 0 else { return nil }

        // Reverse, then apply. In that order, because a bill that kept the same
        // product with a smaller quantity would otherwise floor at zero on the way
        // down and come back wrong.
        for line in existing.lines {
            guard let uid = line.productUID, var product = product(uid: uid) else { continue }
            product.stock += line.qty
            replace(product)
        }
        for line in snapshots {
            guard let uid = line.productUID, var product = product(uid: uid) else { continue }
            product.stock = max(0, product.stock - line.qty)
            replace(product)
        }

        var updated = existing
        updated.lines = snapshots
        updated.total = total
        updated.paid = paid.flatMap { $0 < total ? min(max(0, $0), total) : nil }
        updated.who = name
        updated.invoiceNo = CustomerRecord.tidied(invoiceNo)
        updated.createdAt = createdAt

        bills[index] = updated
        attempt { try repository.update(updated) }
        return updated
    }

    /// Removes a bill and puts its stock back.
    ///
    /// Gone rather than marked: this is the shop's own book, and a bill entered by
    /// mistake is a line the owner would have scribbled out of the paper one. The
    /// number it carried becomes free again, which is what makes re-entering it
    /// work.
    func deleteBill(number: Int) {
        guard let existing = bills.first(where: { $0.number == number }) else { return }
        for line in existing.lines {
            guard let uid = line.productUID, var product = product(uid: uid) else { continue }
            product.stock += line.qty
            replace(product)
        }
        bills.removeAll { $0.number == number }
        attempt { try repository.deleteBill(number: number) }
    }

    /// One bill, by the app's own number.
    ///
    /// The sheets re-read through this rather than holding the copy they were
    /// opened with, so a bill edited behind an open screen does not go on showing
    /// what it used to say.
    func bill(number: Int) -> Bill? {
        bills.first { $0.number == number }
    }

    // MARK: - Photographs of the paper
    //
    // Ids only. The files are the platform's to write, because an image codec is
    // not domain work, and they are kept out of the shop file because that whole
    // record is rewritten every time stock moves.

    /// Records that a photograph belongs to this bill. Adding the same one twice
    /// is a no-op.
    @discardableResult
    func attachPhoto(billNumber: Int, photoID: String) -> Bill? {
        let id = photoID.trimmed
        guard !id.isEmpty,
              let index = bills.firstIndex(where: { $0.number == billNumber }) else { return nil }
        guard !bills[index].photoIDs.contains(id) else { return bills[index] }
        bills[index].photoIDs.append(id)
        let updated = bills[index]
        attempt { try repository.update(updated) }
        return updated
    }

    /// Forgets a photograph.
    ///
    /// Deleting the file is the caller's job and happens after this, so a crash
    /// in between leaves a file nothing points at — which the sweep collects —
    /// rather than a bill pointing at nothing.
    @discardableResult
    func detachPhoto(billNumber: Int, photoID: String) -> Bill? {
        guard let index = bills.firstIndex(where: { $0.number == billNumber }) else { return nil }
        guard bills[index].photoIDs.contains(photoID) else { return bills[index] }
        bills[index].photoIDs.removeAll { $0 == photoID }
        let updated = bills[index]
        attempt { try repository.update(updated) }
        return updated
    }

    /// Every photograph the book still refers to.
    ///
    /// What the sweep is allowed to keep, and the only direction cleanup ever
    /// runs: files nothing refers to are deleted, but an id whose file is missing
    /// is **never** deleted. A book restored ahead of its pictures has to
    /// re-adopt them the moment they arrive, and pruning would sever that
    /// permanently.
    func photoIDsInUse() -> Set<String> {
        Set(bills.flatMap(\.photoIDs))
    }

    // MARK: - Customers

    /// Distinct customers, **sorted by outstanding balance descending, then bill
    /// count descending** — the people who owe money come
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

        for bill in bills where !bill.who.isBlank {
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

        // Credited goods and figures come off what is owed exactly as payments
        // do. They are kept apart on a statement because only one of them is
        // cash — but a customer who was credited 540 owes 540 less, and every
        // screen that asks what somebody owes has to say so.
        for note in creditNotes {
            if var entry = book[note.customerKey] {
                entry.owed -= note.total
                book[note.customerKey] = entry
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

    /// The customer directory: everybody, filtered by what has been typed, in the
    /// order somebody looks a person up.
    ///
    /// A separate function rather than an argument on `customers()`, because the
    /// two orders answer different questions and neither can be the other's
    /// default. `customers()` is biggest-debt-first, which is what Today's banner
    /// and the owed sheets are built on. This one is by name, because a screen you
    /// go to in order to find Fatima is no use sorted by what Fatima happens to
    /// owe.
    func customers(matching query: String) -> [Customer] {
        customers()
            .filter { Self.partyMatches(name: $0.name, phone: $0.phone, query: query) }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
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
    /// empty box as "close without doing anything".
    @discardableResult
    func recordPayment(
        customerKey: String,
        amount: Double,
        receivedAt: Date = .now,
        note: String? = nil,
        paymentNo: String? = nil
    ) -> Payment? {
        guard amount > 0, !customerKey.isEmpty else { return nil }
        let payment = Payment(
            customerKey: customerKey,
            amount: amount,
            paymentNo: paymentNo?.trimmed.isBlank == false ? paymentNo?.trimmed : nil,
            receivedAt: receivedAt,
            note: note
        )
        payments.append(payment)
        payments.sort { $0.receivedAt > $1.receivedAt }
        attempt { try repository.append(payment) }
        return payment
    }

    /// Corrects a payment that was written down wrong.
    ///
    /// Every part of it, because every part can be mistyped: the amount, the day
    /// the money actually arrived, the note, and the number off the receipt book.
    ///
    /// This did not exist while a payment was an amount and a date — deleting and
    /// re-entering was the honest answer to a record with two fields. A receipt
    /// number changed that: a wrong one is spotted weeks later, reconciling
    /// against the paper book, and re-entering by then means re-picking the
    /// original date and hoping. That is how a statement starts claiming money
    /// arrived on the day it was corrected.
    @discardableResult
    func updatePayment(
        id: UUID,
        amount: Double,
        receivedAt: Date,
        note: String? = nil,
        paymentNo: String? = nil
    ) -> Payment? {
        guard amount > 0, let index = payments.firstIndex(where: { $0.id == id }) else { return nil }

        payments[index].amount = amount
        payments[index].paymentNo = paymentNo?.trimmed.isBlank == false ? paymentNo?.trimmed : nil
        payments[index].receivedAt = receivedAt
        payments[index].note = CustomerRecord.tidied(note)
        payments.sort { $0.receivedAt > $1.receivedAt }

        persistEverything()
        return payments.first { $0.id == id }
    }

    func deletePayment(id: UUID) {
        payments.removeAll { $0.id == id }
        attempt { try repository.delete(paymentID: id) }
    }

    /// The receipt already carrying this number, if any.
    ///
    /// Its own series: a receipt numbered 1024 does not clash with invoice 1024,
    /// and refusing it would be the app inventing a rule the shop's paper does
    /// not have. The same question `billWithInvoiceNo` and `creditNoteWithNo`
    /// ask of theirs.
    func paymentWithNo(_ paymentNo: String?, exceptId: UUID? = nil) -> Payment? {
        let key = InvoiceNo.key(paymentNo)
        guard !key.isEmpty else { return nil }
        return payments.first { $0.id != exceptId && InvoiceNo.key($0.paymentNo) == key }
    }

    func payments(forCustomer key: String) -> [Payment] {
        self.payments.filter { $0.customerKey == key }
    }

    // MARK: - Credit notes

    /// Credits a customer's account, and puts anything returned back on the shelf.
    ///
    /// The shelf moves only for an itemised note, which is `saveBill`'s rule read
    /// backwards: what a bill took off, a credit note for the same goods puts
    /// back, and a note that is only a figure moves nothing. A shop that types
    /// totals keeps its own count either way.
    ///
    /// Nil for a note that credits nothing — the mirror of a bill for nothing,
    /// and refused for the same reason.
    @discardableResult
    func addCreditNote(
        customerKey: String,
        lines: [DraftLine] = [],
        amount: Double? = nil,
        noteNo: String? = nil,
        reason: String? = nil,
        issuedAt: Date = .now
    ) -> CreditNote? {
        guard !customerKey.isEmpty else { return nil }

        let snapshots = snapshot(lines)
        let total = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard total > 0 else { return nil }

        let note = CreditNote(
            customerKey: customerKey,
            lines: snapshots,
            total: total,
            noteNo: noteNo?.trimmed.isBlank == false ? noteNo?.trimmed : nil,
            reason: CustomerRecord.tidied(reason),
            issuedAt: issuedAt
        )

        putBackStock(note)
        creditNotes.append(note)
        creditNotes.sort { $0.issuedAt > $1.issuedAt }
        persistEverything()
        return note
    }

    /// Corrects one, shelf and all.
    ///
    /// Takes back whatever the old note returned before applying the new one, so
    /// a note edited from 5 pieces to 3 leaves 3 on the shelf rather than 8 — the
    /// same take-back-first order `updatePurchase` needs, for the same reason.
    @discardableResult
    func updateCreditNote(
        id: UUID,
        customerKey: String,
        lines: [DraftLine] = [],
        amount: Double? = nil,
        noteNo: String? = nil,
        reason: String? = nil,
        issuedAt: Date
    ) -> CreditNote? {
        guard let index = creditNotes.firstIndex(where: { $0.id == id }) else { return nil }
        guard !customerKey.isEmpty else { return nil }

        let snapshots = snapshot(lines)
        let total = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard total > 0 else { return nil }

        takeBackCreditedStock(creditNotes[index])

        var updated = creditNotes[index]
        updated.customerKey = customerKey
        updated.lines = snapshots
        updated.total = total
        updated.noteNo = noteNo?.trimmed.isBlank == false ? noteNo?.trimmed : nil
        updated.reason = CustomerRecord.tidied(reason)
        updated.issuedAt = issuedAt

        putBackStock(updated)
        creditNotes[index] = updated
        creditNotes.sort { $0.issuedAt > $1.issuedAt }
        persistEverything()
        return updated
    }

    /// Removes one, taking back anything it had put on the shelf.
    func deleteCreditNote(id: UUID) {
        guard let note = creditNotes.first(where: { $0.id == id }) else { return }
        takeBackCreditedStock(note)
        creditNotes.removeAll { $0.id == id }
        persistEverything()
    }

    /// The note already carrying this number, if any — the same question
    /// `billWithInvoiceNo` asks, on a series of its own.
    func creditNoteWithNo(_ noteNo: String?, exceptId: UUID? = nil) -> CreditNote? {
        let key = InvoiceNo.key(noteNo)
        guard !key.isEmpty else { return nil }
        return creditNotes.first { $0.id != exceptId && InvoiceNo.key($0.noteNo) == key }
    }

    func creditNotes(forCustomer key: String) -> [CreditNote] {
        creditNotes.filter { $0.customerKey == key }
    }

    /// Returned goods go back on the shelf. Nothing moves for a note with no lines.
    private func putBackStock(_ note: CreditNote) {
        for line in note.lines {
            guard let uid = line.productUID, var product = self.product(uid: uid) else { continue }
            product.stock = max(0, product.stock + line.qty)
            replace(product)
        }
    }

    private func takeBackCreditedStock(_ note: CreditNote) {
        for line in note.lines {
            guard let uid = line.productUID, var product = self.product(uid: uid) else { continue }
            product.stock = max(0, product.stock - line.qty)
            replace(product)
        }
    }

    // MARK: - What the shop turned over

    /// Everything billed inside `period`, whoever it was billed to.
    ///
    /// The shop-wide twin of a statement's `billed`, and deliberately the same
    /// notion of a period — there is one idea of "this month" in this app and
    /// `StatementPeriod` is it, half-open bounds and all, so a bill written at
    /// midnight on the 1st lands in exactly one month here as it does there.
    ///
    /// Bills only. A credit note reduces what somebody *owes*; it does not
    /// unsell the goods, and a month's takings that quietly shrank when a note
    /// was written weeks later would be a figure nobody could reconcile against
    /// the till. The statement is where the two are netted.
    func soldIn(_ period: StatementPeriod) -> Double {
        let range = period.range()
        return bills.filter { range.contains($0.createdAt) }.reduce(0) { $0 + $1.total }
    }

    /// The other side of the counter, over the same span.
    func boughtIn(_ period: StatementPeriod) -> Double {
        let range = period.range()
        return purchases.filter { range.contains($0.createdAt) }.reduce(0) { $0 + $1.total }
    }

    /// How many bills the shop wrote in `period`.
    func billCountIn(_ period: StatementPeriod) -> Int {
        let range = period.range()
        return bills.filter { range.contains($0.createdAt) }.count
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
            creditNotes: creditNotes(forCustomer: key),
            period: period
        )
    }

    /// Every bill for one customer. A bill removed as a mistake is not here,
    /// because it is not history any more.
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

    /// Sets the shelf count to what was actually counted.
    ///
    /// The honest half of keeping stock at all: bills move the count only where
    /// they were itemised, so the figure is a running tally rather than a
    /// measurement, and this is how the owner tells it the truth after looking
    /// at the shelf. A count is *set*, never added to — "there are twelve" is
    /// what somebody says after counting, and asking them to work out the
    /// difference is asking them to do arithmetic the app can do.
    func setStock(_ product: Product, count: Int) {
        guard var current = self.product(uid: product.uid) else { return }
        current.stock = max(0, count)
        replace(current)
    }

    // MARK: - Suppliers

    /// Every supplier, roster and history merged — the mirror of `customers()`,
    /// and the same three-step order for the same hard-won reason.
    ///
    /// Purchases first, then roster entries, then carried-over balances, and
    /// **payments last**. On the customer side the payments loop once ran second,
    /// which silently dropped every payment from somebody who had never been
    /// billed. On a fresh shop that is the ordinary case, not an edge one: a
    /// supplier is entered with what the paper book says is owed, and the first
    /// thing that ever happens to them is being paid.
    func suppliers() -> [Supplier] {
        var order: [String] = []
        var book: [String: (name: String, count: Int, total: Double, owed: Double)] = [:]

        for purchase in purchases where !purchase.supplierKey.isBlank {
            if var entry = book[purchase.supplierKey] {
                entry.count += 1
                entry.total += purchase.total
                entry.owed += purchase.balance
                book[purchase.supplierKey] = entry
            } else {
                order.append(purchase.supplierKey)
                book[purchase.supplierKey] = (purchase.supplierKey, 1, purchase.total, purchase.balance)
            }
        }

        let roster = Dictionary(uniqueKeysWithValues: supplierRecords.map { ($0.key, $0) })
        for record in supplierRecords where book[record.key] == nil {
            order.append(record.key)
            book[record.key] = (record.name, 0, 0, 0)
        }

        for record in supplierRecords {
            if var entry = book[record.key] {
                entry.owed += record.openingBalance
                book[record.key] = entry
            }
        }

        for payment in supplierPayments {
            if var entry = book[payment.supplierKey] {
                entry.owed -= payment.amount
                book[payment.supplierKey] = entry
            }
        }

        return order
            .compactMap { key -> Supplier? in
                guard let entry = book[key] else { return nil }
                let record = roster[key]
                return Supplier(
                    // A purchase stores only the key, so a supplier somehow off
                    // the roster shows as the key rather than as nothing.
                    name: record?.name ?? entry.name,
                    key: key,
                    purchaseCount: entry.count,
                    total: entry.total,
                    // Rounded for the same reason customers are: netting payments
                    // off balances in binary floating point otherwise leaves
                    // 0.000000001 owed and a screen saying money is outstanding.
                    owed: (entry.owed * 100).rounded() / 100,
                    phone: record?.phone,
                    place: record?.place,
                    openingBalance: record?.openingBalance ?? 0,
                    isOnRoster: record != nil
                )
            }
            .sorted { $0.owed != $1.owed ? $0.owed > $1.owed : $0.purchaseCount > $1.purchaseCount }
    }

    func supplier(key: String) -> Supplier? {
        suppliers().first { $0.key == key }
    }

    /// The supplier directory. `customers(matching:)` mirrored.
    func suppliers(matching query: String) -> [Supplier] {
        suppliers()
            .filter { Self.partyMatches(name: $0.name, phone: $0.phone, query: query) }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    /// Whether one person answers to what has been typed.
    ///
    /// Name and phone, because those are the two things written on the paper the
    /// owner is holding. A blank query matches everybody — the box is empty far
    /// more often than it is full, and a stray space must not empty the screen.
    private static func partyMatches(name: String, phone: String?, query: String) -> Bool {
        let wanted = query.trimmed.lowercased()
        if wanted.isEmpty { return true }
        if name.lowercased().contains(wanted) { return true }
        // Not `?? ""`: an absent phone must read as "no match" rather than as an
        // empty string that every query is a substring of.
        guard let phone else { return false }
        return phone.lowercased().contains(wanted)
    }

    /// Adds a supplier to the roster. A key already there is corrected rather
    /// than duplicated. Returns nil for a blank name.
    @discardableResult
    func addSupplier(
        name: String,
        phone: String? = nil,
        place: String? = nil,
        openingBalance: Double = 0
    ) -> SupplierRecord? {
        guard !name.isBlank else { return nil }
        let record = SupplierRecord(name: name, phone: phone, place: place, openingBalance: openingBalance)
        if let index = supplierRecords.firstIndex(where: { $0.key == record.key }) {
            var existing = supplierRecords[index]
            existing.name = record.name
            existing.phone = record.phone
            existing.place = record.place
            existing.openingBalance = record.openingBalance
            supplierRecords[index] = existing
            attempt { try repository.upsert(existing) }
            return existing
        }
        supplierRecords.append(record)
        attempt { try repository.upsert(record) }
        return record
    }

    /// Corrects a supplier. A changed name that produces a different key is a
    /// **rename**, and the purchases move with it — they carry the key, so unlike
    /// a bill there is no spelling to rewrite, which makes this the simpler half
    /// of the pair.
    func updateSupplier(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0
    ) {
        guard !name.isBlank, let existing = supplierRecords.first(where: { $0.key == key }) else { return }
        let newKey = Supplier.key(for: name)
        let record = SupplierRecord(
            key: newKey,
            name: name,
            phone: phone,
            place: place,
            openingBalance: max(0, openingBalance),
            createdAt: existing.createdAt
        )

        // A rename onto somebody already there merges: one supplier, not two.
        supplierRecords.removeAll { $0.key == key || $0.key == newKey }
        supplierRecords.append(record)
        purchases = purchases.map { purchase in
            guard purchase.supplierKey == key else { return purchase }
            var moved = purchase
            moved.supplierKey = newKey
            return moved
        }
        supplierPayments = supplierPayments.map { payment in
            guard payment.supplierKey == key else { return payment }
            var moved = payment
            moved.supplierKey = newKey
            return moved
        }

        attempt {
            try repository.delete(supplierKey: key)
            try repository.delete(supplierKey: newKey)
            try repository.upsert(record)
            for purchase in purchases where purchase.supplierKey == newKey {
                try repository.update(purchase)
            }
            for payment in supplierPayments where payment.supplierKey == newKey {
                try repository.delete(supplierPaymentID: payment.id)
                try repository.append(payment)
            }
        }
    }

    func removeSupplier(key: String) {
        supplierRecords.removeAll { $0.key == key }
        attempt { try repository.delete(supplierKey: key) }
    }

    // MARK: - Purchases

    /// Records a delivery: stock goes on the shelf, the buying price becomes what
    /// was just paid, and what is still owed lands on the supplier's account.
    ///
    /// `paid == nil` means settled on the spot. A quantity of zero or less is a
    /// no-op, exactly as `restock` treats one.
    @discardableResult
    func recordPurchase(
        /// What arrived, where the shop keeps a count of it. `nil` for a
        /// supplier bill entered as a figure — a mixed load, or something that
        /// never sits on a shelf. Only a named product moves stock.
        product: Product?,
        supplierKey: String,
        quantity: Int = 0,
        unitCost: Double = 0,
        paid: Double? = nil,
        /// What the bill came to, where no product was named.
        amount: Double? = nil,
        createdAt: Date = .now,
        /// The number on the supplier's invoice, when it came with one.
        invoiceNo: String? = nil
    ) -> Purchase? {
        guard !supplierKey.isBlank else { return nil }

        // Itemised only when a product was named *and* a count came with it: a
        // product with no quantity is half an answer, and guessing the other
        // half would put stock on the shelf nobody said arrived.
        let resolved = product.flatMap { self.product(uid: $0.uid) }
        let current = quantity > 0 ? resolved : nil

        // A cost above zero is what was just paid; otherwise the price the shop
        // already had stands. Nothing at all where no product was named.
        var cost = 0.0
        if let current {
            cost = unitCost > 0 ? unitCost : current.cost
        }
        let total = current == nil ? (amount ?? 0) : Double(quantity) * cost
        guard total > 0 else { return nil }

        let purchase = Purchase(
            supplierKey: supplierKey,
            productUID: current?.uid,
            name: current?.name,
            qty: current == nil ? 0 : quantity,
            unitCost: cost,
            total: total,
            // Clamped to the total: a delivery cannot be overpaid, and a typo
            // that says so would put the shop permanently in credit.
            paid: paid.map { min(max(0, $0), total) },
            invoiceNo: invoiceNo,
            createdAt: createdAt
        )
        purchases.insert(purchase, at: 0)
        attempt { try repository.append(purchase) }
        // Cost is "latest paid", not a weighted average: the new figure simply
        // takes over, which is the rule `restock` has always followed. Nothing to
        // take over when no product was named.
        if var updated = current {
            updated.stock += quantity
            updated.cost = cost
            replace(updated)
        }
        return purchase
    }

    /// A supplier's bill with no stock on it: a figure, a date and a number.
    ///
    /// The same record as a delivery, and deliberately so — it is money owed to
    /// the same account, and a statement should not care which way it was
    /// entered.
    @discardableResult
    func recordSupplierBill(
        supplierKey: String,
        amount: Double,
        paid: Double? = nil,
        createdAt: Date = .now,
        invoiceNo: String? = nil
    ) -> Purchase? {
        recordPurchase(
            product: nil,
            supplierKey: supplierKey,
            paid: paid,
            amount: amount,
            createdAt: createdAt,
            invoiceNo: invoiceNo
        )
    }

    /// Rewrites a supplier's bill, and moves the shelf by the difference.
    ///
    /// The mirror of `updateBill`: what the old one put on the shelf comes off,
    /// and what the new one says arrived goes on. A delivery edited down to a bare
    /// figure gives back everything it added.
    @discardableResult
    func updatePurchase(
        id: UUID,
        product: Product?,
        supplierKey: String,
        quantity: Int = 0,
        unitCost: Double = 0,
        paid: Double? = nil,
        amount: Double? = nil,
        createdAt: Date,
        invoiceNo: String? = nil
    ) -> Purchase? {
        // Taken once and still valid at the end: nothing below writes to
        // `purchases`, and `replace` moves products rather than purchases.
        guard let index = purchases.firstIndex(where: { $0.id == id }) else { return nil }
        let existing = purchases[index]
        guard !supplierKey.isBlank else { return nil }

        let resolved = product.flatMap { self.product(uid: $0.uid) }
        let current = quantity > 0 ? resolved : nil

        var cost = 0.0
        if let current {
            cost = unitCost > 0 ? unitCost : current.cost
        }
        let total = current == nil ? (amount ?? 0) : Double(quantity) * cost
        guard total > 0 else { return nil }

        // Reverse the old, then apply the new — the same order as on a bill, and
        // for the same reason.
        takeBackStock(existing)
        if let current {
            // Re-read: `current` was captured before the line above moved the
            // shelf, and adding to that stale count would silently undo the
            // taking-back on any delivery that kept the same product.
            var onShelf = self.product(uid: current.uid) ?? current
            onShelf.stock += quantity
            onShelf.cost = cost
            replace(onShelf)
        }

        var updated = existing
        updated.supplierKey = supplierKey
        updated.productUID = current?.uid
        updated.name = current?.name
        updated.qty = current == nil ? 0 : quantity
        updated.unitCost = cost
        updated.total = total
        updated.paid = paid.map { min(max(0, $0), total) }
        updated.invoiceNo = CustomerRecord.tidied(invoiceNo)
        updated.createdAt = createdAt

        purchases[index] = updated
        attempt { try repository.update(updated) }
        return updated
    }

    /// Removes a supplier's bill and takes its stock back off the shelf.
    func deletePurchase(id: UUID) {
        guard let purchase = purchases.first(where: { $0.id == id }) else { return }
        takeBackStock(purchase)
        purchases.removeAll { $0.id == id }
        attempt { try repository.deletePurchase(id: id) }
    }

    /// Unwinds what a delivery put on the shelf. Only an itemised one put anything
    /// there, so only that one has any to take back.
    private func takeBackStock(_ purchase: Purchase) {
        guard purchase.isItemised,
              let uid = purchase.productUID,
              var product = self.product(uid: uid)
        else { return }
        // Floored at zero. The stock may already have been sold, and a negative
        // shelf count is a worse lie than an optimistic one.
        product.stock = max(0, product.stock - purchase.qty)
        replace(product)
    }

    func purchases(forSupplier key: String) -> [Purchase] {
        purchases.filter { $0.supplierKey == key }
    }

    // MARK: - Money out

    @discardableResult
    func recordSupplierPayment(
        supplierKey: String,
        amount: Double,
        paidAt: Date = .now,
        note: String? = nil,
        paymentNo: String? = nil
    ) -> SupplierPayment? {
        guard amount > 0, !supplierKey.isEmpty else { return nil }
        let payment = SupplierPayment(
            supplierKey: supplierKey,
            amount: amount,
            paymentNo: paymentNo?.trimmed.isBlank == false ? paymentNo?.trimmed : nil,
            paidAt: paidAt,
            note: note
        )
        supplierPayments.insert(payment, at: 0)
        attempt { try repository.append(payment) }
        return payment
    }

    /// The same correction, on the money-out side — see `updatePayment`.
    @discardableResult
    func updateSupplierPayment(
        id: UUID,
        amount: Double,
        paidAt: Date,
        note: String? = nil,
        paymentNo: String? = nil
    ) -> SupplierPayment? {
        guard amount > 0,
              let index = supplierPayments.firstIndex(where: { $0.id == id }) else { return nil }

        supplierPayments[index].amount = amount
        supplierPayments[index].paymentNo = paymentNo?.trimmed.isBlank == false ? paymentNo?.trimmed : nil
        supplierPayments[index].paidAt = paidAt
        supplierPayments[index].note = CustomerRecord.tidied(note)
        supplierPayments.sort { $0.paidAt > $1.paidAt }

        persistEverything()
        return supplierPayments.first { $0.id == id }
    }

    func deleteSupplierPayment(id: UUID) {
        supplierPayments.removeAll { $0.id == id }
        attempt { try repository.delete(supplierPaymentID: id) }
    }

    /// The same question `paymentWithNo` asks, on the money-out receipt book.
    func supplierPaymentWithNo(_ paymentNo: String?, exceptId: UUID? = nil) -> SupplierPayment? {
        let key = InvoiceNo.key(paymentNo)
        guard !key.isEmpty else { return nil }
        return supplierPayments.first { $0.id != exceptId && InvoiceNo.key($0.paymentNo) == key }
    }

    func supplierPayments(for key: String) -> [SupplierPayment] {
        supplierPayments.filter { $0.supplierKey == key }
    }

    /// One supplier's account over a period.
    func statementForSupplier(key: String, period: StatementPeriod) -> Statement? {
        guard let supplier = supplier(key: key) else { return nil }
        return Statement.make(
            supplier: supplier,
            purchases: purchases(forSupplier: key),
            payments: supplierPayments(for: key),
            period: period
        )
    }

    /// The other side of `outstanding()`: who the shop owes, and how much in
    /// total. Derived from `suppliers()` for the same reason — walking purchases
    /// alone would ignore both payments made and balances carried over.
    func payable() -> (names: [String], total: Double) {
        let owing = suppliers().filter { $0.owed > 0 }
        return (owing.map(\.name), owing.reduce(0) { $0 + $1.owed })
    }

    // MARK: - Whole-database operations

    /// Wipes everything and sends the owner back to setup step 1.
    func startOver() {
        products = []
        bills = []
        customerRecords = []
        payments = []
        supplierRecords = []
        purchases = []
        supplierPayments = []
        creditNotes = []
        // Everything goes except the language and the theme. Wiping the shop is a
        // data decision; being handed setup in a language you cannot read — or in
        // a colour scheme you turned off — is not one the owner asked for.
        var fresh = Settings()
        fresh.language = settings.language
        fresh.theme = settings.theme
        settings = fresh
        attempt { try repository.replaceAll(with: ShopState(settings: fresh)) }
    }

    /// Replaces the entire database with the contents of a backup.
    ///
    /// A **swap, not a merge** — the handoff is explicit, and the UI gates it
    /// behind a warning naming what is about to be lost.
    func replaceEverything(with document: BackupDocument) {
        var restored = Settings()
        // The language and the theme belong to the person holding this phone, not
        // to the file — a backup carried over from a shop that reads English must
        // not switch this one. Neither is written into a backup for the same
        // reason, so there is nothing in the document to take them from anyway.
        restored.language = settings.language
        restored.theme = settings.theme
        restored.ownerName = document.ownerName
        // Part of the shop's identity on paper, so it travels with it.
        restored.shopAddress = document.shopAddress ?? ""
        // Currency, unlike language, is a property of the numbers in the file:
        // those prices were entered in it.
        restored.currencyCode = document.currencyCode
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
                    invoiceNo: record.invoiceNo,
                    photoIDs: record.photoIDs ?? [],
                    createdAt: record.createdAt
                )
            },
            customers: document.customers.map {
                CustomerRecord(
                    key: $0.key,
                    name: $0.name,
                    phone: $0.phone,
                    place: $0.place,
                    openingBalance: $0.openingBalance,
                    createdAt: $0.createdAt
                )
            },
            payments: document.payments.map {
                Payment(
                    id: $0.id,
                    customerKey: $0.customerKey,
                    amount: $0.amount,
                    paymentNo: $0.paymentNo,
                    receivedAt: $0.receivedAt,
                    note: $0.note
                )
            },
            suppliers: document.suppliers.map {
                SupplierRecord(
                    key: $0.key,
                    name: $0.name,
                    phone: $0.phone,
                    place: $0.place,
                    openingBalance: $0.openingBalance,
                    createdAt: $0.createdAt
                )
            },
            purchases: document.purchases.map {
                Purchase(
                    id: $0.id,
                    supplierKey: $0.supplierKey,
                    productUID: $0.productUID,
                    name: $0.name,
                    qty: $0.qty,
                    unitCost: $0.unitCost,
                    total: $0.total,
                    paid: $0.paid,
                    invoiceNo: $0.invoiceNo,
                    createdAt: $0.createdAt
                )
            },
            supplierPayments: document.supplierPayments.map {
                SupplierPayment(
                    id: $0.id,
                    supplierKey: $0.supplierKey,
                    amount: $0.amount,
                    paymentNo: $0.paymentNo,
                    paidAt: $0.paidAt,
                    note: $0.note
                )
            },
            creditNotes: document.creditNotes.map { row in
                CreditNote(
                    id: row.id,
                    customerKey: row.customerKey,
                    lines: row.lines.map { BillLine(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price) },
                    total: row.total,
                    noteNo: row.noteNo,
                    reason: row.reason,
                    issuedAt: row.issuedAt
                )
            },
            settings: restored
        )

        attempt { try repository.replaceAll(with: state) }
        products = state.products.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        bills = state.bills.sorted { $0.createdAt > $1.createdAt }
        customerRecords = state.customers.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        payments = state.payments.sorted { $0.receivedAt > $1.receivedAt }
        supplierRecords = state.suppliers.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        purchases = state.purchases.sorted { $0.createdAt > $1.createdAt }
        supplierPayments = state.supplierPayments.sorted { $0.paidAt > $1.paidAt }
        creditNotes = state.creditNotes.sorted { $0.issuedAt > $1.issuedAt }
        settings = restored
    }

    /// Writes the whole shop.
    ///
    /// Credit notes have no fine-grained repository calls of their own, unlike
    /// bills and payments. They are the one record type that moves stock *and*
    /// its own list in the same breath, so a partial write is the failure worth
    /// avoiding — and the Kotlin twin persists them the same way, through the
    /// whole-state door.
    private func persistEverything() {
        let state = ShopState(
            products: products,
            bills: bills,
            customers: customerRecords,
            payments: payments,
            suppliers: supplierRecords,
            purchases: purchases,
            supplierPayments: supplierPayments,
            creditNotes: creditNotes,
            settings: settings
        )
        attempt { try repository.replaceAll(with: state) }
    }

    /// Snapshots the whole database into a backup document.
    func makeBackupDocument(at date: Date = .now) -> BackupDocument {
        BackupDocument(
            exportedAt: date,
            ownerName: settings.ownerName,
            // Written as absent rather than as "" when there is none, so the two
            // builds put the same bytes in the file for a shop with no address.
            shopAddress: settings.shopAddress.isBlank ? nil : settings.shopAddress,
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
                    invoiceNo: bill.invoiceNo,
                    // Absent rather than empty, so a shop with no photographs
                    // writes the same bytes it always did — and the same bytes
                    // Kotlin writes, where `explicitNulls = false` drops it too.
                    photoIDs: bill.photoIDs.isEmpty ? nil : bill.photoIDs,
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
                    paymentNo: $0.paymentNo,
                    receivedAt: $0.receivedAt,
                    note: $0.note
                )
            },
            suppliers: supplierRecords.map {
                BackupDocument.SupplierRecordRow(
                    key: $0.key,
                    name: $0.name,
                    phone: $0.phone,
                    place: $0.place,
                    openingBalance: $0.openingBalance,
                    createdAt: $0.createdAt
                )
            },
            purchases: purchases.map {
                BackupDocument.PurchaseRow(
                    id: $0.id,
                    supplierKey: $0.supplierKey,
                    productUID: $0.productUID,
                    name: $0.name,
                    qty: $0.qty,
                    unitCost: $0.unitCost,
                    total: $0.total,
                    paid: $0.paid,
                    invoiceNo: $0.invoiceNo,
                    createdAt: $0.createdAt
                )
            },
            supplierPayments: supplierPayments.map {
                BackupDocument.SupplierPaymentRow(
                    id: $0.id,
                    supplierKey: $0.supplierKey,
                    amount: $0.amount,
                    paymentNo: $0.paymentNo,
                    paidAt: $0.paidAt,
                    note: $0.note
                )
            },
            creditNotes: creditNotes.map { note in
                BackupDocument.CreditNoteRow(
                    id: note.id,
                    customerKey: note.customerKey,
                    total: note.total,
                    noteNo: note.noteNo,
                    reason: note.reason,
                    issuedAt: note.issuedAt,
                    lines: note.lines.map {
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

