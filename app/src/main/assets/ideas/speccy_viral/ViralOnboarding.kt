package com.generacionarcade.speccyos

/**
 * ViralOnboarding.kt
 * ─────────────────────────────────────────────────────────────────
 * Nuevo onboarding viral: WOW primero, formularios después.
 *
 * FLUJO ANTERIOR (6 pasos antes de ver algo):
 *   intro → auth → welcome → hardware → setup_folder → permissions → loading
 *
 * FLUJO NUEVO (wow inmediato):
 *   intro_viral → [boot retro + detección hardware instantánea]
 *               → [primer juego demo aparece]
 *               → (1) perfil rápido (opcional)
 *               → (2) carpeta ROMs
 *               → dashboard
 *
 * EL TRUCO: La detección de hardware ocurre en el background mientras
 * el usuario ve la animación. Los permisos no-críticos se piden lazy
 * (solo cuando se necesitan, no antes de usar la app).
 *
 * INTEGRACIÓN en MainActivity.kt:
 *   Sustituye las rutas "welcome" → "tech_info" → "setup_hardware"
 *   por una sola ruta "onboarding_viral" que llama a ViralOnboardingFlow.
 * ─────────────────────────────────────────────────────────────────
 */

import android.app.Application
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.generacionarcade.speccyos.theme.NeonBlue
import kotlinx.coroutines.delay

// ─── ESTADO DEL ONBOARDING ────────────────────────────────────────

private enum class OnboardingStep {
    BOOT_ANIMATION,     // Boot retro + detección hardware en background
    WOW_REVEAL,         // "Tu dispositivo puede correr X, Y, Z" — el momento wow
    QUICK_PROFILE,      // Nombre / avatar (2 toques, skip disponible)
    DONE                // Listo — va al dashboard
}

// ─── COMPOSABLE PRINCIPAL ─────────────────────────────────────────

@Composable
fun ViralOnboardingFlow(
    settingsManager: SettingsManager,
    userStatusManager: UserStatusManager,
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.BOOT_ANIMATION) }
    var detectedProfile by remember { mutableStateOf<HardwareControlManagerBeta.HardwareProfile?>(null) }

    when (step) {
        OnboardingStep.BOOT_ANIMATION -> {
            OnboardingBootScreen(
                onDetected = { profile ->
                    detectedProfile = profile
                    step = OnboardingStep.WOW_REVEAL
                }
            )
        }
        OnboardingStep.WOW_REVEAL -> {
            detectedProfile?.let { profile ->
                WowRevealScreen(
                    profile = profile,
                    settingsManager = settingsManager,
                    onContinue = { step = OnboardingStep.QUICK_PROFILE }
                )
            }
        }
        OnboardingStep.QUICK_PROFILE -> {
            QuickProfileScreen(
                userStatusManager = userStatusManager,
                onComplete = {
                    step = OnboardingStep.DONE
                    onComplete()
                }
            )
        }
        OnboardingStep.DONE -> { /* navegamos fuera */ }
    }
}

// ─── PASO 1: BOOT ANIMADO (WOW = primero) ─────────────────────────

