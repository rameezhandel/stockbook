import SwiftUI
import UniformTypeIdentifiers

/// The home screen: what sold today, who owes money, the last few bills, and a
/// standing reminder that nothing is backed up.
struct TodayScreen: View {
    @EnvironmentObject private var store: StockbookStore
    @EnvironmentObject private var router: AppRouter
    @Environment(\.currencySymbol) private var symbol

    @State private var exportDocument: BackupFile?
    @State private var isExporting = false

    private var bills: [Bill] { store.bills }
    private var settings: Settings { store.settings }
    private var liveBills: [Bill] { store.liveBills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(
                kicker: Copy.headerDate(),
                title: greeting
            ) {
                Button {
                    router.showingSettings = true
                } label: {
                    Glyph(Icon.settings, size: 18)
                }
                .buttonStyle(.iconOnly)
                .accessibilityLabel("Settings")
            }

            ScrollView {
                VStack(spacing: 0) {
                    statCards
                    owedBanner
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
        return first.isEmpty ? "Today" : "Hello, \(first)"
    }

    // MARK: Stats

    private var statCards: some View {
        HStack(spacing: Metrics.cardGap) {
            StatCard(
                label: "Sold today",
                value: Money.text(liveBills.reduce(0) { $0 + $1.total }, symbol: symbol),
                gradient: true
            )
            StatCard(label: "Bills", value: String(liveBills.count))
        }
        .padding(.bottom, Metrics.cardGap)
    }

    // MARK: Owed

    @ViewBuilder
    private var owedBanner: some View {
        let owed = store.outstanding()
        if !owed.names.isEmpty {
            HStack(spacing: 10) {
                Glyph(Icon.owed, size: 19)
                    .foregroundStyle(Nocturne.accent400)
                Text(owedNote(names: owed.names))
                    .font(NocturneType.inter(12.5))
                    .foregroundStyle(Nocturne.neutral400)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(Money.text(owed.total, symbol: symbol))
                    .font(NocturneType.inter(16))
                    .foregroundStyle(Nocturne.accent400)
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
            .padding(.bottom, 18)
        }
    }

    /// One name reads as a name; several read as a count of **people**, not bills.
    private func owedNote(names: [String]) -> String {
        names.count == 1
            ? "\(names[0]) still owes"
            : "\(Copy.count(names.count, "customer")) still owe"
    }

    // MARK: Recent bills

    private var recentBills: some View {
        VStack(spacing: 0) {
            HStack {
                Kicker("Recent bills")
                Spacer()
                Button("All") { router.tab = .bills }
                    .buttonStyle(.ghost)
            }
            .padding(.bottom, 9)

            if bills.isEmpty {
                EmptyStateBox(
                    message: "No bills yet today.",
                    actionTitle: "Start a bill",
                    action: { router.startBill() }
                )
            } else {
                VStack(spacing: Metrics.rowGap) {
                    ForEach(bills.prefix(3)) { bill in
                        BillRow(bill: bill)
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
                Text(
                    settings.hasBackup
                        ? "Backup written. Copy it somewhere safe — everything lives on this phone only."
                        : "Nothing backed up yet. Everything lives on this phone only."
                )
                .font(NocturneType.inter(12))
                .foregroundStyle(Nocturne.neutral500)
                .lineSpacing(2)
                .frame(maxWidth: .infinity, alignment: .leading)

                Button("Save file") {
                    exportDocument = BackupFile(document: store.makeBackupDocument())
                    isExporting = true
                }
                .buttonStyle(.secondaryCompact)
            }
        }
        .padding(.top, 18)
    }
}
