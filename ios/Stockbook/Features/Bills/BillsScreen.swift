import SwiftUI

/// Every bill ever saved, newest first — and, filtered to one customer, the
/// answer to "what has this person bought and what do they still owe me?"
///
/// A bill entered wrong is **edited or removed** from the document itself, and
/// either puts its stock back where it belongs. Nothing on this list does it:
/// the row is a way in, and the correction lives one tap further on.
struct BillsScreen: View {
    /// False inside the book, which carries one header for both halves.
    var showsHeader = true

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// `Customer.key`, or empty for everyone. A lookup, not a mode — it lives in
    /// view state and is gone the next time the tab is opened.
    @State private var customerKey = ""

    private var customers: [Customer] { store.customers() }

    private var selected: Customer? {
        customers.first { $0.key == customerKey }
    }

    private var bills: [Bill] {
        selected == nil ? store.bills : store.bills(forCustomer: customerKey)
    }

    var body: some View {
        VStack(spacing: 0) {
            if showsHeader {
                ScreenHeader(title: Loc.billsTitle, bottomPadding: 10)
            }

            // The filter is the app's customer surface, so adding one lives
            // beside it rather than in Settings. Shown even with an empty
            // roster: on a fresh shop this is the only way in other than
            // writing a bill.
            HStack(spacing: 8) {
                if customers.isEmpty {
                    // Nothing to filter yet, so the button says what it does
                    // instead of being an icon beside an absent dropdown.
                    Button(Loc.addACustomer) { router.openNewCustomer() }
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: Metrics.inputHeight, fontSize: 13))
                } else {
                    filter
                    Button {
                        router.openNewCustomer()
                    } label: {
                        Glyph(Icon.customer, size: 15)
                    }
                    .buttonStyle(SecondaryButtonStyle(height: Metrics.inputHeight))
                    .frame(width: 48)
                    .accessibilityLabel(Loc.addACustomer)
                }
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 10)

            ScrollView {
                LazyVStack(spacing: Metrics.rowGap) {
                    if let selected {
                        summary(for: selected)
                            .padding(.bottom, 4)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    if bills.isEmpty {
                        EmptyStateBox(
                            icon: Icon.bills,
                            message: Loc.noBillsEver,
                            actionTitle: Loc.startABill,
                            action: { router.startBill() }
                        )
                        .padding(.top, 8)
                    }

                    ForEach(bills) { bill in
                        Button {
                            router.openBill(bill)
                        } label: {
                            BillRow(bill: bill)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
                // Changing the filter is the one thing on this screen that
                // rewrites the whole list, so it is worth showing rather than
                // cutting to the answer.
                .motion(Motion.list, value: customerKey)
            }
        }
    }

    // MARK: The filter

    private var filter: some View {
        DropdownField(
            mark: nil,
            title: selected?.name ?? Loc.allCustomers,
            accessibilityName: Loc.customerLabel
        ) {
            Picker("", selection: $customerKey) {
                Text(Loc.allCustomers).tag("")
                ForEach(customers) { customer in
                    Text(customer.name).tag(customer.key)
                }
            }
        }
    }

    // MARK: What they are worth, and what they owe

    private func summary(for customer: Customer) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 9) {
                Glyph(Icon.customer, size: 16)
                    .foregroundStyle(Nocturne.accent)
                VStack(alignment: .leading, spacing: 1) {
                    Text(customer.name)
                        .nocturneText(.rowPrimary)
                        .lineLimit(1)
                    if let contact = contactLine(customer) {
                        Text(contact).nocturneText(.meta).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                // Editing is where a phone number gets added to somebody who has
                // only ever been a name on a bill.
                Button {
                    router.openCustomer(customer)
                } label: {
                    Glyph(Icon.edit, size: 13)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.editCustomer)
            }
            .padding(.bottom, 10)

            HStack(spacing: 10) {
                figure(
                    label: Loc.transactions,
                    value: Money.text(customer.total, in: currency),
                    detail: Loc.bills(customer.billCount)
                )
                figure(
                    label: Loc.pendingPayment,
                    value: pendingText(customer),
                    detail: nil,
                    tint: customer.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500
                )
            }

            // The statement across the whole width, and the two things that write
            // to the account beneath it. The statement is the one that only
            // *reads* — it is what somebody opens to answer a question, where the
            // two below it change what the customer owes.
            Button(Loc.statement) { router.openStatement(for: customer) }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
                .padding(.top, 11)

            HStack(spacing: 6) {
                // Always offered, including to somebody who owes nothing.
                //
                // It used to appear only while there was a balance, which meant
                // that settling up in full took the button away — and money comes
                // over a counter in more than one instalment, sometimes ahead of
                // the bill. The sheet has always handled that case: pay more than
                // is owed and it says so, "SAR 200 in advance". Hiding the way in
                // while the sheet knew what to do was the app disagreeing with
                // itself.
                Button(Loc.recordAPayment) { router.paymentFor = customer }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
                // Offered even to somebody who owes nothing: goods come back
                // after a bill has been settled, and that leaves them in credit.
                // Secondary beside the payment, because taking money is the daily
                // act and writing some off is the occasional one.
                Button(Loc.issueACreditNote) {
                    router.creditNoteFor = CreditNoteTarget(customer: customer)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 38, fontSize: 12.5))
            }
            .padding(.top, 6)
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
    }

    /// `0500 111 222 · Al Khobar`, or nothing for a customer who is only a name.
    private func contactLine(_ customer: Customer) -> String? {
        let details = [customer.phone, customer.place].compactMap { $0 }
        return details.isEmpty ? nil : details.joined(separator: " · ")
    }

    private func pendingText(_ customer: Customer) -> String {
        if customer.owed > 0 { return Money.text(customer.owed, in: currency) }
        if customer.owed < 0 { return Loc.inAdvance(Money.text(-customer.owed, in: currency)) }
        return Loc.nothingPending
    }

    private func figure(
        label: String,
        value: String,
        detail: String?,
        tint: Color = Nocturne.text
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(value)
                .font(NocturneType.inter(17))
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .rollingNumber(value)
            if let detail {
                Text(detail).nocturneText(.meta)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
