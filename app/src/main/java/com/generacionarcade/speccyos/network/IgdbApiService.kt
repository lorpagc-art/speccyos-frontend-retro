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
