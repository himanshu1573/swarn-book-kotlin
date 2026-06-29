package com.swarnabook.billing.ui.newinvoice

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.swarnabook.billing.R
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.databinding.FragmentNewInvoiceBinding
import java.util.Calendar

class NewInvoiceFragment : Fragment() {

    private var _binding: FragmentNewInvoiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NewInvoiceViewModel by viewModels()
    private lateinit var itemAdapter: ItemRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val invoiceId = arguments?.getLong("invoiceId", -1L) ?: -1L
        viewModel.initIfNeeded(invoiceId)
        val draft = viewModel.draft

        // Header / customer fields
        binding.inputInvoiceNumber.setText(draft.invoiceNumber)
        binding.inputDate.setText(draft.date)
        binding.inputCustomerName.setText(draft.customerName)
        binding.inputCustomerPhone.setText(draft.customerPhone)
        binding.inputNotes.setText(draft.notes)
        binding.switchGst.isChecked = draft.gstEnabled
        if (draft.oldGoldExchange > 0) binding.inputOldGold.setText(trimNum(draft.oldGoldExchange))
        if (draft.amountPaid > 0) binding.inputAmountPaid.setText(trimNum(draft.amountPaid))

        // Static labels on the total rows
        binding.rowGoldValue.label.text = getString(R.string.gold_value)
        binding.rowMaking.label.text = getString(R.string.making_charges_total)
        binding.rowCgst.label.text = "CGST 1.5%"
        binding.rowSgst.label.text = "SGST 1.5%"
        binding.rowRoundOff.label.text = getString(R.string.round_off)

        // Item rows
        itemAdapter = ItemRowAdapter(
            items = draft.items,
            gold24Provider = { viewModel.currentGold() },
            silverProvider = { viewModel.currentSilver() },
            onChanged = { refreshTotals() }
        )
        binding.recyclerItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerItems.adapter = itemAdapter

        binding.btnAddItem.setOnClickListener {
            draft.items.add(viewModel.newItem())
            itemAdapter.notifyItemInserted(draft.items.size - 1)
            refreshTotals()
        }

        // Field watchers that affect totals
        binding.inputCustomerName.addTextChangedListener(simple { draft.customerName = it })
        binding.inputCustomerPhone.addTextChangedListener(simple { draft.customerPhone = it })
        binding.inputNotes.addTextChangedListener(simple { draft.notes = it })
        binding.inputOldGold.addTextChangedListener(simple {
            draft.oldGoldExchange = it.toDoubleOrNull() ?: 0.0
            refreshTotals()
        })
        binding.inputAmountPaid.addTextChangedListener(simple {
            draft.amountPaid = it.toDoubleOrNull() ?: 0.0
            refreshTotals()
        })
        binding.switchGst.setOnCheckedChangeListener { _, checked ->
            draft.gstEnabled = checked
            refreshTotals()
        }

        binding.inputDate.setOnClickListener { showDatePicker() }

        binding.btnSave.setOnClickListener { if (validate()) { viewModel.save(); toastAndBack() } }
        binding.btnSavePdf.setOnClickListener {
            if (validate()) {
                val id = viewModel.save()
                val args = Bundle().apply {
                    putLong("invoiceId", id)
                    putBoolean("generatePdf", true)
                }
                findNavController().navigate(R.id.action_newInvoice_to_invoiceView, args)
            }
        }

        refreshTotals()
    }

    /** Recompute the whole invoice and update the totals card. */
    private fun refreshTotals() {
        val draft = viewModel.draft
        viewModel.recompute()
        binding.rowGoldValue.value.text = CurrencyFormat.rupees(draft.goldValue)
        binding.rowMaking.value.text = CurrencyFormat.rupees(draft.makingTotal)

        val gstVisible = draft.gstEnabled
        binding.rowCgst.root.visibility = if (gstVisible) View.VISIBLE else View.GONE
        binding.rowSgst.root.visibility = if (gstVisible) View.VISIBLE else View.GONE
        binding.rowCgst.value.text = CurrencyFormat.rupees(draft.cgst)
        binding.rowSgst.value.text = CurrencyFormat.rupees(draft.sgst)

        binding.rowRoundOff.value.text = CurrencyFormat.rupees(draft.roundOff)
        binding.textGrandTotal.text = CurrencyFormat.rupeesWhole(draft.grandTotal)

        binding.textBalanceDue.text = CurrencyFormat.rupees(draft.balanceDue)
        val color = if (draft.balanceDue > 0) R.color.balance_due else R.color.paid_success
        binding.textBalanceDue.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                cal.set(y, m, d)
                val formatted = com.swarnabook.billing.data.SampleData.dateFormat.format(cal.time)
                binding.inputDate.setText(formatted)
                viewModel.draft.date = formatted
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun validate(): Boolean {
        val draft = viewModel.draft
        if (draft.customerName.isBlank()) {
            Snackbar.make(binding.root, "Enter customer name", Snackbar.LENGTH_SHORT).show()
            return false
        }
        if (draft.items.none { it.weight > 0 }) {
            Snackbar.make(binding.root, "Add at least one item with weight", Snackbar.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun toastAndBack() {
        Snackbar.make(binding.root, "Invoice saved", Snackbar.LENGTH_SHORT).show()
        findNavController().navigate(R.id.dashboardFragment)
    }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)

    private inline fun simple(crossinline onText: (String) -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
