package com.swarnabook.billing.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.swarnabook.billing.R
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.databinding.ItemInvoiceBinding

/** Shared list adapter used by the Dashboard (today) and History (all) screens. */
class InvoiceAdapter(
    private val onClick: (Invoice) -> Unit,
    private val onLongClick: ((Invoice) -> Unit)? = null
) : ListAdapter<Invoice, InvoiceAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemInvoiceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemInvoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val inv = getItem(position)
        with(holder.binding) {
            avatar.text = Initials.of(inv.customerName)
            invoiceNumber.text = inv.invoiceNumber
            customerName.text = inv.customerName
            date.text = inv.date
            grandTotal.text = CurrencyFormat.rupeesWhole(inv.grandTotal)

            // Soft status pill: red tint while money is owed, green once settled.
            val due = inv.balanceDue > 0.0
            balance.text = if (due) "Due ${CurrencyFormat.rupeesWhole(inv.balanceDue)}" else "Paid"
            balance.setBackgroundResource(if (due) R.drawable.bg_pill_due else R.drawable.bg_pill_success)
            balance.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    if (due) R.color.balance_due else R.color.paid_success
                )
            )

            root.setOnClickListener { onClick(inv) }
            root.setOnLongClickListener {
                onLongClick?.invoke(inv)
                onLongClick != null
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Invoice>() {
            override fun areItemsTheSame(a: Invoice, b: Invoice) = a.id == b.id
            override fun areContentsTheSame(a: Invoice, b: Invoice) =
                a.grandTotal == b.grandTotal && a.balanceDue == b.balanceDue &&
                    a.customerName == b.customerName && a.paid == b.paid
        }
    }
}
