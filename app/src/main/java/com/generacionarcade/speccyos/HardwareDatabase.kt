package com.generacionarcade.speccyos

import android.os.Build

data class ConsoleProfile(
    val id: String,
    val name: String,
    val chipset: String,
    val manufacturer: String,
    val ram: String,
    val hasFan: Boolean = false,
    val fanPath: String? = null,
    val hasRgb: Boolean = false,
    val rgbPath: String? = null,
    val cpuGovernorPath: String = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
    val buildModelPatterns: List<String> = emptyList(),
    val iconRes: Int,
    val history: String = "Información no disponible.",
    val emulationCapacity: String = "Desconocida.",
    val recommendedPower: String = "BALANCED"
)

object HardwareDatabase {
    val profiles = listOf(
        // GAMEMT
        ConsoleProfile(
            id = "gamemt_e5_plus_gamma",
            name = "GameMT E5 Plus (GammaOS)",
            chipset = "RK3566",
            manufacturer = "GameMT",
            ram = "4GB",
            iconRes = R.drawable.ic_handheld_widescreen,
            buildModelPatterns = listOf("e5_plus_gamma"),
            history = "Versión optimizada con GammaOS, maximizando el rendimiento del chip RK3566 en esta consola ergonómica.",
            emulationCapacity = "Hasta PSP y algo de Saturn con gran estabilidad.",
            recommendedPower = "PERFORMANCE"
        ),
        ConsoleProfile(
            id = "gamemt_e5_plus_stock",
            name = "GameMT E5 Plus (Stock)",
            chipset = "RK3566",
            manufacturer = "GameMT",
            ram = "4GB",
            iconRes = R.drawable.ic_handheld_widescreen,
            buildModelPatterns = listOf("e5_plus"),
            history = "Lanzada en 2025, destaca por su diseño inspirado en la PSP. Versión con firmware original.",
            emulationCapacity = "Hasta PSP y algo de Saturn.",
            recommendedPower = "BALANCED"
        ),
        ConsoleProfile(
            id = "e5_ultra",
            name = "GameMT E5 Ultra",
            chipset = "Unisoc T620",
            manufacturer = "GameMT",
            ram = "6GB",
            hasFan = true,
            fanPath = "/sys/class/backlight/sprd_backlight_fan/brightness",
            cpuGovernorPath = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor",
            iconRes = R.drawable.ic_handheld_widescreen,
            buildModelPatterns = listOf("e5_ultra", "gamemt_e5u"),
            history = "Novedad 2026. Un salto de potencia con sticks Hall Effect y refrigeración activa.",
            emulationCapacity = "PS2 y GameCube con fluidez.",
            recommendedPower = "PERFORMANCE"
        ),
        // --- AYN ---
        ConsoleProfile("odin_2", "Odin 2 / Pro / Max", "SD 8 Gen 2", "AYN", "8/12/16GB", true, "/sys/class/fan/level", iconRes = R.drawable.ic_handheld_widescreen, buildModelPatterns = listOf("odin2"), recommendedPower = "EXTREME"),
        ConsoleProfile("odin_2_mini", "Odin 2 Mini", "SD 8 Gen 2", "AYN", "8/12GB", true, "/sys/class/fan/level", iconRes = R.drawable.ic_handheld_widescreen, buildModelPatterns = listOf("odin2_mini"), recommendedPower = "EXTREME"),

        // --- RETROID ---
        ConsoleProfile("retroid_pocket_5", "Retroid Pocket 5", "SD 865", "Retroid", "8GB", true, "/sys/class/fan/fan_speed", iconRes = R.drawable.ic_handheld_widescreen, buildModelPatterns = listOf("retroid_pocket_5", "rp5"), recommendedPower = "PERFORMANCE"),
        ConsoleProfile("retroid_pocket_4_pro", "Retroid Pocket 4 Pro", "Dimensity 1100", "Retroid", "8GB", true, "/sys/class/fan/fan_speed", iconRes = R.drawable.ic_handheld_widescreen, buildModelPatterns = listOf("rp4p"), recommendedPower = "PERFORMANCE"),

        // --- ANBERNIC ---
        ConsoleProfile("anbernic_rg556", "Anbernic RG556", "Unisoc T820", "Anbernic", "8GB", true, "/sys/class/fan/level", iconRes = R.drawable.ic_handheld_widescreen, buildModelPatterns = listOf("rg556"), recommendedPower = "PERFORMANCE"),
        ConsoleProfile("anbernic_rg_cube", "Anbernic RG Cube", "Unisoc T820", "Anbernic", "8GB", true, "/sys/class/fan/level", iconRes = R.drawable.ic_handheld_vertical, buildModelPatterns = listOf("rgcube"), recommendedPower = "PERFORMANCE"),
        
        // --- SMARTPHONES SAMSUNG ---
        ConsoleProfile("samsung_s24_ultra", "Galaxy S24 Ultra", "SD 8 Gen 3", "Samsung", "12GB", false, null, iconRes = R.drawable.ic_handheld_vertical, buildModelPatterns = listOf("sm-s928b"), recommendedPower = "EXTREME"),
        
        // --- TV BOXES & SOBREMESA ---
        ConsoleProfile("nvidia_shield_tv_pro", "Nvidia Shield TV Pro", "Tegra X1+", "NVIDIA", "3GB", true, "/sys/class/fan/level", iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("shield", "darcy"), recommendedPower = "PERFORMANCE"),
        ConsoleProfile("xiaomi_mi_box_s_2gen", "Xiaomi TV Box S (2nd Gen)", "Cortex-A55", "XIAOMI", "2GB", false, null, iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("mibox"), recommendedPower = "BALANCED"),
        ConsoleProfile("fire_tv_stick_4k_max", "Fire TV Stick 4K Max", "Cortex-A78", "AMAZON", "2GB", false, null, iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("aftkrti"), recommendedPower = "BALANCED"),
        ConsoleProfile("x96_max_plus", "X96 Max Plus", "Amlogic S905X3", "TVBOX_GENERIC", "4GB", false, null, iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("x96", "x96_max_plus"), recommendedPower = "BALANCED"),
        ConsoleProfile("kinhank_super_console_x", "Super Console X (Android)", "Amlogic S905X", "KINHANK", "2GB", false, null, iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("super_console"), recommendedPower = "BALANCED"),
        ConsoleProfile("tx3_mini", "Tanix TX3 Mini", "Amlogic S905W", "TANIX", "2GB", false, null, iconRes = R.drawable.ic_generic_hardware, buildModelPatterns = listOf("tx3", "tanix"), recommendedPower = "BALANCED"),
        ConsoleProfile(
            id = "generic",
            name = "Dispositivo Genérico",
            chipset = "Desconocido",
            manufacturer = "Android",
            ram = "N/A",
            iconRes = R.drawable.ic_handheld_widescreen
        )
    )

    fun detectActualHardware(): ConsoleProfile {
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        val display = Build.DISPLAY.lowercase()

        // Detección específica para E5 Plus
        if (model.contains("e5_plus") || model.contains("e5 plus") || device.contains("e5_plus")) {
            return if (display.contains("gamma")) getProfile("gamemt_e5_plus_gamma")
            else getProfile("gamemt_e5_plus_stock")
        }

        return profiles.find { it.buildModelPatterns.any { p -> model.contains(p) || device.contains(p) } } ?: getProfile("generic")
    }

    fun getProfile(id: String): ConsoleProfile = profiles.find { it.id == id } ?: profiles.last()
}
