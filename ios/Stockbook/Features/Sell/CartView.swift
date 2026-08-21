import SwiftUI
import PhotosUI

/// The bill being written. The most important screen in the app: it is what the
/// owner is looking at while a customer waits.
///
/// **A form, not a cart.** The figure is typed until something says what the
/// bill is made of, and computed from the lines after that — never both at once,
/// or the screen shows one number and saves another. What was sold is optional;
/// the only thing itemising buys is the shelf moving.
struct CartView: View {
    let onBrowse: () -> Void
    let onSave: () -> Void

    @Environment(Cart.self) private var cart
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    @State private var picked: PhotosPickerItem?
    @State private var takingPhoto = false
    @State private var photoTrouble: String?

    var body: some View {
        @Bindable var cart = cart

        // Read top to bottom it is the order the owner already has the answers
        // in: the number on the paper, the day, who it is for, what it came to.
        // What was sold comes after all of that, because on most bills here it
        // never comes at all.
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                paperRow

                CustomerPicker()
                    .padding(.top, 12)

                // The figure, however it was arrived at. Never both at once:
                // a typed amount beside a line sum is two answers to one
                // question, and no way to tell which is about to be saved.
                Group {
                    if cart.lines.isEmpty {
                        NocturneField.number(
                            label: Loc.amountField,
                            text: $cart.amountText,
                            height: Metrics.tallInputHeight,
                            isRequiredAndEmpty: cart.subtotal <= 0,
                            emphasis: .sellingPrice,
                            prefix: currency.symbol.trimmed,
                            fontSize: 17,
                            identifier: "cart.amount"
                        )
                    } else {
                        itemisedTotal
                    }
                }
                .padding(.top, 12)

                ForEach(cart.lines) { line in
                    CartLineCard(
                        line: line,
                        stock: cart.stock(for: line, in: store),
                        onQuantity: { cart.setQuantity($0, for: line.id) },
                        onPrice: { cart.setPrice($0, for: line.id) },
                        onResetPrice: { cart.resetPrice(for: line.id) },
                        onRemove: { cart.remove(line.id) }
                    )
                    .padding(.top, 8)
                    .transition(.opacity)
                }

                // Quiet on purpose. Most bills here never touch it, and a
                // button that shouts is a button an owner in a hurry taps by
                // mistake.
                Button(action: onBrowse) {
                    Label(cart.isEmpty ? Loc.addItems : Loc.addAnotherItem, systemImage: Icon.browseAll)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                .padding(.top, 12)

                paymentBlock
                    .padding(.top, 14)

                saveButton
                    .padding(.top, 16)
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 12)
            .motion(Motion.list, value: cart.lines.count)
        }
        .scrollBounceBehavior(.basedOnSize)
        // Nothing moves out of the keyboard's way here, so the way back to what
        // it covers is to push it down — the same as Items, which is the screen
        // in this app that has always got this right.
        .scrollDismissesKeyboard(.interactively)
        .keyboardDoneButton()
    }

    /// The bill already carrying this number, if the shop has written it twice.
    ///
    /// A bill being corrected is excluded from its own check: without that,
    /// opening 1024 to change its date would be told 1024 is already taken, by
    /// itself.
    private var clash: Bill? {
        store.billWithInvoiceNo(cart.invoiceNo, exceptNumber: cart.editing)
    }

    /// The paper's number and the day it happened, side by side.
    ///
    /// First on the form because they describe *the bill* rather than the money,
    /// and because a shop entering yesterday's book needs the date before it
    /// thinks about anything else. The number is required; the date has today in
    /// it already.
    private var paperRow: some View {
        @Bindable var cart = cart

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .bottom, spacing: 8) {
                NocturneField(
                    label: Loc.invoiceNoField,
                    placeholder: Loc.invoiceNoHint,
                    text: $cart.invoiceNo,
                    height: 40,
                    // Opens on digits, with letters a tap away. A bill book is
                    // numbered "1024" far more often than "A-1024", so a full
                    // alphabetic keyboard makes the common case the slow one —
                    // and a pure number pad would make the other case
                    // impossible.
                    keyboard: .numbersAndPunctuation,
                    // Marked, and it means it: a bill cannot be saved without a
                    // number, and nothing puts one in the box but the owner.
                    isRequiredAndEmpty: cart.invoiceNo.isBlank,
                    fontSize: 13.5,
                    identifier: "cart.invoiceNo"
                )
                NocturneDateField(
                    label: Loc.billDate,
                    date: $cart.soldAt,
                    identifier: "cart.soldAt"
                )
            }

            // Named, not merely reported: "already used" leaves the owner
            // hunting, "already used — Ahmed, 18 Aug" points at the bill.
            if let clash {
                Text(Loc.invoiceNoAlreadyUsed(who: clash.who, date: Loc.longDate(clash.createdAt)))
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // A percentage off the whole bill, and what it comes to said back
            // immediately — a shopkeeper typing "10" wants to see "SAR 25 off"
            // before they hand the paper over, not after.
            HStack(alignment: .bottom, spacing: 8) {
                NocturneField(
                    label: Loc.discountField,
                    placeholder: Loc.discountHint,
                    text: $cart.discountText,
                    height: 40,
                    keyboard: .decimalPad,
                    fontSize: 13.5,
                    identifier: "cart.discount"
                )
                Text(
                    cart.discountValue(in: currency) > 0
                        ? Loc.discountComesTo(Money.text(cart.discountValue(in: currency), in: currency))
                        : ""
                )
                .nocturneText(.meta)
                .foregroundStyle(Nocturne.accent400)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 12)
            }
            .padding(.top, 10)

            // What the bill was for, in the owner's words — and the owner's
            // alone. The line under it says so, because a box on a bill form is
            // the obvious place to expect a customer to read it. Optional: most
            // bills are the number and the money.
            NocturneField(
                label: Loc.billNote,
                placeholder: Loc.billNoteHint,
                text: $cart.note,
                identifier: "cart.note"
            )
            .padding(.top, 10)

            Text(Loc.billNoteIsYours)
                .nocturneText(.meta)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 4)

            // The paper, photographed while it is being written — which is when
            // the owner is holding it. Directly under its number, because that is
            // the other thing on this form that describes the document rather
            // than the money.
            billPhotoRow(chooseTitle: Loc.chooseFromPhotos)
                .padding(.top, 12)
        }
    }

    /// Taking a photograph of the bill from the form it is being written on.
    ///
    /// The strip is what has been taken so far, tappable to take one back off.
    /// There is no viewer here: the form is for writing the bill, and looking
    /// closely at the paper is what opening the saved bill is for.
    ///
    /// `chooseTitle` is passed rather than read inside, because `PhotosPicker`'s
    /// label is a plain closure and `Loc` is main-actor isolated.
    @ViewBuilder
    private func billPhotoRow(chooseTitle: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(Loc.billPhotos).nocturneText(.fieldLabel)
                Spacer()
                if CameraSheet.isAvailable {
                    Button(Loc.takePhoto) { takingPhoto = true }
                        .buttonStyle(GhostButtonStyle(fontSize: 12, horizontalPadding: 0))
                }
                PhotosPicker(selection: $picked, matching: .images, photoLibrary: .shared()) {
                    Text(chooseTitle)
                        .font(NocturneType.inter(12, .medium))
                        .foregroundStyle(Nocturne.accent)
                }
            }

            if !cart.photoIDs.isEmpty {
                // Tapping takes it off the form — and only off the form. The
                // file stays until a save reconciles it or the next launch
                // sweeps it, because a correction the owner then abandons must
                // not have already destroyed a picture the bill still names.
                PhotoStrip(ids: cart.photoIDs) { cart.removePhoto($0) }
            }

            if let photoTrouble {
                Text(photoTrouble).nocturneText(.meta).foregroundStyle(Nocturne.accent400)
            }
        }
        .fullScreenCover(isPresented: $takingPhoto) {
            CameraSheet { data in
                takingPhoto = false
                guard let data else { return }
                keepPhoto(data)
            }
            .ignoresSafeArea()
        }
        .onChange(of: picked) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                picked = nil
                guard let data else {
                    photoTrouble = Loc.couldNotReadThatPhoto
                    return
                }
                keepPhoto(data)
            }
        }
    }

    /// Shrinks and keeps one photograph, then puts its id on the form.
    ///
    /// The file exists before the form names it, so an abandoned bill leaves a
    /// picture nothing refers to — which the sweep collects — rather than a bill
    /// pointing at nothing.
    private func keepPhoto(_ data: Data) {
        guard let id = PhotoStore().save(data) else {
            photoTrouble = Loc.couldNotReadThatPhoto
            return
        }
        cart.addPhoto(id)
        photoTrouble = nil
    }

    /// The total, once the bill has been itemised.
    ///
    /// It takes the amount box's place rather than sitting beside it, and says
    /// where the figure came from: it stopped being something typed the moment
    /// there were lines to add up. "Remove items" is the way back to typing.
    /// One `label … value` line above the total, in the total card's own type.
    private func deductionLine(_ label: String, _ value: String, tint: Color) -> some View {
        HStack {
            Text(label).nocturneText(.meta)
            Spacer()
            Text(value)
                .font(NocturneType.inter(12.5))
                .foregroundStyle(tint)
        }
    }

    private var itemisedTotal: some View {
        VStack(alignment: .leading, spacing: 2) {
            // Shown only when there is one: three lines where a shop gives no
            // discount would be two lines of ceremony above the figure that
            // matters.
            if cart.discountValue(in: currency) > 0 {
                deductionLine(
                    Loc.subtotalLabel,
                    Money.text(cart.subtotal, in: currency),
                    tint: Nocturne.neutral400
                )
                deductionLine(
                    Loc.discountOf(Money.amount(cart.discountPercent ?? 0, in: currency)),
                    "− " + Money.text(cart.discountValue(in: currency), in: currency),
                    tint: Nocturne.accent400
                )
            }

            HStack(alignment: .lastTextBaseline) {
                Text(Loc.total)
                    .font(NocturneType.inter(13))
                    .foregroundStyle(Nocturne.neutral500)
                Spacer()
                Text(Money.text(cart.total(in: currency), in: currency))
                    .nocturneText(.bigNumber(26))
                    .rollingNumber(cart.total(in: currency))
            }
            HStack {
                Text(Loc.fromItems(cart.lines.count))
                    .nocturneText(.meta)
                Spacer()
                Button(Loc.removeItems) { cart.removeLines() }
                    .buttonStyle(.plain)
                    .font(NocturneType.inter(12))
                    .foregroundStyle(Nocturne.accent)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(Nocturne.accent700, radius: Metrics.controlRadius)
    }

    /// Paid in full or part, and what is left if part.
    private var paymentBlock: some View {
        @Bindable var cart = cart

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                ChoicePill(
                    title: Loc.paidInFull,
                    icon: Icon.money,
                    selected: cart.payMode == .full
                ) { cart.payMode = .full }

                ChoicePill(
                    title: Loc.partPayment,
                    icon: Icon.partPayment,
                    selected: cart.payMode == .part
                ) { cart.payMode = .part }
            }

            if cart.payMode == .part {
                NocturneField.number(
                    label: Loc.paidNow,
                    text: $cart.paidText,
                    height: 40,
                    prefix: currency.symbol.trimmed
                )
                HStack(alignment: .firstTextBaseline) {
                    Text(Loc.balance)
                        .font(NocturneType.inter(12.5))
                        .foregroundStyle(Nocturne.neutral500)
                    Spacer()
                    Text(Money.text(cart.balance(in: currency), in: currency))
                        .font(NocturneType.inter(15))
                        .foregroundStyle(Nocturne.accent400)
                        .rollingNumber(cart.balance(in: currency))
                }
            }
        }
    }

    /// The last thing on the form rather than a bar pinned over it.
    ///
    /// Pinned is what Android does and what this screen tried twice; on iOS a
    /// fixed bar at the bottom of a scrolling screen is what kept colliding with
    /// the keyboard, and every screen here that works keeps nothing down there.
    /// The form is short — a save that scrolls into view under your thumb costs
    /// nothing next to a screen that swallows itself.
    ///
    /// Validation is the button's label, never a toast: it says what is missing
    /// and stays disabled until it isn't.
    private var saveButton: some View {
        Button(saveTitle, action: onSave)
            .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
            .disabled(!cart.canSave || clash != nil)
    }

    private var saveTitle: String {
        // A number on two bills is two records the shop cannot tell apart later,
        // so this one is a refusal rather than a warning.
        if clash != nil { return Loc.changeTheInvoiceNo }
        if cart.canSave { return cart.isEditing ? Loc.saveChanges : Loc.saveBill }
        // Whatever is missing, the button names it: an empty name box needs a
        // name, a typed one needs a choice from the list, a cleared number box
        // needs a number, and a bill with nothing on it needs a figure.
        if cart.customer.isBlank { return Loc.enterCustomerName }
        if cart.customerKey == nil { return Loc.chooseFromTheList }
        if cart.invoiceNo.isBlank { return Loc.enterBillNumber }
        return Loc.enterAnAmount
    }
}

