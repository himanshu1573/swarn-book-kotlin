package com.swarnabook.billing.data.model

/**
 * Plain UI models for the frontend phase.
 *
 * In the backend phase these become Room @Entity / @Relation classes
 * (Invoice + InvoiceItem with a foreign key, ShopSettings as a single row).
 * The field shape is intentionally final now so the DB schema won't need migration.
 */

/** Carat options shown in the item spinner, in display order. */
object Carat {
    const val K24 = "24K"
    const val K22 = "22K"
    const val K18 = "18K"
    const val K14 = "14K"
    const val SILVER = "Silver"
    val ALL = listOf(K24, K22, K18, K14, SILVER)
}

/** How making charges are entered for a line item. */
enum class MakingMode { PERCENT, FLAT_PER_GRAM }

data class InvoiceItem(
    var id: Long = 0,
    var invoiceId: Long = 0,
    var itemName: String = "",
    var carat: String = Carat.K22,
    var huid: String = "",
    var weight: Double = 0.0,
    var ratePerGram: Double = 0.0,
    var makingMode: MakingMode = MakingMode.PERCENT,
    /** Percent value when PERCENT, rupees-per-gram when FLAT_PER_GRAM. */
    var makingValue: Double = 0.0,
    var makingAmount: Double = 0.0,
    var itemTotal: Double = 0.0
)

data class Invoice(
    var id: Long = 0,
    var invoiceNumber: String = "",
    var customerName: String = "",
    var customerPhone: String = "",
    var date: String = "",
    var items: MutableList<InvoiceItem> = mutableListOf(),
    var goldValue: Double = 0.0,
    var makingTotal: Double = 0.0,
    var oldGoldExchange: Double = 0.0,
    var gstEnabled: Boolean = true,
    var cgst: Double = 0.0,
    var sgst: Double = 0.0,
    var roundOff: Double = 0.0,
    var grandTotal: Double = 0.0,
    var amountPaid: Double = 0.0,
    var balanceDue: Double = 0.0,
    var notes: String = "",
    var paid: Boolean = false,
    var createdAt: Long = 0L
)

data class ShopSettings(
    var shopName: String = "Shri Swarna Jewellers",
    var shopAddress: String = "123 Bazaar Road, Jaipur, Rajasthan",
    var shopPhone: String = "+91 98765 43210",
    var gstin: String = "08ABCDE1234F1Z5",
    var defaultMakingPct: Double = 12.0,
    var gstEnabledDefault: Boolean = true,
    var theme: AppTheme = AppTheme.GOLD
)

enum class AppTheme { GOLD, LIGHT, DARK }
