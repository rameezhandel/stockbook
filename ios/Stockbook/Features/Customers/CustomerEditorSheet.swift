import SwiftUI

/// Adding a customer, or correcting one.
///
/// The same sheet for both, the way the product editor is, because the fields are
/// identical and the difference is one title and whether Remove is there.
///
/// Only the name is required. A shop that knows a name and nothing else still has
/// a customer, and demanding a phone number would teach the owner to type nonsense
/// into the box.
struct CustomerEditorSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    /// Nil when adding.
    let existing: Customer?
    let onClose: () -> Void

    @State private var name = ""
    @State private var phone = ""
    @State private var place = ""
    @State private var confirmingRemoval = false

    private var isEditing: Bool { existing?.isOnRoster == true }

    private var canSave: Bool { !name.isBlank }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: isEditing ? Loc.editCustomer : Loc.newCustomer,
                subtitle: existing.map { customer in
                    customer.hasHistory
                        ? customer.meta(in: currency, strings: Loc)
                        : Loc.noBillsYet
                },
                onClose: onClose
            )

            NocturneField(
                label: Loc.customerName,
                placeholder: Loc.customerNameExample,
                text: $name,
                height: Metrics.tallInputHeight,
                isRequiredAndEmpty: name.isBlank,
                fontSize: 15,
                identifier: "customer.name"
            )
            .padding(.bottom, 12)

            HStack(alignment: .top, spacing: 8) {
                NocturneField(
                    label: Loc.customerPhone,
                    placeholder: Loc.optionalField,
                    text: $phone,
                    keyboard: .phonePad,
                    identifier: "customer.phone"
                )
                NocturneField(
                    label: Loc.customerPlace,
                    placeholder: Loc.optionalField,
                    text: $place,
                    identifier: "customer.place"
                )
            }
            .padding(.bottom, 16)

            Button(Loc.saveCustomer) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!canSave)

            if !canSave {
                Text(Loc.enterCustomerNameFirst)
                    .nocturneText(.meta)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
            }

            if isEditing, let existing {
                // Removal is a second tap, and the note is why: "remove" beside
                // somebody's name reads like deleting them and their history,
                // and it does not do that.
                VStack(alignment: .leading, spacing: 6) {
                    Button(confirmingRemoval ? Loc.tapAgainToRemove : Loc.removeFromCustomers) {
                        if confirmingRemoval {
                            store.removeCustomer(key: existing.key)
                            onClose()
                        } else {
                            withAnimation(Metrics.quick) { confirmingRemoval = true }
                        }
                    }
                    .buttonStyle(.ghostMuted)

                    Text(Loc.removeCustomerNote).nocturneText(.meta)
                }
                .padding(.top, 18)
            }
        }
        .onAppear {
            name = existing?.name ?? ""
            phone = existing?.phone ?? ""
            place = existing?.place ?? ""
        }
        .keyboardDoneButton()
    }

    private func save() {
        guard canSave else { return }
        if let existing, existing.isOnRoster {
            store.updateCustomer(key: existing.key, name: name, phone: phone, place: place)
        } else {
            // A name that has only ever appeared on bills lands here too: adding
            // it is what puts it on the roster, and `addCustomer` keys it the
            // same way, so their history comes with them.
            store.addCustomer(name: name, phone: phone, place: place)
        }
        onClose()
    }
}
