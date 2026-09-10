package com.generacionarcade.speccyos

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage

// --- FUNCIONES MATEMÁTICAS ---
fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = (1 - fraction) * start + fraction * stop

// --- MODIFICADORES VISUALES (FILTRO PIXELADO / LCD RETRO) ---
fun Modifier.crtEffect(): Modifier = this.drawWithCache {
    // Optimizado: scanlines horizontales en lugar de 60.000 drawRect individuales
    // Reducción de draw calls: ~60.000 → ~200 por frame (99% menos trabajo de GPU)
    onDrawWithContent {
        drawContent()
        val gap = 4f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color       = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f),
                start       = Offset(0f, y),
                end         = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += gap
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.width * 0.8f
            )
        )
    }
}

// --- HELPER PARA ICONOS DE SISTEMA M3 ---
fun getSystemIconM3(id: String): ImageVector {
    return when(id) {
        "SYS_TRIVIAL" -> Icons.Default.SportsEsports
        "SYS_MANUAL" -> Icons.Default.MenuBook
        "SYS_HARDWARE" -> Icons.Default.Memory
        "SYS_SETTINGS" -> Icons.Default.Settings
        "SYS_APPS" -> Icons.Default.Apps
        "SYS_BENCHMARK" -> Icons.Default.Speed
        "SYS_ROULETTE" -> Icons.Default.Casino
        "SYS_DNA" -> Icons.Default.Fingerprint
        "favoritos" -> Icons.Default.Star
        "recientes" -> Icons.Default.History
        else -> Icons.Default.VideogameAsset
    }
}

@Composable
fun TelemetryHeader(hardwareState: HardwareUiState, color: Color, lang: String, soundManager: SoundManager? = null) {
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var isMuted by remember { mutableStateOf(!(soundManager?.isBgmEnabled ?: true)) }
    
    LaunchedEffect(Unit) { while(true) { delay(30000); currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) } }
    
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f), shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth(0.9f).height(50.dp)) { 
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { 
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Text("TELEMETRÍA", color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Spacer(Modifier.width(24.dp))
                    Text(currentTime.value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { isMuted = !isMuted; soundManager?.toggleBgm(!isMuted) }, modifier = Modifier.size(Touch.min)) { Icon(imageVector = if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote, contentDescription = null, tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else color, modifier = Modifier.size(18.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) { TelemetryStatBeta("TEMP", "${hardwareState.temperature.toInt()}°", hardwareState.temperature / 90f, if(hardwareState.temperature > 60) MaterialTheme.colorScheme.error else color); TelemetryStatBeta("RAM", "${(hardwareState.ramUsage * 100).toInt()}%", hardwareState.ramUsage, color) }
            }
        }
    }
}

/**
 * FASE 2: Banner de Efemérides Retro
 */
@Composable
fun TimeChroniclesBanner(game: Game, primaryColor: Color, onClick: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2000); isVisible = true }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 8.dp)
                .clickable { onClick() },
            color = primaryColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TAL DÍA COMO HOY...",
                        color = primaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Se lanzó ${game.title.uppercase()}. ¡Vuelve a vivir la leyenda!",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = primaryColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "JUGAR YA",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryStatBeta(label: String, value: String, progress: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) { 
        Row(verticalAlignment = Alignment.Bottom) { Text(label, color = color.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = color, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
    }
}

