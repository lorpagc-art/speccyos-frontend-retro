package com.generacionarcade.speccyos

import android.app.Activity
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * 📱 THEME: DUAL SCREEN EVOLUTION (V0.7.0-Focus)
 * Enhanced for Non-Touch devices and Dual Screen Library View.
 * Added support for custom Pastel Colors in PURE_NDS mode.
 * Fixed Focus states, Touch Interactions, and Display calculations.
 */

enum class DualScreenStyle {
    PURE_NDS,
    CYBERPUNK_FLIP,
    /** Mismo lenguaje visual que el tema Studio: fondo oscuro sobrio y esquinas suaves. */
    STUDIO
}

/**
 * Traduce la preferencia guardada a un estilo. Antes esta conversion estaba
 * copiada en tres sitios con un `if` de dos ramas, asi que cualquier estilo
 * nuevo aparecia solo en una parte de la pantalla.
 */
fun dualStyleFromPref(pref: String): DualScreenStyle = when (pref) {
    SettingsManager.DUAL_STYLE_CYBER -> DualScreenStyle.CYBERPUNK_FLIP
    SettingsManager.DUAL_STYLE_STUDIO -> DualScreenStyle.STUDIO
    else -> DualScreenStyle.PURE_NDS
}

data class SystemMetadata(
    val name: String = "DESCONOCIDO",
    val manufacturer: String = "DESCONOCIDO",
    val year: String = "----",
    val description: String = "No hay información disponible para este sistema."
)

private val metadataCache = mutableMapOf<String, SystemMetadata>()

fun Color.toPastel(): Color {
    val factor = 0.85f
    val r = this.red * (1 - factor) + 1f * factor
    val g = this.green * (1 - factor) + 1f * factor
    val b = this.blue * (1 - factor) + 1f * factor
    return Color(r, g, b, 1f)
}

private val convexBrush = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.15f)),
    start = Offset(0f, 0f), end = Offset(1000f, 1000f)
)

private val concaveBrush = Brush.linearGradient(
    colors = listOf(Color.Black.copy(alpha = 0.3f), Color.White.copy(alpha = 0.8f)),
    start = Offset(0f, 0f), end = Offset(1000f, 1000f)
)

