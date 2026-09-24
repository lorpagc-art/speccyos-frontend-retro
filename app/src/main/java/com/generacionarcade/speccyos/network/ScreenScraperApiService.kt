/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ScreenScraperApiService {

    @GET("jeuInfos.php")
    suspend fun getGameInfo(
        @Query("devid") devId: String? = null,
        @Query("devpassword") devPassword: String? = null,
        @Query("softname") softname: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("romnom") romnom: String? = null,
        @Query("nom") nom: String? = null,
        @Query("md5") md5: String? = null,
        @Query("crc") crc: String? = null,
        @Query("systemeid") systemeid: Int? = null,
        @Query("romtaille") romtaille: Long? = null,
        @Query("output") output: String = "json",
        
        // --- PARÁMETROS DE DESARROLLADOR (DEBUG MODE) ---
        @Query("devdebugpassword") devDebugPassword: String? = null,
        @Query("forceupdate") forceUpdate: Int? = null,
        @Query("forcelevel") forceLevel: Int? = null
    ): Response<ScreenScraperResponse>

    @GET("jeuRecherche.php")
    suspend fun searchGame(
        @Query("devid") devId: String? = null,
        @Query("devpassword") devPassword: String? = null,
        @Query("softname") softname: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("recherche") recherche: String,
        @Query("systemeid") systemeid: Int? = null,
        @Query("output") output: String = "json"
    ): Response<ScreenScraperSearchResponse>

    @GET("ssuserInfos.php")
    suspend fun getApiUserInfo(
        @Query("devid") devId: String? = null,
        @Query("devpassword") devPassword: String? = null,
        @Query("softname") softname: String,
        @Query("ssid") ssid: String? = null,
        @Query("sspassword") sspassword: String? = null,
        @Query("output") output: String = "json"
    ): Response<ScreenScraperUserResponse>
}

// Para la búsqueda de texto
data class ScreenScraperSearchResponse(
    val response: SearchResponseData?
)
data class SearchResponseData(
    val jeux: List<GameData>?
)

data class ScreenScraperUserResponse(
    val response: ScreenScraperUserInfo?
)

data class ScreenScraperUserInfo(
    val ssuser: SsUserData?
)

data class SsUserData(
    val id: String?,
    val niveau: String?,
    val contribution: String?,
    val favs: String?
)
