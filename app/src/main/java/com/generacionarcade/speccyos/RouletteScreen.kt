/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

/**
 * RouletteScreen — "Decide por mí": slot machine para elegir juego.
 *
 * El problema más real de los coleccionistas: 2000 juegos y no saber
 * cuál poner. Esta pantalla lo resuelve con una animación de tragaperras.
 *
 * INTEGRACIÓN en SpeccyDashboard.kt:
 *   var isRouletteOpen by remember { mutableStateOf(false) }
 *   if (isRouletteOpen) RouletteScreen(
 *       mainViewModel = mainViewModel,
 *       onBack = { isRouletteOpen = false },
 *       onGameSelected = { game -> isRouletteOpen = false; launchGame(game) }
 *   )
 *
 *   // Botón (en TelemetryHeader u otro lugar):
 *   IconButton(onClick = { isRouletteOpen = true }) {
 *       Icon(Icons.Default.Casino, null, tint = primaryColor)
 *   }
 */

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RouletteScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onGameSelected: (Game) -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val primaryColor = ThemeManager.primaryColor
    val gameDao      = remember { AppDatabase.getDatabase(context).gameDao() }

    var allGames        by remember { mutableStateOf<List<Game>>(emptyList()) }
    var filteredGames   by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    var availablePlatforms by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSpinning      by remember { mutableStateOf(false) }
    var pickedGame      by remember { mutableStateOf<Game?>(null) }
    var spinCount       by remember { mutableStateOf(0) }

    // Carga juegos
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val games = gameDao.getAllGamesList()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                allGames   = games.shuffled()
                filteredGames = allGames
                availablePlatforms = games.map { it.platformId }.distinct().sorted()
                pickedGame = filteredGames.randomOrNull()
            }
        }
    }

    // Refiltrar al cambiar plataforma
    LaunchedEffect(selectedPlatform, allGames) {
        filteredGames = if (selectedPlatform == null) allGames
        else allGames.filter { it.platformId == selectedPlatform }
        pickedGame = filteredGames.randomOrNull()
    }

    // Spin: anima durante ~1.5s seleccionando juegos aleatorios, luego para
    fun spin() {
        if (filteredGames.size < 2) return
        isSpinning = true
        spinCount++
        scope.launch {
            val total    = 18
            val easeSteps = listOf(80L, 80L, 90L, 100L, 110L, 120L, 140L, 160L, 200L, 250L)
            repeat(total) { i ->
                val delay = when {
                    i < total - easeSteps.size -> 70L
                    else -> easeSteps[i - (total - easeSteps.size)]
                }
                pickedGame = filteredGames.random()
                delay(delay)
            }
            isSpinning = false
        }
    }

    // Animación de glow del resultado
    val resultGlow by rememberInfiniteTransition(label = "rg").animateFloat(
        0.4f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "rg"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Fondo reticulado
        Canvas(Modifier.fillMaxSize()) {
            val s = 60.dp.toPx(); val c = primaryColor.copy(.05f)
            for (x in 0..(size.width/s).toInt()+1) drawLine(c, Offset(x*s,0f), Offset(x*s,size.height))
            for (y in 0..(size.height/s).toInt()+1) drawLine(c, Offset(0f,y*s), Offset(size.width,y*s))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Touch.min)) {
                    Icon(Icons.Default.ArrowBack, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("LA RULETA", color = Color.White, fontWeight = FontWeight.Black,
                        fontSize = 22.sp, letterSpacing = 2.sp, fontFamily = ThemeManager.titleFont)
                    Text("¿No sabes qué jugar? Decide el destino.", color = primaryColor.copy(.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${filteredGames.size} juegos", color = Color.Gray.copy(.5f), fontSize = 12.sp)
            }

            // ── Filtros de plataforma ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    RouletteFilterChip("TODO", selectedPlatform == null, primaryColor) { selectedPlatform = null }
                }
                items(availablePlatforms) { platformId ->
                    RouletteFilterChip(
                        platformId.take(6).uppercase(),
                        selectedPlatform == platformId,
                        primaryColor
                    ) { selectedPlatform = if (selectedPlatform == platformId) null else platformId }
                }
            }

            Spacer(Modifier.weight(0.5f))

            // ── Slot machine central ──
            Box(
                modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(0.75f)
                    .drawBehind {
                        val c = primaryColor.copy(if (isSpinning) 0.5f else resultGlow)
                        val cr = 20.dp.toPx()
                        drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr), style = Stroke(2f))
                        // Esquinas decorativas
                        val corner = 22.dp.toPx()
                        listOf(Offset(0f,0f), Offset(size.width,0f), Offset(0f,size.height), Offset(size.width,size.height))
                            .forEachIndexed { i, pt ->
                                val xS = if (i%2==0) 1f else -1f; val yS = if (i<2) 1f else -1f
                                drawLine(primaryColor, pt, Offset(pt.x+xS*corner, pt.y), 3f)
                                drawLine(primaryColor, pt, Offset(pt.x, pt.y+yS*corner), 3f)
                            }
                    },
                contentAlignment = Alignment.Center
            ) {
                val game = pickedGame
                if (game?.boxArt != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(game.boxArt).crossfade(!isSpinning).build(),
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        Alignment.Center
                    ) {
                        Text(game?.title?.take(1)?.uppercase() ?: "?", color = primaryColor, fontSize = 80.sp, fontWeight = FontWeight.Black)
                    }
                }
                // Badge de plataforma
                Box(Modifier.fillMaxSize().padding(12.dp), Alignment.TopEnd) {
                    Text(
                        pickedGame?.platformId?.take(5)?.uppercase() ?: "",
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(.8f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black
                    )
                }
            }

            // ── Título del juego seleccionado ──
            Text(
                text = if (isSpinning) "..." else (pickedGame?.title ?: "Sin juegos"),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pickedGame?.let { "${it.platformId.uppercase()} · ${it.releaseDate?.take(4) ?: "?"}" } ?: "",
                color = primaryColor.copy(.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(0.5f))

            // ── Botones ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Girar
                Button(
                    onClick = { spin() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpinning) MaterialTheme.colorScheme.surfaceVariant else primaryColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSpinning && filteredGames.size >= 2
                ) {
                    Text(
                        if (isSpinning) "GIRANDO..." else "⬤  GIRAR",
                        color = if (isSpinning) primaryColor else Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
                // Jugar esto
                OutlinedButton(
                    onClick = { pickedGame?.let { onGameSelected(it) } },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(.5f)),
                    enabled = !isSpinning && pickedGame != null
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("JUGAR ESTO", color = primaryColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }

            // Contador de giros
            if (spinCount > 0) {
                Text("Giro #$spinCount · ${filteredGames.size} posibles",
                    color = Color.Gray.copy(.35f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RouletteFilterChip(label: String, isActive: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) color.copy(.2f) else MaterialTheme.colorScheme.background)
            .border(1.dp, if (isActive) color.copy(.7f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f),
            fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal)
    }
}
