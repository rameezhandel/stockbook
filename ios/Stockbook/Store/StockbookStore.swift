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

    /// The owner's own spending, newest first.
    private(set) var expenses: [Expense] = []

    private(set) var balanceTransfers: [BalanceTransfer] = []

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
            expenses = state.expenses.sorted { $0.spentAt > $1.spentAt }
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

    /// Corrects what a product **is** — its name and its two prices.
    ///
    /// Deliberately cannot touch the count. Editing a product used to set stock
    /// as well, which made it a second, unlabelled `setStock` sitting one
    /// keystroke away from the price boxes: fixing a miscount could rewrite a
    /// selling price, and neither field said whether the number was absolute or
    /// something to add.
    ///
    /// The count now moves for a stated reason and by one route each — arriving
    /// as a delivery, leaving on a bill, or corrected through `setStock`, which
    /// says out loud that it is what was counted on the shelf. Taking the
    /// parameter away rather than ignoring it is what stops the two drifting
    /// back together.
    func update(_ product: Product, name: String, cost: Double, price: Double) {
        guard var updated = self.product(uid: product.uid) else { return }
        updated.name = name.trimmed
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
        photoIDs: [String] = [],
        /// What the bill was for. The owner's own reminder.
        note: String? = nil,
        /// A percentage knocked off the whole bill, when the owner gave one.
        ///
        /// Applied here rather than by the screen, so the arithmetic that decides
        /// what a customer owes lives in one tested place — and so the stored
        /// total, the stored discount and the subtotal can never disagree.
        discountPercent: Double? = nil
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
        let subtotal = snapshots.isEmpty ? (amount ?? 0) : itemised
        // A bill for nothing is not a bill. Either something was sold or a figure
        // was typed; neither is the same as a blank saved by accident. Checked on
        // the subtotal, so a 100% discount still saves a bill rather than
        // silently doing nothing — a line given away is a line that left the
        // shelf, and the shop should have the record.
        guard subtotal > 0 else { return nil }

        let off = Money.discount(subtotal, percent: discountPercent ?? 0, in: settings.currency)
        let total = subtotal - off
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
            note: note,
            discountPercent: off > 0 ? discountPercent : nil,
            discountAmount: off > 0 ? off : nil,
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
                price: line.price,
                // Taken here, from the shelf, at the moment of sale — the only
                // moment it is knowable. `Product.cost` is "what it costs now"
                // and moves every time a delivery is priced, so a line that read
                // it later would answer with a figure from after the sale.
                cost: product.cost
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
        invoiceNo: String? = nil,
        note: String? = nil,
        discountPercent: Double? = nil
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
        let subtotal = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard subtotal > 0 else { return nil }

        let off = Money.discount(subtotal, percent: discountPercent ?? 0, in: settings.currency)
        let total = subtotal - off

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
        updated.note = CustomerRecord.tidied(note)
        updated.discountPercent = off > 0 ? discountPercent : nil
        updated.discountAmount = off > 0 ? off : nil
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
        let transfers = balanceTransfers.filter { !$0.isSupplier }
        var order: [String] = []
        var book: [String: PartyTally] = [:]

        for bill in bills where !bill.who.isBlank {
            let key = Customer.key(for: bill.who)
            if book[key] == nil {
                order.append(key)
                book[key] = PartyTally(name: bill.who.trimmed)
            }
            guard var entry = book[key] else { continue }
            entry.count += 1
            entry.total += bill.total
            entry.owed += bill.balance
            entry.firstBilledAt = Self.earliest(entry.firstBilledAt, bill.createdAt)
            // Paid at the counter is money in, exactly as a payment made later
            // is. Only the two together answer "when did they last pay me" — a
            // shop whose customers settle on the spot has no `Payment` rows at
            // all, and would otherwise read as never having been paid.
            if bill.total - bill.balance > Self.cent {
                entry.lastPaidAt = Self.latest(entry.lastPaidAt, bill.createdAt)
            }
            book[key] = entry
        }

        let roster = Dictionary(uniqueKeysWithValues: customerRecords.map { ($0.key, $0) })
        for record in customerRecords where book[record.key] == nil {
            order.append(record.key)
            book[record.key] = PartyTally(name: record.name)
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
                entry.lastPaidAt = Self.latest(entry.lastPaidAt, payment.receivedAt)
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

        // Both ends of every transfer, before either is asked for.
        //
        // The lookups below skip a key that is not already in the book **without
        // a sound** — the shape that stranded the credit notes on a rename. A
        // party reached only by a transfer has no bill and may have no roster
        // entry, so seeding is what keeps the two halves of one transfer from
        // being separated, which would leave the shop's total owed wrong.
        for transfer in transfers {
            for key in [transfer.fromKey, transfer.intoKey] where book[key] == nil {
                book[key] = PartyTally(name: key)
                order.append(key)
            }
        }
        for transfer in transfers {
            if var entry = book[transfer.fromKey] {
                entry.owed -= transfer.amount
                book[transfer.fromKey] = entry
            }
            if var entry = book[transfer.intoKey] {
                entry.owed += transfer.amount
                book[transfer.intoKey] = entry
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
                    isOnRoster: record != nil,
                    // Credit notes and transfers were applied to `owed` above and
                    // are deliberately absent here: both reduce a balance without
                    // a coin moving, and neither is being paid. See `LastPaid`.
                    lastPaidAt: entry.lastPaidAt,
                    firstBilledAt: entry.firstBilledAt
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

    /// The customer a typed name would land on, where that is somebody other
    /// than `exceptKey`.
    ///
    /// Identity in this book is the name, so two accounts cannot share one. The
    /// question this answers — *is that name already taken?* — is the whole of
    /// the gate on renaming, and the form asks it while the owner types so the
    /// answer arrives before the tap rather than after it.
    ///
    /// `exceptKey` is the account being edited. Passing it is what lets somebody
    /// correct a phone number without the form objecting that the name they are
    /// keeping already exists — and what lets a name that has only ever appeared
    /// on bills be promoted onto the roster under its own spelling.
    func customerClashing(_ name: String, exceptKey: String? = nil) -> Customer? {
        let key = Customer.key(for: name)
        guard !key.isEmpty, key != exceptKey else { return nil }
        return customers().first { $0.key == key }
    }

    /// Corrects the facts about a customer already on the roster.
    ///
    /// **Refuses, returning false, where the new name belongs to somebody
    /// else.** It used to merge the two, and merging is defensible when the name
    /// is the identity — but it happened on a keystroke, with no warning and no
    /// undo, and it took the other account's opening balance with it. A mistyped
    /// correction fused two companies' books and quietly changed what each of
    /// them owed. Two accounts entered for one firm are reconciled by moving a
    /// balance — see `transferBalance` — which the owner agrees to on both sides
    /// and can remove afterwards. Doing it on a keystroke instead is data loss.
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
    ) -> Bool {
        guard !name.isBlank, let index = customerRecords.firstIndex(where: { $0.key == key }) else { return false }
        let newKey = Customer.key(for: name)
        // The gate. Checked here and not only in the form, because a rename that
        // silently swallowed another account is the kind of thing that must be
        // impossible rather than merely discouraged.
        guard customerClashing(name, exceptKey: key) == nil else { return false }

        var record = customerRecords[index]
        record.name = name.trimmed
        record.phone = CustomerRecord.tidied(phone)
        record.place = CustomerRecord.tidied(place)
        record.openingBalance = max(0, openingBalance)

        guard newKey != key else {
            customerRecords[index] = record
            attempt { try repository.upsert(record) }
            return true
        }

        // Renamed, and onto a name nothing else answers to — the gate above saw
        // to that. Move the roster entry, then bring the bills and payments with
        // it so nothing is left filed under a name that no longer exists.
        record.key = newKey
        customerRecords.remove(at: index)
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

        // And the two kinds this had never carried. Credit notes were missing
        // for months: rename a credited customer and the note was left under a
        // key nothing pointed at, so it stopped coming off what they owed and
        // their balance silently rose by the credited amount. Balance transfers
        // would have been the same story.
        for (index, note) in creditNotes.enumerated() where note.customerKey == key {
            creditNotes[index].customerKey = newKey
        }
        moveTransfers(from: key, to: newKey, isSupplier: false)
        persistEverything()
        return true
    }


    /// One transfer with `old` rewritten to `new` at whichever end it appears.
    ///
    /// Both ends, because a rename can touch either — and the two ends of one
    /// transfer must never be separated, or the shop's total owed stops
    /// balancing while both screens look fine.
    private func moveTransfers(from old: String, to new: String, isSupplier: Bool) {
        for (index, transfer) in balanceTransfers.enumerated() where transfer.isSupplier == isSupplier {
            if transfer.fromKey == old { balanceTransfers[index].fromKey = new }
            if transfer.intoKey == old { balanceTransfers[index].intoKey = new }
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

    // MARK: - The owner's own spending

    /// Writes down money the owner spent.
    ///
    /// Returns nil rather than saving nonsense: an amount at or below zero is
    /// not an expense, and a blank note is a figure nobody can account for a
    /// month later. Both are refused here rather than in the sheet, so the rule
    /// holds however the store is reached.
    @discardableResult
    func addExpense(amount: Double, note: String, spentAt: Date = .now) -> Expense? {
        let what = note.trimmed
        guard amount > 0, !what.isEmpty else { return nil }

        let expense = Expense(amount: amount, note: what, spentAt: spentAt)
        expenses.insert(expense, at: 0)
        persistEverything()
        return expense
    }

    /// The same money as `spentIn`, broken down by what it went on. Biggest first.
    ///
    /// A shop asking where last month went does not want forty-seven lines, it
    /// wants "petrol 780, rent 2,000, tea 110". Grouping is what turns a list
    /// into an answer.
    ///
    /// Grouped the way `expenseNotes` groups — case-insensitively, showing the
    /// most recent spelling — so "Petrol", "petrol" and "PETROL" are one line
    /// rather than three. That collapsing is worth as much here as it is in the
    /// suggestion list, arguably more: three lines for one thing does not just
    /// look untidy, it hides how much the shop actually spends on it.
    func spendingIn(_ period: StatementPeriod) -> [SpendLine] {
        let range = period.range()
        let inside = expenses.filter { range.contains($0.spentAt) && !$0.note.isBlank }
        return Dictionary(grouping: inside) { $0.note.trimmed.lowercased() }
            .compactMap { _, group -> SpendLine? in
                guard let newest = group.max(by: { $0.spentAt < $1.spentAt }) else { return nil }
                return SpendLine(
                    what: newest.note.trimmed,
                    times: group.count,
                    total: group.reduce(0) { $0 + $1.amount }
                )
            }
            .sorted { $0.total == $1.total ? $0.what < $1.what : $0.total > $1.total }
    }

    /// What the owner has called an expense before, most-used first.
    ///
    /// A shop buys petrol every week and a fan belt once. Typing "Petrol" fifty
    /// times a year is fifty chances to spell it three ways, and a ledger with
    /// "Petrol", "petrol" and "Petrol " in it cannot be read as one thing.
    ///
    /// **Nothing new is stored for this.** These are the notes already on the
    /// expenses themselves — the memory existed, it was simply never read back.
    /// No new field, nothing added to the backup, no format version to bump.
    ///
    /// Grouped case-insensitively, and the spelling shown is the **most recent**
    /// one: an owner who has started writing "Petrol" should be offered that
    /// rather than the "petrol" they abandoned in March. Ties on how often go to
    /// whichever was used last.
    ///
    /// - Parameters:
    ///   - matching: what has been typed so far. Empty offers the usual few,
    ///     which is the whole point of the list appearing before a key is pressed.
    ///   - limit: kept small on purpose. This is a shortcut for the handful of
    ///     things a shop buys constantly, not a directory of everything it has
    ///     ever bought — that is what the expenses list itself is.
    func expenseNotes(matching: String = "", limit: Int = 6) -> [String] {
        let needle = matching.trimmed.lowercased()
        let groups = Dictionary(grouping: expenses.filter { !$0.note.isBlank }) {
            $0.note.trimmed.lowercased()
        }
        return groups
            .filter { needle.isEmpty || $0.key.contains(needle) }
            .compactMap { _, group -> (note: String, count: Int, last: Date)? in
                guard let newest = group.max(by: { $0.spentAt < $1.spentAt }) else { return nil }
                return (newest.note.trimmed, group.count, newest.spentAt)
            }
            .sorted { left, right in
                left.count == right.count ? left.last > right.last : left.count > right.count
            }
            .prefix(limit)
            .map(\.note)
    }

    /// Corrects one. Same rules as writing it: a correction cannot make it invalid.
    @discardableResult
    func updateExpense(id: String, amount: Double, note: String, spentAt: Date) -> Expense? {
        guard let index = expenses.firstIndex(where: { $0.id == id }) else { return nil }
        let what = note.trimmed
        guard amount > 0, !what.isEmpty else { return nil }

        expenses[index].amount = amount
        expenses[index].note = what
        expenses[index].spentAt = spentAt
        persistEverything()
        return expenses[index]
    }

    /// Removes one outright.
    ///
    /// Nothing to put back and nothing to recalculate — the dividend of an
    /// expense being attached to nothing. Deleting a bill has to return its
    /// stock and free its number; this is a line disappearing from a private
    /// list.
    func deleteExpense(id: String) {
        guard expenses.contains(where: { $0.id == id }) else { return }
        expenses.removeAll { $0.id == id }
        persistEverything()
    }

    /// What the owner spent inside `period`.
    ///
    /// The same notion of a period as `soldIn` and `boughtIn`. Deliberately
    /// *not* netted against either: this figure stands beside the shop's, never
    /// inside it.
    func spentIn(_ period: StatementPeriod) -> Double {
        let range = period.range()
        return expenses.filter { range.contains($0.spentAt) }.reduce(0) { $0 + $1.amount }
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

    /// The bills themselves over `period`, newest first — what the sales half of
    /// the book lists.
    ///
    /// Through `StatementPeriod` like every other span in this app, so the list a
    /// customer's statement is checked against and the list the owner scrolls
    /// cover exactly the same days. A second notion of "this month" for a screen
    /// is how two pages of one book start disagreeing about which bills belong to
    /// it.
    ///
    /// The order is `bills`' own, which is newest first, because the reason to
    /// open this list is nearly always to find something written recently.
    func billsIn(_ period: StatementPeriod, calendar: Calendar = .current) -> [Bill] {
        let range = period.range(calendar: calendar)
        return bills.filter { range.contains($0.createdAt) }
    }

    /// The deliveries over `period`, newest first — the purchases half's twin of
    /// `billsIn`, through the same span for the same reason.
    func purchasesIn(_ period: StatementPeriod, calendar: Calendar = .current) -> [Purchase] {
        let range = period.range(calendar: calendar)
        return purchases.filter { range.contains($0.createdAt) }
    }

    /// The owner's own spending over `period`, newest first.
    ///
    /// The records themselves, not `spendingIn`'s folded totals: that page
    /// answers what the money went *on*, and this list answers what was spent and
    /// when — which is the one an owner scrolls to find the receipt they are
    /// holding.
    func expensesIn(_ period: StatementPeriod, calendar: Calendar = .current) -> [Expense] {
        let range = period.range(calendar: calendar)
        return expenses.filter { range.contains($0.spentAt) }
    }

    /// How many bills the shop wrote in `period`.
    func billCountIn(_ period: StatementPeriod) -> Int {
        let range = period.range()
        return bills.filter { range.contains($0.createdAt) }.count
    }

    /// What `period` left the shop with, and what it could not account for.
    ///
    /// **Cost comes off the bill's own lines, never off the shelf.** Each line
    /// carries what one piece cost at the moment it was sold; `Product.cost` is
    /// what it costs *now* and would rewrite last March every time a supplier
    /// put a price up. It is also why this is not `boughtIn`: a hundred padlocks
    /// delivered in March and three sold is not a March loss, and only the three
    /// belong here.
    ///
    /// **The discount needs no apportioning.** `Bill.total` is stored after the
    /// discount, so a bill's takings less its lines' cost is exactly what that
    /// bill earned — no share-out across lines, and no rounding drift from doing
    /// one.
    ///
    /// A bill is countable only when it lists products **and every line knows
    /// its cost**. One line that does not is enough to set the whole bill aside:
    /// counting the rest would subtract part of the cost from all of the
    /// takings, which flatters the answer rather than admitting it cannot give
    /// one.
    func earningsIn(_ period: StatementPeriod) -> Earnings {
        let range = period.range()
        let inPeriod = bills.filter { range.contains($0.createdAt) }

        // What one line cost, preferring what was recorded at the sale and
        // falling back to what the product costs today. Nil only where there is
        // no figure to be had at all — the product has been deleted, so even the
        // shelf cannot answer.
        func unitCost(_ line: BillLine) -> Double? {
            line.cost ?? line.productUID.flatMap { product(uid: $0) }?.cost
        }

        let asTotal = inPeriod.filter { !$0.isItemised }
        let itemised = inPeriod.filter { $0.isItemised }
        let costable = itemised.filter { $0.lines.allSatisfy { unitCost($0) != nil } }
        let beforeCosts = itemised.filter { $0.lines.contains { unitCost($0) == nil } }
        // Counted, but on today's prices rather than the day's. Labelled as such
        // on the page, and self-clearing: every bill written from now on carries
        // its own figure.
        let estimated = costable.filter { $0.lines.contains { $0.cost == nil } }

        let notes = creditNotes.filter { range.contains($0.issuedAt) }

        return Earnings(
            sold: inPeriod.reduce(0) { $0 + $1.total },
            soldAsTotal: asTotal.reduce(0) { $0 + $1.total },
            billsAsTotal: asTotal.count,
            soldEstimated: estimated.reduce(0) { $0 + $1.total },
            billsEstimated: estimated.count,
            soldBeforeCosts: beforeCosts.reduce(0) { $0 + $1.total },
            billsBeforeCosts: beforeCosts.count,
            costOfGoods: costable.reduce(0) { running, bill in
                running + bill.lines.reduce(0) { $0 + Double($1.qty) * (unitCost($1) ?? 0) }
            },
            // A return is a sale run backwards, so it is costed the same way and
            // with the same fallback. A line whose product has since been
            // deleted puts nothing back, which understates what the shop earned
            // rather than overstating it — and the page says so.
            goodsReturned: notes.reduce(0) { running, note in
                running + note.lines.reduce(0) { $0 + Double($1.qty) * (unitCost($1) ?? 0) }
            },
            expenses: spentIn(period),
            credited: notes.reduce(0) { $0 + $1.total },
            creditNotes: notes.count,
            // A note written as a plain figure hands nothing back, so there is
            // nothing to value and it belongs in neither count. The whole credit
            // comes off it, correctly, with nothing to disclose.
            creditNotesEstimated: notes.filter { note in
                !note.lines.isEmpty
                    && note.lines.allSatisfy { unitCost($0) != nil }
                    && note.lines.contains { $0.cost == nil }
            }.count,
            creditNotesBeforeCosts: notes.filter { note in
                !note.lines.isEmpty && note.lines.contains { unitCost($0) == nil }
            }.count
        )
    }

    /// Everything that happened on one day, oldest first.
    ///
    /// The one place in this app that reads all six dated records together, and
    /// the reason it exists: `soldIn` answers what was billed and `spentIn` what
    /// was spent, but a shopkeeper closing up wants the day itself — what was
    /// sold, what came in against it, what arrived, what went out — on one page
    /// they can hold beside the cash box.
    ///
    /// Through `StatementPeriod.custom` with the same date at both ends, which
    /// already resolves to one whole day in the phone's own calendar. There is
    /// one idea of a span in this app and inventing a second for a single day is
    /// how a bill written at ten to midnight starts landing on two of them.
    ///
    /// Names are read from `customers()` and `suppliers()` rather than from the
    /// rosters directly, so a person is spelled here exactly as every other
    /// screen spells them — roster spelling where there is one, the most recent
    /// bill's otherwise. Both walk the whole book, which is work this does not
    /// need but correctness this cannot do without: a day book naming somebody
    /// differently from the statement it sits beside is a day book the owner
    /// stops trusting.
    func dayBook(_ day: Date, calendar: Calendar = .current) -> DayBook {
        let range = StatementPeriod.custom(from: day, to: day).range(calendar: calendar)
        let customerName = Dictionary(customers().map { ($0.key, $0.name) }, uniquingKeysWith: { first, _ in first })
        let supplierName = Dictionary(suppliers().map { ($0.key, $0.name) }, uniquingKeysWith: { first, _ in first })

        // Where each account named on this page stood when the day closed.
        //
        // **Read out of that account's own statement**, run to the end of this
        // day, rather than summed again here. The figure under a customer's name
        // and the figure on the statement they may be handed are the same claim
        // about the same money, and two calculations of it would eventually
        // disagree — on the day somebody is holding both pieces of paper.
        //
        // The period starts at the first record so nothing is left out of the
        // opening figure, and ends on this day rather than at now: a page headed
        // with a date in August that carried December's balance would be two
        // claims side by side.
        //
        // Memoised, because a customer with three bills on one day is one
        // account and each lookup walks their whole history.
        let over = StatementPeriod.custom(from: min(earliestRecord(), day), to: day)
        var closing: [String: Double?] = [:]
        func closingFor(_ key: String, isSupplier: Bool) -> Double? {
            guard !key.isEmpty else { return nil }
            let cacheKey = isSupplier ? "s:\(key)" : "c:\(key)"
            if let cached = closing[cacheKey] { return cached }
            let balance = isSupplier
                ? statementForSupplier(key: key, period: over)?.closingBalance
                : statement(forCustomer: key, period: over)?.closingBalance
            closing[cacheKey] = balance
            return balance
        }

        var entries: [DayEntry] = []

        for bill in bills where range.contains(bill.createdAt) {
            entries.append(
                DayEntry(
                    kind: .bill,
                    // Through the same table the other five kinds go through,
                    // and not `bill.who` — that is the spelling typed at the
                    // counter, and one page carrying "ahmed contracting" on the
                    // bill and "Ahmed Contracting" on his payment reads as two
                    // people.
                    who: customerName[Customer.key(for: bill.who)] ?? bill.who,
                    reference: bill.invoiceNo,
                    billNumber: bill.number,
                    amount: bill.total,
                    // What the customer actually handed over. `balance` is zero
                    // on a bill paid in full, so this is the whole of it; on one
                    // written on credit it is nothing.
                    settled: bill.total - bill.balance,
                    items: bill.lines.map { DayItem(name: $0.name, qty: $0.qty, amount: $0.lineTotal) },
                    // Blank only on a record from an older file, which has no
                    // account and so no balance — see `closingBalance`.
                    closingBalance: closingFor(Customer.key(for: bill.who), isSupplier: false),
                    at: bill.createdAt
                )
            )
        }
        for payment in payments where range.contains(payment.receivedAt) {
            entries.append(
                DayEntry(
                    kind: .payment,
                    who: customerName[payment.customerKey] ?? payment.customerKey,
                    reference: payment.paymentNo,
                    amount: payment.amount,
                    settled: payment.amount,
                    closingBalance: closingFor(payment.customerKey, isSupplier: false),
                    at: payment.receivedAt
                )
            )
        }
        for note in creditNotes where range.contains(note.issuedAt) {
            entries.append(
                DayEntry(
                    kind: .creditNote,
                    who: customerName[note.customerKey] ?? note.customerKey,
                    reference: note.noteNo,
                    amount: note.total,
                    // Credited, not paid. Nothing left the cash box.
                    settled: 0,
                    items: note.lines.map { DayItem(name: $0.name, qty: $0.qty, amount: $0.lineTotal) },
                    closingBalance: closingFor(note.customerKey, isSupplier: false),
                    at: note.issuedAt
                )
            )
        }
        for purchase in purchases where range.contains(purchase.createdAt) {
            entries.append(
                DayEntry(
                    kind: .purchase,
                    who: supplierName[purchase.supplierKey] ?? purchase.supplierKey,
                    reference: purchase.invoiceNo,
                    amount: purchase.total,
                    settled: purchase.total - purchase.balance,
                    // `items`, never `lines` — a delivery entered before a
                    // delivery could hold more than one product keeps its
                    // itemisation only through here.
                    items: purchase.items.map { DayItem(name: $0.name, qty: $0.qty, amount: $0.lineTotal) },
                    closingBalance: closingFor(purchase.supplierKey, isSupplier: true),
                    at: purchase.createdAt
                )
            )
        }
        for payment in supplierPayments where range.contains(payment.paidAt) {
            entries.append(
                DayEntry(
                    kind: .supplierPayment,
                    who: supplierName[payment.supplierKey] ?? payment.supplierKey,
                    reference: payment.paymentNo,
                    amount: payment.amount,
                    settled: payment.amount,
                    closingBalance: closingFor(payment.supplierKey, isSupplier: true),
                    at: payment.paidAt
                )
            )
        }
        for expense in expenses where range.contains(expense.spentAt) {
            entries.append(
                DayEntry(
                    kind: .expense,
                    // An expense is joined to nobody, so what it went on is the
                    // only name it has.
                    who: expense.note,
                    amount: expense.amount,
                    settled: expense.amount,
                    at: expense.spentAt
                )
            )
        }

        return DayBook(day: day, entries: entries.sorted { $0.at < $1.at })
    }

    // MARK: - Statements

    /// One customer's account over a period.
    ///
    /// The arithmetic is in `Statement.make`, which takes plain arrays — this only
    /// decides which arrays. That is what keeps the figures testable without a
    /// store, a repository or a screen.
    /// Every customer's whole history, one statement each, in directory order.
    ///
    /// The book the owner prints once and files. **All time**: the range runs
    /// from the earliest thing on record to now, so nothing is left out and the
    /// closing figures are what each account stands at today.
    ///
    /// **Every customer, including the ones with nothing.** A name with no bills
    /// and no balance still gets its statement, for the reason the day ledger
    /// gives: a book read against a paper one has to have the same names in it,
    /// and a reader cannot tell an account that was left out from one that had a
    /// quiet year.
    ///
    /// Built in **one pass**, unlike calling `statement(forCustomer:period:)` in
    /// a loop. That function asks the store for the customer and again for the
    /// transfer names, and each of those walks every bill in the shop — so a
    /// hundred customers would mean two hundred walks of the whole history to
    /// produce one document. Here the history is bucketed by key once and each
    /// statement is made from its own slice.
    func ledgerBook(calendar: Calendar = .current) -> [Statement] {
        let everyone = customers(matching: "")
        guard !everyone.isEmpty else { return [] }

        let period = StatementPeriod.custom(from: earliestRecord(), to: .now)
        let names = Dictionary(everyone.map { ($0.key, $0.name) }, uniquingKeysWith: { first, _ in first })
        let billsByKey = Dictionary(grouping: bills.filter { !$0.who.isBlank }) { Customer.key(for: $0.who) }
        let paymentsByKey = Dictionary(grouping: payments, by: \.customerKey)
        let notesByKey = Dictionary(grouping: creditNotes, by: \.customerKey)
        let transfers = balanceTransfers.filter { !$0.isSupplier }

        return everyone.map { customer in
            let theirs = transfers.filter { $0.fromKey == customer.key || $0.intoKey == customer.key }
            return Statement.make(
                customer: customer,
                bills: billsByKey[customer.key] ?? [],
                payments: paymentsByKey[customer.key] ?? [],
                creditNotes: notesByKey[customer.key] ?? [],
                transfers: theirs.map { transfer in
                    let outgoing = transfer.fromKey == customer.key
                    let other = outgoing ? transfer.intoKey : transfer.fromKey
                    return Statement.Entry.transfer(
                        transfer,
                        outgoing: outgoing,
                        otherName: names[other] ?? other
                    )
                },
                period: period,
                calendar: calendar
            )
        }
    }

    /// The oldest moment the shop has a record of, or now on an empty book.
    ///
    /// An opening balance carries no date of its own — it came over from the
    /// paper book — so it cannot be in here. It does not need to be: a statement
    /// counts everything before the range into its opening figure, and a range
    /// that starts at the first bill already has the carried-over balance behind
    /// it.
    private func earliestRecord() -> Date {
        let moments = bills.map(\.createdAt)
            + payments.map(\.receivedAt)
            + creditNotes.map(\.issuedAt)
            + purchases.map(\.createdAt)
            + balanceTransfers.map(\.movedAt)
        return moments.min() ?? .now
    }

    func statement(forCustomer key: String, period: StatementPeriod) -> Statement? {
        guard let customer = customer(key: key) else { return nil }
        return Statement.make(
            customer: customer,
            bills: bills(forCustomer: key),
            payments: payments(forCustomer: key),
            creditNotes: creditNotes(forCustomer: key),
            transfers: transferEntries(for: key, isSupplier: false),
            period: period
        )
    }

    /// One payment as its own document: the account it landed on, and where that
    /// account stood the moment after it.
    ///
    /// Read out of that account's own statement rather than summed again here.
    /// The balance a receipt states and the balance the statement states are the
    /// same claim about the same money, and two calculations of it would
    /// eventually disagree — on the day somebody is holding both pieces of paper.
    ///
    /// Nil when the payment is not there, which is what a receipt asked for after
    /// its payment was deleted has to be.
    func receipt(forPayment id: UUID) -> PaymentReceipt? {
        guard let payment = payments.first(where: { $0.id == id }),
              let statement = statement(
                  forCustomer: payment.customerKey,
                  period: wholeHistory(around: payment.receivedAt)
              )
        else { return nil }
        return Self.receipt(
            statement: statement,
            entryId: "payment-\(id.uuidString)",
            paymentNo: payment.paymentNo,
            amount: payment.amount,
            at: payment.receivedAt,
            note: payment.note
        )
    }

    /// The whole book, stretched to make sure one moment is inside it.
    ///
    /// `earliestRecord()` does not look at supplier payments, and neither end of
    /// it knows about a payment somebody dated next week. Either would put the
    /// payment outside the range and leave it out of the statement it is being
    /// looked up in — so the range is widened to hold it rather than trusted to.
    private func wholeHistory(around moment: Date) -> StatementPeriod {
        .custom(from: min(earliestRecord(), moment), to: max(.now, moment))
    }

    /// The common half of both receipts: find the payment's own row in the
    /// statement and take the balance printed beside it.
    private static func receipt(
        statement: Statement,
        entryId: String,
        paymentNo: String?,
        amount: Double,
        at: Date,
        note: String?
    ) -> PaymentReceipt? {
        guard let index = statement.entries.firstIndex(where: { $0.id == entryId }) else { return nil }
        let after = statement.runningBalances[index]
        return PaymentReceipt(
            party: statement.party,
            paymentNo: paymentNo,
            amount: amount,
            at: at,
            note: note,
            // A payment settles and does nothing else, so what the account stood
            // at before it is this figure plus the payment. Derived rather than
            // read from the row above: there may not be a row above.
            balanceBefore: after + amount,
            balanceAfter: after
        )
    }


    // MARK: - Moving a balance between two accounts

    /// Moves `amount` of what one account owes onto another, both of them real.
    ///
    /// Both accounts survive and both keep their invoices — the copy in the
    /// customer's file says which branch it went to, so re-filing it under the
    /// other would put this book out of step with paper they are holding. Only
    /// the outstanding figure moves.
    ///
    /// Refused between an account and itself, and where either side is unknown.
    /// An amount larger than what is owed is allowed: the app already reads a
    /// negative balance as money held in advance.
    @discardableResult
    func transferBalance(
        fromKey: String,
        intoKey: String,
        amount: Double,
        isSupplier: Bool = false,
        note: String? = nil,
        movedAt: Date = .now
    ) -> BalanceTransfer? {
        guard fromKey != intoKey, !fromKey.isEmpty, !intoKey.isEmpty, amount > 0 else { return nil }
        let known: (String) -> Bool = isSupplier
            ? { self.supplier(key: $0) != nil }
            : { self.customer(key: $0) != nil }
        guard known(fromKey), known(intoKey) else { return nil }

        let transfer = BalanceTransfer(
            fromKey: fromKey,
            intoKey: intoKey,
            isSupplier: isSupplier,
            amount: amount,
            note: CustomerRecord.tidied(note),
            movedAt: movedAt
        )
        balanceTransfers.append(transfer)
        balanceTransfers.sort { $0.movedAt > $1.movedAt }
        persistEverything()
        return transfer
    }

    /// Removes one. A mistake is edited or removed, not voided — and unlike a
    /// bill there is no stock to give back, so this is the whole of it.
    func deleteBalanceTransfer(id: UUID) {
        balanceTransfers.removeAll { $0.id == id }
        persistEverything()
    }

    /// One account's transfers as statement entries, each already knowing which
    /// end it is and what the account at the other end is called.
    ///
    /// The name is resolved here rather than stored on the record, so a party
    /// renamed afterwards reads correctly and there is no second copy to drift.
    func transferEntries(for key: String, isSupplier: Bool) -> [Statement.Entry] {
        let names: [String: String] = isSupplier
            ? Dictionary(uniqueKeysWithValues: suppliers().map { ($0.key, $0.name) })
            : Dictionary(uniqueKeysWithValues: customers().map { ($0.key, $0.name) })
        return balanceTransfers
            .filter { $0.isSupplier == isSupplier && ($0.fromKey == key || $0.intoKey == key) }
            .map { transfer in
                let outgoing = transfer.fromKey == key
                let other = outgoing ? transfer.intoKey : transfer.fromKey
                return Statement.Entry.transfer(
                    transfer,
                    outgoing: outgoing,
                    otherName: names[other] ?? other
                )
            }
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

    /// The person the Today banner names: the biggest debtor.
    ///
    /// Returned whole rather than as a name, because the banner now wants a date
    /// off them as well — see `Customer.quietDays`. `customers()` is sorted by
    /// what is owed, so the first one owing anything is the one.
    ///
    /// **Not the stalest.** A customer who owes more is not necessarily the one
    /// who has gone quiet longest, and this deliberately answers the first
    /// question rather than the second: the banner is about the biggest debt, and
    /// the age shown is the age of *that* debt.
    func topDebtor() -> Customer? { customers().first { $0.owed > 0 } }

    /// The supplier the outward banner names. The mirror of `topDebtor`.
    func topCreditor() -> Supplier? { suppliers().first { $0.owed > 0 } }

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
        let transfers = balanceTransfers.filter(\.isSupplier)
        var order: [String] = []
        var book: [String: PartyTally] = [:]

        for purchase in purchases where !purchase.supplierKey.isBlank {
            let key = purchase.supplierKey
            if book[key] == nil {
                order.append(key)
                book[key] = PartyTally(name: key)
            }
            guard var entry = book[key] else { continue }
            entry.count += 1
            entry.total += purchase.total
            entry.owed += purchase.balance
            entry.firstBilledAt = Self.earliest(entry.firstBilledAt, purchase.createdAt)
            // Settled on the delivery is money out, exactly as a payment made
            // afterwards is — the mirror of what a bill does on the other side.
            if purchase.total - purchase.balance > Self.cent {
                entry.lastPaidAt = Self.latest(entry.lastPaidAt, purchase.createdAt)
            }
            book[key] = entry
        }

        let roster = Dictionary(uniqueKeysWithValues: supplierRecords.map { ($0.key, $0) })
        for record in supplierRecords where book[record.key] == nil {
            order.append(record.key)
            book[record.key] = PartyTally(name: record.name)
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
                entry.lastPaidAt = Self.latest(entry.lastPaidAt, payment.paidAt)
                book[payment.supplierKey] = entry
            }
        }

        // Both ends seeded before either is asked for, exactly as on the
        // customer side and for the same reason: a lookup that misses is skipped
        // in silence, and half a transfer is a book that no longer balances.
        for transfer in transfers {
            for key in [transfer.fromKey, transfer.intoKey] where book[key] == nil {
                book[key] = PartyTally(name: key)
                order.append(key)
            }
        }
        for transfer in transfers {
            if var entry = book[transfer.fromKey] {
                entry.owed -= transfer.amount
                book[transfer.fromKey] = entry
            }
            if var entry = book[transfer.intoKey] {
                entry.owed += transfer.amount
                book[transfer.intoKey] = entry
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
                    isOnRoster: record != nil,
                    // Transfers moved a figure between two of the shop's own
                    // accounts and paid nobody, so they are not in here. See
                    // `LastPaid`.
                    lastPaidAt: entry.lastPaidAt,
                    firstBilledAt: entry.firstBilledAt
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

    /// The supplier a typed name would land on. The twin of `customerClashing`.
    func supplierClashing(_ name: String, exceptKey: String? = nil) -> Supplier? {
        let key = Supplier.key(for: name)
        guard !key.isEmpty, key != exceptKey else { return nil }
        return suppliers().first { $0.key == key }
    }

    /// Corrects a supplier. A changed name that produces a different key is a
    /// **rename**, and the purchases move with it — they carry the key, so unlike
    /// a bill there is no spelling to rewrite, which makes this the simpler half
    /// of the pair.
    ///
    /// **Refuses, returning false, where the new name belongs to somebody
    /// else**, for the reason `updateCustomer` gives: a rename that swallows
    /// another account is a merge, and a merge nobody asked for is data loss.
    func updateSupplier(
        key: String,
        name: String,
        phone: String?,
        place: String?,
        openingBalance: Double = 0
    ) -> Bool {
        guard !name.isBlank, let existing = supplierRecords.first(where: { $0.key == key }) else { return false }
        let newKey = Supplier.key(for: name)
        guard supplierClashing(name, exceptKey: key) == nil else { return false }
        let record = SupplierRecord(
            key: newKey,
            name: name,
            phone: phone,
            place: place,
            openingBalance: max(0, openingBalance),
            createdAt: existing.createdAt
        )

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
        moveTransfers(from: key, to: newKey, isSupplier: true)

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
        return true
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
        /// Every product on the delivery note. Empty for a bill entered as a figure.
        lines: [DraftPurchaseLine],
        supplierKey: String,
        paid: Double? = nil,
        /// What the bill came to, where no product was named.
        amount: Double? = nil,
        createdAt: Date = .now,
        /// The number on the supplier's invoice, when it came with one.
        invoiceNo: String? = nil
    ) -> Purchase? {
        guard !supplierKey.isBlank else { return nil }

        let snapshots = snapshotDelivery(lines)
        let total = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard total > 0 else { return nil }

        let purchase = Purchase(
            supplierKey: supplierKey,
            lines: snapshots,
            total: total,
            // Clamped to the total: a delivery cannot be overpaid, and a typo
            // that says so would put the shop permanently in credit.
            paid: paid.map { min(max(0, $0), total) },
            invoiceNo: invoiceNo,
            createdAt: createdAt
        )
        purchases.insert(purchase, at: 0)
        attempt { try repository.append(purchase) }
        putOnShelf(snapshots)
        return purchase
    }

    /// The one-product delivery, which is what most of them are.
    ///
    /// Kept as a way in rather than folded into the caller, because a delivery of
    /// one thing is the common case and building a one-element array says the
    /// same thing three words longer at every call site.
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
        amount: Double? = nil,
        createdAt: Date = .now,
        invoiceNo: String? = nil
    ) -> Purchase? {
        recordPurchase(
            lines: draftOf(product, quantity: quantity, unitCost: unitCost),
            supplierKey: supplierKey,
            paid: paid,
            amount: amount,
            createdAt: createdAt,
            invoiceNo: invoiceNo
        )
    }

    /// Itemised only when a product was named **and** a count came with it: a
    /// product with no quantity is half an answer, and guessing the other half
    /// would put stock on the shelf nobody said arrived.
    private func draftOf(_ product: Product?, quantity: Int, unitCost: Double) -> [DraftPurchaseLine] {
        guard let product, quantity > 0 else { return [] }
        return [DraftPurchaseLine(productUID: product.uid, qty: quantity, unitCost: unitCost)]
    }

    /// Names and costs each line from the shelf, dropping any product that is no
    /// longer there. The mirror of `snapshot`, which does the same for a bill.
    ///
    /// A zero cost falls back to what the product already cost: the sheet leaves
    /// the box empty when the price has not changed since last time, and reading
    /// that as free would rewrite the product's cost to nothing.
    private func snapshotDelivery(_ lines: [DraftPurchaseLine]) -> [PurchaseLine] {
        lines.compactMap { line in
            guard let product = self.product(uid: line.productUID), line.qty > 0 else { return nil }
            return PurchaseLine(
                productUID: product.uid,
                name: product.name,
                qty: line.qty,
                unitCost: line.unitCost > 0 ? line.unitCost : product.cost
            )
        }
    }

    /// Puts a delivery's lines on the shelf.
    ///
    /// Re-read one line at a time rather than mapped in one pass: a delivery note
    /// may name the same product twice — two boxes at two prices is an ordinary
    /// thing on a supplier's paper — and a stale count captured before the first
    /// line would silently swallow the second.
    ///
    /// Cost is "latest paid", not a weighted average: the new figure simply takes
    /// over, so the last line for a product is the one that sets it.
    private func putOnShelf(_ lines: [PurchaseLine]) {
        for line in lines {
            guard let uid = line.productUID, var onShelf = self.product(uid: uid) else { continue }
            onShelf.stock += line.qty
            onShelf.cost = line.unitCost
            replace(onShelf)
        }
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
        lines: [DraftPurchaseLine],
        supplierKey: String,
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

        let snapshots = snapshotDelivery(lines)
        let total = snapshots.isEmpty ? (amount ?? 0) : snapshots.reduce(0) { $0 + $1.lineTotal }
        guard total > 0 else { return nil }

        // Reverse the old, then apply the new — the same order as on a bill, and
        // for the same reason. `putOnShelf` re-reads each product, so a line the
        // edit kept is not added to a count captured before it was taken off.
        takeBackStock(existing)
        putOnShelf(snapshots)

        var updated = existing
        updated.supplierKey = supplierKey
        updated.lines = snapshots
        updated.total = total
        updated.paid = paid.map { min(max(0, $0), total) }
        updated.invoiceNo = CustomerRecord.tidied(invoiceNo)
        updated.createdAt = createdAt
        // A record written when a delivery held one product is rewritten into the
        // new shape. Left in place they would be a second answer to what arrived,
        // and `items` prefers `lines` — so the old figures would sit there unread,
        // waiting to be believed by something.
        updated.productUID = nil
        updated.name = nil
        updated.qty = 0
        updated.unitCost = 0

        purchases[index] = updated
        attempt { try repository.update(updated) }
        return updated
    }

    /// The one-product correction, the way in for a screen that has one product.
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
        updatePurchase(
            id: id,
            lines: draftOf(product, quantity: quantity, unitCost: unitCost),
            supplierKey: supplierKey,
            paid: paid,
            amount: amount,
            createdAt: createdAt,
            invoiceNo: invoiceNo
        )
    }

    /// Removes a supplier's bill and takes its stock back off the shelf.
    func deletePurchase(id: UUID) {
        guard let purchase = purchases.first(where: { $0.id == id }) else { return }
        takeBackStock(purchase)
        purchases.removeAll { $0.id == id }
        attempt { try repository.deletePurchase(id: id) }
    }

    /// Unwinds what a delivery put on the shelf, line by line. Only an itemised
    /// one put anything there, so only that one has any to take back.
    ///
    /// Reads `Purchase.items` rather than `lines`, so a delivery recorded when a
    /// delivery held one product still gives its stock back.
    private func takeBackStock(_ purchase: Purchase) {
        for line in purchase.items {
            guard let uid = line.productUID, var product = self.product(uid: uid) else { continue }
            // Floored at zero. The stock may already have been sold, and a
            // negative shelf count is a worse lie than an optimistic one.
            product.stock = max(0, product.stock - line.qty)
            replace(product)
        }
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
            transfers: transferEntries(for: key, isSupplier: true),
            period: period
        )
    }

    /// The money-out twin of `receipt(forPayment:)`: a voucher for what the shop
    /// paid out, taken from the supplier's own statement for the same reason.
    func receipt(forSupplierPayment id: UUID) -> PaymentReceipt? {
        guard let payment = supplierPayments.first(where: { $0.id == id }),
              let statement = statementForSupplier(
                  key: payment.supplierKey,
                  period: wholeHistory(around: payment.paidAt)
              )
        else { return nil }
        return Self.receipt(
            statement: statement,
            entryId: "supplier-payment-\(id.uuidString)",
            paymentNo: payment.paymentNo,
            amount: payment.amount,
            at: payment.paidAt,
            note: payment.note
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
        expenses = []
        balanceTransfers = []
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
                    lines: record.lines.map { BillLine(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price, cost: $0.cost) },
                    total: record.total,
                    paid: record.paid,
                    who: record.who,
                    invoiceNo: record.invoiceNo,
                    photoIDs: record.photoIDs ?? [],
                    note: record.note,
                    discountPercent: record.discountPercent,
                    discountAmount: record.discountAmount,
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
                    lines: $0.lines.map {
                        PurchaseLine(
                            productUID: $0.productUID,
                            name: $0.name,
                            qty: $0.qty,
                            unitCost: $0.unitCost
                        )
                    },
                    total: $0.total,
                    paid: $0.paid,
                    invoiceNo: $0.invoiceNo,
                    createdAt: $0.createdAt,
                    // Carried through rather than dropped, so a file written by
                    // an older build keeps what arrived on its deliveries.
                    // `items` prefers `lines`, so on any file written since, the
                    // four below are absent and read as nothing.
                    productUID: $0.productUID,
                    name: $0.name,
                    qty: $0.qty,
                    unitCost: $0.unitCost
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
                    lines: row.lines.map { BillLine(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price, cost: $0.cost) },
                    total: row.total,
                    noteNo: row.noteNo,
                    reason: row.reason,
                    issuedAt: row.issuedAt
                )
            },
            expenses: document.expenses.map {
                Expense(id: $0.id, amount: $0.amount, note: $0.note, spentAt: $0.spentAt)
            },
            balanceTransfers: document.balanceTransfers.map {
                BalanceTransfer(
                    id: $0.id,
                    fromKey: $0.fromKey,
                    intoKey: $0.intoKey,
                    isSupplier: $0.isSupplier,
                    amount: $0.amount,
                    note: $0.note,
                    movedAt: $0.movedAt
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
        expenses = state.expenses.sorted { $0.spentAt > $1.spentAt }
        balanceTransfers = state.balanceTransfers.sorted { $0.movedAt > $1.movedAt }
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
            expenses: expenses,
            balanceTransfers: balanceTransfers,
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
                    note: bill.note,
                    discountPercent: bill.discountPercent,
                    discountAmount: bill.discountAmount,
                    lines: bill.lines.map {
                        BackupDocument.LineRecord(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price, cost: $0.cost)
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
                    // `items`, not `lines`: a delivery recorded when a delivery
                    // held one product travels in the new shape rather than the
                    // old one, so the file coming out has exactly one way of
                    // saying what arrived.
                    lines: $0.items.map {
                        BackupDocument.PurchaseLineRecord(
                            productUID: $0.productUID,
                            name: $0.name,
                            qty: $0.qty,
                            unitCost: $0.unitCost
                        )
                    },
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
                        BackupDocument.LineRecord(productUID: $0.productUID, name: $0.name, qty: $0.qty, price: $0.price, cost: $0.cost)
                    }
                )
            },
            expenses: expenses.map {
                BackupDocument.ExpenseRow(id: $0.id, amount: $0.amount, note: $0.note, spentAt: $0.spentAt)
            },
            balanceTransfers: balanceTransfers.map {
                BackupDocument.BalanceTransferRow(
                    id: $0.id,
                    fromKey: $0.fromKey,
                    intoKey: $0.intoKey,
                    isSupplier: $0.isSupplier,
                    amount: $0.amount,
                    note: $0.note,
                    movedAt: $0.movedAt
                )
            }
        )
    }

    // MARK: - Building a party

    /// What one account adds up to while `customers()` or `suppliers()` walks
    /// the history, before it becomes a `Customer` or a `Supplier`.
    ///
    /// A named type rather than the tuple this replaces: six fields where four
    /// of them are numbers is a shape that positional construction gets wrong
    /// silently, and two of the six are now dates whose whole job is to be
    /// compared against each other.
    private struct PartyTally {
        let name: String
        var count = 0
        var total: Double = 0
        var owed: Double = 0
        /// The last time money actually moved. See `LastPaid`.
        var lastPaidAt: Date?
        /// The oldest bill or delivery, where the clock starts if none ever has.
        var firstBilledAt: Date?
    }

    /// Half a riyal, as the line between "some of this was paid" and floating
    /// point noise.
    ///
    /// `total - balance` is a subtraction of two figures that were themselves
    /// arrived at by adding prices together, so a bill paid in full can land a
    /// hair either side of zero. Comparing to zero would have a bill nobody paid
    /// anything on reset the clock.
    private static let cent = 0.005

    /// The later of two moments, either of which may be the first one seen.
    private static func latest(_ current: Date?, _ candidate: Date) -> Date {
        guard let current else { return candidate }
        return candidate > current ? candidate : current
    }

    /// The earlier of two, for the date a history starts rather than ends.
    private static func earliest(_ current: Date?, _ candidate: Date) -> Date {
        guard let current else { return candidate }
        return candidate < current ? candidate : current
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

/// What the shop spent on one thing over a stretch of days: what it was, how
/// many times, and what that came to.
struct SpendLine: Equatable {
    let what: String
    let times: Int
    let total: Double
}
/// What a stretch of trading actually left the shop with.
///
/// Four figures and a confession. Takings, less what the goods on those bills
/// cost, is what the goods earned; less what the owner spent is what they kept.
/// The confession is `soldWithoutCost` — takings this cannot account for,
/// because the bill they came from listed no products and so carries no cost.
///
/// **The gap is not an edge case.** Entering a paper bill as a single figure is
/// the ordinary way to use this app, and every such bill is revenue with no cost
/// behind it. A page that quietly answered for the rest of the month would be
/// flattering by exactly the amount it left out, so the amount it left out is on
/// the page.
struct Earnings: Equatable {
    /// Every bill in the period — the same figure Home shows, so the two can be
    /// held side by side and agree.
    let sold: Double
    /// How much of `sold` came from bills that listed no products at all.
    ///
    /// The owner's own choice, and a permanent one for those bills: a paper bill
    /// entered as a single figure has nothing to cost. Itemising future ones is
    /// the only thing that shrinks this.
    let soldAsTotal: Double
    let billsAsTotal: Int
    /// How much came from bills costed at **today's** buying price rather than at
    /// the price recorded when they were sold.
    ///
    /// These are counted, and they are counted honestly labelled. A bill written
    /// before the app kept costs has no figure of its own, so the only one
    /// available is what the product costs now — near enough on a shelf whose
    /// prices have not moved, and wrong by the drift where they have. Better than
    /// a page that cannot answer at all, and only while the old book is still the
    /// bulk of the shop's history.
    ///
    /// **Nothing is written back.** The line's stored cost stays absent, because
    /// absent is the truth about it; this is an estimate made at the moment the
    /// page is read, and it disappears from the page as costed bills replace the
    /// old ones.
    let soldEstimated: Double
    let billsEstimated: Int
    /// And how much cannot be costed even by estimate, because a line names a
    /// product that has since been deleted.
    ///
    /// Kept apart from `soldAsTotal` for the reason that one is kept apart at
    /// all: the two ask different things of the owner. This one asks nothing —
    /// there is no price left anywhere to use.
    let soldBeforeCosts: Double
    let billsBeforeCosts: Int
    /// What the goods on the countable bills cost the shop, as at their sale.
    let costOfGoods: Double
    /// What the goods a credit note brought **back** cost the shop.
    ///
    /// The mirror of `costOfGoods`, and the reason a credit note can be taken
    /// off the earnings honestly. A sale adds what was charged and takes off
    /// what the goods cost; a return takes off what was credited and puts that
    /// cost back, because those pieces are on the shelf again and were never
    /// really sold.
    ///
    /// Zero for a note written as a plain figure — "knock two hundred off" hands
    /// nothing back — and that is not an approximation. It is why the whole
    /// credited amount comes off such a note and only part of it comes off an
    /// itemised one.
    let goodsReturned: Double
    /// What the owner spent over the same days.
    let expenses: Double
    /// Credit notes written in the period, **disclosed and never subtracted**.
    ///
    /// `soldIn` counts bills and not notes, on the settled argument that a note
    /// reduces what somebody *owes* without unselling the goods — and a month's
    /// takings that shrank when a note was written weeks later is a figure
    /// nobody can reconcile against the till. Netting them here and not there
    /// would put two answers to "what did we sell" on two screens. So the owner
    /// is told the notes exist and left to judge.
    let credited: Double
    let creditNotes: Int
    /// Notes whose returned goods were valued at today's buying price.
    let creditNotesEstimated: Int
    /// Notes with goods on them that could not be valued at all, because a line
    /// names a product since deleted.
    ///
    /// Their goods are put back at nothing, which understates what the shop
    /// earned rather than overstating it — the safe direction — and is said on
    /// the page rather than left for the owner to find.
    ///
    /// A figure-only note is **not** one of these. It hands nothing back, so
    /// nothing needs valuing and the full credit comes off correctly.
    let creditNotesBeforeCosts: Int

    /// Everything this page cannot account for, whichever of the two reasons.
    var soldWithoutCost: Double { soldAsTotal + soldBeforeCosts }
    var billsWithoutCost: Int { billsAsTotal + billsBeforeCosts }

    /// Whether any of the costs on the page were guessed from the shelf as it
    /// stands now.
    var hasEstimates: Bool { billsEstimated > 0 || creditNotesEstimated > 0 }

    /// Takings this page can actually account for.
    var counted: Double { sold - soldWithoutCost }

    /// Whether the period has takings but nothing costable in it.
    ///
    /// The state a shop is in the day cost-keeping arrives: every bill in the
    /// book predates it, so the chain would run Sold → 0 → 0 and land on a
    /// "kept" figure that is really just the month's expenses with a minus in
    /// front. **That is not a loss, it is an absence**, and a page that prints
    /// one as the other is worse than a page that admits it cannot say.
    var nothingCostable: Bool { sold > 0 && counted == 0 }

    /// What the goods that actually left the shop cost it: what the sold ones
    /// cost, less what the returned ones cost.
    ///
    /// The line the page draws. Netting the return in here rather than adding a
    /// row of its own is what keeps every line on the page a figure the owner
    /// recognises — and it is the truth about the figure: goods handed back are
    /// goods the shop still has.
    var netCostOfGoods: Double { costOfGoods - goodsReturned }

    /// What the goods earned: what they sold for, less what they cost.
    var goodsEarned: Double { counted - netCostOfGoods }

    /// And what was left after what was credited back and the owner's own
    /// spending.
    ///
    /// `credited` comes off in full here, its goods having already been added
    /// back through `netCostOfGoods`. Take the note off *and* put its stock back
    /// and the arithmetic lands where it should: a customer credited 200 for
    /// goods that cost 140 leaves the shop 60 worse off, not 200.
    var kept: Double { goodsEarned - credited - expenses }

    /// Whether anything was sold at all, countable or not.
    var isEmpty: Bool { sold == 0 && expenses == 0 }

    /// Whether there is anything to confess.
    ///
    /// No longer true merely because a credit note exists: they are taken off
    /// the figures now rather than listed beside them.
    var hasGap: Bool { billsWithoutCost > 0 || creditNotesBeforeCosts > 0 }
}

/// What kind of thing happened, and — through `direction` — which way the money
/// moved when it did.
///
/// Six kinds because six records carry a date, and a day that quietly left one
/// of them out would be a day the owner reconciles against the cash box and
/// cannot make balance.
enum DayEntryKind: CaseIterable {
    case bill, payment, creditNote, purchase, supplierPayment, expense

    /// Which way this kind points: into the cash box, out of it, or neither.
    ///
    /// A `switch` with no `default` on purpose. Add a seventh kind and this
    /// stops compiling, which is the only reliable way to be asked whether it
    /// is money.
    ///
    /// **A credit note is neither.** It reduces what somebody owes without a
    /// coin moving, and counting it as cash taken would overstate the day's
    /// takings by exactly the amount the shop *gave back*.
    var direction: Int {
        switch self {
        case .bill, .payment: 1
        case .purchase, .supplierPayment, .expense: -1
        case .creditNote: 0
        }
    }
}

/// One product on an itemised bill or delivery, as the day's page lists it.
struct DayItem: Equatable {
    let name: String
    let qty: Int
    let amount: Double
}

/// One thing that happened on one day, whichever of the six records it came from.
///
/// Flattened to a common shape here rather than in the document, because the
/// question "what happened today" has one answer and two platforms both have to
/// give it. The alternative — six arrays handed to a layout that decides how
/// they compare — is six chances for iOS and Android to disagree about a figure.
struct DayEntry: Equatable {
    let kind: DayEntryKind
    /// The customer, the supplier, or — for the owner's own spending — what it went on.
    let who: String
    /// The number on the paper, when there is one: an invoice, a receipt, a credit note.
    var reference: String?
    /// The app's own counter, on a bill that has no paper number. Carried rather
    /// than resolved here because "Bill #7" is words, and words live in `Strings`.
    var billNumber: Int?
    /// What the whole thing came to.
    let amount: Double
    /// What actually changed hands at the time — the part of `amount` that was
    /// cash rather than credit. Equal to `amount` on a payment or an expense,
    /// less on a bill part paid, zero on one written entirely on credit.
    let settled: Double
    /// What was on it, where the record says. Empty for a bill entered as a figure.
    var items: [DayItem] = []
    /// What the account this landed on stood at when the day closed.
    ///
    /// Nil where there is no account: an expense is joined to nobody, and a
    /// record restored from an older file with no name on it has nothing to be a
    /// balance of — the same case `dayLedger` skips outright. Nil is not zero:
    /// zero means settled up, and printing it against something that was never
    /// on anyone's account would answer a question nobody asked.
    ///
    /// **The end of the day, not today.** A page headed with a date in August
    /// that carried December's figure would be two different claims side by
    /// side, and the one the owner is reconciling against is the day's.
    var closingBalance: Double?
    let at: Date
}

/// One day of the shop, in the order it happened.
///
/// **The owner's own page and nobody else's.** It names every customer billed
/// that day beside what the shop spent its money on, so it can no more be handed
/// across the counter than the receivable list can — and for the same two
/// reasons. Nothing here is ever called a statement.
struct DayBook: Equatable {
    let day: Date
    let entries: [DayEntry]

    func entries(of kind: DayEntryKind) -> [DayEntry] { entries.filter { $0.kind == kind } }

    /// What came into the cash box: taken at the counter on bills, plus receipts
    /// against what was already owed.
    ///
    /// Summed from `DayEntry.settled` and never from `DayEntry.amount` — a bill
    /// written on credit is a sale that took no money, and a day's takings that
    /// counted it would be wrong by the whole of it.
    var moneyIn: Double { sum(direction: 1) }

    /// And what went out: paid to suppliers on the spot or since, and spent.
    var moneyOut: Double { sum(direction: -1) }

    /// What the day did to the cash box, which may well be negative.
    var net: Double { moneyIn - moneyOut }

    var isEmpty: Bool { entries.isEmpty }

    private func sum(direction: Int) -> Double {
        entries.filter { $0.kind.direction == direction }.reduce(0) { $0 + $1.settled }
    }
}

/// One line of a delivery as the sheet holds it, before it becomes history.
struct DraftPurchaseLine {
    let productUID: UUID
    var qty: Int
    /// What the shop paid per piece. Zero falls back to the product's own cost.
    var unitCost: Double = 0
}

