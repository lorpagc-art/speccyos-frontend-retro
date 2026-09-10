package com.example.speccyose5ultrav021b.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object ScreenScraperRetrofitInstance {

    private const val BASE_URL = "https://www.screenscraper.fr/api2/"

    // CREDENCIALES DE DESARROLLADOR OFICIALES (BASADAS EN TU INFO)
    // Dejamos esto como base, pero el Service gestionará el fallback si falla.
    const val DEV_ID = "Speccy81" 
    const val DEV_PASSWORD = "speccy_password" 
    const val SOFTWARE_NAME = "SpeccyOS_E5_Ultra"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val api: ScreenScraperApiService by lazy {
        retrofit.create(ScreenScraperApiService::class.java)
    }
}
