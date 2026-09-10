package com.generacionarcade.speccyos

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.absoluteValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 🌌 THEME: AETHER ULTRA (Edition by AI)
 * Un diseño minimalista, futurista y envolvente.
 */

private fun getSystemNewsUltra(id: String, description: String, lang: String): String {
    if (description.isNotEmpty() && description.uppercase() != id.uppercase()) return description
    return when(id) {
        "SYS_SETTINGS" -> if(lang == "es") "CONFIGURACIÓN AVANZADA: OPTIMIZA TU EXPERIENCIA AL MÁXIMO" else "ADVANCED SETTINGS: OPTIMIZE YOUR EXPERIENCE"
        "SYS_HARDWARE" -> if(lang == "es") "HARDWARE MONITOR: TEMPERATURAS EN TIEMPO REAL" else "HARDWARE MONITOR: REAL-TIME TEMPERATURES"
        "SYS_TRIVIAL" -> if(lang == "es") "TRIVIAL ARCADE: PON A PRUEBA TUS CONOCIMIENTOS RETRO" else "TRIVIAL ARCADE: TEST YOUR RETRO KNOWLEDGE"
        "SYS_MANUAL" -> if(lang == "es") "MANUAL DEL SISTEMA Y DOCUMENTACIÓN" else "SYSTEM MANUAL AND DOCUMENTATION"
        "SYS_APPS" -> if(lang == "es") "HERRAMIENTAS ANDROID: ACCEDE A TUS APLICACIONES INSTALADAS" else "ANDROID TOOLS: ACCESS YOUR INSTALLED APPLICATIONS"
        "SYS_BENCHMARK" -> if(lang == "es") "STRESS TEST: COMPRUEBA LA POTENCIA BRUTA" else "STRESS TEST: CHECK THE RAW POWER"
        "SYS_TWEAKER" -> if(lang == "es") "TWEAKER ACTIVO: AJUSTA LA RESOLUCIÓN Y RENDIMIENTO" else "TWEAKER ACTIVE: ADJUST RESOLUTION AND PERFORMANCE"
        "favoritos" -> if(lang == "es") "BIBLIOTECA PERSONAL: TUS CLÁSICOS IMPRESCINDIBLES" else "PERSONAL LIBRARY: YOUR MUST-HAVE CLASSICS"
        "recientes" -> if(lang == "es") "CONTINUIDAD TOTAL: VUELVE A LA ACCIÓN DONDE LA DEJASTE" else "TOTAL CONTINUITY: GET BACK WHERE YOU LEFT OFF"
        else -> {
            val name = id.replace("_", " ").uppercase()
            if(lang == "es") "SISTEMA $name DETECTADO: OPTIMIZACIÓN VULKAN EXTREME ACTIVA" else "SYSTEM $name DETECTED: VULKAN EXTREME OPTIMIZATION ACTIVE"
        }
    }
}

