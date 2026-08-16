import XCTest
import Foundation
@testable import Stockbook

/// The backup file is the only way data leaves the phone, and importing is a
/// destructive whole-database replace — so both directions are pinned down here.
/// Backup file
final class BackupTests: XCTestCase {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// A shop survives a round trip through the file
    func testRoundTrip() throws {
        let store = makeStore()
        store.setOwnerName("Khalid Al-Amri")
        let product = store.addProduct(name: "Cisa lock", stock: 12, cost: 60, price: 95)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 90)], customer: "Ahmed Contracting", paid: 100)

        let data = try BackupService.encode(store.makeBackupDocument())
        let restored = try BackupService.decode(data)

        XCTAssertEqual(restored.version, BackupDocument.currentVersion)
        XCTAssertEqual(restored.ownerName, "Khalid Al-Amri")
        XCTAssertEqual(restored.products.count, 1)
        XCTAssertEqual(restored.products.first?.uid, product.uid)
        XCTAssertEqual(restored.bills.count, 1)
        XCTAssertEqual(restored.bills.first?.paid, 100)
        XCTAssertEqual(restored.bills.first?.lines.first?.price, 90, "the charged price, not the list price")
    }

    /// Importing replaces everything rather than merging
    func testImportReplaces() throws {
        let source = makeStore()
        source.setOwnerName("Khalid Al-Amri")
        let sourceProduct = source.addProduct(name: "Cisa lock", stock: 12, cost: 60, price: 95)
        source.saveBill(lines: [.init(productUID: sourceProduct.uid, qty: 2, price: 95)], customer: "Ahmed", paid: nil)
        let document = source.makeBackupDocument()

        let destination = makeStore()
        destination.setOwnerName("Someone Else")
        destination.addProduct(name: "Padlock", stock: 3, cost: 10, price: 20)

        destination.replaceEverything(with: document)

        XCTAssertEqual(destination.products.map(\.name), ["Cisa lock"])
        XCTAssertEqual(destination.bills.count, 1)
        XCTAssertEqual(destination.settings.ownerName, "Khalid Al-Amri")
        XCTAssertTrue(destination.settings.setupCompleted)
    }

    /// Imported bills keep numbering going instead of colliding
    func testNumberingContinues() throws {
        let source = makeStore()
        let product = source.addProduct(name: "Hinge", stock: 50, cost: 3, price: 6)
        source.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "A", paid: nil)
        source.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "B", paid: nil)

        let destination = makeStore()
        destination.replaceEverything(with: source.makeBackupDocument())

        XCTAssertEqual(destination.settings.nextBillNumber, 3)
    }

    /// An imported file is not this phone's backup
    func testImportDoesNotCountAsBackup() throws {
        let source = makeStore()
        source.markExported()
        let document = source.makeBackupDocument()

        let destination = makeStore()
        destination.replaceEverything(with: document)

        XCTAssertNil(destination.settings.lastExportAt,
                     "the nudge stays on until this phone writes its own file")
    }

    /// Bill lines still point at their products after an import
    func testLinesResolveAfterImport() throws {
        let source = makeStore()
        let product = source.addProduct(name: "Deadbolt", stock: 10, cost: 40, price: 70)
        let bill = try XCTUnwrap(
            source.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )
        XCTAssertEqual(source.product(uid: product.uid)?.stock, 7)

        let destination = makeStore()
        destination.replaceEverything(with: source.makeBackupDocument())

        // Voiding on the new phone has to find the product by uid, which is the
        // whole reason products carry one.
        let importedBill = try XCTUnwrap(destination.bills.first { $0.number == bill.number })
        destination.void(importedBill)

        XCTAssertEqual(destination.products.first?.stock, 10)
    }

    // MARK: Validation

    /// Junk is rejected as not a Stockbook file
    func testRejectsJunk() {
        let data = Data("this is not json".utf8)
        XCTAssertThrowsError(try BackupService.decode(data)) { error in
            XCTAssertEqual(error as? BackupError, BackupError.notStockbookData)
        }
    }

    /// Valid JSON that is not a backup is rejected
    func testRejectsForeignJSON() {
        let data = Data(#"{"hello":"world"}"#.utf8)
        XCTAssertThrowsError(try BackupService.decode(data)) { error in
            XCTAssertEqual(error as? BackupError, BackupError.notStockbookData)
        }
    }

    /// A file from a newer format is rejected, not guessed at
    func testRejectsNewerVersion() {
        let data = Data(#"{"version":99,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K","currencySymbol":"SAR ","products":[],"bills":[]}"#.utf8)
        XCTAssertThrowsError(try BackupService.decode(data)) { error in
            XCTAssertEqual(error as? BackupError, BackupError.newerVersion(found: 99))
        }
    }

    /// The filename carries the export date
    func testFilename() {
        var components = DateComponents()
        components.year = 2026
        components.month = 8
        components.day = 11
        components.hour = 12
        let date = Calendar.current.date(from: components)!

        let document = BackupDocument(
            exportedAt: date,
            ownerName: "Khalid",
            currencySymbol: "SAR ",
            products: [],
            bills: []
        )

        XCTAssertEqual(document.suggestedFilename, "stockbook-2026-08-11.json")
    }

    /// The summary line reads the way Settings shows it
    func testSummary() {
        var components = DateComponents()
        components.year = 2026
        components.month = 7
        components.day = 28
        components.hour = 12
        let date = Calendar.current.date(from: components)!

        let document = BackupDocument(
            exportedAt: date,
            ownerName: "Khalid Al-Amri",
            currencySymbol: "SAR ",
            products: (0..<8).map {
                .init(uid: UUID(), name: "P\($0)", stock: 1, cost: 1, price: 2)
            },
            bills: []
        )

        XCTAssertEqual(document.summaryLine, "Khalid Al-Amri · 8 products · 0 bills · saved 28 July 2026")
    }
}
