import Foundation
import Combine

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
final class ImportFlow: ObservableObject {

    enum Stage {
        case idle
        /// Decoded and validated. The owner has not agreed to anything yet.
        case picked(BackupDocument, filename: String)
        case failed(String)
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

    @Published private(set) var stage: Stage = .idle

    /// Handles the document picker's result. A file that does not decode, or
    /// that fails validation, lands in `failed` and never in `picked`.
    func pick(_ result: Result<URL, Error>) {
        switch result {
        case .failure:
            stage = .failed(BackupError.unreadable.localizedDescription)
        case .success(let url):
            do {
                let document = try BackupService.read(from: url)
                stage = .picked(document, filename: url.lastPathComponent)
            } catch let error as BackupError {
                stage = .failed(error.localizedDescription)
            } catch {
                stage = .failed(BackupError.unreadable.localizedDescription)
            }
        }
    }

    func cancel() {
        stage = .idle
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
