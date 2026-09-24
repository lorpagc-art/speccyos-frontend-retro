/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/**
 * 🎬 THEME: STUDIO
 * ----------------------------------------------------------------------------
 * El tema de las capturas de la ficha de Google Play (septiembre de 2026),
 * llevado a la app: carril de sistemas a la izquierda, cabecera con el nombre
 * del sistema, sus metadatos y las pildoras de estado (perfil, temperatura),
 * rejilla de caratulas con titulo, ano y valoracion, y barra inferior con los
 * botones del mando. Fondo casi negro con rejilla sutil, paneles grafito y un
 * unico acento: el color primario del usuario (cian por defecto).
 *
 * Es el primer tema que dibuja la biblioteca del sistema enfocado en la misma
 * pantalla, sin pasar por la lista de juegos: enfocar un sistema en el carril
 * ya muestra sus juegos a la derecha.
 *
 * NAVEGACION CON MANDO (un unico receptor de teclas, como en Ultra):
 *   - Carril: ARRIBA/ABAJO cambia de sistema. DERECHA o A pasa a la rejilla si
 *     el sistema tiene juegos; si es una entrada de sistema (SYS_*) A la abre.
 *   - Rejilla: cruceta mueve el foco; IZQUIERDA en la primera columna vuelve al
 *     carril; A lanza el juego. Atras lo gestiona el Dashboard, como siempre.
 */

internal val StudioBg = Color(0xFF0A0D12)
internal val StudioPanel = Color(0xFF10151C)
internal val StudioCard = Color(0xFF151B23)
internal val StudioLine = Color(0x14FFFFFF)
internal val StudioTextDim = Color(0xFF8A94A3)
internal val StudioGreen = Color(0xFF3DDC84)
internal val StudioRed = Color(0xFFFF3B5C)
internal val StudioAmber = Color(0xFFF5B73E)

// Cuatro columnas (antes cinco): el carril gana ancho y las caratulas, tamano.
private const val GRID_COLUMNS = 4

private enum class StudioZone { RAIL, GRID }

/** Abreviatura de tres o cuatro letras para el carril, como en las capturas. */
internal fun studioAbbrev(id: String): String {
    val low = id.lowercase()
    return when (low) {
        "recientes" -> "◷"
        "favoritos" -> "★"
        "nes", "famicom" -> "NES"
        "snes", "sfc" -> "SNES"
        "n64" -> "N64"
        "gc", "gamecube" -> "GC"
        "wii" -> "WII"
        "gb", "gameboy" -> "GB"
        "gbc", "gameboycolor" -> "GBC"
        "gba", "gameboyadvance" -> "GBA"
        "nds", "ds" -> "NDS"
        "3ds", "n3ds", "nintendo3ds" -> "3DS"
        "psx", "playstation", "ps1" -> "PSX"
        "ps2" -> "PS2"
        "psp" -> "PSP"
        "md", "megadrive", "genesis" -> "MD"
        "sms", "mastersystem" -> "SMS"
        "gg", "gamegear" -> "GG"
        "saturn" -> "SAT"
        "dc", "dreamcast" -> "DC"
        "segacd", "scd" -> "SCD"
        "32x" -> "32X"
        "arcade", "mame", "fbneo", "fba" -> "ARC"
        "cps1" -> "CPS1"
        "cps2" -> "CPS2"
        "cps3" -> "CPS3"
        "neogeo", "ng" -> "NEO"
        "ngp", "neogeopocket", "ngpc" -> "NGP"
        "pcengine", "tg16", "turbografx16" -> "PCE"
        "atari2600" -> "2600"
        "atari7800" -> "7800"
        "lynx", "atarilynx" -> "LYNX"
        "jaguar", "atarijaguar" -> "JAG"
        "amiga" -> "AMI"
        "c64", "commodore64" -> "C64"
        "zxspectrum", "spectrum", "zx" -> "ZX"
        "amstradcpc", "cpc" -> "CPC"
        "msx", "msx2" -> "MSX"
        "dos", "msdos" -> "DOS"
        "scummvm" -> "SCV"
        "wonderswan", "ws" -> "WS"
        "wonderswancolor", "wsc" -> "WSC"
        "3do" -> "3DO"
        "vectrex" -> "VEC"
        "colecovision", "coleco" -> "COL"
        "intellivision" -> "INTV"
        "naomi" -> "NAO"
        "atomiswave" -> "AW"
        else -> when {
            id.startsWith("SYS_") -> when (id) {
                "SYS_SETTINGS" -> "⚙"
                "SYS_APPS" -> "▦"
                "SYS_TRIVIAL" -> "?"
                "SYS_MANUAL" -> "📖"
                "SYS_BENCHMARK" -> "⏱"
                "SYS_WAR_ROOM" -> "⚔"
                "SYS_ROULETTE" -> "🎲"
                "SYS_DNA" -> "🧬"
                else -> id.removePrefix("SYS_").take(3)
            }
            else -> id.filter { it.isLetterOrDigit() }.take(4).uppercase()
        }
    }
}

