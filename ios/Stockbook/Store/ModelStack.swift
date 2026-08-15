import Foundation
import SwiftData

/// The SwiftData container.
///
/// Local file only. No CloudKit configuration, no `ModelConfiguration(cloudKitDatabase:)`
/// — the product constraint is that nothing ever leaves the device except a file
/// the owner exports by hand. Adding sync here would break the app's central
/// promise, so it is called out rather than left to be inferred.
enum ModelStack {

    static let schema = Schema([
        Product.self,
        Bill.self,
        BillLine.self,
        ShopSettings.self
    ])

    /// The on-disk container used by the app.
    static func makeContainer() -> ModelContainer {
        let configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: false,
            cloudKitDatabase: .none
        )
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            // There is no server to fall back to and no meaningful recovery from
            // a store that will not open, so fail loudly rather than silently
            // running against an empty in-memory store the owner would then type
            // a day's bills into.
            fatalError("Could not open the Stockbook store: \(error)")
        }
    }

    /// An empty in-memory container, for tests and SwiftUI previews.
    static func makeInMemoryContainer() -> ModelContainer {
        let configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: true,
            cloudKitDatabase: .none
        )
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Could not open an in-memory Stockbook store: \(error)")
        }
    }
}
