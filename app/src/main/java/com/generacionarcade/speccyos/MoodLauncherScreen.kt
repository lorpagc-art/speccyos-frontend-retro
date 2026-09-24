/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

/**
 * MoodLauncherScreen — "¿Cómo estás hoy?" Descubrimiento por estado de ánimo.
 *
 * En vez de buscar por plataforma, el usuario dice cómo se siente y el sistema
 * sugiere juegos de su propia biblioteca que encajan con ese mood.
 * Prioriza juegos con cover art disponible y los que no se han jugado recientemente.
 *
 * INTEGRACIÓN en SpeccyDashboard.kt:
 *   var isMoodOpen by remember { mutableStateOf(false) }
 *   if (isMoodOpen) MoodLauncherScreen(
 *       mainViewModel = mainViewModel,
 *       onBack = { isMoodOpen = false },
 *       onGameSelected = { game -> isMoodOpen = false; launchGame(game) }
 *   )
 */

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ── Definición de humores ──────────────────────────────────────────

private data class Mood(
    val id: String,
    val emoji: String,
    val name: String,
    val subtitle: String,
    val color: Color,
    val genres: List<String>,        // Géneros preferidos (busca en game.genre)
    val preferredPlatforms: List<String>, // Plataformas que encajan
    val maxDuration: Int?            // null = cualquiera, N = sesiones cortas (alto playCount)
)

private val MOODS = listOf(
    Mood("action", "⚡", "TENGO ADRENALINA",
        "Beat'em ups · Shoot'em ups · Peleas",
        Color(0xFFFF3D00),
        listOf("Action", "Fighting", "Beat-em-up", "Shoot'em-up", "Shooter"),
        listOf("arcade", "mame", "fbneo", "snes", "genesis", "neogeo"),
        null),

    Mood("relax", "☕", "MODO RELAX",
        "Puzzles · Plataformas · RPGs tranquilos",
        Color(0xFF00E676),
        listOf("Puzzle", "Platform", "RPG", "Adventure", "Simulation"),
        listOf("gba", "snes", "nes", "gb", "gbc"),
        null),

    Mood("challenge", "🔥", "QUIERO UN DESAFÍO",
        "Juegos difíciles · Sin continues · 1CC",
        Color(0xFFFF0055),
        listOf("Action", "Shooter", "Fighting", "Platform"),
        listOf("arcade", "nes", "mame", "fbneo", "megadrive"),
        null),

    Mood("nostalgia", "🌙", "NOSTALGIA PURA",
        "Solo los clásicos de antes de 1990",
        Color(0xFF9C27B0),
        emptyList(), // Se filtra por fecha
        listOf("nes", "atari2600", "c64", "arcade", "zxspectrum", "msx"),
        null),

    Mood("quick", "⏱", "TENGO 10 MINUTOS",
        "Sesiones cortas · Arcade · High scores",
        Color(0xFFFFD600),
        listOf("Action", "Arcade", "Sports"),
        listOf("arcade", "mame", "fbneo"),
        null),

    Mood("discover", "🔍", "DESCUBRIR ALGO NUEVO",
        "Juegos de tu biblioteca que nunca tocaste",
        Color(0xFF00F2FF),
        emptyList(),
        emptyList(),
        null)
)

// ── Lógica de filtrado/scoring ────────────────────────────────────

private fun scoreGame(game: Game, mood: Mood, now: Long): Float {
    var score = 0f

    // Penalizar jugados hace menos de 7 días
    val daysSincePlayed = (now - game.lastPlayed) / 86_400_000L
    if (game.lastPlayed > 0 && daysSincePlayed < 7) score -= 30f

    // Preferir juegos con carátula
    if (game.boxArt != null) score += 10f

    when (mood.id) {
        "nostalgia" -> {
            val year = game.year?.toIntOrNull() ?: 2000
            if (year < 1990) score += 50f else return -1000f
            if (game.platformId.lowercase() in mood.preferredPlatforms) score += 15f
        }
        "discover" -> {
            if (game.playCount == 0) score += 80f
            if (game.lastPlayed == 0L) score += 20f
        }
        "quick" -> {
            if (game.platformId.lowercase() in mood.preferredPlatforms) score += 30f
            val genero = game.genre
            if (!genero.isNullOrBlank() && mood.genres.any { genero.contains(it, true) }) score += 20f
        }
        else -> {
            val genero = game.genre
            if (!genero.isNullOrBlank() && mood.genres.any { genero.contains(it, true) }) score += 40f
            if (game.platformId.lowercase() in mood.preferredPlatforms) score += 20f
        }
    }

    // Bonus si tiene rating alto
    if (game.rating > 0.7f) score += 5f

    // Shuffle leve para variedad
    score += Random.nextFloat() * 10f

    return score
}

