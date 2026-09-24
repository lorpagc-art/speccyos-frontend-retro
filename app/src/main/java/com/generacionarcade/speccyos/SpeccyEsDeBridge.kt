/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringWriter

/**
 * SpeccyEsDeBridge
 * ----------------------------------------------------------------------------
 * Interoperabilidad con ES-DE (EmulationStation Desktop Edition), Pegasus,
 * Daijisho y Skraper.
 *
 * POR QUÉ IMPORTA
 * ---------------
 * `gamelist.xml` es el formato de facto del mundo retro: quien ya tiene una
 * biblioteca scrapeada la tiene ahí. Hasta ahora SpeccyOS tenía CERO soporte
 * (ni una coincidencia de "gamelist" en todo el árbol), así que un usuario que
 * llegaba desde ES-DE tenía que volver a scrapear 20.000 juegos desde cero — y
 * el scraper tarda horas y arriesga un baneo de ScreenScraper.
 *
 * Poder importar una biblioteca entera en un clic es el argumento de venta más
 * fuerte frente a la competencia; poder exportarla evita el efecto "cárcel de
 * datos" que la gente del retro detesta.
 *
 * ADEMÁS ARREGLA LA CACHÉ DE MEDIOS
 * ---------------------------------
 * `ScraperWorker.buildLocalMediaCache` indexaba por NOMBRE DE FICHERO sin la
 * plataforma, así que `Aladdin.png` de SNES y el de Mega Drive colisionaban y
 * uno sobrescribía al otro. Aquí las claves siempre llevan el sistema delante.
 */
object SpeccyEsDeBridge {

    private const val TAG = "SpeccyEsDe"

    /**
     * Subcarpetas REALES de ES-DE. Las que usaba el proyecto (`box2d`, `box3d`,
     * `mixrb`) no existen en ES-DE, así que su media nunca se encontraba.
     */
    private val MEDIA_DIRS = mapOf(
        "covers" to "boxArt",
        "miximages" to "boxArt",
        "screenshots" to "screenshot",
        "titlescreens" to "screenshot",
        "marquees" to "wheel",
        "videos" to "videoPreview",
        "fanart" to "fanart",
        "physicalmedia" to "cdArt",
        "backcovers" to "fanart"
    )

    data class EsDeGame(
        val path: String,
        val name: String?,
        val desc: String?,
        val image: String?,
        val video: String?,
        val marquee: String?,
        val thumbnail: String?,
        val rating: Float?,
        val releaseDate: String?,
        val developer: String?,
        val publisher: String?,
        val genre: String?,
        val players: String?,
        val favorite: Boolean,
        val playCount: Int,
        val lastPlayed: String?
    )

    data class ImportResult(
        val systemsFound: Int,
        val gamesMatched: Int,
        val mediaLinked: Int,
        val message: String
    )

    // ─────────────────────────────────────────────────────────────────────────
    // IMPORTACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recorre el árbol de ROMs buscando `gamelist.xml` por sistema y cruza sus
     * entradas con la biblioteca ya escaneada, rellenando lo que falte.
     * No sobrescribe datos que SpeccyOS ya tenga: sólo completa huecos.
     */
    suspend fun importFrom(
        context: Context,
        romsTreeUri: Uri,
        dao: GameDao,
        onProgress: suspend (String) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, romsTreeUri)
            ?: return@withContext ImportResult(0, 0, 0, "No se ha podido abrir la carpeta de ROMs")

        var systems = 0
        var matched = 0
        var media = 0

        // ES-DE guarda los gamelist en dos sitios según la versión:
        //   <ROMs>/<sistema>/gamelist.xml   (clásico)
        //   ES-DE/gamelists/<sistema>/gamelist.xml   (moderno)
        val candidateDirs = mutableListOf<Pair<String, DocumentFile>>()
        root.listFiles().filter { it.isDirectory }.forEach { dir ->
            val name = dir.name ?: return@forEach
            dir.findFile("gamelist.xml")?.let { candidateDirs += name to it }
        }
        root.findFile("ES-DE")?.findFile("gamelists")?.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { dir ->
                val name = dir.name ?: return@forEach
                dir.findFile("gamelist.xml")?.let { candidateDirs += name to it }
            }

        val mediaIndex = indexEsDeMedia(context, root)

