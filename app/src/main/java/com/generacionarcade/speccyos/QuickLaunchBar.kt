/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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

/**
 * QuickLaunchBar — dock flotante con los últimos juegos jugados.
 *
 * INTEGRACIÓN en SpeccyDashboard.kt — dentro del Box principal, al final:
 *
 *   Box(Modifier.fillMaxSize()) {
 *       // ... contenido del dashboard ...
 *
 *       Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
 *           QuickLaunchBar(
 *               recentGames  = recentGames.take(4),
 *               primaryColor = primaryColor,
 *               isVisible    = !isAttractModeActive && selectedPlatform == null,
 *               onGameClick  = { game -> /* lanzar juego */ }
 *           )
 *       }
 *   }
 */
@Composable
fun QuickLaunchBar(
    recentGames: List<Game>,
    primaryColor: Color,
    isVisible: Boolean,
    onGameClick: (Game) -> Unit
) {
    AnimatedVisibility(
        visible  = isVisible && recentGames.isNotEmpty(),
        enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(300)),
        exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.8f))))
                .padding(horizontal = 40.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(20.dp).height(1.dp).background(primaryColor.copy(.35f)))
                    Text("CONTINUAR", color = primaryColor.copy(.5f), fontSize = 12.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentGames.take(4).forEach { game ->
                        QuickLaunchCard(game, primaryColor) { onGameClick(game) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLaunchCard(game: Game, primaryColor: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    val glowAlpha by rememberInfiniteTransition(label = "ql_${game.path.hashCode()}").animateFloat(
        0.15f, 0.45f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "ga"
    )
    Column(
        modifier = Modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // NAVEGABLE CON MANDO. Esta barra era `clickable{}` puro, es decir
        // inalcanzable sin pantalla tactil: justo lo contrario de lo que promete
        // una app "para consolas portatiles".
        SpeccyFocusable(
            label = game.title,
            onSelect = onClick,
            modifier = Modifier.size(width = 100.dp, height = 70.dp)
                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)
                .border(BorderStroke(1.dp, primaryColor.copy(glowAlpha)), RoundedCornerShape(8.dp))
                .drawBehind {
                    drawLine(primaryColor.copy(glowAlpha * 2), Offset(12f, size.height - 1f),
                        Offset(size.width - 12f, size.height - 1f), 2f)
                },
            contentAlignment = Alignment.Center
        ) {
            if (game.boxArt != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(game.boxArt).crossfade(false).build(),
                    contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.8f)), startY = 30f)))
            } else {
                Text(game.title.take(1).uppercase(), color = primaryColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurface.copy(.7f), modifier = Modifier.size(28.dp))
            }
            Box(Modifier.fillMaxSize().padding(4.dp), Alignment.TopEnd) {
                Text(game.platformId.take(3).uppercase(),
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.background.copy(.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            if (game.playTimeSeconds > 0) {
                Box(Modifier.fillMaxSize().padding(4.dp), Alignment.BottomStart) {
                    Text(game.formattedPlayTime,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.background.copy(.7f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(game.title, color = MaterialTheme.colorScheme.onSurface.copy(.75f), fontSize = 12.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
