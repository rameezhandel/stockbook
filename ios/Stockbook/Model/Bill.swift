import Foundation

/// One sale.
///
/// A mistake is **edited or removed**, and either puts the stock back where it
/// belongs: this is the shop's own book, kept by the one person who writes in it,
/// and the record that outlives a correction is the paper bill in the book rather
/// than a crossed-out row in here.
struct Bill: Identifiable, Codable, Equatable {

    /// The human-facing number, shown as "Bill #7". Allocated from
    /// `Settings.nextBillNumber`, so it is stable, monotonic, and something you
    /// can say to a customer.
    let number: Int

    /// What was on the bill, when the owner said.
    ///
    /// **May be empty.** A shop writing bills in a paper book already knows the
    /// total, and rebuilding it line by line to arrive at a figure it can read
    /// off the paper is work for nothing. An itemised bill moves the shelf
    /// count; one entered as a total does not, and `isItemised` is how
    /// everything downstream tells the two apart.
    var lines: [BillLine]

    /// What the bill came to — **stored**, never recomputed. On an itemised bill
    /// it is the sum of `qty × price` at the moment of sale, so editing a
    /// product tomorrow cannot rewrite what somebody paid today. On a bill
    /// entered as a total it is simply what was typed.
    var total: Double

    /// `nil` means paid in full. A number means part paid, and the customer owes
    /// `total − paid`.
    var paid: Double?

    /// The number printed on the paper bill, when the shop writes one.
    ///
    /// A string, not an int: bill books are numbered "1024" in some shops and
    /// "A-1024" in others, and neither is arithmetic. Distinct from `number`,
    /// which is this app's own counter and its identity — that one has to stay
    /// unique and machine-assigned, or editing and history lookups lose their
    /// handle. This is a label the owner recognises; that is a key.
    var invoiceNo: String?

    /// Photographs of the paper bill, by id — **not** the pictures themselves.
    ///
    /// The bytes live on disk under the app's own storage, because this record is
    /// rewritten every time stock moves and a photograph is a thousand times the
    /// size of everything else in the book. An id here says a photograph was
    /// taken; whether the file is still on *this* phone is a separate question,
    /// asked of the disk. Nothing in this app may prune an id because its file is
    /// missing — a book restored ahead of its pictures re-adopts them the moment
    /// they arrive.
    ///
    /// A list rather than one, so a two-page invoice never forces a change to the
    /// file format.
    var photoIDs: [String] = []

    /// What this bill was for, in the owner's words — "3 keys cut on site",
    /// "delivered to the villa", "replaced under warranty".
    ///
    /// **The owner's own reminder, and it stays that way.** It shows on the bill
    /// when the bill is opened, and nowhere else: not on the statement, which is
    /// a document the customer is handed, and not in the shared receipt text.
    /// The same rule the payment note follows, for the same reason — a
    /// shopkeeper should be able to write "argued about the price" without
    /// wondering who else will read it.
    ///
    /// Absent rather than blank when there is none, so both builds write the
    /// same bytes for a bill without one.
    var note: String?

    /// The percentage knocked off, when the owner gave one. Absent otherwise.
    ///
    /// A **label on the arithmetic**, in exactly the sense `invoiceNo` is a
    /// label beside `number`: `total` is still the figure that was charged,
    /// stored and never recomputed, and this says how it was arrived at.
    /// Nothing downstream needs to know — a statement, a balance and a month's
    /// takings all read `total` and are already right.
    var discountPercent: Double?

    /// What that percentage came to in money, rounded once when the bill was
    /// saved.
    ///
    /// Stored beside the percentage rather than recomputed from it, so
    /// `subtotal − discount = total` holds to the last halala on a document
    /// somebody may check by hand. Recomputing `subtotal` from a percentage is
    /// where that breaks: 10% off 249.99 is not a number that divides back
    /// cleanly.
    var discountAmount: Double?

    /// Customer name, trimmed. Required on every bill.
    var who: String

    var createdAt: Date

    var id: Int { number }

    init(
        number: Int,
        lines: [BillLine] = [],
        total: Double,
        paid: Double?,
        who: String,
        invoiceNo: String? = nil,
        photoIDs: [String] = [],
        note: String? = nil,
        discountPercent: Double? = nil,
        discountAmount: Double? = nil,
        createdAt: Date = .now
    ) {
        self.number = number
        self.lines = lines
        self.total = total
        self.paid = paid
        self.who = who
        self.invoiceNo = CustomerRecord.tidied(invoiceNo)
        self.photoIDs = photoIDs
        self.note = CustomerRecord.tidied(note)
        self.discountPercent = discountPercent
        self.discountAmount = discountAmount
        self.createdAt = createdAt
    }

