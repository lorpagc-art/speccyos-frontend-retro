/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object SystemInfoParser {
    private const val TAG = "SystemInfoParser"

    fun parseSystemInfo(context: Context, platformId: String): RetroArchDatabase.SystemInfo? {
        val assetPath = "contentimg/ROMs/$platformId/systeminfo.txt"
        return try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            
            var fullName = platformId
            var extensions = mutableListOf<String>()
            var defaultCore = ""
            var scrapingPlatform: String? = null
            val alternativeCores = mutableListOf<String>()

            var currentSection = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.endsWith(":")) {
                    currentSection = trimmed.lowercase()
                    continue
                }

                when (currentSection) {
                    "full system name:" -> fullName = trimmed
                    "supported file extensions:" -> {
                        extensions.addAll(trimmed.split(" ").filter { it.startsWith(".") }.map { it.lowercase() })
                    }
                    "launch command:" -> {
                        if (trimmed.contains("EXTRA_LIBRETRO")) {
                            defaultCore = extractCoreName(trimmed)
                        }
                    }
                    "alternative launch commands:", "alternative launch command:" -> {
                        if (trimmed.contains("EXTRA_LIBRETRO")) {
                            val newCore = extractCoreName(trimmed)
                            if (newCore.isNotEmpty()) {
                                alternativeCores.add(newCore)
                            }
                        }
                    }
                    "platform (for scraping):" -> {
                        scrapingPlatform = trimmed
                    }
                }
            }

            if (defaultCore.isEmpty()) return null

            RetroArchDatabase.SystemInfo(
                id = platformId,
                name = fullName,
                extensions = extensions.distinct(),
                defaultCore = defaultCore,
                alternativeCores = alternativeCores.distinct().filter { it != defaultCore },
                icon = "ic_generic_console",
                scrapingPlatform = scrapingPlatform
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCoreName(command: String): String {
        // En RetroArch 1.16 y versiones recientes/modificadas, el parámetro suele venir sin la extensión _libretro_android.so 
        // o con un formato de Intent diferente. Hacemos un filtrado agresivo.
        val key = if (command.contains("EXTRA_LIBRETRO%=")) "EXTRA_LIBRETRO%=" else "EXTRA_LIBRETRO="
        var raw = command.substringAfter(key, "")
        if (raw.isEmpty()) return ""
        
        raw = raw.substringBefore(" ").trim()
        
        // Limpiamos las extensiones si vienen y extraemos solo el nombre del core puro.
        if (raw.contains("_libretro_android.so")) raw = raw.substringBefore("_libretro_android.so")
        if (raw.contains("_libretro.so")) raw = raw.substringBefore("_libretro.so")
        
        // Quitamos la ruta completa (ej: /data/data/com.retroarch/cores/snes9x)
        raw = raw.substringAfterLast("/")
        
        return raw.trim()
    }
}
