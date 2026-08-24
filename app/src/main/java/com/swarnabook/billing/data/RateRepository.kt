package com.swarnabook.billing.data

import com.swarnabook.billing.core.util.IndianRate
import com.swarnabook.billing.data.remote.GoldPriceApi
import com.swarnabook.billing.data.remote.RateQuotaStore
import kotlin.math.round

/**
 * Turns goldprice.dev spot prices into the Uttar Pradesh counter rate the shop actually
 * bills at, while staying inside the free plan's 1,000 calls/month (see [RateQuotaStore]).
 *
 * The API returns international spot converted to INR. [IndianRate] adds the customs
 * duty and the UP local premium from Settings to reach the ex-GST counter rate (GST is
 * applied on the bill, not here). On 25 Aug 2026 that reproduced the published Lucknow
 * 24K rate to within ₹1/g. The rate always stays hand-editable — a stale or wrong fetch
 * must never silently mis-price a real bill.
 */
class RateRepository(
    private val quota: RateQuotaStore,
    private val settings: SettingsStore,
    private val apiKey: String
) {

    sealed class Outcome {
        /** Rates were fetched and written to the rate card. */
        data class Updated(
            val gold24: Double,
            val silver: Double,
            val silverLive: Boolean,
            val spotGold24: Double,
            val importDutyPct: Double,
            val premiumPct: Double,
            val monthRemaining: Int
        ) : Outcome()

        /** Nothing was fetched, on purpose — quota or interval limit. */
        data class Skipped(val reason: String) : Outcome()

        /** A call was attempted and failed (offline, bad key, server error). */
        data class Failed(val reason: String) : Outcome()
    }

    /**
     * @param manual true when the shop tapped Fetch (shorter cooldown, and the result is
     *   reported on screen); false for the automatic refresh on app open.
     */
    suspend fun refresh(manual: Boolean, now: Long = System.currentTimeMillis()): Outcome {
        if (apiKey.isBlank()) return Outcome.Skipped(RateQuotaStore.Block.NoKey.message)

        quota.blockedReason(GoldPriceApi.CALLS_PER_REFRESH, manual, now)
            ?.let { return Outcome.Skipped(it.message) }

        val result = GoldPriceApi.fetchRates(apiKey)
        // Recorded regardless of outcome: a failed request still counted against the plan.
        quota.record(GoldPriceApi.CALLS_PER_REFRESH, now)

        val spot = result.getOrElse {
            return Outcome.Failed(it.message ?: "Could not reach the rate service")
        }

        val s = settings.currentSettings()
        // Whole rupees per gram, the way a sarafa board quotes it.
        val gold = IndianRate.counterPerGramRounded(spot.gold24PerGram, s.importDutyPct, s.ratePremiumPct)
        // Silver comes from a keyless provider that can fail on its own; falling back to
        // the typed value keeps a silver outage from wiping the shop's rate.
        val silver = spot.silverPerGram
            ?.let { IndianRate.counterPerGramRounded(it, s.importDutyPct, s.silverPremiumPct) }
            ?: settings.currentSilver()

        settings.setRates(gold, silver)

        return Outcome.Updated(
            gold24 = gold,
            silver = silver,
            silverLive = spot.silverPerGram != null,
            spotGold24 = round2(spot.gold24PerGram),
            importDutyPct = s.importDutyPct,
            premiumPct = s.ratePremiumPct,
            monthRemaining = quota.snapshot(now).monthRemaining
        )
    }

    suspend fun quotaSnapshot(now: Long = System.currentTimeMillis()): RateQuotaStore.Snapshot =
        quota.snapshot(now)

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
