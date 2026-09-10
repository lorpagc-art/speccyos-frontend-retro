package com.generacionarcade.speccyos

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * 📱 TEMA: SPECCY OS (Pixel / Android Aesthetic)
 * Optimización Final con persistencia de posición en el grid.
 * Soporte de Logos Integrados V0.7.0
 */

@Composable
fun SpeccyDesktopContent(
    mainViewModel: MainViewModel,
    userStatusManager: UserStatusManager,
    platforms: List<String>,
    lang: String,
    hardwareState: HardwareUiState,
    soundManager: SoundManager,
    initialIndex: Int,
    customMediaMap: Map<String, String>, // V0.6.0: Local Modding Support
    onIndexChanged: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onLaunchGame: (Game) -> Unit
) {
    val primaryColor = ThemeManager.primaryColor
    val context = LocalContext.current
    val settingsManager = mainViewModel.settingsManager
    val fontScale = settingsManager.uiFontScale
    
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (initialIndex >= 0) initialIndex else 0
    )
    val focusRequester = remember { FocusRequester() }

    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE d MMM", Locale.getDefault())
        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now).uppercase()
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AsyncImage(
            model = "file:///android_asset/contentimg/fondomultiple.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
            contentScale = ContentScale.Crop
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(text = timeText, color = MaterialTheme.colorScheme.onSurface, fontSize = 48.sp * fontScale, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                    Text(text = dateText, color = primaryColor, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HardwareWidgetMinimal(hardwareState, primaryColor, fontScale)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // remember y FUERA del grid: dentro del contenido de LazyVerticalGrid no
            // hay contexto @Composable, y sin remember los dos filtros se rehacian en
            // cada recomposicion.
            val gameSystems = remember(platforms) {
                platforms.filter { !it.startsWith("SYS_") && it != "favoritos" && it != "recientes" }
            }
            val collections = remember(platforms) {
                platforms.filter { it == "favoritos" || it == "recientes" }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)
            ) {
                itemsIndexed(collections, key = { _, id -> "col_" + id }) { index, id ->
                    SpeccyAppIcon(id, primaryColor, index == 0, customMediaMap, fontScale) { 
                        onIndexChanged(platforms.indexOf(id))
                        onSelect(id) 
                    }
                }
                
                itemsIndexed(gameSystems, key = { _, id -> "sys_" + id }) { index, id ->
                    SpeccyAppIcon(id, primaryColor, false, customMediaMap, fontScale) { 
                        onIndexChanged(platforms.indexOf(id))
                        onSelect(id) 
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))))
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                modifier = Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            ) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val dockSystems = listOf("SYS_SETTINGS", "SYS_TRIVIAL", "SYS_MANUAL", "SYS_APPS", "SYS_BENCHMARK")
                    dockSystems.forEach { id -> 
                        // Implementación en línea de AndroidDockIconItem temporal para solucionar error
                        var isFocused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(if (isFocused) 1.2f else 1f)
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(scale)
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelect(id) }
                                .background(
                                    if (isFocused) primaryColor.copy(alpha = 0.2f) else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = getSystemIconM3(id), contentDescription = id, tint = if (isFocused) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareWidgetMinimal(state: HardwareUiState, primaryColor: Color, fontScale: Float = 1f) {
    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val isOverheating = state.temperature > 85f
            val tempColor = if(isOverheating) MaterialTheme.colorScheme.error else primaryColor
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CPU", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                Text("${state.cpuLoad.toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black)
            }
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RAM", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                Text("${state.ramUsage.toInt()}%", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black)
            }
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TEMP", color = tempColor.copy(alpha = 0.7f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Bold)
                Text("${state.temperature.toInt()}°", color = tempColor, fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun SpeccyAppIcon(id: String, primaryColor: Color, isFirst: Boolean, customMediaMap: Map<String, String>, fontScale: Float = 1f, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")
    val isSpecial = id == "favoritos" || id == "recientes"
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(85.dp)
                .shadow(if (isFocused) 15.dp else 0.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(if (isFocused) primaryColor else if (isSpecial) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(width = if (isFocused) 2.dp else 1.dp, color = if (isFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape = RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSpecial) { 
                Icon(imageVector = getSystemIconM3(id), contentDescription = id, tint = if (isFocused) speccyContentColorOn(primaryColor) else primaryColor, modifier = Modifier.size(32.dp)) 
            } else {
                val logoName = "${id.lowercase()}.webp"
                val iconPath = ThemeManager.getThemeImagePath("logos", logoName, customMediaMap, context)
                AsyncImage(model = iconPath, contentDescription = id, modifier = Modifier.fillMaxSize(0.6f), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = id.uppercase(), color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp * fontScale, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(85.dp))
    }
}