/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos.network

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speccy Identity Engine: Resuelve nombres de ROMs crípticos localmente.
 * Soporta MAME, FBNeo y variantes de Arcade.
 */
object ArcadeIdentityMapper {

    private val TAG = "ArcadeIdentityMapper"
    private var arcadeDatabase: Map<String, String> = emptyMap()

    // Base de datos mínima integrada para arranque rápido
    private val coreArcadeMap = mapOf(
        "mslug" to "Metal Slug - Super Vehicle-001",
        "mslug2" to "Metal Slug 2 - Super Vehicle-001/II",
        "mslugx" to "Metal Slug X - Super Vehicle-001",
        "mslug3" to "Metal Slug 3",
        "mslug4" to "Metal Slug 4",
        "mslug5" to "Metal Slug 5",
        "kof94" to "The King of Fighters '94",
        "kof95" to "The King of Fighters '95",
        "kof96" to "The King of Fighters '96",
        "kof97" to "The King of Fighters '97",
        "kof98" to "The King of Fighters '98 - The Slugfest",
        "kof99" to "The King of Fighters '99 - Millennium Battle",
        "kof2000" to "The King of Fighters 2000",
        "kof2001" to "The King of Fighters 2001",
        "kof2002" to "The King of Fighters 2002 - Challenge to Ultimate Battle",
        "sf2" to "Street Fighter II: The World Warrior",
        "sf2ce" to "Street Fighter II': Champion Edition",
        "sf2hf" to "Street Fighter II': Hyper Fighting",
        "ssf2" to "Super Street Fighter II: The New Challengers",
        "ssf2t" to "Super Street Fighter II Turbo",
        "sfa" to "Street Fighter Alpha: Warriors' Dreams",
        "sfa2" to "Street Fighter Alpha 2",
        "sfa3" to "Street Fighter Alpha 3",
        "msh" to "Marvel Super Heroes",
        "mvsc" to "Marvel vs. Capcom: Clash of Super Heroes",
        "xmvsf" to "X-Men Vs. Street Fighter",
        "mshvsf" to "Marvel Super Heroes Vs. Street Fighter",
        "dino" to "Cadillacs and Dinosaurs",
        "punisher" to "The Punisher",
        "wof" to "Warriors of Fate",
        "captcomm" to "Captain Commando",
        "knights" to "Knights of the Round",
        "ffight" to "Final Fight",
        "sfiii" to "Street Fighter III: New Generation",
        "sfiii2" to "Street Fighter III 2nd Impact: Giant Attack",
        "sfiii3" to "Street Fighter III 3rd Strike: Fight for the Future",
        "garou" to "Garou: Mark of the Wolves",
        "lastblad" to "The Last Blade",
        "lastbld2" to "The Last Blade 2",
        "samsho" to "Samurai Shodown",
        "samsho2" to "Samurai Shodown II",
        "samsho3" to "Samurai Shodown III",
        "samsho4" to "Samurai Shodown IV: Amakusa's Revenge",
        "samsho5" to "Samurai Shodown V",
        "rbff1" to "Real Bout Fatal Fury",
        "rbffspec" to "Real Bout Fatal Fury Special",
        "rbff2" to "Real Bout Fatal Fury 2: The Newcomers",
        "fatfury1" to "Fatal Fury: King of Fighters",
        "fatfury2" to "Fatal Fury 2",
        "fatfury3" to "Fatal Fury 3: Road to the Final Victory",
        "fatfursp" to "Fatal Fury Special",
        "doubledr" to "Double Dragon",
        "ddragon2" to "Double Dragon II: The Revenge",
        "tmnt" to "Teenage Mutant Ninja Turtles",
        "tmnt2" to "Teenage Mutant Ninja Turtles: Turtles in Time",
        "simpsons" to "The Simpsons",
        "xmen" to "X-Men",
        "aliens" to "Aliens",
        "avp" to "Alien vs. Predator",
        "pnm" to "Prehistoric Isle in 1930",
        "pulstar" to "Pulstar",
        "blazstar" to "Blazing Star",
        "viewpoin" to "Viewpoint",
        "neogeo" to "Neo Geo MVS System BIOS"
    )

    /**
     * Carga una base de datos más extensa desde un archivo JSON en assets si existe.
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("metadata/arcade_full_index.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val adapter = moshi.adapter<Map<String, String>>(type)
            arcadeDatabase = adapter.fromJson(jsonString) ?: coreArcadeMap
            Log.d(TAG, "Base de datos de Arcade cargada: ${arcadeDatabase.size} entradas.")
        } catch (e: Exception) {
            Log.w(TAG, "No se encontró arcade_full_index.json, usando base de datos core.")
            arcadeDatabase = coreArcadeMap
        }
    }

    /**
     * Resuelve el nombre real de un archivo de ROM de arcade.
     */
    fun resolve(fileName: String): String? {
        val key = fileName.substringBeforeLast(".").lowercase()
        return arcadeDatabase[key]
    }
    
    /**
     * Verifica si una plataforma es de tipo Arcade.
     */
    fun isArcade(platformId: String): Boolean {
        val p = platformId.lowercase()
        return p == "mame" || p == "arcade" || p == "fba" || p == "fbneo" || p == "cps1" || p == "cps2" || p == "cps3" || p == "neogeo"
    }
}
