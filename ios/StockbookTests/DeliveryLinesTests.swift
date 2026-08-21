import Testing
import Foundation
@testable import Stockbook

/// A delivery note with more than one product on it.
///
/// A port of `DeliveryLinesTests.kt`, assertion for assertion. The rule
/// underneath all of it: **one number, one piece of paper, as many lines as the
/// paper has.** The screen refuses a repeated invoice number across the whole
/// book, which is why a five-line delivery could not be entered as five records
/// — and until `Purchase` had lines, it could not be entered at all.
///
/// The shelf is the part that has to be exactly right. Recording a delivery puts
/// every line on, correcting one moves the shelf by the difference, and removing
/// one takes every line back off. A line that is wrong here is stock the shop
/// does not have.
@MainActor
@Suite("Deliveries with more than one line")
struct DeliveryLinesTests {

    private let strings = Strings(language: .english)
    private let day = Date(timeIntervalSince1970: 1_786_000_000)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("Every line lands on the shelf")
    func everyLineLands() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 10, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                lines: [
                    DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                    DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
                ],
                supplierKey: "al-riyadh hardware",
                paid: 0,
                invoiceNo: "8842"
            )
        )

        #expect(store.product(uid: locks.uid)?.stock == 12)
        #expect(store.product(uid: keys.uid)?.stock == 110)
        #expect(purchase.total == 870, "10 × 62 + 100 × 2.5")
        #expect(purchase.items.count == 2)
        #expect(purchase.isItemised)
    }

    @Test("Each line sets its own product's cost")
    func costPerLine() throws {
        // Latest paid, not a weighted average — the same rule a single-product
        // delivery always followed, now applied per line.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 10, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        store.recordPurchase(
            lines: [
                DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
            ],
            supplierKey: "al-riyadh hardware",
            invoiceNo: "8842"
        )

        #expect(store.product(uid: locks.uid)?.cost == 62)
        #expect(store.product(uid: keys.uid)?.cost == 2.5)
    }

    @Test("A line with no cost keeps what the product already cost")
    func zeroCostKeepsTheOldOne() throws {
        // The sheet leaves the box empty where the price has not moved since last
        // time. Reading that as free would rewrite the product's cost to nothing
        // and make every margin in the shop look enormous.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                lines: [DraftPurchaseLine(productUID: locks.uid, qty: 5, unitCost: 0)],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "8842"
            )
        )

        #expect(store.product(uid: locks.uid)?.cost == 60)
        #expect(purchase.total == 300)
    }

    @Test("The same product twice on one note counts twice")
    func sameProductTwice() throws {
        // Two boxes at two prices is an ordinary thing on a supplier's paper. The
        // shelf is re-read between lines, so the second does not overwrite a
        // count captured before the first.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        store.addSupplier(name: "Al-Riyadh Hardware")

        store.recordPurchase(
            lines: [
                DraftPurchaseLine(productUID: locks.uid, qty: 6, unitCost: 60),
                DraftPurchaseLine(productUID: locks.uid, qty: 4, unitCost: 65)
            ],
            supplierKey: "al-riyadh hardware",
            invoiceNo: "8842"
        )

        #expect(store.product(uid: locks.uid)?.stock == 10, "six then four, not four")
        #expect(store.product(uid: locks.uid)?.cost == 65, "the last line sets it")
    }

    @Test("A correction moves the shelf by the difference")
    func correctionMovesByTheDifference() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 10, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                lines: [
                    DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                    DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
                ],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "8842"
            )
        )

        // The paper said 8 locks, not 10, and the keys were never on it.
        store.updatePurchase(
            id: purchase.id,
            lines: [DraftPurchaseLine(productUID: locks.uid, qty: 8, unitCost: 62)],
            supplierKey: "al-riyadh hardware",
            createdAt: day,
            invoiceNo: "8842"
        )

        #expect(store.product(uid: locks.uid)?.stock == 10, "2 + 8, not 2 + 10 + 8")
        #expect(store.product(uid: keys.uid)?.stock == 10, "the dropped line gave its stock back")
        #expect(store.purchases.first?.total == 496)
    }

    @Test("Removing a delivery takes every line back off")
    func removingUnwindsEveryLine() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 10, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                lines: [
                    DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                    DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
                ],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "8842"
            )
        )
        store.deletePurchase(id: purchase.id)

        #expect(store.product(uid: locks.uid)?.stock == 2)
        #expect(store.product(uid: keys.uid)?.stock == 10)
        #expect(store.purchases.isEmpty)
    }

    @Test("What a supplier is owed is the whole note, once")
    func oweTheWholeNote() throws {
        // The reason this feature exists. Five lines under one number used to be
        // five records, each carrying part of one payment — an apportionment the
        // owner invented and the supplier would not recognise.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 0, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        store.recordPurchase(
            lines: [
                DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
            ],
            supplierKey: "al-riyadh hardware",
            paid: 500,
            invoiceNo: "8842"
        )

        #expect(store.purchases.first?.balance == 370, "870 owed, 500 handed over")
        #expect(store.payable().total == 370)
    }

    @Test("One number covers one delivery, however many lines")
    func oneNumberOneDelivery() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 0, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                lines: [
                    DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                    DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
                ],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "8842"
            )
        )

        // Still one record under 8842, so the screen's clash check still finds
        // exactly one thing and the owner is not told the paper is a duplicate
        // of itself.
        #expect(store.purchases.count == 1)
        #expect(store.purchaseWithInvoiceNo("8842")?.id == purchase.id)
        #expect(store.purchaseWithInvoiceNo("8842", exceptId: purchase.id) == nil)
    }

    @Test("A bill with no stock on it is still a bill with no stock on it")
    func figureOnlyIsUnchanged() throws {
        let store = makeStore()
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordSupplierBill(
                supplierKey: "al-riyadh hardware",
                amount: 800,
                paid: 0,
                invoiceNo: "INV-88"
            )
        )

        #expect(purchase.items.isEmpty)
        #expect(!purchase.isItemised)
        #expect(purchase.total == 800)
    }

    @Test("The summary names what arrived, the way a bill's does")
    func summaryReadsLikeABill() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 0, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let one = try #require(
            store.recordPurchase(
                lines: [DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62)],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "1"
            )
        )
        let many = try #require(
            store.recordPurchase(
                lines: [
                    DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                    DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
                ],
                supplierKey: "al-riyadh hardware",
                invoiceNo: "2"
            )
        )
        let figure = try #require(
            store.recordSupplierBill(supplierKey: "al-riyadh hardware", amount: 800, invoiceNo: "3")
        )

        #expect(one.summary == "Cisa lock")
        #expect(many.summary == "Cisa lock, Key blank")
        #expect(figure.summary == "", "a bill that named nothing says nothing")
        // What a row shows instead: the supplier's number where there is one, and
        // the plain word where the shop never wrote one down.
        #expect(figure.reference(strings) == "3")
        let unnumbered = try #require(
            store.recordSupplierBill(supplierKey: "al-riyadh hardware", amount: 40)
        )
        #expect(unnumbered.reference(strings) == strings.purchaseLabel)
    }

    // MARK: Deliveries recorded when a delivery held one product

    @Test("An older delivery still says what arrived")
    func olderDeliveryKeepsItsItem() {
        let uid = UUID()
        let old = Purchase(
            supplierKey: "al-riyadh hardware",
            total: 600,
            productUID: uid,
            name: "Cisa lock",
            qty: 10,
            unitCost: 60
        )

        #expect(old.items == [PurchaseLine(productUID: uid, name: "Cisa lock", qty: 10, unitCost: 60)])
        #expect(old.isItemised)
        #expect(old.summary == "Cisa lock")
    }

    @Test("Correcting an older delivery rewrites it into the new shape")
    func correctingAnOlderDeliveryRewritesIt() throws {
        // Otherwise the four old fields would sit under the new lines saying
        // something different, waiting for something to believe them.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        store.addSupplier(name: "Al-Riyadh Hardware")
        let purchase = try #require(
            store.recordPurchase(
                product: locks,
                supplierKey: "al-riyadh hardware",
                quantity: 10,
                unitCost: 60,
                invoiceNo: "8842"
            )
        )

        store.updatePurchase(
            id: purchase.id,
            lines: [DraftPurchaseLine(productUID: locks.uid, qty: 8, unitCost: 60)],
            supplierKey: "al-riyadh hardware",
            createdAt: day,
            invoiceNo: "8842"
        )

        let corrected = try #require(store.purchases.first)
        #expect(corrected.name == nil)
        #expect(corrected.qty == 0)
        #expect(corrected.lines == [PurchaseLine(productUID: locks.uid, name: "Cisa lock", qty: 8, unitCost: 60)])
        #expect(store.product(uid: locks.uid)?.stock == 10)
    }

    @Test("The one-product way in is the one-line case")
    func oneProductIsOneLine() throws {
        // Forty call sites use it, and it is the shape of most deliveries. It
        // must produce exactly what handing over a single line produces.
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        store.addSupplier(name: "Al-Riyadh Hardware")

        let purchase = try #require(
            store.recordPurchase(
                product: locks,
                supplierKey: "al-riyadh hardware",
                quantity: 5,
                unitCost: 62,
                invoiceNo: "8842"
            )
        )

        #expect(purchase.lines == [PurchaseLine(productUID: locks.uid, name: "Cisa lock", qty: 5, unitCost: 62)])
        #expect(purchase.total == 310)
        #expect(store.product(uid: locks.uid)?.stock == 7)
    }

    // MARK: Getting to a new phone

    @Test("The lines survive export and import")
    func linesSurviveTheCrossing() throws {
        let store = makeStore()
        let locks = store.addProduct(name: "Cisa lock", stock: 2, cost: 60, price: 95)
        let keys = store.addProduct(name: "Key blank", stock: 10, cost: 3, price: 6)
        store.addSupplier(name: "Al-Riyadh Hardware")
        store.recordPurchase(
            lines: [
                DraftPurchaseLine(productUID: locks.uid, qty: 10, unitCost: 62),
                DraftPurchaseLine(productUID: keys.uid, qty: 100, unitCost: 2.5)
            ],
            supplierKey: "al-riyadh hardware",
            paid: 500,
            invoiceNo: "8842"
        )

        let fresh = makeStore()
        fresh.replaceEverything(with: try BackupService.decode(try BackupService.encode(store.makeBackupDocument())))

        let purchase = try #require(fresh.purchases.first)
        #expect(purchase.items.count == 2)
        #expect(purchase.items.first?.name == "Cisa lock")
        #expect(purchase.items.last?.qty == 100)
        #expect(purchase.total == 870)
        #expect(purchase.balance == 370)
    }

    @Test("An older file's delivery arrives as a line")
    func olderFileArrivesAsALine() throws {
        // The shape every build before this one wrote. It has to keep its
        // itemisation, or a restore would quietly turn every delivery in the book
        // into a bare figure.
        let uid = UUID()
        let text = """
        {"version":3,"exportedAt":"2026-08-13T12:00:00Z","ownerName":"Ahmed","currencyCode":"SAR",
         "products":[],"customers":[],"suppliers":[],"bills":[],"payments":[],"supplierPayments":[],
         "creditNotes":[],"expenses":[],
         "purchases":[{"id":"\(UUID().uuidString)","supplierKey":"al-riyadh hardware",
          "productUID":"\(uid.uuidString)","name":"Cisa lock","qty":10,"unitCost":60.0,"total":600.0,
          "createdAt":"2026-08-13T12:00:00Z"}]}
        """

        let store = makeStore()
        store.replaceEverything(with: try BackupService.decode(Data(text.utf8)))

        let purchase = try #require(store.purchases.first)
        #expect(purchase.items == [PurchaseLine(productUID: uid, name: "Cisa lock", qty: 10, unitCost: 60)])
        #expect(purchase.total == 600)
    }
}
