package com.generacionarcade.speccyos

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * RetroArchPerformanceConfig — Perfiles de rendimiento y rutas de cores.
 * 
 * ESTATALIZACIÓN SPECCY ENGINE V1.0 (Luxury XMB Edition)
 */
object RetroArchPerformanceConfig {

    private const val TAG = "RAPerformance"

    data class PlatformConfig(
        val configFilePath: String,
        val globalConfigPath: String,
        val appendConfig: String,
        val appendConfigPath: String
    )

    /**
     * Ultimo PlatformConfig calculado por plataforma.
     *
     * buildFor() escribe ficheros (y, si hay arbol SAF concedido, pasa por
     * DocumentFile/ContentResolver, que puede tardar cientos de ms). Llamarlo en
     * el hilo principal justo antes de startActivity() retrasaba el arranque del
     * emulador. Ahora el lanzamiento usa lo ya calculado y el recalculo se hace
     * despues, en IO, para el siguiente arranque.
     */
    private val lastConfigByPlatform = java.util.concurrent.ConcurrentHashMap<String, PlatformConfig>()

    /** Ruta publica determinista del appendconfig de una plataforma. */
    private fun publicAppendConfigFile(platformId: String): File =
        File(
            File(android.os.Environment.getExternalStorageDirectory(), "SpeccyOS/ra_configs"),
            "${platformId.lowercase()}_speccy.cfg"
        )

    /**
     * Version BARATA para el hilo de UI: no escribe nada, no toca SAF.
     * Devuelve lo ultimo que se calculo en esta sesion y, si no hay nada, mira
     * si el .cfg publico de una ejecucion anterior sigue ahi.
     */
    fun cachedFor(platformId: String): PlatformConfig {
        val key = platformId.lowercase()
        lastConfigByPlatform[key]?.let { return it }
        val f = publicAppendConfigFile(key)
        // Un .cfg escrito por una version anterior puede traer todavia lineas de
        // entrada, y esas matan los atajos de RetroArch dentro del juego. Vive en
        // almacenamiento COMPARTIDO (/sdcard/SpeccyOS/ra_configs/), asi que
        // actualizar la app no lo borra: sigue ahi y se le sigue pasando a
        // RetroArch por EXTRA_APPENDCONFIG.
        //
        // El criterio es el mismo filtro que usa la escritura. Antes se miraban
        // solo cuatro claves sueltas (input_player1_, input_enable_hotkey_btn,
        // input_menu_toggle_btn, input_exit_emulator_btn) y cualquier otra linea
        // de entrada de una build vieja -input_enable_hotkey, input_player2_*,
        // input_hotkey_block_delay...- pasaba el control y se fusionaba igual.
        val usable = f.exists() && f.length() > 0L && runCatching {
            !SpeccyRaTuning.tieneClavesProhibidas(f.readText())
        }.getOrDefault(false)
        if (f.exists() && !usable) {
            // Se borra, no solo se ignora: asi el residuo no puede volver a
            // colarse en un lanzamiento posterior de esta misma sesion, y el
            // usuario recupera los atajos con solo actualizar, sin tener que
            // buscar el fichero a mano con un explorador. buildFor() lo
            // reescribira limpio en segundo plano para el siguiente arranque.
            val borrado = runCatching { f.delete() }.getOrDefault(false)
            Log.w(
                TAG,
                "Appendconfig obsoleto con claves de entrada en ${f.absolutePath}: " +
                    if (borrado) "borrado." else "NO se ha podido borrar, se ignora."
            )
        }
        val path = if (usable) f.absolutePath else ""
        return PlatformConfig(
            configFilePath = "",
            globalConfigPath = "",
            appendConfig = "",
            appendConfigPath = path
        )
    }

    private var hasVulkanCache: Boolean? = null
    private val androidVersion: Int = Build.VERSION.SDK_INT

    private fun detectVulkan(context: Context): Boolean {
        hasVulkanCache?.let { return it }
        val result = try {
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        } catch (_: Exception) { false }
        hasVulkanCache = result
        return result
    }

