import Testing
import Foundation
@testable import Stockbook

/// The archive format, pinned to the Kotlin original.
///
/// A round trip through our own code would prove only that this implementation
/// is self-consistent — and self-consistent is exactly what a wrong
/// implementation is. The Kotlin suite checks itself against `java.util.zip` in
/// both directions; there is nothing here to check against, so what stands in
/// its place is `sharedFixture`: the same base64 string, written by the Kotlin
/// side, asserted here both as what this platform writes and as what it reads.
///
/// Four assertions across two suites. A change to either implementation that
/// the other did not get breaks one of them.
@Suite("Zip archive")
struct ZipArchiveTests {

    private func entry(_ name: String, _ text: String) -> ZipArchive.Entry {
        ZipArchive.Entry(name: name, bytes: Data(text.utf8))
    }

    /// Written by `ZipArchiveTests.kt`, which asserts the identical constant.
    ///
    /// Only possible because the writer is deterministic — the DOS timestamp is
    /// fixed at 1980-01-01 rather than "now" precisely so this can exist.
    private static let sharedFixture = """
        UEsDBAoAAAAAAAAAIQCx+AZDDQAAAA0AAAAOAAAAc3RvY2tib29rLmpzb257InZlcnNpb24iOjN9U\
        EsDBAoAAAAAAAAAIQCeOs07CAAAAAgAAAAOAAAAcGhvdG9zL29uZS5qcGcAHz5dfJu62VBLAQIKAA\
        oAAAAAAAAAIQCx+AZDDQAAAA0AAAAOAAAAAAAAAAAAAAAAAAAAAABzdG9ja2Jvb2suanNvblBLAQI\
        KAAoAAAAAAAAAIQCeOs07CAAAAAgAAAAOAAAAAAAAAAAAAAAAADkAAABwaG90b3Mvb25lLmpwZ1BL\
        BQYAAAAAAgACAHgAAABtAAAAAAA=
        """

    /// The eight bytes inside the fixture's photograph entry.
    private static var fixturePhoto: Data { Data((0..<8).map { UInt8($0 * 31 % 256) }) }

    @Test("The shared fixture is what this platform writes")
    func writesTheFixture() throws {
        let written = ZipArchive.write([
            entry("stockbook.json", #"{"version":3}"#),
            ZipArchive.Entry(name: "photos/one.jpg", bytes: Self.fixturePhoto)
        ])

        let expected = try #require(Data(base64Encoded: Self.sharedFixture, options: .ignoreUnknownCharacters))
        #expect(written == expected)
    }

    @Test("The shared fixture is what this platform reads")
    func readsTheFixture() throws {
        let bytes = try #require(Data(base64Encoded: Self.sharedFixture, options: .ignoreUnknownCharacters))

        let read = try ZipArchive.read(bytes)

        #expect(read.map(\.name) == ["stockbook.json", "photos/one.jpg"])
        #expect(String(decoding: read[0].bytes, as: UTF8.self) == #"{"version":3}"#)
        #expect(read[1].bytes == Self.fixturePhoto)
    }

    @Test("What goes in comes out")
    func roundTrip() throws {
        let written = ZipArchive.write([
            entry("stockbook.json", #"{"version":3}"#),
            entry("photos/abc.jpg", "not really a jpeg")
        ])

        let read = try ZipArchive.read(written)

        #expect(read.map(\.name) == ["stockbook.json", "photos/abc.jpg"])
        #expect(String(decoding: read[1].bytes, as: UTF8.self) == "not really a jpeg")
    }

    @Test("Bytes survive that are not text")
    func binarySurvives() throws {
        // Photographs are the point, and a JPEG is full of bytes that mean
        // something in one encoding and nothing in another. Nothing in this path
        // may treat a file as a string.
        let payload = Data((0..<512).map { UInt8($0 * 7 % 256) })

        let read = try ZipArchive.read(ZipArchive.write([
            ZipArchive.Entry(name: "photos/x.jpg", bytes: payload)
        ]))

        #expect(read.count == 1)
        #expect(read[0].bytes == payload)
    }

    @Test("An empty archive is still a valid archive")
    func empty() throws {
        // A shop with no photographs and — briefly, during setup — nothing else.
        let read = try ZipArchive.read(ZipArchive.write([]))

        #expect(read.isEmpty)
    }

    @Test("A damaged entry is refused rather than half-read")
    func damaged() throws {
        let name = "stockbook.json"
        var written = ZipArchive.write([entry(name, #"{"version":3}"#)])
        // The first byte of the entry's own data: a 30-byte local header, then
        // the name. Aimed rather than approximate — half this archive is header
        // and directory, and a bit flipped in a field the reader does not check
        // proves nothing about whether the CRC is doing its job.
        let dataStart = 30 + name.count
        written[dataStart] = written[dataStart] &+ 1

        #expect(throws: ZipArchive.Malformed.self) { try ZipArchive.read(written) }
    }

    @Test("Something that is not an archive at all is refused")
    func notAnArchive() {
        #expect(throws: ZipArchive.Malformed.self) {
            try ZipArchive.read(Data(#"{"version":3}"#.utf8))
        }
    }

    @Test("The magic bytes tell an archive from a json file")
    func magicBytes() {
        #expect(ZipArchive.looksLikeZip(ZipArchive.write([entry("a", "b")])))
        #expect(!ZipArchive.looksLikeZip(Data(#"{"version":3}"#.utf8)))
        #expect(!ZipArchive.looksLikeZip(Data()))
    }

    @Test("Two exports of the same thing are the same bytes")
    func deterministic() {
        // The JSON already has this property and it is worth keeping: a fixed DOS
        // timestamp is what stops an unchanged shop producing a different file
        // every time it is exported — and it is what lets the shared fixture
        // above be a constant at all.
        #expect(ZipArchive.write([entry("stockbook.json", "{}")])
            == ZipArchive.write([entry("stockbook.json", "{}")]))
    }

    @Test("A big entry survives")
    func big() throws {
        let payload = Data((0..<300_000).map { UInt8($0 % 251) })

        let read = try ZipArchive.read(ZipArchive.write([
            ZipArchive.Entry(name: "photos/big.jpg", bytes: payload)
        ]))

        #expect(read[0].bytes == payload)
    }

    @Test("A slice of Data reads the same as a fresh one")
    func slicedData() throws {
        // `Data` slices do not start at zero, and every read in the parser is
        // relative to `startIndex` for that reason. This is the test that fails
        // if somebody "simplifies" one of them back to a bare offset — which
        // works perfectly until the bytes arrive from something that sliced them.
        let written = ZipArchive.write([entry("stockbook.json", #"{"version":3}"#)])
        let padded = Data([0xFF, 0xFF]) + written
        let slice = padded[padded.startIndex + 2...]

        let read = try ZipArchive.read(slice)

        #expect(read.count == 1)
        #expect(String(decoding: read[0].bytes, as: UTF8.self) == #"{"version":3}"#)
    }
}
