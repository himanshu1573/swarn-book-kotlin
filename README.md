# swarn-book-kotlin

**SwarnaBook** — a native Android jewellery shop billing & invoice app (Kotlin, MVVM, Material 3).

> *Smart Billing for Every Jeweller*

## Status
Frontend phase complete. The data layer (Room + DataStore) is wired into Gradle and
stubbed behind an in-memory store (`data/SampleData.kt`) so every screen is fully
clickable. Swapping in Room/DataStore is a drop-in replacement — the screens won't change.

## Tech
- Kotlin, single-Activity + Navigation Component
- MVVM (ViewModel + LiveData), ViewBinding, Coroutines
- Material Design 3, RecyclerView
- minSdk 26, targetSdk 34

## Features
- Splash → Dashboard → New Invoice → Invoice View → History → Settings
- Live metal-rate card with auto-derived carat rates (24K/22K/18K/14K/Silver)
- Dynamic invoice item rows with live totals
- Old-gold exchange, % or ₹/g making charges, HUID, CGST/SGST split, round-off
- Free WhatsApp delivery via deep link (zero cost, no Business API needed)
- Invoice history with search and date-range filter

## Build
Open in Android Studio, or:
```
./gradlew :app:assembleDebug
```

## Roadmap (backend phase)
1. Room database (entities, DAOs, AppDatabase)
2. InvoiceRepository
3. DataStore-backed settings
4. PDF generation (`PdfGenerator.kt`) + PrintManager + FileProvider PDF share