    private fun videoDriver(context: Context): String = when {
        detectVulkan(context) -> "vulkan"
        androidVersion >= Build.VERSION_CODES.Q -> "glcore"
        else -> "gl"
    }

    private fun audioDriver(): String = if (androidVersion >= Build.VERSION_CODES.O) "aaudio" else "opensl"

    fun buildFor(context: Context, platformId: String, retroArchPkg: String): PlatformConfig {
        val vid = videoDriver(context)
        val aud = audioDriver()

        val raDataDir = try {
            context.packageManager.getPackageInfo(retroArchPkg, 0).applicationInfo?.dataDir
                ?: "/data/user/0/$retroArchPkg"
        } catch (_: Exception) {
            "/data/user/0/$retroArchPkg"
        }

        val platform = platformId.lowercase()
        val appendCfg = buildAppendConfig(context, platform, vid, aud, retroArchPkg)

        // ── EL BUG QUE HACÍA INÚTIL TODO EL "SPECCY ENGINE" ─────────────────
        //
        // El .cfg se escribía en context.cacheDir/ra_configs/, es decir
        //   /data/user/0/com.generacionarcade.speccyos/cache/...
        // y esa ruta se pasaba a RetroArch por EXTRA_APPENDCONFIG. RetroArch corre
        // con OTRO UID: ese directorio es ilegible para él. Resultado: TODOS los
        // overrides de vídeo, audio, latencia, mandos y XMB se descartaban en
        // silencio, mientras la app mostraba "Speccy Engine aplicado".
        //
        // Ahora SpeccyRaTuning elige un destino compartido y nos dice si de verdad
        // ha quedado accesible.
        val fullCfg = if (appendCfg.isBlank()) "" else buildString {
            append(appendCfg)
            appendLine()
            // Ajustes derivados del catálogo de hardware: driver de vídeo, run-ahead,
            // latencia de audio y presupuesto de shaders según el equipo real.
            SpeccyRaTuning.linesFor(platform).forEach { appendLine(it) }
        }

        val write = if (fullCfg.isBlank()) {
            SpeccyRaTuning.WriteResult(null, false, "Sin overrides que aplicar")
        } else {
            SpeccyRaTuning.writeAppendConfig(
                context = context,
                platformId = platform,
                content = fullCfg,
                romsTreeUri = runCatching {
                    SettingsManager(context).romsLocation.takeIf { it.isNotEmpty() }
                        ?.let { android.net.Uri.parse(it) }
                }.getOrNull()
            )
        }
        if (fullCfg.isNotBlank() && !write.readableByOtherApps) {
            Log.w(TAG, "El appendconfig NO es legible por RetroArch: ${write.reason}")
        }

        // ── CONFIGFILE ──────────────────────────────────────────────────────
        // Antes estaba hardcodeado a la ruta de almacenamiento externo. Si ese
        // fichero no existe (las builds de Play guardan en /data/data/<pkg>/),
        // RetroArch REGENERA un retroarch.cfg limpio y el usuario PIERDE toda su
        // configuración. Ahora se prueban los candidatos y, si ninguno existe, no
        // se manda el extra: mejor no tocar nada que destruir su configuración.
        val cfgCandidates = listOf(
            "/storage/emulated/0/Android/data/$retroArchPkg/files/retroarch.cfg",
            "$raDataDir/retroarch.cfg",
            "/storage/emulated/0/RetroArch/retroarch.cfg"
        )
        val cfgPath = cfgCandidates.firstOrNull { File(it).exists() && File(it).canRead() } ?: ""
        if (cfgPath.isEmpty()) {
            Log.i(TAG, "No se ha localizado un retroarch.cfg accesible: se omite CONFIGFILE.")
        }

        val result = PlatformConfig(
            configFilePath   = cfgPath,
            globalConfigPath = cfgPath,
            appendConfig     = fullCfg,
            appendConfigPath = write.path ?: ""
        )
        // Queda listo para el siguiente lanzamiento sin volver a escribir nada
        // en el hilo principal.
        lastConfigByPlatform[platform] = result
        return result
    }

