package com.generacionarcade.speccyos

import android.content.Context
import android.content.pm.PackageManager

/**
 * EmulatorCompatibilityMatrix — Mapa de compatibilidad emulador/plataforma.
 *
 * Responde a la pregunta: "¿Qué emuladores tengo instalados y qué pueden emular?"
 * Se usa en la pantalla de ajustes de emulación para mostrar el estado de cada
 * emulador: instalado, no instalado, necesita instalación, con enlace de descarga.
 *
 * USO en EmulationSettingsScreen:
 *   val matrix = EmulatorCompatibilityMatrix.getStatus(context)
 *   matrix.forEach { entry ->
 *       EmulatorStatusRow(entry)
 *   }
 */
object EmulatorCompatibilityMatrix {

    enum class InstallStatus { INSTALLED, NOT_INSTALLED, DEPRECATED }

    data class EmulatorEntry(
        val name: String,
        val packageIds: List<String>,        // Uno o varios packages (ej: Gold + Free)
        val platforms: List<String>,         // platformIds que puede emular
        val status: InstallStatus,
        val downloadUrl: String,
        val notes: String = "",
        val isRecommended: Boolean = false,
        val minAndroid: Int = 8              // Android mínimo requerido
    )

    fun getStatus(context: Context): List<EmulatorEntry> {
        val pm = context.packageManager
        fun installed(vararg pkgs: String) = pkgs.any { pkg ->
            try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
        }

        return listOf(
            // ── RetroArch ──────────────────────────────────────────────
            EmulatorEntry(
                name       = "RetroArch (64-bit)",
                packageIds = listOf("com.retroarch.aarch64"),
                platforms  = listOf("todo (multi-core)"),
                status     = if (installed("com.retroarch.aarch64")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64",
                notes      = "Frontend principal. Contiene más de 80 cores.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "RetroArch Plus",
                packageIds = listOf("com.retroarch.plus"),
                platforms  = listOf("todo (multi-core)"),
                status     = if (installed("com.retroarch.plus")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/libretro/RetroArch/releases",
                notes      = "Versión con extras opcionales."
            ),

            // ── PlayStation ─────────────────────────────────────────────
            EmulatorEntry(
                name       = "DuckStation (PS1)",
                packageIds = listOf("com.github.stenzek.duckstation"),
                platforms  = listOf("psx", "ps1"),
                status     = if (installed("com.github.stenzek.duckstation")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/stenzek/duckstation/releases",
                notes      = "Mejor emulador PS1 para Android. 1080p, texturas mejoradas.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "NetherSX2 / AetherSX2 (PS2)",
                packageIds = listOf("xyz.aethersx2.android", "com.tahlreth.aethersx2.android"),
                platforms  = listOf("ps2"),
                status     = if (installed("xyz.aethersx2.android", "com.tahlreth.aethersx2.android")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/Tahlreth/aethersx2/releases",
                notes      = "Requiere SD 845+ para títulos exigentes.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "Vita3K (PS Vita)",
                packageIds = listOf("com.vita3k.emulator"),
                platforms  = listOf("vita", "psvita"),
                status     = if (installed("com.vita3k.emulator")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/Vita3K/Vita3K-Android/releases",
                notes      = "Experimental. Solo algunos títulos comerciales.",
                minAndroid = 10
            ),

            // ── Nintendo ────────────────────────────────────────────────
            EmulatorEntry(
                name       = "Dolphin (GC/Wii)",
                packageIds = listOf("org.dolphinemu.dolphinemu"),
                platforms  = listOf("gc", "gamecube", "wii"),
                status     = if (installed("org.dolphinemu.dolphinemu")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=org.dolphinemu.dolphinemu",
                notes      = "Requiere SD 845+ para GC, SD 8 Gen 1+ para Wii.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "Lime3DS (3DS)",
                packageIds = listOf("io.github.lime3ds.emulator"),
                platforms  = listOf("3ds", "n3ds"),
                status     = if (installed("io.github.lime3ds.emulator")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/Lime3DS/Lime3DS/releases",
                notes      = "Sucesor recomendado de Citra.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "Azahar (3DS)",
                packageIds = listOf("io.github.azahar_emu.azahar"),
                platforms  = listOf("3ds", "n3ds"),
                status     = if (installed("io.github.azahar_emu.azahar")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/azahar-emu/azahar/releases",
                notes      = "Fork activo de Citra con mejoras de rendimiento."
            ),
            EmulatorEntry(
                name       = "Citra (3DS — legacy)",
                packageIds = listOf("org.citra.citra_emu"),
                platforms  = listOf("3ds"),
                status     = if (installed("org.citra.citra_emu")) InstallStatus.DEPRECATED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/citra-emu/citra-android/releases",
                notes      = "Discontinuado. Usa Lime3DS o Azahar."
            ),
            EmulatorEntry(
                name       = "Sudachi (Switch)",
                packageIds = listOf("org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.early_access"),
                platforms  = listOf("switch"),
                status     = if (installed("org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.early_access")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/sudachi-emu/sudachi/releases",
                notes      = "Fork activo post-Yuzu. Requiere SD 8 Gen 2+.",
                isRecommended = true,
                minAndroid = 11
            ),
            EmulatorEntry(
                name       = "Citron (Switch)",
                packageIds = listOf("org.citron_emu.citronn"),
                platforms  = listOf("switch"),
                status     = if (installed("org.citron_emu.citronn")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://git.citron-emu.org/Citron/Citron/-/releases",
                notes      = "Alternativa activa. Compatible con Vulkan.",
                minAndroid = 11
            ),
            EmulatorEntry(
                name       = "DraStic (NDS)",
                packageIds = listOf("com.dsemu.drastic"),
                platforms  = listOf("nds"),
                status     = if (installed("com.dsemu.drastic")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=com.dsemu.drastic",
                notes      = "El más rápido para NDS. De pago.",
                isRecommended = true
            ),
            EmulatorEntry(
                name       = "Mupen64Plus FZ (N64)",
                packageIds = listOf("org.mupen64plusae.v3.fxyz", "org.mupen64plusae.v3.fxyz.pro"),
                platforms  = listOf("n64"),
                status     = if (installed("org.mupen64plusae.v3.fxyz", "org.mupen64plusae.v3.fxyz.pro")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fxyz",
                notes      = "Mejor compatibilidad N64 en Android.",
                isRecommended = true
            ),

            // ── GBA/GBC standalone ──────────────────────────────────────
            EmulatorEntry(
                name       = "Pizza Boy GBA Pro",
                packageIds = listOf("it.pizzaboy.gba"),
                platforms  = listOf("gba"),
                status     = if (installed("it.pizzaboy.gba")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=it.pizzaboy.gba",
                notes      = "Alta precisión + cheats + sincronización RTC."
            ),
            EmulatorEntry(
                name       = "My Boy! (GBA)",
                packageIds = listOf("com.fastemulator.gba"),
                platforms  = listOf("gba"),
                status     = if (installed("com.fastemulator.gba")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=com.fastemulator.gba",
                notes      = "Muy rápido, enlace de cable y cheats."
            ),

            // ── PSP ─────────────────────────────────────────────────────
            EmulatorEntry(
                name       = "PPSSPP",
                packageIds = listOf("org.ppsspp.ppsspp"),
                platforms  = listOf("psp"),
                status     = if (installed("org.ppsspp.ppsspp")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp",
                notes      = "El mejor emulador PSP. Versión Gold sin anuncios.",
                isRecommended = true
            ),

            // ── Dreamcast ───────────────────────────────────────────────
            EmulatorEntry(
                name       = "Redream (Dreamcast)",
                packageIds = listOf("io.recompiled.redream"),
                platforms  = listOf("dreamcast", "dc"),
                status     = if (installed("io.recompiled.redream")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://redream.io/download",
                notes      = "Más fácil de configurar que Flycast. Requiere premium para HD."
            ),
            EmulatorEntry(
                name       = "Flycast Standalone",
                packageIds = listOf("com.flycast.emulator"),
                platforms  = listOf("dreamcast", "naomi", "naomi2", "atomiswave"),
                status     = if (installed("com.flycast.emulator")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/flyinghead/flycast/releases",
                notes      = "Soporta NAOMI, NAOMI2 y Atomiswave además de DC.",
                isRecommended = true
            ),

            // ── Saturn ──────────────────────────────────────────────────
            EmulatorEntry(
                name       = "Yaba Sanshiro 2 (Saturn)",
                packageIds = listOf("org.uoyabause.uoyabause", "org.uoyabause.android.uoyabause2"),
                platforms  = listOf("saturn"),
                status     = if (installed("org.uoyabause.uoyabause", "org.uoyabause.android.uoyabause2")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=org.uoyabause.android.uoyabause2",
                notes      = "Mejor emulador Saturn en Android."
            ),

            // ── Windows / DOS ───────────────────────────────────────────
            EmulatorEntry(
                name       = "Winlator",
                packageIds = listOf("com.winlator", "com.winlator.cmod"),
                platforms  = listOf("windows", "exodos"),
                status     = if (installed("com.winlator", "com.winlator.cmod")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://winlator.org",
                notes      = "Juegos Windows en Android. Requiere SD 845+ y Vulkan.",
                minAndroid = 10
            ),

            // ── Experimentales ──────────────────────────────────────────
            EmulatorEntry(
                name       = "X1 BOX (Xbox Original)",
                packageIds = listOf("com.izzy2lost.x1box"),
                platforms  = listOf("xbox"),
                status     = if (installed("com.izzy2lost.x1box")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://play.google.com/store/apps/details?id=com.izzy2lost.x1box",
                notes      = "Emulador Xbox original basado en xemu. Requiere Vulkan + 8GB RAM. Necesita MCPX ROM, BIOS y HDD image propios.",
                isRecommended = true,
                minAndroid = 26  // Android 8.0+
            ),
            EmulatorEntry(
                name       = "Xanite (Xbox)",
                packageIds = listOf("org.xanite.emu"),
                platforms  = listOf("xbox"),
                status     = if (installed("org.xanite.emu")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/dev-Ali2008/xanite/releases",
                notes      = "Xbox original. Experimental.",
                minAndroid = 10
            ),
            EmulatorEntry(
                name       = "Ryujinx (Switch)",
                packageIds = listOf("org.ryujinx.android"),
                platforms  = listOf("switch"),
                status     = if (installed("org.ryujinx.android")) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED,
                downloadUrl = "https://github.com/Ryujinx/release-channel-master/releases",
                notes      = "Alternativa. Menor compatibilidad que Sudachi en Android.",
                minAndroid = 12
            )
        )
    }

    /** Devuelve solo los emuladores instalados. */
    fun getInstalled(context: Context) = getStatus(context).filter { it.status == InstallStatus.INSTALLED }

    /** Cuenta emuladores instalados. */
    fun countInstalled(context: Context) = getInstalled(context).size

    /** Plataformas con cobertura de emuladores instalados. */
    fun coveredPlatforms(context: Context): Set<String> =
        getInstalled(context).flatMap { it.platforms }.toSet()
}
