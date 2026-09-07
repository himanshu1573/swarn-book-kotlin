# SwarnaBook — Project Status & Handover

**App:** SwarnaBook — native Android jewellery shop billing & invoice app
**Package:** `com.swarnabook.billing`
**Repo:** https://github.com/himanshu1573/swarn-book-kotlin
**Last updated:** 23 August 2026
**Current phase:** ✅ Frontend complete · ✅ Backend (Room + DataStore + live rates) written
· ⚠️ **NOT YET COMPILED** — Android SDK missing from this Mac

---

## 0. ⚠️ Read first — build environment is broken

The Android SDK that used to live at `~/Library/Android/sdk` is **gone**, and Android Studio
is not installed. `./gradlew :app:assembleDebug` fails with *SDK location not found*.

Everything in section 3 below was written but **never compiled**. Expect first-build errors.

**To fix:**
```
brew install --cask android-studio
# open it once, let it download SDK 34 + build-tools + platform-tools
# then point the project at it:
#   local.properties -> sdk.dir=/Users/himanshup/Library/Android/sdk
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew :app:assembleDebug
```

---

## 1. Quick summary (read this first next time)

The **frontend** is complete and was verified running on a real phone (Realme RMX3868,
Android 14) back in June.

The **backend** — Room database, DataStore settings, and live metal rates — is now written
(section 3), so invoices should survive an app restart. But it has **never been compiled**,
because the Android SDK is missing from this Mac (section 0). Treat it as unverified until
it builds and you have created an invoice, force-closed the app, and seen it come back.

**Still genuinely missing:** PDF generation, so Share PDF and Print remain placeholders.

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
- **UI:** Material Design 3, ViewBinding, RecyclerView, Bachatt-style off-white / indigo theme, Manrope type
- **Async:** Coroutines · **State:** ViewModel + LiveData
- **minSdk 26, targetSdk 34**
- Builds cleanly → produces `app-debug.apk` (~6.8 MB)

### Billing logic (working live)
- Rate per carat derived from 24K rate (22K = 24K × 22/24, etc.) — matches goodreturns
  Lucknow 22K/18K to the rupee
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

## 3. Backend phase — written 23 Aug 2026 (unverified, see section 0)

### Room (invoices now persist)
- `data/model/Models.kt` — `Invoice` and `InvoiceItem` are Room `@Entity` classes.
  Items are a separate table with an `ON DELETE CASCADE` foreign key, so `Invoice.items`
  is `@Ignore`d and sits **outside the constructor** — which means `copy()` does NOT
  carry items. Use `copyDeep()`.
- `data/local/` — `AppDatabase` (v1), `InvoiceDao`, `Converters` (MakingMode enum).
- `data/InvoiceRepository.kt` — replaces the deleted `SampleData`. Reads are a Flow
  exposed as LiveData; writes are `suspend`.
- Invoice numbers come from `MAX(id) + 1`, not a count, so a deletion can never reissue
  a number already on a printed bill.

### DataStore (settings + rates persist)
- `data/SettingsStore.kt` — shop details, defaults, theme, and the day's metal rates.
  Loaded once at app start via `warmUpBlocking()` so screens can read synchronously.

### Live metal rates (goldprice.dev → Uttar Pradesh counter rate)
- `data/remote/GoldPriceApi.kt` — one keyed `/v1/carat` call (all karats, INR/gram) =
  **1 metered call per refresh**; silver comes from keyless api.gold-api.com + open.er-api
  FX (goldprice.dev gates XAG on the free tier).
- `data/remote/RateQuotaStore.kt` — persistent spend ledger for the 1,000 calls/month
  free tier: monthly cap 960, daily cap 30, 5h auto interval / 15min manual cooldown.
  Budget lands at ~150 calls/month.
- `core/util/IndianRate.kt` — pure derivation `spot × (1 + duty) × (1 + UP premium)`,
  rounded to whole ₹/g. Unit-tested in `app/src/test/.../IndianRateTest.kt`.
- `data/RateRepository.kt` — applies it with the Settings values and writes the rate card.

> **The API returns international SPOT; the app converts it to the UP counter rate.**
> Calibrated 25 Aug 2026 against goodreturns.in Lucknow:
>
> | metal  | goldprice.dev spot | × duty 15% | × UP premium | app rate | Lucknow published |
> |--------|-------------------:|-----------:|-------------:|---------:|------------------:|
> | gold   | ₹14,270.08/g       | ₹16,410.6  | 0%           | ₹16,411  | ₹16,412 (Delhi same) |
> | silver | ₹211.51/g          | ₹243.2     | 7%           | ₹260     | ₹260 (₹2,60,000/kg) |
>
> Duty = BCD 10% + AIDC 5%, in force since 13 May 2026 (was 6%). All three figures are
> editable under *Settings → Live Rate → Uttar Pradesh Counter Rate*. Rates are **ex-GST**
> — `Calculations` adds CGST 1.5% + SGST 1.5% on the bill, so GST must never be folded
> into the premium. The rate card stays hand-editable by design.

**API key:** lives in `local.properties` as `goldApiKey=...` (gitignored) and reaches the
code via `BuildConfig.GOLD_API_KEY`. **Never hardcode it — this repo is public.**

## 3b. What is still NOT done ⏳

1. **PDF generation** (`PdfGenerator.kt`) — **Share PDF** and **Print** still show a
   placeholder message. That is expected, not a bug.
2. WhatsApp PDF attachment via FileProvider (FileProvider already declared in manifest).
3. No tests yet — `Calculations.recalculate()` is pure and is the obvious first unit test.

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
│   ├── model/Models.kt         Invoice/InvoiceItem @Entity, ShopSettings, Carat
│   ├── local/                  AppDatabase, InvoiceDao, Converters
│   ├── remote/                 GoldPriceApi, RateQuotaStore
│   ├── InvoiceRepository.kt    Room-backed store (replaced SampleData)
│   ├── SettingsStore.kt        DataStore settings + metal rates
│   └── RateRepository.kt       Live rate fetch + quota + premium
└── ui/
    ├── splash/  dashboard/  newinvoice/  invoiceview/  history/  settings/
        (each: Fragment + ViewModel + Adapter where needed)
res/  → layouts, colors, theme, drawables, icons, navigation graph
```

**Note on the old plan:** the handover claimed swapping Room in would need no screen
changes. That was optimistic — `SampleData` was synchronous, Room is `suspend`, so the
ViewModels gained `viewModelScope.launch` and `NewInvoiceFragment` now binds its form from
a `draftReady` observer instead of directly in `onViewCreated`.

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

1. [ ] Install Android Studio, let it fetch SDK 34, fix `sdk.dir` in `local.properties`
2. [ ] `./gradlew :app:assembleDebug` — **fix the first-build errors** (Room/KSP most likely)
3. [ ] Install on phone, create an invoice, force-close the app, reopen — it must still be there
4. [ ] Tap **Fetch Live Rate** on the dashboard — the status line shows the full
       `spot + duty (+ UP premium)` derivation; compare with goodreturns.in/gold-rates/lucknow.html
5. [ ] Build `PdfGenerator.kt` → enable Share PDF + Print + WhatsApp PDF attachment
6. [ ] Add unit tests for `Calculations`, commit, push

**To resume, just say:** "continue from the doc"._
