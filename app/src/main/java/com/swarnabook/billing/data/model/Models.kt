package com.swarnabook.billing.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room entities for invoices, plus the plain settings model.
 *
 * [ShopSettings] is deliberately NOT an entity — a single settings row in SQLite is
 * awkward to read synchronously, so it lives in DataStore (see data/SettingsStore.kt).
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

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId")]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
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

/**
 * The invoice header row. Line items are a separate table, so [items] is @Ignore'd and
 * lives outside the constructor — which also means `copy()` does NOT carry items over.
 * Use [copyDeep] whenever you need a detached editable copy.
 */
@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var invoiceNumber: String = "",
    var customerName: String = "",
    var customerPhone: String = "",
    var date: String = "",
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
) {
    @Ignore
    var items: MutableList<InvoiceItem> = mutableListOf()

    /** Detached copy including a fresh copy of every line item. */
    fun copyDeep(): Invoice = copy().also { clone ->
        clone.items = items.map { it.copy() }.toMutableList()
    }
}

/** Room read-side view: one invoice header joined with its line items. */
data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(parentColumn = "id", entityColumn = "invoiceId")
    val itemRows: List<InvoiceItem>
) {
    /** Flattens back into the single [Invoice] shape every screen already expects. */
    fun toInvoice(): Invoice = invoice.also { it.items = itemRows.toMutableList() }
}

data class ShopSettings(
    var shopName: String = "Shri Swarna Jewellers",
    var shopAddress: String = "123 Bazaar Road, Jaipur, Rajasthan",
    var shopPhone: String = "+91 98765 43210",
    var gstin: String = "08ABCDE1234F1Z5",
    var defaultMakingPct: Double = 12.0,
    /**
     * Percent added on top of international spot to reach the local counter rate
     * (import duty + GST + dealer premium). Zero until the shop sets its own figure,
     * so a fetched rate is never silently inflated.
     */
    var ratePremiumPct: Double = 0.0,
    var gstEnabledDefault: Boolean = true,
    var theme: AppTheme = AppTheme.GOLD
)

enum class AppTheme { GOLD, LIGHT, DARK }
