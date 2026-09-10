package com.generacionarcade.speccyos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.generacionarcade.speccyos.network.ScrapeCandidate
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScraperDashboardDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val scrapedCount by mainViewModel.scrapedGamesCount.collectAsState(initial = 0)
    val missingCount by mainViewModel.missingMediaCount.collectAsState(initial = 0)
    val stats by mainViewModel.scrapingStatsByPlatform.collectAsState(initial = emptyList())
    val cacheSize by mainViewModel.mediaCacheSize.collectAsStateWithLifecycle()

    val totalCount = scrapedCount + missingCount
    val globalProgress = if (totalCount > 0) scrapedCount.toFloat() / totalCount else 0f
    
    val primaryColor = ThemeManager.primaryColor
    val scrollState = rememberScrollState()
    
    var showRematchForPlatform by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf("PROGRESS") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                // HEADER
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusableIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, primaryColor = primaryColor, onClick = onDismiss)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("CENTRO DE CONTROL SCRAPER", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("Sincronización de metadatos y arte Ultra", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(4.dp)) {
                        DashboardTabButton("ESTADÍSTICAS", selectedTab == "PROGRESS", primaryColor) { selectedTab = "PROGRESS" }
                        DashboardTabButton("MANTENIMIENTO", selectedTab == "MAINTENANCE", primaryColor) { selectedTab = "MAINTENANCE"; mainViewModel.calculateMediaCacheSize() }
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (selectedTab == "PROGRESS") {
                    // CARDS DE ESTADO
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardStatCard("TOTAL JUEGOS", totalCount.toString(), Icons.Default.Gamepad, Modifier.weight(1f))
                        DashboardStatCard("COMPLETADO", scrapedCount.toString(), Icons.Default.CheckCircle, Modifier.weight(1f), primaryColor)
                        DashboardStatCard("PENDIENTE", missingCount.toString(), Icons.Default.Pending, Modifier.weight(1f), MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(32.dp))

                    // BARRA DE PROGRESO GLOBAL
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ESTADO GLOBAL DE LA BIBLIOTECA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(globalProgress * 100).toInt()}%", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { globalProgress },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                            color = primaryColor,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Text("DESGLOSE POR SISTEMA", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stats.sortedBy { it.platformId }.forEach { stat ->
                            PlatformScrapingRow(
                                stat = stat, 
                                primaryColor = primaryColor, 
                                onScrape = { mainViewModel.forceScrapePlatform(stat.platformId) }, 
                                onManualRematch = { showRematchForPlatform = stat.platformId }
                            )
                        }
                    }
                } else {
                    MaintenanceView(mainViewModel, cacheSize, primaryColor)
                }
                
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    if (showRematchForPlatform != null) {
        ManualRematchScreen(showRematchForPlatform!!, mainViewModel) { showRematchForPlatform = null }
    }
}

@Composable
fun MaintenanceView(mainViewModel: MainViewModel, cacheSize: String, primaryColor: Color) {
    Column(Modifier.fillMaxWidth()) {
        DashboardStatCard("TAMAÑO DE LA CACHÉ", cacheSize, Icons.Default.Storage, Modifier.fillMaxWidth(), primaryColor)
        Spacer(Modifier.height(32.dp))
        Text("ACCIONES DE LIMPIEZA", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaintenanceActionRow(title = "PURGAR VÍDEOS MP4", desc = "Recuperar espacio masivo.", icon = Icons.Default.VideoFile, color = MaterialTheme.colorScheme.error) { mainViewModel.purgeVideos() }
            MaintenanceActionRow(title = "EXPORTAR METADATOS", desc = "Backup JSON de tu biblioteca.", icon = Icons.Default.Save, color = SpeccyPalette.ok) { }
        }
    }
}

