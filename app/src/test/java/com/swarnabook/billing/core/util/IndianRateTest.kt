package com.swarnabook.billing.core.util

import com.swarnabook.billing.data.model.ShopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every figure below is real, captured on 25 Aug 2026:
 *  - spot: goldprice.dev `/v1/carat?currency=INR&unit=gram` and api.gold-api.com XAG
 *    at USD/INR 95.79
 *  - counter rates: goodreturns.in Lucknow pages (ex-GST)
 */
class IndianRateTest {

    private val spotGold24 = 14_270.08       // goldprice.dev INR/g
    private val spotSilver = 211.51          // 68.678 USD/oz × 95.7937 / 31.1035
    private val lucknowGold24 = 16_412.0     // goodreturns Lucknow 24K ₹/g
    private val lucknowSilver = 260.0        // goodreturns Lucknow silver ₹/g

    @Test
    fun `default settings reproduce the published Lucknow gold rate within one rupee`() {
        val s = ShopSettings()
        val counter = IndianRate.counterPerGramRounded(spotGold24, s.importDutyPct, s.ratePremiumPct)

        // Regression: before this change the default premium was 0 on top of raw spot, so
        // the shop was silently billing ~13% below the UP counter rate.
        assertTrue("default rate must not be raw spot", counter > spotGold24 * 1.10)
        assertEquals(lucknowGold24, counter, 1.0)
    }

    @Test
    fun `default settings reproduce the published Lucknow silver rate`() {
        val s = ShopSettings()
        val counter = IndianRate.counterPerGramRounded(spotSilver, s.importDutyPct, s.silverPremiumPct)
        assertEquals(lucknowSilver, counter, 0.0)
    }

    @Test
    fun `counter rate is ex-GST -- GST belongs on the bill, not in the rate`() {
        // 23 Aug 2026: spot ₹14,161.93 × 1.15 = landed ₹16,286; the ≈ ₹16,780/g "Indian
        // retail" figure in docs/PROJECT_STATUS.md is that landed price × 1.03 GST.
        val landed = IndianRate.landedPerGram(14_161.93, IndianRate.DEFAULT_IMPORT_DUTY_PCT)
        assertEquals(16_286.2, landed, 0.1)
        assertEquals(16_780.0, landed * (1 + Calculations.GST_RATE), 10.0)
    }

    @Test
    fun `duty and premium compound and round to whole rupees`() {
        assertEquals(16_410.59, IndianRate.counterPerGram(spotGold24, 15.0, 0.0), 0.01)
        assertEquals(16_411.0, IndianRate.counterPerGramRounded(spotGold24, 15.0, 0.0), 0.0)
        assertEquals(16_492.6, IndianRate.counterPerGram(spotGold24, 15.0, 0.5), 0.1)
        // Zero duty and zero premium must pass spot through unchanged.
        assertEquals(spotGold24, IndianRate.counterPerGram(spotGold24, 0.0, 0.0), 0.0)
    }

    @Test
    fun `carat chips match the published Lucknow 22K and 18K quotes`() {
        assertEquals(15_045.0, Calculations.getRateForCarat("22K", lucknowGold24, 0.0), 1.0)
        assertEquals(12_308.0, Calculations.getRateForCarat("18K", lucknowGold24, 0.0), 1.0)
        assertEquals(1_64_120.0, IndianRate.per10Grams(lucknowGold24), 0.0)
    }
}
