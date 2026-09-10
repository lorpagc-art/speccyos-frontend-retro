package com.generacionarcade.speccyos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalAnimationApi::class, UnstableApi::class)
@Composable
fun SpeccyDashboard(
    mainViewModel: MainViewModel,
    hardwareViewModel: HardwareViewModel,
    userStatusManager: UserStatusManager,
    settingsManager: SettingsManager,
    soundManager: SoundManager,
    hapticHandler: HapticHandler,
    onSettingsClick: () -> Unit,
    onArchitectClick: () -> Unit,
    onManualClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTweakerClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onWarRoomClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val lang = settingsManager.appLanguage

    val activePlatforms by mainViewModel.activePlatforms.collectAsStateWithLifecycle()
    var mainCarouselIndex by remember { mutableIntStateOf(0) }

    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    val platformScrollPositions = remember { mutableStateMapOf<String, Int>() }

    var isAndroidSettingsVisible by remember { mutableStateOf(false) }
    var isAppViewActive by remember { mutableStateOf(false) }
    // Buscador global. SmartSearchScreen ya existia y no era alcanzable: el boton
    // mostraba un Toast de "proximamente". Se abre como overlay del dashboard para
    // reutilizar launchGameFunction en vez de duplicar la logica de lanzamiento.
    var isSearchVisible by remember { mutableStateOf(false) }
    // Ruleta y ADN de jugador: existian y no eran alcanzables. Se exponen como
    // tarjetas del carrusel, que es la via descubrible que ya usa el dashboard.
    var isRouletteVisible by remember { mutableStateOf(false) }
    var isDnaVisible by remember { mutableStateOf(false) }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Una sola lambda estable para los seis sitios que resuelven la seleccion de
    // sistema (uno por tema, mas el manejador de teclas del carrusel).
    //
    // Antes cada tema recibia una lambda NUEVA en cada recomposicion del dashboard
    // -y el dashboard recompone a menudo: lastInteractionTime cambia con cada
    // pulsacion-, asi que ningun contenido de tema podia saltarse la recomposicion
    // aunque no hubiera cambiado nada visible. Ademas era la misma llamada de 14
    // argumentos posicionales copiada seis veces, donde intercambiar dos lambdas
    // ()->Unit por error no da ningun error de compilacion.
    val onSystemSelected: (String) -> Unit = remember(
        onSettingsClick, onArchitectClick, onManualClick, onBenchmarkClick,
        onTweakerClick, onAchievementsClick, onWarRoomClick, soundManager
    ) {
        { id: String ->
            lastInteractionTime = System.currentTimeMillis()
            handleSystemSelection(
                id, onSettingsClick, onArchitectClick, onManualClick, onBenchmarkClick,
                onTweakerClick, onAchievementsClick, onWarRoomClick,
                { isRouletteVisible = true }, { isDnaVisible = true },
                { isAndroidSettingsVisible = true }, { isAppViewActive = true },
                { selectedPlatform = id }, soundManager
            )
        }
    }
    var isAttractModeActive by remember { mutableStateOf(false) }
    var attractModeCurrentGame by remember { mutableStateOf<Game?>(null) }

    val recentGames by mainViewModel.recentGames.collectAsState(initial = emptyList())
    val favoriteGames by mainViewModel.favoriteGames.collectAsState(initial = emptyList())
    val hardwareState by hardwareViewModel.uiState.collectAsStateWithLifecycle()
    val gameCounts by mainViewModel.platformGameCounts.collectAsStateWithLifecycle()
    val userLevel by userStatusManager.userLevel.collectAsStateWithLifecycle()
    val userName by userStatusManager.userName.collectAsStateWithLifecycle()
    val userPhoto by userStatusManager.userPhotoUrl.collectAsStateWithLifecycle()

    var isSyncingCloud by remember { mutableStateOf(false) }
    var isAppResumed by remember { mutableStateOf(true) }
    
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // La postura del aparato manda sobre la preferencia guardada: al plegar una
    // Fold o abrir una consola de doble panel, la interfaz de dos pantallas entra
    // sola. En un aparato normal devuelve tal cual lo que eligio el usuario.
    val foldLayout = SpeccyFoldableManager.rememberLayout()
    val currentTheme = SpeccyFoldableManager.recommendedThemeFor(
        foldLayout, settingsManager.currentTheme
    )
    var customMediaMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true }
    }

    val newAchievement by mainViewModel.achievementEngine.newlyUnlocked
        .collectAsStateWithLifecycle(initialValue = null)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAppResumed = true
                lastInteractionTime = System.currentTimeMillis()
                isAttractModeActive = false
                mainViewModel.refreshDashboardPacks() // Recarga CustomMediaMap
                if (settingsManager.isBackgroundMusicEnabled && !isAndroidSettingsVisible && !isAppViewActive) soundManager.resumeBgm()
            }
            else if (event == Lifecycle.Event.ON_PAUSE) {
                isAppResumed = false
                soundManager.pauseBgm()
                exoPlayer.pause()
            }
            else if (event == Lifecycle.Event.ON_STOP) {
                isAttractModeActive = false
                attractModeCurrentGame = null
                // El launcher ya no se ve (tipicamente porque el emulador acaba
                // de coger el foco). ON_PAUSE solo pausaba: el decodificador
                // seguia enganchado y le robaba CPU al juego. Se libera el
                // media item y se corta el sonido por completo.
                isAppResumed = false
                soundManager.pauseBgm()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    val currentLang = java.util.Locale.getDefault().language
    val displayPlatforms = remember(activePlatforms, recentGames, favoriteGames) {
        val list = activePlatforms.toList().sortedBy { sys ->
            val meta = SystemMetadataManager.getMetadataForSystem(context, sys, currentLang)
            meta.releaseYear.toIntOrNull() ?: 9999
        }.toMutableList()
        
        // Agregar favoritos y recientes al principio
        if (favoriteGames.isNotEmpty()) list.add(0, "favoritos")
        if (recentGames.isNotEmpty()) list.add(0, "recientes")
        
        // SIEMPRE AGREGAR ACCESOS DE SISTEMA PARA EVITAR PANTALLA NEGRA
        list.add("SYS_SETTINGS")
        list.add("SYS_APPS")
        list.add("SYS_TRIVIAL")
        list.add("SYS_MANUAL")
        list.add("SYS_BENCHMARK")
        list.add("SYS_WAR_ROOM")
        list.add("SYS_ROULETTE")
        list.add("SYS_DNA")
        
        list
    }

    LaunchedEffect(settingsManager.customDashboardPackName, isAppResumed) {
        val packName = settingsManager.customDashboardPackName
        if (packName != "default") {
            withContext(Dispatchers.IO) {
                val packDir = File(context.getExternalFilesDir(null), "dashboard_packs/$packName")
                if (packDir.exists()) {
                    val map = mutableMapOf<String, String>()
                    packDir.listFiles()?.forEach { file ->
                        map[file.name] = file.absolutePath
                    }
                    customMediaMap = map
                } else {
                    customMediaMap = emptyMap()
                }
            }
        } else {
            customMediaMap = emptyMap()
        }
    }

    // OJO con isAppResumed en las dos condiciones.
    // El temporizador vivia mientras la pantalla siguiera compuesta, y el
    // launcher sigue compuesto cuando pasa a segundo plano. Resultado: 30 s
    // despues de arrancar un juego el modo atracción se encendia DETRAS de
    // RetroArch, creaba otro ExoPlayer y se ponia a reproducir videos con
    // sonido mientras el usuario jugaba. Eso es lo que disparaba los ANR de
    // entrada del emulador. Sin foco no hay modo atracción, y si el bucle
    // esta corriendo cuando la app se va, se corta.
    LaunchedEffect(lastInteractionTime, isAppResumed) {
        if (!settingsManager.isAttractModeEnabled) return@LaunchedEffect
        if (!isAppResumed) return@LaunchedEffect
        while (isAppResumed) {
            delay(1000)
            if (isAppResumed && System.currentTimeMillis() - lastInteractionTime > 30000 && !isAttractModeActive && selectedPlatform == null && !isAndroidSettingsVisible && !isAppViewActive && !isSearchVisible && !isRouletteVisible && !isDnaVisible && displayPlatforms.isNotEmpty()) {
                isAttractModeActive = true
            }
        }
    }

    BackHandler(enabled = true) {
        lastInteractionTime = System.currentTimeMillis()
        if (isAttractModeActive) isAttractModeActive = false
        else if (isSearchVisible) isSearchVisible = false
        else if (isRouletteVisible) isRouletteVisible = false
        else if (isDnaVisible) isDnaVisible = false
        else if (isAndroidSettingsVisible) isAndroidSettingsVisible = false
        else if (isAppViewActive) isAppViewActive = false
        else if (selectedPlatform != null) {
            selectedPlatform = null
            soundManager.playBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - backPressedTime < 2000) {
                activity?.finish()
            } else {
                backPressedTime = now
                val msg = if (lang == "es") "Pulsa B de nuevo para salir de Speccy OS" else "Press B again to exit Speccy OS"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val primaryColor = ThemeManager.primaryColor
    val launcherManager = remember { LauncherManager(context) }
    val appManager = remember { AppLauncherManager(context) }

    val launchGameFunction: (Game, File?) -> Unit = { game, saveStateFile ->
        val settings = mainViewModel.settingsManager
        if (settings.canLaunchGame()) {
            soundManager.playLaunch()
            hapticHandler.playLaunchEffect()

            val profile = when (game.platformId.lowercase()) {
                "ps2", "gamecube", "gc", "wii", "3ds", "n3ds", "switch", "vita" -> "EXTREME"
                "psp", "dreamcast", "dc", "n64", "saturn" -> "PERFORMANCE"
                "psx", "ps1", "nds", "arcade", "mame", "fbneo", "neogeo" -> "BALANCED"
                else -> "ECO"
            }
            hardwareViewModel.setManualProfile(profile)
            mainViewModel.markGameAsPlayed(game)

            settings.isReturningFromGame = true
            // Se apunta el juego para poder SUBIR sus partidas al volver:
            // onResume detecta el regreso pero no sabe a que se jugo.
            settings.lastPlayedGamePath = game.path

            if (settings.isCloudSyncEnabled) {
                isSyncingCloud = true
                scope.launch {
                    val restored = mainViewModel.cloudSaveManager.restoreGameSaves(game)
                    isSyncingCloud = false
                    if (restored) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, Translator.t("cloud_restored", lang), Toast.LENGTH_SHORT).show()
                        }
                    }
                    withContext(Dispatchers.Main) { launcherManager.launchGame(game, saveStateFile) }
                }
            } else {
                launcherManager.launchGame(game, saveStateFile)
            }
        }
    }

    val isImperial = userLevel == UserLevel.IMPERIAL

    Box(modifier = Modifier.fillMaxSize()
        .onKeyEvent { event ->
            lastInteractionTime = System.currentTimeMillis()
            if (isAttractModeActive) {
                if (event.type == KeyEventType.KeyDown) {
                    val code = event.nativeKeyEvent.keyCode
                    if ((code == KeyEvent.KEYCODE_BUTTON_START || code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) && attractModeCurrentGame != null) {
                        isAttractModeActive = false
                        launchGameFunction(attractModeCurrentGame!!, null)
                    } else {
                        isAttractModeActive = false
                    }
                }
                return@onKeyEvent true
            }

            if (selectedPlatform == null && !isAndroidSettingsVisible && !isAppViewActive && !isSearchVisible && !isRouletteVisible && !isDnaVisible && displayPlatforms.isNotEmpty()) {
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (currentTheme == SettingsManager.THEME_SPECCY_DESKTOP) return@onKeyEvent false
                            mainCarouselIndex = if (mainCarouselIndex > 0) mainCarouselIndex - 1 else displayPlatforms.size - 1
                            soundManager.playClick()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (currentTheme == SettingsManager.THEME_SPECCY_DESKTOP) return@onKeyEvent false
                            mainCarouselIndex = if (mainCarouselIndex < displayPlatforms.size - 1) mainCarouselIndex + 1 else 0
                            soundManager.playClick()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (currentTheme != SettingsManager.THEME_SPECCY_DESKTOP) return@onKeyEvent false
                            mainCarouselIndex = if (mainCarouselIndex > 0) mainCarouselIndex - 1 else displayPlatforms.size - 1
                            soundManager.playClick()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (currentTheme != SettingsManager.THEME_SPECCY_DESKTOP) return@onKeyEvent false
                            mainCarouselIndex = if (mainCarouselIndex < displayPlatforms.size - 1) mainCarouselIndex + 1 else 0
                            soundManager.playClick()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            if (mainCarouselIndex >= 0 && mainCarouselIndex < displayPlatforms.size) {
                                onSystemSelected(displayPlatforms[mainCarouselIndex])
                            }
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_BUTTON_START -> {
                            onSettingsClick()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_BUTTON_SELECT -> {
                            isAndroidSettingsVisible = true
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            soundManager.playClick()
                            isSearchVisible = true
                            return@onKeyEvent true
                        }
                    }
                }
            }
            false
        }
    ) {
        // Overlay de Usuario
        if (selectedPlatform == null && !isAppViewActive && !isAndroidSettingsVisible && !isAttractModeActive) {
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(32.dp).clickable { onAchievementsClick() }, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = userName.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    if (isImperial) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null, tint = SpeccyPalette.imperial, modifier = Modifier.size(14.dp))
                            Text(text = "IMPERIAL", color = SpeccyPalette.imperial, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                AsyncImage(
                    model = userPhoto.ifEmpty { "file:///android_asset/contentimg/iconoGA.webp" },
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape).border(1.dp, if(isImperial) SpeccyPalette.imperial else MaterialTheme.colorScheme.outline, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (isRouletteVisible) {
            RouletteScreen(
                mainViewModel = mainViewModel,
                onBack = { isRouletteVisible = false; soundManager.playBack(); lastInteractionTime = System.currentTimeMillis() },
                onGameSelected = { game ->
                    isRouletteVisible = false
                    lastInteractionTime = System.currentTimeMillis()
                    launchGameFunction(game, null)
                }
            )
        } else if (isDnaVisible) {
            GamingDnaScreen(
                mainViewModel = mainViewModel,
                onBack = { isDnaVisible = false; soundManager.playBack(); lastInteractionTime = System.currentTimeMillis() }
            )
        } else if (isSearchVisible) {
            SmartSearchScreen(
                mainViewModel = mainViewModel,
                onBack = { isSearchVisible = false; soundManager.playBack(); lastInteractionTime = System.currentTimeMillis() },
                onGameSelected = { game ->
                    isSearchVisible = false
                    lastInteractionTime = System.currentTimeMillis()
                    launchGameFunction(game, null)
                }
            )
        } else if (isAndroidSettingsVisible) {
            AndroidSettingsView(onBack = { isAndroidSettingsVisible = false; lastInteractionTime = System.currentTimeMillis() })
        } else if (isAppViewActive) {
            AppLauncherManager(onBack = { isAppViewActive = false; lastInteractionTime = System.currentTimeMillis() }, lang = lang)
        } else if (isAttractModeActive) {
            AttractModeScreen(mainViewModel = mainViewModel, onExit = { isAttractModeActive = false; lastInteractionTime = System.currentTimeMillis() }, soundManager = soundManager, currentGame = attractModeCurrentGame, onGameChanged = { attractModeCurrentGame = it }, primaryColor = primaryColor)
        } else {
            AnimatedContent(
                targetState = selectedPlatform,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "ViewTransition"
            ) { currentPlatformId ->
                if (currentPlatformId == null && !isAppViewActive) {
                    // Si displayPlatforms está vacío, no debería pasar porque ahora agregamos SYS_ entries.
                    // Pero si por alguna razón lo está, mostramos un aviso.
                    if (displayPlatforms.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Buscando sistemas...", color = MaterialTheme.colorScheme.onSurface)
                        }
                        return@AnimatedContent
                    }

                    if (isPortrait || currentTheme == SettingsManager.THEME_DUAL_SCREEN) {
                        ThemeDualScreen(
                            platforms = displayPlatforms,
                            initialIndex = mainCarouselIndex,
                            customMediaMap = customMediaMap,
                            onFocused = {
                                mainCarouselIndex = displayPlatforms.indexOf(it).coerceAtLeast(0)
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onSelect = onSystemSelected,
                            primaryColor = primaryColor
                        )
                    } else {
                        when (currentTheme) {
                            SettingsManager.THEME_INMERSIVE -> InmersiveDashboardContent(
                                mainViewModel = mainViewModel,
                                platforms = displayPlatforms,
                                initialIndex = mainCarouselIndex,
                                lang = lang,
                                gameCounts = gameCounts,
                                hardwareState = hardwareState,
                                soundManager = soundManager,
                                userStatusManager = userStatusManager,
                                customMediaMap = customMediaMap,
                                onFocused = {
                                    mainCarouselIndex = displayPlatforms.indexOf(it).coerceAtLeast(0)
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onSelect = onSystemSelected,
                                onLaunchGame = { game: Game -> launchGameFunction(game, null) },
                                primaryColor = primaryColor
                            )
                            SettingsManager.THEME_SPECCY_DESKTOP -> SpeccyDesktopContent(
                                mainViewModel = mainViewModel,
                                platforms = displayPlatforms,
                                lang = lang,
                                hardwareState = hardwareState,
                                soundManager = soundManager,
                                userStatusManager = userStatusManager,
                                initialIndex = mainCarouselIndex,
                                customMediaMap = customMediaMap,
                                onIndexChanged = { index ->
                                    mainCarouselIndex = index
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onSelect = onSystemSelected,
                                onLaunchGame = { game: Game -> launchGameFunction(game, null) }
                            )
                            SettingsManager.THEME_ULTRA -> UltraDashboardContent(
                                mainViewModel = mainViewModel,
                                platforms = displayPlatforms,
                                initialIndex = mainCarouselIndex,
                                lang = lang,
                                hardwareState = hardwareState,
                                soundManager = soundManager,
                                customMediaMap = customMediaMap,
                                userStatusManager = userStatusManager,
                                onFocused = {
                                    mainCarouselIndex = displayPlatforms.indexOf(it).coerceAtLeast(0)
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onSelect = onSystemSelected,
                                onLaunchGame = { game: Game -> launchGameFunction(game, null) },
                                primaryColor = primaryColor
                            )
                            else -> ThemeClassicContent(
                                mainViewModel = mainViewModel,
                                userStatusManager = userStatusManager,
                                platforms = displayPlatforms,
                                initialIndex = mainCarouselIndex,
                                onIndexChanged = { index ->
                                    mainCarouselIndex = index
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onSelect = onSystemSelected,
                                onLaunchGame = { game: Game -> launchGameFunction(game, null) }
                            )
                        }
                    }
                } else if (currentPlatformId != null) {
                    val platformId = currentPlatformId
                    
                    var filteredGames by remember { mutableStateOf<List<Game>?>(null) }
                    
                    LaunchedEffect(platformId, lang) {
                        try {
                            mainViewModel.getGamesForPlatform(platformId).collect { gamesList ->
                                val listToFilter = if(platformId == "favoritos") favoriteGames else if(platformId == "recientes") recentGames else gamesList
                                filteredGames = withContext(Dispatchers.Default) {
                                    filterGamesByLanguage(listToFilter, lang)
                                }
                            }
                        } catch (e: CancellationException) {
                            // Ignorar cancelación normal de la corrutina
                        } catch (e: Exception) {
                            Log.e("SpeccyDashboard", "Error inesperado al filtrar juegos", e)
                            filteredGames = emptyList()
                        }
                    }

                    if (filteredGames != null) {
                        if (isPortrait || currentTheme == SettingsManager.THEME_DUAL_SCREEN) {
                            DualScreenGameList(
                                platformId = platformId,
                                games = filteredGames!!,
                                initialSelectedIndex = platformScrollPositions[platformId] ?: 0,
                                lang = lang,
                                settingsManager = settingsManager,
                                onBack = {
                                    selectedPlatform = null
                                    soundManager.playBack()
                                },
                                onGameClick = { game, forceSaveState ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    launchGameFunction(game, forceSaveState)
                                },
                                onToggleFavorite = { game ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    mainViewModel.toggleFavorite(game)
                                },
                                onSelectedIndexChanged = { index ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    platformScrollPositions[platformId] = index
                                    soundManager.playClick()
                                }
                            )
                        } else {
                            GameInmersiveUltraList(
                                platformId = platformId,
                                games = filteredGames!!,
                                lang = lang,
                                initialSelectedIndex = platformScrollPositions[platformId] ?: 0,
                                onBack = {
                                    selectedPlatform = null
                                    soundManager.playBack()
                                },
                                onGameClick = { game, forceSaveState ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    launchGameFunction(game, forceSaveState)
                                },
                                onToggleFavorite = { game ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    mainViewModel.toggleFavorite(game)
                                },
                                primaryColor = ThemeManager.primaryColor,
                                exoPlayer = exoPlayer,
                                settingsManager = settingsManager,
                                onSelectedIndexChanged = { index ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    platformScrollPositions[platformId] = index
                                    soundManager.playClick()
                                },
                                customMediaMap = customMediaMap
                            )
                        }
                    }
                }
            }
        }

        // Reto diario sobre el carrusel principal. La tarjeta es autosuficiente:
        // carga el reto del dia, se auto-oculta si hoy no hay ninguno o si el usuario
        // la cierra, y sube el resultado a Firestore con el uid anonimo que exigen
        // las reglas. Solo se muestra en la vista principal, nunca encima del
        // buscador, de los ajustes, del modo atraccion ni de una plataforma abierta.
        if (selectedPlatform == null && !isSearchVisible && !isAndroidSettingsVisible &&
            !isAppViewActive && !isAttractModeActive && !isRouletteVisible && !isDnaVisible
        ) {
            // Arriba a la derecha: la unica banda libre en los tres temas que
            // ofrece Ajustes. Centrado competia con el carrusel, que es lo que
            // el usuario viene a mirar.
            Box(
                modifier = Modifier.align(Alignment.TopEnd),
                contentAlignment = Alignment.TopEnd
            ) {
                DailyChallengeCard(
                    mainViewModel = mainViewModel,
                    userStatusManager = userStatusManager,
                    settingsManager = settingsManager,
                    onLaunchGame = { game ->
                        lastInteractionTime = System.currentTimeMillis()
                        launchGameFunction(game, null)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isSyncingCloud,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = Translator.t("cloud_syncing", lang).uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "NEBULA SYNC V2.0",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        newAchievement?.let { ach ->
            Box(Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
                AchievementUnlockToast(ach) { /* dismiss */ }
            }
        }
    }
}

private fun handleSystemSelection(
    id: String,
    onSettingsClick: () -> Unit,
    onArchitectClick: () -> Unit,
    onManualClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onTweakerClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onWarRoomClick: () -> Unit,
    onRouletteClick: () -> Unit,
    onDnaClick: () -> Unit,
    onAndroidSettingsClick: () -> Unit,
    onAppsClick: () -> Unit,
    onSystemSelect: () -> Unit,
    soundManager: SoundManager
) {
    soundManager.playClick()
    when (id) {
        "SYS_SETTINGS" -> onSettingsClick()
        "SYS_HARDWARE" -> onAndroidSettingsClick()
        "SYS_TRIVIAL" -> onArchitectClick()
        "SYS_MANUAL" -> onManualClick()
        "SYS_BENCHMARK" -> onBenchmarkClick()
        "SYS_TWEAKER" -> onTweakerClick()
        "SYS_APPS" -> onAppsClick()
        "SYS_ACHIEVEMENTS" -> onAchievementsClick()
        "SYS_WAR_ROOM" -> onWarRoomClick()
        "SYS_ROULETTE" -> onRouletteClick()
        "SYS_DNA" -> onDnaClick()
        else -> onSystemSelect()
    }
}

private fun filterGamesByLanguage(games: List<Game>, lang: String): List<Game> {
    if (games.isEmpty()) return games
    return games.filter { game ->
        val titleLow = game.title.lowercase()
        val descLow = game.description?.lowercase() ?: ""
        val isEs = titleLow.contains("(es") || titleLow.contains("(sp") || descLow.contains("español")
        val isFr = titleLow.contains("(fr") || descLow.contains("français")
        val isIt = titleLow.contains("(it") || descLow.contains("italiano")
        val isDe = titleLow.contains("(de") || descLow.contains("deutsch")

        when (lang) {
            "es" -> isEs || (!isFr && !isIt && !isDe)
            "fr" -> isFr || (!isEs && !isIt && !isDe)
            "it" -> isIt || (!isEs && !isFr && !isDe)
            "de" -> isDe || (!isEs && !isFr && !isIt)
            else -> true
        }
    }
}

@Composable
fun AndroidSettingsView(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(450); try { focusRequester.requestFocus() } catch (e: Exception) {} }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).focusRequester(focusRequester).focusable().onKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK || it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B)) {
            onBack()
            true
        } else false
    }) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(16.dp))
                Text("SISTEMA ANDROID", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }, modifier = Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("AJUSTES GLOBALES")
                }
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }, modifier = Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text("WI-FI")
                }
                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, modifier = Modifier.weight(1f).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Icon(Icons.Default.Bluetooth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("BLUETOOTH")
                }
            }
        }
    }
}

