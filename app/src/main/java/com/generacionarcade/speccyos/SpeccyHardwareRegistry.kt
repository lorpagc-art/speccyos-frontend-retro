package com.generacionarcade.speccyos

import android.os.Build

/**
 * SpeccyHardwareRegistry
 * ----------------------------------------------------------------------------
 * Registro ÚNICO de hardware de SpeccyOS.
 *
 * Sustituye a las dos bases de datos paralelas y desincronizadas que existían
 * (`HardwareDatabase.profiles` y `HardwareControlManagerBeta.hardwareProfiles`),
 * que además provocaban un crash por `!!` sobre claves inexistentes.
 *
 * Cada dispositivo declara:
 *   - Identidad (marca, SoC, GPU, RAM, año)
 *   - Factor de forma (incluye DUAL_SCREEN y FOLDABLE -> SpeccyFoldableManager)
 *   - Tier de emulación (hasta dónde llega de verdad)
 *   - Refrigeración y techo térmico
 *   - Familia de SoC -> determina qué nodos sysfs se sondean (SpeccySysfsProbe)
 *   - Tuning recomendado de RetroArch por dispositivo (SpeccyPerformanceTuner)
 *
 * NADA de rutas sysfs fijas aquí: se descubren en runtime. Un perfil sólo
 * aporta VALORES, nunca rutas, que es lo que hacía frágil al sistema anterior.
 */
object SpeccyHardwareRegistry {

    // ------------------------------------------------------------------ tipos

    enum class FormFactor {
        HANDHELD_WIDE,      // 16:9 / 16:10 apaisada (Odin, RP5, RG556...)
        HANDHELD_VERTICAL,  // vertical tipo GameBoy (RG406V, Pocket Micro)
        HANDHELD_DUAL,      // DOS pantallas físicas (Ayaneo Flip DS, ONEXSUGAR)
        FOLDABLE,           // plegable con bisagra (Z Fold, Pocket Flip)
        PHONE,
        TABLET,
        TV,                 // TV box / dongle / Shield
        DESKTOP
    }

    /** Hasta dónde llega realmente el dispositivo. Se usa para filtrar y avisar. */
    enum class Tier(val label: String, val rank: Int) {
        T0_RETRO("8/16 bits", 0),
        T1_PSX("PS1 · N64 · Saturn básico", 1),
        T2_DC("Dreamcast · PSP · NDS", 2),
        T3_GC("GameCube · Wii · PS2 medio", 3),
        T4_PS2("PS2 completo · 3DS · Wii U básico", 4),
        T5_SWITCH("Switch · Wii U · Windows (Winlator)", 5)
    }

    enum class Cooling { PASSIVE, ACTIVE_FAN, ACTIVE_FAN_PWM }

