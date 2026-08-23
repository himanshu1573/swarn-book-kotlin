package com.swarnabook.billing.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.InvoiceWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observeById(id: Long): Flow<InvoiceWithItems?>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun findById(id: Long): InvoiceWithItems?

    @Query("SELECT MAX(id) FROM invoices")
    suspend fun maxInvoiceId(): Long?

    @Insert
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Insert
    suspend fun insertItems(items: List<InvoiceItem>)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItemsFor(invoiceId: Long)

    /**
     * Insert-or-update the header and replace its line items in one transaction.
     *
     * Items are deleted and re-inserted rather than diffed: a row can be added, removed
     * or reordered on the edit screen, and an invoice has a handful of items at most.
     */
    @Transaction
    suspend fun upsertWithItems(invoice: Invoice): Long {
        val id: Long
        if (invoice.id == 0L) {
            id = insertInvoice(invoice)
            invoice.id = id
        } else {
            id = invoice.id
            updateInvoice(invoice)
            deleteItemsFor(id)
        }
        val rows = invoice.items.map { it.copy(id = 0, invoiceId = id) }
        if (rows.isNotEmpty()) insertItems(rows)
        return id
    }
}
