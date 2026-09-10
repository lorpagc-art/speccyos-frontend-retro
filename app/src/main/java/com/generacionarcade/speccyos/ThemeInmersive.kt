package com.generacionarcade.speccyos

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * 💎 TEMA: INMERSIVE (Final Edition)
 * Optimización de navegación física, Dock Interactivo y Relleno de Arte.
 */

private fun getEclipseFilename(id: String): String {
    val lowId = id.lowercase()
    return when {
        lowId == "atari2600" -> "atari2600.webp"
        lowId == "atari5200" -> "atari5200.webp"
        lowId == "atari7800" -> "atari7800.webp"
        lowId.contains("jaguar") -> "jaguar.webp"
        lowId.contains("lynx") -> "lynx.webp"
        lowId == "dc" || lowId == "dreamcast" -> "dc.webp"
        lowId == "gb" || lowId == "gameboy" -> "gb.webp"
        lowId == "gbc" || lowId == "gameboycolor" -> "gbc.webp"
        lowId == "gba" || lowId == "gameboyadvance" -> "gba.webp"
        lowId == "gc" || lowId == "gamecube" -> "gc.webp"
        lowId == "n64" -> "n64.webp"
        lowId == "nes" -> "nes.webp"
        lowId == "snes" -> "snes.webp"
        lowId == "nds" -> "nds.webp"
        lowId == "3ds" -> "3ds.webp"
        lowId == "genesis" || lowId == "megadrive" || lowId == "md" -> "genesis.webp"
        lowId == "mastersystem" || lowId == "sms" -> "master.webp"
        lowId == "gamegear" || lowId == "gg" -> "gamegear.webp"
        lowId == "psx" || lowId == "ps1" || lowId == "playstation" -> "psx.webp"
        lowId == "ps2" -> "ps2.webp"
        lowId == "psp" -> "psp.webp"
        lowId == "neogeo" -> "neogeo.webp"
        lowId == "ngp" -> "ngp.webp"
        lowId == "ngpc" -> "ngpc.webp"
        lowId == "mame" || lowId == "arcade" -> "mame.webp"
        lowId == "fbneo" -> "fbneo.webp"
        lowId == "cps1" -> "cps1.webp"
        lowId == "cps2" -> "cps2.webp"
        lowId == "cps3" -> "cps3.webp"
        lowId == "dos" || lowId == "pc" -> "dos.webp"
        lowId == "wii" -> "wii.webp"
        lowId == "vita" || lowId == "psvita" -> "vita.webp"
        lowId == "amiga" -> "amiga.webp"
        lowId == "c64" -> "c64.webp"
        lowId == "msx" -> "msx.webp"
        lowId == "zxspectrum" -> "zxspectrum.webp"
        lowId == "switch" -> "switch.webp"
        lowId == "android" -> "android.webp"
        lowId == "scummvm" -> "scummvm.webp"
        lowId == "pico8" -> "pico8.webp"
        lowId == "virtualboy" -> "virtualboy.webp"
        lowId == "32x" -> "32x.webp"
        lowId == "3do" -> "3do.webp"
        lowId == "cpc" || lowId == "amstradcpc" -> "cpc.webp"
        lowId == "tg16" -> "tg16.webp"
        lowId == "tgcd" || lowId == "tg-cd" -> "tgcd.webp"
        lowId == "segacd" -> "segacd.webp"
        lowId == "saturn" -> "saturn.webp"
        lowId == "gameandwatch" || lowId == "gw" -> "gameandwatch.webp"
        lowId == "wonderswan" || lowId == "ws" -> "wonderswan.webp"
        lowId == "wonderswancolor" || lowId == "wsc" -> "wonderswancolor.webp"
        lowId == "model2" -> "model2.webp"
        lowId == "model3" -> "model3.webp"
        else -> "default.webp"
    }
}

