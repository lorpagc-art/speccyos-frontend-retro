/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log

/**
 * Elige con que core de RetroArch se lanza cada plataforma, y aprende de lo
 * que pasa despues.
 *
 * POR QUE EXISTE
 * --------------
 * El core por defecto salia tal cual del `systeminfo.txt` de cada carpeta de
 * assets, que son ficheros heredados de ES-DE. Para todo el arcade eso pone
 * `mamearcade` (el MAME actual), que es el emulador MAS ESTRICTO que existe
 * con la version del set de ROMs: si el .zip no es exactamente del set de esa
 * build de MAME, no arranca y deja la pantalla en negro, sin decir nada.
 *
 * Verificado en consola (GameMT E5 Plus, Unisoc ums9230, Android 14) el 10 de
 * septiembre de 2026 con `1942.zip`:
 *
 *   - `mamearcade`     -> pantalla negra, ni un mensaje.
 *   - `fbneo`          -> "one of your romsets is missing files for THIS
 *                          VERSION of FBNeo. Verify: 1942".
 *   - `mame2003_plus`  -> arranca y se juega.
 *
 * Los sets que tiene la gente en estas consolas son casi siempre de la epoca
 * MAME 0.78, que es justo lo que emula mame2003_plus. De ahi el orden curado.
 *
 * COMO APRENDE
 * ------------
 * No hay forma de que RetroArch nos conteste si el core cargo o no: se lanza
 * con un Intent y no devuelve nada. Pero el tiempo que tarda el usuario en
 * volver lo dice casi todo:
 *
 *   - Vuelve en menos de [ARRANQUE_FALLIDO_MS] -> ese core no arranco. Se
 *     marca como sospechoso para esa plataforma y el siguiente lanzamiento usa
 *     el candidato siguiente.
 *   - Juega mas de [PARTIDA_VALIDA_MS] -> ese core va bien. Se guarda como
 *     preferido de la plataforma y se limpian los sospechosos.
 *
 * Asi, a la segunda o tercera partida, cada plataforma acaba con el core que
 * de verdad funciona con las ROMs de ESA consola, sin que el usuario toque
 * nada y sin necesitar permisos: la carpeta de cores de RetroArch es
 * `drwx------` de otro UID y no se puede ni listar.
 *
 * NUNCA se propone un core que el `systeminfo.txt` de la plataforma no
 * declare: el orden curado solo REORDENA lo que ya estaba permitido.
 */
object SpeccyCoreResolver {

    private const val TAG = "CoreResolver"
    private const val PREFS = "speccy_core_resolver"

    /** Por debajo de esto se da por hecho que el core no llego a arrancar. */
    private const val ARRANQUE_FALLIDO_MS = 20_000L

    /** A partir de aqui la partida cuenta como buena. */
    private const val PARTIDA_VALIDA_MS = 45_000L

    /**
     * Orden de preferencia por familia. Solo se define donde el core por
     * defecto heredado es mala eleccion; el resto de plataformas se quedan
     * exactamente como estaban.
     */
    private val CURADO_ARCADE = listOf(
        "mame2003_plus",  // sets 0.78: los mas extendidos en consolas chinas
        "fbneo",          // el mas rapido cuando el set le encaja
        "mame2010",       // sets 0.139
        "mame2003",       // 0.78 sin los añadidos de _plus
        "mame2000",       // 0.37b5, para sets muy viejos
        "fbalpha2012",
        "mamearcade"      // MAME actual: el ultimo, por estricto
    )

    private val CURADO_NEOGEO = listOf(
        "fbneo",
        "fbalpha2012_neogeo",
        "mame2003_plus",
        "geolith",
        "mamearcade"
    )

    /** Plataformas que van con sets de MAME/FBNeo. */
    private val FAMILIA_ARCADE = setOf(
        "arcade", "mame", "cps", "cps1", "cps2", "cps3",
        "consolearcade", "fbneo", "fba", "hbmame"
    )

