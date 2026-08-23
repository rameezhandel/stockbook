import Testing
import Foundation
@testable import Stockbook

/// The guarantees that make a second language safe to ship.
///
/// None of this can be checked by looking at a screen — a missing translation
/// looks like an English word on a Kannada screen, which is exactly what a
/// reviewer who does not read Kannada will skim past.
@Suite("Localization")
struct LocalizationTests {

    private let english = Strings(language: .english)
    private let kannada = Strings(language: .kannada)

    /// Every plain string in the table, so a new one added in English only
    /// fails here rather than shipping.
    private static let everyString: [KeyPath<Strings, String>] = [
        \.done, \.save, \.cancel, \.add,
        \.all, \.share, \.back, \.continueAction,
        \.next, \.startABill, \.addAProduct, \.today,
        \.settings, \.soldInPeriod, \.receivableStat, \.payableStat, \.billsStat, \.runningLow, \.nothingRunningLow,
        \.itemsTitle, \.search, \.nothingAddedYet, \.shelfEmpty, \.itemsRecordDelivery,
        \.billsTitle, \.noBillsEver, \.editBill, \.saveChanges, \.removeBill, \.removeBillNote,
        \.allCustomers, \.customerLabel, \.transactions, \.pendingPayment,
        \.nothingPending,
        \.expensesTitle, \.addAnExpense, \.newExpense, \.editExpense, \.expenseWhatFor,
        \.expenseWhatForHint, \.expenseSpentOn, \.noExpensesYet, \.expenseInPeriod,
        \.saveExpense, \.enterWhatItWasFor, \.removeExpense, \.removeExpenseNote,
        \.expensesArePrivate,
        \.customersTitle, \.addACustomer, \.noCustomersYet, \.noSuppliersYet, \.nobodyMatches,
        \.newCustomer, \.editCustomer,
        \.customerPhone, \.customerPlace, \.optionalField, \.saveCustomer,
        \.removeFromCustomers, \.tapAgainToRemove, \.noBillsYet,
        \.openingBalanceField, \.openingBalanceNote, \.chooseFromTheList, \.enterCustomerNameFirst, \.removeCustomerNote,
        \.creditNoteLabel, \.creditNotes, \.issueACreditNote, \.editCreditNote,
        \.creditNoteNo, \.creditNoteNoHint, \.amountCredited, \.creditedOn,
        \.creditReason, \.creditReasonExample, \.saveCreditNote, \.removeCreditNote,
        \.enterCreditNoteNumber, \.changeTheCreditNoteNo, \.itemsReturned,
        \.addReturnedItems, \.noCreditNotesYet, \.creditNoteNotAPayment,
        \.recordAPayment, \.correctAPayment, \.amountReceived, \.receivedOn, \.paymentNote,
        \.paymentNoteExample, \.savePayment, \.paymentLabel, \.deleteThisPayment,
        \.enterAnAmount, \.paymentNotAgainstOneBill,
        \.paymentNoField, \.paymentNoHint, \.enterPaymentNumber, \.changeThePaymentNo,
        \.addPhoto, \.takePhoto, \.chooseFromPhotos, \.removePhoto, \.billPhotos,
        \.photoLabel, \.photoNotOnThisPhone, \.couldNotReadThatPhoto, \.noCameraOnThisPhone,
        \.photoStorage, \.removeAllPhotos, \.removeAllPhotosNote, \.photosTravelWithTheBook,
        \.statement, \.thisMonth, \.lastMonth, \.thisYear,
        \.chooseDates, \.fromDate, \.toDate, \.openingBalance,
        \.billedInPeriod, \.receivedInPeriod, \.closingBalance, \.nothingInThisPeriod,
        \.settledUp,
        \.nextCustomers, \.whoDoYouSellTo, \.customersSetupBody,
        \.customerNameExample, \.noCustomersYetKicker,
        \.newBill, \.cartEmpty, \.searchAProduct,
        \.selectProduct, \.addAnotherItem, \.oneFewer, \.oneMore,
        \.noProductsYet, \.reset, \.customerName, \.paidInFull,
        \.partPayment, \.paidNow, \.total, \.balance,
        \.discountField, \.discountHint, \.subtotalLabel,
        \.saveBill, \.enterCustomerName, \.billSaved, \.billDetailTitle,
        \.seeBills, \.nextCustomer, \.paidInFullCash,
        \.newProduct, \.editProduct, \.productName, \.productNameExample,
        \.inStock, \.openingStock, \.openingStockNote, \.buyingPrice, \.sellingPrice, \.addStock,
        \.removeThisProduct, \.setPriceAboveCost,
        \.supplier, \.whoDeliveredIt, \.howMany, \.paidPerPiece,
        \.recordPurchase, \.welcomeToStockbook, \.welcomeBody, \.yourName,
        \.businessOwnerName, \.yourShelves, \.whatDoYouStock, \.stockNamesBody,
        \.commonHardwareLines, \.nothingAddedYetKicker, \.nextStockAndPrices, \.stockAndPrices,
        \.stockAndPricesBody, \.youPay, \.youSell, \.openTheShop,
        \.allSet, \.thisPhone, \.businessOwner, \.productsStat,
        \.customersStat, \.languageSection, \.languageAndCurrency, \.notBackedUpYet,
        \.themeSection, \.themeDark, \.themeLight,
        \.invoiceNoField, \.invoiceNoHint, \.billDate,
        \.billNote,
        \.changeTheInvoiceNo, \.enterBillNumber,
        \.amountField, \.addItems, \.removeItems, \.supplierBillTitle,
        \.setCount, \.setCountNote, \.takePayment, \.makePayment,
        \.bookTitle, \.salesSide, \.purchasesSide, \.recordDelivery,
        \.whoDoYouBuyFrom, \.suppliersSetupBody, \.noSuppliersYetKicker, \.supplierOpeningNote,
        \.whichProductArrived, \.noDeliveriesYet, \.deliveryDetail,
        \.suppliersTitle, \.allSuppliers, \.addASupplier, \.newSupplier,
        \.editSupplier, \.saveSupplier, \.removeFromSuppliers, \.removeSupplierNote, \.supplierNameExample,
        \.noPurchasesYet, \.purchaseLabel, \.paidOn, \.paymentNotAgainstOnePurchase,
        \.boughtFromThem, \.youOwe,
        \.owedToSuppliers, \.nothingOwedOut, \.chooseSupplierFromTheList,
        \.removeSupplierBill, \.removeSupplierBillNote, \.purchasedInPeriod, \.paidOutInPeriod,
        \.shopAddress, \.shopAddressHint, \.shopAddressNote,
        \.accountStatementFor, \.accountActivity, \.balanceDue,
        \.columnInvoiceReceipt, \.columnBillReceipt, \.columnInvoiceAmount,
        \.columnBillAmount, \.columnReceivedAmount, \.columnPaidAmount, \.columnBalance,
        \.receivableSummary, \.payableSummary, \.columnCustomer,
        \.totalReceivable, \.totalPayable, \.nothingReceivable, \.nothingPayable,
        \.savedList, \.sharePdf,
        \.currencySection, \.currencyNote, \.setupCurrencyNote, \.moveToAnotherPhone,
        \.moveToAnotherPhoneNote, \.exportEverything, \.exportNoteFirstTime, \.exportNoteAfterBackup,
        \.writeAFreshFile, \.createBackupFile, \.importABackupFile, \.chooseAFile,
        \.replaceEverything, \.importNoteIdle, \.importNoteDone, \.startAgain,
        \.startOver, \.restoreFromBackup, \.useThisBackup,
    ]

