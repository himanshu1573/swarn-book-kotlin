package com.swarnabook.billing.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarnabook.billing.SwarnaBookApp
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.core.util.DateFormats
import com.swarnabook.billing.data.RateRepository
import com.swarnabook.billing.data.model.Invoice
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val repo = SwarnaBookApp.repo
    private val settings = SwarnaBookApp.settings
    private val rates = SwarnaBookApp.rates

    /** Only today's invoices, refiltered whenever the database changes. */
    val todayInvoices: LiveData<List<Invoice>> =
        MediatorLiveData<List<Invoice>>().apply {
            addSource(repo.invoicesLive) { all ->
                val today = DateFormats.today()
                value = all.filter { it.date == today }
            }
        }

    val gold24Rate: LiveData<Double> = settings.gold24RateLive
    val silverRate: LiveData<Double> = settings.silverRateLive

    private val _rateStatus = MutableLiveData("")
    /** One-line note under the rate card: last fetch result or why one was skipped. */
    val rateStatus: LiveData<String> = _rateStatus

    private val _fetching = MutableLiveData(false)
    val fetching: LiveData<Boolean> = _fetching

    init {
        // Automatic refresh on app open. Silently does nothing if the 3-hour interval
        // has not elapsed or the quota is spent, so it costs at most 8 refreshes a day.
        refreshRates(manual = false)
    }

    fun refreshRates(manual: Boolean) {
        if (_fetching.value == true) return
        _fetching.value = true
        viewModelScope.launch {
            when (val outcome = rates.refresh(manual)) {
                is RateRepository.Outcome.Updated -> {
                    val premiumNote = if (outcome.premiumPct > 0) {
                        " (spot ${CurrencyFormat.rupeesWhole(outcome.spotGold24)} + ${trim(outcome.premiumPct)}%)"
                    } else {
                        " — spot only, set a premium % in Settings"
                    }
                    _rateStatus.value =
                        "Updated$premiumNote · ${outcome.monthRemaining} lookups left this month"
                }
                // A skipped automatic refresh is normal; only say so when asked directly.
                is RateRepository.Outcome.Skipped ->
                    if (manual) _rateStatus.value = outcome.reason
                is RateRepository.Outcome.Failed ->
                    _rateStatus.value = "Rate lookup failed — enter it by hand."
            }
            _fetching.value = false
        }
    }

    fun updateRates(gold24: Double, silver: Double) = settings.setRates(gold24, silver)

    fun currentGold() = settings.currentGold24()
    fun currentSilver() = settings.currentSilver()

    private fun trim(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
