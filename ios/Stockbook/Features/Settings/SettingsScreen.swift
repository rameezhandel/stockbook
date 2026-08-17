import SwiftUI

/// Settings: the shop's name, the two things it reads and bills in, and a way
/// through to the backup handoff.
///
/// It used to hold that handoff too, and ran to one long scroll where the two
/// controls an owner touches occasionally sat above two cards they use once a
/// year. Export and import now live behind a single row in `BackupScreen`, and
/// what is left fits without scrolling on most phones.
struct SettingsScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    @State private var ownerName = ""
    @State private var seeded = false

    private var settings: Settings { store.settings }
    private var products: [Product] { store.products }
    private var liveBills: [Bill] { store.liveBills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.settings) {
                Button(Loc.done) { router.showingSettings = false }
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    thisPhone
                    languageAndCurrency
                    theme
                    backupRow
                    #if DEBUG
                    startAgain
                    #endif
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
        }
        .background(Nocturne.bg.ignoresSafeArea())
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onAppear(perform: seed)
    }

    // MARK: This phone

    private var thisPhone: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(Loc.thisPhone).padding(.bottom, 8)

            VStack(alignment: .leading, spacing: 10) {
                NocturneField(
                    label: Loc.businessOwner,
                    placeholder: Loc.businessOwnerName,
                    text: $ownerName
                )
                .onChange(of: ownerName) { _, new in
                    store.setOwnerName(new)
                }

                HStack(spacing: 10) {
                    stat(Loc.productsStat, products.count)
                    stat(Loc.billsStat, liveBills.count)
                    stat(Loc.customersStat, store.customers().count)
                }
            }
            .padding(12)
            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .padding(.bottom, 20)
        }
    }

    private func stat(_ label: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(String(value)).font(NocturneType.inter(17))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: What it reads and bills in

    /// One card, two dropdowns. They are the same kind of decision — pick one
    /// from a short list, applies everywhere at once — so they read better as a
    /// pair than as two sections with a paragraph each.
    private var languageAndCurrency: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(Loc.languageAndCurrency).padding(.bottom, 8)

            VStack(alignment: .leading, spacing: 10) {
                LanguageField(
                    label: Loc.languageSection,
                    language: Binding(
                        get: { settings.language },
                        set: { store.setLanguage($0) }
                    )
                )

                CurrencyField(
                    label: Loc.currencySection,
                    currency: Binding(
                        get: { settings.currency },
                        set: { store.setCurrency($0) }
                    )
                )
            }
            .padding(12)
            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .padding(.bottom, 8)

            // The one caveat that cannot be discovered by trying it: the numbers
            // do not move when the symbol does.
            Text(Loc.currencyNote)
                .font(NocturneType.inter(12))
                .foregroundStyle(Nocturne.neutral500)
                .lineSpacing(3)
                .padding(.bottom, 20)
        }
    }

    // MARK: How it looks

    /// Two pills rather than a dropdown. There are exactly two answers, both
    /// worth showing at once, and the choice is the sort somebody makes by
    /// looking at the result — a menu that hides the alternative behind a tap is
    /// the wrong shape for that.
    ///
    /// There is no "System". Following the phone would hand the decision to
    /// whoever set the phone up, who is not always the person behind the counter.
    private var theme: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(Loc.themeSection).padding(.bottom, 8)

            HStack(spacing: 6) {
                ForEach(AppTheme.allCases) { option in
                    ThemePill(
                        title: option.name(Loc),
                        icon: option == .light ? Icon.themeLight : Icon.themeDark,
                        selected: settings.theme == option
                    ) {
                        store.setTheme(option)
                    }
                }
            }
            .padding(.bottom, 20)
        }
    }

    // MARK: Move to another phone

    /// A row, not a section. The subtitle carries the backup state, because the
    /// standing reminder that nothing is backed up has to survive being folded
    /// away behind a tap.
    private var backupRow: some View {
        Button {
            router.showingBackup = true
        } label: {
            HStack(spacing: 11) {
                Glyph(settings.hasBackup ? Icon.backupDone : Icon.backupMissing, size: 20)
                    .foregroundStyle(settings.hasBackup ? Nocturne.accent : Nocturne.accent400)

                VStack(alignment: .leading, spacing: 2) {
                    Text(Loc.moveToAnotherPhone).nocturneText(.rowPrimary)
                    Text(backupState).nocturneText(.meta)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Glyph(Icon.openRow, size: 12)
                    .foregroundStyle(Nocturne.neutral600)
            }
            .padding(13)
            .frame(maxWidth: .infinity)
            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.bottom, 20)
    }

    private var backupState: String {
        guard let exportedAt = settings.lastExportAt else { return Loc.notBackedUpYet }
        return Loc.backedUpOn(Loc.longDate(exportedAt))
    }

    // MARK: Start again

    /// Debug builds only.
    ///
    /// One tap, no confirmation step, and it clears every product, price and
    /// bill on the phone. That is the right shape for a developer resetting to
    /// first-run for the tenth time, and the wrong thing to leave sitting under
    /// Settings on a counter where the only copy of the shop lives. Release
    /// builds get here by deleting the app, which at least asks.
    ///
    /// `StockbookStore.startOver` itself is not conditional — the rule stays
    /// tested in both configurations, it just has no button.
    #if DEBUG
    private var startAgain: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker(Loc.startAgain).padding(.bottom, 8)

            Button(Loc.startOver) {
                store.startOver()
                router.closeOverlays()
                router.showingSettings = false
                router.tab = .today
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
        }
    }
    #endif

    // MARK: Actions

    private func seed() {
        guard !seeded else { return }
        seeded = true
        ownerName = settings.ownerName
    }
}
