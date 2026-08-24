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

    var body: some View {
        PartyEditorSheet(
            party: existing.map { customer in
                EditableParty(
                    key: customer.key,
                    name: customer.name,
                    phone: customer.phone,
                    place: customer.place,
                    openingBalance: customer.openingBalance,
                    isOnRoster: customer.isOnRoster,
                    subtitle: customer.hasHistory
                        ? customer.meta(in: currency, strings: Loc)
                        : Loc.noBillsYet
                )
            },
            words: PartyWords(
                newTitle: Loc.newCustomer,
                editTitle: Loc.editCustomer,
                nameLabel: Loc.customerName,
                nameExample: Loc.customerNameExample,
                saveTitle: Loc.saveCustomer,
                nameFirst: Loc.enterCustomerNameFirst,
                removeTitle: Loc.removeFromCustomers,
                removeNote: Loc.removeCustomerNote,
                identifier: "customer"
            ),
            // Asked on every keystroke. The store is `@Observable`, so this
            // re-answers as the roster changes underneath the open sheet.
            clash: { store.customerClashing($0, exceptKey: existing?.key)?.name },
            onSave: { name, phone, place, opening, key in
                if let key {
                    _ = store.updateCustomer(key: key, name: name, phone: phone, place: place, openingBalance: opening)
                } else {
                    // A name that has only ever appeared on bills lands here too:
                    // adding it is what puts it on the roster, and `addCustomer`
                    // keys it the same way, so their history comes with them.
                    store.addCustomer(name: name, phone: phone, place: place, openingBalance: opening)
                }
            },
            onRemove: { store.removeCustomer(key: $0) },
            onClose: onClose
        )
    }
}

/// The same sheet, for a supplier.
///
/// One body, two entry points. The fields, the gate and the two-tap removal are
/// identical on both sides of the book; what differs is the words and where the
/// save lands, and those are the two things passed in.
struct SupplierEditorSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    let existing: Supplier?
    let onClose: () -> Void

    var body: some View {
        PartyEditorSheet(
            party: existing.map { supplier in
                EditableParty(
                    key: supplier.key,
                    name: supplier.name,
                    phone: supplier.phone,
                    place: supplier.place,
                    openingBalance: supplier.openingBalance,
                    isOnRoster: supplier.isOnRoster,
                    subtitle: supplier.hasHistory
                        ? supplier.meta(in: currency, strings: Loc)
                        : Loc.noPurchasesYet
                )
            },
            words: PartyWords(
                newTitle: Loc.newSupplier,
                editTitle: Loc.editSupplier,
                nameLabel: Loc.supplier,
                nameExample: Loc.supplierNameExample,
                saveTitle: Loc.saveSupplier,
                nameFirst: Loc.enterCustomerNameFirst,
                removeTitle: Loc.removeFromSuppliers,
                removeNote: Loc.removeSupplierNote,
                identifier: "supplier"
            ),
            clash: { store.supplierClashing($0, exceptKey: existing?.key)?.name },
            onSave: { name, phone, place, opening, key in
                if let key {
                    _ = store.updateSupplier(key: key, name: name, phone: phone, place: place, openingBalance: opening)
                } else {
                    store.addSupplier(name: name, phone: phone, place: place, openingBalance: opening)
                }
            },
            onRemove: { store.removeSupplier(key: $0) },
            onClose: onClose
        )
    }
}

/// Whatever is being corrected, reduced to what this sheet actually edits.
struct EditableParty {
    let key: String
    let name: String
    let phone: String?
    let place: String?
    let openingBalance: Double
    let isOnRoster: Bool
    let subtitle: String
}

/// The half-dozen sentences that differ between the two sides.
struct PartyWords {
    let newTitle: String
    let editTitle: String
    let nameLabel: String
    let nameExample: String
    let saveTitle: String
    let nameFirst: String
    let removeTitle: String
    let removeNote: String
    /// Prefix for the accessibility identifiers, so a UI test can tell the two
    /// sheets apart even though they are one view.
    let identifier: String
}

