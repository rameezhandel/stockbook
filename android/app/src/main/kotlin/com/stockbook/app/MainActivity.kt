package com.stockbook.app

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stockbook.app.design.Motion
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.StockbookTabBar
import com.stockbook.app.design.StockbookTheme
import com.stockbook.app.design.BottomSheet
import com.stockbook.app.feature.bills.BillPdf
import com.stockbook.app.feature.bills.BillSheet
import com.stockbook.app.feature.book.BookScreen
import com.stockbook.app.feature.book.MoveBalanceSheet
import com.stockbook.app.feature.book.PeopleScreen
import com.stockbook.app.feature.book.PurchaseSheet
import com.stockbook.app.feature.customers.CreditNoteSheet
import com.stockbook.app.feature.customers.CustomerEditorSheet
import com.stockbook.app.feature.customers.PaymentReceiptOverlay
import com.stockbook.app.feature.customers.PaymentReceiptSheet
import com.stockbook.app.feature.customers.PaymentReceiptPdf
import com.stockbook.app.feature.customers.RecordPaymentSheet
import com.stockbook.app.feature.customers.PaySupplierSheet
import com.stockbook.app.feature.customers.SupplierEditorSheet
import com.stockbook.app.feature.customers.StatementPdf
import com.stockbook.app.feature.book.ExpenseSheet
import com.stockbook.app.feature.book.PartyScreen
import com.stockbook.app.feature.customers.StatementScreen
import com.stockbook.app.feature.items.AddStockSheet
import com.stockbook.app.feature.items.ItemsScreen
import com.stockbook.app.feature.items.ProductEditorSheet
import com.stockbook.app.feature.sell.Cart
import com.stockbook.app.feature.sell.ReceiptOverlay
import com.stockbook.app.feature.sell.SellScreen
import com.stockbook.app.feature.settings.BackupScreen
import com.stockbook.app.feature.settings.SettingsScreen
import com.stockbook.app.feature.setup.SetupFlow
import com.stockbook.app.feature.today.TodayScreen
import com.stockbook.app.feature.today.DaySheet
import com.stockbook.app.feature.today.EarningsPdf
import com.stockbook.app.feature.today.EarningsSheet
import com.stockbook.app.feature.today.DaySummaryPdf
import com.stockbook.app.feature.today.SummaryPdf
import com.stockbook.app.feature.today.WhoOwesYouSheet
import com.stockbook.app.feature.today.WhoYouOweSheet
import com.stockbook.core.model.AppTheme
import com.stockbook.core.model.Bill
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Timestamps
import com.stockbook.app.photos.PhotoStore
import com.stockbook.core.store.JsonFileRepository
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.AppTab
import com.stockbook.core.text.BillDocument
import com.stockbook.core.text.Dates
import com.stockbook.core.text.DaySummaryDocument
import com.stockbook.core.text.EarningsDocument
import com.stockbook.core.text.PaymentReceiptDocument
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.SummaryDocument
import com.stockbook.core.text.Strings
import java.io.File