    private fun buildAppendConfig(context: Context, platform: String, videoDriver: String, audioDriver: String, pkg: String): String =
        buildString {
            val settings = SettingsManager(context)
            
            if (settings.isRetroArchVisualsEnabled || settings.isRetroArchPerformanceEnabled) {
                appendLine("# SpeccyOS Neural Performance Override — $platform")
                appendLine("# Speccy Engine Identity Injection V1.0")
                appendLine()
            }

            if (settings.isRetroArchVisualsEnabled) {
                // ── IDENTIDAD VISUAL "SPECCY ENGINE" (LUXURY SETTINGS) ─────────
                if (pkg == "com.retroarch.speccy") {
                    appendLine("menu_driver = \"xmb\"")
                    appendLine("xmb_menu_color_theme = \"4\"") // Electric Blue
                    appendLine("menu_font_color_red = \"0\"")
                    appendLine("menu_font_color_green = \"242\"")
                    appendLine("menu_font_color_blue = \"255\"")
                    
                    // Efectos Premium
                    appendLine("menu_shader_pipeline = \"2\"") // Ribbon Wave
                    appendLine("menu_wallpaper_opacity = \"0.300000\"")
                    appendLine("xmb_alpha_factor = \"75\"") // Transparencia elegante
                    appendLine("xmb_shadows_enable = \"true\"")
                    appendLine("menu_linear_filter = \"true\"")
                    appendLine("menu_scale_factor = \"1.000000\"")
                    
                    // Opciones Bloqueadas (Console Mode)
                    appendLine("menu_show_advanced_settings = \"true\"")
                    appendLine("menu_show_online_updater = \"true\"")
                    appendLine("menu_show_core_updater = \"true\"")
                    appendLine("menu_show_load_content = \"true\"")
                    appendLine("menu_show_quit_retroarch = \"true\"")
                } else {
                    appendLine("menu_driver = \"rgui\"")
                }
            }

            if (settings.isRetroArchPerformanceEnabled) {
                // ── DRIVERS & SYNC ──────────────────────────────────────────────
                appendLine("video_driver = \"$videoDriver\"")
                appendLine("audio_driver = \"$audioDriver\"")
                appendLine("video_vsync = \"true\"")
                appendLine("audio_sync = \"true\"")
                appendLine("audio_latency = \"32\"")
                appendLine("audio_max_timing_skew = \"0.05\"")

                // ── OCULTAR NOTIFICACIONES Y OSD (MODO CONSOLA) ────────────────
                appendLine("video_font_enable = \"false\"")
                appendLine("notification_show_autoconfig = \"false\"")
                appendLine("menu_show_configs = \"false\"")
                
                val dataDir = try {
                    context.packageManager.getPackageInfo(pkg, 0).applicationInfo?.dataDir ?: "/data/user/0/$pkg"
                } catch (e: Exception) { "/data/user/0/$pkg" }
                // La clave es `rgui_config_directory`, NO `dir_config`.
                //
                // `dir_config` no existe en RetroArch: comprobado contra su
                // propio codigo fuente (`configuration.c` declara
                // SETTING_PATH("rgui_config_directory", ...) y no hay ni una
                // aparicion de "dir_config"). La linea se escribia, RetroArch la
                // ignoraba, y el directorio de configuracion se quedaba SIN
                // DEFINIR: de ahi el aviso "Config directory is not set" que sale
                // abajo al arrancar cada juego (command.c:1750, cuando
                // `dir_menu_config` y `rarch_path_config` estan vacios).
                appendLine("rgui_config_directory = \"$dataDir/files\"")



                // ── OVERRIDES POR PLATAFORMA ────────────────────────────────────
                when (platform) {
                    "n64" -> {
                        appendLine("video_driver = \"gl\"")
                        appendLine("video_threaded = \"true\"")
                        appendLine("mupen64plus-screensize = \"960x720\"")
                    }
                    "psx", "ps1" -> {
                        appendLine("video_threaded = \"true\"")
                        appendLine("pcsx_rearmed_drc = \"enabled\"")
                    }
                    else -> {
                        appendLine("video_threaded = \"true\"")
                    }
                }
            }
            
            // ── NO REINTRODUCIR LA INYECCION DE input_player1_*_btn ─────────
            //
            // Esto ya se elimino una vez (ver REVERSION_Y_HISTORIAL_CAMBIOS.md,
            // seccion 3 "Fix de Controles (RetroArch Input)") y volvio a colarse.
            //
            // KeyMapper guarda KEYCODES DE ANDROID (KEYCODE_BUTTON_A = 96,
            // KEYCODE_BUTTON_SELECT = 109...). Los campos input_playerN_*_btn de
            // retroarch.cfg esperan el INDICE de boton del mando (0, 1, 2...).
            // Escribir "96" ahi apunta a un boton que no existe en ningun mando.
            //
            // Con los botones normales eso ya rompia el mando; con
            // input_enable_hotkey_btn es peor: RetroArch exige mantener pulsado
            // ese boton ANTES de aceptar cualquier atajo, y si el boton no existe
            // NINGUN atajo funciona (menu, guardado rapido, salir, avance rapido).
            // Ese es el sintoma de "las teclas de acceso rapido no funcionan
            // dentro de RetroArch".
            //
            // La autoconfiguracion nativa de RetroArch detecta el mando sola y es
            // la opcion estable. Si algun dia se quiere forzar el mapeado, hay que
            // traducir keycode de Android -> indice de boton del mando, no volcar
            // el keycode tal cual.
            if (settings.isCustomControllerMappingEnabled) {
                Log.i(
                    TAG,
                    "Mapeado forzado activado en ajustes, pero NO se inyecta: los " +
                        "keycodes de Android no son indices de boton de RetroArch. " +
                        "Se deja la autoconfiguracion nativa."
                )
            }
        }