// MARK: - Line

/// One product on the bill: quantity, live stock, and the editable price.
private struct CartLineCard: View {
    let line: Cart.Line
    /// Read from the store rather than carried on the line, so adding stock from
    /// another screen shows here immediately.
    let stock: Int
    let onQuantity: (Int) -> Void
    let onPrice: (Double) -> Void
    let onResetPrice: () -> Void
    let onRemove: () -> Void

    @Environment(\.currency) private var currency

    // Both numbers are edited as text so a half-typed value ("1." or "") is
    // representable. They are re-seeded from the model only when it has moved
    // somewhere the text cannot account for — a stepper tap or a price reset —
    // which is what stops a re-seed from fighting the keyboard mid-entry.
    @State private var qtyText = ""
    @State private var priceText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(line.name)
                    .nocturneText(.rowPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                Text(Money.text(line.lineTotal, in: currency))
                    .font(NocturneType.inter(15))
                    .rollingNumber(line.lineTotal)
                Button(action: onRemove) {
                    Glyph(Icon.delete, size: 15)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.remove(line.name))
            }
            .padding(.bottom, 10)

            HStack(spacing: 8) {
                stepper
                // Wraps rather than truncates: "only 3 in stock" is the warning
                // that stops a wrong bill, so it is never worth eliding to fit
                // beside a stepper and a price box on a narrow phone.
                Text(stockNote)
                    .nocturneText(.meta)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(-1)
                priceBox
            }

