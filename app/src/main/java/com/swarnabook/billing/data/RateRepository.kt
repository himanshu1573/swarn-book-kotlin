package com.swarnabook.billing.data

import com.swarnabook.billing.data.remote.GoldPriceApi
import com.swarnabook.billing.data.remote.RateQuotaStore
import kotlin.math.round

/**
 * Turns goldprice.dev spot prices into the rate the shop actually bills at, while
 * staying inside the free plan's 1,000 calls/month (see [RateQuotaStore]).
 *
 * The API returns international spot converted to INR. Indian counter rates run roughly
 * 15-20% higher once import duty, GST and the local dealer premium are added, so the
 * fetched figure is multiplied by the shop's own premium % from Settings before it is
 * written to the rate card. The rate always stays hand-editable — a stale or wrong
 * fetch must never silently mis-price a real bill.
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
            val spotGold24: Double,
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

        val premiumPct = settings.currentSettings().ratePremiumPct
        val factor = 1.0 + (premiumPct / 100.0)
        val gold = round2(spot.gold24PerGram * factor)
        val silver = round2(spot.silverPerGram * factor)

        settings.setRates(gold, silver)

        return Outcome.Updated(
            gold24 = gold,
            silver = silver,
            spotGold24 = round2(spot.gold24PerGram),
            premiumPct = premiumPct,
            monthRemaining = quota.snapshot(now).monthRemaining
        )
    }

    suspend fun quotaSnapshot(now: Long = System.currentTimeMillis()): RateQuotaStore.Snapshot =
        quota.snapshot(now)

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
