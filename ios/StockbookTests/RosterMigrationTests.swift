import Testing
import Foundation
@testable import Stockbook

/// What happens to a shop file older than the code reading it.
///
/// Nothing has shipped, so no such file exists yet — which is exactly why these
/// tests are worth keeping rather than deleting with the format versions. The
/// most expensive bug this app can ship is not a wrong statement: it is an owner
/// taking an update and finding an empty app, because a new field made a
/// perfectly good file undecodable. The shop file's decoders are written by hand
/// so that cannot happen, and this is where that is checked — today with the
/// roster's fields, tomorrow with whatever purchases add.
///
/// The **backup** file is a different matter and deliberately strict: it is
/// version-stamped, always written whole, and a missing key there means a file
/// this app did not write.
@Suite("A shop file older than its reader")
@MainActor
struct RosterMigrationTests {

    /// A shop file with no `customers` and no `payments` key — the shape this
    /// repository wrote before either existed, and the shape it will have again
    /// the next time a field is added. A default value on a property does **not**
    /// make the synthesised decoder tolerate a missing key; it throws. Without a
    /// hand-written decoder this file takes the whole shop down.
    ///
    /// It carries a `voided` key too, which is the other half of the same rule
    /// pointing the other way: a field can be **removed** as well as added, and a
    /// key nothing reads any more has to be ignored rather than refused.
    private let shopFileBeforeRoster = Data("""
    {
      "bills" : [
        {
          "createdAt" : "2026-07-28T09:41:00Z",
          "lines" : [
            { "name" : "Cisa lock", "price" : 90, "qty" : 2 }
          ],
          "number" : 1,
          "paid" : 100,
          "total" : 180,
          "voided" : false,
          "who" : "Ahmed Contracting"
        }
      ],
      "products" : [
        { "cost" : 60, "name" : "Cisa lock", "price" : 95, "stock" : 12,
          "uid" : "6C3E4E9A-1F44-4B0E-9E4B-6D8A2C1B7F01", "createdAt" : "2026-07-01T08:00:00Z" }
      ],
      "settings" : {
        "currencyCode" : "SAR",
        "lowStockAt" : 40,
        "nextBillNumber" : 2,
        "ownerName" : "Khalid Al-Amri",
        "setupCompleted" : true
      }
    }
    """.utf8)

    private var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    @Test("A shop saved before the roster existed still opens, with everything in it")
    func existingShopStillLoads() throws {
        let state = try decoder.decode(ShopState.self, from: shopFileBeforeRoster)

        #expect(state.products.count == 1)
        #expect(state.bills.count == 1)
        #expect(state.settings.ownerName == "Khalid Al-Amri")
        // The new fields arrive empty rather than refusing to arrive.
        #expect(state.customers.isEmpty)
        #expect(state.payments.isEmpty)
    }

    @Test("Their customers still exist, derived from the bills as before")
    func customersStillDerived() throws {
        let state = try decoder.decode(ShopState.self, from: shopFileBeforeRoster)
        let store = StockbookStore(repository: InMemoryRepository(state: state))

        let customer = try #require(store.customers().first)
        #expect(customer.name == "Ahmed Contracting")
        #expect(customer.billCount == 1)
        #expect(customer.owed == 80, "180 billed, 100 paid")
        #expect(!customer.isOnRoster)
        #expect(customer.phone == nil)
    }

    @Test("A backup carries the roster and the payments to the new phone")
    func roundTrip() throws {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Khalid Al-Amri")
        let product = store.addProduct(name: "Cisa lock", stock: 12, cost: 60, price: 95)
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 90)],
            customer: "Ahmed Contracting",
            paid: 100
        )
        store.addCustomer(name: "Ahmed Contracting", phone: "0500 111 222", place: "Al Khobar")
        store.recordPayment(customerKey: "ahmed contracting", amount: 30, note: "cash")

        let data = try BackupService.encode(store.makeBackupDocument())
        let document = try BackupService.decode(data)

        #expect(document.version == BackupDocument.currentVersion)
        #expect(document.customers.count == 1)
        #expect(document.payments.count == 1)

        let restored = StockbookStore(repository: InMemoryRepository())
        restored.replaceEverything(with: document)

        let customer = try #require(restored.customers().first)
        #expect(customer.name == "Ahmed Contracting")
        #expect(customer.phone == "0500 111 222")
        #expect(customer.place == "Al Khobar")
        #expect(customer.isOnRoster)
        // 180 billed, 100 at the counter, 30 after: 50 left.
        #expect(customer.owed == 50)
    }

    /// The key is written into the file rather than re-derived on import, so a
    /// payment and a bill cannot end up filed under different keys for one person.
    @Test("An imported customer keeps the key the file recorded")
    func keyComesFromTheFile() throws {
        let store = StockbookStore(repository: InMemoryRepository())
        store.addCustomer(name: "Ahmed Contracting")
        let document = store.makeBackupDocument()

        let row = try #require(document.customers.first)
        #expect(row.key == "ahmed contracting")

        let restored = StockbookStore(repository: InMemoryRepository())
        restored.replaceEverything(with: document)
        #expect(restored.customerRecords.first?.key == "ahmed contracting")
    }

    @Test("Starting over clears the roster and the payments too")
    func startOverClearsEverything() {
        let store = StockbookStore(repository: InMemoryRepository())
        store.addCustomer(name: "Ahmed")
        store.recordPayment(customerKey: "ahmed", amount: 10)

        store.startOver()

        #expect(store.customerRecords.isEmpty)
        #expect(store.payments.isEmpty)
        #expect(store.customers().isEmpty)
    }
}