/**
 * The one activity.
 *
 * The store is built here, over a file in the app's own directory, and handed
 * down. Nothing above this line knows what a repository is, and nothing below it
 * knows what an Activity is.
 *
 * The keyboard **does** move the layout, via `adjustResize` and `imePadding`.
 * An earlier version copied the iOS rule that the keyboard only overlays — which
 * holds on iOS because the system scrolls a focused field into view by itself.
 * Android does not, so that rule left fields sitting under the keyboard with no
 * way to reach them.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: StockbookStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A repository that cannot open its file is unrecoverable — there is no
        // server to fall back to — so this fails loudly rather than running
        // against a store the owner would type a day's bills into and lose.
        store = StockbookStore(JsonFileRepository(File(filesDir, "stockbook/shop.json")))

        // Pictures the book no longer names, collected on the way in.
        //
        // Every path that strands one already sweeps for itself; this is the net
        // underneath, for the crash that happened between removing a bill and
        // removing its photograph. Cheap — a directory listing — and it runs
        // before the first frame precisely because it must never race the screen
        // that is about to show those pictures.
        PhotoStore(this).sweep(store.photoIdsInUse())

        // The first frame is Compose's, but the window behind it is the system's
        // and comes from `themes.xml`. Repainting it once the shop is loaded stops
        // a light-theme shop from opening on a dark rectangle. The instant before
        // this — the starting window — stays dark, and the only way to fix that
        // would be to read the shop file before the process draws anything.
        Nocturne.use(store.settings.theme)
        window.setBackgroundDrawable(ColorDrawable(Nocturne.bg.toArgb()))

        setContent { StockbookTheme { Shell(store) } }
    }
}

@Composable
private fun Shell(store: StockbookStore) {
    val context = LocalContext.current
    val state by store.state.collectAsStateWithLifecycle()

    // The palette is published to `Nocturne` after the composition that read the
    // setting, not during it. `Nocturne` holds the palette in a snapshot state,
    // and writing snapshot state *inside* composition — in a function that also
    // reads it, as this one does through `Nocturne.bg` below — is how a tree ends
    // up recomposing itself. The first frame does not need this anyway: the
    // activity applies the stored theme before `setContent`.
    SideEffect { Nocturne.use(state.settings.theme) }

    // The system bars are not ours to draw, only to tell: dark icons over a light
    // theme, light icons over a dark one. Without this the status bar keeps the
    // white glyphs `themes.xml` gave it, and they vanish into a white page.
    val view = LocalView.current
    val light = state.settings.theme == AppTheme.LIGHT
    LaunchedEffect(light) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    val router = remember { AppRouter() }
    val cart = remember { Cart() }
    val strings = remember(state.settings.language) { Strings(state.settings.language) }

    // Setup is persisted state, not a route: `setupCompleted` decides which of
    // the two this is, which is also why "Start over" is a data operation.
    if (!state.settings.setupCompleted) {
        SetupFlow(store = store, strings = strings)
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Nocturne.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Lifts the tab screens — the Sell cart's footer above all, which
                // carries the customer field — clear of the keyboard.
                .imePadding()
        ) {
            Crossfade(
                targetState = router.tab,
                animationSpec = Motion.screenSpec,
                label = "tab",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    AppTab.TODAY -> TodayScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings
                    )
                    AppTab.ITEMS -> ItemsScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings
                    )
                    AppTab.SELL -> SellScreen(
                        state = state,
                        store = store,
                        router = router,
                        cart = cart,
                        strings = strings
                    )
                    AppTab.PEOPLE -> PeopleScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings,
                        modifier = Modifier.weight(1f)
                    )
                    AppTab.BOOK -> BookScreen(
                        state = state,
                        store = store,
                        router = router,
                        strings = strings,
                        // The screen builds the page, because it is the screen
                        // that knows which of the four chips is showing. This end
                        // only renders and hands it out, the way it does for every
                        // other document the app makes.
                        onSaveSummary = { document, fileName ->
                            sharePdf(context, SummaryPdf.write(document, context, fileName))
                        },
                        onSaveLedgerBook = {
                            // One document, a page per customer, drawn through
                            // the same routine that draws a single statement —
                            // so a sheet pulled out of this book is the same page
                            // that customer would have been handed, the same
                            // geometry and the same figures, in one ink not two.
                            // A hundred pages printed at once: the band is worth
                            // its toner on the one sheet a customer is handed, and
                            // not a hundred times into a folder, so the whole book
                            // takes the monochrome treatment.
                            //
                            // The contents page is built from the same list the
                            // pages are, which is what stops a line in the index
                            // and the sheet it points at ever stating different
                            // balances.
                            val book = store.ledgerBook()
                            sharePdf(
                                context,
                                StatementPdf.writeLedgerBook(
                                    SummaryDocument.forLedgerBook(book, state.settings, strings),
                                    book.map { StatementDocument.make(it, state.settings, strings) },
                                    context,
                                    strings.ledgerBookFileName(Dates.fileDate(Timestamps.now()))
                                )
                            )
                        }
                    )
                }
            }

            // Hidden only under the product picker, which carries its own bar.
            // The bill form keeps the tab bar: it is a form now rather than a
            // till, and a screen with no way off it is worse than a tall one.
            if (router.tab != AppTab.SELL || !router.pickingProducts) {
                StockbookTabBar(
                    selected = router.tab,
                    onSelect = { router.tab = it },
                    strings = strings
                )
            }
        }

        if (router.showingSettings) {
            BackHandler { router.showingSettings = false }
            SettingsScreen(
                state = state,
                store = store,
                strings = strings,
                onClose = { router.showingSettings = false },
                onOpenBackup = { router.showingBackup = true },
                onStartOver = {
                    store.startOver()
                    cart.clear()
                    router.closeOverlays()
                    router.showingSettings = false
                    router.tab = AppTab.TODAY
                }
            )
        }

        if (router.showingBackup) {
            BackHandler { router.showingBackup = false }
            BackupScreen(
                state = state,
                store = store,
                strings = strings,
                onClose = { router.showingBackup = false }
            )
        }

        // One person, full screen, over whichever half of the book they were
        // opened from. Above the tab bar and below the statement: the statement is
        // reached *from* here and has to sit on top of it.
        router.partyFor?.let { key ->
            BackHandler { router.partyFor = null }
            PartyScreen(
                partyKey = key,
                isSupplier = router.partyIsSupplier,
                state = state,
                store = store,
                router = router,
                strings = strings,
                onClose = { router.partyFor = null }
            )
        }

        // A document rather than a sheet: it runs to a page, and it is the one
        // screen here the owner may turn round and show a customer.
        router.statementFor?.let { key ->
            BackHandler { router.statementFor = null }
            StatementScreen(
                partyKey = key,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onSharePdf = { document ->
                    sharePdf(
                        context,
                        StatementPdf.write(
                            document,
                            context,
                            strings.statementFileName(
                                document.partyName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(),
                                Dates.fileDate(Timestamps.now())
                            )
                        )
                    )
                },
                onEditCreditNote = { note ->
                    store.customer(note.customerKey)?.let { customer ->
                        router.editingCreditNote = note
                        router.creditNoteFor = customer
                    }
                },
                onEditPayment = { id ->
                    // The customer comes with it: the sheet shows what will still
                    // be owed once the correction is saved, which needs the whole
                    // account rather than the one payment.
                    state.payments.firstOrNull { it.id == id }?.let { payment ->
                        store.customer(payment.customerKey)?.let { customer ->
                            router.editingPayment = payment
                            router.paymentFor = customer
                        }
                    }
                },
                onClose = { router.statementFor = null }
            )
        }

        router.supplierStatementFor?.let { key ->
            BackHandler { router.supplierStatementFor = null }
            StatementScreen(
                partyKey = key,
                isSupplier = true,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onSharePdf = { document ->
                    sharePdf(
                        context,
                        StatementPdf.write(
                            document,
                            context,
                            strings.statementFileName(
                                document.partyName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(),
                                Dates.fileDate(Timestamps.now())
                            )
                        )
                    )
                },
                onEditPayment = { id ->
                    state.supplierPayments.firstOrNull { it.id == id }?.let { payment ->
                        store.supplier(payment.supplierKey)?.let { supplier ->
                            router.editingSupplierPayment = payment
                            router.supplierPaymentFor = supplier
                        }
                    }
                },
                onClose = { router.supplierStatementFor = null }
            )
        }

        router.receipt?.let { bill ->
            // Nothing is lost by leaving: the bill is written, and this page only
            // confirms it. The two buttons on it are shortcuts, not the price of
            // getting out.
            BackHandler { router.receipt = null }
            ReceiptOverlay(
                bill = bill,
                state = state,
                strings = strings,
                onSharePdf = { shareBillPdf(context, bill, state, store, strings) },
                onSeeBills = {
                    router.receipt = null
                    router.tab = AppTab.BOOK
                },
                onNextCustomer = {
                    router.receipt = null
                    router.tab = AppTab.SELL
                }
            )
        }

        BottomSheet(
            visible = router.creatingExpense || router.expenseEditor != null,
            onDismiss = { router.closeExpense() }
        ) {
            ExpenseSheet(
                editing = router.expenseEditor,
                state = state,
                store = store,
                strings = strings,
                onClose = { router.closeExpense() }
            )
        }

        // Overlays sit above every screen, which is the whole of this app's
        // navigation — there is no drill-down and no back stack anywhere.
        BottomSheet(
            visible = router.creatingProduct || router.productEditor != null,
            onDismiss = { router.creatingProduct = false; router.productEditor = null }
        ) {
            ProductEditorSheet(
                product = router.productEditor,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onClose = { router.creatingProduct = false; router.productEditor = null },
                onAddStock = { router.openAddStock(it) },
                onDeleted = { }
            )
        }

        // One sheet, three doors into it: from a product, where it can also count
        // that product's shelf; from the Delivery button, where it is a supplier's
        // bill with nothing named on it yet; and from a delivery being corrected,
        // which is the same bill arriving already filled in.
        BottomSheet(
            visible = router.addStock != null || router.recordingDelivery || router.editingPurchase != null,
            onDismiss = { router.closeAddStock() }
        ) {
            AddStockSheet(
                product = router.addStock,
                state = state,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                editing = router.editingPurchase,
                onClose = { router.closeAddStock() }
            )
        }

        // Moving a balance between two accounts that are both real.
        BottomSheet(
            visible = router.moveBalanceFrom != null,
            onDismiss = { router.moveBalanceFrom = null }
        ) {
            router.moveBalanceFrom?.let { key ->
                MoveBalanceSheet(
                    fromKey = key,
                    isSupplier = router.moveBalanceIsSupplier,
                    state = state,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    // Both accounts survive, so the screen behind this stays
                    // open and still correct.
                    onClose = { router.moveBalanceFrom = null }
                )
            }
        }

        // Who is behind the Today banners, and the way to collect from them.
        BottomSheet(
            visible = router.showingDebtors,
            onDismiss = { router.showingDebtors = false }
        ) {
            WhoOwesYouSheet(
                state = state,
                store = store,
                router = router,
                currency = state.settings.currency,
                strings = strings,
                onSave = {
                    sharePdf(
                        context,
                        SummaryPdf.write(
                            SummaryDocument.forReceivable(store.customers(), state.settings, strings),
                            context,
                            strings.receivableFileName(Dates.fileDate(Timestamps.now()))
                        )
                    )
                },
                onClose = { router.showingDebtors = false }
            )
        }

        BottomSheet(
            visible = router.showingCreditors,
            onDismiss = { router.showingCreditors = false }
        ) {
            WhoYouOweSheet(
                state = state,
                store = store,
                router = router,
                currency = state.settings.currency,
                strings = strings,
                onSave = {
                    sharePdf(
                        context,
                        SummaryPdf.write(
                            SummaryDocument.forPayable(store.suppliers(), state.settings, strings),
                            context,
                            strings.payableFileName(Dates.fileDate(Timestamps.now()))
                        )
                    )
                },
                onClose = { router.showingCreditors = false }
            )
        }

        // What the trading left the shop with, from the Sold card on Home.
        BottomSheet(
            visible = router.earningsFor != null,
            onDismiss = { router.earningsFor = null }
        ) {
            router.earningsFor?.let { period ->
                EarningsSheet(
                    period = period,
                    state = state,
                    store = store,
                    strings = strings,
                    onSave = {
                        sharePdf(
                            context,
                            EarningsPdf.write(
                                EarningsDocument.make(
                                    store.earningsIn(period),
                                    period.range(),
                                    state.settings,
                                    strings
                                ),
                                context,
                                strings.earningsFileName(Dates.fileDate(Timestamps.now()))
                            )
                        )
                    },
                    onClose = { router.earningsFor = null }
                )
            }
        }

        // One day of the shop, from the date at the top of Home.
        BottomSheet(
            visible = router.dayInView != null,
            onDismiss = { router.dayInView = null }
        ) {
            router.dayInView?.let { day ->
                DaySheet(
                    day = day,
                    state = state,
                    store = store,
                    strings = strings,
                    onDay = { router.dayInView = it },
                    onSave = {
                        sharePdf(
                            context,
                            DaySummaryPdf.write(
                                DaySummaryDocument.forDay(store.dayBook(day), state.settings, strings),
                                context,
                                // Named for the day it covers, not for today: a
                                // folder of these is read by their file names.
                                strings.dayFileName(Dates.fileDate(day))
                            )
                        )
                    },
                    onClose = { router.dayInView = null }
                )
            }
        }

        BottomSheet(
            visible = router.purchaseDetail != null,
            onDismiss = { router.purchaseDetail = null }
        ) {
            router.purchaseDetail?.let { purchase ->
                PurchaseSheet(
                    purchase = purchase,
                    state = state,
                    store = store,
                    strings = strings,
                    onEdit = { router.editPurchase(it) },
                    onClose = { router.purchaseDetail = null }
                )
            }
        }

        BottomSheet(
            visible = router.creatingCustomer || router.customerEditor != null,
            onDismiss = { router.closeCustomerEditor() }
        ) {
            CustomerEditorSheet(
                existing = router.customerEditor,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onClose = { router.closeCustomerEditor() }
            )
        }

        BottomSheet(
            visible = router.paymentFor != null,
            onDismiss = { router.paymentFor = null; router.editingPayment = null }
        ) {
            router.paymentFor?.let { customer ->
                RecordPaymentSheet(
                    // Re-read from the store so the sheet's "what will be left"
                    // line is not a stale copy taken when it opened.
                    customer = store.customer(customer.key) ?: customer,
                    state = state,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    editing = router.editingPayment,
                    onReceipt = { slip, justSaved -> router.showReceipt(slip, justSaved) },
                    onClose = { router.paymentFor = null; router.editingPayment = null }
                )
            }
        }

        // The payment sheet's sibling: the same act with no money in it.
        BottomSheet(
            visible = router.creditNoteFor != null,
            onDismiss = { router.creditNoteFor = null; router.editingCreditNote = null }
        ) {
            router.creditNoteFor?.let { customer ->
                CreditNoteSheet(
                    customer = store.customer(customer.key) ?: customer,
                    state = state,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    editing = router.editingCreditNote,
                    onClose = { router.creditNoteFor = null; router.editingCreditNote = null }
                )
            }
        }

        BottomSheet(
            visible = router.creatingSupplier || router.supplierEditor != null,
            onDismiss = { router.closeSupplierEditor() }
        ) {
            SupplierEditorSheet(
                existing = router.supplierEditor,
                store = store,
                currency = state.settings.currency,
                strings = strings,
                onClose = { router.closeSupplierEditor() }
            )
        }

        BottomSheet(
            visible = router.supplierPaymentFor != null,
            onDismiss = { router.supplierPaymentFor = null; router.editingSupplierPayment = null }
        ) {
            router.supplierPaymentFor?.let { supplier ->
                PaySupplierSheet(
                    // Re-read from the store so the sheet's "what will be left"
                    // line is not a stale copy taken when it opened.
                    supplier = store.supplier(supplier.key) ?: supplier,
                    state = state,
                    store = store,
                    currency = state.settings.currency,
                    strings = strings,
                    editing = router.editingSupplierPayment,
                    onReceipt = { slip, justSaved -> router.showReceipt(slip, justSaved) },
                    onClose = { router.supplierPaymentFor = null; router.editingSupplierPayment = null }
                )
            }
        }

        BottomSheet(
            visible = router.billDetail != null,
            onDismiss = { router.billDetail = null }
        ) {
            router.billDetail?.let { bill ->
                BillSheet(
                    bill = bill,
                    state = state,
                    store = store,
                    strings = strings,
                    onSharePdf = { live -> shareBillPdf(context, live, state, store, strings) },
                    onSharePhoto = { id -> sharePhoto(context, PhotoStore(context).file(id)) },
                    onEdit = { existing ->
                        // Filled here rather than inside the router, which is the
                        // only place that holds both. Whatever was half-typed on
                        // the Sell form goes with it — a correction is one bill at
                        // a time, and stashing the other one would be a second
                        // draft nothing on screen mentions.
                        cart.fill(existing, state.products, state.settings.currency)
                        router.editBill(existing)
                    },
                    onClose = { router.billDetail = null }
                )
            }
        }

        // **Two shapes for one page.** A payment just taken takes the whole
        // screen, the way a saved bill does: it is a confirmation, and the owner
        // turns it round to the customer. The same slip looked up afterwards is a
        // sheet over whatever they were reading, the way an opened bill is —
        // taking the screen for it read as something having happened, and left
        // nothing behind it to go back to.
        router.paymentReceipt?.let { receipt ->
            val document = PaymentReceiptDocument.make(
                receipt,
                state.settings,
                strings
            )
            val share = {
                sharePdf(
                    context,
                    PaymentReceiptPdf.write(
                        document,
                        context,
                        strings.receiptFileName(
                            document.partyName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(),
                            Dates.fileDate(receipt.at)
                        )
                    )
                )
            }

            if (router.paymentReceiptIsNew) {
                // Nothing is lost by leaving: the payment is written, and this
                // page only states it. The same reasoning the bill's receipt
                // settled on.
                BackHandler { router.paymentReceipt = null }
                PaymentReceiptOverlay(
                    document = document,
                    strings = strings,
                    onSharePdf = share,
                    onClose = { router.paymentReceipt = null }
                )
            } else {
                // `BottomSheet` registers its own back handler, so there is none
                // here — a second would take the press first and close the sheet
                // twice over.
                BottomSheet(
                    visible = true,
                    onDismiss = { router.paymentReceipt = null }
                ) {
                    PaymentReceiptSheet(
                        document = document,
                        strings = strings,
                        onSharePdf = share,
                        onClose = { router.paymentReceipt = null }
                    )
                }
            }
        }
    }
}

/**
 * Renders one bill and hands the file out.
 *
 * Both ways a bill is shared go through here — the confirmation straight after
 * saving, and the sheet a bill is opened on later — so the copy a customer is
 * given at the counter and the copy they ask for a week afterwards are the same
 * document.
 *
 * The customer is looked up rather than taken from the bill: `Bill.who` is the
 * name typed at the counter and is all a bill carries, while the place and phone
 * that go under it live on the roster.
 */