            if line.isPriceOverridden {
                HStack(spacing: 5) {
                    Glyph(Icon.edit, size: 11)
                    Text(Loc.usualPriceNote(Money.text(line.basePrice, in: currency)))
                        .font(NocturneType.inter(11))
                    Spacer(minLength: 6)
                    Button(Loc.reset) {
                        onResetPrice()
                        priceText = Money.amount(line.basePrice, in: currency)
                    }
                    .buttonStyle(GhostButtonStyle(fontSize: 11))
                }
                .foregroundStyle(Nocturne.accent400)
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .onAppear {
            qtyText = String(line.qty)
            priceText = Money.amount(line.price, in: currency)
        }
        .onChange(of: line.qty) { _, new in
            if Int(qtyText.trimmed) != new { qtyText = String(new) }
        }
        .onChange(of: line.price) { _, new in
            if Money.parse(priceText) != new { priceText = Money.amount(new, in: currency) }
        }
    }

    private var stepper: some View {
        HStack(spacing: 0) {
            Button {
                onQuantity(line.qty - 1)
            } label: {
                Glyph(Icon.remove, size: 15)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Loc.oneFewer)

            TextField("", text: $qtyText)
                .font(NocturneType.inter(14))
                .multilineTextAlignment(.center)
                .keyboardType(.numberPad)
                .tint(Nocturne.accent)
                .frame(width: 44, height: 34)
                .onChange(of: qtyText) { _, new in
                    if let value = Int(new.trimmed), value != line.qty { onQuantity(value) }
                }

            Button {
                onQuantity(line.qty + 1)
            } label: {
                Glyph(Icon.add, size: 15)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Loc.oneMore)
        }
        .foregroundStyle(Nocturne.text)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(radius: Metrics.controlRadius)
    }

