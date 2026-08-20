import Testing
import Foundation
@testable import Stockbook

/// The pictures travelling with the book.
///
/// The twin of `BackupArchiveTests.kt`. What is being pinned is not the ZIP —
/// `ZipArchiveTests` does that against a fixture the Kotlin side wrote — but the
/// two promises around it: that the document inside is byte-for-byte what the
/// plain export always was, and that a file written before archives existed
/// still opens.
@MainActor
@Suite("Backup archive")
struct BackupArchiveTests {

    private func shopWithAPhotographedBill() -> (StockbookStore, String) {
        let store = StockbookStore(repository: InMemoryRepository())
        let product = store.addProduct(name: "Cisa lock", stock: 10, cost: 60, price: 95)
        let bill = store.saveBill(
            lines: [.init(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: 95,
            invoiceNo: "1024"
        )
        let id = "photo-one"
        if let bill { _ = store.attachPhoto(billNumber: bill.number, photoID: id) }
        return (store, id)
    }

    @Test("The document inside the archive is the file we have always written")
    func documentIsUnchanged() throws {
        // The whole reason the JSON is an entry rather than a new shape: every
        // existing test of the format, and the cross-platform byte guarantee,
        // keep testing the same bytes.
        let (store, _) = shopWithAPhotographedBill()
        let document = store.makeBackupDocument()

        let archive = try BackupArchive.pack(document) { (_: String) -> Data? in nil }
        let entries = try ZipArchive.read(archive)
        let inside = try #require(entries.first { $0.name == BackupArchive.documentEntry })
        let plain = try BackupService.encode(document)

        #expect(inside.bytes == plain)
    }

    @Test("A photograph goes in and comes back out unchanged")
    func photographSurvives() throws {
        let (store, id) = shopWithAPhotographedBill()
        // Bytes that are not text, because a JPEG is not text and nothing on
        // this path may quietly decode one.
        let picture = Data((0..<4096).map { UInt8($0 * 13 % 256) })

        let archive = try BackupArchive.pack(store.makeBackupDocument()) { (asked: String) -> Data? in
            asked == id ? picture : nil
        }

        var restored: [String: Data] = [:]
        _ = try BackupArchive.unpack(archive) { photoID, data in restored[photoID] = data }

        #expect(restored[id] == picture)
    }

    @Test("A picture the phone no longer holds is skipped, not fatal")
    func missingPictureIsSkipped() throws {
        // The photo store's rule, kept: an id whose file is missing is never
        // pruned from the book, because the file may yet arrive. Export has to
        // live by the same rule or it would refuse to run on a shop that has
        // ever lost one.
        let (store, _) = shopWithAPhotographedBill()

        let archive = try BackupArchive.pack(store.makeBackupDocument()) { (_: String) -> Data? in nil }

        var restored: [String] = []
        let document = try BackupArchive.unpack(archive) { id, _ in restored.append(id) }

        #expect(restored.isEmpty)
        // And the id is still in the book, waiting.
        #expect(document.bills.first?.photoIDs == ["photo-one"])
    }

    @Test("A bare json file still imports")
    func bareJSONStillImports() throws {
        // Every backup taken before this existed. The reader sniffs the bytes
        // rather than the extension, because the document picker lies about
        // types.
        let (store, _) = shopWithAPhotographedBill()
        let json = try BackupService.encode(store.makeBackupDocument())

        let document = try BackupArchive.document(json)

        #expect(document.bills.first?.who == "Ahmed")
    }

    @Test("An archive with no document in it is not ours")
    func noDocument() {
        let archive = ZipArchive.write([
            ZipArchive.Entry(name: "photos/a.jpg", bytes: Data([1, 2, 3]))
        ])

        #expect(throws: BackupError.self) { try BackupArchive.document(archive) }
    }

    @Test("A damaged photograph costs the photograph, never the book")
    func damagedPhotographKeepsTheBook() throws {
        // Which way round this fails is the whole question. The book is the part
        // that cannot be replaced; a picture of a bill can be taken again, and
        // the id stays on the bill either way so it can be re-adopted later.
        let (store, _) = shopWithAPhotographedBill()
        let json = try BackupService.encode(store.makeBackupDocument())
        var archive = ZipArchive.write([
            ZipArchive.Entry(name: BackupArchive.documentEntry, bytes: json),
            ZipArchive.Entry(name: "photos/x.jpg", bytes: Data(repeating: 9, count: 64))
        ])
        // A 30-byte local header and a 14-byte name, then the document; then the
        // second header, 30 bytes and a 12-byte name.
        archive[30 + 14 + json.count + 30 + 12] = 0x7F

        var restored: [String] = []
        let document = try BackupArchive.unpack(archive) { id, _ in restored.append(id) }

        #expect(restored.isEmpty)
        #expect(document.bills.first?.who == "Ahmed")
    }

    @Test("Entry names map to ids and back")
    func names() {
        #expect(BackupArchive.photoEntry("abc") == "photos/abc.jpg")
        #expect(BackupArchive.photoID("photos/abc.jpg") == "abc")
    }

    @Test("Anything else in the archive is ignored rather than refused")
    func unknownEntriesIgnored() {
        // Room for a later version to put a file beside these. Incompatibility
        // is declared by the version number, never by the file list.
        #expect(BackupArchive.photoID("stockbook.json") == nil)
        #expect(BackupArchive.photoID("photos/nested/a.jpg") == nil)
        #expect(BackupArchive.photoID("photos/.jpg") == nil)
        #expect(BackupArchive.photoID("photos/a.png") == nil)
    }

    @Test("One picture on two bills is stored once")
    func sharedPictureStoredOnce() throws {
        let store = StockbookStore(repository: InMemoryRepository())
        let product = store.addProduct(name: "Cisa lock", stock: 10, cost: 60, price: 95)
        let first = try #require(store.saveBill(
            lines: [.init(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed", paid: 95, invoiceNo: "1"
        ))
        let second = try #require(store.saveBill(
            lines: [.init(productUID: product.uid, qty: 1, price: 95)],
            customer: "Fatima", paid: 95, invoiceNo: "2"
        ))
        _ = store.attachPhoto(billNumber: first.number, photoID: "shared")
        _ = store.attachPhoto(billNumber: second.number, photoID: "shared")

        var asked = 0
        let archive = try BackupArchive.pack(store.makeBackupDocument()) { (_: String) -> Data? in
            asked += 1
            return Data(repeating: 1, count: 16)
        }

        #expect(asked == 1)
        let entries = try ZipArchive.read(archive)
        let pictures = entries.filter { BackupArchive.photoID($0.name) != nil }
        #expect(pictures.count == 1)
    }
}
