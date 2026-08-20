import Foundation

/// A ZIP archive, written and read by hand, storing rather than compressing.
///
/// **A line-for-line port of `ZipArchive.kt`, and it must stay one.** iOS has no
/// zip reader in Foundation and this project has no dependencies, so the format
/// has to be written out from the specification here whatever happens. Written
/// twice from the specification, the two platforms would drift; written once in
/// Kotlin — where it can be tested in fifteen seconds against `java.util.zip`,
/// in both directions — and ported here, they cannot. Nothing in this file has
/// a neighbour to check it against, which is exactly why it is the copy.
///
/// **Stored, never deflated.** Everything that goes in is either JSON — written
/// once, small — or a JPEG, which is already compressed and would grow slightly
/// if deflated. Dropping compression drops the only part of the format that
/// would be genuinely hard to hand-write.
///
/// No zip64, no encryption, no data descriptors, no multi-disk. A shop's records
/// will not reach four gigabytes, and refusing what we do not write keeps the
/// reader small enough to hold in the head.
enum ZipArchive {

    /// One file in the archive.
    struct Entry {
        let name: String
        let bytes: Data
    }

    /// Thrown for anything this reader will not accept. Callers turn it into a
    /// `BackupError`.
    struct Malformed: Error {
        let reason: String
    }

    private static let localSignature: UInt32 = 0x0403_4b50
    private static let centralSignature: UInt32 = 0x0201_4b50
    private static let endSignature: UInt32 = 0x0605_4b50

    /// The DOS timestamp every entry carries: 1980-01-01 00:00, the earliest the
    /// format can express.
    ///
    /// Deliberately fixed rather than "now". Two exports of an unchanged shop
    /// should be the same bytes, the way the JSON already is — and a real
    /// timestamp here would be a second clock disagreeing with `exportedAt`
    /// inside the document, in a field with no timezone to say what it means.
    private static let dosTime = 0
    private static let dosDate = 0x0021

    // MARK: Writing

    /// Builds an archive from `entries`.
    ///
    /// The entries arrive one at a time from a closure rather than as an array,
    /// so the caller can read each photograph from disk as it is asked for. Peak
    /// memory is then the largest single file rather than the whole shop's
    /// pictures at once — which is the entire reason this is a ZIP and not
    /// base64 inside the JSON.
    static func write(count: Int, provide: (Int) -> Entry?) -> Data {
        var out = Data()
        // Kept to write the central directory, which repeats each entry's
        // details at the end of the file. Names, sizes and offsets only — the
        // bytes themselves are already written and gone.
        var directory: [(name: String, crc: UInt32, offset: Int, size: Int)] = []

        for index in 0..<count {
            guard let file = provide(index) else { continue }
            let name = Data(file.name.utf8)
            let crc = crc32(file.bytes)
            let offset = out.count

            out.append(int: localSignature)
            out.append(short: 10)               // version needed: 1.0 is enough for stored
            out.append(short: 0)                // flags
            out.append(short: 0)                // method: stored
            out.append(short: dosTime)
            out.append(short: dosDate)
            out.append(int: crc)
            out.append(int: UInt32(file.bytes.count))    // compressed size
            out.append(int: UInt32(file.bytes.count))    // uncompressed size
            out.append(short: name.count)
            out.append(short: 0)                // extra length
            out.append(name)
            out.append(file.bytes)

            directory.append((file.name, crc, offset, file.bytes.count))
        }

        let directoryStart = out.count
        for record in directory {
            let name = Data(record.name.utf8)
            out.append(int: centralSignature)
            out.append(short: 10)               // version made by
            out.append(short: 10)               // version needed
            out.append(short: 0)                // flags
            out.append(short: 0)                // method
            out.append(short: dosTime)
            out.append(short: dosDate)
            out.append(int: record.crc)
            out.append(int: UInt32(record.size))
            out.append(int: UInt32(record.size))
            out.append(short: name.count)
            out.append(short: 0)                // extra
            out.append(short: 0)                // comment
            out.append(short: 0)                // disk number
            out.append(short: 0)                // internal attributes
            out.append(int: 0)                  // external attributes
            out.append(int: UInt32(record.offset))
            out.append(name)
        }
        let directorySize = out.count - directoryStart

        out.append(int: endSignature)
        out.append(short: 0)                    // this disk
        out.append(short: 0)                    // disk with the central directory
        out.append(short: directory.count)
        out.append(short: directory.count)
        out.append(int: UInt32(directorySize))
        out.append(int: UInt32(directoryStart))
        out.append(short: 0)                    // comment length

        return out
    }

