package com.swarnabook.billing.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.data.model.Carat
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.MakingMode
import com.swarnabook.billing.data.model.ShopSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * In-memory data store for the FRONTEND phase so all screens share live state
 * while you click through the app. It mimics the API the real Repository will
 * expose (LiveData lists, suspend-free CRUD) so swapping in Room later is a
 * drop-in replacement — screens won't change.
 */
object SampleData {

    val dateFormat: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("en", "IN"))

    // ----- Today's metal rates (editable on the dashboard) -----
    private val _gold24Rate = MutableLiveData(7245.0)
    val gold24Rate: LiveData<Double> = _gold24Rate

    private val _silverRate = MutableLiveData(92.0)
    val silverRate: LiveData<Double> = _silverRate

    fun setRates(gold24: Double, silver: Double) {
        _gold24Rate.value = gold24
        _silverRate.value = silver
    }

    fun currentGold24(): Double = _gold24Rate.value ?: 0.0
    fun currentSilver(): Double = _silverRate.value ?: 0.0

    // ----- Shop settings -----
    private val _settings = MutableLiveData(ShopSettings())
    val settings: LiveData<ShopSettings> = _settings
    fun currentSettings(): ShopSettings = _settings.value ?: ShopSettings()
    fun saveSettings(s: ShopSettings) { _settings.value = s }

    // ----- Invoices -----
    private val invoices = mutableListOf<Invoice>()
    private val _invoicesLive = MutableLiveData<List<Invoice>>(emptyList())
    val invoicesLive: LiveData<List<Invoice>> = _invoicesLive

    private var nextId = 1L
    private var seqCounter = 0

    init {
        seedSamples()
    }

    fun nextInvoiceNumber(): String = "INV-%03d".format(seqCounter + 1)

    fun getById(id: Long): Invoice? = invoices.find { it.id == id }

    fun todayInvoices(): List<Invoice> {
        val today = dateFormat.format(Date())
        return invoices.filter { it.date == today }.sortedByDescending { it.createdAt }
    }

    fun allInvoices(): List<Invoice> = invoices.sortedByDescending { it.createdAt }

    fun search(query: String): List<Invoice> {
        if (query.isBlank()) return allInvoices()
        val q = query.trim().lowercase()
        return allInvoices().filter {
            it.customerName.lowercase().contains(q) || it.invoiceNumber.lowercase().contains(q)
        }
    }

    /** Insert (id == 0) or update an existing invoice; returns its id. */
    fun upsert(invoice: Invoice): Long {
        recalculate(invoice)
        if (invoice.id == 0L) {
            invoice.id = nextId++
            seqCounter++
            if (invoice.createdAt == 0L) invoice.createdAt = System.currentTimeMillis()
            invoices.add(invoice)
        } else {
            val idx = invoices.indexOfFirst { it.id == invoice.id }
            if (idx >= 0) invoices[idx] = invoice else invoices.add(invoice)
        }
        publish()
        return invoice.id
    }

    fun markPaid(id: Long) {
        getById(id)?.let {
            it.amountPaid = it.grandTotal
            it.balanceDue = 0.0
            it.paid = true
            publish()
        }
    }

    fun delete(id: Long) {
        invoices.removeAll { it.id == id }
        publish()
    }

    private fun publish() {
        _invoicesLive.value = allInvoices()
    }

    /** Recomputes every derived figure on an invoice from its items + rates. */
    fun recalculate(inv: Invoice) {
        var goldValue = 0.0
        var makingTotal = 0.0
        for (item in inv.items) {
            val gv = Calculations.calcGoldValue(item.weight, item.ratePerGram)
            val making = when (item.makingMode) {
                MakingMode.PERCENT -> Calculations.calcMakingByPercent(gv, item.makingValue)
                MakingMode.FLAT_PER_GRAM -> Calculations.calcMakingByFlat(item.weight, item.makingValue)
            }
            item.makingAmount = making
            item.itemTotal = Calculations.calcItemTotal(gv, making)
            goldValue += gv
            makingTotal += making
        }
        inv.goldValue = goldValue
        inv.makingTotal = makingTotal

        val taxable = goldValue + makingTotal - inv.oldGoldExchange
        if (inv.gstEnabled) {
            inv.cgst = Calculations.calcCgst(taxable)
            inv.sgst = Calculations.calcSgst(taxable)
        } else {
            inv.cgst = 0.0
            inv.sgst = 0.0
        }
        val preRound = taxable + inv.cgst + inv.sgst
        inv.roundOff = Calculations.calcRoundOff(preRound)
        inv.grandTotal = preRound + inv.roundOff
        inv.balanceDue = Calculations.calcBalanceDue(inv.grandTotal, inv.amountPaid)
        inv.paid = inv.balanceDue <= 0.0
    }

    private fun seedSamples() {
        val cal = Calendar.getInstance()
        val today = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val older = dateFormat.format(cal.time)

        val inv1 = Invoice(
            invoiceNumber = "INV-001",
            customerName = "Anita Sharma",
            customerPhone = "9876500011",
            date = today,
            gstEnabled = true,
            amountPaid = 50000.0,
            notes = "Advance received in cash.",
            items = mutableListOf(
                InvoiceItem(itemName = "Gold Necklace", carat = Carat.K22, weight = 22.5,
                    ratePerGram = Calculations.getRateForCarat(Carat.K22, 7245.0, 92.0),
                    makingMode = MakingMode.PERCENT, makingValue = 12.0),
                InvoiceItem(itemName = "Gold Bangles (pair)", carat = Carat.K22, weight = 18.0,
                    ratePerGram = Calculations.getRateForCarat(Carat.K22, 7245.0, 92.0),
                    makingMode = MakingMode.PERCENT, makingValue = 10.0)
            )
        )
        val inv2 = Invoice(
            invoiceNumber = "INV-002",
            customerName = "Rakesh Verma",
            customerPhone = "9123456780",
            date = today,
            gstEnabled = true,
            oldGoldExchange = 15000.0,
            amountPaid = 0.0,
            notes = "Old gold 5g exchanged.",
            items = mutableListOf(
                InvoiceItem(itemName = "Gold Ring", carat = Carat.K18, weight = 6.2,
                    ratePerGram = Calculations.getRateForCarat(Carat.K18, 7245.0, 92.0),
                    makingMode = MakingMode.FLAT_PER_GRAM, makingValue = 350.0)
            )
        )
        val inv3 = Invoice(
            invoiceNumber = "INV-003",
            customerName = "Meena Gupta",
            customerPhone = "9988776655",
            date = older,
            gstEnabled = false,
            amountPaid = 8500.0,
            items = mutableListOf(
                InvoiceItem(itemName = "Silver Anklet", carat = Carat.SILVER, weight = 85.0,
                    ratePerGram = Calculations.getRateForCarat(Carat.SILVER, 7245.0, 92.0),
                    makingMode = MakingMode.PERCENT, makingValue = 15.0)
            )
        )
        listOf(inv1, inv2, inv3).forEach { upsert(it) }
    }
}