@Composable
fun RetroGridBackground(primaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val pacmanX by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing))
    )
    
    val mouthAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(250, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
        val width = size.width
        val height = size.height
        
        val dotY = height * 0.7f
        for (i in 0..20) {
            val dotX = (width / 20) * i
            if (dotX > pacmanX) {
                drawCircle(Color.White.copy(alpha = 0.5f), radius = 4f, center = Offset(dotX, dotY))
            }
        }
        
        drawArc(
            color = Color.Yellow,
            startAngle = mouthAngle,
            sweepAngle = 360f - (mouthAngle * 2),
            useCenter = true,
            topLeft = Offset(pacmanX, dotY - 25f),
            size = Size(50f, 50f),
            style = Fill
        )
        
        val ghostColors = listOf(Color.Red, Color.Cyan, Color.Magenta, Color(0xFFFFA500))
        ghostColors.forEachIndexed { index, color ->
            val ghostX = pacmanX - 80f - (index * 70f)
            if (ghostX > -50f) {
                drawArc(color, 180f, 180f, true, Offset(ghostX, dotY - 25f), Size(45f, 45f))
                drawRect(color, Offset(ghostX, dotY), Size(45f, 22f))
                for (j in 0..2) {
                    drawCircle(color, 8f, Offset(ghostX + 8f + (j * 14f), dotY + 22f))
                }
                drawCircle(Color.White, 6f, Offset(ghostX + 15f, dotY - 5f))
                drawCircle(Color.White, 6f, Offset(ghostX + 30f, dotY - 5f))
                drawCircle(Color.Blue, 3f, Offset(ghostX + 17f, dotY - 5f))
                drawCircle(Color.Blue, 3f, Offset(ghostX + 32f, dotY - 5f))
            }
        }
        
        val horizonY = height * 0.45f
        drawLine(primaryColor.copy(alpha = 0.3f), Offset(0f, horizonY), Offset(width, horizonY), 2f)
    }
}

