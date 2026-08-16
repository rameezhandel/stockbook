import XCTest
import Foundation
@testable import Stockbook

/// One suite, run against every repository.
///
/// The point of a storage protocol is that swapping the implementation changes
/// nothing above it. That is only true if every implementation is held to the
/// same contract — so these tests are written once against `StockbookRepository`
/// and applied to each. Adding a Core Data or SQLite backing later means adding
/// one line here, and knowing immediately whether it behaves.
/// Repository contract
final class RepositoryTests: XCTestCase {

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

    /// A fresh store is empty, not an error
    func testStartsEmpty() throws {
        try eachRepository { repository, name in
            let state = try repository.loadAll()
            XCTAssertTrue(state.products.isEmpty, "\(name)")
            XCTAssertTrue(state.bills.isEmpty, "\(name)")
            XCTAssertEqual(state.settings.setupCompleted, false, "\(name)")
        }
    }

    /// Upsert inserts, then updates in place
    func testUpsertIsIdempotentOnIdentity() throws {
        try eachRepository { repository, name in
            var product = sampleProduct()
            try repository.upsert(product)
            XCTAssertEqual(try repository.loadAll().products.count, 1, "\(name)")

            product.stock = 99
            try repository.upsert(product)

            let products = try repository.loadAll().products
            XCTAssertEqual(products.count, 1, "\(name): upsert must not duplicate on uid")
            XCTAssertEqual(products.first?.stock, 99, "\(name)")
        }
    }

    /// Delete removes only the named product
    func testDeleteIsTargeted() throws {
        try eachRepository { repository, name in
            let keep = sampleProduct(name: "Deadbolt")
            let drop = sampleProduct(name: "Padlock")
            try repository.upsert(keep)
            try repository.upsert(drop)

            try repository.delete(productUID: drop.uid)

            let products = try repository.loadAll().products
            XCTAssertEqual(products.map(\.name), ["Deadbolt"], "\(name)")
        }
    }

    /// Bills append and update by number
    func testBillLifecycle() throws {
        try eachRepository { repository, name in
            let bill = Bill(
                number: 1,
                lines: [BillLine(productUID: UUID(), name: "Padlock", qty: 2, price: 45)],
                total: 90,
                paid: nil,
                who: "Sami"
            )
            try repository.append(bill)
            XCTAssertEqual(try repository.loadAll().bills.count, 1, "\(name)")

            var voided = bill
            voided.voided = true
            try repository.update(voided)

            let bills = try repository.loadAll().bills
            XCTAssertEqual(bills.count, 1, "\(name): update must not append")
            XCTAssertEqual(bills.first?.voided, true, "\(name)")
        }
    }

    /// Updating an unknown bill does nothing
    func testUpdateUnknownBillIsInert() throws {
        try eachRepository { repository, name in
            let stranger = Bill(number: 42, lines: [], total: 0, paid: nil, who: "Nobody")
            try repository.update(stranger)
            XCTAssertTrue(try repository.loadAll().bills.isEmpty, "\(name)")
        }
    }

    /// Settings round-trip
    func testSettingsPersist() throws {
        try eachRepository { repository, name in
            var settings = Settings()
            settings.ownerName = "Khalid Al-Amri"
            settings.nextBillNumber = 7
            settings.setupCompleted = true
            try repository.save(settings)

            let loaded = try repository.loadAll().settings
            XCTAssertEqual(loaded.ownerName, "Khalid Al-Amri", "\(name)")
            XCTAssertEqual(loaded.nextBillNumber, 7, "\(name)")
            XCTAssertTrue(loaded.setupCompleted, "\(name)")
        }
    }

    /// replaceAll swaps everything, leaving nothing behind
    func testReplaceAllIsASwap() throws {
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
            XCTAssertEqual(state.products.map(\.name), ["New"], "\(name)")
            XCTAssertTrue(state.bills.isEmpty, "\(name): the old bills must not survive")
            XCTAssertEqual(state.settings.ownerName, "New Owner", "\(name)")
        }
    }

    // MARK: File-backed specifics

    /// The JSON file survives being reopened
    func testJsonSurvivesRelaunch() throws {
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

        XCTAssertEqual(state.products.map(\.name), ["Cisa lock"])
        XCTAssertEqual(state.settings.ownerName, "Khalid")
    }

    /// A corrupt file is reported rather than silently treated as empty
    func testCorruptFileIsReported() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("stockbook.json")
        try Data("this is not json".utf8).write(to: url)

        // Silently starting empty would look exactly like a working app that had
        // eaten the owner's shop.
        XCTAssertThrowsError(try JSONFileRepository(url: url))
    }
}
