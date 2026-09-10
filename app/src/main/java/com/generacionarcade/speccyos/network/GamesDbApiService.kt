package com.generacionarcade.speccyos.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GamesDbApiService {

    @GET("Games/ByGameName")
    suspend fun searchGameByName(
        @Query("apikey") apiKey: String,
        @Query("name") name: String,
        @Query("fields") fields: String = "overview,release_date,developer,genres",
        @Query("filter[platform]") platformId: Int? = null
    ): Response<GameSearchResponse>

    @GET("Games/Images")
    suspend fun getGameImages(
        @Query("apikey") apiKey: String,
        @Query("games_id") gameId: Long
    ): Response<GameImagesResponse>
}
