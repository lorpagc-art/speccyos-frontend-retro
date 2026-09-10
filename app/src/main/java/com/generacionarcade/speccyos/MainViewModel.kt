package com.generacionarcade.speccyos

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.generacionarcade.speccyos.network.OnlineScraperService
import com.generacionarcade.speccyos.network.ScrapeCandidate
import com.generacionarcade.speccyos.network.ScreenScraperRetrofitInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val gameDao = database.gameDao()
    private val scanner = RomScanner(application)
    val settingsManager = SettingsManager(application)
    private val onlineScraper = OnlineScraperService(application, settingsManager)
    
    // IA ELIMINADA - Versión 1.1.4
    
    val cloudSaveManager = CloudSaveManager(application)
    val raManager = RetroAchievementsManager(application, settingsManager)
    val achievementEngine = AchievementEngine(application)

    val favoriteGames = gameDao.getFavoriteGames()
    val recentGames = gameDao.getRecentGames()
    
    val scrapedGamesCount = gameDao.getScrapedGamesCount()
    val missingMediaCount = gameDao.getMissingMediaCount()
    val scrapingStatsByPlatform = gameDao.getScrapingStatsByPlatform()
    
    val platformGameCounts: StateFlow<Map<String, Int>> = scrapingStatsByPlatform
        .map { stats -> stats.associate { it.platformId to it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val platformProgress: StateFlow<Map<String, Float>> = scrapingStatsByPlatform
        .map { stats -> stats.associate { it.platformId to if(it.total > 0) it.scraped.toFloat()/it.total else 0f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * "Un día como hoy".
     *
     * Antes: `gameDao.getAllGames()` (la tabla ENTERA: con una colección MAME son
     * 30.000 objetos Game con descripciones largas) seguido de un `.map` que
     * compila un Regex y lo aplica juego a juego — y ese `.map` se ejecuta en el
     * contexto del colector, o sea `Dispatchers.Main.immediate`. Cientos de ms de
     * bloqueo del hilo principal EN CADA cambio de la tabla, incluidos los lotes
     * de inserción del escaneo.
     *
     * Ahora se filtra en SQL y se recalcula una vez, no en cada emisión.
     */
    private val _gamesReleasedToday = MutableStateFlow<List<Game>>(emptyList())
    val gamesReleasedToday: StateFlow<List<Game>> = _gamesReleasedToday.asStateFlow()

    fun refreshGamesReleasedToday() {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = java.util.Calendar.getInstance()
            val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val m = cal.get(java.util.Calendar.MONTH) + 1
            val dd = String.format(Locale.US, "%02d", d)
            val mm = String.format(Locale.US, "%02d", m)
            val iso  = "%-$mm-$dd%"   // YYYY-MM-DD
            val euro = "%$dd/$mm/%"   // DD/MM/YYYY
            _gamesReleasedToday.value = runCatching { gameDao.getGamesReleasedOn(iso, euro) }
                .getOrDefault(emptyList())
        }
    }

    /**
     * Busca en la biblioteca el juego al que se refiere el reto del dia.
     *
     * El boton JUGAR del reto estaba sin implementar: tenia literalmente
     * `null as Game?` con un comentario "se buscaria en la DB". Pulsarlo no
     * lanzaba nada y aun asi la tarjeta se marcaba "COMPLETADO".
     *
     * Se busca primero en la plataforma que indica el reto y, si el usuario no
     * la tiene con ese id, en toda la biblioteca: el reto viene de Remote
     * Config con nombres tipo "megadrive", y cada consola nombra sus carpetas
     * como quiere. Devuelve null si no lo tiene, y entonces la tarjeta lo dice
     * en vez de fingir que se puede jugar.
     */
    suspend fun findGameForChallenge(platformId: String, title: String): Game? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            runCatching {
                // searchInPlatform hace `title LIKE :query`: sin comodines solo
                // acertaria con el titulo exacto, y los retos vienen con nombres
                // comerciales ("Sonic the Hedgehog") que en disco son ficheros
                // como "Sonic The Hedgehog (USA, Europe).md".
                val enPlataforma = if (platformId.isNotBlank()) {
                    gameDao.searchInPlatform(platformId, "%$title%")
                } else emptyList()
                enPlataforma.firstOrNull()
                    ?: gameDao.getAllGamesList().firstOrNull {
                        it.title.equals(title, ignoreCase = true)
                    }
                    ?: gameDao.getAllGamesList().firstOrNull {
                        it.title.contains(title, ignoreCase = true)
                    }
            }.getOrNull()
        }

    /** Recupera un juego por su ruta (clave primaria), para el retorno de partida. */
    suspend fun findGameByPath(path: String): Game? = withContext(Dispatchers.IO) {
        runCatching { gameDao.getGameByPath(path) }.getOrNull()
    }

    private val _isScrapingActive = MutableStateFlow(false)
    val isScrapingActive = _isScrapingActive.asStateFlow()

    private val _selectedGameAchievements = MutableStateFlow<JSONObject?>(null)
    val selectedGameAchievements = _selectedGameAchievements.asStateFlow()

    private val _userRaProfile = MutableStateFlow("Cargando...")
    val userRaProfile = _userRaProfile.asStateFlow()

    private val _mediaCacheSize = MutableStateFlow("Calculando...")
    val mediaCacheSize = _mediaCacheSize.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud = _isSyncingCloud.asStateFlow()

    private val _scrapeCandidates = MutableStateFlow<List<ScrapeCandidate>>(emptyList())
    val scrapeCandidates = _scrapeCandidates.asStateFlow()
    private val _isSearchingManual = MutableStateFlow(false)
    val isSearchingManual = _isSearchingManual.asStateFlow()
    private val _discoveredPlatformIds = MutableStateFlow(settingsManager.cachedPlatformIds)
    // Antes era un Flow FRÍO que reasignaba un Set nuevo en cada emisión y se
    // recolectaba con collectAsState(emptySet()). El resto del ViewModel ya usaba
    // stateIn; aquí faltaba.
    val activePlatforms: StateFlow<Set<String>> = _discoveredPlatformIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private val _scanStatusMessage = MutableStateFlow("")
    val scanStatusMessage = _scanStatusMessage.asStateFlow()
    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()
    val isReady = isInitializing.map { !it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    // ESTADOS DE IA ELIMINADOS
    
    // --- ESTADOS REACTIVOS DE PANTALLA ---
    private val _screenScaleX = MutableStateFlow(settingsManager.screenWidthScale)
    val screenScaleX = _screenScaleX.asStateFlow()
    private val _screenScaleY = MutableStateFlow(settingsManager.screenHeightScale)
    val screenScaleY = _screenScaleY.asStateFlow()
    private val _screenAspectRatio = MutableStateFlow(settingsManager.screenAspectRatio)
    val screenAspectRatio = _screenAspectRatio.asStateFlow()

    fun updateScreenMetrics(scaleX: Float, scaleY: Float, ratio: String) {
        settingsManager.screenWidthScale = scaleX
        settingsManager.screenHeightScale = scaleY
        settingsManager.screenAspectRatio = ratio
        _screenScaleX.value = scaleX
        _screenScaleY.value = scaleY
        _screenAspectRatio.value = ratio
    }

    private val _gameToLaunchEvent = MutableSharedFlow<Game>()
    val gameToLaunchEvent = _gameToLaunchEvent.asSharedFlow()

    private val _dashboardPacks = MutableStateFlow<List<String>>(listOf("default"))
    val dashboardPacks = _dashboardPacks.asStateFlow()

    private val _customFanartFolders = MutableStateFlow<List<String>>(emptyList())
    val customFanartFolders = _customFanartFolders.asStateFlow()

    private val _customThemeMedia = MutableStateFlow<Map<String, String>>(emptyMap())
    val customThemeMedia = _customThemeMedia.asStateFlow()

    private var scanJob: Job? = null

    init {
        loadInitialData()
        refreshRaProfile()
        monitorScrapingWork()
        refreshGamesReleasedToday()
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val countsList = gameDao.getPlatformCounts()
                if (countsList.isNotEmpty()) _scanProgress.value = 1.0f
                val platforms = countsList.map { it.platformId }.toSet()
                if (platforms.isNotEmpty()) {
                    _discoveredPlatformIds.value = platforms
                    settingsManager.cachedPlatformIds = platforms
                }
                initializeDashboardFolder()
            } catch (e: Exception) { 
                Log.e("MainViewModel", "Init error", e) 
            } finally { 
                _isInitializing.value = false 
            }
        }
    }

    private suspend fun initializeDashboardFolder() = withContext(Dispatchers.IO) {
        val rootUri = settingsManager.romsLocation
        if (rootUri.isEmpty()) return@withContext

        try {
            val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(rootUri)) ?: return@withContext
            
            var dashboardDoc = rootDoc.findFile("Dashboard")
            if (dashboardDoc == null) {
                dashboardDoc = rootDoc.createDirectory("Dashboard")
                dashboardDoc?.createDirectory("ejemplo_pack_neones")
            }

            val packs = mutableListOf("default")
            dashboardDoc?.listFiles()?.forEach { file ->
                val nombre = file.name
                if (file.isDirectory && nombre != null) {
                    packs.add(nombre)
                }
            }
            _dashboardPacks.value = packs

            val currentPack = settingsManager.customDashboardPackName
            if (currentPack != "default" && !packs.contains(currentPack)) {
                settingsManager.customDashboardPackName = "default"
            }

            val mediaMap = mutableMapOf<String, String>()
            if (settingsManager.customDashboardPackName != "default") {
                val packDoc = dashboardDoc?.findFile(settingsManager.customDashboardPackName)
                packDoc?.listFiles()?.forEach { folder ->
                    val nombreCarpeta = folder.name
                    if (folder.isDirectory && nombreCarpeta != null) {
                        val folderName = nombreCarpeta.lowercase()
                        folder.listFiles().forEach { file ->
                            val nombreFichero = file.name
                            if (file.isFile && nombreFichero != null) {
                                val nameWithoutExt = nombreFichero.substringBeforeLast(".").lowercase()
                                mediaMap["${folderName}_$nameWithoutExt"] = file.uri.toString()
                            }
                        }
                    }
                }
            }

            val customMedia = settingsManager.scraperMediaLocation
            val baseUriMedia = if (customMedia.isNotEmpty()) customMedia else settingsManager.romsLocation
            val mediaRootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(baseUriMedia))
            var mediaDoc = if (customMedia.isEmpty()) mediaRootDoc?.findFile("media") else mediaRootDoc
            
            if (mediaDoc == null && customMedia.isEmpty()) {
                mediaDoc = rootDoc.createDirectory("media")
            }
            
            var fanartsDoc = mediaDoc?.findFile("fanarts")
            if (fanartsDoc == null) {
                fanartsDoc = mediaDoc?.createDirectory("fanarts")
                fanartsDoc?.createDirectory("custom_pack_1")
            }

            val fanartFolders = mutableListOf<String>()
            fanartsDoc?.listFiles()?.forEach { file ->
                val nombre = file.name
                if (file.isDirectory && nombre != null) {
                    fanartFolders.add(nombre)
                }
            }
            _customFanartFolders.value = fanartFolders

            val currentFanart = settingsManager.systemArtworkStyle
            if (currentFanart != SettingsManager.ARTWORK_STYLE_CYBERPUNK && currentFanart != SettingsManager.ARTWORK_STYLE_ICONIC) {
                val fanartDoc = fanartsDoc?.findFile(currentFanart)
                fanartDoc?.listFiles()?.forEach { file ->
                    val nombreFichero = file.name
                    if (file.isFile && nombreFichero != null) {
                        val nameWithoutExt = nombreFichero.substringBeforeLast(".").lowercase()
                        mediaMap["${currentFanart.lowercase()}_$nameWithoutExt"] = file.uri.toString()
                    }
                }
            }

            _customThemeMedia.value = mediaMap

        } catch (e: Exception) {
            Log.e("MainViewModel", "Error gestionando carpeta Dashboard", e)
        }
    }

    fun refreshDashboardPacks() {
        viewModelScope.launch {
            initializeDashboardFolder()
        }
    }

    private fun monitorScrapingWork() {
        WorkManager.getInstance(getApplication())
            .getWorkInfosByTagLiveData("scraper_task")
            .asFlow()
            .onEach { workInfos ->
                val active = workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true
                _isScrapingActive.value = active
            }
            .launchIn(viewModelScope)
    }

    fun refreshRaProfile() {
        viewModelScope.launch {
            if (settingsManager.isRAEnabled) _userRaProfile.value = raManager.getUserProfile()
            else _userRaProfile.value = "RA Desactivado"
        }
    }

    suspend fun getGameCountSync(): Int {
        return gameDao.getGameCountSync()
    }

    private suspend fun preloadExistingMedia(): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val customMedia = settingsManager.scraperMediaLocation
        val baseUriStr = if (customMedia.isNotEmpty()) customMedia else settingsManager.romsLocation
        if (baseUriStr.isEmpty()) return@withContext emptyMap()

        val resultMap = mutableMapOf<String, Map<String, String>>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(baseUriStr))
            val mediaDoc = if (customMedia.isEmpty()) rootDoc?.findFile("media") else rootDoc
            if (mediaDoc == null || !mediaDoc.exists()) return@withContext emptyMap()

            val subfolders = listOf("images", "videos", "wheel", "fanart")
            for (sub in subfolders) {
                val subDoc = mediaDoc.findFile(sub)
                if (subDoc != null && subDoc.isDirectory) {
                    val fileMap = mutableMapOf<String, String>()
                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        subDoc.uri, android.provider.DocumentsContract.getDocumentId(subDoc.uri)
                    )
                    getApplication<Application>().contentResolver.query(childrenUri, arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ), null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameCol) ?: continue
                            val docId = cursor.getString(idCol)
                            val nameWithoutExt = name.substringBeforeLast(".").lowercase()
                            val itemUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(subDoc.uri, docId)
                            fileMap[nameWithoutExt] = itemUri.toString()
                        }
                    }
                    resultMap[sub] = fileMap
                }
            }
        } catch (e: Exception) {
             Log.e("MainViewModel", "Error preloading media", e)
        }
        return@withContext resultMap
    }

    fun fullRescan(incremental: Boolean = false) {
        scanJob?.cancel()
        _isScanning.value = false
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = 0f
            _scanStatusMessage.value = if(incremental) "Buscando nuevos juegos y rutas..." else "Reiniciando base de datos..."
            
            initializeDashboardFolder()

            val existingMedia = preloadExistingMedia()
            val imagesMap = existingMedia["images"] ?: emptyMap()
            val videosMap = existingMedia["videos"] ?: emptyMap()
            val wheelMap = existingMedia["wheel"] ?: emptyMap()
            val fanartMap = existingMedia["fanart"] ?: emptyMap()

            val gameChannel = Channel<Game>(capacity = 5000)
            val discoveredPlatforms = Collections.synchronizedSet(mutableSetOf<String>())
            var totalGamesCount = 0
            var lastUiUpdate = 0L

            val consumerJob = launch {
                val buffer = mutableListOf<Game>()
                try {
                    for (rawGame in gameChannel) {
                        val nameKey = rawGame.fileName.substringBeforeLast(".").lowercase()
                        
                        val game = rawGame.copy(
                            boxArt = imagesMap[nameKey],
                            videoPreview = videosMap[nameKey],
                            wheel = wheelMap[nameKey],
                            fanart = fanartMap[nameKey]
                        )
                        
                        buffer.add(game)
                        totalGamesCount++
                        discoveredPlatforms.add(game.platformId)
                        if (buffer.size >= 200) {
                            val batch = buffer.toList()
                            buffer.clear()
                            
                            if(incremental) gameDao.insertOrIgnore(batch)
                            else gameDao.insertAll(batch)
                            
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 500) {
                                _discoveredPlatformIds.value = discoveredPlatforms.toSet()
                                _scanStatusMessage.value = "Procesados $totalGamesCount juegos..."
                                lastUiUpdate = now
                            }
                        }
                    }
                } finally {
                    if (buffer.isNotEmpty()) {
                        if(incremental) gameDao.insertOrIgnore(buffer)
                        else gameDao.insertAll(buffer)
                    }
                    _discoveredPlatformIds.value = discoveredPlatforms.toSet()
                    _scanStatusMessage.value = "¡Escaneo completo! $totalGamesCount juegos procesados."
                }
            }

            try {
                if (!incremental) gameDao.deleteAll()
                
                val allRoots = settingsManager.getAllRomsLocations()
                
                if (allRoots.isEmpty()) { 
                    _isScanning.value = false
                    return@launch 
                }
                
                var totalSystems = 0
                allRoots.forEach { path ->
                    try {
                        val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(path))
                        totalSystems += rootDoc?.listFiles()?.count { it.isDirectory } ?: 0
                    } catch (e: Exception) { Log.w("MainViewModel", "Error counting systems for $path") }
                }
                
                val systemsFinished = AtomicInteger(0)
                
                allRoots.map { rootPath ->
                    launch {
                        scanner.scanAllRecursive(
                            rootUri = rootPath, 
                            onGameFound = { gameChannel.send(it) }, 
                            onPlatformDiscovered = {}, 
                            onPlatformFinished = {
                                val finished = systemsFinished.incrementAndGet()
                                _scanProgress.value = finished.DivideFloatBy(totalSystems.coerceAtLeast(1))
                            }
                        )
                    }
                }.forEach { it.join() }
                
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("MainViewModel", "Escaneo cancelado.")
                } else {
                    Log.e("MainViewModel", "Scan error crítico", e)
                }
            } finally { 
                gameChannel.close()
                consumerJob.join()
                
                val platformCount = discoveredPlatforms.size
                achievementEngine.onLibraryUpdated(totalGamesCount, platformCount)

                if (incremental) {
                    val currentCached = settingsManager.cachedPlatformIds.toMutableSet()
                    currentCached.addAll(discoveredPlatforms)
                    settingsManager.cachedPlatformIds = currentCached
                } else {
                    settingsManager.cachedPlatformIds = discoveredPlatforms.toSet()
                }
                
                _scanProgress.value = 1.0f
                _isScanning.value = false 
            }
        }
    }

    private fun Int.DivideFloatBy(divisor: Int): Float = this.toFloat() / divisor.toFloat()

    suspend fun getRandomGameWithVideo(): Game? = withContext(Dispatchers.IO) {
        gameDao.getRandomGameWithVideo()
    }

    fun forceScrapePlatform(platformId: String) {
        val workRequest = OneTimeWorkRequestBuilder<ScraperWorker>()
            .setInputData(workDataOf("platformId" to platformId))
            .addTag("scraper_task")
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork("scrape_$platformId", ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun forceScrapeAll() {
        val workRequest = OneTimeWorkRequestBuilder<ScraperWorker>().addTag("scraper_task").build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork("scrape_all", ExistingWorkPolicy.KEEP, workRequest)
    }

    fun stopScraping() { 
        WorkManager.getInstance(getApplication()).cancelAllWorkByTag("scraper_task")
        _isScrapingActive.value = false 
    }
    
    fun toggleFavorite(game: Game) = viewModelScope.launch(Dispatchers.IO) { 
        gameDao.updateGame(game.copy(isFavorite = !game.isFavorite)) 
        achievementEngine.onFavoriteAdded(gameDao.getFavoriteCountSync())
    }
    
    fun calculateMediaCacheSize() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val customMedia = settingsManager.scraperMediaLocation
            val baseUri = if (customMedia.isNotEmpty()) customMedia else settingsManager.romsLocation
            if (baseUri.isEmpty()) { 
                _mediaCacheSize.value = "0 MB"
                return@launch 
            }

            val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(baseUri))
            val mediaDir = if (customMedia.isEmpty()) rootDoc?.findFile("media") else rootDoc
            
            if (mediaDir == null || !mediaDir.exists()) { 
                _mediaCacheSize.value = "0 MB"
                return@launch 
            }

            var totalBytes = 0L
            fun sumSize(dir: DocumentFile) {
                dir.listFiles().forEach { file ->
                    if (file.isDirectory) sumSize(file) else totalBytes += file.length()
                }
            }
            
            val targets = listOf("images", "videos", "wheel", "fanart")
            targets.forEach { name -> mediaDir.findFile(name)?.let { sumSize(it) } }

            val mb = totalBytes / (1024 * 1024)
            _mediaCacheSize.value = if (mb > 1024) String.format(Locale.US, "%.2f GB", mb / 1024f) else "$mb MB"
        } catch (e: Exception) { 
            _mediaCacheSize.value = "Error"
        }
    }

    fun purgeVideos() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val customMedia = settingsManager.scraperMediaLocation
            val baseUri = if (customMedia.isNotEmpty()) customMedia else settingsManager.romsLocation
            if (baseUri.isEmpty()) return@launch

            val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(baseUri))
            val mediaDir = if (customMedia.isEmpty()) rootDoc?.findFile("media") else rootDoc
            val videoDir = mediaDir?.findFile("videos")

            videoDir?.listFiles()?.forEach { it.delete() }
            gameDao.clearAllVideoPaths()
            calculateMediaCacheSize()
        } catch (e: Exception) { 
            Log.e("MainViewModel", "Error purge", e) 
        }
    }

    fun markGameAsPlayed(game: Game) { 
        viewModelScope.launch(Dispatchers.IO) { 
            gameDao.updateGame(game.copy(lastPlayed = System.currentTimeMillis(), playCount = game.playCount + 1)) 
            achievementEngine.onGameLaunched(game, settingsManager.gamesLaunchedCount)
        } 
    }
    
    fun getGamesForPlatform(platformId: String): Flow<List<Game>> = gameDao.getGamesByPlatform(platformId)

    fun addExtraRomsLocation(path: String) {
        val current = settingsManager.extraRomsLocations.toMutableSet()
        current.add(path)
        settingsManager.extraRomsLocations = current
        fullRescan(incremental = true)
    }

    fun removeExtraRomsLocation(path: String) {
        val current = settingsManager.extraRomsLocations.toMutableSet()
        current.remove(path)
        settingsManager.extraRomsLocations = current
        fullRescan(incremental = false)
    }

    fun searchManual(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingManual.value = true
            try {
                val resp = ScreenScraperRetrofitInstance.api.searchGame(
                    devId = ScreenScraperRetrofitInstance.DEV_ID,
                    devPassword = ScreenScraperRetrofitInstance.DEV_PASSWORD,
                    softname = ScreenScraperRetrofitInstance.SOFTWARE_NAME,
                    ssid = settingsManager.scraperUsername.ifEmpty { null },
                    sspassword = settingsManager.scraperPassword.ifEmpty { null },
                    recherche = query
                )
                if (resp.isSuccessful) {
                    val candidates = resp.body()?.response?.jeux?.map { data ->
                        ScrapeCandidate(
                            id = data.names?.firstOrNull()?.text ?: "unknown",
                            title = data.names?.firstOrNull { it.language == "es" }?.text 
                                ?: data.names?.firstOrNull { it.language == "en" }?.text 
                                ?: data.names?.firstOrNull()?.text ?: "Sin título",
                            platform = data.dates?.firstOrNull()?.text ?: "Desconocida",
                            imageUrl = data.medias?.find { it.type == "mixrbv2" || it.type == "box-2D" }?.url,
                            releaseDate = data.dates?.firstOrNull()?.text,
                            developer = data.developer?.text,
                            source = "SCREEN_SCRAPER"
                        )
                    } ?: emptyList()
                    _scrapeCandidates.value = candidates
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error manual search", e)
            } finally {
                _isSearchingManual.value = false
            }
        }
    }

    fun applyManualCandidate(game: Game, candidate: ScrapeCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedGame = onlineScraper.scrapeGame(game.copy(title = candidate.title))
                gameDao.updateGame(updatedGame)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error applying candidate", e)
            }
        }
    }
}