private fun getLogoFilename(id: String): String {
    val lowId = id.lowercase()
    return when {
        lowId == "atari2600" -> "atari2600.webp"
        lowId == "atari5200" -> "atari5200.webp"
        lowId == "atari7800" -> "atari7800.webp"
        lowId.contains("jaguar") -> "atarijaguar.webp"
        lowId.contains("lynx") -> "atarilynx.webp"
        lowId == "dc" || lowId == "dreamcast" -> "dreamcast.webp"
        lowId == "gb" || lowId == "gameboy" -> "gb.webp"
        lowId == "gbc" || lowId == "gameboycolor" -> "gbc.webp"
        lowId == "gba" || lowId == "gameboyadvance" -> "gba.webp"
        lowId == "gc" || lowId == "gamecube" -> "gc.webp"
        lowId == "n64" -> "n64.webp"
        lowId == "nes" -> "nes.webp"
        lowId == "snes" -> "snes.webp"
        lowId == "nds" -> "nds.webp"
        lowId == "3ds" -> "n3ds.webp"
        lowId == "genesis" -> "genesis.webp"
        lowId == "megadrive" || lowId == "md" -> "megadrive.webp"
        lowId == "mastersystem" || lowId == "sms" -> "mastersystem.webp"
        lowId == "gamegear" || lowId == "gg" -> "gamegear.webp"
        lowId == "psx" || lowId == "ps1" || lowId == "playstation" -> "psx.webp"
        lowId == "ps2" -> "ps2.webp"
        lowId == "psp" -> "psp.webp"
        lowId == "neogeo" -> "neogeo.webp"
        lowId == "ngp" -> "ngp.webp"
        lowId == "ngpc" -> "ngpc.webp"
        lowId == "mame" || lowId == "arcade" -> "mame.webp"
        lowId == "fbneo" -> "fbneo.webp"
        lowId == "cps1" -> "cps1.webp"
        lowId == "cps2" -> "cps2.webp"
        lowId == "cps3" -> "cps3.webp"
        lowId == "dos" -> "dos.webp"
        lowId == "pc" -> "pc.webp"
        lowId == "wii" -> "wii.webp"
        lowId == "vita" || lowId == "psvita" -> "psvita.webp"
        lowId == "amiga" -> "amiga.webp"
        lowId == "c64" -> "c64.webp"
        lowId == "msx" -> "msx.webp"
        lowId == "zxspectrum" -> "zxspectrum.webp"
        lowId == "switch" -> "switch.webp"
        lowId == "android" -> "android.webp"
        lowId == "scummvm" -> "scummvm.webp"
        lowId == "pico8" -> "pico8.webp"
        lowId == "virtualboy" -> "virtualboy.webp"
        lowId == "32x" -> "sega32x.webp"
        lowId == "3do" -> "3do.webp"
        lowId == "cpc" || lowId == "amstradcpc" -> "amstradcpc.webp"
        lowId == "tg16" -> "tg16.webp"
        lowId == "tgcd" || lowId == "tg-cd" -> "tg-cd.webp"
        lowId == "segacd" -> "segacd.webp"
        lowId == "saturn" -> "saturn.webp"
        lowId == "gameandwatch" || lowId == "gw" -> "gameandwatch.webp"
        lowId == "wonderswan" || lowId == "ws" -> "wonderswan.webp"
        lowId == "wonderswancolor" || lowId == "wsc" -> "wonderswancolor.webp"
        lowId == "model2" -> "model2.webp"
        lowId == "model3" -> "model3.webp"
        lowId == "favoritos" -> "auto-favorites.webp"
        lowId == "recientes" -> "auto-lastplayed.webp"
        else -> "default.webp"
    }
}

