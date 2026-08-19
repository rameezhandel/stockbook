import Testing
import Foundation
@testable import Stockbook

/// Photographs of the paper bill, as far as the book is concerned.
///
/// The twin of `BillPhotoTests.kt`, test for test. The book holds **ids, never
/// pictures**. What is pinned here is the rule that makes that safe: cleanup runs
/// one way only. Files nothing refers to may be deleted; an id whose file is
/// missing may not. Get that backwards and a book restored ahead of its pictures
/// loses the link to them permanently, silently, and at the exact moment the
/// owner is least able to notice.
@Suite("Bill photographs")
@MainActor
struct BillPhotoTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @discardableResult
    private func aBill(_ store: StockbookStore, _ photos: String...) throws -> Bill {
        try #require(
            store.saveBill(
                customer: "Ahmed",
                paid: nil,
                amount: 500,
                invoiceNo: "06011",
                photoIDs: photos
            )
        )
    }

    // MARK: Carrying them

    @Test("A bill keeps the photographs it was saved with")
    func billKeepsItsPhotos() throws {
        let bill = try aBill(makeStore(), "a", "b")

        #expect(bill.photoIDs == ["a", "b"])
    }

    @Test("A bill with no photograph carries an empty list, not a nil")
    func noPhotosIsEmptyList() throws {
        // Every reading site walks the list. An absent list would make each of
        // them ask a question that has one answer everywhere.
        let bill = try aBill(makeStore())

        #expect(bill.photoIDs.isEmpty)
    }

    @Test("The same photograph is not attached twice")
    func attachIsIdempotent() throws {
        let store = makeStore()
        let bill = try aBill(store)

        store.attachPhoto(billNumber: bill.number, photoID: "a")
        store.attachPhoto(billNumber: bill.number, photoID: "a")

        #expect(try #require(store.bill(number: bill.number)).photoIDs == ["a"])
    }

    @Test("Attaching and detaching move only the ids")
    func attachAndDetach() throws {
        let store = makeStore()
        let bill = try aBill(store, "a")

        store.attachPhoto(billNumber: bill.number, photoID: "b")
        #expect(try #require(store.bill(number: bill.number)).photoIDs == ["a", "b"])

        store.detachPhoto(billNumber: bill.number, photoID: "a")
        #expect(try #require(store.bill(number: bill.number)).photoIDs == ["b"])
    }

    @Test("Detaching something that was never there changes nothing")
    func detachUnknown() throws {
        let store = makeStore()
        let bill = try aBill(store, "a")

        store.detachPhoto(billNumber: bill.number, photoID: "somebody else's")

        #expect(try #require(store.bill(number: bill.number)).photoIDs == ["a"])
    }

    @Test("Editing a bill leaves its photographs alone")
    func editingKeepsPhotos() throws {
        // The edit form knows nothing about photographs, and must not be able to
        // wipe them by not mentioning them. This is why `updateBill` takes no
        // photo argument at all.
        let store = makeStore()
        let bill = try aBill(store, "a", "b")

        store.updateBill(
            number: bill.number,
            customer: "Ahmed",
            paid: 100,
            amount: 900,
            createdAt: bill.createdAt,
            invoiceNo: "06011"
        )

        #expect(try #require(store.bill(number: bill.number)).photoIDs == ["a", "b"])
    }

    // MARK: What the sweep may take

    @Test("The book reports every photograph it still refers to")
    func idsInUse() throws {
        let store = makeStore()
        try aBill(store, "a", "b")
        try aBill(store, "c")

        #expect(store.photoIDsInUse() == ["a", "b", "c"])
    }

    @Test("Deleting a bill releases its photographs")
    func deletingReleases() throws {
        // Which is what lets the sweep collect the files afterwards. Until the
        // bill is gone the files are still spoken for.
        let store = makeStore()
        let bill = try aBill(store, "a")
        try aBill(store, "b")

        store.deleteBill(number: bill.number)

        #expect(store.photoIDsInUse() == ["b"])
    }

    @Test("Restoring a file replaces the whole set")
    func restoreReplaces() throws {
        // `replaceEverything` is a swap, not a merge, so every photograph that
        // was on this phone is stranded by an import. The sweep is what collects
        // them, and this is the figure it works from.
        let store = makeStore()
        try aBill(store, "mine")

        let incoming = makeStore()
        try aBill(incoming, "theirs")
        store.replaceEverything(with: incoming.makeBackupDocument())

        #expect(store.photoIDsInUse() == ["theirs"])
    }

    @Test("An id whose picture is missing is still an id")
    func missingFileKeepsItsID() throws {
        // The rule the whole design rests on. Nothing here can prune a reference
        // because a file is absent — the file being absent is a question for the
        // disk, asked every time the picture is shown, and answered "not on this
        // phone" rather than "there was never a photograph".
        let store = makeStore()
        let bill = try aBill(store, "a picture this phone has never had")

        #expect(try #require(store.bill(number: bill.number)).photoIDs.count == 1)
        #expect(store.photoIDsInUse().contains("a picture this phone has never had"))
    }

    // MARK: The file

    @Test("Photograph ids survive a backup round trip")
    func roundTrip() throws {
        let store = makeStore()
        try aBill(store, "a", "b")

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))

        // No version bump. A reader that drops these shows a bill with no
        // photograph where the owner took one: a picture lost, not a figure
        // misread — the same rule that let invoice numbers in.
        #expect(document.version == BackupDocument.currentVersion)

        let restored = makeStore()
        restored.replaceEverything(with: document)
        #expect(restored.bills.first?.photoIDs == ["a", "b"])
    }

    @Test("A backup written before photographs still opens")
    func olderFileStillOpens() throws {
        // The absent key reads as no photographs rather than as a broken file.
        // A default alone would not do it — the synthesised decoder throws on a
        // missing key regardless, which is how adding credit notes once made
        // every older backup unreadable.
        let json = """
        {
          "version": 3,
          "exportedAt": "2026-07-28T11:00:00Z",
          "ownerName": "Khalid Al-Amri",
          "currencyCode": "SAR",
          "bills": [
            {
              "number": 1,
              "createdAt": "2026-07-28T09:00:00Z",
              "total": 95,
              "who": "Ahmed",
              "lines": []
            }
          ],
          "customers": [],
          "payments": [],
          "products": [],
          "suppliers": [],
          "purchases": [],
          "supplierPayments": [],
          "creditNotes": []
        }
        """

        let document = try BackupService.decode(Data(json.utf8))

        #expect(document.bills.first?.photoIDs == nil)
    }

    @Test("A bill with no photograph writes no key at all")
    func emptyWritesNoKey() throws {
        // Absent, not `[]`. A shop that has never taken one must write exactly
        // the bytes it always did — and the same bytes the Android build writes,
        // which drops the key too. An empty array on one side and a missing key
        // on the other is how the two builds stop producing identical files.
        let store = makeStore()
        try aBill(store)

        let json = try #require(
            String(data: try BackupService.encode(store.makeBackupDocument()), encoding: .utf8)
        )

        #expect(!json.contains("photoIDs"))
    }

    // MARK: What a stored photograph is

    @Test("Both phones agree on what they are storing")
    func policyMatchesKotlin() {
        #expect(PhotoPolicy.maxEdge == 1600)
        #expect(PhotoPolicy.qualityOutOfHundred == 60)
        #expect(PhotoPolicy.fileExtension == "jpg")
    }

    @Test("An id names exactly one file, and the name gives it back")
    func idRoundTripsThroughFileName() {
        let id = PhotoPolicy.newID()

        #expect(PhotoPolicy.fileName(id) == "\(id).jpg")
        #expect(PhotoPolicy.id(fromFileName: PhotoPolicy.fileName(id)) == id)
    }

    @Test("Ids are lowercase, so both phones write the same name")
    func idsAreLowercase() {
        // `UUID` prints uppercase here and lowercase in Kotlin, and these names
        // travel between the two.
        #expect(PhotoPolicy.newID() == PhotoPolicy.newID().lowercased())
    }

    @Test("Ids are not reused")
    func idsAreUnique() {
        #expect(PhotoPolicy.newID() != PhotoPolicy.newID())
    }

    @Test("A file this app did not write is not claimed")
    func foreignFilesAreLeftAlone() {
        // The sweep deletes by name. Anything that is not one of ours is left
        // where it is rather than tidied away by an app that did not put it
        // there.
        #expect(PhotoPolicy.id(fromFileName: "shop.json") == nil)
        #expect(PhotoPolicy.id(fromFileName: ".jpg") == nil)
    }
}
