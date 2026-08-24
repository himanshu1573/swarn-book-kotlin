package com.swarnabook.billing.core.util

import kotlin.math.roundToLong

/**
 * Turns an international spot price into the rate an Uttar Pradesh jeweller quotes at the
 * counter. Pure Kotlin with no Android dependencies, so it is trivially unit-testable
 * (see IndianRateTest).
 *
 * How the Indian counter rate is built from international spot:
 *
 *   spot (INR/g)              London/COMEX price converted at the USD/INR rate
 *   × (1 + import duty)       landed India price — what IBJA-style benchmarks track
 *   × (1 + local premium)     Lucknow sarafa quote: local demand, transport, dealer margin
 *   = counter rate, EX-GST    what the shop writes on the board and enters as ₹/g
 *
 * GST (3%) is deliberately NOT part of the rate. [Calculations.recalculate] adds
 * CGST 1.5% + SGST 1.5% on the bill, so folding it into the rate would tax the customer
 * twice. Published city rates (goodreturns, IBJA) are likewise ex-GST.
 *
 * Calibrated against goldprice.dev and goodreturns.in on 25 Aug 2026:
 *
 *   gold    spot ₹14,270.08/g × 1.15           = ₹16,411/g   Lucknow published ₹16,412/g
 *   silver  spot ₹211.51/g   × 1.15 × 1.07     = ₹260/g      Lucknow published ₹260/g
 *
 * Gold in Lucknow tracks Delhi (₹15/g above Mumbai), so it needs no local premium on top
 * of duty. Physical silver carries a real local premium, hence the separate default.
 */
object IndianRate {

    /**
     * Basic Customs Duty 10% + Agriculture Infrastructure & Development Cess 5% on gold and
     * silver, in force since 13 May 2026 (Budget July 2024 to May 2026 it was 5% + 1% = 6%).
     * Editable in Settings so a future budget change does not need an app update.
     */
    const val DEFAULT_IMPORT_DUTY_PCT = 15.0

    /**
     * Lucknow / UP sarafa premium on gold over the landed price. Zero reproduces the
     * published Lucknow 24K rate to within ₹1/g (Aug 2026); a shop that quotes above the
     * city rate sets its own figure. Can legitimately go negative when demand is weak.
     */
    const val DEFAULT_UP_GOLD_PREMIUM_PCT = 0.0

    /**
     * Same for silver, which trades at a visible physical premium in UP: 7% lands exactly
     * on the ₹260/g Lucknow quote of 25 Aug 2026 (spot ₹211.51 × 1.15 × 1.07 = ₹260.3).
     */
    const val DEFAULT_UP_SILVER_PREMIUM_PCT = 7.0

    /** Indian gold rates are conventionally quoted per 10 grams. */
    const val QUOTE_UNIT_GRAMS = 10.0

    /** Spot plus import duty: the all-India landed price, ex-GST. */
    fun landedPerGram(spotPerGram: Double, importDutyPct: Double): Double =
        spotPerGram * (1.0 + importDutyPct / 100.0)

    /** Landed price plus the local (UP) premium: the counter rate, ex-GST, unrounded. */
    fun counterPerGram(spotPerGram: Double, importDutyPct: Double, localPremiumPct: Double): Double =
        landedPerGram(spotPerGram, importDutyPct) * (1.0 + localPremiumPct / 100.0)

    /** [counterPerGram] rounded the way a sarafa board shows it: whole rupees per gram. */
    fun counterPerGramRounded(spotPerGram: Double, importDutyPct: Double, localPremiumPct: Double): Double =
        counterPerGram(spotPerGram, importDutyPct, localPremiumPct).roundToLong().toDouble()

    fun per10Grams(perGram: Double): Double = perGram * QUOTE_UNIT_GRAMS
}
