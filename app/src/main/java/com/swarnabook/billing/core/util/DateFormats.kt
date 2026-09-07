package com.swarnabook.billing.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The single invoice date format used across screens and stored in the DB.
 *
 * Dates are persisted as dd/MM/yyyy strings (the format a jeweller reads off a bill).
 * [invoiceDate] is only touched from the main thread, matching SimpleDateFormat's
 * thread-safety limits.
 */
object DateFormats {

    val invoiceDate: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("en", "IN"))

    /** Timestamp used in export file names — safe on every filesystem. */
    private val fileStampFormat: SimpleDateFormat = SimpleDateFormat("dd-MM-yyyy_HHmm", Locale("en", "IN"))

    fun today(): String = invoiceDate.format(Date())

    fun fileStamp(): String = fileStampFormat.format(Date())

    fun format(millis: Long): String = invoiceDate.format(Date(millis))

    /** Parses a stored dd/MM/yyyy date back to millis, or null if it is malformed. */
    fun parseOrNull(value: String): Long? =
        runCatching { invoiceDate.parse(value)?.time }.getOrNull()
}