private struct PartyEditorSheet: View {
    @Environment(\.currency) private var currency

    let party: EditableParty?
    let words: PartyWords
    /// The account the typed name already belongs to, or nil where it is free.
    ///
    /// The gate on renaming. Identity here is the name, so a rename onto a name
    /// somebody else answers to used to join the two accounts — on a keystroke,
    /// with no warning, taking the other one's opening balance with it. Asked
    /// while the owner types, so the answer arrives before the tap.
    let clash: (String) -> String?
    let onSave: (String, String, String, Double, String?) -> Void
    let onRemove: (String) -> Void
    let onClose: () -> Void

    @State private var name = ""
    @State private var phone = ""
    @State private var place = ""
    @State private var opening = ""
    @State private var confirmingRemoval = false

    private var existing: EditableParty? { party }

    private var isEditing: Bool { party?.isOnRoster == true }

    private var taken: String? { clash(name) }

    private var canSave: Bool { !name.isBlank && taken == nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: isEditing ? words.editTitle : words.newTitle,
                subtitle: existing?.subtitle,
                onClose: onClose
            )

            NocturneField(
                label: words.nameLabel,
                placeholder: words.nameExample,
                text: $name,
                height: Metrics.tallInputHeight,
                isRequiredAndEmpty: name.isBlank,
                fontSize: 15,
                identifier: "\(words.identifier).name"
            )
            .padding(.bottom, taken == nil ? 12 : 6)

            // Under the box that caused it, in the colour of a figure owed,
            // because it is the one thing standing between the owner and Save.
            if let taken {
                Text(Loc.nameAlreadyUsed(taken))
                    .font(NocturneType.inter(11.5))
                    .foregroundStyle(Nocturne.accent400)
                    .padding(.bottom, 12)
            }

            HStack(alignment: .top, spacing: 8) {
                NocturneField(
                    label: Loc.customerPhone,
                    placeholder: Loc.optionalField,
                    text: $phone,
                    keyboard: .phonePad,
                    identifier: "\(words.identifier).phone"
                )
                NocturneField(
                    label: Loc.customerPlace,
                    placeholder: Loc.optionalField,
                    text: $place,
                    identifier: "\(words.identifier).place"
                )
            }
            .padding(.bottom, 12)

            NocturneField.number(
                label: Loc.openingBalanceField,
                placeholder: Loc.optionalField,
                text: $opening,
                prefix: currency.symbol.trimmed,
                identifier: "\(words.identifier).openingBalance"
            )

            Text(Loc.openingBalanceNote)
                .nocturneText(.meta)
                .padding(.top, 6)
                .padding(.bottom, 16)

            Button(words.saveTitle) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!canSave)

            if !canSave {
                Text(words.nameFirst)
                    .nocturneText(.meta)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
            }

            if isEditing, let existing {
                // Removal is a second tap, and the note is why: "remove" beside
                // somebody's name reads like deleting them and their history,
                // and it does not do that.
                VStack(alignment: .leading, spacing: 6) {
                    Button(confirmingRemoval ? Loc.tapAgainToRemove : words.removeTitle) {
                        if confirmingRemoval {
                            onRemove(existing.key)
                            onClose()
                        } else {
                            withAnimation(Metrics.quick) { confirmingRemoval = true }
                        }
                    }
                    .buttonStyle(.ghostMuted)

                    Text(words.removeNote).nocturneText(.meta)
                }
                .padding(.top, 18)
            }
        }
        .onAppear {
            name = existing?.name ?? ""
            phone = existing?.phone ?? ""
            place = existing?.place ?? ""
            // Blank rather than "0" for a customer who owes nothing from before: a
            // zero in the box reads as a figure somebody checked.
            if let carried = existing?.openingBalance, carried > 0 {
                opening = Money.amount(carried, in: currency)
            }
        }
        .keyboardDoneButton()
    }

    private func save() {
        guard canSave else { return }
        onSave(name, phone, place, Money.parse(opening) ?? 0, isEditing ? existing?.key : nil)
        onClose()
    }
}
