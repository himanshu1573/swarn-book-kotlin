package com.swarnabook.billing.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.swarnabook.billing.R
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.MakingMode
import com.swarnabook.billing.data.model.ShopSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exports a list of invoices to a single CSV file and hands it to the share sheet.
 *
 * CSV rather than PDF because the point of an export is to get the books into Excel /
 * Google Sheets / a Tally import — one file the shop can archive or send to its
 * accountant. The file carries two sections so nothing is lost:
 *
 *   1. INVOICE REGISTER — one row per bill, plus a TOTAL row.
 *   2. ITEM DETAIL      — one row per line item, keyed back by invoice number.
 *
 * Delivery reuses the existing FileProvider (see res/xml/file_paths.xml), the same
 * mechanism [WhatsAppShare] will use for PDFs, so no new manifest entry is needed.
 */
object InvoiceExporter {

    private const val EXPORT_DIR = "invoices"
    private const val MIME_CSV = "text/csv"

    /** Excel on Windows needs the byte-order mark to read ₹ and Devanagari names. */
    private const val UTF8_BOM = "\uFEFF"

    /** Plain, unformatted money so a spreadsheet reads the cells as numbers. */
    private val money = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
    private val grams = DecimalFormat("0.###", DecimalFormatSymbols(Locale.US))

    /**
     * Builds the CSV and writes it under filesDir/invoices/. Runs off the main thread.
     * Previous exports are removed first so the folder never grows without bound.
     */
    suspend fun exportToFile(
        context: Context,
        invoices: List<Invoice>,
        settings: ShopSettings
    ): File {
        // DateFormats' SimpleDateFormats are main-thread-only, so read them here —
        // before hopping to IO — rather than inside the worker block.
        val exportedOn = DateFormats.today()
        val name = fileName(settings.shopName)

        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, EXPORT_DIR).apply { mkdirs() }
            dir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }?.forEach { it.delete() }

            val file = File(dir, name)
            file.writeText(UTF8_BOM + buildCsv(invoices, settings, exportedOn), Charsets.UTF_8)
            file
        }
    }

    /** Opens the system share sheet (Drive, Files, WhatsApp, Gmail…) for [file]. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_CSV
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.export_share_title))
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** e.g. `Anukul-Jewellers-Invoices-25-08-2026_1430.csv` */
    internal fun fileName(shopName: String): String {
        val slug = shopName.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "Invoices" }
        return "$slug-Invoices-${DateFormats.fileStamp()}.csv"
    }

    /** Pure, so it can be unit tested without an Android runtime. */
    internal fun buildCsv(
        invoices: List<Invoice>,
        settings: ShopSettings,
        exportedOn: String
    ): String = buildString {
        row(settings.shopName, "Invoice export")
        row("Exported on", exportedOn)
        row("Invoices", invoices.size.toString())
        row("GSTIN", settings.gstin)
        row()

        row("INVOICE REGISTER")
        row(
            "Invoice No", "Date", "Customer", "Phone", "Items",
            "Gold Value", "Making Charges", "Old Gold Exchange",
            "CGST", "SGST", "Round Off", "Grand Total",
            "Amount Paid", "Balance Due", "Status", "Notes"
        )
        invoices.forEach { inv ->
            row(
                inv.invoiceNumber, inv.date, inv.customerName, inv.customerPhone,
                itemSummary(inv),
                money.format(inv.goldValue), money.format(inv.makingTotal),
                money.format(inv.oldGoldExchange),
                money.format(inv.cgst), money.format(inv.sgst),
                money.format(inv.roundOff), money.format(inv.grandTotal),
                money.format(inv.amountPaid), money.format(inv.balanceDue),
                if (inv.balanceDue > 0.0) "Due" else "Paid",
                inv.notes
            )
        }
        row(
            "TOTAL", "", "", "", "",
            money.format(invoices.sumOf { it.goldValue }),
            money.format(invoices.sumOf { it.makingTotal }),
            money.format(invoices.sumOf { it.oldGoldExchange }),
            money.format(invoices.sumOf { it.cgst }),
            money.format(invoices.sumOf { it.sgst }),
            money.format(invoices.sumOf { it.roundOff }),
            money.format(invoices.sumOf { it.grandTotal }),
            money.format(invoices.sumOf { it.amountPaid }),
            money.format(invoices.sumOf { it.balanceDue }),
            "", ""
        )
        row()

        row("ITEM DETAIL")
        row(
            "Invoice No", "Date", "Customer", "Item", "Carat", "HUID",
            "Weight (g)", "Rate/g", "Making", "Making Amount", "Item Total"
        )
        invoices.forEach { inv ->
            inv.items.forEach { item ->
                row(
                    inv.invoiceNumber, inv.date, inv.customerName,
                    item.itemName, item.carat, item.huid,
                    grams.format(item.weight), money.format(item.ratePerGram),
                    makingLabel(item.makingMode, item.makingValue),
                    money.format(item.makingAmount), money.format(item.itemTotal)
                )
            }
        }
    }

    /** e.g. `Necklace (22K) 18.5 g; Ring (18K) 4 g` */
    private fun itemSummary(invoice: Invoice): String =
        invoice.items.joinToString("; ") {
            "${it.itemName.ifBlank { "Item" }} (${it.carat}) ${grams.format(it.weight)} g"
        }

    private fun makingLabel(mode: MakingMode, value: Double): String = when (mode) {
        MakingMode.PERCENT -> "${grams.format(value)}%"
        MakingMode.FLAT_PER_GRAM -> "${money.format(value)}/g"
    }

    // ----- CSV plumbing -----

    /** CRLF because that is what Excel expects from a .csv. */
    private fun StringBuilder.row(vararg cells: String) {
        append(cells.joinToString(",") { escape(it) })
        append("\r\n")
    }

    /** RFC 4180: quote a field containing a comma, quote or newline; double its quotes. */
    private fun escape(value: String): String {
        val cleaned = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return if (cleaned.any { it == ',' || it == '"' }) {
            "\"" + cleaned.replace("\"", "\"\"") + "\""
        } else {
            cleaned
        }
    }
}
