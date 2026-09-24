# Speccy OS

**[English](README.md) · [Español](README.es.md)**

**A retro frontend for Android.** It organises your collection, makes it look
good, and launches every game with the right emulator — tuning the device before
the game starts.

Free, no ads, no subscriptions. **No games or BIOS files included**: it is the
shop window and the conductor for what you already own.

[![Google Play](https://img.shields.io/badge/Google_Play-Speccy_OS-3DDC84?logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.generacionarcade.speccyos)
![Android 7+](https://img.shields.io/badge/Android-7.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)

---

## Where it comes from

Speccy OS started with one very specific goal: **getting the most out of a GameMT
E5 Ultra**. An honest Chinese handheld, with good controls and a decent screen,
but a poor stock launcher and a chip you have to treat properly before it will
run a PS2 game.

Doing that right — reading the real hardware, raising clocks only when they are
needed, finding the BIOS where the emulator actually looks for it, picking a core
that really boots — turned out to be exactly what **every** Android handheld
needs. So today Speccy OS works just as well on a Retroid Pocket 6, an AYN Odin 3,
a phone with a controller or a TV with a Fire TV Stick — and, of course, on the
**whole new GameMT range** (EX8, EX5, E6, E5 Ultra…).

The E5 Ultra is still the reference device where every release is tested, but it
is no longer the only one that matters.

---

## What it does

**Library**
- Scans your ROM folders and sorts them by system, with box art, videos and
  metadata fetched by the built-in scraper (ScreenScraper).
- Five themes: Ultra HD, Speccy OS, Pure, **Studio** (system rail with real logos
  plus a game grid) and **Dual Screen**, made for foldables and dual-screen
  handhelds (AYANEO Pocket DS, ONEXSUGAR, Anbernic RG DS).
- Recents, favourites, daily challenges and "resume where you left off".

**Performance (the part that actually matters)**
- Recognises **95 handhelds, phones and TV boxes**, and falls back to guessing the
  tier from the chipset when the device is unknown.
- Before launching a game it applies the profile that system needs: CPU and GPU
  governor, **frequency floor** (the only thing many MediaTek and Unisoc kernels
  respect), I/O scheduler, fan and Android's Game Mode.
- Thermal watch with hysteresis: it steps the profile down at the ceiling and
  brings it back once the device cools, instead of leaving you in power-save mode
  for the rest of the session.
- Works **with root, with Shizuku, or with neither** (in that last case it does
  what a normal app can, and says so plainly).

**Emulators**
- Launches RetroArch with the right core, or the matching standalone emulator:
  Dolphin, ARMSX2, NetherSX2, DuckStation, PPSSPP, Flycast, Redream, Azahar,
  Eden, Vita3K, aX360e, Winlator, GameNative and a few more.
- **Picks the core by device tier**: on a modest handheld PS1 runs on
  `pcsx_rearmed` rather than the inherited `mednafen_psx`, which is a pure
  interpreter. And it learns: if a core fails to boot, the next one is tried.
- BIOS checker for 24 systems that looks **in the folder cores actually read**,
  not where one assumes.
- Auto save states, RetroAchievements and states shared with RetroArch.

---

## Supported handhelds and devices

Any Android 7 or newer will do. These are the ones Speccy OS **recognises by
name**, with their own performance and cooling profile:

### AYANEO

| Model | SoC | RAM | Up to |
|---|---|---|---|
| AYANEO Pocket S Mini | Snapdragon G3x Gen 2 | 8/12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket AIR Mini | Helio G90T | 4/6/8 GB | Dreamcast · PSP · NDS |
| AYANEO KONKR Pocket FIT | Snapdragon G3 Gen 3 / 8 Elite | 12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket S2 | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket EVO | Snapdragon G3x Gen 2 | 12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket DS (doble pantalla) | Snapdragon G3x Gen 2 | 12 GB | Switch · Wii U · Windows |
| AYANEO Pocket DMG | Snapdragon G3x Gen 2 | 12 GB | Switch · Wii U · Windows |
| AYANEO Pocket ACE | Snapdragon G3x Gen 2 | 12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket MICRO Classic | Helio G99 | 6/8 GB | Dreamcast · PSP · NDS |
| AYANEO Pocket S | Snapdragon G3x Gen 2 | 12/16 GB | Switch · Wii U · Windows |
| AYANEO Pocket MICRO | Helio G99 | 6/8 GB | Dreamcast · PSP · NDS |
| AYANEO Pocket AIR | Dimensity 1200 | 8/12 GB | full PS2 · 3DS |

### AYN

| Model | SoC | RAM | Up to |
|---|---|---|---|
| AYN Thor (Base / Pro / Max) | Snapdragon 8 Gen 2 | 8/12/16 GB | Switch · Wii U · Windows |
| AYN Odin 3 | Dragonwing Q8 (Snapdragon 8 Elite) | 12/16 GB | Switch · Wii U · Windows |
| AYN Odin 2 Portal | Snapdragon 8 Gen 2 | 12 GB | Switch · Wii U · Windows |
| AYN Odin 2 Mini | Snapdragon 8 Gen 2 | 8/12 GB | Switch · Wii U · Windows |
| AYN Odin 2 / Pro / Max | Snapdragon 8 Gen 2 | 8/12/16 GB | Switch · Wii U · Windows |
| AYN Odin Lite | Dimensity 900 | 4/6/8 GB | GameCube · Wii · mid PS2 |
| AYN Odin Pro / Base | Snapdragon 845 | 4/8 GB | GameCube · Wii · mid PS2 |

### Anbernic

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Anbernic RG477M | Dimensity 8300 | 12 GB | Switch · Wii U · Windows |
| Anbernic RG477V | Dimensity 8300 | 8/12 GB | Switch · Wii U · Windows |
| Anbernic RG476H | Unisoc T820 | 8 GB | GameCube · Wii · mid PS2 |
| Anbernic RG DS / DS Plus (doble pantalla) | Rockchip RK3568 | 3 GB | PS1 · N64 · Saturn |
| Anbernic RG557 | Dimensity 8300 | 8/12 GB | Switch · Wii U · Windows |
| Anbernic RG Vita | Snapdragon 865 | 8 GB | full PS2 · 3DS |
| Anbernic RG Slide | Unisoc T820 | 8 GB | GameCube · Wii · mid PS2 |
| Anbernic RG556 | Unisoc T820 | 8 GB | GameCube · Wii · mid PS2 |
| Anbernic RG Cube / Cube XX | Unisoc T820 | 8 GB | GameCube · Wii · mid PS2 |
| Anbernic RG406V / RG406H | Unisoc T820 | 8 GB | GameCube · Wii · mid PS2 |
| Anbernic RG505 | Unisoc T618 | 4 GB | Dreamcast · PSP · NDS |
| Anbernic RG405M / RG405V | Unisoc T618 | 4 GB | Dreamcast · PSP · NDS |
| Anbernic RG353 (Android) | Rockchip RK3566 | 1/2 GB | PS1 · N64 · Saturn |

### GameMT

| Model | SoC | RAM | Up to |
|---|---|---|---|
| GameMT E5 Ultra | Unisoc T620 | 6 GB | GameCube · Wii · mid PS2 |
| GameMT EX8 | Helio G99 | 6/8 GB | GameCube · Wii · mid PS2 |
| GameMT EX5 (PSK5000) | Helio G81 | 4 GB | Dreamcast · PSP · NDS |
| GameMT E5 Plus (GammaOS) | Rockchip RK3566 | 4 GB | Dreamcast · PSP · NDS |
| GameMT E5 Plus (Stock) | Rockchip RK3566 | 4 GB | Dreamcast · PSP · NDS |
| GameMT E6 | Unisoc T616 | 4 GB | Dreamcast · PSP · NDS |

### Retroid

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Retroid Pocket G2 | Snapdragon G2 Gen 2 | 8 GB | full PS2 · 3DS |
| Retroid Pocket Nova | QCS8550 (Snapdragon 8 Gen 2) | 8/12 GB | Switch · Wii U · Windows |
| Retroid Pocket Classic | Snapdragon G1 Gen 2 | 4/6 GB | GameCube · Wii · mid PS2 |
| Retroid Pocket 6 | Snapdragon 8 Gen 2 | 8/12 GB | Switch · Wii U · Windows |
| Retroid Pocket Flip 2 | Snapdragon 865 | 8 GB | full PS2 · 3DS |
| Retroid Pocket 5 | Snapdragon 865 | 8 GB | full PS2 · 3DS |
| Retroid Pocket Mini / Mini V2 | Snapdragon 865 | 6/8 GB | full PS2 · 3DS |
| Retroid Pocket 4 Pro | Dimensity 1100 | 8 GB | GameCube · Wii · mid PS2 |
| Retroid Pocket 4 | Dimensity 900 | 4/6 GB | GameCube · Wii · mid PS2 |
| Retroid Pocket 2S | Unisoc T610 | 3/4 GB | Dreamcast · PSP · NDS |
| Retroid Pocket 3+ | Unisoc T618 | 4 GB | Dreamcast · PSP · NDS |

### ASUS

| Model | SoC | RAM | Up to |
|---|---|---|---|
| ASUS ROG Phone 9 / Pro | Snapdragon 8 Elite | 12/16/24 GB | Switch · Wii U · Windows |
| ASUS ROG Phone 8 / Pro | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |

### Abxylute

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Abxylute One | Dimensity 900 | 8 GB | GameCube · Wii · mid PS2 |

### Amazon

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Fire TV Stick 4K Max | MT8696 (Cortex-A73) | 2 GB | PS1 · N64 · Saturn |
| Fire TV Cube (3ª gen) | Octa-core A73/A53 | 2 GB | Dreamcast · PSP · NDS |

### GPD

| Model | SoC | RAM | Up to |
|---|---|---|---|
| GPD XP Plus | Dimensity 1200 | 6/8 GB | GameCube · Wii · mid PS2 |

### Google

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Pixel 9 / 9 Pro | Tensor G4 | 12/16 GB | full PS2 · 3DS |
| Pixel 8 / 8 Pro | Tensor G3 | 8/12 GB | full PS2 · 3DS |
| Chromecast con Google TV 4K | Amlogic S905X3 | 2 GB | PS1 · N64 · Saturn |

### KT Pocket

| Model | SoC | RAM | Up to |
|---|---|---|---|
| KT-R1 / KT Pocket | Helio G99 | 4/6/8 GB | GameCube · Wii · mid PS2 |

### Kinhank

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Kinhank Super Console X (Android) | Amlogic S905X | 2 GB | 8/16-bit |

### Lenovo

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Lenovo Legion Y700 (3ª gen) | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |
| Lenovo Legion Y700 (2ª gen) | Snapdragon 8+ Gen 1 | 8/12/16 GB | full PS2 · 3DS |

### Logitech

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Logitech G Cloud | Snapdragon 720G | 4 GB | Dreamcast · PSP · NDS |

### Mangmi

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Mangmi Pocket Max | Snapdragon 8 Gen 2 | 12 GB | Switch · Wii U · Windows |

### NVIDIA

| Model | SoC | RAM | Up to |
|---|---|---|---|
| NVIDIA Shield TV Pro | Tegra X1+ | 3 GB | GameCube · Wii · mid PS2 |
| NVIDIA Shield TV (2019 tubo) | Tegra X1+ | 2 GB | Dreamcast · PSP · NDS |

### Nubia

| Model | SoC | RAM | Up to |
|---|---|---|---|
| RedMagic 10 Pro | Snapdragon 8 Elite | 12/16/24 GB | Switch · Wii U · Windows |
| RedMagic 9 Pro | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |

### ONEXPLAYER

| Model | SoC | RAM | Up to |
|---|---|---|---|
| ONEXSUGAR Sugar 1 (plegable doble pantalla) | Snapdragon G3x Gen 2 | 12 GB | Switch · Wii U · Windows |

### OnePlus

| Model | SoC | RAM | Up to |
|---|---|---|---|
| OnePlus 13 | Snapdragon 8 Elite | 12/16/24 GB | Switch · Wii U · Windows |
| OnePlus 12 | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |

### POCO

| Model | SoC | RAM | Up to |
|---|---|---|---|
| POCO X7 Pro | Dimensity 8400 Ultra | 8/12 GB | full PS2 · 3DS |
| POCO F6 Pro | Snapdragon 8 Gen 2 | 12/16 GB | Switch · Wii U · Windows |
| POCO X6 Pro | Dimensity 8300 Ultra | 8/12 GB | full PS2 · 3DS |
| POCO F5 Pro | Snapdragon 8+ Gen 1 | 8/12 GB | full PS2 · 3DS |

### Powkiddy

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Powkiddy X55 | Rockchip RK3566 | 2/4 GB | PS1 · N64 · Saturn |
| Powkiddy X28 | Unisoc T618 | 4 GB | Dreamcast · PSP · NDS |

### Razer

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Razer Edge | Snapdragon G3x Gen 1 | 6/8 GB | full PS2 · 3DS |

### Samsung

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Galaxy S25 Ultra | Snapdragon 8 Elite | 12/16 GB | Switch · Wii U · Windows |
| Galaxy Z Fold 7 | Snapdragon 8 Elite | 12/16 GB | Switch · Wii U · Windows |
| Galaxy S24 Ultra | Snapdragon 8 Gen 3 | 12 GB | Switch · Wii U · Windows |
| Galaxy Z Fold 6 | Snapdragon 8 Gen 3 | 12 GB | Switch · Wii U · Windows |
| Galaxy Z Flip 6 | Snapdragon 8 Gen 3 | 12 GB | full PS2 · 3DS |
| Galaxy Tab S10 Ultra / S10+ | Dimensity 9300+ | 12/16 GB | Switch · Wii U · Windows |
| Galaxy S23 Ultra | Snapdragon 8 Gen 2 | 8/12 GB | Switch · Wii U · Windows |
| Galaxy Z Fold 5 | Snapdragon 8 Gen 2 | 12 GB | Switch · Wii U · Windows |
| Galaxy Tab S9 / S9+ / Ultra | Snapdragon 8 Gen 2 | 8/12/16 GB | Switch · Wii U · Windows |
| Galaxy S22 Ultra | SD 8 Gen 1 / Exynos 2200 | 8/12 GB | full PS2 · 3DS |

### Tanix

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Tanix TX3 Mini | Amlogic S905W | 2 GB | 8/16-bit |

### Walmart

| Model | SoC | RAM | Up to |
|---|---|---|---|
| onn. 4K Pro (Google TV) | Amlogic S905X4-J | 3 GB | Dreamcast · PSP · NDS |

### Xiaomi

| Model | SoC | RAM | Up to |
|---|---|---|---|
| Xiaomi 15 / 15 Pro / Ultra | Snapdragon 8 Elite | 12/16 GB | Switch · Wii U · Windows |
| Xiaomi 14 / Pro / Ultra | Snapdragon 8 Gen 3 | 12/16 GB | Switch · Wii U · Windows |
| Xiaomi TV Box S (2ª gen) | Amlogic S905Y4 | 2 GB | PS1 · N64 · Saturn |

---

## Screenshots

| Library | Studio theme (GameMT EX8) |
|---|---|
| ![Library](docs/capturas/biblioteca.png) | ![Studio theme](docs/capturas/tema-studio-ex8.png) |

| Performance profile | Thermal control | BIOS checker |
|---|---|---|
| ![Performance](docs/capturas/rendimiento.png) | ![Thermal](docs/capturas/control-termico.png) | ![BIOS](docs/capturas/comprobador-bios.png) |

| Dual screen and foldables | Controller |
|---|---|
| ![Dual screen](docs/capturas/dos-pantallas.png) | ![Controller](docs/capturas/mando.png) |

---

## What actually improves: measurements

A pretty frontend does not make a game run better. What does is **how the device
is configured at the moment you press play**. These are real measurements, not
estimates.

**Test device:** GameMT EX8 (MediaTek Helio G99, Mali-G57, 8 GB, Android 14), with
Shizuku granted. **Method:** same game running throughout; 15 samples of the big
cluster's `scaling_cur_freq` (`policy6`), GPU frequency and `thermal_zone0` before
and after applying the profile, without restarting the game. Every run was
verified with a screenshot of the game actually on screen.

### Nintendo 64 — AeroGauge (RetroArch · mupen64plus-next)

| | Average CPU | Minimum CPU | Temperature |
|---|---|---|---|
| No profile (as the system leaves it) | 725 MHz | 725 MHz | 45 °C |
| **With the Speccy OS profile** | **2200 MHz** | **2200 MHz** | 47 °C |

The average is not the interesting part: it is that **the governor left the big
cluster at its minimum, 725 MHz, while emulating a Nintendo 64**. Emulators load
one or two threads in bursts, and Android's scheduler reads that as "no power
needed". That is where stutter in supposedly easy games comes from.

### PSP — Dragon Ball Z Shin Budokai 2 (PPSSPP)

| | Average CPU | Minimum CPU | Speed |
|---|---|---|---|
| No profile | 725 MHz | 725 MHz | 60/60 fps |
| **With the Speccy OS profile** | **1400 MHz** | **1400 MHz** | 60/60 fps |

Here the game was already running at full speed, and the lesson is the opposite
one: **the profile is not "max everything"**. Speccy OS applies the tier that
system needs — PSP sits in the middle step — instead of cooking the battery.

### Why it works: the frequency floor

Many MediaTek and Unisoc kernels **ignore** the `performance` governor: their own
power manager overrides it. What they do respect is `scaling_min_freq`. Speccy OS
sets that floor (60 % of maximum in Performance mode, the maximum in Extreme), and
that is why the clock stops dropping mid-session. It is the difference between a
high average with dips and a **sustained clock**.

---

## Compared with other frontends

Speccy OS does not compete on theme count or platform count: it competes on what
happens **when you press play**. Against the two reference Android frontends,
based on what each one documents:

| | Speccy OS | Daijishō | ES-DE |
|---|---|---|---|
| Library, scraper and themes | ✅ | ✅ | ✅ |
| Price | Free, no ads | Free | Paid on Android |
| CPU/GPU profile **per system** on launch | ✅ | ❌ | ❌ |
| Frequency floor (MediaTek/Unisoc kernels) | ✅ | ❌ | ❌ |
| Handheld fan control | ✅ | ❌ | ❌ |
| Android Game Mode for the emulator | ✅ | ❌ | ❌ |
| Thermal watch with hysteresis | ✅ | ❌ | ❌ |
| 95-device catalogue with real profiles | ✅ | ❌ | ❌ |
| BIOS checker (24 systems, MD5 verified) | ✅ | ❌ | ❌ |
| Core chosen by device tier | ✅ | ❌ | ❌ |
| Works with root, Shizuku or neither | ✅ | — | — |

To tune clocks alongside Daijishō or ES-DE you need a second (root) app, set up by
hand for every game. In Speccy OS it is built in and applied automatically.

---

## Emulated systems

27 systems configured out of the box (and 175 folder names recognised while
scanning): NES, SNES, Mega Drive, Master System, Game Gear, Game Boy / Color /
Advance, Nintendo DS, Nintendo 64, GameCube, Wii, PlayStation, PlayStation 2, PSP,
PS Vita, Dreamcast, Saturn, Sega CD, Naomi, Atomiswave, Xbox, Xbox 360, 3DS,
Neo Geo, PC Engine, MSX, Amiga, DOS and full arcade (MAME / FinalBurn Neo).

**Recognised emulators** (31 compatibility entries, each with a download link and
its requirements): RetroArch, Dolphin, ARMSX2, NetherSX2/AetherSX2, Play!,
DuckStation, PPSSPP, Flycast, Redream, Yaba Sanshiro, Supermodel, mupen64plus FZ,
melonDS, DraStic, Azahar, Lime3DS, Mandarine, Borked3DS, Panda3DS, Eden, Citron,
Sudachi, Torzu, Uzuy, Kenji-NX, Vita3K, aX360e, X1 BOX, Xanite, Cemu, RPCS3,
Winlator, GameNative, GameHub, MiceWine, MAME4droid and Robert Broglia's emulators.

---

## Installing

**The normal way:** [get it from Google Play](https://play.google.com/store/apps/details?id=com.generacionarcade.speccyos).

**Building it yourself:**

```bash
git clone https://github.com/lorpagc-art/speccyos-frontend-retro.git
cd speccyos-frontend-retro
./gradlew assemblePlaystoreDebug
```

The APK lands in `app/build/outputs/apk/playstore/debug/`.

You need the Android SDK (API 36) and JDK 17. The project has three flavours:
`playstore` (the store build), `website` and `systemos` — the last one includes an
accessibility service for system-level features, which does **not** ship in the
Google Play build.

For a signed build, add the keystore path and passwords to `local.properties`.
That file, `google-services.json` and any key are deliberately kept out of this
repository.

### Getting everything out of it (optional)

With no special permissions, Speccy OS organises, launches and warns you. For it
to also tune the device, it needs one of these:

- **Root**, if your handheld has it.
- **[Shizuku](https://shizuku.rikka.app/)**, which does not need root: install it,
  start it over ADB (or with root) and grant Speccy OS permission once. On
  handhelds whose `adbd` already runs as root — many Chinese ones do — Shizuku
  inherits that level and control is complete.

---

## Built with

Kotlin and Jetpack Compose · Media3/ExoPlayer for video · Coil for images · Room
for caching · Firebase (Remote Config, App Check, Crashlytics) · AIDL over Shizuku
for the privileged service · Gradle with three flavours and R8 on release builds.

Performance engine architecture: `SpeccyHardwareRegistry` (device catalogue) →
`SpeccySysfsProbe` (probes the real hardware — no hardcoded paths) →
`SpeccyPerformanceTuner` (applies and verifies) → `PerformanceService` (executes
with privileges, behind a path and value allowlist).

---

## Legal notice

Speccy OS **does not include or distribute games, BIOS files or emulators**. It is
an organiser and launcher: users provide their own files and choose which
emulators to install. Console trademarks and logos belong to their respective
owners and are used only to identify each system inside the interface.

---

## Credits

Developed by **Speccy81** (LORPAGC) for **LV-Webstudio** · 2026.
Published on Google Play under the developer account **Speccy**.

- Web: [lv-webstudio.com](https://lv-webstudio.com)
- Email: **administracion@lv-webstudio.com**
- Google Play: [Speccy OS: Emulation Frontend](https://play.google.com/store/apps/details?id=com.generacionarcade.speccyos)

LV-Webstudio is a web and app development studio based in Granada, Spain. If you
run a business and need a website, an online shop or a custom app, get in touch.

---

## Licence

Proprietary — see [LICENSE](LICENSE). All rights reserved.

In short: you may read the code, study it and build a copy for your own devices.
You may not redistribute it, publish it on any app store or make commercial use
of it. For anything else, write to administracion@lv-webstudio.com.