    fun resolveCorePath(context: Context, retroArchPkg: String, core: String): String {
        if (core == "detect" || core.isBlank()) return ""
        val baseName = "${core}_libretro_android.so"
        val raNativeLibDir = try {
            context.packageManager.getPackageInfo(retroArchPkg, 0).applicationInfo?.nativeLibraryDir
        } catch (_: Exception) { null }

        val candidates = buildList {
            raNativeLibDir?.let { add("$it/$baseName") }
            add("/data/data/$retroArchPkg/cores/$baseName")
            add("/storage/emulated/0/Android/data/$retroArchPkg/files/cores/$baseName")
        }

        for (path in candidates) {
            val file = File(path)
            if (file.exists() && file.canRead()) return path
        }

        // AQUI ESTABA EL FALLO QUE ROMPIO EL LANZAMIENTO.
        //
        // El bucle de arriba comprueba exists()/canRead() CON NUESTRO UID. El
        // directorio de cores de RetroArch (/data/data/<pkg>/cores/) pertenece a
        // OTRO UID, asi que en Android 11+ esa comprobacion SIEMPRE falla, por
        // mucho que el core este ahi. Devolver "" dejaba el intent sin el extra
        // LIBRETRO y RetroArch abria su menu en vez del juego: exactamente el
        // sintoma de "no lanza los juegos".
        //
        // Quien abre el .so es RetroArch, no nosotros: basta con darle la ruta
        // canonica, que es lo que se hacia originalmente y funcionaba. Solo
        // devolvemos "" si ni siquiera sabemos como se llama el core.
        val fallback = "/data/data/$retroArchPkg/cores/$baseName"
        Log.i(
            TAG,
            "Core '$core' no verificable desde nuestro UID (normal en Android 11+); " +
                "se envia la ruta canonica y RetroArch la resuelve: $fallback"
        )
        return fallback
    }
}