@OptIn(UnstableApi::class)
@Composable
fun ThemeDualScreen(
    platforms: List<String>,
    initialIndex: Int,
    customMediaMap: Map<String, String>,
    onFocused: (String) -> Unit,
    onSelect: (String) -> Unit,
    primaryColor: Color
) {
    val configuration = LocalConfiguration.current
    val isVertical = remember(configuration.orientation) {
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT 
    }
    
    val context = LocalContext.current
    val activity = context as? Activity
    val settingsManager = remember { SettingsManager(context) }
    val view = LocalView.current
    val density = LocalDensity.current

    val currentStyle by remember {
        derivedStateOf { dualStyleFromPref(settingsManager.dualScreenStyle) }
    }
    var selectedPlatformIndex by remember { mutableIntStateOf(initialIndex) }
    var foldingFeature by remember { mutableStateOf<FoldingFeature?>(null) }

    LaunchedEffect(activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { layoutInfo ->
                foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
        }
    }

    LaunchedEffect(selectedPlatformIndex) {
        if (platforms.isNotEmpty() && selectedPlatformIndex in platforms.indices) {
            onFocused(platforms[selectedPlatformIndex])
        }
    }

    // Los roles se leen FUERA del remember: el lambda de remember{} no es un
    // contexto @Composable, asi que MaterialTheme no existe dentro.
    val rolFondo    = MaterialTheme.colorScheme.background
    val rolSuperfic = MaterialTheme.colorScheme.surface
    val rolTexto    = MaterialTheme.colorScheme.onSurface

    val basePlasticColor = remember(currentStyle, primaryColor, settingsManager.ndsBodyColor, rolFondo) {
        if (currentStyle == DualScreenStyle.PURE_NDS) {
            if (settingsManager.ndsBodyColor == 0xFFE0E0E0.toInt()) primaryColor.toPastel()
            else Color(settingsManager.ndsBodyColor)
        } else rolFondo
    }

    val surfaceColor = remember(currentStyle, rolSuperfic) {
        if (currentStyle == DualScreenStyle.PURE_NDS) Color(0xFFF0F4F8) else rolSuperfic
    }
    val textColor = remember(currentStyle, rolTexto) {
        if (currentStyle == DualScreenStyle.PURE_NDS) Color(0xFF333333) else rolTexto
    }
    val defaultShape = remember(currentStyle) {
        if (currentStyle == DualScreenStyle.PURE_NDS) RoundedCornerShape(24.dp) else CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            if (currentStyle == DualScreenStyle.PURE_NDS) {
                Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.4f), basePlasticColor), center = Offset(0f, 0f), radius = 2000f)
            } else {
                Brush.verticalGradient(listOf(basePlasticColor, Color.Black))
            }
        )
    ) {
        if (isVertical) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp, start = 8.dp, end = 8.dp, top = 16.dp)) {
                    RenderSlotContent(settingsManager.uiSlotLeft, platforms, selectedPlatformIndex, currentStyle, defaultShape, primaryColor, surfaceColor, textColor, customMediaMap, { selectedPlatformIndex = it }, onSelect)
                }

                val hingeHeight = remember(foldingFeature, density) {
                    val ff = foldingFeature
                    if (ff != null && ff.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                        with(density) { (ff.bounds.bottom - ff.bounds.top).toDp() }
                    } else 12.dp
                }

                Box(modifier = Modifier.fillMaxWidth().height(if (hingeHeight < 4.dp) 12.dp else hingeHeight).background(
                    if (currentStyle == DualScreenStyle.PURE_NDS) {
                        Brush.verticalGradient(colors = listOf(basePlasticColor.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.2f), Color.White.copy(alpha = 0.5f), basePlasticColor.copy(alpha = 0.5f)))
                    } else {
                        Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.5f), primaryColor.copy(alpha = 0.5f)))
                    }
                ).clickable { if (currentStyle == DualScreenStyle.PURE_NDS) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) })

                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp, start = 8.dp, end = 8.dp, bottom = 16.dp)) {
                    RenderSlotContent(settingsManager.uiSlotRight, platforms, selectedPlatformIndex, currentStyle, defaultShape, primaryColor, surfaceColor, textColor, customMediaMap, { selectedPlatformIndex = it }, onSelect)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(if (currentStyle == DualScreenStyle.CYBERPUNK_FLIP) 8.dp else 16.dp)) {
                Box(modifier = Modifier.weight(0.4f).fillMaxHeight().padding(end = 8.dp)) {
                    RenderSlotContent(settingsManager.uiSlotLeft, platforms, selectedPlatformIndex, currentStyle, defaultShape, primaryColor, surfaceColor, textColor, customMediaMap, { selectedPlatformIndex = it }, onSelect)
                }

                val hingeWidth = remember(foldingFeature, density) {
                    val ff = foldingFeature
                    if (ff != null && ff.orientation == FoldingFeature.Orientation.VERTICAL) {
                        with(density) { (ff.bounds.right - ff.bounds.left).toDp() }
                    } else 12.dp
                }

                if (hingeWidth > 0.dp) {
                    Box(modifier = Modifier.fillMaxHeight().width(if (hingeWidth < 4.dp) 12.dp else hingeWidth).background(
                        if (currentStyle == DualScreenStyle.PURE_NDS) {
                            Brush.horizontalGradient(colors = listOf(basePlasticColor.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.2f), Color.White.copy(alpha = 0.5f), basePlasticColor.copy(alpha = 0.5f)))
                        } else {
                            Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.2f), primaryColor.copy(alpha = 0.2f)))
                        }
                    ).clickable { if (currentStyle == DualScreenStyle.PURE_NDS) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) })
                }
                
                Column(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(start = 8.dp)) {
                    RenderSlotContent(settingsManager.uiSlotRight, platforms, selectedPlatformIndex, currentStyle, defaultShape, primaryColor, surfaceColor, textColor, customMediaMap, { selectedPlatformIndex = it }, onSelect)
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun NdsLibraryContent(
    platformId: String,
    games: List<Game>,
    initialSelectedIndex: Int,
    customMediaMap: Map<String, String>,
    exoPlayer: ExoPlayer,
    onBack: () -> Unit,
    onGameClick: (Game) -> Unit,
    primaryColor: Color,
    settingsManager: SettingsManager
) {
    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, games.size.coerceAtLeast(1) - 1)) }
    val selectedGame = if (games.isNotEmpty()) games[selectedIndex] else null

    val basePlasticColor = remember(primaryColor, settingsManager.ndsBodyColor) {
        if (settingsManager.ndsBodyColor == 0xFFE0E0E0.toInt()) primaryColor.toPastel()
        else Color(settingsManager.ndsBodyColor)
    }

    val currentStyle = dualStyleFromPref(settingsManager.dualScreenStyle)
    val surfaceColor = if(currentStyle == DualScreenStyle.PURE_NDS) Color(0xFFF0F4F8) else MaterialTheme.colorScheme.surface
    val isCyber = currentStyle == DualScreenStyle.CYBERPUNK_FLIP

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val context = LocalContext.current
    val activity = context as? Activity
    var foldingFeature by remember { mutableStateOf<FoldingFeature?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { layoutInfo ->
                foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
        }
    }

    if (isPortrait) {
        Column(modifier = Modifier.fillMaxSize().background(if(isCyber) MaterialTheme.colorScheme.background else basePlasticColor)) {
            // --- PANTALLA SUPERIOR: INFO & VIDEO ---
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(if(isCyber) 8.dp else 12.dp),
                color = MaterialTheme.colorScheme.background,
                shape = if(isCyber) CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(16.dp),
                border = BorderStroke(if(isCyber) 1.dp else 2.dp, if(isCyber) primaryColor else Color.White.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                        AndroidView(factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }, modifier = Modifier.fillMaxSize())
                    } else if (settingsManager.libShowBoxArt) {
                        AsyncImage(
                            model = selectedGame?.boxArt,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text = selectedGame?.title?.uppercase() ?: "---", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(text = selectedGame?.developer ?: "RETRO PROTOCOL", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isCyber) {
                        Text(text = "SIGNAL ACQUIRED // $platformId", color = primaryColor, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // --- BISAGRA HORIZONTAL ---
            val hingeHeight = remember(foldingFeature, density) {
                val ff = foldingFeature
                    if (ff != null && ff.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                    with(density) { (ff.bounds.bottom - ff.bounds.top).toDp() }
                } else 12.dp
            }
            Box(modifier = Modifier.fillMaxWidth().height(if (hingeHeight < 4.dp) 12.dp else hingeHeight).background(
                if (isCyber) {
                    Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.5f), primaryColor.copy(alpha = 0.5f)))
                } else {
                    Brush.verticalGradient(listOf(basePlasticColor, Color.Black.copy(alpha = 0.3f), Color.White.copy(alpha = 0.3f), basePlasticColor))
                }
            ))

            // --- PANTALLA INFERIOR: GRID DE JUEGOS ---
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(if(isCyber) 8.dp else 12.dp),
                color = surfaceColor,
                shape = if(isCyber) CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(16.dp),
                border = if(isCyber) BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)) else BorderStroke(4.dp, concaveBrush)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = primaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text(text = if(isCyber) "SYS_INDEX" else platformId.uppercase(), color = if(isCyber) primaryColor else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = if(isCyber) primaryColor else Color.Gray) }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 90.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(games, key = { _, g -> g.path }) { index, game ->
                            val isFocused = selectedIndex == index
                            if (isCyber) {
                                CyberpunkGameCard(
                                    game = game,
                                    isFocused = isFocused,
                                    primaryColor = primaryColor,
                                    onFocus = { selectedIndex = index },
                                    onClick = { onGameClick(game) }
                                )
                            } else {
                                NdsGameCard(
                                    game = game,
                                    isFocused = isFocused,
                                    primaryColor = primaryColor,
                                    onFocus = { selectedIndex = index },
                                    onClick = { onGameClick(game) }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Landscape Mode
        Row(modifier = Modifier.fillMaxSize().background(if(isCyber) MaterialTheme.colorScheme.background else basePlasticColor).padding(if (isCyber) 8.dp else 16.dp)) {
            // --- PANTALLA IZQUIERDA: INFO & VIDEO ---
            Surface(
                modifier = Modifier.weight(0.4f).fillMaxHeight().padding(end = 8.dp),
                color = MaterialTheme.colorScheme.background,
                shape = if(isCyber) CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(16.dp),
                border = BorderStroke(if(isCyber) 1.dp else 2.dp, if(isCyber) primaryColor else Color.White.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (settingsManager.libShowVideos && selectedGame?.videoPreview != null) {
                        AndroidView(factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }, modifier = Modifier.fillMaxSize())
                    } else if (settingsManager.libShowBoxArt) {
                        AsyncImage(
                            model = selectedGame?.boxArt,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(text = selectedGame?.title?.uppercase() ?: "---", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(text = selectedGame?.developer ?: "RETRO PROTOCOL", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isCyber) {
                        Text(text = "SIGNAL ACQUIRED // $platformId", color = primaryColor, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // --- BISAGRA VERTICAL ---
            val hingeWidth = remember(foldingFeature, density) {
                val ff = foldingFeature
                    if (ff != null && ff.orientation == FoldingFeature.Orientation.VERTICAL) {
                    with(density) { (ff.bounds.right - ff.bounds.left).toDp() }
                } else 12.dp
            }
            Box(modifier = Modifier.fillMaxHeight().width(if (hingeWidth < 4.dp) 12.dp else hingeWidth).background(
                if (isCyber) {
                    Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.2f), primaryColor.copy(alpha = 0.2f)))
                } else {
                    Brush.horizontalGradient(listOf(basePlasticColor.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.2f), Color.White.copy(alpha = 0.5f), basePlasticColor.copy(alpha = 0.5f)))
                }
            ))

            // --- PANTALLA DERECHA: GRID DE JUEGOS ---
            Surface(
                modifier = Modifier.weight(0.6f).fillMaxHeight().padding(start = 8.dp),
                color = surfaceColor,
                shape = if(isCyber) CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) else RoundedCornerShape(16.dp),
                border = if(isCyber) BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)) else BorderStroke(4.dp, concaveBrush)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = primaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text(text = if(isCyber) "SYS_INDEX" else platformId.uppercase(), color = if(isCyber) primaryColor else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onBack) { Icon(Icons.Default.Close, null, tint = if(isCyber) primaryColor else Color.Gray) }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 90.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(games, key = { _, g -> g.path }) { index, game ->
                            val isFocused = selectedIndex == index
                            if (isCyber) {
                                CyberpunkGameCard(
                                    game = game,
                                    isFocused = isFocused,
                                    primaryColor = primaryColor,
                                    onFocus = { selectedIndex = index },
                                    onClick = { onGameClick(game) }
                                )
                            } else {
                                NdsGameCard(
                                    game = game,
                                    isFocused = isFocused,
                                    primaryColor = primaryColor,
                                    onFocus = { selectedIndex = index },
                                    onClick = { onGameClick(game) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NdsGameCard(game: Game, isFocused: Boolean, primaryColor: Color, onFocus: () -> Unit, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .aspectRatio(0.75f)
            .scale(scale)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable { onClick() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) primaryColor.copy(alpha = 0.1f) else Color.White,
        border = BorderStroke(if (isFocused) 3.dp else 1.dp, if (isFocused) primaryColor else Color.LightGray),
        shadowElevation = if (isFocused) 8.dp else 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = game.boxArt,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (isFocused) {
                Box(Modifier.fillMaxSize().background(primaryColor.copy(alpha = 0.05f)))
            }
        }
    }
}

@Composable
fun CyberpunkGameCard(game: Game, isFocused: Boolean, primaryColor: Color, onFocus: () -> Unit, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val alpha by animateFloatAsState(if (isFocused) 1f else 0.7f, label = "alpha")

    Surface(
        modifier = Modifier
            .aspectRatio(0.75f)
            .scale(scale)
            .alpha(alpha)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable { onClick() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            },
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = if (isFocused) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) primaryColor else Color.DarkGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = game.boxArt,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Crop
            )
            if (isFocused) {
                Box(Modifier.fillMaxSize().background(primaryColor.copy(alpha = 0.2f)))
                Icon(Icons.Default.PlayArrow, null, tint = primaryColor, modifier = Modifier.align(Alignment.Center).size(32.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(4.dp))
            }
        }
    }
}

@Composable
fun RenderSlotContent(
    slotType: String, platforms: List<String>, selectedIndex: Int, style: DualScreenStyle, shape: androidx.compose.ui.graphics.Shape,
    accentColor: Color, surfaceColor: Color, textColor: Color, customMediaMap: Map<String, String>, onIndexChanged: (Int) -> Unit, onSelect: (String) -> Unit
) {
    when (slotType) {
        "GRID", "LIST" -> GridSlot(platforms, selectedIndex, style, shape, accentColor, surfaceColor, customMediaMap, onIndexChanged, onSelect)
        "INFO" -> InfoSlot(platforms, selectedIndex, style, shape, accentColor, surfaceColor, textColor, customMediaMap)
        "VIDEO" -> VideoSlot(platforms, selectedIndex, style, shape, accentColor, surfaceColor, customMediaMap)
        else -> GridSlot(platforms, selectedIndex, style, shape, accentColor, surfaceColor, customMediaMap, onIndexChanged, onSelect)
    }
}

@Composable
fun GridSlot(
    platforms: List<String>, selectedIndex: Int, style: DualScreenStyle, shape: androidx.compose.ui.graphics.Shape,
    accentColor: Color, surfaceColor: Color, customMediaMap: Map<String, String>, onIndexChanged: (Int) -> Unit, onSelect: (String) -> Unit
) {
    val isCyber = style == DualScreenStyle.CYBERPUNK_FLIP
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))

    // "recientes" y "favoritos" se insertan en la posicion 0 despues de la
    // primera composicion: sin observar tambien el tamano de la lista, la
    // rejilla se quedaba en el sistema anterior.
    LaunchedEffect(selectedIndex, platforms.size) {
        if (selectedIndex in platforms.indices) gridState.animateScrollToItem(selectedIndex)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaceColor,
        shape = if (isCyber) shape else RoundedCornerShape(24.dp),
        border = if (isCyber) BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)) else BorderStroke(4.dp, concaveBrush)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if(isCyber) "SYSTEM INDEX // ONLINE" else "Pantalla Táctil",
                    color = if(isCyber) accentColor else Color.Gray,
                    fontSize = if(isCyber) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp)
                )

                LazyVerticalGrid(
                    state = gridState, columns = GridCells.Adaptive(minSize = 90.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(platforms, key = { _, id -> id }) { index, id ->
                        val isFocused = selectedIndex == index
                        if (isCyber) {
                            CyberpunkPlatformItem(
                                id = id,
                                isSelected = isFocused,
                                accentColor = accentColor,
                                customMediaMap = customMediaMap,
                                onFocus = { onIndexChanged(index) },
                                onClick = { onSelect(id) }
                            )
                        } else {
                            PureNDSPlatformItem(
                                id = id,
                                isSelected = isFocused,
                                accentColor = accentColor,
                                customMediaMap = customMediaMap,
                                onFocus = { onIndexChanged(index) },
                                onClick = { onSelect(id) }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(70.dp).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    color = if(isCyber) Color.Black.copy(alpha = 0.9f) else Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = if (isCyber) BorderStroke(1.dp, accentColor) else BorderStroke(2.dp, convexBrush)
                ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        DockIcon(Icons.Default.Settings, "AJUSTES", isCyber, accentColor) { onSelect("SYS_SETTINGS") }
                        DockIcon(Icons.Default.SportsEsports, "TRIVIAL", isCyber, accentColor) { onSelect("SYS_TRIVIAL") }
                        DockIcon(Icons.Default.MenuBook, "MANUAL", isCyber, accentColor) { onSelect("SYS_MANUAL") }
                        DockIcon(Icons.Default.Speed, "BENCH", isCyber, accentColor) { onSelect("SYS_BENCHMARK") }
                        DockIcon(Icons.Default.Apps, "APPS", isCyber, accentColor) { onSelect("SYS_APPS") }
                    }
                }
            }
        }
    }
}

