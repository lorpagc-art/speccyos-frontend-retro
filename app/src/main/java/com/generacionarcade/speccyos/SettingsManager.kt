/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("speccy_settings", Context.MODE_PRIVATE)

    companion object {
        const val EMULATOR_RETROARCH = "RETROARCH"
        const val EMULATOR_STANDALONE = "STANDALONE"

        const val THEME_INMERSIVE = "INMERSIVE"
        const val THEME_ULTRA = "ULTRA"
        const val THEME_SPECCY_DESKTOP = "SPECCY_OS"
        const val THEME_DUAL_SCREEN = "DUAL_SCREEN"

        /** Tema Studio: carril de sistemas a la izquierda y rejilla de juegos. */
        const val THEME_STUDIO = "STUDIO"

        const val ARTWORK_STYLE_CYBERPUNK = "CYBERPUNK"
        const val ARTWORK_STYLE_ICONIC = "ICONIC_CHARACTER"
        const val ARTWORK_STYLE_STUDIO = "STUDIO"

        const val MEDIA_PRIORITY_2D = "box-2d"
        const val MEDIA_PRIORITY_3D = "box-3d"
        const val MEDIA_PRIORITY_SCREENSHOT = "screenshot"
        const val MEDIA_PRIORITY_MIX = "mixrb"

        const val ASPECT_RATIO_16_9 = "16:9"
        const val ASPECT_RATIO_9_16 = "9:16"
        const val ASPECT_RATIO_1_1 = "1:1"
        const val ASPECT_RATIO_4_3 = "4:3"
        const val ASPECT_RATIO_3_2 = "3:2"
        const val ASPECT_RATIO_AUTO = "AUTO"

        const val DUAL_STYLE_CYBER = "CYBER"
        const val DUAL_STYLE_CLASSIC = "CLASSIC"
        /** Estilo Studio para el modo de dos pantallas. */
        const val DUAL_STYLE_STUDIO = "STUDIO"

        // ── Qué muestran las tarjetas del tema Studio ────────────────────────
        /** Solo carátula. */
        const val STUDIO_MEDIA_COVER = "COVER"
        /** Solo captura de pantalla. */
        const val STUDIO_MEDIA_SCREENSHOT = "SCREENSHOT"
        /** Carátula en todas y vídeo en la tarjeta enfocada. */
        const val STUDIO_MEDIA_COVER_VIDEO = "COVER_VIDEO"
        /** Captura en todas y vídeo en la tarjeta enfocada. */
        const val STUDIO_MEDIA_SCREENSHOT_VIDEO = "SCREENSHOT_VIDEO"

        /** ¿Este modo reproduce vídeo en la tarjeta enfocada? */
        fun studioMediaUsesVideo(media: String): Boolean =
            media == STUDIO_MEDIA_COVER_VIDEO || media == STUDIO_MEDIA_SCREENSHOT_VIDEO

        /** ¿La imagen fija es la captura (true) o la carátula (false)? */
        fun studioMediaPrefersScreenshot(media: String): Boolean =
            media == STUDIO_MEDIA_SCREENSHOT || media == STUDIO_MEDIA_SCREENSHOT_VIDEO
    }

    var isDebugFreeModeActive: Boolean
        get() = prefs.getBoolean("debug_free_mode", false)
        set(value) = prefs.edit().putBoolean("debug_free_mode", value).apply()

    var isHardwareConfigured: Boolean
        get() = prefs.getBoolean("hw_configured", false)
        set(value) = prefs.edit().putBoolean("hw_configured", value).apply()

    var lastIntroImageIndex: Int
        get() = prefs.getInt("last_intro_img", 2)
        set(value) = prefs.edit().putInt("last_intro_img", value).apply()

    var currentTheme: String
        get() = prefs.getString("current_theme", THEME_ULTRA) ?: THEME_ULTRA
        set(value) = prefs.edit().putString("current_theme", value).apply()

    var systemArtworkStyle: String
        get() = prefs.getString("system_artwork_style", ARTWORK_STYLE_CYBERPUNK) ?: ARTWORK_STYLE_CYBERPUNK
        set(value) = prefs.edit().putString("system_artwork_style", value).apply()

    // --- NUEVO: Opciones de Pantalla y Escalado ---
    var screenAspectRatio: String
        get() = prefs.getString("screen_aspect_ratio", ASPECT_RATIO_AUTO) ?: ASPECT_RATIO_AUTO
        set(value) = prefs.edit().putString("screen_aspect_ratio", value).apply()

    var screenWidthScale: Float
        get() = prefs.getFloat("screen_width_scale", 1.0f)
        set(value) = prefs.edit().putFloat("screen_width_scale", value).apply()

    var screenHeightScale: Float
        get() = prefs.getFloat("screen_height_scale", 1.0f)
        set(value) = prefs.edit().putFloat("screen_height_scale", value).apply()

    var artworkAspectRatio: String
        get() = prefs.getString("artwork_aspect_ratio", ASPECT_RATIO_16_9) ?: ASPECT_RATIO_16_9
        set(value) = prefs.edit().putString("artwork_aspect_ratio", value).apply()
        
    // --- NUEVO: Escala de fuente Global ---
    var uiFontScale: Float
        get() = prefs.getFloat("ui_font_scale", 1.0f)
        set(value) = prefs.edit().putFloat("ui_font_scale", value).apply()

    // --- Dual Screen Settings ---
    /**
     * Qué enseñan las tarjetas del tema Studio. El valor antiguo "VIDEO"
     * (anterior a que hubiera cuatro modos) se traduce a carátula + vídeo.
     */
    var studioCardMedia: String
        get() = (prefs.getString("studio_card_media", STUDIO_MEDIA_COVER) ?: STUDIO_MEDIA_COVER)
            .let { if (it == "VIDEO") STUDIO_MEDIA_COVER_VIDEO else it }
        set(value) = prefs.edit().putString("studio_card_media", value).apply()

    var dualScreenStyle: String
        get() = prefs.getString("dual_screen_style", DUAL_STYLE_CYBER) ?: DUAL_STYLE_CYBER
        set(value) = prefs.edit().putString("dual_screen_style", value).apply()

    var uiSlotLeft: String
        get() = prefs.getString("ui_slot_left", "VIDEO") ?: "VIDEO"
        set(value) = prefs.edit().putString("ui_slot_left", value).apply()

    var uiSlotRight: String
        get() = prefs.getString("ui_slot_right", "LIST") ?: "LIST"
        set(value) = prefs.edit().putString("ui_slot_right", value).apply()

    var ndsBodyColor: Int
        get() = prefs.getInt("nds_body_color", 0xFFE0E0E0.toInt())
        set(value) = prefs.edit().putInt("nds_body_color", value).apply()


    fun setPreferredEmulator(platformId: String, emulatorType: String) {
        prefs.edit().putString("emu_pref_$platformId", emulatorType).apply()
    }

    fun getPreferredEmulator(platformId: String): String {
        val default = when(platformId.lowercase()) {
            "ps2", "gc", "gamecube", "wii", "3ds", "n3ds", "psp", "xbox" -> EMULATOR_STANDALONE
            else -> EMULATOR_RETROARCH
        }
        return prefs.getString("emu_pref_$platformId", default) ?: default
    }

    fun setStandalonePackage(platformId: String, packageName: String) {
        prefs.edit().putString("standalone_pkg_$platformId", packageName).apply()
    }

    fun getStandalonePackage(platformId: String): String? {
        return prefs.getString("standalone_pkg_$platformId", null)
    }

    fun setPreferredCore(platformId: String, coreName: String) {
        prefs.edit().putString("core_pref_$platformId", coreName).apply()
    }

    fun getPreferredCore(platformId: String): String? {
        return prefs.getString("core_pref_$platformId", null)
    }

    fun getEmulatorType(platformId: String): String = getPreferredEmulator(platformId)

    var themePrimaryColor: Int
        get() = prefs.getInt("theme_primary_color", 0xFF00F2FF.toInt())
        set(value) = prefs.edit().putInt("theme_primary_color", value).apply()

    var themeAccentColor: Int
        get() = prefs.getInt("theme_accent_color", 0xFFFF0055.toInt())
        set(value) = prefs.edit().putInt("theme_accent_color", value).apply()

    var isDynamicThemeEnabled: Boolean
        get() = prefs.getBoolean("is_dynamic_theme_enabled", true)
        set(value) = prefs.edit().putBoolean("is_dynamic_theme_enabled", value).apply()

    var showCrtEffect: Boolean
        get() = prefs.getBoolean("show_crt_effect", false)
        set(value) = prefs.edit().putBoolean("show_crt_effect", value).apply()

    var isBackgroundMusicEnabled: Boolean
        get() = prefs.getBoolean("bg_music_enabled", true)
        set(value) = prefs.edit().putBoolean("bg_music_enabled", value).apply()

    var isAttractModeEnabled: Boolean
        get() = prefs.getBoolean("attract_mode_enabled", true)
        set(value) = prefs.edit().putBoolean("attract_mode_enabled", value).apply()

    var lastScanTimestamp: Long
        get() = prefs.getLong("last_scan_ts", 0L)
        set(value) = prefs.edit().putLong("last_scan_ts", value).apply()

    var cachedPlatformIds: Set<String>
        get() = prefs.getStringSet("cached_platforms", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("cached_platforms", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "es") ?: "es"
        set(value) = prefs.edit().putString("app_language", value).apply()

    /**
     * La app es gratuita y completa para todo el mundo: no hay compras ni
     * suscripcion, asi que no hay nada que autorizar. Antes esto dependia de una
     * lista de cinco correos personales incrustada en el binario publicado.
     * Se conserva la propiedad para no romper las llamadas existentes.
     */
    var isAuthorized: Boolean
        get() = true
        set(value) = prefs.edit().putBoolean("is_authorized", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        set(value) = prefs.edit().putString("user_email", value).apply()

    var isProUser: Boolean
        get() = isAuthorized
        set(value) { isAuthorized = value }

    var gamesLaunchedCount: Int
        get() = prefs.getInt("games_launched_count", 0)
        set(value) = prefs.edit().putInt("games_launched_count", value).apply()

    fun canLaunchGame(): Boolean = true

    var aiQueriesToday: Int
        get() {
            resetAiQueriesIfNewDay()
            return prefs.getInt("ai_queries_today", 0)
        }
        set(value) = prefs.edit().putInt("ai_queries_today", value).apply()

    var aiGamesLaunchedToday: Int
        get() {
            resetAiQueriesIfNewDay()
            return prefs.getInt("ai_games_launched_today", 0)
        }
        set(value) = prefs.edit().putInt("ai_games_launched_today", value).apply()

    /** Traducciones OCR consumidas hoy. Se resetea con el resto de contadores diarios. */
    var ocrTranslationsToday: Int
        get() {
            resetAiQueriesIfNewDay()
            return prefs.getInt("ocr_translations_today", 0)
        }
        set(value) = prefs.edit().putInt("ocr_translations_today", value).apply()

    private var lastAiQueryDate: String
        get() = prefs.getString("last_ai_query_date", "") ?: ""
        set(value) = prefs.edit().putString("last_ai_query_date", value).apply()

    /**
     * Marca de tiempo del ultimo reseteo. Sirve para detectar que el reloj del
     * dispositivo ha ido HACIA ATRAS, que era la forma mas comoda de regalarse
     * cuota: poner la fecha en el dia siguiente, gastar, y volver atras.
     */
    private var lastAiResetMillis: Long
        get() = prefs.getLong("last_ai_reset_millis", 0L)
        set(value) = prefs.edit().putLong("last_ai_reset_millis", value).apply()

    private fun resetAiQueriesIfNewDay() {
        val ahora = System.currentTimeMillis()
        val ultimo = lastAiResetMillis

        // Si el reloj va por detras del ultimo reseteo, lo han movido a mano: no se
        // regala dia nuevo. Se deja un dia de margen para cambios de zona horaria y
        // ajustes legitimos por NTP.
        if (ultimo > 0L && ahora < ultimo - 86_400_000L) {
            android.util.Log.w(
                "SettingsManager",
                "Reloj por detras del ultimo reseteo: no se reinicia la cuota diaria"
            )
            return
        }

        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (currentDate != lastAiQueryDate) {
            lastAiResetMillis = ahora
            prefs.edit().putInt("ai_queries_today", 0).apply()
            prefs.edit().putInt("ai_games_launched_today", 0).apply()
            prefs.edit().putInt("ocr_translations_today", 0).apply()
            lastAiQueryDate = currentDate
        }
    }

    /**
     * Clave de programa de TheGamesDB.
     *
     * Por defecto toma la de [com.generacionarcade.speccyos.network.RetrofitInstance.API_KEY],
     * que es la que consume realmente la pasarela. Antes este valor repetia la misma
     * clave escrita a mano, con lo que habia dos copias que podian desincronizarse al
     * rotarla. Ahora hay una sola fuente de verdad y el usuario puede sobrescribirla
     * con la suya desde los ajustes.
     */
    var scraperApiKey: String
        get() = prefs.getString("scraper_api_key", null)
            ?: com.generacionarcade.speccyos.network.RetrofitInstance.API_KEY
        set(value) = prefs.edit().putString("scraper_api_key", value).apply()

    var scraperUsername: String
        get() = prefs.getString("scraper_username", "") ?: ""
        set(value) = prefs.edit().putString("scraper_username", value).apply()

    var scraperPassword: String
        get() = prefs.getString("scraper_password", "") ?: ""
        set(value) = prefs.edit().putString("scraper_password", value).apply()

    var scraperTgdbUsername: String
        get() = prefs.getString("scraper_username", "") ?: ""
        set(value) = prefs.edit().putString("scraper_username", value).apply()

    var scraperTgdbPassword: String
        get() = prefs.getString("scraper_password", "") ?: ""
        set(value) = prefs.edit().putString("scraper_password", value).apply()

    var scraperSource: String
        get() = prefs.getString("scraper_source", "THEGAMESDB") ?: "THEGAMESDB"
        set(value) = prefs.edit().putString("scraper_source", value).apply()

    var scraperMediaLocation: String
        get() = prefs.getString("scraper_media_location", "") ?: ""
        set(value) = prefs.edit().putString("scraper_media_location", value).apply()

    var mediaPriority: String
        get() = prefs.getString("media_priority", MEDIA_PRIORITY_2D) ?: MEDIA_PRIORITY_2D
        set(value) = prefs.edit().putString("media_priority", value).apply()

    var scrapeBoxArt: Boolean
        get() = prefs.getBoolean("scrape_boxart", true)
        set(value) = prefs.edit().putBoolean("scrape_boxart", value).apply()

    var scrapeVideos: Boolean
        get() = prefs.getBoolean("scrape_videos", true)
        set(value) = prefs.edit().putBoolean("scrape_videos", value).apply()

    var scrapeWheel: Boolean
        get() = prefs.getBoolean("scrape_wheel", true)
        set(value) = prefs.edit().putBoolean("scrape_wheel", value).apply()

    var scrapeFanart: Boolean
        get() = prefs.getBoolean("scrape_fanart", true)
        set(value) = prefs.edit().putBoolean("scrape_fanart", value).apply()

    var scrapeCd: Boolean
        get() = prefs.getBoolean("scrape_cd", true)
        set(value) = prefs.edit().putBoolean("scrape_cd", value).apply()

    var scrapeScreenshot: Boolean
        get() = prefs.getBoolean("scrape_screenshot", true)
        set(value) = prefs.edit().putBoolean("scrape_screenshot", value).apply()

    var libShowVideos: Boolean
        get() = prefs.getBoolean("lib_show_videos", true)
        set(value) = prefs.edit().putBoolean("lib_show_videos", value).apply()

    var libShowBoxArt: Boolean
        get() = prefs.getBoolean("lib_show_boxart", true)
        set(value) = prefs.edit().putBoolean("lib_show_boxart", value).apply()

    var libShowWheel: Boolean
        get() = prefs.getBoolean("lib_show_wheel", true)
        set(value) = prefs.edit().putBoolean("lib_show_wheel", value).apply()

    var libShowFanart: Boolean
        get() = prefs.getBoolean("lib_show_fanart", true)
        set(value) = prefs.edit().putBoolean("lib_show_fanart", value).apply()

    var libShowDescription: Boolean
        get() = prefs.getBoolean("lib_show_description", true)
        set(value) = prefs.edit().putBoolean("lib_show_description", value).apply()

    var libShowScreenshot: Boolean
        get() = prefs.getBoolean("lib_show_screenshot", true)
        set(value) = prefs.edit().putBoolean("lib_show_screenshot", value).apply()

    var libShowCdArt: Boolean
        get() = prefs.getBoolean("lib_show_cdart", true)
        set(value) = prefs.edit().putBoolean("lib_show_cdart", value).apply()

    var libCentralMediaSlot: String
        get() = prefs.getString("lib_central_media_slot", "VIDEO") ?: "VIDEO"
        set(value) = prefs.edit().putString("lib_central_media_slot", value).apply()

    var libImmersiveLayoutOrder: String
        get() = prefs.getString("lib_immersive_layout_order", "LIST_HERO_INFO") ?: "LIST_HERO_INFO"
        set(value) = prefs.edit().putString("lib_immersive_layout_order", value).apply()

    var customDashboardPackName: String
        get() = prefs.getString("custom_dashboard_pack", "default") ?: "default"
        set(value) = prefs.edit().putString("custom_dashboard_pack", value).apply()

    var igdbClientId: String
        get() = prefs.getString("igdb_client_id", "") ?: ""
        set(value) = prefs.edit().putString("igdb_client_id", value).apply()

    var igdbClientSecret: String
        get() = prefs.getString("igdb_client_secret", "") ?: ""
        set(value) = prefs.edit().putString("igdb_client_secret", value).apply()

    var igdbAccessToken: String?
        get() = prefs.getString("igdb_access_token", null)
        set(value) = prefs.edit().putString("igdb_access_token", value).apply()

    var raUsername: String
        get() = prefs.getString("ra_username", "") ?: ""
        set(value) = prefs.edit().putString("ra_username", value).apply()

    /**
     * Token de conexion de RetroAchievements (el que acepta RetroArch en
     * `cheevos_token`). Es distinto de la contrasena: se obtiene una vez y se
     * puede revocar. Solo se escribe en el override del core cuando el usuario
     * activa [raPassToRetroArch].
     */
    var raConnectToken: String
        get() = prefs.getString("ra_connect_token", "") ?: ""
        set(value) = prefs.edit().putString("ra_connect_token", value).apply()

    /** ¿Pasar las credenciales de RetroAchievements a RetroArch al lanzar? */
    var raPassToRetroArch: Boolean
        get() = prefs.getBoolean("ra_pass_to_retroarch", false)
        set(value) = prefs.edit().putBoolean("ra_pass_to_retroarch", value).apply()

    /**
     * "Continuar donde lo dejaste": pide a RetroArch que guarde el estado
     * automatico al salir (`savestate_auto_save`) para poder reanudar.
     */
    var continuarPartida: Boolean
        get() = prefs.getBoolean("continuar_partida", true)
        set(value) = prefs.edit().putBoolean("continuar_partida", value).apply()

    /**
     * Ruta del juego para el que el usuario ha descartado la tarjeta de
     * "Continuar": mientras sea la misma, no se vuelve a ofrecer.
     */
    var continuarOcultoPara: String
        get() = prefs.getString("continuar_oculto_para", "") ?: ""
        set(value) = prefs.edit().putString("continuar_oculto_para", value).apply()

    var raToken: String
        get() = prefs.getString("ra_token", "") ?: ""
        set(value) = prefs.edit().putString("ra_token", value).apply()

    var isRAEnabled: Boolean
        get() = prefs.getBoolean("ra_enabled", false)
        set(value) = prefs.edit().putBoolean("ra_enabled", value).apply()

    var isRetroArchVisualsEnabled: Boolean
        get() = prefs.getBoolean("ra_visuals_enabled", false)
        set(value) = prefs.edit().putBoolean("ra_visuals_enabled", value).apply()

    var isRetroArchPerformanceEnabled: Boolean
        get() = prefs.getBoolean("ra_perf_enabled", false)
        set(value) = prefs.edit().putBoolean("ra_perf_enabled", value).apply()

    var isManualPerformanceMode: Boolean
        get() = prefs.getBoolean("manual_perf_mode", false)
        set(value) = prefs.edit().putBoolean("manual_perf_mode", value).apply()

    var isDynamicTuningEnabled: Boolean
        get() = prefs.getBoolean("dynamic_tuning_enabled", true)
        set(value) = prefs.edit().putBoolean("dynamic_tuning_enabled", value).apply()

    var isCloudSyncEnabled: Boolean
        get() = prefs.getBoolean("cloud_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("cloud_sync_enabled", value).apply()

    /**
     * Ruta del ultimo juego lanzado, para poder subir sus partidas al volver.
     *
     * Hace falta porque el retorno se detecta en `MainActivity.onResume`, que
     * no sabe a que juego se jugo: solo `RetroArchSyncManager` guarda la
     * plataforma. Sin esto no habia forma de llamar a `backupGameSaves()`, que
     * es justo por lo que Nebula Sync solo funcionaba a medias: restauraba de
     * Drive, pero no subia nunca nada, asi que en Drive no habia nada que
     * restaurar.
     */
    var lastPlayedGamePath: String
        get() = prefs.getString("last_played_game_path", "") ?: ""
        set(value) = prefs.edit().putString("last_played_game_path", value).apply()

    /**
     * Arbol SAF de la carpeta de RetroArch (`/sdcard/RetroArch`), concedido por
     * el usuario desde Ajustes.
     *
     * Sin esto no hay forma de ver las partidas. En Android 11+ un
     * `java.io.File` sobre esa ruta es ilegible sin "Acceso a todos los
     * archivos", que esta app no declara -y que en Play exige justificar como
     * permiso sensible, con riesgo de rechazo-. La via SAF no tiene ese
     * problema y es la misma que ya se usa para las ROMs.
     */
    /**
     * Enviar el extra CONFIGFILE al lanzar RetroArch, apuntando a su propio
     * retroarch.cfg.
     *
     * Sin el, RetroArch arranca SIN CARGAR NINGUNA configuracion: avisa de
     * "Config directory is not set", no pinta el overlay y **los atajos de
     * teclado no funcionan** (el combo L3+R3 no abre el Quick Menu). Verificado
     * con una prueba A/B en una GameMT EX8 el 11 de septiembre de 2026.
     *
     * Se deja como interruptor porque la ruta no se puede verificar antes de
     * mandarla -Android 11+ prohibe mirar el Android/data de otra app-, y en un
     * RetroArch que guarde su configuracion en otro sitio esto le haria empezar
     * con una nueva. Es la misma ruta que usa ES-DE en estas consolas.
     */
    var sendRetroArchConfigFile: Boolean
        get() = prefs.getBoolean("send_retroarch_configfile", true)
        set(value) = prefs.edit().putBoolean("send_retroarch_configfile", value).apply()

    var retroarchFolderUri: String
        get() = prefs.getString("retroarch_folder_uri", "") ?: ""
        set(value) = prefs.edit().putString("retroarch_folder_uri", value).apply()

    var lastCloudSyncTimestamp: Long
        get() = prefs.getLong("last_cloud_sync_ts", 0L)
        set(value) = prefs.edit().putLong("last_cloud_sync_ts", value).apply()

    var romsLocation: String
        get() = prefs.getString("roms_location", "") ?: ""
        set(value) = prefs.edit().putString("roms_location", value).apply()

    var extraRomsLocations: Set<String>
        get() = prefs.getStringSet("extra_roms_locations", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("extra_roms_locations", value).apply()

    fun getAllRomsLocations(): List<String> {
        val base = romsLocation
        val extras = extraRomsLocations
        val result = mutableListOf<String>()
        if (base.isNotEmpty()) result.add(base)
        result.addAll(extras)
        return result.distinct()
    }

    var manualProfile: String
        get() = prefs.getString("current_manual_profile", "BALANCED") ?: "BALANCED"
        set(value) = prefs.edit().putString("current_manual_profile", value).apply()

    var manualHardwareId: String
        get() = prefs.getString("manual_hw_id", "generic") ?: "generic"
        set(value) = prefs.edit().putString("manual_hw_id", value).apply()

    var isFirstRun: Boolean
        get() = prefs.getBoolean("is_first_run", true)
        set(value) = prefs.edit().putBoolean("is_first_run", value).apply()
        
    var isReturningFromGame: Boolean
        get() = prefs.getBoolean("is_returning_from_game", false)
        set(value) = prefs.edit().putBoolean("is_returning_from_game", value).apply()

    /**
     * Dia (yyyy-MM-dd) en el que el usuario cerro la tarjeta del reto.
     *
     * Antes el cierre era un `remember` dentro del composable: la tarjeta se
     * quitaba, pero volvia sola en cuanto se entraba en una plataforma o
     * saltaba el modo atraccion, porque el composable salia de la composicion y
     * el estado se perdia. Ahora el cierre dura hasta el reto siguiente.
     */
    var dailyChallengeHiddenOn: String
        get() = prefs.getString("daily_challenge_hidden_on", "") ?: ""
        set(value) = prefs.edit().putString("daily_challenge_hidden_on", value).apply()
        
    // --- NUEVO: Control manual vs RetroArch ---
    var isCustomControllerMappingEnabled: Boolean
        get() = prefs.getBoolean("custom_controller_mapping", false)
        set(value) = prefs.edit().putBoolean("custom_controller_mapping", value).apply()
}
