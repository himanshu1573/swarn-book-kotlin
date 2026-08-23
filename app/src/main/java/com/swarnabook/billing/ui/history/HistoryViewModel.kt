package com.swarnabook.billing.ui.history

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarnabook.billing.SwarnaBookApp
import com.swarnabook.billing.core.util.DateFormats
import com.swarnabook.billing.data.model.Invoice
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    private val repo = SwarnaBookApp.repo

    private var query: String = ""
    private var startMillis: Long? = null
    private var endMillis: Long? = null

    /** Latest full list from the database; filters are applied on top of it. */
    private var allInvoices: List<Invoice> = emptyList()

    val results = MediatorLiveData<List<Invoice>>().apply {
        addSource(repo.invoicesLive) { list ->
            allInvoices = list
            value = applyFilters()
        }
    }

    fun setQuery(q: String) {
        query = q
        results.value = applyFilters()
    }

    fun setDateRange(start: Long?, end: Long?) {
        startMillis = start
        endMillis = end
        results.value = applyFilters()
    }

    fun clearDateRange() = setDateRange(null, null)

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    private fun applyFilters(): List<Invoice> {
        var list = allInvoices
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.customerName.lowercase().contains(q) ||
                    it.invoiceNumber.lowercase().contains(q)
            }
        }
        val start = startMillis
        val end = endMillis
        if (start != null && end != null) {
            list = list.filter {
                val millis = DateFormats.parseOrNull(it.date)
                millis != null && millis in start..(end + DAY_MS)
            }
        }
        return list
    }

    companion object { private const val DAY_MS = 24L * 60 * 60 * 1000 }
}