    private var priceBox: some View {
        HStack(spacing: 5) {
            Text(currency.symbol.trimmed)
                .font(NocturneType.inter(12.5))
                .foregroundStyle(Nocturne.neutral500)
                .lineLimit(1)
                // Without this the row's squeeze lands here first and "SAR"
                // wraps onto two lines inside a 34pt box.
                .fixedSize(horizontal: true, vertical: false)
            TextField("", text: $priceText)
                .font(NocturneType.inter(14))
                .foregroundStyle(line.isPriceOverridden ? Nocturne.accent300 : Nocturne.text)
                .multilineTextAlignment(.trailing)
                .keyboardType(.decimalPad)
                .tint(Nocturne.accent)
                .frame(width: 56)
                .onChange(of: priceText) { _, new in
                    if let value = Money.parse(new), value != line.price { onPrice(value) }
                }
        }
        .padding(.horizontal, 11)
        .frame(height: Metrics.compactControlHeight)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(line.isPriceOverridden ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
    }

    /// The shelf, honestly. Overselling is allowed — the customer is standing
    /// there and the count may simply be wrong — but it is never silent.
    private var stockNote: String {
        qty > stock ? Loc.onlyInStock(stock) : Loc.piecesInStock(stock)
    }

    private var qty: Int { line.qty }
}

// MARK: - Customer

/// Required on every bill, and **chosen** rather than typed: the box filters the
/// roster as characters arrive, and only tapping a row counts.
///
/// Free text is how "Ahmed", "ahmed " and "Ahmd" become three people with three
/// balances, which stopped being cosmetic once statements, payments and opening
/// balances started hanging off a customer.
private struct CustomerPicker: View {
    @Environment(Cart.self) private var cart
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency
    @FocusState private var focused: Bool

    /// Rows are a fixed height so the list's own height is arithmetic rather than
    /// a measurement — a `ScrollView` given `maxHeight` alone accepts the whole
    /// 150 even for one row, leaving a gap where the list has ended.
    private static let rowHeight: CGFloat = 35

