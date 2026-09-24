/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

/**
 * SmartSearchScreen — búsqueda en tiempo real sobre toda la biblioteca.
 *
 * INTEGRACIÓN en SpeccyDashboard.kt:
 *   var isSearchOpen by remember { mutableStateOf(false) }
 *   if (isSearchOpen) SmartSearchScreen(mainViewModel, { isSearchOpen = false }) { game ->
 *       isSearchOpen = false
 *       // lanzar juego
 *   }
 *
 * Añade un botón de búsqueda donde quieras (TelemetryHeader, etc.):
 *   IconButton(onClick = { isSearchOpen = true }) {
 *       Icon(Icons.Default.Search, null, tint = primaryColor)
 *   }
 */
@OptIn(FlowPreview::class)
@Composable
fun SmartSearchScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onGameSelected: (Game) -> Unit
) {
    val context      = LocalContext.current
    val primaryColor = ThemeManager.primaryColor
    val gameDao      = remember { AppDatabase.getDatabase(context).gameDao() }

    var queryText by remember { mutableStateOf("") }
    var selectedPlatformFilter by remember { mutableStateOf<String?>(null) }

    val queryFlow = remember { MutableStateFlow("") }
    LaunchedEffect(queryText) { queryFlow.value = queryText }

    val searchResults by queryFlow
        .debounce(200)
        .flatMapLatest { q ->
            if (q.length < 2) flowOf(emptyList())
            else gameDao.searchGames("%${q.lowercase()}%")
        }
        .collectAsState(initial = emptyList())

    val filteredResults = remember(searchResults, selectedPlatformFilter) {
        if (selectedPlatformFilter == null) searchResults
        else searchResults.filter { it.platformId == selectedPlatformFilter }
    }
    val platformsInResults = remember(searchResults) {
        searchResults.map { it.platformId }.distinct().sorted()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (_: Exception) {} }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Barra de búsqueda
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind { drawLine(primaryColor.copy(.2f), Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Touch.min)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                Icon(Icons.Default.Search, null, tint = primaryColor.copy(.7f), modifier = Modifier.size(20.dp))
                Box(
                    modifier = Modifier.weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (queryText.isEmpty()) {
                        Text("Buscar en toda la biblioteca...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f), fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = queryText, onValueChange = { queryText = it },
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(primaryColor),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true
                    )
                }
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = { queryText = "" }, modifier = Modifier.size(Touch.min)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                AnimatedVisibility(visible = queryText.length >= 2) {
                    Text("${filteredResults.size}", color = primaryColor.copy(.7f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }

            // Chips de filtro por plataforma
            AnimatedVisibility(visible = platformsInResults.isNotEmpty(), enter = expandVertically(tween(200)), exit = shrinkVertically(tween(150))) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SearchFilterChip("Todo", selectedPlatformFilter == null, primaryColor) { selectedPlatformFilter = null }
                    }
                    items(platformsInResults) { platformId ->
                        SearchFilterChip(platformId.uppercase(), selectedPlatformFilter == platformId, primaryColor) {
                            selectedPlatformFilter = if (selectedPlatformFilter == platformId) null else platformId
                        }
                    }
                }
            }

            when {
                queryText.length < 2 -> SearchEmptyHint("Escribe al menos 2 caracteres", Icons.Default.Search, primaryColor)
                filteredResults.isEmpty() -> SearchEmptyHint("Sin resultados para \"$queryText\"", Icons.Default.SearchOff, primaryColor)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredResults, key = { it.path }) { game ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background)
                                .border(1.dp, primaryColor.copy(.1f), RoundedCornerShape(10.dp))
                                .clickable { onGameSelected(game) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (game.boxArt != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(game.boxArt).crossfade(false).build(),
                                        contentDescription = null, modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Text(game.title.take(1).uppercase(), color = primaryColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(game.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(game.platformId.uppercase(),
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(primaryColor.copy(.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    val desarrollador = game.developer
                                    if (!desarrollador.isNullOrBlank() && desarrollador != "Desconocido")
                                        Text(desarrollador, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f), fontSize = 12.sp)
                                    game.year?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f), fontSize = 12.sp) }
                                }
                            }
                            if (game.playCount > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayArrow, null, tint = primaryColor.copy(.5f), modifier = Modifier.size(14.dp))
                                    Text("${game.playCount}", color = primaryColor.copy(.5f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.3f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(label: String, isActive: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) color.copy(.2f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isActive) color.copy(.6f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(.7f),
            fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal)
    }
}

@Composable
private fun SearchEmptyHint(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primaryColor: Color) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = primaryColor.copy(.25f), modifier = Modifier.size(64.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f), fontSize = 13.sp)
        }
    }
}