    /// The whole-array form, for callers with everything already in hand.
    static func write(_ entries: [Entry]) -> Data {
        write(count: entries.count) { entries[$0] }
    }

    // MARK: Reading

    /// Whether `bytes` begins with the local-header signature — `PK`.
    static func looksLikeZip(_ bytes: Data) -> Bool {
        bytes.count >= 4
            && bytes[bytes.startIndex] == 0x50
            && bytes[bytes.startIndex + 1] == 0x4b
            && bytes[bytes.startIndex + 2] == 0x03
            && bytes[bytes.startIndex + 3] == 0x04
    }

    /// Where one file sits in the archive. Reading the directory costs no data.
    private struct Record {
        let name: String
        let offset: Int
        let size: Int
        let crc: UInt32
    }

    /// Every file in the archive, in the order the central directory lists them.
    static func read(_ bytes: Data) throws -> [Entry] {
        var entries: [Entry] = []
        try forEach(bytes) { name, data in entries.append(Entry(name: name, bytes: data)) }
        return entries
    }

    /// The same walk, handing each file over as it is found and keeping none.
    ///
    /// This is what the import path uses. `read` materialises the whole archive,
    /// which for a shop with two hundred photographs would be the pictures twice
    /// over — once as the archive still in hand, once as the array. Here the only
    /// thing alive at any moment is the photograph being written to disk.
    static func forEach(_ bytes: Data, action: (String, Data) throws -> Void) throws {
        for record in try records(bytes) {
            try action(record.name, try data(bytes, record))
        }
    }

    /// One named file, or nil if the archive does not have it.
    ///
    /// Separate from `forEach` so that finding the document does not read every
    /// photograph on the way past. That is not only wasted work: every entry it
    /// touched would have its CRC checked, so a single damaged picture would have
    /// refused the whole book — which is exactly backwards, since the book is the
    /// part that cannot be replaced.
    static func entry(_ bytes: Data, named name: String) throws -> Data? {
        guard let record = try records(bytes).first(where: { $0.name == name }) else { return nil }
        return try data(bytes, record)
    }

