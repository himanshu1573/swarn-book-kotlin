package com.swarnabook.billing.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Indian-style currency + weight formatting (₹1,00,000.00 grouping). */
object CurrencyFormat {

    private val rupeeFormat = DecimalFormat("#,##,##0.00", DecimalFormatSymbols(Locale("en", "IN")))
    private val rupeeWhole = DecimalFormat("#,##,##0", DecimalFormatSymbols(Locale("en", "IN")))
    private val gramFormat = DecimalFormat("0.###")

    /** e.g. ₹1,23,456.00 */
    fun rupees(amount: Double): String = "₹" + rupeeFormat.format(amount)

    /** e.g. ₹1,23,456 (no paise) */
    fun rupeesWhole(amount: Double): String = "₹" + rupeeWhole.format(amount)

    /** e.g. 8.5 g */
    fun grams(weight: Double): String = gramFormat.format(weight) + " g"
}
