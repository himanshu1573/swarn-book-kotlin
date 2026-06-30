# SwarnaBook — Project Status & Handover

**App:** SwarnaBook — native Android jewellery shop billing & invoice app
**Package:** `com.swarnabook.billing`
**Repo:** https://github.com/himanshu1573/swarn-book-kotlin
**Last updated:** 30 June 2026
**Current phase:** ✅ Frontend complete & running on device · ⏳ Backend (database) not started

---

## 1. Quick summary (read this first next time)

We have built the **entire frontend (UI)** of the app in Kotlin. It compiles, installs,
and runs on a real phone (tested on Realme RMX3868, Android 14). Every screen is clickable
and the billing math works live.

**The one thing NOT done yet:** data is not saved permanently. Right now invoices live in
the phone's memory (RAM) and disappear when the app is fully closed. Making data permanent
is the **next phase** (the on-device Room database).

---

## 2. What is DONE ✅

### Screens (all built and working)
| Screen | What it does |
|--------|--------------|
| **Splash** | Gold logo on navy + tagline, auto-moves to Dashboard after 2s |
| **Dashboard** | Editable today's gold/silver rate; auto-derived carat chips (24K/22K/18K/14K/Silver); list of today's invoices; "+ New Invoice" button |
| **New Invoice** | Customer details, dynamic item rows, **live total calculations**, GST 3% toggle (CGST/SGST split), old-gold exchange, round-off, amount paid, balance due, notes |
| **Invoice View** | Clean printable invoice layout; buttons: Send on WhatsApp, Share PDF, Print, Mark as Paid, Edit |
| **History** | All invoices, search by customer name, filter by date range, tap to open, long-press to delete |
| **Settings** | Shop name/address/phone/GSTIN, default making %, GST default, theme choice |

### Technical foundation
- **Language/Arch:** Kotlin, MVVM, single-Activity + Navigation Component
- **UI:** Material Design 3, ViewBinding, RecyclerView, navy/gold/cream theme
- **Async:** Coroutines · **State:** ViewModel + LiveData
- **minSdk 26, targetSdk 34**
- Builds cleanly → produces `app-debug.apk` (~6.8 MB)

### Billing logic (working live)
- Rate per carat derived from 24K rate (22K = 24K × 22/24, etc.)
- Gold value, making charges (**% OR flat ₹/gram**), item total
- GST 3% split into CGST 1.5% + SGST 1.5%
- Old-gold exchange deduction, round-off, balance due

### WhatsApp (decided & partly built)
- **Approach chosen: FREE WhatsApp deep link** (`wa.me`) — no Business API, no monthly
  cost, no server. Right choice for <100 messages/month.
- The "Send on WhatsApp" button **works now** — opens WhatsApp with a text invoice summary
  to the customer's number.
- Sending the actual **PDF** attachment comes after PDF generation (backend phase).
- Code isolated in `core/util/WhatsAppShare.kt` so a paid API could be swapped in later.

---

## 3. What is NOT done yet ⏳ (the backend phase)

1. **Room database** — save invoices permanently on the phone (offline, free, no server).
   *(The Room library is already added to Gradle — just not connected to screens yet.)*
2. **DataStore** — make Settings persist.
3. **PDF generation** (`PdfGenerator.kt`) — then the **Share PDF** and **Print** buttons
   will work, and WhatsApp can attach the PDF.
4. Wire WhatsApp PDF attachment via FileProvider (FileProvider already declared in manifest).

> **Stubs to know about:** Share PDF and Print buttons currently show a small message
> ("comes in backend phase"). That is expected, not a bug.

---

## 4. Key decisions already made (so we don't re-discuss)

- **No online/cloud database needed.** A local **Room (SQLite)** database on the phone is
  enough for a single shop — free, offline. Cloud sync is optional and only if multiple
  phones are needed later.
- **WhatsApp = free deep link**, not the paid Business API (cost wasn't justified at
  <100 msgs/month).
- **Data model is future-proofed** with real jeweller fields (old-gold exchange, flat ₹/g
  making, HUID/hallmark, CGST/SGST split, round-off) so the database schema won't need
  painful changes later.
- **Fonts:** using system serif/sans-serif as Playfair/Lato stand-ins so it builds with zero
  setup. To use exact fonts: Android Studio → New → Font resource (instructions in
  `app/src/main/res/values/themes.xml`).

---

## 5. Project structure (where things live)

```
com.swarnabook.billing/
├── SwarnaBookApp.kt            App entry point
├── MainActivity.kt             Hosts navigation + bottom bar
├── core/util/
│   ├── Calculations.kt         All billing math (pure functions)
│   ├── CurrencyFormat.kt       ₹ Indian formatting (1,00,000)
│   └── WhatsAppShare.kt        Free WhatsApp sending
├── data/
│   ├── model/Models.kt         Invoice, InvoiceItem, ShopSettings, Carat
│   └── SampleData.kt           ⚠️ TEMPORARY in-memory store (replace with Room next)
└── ui/
    ├── splash/  dashboard/  newinvoice/  invoiceview/  history/  settings/
        (each: Fragment + ViewModel + Adapter where needed)
res/  → layouts, colors, theme, drawables, icons, navigation graph
```

**Most important file for the next phase:** `data/SampleData.kt`. It deliberately mimics the
API a real database repository will have (same function names like `upsert`, `getById`,
`delete`, `invoicesLive`). So swapping it for Room should NOT require changing the screens.

---

## 6. Bug fixed during testing

- **Launch crash** ("does not have a NavController set"): `MainActivity` was asking for the
  navigation controller too early. Fixed by getting it from the `NavHostFragment` directly.
  Resolved and pushed (commit `3d81e18`).

---

## 7. How to run / rebuild (cheat sheet)

Environment already set up on this Mac:
- SDK: `/Users/himanshup/Library/Android/sdk`
- Java: Temurin 21 (`/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`)
- Gradle: via `./gradlew` wrapper in the project

**Phone setup (one-time):** enable Developer Options (tap Build Number 7×) → turn on USB
Debugging → connect cable → set USB mode to "File transfer" → tap Allow on the popup.

**Check phone is connected:**
```
/Users/himanshup/Library/Android/sdk/platform-tools/adb devices
```

**Rebuild + install to phone (one command, from project folder):**
```
cd /Users/himanshup/Swarn_book
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew installDebug
```

**Just build the APK:**
```
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

> The app runs standalone on the phone — no cable/computer needed to *use* it. The cable is
> only needed to *reinstall* after code changes.

---

## 8. Resume checklist for next session

When we continue, the plan (in order):
1. [ ] Create Room entities from `data/model/Models.kt` (Invoice + InvoiceItem relation, ShopSettings)
2. [ ] Build DAOs (InvoiceDao, InvoiceItemDao) + AppDatabase
3. [ ] Create `InvoiceRepository` with the same API `SampleData` exposes
4. [ ] Swap screens from `SampleData` to the repository (minimal changes)
5. [ ] Persist Settings with DataStore
6. [ ] Build `PdfGenerator.kt` → enable Share PDF + Print + WhatsApp PDF attachment
7. [ ] Test on phone, commit, push

**To resume, just say:** "Let's start the backend phase" (or "continue from the doc").