/** Nombre completo para la cabecera. */
internal fun studioTitle(id: String, lang: String): String {
    val es = lang.startsWith("es")
    return when (id.lowercase()) {
        "recientes" -> if (es) "Recientes" else "Recent"
        "favoritos" -> if (es) "Favoritos" else "Favourites"
        "nes", "famicom" -> "Nintendo NES"
        "snes", "sfc" -> "Super Nintendo"
        "n64" -> "Nintendo 64"
        "gc", "gamecube" -> "GameCube"
        "gb", "gameboy" -> "Game Boy"
        "gbc", "gameboycolor" -> "Game Boy Color"
        "gba", "gameboyadvance" -> "Game Boy Advance"
        "nds", "ds" -> "Nintendo DS"
        "3ds", "n3ds", "nintendo3ds" -> "Nintendo 3DS"
        "psx", "playstation", "ps1" -> "PlayStation"
        "ps2" -> "PlayStation 2"
        "psp" -> "PSP"
        "md", "megadrive", "genesis" -> "Mega Drive"
        "sms", "mastersystem" -> "Master System"
        "gg", "gamegear" -> "Game Gear"
        "saturn" -> "Sega Saturn"
        "dc", "dreamcast" -> "Dreamcast"
        "arcade", "mame" -> "Arcade"
        "cps1" -> "Capcom CPS-1"
        "cps2" -> "Capcom CPS-2"
        "cps3" -> "Capcom CPS-3"
        "atari2600" -> "Atari 2600"
        "atari7800" -> "Atari 7800"
        "lynx", "atarilynx" -> "Atari Lynx"
        "jaguar", "atarijaguar" -> "Atari Jaguar"
        "neogeo", "ng" -> "Neo Geo"
        "ngp", "neogeopocket" -> "Neo Geo Pocket"
        "ngpc" -> "Neo Geo Pocket Color"
        "wonderswan", "ws" -> "WonderSwan"
        "wonderswancolor", "wsc" -> "WonderSwan Color"
        "naomi" -> "Sega Naomi"
        "atomiswave" -> "Atomiswave"
        "segacd", "scd" -> "Mega-CD"
        "32x" -> "Mega Drive 32X"
        "3do" -> "3DO"
        "vectrex" -> "Vectrex"
        "colecovision", "coleco" -> "ColecoVision"
        "intellivision" -> "Intellivision"
        "msx", "msx2" -> "MSX"
        "scummvm" -> "ScummVM"
        "wii" -> "Wii"
        "psp" -> "PSP"
        "fbneo", "fba" -> "FinalBurn Neo"
        "neogeo", "ng" -> "Neo Geo"
        "pcengine", "tg16", "turbografx16" -> "PC Engine"
        "amiga" -> "Amiga"
        "c64", "commodore64" -> "Commodore 64"
        "zxspectrum", "spectrum", "zx" -> "ZX Spectrum"
        "amstradcpc", "cpc" -> "Amstrad CPC"
        "dos", "msdos" -> "MS-DOS"
        else -> when (id) {
            "SYS_SETTINGS" -> if (es) "Ajustes" else "Settings"
            "SYS_APPS" -> if (es) "Aplicaciones" else "Apps"
            "SYS_TRIVIAL" -> "Trivial"
            "SYS_MANUAL" -> "Manual"
            "SYS_BENCHMARK" -> "Benchmark"
            "SYS_WAR_ROOM" -> "War Room"
            "SYS_ROULETTE" -> if (es) "Ruleta" else "Roulette"
            "SYS_DNA" -> "Gaming DNA"
            else -> id.replace("_", " ")
                .replace(Regex("(?<=[A-Za-z])(?=[0-9])"), " ")
                .replaceFirstChar { it.uppercase() }
        }
    }
}

