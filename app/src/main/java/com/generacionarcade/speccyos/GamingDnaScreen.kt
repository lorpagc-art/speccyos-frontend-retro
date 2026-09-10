package com.generacionarcade.speccyos

/**
 * GamingDnaScreen — Tu identidad como jugador retro.
 *
 * Calcula y muestra de forma hermosa:
 *   · Distribución de tiempo por plataforma (hexágonos con %)
 *   · Personalidad gamer deducida (8 arquetipos)
 *   · Top 5 juegos más jugados con barra de progreso
 *   · Estadísticas de vida: horas totales, racha, colección
 *
 * INTEGRACIÓN en SpeccyDashboard.kt:
 *   var isDnaOpen by remember { mutableStateOf(false) }
 *   if (isDnaOpen) GamingDnaScreen(mainViewModel) { isDnaOpen = false }
 *
 *   // Botón (p.ej. junto al avatar de usuario):
 *   IconButton(onClick = { isDnaOpen = true }) {
 *       Icon(Icons.Default.Biotech, null, tint = primaryColor)
 *   }
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

// ── Arquetipos gamer basados en plataforma dominante + horas ──────

private enum class GamerArchetype(
    val title: String,
    val subtitle: String,
    val color: Color
) {
    ARCADE_WARRIOR  ("El Guerrero de Arcade",   "Amas los coins, odias los continues. High score o muerte.", Color(0xFFFFD600)),
    NINTENDO_KNIGHT ("El Caballero Nintendo",   "Saltar, rescatar, completar. La perfección por encima de todo.", Color(0xFFFF0000)),
    SEGA_REBEL      ("El Rebelde SEGA",         "Velocidad, estilo, actitud. Nintendo nunca te entendió.", Color(0xFF0088FF)),
    RETRO_SCHOLAR   ("El Arqueólogo Digital",   "Juegas todo, de 1975 en adelante. La historia es tu obsesión.", Color(0xFF9C27B0)),
    PC_ARCHITECT    ("El Arquitecto de PC",      "DOS, ScummVM, aventuras de texto. Eres de otra dimensión.", Color(0xFF00E676)),
    JRPG_PILGRIM    ("El Peregrino JRPG",       "Horas infinitas. Cada número importa. Subir niveles es tu zen.", Color(0xFFFF4081)),
    SPEEDRUNNER     ("El Corredor del Tiempo",  "Si no es el récord, no vale. El reloj nunca para.", Color(0xFF00F2FF)),
    COLLECTOR       ("El Gran Preservacionista","Más de 1000 juegos. Nunca los juegas todos, pero están ahí.", SpeccyPalette.warn)
}

private fun detectArchetype(
    platformDist: Map<String, Float>,
    totalGames: Int,
    totalPlayTime: Long
): GamerArchetype {
    val top = platformDist.entries.maxByOrNull { it.value }?.key?.lowercase() ?: ""
    if (totalGames >= 1000) return GamerArchetype.COLLECTOR
    if (top.contains("arcade") || top.contains("mame") || top.contains("fbneo")) return GamerArchetype.ARCADE_WARRIOR
    if (top.contains("nes") || top.contains("snes") || top.contains("n64") || top.contains("gba")) return GamerArchetype.NINTENDO_KNIGHT
    if (top.contains("sega") || top.contains("genesis") || top.contains("megadrive") || top.contains("saturn")) return GamerArchetype.SEGA_REBEL
    if (top.contains("dos") || top.contains("pc") || top.contains("scumm")) return GamerArchetype.PC_ARCHITECT
    if (totalPlayTime / 3600 > 200) return GamerArchetype.JRPG_PILGRIM
    return GamerArchetype.RETRO_SCHOLAR
}

private fun platformDisplayName(id: String): String = when {
    id.contains("nes", true) && !id.contains("snes") -> "NES"
    id.contains("snes", true) -> "SNES"
    id.contains("n64", true)  -> "N64"
    id.contains("gb", true) && !id.contains("gbc") && !id.contains("gba") -> "Game Boy"
    id.contains("gba", true)  -> "GBA"
    id.contains("gbc", true)  -> "GBC"
    id.contains("genesis", true) || id.contains("megadrive", true) -> "Genesis"
    id.contains("saturn", true)  -> "Saturn"
    id.contains("dreamcast", true) -> "Dreamcast"
    id.contains("psx", true) || id.contains("ps1", true) -> "PS1"
    id.contains("ps2", true) -> "PS2"
    id.contains("arcade", true) || id.contains("mame", true) || id.contains("fbneo", true) -> "Arcade"
    id.contains("dos", true) -> "DOS"
    id.contains("atari", true) -> "Atari"
    else -> id.take(8).uppercase()
}

private val platformColors = listOf(
    Color(0xFFFF0000), Color(0xFF0088FF), Color(0xFFFFD600),
    Color(0xFF00E676), Color(0xFFFF4081), Color(0xFF9C27B0),
    Color(0xFF00F2FF), SpeccyPalette.warn
)

// ── Composable principal ──────────────────────────────────────────

@Composable
fun GamingDnaScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context      = LocalContext.current
    val primaryColor = ThemeManager.primaryColor
    val gameDao      = remember { AppDatabase.getDatabase(context).gameDao() }

    // Estado de carga
    var platformDist   by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var topGames       by remember { mutableStateOf<List<Game>>(emptyList()) }
    var totalTime      by remember { mutableStateOf(0L) }
    var totalGames     by remember { mutableStateOf(0) }
    var archetype      by remember { mutableStateOf<GamerArchetype?>(null) }
    var isLoading      by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val playTimes  = gameDao.getPlatformsByPlayTime()
            val total      = gameDao.getTotalPlayTimeSeconds() ?: 0L
            val top        = gameDao.getTopPlayedGames(5)
            val count      = gameDao.getGameCountSync()

            val totalSecs  = playTimes.sumOf { it.totalTime }.takeIf { it > 0 } ?: 1L
            val dist       = playTimes.associate { it.platformId to (it.totalTime.toFloat() / totalSecs) }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                platformDist = dist
                topGames     = top
                totalTime    = total
                totalGames   = count
                archetype    = detectArchetype(dist, count, total)
                isLoading    = false
            }
        }
    }

    // Aqui vivia una animacion infinita `scanAnim` que no leia nadie.
    // rememberInfiniteTransition lanza una corrutina que interpola en cada frame
    // aunque el valor no se use en ningun sitio: gastaba CPU todo el tiempo que
    // la pantalla estaba abierta sin dibujar absolutamente nada. Eliminada.

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Fondo: líneas diagonales sutiles (ADN)
        Canvas(Modifier.fillMaxSize()) {
            val spacing = 40.dp.toPx()
            val lineColor = primaryColor.copy(alpha = 0.04f)
            var i = -size.height
            while (i < size.width + size.height) {
                drawLine(lineColor, Offset(i, 0f), Offset(i + size.height, size.height), 1f)
                i += spacing
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(40.dp))
                    Text("ANALIZANDO TU ADN GAMER...", color = primaryColor.copy(.7f),
                        fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(Touch.min)) {
                        Icon(Icons.Default.ArrowBack, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("ADN GAMER", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black,
                            fontSize = 22.sp, letterSpacing = 2.sp, fontFamily = ThemeManager.titleFont)
                        Text("Tu identidad retro, en datos", color = primaryColor.copy(.6f), fontSize = 12.sp)
                    }
                }
            }

            // ── Tarjetas de vida ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        Triple("TIEMPO TOTAL", formatDuration(totalTime), primaryColor),
                        Triple("COLECCIÓN",    "$totalGames juegos",      SpeccyPalette.warn),
                        Triple("PLATAFORMAS",  "${platformDist.size}",    Color(0xFF00E676))
                    ).forEach { (label, value, color) ->
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, color.copy(.2f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(label, color = color.copy(.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ── Arquetipos ──
            item {
                val arc = archetype ?: return@item
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(arc.color.copy(.08f))
                        .border(1.dp, arc.color.copy(.35f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(arc.color.copy(.15f))
                                .border(1.dp, arc.color.copy(.5f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EmojiEvents, null, tint = arc.color, modifier = Modifier.size(28.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("// PERSONALIDAD RETRO", color = arc.color.copy(.6f),
                                fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text(arc.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(arc.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.7f), fontSize = 12.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }

            // ── Distribución por plataforma ──
            item {
                DnaSectionHeader("DISTRIBUCIÓN POR PLATAFORMA", primaryColor)
                Spacer(Modifier.height(10.dp))
                val sorted = platformDist.entries.sortedByDescending { it.value }.take(8)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sorted.forEachIndexed { i, (platformId, pct) ->
                        val color = platformColors[i % platformColors.size]
                        val animPct by animateFloatAsState(pct, tween(800, i * 80), label = "p$i")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(platformDisplayName(platformId),
                                color = color, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.width(60.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(
                                modifier = Modifier.weight(1f).height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight()
                                        .fillMaxWidth(animPct)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Brush.horizontalGradient(listOf(color.copy(.6f), color)))
                                )
                            }
                            Text("${(pct * 100).toInt()}%", color = color.copy(.8f),
                                fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(30.dp))
                        }
                    }
                }
            }

            // ── Top 5 juegos ──
            if (topGames.isNotEmpty()) {
                item {
                    DnaSectionHeader("TOP JUEGOS JUGADOS", primaryColor)
                    Spacer(Modifier.height(10.dp))
                    val maxCount = topGames.maxOf { it.playCount }.toFloat().coerceAtLeast(1f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topGames.forEachIndexed { i, game ->
                            val barPct by animateFloatAsState(game.playCount / maxCount, tween(800, i * 100), label = "g$i")
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("#${i + 1}", color = primaryColor.copy(.5f), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(game.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surface)) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(barPct).clip(RoundedCornerShape(2.dp)).background(primaryColor))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${game.playCount}x", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    if (game.playTimeSeconds > 0) Text(game.formattedPlayTime, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f), fontSize = 12.sp)
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
private fun DnaSectionHeader(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text, color = color.copy(.7f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0h"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
