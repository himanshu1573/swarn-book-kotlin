package com.swarnabook.billing.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.data.model.Invoice

class HistoryViewModel : ViewModel() {

    private var query: String = ""
    private var startMillis: Long? = null
    private var endMillis: Long? = null

    val results = MediatorLiveData<List<Invoice>>().apply {
        addSource(SampleData.invoicesLive) { value = applyFilters() }
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

    fun delete(id: Long) = SampleData.delete(id)

    private fun applyFilters(): List<Invoice> {
        var list = SampleData.search(query)
        val start = startMillis
        val end = endMillis
        if (start != null && end != null) {
            list = list.filter {
                val millis = runCatching { SampleData.dateFormat.parse(it.date)?.time }.getOrNull()
                millis != null && millis in start..(end + DAY_MS)
            }
        }
        return list
    }

    companion object { private const val DAY_MS = 24L * 60 * 60 * 1000 }
}
