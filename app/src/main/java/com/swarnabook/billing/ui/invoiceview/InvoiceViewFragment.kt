package com.swarnabook.billing.ui.invoiceview

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.swarnabook.billing.R
import com.swarnabook.billing.core.util.CurrencyFormat
import com.swarnabook.billing.core.util.WhatsAppShare
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.databinding.FragmentInvoiceViewBinding

class InvoiceViewFragment : Fragment() {

    private var _binding: FragmentInvoiceViewBinding? = null
    private val binding get() = _binding!!
    private var invoiceId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        invoiceId = arguments?.getLong("invoiceId", -1L) ?: -1L
        render()

        if (arguments?.getBoolean("generatePdf", false) == true) {
            Snackbar.make(binding.root,
                "Invoice saved. PDF generation arrives with PdfGenerator in the backend phase.",
                Snackbar.LENGTH_LONG).show()
        }

        binding.btnEdit.setOnClickListener {
            val args = Bundle().apply { putLong("invoiceId", invoiceId) }
            findNavController().navigate(R.id.action_invoiceView_to_editInvoice, args)
        }
        binding.btnMarkPaid.setOnClickListener {
            SampleData.markPaid(invoiceId)
            render()
            Snackbar.make(binding.root, "Marked as paid", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnWhatsapp.setOnClickListener {
            val inv = SampleData.getById(invoiceId) ?: return@setOnClickListener
            WhatsAppShare.sendTextToCustomer(requireContext(), inv, SampleData.currentSettings())
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

    private fun render() {
        val inv = SampleData.getById(invoiceId) ?: run {
            Snackbar.make(binding.root, "Invoice not found", Snackbar.LENGTH_SHORT).show()
            return
        }
        val settings = SampleData.currentSettings()

        binding.shopName.text = settings.shopName
        binding.shopAddress.text = settings.shopAddress
        binding.shopMeta.text = "Ph: ${settings.shopPhone}  |  GSTIN: ${settings.gstin}"
        binding.invoiceNumber.text = "#${inv.invoiceNumber}"
        binding.invoiceDate.text = inv.date
        binding.customerLine.text = "${inv.customerName}  |  ${inv.customerPhone}"
        binding.notesView.text = if (inv.notes.isBlank()) "" else "Notes: ${inv.notes}"

        buildItemRows(inv)
        buildTotals(inv)

        binding.btnMarkPaid.visibility = if (inv.balanceDue > 0) View.VISIBLE else View.GONE
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
                    setTextColor(Color.parseColor("#1C2B4A"))
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
            addTotal(getString(R.string.balance_due), CurrencyFormat.rupeesWhole(inv.balanceDue), true, "#B83030")
        else
            addTotal("Status", "PAID", true, "#2A7A3A")
    }

    private fun addTotal(label: String, value: String, bold: Boolean, colorHex: String? = null) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val color = Color.parseColor(colorHex ?: "#1C2B4A")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