    /**
     * Curado por GAMA DE DISPOSITIVO (sep-2026). Los systeminfo.txt heredados
     * traen como core por defecto el mas "preciso" del escritorio, que en un
     * Unisoc T610 o un Helio G99 es justo el que no llega a 60 fps:
     *
     *   - psx: `mednafen_psx` es interprete puro (sin dynarec ARM). En gama
     *     baja/media va `pcsx_rearmed` (dynarec NEON); en gama alta
     *     `swanstation` (DuckStation: reescalado, PGXP) y ya con margen.
     *   - saturn: `mednafen_saturn` tampoco tiene dynarec ARM y pide un
     *     Snapdragon 8 Gen 2 para ir fluido; `yabasanshiro` es el unico que
     *     se juega en un T820 o un G99.
     *   - n64: `parallel_n64` (renderer glN64/rice) rinde mas en Mali de gama
     *     baja; `mupen64plus_next` da mejor imagen en gama media/alta.
     *   - snes: `snes9x2010` es un 30-40 % mas rapido que `snes9x` actual y
     *     lo notan los Rockchip/T610 con juegos SuperFX; en gama alta `snes9x`.
     *   - nes: `mesen` es exacto pero pesado; `fceumm` para gama baja.
     *   - gba/gb: `mgba` en todo salvo gama baja, donde `gpsp` (GBA) y
     *     `gambatte` (GB/GBC) van sobrados.
     *
     * Solo REORDENA lo que la plataforma declara, como el resto del curado.
     */
    private val CURADO_BAJA: Map<String, List<String>> = mapOf(
        "psx" to listOf("pcsx_rearmed", "swanstation", "mednafen_psx_hw", "mednafen_psx"),
        "saturn" to listOf("yabasanshiro", "yabause", "mednafen_saturn"),
        "n64" to listOf("parallel_n64", "mupen64plus_next_gles3", "mupen64plus_next"),
        "snes" to listOf("snes9x2010", "snes9x2005_plus", "snes9x", "mednafen_supafaust"),
        "nes" to listOf("fceumm", "nestopia", "quicknes", "mesen"),
        "gba" to listOf("gpsp", "mgba", "vba_next", "vbam"),
        "psp" to listOf("ppsspp"),
        "dreamcast" to listOf("flycast")
    )
    private val CURADO_MEDIA: Map<String, List<String>> = mapOf(
        "psx" to listOf("pcsx_rearmed", "swanstation", "mednafen_psx_hw", "mednafen_psx"),
        "saturn" to listOf("yabasanshiro", "mednafen_saturn", "yabause"),
        "n64" to listOf("mupen64plus_next_gles3", "mupen64plus_next", "parallel_n64"),
        "snes" to listOf("snes9x", "snes9x2010", "bsnes", "mednafen_supafaust"),
        "nes" to listOf("fceumm", "nestopia", "mesen", "quicknes"),
        "gba" to listOf("mgba", "vbam", "gpsp", "vba_next")
    )
    private val CURADO_ALTA: Map<String, List<String>> = mapOf(
        "psx" to listOf("swanstation", "pcsx_rearmed", "mednafen_psx_hw", "mednafen_psx"),
        "saturn" to listOf("yabasanshiro", "mednafen_saturn", "yabause"),
        "n64" to listOf("mupen64plus_next_gles3", "mupen64plus_next", "parallel_n64"),
        "snes" to listOf("snes9x", "bsnes", "bsnes_hd_beta", "snes9x2010"),
        "nes" to listOf("mesen", "nestopia", "fceumm", "quicknes"),
        "gba" to listOf("mgba", "vbam", "vba_next", "gpsp")
    )

    /** Alias de carpeta -> clave de la tabla de curado. */
    private val ALIAS = mapOf(
        "ps1" to "psx", "sfc" to "snes", "snesna" to "snes", "famicom" to "nes", "fds" to "nes",
        "saturnjp" to "saturn", "n64dd" to "n64", "dc" to "dreamcast", "gbc" to "gba", "gb" to "gba"
    )

