package com.swarnabook.billing.core.util

import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.MakingMode
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

    /**
     * Recomputes every derived figure on an invoice from its items and the day's rates.
     * Pure and side-effect-free apart from mutating the invoice passed in, so both the
     * live edit screen and the repository can call it before a save.
     */
    fun recalculate(inv: Invoice) {
        var goldValue = 0.0
        var makingTotal = 0.0
        for (item in inv.items) {
            val gv = calcGoldValue(item.weight, item.ratePerGram)
            val making = when (item.makingMode) {
                MakingMode.PERCENT -> calcMakingByPercent(gv, item.makingValue)
                MakingMode.FLAT_PER_GRAM -> calcMakingByFlat(item.weight, item.makingValue)
            }
            item.makingAmount = making
            item.itemTotal = calcItemTotal(gv, making)
            goldValue += gv
            makingTotal += making
        }
        inv.goldValue = goldValue
        inv.makingTotal = makingTotal

        val taxable = goldValue + makingTotal - inv.oldGoldExchange
        if (inv.gstEnabled) {
            inv.cgst = calcCgst(taxable)
            inv.sgst = calcSgst(taxable)
        } else {
            inv.cgst = 0.0
            inv.sgst = 0.0
        }
        val preRound = taxable + inv.cgst + inv.sgst
        inv.roundOff = calcRoundOff(preRound)
        inv.grandTotal = preRound + inv.roundOff
        inv.balanceDue = calcBalanceDue(inv.grandTotal, inv.amountPaid)
        inv.paid = inv.balanceDue <= 0.0
    }
}
