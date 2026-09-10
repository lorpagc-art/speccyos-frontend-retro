package com.generacionarcade.speccyos

/**
 * KonamiCodeDetector — Easter egg legendario: ↑↑↓↓←→←→BA
 *
 * Detecta la secuencia Konami en cualquier punto de la app y desbloquea
 * el "MODO IMPERIAL" durante 24h: IA ilimitada, todos los temas, scraping turbo.
 *
 * INTEGRACIÓN — añadir en MainActivity.kt o en el Box raíz del dashboard:
 *
 *   // 1. Instancia (una sola vez):
 *   val konamiDetector = remember { KonamiCodeDetector() }
 *
 *   // 2. En el Modifier.onKeyEvent del Box principal:
 *   .onKeyEvent { event ->
 *       if (event.type == KeyEventType.KeyDown) {
 *           if (konamiDetector.onKey(event.nativeKeyEvent.keyCode)) {
 *               // Código detectado!
 *               settingsManager.activateImperialMode()
 *               soundManager.playKonami()  // opcional
 *               showKonamiToast = true
 *           }
 *       }
 *       false
 *   }
 *
 *   // 3. Overlay de celebración:
 *   if (showKonamiToast) {
 *       KonamiUnlockOverlay(primaryColor) { showKonamiToast = false }
 *   }
 *
 * Añade en SettingsManager.kt:
 *   fun activateImperialMode() {
 *       prefs.edit()
 *           .putLong("imperial_mode_until", System.currentTimeMillis() + 86_400_000L)
 *           .apply()
 *   }
 *   val isImperialModeActive: Boolean
 *       get() = System.currentTimeMillis() < prefs.getLong("imperial_mode_until", 0L)
 */

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Detector de secuencia ─────────────────────────────────────────

class KonamiCodeDetector {
    private val sequence = intArrayOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_A
    )
    private var position = 0
    private var lastKeyTime = 0L

    /** @return true si se completó la secuencia Konami. */
    fun onKey(keyCode: Int): Boolean {
        val now = System.currentTimeMillis()
        // Resetear si llevan más de 3s entre teclas
        if (now - lastKeyTime > 3000) position = 0
        lastKeyTime = now

        if (keyCode == sequence[position]) {
            position++
            if (position == sequence.size) {
                position = 0
                return true // ¡Código completo!
            }
        } else {
            // Falló — resetear, pero comprobar si el keyCode inicia la secuencia
            position = if (keyCode == sequence[0]) 1 else 0
        }
        return false
    }

    fun reset() { position = 0 }
    val progress: Float get() = position.toFloat() / sequence.size
}

// ── Overlay de celebración ────────────────────────────────────────

@Composable
fun KonamiUnlockOverlay(
    primaryColor: Color,
    onDismiss: () -> Unit
) {
    val shimmer by rememberInfiniteTransition(label = "k").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "ks"
    )
    val gold = SpeccyPalette.imperial

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .then(
                    Modifier.padding(1.dp)
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🏆", fontSize = 56.sp)

            Text(
                "↑ ↑ ↓ ↓ ← → ← → B A",
                color = gold.copy(0.4f + shimmer * 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = ThemeManager.titleFont
            )

            Text(
                "MODO IMPERIAL\nDESBLOQUEADO",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Text(
                "Activo durante 24 horas",
                color = gold.copy(.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            // Beneficios
            val perks = listOf(
                Icons.Default.AutoAwesome to "Consultas IA ilimitadas",
                Icons.Default.Palette     to "Todos los temas visuales",
                Icons.Default.Speed       to "Scraping sin throttle"
            )
            perks.forEach { (icon, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(gold.copy(.08f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = gold, modifier = Modifier.size(18.dp))
                    Text(text, color = MaterialTheme.colorScheme.onSurface.copy(.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Toca para continuar",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f),
                fontSize = 12.sp
            )
        }
    }
}