    private fun curadoPara(platformId: String): List<String> {
        val id = platformId.lowercase().trim()
        if (id == "neogeo") return CURADO_NEOGEO
        if (id in FAMILIA_ARCADE) return CURADO_ARCADE
        val key = ALIAS[id] ?: id
        val rank = runCatching { SpeccyPerformanceTuner.activeDevice().tier.rank }.getOrDefault(2)
        val tabla = when {
            rank <= 1 -> CURADO_BAJA          // Rockchip RK3566, Amlogic, T610 sin ventilador
            rank <= 3 -> CURADO_MEDIA         // T820, Helio G99, Dimensity 900-1100, SD 845
            else -> CURADO_ALTA               // SD 865+, Dimensity 8300+, 8 Gen 2+
        }
        return tabla[key].orEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Eleccion
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Candidatos para [platformId], mejor primero.
     *
     * Solo entran cores que [sys] declare (por defecto + alternativos): el
     * curado reordena, no inventa. Los sospechosos se van al final en vez de
     * desaparecer, para que la lista nunca quede vacia.
     */
    fun candidatosPara(
        context: Context,
        platformId: String,
        sys: RetroArchDatabase.SystemInfo?
    ): List<String> {
        val declarados = buildList {
            sys?.defaultCore?.let { add(it) }
            sys?.alternativeCores?.let { addAll(it) }
        }.filter { it.isNotBlank() && it != "detect" }.distinct()

        if (declarados.isEmpty()) return emptyList()

        val curado = curadoPara(platformId)
        val ordenados = curado.filter { it in declarados } +
            declarados.filterNot { it in curado }

        val sospechosos = sospechososDe(context, platformId)
        val (dudosos, sanos) = ordenados.partition { it in sospechosos }
        return sanos + dudosos
    }

    /**
     * Core con el que lanzar. Respeta siempre lo que el usuario haya fijado a
     * mano, salvo que ese core ya se haya demostrado que no arranca.
     */
    fun resolver(
        context: Context,
        platformId: String,
        sys: RetroArchDatabase.SystemInfo?,
        preferidoDelUsuario: String?
    ): String {
        val candidatos = candidatosPara(context, platformId, sys)
        val sospechosos = sospechososDe(context, platformId)

        if (!preferidoDelUsuario.isNullOrBlank() && preferidoDelUsuario !in sospechosos) {
            return preferidoDelUsuario
        }
        return candidatos.firstOrNull() ?: sys?.defaultCore ?: "detect"
    }

    // ─────────────────────────────────────────────────────────────────────
    // Aprendizaje
    // ─────────────────────────────────────────────────────────────────────

    /** Se llama justo al lanzar, para poder cronometrar la vuelta. */
    fun anotarLanzamiento(context: Context, platformId: String, core: String) {
        if (core.isBlank() || core == "detect") return
        prefs(context).edit()
            .putString("ultima_plataforma", platformId)
            .putString("ultimo_core", core)
            .putLong("ultimo_lanzamiento", System.currentTimeMillis())
            .apply()
    }

    /**
     * Se llama al volver del juego. Decide si el core de la ultima partida se
     * asciende a preferido o se marca como sospechoso.
     *
     * Devuelve el core siguiente a probar cuando cree que no arranco, o null
     * si no hay nada que proponer.
     */
    fun anotarRegreso(
        context: Context,
        settingsManager: SettingsManager
    ): SiguienteIntento? {
        val p = prefs(context)
        val plataforma = p.getString("ultima_plataforma", null) ?: return null
        val core = p.getString("ultimo_core", null) ?: return null
        val lanzado = p.getLong("ultimo_lanzamiento", 0L)
        if (lanzado <= 0L) return null

        // Una sola lectura: la partida ya esta contabilizada pase lo que pase.
        p.edit().remove("ultimo_lanzamiento").apply()

        val duracion = System.currentTimeMillis() - lanzado
        if (duracion < 0) return null  // reloj hacia atras: no concluimos nada

        return when {
            duracion < ARRANQUE_FALLIDO_MS -> {
                marcarSospechoso(context, plataforma, core)
                val sys = RetroArchDatabase.findSystemById(plataforma)
                val siguiente = candidatosPara(context, plataforma, sys)
                    .firstOrNull { it != core && it !in sospechososDe(context, plataforma) }
                Log.i(TAG, "'$core' volvio en ${duracion}ms en '$plataforma': " +
                    "marcado sospechoso. Siguiente: $siguiente")
                siguiente?.let { SiguienteIntento(plataforma, core, it) }
            }
            duracion > PARTIDA_VALIDA_MS -> {
                settingsManager.setPreferredCore(plataforma, core)
                limpiarSospechosos(context, plataforma)
                Log.i(TAG, "'$core' aguanto ${duracion}ms en '$plataforma': " +
                    "queda como preferido")
                null
            }
            else -> null  // zona gris: no se concluye nada
        }
    }

    /** Lo que hay que ofrecer al usuario cuando un core no arranco. */
    data class SiguienteIntento(
        val platformId: String,
        val coreFallido: String,
        val coreSugerido: String
    )

    // ─────────────────────────────────────────────────────────────────────
    // Sospechosos
    // ─────────────────────────────────────────────────────────────────────

    fun sospechososDe(context: Context, platformId: String): Set<String> =
        prefs(context).getStringSet(clave(platformId), emptySet()) ?: emptySet()

    private fun marcarSospechoso(context: Context, platformId: String, core: String) {
        val actuales = sospechososDe(context, platformId).toMutableSet()
        actuales.add(core)
        prefs(context).edit().putStringSet(clave(platformId), actuales).apply()
    }

    fun limpiarSospechosos(context: Context, platformId: String) {
        prefs(context).edit().remove(clave(platformId)).apply()
    }

    private fun clave(platformId: String) = "sospechosos_${platformId.lowercase().trim()}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
