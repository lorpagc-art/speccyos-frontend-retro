package com.generacionarcade.speccyos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.generacionarcade.speccyos.network.OnlineScraperService
import com.generacionarcade.speccyos.network.RateLimitException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class ScraperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "scraper_channel"
    private val notificationId = 101

    private val database = AppDatabase.getDatabase(context)
    private val gameDao = database.gameDao()
    private val settingsManager = SettingsManager(context)
    private val userStatusManager = UserStatusManager(context)
    private val onlineScraper = OnlineScraperService(context, settingsManager)

    /** Veces que ScreenScraper ha devuelto 429 en esta ejecucion, para el backoff. */
    private val rateLimitHits = java.util.concurrent.atomic.AtomicInteger(0)

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val platformId = inputData.getString("platformId")
        createNotificationChannel()
        
        val isImperial = userStatusManager.isImperial()
        val maxConcurrency = if (isImperial) 8 else 1
        val delayBetweenRequests = if (isImperial) 500L else 5000L
        val semaphore = Semaphore(maxConcurrency)
        
        val statusLabel = if (isImperial) "MODO IMPERIAL (ULTRA-SYNC X$maxConcurrency)" else "MODO ESTÁNDAR"
        
        try {
            setForeground(createForegroundInfo(0, "Sincronizando biblioteca local..."))
        } catch (e: Exception) { Log.e("ScraperWorker", "Error foreground", e) }

        try {
            val allGamesToCheck = if (platformId != null) {
                gameDao.getGamesByPlatformList(platformId)
            } else {
                gameDao.getAllGamesList()
            }

            val customMediaLocation = settingsManager.scraperMediaLocation
            val baseUriStr = if (customMediaLocation.isNotEmpty()) customMediaLocation else settingsManager.romsLocation
            
            if (baseUriStr.isEmpty()) return@withContext ListenableWorker.Result.failure()
            
            val baseDoc = DocumentFile.fromTreeUri(applicationContext, baseUriStr.toUri())
            val mediaDir = if (customMediaLocation.isEmpty()) baseDoc?.findFile("media") else baseDoc

            // --- 1. CONSTRUCCIÓN DE CACHÉ LOCAL (EVITA LISTAR MILES DE VECES) ---
            val localCache = buildLocalMediaCache(mediaDir)
            
            // --- 2. FILTRADO INTELIGENTE Y AUTOCURACIÓN DE BD ---
            //
            // ESCRITURA EN LOTE. Antes se llamaba a `gameDao.updateGame(g)` DENTRO
            // del filter, fila a fila sobre toda la biblioteca: con 20.000 juegos
            // son 20.000 transacciones SQLite independientes, cada una con su
            // fsync incluso en modo WAL. Ahora se acumulan y se vuelcan de golpe.
            val alreadyFailed = applicationContext
                .getSharedPreferences("speccy_scraper_fails", android.content.Context.MODE_PRIVATE)
                .getStringSet("paths", emptySet()) ?: emptySet()
            val pendingLocalUpdates = mutableListOf<Game>()
            val gamesToScrape = allGamesToCheck.filter { game ->
                val updatedGame = syncGameWithLocalCache(game, localCache)
                if (updatedGame != game) {
                    pendingLocalUpdates += updatedGame
                }
                // Solo mandamos al scraper online si aun le falta algo Y no ha
                // fallado ya en pasadas anteriores.
                needsOnlineScraping(updatedGame) && updatedGame.path !in alreadyFailed
            }
            if (pendingLocalUpdates.isNotEmpty()) {
                pendingLocalUpdates.chunked(500).forEach { gameDao.updateGames(it) }
                Log.i("ScraperWorker", "Autocuración local: ${pendingLocalUpdates.size} juegos actualizados en lote.")
            }

            val total = gamesToScrape.size
            if (total == 0) {
                updateNotification(100, "¡Biblioteca sincronizada localmente!")
                return@withContext ListenableWorker.Result.success()
            }

            val processedCount = AtomicInteger(0)
            // Fallos PERSISTIDOS: sin esto, cada ejecucion del worker reintentaba
            // eternamente los mismos juegos que ScreenScraper no conoce, gastando
            // cuota de peticiones y arriesgando un baneo.
            val failPrefs = applicationContext.getSharedPreferences("speccy_scraper_fails", android.content.Context.MODE_PRIVATE)
            val previouslyFailed = failPrefs.getStringSet("paths", emptySet()) ?: emptySet()
            val failedPaths = java.util.Collections.synchronizedSet(previouslyFailed.toMutableSet())
            
            gamesToScrape.chunked(if (isImperial) 50 else 10).forEach { batch ->
                coroutineScope {
                    batch.forEach { game ->
                        launch {
                            semaphore.withPermit {
                                val current = processedCount.incrementAndGet()
                                val progress = (current.toFloat() / total * 100).toInt()
                                
                                if (current % 5 == 0 || isImperial) {
                                    updateNotification(progress, "[$current/$total] $statusLabel: ${game.title}")
                                }
                                
                                try {
                                    val updatedGame = onlineScraper.scrapeGame(game)
                                    if (updatedGame != game) {
                                        gameDao.updateGame(updatedGame)
                                    } else {
                                        // Marcamos el intento fallido para no reintentar
                                        // eternamente los mismos juegos sin resultado en
                                        // cada ejecución del worker.
                                        failedPaths.add(game.path)
                                    }
                                    delay(delayBetweenRequests)
                                } catch (e: RateLimitException) {
                                    // Backoff exponencial y compartido: con hasta 8 tareas en
                                    // paralelo, pausar solo la que se comio el 429 dejaba a las
                                    // otras siete martilleando ScreenScraper, que es como se
                                    // acaba con la cuenta baneada en vez de solo frenada.
                                    val intento = rateLimitHits.incrementAndGet()
                                    val espera = (60_000L * intento).coerceAtMost(300_000L)
                                    Log.w("ScraperWorker", "429 de ScreenScraper (x$intento): esperando ${espera / 1000}s")
                                    delay(espera)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    // Nunca tragarse una cancelacion: sin esto, parar el worker
                                    // se registraba como un error mas y la corrutina seguia
                                    // trabajando un rato mas gastando bateria y red.
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("ScraperWorker", "Error en ${game.title}", e)
                                }
                            }
                        }
                    }
                }
                if (isImperial) delay(1000)
            }

            if (failedPaths.isNotEmpty()) {
                failPrefs.edit().putStringSet("paths", failedPaths.toSet()).apply()
                Log.i("ScraperWorker", "${failedPaths.size} juegos sin resultado online (marcados para no reintentar).")
            }
            updateNotification(100, "¡Sincronización Imperial completada!")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("ScraperWorker", "Fallo fatal en el motor", e)
            ListenableWorker.Result.failure()
        }
    }

    private class LocalMediaCache {
        val images = mutableMapOf<String, String>()
        val videos = mutableMapOf<String, String>()
        val wheel = mutableMapOf<String, String>()
        val fanart = mutableMapOf<String, String>()
    }

    private fun buildLocalMediaCache(mediaDir: DocumentFile?): LocalMediaCache {
        val cache = LocalMediaCache()
        if (mediaDir == null) return cache
        
        fun fillMap(folderName: String, map: MutableMap<String, String>) {
            mediaDir.findFile(folderName)?.listFiles()?.forEach { file ->
                file.name?.lowercase()?.let { map[it] = file.uri.toString() }
            }
        }
        
        fillMap("images", cache.images)
        fillMap("videos", cache.videos)
        fillMap("wheel", cache.wheel)
        fillMap("fanart", cache.fanart)
        
        return cache
    }

    private fun syncGameWithLocalCache(game: Game, cache: LocalMediaCache): Game {
        var updated = game
        val baseName = game.fileName.substringBeforeLast(".").lowercase()
        
        fun findInCache(map: Map<String, String>, base: String, extensions: List<String>, suffixes: List<String>): String? {
            for (s in suffixes) {
                for (e in extensions) {
                    val fullName = "$base$s$e"
                    if (map.containsKey(fullName)) return map[fullName]
                }
            }
            return null
        }

        if (updated.boxArt == null && settingsManager.scrapeBoxArt) {
            findInCache(cache.images, baseName, listOf(".webp", ".png", ".jpg", ".jpeg"), listOf("", "-image", "-thumb", "_boxart"))?.let {
                updated = updated.copy(boxArt = it)
            }
        }
        
        if (updated.videoPreview == null && settingsManager.scrapeVideos) {
            findInCache(cache.videos, baseName, listOf(".mp4", ".mkv", ".avi", ".webm"), listOf("", "-video", "_video"))?.let {
                updated = updated.copy(videoPreview = it)
            }
        }

        if (updated.wheel == null && settingsManager.scrapeWheel) {
            findInCache(cache.wheel, baseName, listOf(".png", ".webp"), listOf("", "-wheel", "_logo"))?.let {
                updated = updated.copy(wheel = it)
            }
        }

        if (updated.fanart == null && settingsManager.scrapeFanart) {
            findInCache(cache.fanart, baseName, listOf(".jpg", ".png", ".webp"), listOf("", "-fanart", "_background"))?.let {
                updated = updated.copy(fanart = it)
            }
        }
        
        return updated
    }

    private fun needsOnlineScraping(game: Game): Boolean {
        // Si falta la descripción, consideramos que necesita scrape de datos
        if (game.description == null) return true
        
        // Si falta algún medio que el usuario QUIERE descargar
        if (settingsManager.scrapeBoxArt && game.boxArt == null) return true
        if (settingsManager.scrapeVideos && game.videoPreview == null) return true
        if (settingsManager.scrapeWheel && game.wheel == null) return true
        if (settingsManager.scrapeFanart && game.fanart == null) return true
        
        return false
    }

    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Speccy OS: Ultra-Scraper")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(notificationId, notification)
    }

    private fun updateNotification(progress: Int, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Speccy OS: Ultra-Scraper")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sincronización Imperial", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
