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
import com.swarnabook.billing.SwarnaBookApp
import com.swarnabook.billing.core.util.Calculations
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.data.model.Carat
import com.swarnabook.billing.databinding.FragmentDashboardBinding
import com.swarnabook.billing.ui.common.Initials
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

        // Header: shop name + initials avatar, falling back to the app name.
        val shopName = SwarnaBookApp.settings.currentSettings().shopName
            .ifBlank { getString(R.string.app_name) }
        binding.shopTitle.text = shopName
        binding.avatarText.text = Initials.of(shopName)
        binding.btnSeeAll.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }

        // Seed rate inputs (without re-triggering watchers).
        watchersActive = false
        binding.inputGold.setText(trimNum(viewModel.currentGold()))
        binding.inputSilver.setText(trimNum(viewModel.currentSilver()))
        watchersActive = true
        rebuildChips()

        binding.inputGold.addTextChangedListener(rateWatcher())
        binding.inputSilver.addTextChangedListener(rateWatcher())

        // A live fetch writes to DataStore, so push the new value into the field --
        // but never overwrite what the shop is currently typing.
        viewModel.gold24Rate.observe(viewLifecycleOwner) { rate ->
            syncRateField(binding.inputGold, rate)
        }
        viewModel.silverRate.observe(viewLifecycleOwner) { rate ->
            syncRateField(binding.inputSilver, rate)
        }

        binding.btnFetchRate.setOnClickListener { viewModel.refreshRates(manual = true) }
        viewModel.fetching.observe(viewLifecycleOwner) { busy ->
            binding.btnFetchRate.isEnabled = !busy
        }
        viewModel.rateStatus.observe(viewLifecycleOwner) { status ->
            binding.rateStatus.text = status
            binding.rateStatus.visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
        }

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

    /**
     * Writes a fetched rate into its input without fighting the user's typing:
     * the field is only touched when it is not focused and the value really changed.
     */
    private fun syncRateField(field: com.google.android.material.textfield.TextInputEditText, rate: Double) {
        if (field.hasFocus()) return
        if (field.text.toString().toDoubleOrNull() == rate) return
        watchersActive = false
        field.setText(trimNum(rate))
        watchersActive = true
        rebuildChips()
    }

    /** Derived per-carat chips: 22K/18K/14K from 24K, plus Silver. */
    private fun rebuildChips() {
        val gold = viewModel.currentGold()
        val silver = viewModel.currentSilver()
        binding.chipGroup.removeAllViews()
        Carat.ALL.forEach { carat ->
            val rate = Calculations.getRateForCarat(carat, gold, silver)
            binding.chipGroup.addView(makeChip(carat, "$carat  ${CurrencyFormat.rupeesWhole(rate)}/g"))
        }
    }

    /** Pill chip; gold carats get the pale-gold fill, silver the grey one. */
    private fun makeChip(carat: String, text: String): Chip = Chip(requireContext()).apply {
        this.text = text
        isClickable = false
        isCheckable = false
        val gold = carat != Carat.SILVER
        setChipBackgroundColorResource(if (gold) R.color.gold_pale else R.color.silver_soft)
        setChipStrokeColorResource(if (gold) R.color.gold else R.color.silver)
        chipStrokeWidth = resources.displayMetrics.density
        setTextColor(resources.getColor(R.color.ink, null))
    }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