@Composable
fun MaintenanceActionRow(title: String, desc: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.focusable(enabled).clickable(enabled = enabled) { onClick() }.scale(if (isFocused) 1.03f else 1f),
        color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isFocused) color else if (enabled) color.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isFocused) color else if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (isFocused || enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(desc, color = if (isFocused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DashboardTabButton(label: String, isSelected: Boolean, primaryColor: Color, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.focusable().clickable { onClick() },
        color = if (isSelected) primaryColor else if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ManualRematchScreen(platformId: String, mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val gamesFlow = remember(platformId) { mainViewModel.getGamesForPlatform(platformId) }
    val games by gamesFlow.collectAsState(initial = emptyList())
    val missingGames = games.filter { it.boxArt == null }
    val candidates by mainViewModel.scrapeCandidates.collectAsStateWithLifecycle()
    val isSearching by mainViewModel.isSearchingManual.collectAsStateWithLifecycle()
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusableIconButton(icon = Icons.Default.Close, primaryColor = ThemeManager.primaryColor, onClick = onDismiss)
                    Text("REMATCH MANUAL: ${platformId.uppercase()}", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(0.4f)) {
                        Text("JUEGOS PENDIENTES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(missingGames) { game ->
                                var isFocused by remember { mutableStateOf(false) }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.focusable().clickable { selectedGame = game; searchQuery = game.title; mainViewModel.searchManual(game.title) },
                                    color = if (selectedGame?.path == game.path) ThemeManager.primaryColor.copy(alpha = 0.3f) else if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text(game.title, color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                    Column(Modifier.weight(0.6f)) {
                        if (selectedGame != null) {
                            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar...") }, trailingIcon = { IconButton(onClick = { mainViewModel.searchManual(searchQuery) }) { Icon(Icons.Default.Search, null, tint = ThemeManager.primaryColor) } })
                            Spacer(Modifier.height(24.dp))
                            if (isSearching) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ThemeManager.primaryColor) } }
                            else {
                                LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Se captura a un val: el !! vivia dentro de una lambda de
                                    // clic, que corre en un instante posterior a la comprobacion
                                    // de null, no en la misma composicion.
                                    val gameToRematch = selectedGame
                                    items(candidates, key = { it.id }) { candidate ->
                                        CandidateCard(candidate) {
                                            if (gameToRematch != null) {
                                                mainViewModel.applyManualCandidate(gameToRematch, candidate)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CandidateCard(candidate: ScrapeCandidate, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(180.dp).onFocusChanged { isFocused = it.isFocused }.focusable().clickable(onClick = onClick).scale(if (isFocused) 1.05f else 1f),
        color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(model = candidate.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp), shape = RoundedCornerShape(4.dp)) { Text(candidate.source, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            }
            Text(text = candidate.title, color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DashboardStatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accentColor: Color = SpeccyPalette.onSurface) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(16.dp))
            Text(value, color = accentColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PlatformScrapingRow(stat: ScrapingStat, primaryColor: Color, onScrape: () -> Unit, onManualRematch: () -> Unit) {
    val progress = if (stat.total > 0) stat.scraped.toFloat() / stat.total else 0f
    var isRowFocused by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().onFocusChanged { isRowFocused = it.isFocused }.focusable(), color = if (isRowFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isRowFocused) primaryColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.03f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stat.platformId.uppercase(), color = if (isRowFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black, fontSize = 14.sp); Spacer(Modifier.width(8.dp)); Text("${stat.scraped}/${stat.total}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = if (progress == 1f) SpeccyPalette.ok else primaryColor, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }
            Spacer(Modifier.width(24.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FocusableIconButton(icon = Icons.Default.Edit, primaryColor = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onManualRematch); FocusableIconButton(icon = if (progress == 1f) Icons.Default.Refresh else Icons.Default.CloudDownload, primaryColor = if (progress == 1f) MaterialTheme.colorScheme.onSurfaceVariant else primaryColor, onClick = onScrape) }
        }
    }
}

@Composable
fun FocusableIconButton(icon: ImageVector, primaryColor: Color, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(onClick = onClick, modifier = Modifier.size(Touch.min).onFocusChanged { isFocused = it.isFocused }.background(if (isFocused) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.05f), CircleShape).border(if (isFocused) BorderStroke(2.dp, primaryColor) else BorderStroke(0.dp, Color.Transparent), CircleShape)) { Icon(icon, null, tint = if (isFocused) MaterialTheme.colorScheme.onSurface else primaryColor, modifier = Modifier.size(18.dp)) }
}
