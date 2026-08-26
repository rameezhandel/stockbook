import Testing
import Foundation
@testable import Stockbook

/// A balance moved between two accounts that are both real.
///
/// The twin of `BalanceTransferTests.kt`. The case is two branches of one
/// contractor consolidating: both were rightly invoiced, both keep their
/// invoices, and only the outstanding figure moves. Neither account is absorbed
/// and no history is re-filed — that is the whole point, and it is why this is
/// the only way the app joins anything up.
///
/// **The invariant that matters is that nothing is created or destroyed.** A
/// transfer moves money between two columns of the same book, so what the shop
/// is owed altogether cannot change — and a half-applied transfer is the one bug
/// here that would be invisible on both screens while quietly making the totals
/// wrong.
@MainActor
@Suite("Balance transfers")
struct BalanceTransferTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// One contractor, entered as two branches, each with its own bill.
    private func shopWithTwoBranches() -> StockbookStore {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        store.addCustomer(name: "Ahmed Riyadh")
        store.addCustomer(name: "Ahmed Jeddah")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed Riyadh", paid: 0)
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 4, price: 95)], customer: "Ahmed Jeddah", paid: 0)
        return store
    }

    @Test("The balance moves off one account and onto the other")
    func moves() throws {
        let store = shopWithTwoBranches()

        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380) != nil)

        #expect(try #require(store.customer(key: "ahmed jeddah")).owed == 0)
        #expect(try #require(store.customer(key: "ahmed riyadh")).owed == 1330, "950 of its own and 380 arrived")
    }

    @Test("What the shop is owed altogether does not change")
    func totalIsUnchanged() {
        // The invariant. A transfer moves a figure between two columns of one
        // book; it cannot make or lose money, and a half-applied one would be
        // invisible on both screens while making this wrong.
        let store = shopWithTwoBranches()
        let before = store.customers().reduce(0) { $0 + $1.owed }

        store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)

        #expect(store.customers().reduce(0) { $0 + $1.owed } == before)
    }

    @Test("The invoices stay where they were issued")
    func invoicesDoNotMove() {
        // The line this feature is built on. The Jeddah branch's copy of its
        // invoice says Jeddah, and rewriting it would put this book out of step
        // with paper the customer is holding.
        let store = shopWithTwoBranches()

        store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)

        #expect(store.bills(forCustomer: "ahmed jeddah").count == 1)
        #expect(store.bills(forCustomer: "ahmed riyadh").count == 1)
        // And both accounts are still there. Nobody was absorbed.
        #expect(store.customers().count == 2)
    }

    @Test("It shows on both statements, as a charge on one and a settlement on the other")
    func bothStatements() throws {
        let store = shopWithTwoBranches()
        store.transferBalance(
            fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380, note: "Consolidating on Riyadh"
        )

        let leaving = try #require(store.statement(forCustomer: "ahmed jeddah", period: .thisMonth()))
        let arriving = try #require(store.statement(forCustomer: "ahmed riyadh", period: .thisMonth()))

        // Its own totals on each side, kept out of what was billed and received.
        #expect(leaving.transferredOut == 380)
        #expect(leaving.transferredIn == 0)
        #expect(leaving.billed == 380, "its own bill, and not the transfer")
        #expect(leaving.received == 0, "no money changed hands")
        #expect(leaving.closingBalance == 0)

        #expect(arriving.transferredIn == 380)
        #expect(arriving.billed == 950, "its own bill, and not the transfer")
        #expect(arriving.closingBalance == 1330)
    }

    @Test("A transfer is neither a payment nor a credit note")
    func ownBucket() throws {
        // The reason it has its own line: `received` is what the shop reconciles
        // against its till, and `credited` is goods or money given back. A
        // transfer is neither, and folding it into either would make that figure
        // mean two things.
        let store = shopWithTwoBranches()
        store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)

        let leaving = try #require(store.statement(forCustomer: "ahmed jeddah", period: .thisMonth()))

        #expect(leaving.received == 0)
        #expect(leaving.credited == 0)
        #expect(leaving.transferredOut == 380)
    }

    @Test("The statement names the account at the other end")
    func namesTheOtherEnd() throws {
        let store = shopWithTwoBranches()
        store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)

        let entry = try #require(store.transferEntries(for: "ahmed jeddah", isSupplier: false).first)
        guard case .transfer(_, let outgoing, let otherName) = entry else {
            Issue.record("expected a transfer entry")
            return
        }
        #expect(outgoing)
        #expect(otherName == "Ahmed Riyadh")

        // Resolved from the roster, so a rename afterwards reads correctly here
        // rather than leaving a stale copy on the record.
        #expect(store.updateCustomer(key: "ahmed riyadh", name: "Ahmed Riyadh Branch", phone: nil, place: nil))
        let renamed = try #require(store.transferEntries(for: "ahmed jeddah", isSupplier: false).first)
        guard case .transfer(_, _, let newName) = renamed else {
            Issue.record("expected a transfer entry")
            return
        }
        #expect(newName == "Ahmed Riyadh Branch")
    }

    @Test("An account cannot transfer to itself, or to somebody who is not there")
    func refusals() {
        let store = shopWithTwoBranches()

        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed jeddah", amount: 100) == nil)
        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "nobody", amount: 100) == nil)
        #expect(store.transferBalance(fromKey: "nobody", intoKey: "ahmed jeddah", amount: 100) == nil)
        #expect(store.transferBalance(fromKey: "", intoKey: "ahmed jeddah", amount: 100) == nil)
        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 0) == nil)
        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: -50) == nil)

        #expect(store.balanceTransfers.isEmpty)
    }

    @Test("More than is owed is allowed, and leaves the account in advance")
    func moreThanIsOwed() throws {
        // The app already reads a negative balance as money held in advance, and
        // refusing would block a legitimate shuffle of a prepayment.
        let store = shopWithTwoBranches()

        #expect(store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 500) != nil)

        #expect(try #require(store.customer(key: "ahmed jeddah")).owed == -120, "380 owed, 500 moved")
        #expect(try #require(store.customer(key: "ahmed riyadh")).owed == 1450)
    }

    @Test("Removing one puts both balances back")
    func removing() throws {
        let store = shopWithTwoBranches()
        let before = store.customers().reduce(into: [String: Double]()) { $0[$1.key] = $1.owed }
        let transfer = try #require(
            store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)
        )

        store.deleteBalanceTransfer(id: transfer.id)

        #expect(store.customers().reduce(into: [String: Double]()) { $0[$1.key] = $1.owed } == before)
        #expect(store.balanceTransfers.isEmpty)
    }

    @Test("A transfer survives being written down and read back")
    func roundTrip() throws {
        let store = shopWithTwoBranches()
        store.transferBalance(
            fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380, note: "Consolidating on Riyadh"
        )

        let reopened = StockbookStore(repository: InMemoryRepository())
        reopened.replaceEverything(with: store.makeBackupDocument())

        #expect(reopened.balanceTransfers.count == 1)
        #expect(reopened.balanceTransfers[0].note == "Consolidating on Riyadh")
        #expect(try #require(reopened.customer(key: "ahmed jeddah")).owed == 0)
        #expect(try #require(reopened.customer(key: "ahmed riyadh")).owed == 1330)
    }

    /// The app's own database, not the backup file — a different door, and until
    /// now an untested one.
    ///
    /// The backup round trip above goes through `BackupDocument`. What the phone
    /// reads on every launch is `ShopState`, and its decoder here is written out
    /// by hand key by key. It was missing this one: the transfers were written to
    /// the file and dropped on the way back in, so the shop would have looked
    /// right until it was next opened and then quietly owed different figures.
    /// Kotlin fills a missing key from the default and so could never have shown
    /// it — this is the half of the pair that has to catch it.
    @Test("A transfer survives the app's own save file")
    func savedStateRoundTrip() throws {
        let repository = InMemoryRepository()
        let store = StockbookStore(repository: repository)
        store.addCustomer(name: "Ahmed Riyadh")
        store.addCustomer(name: "Ahmed Jeddah")
        store.saveBill(customer: "Ahmed Jeddah", paid: 0, amount: 380)
        #expect(
            store.transferBalance(
                fromKey: "ahmed jeddah",
                intoKey: "ahmed riyadh",
                amount: 380,
                note: "Consolidating on Riyadh"
            ) != nil
        )

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let written = try encoder.encode(repository.loadAll())
        let read = try decoder.decode(ShopState.self, from: written)

        #expect(read.balanceTransfers.count == 1)
        let transfer = try #require(read.balanceTransfers.first)
        #expect(transfer.note == "Consolidating on Riyadh")
        #expect(transfer.amount == 380)
        #expect(transfer.fromKey == "ahmed jeddah")
        #expect(transfer.intoKey == "ahmed riyadh")
    }

    @Test("A customer transfer leaves the supplier side alone")
    func sidesAreSeparate() throws {
        // Both sides share a key space — a firm the shop both buys from and sells
        // to has the same key on each — so the record says which side it belongs
        // to rather than letting the keys decide.
        let store = shopWithTwoBranches()
        _ = store.addSupplier(name: "Ahmed Jeddah", openingBalance: 700)

        store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380)

        #expect(try #require(store.supplier(key: "ahmed jeddah")).owed == 700)
    }

    // MARK: Suppliers

    @Test("The supplier side moves the same way")
    func supplierSide() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 4, cost: 60, price: 95)
        let north = try #require(store.addSupplier(name: "Gulf Locks North"))
        _ = try #require(store.addSupplier(name: "Gulf Locks South", openingBalance: 200))
        store.recordPurchase(product: product, supplierKey: north.key, quantity: 10, unitCost: 60, paid: 0)

        let before = store.suppliers().reduce(0) { $0 + $1.owed }
        #expect(
            store.transferBalance(
                fromKey: "gulf locks north", intoKey: "gulf locks south", amount: 600, isSupplier: true
            ) != nil
        )

        #expect(try #require(store.supplier(key: "gulf locks north")).owed == 0)
        #expect(try #require(store.supplier(key: "gulf locks south")).owed == 800)
        #expect(store.suppliers().reduce(0) { $0 + $1.owed } == before, "what the shop owes out is unchanged")
        // The delivery stays with the branch it arrived from.
        #expect(store.purchases(forSupplier: "gulf locks north").count == 1)
    }

    @Test("A supplier transfer cannot name a customer")
    func sideIsChecked() {
        let store = shopWithTwoBranches()

        #expect(
            store.transferBalance(
                fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 380, isSupplier: true
            ) == nil
        )
        #expect(!store.balanceTransfers.contains { $0.isSupplier })
    }
}
