/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.palette.graphics.Palette
import java.util.concurrent.ConcurrentHashMap

object ThemeManager {

    // ─────────────────────────────────────────────────────────────────────────
    // CACHÉ DE RUTAS DE ARTE
    //
    // `getThemeImagePath()` se llama SIN `remember` desde ocho puntos de la UI
    // (GameListComponents, ThemeInmersive, ThemeUltra, ThemeDesktop), varios de
    // ellos dentro de bucles de items. Cada llamada creaba un SettingsManager
    // nuevo (otro getSharedPreferences, lectura de disco) y recorría hasta SIETE
    // candidatos abriendo assets en un try/catch, todo en el hilo principal.
    //
    // En un carrusel de plataformas eso son decenas de aperturas de asset por
    // frame: la causa principal del jank al navegar. Aquí se memoiza tanto la
    // existencia de cada asset como el resultado final.
    // ─────────────────────────────────────────────────────────────────────────
    private val assetExistsCache = ConcurrentHashMap<String, Boolean>()
    private val resolvedPathCache = ConcurrentHashMap<String, String>()

    @Volatile private var cachedArtworkStyle: String? = null

    /** Debe llamarse al cambiar el estilo de arte o los medios personalizados. */
    fun invalidateArtworkCache() {
        assetExistsCache.clear()
        resolvedPathCache.clear()
        cachedArtworkStyle = null
    }

    private fun artworkStyle(context: Context): String {
        cachedArtworkStyle?.let { return it }
        val style = SettingsManager(context).systemArtworkStyle
        cachedArtworkStyle = style
        return style
    }

    private fun assetExists(context: Context, path: String): Boolean =
        assetExistsCache.getOrPut(path) {
            try { context.assets.open(path).close(); true } catch (_: Exception) { false }
        }

    data class NeonProfile(
        val name: String,
        val main: Color,
        val accent: Color
    )

    val neonPresets = listOf(
        NeonProfile("CYBER", Color(0xFF00F2FF), Color(0xFFFF0055)),
        NeonProfile("ACID", Color(0xFF39FF14), Color(0xFFD500F9)),
        NeonProfile("MAGMA", Color(0xFFFF3D00), Color(0xFFFFD600)),
        NeonProfile("VAPOR", Color(0xFFE040FB), Color(0xFF00E5FF)),
        NeonProfile("GHOST", Color(0xFFBDBDBD), Color(0xFF18FFFF)),
        NeonProfile("CLASSIC", Color(0xFFFF0000), Color(0xFF2962FF))
    )

    val matrixHackerProfile = NeonProfile("MATRIX", Color(0xFF00FF00), Color(0xFF003300))

    var primaryColor by mutableStateOf(Color(0xFF00F2FF))
    var accentColor by mutableStateOf(Color(0xFFFF0055))

    val titleFont = FontFamily.Monospace

    var colorScheme by mutableStateOf(
        // Antes habia aqui un darkColorScheme con SOLO OCHO roles, duplicado
        // literalmente en updateColorScheme(). Todo componente Material3 que usara
        // surfaceVariant, outline, outlineVariant, error o primaryContainer caia en
        // el morado por defecto de Material, fuera de tema. speccyColorScheme()
        // rellena los dieciocho a partir del color de acento del usuario.
        speccyColorScheme(primaryColor, accentColor)
    )

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("speccy_settings", Context.MODE_PRIVATE)
        val pColor = prefs.getInt("theme_primary_color", 0xFF00F2FF.toInt())
        val aColor = prefs.getInt("theme_accent_color", 0xFFFF0055.toInt())

