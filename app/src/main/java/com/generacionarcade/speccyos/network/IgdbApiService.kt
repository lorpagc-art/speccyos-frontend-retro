/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos.network

import retrofit2.Response
import retrofit2.http.*

interface IgdbApiService {

    @POST("https://id.twitch.tv/oauth2/token")
    suspend fun getAccessToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): Response<IgdbTokenResponse>

    @POST("games")
    @Headers("Accept: application/json")
    suspend fun searchGames(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") authorization: String,
        @Body body: String
    ): Response<List<IgdbGame>>
}
