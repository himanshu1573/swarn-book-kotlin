package com.swarnabook.billing.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.data.local.InvoiceDao
import com.swarnabook.billing.data.model.Invoice
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for invoices, backed by Room.
 *
 * Replaces the frontend-phase in-memory SampleData store. The read side is a Flow the
 * screens observe as LiveData, so any insert/update/delete refreshes every screen
 * automatically. The write side is suspend — SQLite must not be touched on the main
 * thread — so callers wrap saves in viewModelScope/lifecycleScope.
 */
class InvoiceRepository(private val dao: InvoiceDao) {

    /** Every invoice, newest first. Emits again on any write. */
    val invoicesLive: LiveData<List<Invoice>> =
        dao.observeAll().map { rows -> rows.map { it.toInvoice() } }.asLiveData()

    /** One invoice, re-emitting when it changes (used by the invoice view screen). */
    fun observeById(id: Long): LiveData<Invoice?> =
        dao.observeById(id).map { it?.toInvoice() }.asLiveData()

    suspend fun getById(id: Long): Invoice? = dao.findById(id)?.toInvoice()

    /**
     * Insert (id == 0) or update an existing invoice with its items; returns the row id.
     * Totals are always recomputed here so a stored invoice can never disagree with its
     * own line items, whatever the screen sent.
     */
    suspend fun upsert(invoice: Invoice): Long {
        Calculations.recalculate(invoice)
        if (invoice.createdAt == 0L) invoice.createdAt = System.currentTimeMillis()
        return dao.upsertWithItems(invoice)
    }

    suspend fun markPaid(id: Long) {
        val inv = dao.findById(id)?.invoice ?: return
        inv.amountPaid = inv.grandTotal
        inv.balanceDue = 0.0
        inv.paid = true
        dao.updateInvoice(inv)
    }

    /** Line items go with it via the ON DELETE CASCADE foreign key. */
    suspend fun delete(id: Long) = dao.deleteInvoice(id)

    /**
     * Next invoice number, derived from the highest row id so it never repeats after a
     * deletion (a count-based sequence would hand out a number already on a printed bill).
     */
    suspend fun nextInvoiceNumber(): String =
        "INV-%03d".format((dao.maxInvoiceId() ?: 0L) + 1)
}
