/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RetroArchDatabase {

    private const val TAG = "RetroArchDatabase"

    data class SystemInfo(
        val id: String,
        val name: String,
        val extensions: List<String>,
        val defaultCore: String,
        val alternativeCores: List<String> = emptyList(),
        val icon: String = "ic_generic_console",
        val scrapingPlatform: String? = null
    )

    private val ARCHIVE_EXTS = listOf(".zip", ".7z", ".rar")

    // Se escriben desde Dispatchers.IO y se leen desde el hilo principal. Como en
    // ambos casos se asigna una lista ya construida e inmutable, @Volatile basta
    // para garantizar la publicacion segura entre hilos.
    @Volatile private var hardcodedSystems: List<SystemInfo> = emptyList()
    @Volatile private var dynamicSystems: List<SystemInfo> = emptyList()

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Job de la fase lenta, para que quien la necesite completa pueda esperarla.
     *
     * consoles.json solo trae 27 sistemas; los otros ~146 salen de los 173
     * systeminfo.txt que carga la fase lenta en segundo plano. Sin esto,
     * escanear justo despues de abrir la app hacia que findSystemById devolviera
     * null para esas plataformas y sus ROMs se descartaran EN SILENCIO: el
     * usuario veia media biblioteca y ningun error.
     */
    @Volatile private var slowLoadJob: kotlinx.coroutines.Job? = null

    /** Espera a que la base este completa. Llamar solo desde fuera del hilo principal. */
    suspend fun awaitFullyLoaded() {
        slowLoadJob?.join()
    }

    /**
     * Fase rapida: un unico fichero JSON. Es barata y deja la base utilizable de
     * inmediato, asi que puede llamarse desde el hilo principal.
     *
     * La fase lenta (173 ficheros systeminfo.txt, uno por carpeta de assets) se
     * lanza en segundo plano: antes se hacia aqui mismo y bloqueaba onCreate hasta
     * terminar, con la pantalla en blanco. Ningun consumidor la necesita durante el
     * arranque; findSystemById se usa al lanzar un juego, al escanear y al abrir
     * ajustes, todos posteriores.
     */
    fun initialize(context: Context) {
        val app = context.applicationContext
        loadConsolesJson(app)
        slowLoadJob = loaderScope.launch { loadSystemInfoAssets(app) }
    }

    private fun loadConsolesJson(context: Context) {
        try {
            // Cargar consoles.json
            val jsonString = context.assets.open("consoles.json").bufferedReader().use { it.readText() }
            val jsonObject = org.json.JSONObject(jsonString)
            val jsonArray = jsonObject.optJSONArray("systems")
            
            val jsonLoadedSystems = mutableListOf<SystemInfo>()
            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val sysObj = jsonArray.getJSONObject(i)
                    val id = sysObj.getString("id")
                    val name = sysObj.getString("name")
                    
                    val extArray = sysObj.optJSONArray("extensions")
                    val extensions = mutableListOf<String>()
                    if (extArray != null) {
                        for (j in 0 until extArray.length()) {
                            extensions.add(extArray.getString(j))
                        }
                    }
                    
                    val defaultCore = sysObj.optString("defaultCore", "")
                    
                    val altCoresArray = sysObj.optJSONArray("alternativeCores")
                    val altCores = mutableListOf<String>()
                    if (altCoresArray != null) {
                        for (j in 0 until altCoresArray.length()) {
                            altCores.add(altCoresArray.getString(j))
                        }
                    }
                    
                    val scrapingPlatform = sysObj.optString("scrapingPlatform", null)
                    
                    var sysInfo = SystemInfo(id, name, extensions, defaultCore, altCores, scrapingPlatform = scrapingPlatform)
                    
                    // Añadir ARCHIVE_EXTS
                    val extendedExtensions = if (sysInfo.id !in listOf("ps2", "wii", "gc", "psx", "psp")) {
                        (sysInfo.extensions + ARCHIVE_EXTS).distinct()
                    } else {
                        sysInfo.extensions
                    }
                    sysInfo = sysInfo.copy(extensions = extendedExtensions)
                    
                    jsonLoadedSystems.add(sysInfo)
                }
            }
            hardcodedSystems = jsonLoadedSystems
            systemsCache = null
            Log.d(TAG, "consoles.json cargado: ${jsonLoadedSystems.size} sistemas")
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando consoles.json: ${e.message}")
        }
    }

    /** Fase lenta. Enriquece la lista; nunca debe correr en el hilo principal. */
    private fun loadSystemInfoAssets(context: Context) {
        try {
            val allPlatformIds = context.assets.list("contentimg/ROMs") ?: emptyArray()
            val loaded = mutableListOf<SystemInfo>()

            for (id in allPlatformIds) {
                SystemInfoParser.parseSystemInfo(context, id)?.let { loaded.add(it) }
            }
            dynamicSystems = loaded
            systemsCache = null
            Log.d(TAG, "Base de datos completa: ${systems.size} sistemas activos")
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando systeminfo.txt de assets: ${e.message}")
        }
    }

    // Cache del combinado. Antes cada acceso reconstruia una lista de ~200
    // elementos y la deduplicaba, y esto se lee desde composables que recomponen.
    // Se invalida asignando null desde las dos fases de carga.
    @Volatile private var systemsCache: List<SystemInfo>? = null

    val systems: List<SystemInfo>
        get() = systemsCache ?: (hardcodedSystems + dynamicSystems)
            .distinctBy { it.id }
            .also { systemsCache = it }

    // Mapa de aliases para plataformas con nombres alternativos frecuentes
    private val ALIASES = mapOf(
        "sfc" to "snes", "snesna" to "snes", "superfamicom" to "snes",
        "famicom" to "nes", "fds" to "nes",
        "megadrive" to "genesis", "megadrivejp" to "genesis",
        "ps1" to "psx", "playstation" to "psx",
        "ps2" to "ps2", "playstation2" to "ps2",
        "gba" to "gba", "gameboyadvance" to "gba",
        "gb" to "gb", "gameboy" to "gb",
        "gbc" to "gbc", "gameboycolor" to "gbc",
        "gc" to "gc", "gamecube" to "gc",
        "dc" to "dreamcast",
        "n3ds" to "3ds", "nintendo3ds" to "3ds",
        "pcengine" to "pce", "turbografx" to "pce", "tg16" to "pce",
        "zxspectrum" to "spectrum", "zx" to "spectrum",
        "psvita" to "vita",
        "fbneo" to "arcade", "mame2003" to "arcade",
        "segacd" to "megacd",
        "sega32x" to "32x"
    )

    fun findSystemById(id: String): SystemInfo? {
        val cleanId = id.lowercase().trim()
        // 1. Alias exacto
        val aliased = ALIASES[cleanId]
        if (aliased != null) return systems.find { it.id == aliased }
        // 2. Match exacto
        systems.find { it.id == cleanId }?.let { return it }
        // 3. Match sin guiones/espacios
        val stripped = cleanId.replace("_", "").replace("-", "").replace(" ", "")
        return systems.find { sys ->
            val sysStripped = sys.id.replace("_", "").replace("-", "")
            sysStripped == stripped || stripped.startsWith(sysStripped) || sysStripped.startsWith(stripped)
        }
    }
}