private fun getRetrofixFilename(id: String): String {
    val lowId = id.lowercase()
    return when {
        lowId == "atarijaguar" || lowId == "jaguar" -> "jaguar"
        lowId == "colecovision" || lowId == "coleco" -> "coleco"
        lowId == "mastersystem" || lowId == "sms" -> "master"
        lowId == "megadrive" || lowId == "genesis" -> "genesis"
        lowId == "pcengine" || lowId == "tg16" -> "tg16"
        lowId == "superv" || lowId == "supervision" -> "supervision"
        lowId == "segacd" || lowId == "scd" -> "segacd"
        lowId == "playstation" || lowId == "psx" -> "psx"
        lowId == "gamecube" || lowId == "gc" -> "gc"
        lowId == "nintendo3ds" || lowId == "3ds" -> "3ds"
        lowId == "gameboy" || lowId == "gb" -> "gb"
        lowId == "gameboycolor" || lowId == "gbc" -> "gbc"
        lowId == "gameboyadvance" || lowId == "gba" -> "gba"
        lowId == "neogeo" || lowId == "ng" -> "neogeo"
        lowId == "neogeopocket" || lowId == "ngp" -> "ngp"
        lowId == "wonderswan" || lowId == "ws" -> "ws"
        lowId == "wonderswancolor" || lowId == "wsc" -> "wsc"
        lowId == "turbografx16" || lowId == "tg16" -> "tg16"
        lowId == "turbografxcd" || lowId == "tgcd" -> "tgcd"
        else -> lowId
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UltraDashboardContent(
    mainViewModel: MainViewModel,
    platforms: List<String>,
    initialIndex: Int,
    lang: String,
    hardwareState: HardwareUiState,
    soundManager: SoundManager,
    customMediaMap: Map<String, String>,
    userStatusManager: UserStatusManager,
    onFocused: (String) -> Unit,
    onSelect: (String) -> Unit,
    onLaunchGame: (Game) -> Unit,
    primaryColor: Color
) {
    val context = LocalContext.current
    val settingsManager = mainViewModel.settingsManager
    val fontScale = settingsManager.uiFontScale
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { platforms.size })
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    val achievements by mainViewModel.selectedGameAchievements.collectAsStateWithLifecycle()
    val raProfile by mainViewModel.userRaProfile.collectAsStateWithLifecycle()

    // Sin `by`: se pasa el State a AetherCard en vez de su valor. Con el valor,
    // el pulso (2 s en bucle, infinito) recomponia la tarjeta enfocada 60 veces
    // por segundo todo el tiempo que el dashboard estaba abierto, incluso en las
    // tarjetas de juego, donde ni se usa. Con el State, la lectura ocurre dentro
    // del Canvas y del graphicsLayer: se redibuja, que es barato, sin recomponer.
    val infiniteTransition = rememberInfiniteTransition(label = "aetherPulse")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(pagerState.currentPage) { 
        if (platforms.isNotEmpty()) {
            val currentId = platforms[pagerState.currentPage]
            onFocused(currentId)
            ThemeManager.adaptDNA(context, currentId)
        }
    }
    
    val currentPlatformId = if (platforms.isNotEmpty()) platforms[pagerState.currentPage] else ""
    
    val fullScreenBgPath = remember(currentPlatformId) {
        when {
            currentPlatformId == "SYS_TRIVIAL" -> "file:///android_asset/contentimg/IA.webp"
            currentPlatformId == "SYS_MANUAL" -> "file:///android_asset/contentimg/IA.webp"
            currentPlatformId == "SYS_HARDWARE" -> "file:///android_asset/contentimg/hardware.webp"
            currentPlatformId == "SYS_SETTINGS" -> "file:///android_asset/contentimg/ajustes.webp"
            currentPlatformId == "SYS_APPS" -> "file:///android_asset/contentimg/androidapps.webp"
            currentPlatformId == "SYS_TWEAKER" -> "file:///android_asset/contentimg/banner-neon.webp"
            currentPlatformId == "favoritos" || currentPlatformId == "recientes" -> "file:///android_asset/contentimg/iconoGA.webp"
            currentPlatformId.startsWith("SYS_") -> "file:///android_asset/contentimg/banner-neon.webp"
            else -> ThemeManager.getThemeImagePath("Retrofix-16_9", "${getRetrofixFilename(currentPlatformId)}.webp", customMediaMap, context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        // --- FONDO ATMOSFÉRICO ---
        AsyncImage(
            model = fullScreenBgPath,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.4f)
                // 40 dp a pantalla completa es el efecto mas caro de todo el tema.
                // SpeccyFx lo mantiene intacto en gama alta y lo reduce por debajo.
                .blur(SpeccyFx.blurRadius(40.dp)),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(primaryColor.copy(alpha = 0.2f), BlendMode.Color)
        )
        
        // Partículas sutiles de fondo (simuladas con un gradiente animado)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.05f), Color.Transparent),
                        radius = 2000f
                    )
                )
        )

        val userName by userStatusManager.userName.collectAsStateWithLifecycle()
        val userPhoto by userStatusManager.userPhotoUrl.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = userName.uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp * fontScale,
                fontWeight = FontWeight.Black,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 4f))
            )
            Spacer(Modifier.width(16.dp))
            AsyncImage(
                model = userPhoto.ifEmpty { "file:///android_asset/contentimg/iconoGA.webp" },
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Indicador de "AETHER ULTRA"
        Text(
            text = "AETHER ULTRA",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
                .alpha(0.5f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp * fontScale,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )

        HorizontalPager(
            state = pagerState, 
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (pagerState.currentPage > 0) { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }; true } 
                                else { scope.launch { pagerState.animateScrollToPage(platforms.size - 1) }; true }
                            }
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (pagerState.currentPage < platforms.size - 1) { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }; true } 
                                else { scope.launch { pagerState.animateScrollToPage(0) }; true }
                            }
                            NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_DPAD_CENTER -> {
                                onSelect(platforms[pagerState.currentPage]); true
                            }
                            else -> false
                        }
                    } else false
                },
            contentPadding = PaddingValues(horizontal = 180.dp)
        ) { page ->
            val id = platforms[page]
            val isCurrentPage = pagerState.currentPage == page
            val isSystem = id.startsWith("SYS_") || id == "favoritos" || id == "recientes"
            
            // currentPageOffsetFraction cambia en CADA frame del swipe. Leido aqui,
            // en el cuerpo del composable, recomponia la tarjeta entera -imagen,
            // gradientes, textos- 60 veces por segundo mientras se desliza. Dentro
            // de graphicsLayer la lectura es de fase de dibujo: mismo movimiento,
            // sin recomponer nada.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp)
                    .graphicsLayer {
                        val pageOffset =
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                        val s = 0.8f + (0.3f * fraction)
                        scaleX = s
                        scaleY = s
                        alpha = 0.3f + (0.7f * fraction)
                        rotationY = -15f * pageOffset
                        cameraDistance = 12f * density
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center
            ) {
                AetherCard(
                    id = id,
                    isSystem = isSystem,
                    isFocused = isCurrentPage,
                    primaryColor = primaryColor,
                    pulseAlpha = pulseAlpha,
                    customMediaMap = customMediaMap,
                    fontScale = fontScale
                )
            }
        }

        // DOCK INFERIOR BLINDADO Y VISIBLE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                .border(1.dp, primaryColor.copy(alpha = 0.1f))
        ) {
            val metadata = remember(currentPlatformId, lang) { 
                SystemMetadataManager.getMetadataForSystem(context, currentPlatformId, lang)
            }
            val displayNews = remember(currentPlatformId, metadata.description, lang) {
                getSystemNewsUltra(currentPlatformId, metadata.description, lang)
            }
            
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(30.dp).background(primaryColor.copy(alpha = 0.05f)).padding(horizontal = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = displayNews.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), 
                        fontSize = 12.sp * fontScale, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1, 
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 50.dp),
                        letterSpacing = 2.sp,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 8f))
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        SystemDockIcon(Icons.Default.Settings, "AJUSTES", primaryColor) { onSelect("SYS_SETTINGS") }
                        SystemDockIcon(Icons.Default.SportsEsports, "TRIVIAL", primaryColor) { onSelect("SYS_TRIVIAL") }
                        SystemDockIcon(Icons.Default.MenuBook, "MANUAL", primaryColor) { onSelect("SYS_MANUAL") }
                        SystemDockIcon(Icons.Default.Speed, "POTENCIA", primaryColor) { onSelect("SYS_BENCHMARK") }
                        SystemDockIcon(Icons.Default.Tune, "TWEAKER", primaryColor) { onSelect("SYS_TWEAKER") }
                        SystemDockIcon(Icons.Default.Apps, "ANDROID", primaryColor) { onSelect("SYS_APPS") }
                    }
                }
            }
        }

        // 🏆 BANNER DE RETROACHIEVEMENTS (Rediseñado)
        if (mainViewModel.settingsManager.isRAEnabled) {
            AchievementBanner(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                achievements = achievements,
                userProfile = raProfile,
                primaryColor = primaryColor,
                fontScale = fontScale
            )
        }
        
        StatusBarReal(hardwareState, primaryColor, soundManager)
    }
}