private fun getSystemNews(id: String, description: String, lang: String): String {
    if (description.isNotEmpty() && description.uppercase() != id.uppercase()) return description
    
    return when(id) {
        "SYS_SETTINGS" -> if(lang == "es") "CONFIGURACIÓN AVANZADA: OPTIMIZA TU EXPERIENCIA AL MÁXIMO" else "ADVANCED SETTINGS: OPTIMIZE YOUR EXPERIENCE"
        "SYS_HARDWARE" -> if(lang == "es") "HARDWARE MONITOR: TEMPERATURAS EN TIEMPO REAL" else "HARDWARE MONITOR: REAL-TIME TEMPERATURES"
        "SYS_TRIVIAL" -> if(lang == "es") "TRIVIAL ARCADE: PON A PRUEBA TUS CONOCIMIENTOS RETRO" else "TRIVIAL ARCADE: TEST YOUR RETRO KNOWLEDGE"
        "SYS_MANUAL" -> if(lang == "es") "MANUAL DEL SISTEMA Y DOCUMENTACIÓN" else "SYSTEM MANUAL AND DOCUMENTATION"
        "SYS_APPS" -> if(lang == "es") "HERRAMIENTAS ANDROID: ACCEDE A TUS APLICACIONES INSTALADAS" else "ANDROID TOOLS: ACCESS YOUR INSTALLED APPLICATIONS"
        "SYS_BENCHMARK" -> if(lang == "es") "STRESS TEST: COMPRUEBA LA POTENCIA BRUTA" else "STRESS TEST: CHECK THE RAW POWER"
        "favoritos" -> if(lang == "es") "BIBLIOTECA PERSONAL: TUS CLÁSICOS IMPRESCINDIBLES" else "PERSONAL LIBRARY: YOUR MUST-HAVE CLASSICS"
        "recientes" -> if(lang == "es") "CONTINUIDAD TOTAL: VUELVE A LA ACCIÓN DONDE LA DEJASTE" else "TOTAL CONTINUITY: GET BACK WHERE YOU LEFT OFF"
        else -> {
            val name = id.replace("_", " ").uppercase()
            if(lang == "es") "SISTEMA $name DETECTADO: OPTIMIZACIÓN VULKAN EXTREME ACTIVA" 
            else "SYSTEM $name DETECTED: VULKAN EXTREME OPTIMIZATION ACTIVE"
        }
    }
}

