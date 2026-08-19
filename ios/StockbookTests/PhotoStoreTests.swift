import Testing
import Foundation
@testable import Stockbook

/// The picture side of a photographed bill.
///
/// The book's half of this is `BillPhotoTests`; this is the disk's half, and the
/// part worth pinning is the sweep. It deletes files, which is the one operation
/// in this app that cannot be undone by editing something afterwards — so what it
/// may take, and what it must leave, is checked rather than assumed.
///
/// Each test gets its own directory, so nothing here can reach the pictures of a
/// simulator that happens to have the app installed.
@Suite("Photo store")
struct PhotoStoreTests {

    private func makeStore() throws -> (PhotoStore, URL) {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("photos-\(UUID().uuidString)", isDirectory: true)
        return (PhotoStore(directory: directory), directory)
    }

    /// A file of a known size under a given id, without going near an encoder.
    @discardableResult
    private func plant(_ id: String, in directory: URL, bytes: Int = 8) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent(PhotoPolicy.fileName(id))
        try Data(repeating: 0x41, count: bytes).write(to: url)
        return url
    }

    @Test("A picture this phone has not got is reported missing, not assumed present")
    func missingIsAnswerable() throws {
        let (store, _) = try makeStore()

        #expect(!store.has(id: "never been here"))
    }

    @Test("The sweep collects pictures the book no longer names")
    func sweepCollectsOrphans() throws {
        let (store, directory) = try makeStore()
        try plant("kept", in: directory)
        try plant("orphan", in: directory)

        store.sweep(keeping: ["kept"])

        #expect(store.has(id: "kept"))
        #expect(!store.has(id: "orphan"))
    }

    @Test("An id the book names but this phone lacks survives a sweep")
    func sweepToleratesMissingFiles() throws {
        // The asymmetry the whole design rests on. A book restored ahead of its
        // pictures names files that are not here yet; the sweep must not treat
        // that as anything at all, let alone as a reason to tidy up.
        let (store, directory) = try makeStore()
        try plant("here", in: directory)

        store.sweep(keeping: ["here", "not here yet"])

        #expect(store.has(id: "here"))
    }

    @Test("A file this app did not write is left where it is")
    func sweepLeavesForeignFiles() throws {
        // An app that tidies away things it does not recognise is an app that
        // eventually deletes something it should not have.
        let (store, directory) = try makeStore()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let stranger = directory.appendingPathComponent("someone-elses-notes.txt")
        try Data("hello".utf8).write(to: stranger)

        store.sweep(keeping: [])

        #expect(FileManager.default.fileExists(atPath: stranger.path))
    }

    @Test("Deleting one picture leaves the others alone")
    func deleteIsNarrow() throws {
        let (store, directory) = try makeStore()
        try plant("a", in: directory)
        try plant("b", in: directory)

        store.delete(id: "a")

        #expect(!store.has(id: "a"))
        #expect(store.has(id: "b"))
    }

    @Test("Usage counts only our own files")
    func usageIgnoresStrangers() throws {
        // This figure is shown to the owner as what the app is costing them in
        // storage. Counting a file the app did not write would make it a lie.
        let (store, directory) = try makeStore()
        try plant("a", in: directory, bytes: 100)
        try plant("b", in: directory, bytes: 200)
        try Data(repeating: 0x42, count: 999).write(
            to: directory.appendingPathComponent("stranger.txt")
        )

        let usage = store.usage()

        #expect(usage.count == 2)
        #expect(usage.bytes == 300)
    }

    @Test("An empty store reports nothing rather than failing")
    func emptyUsage() throws {
        let (store, _) = try makeStore()

        #expect(store.usage().count == 0)
        #expect(store.usage().bytes == 0)
    }

    @Test("A url is rebuilt from its id, never remembered")
    func urlComesFromTheID() throws {
        // Held URLs go stale: an app container's path changes between launches
        // and across updates on iOS, and a remembered one points at nothing.
        let (store, directory) = try makeStore()

        #expect(store.url(id: "abc") == directory.appendingPathComponent("abc.jpg"))
    }

    @Test("Something that is not an image is refused rather than stored")
    func rubbishIsNotStored() throws {
        let (store, _) = try makeStore()

        #expect(store.save(Data("not a jpeg".utf8)) == nil)
        #expect(store.usage().count == 0)
    }
}