    /**
     * Familia de SoC. Determina los CANDIDATOS de nodo sysfs y los nombres de
     * gobernador válidos. Es lo que permite soportar un dispositivo nuevo sin
     * conocer sus rutas exactas.
     */
    enum class SocFamily(
        val gpuGovBalanced: String,
        val gpuGovPerformance: String,
        val gpuNodeHints: List<String>
    ) {
        SNAPDRAGON(
            "msm-adreno-tz", "performance",
            listOf("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
        ),
        UNISOC(
            "simple_ondemand", "performance",
            listOf("/sys/class/devfreq/gpu/governor", "/sys/class/devfreq/60000000.gpu/governor")
        ),
        MEDIATEK(
            "simple_ondemand", "performance",
            listOf("/sys/class/devfreq/13000000.mali/governor", "/proc/gpufreq/gpufreq_opp_freq")
        ),
        ROCKCHIP(
            "simple_ondemand", "performance",
            listOf("/sys/class/devfreq/fde60000.gpu/governor", "/sys/class/devfreq/ff9a0000.gpu/governor")
        ),
        AMLOGIC(
            "simple_ondemand", "performance",
            listOf("/sys/class/devfreq/ffe40000.bifrost/governor")
        ),
        EXYNOS(
            "simple_ondemand", "performance",
            listOf("/sys/class/devfreq/gpufreq/governor")
        ),
        TEGRA(
            "nvhost_podgov", "userspace",
            listOf("/sys/devices/gpu.0/devfreq/57000000.gpu/governor")
        ),
        GENERIC("simple_ondemand", "performance", emptyList())
    }

    /**
     * Ajustes de RetroArch recomendados por dispositivo. Los consume
     * RetroArchPerformanceConfig al generar el appendconfig.
     */
    data class RaTuning(
        val videoDriver: String = "gl",       // gl | glcore | vulkan
        val threadedVideo: Boolean = true,
        val runAheadFrames: Int = 0,          // 0 = desactivado (coste de CPU alto)
        val runAheadSecondInstance: Boolean = false,
        val frameDelay: Int = 0,              // 0 = auto
        val audioLatencyMs: Int = 64,
        val maxShader: String = "none",       // none | crt-lite | crt-full
        val vsyncSwapInterval: Int = 1,
        val hardGpuSync: Boolean = false,
        val preferredInternalScale: Int = 1   // multiplicador de resolución interna
    )

    data class SpeccyDevice(
        val id: String,
        val name: String,
        val brand: String,
        val soc: String,
        val gpu: String,
        val ram: String,
        val year: Int,
        val form: FormFactor,
        val tier: Tier,
        val socFamily: SocFamily,
        val cooling: Cooling = Cooling.PASSIVE,
        val maxFanLevel: Int = 0,
        /** Tokens en minúsculas que se buscan en MODEL / DEVICE / BOARD / PRODUCT. */
        val match: List<String> = emptyList(),
        val defaultMode: String = "BALANCED",
        /** °C a partir de los cuales el tuner degrada el modo automáticamente. */
        val thermalCeiling: Int = 80,
        val tuning: RaTuning = RaTuning(),
        val dualScreen: Boolean = false,
        val notes: String = ""
    ) {
        val hasFan: Boolean get() = cooling != Cooling.PASSIVE

        val iconRes: Int
            get() = when (form) {
                FormFactor.HANDHELD_VERTICAL, FormFactor.PHONE -> R.drawable.ic_handheld_vertical
                FormFactor.HANDHELD_WIDE, FormFactor.HANDHELD_DUAL,
                FormFactor.FOLDABLE, FormFactor.TABLET -> R.drawable.ic_handheld_widescreen
                else -> R.drawable.ic_generic_hardware
            }
    }

    // --------------------------------------------------------------- perfiles
    // Tunings reutilizables por gama.

    private val TUNE_FLAGSHIP = RaTuning(
        videoDriver = "vulkan", threadedVideo = true,
        runAheadFrames = 2, runAheadSecondInstance = true,
        frameDelay = 0, audioLatencyMs = 32,
        maxShader = "crt-full", hardGpuSync = true, preferredInternalScale = 4
    )
    private val TUNE_HIGH = RaTuning(
        videoDriver = "vulkan", threadedVideo = true,
        runAheadFrames = 1, runAheadSecondInstance = true,
        frameDelay = 0, audioLatencyMs = 48,
        maxShader = "crt-lite", hardGpuSync = true, preferredInternalScale = 3
    )
    private val TUNE_MID = RaTuning(
        videoDriver = "gl", threadedVideo = true,
        runAheadFrames = 1, runAheadSecondInstance = false,
        audioLatencyMs = 64, maxShader = "crt-lite", preferredInternalScale = 2
    )
    private val TUNE_LOW = RaTuning(
        videoDriver = "gl", threadedVideo = true,
        runAheadFrames = 0, audioLatencyMs = 96,
        maxShader = "none", preferredInternalScale = 1
    )
    private val TUNE_TV = RaTuning(
        videoDriver = "gl", threadedVideo = true,
        runAheadFrames = 0, audioLatencyMs = 80,
        maxShader = "crt-lite", vsyncSwapInterval = 1, preferredInternalScale = 2
    )

    // ------------------------------------------------------------- dispositivos

    val devices: List<SpeccyDevice> = listOf(

        // ============================== AYN ==============================
        SpeccyDevice("ayn_odin2", "AYN Odin 2 / Pro / Max", "AYN", "Snapdragon 8 Gen 2", "Adreno 740", "8/12/16 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("odin2", "odin 2", "ayn_odin2"), "PERFORMANCE", 85, TUNE_FLAGSHIP,
            notes = "Referencia absoluta Android. Switch y Windows vía Winlator."),
        SpeccyDevice("ayn_odin2_mini", "AYN Odin 2 Mini", "AYN", "Snapdragon 8 Gen 2", "Adreno 740", "8/12 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("odin2_mini", "odin 2 mini"), "PERFORMANCE", 83, TUNE_FLAGSHIP),
        SpeccyDevice("ayn_odin2_portal", "AYN Odin 2 Portal", "AYN", "Snapdragon 8 Gen 2", "Adreno 740", "12 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("odin2_portal", "odin 2 portal", "portal"), "PERFORMANCE", 85, TUNE_FLAGSHIP,
            notes = "Panel OLED 7\": subir brillo cuesta batería, no rendimiento."),
        SpeccyDevice("ayn_odin_pro", "AYN Odin Pro / Base", "AYN", "Snapdragon 845", "Adreno 630", "4/8 GB", 2021,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("odin_pro", "ayn odin"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("ayn_odin_lite", "AYN Odin Lite", "AYN", "Dimensity 900", "Mali-G68", "4/6/8 GB", 2022,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("odin_lite"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("ayn_thor", "AYN Thor", "AYN", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("ayn_thor", "thor"), "PERFORMANCE", 86, TUNE_FLAGSHIP),

        // ============================ RETROID ============================
        SpeccyDevice("retroid_rp5", "Retroid Pocket 5", "Retroid", "Snapdragon 865", "Adreno 650", "8 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rp5", "retroid pocket 5", "pocket 5"), "PERFORMANCE", 82, TUNE_HIGH),
        SpeccyDevice("retroid_rp_mini", "Retroid Pocket Mini / Mini V2", "Retroid", "Snapdragon 865", "Adreno 650", "6/8 GB", 2024,
            FormFactor.HANDHELD_VERTICAL, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rp mini", "retroid pocket mini", "rpmini"), "PERFORMANCE", 80, TUNE_HIGH),
        SpeccyDevice("retroid_rp_classic", "Retroid Pocket Classic", "Retroid", "Snapdragon 865", "Adreno 650", "8 GB", 2025,
            FormFactor.HANDHELD_VERTICAL, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rp classic", "pocket classic"), "PERFORMANCE", 80, TUNE_HIGH),
        SpeccyDevice("retroid_rp_flip2", "Retroid Pocket Flip 2", "Retroid", "Snapdragon 865", "Adreno 650", "8 GB", 2025,
            FormFactor.FOLDABLE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rp flip", "pocket flip", "flip2"), "PERFORMANCE", 80, TUNE_HIGH,
            notes = "Formato concha: SpeccyOS activa el layout plegable automáticamente."),
        SpeccyDevice("retroid_rp4pro", "Retroid Pocket 4 Pro", "Retroid", "Dimensity 1100", "Mali-G77", "8 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("rp4p", "retroid pocket 4 pro"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("retroid_rp4", "Retroid Pocket 4", "Retroid", "Dimensity 900", "Mali-G68", "4/6 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("rp4", "retroid pocket 4"), "BALANCED", 78, TUNE_MID),
        SpeccyDevice("retroid_rp3p", "Retroid Pocket 3+", "Retroid", "Unisoc T618", "Mali-G52", "4 GB", 2022,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.UNISOC, Cooling.PASSIVE, 0,
            listOf("rp3+", "rp3plus", "retroid pocket 3"), "PERFORMANCE", 75, TUNE_LOW),
        SpeccyDevice("retroid_rp2s", "Retroid Pocket 2S", "Retroid", "Unisoc T610", "Mali-G52", "3/4 GB", 2023,
            FormFactor.HANDHELD_VERTICAL, Tier.T2_DC, SocFamily.UNISOC, Cooling.PASSIVE, 0,
            listOf("rp2s", "retroid pocket 2s"), "PERFORMANCE", 75, TUNE_LOW),

        // =========================== ANBERNIC ============================
        SpeccyDevice("anbernic_rg557", "Anbernic RG557", "Anbernic", "Dimensity 8300", "Mali-G615", "8/12 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("rg557", "rg-557"), "PERFORMANCE", 82, TUNE_HIGH),
        SpeccyDevice("anbernic_rg556", "Anbernic RG556", "Anbernic", "Unisoc T820", "Mali-G57", "8 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("rg556", "rg-556"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("anbernic_rg_cube", "Anbernic RG Cube / Cube XX", "Anbernic", "Unisoc T820", "Mali-G57", "8 GB", 2024,
            FormFactor.HANDHELD_VERTICAL, Tier.T3_GC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("rgcube", "rg cube"), "PERFORMANCE", 80, TUNE_MID,
            notes = "Pantalla 1:1: usa aspect ratio core provider, no 'full'."),
        SpeccyDevice("anbernic_rg406", "Anbernic RG406V / RG406H", "Anbernic", "Unisoc T820", "Mali-G57", "8 GB", 2024,
            FormFactor.HANDHELD_VERTICAL, Tier.T3_GC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("rg406v", "rg406h", "rg406"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("anbernic_rg_vita", "Anbernic RG Vita / RG477M", "Anbernic", "Snapdragon 865", "Adreno 650", "8 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rg vita", "rg-vita", "rgvita", "rg477"), "PERFORMANCE", 82, TUNE_HIGH),
        SpeccyDevice("anbernic_rg_slide", "Anbernic RG Slide", "Anbernic", "Unisoc T820", "Mali-G57", "8 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("rg slide", "rgslide"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("anbernic_rg505", "Anbernic RG505", "Anbernic", "Unisoc T618", "Mali-G52", "4 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.UNISOC, Cooling.PASSIVE, 0,
            listOf("rg505"), "PERFORMANCE", 75, TUNE_LOW),
        SpeccyDevice("anbernic_rg405", "Anbernic RG405M / RG405V", "Anbernic", "Unisoc T618", "Mali-G52", "4 GB", 2023,
            FormFactor.HANDHELD_VERTICAL, Tier.T2_DC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 2,
            listOf("rg405m", "rg405v", "rg405"), "PERFORMANCE", 75, TUNE_LOW),
        SpeccyDevice("anbernic_rg353", "Anbernic RG353 (Android)", "Anbernic", "Rockchip RK3566", "Mali-G52 2EE", "1/2 GB", 2022,
            FormFactor.HANDHELD_VERTICAL, Tier.T1_PSX, SocFamily.ROCKCHIP, Cooling.PASSIVE, 0,
            listOf("rg353", "rk3566_rg353"), "PERFORMANCE", 72, TUNE_LOW),

        // ============================ AYANEO =============================
        SpeccyDevice("ayaneo_pocket_s2", "AYANEO Pocket S2", "AYANEO", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket s2", "ayaneo_s2"), "PERFORMANCE", 86, TUNE_FLAGSHIP),
        SpeccyDevice("ayaneo_pocket_s", "AYANEO Pocket S", "AYANEO", "Snapdragon G3x Gen 2", "Adreno 735", "12/16 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket s", "ayaneo_pocket_s"), "PERFORMANCE", 85, TUNE_FLAGSHIP),
        SpeccyDevice("ayaneo_pocket_evo", "AYANEO Pocket EVO", "AYANEO", "Snapdragon G3x Gen 2", "Adreno 735", "12/16 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket evo", "ayaneo_evo"), "PERFORMANCE", 85, TUNE_FLAGSHIP),
        SpeccyDevice("ayaneo_flip_ds", "AYANEO Pocket DS (doble pantalla)", "AYANEO", "Snapdragon G3x Gen 2", "Adreno 735", "12 GB", 2025,
            FormFactor.HANDHELD_DUAL, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket ds", "flip ds", "ayaneo_ds"), "PERFORMANCE", 85, TUNE_FLAGSHIP,
            dualScreen = true,
            notes = "Doble pantalla real: NDS/3DS/Wii U en modo nativo de dos paneles."),
        SpeccyDevice("ayaneo_pocket_air", "AYANEO Pocket AIR", "AYANEO", "Dimensity 1200", "Mali-G77", "8/12 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T4_PS2, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("pocket air", "ayaneo_air"), "PERFORMANCE", 82, TUNE_MID),
        SpeccyDevice("ayaneo_pocket_micro", "AYANEO Pocket MICRO", "AYANEO", "Helio G99", "Mali-G57", "6/8 GB", 2024,
            FormFactor.HANDHELD_VERTICAL, Tier.T2_DC, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("pocket micro", "ayaneo_micro"), "PERFORMANCE", 78, TUNE_LOW),
        SpeccyDevice("ayaneo_pocket_dmg", "AYANEO Pocket DMG", "AYANEO", "Snapdragon G3x Gen 2", "Adreno 735", "12 GB", 2025,
            FormFactor.HANDHELD_VERTICAL, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket dmg", "ayaneo_dmg"), "PERFORMANCE", 84, TUNE_FLAGSHIP),

        // ============================ ONEXSUGAR / GPD ====================
        SpeccyDevice("onexsugar_1", "ONEXSUGAR Sugar 1 (plegable doble pantalla)", "ONEXPLAYER", "Snapdragon G3x Gen 2", "Adreno 735", "12 GB", 2025,
            FormFactor.HANDHELD_DUAL, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("sugar 1", "onexsugar", "sugar1"), "PERFORMANCE", 85, TUNE_FLAGSHIP,
            dualScreen = true,
            notes = "Plegable con pantalla interna: modo DS nativo + modo pantalla única."),
        SpeccyDevice("gpd_xp_plus", "GPD XP Plus", "GPD", "Dimensity 1200", "Mali-G77", "6/8 GB", 2022,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("gpd xp", "xp plus"), "PERFORMANCE", 80, TUNE_MID),

        // ============================ GAMEMT ============================
        SpeccyDevice("gamemt_e5_ultra", "GameMT E5 Ultra", "GameMT", "Unisoc T620", "Mali-G57", "6 GB", 2026,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("e5_ultra", "e5 ultra", "gamemt_e5u"), "PERFORMANCE", 80, TUNE_MID,
            notes = "Sticks Hall Effect y ventilador: aguanta PERFORMANCE sostenido."),
        SpeccyDevice("gamemt_e5_plus_gamma", "GameMT E5 Plus (GammaOS)", "GameMT", "Rockchip RK3566", "Mali-G52 2EE", "4 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.ROCKCHIP, Cooling.PASSIVE, 0,
            listOf("e5_plus_gamma"), "PERFORMANCE", 75, TUNE_LOW,
            notes = "GammaOS elimina bloatware: usar PERFORMANCE de serie."),
        SpeccyDevice("gamemt_e5_plus", "GameMT E5 Plus (Stock)", "GameMT", "Rockchip RK3566", "Mali-G52 2EE", "4 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.ROCKCHIP, Cooling.PASSIVE, 0,
            listOf("e5_plus", "e5 plus"), "BALANCED", 75, TUNE_LOW),
        SpeccyDevice("gamemt_ex8", "GameMT EX8", "GameMT", "Helio G99", "Mali-G57", "6/8 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.ACTIVE_FAN, 3,
            listOf("ex8", "gamemt_ex8"), "PERFORMANCE", 80, TUNE_MID),
        SpeccyDevice("gamemt_e6", "GameMT E6", "GameMT", "Unisoc T616", "Mali-G57", "4 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.UNISOC, Cooling.ACTIVE_FAN, 3,
            listOf("e6", "gamemt_e6"), "PERFORMANCE", 78, TUNE_LOW),
        SpeccyDevice("gamemt_psk5000", "GameMT PSK5000", "GameMT", "Helio G85", "Mali-G52", "4 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("psk5000"), "PERFORMANCE", 75, TUNE_LOW),

        // ======================= KT / MANGMI / POWKIDDY ==================
        SpeccyDevice("kt_r1", "KT-R1 / KT Pocket", "KT Pocket", "Helio G99", "Mali-G57", "4/6/8 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("kt-r1", "kt_r1", "ktr1"), "PERFORMANCE", 78, TUNE_MID),
        SpeccyDevice("mangmi_pocket_max", "Mangmi Pocket Max", "Mangmi", "Snapdragon 8 Gen 2", "Adreno 740", "12 GB", 2025,
            FormFactor.HANDHELD_WIDE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("pocket max", "mangmi"), "PERFORMANCE", 85, TUNE_FLAGSHIP),
        SpeccyDevice("powkiddy_x55", "Powkiddy X55", "Powkiddy", "Rockchip RK3566", "Mali-G52 2EE", "2/4 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T1_PSX, SocFamily.ROCKCHIP, Cooling.PASSIVE, 0,
            listOf("x55", "powkiddy_x55"), "PERFORMANCE", 72, TUNE_LOW),
        SpeccyDevice("powkiddy_x28", "Powkiddy X28", "Powkiddy", "Unisoc T618", "Mali-G52", "4 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.UNISOC, Cooling.PASSIVE, 0,
            listOf("x28", "powkiddy_x28"), "PERFORMANCE", 75, TUNE_LOW),

        // ====================== GAMING PHONES / CLOUD ====================
        SpeccyDevice("razer_edge", "Razer Edge", "Razer", "Snapdragon G3x Gen 1", "Adreno 730", "6/8 GB", 2023,
            FormFactor.HANDHELD_WIDE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("razer edge", "razer_edge"), "PERFORMANCE", 82, TUNE_HIGH),
        SpeccyDevice("logitech_g_cloud", "Logitech G Cloud", "Logitech", "Snapdragon 720G", "Adreno 618", "4 GB", 2022,
            FormFactor.HANDHELD_WIDE, Tier.T2_DC, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("g cloud", "logitech"), "PERFORMANCE", 78, TUNE_LOW,
            notes = "4 GB de RAM: limitar precarga de carátulas y desactivar vídeos."),
        SpeccyDevice("abxylute_one", "Abxylute One", "Abxylute", "Dimensity 900", "Mali-G68", "8 GB", 2024,
            FormFactor.HANDHELD_WIDE, Tier.T3_GC, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("abxylute"), "PERFORMANCE", 78, TUNE_MID),
        SpeccyDevice("asus_rog_9", "ASUS ROG Phone 9 / Pro", "ASUS", "Snapdragon 8 Elite", "Adreno 830", "12/16/24 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rog phone 9", "asus_ai2501", "ai2501"), "PERFORMANCE", 88, TUNE_FLAGSHIP,
            notes = "AeroActive Cooler: si está conectado, EXTREME es sostenible."),
        SpeccyDevice("asus_rog_8", "ASUS ROG Phone 8 / Pro", "ASUS", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN, 3,
            listOf("rog phone 8", "ai2401"), "PERFORMANCE", 88, TUNE_FLAGSHIP),
        SpeccyDevice("redmagic_10", "RedMagic 10 Pro", "Nubia", "Snapdragon 8 Elite", "Adreno 830", "12/16/24 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("redmagic 10", "nx769"), "EXTREME", 90, TUNE_FLAGSHIP,
            notes = "Ventilador interno real: único móvil donde EXTREME es seguro."),
        SpeccyDevice("redmagic_9", "RedMagic 9 Pro", "Nubia", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.ACTIVE_FAN_PWM, 3,
            listOf("redmagic 9", "nx769j"), "PERFORMANCE", 88, TUNE_FLAGSHIP),

        // ============================ SAMSUNG ============================
        SpeccyDevice("samsung_s25_ultra", "Galaxy S25 Ultra", "Samsung", "Snapdragon 8 Elite", "Adreno 830", "12/16 GB", 2025,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-s938", "s25 ultra"), "BALANCED", 82, TUNE_FLAGSHIP),
        SpeccyDevice("samsung_s24_ultra", "Galaxy S24 Ultra", "Samsung", "Snapdragon 8 Gen 3", "Adreno 750", "12 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-s928", "s24 ultra"), "BALANCED", 82, TUNE_FLAGSHIP),
        SpeccyDevice("samsung_s23_ultra", "Galaxy S23 Ultra", "Samsung", "Snapdragon 8 Gen 2", "Adreno 740", "8/12 GB", 2023,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-s918", "s23 ultra"), "BALANCED", 80, TUNE_HIGH),
        SpeccyDevice("samsung_s22_ultra", "Galaxy S22 Ultra", "Samsung", "SD 8 Gen 1 / Exynos 2200", "Adreno 730 / Xclipse", "8/12 GB", 2022,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.EXYNOS, Cooling.PASSIVE, 0,
            listOf("sm-s908"), "BALANCED", 78, TUNE_HIGH,
            notes = "Exynos 2200 sufre throttling severo: no usar EXTREME."),
        SpeccyDevice("samsung_zfold7", "Galaxy Z Fold 7", "Samsung", "Snapdragon 8 Elite", "Adreno 830", "12/16 GB", 2025,
            FormFactor.FOLDABLE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-f966", "z fold7", "zfold7"), "BALANCED", 80, TUNE_FLAGSHIP,
            dualScreen = true,
            notes = "Bisagra: layout dual (lista arriba / arte abajo) en semi-abierto."),
        SpeccyDevice("samsung_zfold6", "Galaxy Z Fold 6", "Samsung", "Snapdragon 8 Gen 3", "Adreno 750", "12 GB", 2024,
            FormFactor.FOLDABLE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-f956", "z fold6", "zfold6"), "BALANCED", 80, TUNE_FLAGSHIP, dualScreen = true),
        SpeccyDevice("samsung_zfold5", "Galaxy Z Fold 5", "Samsung", "Snapdragon 8 Gen 2", "Adreno 740", "12 GB", 2023,
            FormFactor.FOLDABLE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-f946", "z fold5"), "BALANCED", 78, TUNE_HIGH, dualScreen = true),
        SpeccyDevice("samsung_zflip6", "Galaxy Z Flip 6", "Samsung", "Snapdragon 8 Gen 3", "Adreno 750", "12 GB", 2024,
            FormFactor.FOLDABLE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-f741", "z flip6"), "BALANCED", 76, TUNE_HIGH, dualScreen = true,
            notes = "Modo Flex: juego arriba, controles táctiles abajo."),
        SpeccyDevice("samsung_tab_s10", "Galaxy Tab S10 Ultra / S10+", "Samsung", "Dimensity 9300+", "Immortalis-G720", "12/16 GB", 2024,
            FormFactor.TABLET, Tier.T5_SWITCH, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("sm-x926", "sm-x820", "tab s10"), "BALANCED", 80, TUNE_HIGH),
        SpeccyDevice("samsung_tab_s9", "Galaxy Tab S9 / S9+ / Ultra", "Samsung", "Snapdragon 8 Gen 2", "Adreno 740", "8/12/16 GB", 2023,
            FormFactor.TABLET, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("sm-x710", "sm-x810", "sm-x910", "tab s9"), "BALANCED", 78, TUNE_HIGH),

        // ==================== XIAOMI / POCO / ONEPLUS ====================
        SpeccyDevice("xiaomi_15", "Xiaomi 15 / 15 Pro / Ultra", "Xiaomi", "Snapdragon 8 Elite", "Adreno 830", "12/16 GB", 2025,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("xiaomi 15", "24129", "25010"), "BALANCED", 82, TUNE_FLAGSHIP),
        SpeccyDevice("xiaomi_14", "Xiaomi 14 / Pro / Ultra", "Xiaomi", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("xiaomi 14", "23127", "2304fpn"), "BALANCED", 82, TUNE_FLAGSHIP),
        SpeccyDevice("poco_f6_pro", "POCO F6 Pro", "POCO", "Snapdragon 8 Gen 2", "Adreno 740", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("poco f6", "23113rkc6g"), "BALANCED", 80, TUNE_HIGH),
        SpeccyDevice("poco_f5_pro", "POCO F5 Pro", "POCO", "Snapdragon 8+ Gen 1", "Adreno 730", "8/12 GB", 2023,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("poco f5", "23013pc75g"), "BALANCED", 78, TUNE_HIGH),
        SpeccyDevice("poco_x7_pro", "POCO X7 Pro", "POCO", "Dimensity 8400 Ultra", "Mali-G720", "8/12 GB", 2025,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("poco x7", "24094rad4g"), "BALANCED", 78, TUNE_HIGH),
        SpeccyDevice("poco_x6_pro", "POCO X6 Pro", "POCO", "Dimensity 8300 Ultra", "Mali-G615", "8/12 GB", 2024,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("poco x6", "2311drk48g"), "BALANCED", 78, TUNE_HIGH),
        SpeccyDevice("oneplus_13", "OnePlus 13", "OnePlus", "Snapdragon 8 Elite", "Adreno 830", "12/16/24 GB", 2025,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("oneplus 13", "pjz110", "cph2649"), "BALANCED", 84, TUNE_FLAGSHIP),
        SpeccyDevice("oneplus_12", "OnePlus 12", "OnePlus", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("oneplus 12", "cph2573", "pjd110"), "BALANCED", 84, TUNE_FLAGSHIP),

        // ============================= GOOGLE ============================
        SpeccyDevice("pixel_9_pro", "Pixel 9 / 9 Pro", "Google", "Tensor G4", "Mali-G715", "12/16 GB", 2024,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("pixel 9", "komodo", "caiman", "tokay"), "BALANCED", 74, TUNE_MID,
            notes = "Tensor tiene throttling agresivo: BALANCED da más FPS medios que PERFORMANCE."),
        SpeccyDevice("pixel_8_pro", "Pixel 8 / 8 Pro", "Google", "Tensor G3", "Mali-G715", "8/12 GB", 2023,
            FormFactor.PHONE, Tier.T4_PS2, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("pixel 8", "husky", "shiba"), "BALANCED", 74, TUNE_MID),

        // ========================= TV / SOBREMESA ========================
        SpeccyDevice("nvidia_shield_pro", "NVIDIA Shield TV Pro", "NVIDIA", "Tegra X1+", "Maxwell 256", "3 GB", 2019,
            FormFactor.TV, Tier.T3_GC, SocFamily.TEGRA, Cooling.PASSIVE, 0,
            listOf("shield", "darcy", "mdarcy"), "PERFORMANCE", 80, TUNE_TV,
            notes = "La mejor caja Android para emulación. GameCube/Wii jugables."),
        SpeccyDevice("nvidia_shield_tube", "NVIDIA Shield TV (2019 tubo)", "NVIDIA", "Tegra X1+", "Maxwell 256", "2 GB", 2019,
            FormFactor.TV, Tier.T2_DC, SocFamily.TEGRA, Cooling.PASSIVE, 0,
            listOf("sif", "shield_tube"), "PERFORMANCE", 80, TUNE_TV),
        SpeccyDevice("onn_4k_pro", "onn. 4K Pro (Google TV)", "Walmart", "Amlogic S905X4-J", "Mali-G31", "3 GB", 2024,
            FormFactor.TV, Tier.T2_DC, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("onn 4k", "onn_4k", "wallace"), "PERFORMANCE", 75, TUNE_TV,
            notes = "La mejor relación precio/emulación en TV boxes actuales."),
        SpeccyDevice("fire_tv_4k_max", "Fire TV Stick 4K Max", "Amazon", "MT8696 (Cortex-A73)", "Mali-G52", "2 GB", 2023,
            FormFactor.TV, Tier.T1_PSX, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("aftkrt", "aftkm", "fire tv stick 4k"), "PERFORMANCE", 72, TUNE_LOW,
            notes = "2 GB de RAM: desactivar vídeos de previsualización y limitar caché."),
        SpeccyDevice("fire_tv_cube", "Fire TV Cube (3ª gen)", "Amazon", "Octa-core A73/A53", "Mali-G52 MP2", "2 GB", 2022,
            FormFactor.TV, Tier.T2_DC, SocFamily.MEDIATEK, Cooling.PASSIVE, 0,
            listOf("aftgaz", "fire tv cube"), "PERFORMANCE", 74, TUNE_LOW),
        SpeccyDevice("chromecast_gtv_4k", "Chromecast con Google TV 4K", "Google", "Amlogic S905X3", "Mali-G31 MP2", "2 GB", 2020,
            FormFactor.TV, Tier.T1_PSX, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("chromecast", "sabrina", "boreal"), "PERFORMANCE", 72, TUNE_LOW),
        SpeccyDevice("xiaomi_tvbox_s2", "Xiaomi TV Box S (2ª gen)", "Xiaomi", "Amlogic S905Y4", "Mali-G31 MP2", "2 GB", 2023,
            FormFactor.TV, Tier.T1_PSX, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("mibox", "mibox4", "dopinder"), "PERFORMANCE", 72, TUNE_LOW),
        SpeccyDevice("x96_max_plus", "X96 Max Plus", "Genérico", "Amlogic S905X3", "Mali-G31 MP2", "4 GB", 2020,
            FormFactor.TV, Tier.T1_PSX, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("x96", "x96_max_plus"), "PERFORMANCE", 72, TUNE_LOW),
        SpeccyDevice("h96_max", "H96 Max", "Genérico", "Rockchip RK3318", "Mali-450", "4 GB", 2020,
            FormFactor.TV, Tier.T0_RETRO, SocFamily.ROCKCHIP, Cooling.PASSIVE, 0,
            listOf("h96", "rk3318"), "PERFORMANCE", 70, TUNE_LOW),
        SpeccyDevice("kinhank_super_console_x", "Kinhank Super Console X (Android)", "Kinhank", "Amlogic S905X", "Mali-450", "2 GB", 2021,
            FormFactor.TV, Tier.T0_RETRO, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("super_console", "kinhank"), "PERFORMANCE", 70, TUNE_LOW),
        SpeccyDevice("tx3_mini", "Tanix TX3 Mini", "Tanix", "Amlogic S905W", "Mali-450", "2 GB", 2018,
            FormFactor.TV, Tier.T0_RETRO, SocFamily.AMLOGIC, Cooling.PASSIVE, 0,
            listOf("tx3", "tanix"), "PERFORMANCE", 70, TUNE_LOW),

        // ============================ TABLETAS ===========================
        SpeccyDevice("legion_y700_3", "Lenovo Legion Y700 (3ª gen)", "Lenovo", "Snapdragon 8 Gen 3", "Adreno 750", "12/16 GB", 2024,
            FormFactor.TABLET, Tier.T5_SWITCH, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("tb320", "legion y700", "y700"), "PERFORMANCE", 82, TUNE_FLAGSHIP,
            notes = "8,8\" 3:2 con altavoces frontales: excelente para arcade vertical."),
        SpeccyDevice("legion_y700_2", "Lenovo Legion Y700 (2ª gen)", "Lenovo", "Snapdragon 8+ Gen 1", "Adreno 730", "8/12/16 GB", 2023,
            FormFactor.TABLET, Tier.T4_PS2, SocFamily.SNAPDRAGON, Cooling.PASSIVE, 0,
            listOf("tb132", "y700 2"), "PERFORMANCE", 80, TUNE_HIGH),

        // ============================= GENÉRICO ==========================
        SpeccyDevice("generic", "Dispositivo genérico", "Android", "Desconocido", "Desconocida", "N/A", 0,
            FormFactor.PHONE, Tier.T2_DC, SocFamily.GENERIC, Cooling.PASSIVE, 0,
            emptyList(), "BALANCED", 78, TUNE_MID,
            notes = "SpeccyOS ajustará por sondeo de hardware en vez de por modelo.")
    )

    // ------------------------------------------------------------- utilidades

    private val byId: Map<String, SpeccyDevice> = devices.associateBy { it.id }

    /** Nunca devuelve null: el genérico es el último recurso. Evita el `!!` que crasheaba. */
    fun get(id: String?): SpeccyDevice = byId[id] ?: byId.getValue("generic")

    fun exists(id: String?): Boolean = id != null && byId.containsKey(id)

    val brands: List<String> get() = devices.map { it.brand }.distinct().sorted()

    fun byBrand(brand: String): List<SpeccyDevice> =
        devices.filter { it.brand.equals(brand, true) }.sortedByDescending { it.year }

    fun search(query: String): List<SpeccyDevice> {
        if (query.isBlank()) return devices
        val q = query.trim().lowercase()
        return devices.filter {
            it.name.lowercase().contains(q) || it.brand.lowercase().contains(q) ||
                it.soc.lowercase().contains(q) || it.id.contains(q)
        }
    }

    /**
     * Detección automática. Puntúa por coincidencia más larga para que
     * "odin2_mini" gane a "odin2" y "rp5" no capture a "rp5x".
     */
    fun detect(): SpeccyDevice {
        val haystack = listOf(
            Build.MODEL, Build.DEVICE, Build.BOARD, Build.PRODUCT, Build.DISPLAY
        ).joinToString(" ") { it.orEmpty() }.lowercase()

        var best: SpeccyDevice? = null
        var bestLen = 0
        for (d in devices) {
            for (token in d.match) {
                if (token.length > bestLen && haystack.contains(token)) {
                    best = d; bestLen = token.length
                }
            }
        }
        if (best != null) return best

        // Sin coincidencia por modelo: inferimos una gama razonable por SoC.
        return inferFromSoc(haystack)
    }

    /** Heurística por hardware cuando el modelo no está en el catálogo. */
    private fun inferFromSoc(haystack: String): SpeccyDevice {
        val hw = Build.HARDWARE.lowercase()
        val family = when {
            hw.startsWith("qcom") || hw.contains("kona") || hw.contains("lahaina") ||
                hw.contains("taro") || hw.contains("kalama") || hw.contains("pineapple") -> SocFamily.SNAPDRAGON
            hw.startsWith("mt") || haystack.contains("mediatek") || haystack.contains("dimensity") -> SocFamily.MEDIATEK
            hw.startsWith("ums") || haystack.contains("unisoc") -> SocFamily.UNISOC
            hw.startsWith("rk") -> SocFamily.ROCKCHIP
            hw.contains("amlogic") || hw.startsWith("s905") || hw.startsWith("s922") -> SocFamily.AMLOGIC
            hw.startsWith("exynos") || hw.startsWith("s5e") -> SocFamily.EXYNOS
            hw.contains("tegra") -> SocFamily.TEGRA
            else -> SocFamily.GENERIC
        }
        val generic = get("generic")
        return generic.copy(
            name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            soc = Build.HARDWARE.orEmpty(),
            socFamily = family
        )
    }
}
