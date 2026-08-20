import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Photographs of paper bills, on this phone.
///
/// The twin of `PhotoStore.kt`. The book holds ids; this holds the pictures. They
/// are kept apart because the shop file is rewritten every time stock moves, and
/// a photograph is a thousand times the size of everything else in it — one sale
/// would mean rewriting megabytes.
///
/// Files live inside the app's own container, beside the book. Nothing else on
/// the phone can read them: they are not in Photos, and not in the Files app,
/// which only ever shows `Documents/` and only for apps that ask it to. A shop's
/// invoices should not turn up while somebody is scrolling their pictures, and
/// they do not.
///
/// Deliberately not a model type: an image codec is not domain work. What *is*
/// domain work — how large, how compressed, what a file is called — lives in
/// `PhotoPolicy`, so both phones store the same kind of object.
final class PhotoStore {

    private let directory: URL

    /// Beside `stockbook.json`, so the book and its pictures share a fate.
    ///
    /// Application Support rather than Caches: the system may reclaim Caches
    /// under storage pressure, and a photograph of a bill is not something the
    /// app can fetch again.
    init(directory: URL? = nil) {
        let resolved = directory ?? FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("photos", isDirectory: true)
        self.directory = resolved
        try? FileManager.default.createDirectory(at: resolved, withIntermediateDirectories: true)
    }

    /// Where a photograph lives.
    ///
    /// Rebuilt from the id every time, **never stored**. An app container's path
    /// changes between launches and across updates on iOS, so a URL held from
    /// last time points at nothing and the picture appears to have vanished.
    func url(id: String) -> URL {
        directory.appendingPathComponent(PhotoPolicy.fileName(id))
    }

    /// Whether this phone actually has the picture.
    ///
    /// A separate question from whether the bill names one, and asked every time
    /// a photograph is shown. A book that arrived from another phone names
    /// pictures this one has never had.
    func has(id: String) -> Bool {
        FileManager.default.fileExists(atPath: url(id: id).path)
    }

    /// Reads what the camera or the picker handed back, shrinks it, and keeps it.
    ///
    /// Returns the new id, or nil if the image could not be read — a picker can
    /// hand back something that resolves to nothing, and that is not a crash.
    func save(_ data: Data) -> String? {
        guard let shrunk = shrink(data) else { return nil }
        let id = PhotoPolicy.newID()
        do {
            try shrunk.write(to: url(id: id), options: .atomic)
            return id
        } catch {
            try? FileManager.default.removeItem(at: url(id: id))
            return nil
        }
    }

    func delete(id: String) {
        try? FileManager.default.removeItem(at: url(id: id))
    }

    /// The stored bytes, for putting a photograph into a backup archive.
    ///
    /// The file as it sits on disk, not a re-encode: it was shrunk and
    /// compressed once when it was taken, and running it through the JPEG
    /// encoder a second time would cost quality for nothing.
    ///
    /// Nil when this phone has not got the picture, which is an ordinary answer.
    func bytes(id: String) -> Data? {
        try? Data(contentsOf: url(id: id))
    }

    /// Puts a photograph from a backup archive on disk under the id the book
    /// already knows it by.
    ///
    /// Overwrites, deliberately. An import is a replacement of the whole book,
    /// and the incoming archive is the authority on what its own ids mean.
    func write(id: String, data: Data) {
        try? data.write(to: url(id: id), options: .atomic)
    }

    /// Collects pictures the book no longer refers to.
    ///
    /// Runs one way only, and the asymmetry is the point: a file nothing points
    /// at is rubbish, but an id whose file is missing is a photograph this phone
    /// has not got *yet*. Restoring a book strands every picture on the phone,
    /// which is what this is mainly for.
    ///
    /// Files it did not write are left alone — an app that tidies away things it
    /// does not recognise is an app that eventually deletes something it should
    /// not have.
    func sweep(keeping: Set<String>) {
        for name in names() where !keeping.contains(name.id) {
            try? FileManager.default.removeItem(at: name.url)
        }
    }

    /// What the owner is spending on pictures: how many, and how much room.
    func usage() -> Usage {
        let ours = names()
        let bytes = ours.reduce(into: Int64(0)) { total, entry in
            let size = try? entry.url.resourceValues(forKeys: [.fileSizeKey]).fileSize
            total += Int64(size ?? 0)
        }
        return Usage(count: ours.count, bytes: bytes)
    }

    struct Usage {
        let count: Int
        let bytes: Int64
    }

    private func names() -> [(id: String, url: URL)] {
        let contents = (try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.fileSizeKey]
        )) ?? []
        return contents.compactMap { url in
            PhotoPolicy.id(fromFileName: url.lastPathComponent).map { (id: $0, url: url) }
        }
    }

    /// Decodes at a reduced size rather than decoding and then shrinking.
    ///
    /// A modern phone camera hands back twelve megapixels or more. Holding that
    /// whole bitmap to throw most of it away is how an app gets killed for memory
    /// while saving a picture of a receipt. `CGImageSourceCreateThumbnailAtIndex`
    /// decodes straight to the size wanted and never materialises the full image
    /// — and with `…WithTransform` it applies the orientation tag, so a
    /// photograph taken in portrait does not arrive on its side.
    private func shrink(_ data: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }

        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: PhotoPolicy.maxEdge
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }

        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output, UTType.jpeg.identifier as CFString, 1, nil
        ) else { return nil }

        CGImageDestinationAddImage(
            destination,
            image,
            [kCGImageDestinationLossyCompressionQuality: PhotoPolicy.quality] as CFDictionary
        )
        guard CGImageDestinationFinalize(destination) else { return nil }
        return output as Data
    }
}