    /// The central directory, read for what it says rather than for what it
    /// points at.
    ///
    /// The directory rather than a walk of local headers, because a writer that
    /// used a data descriptor leaves the local header's sizes as zero and only
    /// the directory is then telling the truth. We never write one; other
    /// people's tools do.
    private static func records(_ bytes: Data) throws -> [Record] {
        guard let end = findEndRecord(bytes) else {
            throw Malformed(reason: "no end-of-central-directory record")
        }
        let count = bytes.short(at: end + 10)
        let directoryStart = Int(bytes.int(at: end + 16))
        guard directoryStart <= bytes.count else {
            throw Malformed(reason: "central directory outside the file")
        }

        var found: [Record] = []
        var cursor = directoryStart
        for _ in 0..<count {
            guard cursor + 46 <= bytes.count else { throw Malformed(reason: "central directory ends early") }
            guard bytes.int(at: cursor) == centralSignature else {
                throw Malformed(reason: "bad central directory header")
            }

            let method = bytes.short(at: cursor + 10)
            let crc = bytes.int(at: cursor + 16)
            let size = Int(bytes.int(at: cursor + 24))
            let nameLength = bytes.short(at: cursor + 28)
            let extraLength = bytes.short(at: cursor + 30)
            let commentLength = bytes.short(at: cursor + 32)
            let localOffset = Int(bytes.int(at: cursor + 42))
            let start = bytes.startIndex + cursor + 46
            guard start + nameLength <= bytes.endIndex else {
                throw Malformed(reason: "entry name runs past the end of the file")
            }
            let name = String(decoding: bytes[start..<(start + nameLength)], as: UTF8.self)

            // Said plainly rather than guessed at: a deflated archive is somebody
            // else's file, and half-reading it would be worse than refusing it.
            guard method == 0 else {
                throw Malformed(reason: "entry '\(name)' is compressed; this reader only stores")
            }

            found.append(Record(name: name, offset: localOffset, size: size, crc: crc))
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return found
    }

    /// The bytes of one entry, found through its local header and checked
    /// against the CRC the directory recorded.
    private static func data(_ bytes: Data, _ record: Record) throws -> Data {
        guard record.offset >= 0, record.offset + 30 <= bytes.count else {
            throw Malformed(reason: "entry '\(record.name)' points outside the file")
        }
        guard bytes.int(at: record.offset) == localSignature else {
            throw Malformed(reason: "bad local header for '\(record.name)'")
        }

        // The local header's own name and extra lengths, not the directory's:
        // the extra field is allowed to differ between the two, and using the
        // wrong one lands the read a few bytes into the file's contents.
        let nameLength = bytes.short(at: record.offset + 26)
        let extraLength = bytes.short(at: record.offset + 28)
        let start = record.offset + 30 + nameLength + extraLength
        guard start + record.size <= bytes.count else {
            throw Malformed(reason: "entry '\(record.name)' ends past the end of the file")
        }

        let from = bytes.startIndex + start
        let found = Data(bytes[from..<(from + record.size)])
        guard crc32(found) == record.crc else {
            throw Malformed(reason: "entry '\(record.name)' is damaged")
        }
        return found
    }

    /// Scans backwards for the end record.
    ///
    /// Backwards because the record is last, and only *nearly* last: the format
    /// allows a trailing comment of up to 64 KB after it. Scanning forwards would
    /// also risk stopping at a signature that happens to appear inside a JPEG.
    private static func findEndRecord(_ bytes: Data) -> Int? {
        guard bytes.count >= 22 else { return nil }
        let earliest = max(0, bytes.count - 22 - 0xFFFF)
        var index = bytes.count - 22
        while index >= earliest {
            if bytes.int(at: index) == endSignature { return index }
            index -= 1
        }
        return nil
    }

    // MARK: CRC-32

    /// The table for the CRC-32 every ZIP entry carries, polynomial `0xEDB88320`.
    private static let table: [UInt32] = (0..<256).map { index -> UInt32 in
        var value = UInt32(index)
        for _ in 0..<8 {
            value = (value & 1) != 0 ? (value >> 1) ^ 0xEDB8_8320 : value >> 1
        }
        return value
    }

    static func crc32(_ bytes: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in bytes {
            crc = (crc >> 8) ^ table[Int((crc ^ UInt32(byte)) & 0xFF)]
        }
        return crc ^ 0xFFFF_FFFF
    }
}

// MARK: - Little-endian bytes

private extension Data {
    mutating func append(short value: Int) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
    }

    mutating func append(int value: UInt32) {
        for shift in 0..<4 {
            append(UInt8((value >> (8 * UInt32(shift))) & 0xFF))
        }
    }

    /// `Data` slices do not start at zero, so every read is relative to
    /// `startIndex`. Forgetting that is the classic way a parser works on a fresh
    /// `Data` and fails on one that came out of another.
    func short(at offset: Int) -> Int {
        let base = startIndex + offset
        return Int(self[base]) | (Int(self[base + 1]) << 8)
    }

    func int(at offset: Int) -> UInt32 {
        let base = startIndex + offset
        return UInt32(self[base])
            | (UInt32(self[base + 1]) << 8)
            | (UInt32(self[base + 2]) << 16)
            | (UInt32(self[base + 3]) << 24)
    }
}
