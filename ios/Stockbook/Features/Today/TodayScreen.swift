import SwiftUI
import UniformTypeIdentifiers

/// The home screen: what sold today, who owes money, the last few bills, and a
/// standing reminder that nothing is backed up.
struct TodayScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    @State private var exportDocument: BackupFile?
    @State private var isExporting = false

    private var bills: [Bill] { store.bills }
    private var settings: Settings { store.settings }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(
                kicker: Loc.headerDate(.now),
                title: greeting
            ) {
                Button {
                    router.showingSettings = true
                } label: {
                    Glyph(Icon.settings, size: 18)
                }
                .buttonStyle(.iconOnly)
                .accessibilityLabel(Loc.settings)
            }

            ScrollView {
                // Both sides read once here rather than inside the banners: each
                // is a walk over every bill or delivery, and the first banner's
                // spacing depends on whether the second one is there.
                let owed = store.outstanding()
                let payable = store.payable()

                VStack(spacing: 0) {
                    statCards
                    owedBanner(owed, followedByPayable: !payable.names.isEmpty)
                    payableBanner(payable)
                    recentBills
                    backupNudge
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.top, 4)
                .padding(.bottom, 18)
            }
        }
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: exportDocument?.document.suggestedFilename
        ) { result in
            // Only a real write counts as a backup — a cancelled save sheet must
            // not quiet the nudge.
            if case .success = result {
                store.markExported()
            }
        }
    }

    private var greeting: String {
        let first = settings.ownerName.firstName
        return first.isEmpty ? Loc.today : Loc.greeting(first)
    }

    // MARK: Stats

    private var statCards: some View {
        HStack(spacing: Metrics.cardGap) {
            StatCard(
                label: Loc.soldToday,
                value: Money.text(bills.reduce(0) { $0 + $1.total }, in: currency),
                gradient: true
            )
            StatCard(label: Loc.billsStat, value: String(bills.count))
        }
        .padding(.bottom, Metrics.cardGap)
    }

    // MARK: Owed

    @ViewBuilder
    private func owedBanner(_ owed: (names: [String], total: Double), followedByPayable: Bool) -> some View {
        if !owed.names.isEmpty {
            banner(
                note: owedNote(names: owed.names),
                amount: owed.total,
                icon: Icon.owed,
                // The banner is where the debt gets noticed; the list behind it is
                // where it gets collected. Without this tap the only route to
                // Ahmed's cash was to remember to go and find Ahmed in the Book.
                action: { router.showingDebtors = true }
            )
            // Tightened when the second banner follows, so the pair reads as one
            // block of money rather than two unrelated notices.
            .padding(.bottom, followedByPayable ? 6 : 18)
        }
    }

    /// The other direction, and only when there is one.
    ///
    /// A shop owner's own bills matter as much as the ones owed to them, but a
    /// banner saying "you owe nothing" every day teaches people to stop reading
    /// banners.
    @ViewBuilder
    private func payableBanner(_ payable: (names: [String], total: Double)) -> some View {
        if !payable.names.isEmpty {
            banner(
                note: payable.names.count == 1
                    ? Loc.youOweOne(payable.names[0])
                    : Loc.youOweMany(payable.names.count),
                amount: payable.total,
                icon: Icon.items,
                action: { router.showingCreditors = true }
            )
            .padding(.bottom, 18)
        }
    }

    /// One banner body, both directions — what a debt looks like does not change
    /// with who owes it.
    private func banner(note: String, amount: Double, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Glyph(icon, size: 19)
                    .foregroundStyle(Nocturne.accent400)
                Text(note)
                    .font(NocturneType.inter(12.5))
                    .foregroundStyle(Nocturne.neutral400)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(Money.text(amount, in: currency))
                    .font(NocturneType.inter(16))
                    .foregroundStyle(Nocturne.accent400)
                    .rollingNumber(amount)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 12)
            .background(Nocturne.surface)
            .clipShape(.rect(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: Metrics.cardRadius, topTrailingRadius: Metrics.cardRadius))
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(Nocturne.accent)
                    .frame(width: 2)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// One name reads as a name; several read as a count of **people**, not bills.
    private func owedNote(names: [String]) -> String {
        names.count == 1
            ? Loc.stillOwes(oneName: names[0])
            : Loc.stillOwe(customerCount: names.count)
    }

    // MARK: Recent bills

    private var recentBills: some View {
        VStack(spacing: 0) {
            HStack {
                Kicker(Loc.recentBills)
                Spacer()
                Button(Loc.all) { router.tab = .book }
                    .buttonStyle(.ghost)
            }
            .padding(.bottom, 9)

            if bills.isEmpty {
                EmptyStateBox(
                    message: Loc.noBillsToday,
                    actionTitle: Loc.startABill,
                    action: { router.startBill() }
                )
            } else {
                VStack(spacing: Metrics.rowGap) {
                    ForEach(Array(bills.prefix(3))) { bill in
                        Button {
                            router.openBill(bill)
                        } label: {
                            BillRow(bill: bill)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: Backup

    private var backupNudge: some View {
        DashedBox(padding: EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)) {
            HStack(spacing: 11) {
                Glyph(settings.hasBackup ? Icon.backupDone : Icon.backupMissing, size: 20)
                    .foregroundStyle(settings.hasBackup ? Nocturne.accent : Nocturne.neutral500)
                Text(settings.hasBackup ? Loc.backupWrittenNote : Loc.backupMissingNote)
                    .font(NocturneType.inter(12))
                    .foregroundStyle(Nocturne.neutral500)
                    .lineSpacing(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button(Loc.saveFile) {
                    exportDocument = BackupFile(document: store.makeBackupDocument())
                    isExporting = true
                }
                .buttonStyle(.secondaryCompact)
            }
        }
        .padding(.top, 18)
    }
}
