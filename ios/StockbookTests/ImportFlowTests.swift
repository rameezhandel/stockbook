import XCTest
import Foundation
@testable import Stockbook

/// Import replaces every product and bill on the phone and cannot be undone, so
/// the gating in front of it is worth pinning down properly.
///
/// The property under test throughout: **a document only ever comes out of
/// `confirm()`, and only from `picked`.** Every other path — a bad file, a
/// cancel, a second tap — has to yield nil, because nil is what stops the view
/// calling `replaceEverything`.
/// Import gating
final class ImportFlowTests: XCTestCase {

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

    /// A valid file lands in picked, and confirm hands it back
    func testValidFileConfirms() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        XCTAssertNotNil(flow.stage.pickedDocument)
        XCTAssertEqual(flow.stage.pickedDocument?.ownerName, "Khalid Al-Amri")

        let confirmed = flow.confirm()
        XCTAssertEqual(confirmed?.ownerName, "Khalid Al-Amri")
        XCTAssertEqual(confirmed?.products.count, 1)
    }

    /// The filename shown is the one the owner picked
    func testFilenameSurvives() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        guard case .picked(_, let filename) = flow.stage else {
            XCTFail("expected picked")
            return
        }
        XCTAssertEqual(filename, "stockbook-2026-07-28.json")
    }

    // MARK: Nothing else may produce a document

    /// Cancel discards the pick, and confirm then yields nothing
    func testCancelDiscards() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))
        XCTAssertNotNil(flow.stage.pickedDocument)

        flow.cancel()

        XCTAssertNil(flow.stage.pickedDocument)
        XCTAssertNil(flow.confirm(), "Cancel must leave the database untouched")
    }

    /// Confirming from idle yields nothing
    func testIdleConfirmsNothing() {
        let flow = ImportFlow()
        XCTAssertNil(flow.confirm())
    }

    /// Confirming twice yields nothing the second time
    func testDoubleConfirmIsInert() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))

        XCTAssertNotNil(flow.confirm())
        XCTAssertNil(flow.confirm(), "a second tap must not replace the database again")
    }

    // MARK: Bad files never reach confirm

    /// Junk is refused and cannot be confirmed
    func testJunkIsRefused() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeFile("this is not json")))

        XCTAssertTrue(flow.stage.isFailure)
        XCTAssertNil(flow.confirm())
    }

    /// Valid JSON that is not a backup is refused
    func testForeignJSONIsRefused() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeFile(#"{"hello":"world"}"#)))

        XCTAssertTrue(flow.stage.isFailure)
        XCTAssertNil(flow.confirm())
    }

    /// A newer format is refused rather than guessed at
    func testNewerVersionIsRefused() throws {
        let flow = ImportFlow()
        let json = #"{"version":99,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K","currencySymbol":"SAR ","products":[],"bills":[]}"#
        flow.pick(.success(try writeFile(json)))

        XCTAssertTrue(flow.stage.isFailure)
        XCTAssertNil(flow.confirm())
    }

    /// A picker failure is refused
    func testPickerFailureIsRefused() {
        struct Cancelled: Error {}
        let flow = ImportFlow()
        flow.pick(.failure(Cancelled()))

        XCTAssertTrue(flow.stage.isFailure)
        XCTAssertNil(flow.confirm())
    }

    /// A bad file after a good one clears the pending document
    func testBadFileClearsPrevious() throws {
        let flow = ImportFlow()
        flow.pick(.success(try writeValidBackup()))
        XCTAssertNotNil(flow.stage.pickedDocument)

        flow.pick(.success(try writeFile("garbage")))

        XCTAssertNil(flow.stage.pickedDocument, "the earlier document must not remain confirmable")
        XCTAssertNil(flow.confirm())
    }
}
