import Testing
import Foundation
@testable import Stockbook

/// One suite, run against every repository.
///
/// The point of a storage protocol is that swapping the implementation changes
/// nothing above it. That is only true if every implementation is held to the
/// same contract — so these tests are written once against `StockbookRepository`
/// and applied to each. Adding a Core Data or SQLite backing later means adding
/// one line here, and knowing immediately whether it behaves.
@Suite("Repository contract")
struct RepositoryTests {

    /// Every implementation under test. `JSONFileRepository` gets a fresh
    /// temporary file per case so nothing leaks between them.
    static func implementations() throws -> [(name: String, make: () throws -> StockbookRepository)] {
        [
            ("in-memory", { InMemoryRepository() }),
            ("json-file", {
                let directory = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString, isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                return try JSONFileRepository(url: directory.appendingPathComponent("stockbook.json"))
            })
        ]
    }

    private func eachRepository(_ body: (StockbookRepository, String) throws -> Void) throws {
        for implementation in try Self.implementations() {
            try body(try implementation.make(), implementation.name)
        }
    }

    private func sampleProduct(name: String = "Padlock") -> Product {
        Product(name: name, stock: 10, cost: 20, price: 45)
    }

    @Test("A fresh store is empty, not an error")
    func startsEmpty() throws {
        try eachRepository { repository, name in
            let state = try repository.loadAll()
            #expect(state.products.isEmpty, "\(name)")
            #expect(state.bills.isEmpty, "\(name)")
            #expect(state.settings.setupCompleted == false, "\(name)")
        }
    }

    @Test("Upsert inserts, then updates in place")
    func upsertIsIdempotentOnIdentity() throws {
        try eachRepository { repository, name in
            var product = sampleProduct()
            try repository.upsert(product)
            #expect(try repository.loadAll().products.count == 1, "\(name)")

            product.stock = 99
            try repository.upsert(product)

            let products = try repository.loadAll().products
            #expect(products.count == 1, "\(name): upsert must not duplicate on uid")
            #expect(products.first?.stock == 99, "\(name)")
        }
    }

    @Test("Delete removes only the named product")
    func deleteIsTargeted() throws {
        try eachRepository { repository, name in
            let keep = sampleProduct(name: "Deadbolt")
            let drop = sampleProduct(name: "Padlock")
            try repository.upsert(keep)
            try repository.upsert(drop)

            try repository.delete(productUID: drop.uid)

            let products = try repository.loadAll().products
            #expect(products.map(\.name) == ["Deadbolt"], "\(name)")
        }
    }

    @Test("A bill can be appended, then updated in place, then removed")
    func billLifecycle() throws {
        try eachRepository { repository, name in
            let bill = Bill(
                number: 1,
                lines: [BillLine(productUID: UUID(), name: "Padlock", qty: 2, price: 45)],
                total: 90,
                paid: nil,
                who: "Ahmed"
            )
            try repository.append(bill)
            #expect(try repository.loadAll().bills.count == 1, "\(name)")

            var corrected = bill
            corrected.who = "Ahmed Contracting"
            try repository.update(corrected)

            let bills = try repository.loadAll().bills
            #expect(bills.count == 1, "\(name): update must not append")
            #expect(bills.first?.who == "Ahmed Contracting", "\(name)")

            try repository.deleteBill(number: bill.number)
            #expect(try repository.loadAll().bills.isEmpty, "\(name)")
        }
    }

    @Test("Updating an unknown bill does nothing")
    func updateUnknownBillIsInert() throws {
        try eachRepository { repository, name in
            let stranger = Bill(number: 42, lines: [], total: 0, paid: nil, who: "Nobody")
            try repository.update(stranger)
            #expect(try repository.loadAll().bills.isEmpty, "\(name)")
        }
    }