internal fun studioSystemBlurb(id: String, lang: String): String {
    val es = lang.startsWith("es")
    return when (id) {
        "SYS_SETTINGS" -> if (es) "Interfaz, rendimiento, carpetas, scraper y cuenta." else "Interface, performance, folders, scraper and account."
        "SYS_APPS" -> if (es) "Tus aplicaciones Android, sin salir del launcher." else "Your Android apps, without leaving the launcher."
        "SYS_TRIVIAL" -> if (es) "Pon a prueba tu memoria retro." else "Test your retro memory."
        "SYS_MANUAL" -> if (es) "Documentación y ayuda del sistema." else "System manual and help."
        "SYS_BENCHMARK" -> if (es) "Mide la potencia real de tu máquina." else "Measure your device's real power."
        "SYS_WAR_ROOM" -> if (es) "Retos y competición." else "Challenges and competition."
        "SYS_ROULETTE" -> if (es) "Un juego al azar de tu colección." else "A random game from your collection."
        "SYS_DNA" -> if (es) "Tu perfil de jugador, en datos." else "Your player profile, in numbers."
        else -> ""
    }
}

/** Degradado de respaldo para juegos sin carátula: estable por título. */
private fun studioFallbackBrush(seed: String): Brush {
    val palettes = listOf(
        Color(0xFF0B2A6F) to Color(0xFF071A44),
        Color(0xFF7A1131) to Color(0xFF3C0817),
        Color(0xFF0E6B6B) to Color(0xFF063A3A),
        Color(0xFF8A4B00) to Color(0xFF3F2200),
        Color(0xFF3C1E7A) to Color(0xFF1B0D3A),
        Color(0xFF0E5A3A) to Color(0xFF06301F),
        Color(0xFF6B0E0E) to Color(0xFF330606)
    )
    val (a, b) = palettes[abs(seed.hashCode()) % palettes.size]
    return Brush.verticalGradient(listOf(a, b))
}

