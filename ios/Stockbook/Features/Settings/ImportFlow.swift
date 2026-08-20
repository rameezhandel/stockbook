import Foundation
import Observation

/// The state machine behind importing a backup.
///
/// Lifted out of `SettingsScreen` because it is the most destructive path in the
/// app — it replaces every product and bill on the phone — and logic that
/// dangerous should be assertable without a simulator. The view now only renders
/// the stage and forwards taps.
///
/// The safety property this type exists to guarantee: **`confirm()` returns a
/// document only from `picked`.** Nothing else can produce one, so a corrupt
/// file, a cancelled pick, or a double tap after importing cannot reach
/// `replaceEverything`.
@Observable
final class ImportFlow {

    enum Stage {
        case idle
        /// Decoded and validated. The owner has not agreed to anything yet.
        case picked(BackupDocument, filename: String)
        case failed(BackupError)
        case imported

        var isFailure: Bool {
            if case .failed = self { return true }
            return false
        }

        var pickedDocument: BackupDocument? {
            if case .picked(let document, _) = self { return document }
            return nil
        }
    }

    private(set) var stage: Stage = .idle

    /// Handles the document picker's result. A file that does not decode, or
    /// that fails validation, lands in `failed` and never in `picked`.
    func pick(_ result: Result<URL, Error>) {
        switch result {
        case .failure:
            stage = .failed(.unreadable)
        case .success(let url):
            do {
                // The document only. An archive's photographs are not touched
                // until the owner has agreed to the swap — a book they look at
                // and cancel must not leave pictures behind that nothing refers
                // to.
                let document = try BackupService.read(from: url)
                source = url
                stage = .picked(document, filename: url.lastPathComponent)
            } catch let error as BackupError {
                source = nil
                stage = .failed(error)
            } catch {
                source = nil
                stage = .failed(.unreadable)
            }
        }
    }

    /// What was picked, kept so the photographs can be read after the owner
    /// agrees rather than before.
    ///
    /// The URL rather than the bytes: an archive of two hundred photographs is
    /// tens of megabytes, and holding it across a decision the owner may take a
    /// minute over is memory this phone has better uses for.
    private var source: URL?

    /// Writes the archive's photographs to disk. Call **after**
    /// `replaceEverything`, so the book that names them exists first.
    ///
    /// Silent about failure on purpose. The book is already in; a picture that
    /// will not come out of the archive costs that picture, and the bill keeps
    /// its id either way so the same archive can be tried again later.
    func restorePhotos(into photos: PhotoStore) {
        guard let url = source else { return }
        // Picked URLs live outside the app's sandbox, so the read has to be
        // scoped a second time — the scope taken during `pick` ended with it.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let bytes = try? Data(contentsOf: url) else { return }
        BackupArchive.photos(bytes) { id, data in photos.write(id: id, data: data) }
    }

    func cancel() {
        stage = .idle
        source = nil
    }

    /// The document to apply, or `nil` when there is nothing the owner has
    /// agreed to. Callers must treat `nil` as "do nothing" — it is the only
    /// thing standing between a stray tap and an unrecoverable swap.
    func confirm() -> BackupDocument? {
        guard let document = stage.pickedDocument else { return nil }
        stage = .imported
        return document
    }
}