private fun shareBillPdf(
    context: android.content.Context,
    bill: Bill,
    state: com.stockbook.core.model.ShopState,
    store: StockbookStore,
    strings: Strings
) {
    val document = BillDocument.make(
        bill,
        state.settings,
        strings,
        customer = store.customer(Customer.key(bill.who))
    )
    sharePdf(
        context,
        BillPdf.write(
            document,
            context,
            strings.billFileName(
                document.reference.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase(),
                Dates.fileDate(bill.createdAt)
            )
        )
    )
}

/**
 * Hands a file to whatever the owner picks — a message, a note, a printer app.
 *
 * `ACTION_SEND` is a hand-off, not a network call: this app opens no socket and
 * has no permission to. What happens next belongs to the app the owner chose.
 *
 * There was a plain-text twin of this until every screen that shared went to
 * sharing the document instead.
 *
 * The URI comes from the app's own `FileProvider` and is granted read access for
 * the life of the chooser — a path would be unreadable to whatever app the owner
 * picks, and making the file readable any other way would need a storage
 * permission this app does not have.
 */
private fun sharePdf(context: android.content.Context, file: java.io.File) =
    shareFile(context, file, "application/pdf")

/**
 * A photograph of a bill, on its way to whoever asked for it.
 *
 * The same hand-off as the PDF's, and it works for the same reason: the photo
 * directory is named in `shared_files.xml`, so a `content://` URI can be granted
 * for one file without the app holding any storage permission.
 */
private fun sharePhoto(context: android.content.Context, file: java.io.File) =
    shareFile(context, file, "image/jpeg")

private fun shareFile(context: android.content.Context, file: java.io.File, type: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}