@Composable
fun AppLauncherManager(onBack: () -> Unit, lang: String) {
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AttractModeScreen(mainViewModel: MainViewModel, primaryColor: Color, soundManager: SoundManager, currentGame: Game?, onGameChanged: (Game?) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentVolume by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
        }
    }

    LaunchedEffect(currentGame) {
        if (currentGame != null) {
            currentVolume = 0f
            exoPlayer.volume = 0f
            for (i in 1..10) {
                delay(100)
                currentVolume = i / 10f
                exoPlayer.volume = currentVolume
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_STOP -> { exoPlayer.stop(); exoPlayer.clearMediaItems() }
                Lifecycle.Event.ON_RESUME -> {
                    if (currentGame != null) exoPlayer.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onGameChanged(null)
                }
            }
        }
        exoPlayer.addListener(playerListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(currentGame) {
        if (currentGame == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val randomGame = mainViewModel.getRandomGameWithVideo()
            if (randomGame?.videoPreview != null) {
                onGameChanged(randomGame)
                exoPlayer.setMediaItem(MediaItem.fromUri(randomGame.videoPreview!!.toUri()))
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                delay(5000)
                onGameChanged(null)
            }
        } else if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            exoPlayer.pause()
        }
    }

    val blinkAlpha by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable { onExit() }) {
        if (currentGame != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "file:///android_asset/contentimg/logos/${currentGame.platformId.lowercase()}.webp",
                        contentDescription = null,
                        modifier = Modifier.height(30.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = currentGame.title.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionIcon("A", primaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "JUGAR",
                            color = primaryColor.copy(alpha = blinkAlpha),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            fontFamily = ThemeManager.titleFont
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "O CUALQUIER BOTÓN PARA SALIR",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "SPECCY OS E5 ULTRA\nATTRACT MODE",
                    color = primaryColor.copy(alpha = blinkAlpha),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = ThemeManager.titleFont
                )
            }
        }
    }
}

@Composable
fun ActionIcon(buttonLabel: String, color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = buttonLabel, color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
