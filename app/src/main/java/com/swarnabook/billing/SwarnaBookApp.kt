package com.swarnabook.billing

import android.app.Application

/**
 * Application entry point.
 *
 * Frontend phase: only used to hold the in-memory [com.swarnabook.billing.data.SampleData]
 * store so screens share state while clicking through the app.
 *
 * Backend phase: this is where Room (AppDatabase), the InvoiceRepository and the
 * SettingsDataStore will be initialised and exposed.
 */
class SwarnaBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SwarnaBookApp
            private set
    }
}
