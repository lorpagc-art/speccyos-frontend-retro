package com.generacionarcade.speccyos.network

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.generacionarcade.speccyos.Game
import com.generacionarcade.speccyos.HashUtility
import com.generacionarcade.speccyos.ImageProcessUtils
import com.generacionarcade.speccyos.SettingsManager
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream

class RateLimitException(message: String) : Exception(message)

/**
 * 🛰️ ULTRA-SCRAPER ENGINE V4.6 - "File System Fallback Shield"
 * Ahora intenta guardar usando DocumentFile (SAF), pero si el usuario no tiene los permisos
 * actualizados (SecurityException / Permission Denial), usa el acceso directo por la API de java.io.File.
 */
class OnlineScraperService(private val context: Context, private val settingsManager: SettingsManager) {

    private val TAG = "UltraScraperV4"
    private val USER_AGENT = "Speccy OS Imperial V0.4.4"
    private var masterIndex: JSONObject? = null

    // Semáforo para descargas pesadas
    private val downloadSemaphore = Semaphore(3) 

    init {
        try {
            context.assets.open("metadata/master_retroarch_systems_index.json").bufferedReader().use { it.readText() }.let {
                masterIndex = JSONObject(it)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error carga Master Index: ${e.message}")
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(regexPrefix, "")           
            .replace(regexSceneGroups, "")      
            .replace(regexParentheses, "")      
            .replace(regexBrackets, "")         
            .replace(regexExtensions, "")       
            .replace(regexDoubleSpace, " ")     
            .trim()
    }

    suspend fun testScreenScraperConnection(user: String?, pass: String?): String = withContext(Dispatchers.IO) {
        try {
            val resp = ScreenScraperRetrofitInstance.api.getApiUserInfo(
                devId = ScreenScraperRetrofitInstance.DEV_ID,
                devPassword = ScreenScraperRetrofitInstance.DEV_PASSWORD,
                softname = ScreenScraperRetrofitInstance.SOFTWARE_NAME,
                ssid = user?.trim(), 
                sspassword = pass?.trim()
            )
            if (resp.isSuccessful) "✅ CONEXIÓN IMPERIAL ACTIVA" else "❌ ERROR ${resp.code()}"
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { 
            Log.e(TAG, "Red Error: ${e.message}")
            "❌ FALLO DE RED" 
        }
    }

    suspend fun scrapeGame(game: Game): Game {
        return scrapeFromScreenScraper(game)
    }

    private suspend fun scrapeFromScreenScraper(game: Game): Game = withContext(Dispatchers.IO) {
        try {
            val user = settingsManager.scraperUsername
            val pass = settingsManager.scraperPassword
            val systemId = getScreenScraperSystemId(game.platformId)
            val fileSize = getFileSize(game.path)
            
            val mappedName = getMappedName(game.platformId, game.fileName)
            val cleanName = sanitizeTitle(game.fileName)

            Log.d(TAG, "🔍 Scrapeando [${game.platformId}]: Original='${game.fileName}' -> Clean='$cleanName'")

            // 1. INTENTO HASH FORENSE (Prioridad #1)
            val hashes = HashUtility.calculateHashes(context, Uri.parse(game.path))
            val md5 = hashes.first
            val crc = hashes.second

            var resp = if (md5 != null || crc != null) {
                ScreenScraperRetrofitInstance.api.getGameInfo(
                    devId = ScreenScraperRetrofitInstance.DEV_ID,
                    devPassword = ScreenScraperRetrofitInstance.DEV_PASSWORD,
                    softname = ScreenScraperRetrofitInstance.SOFTWARE_NAME,
                    ssid = user.ifEmpty { null },
                    sspassword = pass.ifEmpty { null },
                    md5 = md5,
                    crc = crc,
                    systemeid = systemId,
                    romtaille = fileSize
                )
            } else { null }

            // 2. INTENTO NOMBRE COMPLETO EXACTO
            if (resp == null || !resp.isSuccessful || resp.code() == 404) {
                resp = ScreenScraperRetrofitInstance.api.getGameInfo(
                    devId = ScreenScraperRetrofitInstance.DEV_ID,
                    devPassword = ScreenScraperRetrofitInstance.DEV_PASSWORD,
                    softname = ScreenScraperRetrofitInstance.SOFTWARE_NAME,
                    ssid = user.ifEmpty { null },
                    sspassword = pass.ifEmpty { null },
                    romnom = if (mappedName == null) game.fileName else null,
                    nom = mappedName,
                    systemeid = systemId,
                    romtaille = fileSize
                )
            }

            // 3. INTENTO BÚSQUEDA AGRESIVA (Sanitizer)
            if (!resp.isSuccessful || resp.code() == 404) {
                val searchName = mappedName ?: cleanName
                
                val searchResp = ScreenScraperRetrofitInstance.api.searchGame(
                    devId = ScreenScraperRetrofitInstance.DEV_ID,
                    devPassword = ScreenScraperRetrofitInstance.DEV_PASSWORD,
                    softname = ScreenScraperRetrofitInstance.SOFTWARE_NAME,
                    ssid = user.ifEmpty { null },
                    sspassword = pass.ifEmpty { null },
                    recherche = searchName,
                    systemeid = systemId
                )
                
                if (searchResp.isSuccessful) {
                    val foundGame = searchResp.body()?.response?.jeux?.firstOrNull()
                    if (foundGame != null) {
                        Log.d(TAG, "✅ Búsqueda Imperial Exitosa: ${foundGame.names?.firstOrNull()?.text}")
                        return@withContext processScreenScraperResponse(game, foundGame)
                    }
                }
            }

            if (resp.code() == 429) throw RateLimitException("API Overload (429 Too Many Requests)")

            if (resp.isSuccessful) {
                val data = resp.body()?.response?.game ?: return@withContext game
                Log.d(TAG, "✅ Scrape Directo Exitoso.")
                return@withContext processScreenScraperResponse(game, data)
            } else {
                Log.w(TAG, "❌ Fallo 404 definitivo para: $cleanName")
            }
        } catch (e: RateLimitException) { 
            throw e 
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) { 
            Log.e(TAG, "SS Error for ${game.title}: ${e.message}") 
        }
        game
    }

    private fun getFileSize(path: String): Long? {
        return try {
            val uri = Uri.parse(path)
            if (uri.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            } else {
                File(path).length()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun getMappedName(platformId: String, fileName: String): String? {
        val romKey = fileName.substringBeforeLast(".")
        masterIndex?.optJSONArray("systems")?.let { systems ->
            for (i in 0 until systems.length()) {
                val sys = systems.optJSONObject(i) ?: continue
                if (sys.optString("id").equals(platformId, ignoreCase = true)) {
                    return sys.optJSONObject("identity_map")?.optString(romKey, null)
                }
            }
        }
        return null
    }

    private suspend fun processScreenScraperResponse(game: Game, data: GameData): Game {
        var updated = game.copy(
            title = data.names?.find { it.language == "es" }?.text 
                    ?: data.names?.find { it.language == "en" }?.text 
                    ?: data.names?.firstOrNull()?.text 
                    ?: game.title,
            description = data.synopsis?.find { it.language == "es" }?.text 
                    ?: data.synopsis?.find { it.language == "en" }?.text 
                    ?: game.description,
            developer = data.developer?.text ?: game.developer,
            releaseDate = data.dates?.find { it.region == "eu" }?.text 
                    ?: data.dates?.find { it.region == "us" }?.text 
                    ?: data.dates?.firstOrNull()?.text 
                    ?: game.releaseDate,
            rating = try { data.note?.text?.toFloat() ?: 0f } catch(e:Exception) { 0f },
            genre = data.genres?.firstOrNull()?.noms?.find { it.language == "es" }?.text 
                    ?: data.genres?.firstOrNull()?.noms?.find { it.language == "en" }?.text 
                    ?: game.genre
        )

        // --- CONDICIONALES "A LA CARTA" ---
        
        if (settingsManager.scrapeBoxArt) {
            val boxArtUrl = data.medias?.find { it.type == "mixrbv2" }?.url
                ?: data.medias?.find { it.type == "box-2D" }?.url
                ?: data.medias?.find { it.type == "box-2d" }?.url

            boxArtUrl?.let { url ->
                downloadMedia(game, url, "images")?.let { updated = updated.copy(boxArt = it) }
            }
        }

        if (settingsManager.scrapeCd) {
            val cdUrl = data.medias?.find { it.type == "support-2d" }?.url 
                ?: data.medias?.find { it.type == "cd" }?.url
                
            cdUrl?.let { url ->
                downloadMedia(game, url, "cd")?.let { updated = updated.copy(cdArt = it) }
            }
        }
        
        if (settingsManager.scrapeScreenshot) {
            val screenUrl = data.medias?.find { it.type == "ss" }?.url 
                ?: data.medias?.find { it.type == "screenshot" }?.url
                
            screenUrl?.let { url ->
                downloadMedia(game, url, "screenshots")?.let { updated = updated.copy(screenshot = it) }
            }
        }

        if (settingsManager.scrapeWheel) {
            data.medias?.find { it.type == "wheel" }?.url?.let { url ->
                downloadMedia(game, url, "wheel")?.let { updated = updated.copy(wheel = it) }
            }
        }

        if (settingsManager.scrapeFanart) {
            data.medias?.find { it.type == "fanart" }?.url?.let { url ->
                downloadMedia(game, url, "fanart")?.let { updated = updated.copy(fanart = it) }
            }
        }

        if (settingsManager.scrapeVideos) {
            data.medias?.find { it.type == "video-normalized" }?.url?.let { url ->
                downloadMedia(game, url, "videos", ".mp4")?.let { updated = updated.copy(videoPreview = it) }
            }
        }

        return updated
    }

    private fun getScreenScraperSystemId(platformId: String): Int? {
        masterIndex?.optJSONArray("systems")?.let { systems ->
            for (i in 0 until systems.length()) {
                val sys = systems.optJSONObject(i) ?: continue
                if (sys.optString("id").equals(platformId, ignoreCase = true)) {
                    val ssId = sys.optJSONObject("scraper")?.optInt("system_id", -1) ?: -1
                    if (ssId != -1) return ssId
                }
            }
        }
        // Fallback Imperial
        return when (platformId.lowercase()) {
            "nes", "famicom" -> 3; "snes", "sfc" -> 4; "n64" -> 14; "psx", "ps1" -> 57; "ps2" -> 58;
            "gba" -> 12; "gbc" -> 10; "gb" -> 9; "megadrive", "genesis" -> 1; "mame" -> 75;
            "cps1" -> 6; "cps2" -> 7; "cps3" -> 17; "neogeo" -> 142; "dreamcast", "dc" -> 23;
            "gamecube", "gc" -> 13; "wii" -> 21; "3ds" -> 17; "nds" -> 15; "psp" -> 61;
            "msx", "msx1", "msx2" -> 49; "c64" -> 66; "zxspectrum" -> 76; "amiga" -> 64;
            "colecovision" -> 48; "pcengine" -> 31;
            else -> null
        }
    }

    private suspend fun downloadMedia(game: Game, url: String, subFolder: String, extensionOverride: String? = null): String? = withContext(Dispatchers.IO) {
        downloadSemaphore.withPermit {
            val extension = extensionOverride ?: ".webp"
            val fileName = "${game.fileName.substringBeforeLast(".")}$extension"

            try {
                val customMediaLocation = settingsManager.scraperMediaLocation
                val baseUriStr = if (customMediaLocation.isNotEmpty()) customMediaLocation else settingsManager.romsLocation
                
                if (baseUriStr.isEmpty()) return@withContext null
                
                // INTENTO 1: Usando la API moderna de Storage Access Framework (DocumentFile)
                try {
                    val baseDoc = DocumentFile.fromTreeUri(context, Uri.parse(baseUriStr))
                    if (baseDoc != null) {
                        val targetRoot = if (customMediaLocation.isEmpty()) findOrCreateDir(baseDoc, "media") else baseDoc
                        if (targetRoot != null) {
                            val targetFolder = findOrCreateDir(targetRoot, subFolder)
                            if (targetFolder != null) {
                                // 🛡️ ESCUDO CONTRA DUPLICADOS
                                val existingFile = targetFolder.findFile(fileName)
                                if (existingFile != null) {
                                    return@withContext existingFile.uri.toString()
                                }

                                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                                httpClient.newCall(request).execute().use { resp ->
                                    if (resp.isSuccessful && resp.body != null) {
                                        val file = targetFolder.createFile(if (extension == ".mp4") "video/mp4" else "image/webp", fileName)
                                        if (file != null) {
                                            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                                                resp.body!!.byteStream().use { input ->
                                                    if (extension == ".webp") {
                                                        ImageProcessUtils.convertToWebP(input)?.let { out.write(it) }
                                                    } else {
                                                        input.copyTo(out)
                                                    }
                                                }
                                            }
                                            return@withContext file.uri.toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permiso SAF denegado para $fileName, intentando Fallback Java.io.File...")
                    // Dejamos que falle silenciosamente y pase al Intento 2 (Fallback)
                }

                // INTENTO 2: FALLBACK JAVA.IO (Para firmwares chinos y Android 11/12 con permisos globales)
                val rawPath = getPathFromUriStr(baseUriStr)
                if (rawPath != null) {
                    val baseDir = File(rawPath)
                    val targetRoot = if (customMediaLocation.isEmpty()) File(baseDir, "media") else baseDir
                    val targetFolder = File(targetRoot, subFolder)

                    if (!targetFolder.exists()) targetFolder.mkdirs()

                    val targetFile = File(targetFolder, fileName)
                    if (targetFile.exists()) {
                        return@withContext Uri.fromFile(targetFile).toString()
                    }

                    val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            FileOutputStream(targetFile).use { out ->
                                resp.body!!.byteStream().use { input ->
                                    if (extension == ".webp") {
                                        ImageProcessUtils.convertToWebP(input)?.let { out.write(it) }
                                    } else {
                                        input.copyTo(out)
                                    }
                                }
                            }
                            return@withContext Uri.fromFile(targetFile).toString()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Una cancelacion no es un error: sin relanzarla, cerrar la
                // pantalla se registraba como fallo y la corrutina seguia
                // trabajando un rato mas, gastando bateria y red.
                throw e
            } catch (e: Exception) { 
                if (e.message?.contains("No space left", ignoreCase = true) == true) {
                    Log.e(TAG, "❌ ERROR CRÍTICO: DISCO LLENO.")
                } else {
                    Log.e(TAG, "Media download error para $fileName: ${e.message}")
                }
            }
            null
        }
    }

    private fun findOrCreateDir(parent: DocumentFile, name: String) = parent.findFile(name) ?: parent.createDirectory(name)

    // Convierte un URI de tipo 'content://...tree/primary:ROMS' a ruta real '/storage/emulated/0/ROMS'
    private fun getPathFromUriStr(uriStr: String): String? {
        try {
            val uri = Uri.parse(uriStr)
            val path = uri.path ?: return null
            if (path.contains("primary:")) {
                return "/storage/emulated/0/" + path.substringAfter("primary:")
            } else if (path.contains(":")) {
                val split = path.split(":")
                if (split.size >= 2) {
                    val uuid = split[0].substringAfterLast("/")
                    return "/storage/$uuid/" + split[1]
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {}
        return null
    }

    companion object {
        private val regexPrefix = Regex("^\\d{2,4}\\s*[-_.]?\\s*")
        private val regexSceneGroups = Regex("(?i)\\[?(trashman|mode7|pd|tr|en|fr|de|it|es|pt|ch|jp|ru|kr|beta|demo|proto|unl|hack|rev\\s*\\d+|v\\d+\\.\\d+)\\]?")
        private val regexParentheses = Regex("\\s*\\(.*?\\)")
        private val regexBrackets = Regex("\\s*\\[.*?\\]")
        private val regexExtensions = Regex("(?i)\\.(zip|sfc|smc|nes|gb|gbc|gba|pce|bin|gen|7z|iso|chd|cue|md)$")
        private val regexDoubleSpace = Regex("\\s{2,}")
    }
}
