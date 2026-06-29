package com.swarnabook.billing.ui.newinvoice

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.data.model.Carat
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.MakingMode
import com.swarnabook.billing.databinding.ItemInvoiceRowBinding

/**
 * Editable item rows for the New Invoice screen. Each row mutates its backing
 * [InvoiceItem] in place; any change recomputes that row and calls [onChanged]
 * so the invoice totals update live.
 */
class ItemRowAdapter(
    private val items: MutableList<InvoiceItem>,
    private val gold24Provider: () -> Double,
    private val silverProvider: () -> Double,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<ItemRowAdapter.VH>() {

    private val makingModes = listOf("%", "₹/g")

    inner class VH(val b: ItemInvoiceRowBinding) : RecyclerView.ViewHolder(b.root) {
        var current: InvoiceItem? = null
        var isBinding = false

        init {
            val ctx = b.root.context
            b.spinnerCarat.adapter =
                ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Carat.ALL)
            b.spinnerMakingMode.adapter =
                ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, makingModes)

            b.inputItemName.addTextChangedListener(watcher {
                if (!isBinding) current?.itemName = it
            })
            b.inputHuid.addTextChangedListener(watcher {
                if (!isBinding) current?.huid = it
            })
            b.inputWeight.addTextChangedListener(watcher {
                if (isBinding) return@watcher
                current?.weight = it.toDoubleOrNull() ?: 0.0
                recalc()
            })
            b.inputMakingValue.addTextChangedListener(watcher {
                if (isBinding) return@watcher
                current?.makingValue = it.toDoubleOrNull() ?: 0.0
                recalc()
            })

            b.spinnerCarat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (isBinding) return
                    val item = current ?: return
                    item.carat = Carat.ALL[pos]
                    item.ratePerGram = Calculations.getRateForCarat(
                        item.carat, gold24Provider(), silverProvider()
                    )
                    b.inputRate.setText(trimNum(item.ratePerGram))
                    recalc()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }

            b.spinnerMakingMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (isBinding) return
                    current?.makingMode = if (pos == 0) MakingMode.PERCENT else MakingMode.FLAT_PER_GRAM
                    recalc()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }

            b.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                    onChanged()
                }
            }
        }

        /** Recompute this row's making + total, refresh its labels, bubble up. */
        fun recalc() {
            val item = current ?: return
            val gv = Calculations.calcGoldValue(item.weight, item.ratePerGram)
            val making = when (item.makingMode) {
                MakingMode.PERCENT -> Calculations.calcMakingByPercent(gv, item.makingValue)
                MakingMode.FLAT_PER_GRAM -> Calculations.calcMakingByFlat(item.weight, item.makingValue)
            }
            item.makingAmount = making
            item.itemTotal = Calculations.calcItemTotal(gv, making)
            b.textMakingAmount.text = "Making " + CurrencyFormat.rupees(making)
            b.textItemTotal.text = CurrencyFormat.rupees(item.itemTotal)
            onChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemInvoiceRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.isBinding = true
        holder.current = item

        if (item.ratePerGram == 0.0) {
            item.ratePerGram = Calculations.getRateForCarat(
                item.carat, gold24Provider(), silverProvider()
            )
        }

        with(holder.b) {
            inputItemName.setText(item.itemName)
            inputHuid.setText(item.huid)
            spinnerCarat.setSelection(Carat.ALL.indexOf(item.carat).coerceAtLeast(0))
            inputRate.setText(trimNum(item.ratePerGram))
            inputWeight.setText(if (item.weight == 0.0) "" else trimNum(item.weight))
            spinnerMakingMode.setSelection(if (item.makingMode == MakingMode.PERCENT) 0 else 1)
            inputMakingValue.setText(if (item.makingValue == 0.0) "" else trimNum(item.makingValue))
            textMakingAmount.text = "Making " + CurrencyFormat.rupees(item.makingAmount)
            textItemTotal.text = CurrencyFormat.rupees(item.itemTotal)
        }
        holder.isBinding = false
    }

    override fun getItemCount(): Int = items.size

    private inline fun watcher(crossinline onText: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
        }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)
}