    @Test("Settings round-trip")
    func settingsPersist() throws {
        try eachRepository { repository, name in
            var settings = Settings()
            settings.ownerName = "Khalid Al-Amri"
            settings.nextBillNumber = 7
            settings.setupCompleted = true
            try repository.save(settings)

            let loaded = try repository.loadAll().settings
            #expect(loaded.ownerName == "Khalid Al-Amri", "\(name)")
            #expect(loaded.nextBillNumber == 7, "\(name)")
            #expect(loaded.setupCompleted, "\(name)")
        }
    }

    @Test("replaceAll swaps everything, leaving nothing behind")
    func replaceAllIsASwap() throws {
        try eachRepository { repository, name in
            try repository.upsert(sampleProduct(name: "Old"))
            try repository.append(Bill(number: 1, lines: [], total: 0, paid: nil, who: "Old"))

            var settings = Settings()
            settings.ownerName = "New Owner"
            try repository.replaceAll(with: ShopState(
                products: [sampleProduct(name: "New")],
                bills: [],
                settings: settings
            ))

            let state = try repository.loadAll()
            #expect(state.products.map(\.name) == ["New"], "\(name)")
            #expect(state.bills.isEmpty, "\(name): the old bills must not survive")
            #expect(state.settings.ownerName == "New Owner", "\(name)")
        }
    }

    @Test("A customer is stored once per key, however many times it is written")
    func customerUpsert() throws {
        try eachRepository { repository, name in
            try repository.upsert(CustomerRecord(name: "Ahmed", phone: "0500 111 222"))
            // Same key, better phone number: a correction, not a second person.
            try repository.upsert(CustomerRecord(name: "  ahmed ", phone: "0500 999 888"))

            let stored = try repository.loadAll().customers
            #expect(stored.count == 1, "\(name)")
            #expect(stored.first?.phone == "0500 999 888", "\(name)")

            try repository.delete(customerKey: "ahmed")
            let afterDelete = try repository.loadAll().customers
            #expect(afterDelete.isEmpty, "\(name)")
        }
    }

    @Test("Payments append and delete by id")
    func paymentsAppendAndDelete() throws {
        try eachRepository { repository, name in
            let first = Payment(customerKey: "ahmed", amount: 100)
            let second = Payment(customerKey: "ahmed", amount: 50)
            try repository.append(first)
            try repository.append(second)

            let both = try repository.loadAll().payments
            #expect(both.count == 2, "\(name)")

            try repository.delete(paymentID: first.id)
            let left = try repository.loadAll().payments
            #expect(left.map(\.amount) == [50], "\(name)")
        }
    }

    @Test("replaceAll takes the roster and the payments with it")
    func replaceAllCoversTheRoster() throws {
        try eachRepository { repository, name in
            try repository.upsert(CustomerRecord(name: "Old Customer"))
            try repository.append(Payment(customerKey: "old customer", amount: 10))

            try repository.replaceAll(with: ShopState(
                customers: [CustomerRecord(name: "New Customer")],
                payments: []
            ))

            let state = try repository.loadAll()
            #expect(state.customers.map(\.name) == ["New Customer"], "\(name)")
            #expect(state.payments.isEmpty, "\(name): the old payments must not survive")
        }
    }

    // MARK: File-backed specifics

    @Test("The JSON file survives being reopened")
    func jsonSurvivesRelaunch() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("stockbook.json")

        let first = try JSONFileRepository(url: url)
        try first.upsert(sampleProduct(name: "Cisa lock"))
        var settings = Settings()
        settings.ownerName = "Khalid"
        try first.save(settings)

        // A second instance reads from disk, which is what a relaunch does.
        let second = try JSONFileRepository(url: url)
        let state = try second.loadAll()

        #expect(state.products.map(\.name) == ["Cisa lock"])
        #expect(state.settings.ownerName == "Khalid")
    }

    @Test("A corrupt file is reported rather than silently treated as empty")
    func corruptFileIsReported() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("stockbook.json")
        try Data("this is not json".utf8).write(to: url)

        // Silently starting empty would look exactly like a working app that had
        // eaten the owner's shop.
        #expect(throws: RepositoryError.self) {
            _ = try JSONFileRepository(url: url)
        }
    }
}
