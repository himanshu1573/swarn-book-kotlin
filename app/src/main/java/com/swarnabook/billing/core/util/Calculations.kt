package com.swarnabook.billing.core.util

import kotlin.math.roundToInt

/**
 * Pure, side-effect-free billing math. Kept free of Android dependencies so it is
 * trivially unit-testable. Every screen routes its totals through these functions.
 */
object Calculations {

    const val GST_RATE = 0.03            // 3% total (1.5% CGST + 1.5% SGST)
    const val CGST_RATE = 0.015
    const val SGST_RATE = 0.015

    /** Rate per gram for a given carat, derived from the day's 24K gold and silver rates. */
    fun getRateForCarat(carat: String, gold24Rate: Double, silverRate: Double): Double {
        return when (carat) {
            "24K" -> gold24Rate
            "22K" -> gold24Rate * (22.0 / 24.0)
            "18K" -> gold24Rate * (18.0 / 24.0)
            "14K" -> gold24Rate * (14.0 / 24.0)
            "Silver" -> silverRate
            else -> gold24Rate
        }
    }

    fun calcGoldValue(weight: Double, rate: Double): Double = weight * rate

    /** Making charge as a percentage of the metal value. */
    fun calcMakingByPercent(goldValue: Double, makingPct: Double): Double =
        goldValue * (makingPct / 100.0)

    /** Making charge as a flat rupee amount per gram. */
    fun calcMakingByFlat(weight: Double, flatPerGram: Double): Double = weight * flatPerGram

    fun calcItemTotal(goldValue: Double, making: Double): Double = goldValue + making

    fun calcGst(total: Double): Double = total * GST_RATE

    fun calcCgst(total: Double): Double = total * CGST_RATE
    fun calcSgst(total: Double): Double = total * SGST_RATE

    /** Difference to reach the nearest whole rupee (can be negative). */
    fun calcRoundOff(amount: Double): Double = amount.roundToInt() - amount

    fun calcBalanceDue(grandTotal: Double, paid: Double): Double = grandTotal - paid
}
