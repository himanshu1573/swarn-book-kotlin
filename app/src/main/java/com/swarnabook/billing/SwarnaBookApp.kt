package com.swarnabook.billing

import android.app.Application
import com.swarnabook.billing.data.InvoiceRepository
import com.swarnabook.billing.data.RateRepository
import com.swarnabook.billing.data.SettingsStore
import com.swarnabook.billing.data.local.AppDatabase
import com.swarnabook.billing.data.remote.RateQuotaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application entry point and the app's tiny service locator.
 *
 * Holds the Room database, the invoice repository and the DataStore-backed settings —
 * all created lazily and shared by every screen. A DI framework would be overkill for
 * three singletons.
 */
class SwarnaBookApp : Application() {

    /** Outlives any screen; used for fire-and-forget settings writes. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: InvoiceRepository by lazy { InvoiceRepository(database.invoiceDao()) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this, applicationScope) }

    /** Live metal rates from goldprice.dev; key comes from local.properties via BuildConfig. */
    val rateRepository: RateRepository by lazy {
        RateRepository(RateQuotaStore(this), settingsStore, BuildConfig.GOLD_API_KEY)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Load settings and rates once so the screens can read them synchronously.
        settingsStore.warmUpBlocking()
    }

    companion object {
        lateinit var instance: SwarnaBookApp
            private set

        val repo: InvoiceRepository get() = instance.repository
        val settings: SettingsStore get() = instance.settingsStore
        val rates: RateRepository get() = instance.rateRepository
    }
}
