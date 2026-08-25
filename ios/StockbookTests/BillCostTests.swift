import Testing
import Foundation
@testable import Stockbook

/// What the goods cost, captured on the bill that sold them.
///
/// The twin of `BillCostTests.kt`, test for test. **The whole point is the
/// second one.** A bill line records the price charged and, until this existed,
/// nothing about what the shop paid — so working out what a sale earned meant
/// reading `Product.cost`, which is the buying price *now*. Raise a supplier's
/// price next month and last March's figure silently changes: the bill has not
/// moved, the number under it has. `Bill.total` is stored rather than recomputed
/// for exactly this reason on the selling side, and `PurchaseLine.unitCost` on
/// the buying side; this is the third corner of the same rule.
///
/// There is no profit screen and these tests do not ask for one. They pin that
/// the figure needed to build one honestly is written down at the only moment it
/// is knowable, and survives a trip through the backup file.
@MainActor
@Suite("Bill line cost")
struct BillCostTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("A sale records what the goods cost the shop")
    func costIsCaptured() throws {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        let line = try #require(store.bills.first?.lines.first)

        #expect(line.cost == 20)
        #expect(line.lineTotal == 90)
        #expect(line.lineCost == 60)
    }

    @Test("A later price rise does not rewrite what an old sale cost")
    func historyDoesNotMove() throws {
        // The regression this whole field exists to prevent. March: bought at
        // 20, sold at 30. May: the supplier puts the price up. The March bill
        // must still say 20, because that is what the shop actually paid for the
        // padlocks it sold in March.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        // A delivery at the new price, which is how `Product.cost` moves.
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        // Hoisted out of the call. `#require` inside a plain argument is type
        // checked against the parameter — a non-optional `Product` — so the
        // macro sees an expression that "never equals nil" and refuses to
        // compile. `#expect(try #require(x))` is fine; a bare call is not.
        let stocked = try #require(store.product(uid: padlock.uid))
        store.recordPurchase(
            product: stocked,
            supplierKey: supplier.key,
            quantity: 10,
            unitCost: 25
        )

        #expect(try #require(store.product(uid: padlock.uid)).cost == 25)
        // The shelf moved. The bill did not.
        #expect(store.bills.first?.lines.first?.cost == 20)
        #expect(store.bills.first?.lines.first?.lineCost == 60)
    }

    @Test("Correcting a bill re-reads the shelf, because the sale is being restated")
    func editingRestates() throws {
        // Editing a bill is the owner saying "this is what the sale actually
        // was", and the line is written afresh from the shelf as it stands. That
        // is the same rule the name and the stock movement already follow.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        let bill = try #require(
            store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)
        )

        let repriced = try #require(store.product(uid: padlock.uid))
        store.update(repriced, name: "Padlock 40mm", cost: 22, price: 30)
        store.updateBill(
            number: bill.number,
            lines: [.init(productUID: padlock.uid, qty: 4, price: 30)],
            customer: "Ahmed",
            paid: nil,
            createdAt: bill.createdAt
        )

        #expect(store.bill(number: bill.number)?.lines.first?.cost == 22)
    }

    @Test("A line whose cost was never recorded says so, rather than saying zero")
    func nilIsNotZero() {
        // What a bill written before this field existed looks like. Nil and 0
        // are different answers: one is "nobody wrote it down", the other is
        // "these goods were free", and a page netting cost off takings has to
        // keep them apart or an old bill reads as pure profit.
        let line = BillLine(productUID: nil, name: "Padlock 40mm", qty: 3, price: 30)

        #expect(line.cost == nil)
        #expect(line.lineCost == nil)

        let free = BillLine(productUID: nil, name: "Sample", qty: 2, price: 5, cost: 0)
        #expect(free.lineCost == 0)
    }

    @Test("A bill entered as a figure has no lines and so no cost to read")
    func figureOnlyBill() {
        // Ordinary, not exceptional — it is how a shop enters the paper bill it
        // already wrote. Whatever eventually computes earnings has to say so
        // rather than treat the whole total as profit.
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100)

        #expect(store.bills.first?.lines.isEmpty == true)
    }

    @Test("The cost survives a trip through the backup file")
    func survivesTheFile() throws {
        // The fourth corner: a field that is written but not carried is a field
        // the owner loses on the way to a new phone. `paymentNo` once matched
        // three call sites out of four and would have dropped every receipt
        // number.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        let data = try BackupService.encode(store.makeBackupDocument())
        let restored = makeStore()
        restored.replaceEverything(with: try BackupService.decode(data))

        #expect(restored.bills.first?.lines.first?.cost == 20)
    }

    @Test("An older backup restores with the cost absent rather than refusing to open")
    func olderFileStillOpens() throws {
        // Every file already written has no `cost` key. Reading one must leave
        // the line unable to answer, not fail — which is why the field is
        // optional rather than defaulted, and why the document version did not
        // move. A defaulted non-optional would have thrown here and made every
        // existing backup unreadable; that has happened once already, adding
        // `creditNotes`.
        //
        // Written by the real exporter with the field taken back out, rather
        // than typed out here: a hand-built fixture only proves the decoder can
        // read what this test happens to know about the format.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        var older = store.makeBackupDocument()
        older.bills = older.bills.map { bill in
            var bill = bill
            bill.lines = bill.lines.map { line in
                var line = line
                line.cost = nil
                return line
            }
            return bill
        }

        let restored = makeStore()
        restored.replaceEverything(with: try BackupService.decode(try BackupService.encode(older)))

        let line = try #require(restored.bills.first?.lines.first)
        #expect(line.name == "Padlock 40mm")
        #expect(line.cost == nil)
    }
}
