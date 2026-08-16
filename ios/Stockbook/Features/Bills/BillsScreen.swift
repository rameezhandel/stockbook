import SwiftUI

/// Every bill ever saved, newest first — and, filtered to one customer, the
/// answer to "what has this person bought and what do they still owe me?"
///
/// Nothing here is deleted. A bill entered wrong is *voided*, which puts its
/// stock back and leaves the record in place with a "voided" mark. Without that,
/// one mistyped bill puts the shelf and the app permanently out of step; with
/// deletion instead, the history quietly stops matching what actually happened.
struct BillsScreen: View {
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
            ScreenHeader(title: Loc.billsTitle, bottomPadding: 10)

            if !customers.isEmpty {
                filter
                    .padding(.horizontal, Metrics.screenPadding)
                    .padding(.bottom, 10)
            }

            ScrollView {
                LazyVStack(spacing: Metrics.rowGap) {
                    if let selected {
                        summary(for: selected)
                            .padding(.bottom, 4)
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

    /// Both figures count **live bills only** — a voided bill did not happen, so
    /// it is neither a sale nor a debt. It still appears in the list below,
    /// marked, because history is never hidden.
    private func summary(for customer: Customer) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 9) {
                Glyph(Icon.customer, size: 16)
                    .foregroundStyle(Nocturne.accent)
                Text(customer.name)
                    .nocturneText(.rowPrimary)
                    .lineLimit(1)
                Spacer(minLength: 0)
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
                    value: customer.owed > 0 ? Money.text(customer.owed, in: currency) : Loc.nothingPending,
                    detail: nil,
                    tint: customer.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500
                )
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
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
            if let detail {
                Text(detail).nocturneText(.meta)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
