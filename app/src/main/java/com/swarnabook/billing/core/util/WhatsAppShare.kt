package com.swarnabook.billing.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.ShopSettings

/**
 * Free, zero-cost WhatsApp delivery via deep link (no Business API, no backend).
 *
 * Frontend phase: opens a chat with the invoice's customer pre-filled with a text
 * summary. Backend phase: once PdfGenerator exists, [shareInvoicePdf] will attach the
 * generated PDF through FileProvider + ACTION_SEND. Both paths live behind this object
 * so the call sites never change when the paid API is (optionally) added later.
 */
object WhatsAppShare {

    /** Open WhatsApp chat with the customer, message body pre-filled. */
    fun sendTextToCustomer(context: Context, invoice: Invoice, settings: ShopSettings) {
        val phone = normalizePhone(invoice.customerPhone)
        val text = buildMessage(invoice, settings)
        val uri = if (phone != null) {
            Uri.parse("https://wa.me/$phone?text=" + Uri.encode(text))
        } else {
            Uri.parse("https://wa.me/?text=" + Uri.encode(text))
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Text summary used until PDF attachment lands in the backend phase. */
    fun buildMessage(invoice: Invoice, settings: ShopSettings): String = buildString {
        appendLine("*${settings.shopName}*")
        appendLine("Invoice ${invoice.invoiceNumber} | ${invoice.date}")
        appendLine("Dear ${invoice.customerName.ifBlank { "Customer" }},")
        appendLine()
        invoice.items.filter { it.weight > 0 }.forEach {
            appendLine("• ${it.itemName} (${it.carat}) ${CurrencyFormat.grams(it.weight)} — ${CurrencyFormat.rupees(it.itemTotal)}")
        }
        appendLine()
        appendLine("Grand Total: ${CurrencyFormat.rupeesWhole(invoice.grandTotal)}")
        appendLine("Paid: ${CurrencyFormat.rupeesWhole(invoice.amountPaid)}")
        if (invoice.balanceDue > 0) appendLine("Balance Due: ${CurrencyFormat.rupeesWhole(invoice.balanceDue)}")
        appendLine()
        append("Thank you for shopping with us. 🙏")
    }

    /** Indian numbers normalised to 91XXXXXXXXXX for wa.me. */
    private fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length > 10 -> digits
            else -> null
        }
    }
}
