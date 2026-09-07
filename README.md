# swarn-book-kotlin

**Anukul Jewellers** — a native Android billing & invoice app for the Anukul Jewellers
showroom in Unnao, Uttar Pradesh (Kotlin, MVVM, Material 3).

> *Timeless Tradition, Crafted to Perfection*

Visual language follows the Bachatt app (bachatt.app): off-white canvas with a soft
violet glow, white hairline cards, pill-shaped controls, one indigo action colour and
Manrope type. Gold/silver appear only where the metal is the subject (rate badges,
the bill's top strip). Tokens live in `res/values/colors.xml` and `themes.xml`.

## Status
Frontend and data layer complete: invoices persist in Room, settings and the day's
rates in DataStore, and metal rates are fetched live from goldprice.dev. PDF generation
is the remaining piece.

## Tech
- Kotlin, single-Activity + Navigation Component
- MVVM (ViewModel + LiveData), ViewBinding, Coroutines
- Material Design 3, RecyclerView
- minSdk 26, targetSdk 34

## Features
- Splash → Dashboard → New Invoice → Invoice View → History → Settings
- Live metal-rate card: goldprice.dev spot → Uttar Pradesh counter rate (customs duty +
  local premium, ex-GST), auto-derived carat rates (24K/22K/18K/14K/Silver)
- Dynamic invoice item rows with live totals
- Old-gold exchange, % or ₹/g making charges, HUID, CGST/SGST split, round-off
- Free WhatsApp delivery via deep link (zero cost, no Business API needed)
- Invoice history with search and date-range filter
- One-tap CSV export of the invoice list (register + line-item detail) to the share sheet

## Build
Open in Android Studio, or:
```
./gradlew :app:assembleDebug
```

## Roadmap
1. PDF generation (`PdfGenerator.kt`) + PrintManager + FileProvider PDF share
2. Attach the generated PDF to the existing WhatsApp share
