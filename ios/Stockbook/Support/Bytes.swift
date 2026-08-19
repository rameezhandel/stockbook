import Foundation

/// Bytes as something a shopkeeper reads: `8.4 MB`, not `8804400`.
///
/// Decimal rather than binary units — a phone's own storage screen counts in
/// millions, and a figure that disagreed with the one in Settings would read as
/// the app being wrong about itself.
enum Bytes {
    static func text(_ bytes: Int64) -> String {
        if bytes >= 1_000_000 { return String(format: "%.1f MB", Double(bytes) / 1_000_000) }
        if bytes >= 1_000 { return "\(bytes / 1_000) KB" }
        return "\(bytes) B"
    }
}
