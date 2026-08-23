package com.swarnabook.billing.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin client for goldprice.dev.
 *
 * Deliberately built on HttpURLConnection rather than Retrofit/OkHttp: the app makes two
 * GET requests a few times a day, so a networking stack would be dead weight in the APK.
 *
 * IMPORTANT: these are INTERNATIONAL SPOT prices converted to INR. They are NOT the
 * Indian retail counter rate, which additionally carries import duty, GST and the local
 * dealer premium (roughly 15-20% above spot). Apply the shop's premium before billing —
 * see RateRepository.
 */
object GoldPriceApi {

    private const val BASE = "https://api.goldprice.dev/v1"
    private const val TIMEOUT_MS = 12_000
    private const val TROY_OUNCE_IN_GRAMS = 31.1034768

    /** Spot metal rates in INR per gram, before any shop premium. */
    data class SpotRates(
        val gold24PerGram: Double,
        val silverPerGram: Double,
        val computedAt: String?
    )

    /**
     * One carat call (all gold karats in INR/gram) plus one silver spot call.
     * Costs [CALLS_PER_REFRESH] against the monthly quota.
     */
    const val CALLS_PER_REFRESH = 2

    suspend fun fetchRates(apiKey: String): Result<SpotRates> = withContext(Dispatchers.IO) {
        runCatching {
            val carat = JSONObject(get("$BASE/carat?currency=INR&unit=gram", apiKey))
            val gold24 = carat.getString("price_gram_24k").toDouble()

            // Silver comes back per troy ounce, so convert to grams.
            val silverJson = JSONObject(get("$BASE/spot/XAG-INR-SPOT", apiKey))
            val silverPerGram = silverJson.getString("price").toDouble() / TROY_OUNCE_IN_GRAMS

            SpotRates(
                gold24PerGram = gold24,
                silverPerGram = silverPerGram,
                computedAt = carat.optString("timestamp", null)
            )
        }
    }

    private fun get(url: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("goldprice.dev HTTP $code: ${body.take(200)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