    /// How tall the list may grow before it scrolls. Deliberately not a whole
    /// number of rows: a sliver of the fifth showing is what says "there is more
    /// below" without a scrollbar to draw.
    private static let maxListHeight: CGFloat = 150

    private var typed: String { cart.customer.trimmed }
    private var query: String { Customer.key(for: typed) }

    /// Not `store.customerSuggestions`, which deliberately drops an exact match —
    /// sensible when the field also took free text, fatal now that a choice is
    /// compulsory: typing a name in full would remove the only row that could be
    /// tapped, and offer no way to create it either, because it already exists.
    ///
    /// Every match, not the first four. The list scrolls instead — a cap looks
    /// identical to "no such customer" for anyone who happens to sort fifth.
    private var matches: [Customer] {
        guard cart.customerKey == nil else { return [] }
        return store.customers().filter { query.isEmpty || $0.key.contains(query) }
    }

    /// Offered when what was typed is nobody yet. **A required choice must never
    /// block a sale**, and right now it would: a shop with an empty roster would
    /// have a permanently unusable Sell screen.
    private var canCreate: Bool {
        cart.customerKey == nil && !typed.isEmpty && !store.customers().contains { $0.key == query }
    }

    var body: some View {
        // The list sits **below** the field, as an ordinary sibling rather than
        // an overlay. It used to open upwards, which was right when this picker
        // was pinned to the bottom edge with the keyboard under it — on a form
        // it would shove the bill number and the date off the top of the screen
        // as you typed.
        //
        // A sibling rather than an overlay because an overlay is proposed its
        // parent's 40pt height, and no amount of alignment-guide arithmetic
        // reliably undoes that; two attempts both landed back on top of the
        // field. A sibling cannot overlap by construction.
        VStack(spacing: 6) {
            field
            if !matches.isEmpty || canCreate {
                listCard
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeOut(duration: 0.14), value: matches.count)
        .animation(.easeOut(duration: 0.14), value: canCreate)
    }

    private var field: some View {
        ZStack(alignment: .leading) {
            if cart.customer.isEmpty {
                Text(Loc.customerName)
                    .font(NocturneType.inter(14))
                    .foregroundStyle(Nocturne.neutral500)
                    .allowsHitTesting(false)
            }
            TextField("", text: Binding(get: { cart.customer }, set: { cart.typeCustomer($0) }))
                .font(NocturneType.inter(14))
                .accessibilityIdentifier("cart.customer")
                .focused($focused)
                .tint(Nocturne.accent)
                .submitLabel(.done)
        }
        .padding(.horizontal, 10)
        .frame(height: 40)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        // Marked until somebody is actually chosen, not merely until the box has
        // characters in it. Accent means "this still needs something", so a chosen
        // customer drops back to the neutral border — the two states have to look
        // different or the gate is invisible.
        .hairline(cart.customerKey == nil ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
    }

    private var listCard: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(matches) { candidate in
                        Button { choose(candidate) } label: {
                            HStack(spacing: 8) {
                                Glyph(Icon.customer, size: 13)
                                    .foregroundStyle(Nocturne.neutral500)
                                Text(candidate.name)
                                    .font(NocturneType.inter(13.5))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .lineLimit(1)
                                Text(candidate.meta(in: currency, strings: Loc))
                                    .font(NocturneType.inter(11))
                                    .foregroundStyle(candidate.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                            }
                            .padding(.horizontal, 11)
                            .frame(height: Self.rowHeight)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(height: min(CGFloat(matches.count) * Self.rowHeight, Self.maxListHeight))
            .scrollBounceBehavior(.basedOnSize)

            // Outside the scrolling part on purpose: this is the way out when
            // nobody matches, and it must never be something to scroll for.
            if canCreate {
                Button {
                    guard let record = store.addCustomer(name: typed),
                          let created = store.customer(key: record.key)
                    else { return }
                    choose(created)
                } label: {
                    HStack(spacing: 8) {
                        Glyph(Icon.add, size: 13)
                        Text(Loc.addAsCustomer(typed))
                            .font(NocturneType.inter(13.5))
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(Nocturne.accent)
                    .padding(.horizontal, 11)
                    .frame(height: Self.rowHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 3)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(Nocturne.accent, radius: Metrics.controlRadius)
        .shadow(color: Nocturne.shadow, radius: 9, x: 0, y: 6)
    }

    private func choose(_ customer: Customer) {
        cart.selectCustomer(customer)
        focused = false
        dismissKeyboard()
    }
}
