/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object SystemMetadataManager {
    data class SystemMetadata(
        val name: String = "",
        val description: String = "",
        val manufacturer: String = "",
        val releaseYear: String = ""
    )

    private val metadataCache = ConcurrentHashMap<String, SystemMetadata>()

    fun getMetadataForSystem(context: Context, systemId: String, lang: String): SystemMetadata {
        val cacheKey = "${systemId}_$lang"
        metadataCache[cacheKey]?.let { return it }

        val assetPath = "contentimg/system-metadata/${systemId.lowercase()}.xml"
        val metadata = try {
            // Antes: `assets.open(assetPath)` SIN `use{}` — el InputStream no se
            // cerraba nunca, así que se filtraba un descriptor de fichero por cada
            // plataforma en el primer render del dashboard.
            context.assets.open(assetPath).use { inputStream ->
                parseXml(inputStream, lang)
            }
        } catch (e: Exception) {
            SystemMetadata(name = systemId.uppercase())
        }
        
        metadataCache[cacheKey] = metadata
        return metadata
    }

    private fun parseXml(inputStream: InputStream, targetLang: String): SystemMetadata {
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)
        
        var name = ""
        var description = ""
        var manufacturer = ""
        var releaseYear = ""
        
        var currentLang: String? = null
        var inVariables = false
        
        var defaultName = ""
        var defaultDesc = ""
        var defaultMan = ""
        var defaultYear = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "language") {
                        currentLang = parser.getAttributeValue(null, "name")
                    } else if (tagName == "variables") {
                        inVariables = true
                    } else if (inVariables) {
                        try {
                            val text = parser.nextText()
                            if (currentLang == null) {
                                when (tagName) {
                                    "systemName" -> defaultName = text
                                    "systemDescription" -> defaultDesc = text
                                    "systemManufacturer" -> defaultMan = text
                                    "systemReleaseYear" -> defaultYear = text
                                }
                            } else if (currentLang.startsWith(targetLang)) {
                                when (tagName) {
                                    "systemName" -> name = text
                                    "systemDescription" -> description = text
                                    "systemManufacturer" -> manufacturer = text
                                    "systemReleaseYear" -> releaseYear = text
                                }
                            }
                        } catch (e: Exception) { /* Skip errors in specific tags */ }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "language") currentLang = null
                    else if (tagName == "variables") inVariables = false
                }
            }
            eventType = parser.next()
        }
        
        return SystemMetadata(
            name = name.ifEmpty { defaultName },
            description = description.ifEmpty { defaultDesc },
            manufacturer = manufacturer.ifEmpty { defaultMan },
            releaseYear = releaseYear.ifEmpty { defaultYear }
        )
    }
}
