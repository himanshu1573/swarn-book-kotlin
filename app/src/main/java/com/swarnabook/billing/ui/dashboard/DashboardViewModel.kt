package com.swarnabook.billing.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.data.model.Invoice

class DashboardViewModel : ViewModel() {

    /** Only today's invoices, recomputed whenever the store changes. */
    val todayInvoices: LiveData<List<Invoice>> =
        MediatorLiveData<List<Invoice>>().apply {
            addSource(SampleData.invoicesLive) { value = SampleData.todayInvoices() }
        }

    val gold24Rate: LiveData<Double> = SampleData.gold24Rate
    val silverRate: LiveData<Double> = SampleData.silverRate

    fun updateRates(gold24: Double, silver: Double) = SampleData.setRates(gold24, silver)

    fun currentGold() = SampleData.currentGold24()
    fun currentSilver() = SampleData.currentSilver()
}
