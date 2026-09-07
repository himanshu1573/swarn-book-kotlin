package com.swarnabook.billing.core.util

import com.swarnabook.billing.data.model.Carat
import com.swarnabook.billing.data.model.Invoice
import com.swarnabook.billing.data.model.InvoiceItem
import com.swarnabook.billing.data.model.MakingMode
import com.swarnabook.billing.data.model.ShopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is the shop's books leaving the phone, so the two things that must never
 * break are: every invoice reaches the file, and a stray comma in a customer name or a
 * note cannot shift the columns of the row it sits in.
 */
class InvoiceExporterTest {

    private val settings = ShopSettings()

    private fun invoice(
        number: String,
        customer: String = "Anita Sharma",
        notes: String = "",
        grandTotal: Double = 0.0,
        balanceDue: Double = 0.0,
        items: List<InvoiceItem> = emptyList()
    ) = Invoice(
        invoiceNumber = number,
        customerName = customer,
        customerPhone = "9876543210",
        date = "25/08/2026",
        goldValue = grandTotal,
        grandTotal = grandTotal,
        amountPaid = grandTotal - balanceDue,
        balanceDue = balanceDue,
        notes = notes
    ).also { it.items = items.toMutableList() }

    private fun csv(vararg invoices: Invoice) =
        InvoiceExporter.buildCsv(invoices.toList(), settings, "25/08/2026")

    private fun rowsOf(csv: String) = csv.split("\r\n")

    @Test
    fun `register holds one row per invoice and a summed total row`() {
        val out = csv(
            invoice("INV-001", grandTotal = 1_00_000.0),
            invoice("INV-002", grandTotal = 50_000.0, balanceDue = 5_000.0)
        )
        val rows = rowsOf(out)

        assertTrue(rows.any { it.startsWith("INV-001,") })
        assertTrue(rows.any { it.startsWith("INV-002,") })

        val total = rows.single { it.startsWith("TOTAL,") }
        assertTrue("grand totals must be summed", total.contains("150000.00"))
        assertTrue("balances must be summed", total.contains("5000.00"))
    }

    @Test
    fun `status column reflects the balance`() {
        val rows = rowsOf(csv(invoice("INV-001", grandTotal = 1000.0)))
        assertTrue(rows.single { it.startsWith("INV-001,") }.endsWith("Paid,"))

        val due = rowsOf(csv(invoice("INV-002", grandTotal = 1000.0, balanceDue = 1.0)))
        assertTrue(due.single { it.startsWith("INV-002,") }.endsWith("Due,"))
    }

    @Test
    fun `commas quotes and newlines cannot break the column layout`() {
        // Regression: an unescaped comma in the customer name used to push every money
        // column one cell to the right, so the spreadsheet silently misread the row.
        val out = csv(
            invoice(
                "INV-001",
                customer = "Sharma, Anita",
                notes = "Said \"deliver Friday\"\nadvance taken",
                grandTotal = 1000.0
            )
        )
        val row = rowsOf(out).single { it.startsWith("INV-001,") }

        assertTrue(row.contains("\"Sharma, Anita\""))
        assertTrue("inner quotes are doubled", row.contains("\"\"deliver Friday\"\""))
        // The embedded newline became a space, so the invoice is still exactly one row.
        assertEquals(1, rowsOf(out).count { it.startsWith("INV-001,") })
    }

    @Test
    fun `item detail carries every line item with its making mode`() {
        val out = csv(
            invoice(
                "INV-001",
                items = listOf(
                    InvoiceItem(
                        itemName = "Necklace", carat = Carat.K22, weight = 18.5,
                        ratePerGram = 15_045.0, makingMode = MakingMode.PERCENT,
                        makingValue = 12.0, makingAmount = 33_400.0, itemTotal = 3_11_732.0
                    ),
                    InvoiceItem(
                        itemName = "Ring", carat = Carat.K18, weight = 4.0,
                        ratePerGram = 12_308.0, makingMode = MakingMode.FLAT_PER_GRAM,
                        makingValue = 150.0, makingAmount = 600.0, itemTotal = 49_832.0
                    )
                )
            )
        )
        val rows = rowsOf(out)

        assertTrue(rows.any { it.startsWith("INV-001,25/08/2026,Anita Sharma,Necklace,22K,,18.5,15045.00,12%,") })
        assertTrue(rows.any { it.startsWith("INV-001,25/08/2026,Anita Sharma,Ring,18K,,4,12308.00,150.00/g,") })
        // …and the register row summarises the same items in one cell. Items are joined
        // with "; " precisely so the cell needs no quoting.
        assertTrue(rows.any { it.contains(",Necklace (22K) 18.5 g; Ring (18K) 4 g,") })
    }

    @Test
    fun `file name is filesystem safe whatever the shop is called`() {
        assertTrue(
            InvoiceExporter.fileName("Anukul Jewellers")
                .startsWith("Anukul-Jewellers-Invoices-")
        )
        assertTrue(InvoiceExporter.fileName("  ///  ").startsWith("Invoices-Invoices-"))
        assertTrue(InvoiceExporter.fileName("Anukul Jewellers").endsWith(".csv"))
    }
}