@Composable
fun InmersiveDashboardContent(
    mainViewModel: MainViewModel,
    platforms: List<String>,
    initialIndex: Int,
    lang: String,
    gameCounts: Map<String, Int>,
    hardwareState: HardwareUiState,
    soundManager: SoundManager,
    customMediaMap: Map<String, String>,
    userStatusManager: UserStatusManager,
    onFocused: (String) -> Unit,
    onSelect: (String) -> Unit,
    onLaunchGame: (Game) -> Unit,
    primaryColor: Color
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { platforms.size })
    val context = LocalContext.current
    val settingsManager = mainViewModel.settingsManager
    val fontScale = settingsManager.uiFontScale
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(pagerState.currentPage) { 
        if (platforms.isNotEmpty()) {
            val currentId = platforms.getOrNull(pagerState.currentPage) ?: platforms.last()
            onFocused(currentId)
            ThemeManager.adaptDNA(context, currentId)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            } else {
                                scope.launch { pagerState.animateScrollToPage(platforms.size - 1) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pagerState.currentPage < platforms.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                            platforms.getOrNull(pagerState.currentPage)?.let { onSelect(it) }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        HorizontalPager(
            state = pagerState, 
            modifier = Modifier.fillMaxSize(),
            // Con 2, el pager mantenia compuestas 5 paginas a la vez y cada una
            // dibuja hasta 3 AsyncImage: 15 imagenes decodificadas y vivas para
            // lo que el usuario ve como una sola pantalla. Con 1 la transicion
            // sigue sin parpadeo y la presion sobre la cache de Coil y la GPU
            // baja casi a la mitad.
            beyondViewportPageCount = 1,
            userScrollEnabled = true
        ) { page ->
            val id = platforms[page]
            val isSystem = id.startsWith("SYS_")
            val isCollection = id == "favoritos" || id == "recientes"
            
            val eclipsePath = if (isSystem) {
                "file:///android_asset/contentimg/banner-neon.webp"
            } else {
                ThemeManager.getThemeImagePath("cyberpunk", getEclipseFilename(id), customMediaMap, context)
            }

            val romCount = gameCounts[id] ?: 0

            // currentPageOffsetFraction cambia en cada frame del swipe: leerlo aqui
            // recomponia la pagina entera -fondo, icono y logo, tres AsyncImage- 60
            // veces por segundo. Dentro de graphicsLayer es lectura de fase de
            // dibujo y el movimiento queda exactamente igual.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset =
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                        scaleX = 0.96f + (0.04f * fraction)
                        scaleY = 0.96f + (0.04f * fraction)
                        alpha = 0.6f + (0.4f * fraction)
                    }
                    .clickable { onSelect(id) }
            ) {
                AsyncImage(
                    model = eclipsePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (!isSystem || isCollection) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val iconName = ThemeManager.mapPlatformToTransparentIcon(id)
                        val iconPath = ThemeManager.getThemeImagePath("transparent-pack", "$iconName.webp", customMediaMap, context)
                        AsyncImage(
                            model = iconPath,
                            contentDescription = null,
                            modifier = Modifier
                                .size(300.dp)
                                .graphicsLayer {
                                    val pageOffset = (pagerState.currentPage - page) +
                                        pagerState.currentPageOffsetFraction
                                    alpha = 0.1f *
                                        (1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                                },
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(primaryColor, BlendMode.SrcAtop)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                if (!isSystem || isCollection) {
                    val metadata = remember(id, lang) { 
                        SystemMetadataManager.getMetadataForSystem(context, id, lang)
                    }
                
                    // LOGO DEL SISTEMA EN LA ESQUINA SUPERIOR
                    val logoName = "${id.lowercase()}.webp"
                    AsyncImage(
                        model = ThemeManager.getThemeImagePath("logos", logoName, customMediaMap, context),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 44.dp, top = 80.dp)
                            .widthIn(max = 240.dp)
                            .heightIn(max = 100.dp)
                            .align(Alignment.TopStart),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopStart
                    )
                    
                    // INFORMACIÓN DEL SISTEMA EN LA PARTE INFERIOR
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 44.dp, bottom = 120.dp)
                            .fillMaxWidth(0.6f)
                    ) {
                        Text(
                            text = metadata.name.ifEmpty { id.uppercase() },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 36.sp * fontScale,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 8f
                                )
                            )
                        )
                        
                        val subtitle = buildString {
                            if (metadata.manufacturer.isNotEmpty()) append(metadata.manufacturer)
                            if (metadata.manufacturer.isNotEmpty() && metadata.releaseYear.isNotEmpty()) append(" · ")
                            if (metadata.releaseYear.isNotEmpty()) append(metadata.releaseYear)
                        }
                        
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle.uppercase(),
                                color = primaryColor,
                                fontSize = 14.sp * fontScale,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        blurRadius = 4f
                                    )
                                )
                            )
                        }
                        
                        if (metadata.description.isNotEmpty()) {
                            Text(
                                text = metadata.description,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                fontSize = 12.sp * fontScale,
                                maxLines = 4,
                                lineHeight = (18f * fontScale).sp,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black,
                                        blurRadius = 4f
                                    )
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = id.replace("SYS_", "").uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp * fontScale,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 44.dp, top = 85.dp).align(Alignment.TopStart),
                        letterSpacing = 2.sp
                    )
                }

                if (!isSystem) {
                    Surface(
                        color = primaryColor.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 120.dp, end = 44.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "$romCount GAMES",
                            color = speccyContentColorOn(primaryColor),
                            fontSize = 14.sp * fontScale,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter), 
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f))
        ) {
            val currentId = platforms.getOrNull(pagerState.currentPage) ?: platforms.lastOrNull() ?: ""
            val metadata = remember(currentId, lang) { 
                SystemMetadataManager.getMetadataForSystem(context, currentId, lang)
            }
            val displayNews = remember(currentId, metadata.description, lang) {
                getSystemNews(currentId, metadata.description, lang)
            }
            
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(30.dp).background(primaryColor.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                    Row(modifier = Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = displayNews.uppercase(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), 
                            fontSize = 12.sp * fontScale,
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 50.dp),
                            letterSpacing = 2.sp
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp), 
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        SystemDockIcon(Icons.Default.Settings, "AJUSTES", primaryColor) { onSelect("SYS_SETTINGS") }
                        SystemDockIcon(Icons.Default.SportsEsports, "TRIVIAL", primaryColor) { onSelect("SYS_TRIVIAL") }
                        SystemDockIcon(Icons.Default.MenuBook, "MANUAL", primaryColor) { onSelect("SYS_MANUAL") }
                        SystemDockIcon(Icons.Default.Speed, "POTENCIA", primaryColor) { onSelect("SYS_BENCHMARK") }
                        SystemDockIcon(Icons.Default.Apps, "ANDROID", primaryColor) { onSelect("SYS_APPS") }
                    }
                }
            }
        }

        StatusBarReal(hardwareState, primaryColor, soundManager)
    }
}