    /// Written by hand for the same reason `Settings` is: a default does not make
    /// the synthesised decoder tolerate a missing key, and every bill already
    /// stored was written before `invoiceNo` existed.
    ///
    /// `lines` is read the same tolerant way now that a bill may have none. A
    /// bill entered as a figure has an empty list, and a writer that omits an
    /// empty list — which kotlinx.serialization would do the moment somebody
    /// turned `encodeDefaults` off on the other side — must not cost this shop
    /// its sales history.
    ///
    /// A `voided` key left over from the builds that marked a mistake instead of
    /// correcting it is simply not read: an unknown key is ignored, so a shop
    /// file written before voiding was removed still opens, whole.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        number = try container.decode(Int.self, forKey: .number)
        lines = try container.decodeIfPresent([BillLine].self, forKey: .lines) ?? []
        total = try container.decode(Double.self, forKey: .total)
        paid = try container.decodeIfPresent(Double.self, forKey: .paid)
        who = try container.decode(String.self, forKey: .who)
        invoiceNo = try container.decodeIfPresent(String.self, forKey: .invoiceNo)
        // Tolerant for the same reason as everything above it, and for one more:
        // every bill already on every phone was written before photographs
        // existed. A default alone would not save this — the synthesised decoder
        // throws on a missing key regardless, which is how adding credit notes
        // once made every older backup unreadable.
        photoIDs = try container.decodeIfPresent([String].self, forKey: .photoIDs) ?? []
        // Optional, so the synthesised decoder would tolerate it anyway — spelled
        // out here because this type has a hand-written `init(from:)` and a key
        // left out of it is a field silently dropped on every read.
        note = try container.decodeIfPresent(String.self, forKey: .note)
        discountPercent = try container.decodeIfPresent(Double.self, forKey: .discountPercent)
        discountAmount = try container.decodeIfPresent(Double.self, forKey: .discountAmount)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
    }

    /// What the bill came to before the discount — the sum the lines add up to,
    /// or the figure the owner typed.
    ///
    /// Derived by addition rather than division, which is the whole reason
    /// `discountAmount` is stored.
    var subtotal: Double { total + (discountAmount ?? 0) }

    /// Whether anything was knocked off.
    var isDiscounted: Bool { (discountAmount ?? 0) > 0 }

    /// What is still owed on this bill. Zero when paid in full.
    var balance: Double {
        guard let paid else { return 0 }
        return max(0, total - paid)
    }

    var isPartPaid: Bool { paid != nil }

    /// Whether this bill says what was sold, or only what it came to.
    ///
    /// The one question the rest of the app asks about a bill's lines: stock
    /// moves for an itemised bill and not for a typed total, and a document with
    /// nothing to list has to say so rather than print an empty table.
    var isItemised: Bool { !lines.isEmpty }

    /// What to call this bill on screen: the paper's number where there is one,
    /// and the app's own otherwise. One number, never both — two numbers on a
    /// document is how somebody reads out the wrong one over the phone.
    func reference(_ strings: Strings) -> String {
        if let invoiceNo, !invoiceNo.isBlank { return invoiceNo }
        return strings.billNumber(number)
    }

    /// The row's first line: the names on the bill, joined. Blank when there are
    /// none.
    var summary: String { lines.map(\.name).joined(separator: ", ") }
}

/// A single line on a bill.
///
/// `name`, `price` and `cost` are **snapshots** taken at sale time. The product
/// may be renamed, repriced or deleted afterwards; history must not move.
struct BillLine: Identifiable, Codable, Equatable {

    /// Which product this was. Optional because a line can outlive its product.
    var productUID: UUID?

    /// The product's name *at the time of sale*.
    var name: String

    /// At least 1.
    var qty: Int

    /// The price actually charged — which may be an override, not the list price.
    var price: Double

    /// What one piece cost the shop, **as at the moment of sale**.
    ///
    /// Stored for the reason `Bill.total` is stored, and it is the same reason:
    /// anything recomputed from the shelf answers with today's figure. Read
    /// `Product.cost` to work out what a sale earned and a supplier's price rise
    /// next month silently rewrites what last March made — the bill has not
    /// changed, but the number under it has. The delivery side already knew
    /// this; `PurchaseLine.unitCost` has always snapshotted what was paid.
    ///
    /// **Nil means never recorded, and that is not the same as zero.** A line
    /// written before this field existed genuinely cannot answer, and goods that
    /// really did cost nothing are a different fact. Anything netting cost off
    /// takings has to keep the two apart or a bill from the old book reads as
    /// pure profit.
    var cost: Double?

    /// Lines are identified by position within their bill; nothing outside a
    /// bill ever refers to one.
    var id: String { "\(productUID?.uuidString ?? "none")-\(name)-\(qty)-\(price)" }

    init(productUID: UUID?, name: String, qty: Int, price: Double, cost: Double? = nil) {
        self.productUID = productUID
        self.name = name
        self.qty = qty
        self.price = price
        self.cost = cost
    }

    var lineTotal: Double { Double(qty) * price }

    /// What this line's goods cost the shop, or nil where it was never recorded.
    ///
    /// The arithmetic stops here on purpose. What the line *earned* is not
    /// `lineTotal - lineCost`: a discount is applied to the whole bill and not
    /// to any one line, so subtracting here overstates every line on a
    /// discounted bill. That figure belongs to whoever builds the screen, along
    /// with the decision about a bill entered as a total, which has no lines and
    /// so no cost at all.
    var lineCost: Double? { cost.map { Double(qty) * $0 } }
}