    @Test("Every string is written in both languages")
    func nothingUntranslated() {
        for keyPath in Self.everyString {
            let en = english[keyPath: keyPath]
            let kn = kannada[keyPath: keyPath]
            #expect(!en.isEmpty)
            #expect(!kn.isEmpty)
            // Identical text in both columns is the signature of a line that was
            // copied and never translated.
            #expect(en != kn, "untranslated: “\(en)”")
        }
    }

    @Test("Kannada is written in Kannada")
    func kannadaScript() {
        let kannadaRange = UnicodeScalar(0x0C80)!...UnicodeScalar(0x0CFF)!
        for keyPath in Self.everyString {
            let text = kannada[keyPath: keyPath]
            // Hoisted out of #expect: the macro rewrites its expression to
            // capture sub-values, and a `rethrows` call does not survive that
            // rewrite as non-throwing.
            let hasKannadaLetters = text.unicodeScalars.contains { kannadaRange.contains($0) }
            #expect(hasKannadaLetters, "no Kannada letters in “\(text)”")
        }
    }

    @Test("Counts read correctly at zero, one and many")
    func counts() {
        #expect(english.products(1) == "1 product")
        #expect(english.products(0) == "0 products")
        #expect(english.bills(4) == "4 bills")
        #expect(english.pieces(1) == "1 piece")
        #expect(english.stillNeedPrices(1).hasPrefix("1 item still needs"))
        #expect(english.stillNeedPrices(3).hasPrefix("3 items still need"))

        // Kannada does not inflect for number here; what matters is that the
        // count reaches the sentence at all.
        for n in [0, 1, 7] {
            #expect(kannada.products(n).contains("\(n)"))
            #expect(kannada.bills(n).contains("\(n)"))
            #expect(kannada.pieces(n).contains("\(n)"))
        }
    }

