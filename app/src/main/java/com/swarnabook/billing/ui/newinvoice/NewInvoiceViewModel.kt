package com.swarnabook.billing.ui.newinvoice

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarnabook.billing.SwarnaBookApp
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.core.util.DateFormats
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.MakingMode
import kotlinx.coroutines.launch

class NewInvoiceViewModel : ViewModel() {

    private val repo = SwarnaBookApp.repo
    private val settings = SwarnaBookApp.settings

    lateinit var draft: Invoice
        private set

    /**
     * Emits once the draft has been loaded. Building a draft now needs the database
     * (to read an invoice being edited, and to pick the next invoice number), so the
     * screen binds its fields from this observer instead of directly in onViewCreated.
     */
    private val _draftReady = MutableLiveData<Invoice>()
    val draftReady: LiveData<Invoice> = _draftReady

    private var initialized = false

    fun initIfNeeded(invoiceId: Long) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            draft = if (invoiceId > 0) {
                repo.getById(invoiceId)?.copyDeep() ?: newDraft()
            } else {
                newDraft()
            }
            if (draft.items.isEmpty()) draft.items.add(newItem())
            _draftReady.value = draft
        }
    }

    private suspend fun newDraft(): Invoice {
        val s = settings.currentSettings()
        return Invoice(
            invoiceNumber = repo.nextInvoiceNumber(),
            date = DateFormats.today(),
            gstEnabled = s.gstEnabledDefault
        )
    }

    fun newItem(): InvoiceItem {
        val s = settings.currentSettings()
        return InvoiceItem(
            makingMode = MakingMode.PERCENT,
            makingValue = s.defaultMakingPct
        )
    }

    fun recompute() = Calculations.recalculate(draft)

    /** Persists the draft and reports the row id back on the main thread. */
    fun save(onSaved: (Long) -> Unit) {
        viewModelScope.launch { onSaved(repo.upsert(draft)) }
    }

    fun currentGold() = settings.currentGold24()
    fun currentSilver() = settings.currentSilver()
}