@Composable
fun PureNDSPlatformItem(id: String, isSelected: Boolean, accentColor: Color, customMediaMap: Map<String, String>, onFocus: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isSelected || isFocused) 1.15f else 1f, animationSpec = tween(100), label = "scale")
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if(it.isFocused) onFocus()
            }
            .focusable()
            .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            }
    ) {
        Surface(
            modifier = Modifier.size(75.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isSelected || isFocused) accentColor.copy(alpha = 0.1f) else Color.White,
            shadowElevation = if (isSelected || isFocused) 12.dp else 4.dp,
            border = BorderStroke(if (isSelected || isFocused) 4.dp else 2.dp, if (isSelected || isFocused) accentColor else Color.LightGray)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                LogotipoSistema(
                    id = id,
                    customMediaMap = customMediaMap,
                    colorGlifo = if (isSelected || isFocused) accentColor else Color.Gray
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = studioTitle(id, "es").uppercase(), color = if (isSelected || isFocused) accentColor else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Logotipo de un sistema para los items del modo de dos pantallas.
 *
 * Los wordmarks de `assets/contentimg/logos/` son ~4:1, asi que se dibujan
 * ajustados al ancho. Recientes, favoritos y las entradas SYS_ no tienen
 * logotipo: para esas se usa la abreviatura del tema Studio.
 */
@Composable
fun LogotipoSistema(id: String, customMediaMap: Map<String, String>, colorGlifo: Color) {
    val esEspecial = id.startsWith("SYS_") || id == "recientes" || id == "favoritos"
    if (esEspecial) {
        Text(studioAbbrev(id), color = colorGlifo, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        return
    }
    val ruta = remember(id, customMediaMap) {
        ThemeManager.getThemeImagePath("logos", "${id.lowercase()}.webp", customMediaMap)
    }
    SubcomposeAsyncImage(
        model = ruta,
        contentDescription = null,
        modifier = Modifier.fillMaxWidth(),
        contentScale = ContentScale.Fit,
        error = {
            Text(studioAbbrev(id), color = colorGlifo, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    )
}

@Composable
fun CyberpunkPlatformItem(id: String, isSelected: Boolean, accentColor: Color, customMediaMap: Map<String, String>, onFocus: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isSelected || isFocused) 1.08f else 1f, animationSpec = tween(80), label = "scale")
    val alpha by animateFloatAsState(if (isSelected || isFocused) 1f else 0.6f, animationSpec = tween(100), label = "alpha")
    val view = LocalView.current

    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).scale(scale).alpha(alpha)
            .onFocusChanged {
                isFocused = it.isFocused
                if(it.isFocused) onFocus()
            }
            .focusable()
            .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            },
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        color = if (isSelected || isFocused) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (isSelected || isFocused) 3.dp else 1.dp, if (isSelected || isFocused) accentColor else Color.DarkGray)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Sin tinte: un wordmark de color teñido se convierte en una silueta.
            Box(modifier = Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LogotipoSistema(id = id, customMediaMap = customMediaMap, colorGlifo = accentColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(text = studioTitle(id, "es").uppercase(), color = if (isSelected || isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DockIcon(icon: ImageVector, label: String, isCyber: Boolean, accentColor: Color, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            }
            .padding(4.dp)
    ) {
        Icon(
            imageVector = icon, contentDescription = label,
            tint = if (isFocused) accentColor else if (isCyber) accentColor.copy(alpha = 0.7f) else Color.Gray,
            modifier = Modifier.size(28.dp).let { if (isFocused) it.background(accentColor.copy(alpha = 0.1f), CircleShape) else it }
        )
        Text(text = label, color = if (isFocused) accentColor else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InfoSlot(platforms: List<String>, selectedIndex: Int, style: DualScreenStyle, shape: androidx.compose.ui.graphics.Shape, accentColor: Color, surfaceColor: Color, textColor: Color, customMediaMap: Map<String, String>) {
    val isCyber = style == DualScreenStyle.CYBERPUNK_FLIP
    val platformId = remember(platforms, selectedIndex) { if (platforms.isNotEmpty() && selectedIndex in platforms.indices) platforms[selectedIndex] else "" }
    var metadata by remember(platformId) { mutableStateOf(metadataCache[platformId] ?: SystemMetadata()) }
    val context = LocalContext.current

    LaunchedEffect(platformId) {
        if (platformId.isNotEmpty() && !metadataCache.containsKey(platformId)) {
            val loaded = withContext(Dispatchers.IO) { loadSystemMetadata(context, platformId) }
            metadataCache[platformId] = loaded
            metadata = loaded
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(), color = surfaceColor, shape = if (isCyber) shape else RoundedCornerShape(24.dp),
        border = if (isCyber) BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)) else BorderStroke(4.dp, concaveBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = metadata.name.uppercase(), color = accentColor, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = if (isCyber) FontFamily.Monospace else FontFamily.Default)
            Text(text = "${metadata.manufacturer} // ${metadata.year}", color = textColor.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(if (isCyber) accentColor.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text(text = metadata.description, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (isCyber) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CyberStat("CORE", "STABLE"); CyberStat("SYNC", "ACTIVE"); CyberStat("DATA", "RK3566_OK")
                }
            }
        }
    }
}

@Composable
fun CyberStat(label: String, value: String) {
    Column {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun VideoSlot(platforms: List<String>, selectedIndex: Int, style: DualScreenStyle, shape: androidx.compose.ui.graphics.Shape, accentColor: Color, surfaceColor: Color, customMediaMap: Map<String, String>) {
    val isCyber = style == DualScreenStyle.CYBERPUNK_FLIP
    val isStudio = style == DualScreenStyle.STUDIO
    val platformId = remember(platforms, selectedIndex) { if (platforms.isNotEmpty() && selectedIndex in platforms.indices) platforms[selectedIndex] else "" }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = when { isStudio -> Color(0xFF101114); isCyber -> MaterialTheme.colorScheme.background; else -> surfaceColor },
        shape = when { isStudio -> RoundedCornerShape(14.dp); isCyber -> shape; else -> RoundedCornerShape(24.dp) },
        border = when {
            isStudio -> BorderStroke(1.dp, Color(0xFF23252B))
            isCyber -> BorderStroke(1.dp, accentColor)
            else -> BorderStroke(4.dp, concaveBrush)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Recientes, favoritos y las entradas SYS_ no tienen captura en
            // `snaps/`: sin este corte se cargaba un webp inexistente y el panel
            // se quedaba en blanco.
            val esEspecial = platformId.startsWith("SYS_") || platformId == "recientes" || platformId == "favoritos"
            if (!esEspecial) {
                val snapPath = remember(platformId, customMediaMap) { ThemeManager.getThemeImagePath("snaps", "$platformId.webp", customMediaMap) }
                AsyncImage(model = snapPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.8f)
            }
            if (isCyber) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                Text(text = "SIGNAL ACQUIRED // $platformId", color = accentColor, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), fontFamily = FontFamily.Monospace)
            }
            if (esEspecial) {
                // Ficha de respaldo, con el lenguaje visual de cada estilo.
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = if (isCyber || isStudio) Alignment.Start else Alignment.CenterHorizontally
                ) {
                    if (isCyber || isStudio) {
                        Text(
                            studioAbbrev(platformId), color = accentColor, fontSize = 44.sp, fontWeight = FontWeight.Black,
                            fontFamily = if (isCyber) FontFamily.Monospace else FontFamily.Default
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(84.dp).background(accentColor, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(studioAbbrev(platformId), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = (if (isCyber) "> " else "") + studioTitle(platformId, "es").uppercase(),
                        color = if (isCyber || isStudio) Color.White else Color.DarkGray,
                        fontSize = 22.sp, fontWeight = FontWeight.Black,
                        fontFamily = if (isCyber) FontFamily.Monospace else FontFamily.Default
                    )
                    val blurb = studioSystemBlurb(platformId, "es")
                    if (blurb.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            blurb,
                            color = if (isCyber) accentColor.copy(alpha = 0.75f) else if (isStudio) Color(0xFF9AA0A6) else Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = if (isCyber) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                }
            }
            if (isStudio && !esEspecial) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF101114).copy(alpha = 0.85f)))))
                Text(
                    text = studioTitle(platformId, "es").uppercase(),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }
        }
    }
}

private fun loadSystemMetadata(context: android.content.Context, systemId: String): SystemMetadata {
    return try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        val inputStream = context.assets.open("metadata/systems_metadata.xml")
        parser.setInput(inputStream, null)
        var eventType = parser.eventType
        var currentMetadata = SystemMetadata()
        var isTargetSystem = false
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "system" && parser.getAttributeValue(null, "id") == systemId) isTargetSystem = true
                    if (isTargetSystem) {
                        when (name) {
                            "name" -> currentMetadata = currentMetadata.copy(name = parser.nextText())
                            "manufacturer" -> currentMetadata = currentMetadata.copy(manufacturer = parser.nextText())
                            "year" -> currentMetadata = currentMetadata.copy(year = parser.nextText())
                            "description" -> currentMetadata = currentMetadata.copy(description = parser.nextText())
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (name == "system" && isTargetSystem) return currentMetadata
            }
            eventType = parser.next()
        }
        SystemMetadata(name = systemId)
    } catch (e: Exception) { SystemMetadata(name = systemId) }
}