    @Test("Values interpolated into a sentence survive it")
    func interpolation() {
        for strings in [english, kannada] {
            #expect(strings.greeting("Khalid").contains("Khalid"))
            #expect(strings.stillOwes(oneName: "Ahmed").contains("Ahmed"))
            // The banner names the biggest debtor even when several owe — the
            // whole point of it, and the part a count-only sentence dropped.
            #expect(strings.stillOweWithOthers("Ahmed", others: 2).contains("Ahmed"))
            #expect(strings.stillOweWithOthers("Ahmed", others: 2).contains("2"))
            #expect(strings.youOweWithOthers("Al Faisal", others: 1).contains("Al Faisal"))
            #expect(strings.onlyInStock(3).contains("3"))
            #expect(strings.usualPriceNote("SAR 20").contains("SAR 20"))
            #expect(strings.youMakeAPiece("SAR 30").contains("SAR 30"))
            #expect(strings.billNumber(7).contains("7"))
            #expect(strings.billWhen(date: "28 July 2026", time: "09:41").contains("09:41"))
            #expect(strings.billedTo("Ahmed").contains("Ahmed"))
            #expect(strings.quantityAtPrice(quantity: 2, price: "SAR 95").contains("SAR 95"))
            let replace = strings.replaceWarning(productCount: 8, billCount: 4)
            #expect(replace.contains("8"))
            #expect(replace.contains("4"))
        }
    }

    @Test("Backup errors are said in both languages")
    func backupErrors() {
        for error in [BackupError.unreadable, .notStockbookData, .newerVersion(found: 99)] {
            #expect(!english.backupError(error).isEmpty)
            #expect(english.backupError(error) != kannada.backupError(error))
        }
        #expect(english.backupError(.newerVersion(found: 99)).contains("99"))
        #expect(kannada.backupError(.newerVersion(found: 99)).contains("99"))
    }

    /// The filename has to sort and parse the same on every phone, so it stays
    /// out of the language entirely.
    @Test("The backup filename is never localised")
    func filenameIsStable() {
        let date = Date(timeIntervalSince1970: 1_785_000_000)
        let document = BackupDocument(
            exportedAt: date,
            ownerName: "Khalid",
            currencyCode: "SAR",
            products: [],
            bills: []
        )
        let isASCII = document.suggestedFilename.allSatisfy(\.isASCII)
        #expect(document.suggestedFilename == "stockbook-\(Copy.fileDate(date)).zip")
        #expect(isASCII)
    }

    @Test("Dates follow the language")
    func dates() {
        let date = Date(timeIntervalSince1970: 1_785_000_000)
        #expect(english.longDate(date) != kannada.longDate(date))
        // The time on a bill is deliberately identical in both.
        #expect(english.time(date) == kannada.time(date))
    }
}

@Suite("Language setting")
@MainActor
struct LanguageSettingTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("A settings blob with no language in it opens in English")
    func settingsWithoutLanguageDecode() throws {
        // Not a file from the past — nothing has shipped — but the shape the
        // *next* field will make of this one, which is why `Settings` decodes by
        // hand and why this test outlives the field that prompted it.
        let json = Data("""
        {
          "ownerName": "Khalid",
          "currencyCode": "SAR",
          "lowStockAt": 40,
          "setupCompleted": true,
          "nextBillNumber": 3
        }
        """.utf8)

        let settings = try JSONDecoder().decode(Settings.self, from: json)
        #expect(settings.language == .english)
        #expect(settings.ownerName == "Khalid")
        #expect(settings.nextBillNumber == 3)
        #expect(settings.currencyCode == "SAR")
    }

    @Test("Choosing a language persists it and takes effect immediately")
    func setLanguage() throws {
        let repository = InMemoryRepository()
        let store = StockbookStore(repository: repository)

        store.setLanguage(.kannada)

        let onDisk = try repository.loadAll().settings.language

        #expect(store.settings.language == .kannada)
        #expect(L10n.language == .kannada)
        #expect(onDisk == .kannada)

        store.setLanguage(.english)
        #expect(L10n.language == .english)
    }

    @Test("Starting over keeps the language")
    func startOverKeepsLanguage() {
        let store = makeStore()
        store.setLanguage(.kannada)
        store.setOwnerName("Khalid")

        store.startOver()

        #expect(store.settings.ownerName.isEmpty)
        #expect(store.settings.setupCompleted == false)
        #expect(store.settings.language == .kannada, "setup must not arrive in a language the owner cannot read")
    }

    @Test("Importing another phone's shop does not import its language")
    func importKeepsThisPhonesLanguage() {
        let store = makeStore()
        store.setLanguage(.kannada)

        store.replaceEverything(with: BackupDocument(
            exportedAt: .now,
            ownerName: "Someone Else",
            currencyCode: "SAR",
            products: [],
            bills: []
        ))

        #expect(store.settings.ownerName == "Someone Else")
        #expect(store.settings.language == .kannada)
    }
}
