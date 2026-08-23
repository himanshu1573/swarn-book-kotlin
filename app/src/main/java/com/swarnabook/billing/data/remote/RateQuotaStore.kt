package com.swarnabook.billing.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.rateQuotaDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "rate_quota")

/**
 * Spend ledger for the goldprice.dev free tier (1,000 calls/month).
 *
 * Budget, given one refresh costs [GoldPriceApi.CALLS_PER_REFRESH] = 2 calls:
 *
 *   auto-refresh every 3h  ->  8 refreshes/day  ->  16 calls/day  ->  ~496 calls/month
 *
 * That is roughly half the allowance, leaving room for manual refreshes. Three
 * independent limits are enforced so no combination of app restarts, manual taps or a
 * long month can overrun the plan:
 *
 *   1. [MONTHLY_CAP]  — hard stop at 960, keeping 40 calls in reserve
 *   2. [DAILY_CAP]    — 30 calls/day (1000 / 31 = 32, rounded down for safety)
 *   3. a minimum interval between calls — 3h automatic, 15min for a manual tap
 *
 * Counters persist across restarts and reset when the calendar day/month rolls over.
 */
class RateQuotaStore(context: Context) {

    private val store = context.applicationContext.rateQuotaDataStore

    private object Keys {
        val MONTH = stringPreferencesKey("month_key")
        val MONTH_USED = intPreferencesKey("month_used")
        val DAY = stringPreferencesKey("day_key")
        val DAY_USED = intPreferencesKey("day_used")
        val LAST_FETCH = longPreferencesKey("last_fetch_millis")
    }

    data class Snapshot(
        val monthUsed: Int,
        val dayUsed: Int,
        val lastFetchMillis: Long
    ) {
        val monthRemaining: Int get() = (MONTHLY_CAP - monthUsed).coerceAtLeast(0)
        val dayRemaining: Int get() = (DAILY_CAP - dayUsed).coerceAtLeast(0)
    }

    /** Why a refresh was refused, or null when it is allowed. */
    sealed class Block(val message: String) {
        object MonthExhausted : Block("Monthly rate-lookup limit reached. Enter the rate by hand.")
        object DayExhausted : Block("Today's rate lookups are used up. Enter the rate by hand.")
        class TooSoon(minutes: Long) : Block("Rate was just updated. Try again in $minutes min.")
        object NoKey : Block("No goldprice.dev API key configured.")
    }

    private fun monthKey(now: Long) = MONTH_FMT.format(Date(now))
    private fun dayKey(now: Long) = DAY_FMT.format(Date(now))

    /** Reads counters, zeroing them if the stored day/month has rolled over. */
    suspend fun snapshot(now: Long): Snapshot {
        val prefs = store.data.first()
        val sameMonth = prefs[Keys.MONTH] == monthKey(now)
        val sameDay = prefs[Keys.DAY] == dayKey(now)
        return Snapshot(
            monthUsed = if (sameMonth) prefs[Keys.MONTH_USED] ?: 0 else 0,
            dayUsed = if (sameDay) prefs[Keys.DAY_USED] ?: 0 else 0,
            lastFetchMillis = prefs[Keys.LAST_FETCH] ?: 0L
        )
    }

    /**
     * Checks all three limits before any network call is made.
     * @param calls how many API calls the refresh will cost.
     */
    suspend fun blockedReason(calls: Int, manual: Boolean, now: Long): Block? {
        val snap = snapshot(now)
        if (snap.monthUsed + calls > MONTHLY_CAP) return Block.MonthExhausted
        if (snap.dayUsed + calls > DAILY_CAP) return Block.DayExhausted

        val minInterval = if (manual) MANUAL_MIN_INTERVAL_MS else AUTO_INTERVAL_MS
        val elapsed = now - snap.lastFetchMillis
        if (snap.lastFetchMillis > 0L && elapsed < minInterval) {
            // Only a manual tap deserves an explanation; an automatic refresh just skips.
            return Block.TooSoon(((minInterval - elapsed) / 60_000L) + 1)
        }
        return null
    }

    /** Records spent calls. Called even on a failed request — the quota is consumed either way. */
    suspend fun record(calls: Int, now: Long) {
        store.edit { prefs ->
            val sameMonth = prefs[Keys.MONTH] == monthKey(now)
            val sameDay = prefs[Keys.DAY] == dayKey(now)
            prefs[Keys.MONTH] = monthKey(now)
            prefs[Keys.DAY] = dayKey(now)
            prefs[Keys.MONTH_USED] = (if (sameMonth) prefs[Keys.MONTH_USED] ?: 0 else 0) + calls
            prefs[Keys.DAY_USED] = (if (sameDay) prefs[Keys.DAY_USED] ?: 0 else 0) + calls
            prefs[Keys.LAST_FETCH] = now
        }
    }

    companion object {
        /** Plan allows 1,000/month; stop at 960 so a bad day can never overshoot. */
        const val MONTHLY_CAP = 960

        /** 1000 / 31 days = 32.2; rounded down to 30. */
        const val DAILY_CAP = 30

        const val AUTO_INTERVAL_MS = 3L * 60 * 60 * 1000      // 3 hours
        const val MANUAL_MIN_INTERVAL_MS = 15L * 60 * 1000    // 15 minutes

        private val MONTH_FMT = SimpleDateFormat("yyyy-MM", Locale.US)
        private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
