# Hanem Dashboard 🌓🏠

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Declarative_UI-green.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android_12+-black.svg?style=flat&logo=android)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Clean_%2B_MVVM-orange.svg?style=flat)](https://developer.android.com/topic/architecture)

**Transform legacy hardware into an industrial-grade "Living Canvas."**

Hanem is a high-fidelity, highly resilient Smart Home Dashboard designed specifically for permanent, wall-mounted kiosk installations. While most ambient displays are dangerous for long-term hardware health (causing severe burn-in, ghosting, and battery bloating), Hanem is engineered from the sub-pixel level up to solve the physical limitations of legacy IPS and OLED panels. It operates completely autonomously, merging data-rich telemetry with museum-grade aesthetics.

---

## 🖼️ Visual Experience & Interface Modules

|    Landscape Perspective     |
|:----------------------------:|
| ![](./screenshots/Night.png) |

Hanem's UI is built entirely in **Jetpack Compose**, relying on declarative graphics, custom `Canvas` drawing, and hardware-accelerated animations. 

### Core Dashboard Modules:
1. **The Weather King**: A massive, typography-heavy atmospheric display. It features a reactive, 10-hour sliding window forecast that constantly shifts based on the current hour, alongside a 5-day predictive layout.
2. **Astro-Dune Celestial Tracker**: A custom mathematical `Canvas` that renders the sun and moon on a **Quadratic Bezier Path**. The landscape dynamically shifts based on exact localized dawn, noon, and sunset data fetched from the Aladhan API.
3. **Dynamic Digital Art Frame**: Choose your dashboard centerpiece. Fetch the **NASA Astronomy Picture of the Day (APOD)** for daily cosmos updates, or switch to the **Local Gallery** engine which seamlessly crossfades your personal ambient mode images every 30 seconds using Coil's internal transitions.
4. **"On This Day" History Engine**: A curated historical events timeline (`assets/history.json`) that locks to your local calendar, providing exactly one profound historical moment per 24-hour cycle. It operates entirely offline with zero latency.
5. **Ultra-Minimal Ambient Mode**: A secondary, distraction-free state designed for pure aesthetics. With a single tap, detailed telemetry fades away, leaving a hyper-minimalist digital art frame and a subtle clock, maximizing screen protection.

---

## ⚙️ Interactive Dashboard Settings (DataStore Powered)

Forget hardcoding JSON configs. Hanem features a powerful, interactive settings panel built directly into the UI (accessible via the Ambient Mode overlay). It is powered by an atomic, type-safe `DataStore` engine:

* **Dual-Engine Location System**: 
  * **Auto (GPS)**: Utilizes a resilient, multi-layered Google Play Services `FusedLocationProviderClient`.
  * **Manual Search**: A bulletproof manual input field backed by Android's native `Geocoder` to search and lock your exact city if you prefer zero GPS battery drain.
* **Reactive Cache Invalidation**: The moment you update your location, a reactive `Flow` watcher (`distinctUntilChanged`) instantly fires, immediately invalidating the stale cache and force-fetching localized Open-Meteo Weather and Aladhan prayer times.
* **Visual Theme Control**: Toggle between **Auto** (shifts naturally based on calculated sunset/sunrise times), forced **Light**, or forced **Dark** rendering modes on the fly.
* **Papyrus / Straw Paper Aesthetic**: The Light Mode theme has been meticulously engineered to reduce eye strain, trading stark whites for a gorgeous, warm "Papyrus and Cream" palette.
* **Active Hours (Sleep Engine)**: Define custom `Wake` and `Sleep` schedules natively in the UI. A background `AlarmManager` and `BroadcastReceiver` manage when the tablet should dim or wake, dramatically extending battery lifespan and preventing screen damage overnight.

---

## 🛡️ The Hardware Problem: "Zero-Static" Defense

Standard Android UI is "static-deadly." Running an app 24/7 on an older tablet causes permanent sub-pixel degradation (Burn-in) and liquid crystal fatigue. Hanem solves this through a proprietary **Mathematical Motion Engine** that ensures no single pixel remains in the same state for more than 45 seconds.

* **The Macro-Shift (4-Point Rotation)**: The entire UI container is bound to a deterministic, sequential rotation engine. Every 45 seconds, the layout performs a micro-shift: `(1,1) → (-1,1) → (-1,-1) → (1,-1)`. This ±1dp movement is visually invisible from a distance but guarantees that every high-contrast edge physically moves across different sub-pixels.
* **MicroScale "Breathing"**: For high-contrast elements (like NASA space imagery), static bright pixels can cause image retention. Hanem implements a slow, continuous scale loop from `1.0f` to `1.02f` over 45 seconds to keep edges blurred over time.
* **Surface Tone Oscillation**: To prevent OLED voltage fatigue, a 5-minute background heartbeat silently oscillates card surfaces between elegant tones (e.g., Papyrus to Warm Cream in Light Mode), keeping RGB voltage values constantly in flux.
* **Text Alpha Shifting**: The opacity of secondary text elements randomly shifts between `0.40f` and `0.55f` every 45 seconds, preventing static luminance burn-in.

---

## ⚡ The "Silver Bullet" API & Data Architecture

Hanem is built for 24/7 uptime in environments with unstable internet. The data flow is designed to be **unbreakable**, executing precisely timed fetch schedules while strictly adhering to the **Unidirectional Data Flow (UDF)** pattern.

### Fetch Scheduling Logic (`WorkManager`):
* **`00:00` (Midnight Crossing)**: A heartbeat radar detects the day-rollover. It instantly invalidates the cache and fetches fresh **Weather** and **Prayer/Astro** data to ensure dawn and sunrise calculations are perfectly accurate before morning.
* **`08:00` (Morning Sync)**: The primary `WorkManager` sync fires. It fetches the latest **Weather** and **NASA APOD** (which usually updates on US time).
* **`17:00` (Evening Transition)**: A secondary Weather sync fires to guarantee the 10-hour sliding window has the most accurate data for the transition into the night.

### Resilience Mechanisms:
* **Repository Single Source of Truth**: The UI *never* reads directly from the network. Data always flows unidirectionally from SQLite (`Room` DB) `Flow`s to the UI. Remote API calls (`Retrofit`) surgically update the DB cache, automatically triggering UI recomposition.
* **Reactive Network Recovery**: Built on `ConnectivityManager.NetworkCallback`. If the tablet drops offline during a scheduled update, the UI displays cached data without crashing. The exact millisecond Wi-Fi returns, a Flow observer triggers a silent background catch-up.
* **API Debounce Shield**: A strict 15-minute software throttle blocks "Hammering" during network instability, protecting your IP from API rate-limit bans (critical for Open-Meteo and NASA APIs).

---

## 🛠️ True Kiosk: Hardware Setup Guide

Hanem is designed to turn a general-purpose tablet into a specialized appliance. For the best 24/7 experience, configure your tablet with these hardware-level settings:

1.  **Screen Timeout to "Never"**: Go to your tablet's *Settings -> Display -> Screen Timeout* (or Sleep) and set it to **"Never"** (or the maximum allowed duration).
2.  **Stay Awake**: Enable Developer Options on your tablet and toggle **"Stay Awake"** (Screen will never sleep while charging).
3.  **The "Hardware Lung" Strategy (Battery Management)**:
    * **Do NOT leave the tablet charging 24/7.** Constant 100% voltage will cause the lithium-ion battery to bloat and destroy the screen from the inside.
    * **Smart Plug Setup**: Use a cheap smart plug timer to cycle the battery. *Example Setup:* Run the screen from 08:00 to 23:00. Set the smart plug to charge for only 2 hours in the morning and 2 hours in the evening.
    * *Tip:* Observe your specific tablet's battery drain and adjust the smart plug schedule to keep the battery level oscillating roughly between 20% and 80%.

---

## 🏗️ Technical Stack

* **UI**: 100% Jetpack Compose (Declarative Graphics, Custom Canvas, Animation APIs, Material 3)
* **Architecture**: MVVM + Clean Architecture with Repository Pattern + Unidirectional Data Flow
* **Dependency Injection**: Hilt
* **Concurrency**: Kotlin Coroutines & Flow (StateFlow / SharedFlow)
* **Persistence**: Room Database (Cache) & DataStore Preferences (Atomic configuration state)
* **Networking**: Retrofit + OkHttp (Custom Logging & Interceptors)
* **Background Tasks**: WorkManager (Scheduled daily updates & idempotent retries), AlarmManager (Wake schedules)
* **Location**: Google Play Services `FusedLocationProviderClient` & Native Android Geocoder
* **Image Loading**: Coil (with HD URI permission persistence & Crossfade animations)

---

## ⚖️ License
Project developed for professional portfolio demonstration. All rights reserved.