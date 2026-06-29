package com.swarnabook.billing.ui.newinvoice

import androidx.lifecycle.ViewModel
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.MakingMode
import java.util.Date

class NewInvoiceViewModel : ViewModel() {

    lateinit var draft: Invoice
        private set

    private var initialized = false

    /** Prepare the working draft once: either a fresh invoice or a copy for editing. */
    fun initIfNeeded(invoiceId: Long) {
        if (initialized) return
        draft = if (invoiceId > 0) {
            SampleData.getById(invoiceId)?.copyDeep()
                ?: newDraft()
        } else {
            newDraft()
        }
        if (draft.items.isEmpty()) draft.items.add(newItem())
        initialized = true
    }

    private fun newDraft(): Invoice {
        val settings = SampleData.currentSettings()
        return Invoice(
            invoiceNumber = SampleData.nextInvoiceNumber(),
            date = SampleData.dateFormat.format(Date()),
            gstEnabled = settings.gstEnabledDefault
        )
    }

    fun newItem(): InvoiceItem {
        val settings = SampleData.currentSettings()
        return InvoiceItem(
            makingMode = MakingMode.PERCENT,
            makingValue = settings.defaultMakingPct
        )
    }

    fun recompute() = SampleData.recalculate(draft)

    fun save(): Long = SampleData.upsert(draft)

    fun currentGold() = SampleData.currentGold24()
    fun currentSilver() = SampleData.currentSilver()

    private fun Invoice.copyDeep(): Invoice = copy(
        items = items.map { it.copy() }.toMutableList()
    )
}