        for ((systemName, gamelistDoc) in candidateDirs) {
            systems++
            onProgress("Importando $systemName…")
            val entries = runCatching { parseGamelist(context, gamelistDoc.uri) }.getOrDefault(emptyList())
            if (entries.isEmpty()) continue

            // Indexamos por nombre de fichero, que es lo estable entre frontends.
            val byFileName = entries.associateBy { it.path.substringAfterLast('/').lowercase() }

            // `systemName` es el nombre de carpeta de ES-DE (megadrive, pcengine,
            // snesna, dc, ps1...), mientras que Game.platformId usa la nomenclatura
            // de SpeccyOS (genesis, pce, snes, dreamcast, psx). Sin traducir, la
            // importacion cruzaba CERO juegos en todos esos sistemas.
            val platformId = runCatching { RetroArchDatabase.findSystemById(systemName)?.id }
                .getOrNull() ?: systemName.lowercase()
            val existing = runCatching { dao.getGamesByPlatformList(platformId) }
                .getOrDefault(emptyList())
            val updates = mutableListOf<Game>()

            for (game in existing) {
                val entry = byFileName[game.fileName.lowercase()] ?: continue
                val sysKey = systemName.lowercase()
                val stem = game.fileName.substringBeforeLast('.').lowercase()

                val newBox = game.boxArt ?: mediaIndex["$sysKey|covers|$stem"]
                    ?: mediaIndex["$sysKey|miximages|$stem"]
                val newVideo = game.videoPreview ?: mediaIndex["$sysKey|videos|$stem"]
                val newWheel = game.wheel ?: mediaIndex["$sysKey|marquees|$stem"]
                val newShot = game.screenshot ?: mediaIndex["$sysKey|screenshots|$stem"]

                val merged = game.copy(
                    title       = game.title.ifBlank { entry.name ?: game.title },
                    description = game.description ?: entry.desc,
                    developer   = if (game.developer.isNullOrBlank() || game.developer == "Desconocido")
                                      entry.developer ?: game.developer else game.developer,
                    genre       = if (game.genre.isNullOrBlank() || game.genre == "Retro")
                                      entry.genre ?: game.genre else game.genre,
                    releaseDate = if (game.releaseDate.isNullOrBlank() || game.releaseDate == "N/A")
                                      normalizeDate(entry.releaseDate) ?: game.releaseDate else game.releaseDate,
                    rating      = if (game.rating == 0f) (entry.rating ?: 0f) * 10f else game.rating,
                    isFavorite  = game.isFavorite || entry.favorite,
                    playCount   = maxOf(game.playCount, entry.playCount),
                    boxArt      = newBox,
                    videoPreview = newVideo,
                    wheel       = newWheel,
                    screenshot  = newShot
                )
                if (merged != game) {
                    updates += merged
                    matched++
                    if (newBox != game.boxArt || newVideo != game.videoPreview) media++
                }
            }
            // Escritura en lote: una transacción por sistema, no una por juego.
            updates.chunked(500).forEach { runCatching { dao.updateGames(it) } }
        }

