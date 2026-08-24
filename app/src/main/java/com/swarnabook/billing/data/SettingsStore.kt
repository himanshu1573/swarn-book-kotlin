package com.swarnabook.billing.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.swarnabook.billing.data.model.AppTheme
import com.swarnabook.billing.data.model.ShopSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "swarnabook_settings")

/**
 * Persistent shop settings and the day's metal rates, backed by DataStore.
 *
 * Several call sites (building a new invoice draft, the WhatsApp message, the rate chips)
 * need these values synchronously on the main thread, so the current values are also held
 * in memory and [warmUpBlocking] loads them once at app start. Writes update the cache
 * immediately and persist in the background.
 */
class SettingsStore(context: Context, private val scope: CoroutineScope) {

    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val SHOP_NAME = stringPreferencesKey("shop_name")
        val SHOP_ADDRESS = stringPreferencesKey("shop_address")
        val SHOP_PHONE = stringPreferencesKey("shop_phone")
        val GSTIN = stringPreferencesKey("gstin")
        val DEFAULT_MAKING_PCT = doublePreferencesKey("default_making_pct")
        val IMPORT_DUTY_PCT = doublePreferencesKey("import_duty_pct")
        val RATE_PREMIUM_PCT = doublePreferencesKey("rate_premium_pct")
        val SILVER_PREMIUM_PCT = doublePreferencesKey("silver_premium_pct")
        val GST_DEFAULT = booleanPreferencesKey("gst_enabled_default")
        val THEME = stringPreferencesKey("theme")
        val GOLD_24_RATE = doublePreferencesKey("gold_24_rate")
        val SILVER_RATE = doublePreferencesKey("silver_rate")
    }

    private fun Preferences.toSettings(): ShopSettings {
        val fallback = ShopSettings()
        return ShopSettings(
            shopName = this[Keys.SHOP_NAME] ?: fallback.shopName,
            shopAddress = this[Keys.SHOP_ADDRESS] ?: fallback.shopAddress,
            shopPhone = this[Keys.SHOP_PHONE] ?: fallback.shopPhone,
            gstin = this[Keys.GSTIN] ?: fallback.gstin,
            defaultMakingPct = this[Keys.DEFAULT_MAKING_PCT] ?: fallback.defaultMakingPct,
            importDutyPct = this[Keys.IMPORT_DUTY_PCT] ?: fallback.importDutyPct,
            ratePremiumPct = this[Keys.RATE_PREMIUM_PCT] ?: fallback.ratePremiumPct,
            silverPremiumPct = this[Keys.SILVER_PREMIUM_PCT] ?: fallback.silverPremiumPct,
            gstEnabledDefault = this[Keys.GST_DEFAULT] ?: fallback.gstEnabledDefault,
            theme = this[Keys.THEME]?.let { name ->
                runCatching { AppTheme.valueOf(name) }.getOrDefault(fallback.theme)
            } ?: fallback.theme
        )
    }

    private val settingsFlow: Flow<ShopSettings> = store.data.map { it.toSettings() }

    val settingsLive: LiveData<ShopSettings> = settingsFlow.asLiveData()

    val gold24RateLive: LiveData<Double> =
        store.data.map { it[Keys.GOLD_24_RATE] ?: DEFAULT_GOLD_24 }.asLiveData()

    val silverRateLive: LiveData<Double> =
        store.data.map { it[Keys.SILVER_RATE] ?: DEFAULT_SILVER }.asLiveData()

    // ----- Synchronous in-memory view, kept in step with the persisted values -----

    @Volatile
    private var cachedSettings: ShopSettings = ShopSettings()

    @Volatile
    private var cachedGold24: Double = DEFAULT_GOLD_24

    @Volatile
    private var cachedSilver: Double = DEFAULT_SILVER

    fun currentSettings(): ShopSettings = cachedSettings
    fun currentGold24(): Double = cachedGold24
    fun currentSilver(): Double = cachedSilver

    /**
     * One-shot blocking read at app start. It is a single small preferences file, and
     * every synchronous caller below would otherwise race the first async emission.
     */
    fun warmUpBlocking() {
        runCatching {
            runBlocking {
                val prefs = store.data.first()
                cachedSettings = prefs.toSettings()
                cachedGold24 = prefs[Keys.GOLD_24_RATE] ?: DEFAULT_GOLD_24
                cachedSilver = prefs[Keys.SILVER_RATE] ?: DEFAULT_SILVER
            }
        }
    }

    fun saveSettings(settings: ShopSettings) {
        cachedSettings = settings
        scope.launch {
            store.edit { prefs ->
                prefs[Keys.SHOP_NAME] = settings.shopName
                prefs[Keys.SHOP_ADDRESS] = settings.shopAddress
                prefs[Keys.SHOP_PHONE] = settings.shopPhone
                prefs[Keys.GSTIN] = settings.gstin
                prefs[Keys.DEFAULT_MAKING_PCT] = settings.defaultMakingPct
                prefs[Keys.IMPORT_DUTY_PCT] = settings.importDutyPct
                prefs[Keys.RATE_PREMIUM_PCT] = settings.ratePremiumPct
                prefs[Keys.SILVER_PREMIUM_PCT] = settings.silverPremiumPct
                prefs[Keys.GST_DEFAULT] = settings.gstEnabledDefault
                prefs[Keys.THEME] = settings.theme.name
            }
        }
    }

    fun setRates(gold24: Double, silver: Double) {
        cachedGold24 = gold24
        cachedSilver = silver
        scope.launch {
            store.edit { prefs ->
                prefs[Keys.GOLD_24_RATE] = gold24
                prefs[Keys.SILVER_RATE] = silver
            }
        }
    }

    companion object {
        /**
         * Only used until the shop enters its own rate or the first live fetch lands.
         * Lucknow counter rates, ex-GST, 25 Aug 2026 (goodreturns.in).
         */
        const val DEFAULT_GOLD_24 = 16412.0
        const val DEFAULT_SILVER = 260.0
    }
}
