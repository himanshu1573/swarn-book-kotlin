package com.swarnabook.billing.ui.invoiceview

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.swarnabook.billing.R
import com.swarnabook.billing.SwarnaBookApp
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.core.util.WhatsAppShare
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.databinding.FragmentInvoiceViewBinding
import kotlinx.coroutines.launch

class InvoiceViewFragment : Fragment() {

    private var _binding: FragmentInvoiceViewBinding? = null
    private val binding get() = _binding!!
    private var invoiceId: Long = -1L
    private var invoice: Invoice? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        invoiceId = arguments?.getLong("invoiceId", -1L) ?: -1L

        // Observed, so "Mark as paid" and edits from other screens redraw automatically.
        SwarnaBookApp.repo.observeById(invoiceId).observe(viewLifecycleOwner) { inv ->
            invoice = inv
            if (inv == null) {
                Snackbar.make(binding.root, "Invoice not found", Snackbar.LENGTH_SHORT).show()
            } else {
                render(inv)
            }
        }

        if (arguments?.getBoolean("generatePdf", false) == true) {
            Snackbar.make(binding.root,
                "Invoice saved. PDF generation arrives with PdfGenerator in the backend phase.",
                Snackbar.LENGTH_LONG).show()
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnEdit.setOnClickListener {
            val args = Bundle().apply { putLong("invoiceId", invoiceId) }
            findNavController().navigate(R.id.action_invoiceView_to_editInvoice, args)
        }
        binding.btnMarkPaid.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                SwarnaBookApp.repo.markPaid(invoiceId)
                if (_binding != null) {
                    Snackbar.make(binding.root, "Marked as paid", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnWhatsapp.setOnClickListener {
            val inv = invoice ?: return@setOnClickListener
            WhatsAppShare.sendTextToCustomer(
                requireContext(), inv, SwarnaBookApp.settings.currentSettings()
            )
        }
        binding.btnShare.setOnClickListener {
            Snackbar.make(binding.root,
                "PDF share opens once PdfGenerator + FileProvider are wired in the backend phase.",
                Snackbar.LENGTH_LONG).show()
        }
        binding.btnPrint.setOnClickListener {
            Snackbar.make(binding.root,
                "Printing uses PrintManager on the generated PDF (backend phase).",
                Snackbar.LENGTH_LONG).show()
        }
    }

    private fun render(inv: Invoice) {
        val settings = SwarnaBookApp.settings.currentSettings()

        binding.shopName.text = settings.shopName
        binding.shopAddress.text = settings.shopAddress
        binding.shopMeta.text = "Ph: ${settings.shopPhone}  |  GSTIN: ${settings.gstin}"
        binding.invoiceNumber.text = "#${inv.invoiceNumber}"
        binding.invoiceDate.text = inv.date
        binding.customerLine.text = "${inv.customerName}  |  ${inv.customerPhone}"
        binding.notesView.text = if (inv.notes.isBlank()) "" else "Notes: ${inv.notes}"

        buildItemRows(inv)
        buildTotals(inv)

        val due = inv.balanceDue > 0
        binding.headerTitle.text = "${getString(R.string.invoice)} #${inv.invoiceNumber}"
        binding.statusPill.text =
            if (due) "Due ${CurrencyFormat.rupeesWhole(inv.balanceDue)}" else getString(R.string.status_paid)
        binding.statusPill.setBackgroundResource(if (due) R.drawable.bg_pill_due else R.drawable.bg_pill_success)
        binding.statusPill.setTextColor(color(if (due) R.color.danger else R.color.success))
        binding.btnMarkPaid.visibility = if (due) View.VISIBLE else View.GONE
    }

    private fun buildItemRows(inv: Invoice) {
        binding.itemsContainer.removeAllViews()
        val weights = floatArrayOf(2.4f, 1f, 1.2f, 1.2f, 1.4f)
        inv.items.filter { it.weight > 0 }.forEach { item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(6), dp(5), dp(6), dp(5))
            }
            val makingLabel = CurrencyFormat.rupeesWhole(item.makingAmount)
            val cells = listOf(
                "${item.itemName}\n${item.carat}" to Gravity.START,
                CurrencyFormat.grams(item.weight) to Gravity.END,
                CurrencyFormat.rupeesWhole(item.ratePerGram) to Gravity.END,
                makingLabel to Gravity.END,
                CurrencyFormat.rupeesWhole(item.itemTotal) to Gravity.END
            )
            cells.forEachIndexed { i, (text, grav) ->
                row.addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weights[i])
                    this.text = text
                    textSize = 12f
                    gravity = grav
                    setTextColor(color(R.color.ink))
                })
            }
            binding.itemsContainer.addView(row)
        }
    }

    private fun buildTotals(inv: Invoice) {
        binding.totalsContainer.removeAllViews()
        addTotal(getString(R.string.gold_value), CurrencyFormat.rupees(inv.goldValue), false)
        addTotal(getString(R.string.making_charges_total), CurrencyFormat.rupees(inv.makingTotal), false)
        if (inv.oldGoldExchange > 0)
            addTotal(getString(R.string.old_gold_exchange), "- " + CurrencyFormat.rupees(inv.oldGoldExchange), false)
        if (inv.gstEnabled) {
            addTotal("CGST 1.5%", CurrencyFormat.rupees(inv.cgst), false)
            addTotal("SGST 1.5%", CurrencyFormat.rupees(inv.sgst), false)
        }
        addTotal(getString(R.string.round_off), CurrencyFormat.rupees(inv.roundOff), false)
        addTotal(getString(R.string.grand_total), CurrencyFormat.rupeesWhole(inv.grandTotal), true)
        addTotal(getString(R.string.amount_paid), CurrencyFormat.rupeesWhole(inv.amountPaid), false)
        if (inv.balanceDue > 0)
            addTotal(getString(R.string.balance_due), CurrencyFormat.rupeesWhole(inv.balanceDue), true, R.color.danger)
        else
            addTotal("Status", "PAID", true, R.color.success)
    }

    private fun addTotal(label: String, value: String, bold: Boolean, colorRes: Int = R.color.ink) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val color = color(colorRes)
        row.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = label
            textSize = if (bold) 16f else 14f
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            textSize = if (bold) 16f else 14f
            setTextColor(color)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        binding.totalsContainer.addView(row)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun color(res: Int): Int = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