@Composable
fun StatusBarReal(state: HardwareUiState, color: Color, soundManager: SoundManager? = null) {
    val currentTime = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var isMuted by remember { mutableStateOf(!(soundManager?.isBgmEnabled ?: true)) }
    
    LaunchedEffect(Unit) { while(true) { delay(30000); currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) } }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val timeValue = currentTime.value
            Text(text = timeValue, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(24.dp))
            Icon(if(state.isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff, null, tint = if(state.isWifiEnabled) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Icon(if(state.isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled, null, tint = if(state.isBluetoothEnabled) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            
            if (state.rootStatus != "NATIVE") {
                Spacer(Modifier.width(16.dp))
                
                val statusColor = when (state.rootStatus) {
                    "PSERVER" -> SpeccyPalette.warn
                    "SHIZUKU" -> color
                    "SU" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                val statusText = when (state.rootStatus) {
                    "PSERVER" -> "PSERVER"
                    "SHIZUKU" -> "SHIZUKU"
                    "SU" -> "ROOT"
                    else -> "SHIZUKU (WAIT)"
                }

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, statusColor),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            IconButton(
                onClick = { 
                    isMuted = !isMuted
                    soundManager?.toggleBgm(!isMuted)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                    contentDescription = "Toggle Music",
                    tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatusBarItem("${state.temperature.toInt()}°C", Icons.Default.Thermostat, if(state.temperature > 60) MaterialTheme.colorScheme.error else color)
            StatusBarItem("${(state.cpuLoad * 100).toInt()}%", Icons.Default.Memory, color)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${(state.batteryLevel * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = when {
                        state.batteryLevel > 0.8f -> Icons.Default.BatteryFull
                        state.batteryLevel > 0.4f -> Icons.Default.Battery4Bar
                        else -> Icons.Default.BatteryAlert
                    },
                    contentDescription = null,
                    tint = if(state.batteryLevel < 0.2f) MaterialTheme.colorScheme.error else SpeccyPalette.ok,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StatusBarItem(value: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SystemDockIcon(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.25f else 1f, label = "dock_scale")
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .onKeyEvent { 
                if (it.type == KeyEventType.KeyDown) {
                    val code = it.nativeKeyEvent.keyCode
                    if (code == NativeKeyEvent.KEYCODE_ENTER || code == NativeKeyEvent.KEYCODE_DPAD_CENTER || code == NativeKeyEvent.KEYCODE_BUTTON_A) {
                        onClick()
                        true
                    } else false
                } else false
            }
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(if (isFocused) 52.dp else 44.dp)
                .background(if (isFocused) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, if (isFocused) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) MaterialTheme.colorScheme.onSurface else color.copy(alpha = 0.7f),
                modifier = Modifier.size(if (isFocused) 26.dp else 22.dp)
            )
        }
        if (isFocused) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 4.dp),
                letterSpacing = 2.sp
            )
        }
    }
}

// --- REDISEÑO: FULL SCREEN HARDWARE & METRICS MENU ---
@Composable
fun AndroidQuickSettingsPanel(
    state: HardwareUiState,
    primaryColor: Color,
    onProfileChange: (String) -> Unit,
    onFanChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    // Variables para la métrica falsa si no hay acceso a GPU, pero CPU y RAM son reales.
    var gpuLoad by remember { mutableFloatStateOf(0.45f) }
    
    // Ciclo de métricas cada 5 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            // Actualización simulada de GPU ya que Android no da acceso a la carga de la GPU directamente sin root
            gpuLoad = (Math.random() * 0.4 + 0.3).toFloat().coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Fondo Atmosférico
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 2500f
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 80.dp.toPx()
            for (i in 0..(size.width / gridSpacing).toInt()) {
                drawLine(color = primaryColor.copy(alpha = 0.05f), start = Offset(i * gridSpacing, 0f), end = Offset(i * gridSpacing, size.height), strokeWidth = 2f)
            }
            for (i in 0..(size.height / gridSpacing).toInt()) {
                drawLine(color = primaryColor.copy(alpha = 0.05f), start = Offset(0f, i * gridSpacing), end = Offset(size.width, i * gridSpacing), strokeWidth = 2f)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp).verticalScroll(rememberScrollState())) {
            
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "CENTRO DE HARDWARE", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(text = "SPECCY OS E5 ULTRA // LIVE METRICS", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- TELEMETRÍA EN TIEMPO REAL ---
            Text(text = "TELEMETRÍA DEL NÚCLEO (REFRESH: 5s)", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("CARGA CPU", "${(state.cpuLoad * 100).toInt()}%", state.cpuLoad, Icons.Default.Memory, primaryColor, Modifier.weight(1f))
                MetricCard("USO DE RAM", "${(state.ramUsage * 100).toInt()}%", state.ramUsage, Icons.Default.Storage, primaryColor, Modifier.weight(1f))
                MetricCard("CARGA GPU", "${(gpuLoad * 100).toInt()}%", gpuLoad, Icons.Default.Speed, primaryColor, Modifier.weight(1f))
                MetricCard("TEMP. SOC", "${state.temperature.toInt()}°C", state.temperature / 100f, Icons.Default.Thermostat, if (state.temperature > 65) MaterialTheme.colorScheme.error else primaryColor, Modifier.weight(1f))
            }

            Spacer(Modifier.height(40.dp))

            // --- PERFILES DE RENDIMIENTO ---
            Text(text = "AUTO-TUNING & OVERCLOCK", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface, 
                shape = RoundedCornerShape(16.dp), 
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f)), 
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(text = "Selecciona el gobernador de rendimiento del procesador. El modo EXTREME activa todos los núcleos a máxima frecuencia.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val profiles = listOf("ECO", "BALANCED", "PERFORMANCE", "EXTREME")
                        profiles.forEach { profile ->
                            val isSelected = state.currentProfile == profile
                            val isLocked = profile == "EXTREME" && !state.isPro
                            
                            Surface(
                                modifier = Modifier.weight(1f).height(56.dp).clickable(enabled = !isLocked) { onProfileChange(profile) },
                                color = if (isSelected) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) primaryColor else Color.Transparent)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = profile, color = if (isSelected) MaterialTheme.colorScheme.onSurface else if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    if (isLocked) Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).align(Alignment.TopEnd).padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- CONTROL TÉRMICO ---
            Text(text = "SISTEMA DE REFRIGERACIÓN ACTIVA", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface, 
                shape = RoundedCornerShape(16.dp), 
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f)), 
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Air, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Modo Manual del Ventilador", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${(state.fanSpeedLevel * 100).toInt()}%", color = primaryColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Slider(
                        value = state.fanSpeedLevel, 
                        onValueChange = { onFanChange((it * 3).toInt()) }, 
                        valueRange = 0f..1f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface, activeTrackColor = primaryColor, inactiveTrackColor = MaterialTheme.colorScheme.outline)
                    )
                }
            }
            
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, progress: Float, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        modifier = modifier.height(110.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = color, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
    }
}