import Testing
import Foundation
@testable import Stockbook

/// The backup file is the only way data leaves the phone, and importing is a
/// destructive whole-database replace — so both directions are pinned down here.
@MainActor
@Suite("Backup file")
struct BackupTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("A shop survives a round trip through the file")
    func roundTrip() throws {
        let store = makeStore()
        store.setOwnerName("Khalid Al-Amri")
        let product = store.addProduct(name: "Cisa lock", stock: 12, cost: 60, price: 95)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 90)], customer: "Ahmed Contracting", paid: 100)

        let data = try BackupService.encode(store.makeBackupDocument())
        let restored = try BackupService.decode(data)

        #expect(restored.version == BackupDocument.currentVersion)
        #expect(restored.ownerName == "Khalid Al-Amri")
        #expect(restored.products.count == 1)
        #expect(restored.products.first?.uid == product.uid)
        #expect(restored.bills.count == 1)
        #expect(restored.bills.first?.paid == 100)
        #expect(restored.bills.first?.lines.first?.price == 90, "the charged price, not the list price")
    }

    @Test("Importing replaces everything rather than merging")
    func importReplaces() throws {
        let source = makeStore()
        source.setOwnerName("Khalid Al-Amri")
        let sourceProduct = source.addProduct(name: "Cisa lock", stock: 12, cost: 60, price: 95)
        source.saveBill(lines: [.init(productUID: sourceProduct.uid, qty: 2, price: 95)], customer: "Ahmed", paid: nil)
        let document = source.makeBackupDocument()

        let destination = makeStore()
        destination.setOwnerName("Someone Else")
        destination.addProduct(name: "Padlock", stock: 3, cost: 10, price: 20)

        destination.replaceEverything(with: document)

        #expect(destination.products.map(\.name) == ["Cisa lock"])
        #expect(destination.bills.count == 1)
        #expect(destination.settings.ownerName == "Khalid Al-Amri")
        #expect(destination.settings.setupCompleted)
    }

    @Test("Imported bills keep numbering going instead of colliding")
    func numberingContinues() throws {
        let source = makeStore()
        let product = source.addProduct(name: "Hinge", stock: 50, cost: 3, price: 6)
        source.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "A", paid: nil)
        source.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "B", paid: nil)

        let destination = makeStore()
        destination.replaceEverything(with: source.makeBackupDocument())

        #expect(destination.settings.nextBillNumber == 3)
    }

    @Test("An imported file is not this phone's backup")
    func importDoesNotCountAsBackup() throws {
        let source = makeStore()
        source.markExported()
        let document = source.makeBackupDocument()

        let destination = makeStore()
        destination.replaceEverything(with: document)

        #expect(destination.settings.lastExportAt == nil,
                "the nudge stays on until this phone writes its own file")
    }

    @Test("Bill lines still point at their products after an import")
    func linesResolveAfterImport() throws {
        let source = makeStore()
        let product = source.addProduct(name: "Deadbolt", stock: 10, cost: 40, price: 70)
        let bill = try #require(
            source.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )
        #expect(source.product(uid: product.uid)?.stock == 7)

        let destination = makeStore()
        destination.replaceEverything(with: source.makeBackupDocument())

        // Voiding on the new phone has to find the product by uid, which is the
        // whole reason products carry one.
        let importedBill = try #require(destination.bills.first { $0.number == bill.number })
        destination.void(importedBill)

        #expect(destination.products.first?.stock == 10)
    }

    // MARK: Validation

    @Test("Junk is rejected as not a Stockbook file")
    func rejectsJunk() {
        let data = Data("this is not json".utf8)
        #expect(throws: BackupError.notStockbookData) {
            try BackupService.decode(data)
        }
    }

    @Test("Valid JSON that is not a backup is rejected")
    func rejectsForeignJSON() {
        let data = Data(#"{"hello":"world"}"#.utf8)
        #expect(throws: BackupError.notStockbookData) {
            try BackupService.decode(data)
        }
    }

    @Test("A file from a newer format is rejected, not guessed at")
    func rejectsNewerVersion() {
        let data = Data(#"{"version":99,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K","currencySymbol":"SAR ","products":[],"bills":[]}"#.utf8)
        #expect(throws: BackupError.newerVersion(found: 99)) {
            try BackupService.decode(data)
        }
    }

    @Test("The filename carries the export date")
    func filename() {
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

        #expect(document.suggestedFilename == "stockbook-2026-08-11.json")
    }

    @Test("The summary line reads the way Settings shows it")
    func summary() {
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

        #expect(document.summaryLine == "Khalid Al-Amri · 8 products · 0 bills · saved 28 July 2026")
    }
}
