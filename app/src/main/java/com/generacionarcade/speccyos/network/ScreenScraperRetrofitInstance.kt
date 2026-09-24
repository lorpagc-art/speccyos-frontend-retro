/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos.network

import com.generacionarcade.speccyos.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * ScreenScraperRetrofitInstance
 *
 * SEGURIDAD: Las credenciales de desarrollador se leen desde BuildConfig,
 * que las inyecta desde local.properties en tiempo de compilación.
 * NUNCA se hardcodean en el código fuente.
 *
 * En local.properties (NO subir a git):
 *   screenscraper.dev_id=Speccy81
 *   screenscraper.dev_password=<tu contrasena de ScreenScraper>
 *   screenscraper.debug_password=<tu contrasena de desarrollo>
 *
 * En app/build.gradle.kts, dentro de defaultConfig:
 *   buildConfigField("String", "SS_DEV_ID",       "\"${localProperties["screenscraper.dev_id"] ?: ""}\"")
 *   buildConfigField("String", "SS_DEV_PASSWORD",  "\"${localProperties["screenscraper.dev_password"] ?: ""}\"")
 *   buildConfigField("String", "SS_DEBUG_PASSWORD","\"${localProperties["screenscraper.debug_password"] ?: ""}\"")
 */
object ScreenScraperRetrofitInstance {

    private const val BASE_URL = "https://www.screenscraper.fr/api2/"

    // Credenciales leídas desde BuildConfig — generadas en compilación, no en código
    val DEV_ID: String       get() = BuildConfig.SS_DEV_ID
    val DEV_PASSWORD: String get() = BuildConfig.SS_DEV_PASSWORD
    val DEBUG_PASSWORD: String get() = BuildConfig.SS_DEBUG_PASSWORD

    const val SOFTWARE_NAME = "Speccy OS"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Logging SOLO en debug — en release no se exponen credenciales en logcat
    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
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