@UnstableApi
@Composable
fun StudioDashboardContent(
    mainViewModel: MainViewModel,
    platforms: List<String>,
    initialIndex: Int,
    lang: String,
    hardwareState: HardwareUiState,
    soundManager: SoundManager,
    gameCounts: Map<String, Int>,
    customMediaMap: Map<String, String>,
    onFocused: (String) -> Unit,
    onSelect: (String) -> Unit,
    onLaunchGame: (Game) -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    val es = lang.startsWith("es")
    val fontScale = mainViewModel.settingsManager.uiFontScale
    val cardMedia = mainViewModel.settingsManager.studioCardMedia

    // Un unico reproductor para toda la rejilla: solo la tarjeta enfocada lo usa.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    var railIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (platforms.size - 1).coerceAtLeast(0))) }
    var zone by remember { mutableStateOf(StudioZone.RAIL) }
    var gridIndex by remember { mutableIntStateOf(0) }

    val focusedId = platforms.getOrNull(railIndex) ?: ""
    val isSystemEntry = focusedId.startsWith("SYS_")

    // Fuente de juegos del sistema enfocado. Recientes y favoritos tienen su
    // propio flujo; las entradas SYS_ no tienen juegos.
    val gamesFlow: Flow<List<Game>> = remember(focusedId) {
        when {
            focusedId == "recientes" -> mainViewModel.recentGames
            focusedId == "favoritos" -> mainViewModel.favoriteGames
            isSystemEntry || focusedId.isEmpty() -> flowOf(emptyList())
            else -> mainViewModel.getGamesForPlatform(focusedId)
        }
    }
    val games by gamesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(railIndex) {
        platforms.getOrNull(railIndex)?.let(onFocused)
        gridIndex = 0
    }

    // Video de la tarjeta enfocada (solo en modo VIDEO): cambia de juego, cambia de video.
    val focusedVideo = if (zone == StudioZone.GRID && SettingsManager.studioMediaUsesVideo(cardMedia))
        games.getOrNull(gridIndex)?.videoPreview else null
    LaunchedEffect(focusedVideo) {
        exoPlayer.stop(); exoPlayer.clearMediaItems()
        if (focusedVideo != null) { exoPlayer.setMediaItem(MediaItem.fromUri(focusedVideo.toUri())); exoPlayer.prepare() }
    }

    val railState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState()
    // Tambien al cambiar la lista: "recientes" y "favoritos" se insertan en la
    // posicion 0 tras la primera composicion y el carril debe seguirlos.
    LaunchedEffect(railIndex, platforms) { if (railIndex in platforms.indices) railState.animateScrollToItem(railIndex) }
    LaunchedEffect(gridIndex, zone) { if (zone == StudioZone.GRID && games.isNotEmpty()) gridState.animateScrollToItem(gridIndex) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun moveRail(delta: Int) {
        if (platforms.isEmpty()) return
        railIndex = (railIndex + delta + platforms.size) % platforms.size
        soundManager.playClick()
    }

    fun enterGridOrOpen() {
        if (games.isNotEmpty()) { zone = StudioZone.GRID; gridIndex = 0; soundManager.playClick() }
        else onSelect(focusedId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    NativeKeyEvent.KEYCODE_DPAD_UP -> {
                        if (zone == StudioZone.RAIL) moveRail(-1)
                        else if (gridIndex >= GRID_COLUMNS) gridIndex -= GRID_COLUMNS
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (zone == StudioZone.RAIL) moveRail(1)
                        else if (gridIndex + GRID_COLUMNS < games.size) gridIndex += GRID_COLUMNS
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (zone == StudioZone.GRID) {
                            if (gridIndex % GRID_COLUMNS == 0) { zone = StudioZone.RAIL; soundManager.playBack() }
                            else gridIndex -= 1
                        }
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (zone == StudioZone.RAIL) enterGridOrOpen()
                        else if (gridIndex + 1 < games.size) gridIndex += 1
                        true
                    }
                    NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_BUTTON_A -> {
                        if (zone == StudioZone.RAIL) enterGridOrOpen()
                        else games.getOrNull(gridIndex)?.let(onLaunchGame)
                        true
                    }
                    NativeKeyEvent.KEYCODE_BUTTON_Y -> {
                        // Y abre la lista completa del sistema (filtros, orden, detalles).
                        if (!isSystemEntry) onSelect(focusedId); true
                    }
                    else -> false
                }
            }
    ) {
        StudioGridBackdrop()

        Row(Modifier.fillMaxSize()) {
            // ── Carril de sistemas ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(128.dp)
                    .background(StudioPanel)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = StudioBg, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    state = railState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(platforms, key = { _, id -> id }) { index, id ->
                        val selected = index == railIndex
                        val railFocused = selected && zone == StudioZone.RAIL
                        val chipColor by animateColorAsState(
                            if (selected) primaryColor.copy(alpha = 0.16f) else StudioCard, label = "chip"
                        )
                        val isSpecial = id.startsWith("SYS_") || id == "recientes" || id == "favoritos"
                        Box(
                            modifier = Modifier
                                .size(width = 112.dp, height = 64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(chipColor)
                                .border(
                                    width = if (railFocused) 3.dp else 1.dp,
                                    color = if (selected) primaryColor else StudioLine,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (railIndex == index) enterGridOrOpen()
                                    else { railIndex = index; zone = StudioZone.RAIL; soundManager.playClick() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val label = studioAbbrev(id)
                            val abbrev: @Composable () -> Unit = {
                                Text(
                                    text = label,
                                    color = if (selected) primaryColor else StudioTextDim,
                                    fontSize = (if (label.length > 3) 15.sp else 19.sp) * fontScale,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                            if (isSpecial) abbrev() else {
                                // Logotipo del sistema (assets/contentimg/logos, apaisados ~4:1).
                                // Si no existe para este id, cae a la abreviatura.
                                val logoPath = remember(id, customMediaMap) {
                                    ThemeManager.getThemeImagePath("logos", "${id.lowercase()}.webp", customMediaMap, context)
                                }
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context).data(logoPath).crossfade(true).build(),
                                    contentDescription = id,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)
                                ) {
                                    if (painter.state is AsyncImagePainter.State.Error) abbrev()
                                    else SubcomposeAsyncImageContent()
                                }
                            }
                        }
                    }
                }
            }

            // ── Panel principal ────────────────────────────────────────────
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                // Cabecera
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = studioTitle(focusedId, lang),
                        color = Color.White,
                        fontSize = 20.sp * fontScale,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(12.dp))
                    val count = if (isSystemEntry) null else (gameCounts[focusedId] ?: games.size)
                    val noCover = games.count { it.boxArt.isNullOrBlank() }
                    if (count != null) {
                        Text(
                            text = buildString {
                                append(count); append(if (es) " juegos" else " games")
                                if (noCover > 0) { append(" · "); append(noCover); append(if (es) " sin carátula" else " without cover") }
                            },
                            color = StudioTextDim,
                            fontSize = 11.sp * fontScale
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val profile = hardwareState.currentProfile.uppercase()
                    val profileColor = when (profile) {
                        "EXTREME", "EXTREMO", "MAX" -> StudioRed
                        "SAVER", "AHORRO", "ECO" -> StudioAmber
                        else -> primaryColor
                    }
                    StudioPill(text = "⚡ " + profileLabel(profile, es), color = profileColor, fontScale = fontScale)
                    Spacer(Modifier.width(8.dp))
                    if (hardwareState.temperature > 0f) {
                        StudioPill(text = "${hardwareState.temperature.toInt()} °C", color = StudioTextDim, fontScale = fontScale)
                        Spacer(Modifier.width(8.dp))
                    }
                    StudioPill(text = "${(hardwareState.batteryLevel * 100).toInt()} %", color = StudioGreen, fontScale = fontScale)
                }

                Spacer(Modifier.height(12.dp))

                // Contenido
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioLine, RoundedCornerShape(14.dp))
                ) {
                    when {
                        isSystemEntry -> StudioSystemPanel(
                            id = focusedId, lang = lang, primaryColor = primaryColor, fontScale = fontScale,
                            onOpen = { onSelect(focusedId) }
                        )
                        games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (es) "Sin juegos en este sistema todavía" else "No games in this system yet",
                                color = StudioTextDim, fontSize = 13.sp * fontScale
                            )
                        }
                        else -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(GRID_COLUMNS),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(games, key = { _, g -> g.path }) { index, game ->
                                val isFocusedCard = zone == StudioZone.GRID && index == gridIndex
                                StudioGameCard(
                                    game = game,
                                    focused = isFocusedCard,
                                    media = cardMedia,
                                    player = if (isFocusedCard && focusedVideo != null) exoPlayer else null,
                                    primaryColor = primaryColor,
                                    fontScale = fontScale,
                                    onClick = {
                                        if (zone == StudioZone.GRID && index == gridIndex) onLaunchGame(game)
                                        else { zone = StudioZone.GRID; gridIndex = index; soundManager.playClick() }
                                    }
                                )
                            }
                        }
                    }
                }

                // Barra inferior de botones
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hints = if (zone == StudioZone.GRID) listOf(
                        "A" to StudioGreen to (if (es) "Jugar" else "Play"),
                        "B" to StudioRed to (if (es) "Atrás" else "Back"),
                        "Y" to StudioAmber to (if (es) "Lista completa" else "Full list")
                    ) else listOf(
                        "A" to StudioGreen to (if (isSystemEntry) (if (es) "Abrir" else "Open") else (if (es) "Juegos" else "Games")),
                        "Y" to StudioAmber to (if (es) "Lista completa" else "Full list"),
                        "B" to StudioRed to (if (es) "Atrás" else "Back")
                    )
                    hints.forEach { (btn, label) ->
                        val (letter, color) = btn
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape).background(color),
                            contentAlignment = Alignment.Center
                        ) { Text(letter, color = StudioBg, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = StudioTextDim, fontSize = 10.sp * fontScale)
                        Spacer(Modifier.width(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("SPECCY OS", color = StudioTextDim.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun profileLabel(profile: String, es: Boolean): String = when (profile) {
    "EXTREME", "EXTREMO", "MAX" -> if (es) "EXTREMO" else "EXTREME"
    "PERFORMANCE", "RENDIMIENTO" -> if (es) "RENDIMIENTO" else "PERFORMANCE"
    "SAVER", "AHORRO", "ECO" -> if (es) "AHORRO" else "SAVER"
    "BALANCED", "EQUILIBRADO" -> if (es) "EQUILIBRADO" else "BALANCED"
    else -> profile
}

@Composable
private fun StudioPill(text: String, color: Color, fontScale: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp * fontScale, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@UnstableApi
@Composable
private fun StudioGameCard(
    game: Game,
    focused: Boolean,
    media: String,
    player: ExoPlayer?,
    primaryColor: Color,
    fontScale: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "cardScale")
    // CAPTURA prefiere la pantalla del juego; CARATULA y VIDEO (parado) prefieren la caja.
    val still = if (SettingsManager.studioMediaPrefersScreenshot(media))
        game.screenshot?.takeIf { it.isNotBlank() } ?: game.boxArt
    else
        game.boxArt?.takeIf { it.isNotBlank() } ?: game.screenshot
    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioCard)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) primaryColor else StudioLine,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .background(studioFallbackBrush(game.title))
        ) {
            if (!still.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(still).crossfade(true).build(),
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (player != null) {
                // Solo la tarjeta enfocada monta el reproductor; la imagen fija queda debajo
                // hasta que llega el primer fotograma.
                AndroidView(
                    factory = { ctx ->
                        // Inflado desde XML: surface_type=texture_view solo se puede fijar ahi.
                        (android.view.LayoutInflater.from(ctx).inflate(R.layout.studio_player_view, null) as PlayerView)
                            .apply { this.player = player }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Velo inferior para que el titulo se lea sobre cualquier caratula.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to StudioCard)
                )
            )
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = game.title.uppercase(),
                color = Color.White,
                fontSize = 11.sp * fontScale,
                fontWeight = FontWeight.Black,
                lineHeight = 13.sp * fontScale,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(game.year ?: "—", color = StudioTextDim, fontSize = 10.sp * fontScale)
                Spacer(Modifier.weight(1f))
                if (game.rating > 0f) {
                    // El scraper guarda la nota sobre 20; aqui se muestra sobre 5.
                    val sobre5 = (game.rating / 4f).coerceIn(0f, 5f)
                    Text("★ ${"%.1f".format(sobre5)}", color = StudioTextDim, fontSize = 10.sp * fontScale)
                }
            }
        }
    }
}

@Composable
private fun StudioSystemPanel(id: String, lang: String, primaryColor: Color, fontScale: Float, onOpen: () -> Unit) {
    val es = lang.startsWith("es")
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = studioAbbrev(id),
            color = primaryColor,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = studioTitle(id, lang).uppercase(),
            color = Color.White,
            fontSize = 26.sp * fontScale,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = studioSystemBlurb(id, lang),
            color = StudioTextDim,
            fontSize = 13.sp * fontScale
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(primaryColor)
                .clickable(onClick = onOpen)
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Text(if (es) "ABRIR" else "OPEN", color = StudioBg, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

/** Rejilla de fondo muy tenue, como en las capturas de la ficha. */
@Composable
private fun StudioGridBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 48.dp.toPx()
        val color = Color.White.copy(alpha = 0.025f)
        var x = 0f
        while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        // Halo de acento arriba a la izquierda, muy suave.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFF0E3A3F).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 0.6f
            )
        )
    }
}