        val msg = if (systems == 0) "No se ha encontrado ningún gamelist.xml"
                  else "Importados $matched juegos de $systems sistemas ($media con medios)"
        Log.i(TAG, msg)
        ImportResult(systems, matched, media, msg)
    }

    /** Índice `sistema|carpeta|nombre` -> uri, para no colisionar entre sistemas. */
    private fun indexEsDeMedia(context: Context, root: DocumentFile): Map<String, String> {
        val index = mutableMapOf<String, String>()
        val mediaRoot = root.findFile("ES-DE")?.findFile("downloaded_media")
            ?: root.findFile("downloaded_media")
            ?: return index
        mediaRoot.listFiles().filter { it.isDirectory }.forEach { sysDir ->
            val sys = sysDir.name?.lowercase() ?: return@forEach
            sysDir.listFiles().filter { it.isDirectory }.forEach { typeDir ->
                val type = typeDir.name?.lowercase() ?: return@forEach
                if (type !in MEDIA_DIRS) return@forEach
                typeDir.listFiles().forEach { f ->
                    val stem = f.name?.substringBeforeLast('.')?.lowercase() ?: return@forEach
                    index["$sys|$type|$stem"] = f.uri.toString()
                }
            }
        }
        Log.i(TAG, "Media de ES-DE indexada: ${index.size} ficheros")
        return index
    }

    private fun parseGamelist(context: Context, uri: Uri): List<EsDeGame> {
        val out = mutableListOf<EsDeGame>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            var cur: MutableMap<String, String>? = null
            var tag: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "game") cur = mutableMapOf()
                    }
                    XmlPullParser.TEXT -> {
                        val t = parser.text?.trim()
                        val currentTag = tag
                        if (cur != null && !currentTag.isNullOrBlank() && !t.isNullOrBlank()) {
                            cur[currentTag] = t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "game" && cur != null) {
                            out += EsDeGame(
                                path        = cur["path"].orEmpty(),
                                name        = cur["name"],
                                desc        = cur["desc"],
                                image       = cur["image"],
                                video       = cur["video"],
                                marquee     = cur["marquee"],
                                thumbnail   = cur["thumbnail"],
                                rating      = cur["rating"]?.toFloatOrNull(),
                                releaseDate = cur["releasedate"],
                                developer   = cur["developer"],
                                publisher   = cur["publisher"],
                                genre       = cur["genre"],
                                players     = cur["players"],
                                favorite    = cur["favorite"]?.equals("true", true) == true,
                                playCount   = cur["playcount"]?.toIntOrNull() ?: 0,
                                lastPlayed  = cur["lastplayed"]
                            )
                            cur = null
                        }
                        tag = null
                    }
                }
                event = parser.next()
            }
        }
        return out
    }

    /** ES-DE usa `YYYYMMDDT000000`; nosotros guardamos `YYYY-MM-DD`. */
    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.takeWhile { it.isDigit() }
        return when {
            digits.length >= 8 -> "${digits.substring(0,4)}-${digits.substring(4,6)}-${digits.substring(6,8)}"
            digits.length == 4 -> digits
            else -> raw
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXPORTACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escribe un `gamelist.xml` por sistema dentro de la carpeta de ROMs, en el
     * formato que ES-DE, Pegasus y Skraper entienden. El usuario puede llevarse
     * su biblioteca a otro frontend cuando quiera.
     */
    suspend fun exportTo(
        context: Context,
        romsTreeUri: Uri,
        dao: GameDao,
        onProgress: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, romsTreeUri)
            ?: return@withContext "No se ha podido abrir la carpeta de ROMs"

        val counts = runCatching { dao.getPlatformCounts() }.getOrDefault(emptyList())
        var written = 0
        for (pc in counts) {
            onProgress("Exportando ${pc.platformId}…")
            val games = runCatching { dao.getGamesByPlatformList(pc.platformId) }.getOrDefault(emptyList())
            if (games.isEmpty()) continue

            val xml = buildGamelistXml(games)
            val dir = root.findFile(pc.platformId) ?: root.createDirectory(pc.platformId) ?: continue
            val doc = dir.findFile("gamelist.xml") ?: dir.createFile("text/xml", "gamelist.xml") ?: continue
            runCatching {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(xml.toByteArray()) }
                written++
            }
        }
        "Exportados $written sistemas a gamelist.xml"
    }

    private fun buildGamelistXml(games: List<Game>): String {
        val w = StringWriter()
        val s = Xml.newSerializer()
        s.setOutput(w)
        s.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        s.startDocument("UTF-8", true)
        s.startTag(null, "gameList")
        for (g in games) {
            s.startTag(null, "game")
            fun tag(name: String, value: String?) {
                if (value.isNullOrBlank()) return
                s.startTag(null, name); s.text(value); s.endTag(null, name)
            }
            tag("path", "./${g.fileName}")
            tag("name", g.title)
            tag("desc", g.description)
            tag("image", g.boxArt)
            tag("video", g.videoPreview)
            tag("marquee", g.wheel)
            tag("thumbnail", g.screenshot)
            tag("rating", if (g.rating > 0f) (g.rating / 10f).toString() else null)
            // Game.releaseDate vale "N/A" por defecto: sin filtrar, se exportaba
            // <releasedate>N/A00000T000000</releasedate>, que ES-DE y Skraper rechazan.
            tag("releasedate", g.releaseDate?.filter { it.isDigit() }
                ?.takeIf { it.length >= 4 }?.padEnd(8, '0')?.let { "${it}T000000" })
            tag("developer", g.developer)
            tag("genre", g.genre)
            tag("playcount", g.playCount.toString())
            if (g.isFavorite) tag("favorite", "true")
            s.endTag(null, "game")
        }
        s.endTag(null, "gameList")
        s.endDocument()
        return w.toString()
    }
}