// ── Composable ────────────────────────────────────────────────────

@Composable
fun MoodLauncherScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onGameSelected: (Game) -> Unit
) {
    val context      = LocalContext.current
    val primaryColor = ThemeManager.primaryColor
    val gameDao      = remember { AppDatabase.getDatabase(context).gameDao() }
    val now          = remember { System.currentTimeMillis() }

    var selectedMood  by remember { mutableStateOf<Mood?>(null) }
    var suggestions   by remember { mutableStateOf<List<Game>>(emptyList()) }
    var allGames      by remember { mutableStateOf<List<Game>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGamesList()
            withContext(kotlinx.coroutines.Dispatchers.Main) { allGames = games }
        }
    }

    LaunchedEffect(selectedMood, allGames) {
        val mood = selectedMood ?: return@LaunchedEffect
        isLoading = true
        withContext(Dispatchers.IO) {
            val scored = allGames
                .map { it to scoreGame(it, mood, now) }
                .filter { it.second > -999f }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                suggestions = scored
                isLoading   = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind { drawLine(primaryColor.copy(.18f), Offset(0f,size.height), Offset(size.width,size.height), 1f) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(Touch.min)) {
                    Icon(Icons.Default.ArrowBack, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("LANZADOR DE HUMOR", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("¿Cómo estás hoy?", color = primaryColor.copy(.6f), fontSize = 12.sp)
                }
            }

            // ── Grid de humores ──
            LazyVerticalGrid(
                columns       = GridCells.Fixed(3),
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                items(MOODS) { mood ->
                    val isSelected = selectedMood?.id == mood.id
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) mood.color.copy(.15f) else MaterialTheme.colorScheme.background)
                            .border(
                                1.dp,
                                if (isSelected) mood.color.copy(.7f) else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMood = mood }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(mood.emoji, fontSize = 26.sp)
                        Text(mood.name, color = if (isSelected) mood.color else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(mood.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 16.sp)
                    }
                }
            }

            // ── Sugerencias ──
            AnimatedVisibility(
                visible = selectedMood != null,
                enter   = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit    = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    val mood = selectedMood
                    if (mood != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(mood.color))
                            Spacer(Modifier.width(8.dp))
                            Text("SUGERIDOS · ${mood.name}", color = mood.color.copy(.8f),
                                fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.weight(1f))
                            if (suggestions.isNotEmpty())
                                Text("${suggestions.size} juegos", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f), fontSize = 12.sp)
                        }
                    }

                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(color = selectedMood?.color ?: primaryColor, modifier = Modifier.size(32.dp))
                        }
                    } else if (suggestions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("No hay juegos para este humor\nen tu biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(8.dp),
                            contentPadding        = PaddingValues(bottom = 16.dp)
                        ) {
                            // Sin key, Compose reutiliza los slots por posicion: al
                            // cambiar de humor la lista se sustituye entera y las
                            // caratulas parpadean recargandose aunque el juego de esa
                            // posicion sea el mismo.
                            items(suggestions, key = { it.path }) { game ->
                                MoodGameCard(game, selectedMood?.color ?: primaryColor) { onGameSelected(game) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodGameCard(game: Game, accentColor: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, accentColor.copy(.15f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (game.boxArt != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(game.boxArt).crossfade(false).build(),
                    contentDescription = game.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(game.title.take(1).uppercase(), color = accentColor, fontSize = 32.sp, fontWeight = FontWeight.Black)
            }
            if (game.playCount == 0) {
                Box(Modifier.fillMaxSize().padding(4.dp), Alignment.TopStart) {
                    Text("NUEVO",
                        modifier = Modifier.clip(RoundedCornerShape(3.dp))
                            .background(accentColor).padding(horizontal = 4.dp, vertical = 1.dp),
                        color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Column(Modifier.padding(6.dp)) {
            Text(game.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(game.platformId.take(5).uppercase(), color = accentColor.copy(.6f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}
