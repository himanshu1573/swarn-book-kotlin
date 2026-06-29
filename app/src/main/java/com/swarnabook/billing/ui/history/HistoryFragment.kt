package com.swarnabook.billing.ui.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.swarnabook.billing.R
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.databinding.FragmentHistoryBinding
import com.swarnabook.billing.ui.common.InvoiceAdapter

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: InvoiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = InvoiceAdapter(
            onClick = { inv ->
                val args = Bundle().apply { putLong("invoiceId", inv.id) }
                findNavController().navigate(R.id.action_history_to_invoiceView, args)
            },
            onLongClick = { inv -> confirmDelete(inv.id, inv.invoiceNumber) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { viewModel.setQuery(s?.toString() ?: "") }
        })

        binding.btnFilter.setOnClickListener { showDateRangePicker() }
        binding.filterLabel.setOnClickListener {
            viewModel.clearDateRange()
            binding.filterLabel.visibility = View.GONE
        }

        viewModel.results.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.filter_dates))
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first
            val end = range.second
            if (start != null && end != null) {
                viewModel.setDateRange(start, end)
                val s = SampleData.dateFormat.format(start)
                val e = SampleData.dateFormat.format(end)
                binding.filterLabel.text = "$s – $e   ✕  (tap to clear)"
                binding.filterLabel.visibility = View.VISIBLE
            }
        }
        picker.show(childFragmentManager, "dateRange")
    }

    private fun confirmDelete(id: Long, number: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_invoice_q))
            .setMessage("$number — ${getString(R.string.delete_invoice_msg)}")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(id) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
