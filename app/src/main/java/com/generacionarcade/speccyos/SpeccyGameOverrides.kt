package com.generacionarcade.speccyos

import android.content.Context

/**
 * SpeccyGameOverrides
 * ----------------------------------------------------------------------------
 * Preferencias POR JUEGO, no sólo por plataforma.
 *
 * `SettingsManager` sólo permitía fijar core y emulador por plataforma. Es lo
 * primero que pide un usuario avanzado: "este Resident Evil con Beetle PSX HW
 * porque el resto va perfecto con SwanStation", o "este juego concreto con
 * DuckStation standalone y los demás con RetroArch". ES-DE y Pegasus lo llevan
 * de serie; aquí no existía.
 *
 * La resolución es en cascada, de lo más específico a lo más general:
 *   juego  ->  plataforma  ->  valor por defecto del sistema
 */
class SpeccyGameOverrides(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("speccy_game_overrides", Context.MODE_PRIVATE)

    /** Clave estable e independiente de la URI SAF, que puede cambiar. */
    private fun keyFor(game: Game): String =
        "${game.platformId}|${game.fileName}".lowercase()

    // -- Core de libretro ----------------------------------------------------

    fun setCore(game: Game, coreId: String?) {
        val k = "core_${keyFor(game)}"
        prefs.edit().apply { if (coreId.isNullOrBlank()) remove(k) else putString(k, coreId) }.apply()
    }

    fun getCore(game: Game): String? = prefs.getString("core_${keyFor(game)}", null)

    // -- Emulador standalone -------------------------------------------------

    fun setEmulatorPackage(game: Game, pkg: String?) {
        val k = "pkg_${keyFor(game)}"
        prefs.edit().apply { if (pkg.isNullOrBlank()) remove(k) else putString(k, pkg) }.apply()
    }

    fun getEmulatorPackage(game: Game): String? = prefs.getString("pkg_${keyFor(game)}", null)

    // -- Resolucion en cascada -----------------------------------------------

    /**
     * Core efectivo: override del juego -> preferencia de plataforma ->
     * core por defecto del sistema -> "detect" (que RetroArch elija).
     */
    fun resolveCore(game: Game, settings: SettingsManager): String =
        getCore(game)
            ?: settings.getPreferredCore(game.platformId)
            ?: RetroArchDatabase.findSystemById(game.platformId)?.defaultCore
            ?: "detect"

    /** Paquete efectivo del emulador standalone, o null para usar RetroArch. */
    fun resolveEmulatorPackage(game: Game, settings: SettingsManager): String? =
        getEmulatorPackage(game) ?: settings.getStandalonePackage(game.platformId)

    fun hasOverride(game: Game): Boolean =
        getCore(game) != null || getEmulatorPackage(game) != null

    fun clear(game: Game) {
        val k = keyFor(game)
        prefs.edit().remove("core_$k").remove("pkg_$k").apply()
    }

    /** Numero de juegos con ajustes propios, para mostrarlo en Ajustes. */
    fun overrideCount(): Int = prefs.all.keys
        .filter { it.startsWith("core_") || it.startsWith("pkg_") }
        // Un juego con core Y emulador propios aportaba dos claves y contaba dos veces.
        .map { it.substringAfter('_') }
        .distinct()
        .size

    fun clearAll() = prefs.edit().clear().apply()
}
