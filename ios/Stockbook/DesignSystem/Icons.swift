import SwiftUI

/// Every glyph the app uses, in one place.
///
/// The design calls for **Phosphor Icons** (regular weight, filled only for the
/// active tab and the backup shield). Phosphor is not on the system, so each
/// name below maps to the closest SF Symbol. When the Phosphor font or an asset
/// set is added to the project, this is the only file that changes — no feature
/// code names a glyph directly.
enum Icon {
    // Navigation / chrome
    static let settings = "gearshape"                 // phosphor: gear-six
    static let today = "house"                        // phosphor: house
    static let todayActive = "house.fill"
    static let items = "shippingbox"                  // phosphor: shapes
    static let itemsActive = "shippingbox.fill"
    static let sell = "plus.circle"                   // phosphor: plus-circle
    static let sellActive = "plus.circle.fill"
    static let bills = "doc.text"                     // phosphor: receipt
    static let billsActive = "doc.text.fill"
    static let back = "arrow.left"                    // phosphor: arrow-left

    // Actions
    static let add = "plus"                           // phosphor: plus
    static let remove = "minus"                       // phosphor: minus
    static let delete = "trash"                       // phosphor: trash
    static let edit = "pencil"                        // phosphor: pencil-simple
    static let close = "xmark"                        // phosphor: x
    static let confirm = "checkmark"                  // phosphor: check
    static let browseAll = "text.magnifyingglass"     // phosphor: list-magnifying-glass
    static let addStock = "plus.square.on.square"     // phosphor: stack-plus
    static let chooseFromList = "chevron.up.chevron.down" // phosphor: caret-up-down
    static let openRow = "chevron.right"              // phosphor: caret-right

    // People & money
    static let customer = "person"                    // phosphor: user
    static let owed = "banknote"                      // phosphor: hand-coins
    static let money = "dollarsign.circle"            // phosphor: money
    /// The owner's own spending. A receipt, not a coin: what is written down
    /// here is the slip, and the coin icons are already spoken for.
    static let expenses = "receipt"                   // phosphor: receipt
    static let partPayment = "scissors"               // phosphor: scissors

    // Appearance
    static let themeDark = "moon"                     // phosphor: moon
    static let themeLight = "sun.max"                 // phosphor: sun

    // Files & backup
    static let export = "square.and.arrow.up"         // phosphor: export
    static let importFile = "tray.and.arrow.down"     // phosphor: tray-arrow-down
    static let file = "doc.text"                      // phosphor: file-text
    static let share = "square.and.arrow.up"          // phosphor: share-network
    static let folder = "folder"                      // phosphor: folder-open
    static let backupMissing = "exclamationmark.shield" // phosphor: shield-warning
    static let backupDone = "checkmark.shield.fill"   // phosphor: shield-check (filled)
}

/// A glyph at an explicit point size, the way the design specifies them.
struct Glyph: View {
    let name: String
    var size: CGFloat = 16

    init(_ name: String, size: CGFloat = 16) {
        self.name = name
        self.size = size
    }

    var body: some View {
        Image(systemName: name)
            .font(.system(size: size, weight: .regular))
    }
}
