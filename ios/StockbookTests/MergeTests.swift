import Testing
import Foundation
@testable import Stockbook

/// One firm entered twice, joined on purpose.
///
/// The twin of `MergeTests.kt`. The counts are the least of it. What these tests
/// are really for is the money: the merge this replaces happened by accident on
/// a rename, threw one opening balance away and left the credit notes filed
/// under a key nothing pointed at, and the test that covered it asserted row
/// counts alone — which is exactly why none of that was noticed. **Every test
/// here checks a figure.**
@MainActor
@Suite("Merging accounts")
struct MergeTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    private func shopWithProduct() -> (StockbookStore, Product) {
        let store = makeStore()
        return (store, store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95))
    }

    /// Ahmed, entered twice: once as himself and once as the firm.
    private func shopWithADuplicate() -> StockbookStore {
        let (store, lock) = shopWithProduct()
        store.addCustomer(name: "Ahmed", phone: "0500 111 222", openingBalance: 300)
        store.addCustomer(name: "Ahmed Contracting", place: "Riyadh", openingBalance: 700)
        store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0)
        store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 1, price: 95)], customer: "Ahmed Contracting", paid: 0)
        store.recordPayment(customerKey: "ahmed", amount: 40)
        store.addCreditNote(customerKey: "ahmed", amount: 50)
        return store
    }

    @Test("The preview says what will move before anything moves")
    func preview() throws {
        let store = shopWithADuplicate()

        let preview = try #require(store.previewCustomerMerge(from: "ahmed", into: "ahmed contracting"))

        #expect(preview.from == "Ahmed")
        #expect(preview.into == "Ahmed Contracting")
        #expect(preview.bills == 1)
        #expect(preview.payments == 1)
        #expect(preview.creditNotes == 1)
        #expect(preview.openingBalance == 1000, "300 and 700 added, never one of them chosen")
        // 1000 opening + 285 billed - 40 paid - 50 credited.
        #expect(preview.owed == 1195)
        #expect(!preview.movesNothing)

        // And it is a preview: the book is exactly as it was.
        #expect(store.customers().count == 2)
        // 300 opening + 190 billed - 40 paid - 50 credited.
        #expect(try #require(store.customer(key: "ahmed")).owed == 400)
    }

    @Test("Merging brings the bills, the payments and the credit notes across")
    func merges() throws {
        let store = shopWithADuplicate()
        let expected = try #require(store.previewCustomerMerge(from: "ahmed", into: "ahmed contracting")).owed

        #expect(store.mergeCustomer(from: "ahmed", into: "ahmed contracting"))

        let ahmed = try #require(store.customers().first)
        #expect(store.customers().count == 1)
        #expect(ahmed.name == "Ahmed Contracting")
        #expect(ahmed.billCount == 2)
        // The figure the owner was shown is the figure they end up with. Nothing
        // else in this suite matters as much as these two lines agreeing.
        #expect(ahmed.owed == expected)
        #expect(ahmed.owed == 1195)

        // Nothing is left filed under a name that no longer exists — the credit
        // note especially, which the accidental merge used to strand.
        #expect(store.bills.allSatisfy { Customer.key(for: $0.who) == "ahmed contracting" })
        #expect(store.payments.allSatisfy { $0.customerKey == "ahmed contracting" })
        #expect(store.creditNotes.allSatisfy { $0.customerKey == "ahmed contracting" })
    }

    @Test("The two opening balances are added, never one of them dropped")
    func openingBalancesAdd() throws {
        // The bug that made this feature necessary, on its own so it cannot be
        // lost among the others.
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 300)
        store.addCustomer(name: "Ahmed Contracting", openingBalance: 700)

        store.mergeCustomer(from: "ahmed", into: "ahmed contracting")

        let ahmed = try #require(store.customer(key: "ahmed contracting"))
        #expect(ahmed.openingBalance == 1000)
        #expect(ahmed.owed == 1000)
    }

    @Test("The surviving details win, and fill in from the other only where blank")
    func detailsSurvive() throws {
        let store = shopWithADuplicate()

        store.mergeCustomer(from: "ahmed", into: "ahmed contracting")

        let ahmed = try #require(store.customer(key: "ahmed contracting"))
        #expect(ahmed.place == "Riyadh", "its own")
        #expect(ahmed.phone == "0500 111 222", "it had none, so the other's rather than nothing")
    }

    @Test("A name that has only ever appeared on bills can be merged away")
    func billOnlyNameMergedAway() throws {
        // The common duplicate: a name typed at the counter in a hurry that
        // nobody ever added to the roster.
        let (store, lock) = shopWithProduct()
        store.addCustomer(name: "Ahmed Contracting")
        store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 1, price: 95)], customer: "ahmed cont", paid: 0)

        #expect(store.mergeCustomer(from: "ahmed cont", into: "ahmed contracting"))

        let ahmed = try #require(store.customers().first)
        #expect(store.customers().count == 1)
        #expect(ahmed.name == "Ahmed Contracting")
        #expect(ahmed.owed == 95)
        #expect(store.bills[0].who == "Ahmed Contracting")
    }

    @Test("Merging into a name that has only ever appeared on bills keeps the opening balance")
    func openingBalanceTravels() throws {
        // The other way round, and the one that could quietly lose money: the
        // roster entry is the one going, so its opening balance has to travel
        // rather than be deleted with it.
        let (store, lock) = shopWithProduct()
        store.addCustomer(name: "Ahmed", openingBalance: 300)
        store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 1, price: 95)], customer: "Ahmed Contracting", paid: 0)

        #expect(store.mergeCustomer(from: "ahmed", into: "ahmed contracting"))

        let ahmed = try #require(store.customers().first)
        #expect(store.customers().count == 1)
        #expect(ahmed.name == "Ahmed Contracting")
        #expect(ahmed.openingBalance == 300)
        #expect(ahmed.owed == 395)
    }

    @Test("A customer cannot be merged into themselves or into somebody who is not there")
    func refusals() {
        let store = shopWithADuplicate()

        #expect(store.previewCustomerMerge(from: "ahmed", into: "ahmed") == nil)
        #expect(!store.mergeCustomer(from: "ahmed", into: "ahmed"))
        #expect(store.previewCustomerMerge(from: "ahmed", into: "nobody") == nil)
        #expect(!store.mergeCustomer(from: "ahmed", into: "nobody"))
        #expect(!store.mergeCustomer(from: "nobody", into: "ahmed"))
        #expect(!store.mergeCustomer(from: "", into: "ahmed"))

        #expect(store.customers().count == 2)
    }

    @Test("What the shop is owed altogether does not change")
    func totalOwedIsUnchanged() {
        // The property that says the merge moved money rather than making or
        // losing any: two accounts joined owe what the two owed.
        let store = shopWithADuplicate()
        let before = store.customers().reduce(0) { $0 + $1.owed }

        store.mergeCustomer(from: "ahmed", into: "ahmed contracting")

        #expect(store.customers().reduce(0) { $0 + $1.owed } == before)
    }

    @Test("The merge survives being written down and read back")
    func survivesARoundTrip() throws {
        let store = shopWithADuplicate()
        store.mergeCustomer(from: "ahmed", into: "ahmed contracting")

        let reopened = StockbookStore(repository: InMemoryRepository())
        reopened.replaceEverything(with: store.makeBackupDocument())

        let ahmed = try #require(reopened.customers().first)
        #expect(reopened.customers().count == 1)
        #expect(ahmed.owed == 1195)
        #expect(reopened.creditNotes.count == 1)
        #expect(reopened.creditNotes[0].customerKey == "ahmed contracting")
    }

    // MARK: Suppliers

    @Test("A supplier merge moves the deliveries and the payments")
    func supplierMerge() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 4, cost: 60, price: 95)
        let faisal = try #require(store.addSupplier(name: "Al Faisal", openingBalance: 200))
        let hardware = try #require(store.addSupplier(name: "Al Faisal Hardware", openingBalance: 100))
        store.recordPurchase(product: product, supplierKey: faisal.key, quantity: 10, unitCost: 60, paid: 0)
        store.recordSupplierPayment(supplierKey: faisal.key, amount: 150)

        let preview = try #require(store.previewSupplierMerge(from: faisal.key, into: hardware.key))
        #expect(preview.deliveries == 1)
        #expect(preview.payments == 1)
        #expect(preview.bills == 0, "a supplier has none, and the line is not drawn")
        #expect(preview.openingBalance == 300)
        // 300 opening + 600 delivered - 150 paid.
        #expect(preview.owed == 750)

        #expect(store.mergeSupplier(from: faisal.key, into: hardware.key))

        let supplier = try #require(store.suppliers().first)
        #expect(store.suppliers().count == 1)
        #expect(supplier.name == "Al Faisal Hardware")
        #expect(supplier.owed == 750)
        #expect(store.purchases(forSupplier: supplier.key).count == 1)
        #expect(store.supplierPayments(for: supplier.key).count == 1)
    }

    @Test("The shelf is untouched by a supplier merge")
    func shelfUntouched() throws {
        // A merge is about who a delivery came from, never about what arrived.
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 4, cost: 60, price: 95)
        let faisal = try #require(store.addSupplier(name: "Al Faisal"))
        _ = try #require(store.addSupplier(name: "Al Faisal Hardware"))
        store.recordPurchase(product: product, supplierKey: faisal.key, quantity: 10, unitCost: 62.5)
        let stock = try #require(store.product(uid: product.uid)).stock

        store.mergeSupplier(from: faisal.key, into: "al faisal hardware")

        let after = try #require(store.product(uid: product.uid))
        #expect(after.stock == stock)
        #expect(after.cost == 62.5)
    }
}
