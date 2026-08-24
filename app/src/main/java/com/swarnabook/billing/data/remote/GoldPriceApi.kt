package com.swarnabook.billing.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metal rate lookup.
 *
 * Deliberately built on HttpURLConnection rather than Retrofit/OkHttp: the app makes a
 * handful of GETs a day, so a networking stack would be dead weight in the APK.
 *
 * Two providers, on purpose:
 *
 *  - GOLD comes from goldprice.dev `/v1/carat`, which returns every karat in INR/gram in
 *    one call. This is the keyed provider and the only one that spends the 1,000/month
 *    free-tier quota (see [RateQuotaStore]).
 *  - SILVER comes from the keyless api.gold-api.com + an FX rate, because goldprice.dev
 *    answers XAG-INR-SPOT with HTTP 403 `plan_gated` on the free tier. These endpoints
 *    need no key and do NOT count against the quota.
 *
 * IMPORTANT: both are INTERNATIONAL SPOT prices, NOT the Uttar Pradesh counter rate,
 * which additionally carries customs duty (15% since May 2026) and the local sarafa
 * premium. RateRepository applies both via IndianRate before anything is billed.
 */
object GoldPriceApi {

    private const val GOLD_BASE = "https://api.goldprice.dev/v1"
    private const val SILVER_URL = "https://api.gold-api.com/price/XAG"
    private const val FX_URL = "https://open.er-api.com/v6/latest/USD"

    private const val TIMEOUT_MS = 12_000
    private const val TROY_OUNCE_IN_GRAMS = 31.1034768

    /** Spot rates in INR per gram, before any shop premium. */
    data class SpotRates(
        val gold24PerGram: Double,
        /** Null when the keyless silver lookup failed; gold is still usable. */
        val silverPerGram: Double?,
        val computedAt: String?
    )

    /**
     * Only the gold call is metered. A 5-hour refresh is ~5 calls/day, ~150/month --
     * well inside the 1,000 allowance.
     */
    const val CALLS_PER_REFRESH = 1

    suspend fun fetchRates(apiKey: String): Result<SpotRates> = withContext(Dispatchers.IO) {
        runCatching {
            // Gold is required: if this throws, the whole refresh fails.
            val carat = JSONObject(get("$GOLD_BASE/carat?currency=INR&unit=gram", apiKey))

            SpotRates(
                gold24PerGram = carat.getString("price_gram_24k").toDouble(),
                // Silver is best-effort. A failure here must not cost the shop its gold
                // rate, so it degrades to null and the typed silver value is kept.
                silverPerGram = runCatching { fetchSilverPerGramInr() }.getOrNull(),
                computedAt = carat.optString("timestamp").takeIf { it.isNotBlank() }
            )
        }
    }

    /** XAG is quoted in USD per troy ounce, so convert to INR per gram. */
    private fun fetchSilverPerGramInr(): Double {
        val silverUsdPerOunce = JSONObject(get(SILVER_URL, null)).getDouble("price")
        val usdToInr = JSONObject(get(FX_URL, null))
            .getJSONObject("rates").getDouble("INR")
        return silverUsdPerOunce * usdToInr / TROY_OUNCE_IN_GRAMS
    }

    /** @param apiKey null for the keyless endpoints. */
    private fun get(url: String, apiKey: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (apiKey != null) setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(160)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
