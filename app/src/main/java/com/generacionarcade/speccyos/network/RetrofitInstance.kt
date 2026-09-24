/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstance {

    // TheGamesDB API Base URL
    private const val BASE_URL = "https://api.thegamesdb.net/v1/"

    // API Key proporcionada por el usuario
    const val API_KEY = "fb80bb030d65f54d4a0e23488757950ae518ed8dc614e7a569b18353e6f833b3"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val api: GamesDbApiService by lazy {
        retrofit.create(GamesDbApiService::class.java)
    }
}
