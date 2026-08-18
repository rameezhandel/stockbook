package com.stockbook.core.store

import com.stockbook.core.model.Bill
import com.stockbook.core.model.CustomerRecord
import com.stockbook.core.model.Payment
import com.stockbook.core.model.Product
import com.stockbook.core.model.Purchase
import com.stockbook.core.model.Settings
import com.stockbook.core.model.SupplierPayment
import com.stockbook.core.model.SupplierRecord
import com.stockbook.core.model.ShopState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The whole shop as one JSON file, written atomically.
 *
 * That suits what this app actually is — 50–300 products, one user, no queries,
 * no reporting. The incremental writes of [StockbookRepository] are honoured by
 * rewriting the file, which at this size costs less than the machinery to avoid
 * it would.
 *
 * Atomic on purpose: a half-written file is a lost shop, and this app has no
 * server to recover one from.
 */
class JsonFileRepository(private val file: File) : StockbookRepository {

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cache: ShopState? = null

    override fun loadAll(): ShopState {
        cache?.let { return it }
        val state = if (!file.exists()) {
            ShopState.EMPTY
        } else {
            val text = file.readText()
            if (text.isBlank()) ShopState.EMPTY
            else json.decodeFromString<ShopState>(text)
        }
        cache = state
        return state
    }

    override fun upsert(product: Product) = mutate { state ->
        val index = state.products.indexOfFirst { it.uid == product.uid }
        val products = state.products.toMutableList()
        if (index >= 0) products[index] = product else products.add(product)
        state.copy(products = products)
    }

    override fun delete(productUid: String) = mutate { state ->
        state.copy(products = state.products.filterNot { it.uid == productUid })
    }

    override fun append(bill: Bill) = mutate { state ->
        state.copy(bills = state.bills + bill)
    }

    override fun update(bill: Bill) = mutate { state ->
        state.copy(bills = state.bills.map { if (it.number == bill.number) bill else it })
    }

    override fun deleteBill(number: Int) = mutate { state ->
        state.copy(bills = state.bills.filterNot { it.number == number })
    }

    override fun upsert(customer: CustomerRecord) = mutate { state ->
        val index = state.customers.indexOfFirst { it.key == customer.key }
        if (index >= 0) {
            state.copy(customers = state.customers.toMutableList().also { it[index] = customer })
        } else {
            state.copy(customers = state.customers + customer)
        }
    }

    override fun deleteCustomer(key: String) = mutate { state ->
        state.copy(customers = state.customers.filterNot { it.key == key })
    }

    override fun append(payment: Payment) = mutate { state ->
        state.copy(payments = state.payments + payment)
    }

    override fun upsert(supplier: SupplierRecord) = mutate { state ->
        val index = state.suppliers.indexOfFirst { it.key == supplier.key }
        if (index >= 0) {
            state.copy(suppliers = state.suppliers.toMutableList().also { it[index] = supplier })
        } else {
            state.copy(suppliers = state.suppliers + supplier)
        }
    }

    override fun deleteSupplier(key: String) = mutate { state ->
        state.copy(suppliers = state.suppliers.filterNot { it.key == key })
    }

    override fun append(purchase: Purchase) = mutate { state ->
        state.copy(purchases = state.purchases + purchase)
    }

    override fun update(purchase: Purchase) = mutate { state ->
        val index = state.purchases.indexOfFirst { it.id == purchase.id }
        if (index < 0) state
        else state.copy(purchases = state.purchases.toMutableList().also { it[index] = purchase })
    }

    override fun deletePurchase(id: String) = mutate { state ->
        state.copy(purchases = state.purchases.filterNot { it.id == id })
    }

    override fun append(payment: SupplierPayment) = mutate { state ->
        state.copy(supplierPayments = state.supplierPayments + payment)
    }

    override fun deleteSupplierPayment(id: String) = mutate { state ->
        state.copy(supplierPayments = state.supplierPayments.filterNot { it.id == id })
    }

    override fun deletePayment(id: String) = mutate { state ->
        state.copy(payments = state.payments.filterNot { it.id == id })
    }

    override fun save(settings: Settings) = mutate { state ->
        state.copy(settings = settings)
    }

    override fun replaceAll(state: ShopState) {
        cache = state
        write(state)
    }

    private fun mutate(change: (ShopState) -> ShopState) {
        val updated = change(loadAll())
        cache = updated
        write(updated)
    }

    private fun write(state: ShopState) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.writing")
        temporary.writeText(json.encodeToString(state))
        if (!temporary.renameTo(file)) {
            // Rename across the same directory should not fail, but a copy is
            // better than losing the shop to an assumption.
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }
}

/** For tests, and for anything that wants a shop that does not outlive it. */
class InMemoryRepository(initial: ShopState = ShopState.EMPTY) : StockbookRepository {
    private var state = initial

    override fun loadAll(): ShopState = state
    override fun upsert(product: Product) {
        val index = state.products.indexOfFirst { it.uid == product.uid }
        val products = state.products.toMutableList()
        if (index >= 0) products[index] = product else products.add(product)
        state = state.copy(products = products)
    }
    override fun delete(productUid: String) {
        state = state.copy(products = state.products.filterNot { it.uid == productUid })
    }
    override fun append(bill: Bill) { state = state.copy(bills = state.bills + bill) }
    override fun update(bill: Bill) {
        state = state.copy(bills = state.bills.map { if (it.number == bill.number) bill else it })
    }
    override fun deleteBill(number: Int) {
        state = state.copy(bills = state.bills.filterNot { it.number == number })
    }
    override fun upsert(customer: CustomerRecord) {
        val index = state.customers.indexOfFirst { it.key == customer.key }
        state = if (index >= 0) {
            state.copy(customers = state.customers.toMutableList().also { it[index] = customer })
        } else {
            state.copy(customers = state.customers + customer)
        }
    }

    override fun deleteCustomer(key: String) {
        state = state.copy(customers = state.customers.filterNot { it.key == key })
    }

    override fun append(payment: Payment) { state = state.copy(payments = state.payments + payment) }

    override fun upsert(supplier: SupplierRecord) {
        val index = state.suppliers.indexOfFirst { it.key == supplier.key }
        state = if (index >= 0) {
            state.copy(suppliers = state.suppliers.toMutableList().also { it[index] = supplier })
        } else {
            state.copy(suppliers = state.suppliers + supplier)
        }
    }

    override fun deleteSupplier(key: String) {
        state = state.copy(suppliers = state.suppliers.filterNot { it.key == key })
    }

    override fun append(purchase: Purchase) { state = state.copy(purchases = state.purchases + purchase) }

    override fun update(purchase: Purchase) {
        val index = state.purchases.indexOfFirst { it.id == purchase.id }
        if (index >= 0) {
            state = state.copy(purchases = state.purchases.toMutableList().also { it[index] = purchase })
        }
    }
    override fun deletePurchase(id: String) {
        state = state.copy(purchases = state.purchases.filterNot { it.id == id })
    }

    override fun append(payment: SupplierPayment) {
        state = state.copy(supplierPayments = state.supplierPayments + payment)
    }

    override fun deleteSupplierPayment(id: String) {
        state = state.copy(supplierPayments = state.supplierPayments.filterNot { it.id == id })
    }

    override fun deletePayment(id: String) {
        state = state.copy(payments = state.payments.filterNot { it.id == id })
    }

    override fun save(settings: Settings) { state = state.copy(settings = settings) }
    override fun replaceAll(state: ShopState) { this.state = state }
}