@Composable
fun AetherCard(
    id: String,
    isSystem: Boolean,
    isFocused: Boolean,
    primaryColor: Color,
    // State, no Float: se lee dentro del Canvas y del graphicsLayer, en fase de
    // dibujo, para que el pulso infinito no recomponga la tarjeta en cada frame.
    pulseAlpha: State<Float>,
    customMediaMap: Map<String, String>,
    fontScale: Float = 1f
) {
    val context = LocalContext.current
    val cardBgPath = when {
        id == "SYS_TRIVIAL" || id == "SYS_MANUAL" -> "file:///android_asset/contentimg/IA.webp"
        id == "SYS_HARDWARE" || id == "SYS_BENCHMARK" -> "file:///android_asset/contentimg/hardware.webp"
        id == "SYS_SETTINGS" -> "file:///android_asset/contentimg/ajustes.webp"
        id == "SYS_APPS" -> "file:///android_asset/contentimg/androidapps.webp"
        id == "SYS_TWEAKER" -> "file:///android_asset/contentimg/banner-neon.webp"
        id == "favoritos" || id == "recientes" -> "file:///android_asset/contentimg/iconoGA.webp"
        isSystem -> "file:///android_asset/contentimg/banner-neon.webp"
        else -> ThemeManager.getThemeImagePath("Retrofix-16_9", "${getRetrofixFilename(id)}.webp", customMediaMap, context)
    }

    Box(
        modifier = Modifier
            .size(500.dp, 280.dp)
            .shadow(
                elevation = if (isFocused) 60.dp else 0.dp,
                spotColor = primaryColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.sweepGradient(listOf(primaryColor, MaterialTheme.colorScheme.onSurface, primaryColor))
                } else {
                    SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                },
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // Imagen de fondo con filtro
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(cardBgPath)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(if (isSystem) 0.3f else 0.8f),
            contentScale = ContentScale.Crop
        )

        // Capa de degradado lateral para el texto
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        0.4f to Color.Transparent
                    )
                )
        )

        if (isSystem) {
            // Diseño especial para Sistemas: Glassmorphism y Centrado
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.1f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Icono estilizado
                    Box(contentAlignment = Alignment.Center) {
                        // Halo de luz detras del icono
                        Canvas(modifier = Modifier.size(140.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.4f * pulseAlpha.value), Color.Transparent)
                                )
                            )
                        }
                        Icon(
                            imageVector = getSystemIconM3(id),
                            contentDescription = null,
                            tint = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(90.dp)
                                .graphicsLayer {
                                    if (isFocused) {
                                        scaleX = 1f + (pulseAlpha.value * 0.05f)
                                        scaleY = 1f + (pulseAlpha.value * 0.05f)
                                    }
                                }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    val title = when (id) {
                        "SYS_SETTINGS" -> "AJUSTES DEL SISTEMA"
                        "SYS_TRIVIAL" -> "TRIVIAL ARCADE"
                        "SYS_MANUAL" -> "MANUAL"
                        "SYS_BENCHMARK" -> "PRUEBAS DE POTENCIA"
                        "SYS_TWEAKER" -> "NEBULA TWEAKER"
                        "SYS_APPS" -> "ANDROID APPS"
                        "SYS_ROULETTE" -> "RULETA RETRO"
                        "SYS_DNA" -> "TU ADN DE JUGADOR"
                        else -> id.replace("SYS_", "").uppercase()
                    }
                    
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp * fontScale,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp,
                        modifier = Modifier.alpha(if (isFocused) 1f else 0.6f)
                    )
                }
            }
        } else {
            // Diseño para Plataformas de Juegos
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = id.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp * fontScale,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(40.dp)
                        .background(primaryColor)
                )
            }
        }
        
        // Efecto de brillo de escaneo (innovador)
        if (isFocused) {
            val scanAnim = rememberInfiniteTransition().animateFloat(
                initialValue = -1f, targetValue = 2f,
                animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to Color.Transparent,
                            scanAnim.value to primaryColor.copy(alpha = 0.05f),
                            scanAnim.value + 0.1f to Color.Transparent,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                        )
                    )
            )
        }
    }
}

@Composable
fun AchievementBanner(
    modifier: Modifier,
    achievements: JSONObject?,
    userProfile: String,
    primaryColor: Color,
    fontScale: Float = 1f
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 2 },
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Perfil Flotante
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                modifier = Modifier.blur(0.5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(primaryColor, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        userProfile.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp * fontScale,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            if (achievements != null) {
                val earned = achievements.optInt("NumAwarded", 0)
                val total = achievements.optInt("NumAchievements", 0)
                
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = if (total > 0) earned.toFloat() / total else 0f
                    
                    // Barra de progreso minimalista
                    Box(modifier = Modifier.width(150.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(primaryColor))
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Text(
                        "$earned / $total",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp * fontScale,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}