@Composable
private fun OnboardingBootScreen(
    onDetected: (HardwareControlManagerBeta.HardwareProfile) -> Unit
) {
    var lines by remember { mutableStateOf(listOf<String>()) }
    var scanPercent by remember { mutableIntStateOf(0) }

    // Detección en background mientras el usuario ve la animación
    LaunchedEffect(Unit) {
        val profile = HardwareControlManagerBeta.detectActualHardware()

        val bootLines = listOf(
            "SPECCY OS E5 ULTRA",
            "Iniciando...",
            "",
            "▸ Escaneando hardware...",
            "▸ CPU detectada: ${profile.chipset}",
            "▸ GPU: ${profile.gpu}",
            "▸ RAM: ${profile.ram}",
            "",
            "▸ Calculando capacidad de emulación...",
        )

        bootLines.forEach { line ->
            lines = lines + line
            delay(120)
        }

        // Simula un scan rápido (1.5s)
        repeat(10) {
            scanPercent = (it + 1) * 10
            delay(150)
        }

        lines = lines + "▸ Sistema listo"
        delay(400)

        // Guarda el perfil y avanza
        onDetected(profile)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp)
    ) {
        Column {
            Spacer(Modifier.height(60.dp))
            lines.forEach { line ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically()
                ) {
                    Text(
                        text = line,
                        color = when {
                            line.startsWith("SPECCY") -> Color.White
                            line.startsWith("▸ Sistema") -> NeonBlue
                            line.startsWith("▸") -> NeonBlue.copy(alpha = 0.8f)
                            else -> Color.Green
                        },
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                }
            }

            if (scanPercent > 0 && scanPercent < 100) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { scanPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonBlue,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Text("$scanPercent%", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ─── PASO 2: REVEAL WOW ───────────────────────────────────────────

@Composable
private fun WowRevealScreen(
    profile: HardwareControlManagerBeta.HardwareProfile,
    settingsManager: SettingsManager,
    onContinue: () -> Unit
) {
    // Sistemas que puede emular según la capacidad del perfil
    val systems = remember(profile.emulationCapacity) {
        when {
            profile.emulationCapacity.contains("Ultra", ignoreCase = true) ||
            profile.emulationCapacity.contains("Xanite", ignoreCase = true) -> listOf(
                "PlayStation 2" to "✓",
                "GameCube" to "✓",
                "Nintendo DS" to "✓",
                "PSP" to "✓",
                "Dreamcast" to "✓",
                "Arcade CPS3" to "✓"
            )
            profile.emulationCapacity.contains("Pro", ignoreCase = true) -> listOf(
                "PlayStation" to "✓",
                "Nintendo 64" to "✓",
                "SNES" to "✓",
                "GBA" to "✓",
                "Megadrive" to "✓",
                "NeoGeo" to "✓"
            )
            else -> listOf(
                "SNES" to "✓",
                "Megadrive" to "✓",
                "GBA" to "✓",
                "NES" to "✓",
                "GameBoy" to "✓"
            )
        }
    }

    // Guarda el hardware y marca como configurado
    LaunchedEffect(Unit) {
        HardwareControlManagerBeta.initialize(profile.id, null)
        settingsManager.manualHardwareId = profile.id
        settingsManager.isHardwareConfigured = true
    }

    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "a"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Fondo sutil
        AsyncImage(
            model = "file:///android_asset/contentimg/banner-neon.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.08f).blur(20.dp),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Nombre del dispositivo con glow
            Text(
                text = profile.name.uppercase(),
                color = NeonBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )
            Text(
                text = profile.chipset,
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Tagline
            Text(
                text = "Tu dispositivo puede emular:",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Grid de sistemas
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                systems.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (system, check) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = NeonBlue.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, NeonBlue.copy(alpha = glowAlpha * 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(check, color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text(system, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        // Rellena si el row tiene 1 elemento
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Badge de capacidad
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Capacidad: ${profile.emulationCapacity}",
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "ENTRAR AL SISTEMA",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── PASO 3: PERFIL RÁPIDO (2 toques) ────────────────────────────

@Composable
private fun QuickProfileScreen(
    userStatusManager: UserStatusManager,
    onComplete: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Person,
                null,
                tint = NeonBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("¿CÓMO TE LLAMAN?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Puedes cambiarlo luego en ajustes", color = Color.Gray, fontSize = 13.sp)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 20) nickname = it },
                label = { Text("Tu apodo retro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = NeonBlue
                )
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val finalNick = nickname.ifBlank { "Jugador Retro" }
                    val dummyEmail = "local_${System.currentTimeMillis()}@speccyos.local"
                    userStatusManager.syncUser(dummyEmail, finalNick, "")
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("EMPEZAR", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = {
                userStatusManager.syncUser("local_anon@speccyos.local", "Jugador Retro", "")
                onComplete()
            }) {
                Text("Omitir por ahora", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}
