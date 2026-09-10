package com.generacionarcade.speccyos

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class PlatformMetadata(
    var name: String = "",
    var manufacturer: String = "",
    var year: String = "",
    var description: String = ""
)

object MetadataReader {

    fun getMetadata(context: Context, platformId: String, targetLanguage: String = "es_ES"): PlatformMetadata {
        val filename = mapPlatformToFilename(platformId)
        
        return try {
            context.assets.open("contentimg/system-metadata/$filename.xml").use { inputStream ->
                parseThemeXml(inputStream, targetLanguage)
            }
        } catch (e: Exception) {
            PlatformMetadata(description = "Información clasificada.")
        }
    }

    private fun mapPlatformToFilename(id: String): String {
        return when (id.lowercase()) {
            "snes" -> "snes"
            "nes" -> "nes"
            "psx", "ps1" -> "psx"
            "ps2" -> "ps2"
            "genesis", "megadrive" -> "megadrive"
            "n64" -> "n64"
            "gb" -> "gb"
            "gba" -> "gba"
            "gbc" -> "gbc"
            "mame" -> "mame"
            "psp" -> "psp"
            "dreamcast" -> "dreamcast"
            "wii" -> "wii"
            "gc", "gamecube" -> "gc"
            "nds" -> "nds"
            "3ds", "n3ds" -> "n3ds"
            else -> id.lowercase()
        }
    }

    private fun parseThemeXml(inputStream: InputStream, targetLanguage: String): PlatformMetadata {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        val metadata = PlatformMetadata()
        
        var eventType = parser.eventType
        var currentTag = ""
        var inTargetLanguageBlock = false
        var inVariablesBlock = false
        
        val targetLangShort = targetLanguage.split("_")[0]

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "language") {
                        val langAttr = parser.getAttributeValue(null, "name")
                        inTargetLanguageBlock = (langAttr == targetLanguage || langAttr?.startsWith(targetLangShort) == true)
                    } else if (currentTag == "variables") {
                        inVariablesBlock = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank() && inVariablesBlock) {
                        val isGlobalVariables = !inTargetLanguageBlock && parser.depth <= 3 
                        if (isGlobalVariables || inTargetLanguageBlock) {
                            when (currentTag) {
                                "systemName", "fullname" -> metadata.name = text.trim()
                                "systemManufacturer", "manufacturer" -> metadata.manufacturer = text.trim()
                                "systemReleaseYear", "releaseyear", "year" -> metadata.year = text.trim()
                                "systemDescription", "story", "desc", "description" -> metadata.description = text.trim()
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "language") {
                        inTargetLanguageBlock = false
                    } else if (parser.name == "variables") {
                        inVariablesBlock = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return metadata
    }
}
