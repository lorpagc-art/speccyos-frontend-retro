/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

@UnstableApi
@Composable
fun GameInmersiveUltraList(
    platformId: String,
    games: List<Game>,
    lang: String,
    initialSelectedIndex: Int = 0,
    onBack: () -> Unit,
    onGameClick: (Game, File?) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    primaryColor: Color,
    exoPlayer: ExoPlayer,
    settingsManager: SettingsManager,
    onSelectedIndexChanged: (Int) -> Unit,
    customMediaMap: Map<String, String>
) {
    val context = LocalContext.current

    when (settingsManager.currentTheme) {
        SettingsManager.THEME_SPECCY_DESKTOP -> SpeccyAndroidGameList(platformId, games, initialSelectedIndex, onBack, onGameClick, onToggleFavorite, onSelectedIndexChanged, settingsManager)
        SettingsManager.THEME_DUAL_SCREEN -> DualScreenGameList(platformId, games, initialSelectedIndex, lang, onBack, onGameClick, onToggleFavorite, onSelectedIndexChanged, settingsManager)
        else -> StandardInmersiveGameList(platformId, games, initialSelectedIndex, lang, onBack, onGameClick, onToggleFavorite, onSelectedIndexChanged, settingsManager, customMediaMap)
    }
}

@UnstableApi
@Composable
fun DualScreenGameList(
    platformId: String,
    games: List<Game>,
    initialSelectedIndex: Int,
    lang: String,
    onBack: () -> Unit,
    onGameClick: (Game, File?) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onSelectedIndexChanged: (Int) -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, games.size.coerceAtLeast(1) - 1)) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val selectedGame = if (games.isNotEmpty()) games[selectedIndex.coerceIn(0, games.lastIndex)] else null
    val primaryColor = ThemeManager.primaryColor

    val fontScale = settingsManager.uiFontScale

    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true } }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                // ON_STOP: el juego ya tiene el foco. ON_PAUSE solo pausaba la
                // reproduccion, pero el decodificador de video seguia vivo y
                // SpeccyOS se quedaba al 49% de CPU con RetroArch delante. Aqui
                // se suelta el media item para liberar el codec de verdad.
                Lifecycle.Event.ON_STOP -> { exoPlayer.stop(); exoPlayer.clearMediaItems() }
                Lifecycle.Event.ON_RESUME -> if(settingsManager.libShowVideos) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exoPlayer.release() }
    }

    LaunchedEffect(selectedGame?.videoPreview, settingsManager.libShowVideos) {
        exoPlayer.stop(); exoPlayer.clearMediaItems()
        if (settingsManager.libShowVideos) {
            selectedGame?.videoPreview?.let { exoPlayer.setMediaItem(MediaItem.fromUri(it.toUri())); exoPlayer.prepare() }
        }
    }

    /**
     * FASE 1: Ambilight Dinámico
     */
    LaunchedEffect(selectedGame?.boxArt) {
        // Debounce. Con el D-pad mantenido el indice avanza hasta de 15 en 15
        // cada 100 ms, y sin esta espera cada paso intermedio lanzaba una
        // decodificacion de bitmap por software -allowHardware(false)- mas un
        // analisis de paleta que se tiraba a la basura al paso siguiente. Solo
        // se decodifica la caratula en la que el usuario se detiene.
        delay(200)
        if (selectedGame?.boxArt != null) {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(selectedGame.boxArt)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    ThemeManager.updateColorsFromBitmap(bitmap)
                }
            }
        } else {
            ThemeManager.adaptDNA(context, platformId)
        }
    }

    var scrollVelocity by remember { mutableIntStateOf(1) }
    var isHoldingDown by remember { mutableStateOf(false) }

    LaunchedEffect(isHoldingDown) {
        if (isHoldingDown) {
            scrollVelocity = 1; delay(500)
            while(isHoldingDown) { scrollVelocity = (scrollVelocity + 1).coerceAtMost(15); delay(100) }
        } else { scrollVelocity = 1 }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); try { focusRequester.requestFocus() } catch (_: Exception) {} }

    // El estilo CLARO de la vista de doble pantalla es una opcion del usuario,
    // no un descuido: por eso NO pasa por los roles del tema (que son oscuros).
    // Solo la rama CYBER se engancha al sistema de diseno.
    val isCyber = settingsManager.dualScreenStyle == SettingsManager.DUAL_STYLE_CYBER
    val bgColor = if (isCyber) MaterialTheme.colorScheme.background else Color(0xFFF0F0F5)
    val accentColor = if (isCyber) primaryColor else Color(0xFF007AFF)

    val isVertical = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    Box(modifier = Modifier.fillMaxSize().background(bgColor).focusRequester(focusRequester).focusable().onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    isHoldingDown = true
                    if (selectedIndex < games.size - 1) {
                        selectedIndex = (selectedIndex + scrollVelocity).coerceAtMost(games.size - 1)
                        onSelectedIndexChanged(selectedIndex)
                        scope.launch { listState.centerOnItem(selectedIndex) }
                    }
                    return@onPreviewKeyEvent true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingDown = true
                    if (selectedIndex > 0) {
                        selectedIndex = (selectedIndex - scrollVelocity).coerceAtLeast(0)
                        onSelectedIndexChanged(selectedIndex)
                        scope.launch { listState.centerOnItem(selectedIndex) }
                    }
                    return@onPreviewKeyEvent true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> { selectedGame?.let { exoPlayer.pause(); onGameClick(it, null) }; return@onPreviewKeyEvent true }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> { onBack(); return@onPreviewKeyEvent true }
                KeyEvent.KEYCODE_BUTTON_Y -> { selectedGame?.let { onToggleFavorite(it) }; return@onPreviewKeyEvent true }
                else -> return@onPreviewKeyEvent false
            }
        } else if (event.type == KeyEventType.KeyUp) {
            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                isHoldingDown = false; return@onPreviewKeyEvent true
            } else return@onPreviewKeyEvent false
        } else return@onPreviewKeyEvent false
    }) {
        if (isVertical) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp)) {
                    RenderLibrarySlot(settingsManager.uiSlotLeft, platformId, games, selectedIndex, selectedGame, listState, exoPlayer, isCyber, accentColor, settingsManager) {
                        selectedIndex = it; onSelectedIndexChanged(it)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(if(isCyber) accentColor.copy(alpha=0.5f) else Color.LightGray))
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
                    RenderLibrarySlot(settingsManager.uiSlotRight, platformId, games, selectedIndex, selectedGame, listState, exoPlayer, isCyber, accentColor, settingsManager) {
                        selectedIndex = it; onSelectedIndexChanged(it)
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 2.dp)) {
                    RenderLibrarySlot(settingsManager.uiSlotLeft, platformId, games, selectedIndex, selectedGame, listState, exoPlayer, isCyber, accentColor, settingsManager) {
                        selectedIndex = it; onSelectedIndexChanged(it)
                    }
                }
                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(if(isCyber) accentColor.copy(alpha=0.5f) else Color.LightGray))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp)) {
                    RenderLibrarySlot(settingsManager.uiSlotRight, platformId, games, selectedIndex, selectedGame, listState, exoPlayer, isCyber, accentColor, settingsManager) {
                        selectedIndex = it; onSelectedIndexChanged(it)
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun RenderLibrarySlot(
    slotType: String,
    platformId: String,
    games: List<Game>,
    selectedIndex: Int,
    selectedGame: Game?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    exoPlayer: ExoPlayer,
    isCyber: Boolean,
    accentColor: Color,
    settingsManager: SettingsManager,
    onIndexChanged: (Int) -> Unit
) {
    val surfaceColor = if (isCyber) MaterialTheme.colorScheme.surface else Color.White
    val textColor = if (isCyber) MaterialTheme.colorScheme.onSurface else Color.Black
    val fontScale = settingsManager.uiFontScale

    Surface(
        modifier = Modifier.fillMaxSize().padding(if (isCyber) 8.dp else 4.dp),
        color = surfaceColor,
        shape = RoundedCornerShape(16.dp),
        border = if (isCyber) BorderStroke(1.dp, accentColor.copy(alpha=0.5f)) else BorderStroke(1.dp, Color.LightGray)
    ) {
        when (slotType) {
            "VIDEO" -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                        AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT } }, modifier = Modifier.fillMaxSize())
                    } else if (settingsManager.libShowBoxArt) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(selectedGame?.screenshot ?: selectedGame?.boxArt)
                                .crossfade(false)
                                .build(),
                            contentDescription = null, 
                            modifier = Modifier.fillMaxSize().padding(16.dp), 
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("SIN SEÑAL DE VÍDEO", color = if (isCyber) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray, fontSize = 14.sp * fontScale)
                    }
                }
            }
            "LIST" -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
                    itemsIndexed(games, key = { _, game -> game.path }) { index, game ->
                        val isSelected = index == selectedIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(50.dp).clickable { onIndexChanged(index) },
                            color = if (isSelected) accentColor.copy(alpha = if (isCyber) 0.3f else 0.1f) else Color.Transparent
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Box(modifier = Modifier.size(8.dp).background(accentColor, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = game.title.uppercase(),
                                    color = if (isSelected) accentColor else if (isCyber) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray,
                                    fontSize = (if (isSelected) 14.sp else 12.sp) * fontScale,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            "INFO" -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file:///android_asset/contentimg/logos/${platformId.lowercase()}.png")
                            .crossfade(false)
                            .build(),
                        contentDescription = null, 
                        modifier = Modifier.height(40.dp).graphicsLayer { alpha = if(isCyber) 0.8f else 1f }, 
                        contentScale = ContentScale.Fit, 
                        colorFilter = if(isCyber) ColorFilter.tint(accentColor) else null
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(text = selectedGame?.title ?: "N/A", color = textColor, fontSize = 20.sp * fontScale, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    RatingStars(rating = selectedGame?.rating ?: 0f, accentColor)
                    Spacer(Modifier.height(16.dp))
                    Text(text = "DEVELOPER: ${selectedGame?.developer ?: "Unknown"}", color = if(isCyber) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray, fontSize = 12.sp * fontScale)
                    Text(text = "GENRE: ${selectedGame?.genre ?: "Retro"}", color = if(isCyber) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray, fontSize = 12.sp * fontScale)
                    Spacer(Modifier.height(16.dp))
                    val descScroll = rememberScrollState()
                    Box(modifier = Modifier.weight(1f).verticalScroll(descScroll)) {
                        Text(text = selectedGame?.description ?: "", color = if(isCyber) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black, fontSize = 12.sp * fontScale, lineHeight = (16f * fontScale).sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SpeccyAndroidGameList(
    platformId: String,
    games: List<Game>,
    initialSelectedIndex: Int,
    onBack: () -> Unit,
    onGameClick: (Game, File?) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onSelectedIndexChanged: (Int) -> Unit,
    settingsManager: SettingsManager
) {
    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, games.size.coerceAtLeast(1) - 1)) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val selectedGame = if (games.isNotEmpty()) games[selectedIndex.coerceIn(0, games.lastIndex)] else null
    val primaryColor = ThemeManager.primaryColor
    val context = LocalContext.current
    val fontScale = settingsManager.uiFontScale

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    /**
     * FASE 1: Ambilight Dinámico
     */
    LaunchedEffect(selectedGame?.boxArt) {
        // Debounce. Con el D-pad mantenido el indice avanza hasta de 15 en 15
        // cada 100 ms, y sin esta espera cada paso intermedio lanzaba una
        // decodificacion de bitmap por software -allowHardware(false)- mas un
        // analisis de paleta que se tiraba a la basura al paso siguiente. Solo
        // se decodifica la caratula en la que el usuario se detiene.
        delay(200)
        if (selectedGame?.boxArt != null) {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(selectedGame.boxArt)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    ThemeManager.updateColorsFromBitmap(bitmap)
                }
            }
        } else {
            ThemeManager.adaptDNA(context, platformId)
        }
    }

    var scrollVelocity by remember { mutableIntStateOf(1) }
    var isHoldingDown by remember { mutableStateOf(false) }

    LaunchedEffect(isHoldingDown) {
        if (isHoldingDown) {
            scrollVelocity = 1
            delay(500)
            while(isHoldingDown) {
                scrollVelocity = (scrollVelocity + 1).coerceAtMost(15)
                delay(100)
            }
        } else { scrollVelocity = 1 }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); try { focusRequester.requestFocus() } catch (_: Exception) {} }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).focusRequester(focusRequester).focusable().onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    isHoldingDown = true
                    if (selectedIndex < games.size - 1) { 
                        selectedIndex = (selectedIndex + scrollVelocity).coerceAtMost(games.size - 1)
                        onSelectedIndexChanged(selectedIndex)
                        scope.launch { listState.centerOnItem(selectedIndex) } 
                    }
                    true 
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingDown = true
                    if (selectedIndex > 0) { 
                        selectedIndex = (selectedIndex - scrollVelocity).coerceAtLeast(0)
                        onSelectedIndexChanged(selectedIndex)
                        scope.launch { listState.centerOnItem(selectedIndex) } 
                    }
                    true 
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> { selectedGame?.let { onGameClick(it, null) }; true }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> { onBack(); true }
                KeyEvent.KEYCODE_BUTTON_Y -> { selectedGame?.let { onToggleFavorite(it) }; true }
                else -> false
            }
        } else if (event.type == KeyEventType.KeyUp) {
            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                isHoldingDown = false
                true
            } else false
        } else false
    }) {
        if (settingsManager.libShowFanart) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(selectedGame?.fanart ?: selectedGame?.boxArt)
                    .crossfade(false)
                    .build(),
                contentDescription = null, 
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.15f }.blur(10.dp), 
                contentScale = ContentScale.Crop
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(0.48f).fillMaxHeight().padding(start = 50.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/contentimg/logos/${platformId.lowercase()}.webp") // Cambiado a .webp para consistencia
                        .crossfade(false)
                        .build(),
                    contentDescription = null, 
                    modifier = Modifier.height(50.dp).graphicsLayer { alpha = 0.9f }, 
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(32.dp))

                if (settingsManager.libShowBoxArt) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectedGame?.cdArt != null && settingsManager.libShowCdArt) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(selectedGame.cdArt).crossfade(false).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).offset(x = 60.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(0.75f).shadow(if(selectedGame != null) 40.dp else 0.dp, RoundedCornerShape(28.dp), spotColor = primaryColor).border(if(selectedGame != null) 2.5.dp else 0.dp, primaryColor.copy(alpha = pulseAlpha), RoundedCornerShape(28.dp)),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(selectedGame?.boxArt)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null, 
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).padding(8.dp), 
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(text = selectedGame?.title?.uppercase() ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp * fontScale, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (26f * fontScale).sp)
                Spacer(Modifier.height(12.dp))
                RatingStars(rating = selectedGame?.rating ?: 0f, primaryColor)
            }

            Column(modifier = Modifier.weight(0.52f).fillMaxHeight().background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))) {
                Surface(modifier = Modifier.fillMaxWidth().height(60.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "LISTADO DE PROTOCOLOS", color = primaryColor, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(text = "${games.size} REGISTROS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp)) {
                    itemsIndexed(games, key = { _, game -> game.path }) { index, game ->
                        val isSelected = index == selectedIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(70.dp).pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        selectedIndex = index; onSelectedIndexChanged(index)
                                    },
                                    onDoubleTap = {
                                        selectedIndex = index; onSelectedIndexChanged(index)
                                        onGameClick(game, null)
                                    },
                                    onLongPress = {
                                        selectedIndex = index; onSelectedIndexChanged(index)
                                        onToggleFavorite(game)
                                    }
                                )
                            },
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, primaryColor.copy(alpha = pulseAlpha)) else null
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (settingsManager.libShowBoxArt) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(game.boxArt)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = null, 
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, if(isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape).padding(4.dp), 
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = game.title.uppercase(), color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp * fontScale, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = (game.developer ?: "Protocolo Retro").uppercase(), color = if (isSelected) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                                }
                                if (isSelected) Icon(Icons.Default.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun StandardInmersiveGameList(
    platformId: String,
    games: List<Game>,
    initialSelectedIndex: Int,
    lang: String,
    onBack: () -> Unit,
    onGameClick: (Game, File?) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onSelectedIndexChanged: (Int) -> Unit,
    settingsManager: SettingsManager,
    customMediaMap: Map<String, String>
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope         = rememberCoroutineScope()
    val fontScale = settingsManager.uiFontScale

    var selectedIndex by remember {
        mutableIntStateOf(initialSelectedIndex.coerceIn(0, games.size.coerceAtLeast(1) - 1))
    }
    val listState   = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedGame = games.getOrNull(selectedIndex.coerceIn(0, games.lastIndex))

    val primaryColor = ThemeManager.primaryColor
    var scrollVelocity by remember { mutableIntStateOf(1) }
    var isHoldingDown  by remember { mutableStateOf(false) }
    var isMediaFullscreen by remember { mutableStateOf(false) }
    var isDescFullscreen  by remember { mutableStateOf(false) }
    var showTimeMachine   by remember { mutableStateOf(false) }
    var saveStatesList    by remember { mutableStateOf<List<CloudSaveManager.SaveState>>(emptyList()) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = true
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> exoPlayer.pause()
                // ON_STOP: el juego ya tiene el foco. ON_PAUSE solo pausaba la
                // reproduccion, pero el decodificador de video seguia vivo y
                // SpeccyOS se quedaba al 49% de CPU con RetroArch delante. Aqui
                // se suelta el media item para liberar el codec de verdad.
                Lifecycle.Event.ON_STOP   -> { exoPlayer.stop(); exoPlayer.clearMediaItems() }
                Lifecycle.Event.ON_RESUME -> if (settingsManager.libShowVideos && !isMediaFullscreen) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); exoPlayer.release() }
    }
    LaunchedEffect(selectedGame?.videoPreview, settingsManager.libShowVideos, isMediaFullscreen) {
        if (!isMediaFullscreen) {
            exoPlayer.stop(); exoPlayer.clearMediaItems()
            if (settingsManager.libShowVideos) {
                val vid = selectedGame?.videoPreview
                if (vid != null) { exoPlayer.setMediaItem(MediaItem.fromUri(vid.toUri())); exoPlayer.prepare() }
            }
        }
    }
    // Ambilight dinámico
    LaunchedEffect(selectedGame?.boxArt) {
        // Debounce: ver el mismo efecto mas arriba. Sin el, cada paso del scroll
        // acelerado decodifica una caratula que se descarta al paso siguiente.
        delay(200)
        if (selectedGame?.boxArt != null) {
            val loader  = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context).data(selectedGame.boxArt).allowHardware(false).build()
            val result  = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap?.let { ThemeManager.updateColorsFromBitmap(it) }
            }
        } else {
            ThemeManager.adaptDNA(context, platformId)
        }
    }
    LaunchedEffect(isHoldingDown) {
        if (isHoldingDown) {
            scrollVelocity = 1; delay(500)
            while (isHoldingDown) { scrollVelocity = (scrollVelocity + 1).coerceAtMost(15); delay(100) }
        } else { scrollVelocity = 1 }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isMediaFullscreen, isDescFullscreen, showTimeMachine) {
        if (!isMediaFullscreen && !isDescFullscreen && !showTimeMachine) {
            delay(100); try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    val heroGlow by rememberInfiniteTransition(label = "hg").animateFloat(
        0.35f, 0.65f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "hg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize().background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            isHoldingDown = true
                            if (selectedIndex < games.size - 1) {
                                selectedIndex = (selectedIndex + scrollVelocity).coerceAtMost(games.size - 1)
                                onSelectedIndexChanged(selectedIndex)
                                scope.launch { listState.centerOnItem(selectedIndex) }
                            }; true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            isHoldingDown = true
                            if (selectedIndex > 0) {
                                selectedIndex = (selectedIndex - scrollVelocity).coerceAtLeast(0)
                                onSelectedIndexChanged(selectedIndex)
                                scope.launch { listState.centerOnItem(selectedIndex) }
                            }; true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) isMediaFullscreen = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { isDescFullscreen = true; true }
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            selectedGame?.let { exoPlayer.pause(); onGameClick(it, null) }; true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> { onBack(); true }
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            selectedGame?.let { g ->
                                scope.launch(Dispatchers.IO) {
                                    saveStatesList = TimeMachineManager.getAvailableSaveStates(context, g)
                                    if (saveStatesList.isNotEmpty()) showTimeMachine = true
                                    else {
                                        // Distinguir "no hay partidas" de "no puedo
                                        // verlas": sin acceso a todos los archivos,
                                        // /sdcard/RetroArch es ilegible en Android 11+
                                        // y decir "no hay partidas" es mentira.
                                        val puedeLeer = CloudSaveManager(context).puedeLeerPartidas()
                                        val aviso = if (puedeLeer) "no_saves_found" else "saves_no_permission"
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, Translator.t(aviso, lang), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }; true
                        }
                        KeyEvent.KEYCODE_BUTTON_X -> { selectedGame?.let { onToggleFavorite(it) }; true }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    val k = event.nativeKeyEvent.keyCode
                    if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_UP) { isHoldingDown = false; true } else false
                } else false
            }
    ) {
        // Fanart de fondo difuminado
        if (settingsManager.libShowFanart) {
            val fanartPath = selectedGame?.fanart
                ?: ThemeManager.getThemeImagePath("cyberpunk", "${platformId}_16x9_fanart.webp", customMediaMap, context)
            AsyncImage(
                model = ImageRequest.Builder(context).data(fanartPath).crossfade(false).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f }.blur(22.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(Modifier.fillMaxSize()) {
            // ── TOP BAR ──
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind { drawLine(primaryColor.copy(.18f), Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Touch.min)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ThemeManager.getThemeImagePath("logos", "${platformId.lowercase()}.webp", customMediaMap, context))
                        .crossfade(false).build(),
                    contentDescription = null,
                    modifier = Modifier.height(26.dp), contentScale = ContentScale.Fit
                )
                Spacer(Modifier.weight(1f))
                Text("${selectedIndex + 1} / ${games.size}", color = primaryColor.copy(.6f),
                    fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, fontFamily = ThemeManager.titleFont)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("▲▼" to "nav", "A" to "jugar", "X" to "fav", "Y" to "saves").forEach { (k, l) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(k, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(3.dp))
                            Text(l, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f), fontSize = 12.sp * fontScale)
                        }
                    }
                }
            }

            // ── 3 COLUMNAS PERSONALIZABLES ──
            val ListColumn = @Composable {
                Surface(
                    modifier = Modifier.weight(0.28f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(.15f))
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)) {
                        // Sin `key`, cualquier filtrado o reordenación descarta y
                        // recrea todos los slots visibles y pierde el scroll.
                        itemsIndexed(games, key = { _, g -> g.path }) { index, game ->
                            val isSel = index == selectedIndex
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (isSel) primaryColor.copy(.12f) else Color.Transparent)
                                    .drawBehind { if (isSel) drawLine(primaryColor, Offset(0f,0f), Offset(0f,size.height), 3f) }
                                    .clickable { selectedIndex = index; onSelectedIndexChanged(index) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (settingsManager.libShowBoxArt && game.boxArt != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(game.boxArt).crossfade(false).build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(3.dp))
                                            .border(if (isSel) BorderStroke(1.dp, primaryColor.copy(.5f)) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(3.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(3.dp))
                                        .background(if (isSel) primaryColor.copy(.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                        Alignment.Center) {
                                        Text("${index+1}", fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black,
                                            color = if (isSel) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
                                    }
                                }
                                Text(game.title,
                                    color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(.75f),
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Normal,
                                    fontSize = (if (isSel) 12.sp else 12.sp) * fontScale,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (game.isFavorite) Icon(Icons.Default.Favorite, null,
                                    tint = primaryColor.copy(if (isSel) 1f else .5f), modifier = Modifier.size(10.dp))
                            }
                            if (index < games.size - 1)
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.04f), thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 10.dp))
                        }
                    }
                }
            }

            val HeroColumn = @Composable {
                Column(
                    modifier = Modifier.weight(0.44f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .drawBehind {
                                val gc = primaryColor.copy(heroGlow)
                                val cr = 20.dp.toPx()
                                drawRoundRect(gc, cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                                val c = 18.dp.toPx()
                                listOf(Offset(0f,0f), Offset(size.width,0f), Offset(0f,size.height), Offset(size.width,size.height))
                                    .forEachIndexed { i, pt ->
                                        val xS = if (i%2==0) 1f else -1f; val yS = if (i<2) 1f else -1f
                                        drawLine(primaryColor, pt, Offset(pt.x+xS*c, pt.y), 2.5f)
                                        drawLine(primaryColor, pt, Offset(pt.x, pt.y+yS*c), 2.5f)
                                    }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val mediaSlot = settingsManager.libCentralMediaSlot
                        var rendered = false

                        @Composable
                        fun VideoPlayerBlock() {
                            AndroidView(
                                factory = { ctx -> PlayerView(ctx).apply {
                                    player = exoPlayer; useController = false
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }},
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(10.dp))
                                    .pointerInput(Unit) { detectTapGestures { isMediaFullscreen = true } }
                            )
                        }

                        @Composable
                        fun BoxArtBlock() {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(selectedGame?.boxArt).crossfade(true).build(),
                                contentDescription = selectedGame?.title,
                                modifier = Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures { isMediaFullscreen = true } },
                                contentScale = ContentScale.Fit
                            )
                        }

                        @Composable
                        fun ScreenshotBlock() {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(selectedGame?.screenshot).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures { isMediaFullscreen = true } },
                                contentScale = ContentScale.Fit
                            )
                        }

                        @Composable
                        fun CdArtBlock() {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(selectedGame?.cdArt).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures { isMediaFullscreen = true } },
                                contentScale = ContentScale.Fit
                            )
                        }

                        @Composable
                        fun FanartBlock() {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(selectedGame?.fanart).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures { isMediaFullscreen = true } },
                                contentScale = ContentScale.Fit
                            )
                        }

                        when (mediaSlot) {
                            "VIDEO" -> {
                                if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                                    VideoPlayerBlock()
                                    rendered = true
                                }
                            }
                            "BOXART" -> {
                                if (settingsManager.libShowBoxArt && selectedGame?.boxArt != null) {
                                    BoxArtBlock()
                                    rendered = true
                                }
                            }
                            "SCREENSHOT" -> {
                                if (settingsManager.libShowScreenshot && selectedGame?.screenshot != null) {
                                    ScreenshotBlock()
                                    rendered = true
                                }
                            }
                            "CD_ART" -> {
                                if (settingsManager.libShowCdArt && selectedGame?.cdArt != null) {
                                    CdArtBlock()
                                    rendered = true
                                }
                            }
                            "FANART" -> {
                                if (settingsManager.libShowFanart && selectedGame?.fanart != null) {
                                    FanartBlock()
                                    rendered = true
                                }
                            }
                        }

                        if (!rendered) {
                            if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                                VideoPlayerBlock()
                            } else if (settingsManager.libShowBoxArt && selectedGame?.boxArt != null) {
                                BoxArtBlock()
                            } else if (settingsManager.libShowScreenshot && selectedGame?.screenshot != null) {
                                ScreenshotBlock()
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("SIN PREVIEW", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Box(Modifier.fillMaxSize().padding(10.dp), Alignment.TopEnd) {
                            Icon(Icons.Default.Fullscreen, null, tint = primaryColor.copy(.6f), modifier = Modifier.size(20.dp))
                        }
                    }

                    Text(selectedGame?.title ?: "───", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black,
                        fontSize = 15.sp * fontScale, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    selectedGame?.let { g -> RatingStars(rating = g.rating, color = primaryColor) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedGame?.let { exoPlayer.pause(); onGameClick(it, null) } },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("JUGAR", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp * fontScale)
                        }
                        OutlinedButton(
                            onClick = { selectedGame?.let { onToggleFavorite(it) } },
                            modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(.5f)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                if (selectedGame?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null, tint = if (selectedGame?.isFavorite == true) primaryColor else primaryColor.copy(.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            val InfoColumn = @Composable {
                if (settingsManager.libShowDescription) {
                    Surface(
                        modifier = Modifier.weight(0.28f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(.15f))
                    ) {
                        val descScroll = rememberScrollState()
                        LaunchedEffect(selectedGame) {
                            descScroll.scrollTo(0); delay(3500)
                            while (descScroll.maxValue > 0) {
                                descScroll.animateScrollTo(descScroll.maxValue, tween(descScroll.maxValue * 35, easing = LinearEasing))
                                delay(2500); descScroll.scrollTo(0); delay(2000)
                            }
                        }
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("// INFO", color = primaryColor.copy(.5f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            if (selectedGame == null) {
                                Text("Selecciona un juego", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale)
                            } else {
                                val anio = selectedGame.releaseDate
                                if (!anio.isNullOrBlank() && anio != "N/A") {
                                    InfoDataRow("AÑO", anio, primaryColor, fontScale)
                                }
                                val genero = selectedGame.genre
                                if (!genero.isNullOrBlank() && genero != "Retro") {
                                    InfoDataRow("GÉNERO", genero, primaryColor, fontScale)
                                }
                                val desarrollador = selectedGame.developer
                                if (!desarrollador.isNullOrBlank() && desarrollador != "Desconocido") {
                                    InfoDataRow("DEV", desarrollador, primaryColor, fontScale)
                                }
                                if (selectedGame.playCount > 0) {
                                    InfoDataRow("PARTIDAS", selectedGame.playCount.toString(), primaryColor, fontScale)
                                }
                                if (selectedGame.playTimeSeconds > 0) {
                                    InfoDataRow("TIEMPO", selectedGame.formattedPlayTime, primaryColor, fontScale)
                                }
                                if (selectedGame.rating > 0f) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("RATING", color = primaryColor.copy(.6f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                        RatingStars(rating = selectedGame.rating, color = primaryColor)
                                    }
                                }
                                if (!selectedGame.description.isNullOrBlank()) {
                                    HorizontalDivider(color = primaryColor.copy(.12f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                                    Text("DESC", color = primaryColor.copy(.5f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                    Text(selectedGame.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.8f),
                                        fontSize = 12.sp * fontScale, lineHeight = (15f * fontScale).sp, modifier = Modifier.verticalScroll(descScroll))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (settingsManager.libImmersiveLayoutOrder) {
                    "INFO_HERO_LIST" -> {
                        InfoColumn()
                        HeroColumn()
                        ListColumn()
                    }
                    "LIST_INFO_HERO" -> {
                        ListColumn()
                        InfoColumn()
                        HeroColumn()
                    }
                    else -> { // "LIST_HERO_INFO"
                        ListColumn()
                        HeroColumn()
                        InfoColumn()
                    }
                }
            }
        }

        // Time Machine overlay
        if (showTimeMachine && selectedGame != null) {
            TimeMachineDialog(
                game        = selectedGame,
                saveStates  = saveStatesList,
                onDismiss   = { showTimeMachine = false; try { focusRequester.requestFocus() } catch (_: Exception) {} },
                onLoadState = { file -> showTimeMachine = false; onGameClick(selectedGame, file) },
                lang        = settingsManager.appLanguage,
                fontScale   = fontScale
            )
        }

        // Pantalla completa media
        if (isMediaFullscreen) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable { isMediaFullscreen = false },
                Alignment.Center) {
                if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                        player = exoPlayer; useController = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }}, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(model = selectedGame?.boxArt, contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
                }
                Box(Modifier.fillMaxSize().padding(16.dp), Alignment.TopEnd) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(.7f), modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoDataRow(label: String, value: String, primaryColor: Color, fontScale: Float) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // "PARTIDAS" es la etiqueta mas larga y con letterSpacing 2.sp no cabia
        // en el 38 %: se partia en dos lineas y se leia "PARTID AS". Con menos
        // espaciado, algo mas de ancho y una sola linea, entra entera.
        Text(label, color = primaryColor.copy(.6f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black,
            letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.46f))
        Text(value, color = MaterialTheme.colorScheme.onSurface.copy(.85f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.54f))
    }
}


@Composable
fun TimeMachineDialog(
    game: Game,
    saveStates: List<CloudSaveManager.SaveState>,
    onDismiss: () -> Unit,
    onLoadState: (File) -> Unit,
    lang: String,
    fontScale: Float = 1.0f
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val primaryColor = ThemeManager.primaryColor

    LaunchedEffect(Unit) { delay(100); try { focusRequester.requestFocus() } catch (_: Exception) {} }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 500.dp).focusRequester(focusRequester).focusable().onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedIndex > 0) selectedIndex--
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedIndex < saveStates.size - 1) selectedIndex++
                            true
                        }
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            if (saveStates.isNotEmpty()) onLoadState(saveStates[selectedIndex].file)
                            true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, primaryColor)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = "TIME MACHINE", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp * fontScale, fontWeight = FontWeight.Black)
                        Text(text = game.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(24.dp))

                if (saveStates.isEmpty()) {
                    Text(text = Translator.t("no_saves_found", lang), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp * fontScale, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(saveStates) { index, state ->
                            val isSelected = index == selectedIndex
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onLoadState(state.file) },
                                color = if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(1.dp, primaryColor) else null
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Con SAF la miniatura llega como uri: la
                                    // ruta directa no se puede abrir en Android 11+.
                                    val miniatura: Any? = state.imageUri
                                        ?: state.imageFile?.takeIf { it.exists() }
                                    if (miniatura != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(miniatura)
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(60.dp, 45.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Column {
                                        Text(text = "SAVE STATE", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp * fontScale, fontWeight = FontWeight.Bold)
                                        Text(text = TimeMachineManager.findLatestSaveState(LocalContext.current, game).second, color = primaryColor, fontSize = 12.sp * fontScale)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(text = "A: CARGAR   B: CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun RatingStars(rating: Float, color: Color) {
    Row {
        val fullStars = (rating / 2).toInt()
        val hasHalf = (rating % 2) >= 1
        for (i in 0 until 5) {
            when {
                i < fullStars -> Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(16.dp))
                i == fullStars && hasHalf -> Icon(Icons.Default.StarHalf, null, tint = color, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Default.StarOutline, null, tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}