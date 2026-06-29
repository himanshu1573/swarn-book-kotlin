package com.swarnabook.billing.ui.dashboard

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
import com.google.android.material.chip.Chip
import com.swarnabook.billing.R
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.data.model.Carat
import com.swarnabook.billing.databinding.FragmentDashboardBinding
import com.swarnabook.billing.ui.common.InvoiceAdapter

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: InvoiceAdapter

    private var watchersActive = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = InvoiceAdapter(onClick = { inv ->
            val args = Bundle().apply { putLong("invoiceId", inv.id) }
            findNavController().navigate(R.id.action_dashboard_to_invoiceView, args)
        })
        binding.recyclerRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecent.adapter = adapter

        // Seed rate inputs (without re-triggering watchers).
        watchersActive = false
        binding.inputGold.setText(trimNum(viewModel.currentGold()))
        binding.inputSilver.setText(trimNum(viewModel.currentSilver()))
        watchersActive = true
        rebuildChips()

        binding.inputGold.addTextChangedListener(rateWatcher())
        binding.inputSilver.addTextChangedListener(rateWatcher())

        binding.fabNewInvoice.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_newInvoice)
        }

        viewModel.todayInvoices.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun rateWatcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (!watchersActive) return
            val gold = binding.inputGold.text.toString().toDoubleOrNull() ?: 0.0
            val silver = binding.inputSilver.text.toString().toDoubleOrNull() ?: 0.0
            viewModel.updateRates(gold, silver)
            rebuildChips()
        }
    }

    /** Derived per-carat chips: 22K/18K/14K from 24K, plus Silver. */
    private fun rebuildChips() {
        val gold = viewModel.currentGold()
        val silver = viewModel.currentSilver()
        binding.chipGroup.removeAllViews()
        Carat.ALL.forEach { carat ->
            val rate = Calculations.getRateForCarat(carat, gold, silver)
            binding.chipGroup.addView(makeChip("$carat  ${CurrencyFormat.rupeesWhole(rate)}/g"))
        }
    }

    private fun makeChip(text: String): Chip = Chip(requireContext()).apply {
        this.text = text
        isClickable = false
        isCheckable = false
        setChipBackgroundColorResource(R.color.cream)
        setTextColor(resources.getColor(R.color.navy, null))
        chipStrokeWidth = 3f
        setChipStrokeColorResource(R.color.gold_dark)
    }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
