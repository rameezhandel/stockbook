import Testing
import Foundation
@testable import Stockbook

/// Import replaces every product and bill on the phone and cannot be undone, so
/// the gating in front of it is worth pinning down properly.
///
/// The property under test throughout: **a document only ever comes out of
/// `confirm()`, and only from `picked`.** Every other path — a bad file, a
/// cancel, a second tap — has to yield nil, because nil is what stops the view
/// calling `replaceEverything`.
@Suite("Import gating")
struct ImportFlowTests {

    private func writeFile(_ contents: String, named name: String = "backup.json") throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        let file = url.appendingPathComponent(name)
        try Data(contents.utf8).write(to: file)
        return file
    }

    private func validBackup() -> BackupDocument {
        BackupDocument(
            exportedAt: Date(timeIntervalSince1970: 1_785_000_000),
            ownerName: "Khalid Al-Amri",
            currencySymbol: "SAR ",
            products: [.init(uid: UUID(), name: "Padlock", stock: 8, cost: 20, price: 45)],
            bills: []
        )
    }

    private func writeValidBackup() throws -> URL {
        let data = try BackupService.encode(validBackup())
        return try writeFile(String(decoding: data, as: UTF8.self), named: "stockbook-2026-07-28.json")
    }

    // MARK: The happy path

    @Test("A valid file lands in picked, and confirm hands it back")
    func validFileConfirms() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        #expect(flow.stage.pickedDocument != nil)
        #expect(flow.stage.pickedDocument?.ownerName == "Khalid Al-Amri")

        let confirmed = flow.confirm()
        #expect(confirmed?.ownerName == "Khalid Al-Amri")
        #expect(confirmed?.products.count == 1)
    }

    @Test("The filename shown is the one the owner picked")
    func filenameSurvives() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        guard case .picked(_, let filename) = flow.stage else {
            Issue.record("expected picked")
            return
        }
        #expect(filename == "stockbook-2026-07-28.json")
    }

    // MARK: Nothing else may produce a document

    @Test("Cancel discards the pick, and confirm then yields nothing")
    func cancelDiscards() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))
        #expect(flow.stage.pickedDocument != nil)

        flow.cancel()

        #expect(flow.stage.pickedDocument == nil)
        #expect(flow.confirm() == nil, "Cancel must leave the database untouched")
    }

    @Test("Confirming from idle yields nothing")
    func idleConfirmsNothing() {
        let flow = ImportFlow()
        #expect(flow.confirm() == nil)
    }

    @Test("Confirming twice yields nothing the second time")
    func doubleConfirmIsInert() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        #expect(flow.confirm() != nil)
        #expect(flow.confirm() == nil, "a second tap must not replace the database again")
    }

    // MARK: Bad files never reach confirm

    @Test("Junk is refused and cannot be confirmed")
    func junkIsRefused() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeFile("this is not json")))

        #expect(flow.stage.isFailure)
        #expect(flow.confirm() == nil)
    }

    @Test("Valid JSON that is not a backup is refused")
    func foreignJSONIsRefused() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeFile(#"{"hello":"world"}"#)))

        #expect(flow.stage.isFailure)
        #expect(flow.confirm() == nil)
    }

    @Test("A newer format is refused rather than guessed at")
    func newerVersionIsRefused() throws {
        let flow = ImportFlow()
        let json = #"{"version":99,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K","currencySymbol":"SAR ","products":[],"bills":[]}"#
        flow.pick(.success(try writeFile(json)))

        #expect(flow.stage.isFailure)
        #expect(flow.confirm() == nil)
    }

    @Test("A picker failure is refused")
    func pickerFailureIsRefused() {
        struct Cancelled: Error {}
        let flow = ImportFlow()
        flow.pick(.failure(Cancelled()))

        #expect(flow.stage.isFailure)
        #expect(flow.confirm() == nil)
    }

    @Test("A bad file after a good one clears the pending document")
    func badFileClearsPrevious() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))
        #expect(flow.stage.pickedDocument != nil)

        flow.pick(.success(try writeFile("garbage")))

        #expect(flow.stage.pickedDocument == nil, "the earlier document must not remain confirmable")
        #expect(flow.confirm() == nil)
    }
}