        primaryColor = Color(pColor)
        accentColor = Color(aColor)
        updateColorScheme()
    }

    fun saveNeonProfile(context: Context, profile: NeonProfile) {
        val prefs = context.getSharedPreferences("speccy_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("theme_primary_color", profile.main.toArgb())
            putInt("theme_accent_color", profile.accent.toArgb())
            apply()
        }
        primaryColor = profile.main
        accentColor = profile.accent
        updateColorScheme()
    }

    private fun updateColorScheme() {
        colorScheme = speccyColorScheme(primaryColor, accentColor)
    }

    /**
     * FASE 1: Ambilight Dinámico.
     * Extrae colores de la carátula del juego para ambientar la interfaz.
     */
    fun updateColorsFromBitmap(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            palette?.let {
                val dominant = it.getDominantColor(primaryColor.toArgb())
                val vibrant = it.getVibrantColor(accentColor.toArgb())
                
                primaryColor = Color(dominant)
                accentColor = Color(vibrant)
                updateColorScheme()
            }
        }
    }

    fun resetToSavedColors(context: Context) {
        val prefs = context.getSharedPreferences("speccy_settings", Context.MODE_PRIVATE)
        primaryColor = Color(prefs.getInt("theme_primary_color", 0xFF00F2FF.toInt()))
        accentColor = Color(prefs.getInt("theme_accent_color", 0xFFFF0055.toInt()))
        updateColorScheme()
    }

    fun adaptDNA(context: Context, systemId: String) {
        val settings = SettingsManager(context)
        if (!settings.isDynamicThemeEnabled) return

        val lowId = systemId.lowercase()
        when {
            lowId.contains("nintendo") || lowId == "nes" || lowId == "snes" || lowId == "n64" || lowId == "gc" || lowId == "wii" || lowId == "switch" -> {
                primaryColor = Color(0xFFFF0000)
                accentColor = Color(0xFFE60012)
            }
            lowId.contains("sega") || lowId == "megadrive" || lowId == "genesis" || lowId == "dc" || lowId == "saturn" || lowId == "sms" -> {
                primaryColor = Color(0xFF0088FF)
                accentColor = Color(0xFF0055CC)
            }
            lowId.contains("playstation") || lowId == "psx" || lowId == "ps2" || lowId == "psp" || lowId == "vita" -> {
                primaryColor = Color(0xFF00439C)
                accentColor = Color(0xFF00D1FF)
            }
            lowId.contains("xbox") -> {
                primaryColor = Color(0xFF107C10)
                accentColor = Color(0xFF000000)
            }
            lowId.contains("arcade") || lowId == "mame" || lowId == "fbneo" || lowId == "neogeo" -> {
                primaryColor = Color(0xFFFFD700)
                accentColor = Color(0xFFFF0000)
            }
            else -> resetToSavedColors(context)
        }
        updateColorScheme()
    }

    fun mapPlatformToSystemImage(id: String): String {
        val lowId = id.lowercase().trim().replace(" ", "").replace("_", "")
        return when {
            lowId.contains("atari2600") -> "atari_2600"
            lowId.contains("atari5200") -> "atari_5200"
            lowId.contains("atari7800") -> "atari_7800"
            lowId.contains("atari800") -> "atari_800"
            lowId.contains("jaguar") -> "atari_jaguar"
            lowId.contains("lynx") -> "atari_lynx"
            lowId.contains("atarist") -> "atari_st"
            lowId.contains("3do") -> "3do"
            lowId.contains("amiga") && lowId.contains("cd32") -> "commodore_amiga_cd32"
            lowId.contains("amiga") -> "amiga"
            lowId.contains("amstrad") || lowId.contains("cpc") -> "amstrad_cpc"
            lowId.contains("appleii") -> "apple_ii"
            lowId.contains("macintosh") -> "apple_macintosh"
            lowId.contains("atomiswave") -> "atomiswave"
            lowId.contains("astrocade") -> "bally_astrocade"
            lowId.contains("wonderswancolor") || lowId == "wsc" -> "bandai_wonderswan_color"
            lowId.contains("wonderswan") || lowId == "ws" -> "bandai_wonderswan"
            lowId.contains("cps1") -> "capcom_play_system_1__cps1_"
            lowId.contains("cps2") -> "capcom_play_system_2__cps2_"
            lowId.contains("cps3") -> "capcom_play_system_3__cps3_"
            lowId.contains("colecovision") || lowId == "coleco" -> "colecovision"
            lowId.contains("c128") -> "commodore_128"
            lowId.contains("c64") || lowId.contains("commodore") -> "commodore_64"
            lowId.contains("vic20") -> "commodore_vic-20"
            lowId.contains("plus4") -> "commodore_plus_4"
            lowId.contains("pet") -> "commodore_pet"
            lowId.contains("dos") -> "dos"
            lowId.contains("fairchild") -> "fairchild_channel_f"
            lowId.contains("fbneo") || lowId.contains("finalburn") -> "finalburn_neo"
            lowId.contains("fmtownsmart") -> "fujitsu_fm_towns_marty"
            lowId.contains("fmtowns") -> "fujitsu_fm_towns"
            lowId.contains("vectrex") -> "gce_vectrex"
            lowId.contains("intellivision") -> "mattel_intellivision"
            lowId.contains("xbox360") -> "microsoft_xbox_360"
            lowId.contains("xbox") -> "microsoft_xbox"
            lowId.contains("msx2") -> "microsoft_msx2"
            lowId.contains("msx") -> "microsoft_msx"
            lowId.contains("pc88") -> "nec_pc-8800"
            lowId.contains("pc98") -> "nec_pc-9800"
            lowId.contains("pcfx") -> "nec_pc-fx"
            lowId.contains("tgcd") || lowId.contains("pcecd") -> "nec_pc_engine_cd"
            lowId.contains("tg16") || lowId.contains("pcengine") -> "nec_pc_engine"
            lowId.contains("supergrafx") -> "nec_supergrafx"
            lowId.contains("3ds") || lowId == "n3ds" -> "nintendo_3ds"
            lowId.contains("n64") -> "nintendo_64"
            lowId.contains("snes") || lowId.contains("supernintendo") -> "nintendo_super_nintendo_entertainment_system"
            lowId.contains("nes") || lowId.contains("famicom") -> "nintendo_entertainment_system"
            lowId.contains("gba") || lowId.contains("advance") -> "nintendo_game_boy_advance"
            lowId.contains("gbc") || lowId.contains("color") -> "nintendo_game_boy_color"
            lowId.contains("gb") || lowId.contains("gameboy") -> "nintendo_game_boy"
            lowId.contains("gc") || lowId.contains("gamecube") -> "nintendo_gamecube"
            lowId.contains("nds") -> "nintendo_ds"
            lowId.contains("switch") -> "nintendo_switch"
            lowId.contains("virtualboy") || lowId == "vb" -> "nintendo_virtual_boy"
            lowId.contains("wiiu") -> "nintendo_wii_u"
            lowId.contains("wii") -> "nintendo_wii"
            lowId.contains("pokemonmini") || lowId == "pokemini" -> "nintendo_pokemon_mini"
            lowId.contains("pico8") -> "pico-8"
            lowId.contains("scummvm") -> "scummvm"
            lowId.contains("32x") -> "sega_32x"
            lowId.contains("segacd") || lowId.contains("scd") || lowId.contains("megacd") -> "sega_cd"
            lowId.contains("dreamcast") || lowId == "dc" -> "sega_dreamcast"
            lowId.contains("gamegear") || lowId == "gg" -> "sega_game_gear"
            lowId.contains("megadrive") || lowId.contains("genesis") || lowId == "md" -> "sega_genesis"
            lowId.contains("mastersystem") || lowId == "sms" || lowId == "master" -> "sega_master_system"
            lowId.contains("model2") -> "sega_model_2"
            lowId.contains("model3") -> "sega_model_3"
            lowId.contains("naomi") -> "sega_naomi"
            lowId.contains("saturn") -> "sega_saturn"
            lowId.contains("sg1000") -> "sega_sg-1000"
            lowId.contains("x68000") -> "sharp_x68000"
            lowId.contains("x1") -> "sharp_x1"
            lowId.contains("spectrum") || lowId == "zx" -> "sinclair_zx_spectrum"
            lowId.contains("zx81") -> "sinclair_zx81"
            lowId.contains("neogeocd") -> "snk_neo_geo_cd"
            lowId.contains("ngpc") -> "snk_neo_geo_pocket_color"
            lowId.contains("ngp") -> "snk_neo_geo_pocket"
            lowId.contains("neogeo") -> "snk_neo_geo"
            lowId.contains("ps2") -> "sony_playstation_2"
            lowId.contains("ps3") -> "sony_playstation_3"
            lowId.contains("psp") -> "sony_playstation_portable"
            lowId.contains("vita") -> "sony_playstation_vita"
            lowId.contains("psx") || lowId.contains("ps1") || lowId.contains("playstation") -> "sony_playstation"
            lowId.contains("ti99") -> "ti-99_4a"
            lowId.contains("thomson") || lowId == "to8" -> "thomson_mo_to"
            lowId.contains("vsmile") -> "vtech_v_smile"
            lowId.contains("creativision") -> "vtech_creativision"
            lowId.contains("supervision") -> "watara_supervision"
            lowId.contains("gamemaster") -> "hartung_game_master"
            lowId.contains("polygame") -> "igs_polygame_master"
            lowId.contains("megaduck") -> "mega_duck"
            lowId.contains("supercassette") || lowId == "scv" -> "epoch_super_cassette_vision"
            lowId.contains("odyssey2") -> "magnavox_odyssey_2"
            lowId.contains("arcadia") -> "emerson_arcadia_2001"
            lowId.contains("colorcomputer") || lowId == "coco" -> "tandy_color_computer"
            lowId.contains("pv1000") -> "casio_pv-1000"
            lowId.contains("tic80") -> "tic-80"
            lowId.contains("uzebox") -> "uzebox"
            lowId.contains("lutro") -> "lutro"
            lowId.contains("lowres") -> "lowres_nx"
            lowId.contains("cave") -> "cave"
            lowId.contains("daphne") -> "daphne"
            lowId.contains("mame") -> "mame"
            lowId.contains("arcade") -> "arcade"
            else -> "arcade"
        }
    }

    fun getThemeImagePath(
        baseDir: String,
        fileName: String,
        customMediaMap: Map<String, String>,
        context: Context? = null
    ): String {
        // La clave incluye el tamaño del mapa de medios personalizados para que la
        // caché se invalide sola cuando el usuario añade o quita arte propio.
        val cacheKey = "$baseDir|$fileName|${customMediaMap.size}|" +
            (context?.let { artworkStyle(it) } ?: "-")
        resolvedPathCache[cacheKey]?.let { return it }
        val resolved = resolveThemeImagePath(baseDir, fileName, customMediaMap, context)
        resolvedPathCache[cacheKey] = resolved
        return resolved
    }

    private fun resolveThemeImagePath(
        baseDir: String,
        fileName: String,
        customMediaMap: Map<String, String>,
        context: Context?
    ): String {
        val nameWithoutExt = fileName.substringBeforeLast(".").lowercase()
        
        val isFanartDir = baseDir == "Retrofix-16_9" || 
                          baseDir == "Retrofix-4_3" || 
                          baseDir == "Retrofix-9_16" || 
                          baseDir == "Retrofix-1_1" || 
                          baseDir == "Eclipse" || 
                          baseDir == "snap" ||
                          baseDir == "cyberpunk" ||
                          baseDir == "iconic_character" ||
                          baseDir == "studio"

        if (isFanartDir) {
            val mappedSystem = mapPlatformToSystemImage(nameWithoutExt)
            
            val targetFolder = if (context != null) {
                // Antes: `SettingsManager(context)` en CADA llamada, es decir un
                // getSharedPreferences (I/O de disco) por item de lista y por frame.
                val style = artworkStyle(context)
                when (style) {
                    SettingsManager.ARTWORK_STYLE_ICONIC -> "iconic_character"
                    SettingsManager.ARTWORK_STYLE_CYBERPUNK -> "cyberpunk"
                    SettingsManager.ARTWORK_STYLE_STUDIO -> "studio"
                    else -> style
                }
            } else {
                "cyberpunk"
            }
            
            val targetFolderLow = targetFolder.lowercase()
            val rawId = nameWithoutExt.substringBefore("_").lowercase()
            
            val customUri = customMediaMap["${targetFolderLow}_$rawId"] 
                ?: customMediaMap["${targetFolderLow}_$mappedSystem"]
                ?: customMediaMap["${targetFolderLow}_${nameWithoutExt}"]
                ?: customMediaMap["${targetFolderLow}_${mappedSystem}_16x9_fanart"]

            if (customUri != null) return customUri

            val finalFolder = if (targetFolderLow == "iconic_character" || targetFolderLow == "cyberpunk" || targetFolderLow == "studio") targetFolderLow else "cyberpunk"

            if (context != null) {
                val possibleFiles = listOf(
                    "${mappedSystem}_16x9_fanart.webp",
                    "${nameWithoutExt}_16x9_fanart.webp",
                    "${nameWithoutExt}.webp",
                    "${nameWithoutExt}.jpg",
                    "${nameWithoutExt}.png",
                    "${nameWithoutExt}_16x9_fanart.jpg",
                    "${nameWithoutExt}_16x9_fanart.png"
                )
                for (pf in possibleFiles) {
                    val assetPath = "contentimg/$finalFolder/$pf"
                    if (assetExists(context, assetPath)) {
                        return "file:///android_asset/$assetPath"
                    }
                }
            }
            
            val cyberFileName = "${mappedSystem}_16x9_fanart.webp"
            return "file:///android_asset/contentimg/$finalFolder/$cyberFileName"
        }

        val folderKey = baseDir.lowercase()
        val key = "${folderKey}_$nameWithoutExt"
        
        val customUri = customMediaMap[key]
        if (customUri != null) return customUri

        return "file:///android_asset/contentimg/$baseDir/$fileName"
    }

    fun mapPlatformToTransparentIcon(id: String): String {
        val lowId = id.lowercase().trim()
        return when {
            lowId == "fbneo" || lowId == "fba" -> "fbn"
            lowId == "mastersystem" || lowId == "sms" -> "master"
            lowId.contains("jaguar") -> "jaguar"
            lowId.contains("cpc") || lowId.contains("amstrad") -> "cpc"
            lowId.contains("colecovision") || lowId == "coleco" -> "coleco"
            lowId == "gameboy" || lowId == "gb" -> "gb"
            lowId == "gba" || lowId.contains("advance") -> "gba"
            lowId == "gbc" || lowId.contains("color") -> "gbc"
            lowId == "gamecube" || lowId == "gc" -> "gc"
            lowId.contains("watch") || lowId == "gw" -> "gw"
            lowId == "turbografx16" || lowId == "tg16" || lowId == "pcengine" -> "tg16"
            lowId == "turbografxcd" || lowId == "tgcd" || lowId == "pcenginecd" -> "tgcd"
            lowId.contains("32x") -> "32x"
            lowId.contains("segacd") || lowId.contains("megacd") -> "segacd"
            lowId.contains("sfc") || lowId.contains("superfamicom") -> "sfc"
            lowId == "wonderswan" || lowId == "ws" -> "ws"
            lowId == "wonderswancolor" || lowId == "wsc" -> "wsc"
            lowId == "ngp" || (lowId.contains("pocket") && !lowId.contains("color")) -> "ngp"
            lowId == "ngpc" || (lowId.contains("pocket") && lowId.contains("color")) -> "ngpc"
            lowId.contains("c64") || lowId.contains("commodore") -> "c64"
            lowId.contains("spectrum") || lowId == "zxspectrum" -> "zxspectrum"
            lowId.contains("2600") -> "atari2600"
            lowId.contains("5200") -> "atari5200"
            lowId.contains("7800") -> "atari7800"
            lowId.contains("playstation") || lowId == "psx" || lowId == "ps1" -> "psx"
            lowId == "ps2" -> "ps2"
            lowId == "psp" -> "psp"
            lowId == "nds" -> "nds"
            lowId == "n64" -> "n64"
            lowId == "wii" -> "wii"
            lowId == "3ds" || lowId == "n3ds" -> "3ds"
            lowId == "switch" -> "switch"
            lowId.contains("android") || lowId == "sys_apps" -> "android"
            lowId.contains("dreamcast") || lowId == "dc" -> "dreamcast"
            lowId.contains("megadrive") -> "megadrive"
            lowId.contains("genesis") -> "genesis"
            lowId.contains("saturn") -> "saturn"
            lowId.contains("neogeo") && !lowId.contains("cd") -> "neogeo"
            lowId.contains("neogeocd") -> "neogeocd"
            lowId.contains("mame") -> "mame"
            lowId.contains("arcade") -> "arcade"
            lowId.contains("famicom") -> "famicom"
            lowId.contains("nes") -> "nes"
            lowId.contains("snes") -> "snes"
            lowId.contains("atari") && lowId.contains("st") -> "atarist"
            lowId.contains("amiga") -> "amiga"
            lowId.contains("pico8") -> "pico8"
            lowId.contains("scummvm") -> "scummvm"
            lowId.contains("dos") -> "dos"
            lowId.contains("doom") -> "doom"
            lowId == "supervision" || lowId == "superv" -> "supervision"
            lowId == "gamegear" || lowId == "gg" -> "gamegear"
            lowId == "lynx" -> "lynx"
            lowId == "virtualboy" || lowId == "vb" -> "vb"
            lowId == "3do" -> "3do"
            lowId == "msx" -> "msx"
            lowId == "msx2" -> "msx2"
            lowId == "pcfx" -> "pcfx"
            lowId == "ti99" -> "ti99"
            lowId == "zx81" -> "zx81"
            lowId == "x68000" -> "x68000"
            lowId == "pc88" -> "pc88"
            lowId == "pc98" -> "pc98"
            lowId == "naomi" -> "naomi"
            lowId == "atomiswave" -> "atomiswave"
            lowId == "model2" -> "model2"
            lowId == "model3" -> "model3"
            lowId == "favoritos" -> "sgb"
            lowId == "recientes" -> "vmu"
            else -> lowId.replace("atari ", "").replace(" ", "")
        }
    }
}