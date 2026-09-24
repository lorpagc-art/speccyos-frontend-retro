/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
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
    MANUAL_HARDWARE_SELECTION, // Elegir modelo de consola o teléfono
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
                    step = OnboardingStep.MANUAL_HARDWARE_SELECTION
                }
            )
        }
        OnboardingStep.MANUAL_HARDWARE_SELECTION -> {
            ManualHardwareSelectionScreen(
                initialProfile = detectedProfile,
                onSelected = { profile ->
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
            .background(MaterialTheme.colorScheme.background)
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
                        color = NeonBlue,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (scanPercent in 1..99) {
                LinearProgressIndicator(
                    progress = { scanPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    color = NeonBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ─── PASO 1.5: SELECCIÓN MANUAL DE HARDWARE ─────────────────────────

@Composable
private fun ManualHardwareSelectionScreen(
    initialProfile: HardwareControlManagerBeta.HardwareProfile?,
    onSelected: (HardwareControlManagerBeta.HardwareProfile) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialProfile?.category ?: "ANBERNIC") }
    var selectedProfile by remember { mutableStateOf(initialProfile ?: HardwareControlManagerBeta.hardwareProfiles.values.first()) }
    
    val allProfiles = HardwareControlManagerBeta.hardwareProfiles.values.toList()
    val groupedProfiles = remember(allProfiles) { allProfiles.groupBy { it.category }.toSortedMap() }
    val categories = groupedProfiles.keys.toList()
    val profilesInActiveCategory = groupedProfiles[selectedCategory] ?: emptyList()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Fondo
        AsyncImage(
            model = "file:///android_asset/contentimg/banner-neon.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.15f).blur(SpeccyFx.blurRadius(15.dp)),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("SELECCIÓN DE HARDWARE", color = NeonBlue, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("Confirma tu modelo para optimizar Speccy OS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Marcas (Horizontal)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // NAVEGABLE CON MANDO. Antes esto era un `clickable{}` puro: en una
                // consola conectada a la TV, o en Android TV, el primer arranque de
                // la app era inalcanzable — y el manifiesto declara explicitamente
                // que la pantalla tactil NO es obligatoria.
                items(categories, key = { it }) { category ->
                    val isSelected = category == selectedCategory
                    SpeccyFocusable(
                        label = "Marca $category",
                        onSelect = { selectedCategory = category },
                        modifier = Modifier
                            .border(1.dp, if (isSelected) NeonBlue else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeonBlue.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            category,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de dispositivos (Vertical)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profilesInActiveCategory, key = { it.id }) { profile ->
                    val isSelected = profile.id == selectedProfile.id
                    SpeccyFocusable(
                        label = "${profile.name}, ${profile.chipset}, ${profile.emulationCapacity}",
                        onSelect = { selectedProfile = profile },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isSelected) NeonBlue else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeonBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                // Antes 11 sp y 10 sp: por debajo del piso legible.
                                Text("${profile.chipset} - ${profile.ram}", color = NeonBlue.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Perfil: ${profile.emulationCapacity}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = NeonBlue)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SpeccyFocusable(
                label = "Confirmar modelo ${selectedProfile.name}",
                onSelect = { onSelected(selectedProfile) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(NeonBlue, RoundedCornerShape(8.dp))
            ) {
                Text("CONFIRMAR MODELO", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
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
    val context = LocalContext.current
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
        HardwareControlManagerBeta.initialize(profile.id, context.applicationContext as Application)
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
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Fondo sutil
        AsyncImage(
            model = "file:///android_asset/contentimg/banner-neon.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.08f).blur(SpeccyFx.blurRadius(20.dp)),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Tagline
            Text(
                text = "Tu dispositivo puede emular:",
                color = MaterialTheme.colorScheme.onSurface,
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
                                    Text(check, color = SpeccyPalette.ok, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text(system, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                color = SpeccyPalette.imperial.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, SpeccyPalette.imperial.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EmojiEvents, null, tint = SpeccyPalette.imperial, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Capacidad: ${profile.emulationCapacity}",
                        color = SpeccyPalette.imperial,
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
                    color = MaterialTheme.colorScheme.onPrimary,
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
            .background(MaterialTheme.colorScheme.background),
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
            Text("¿CÓMO TE LLAMAN?", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Puedes cambiarlo luego en ajustes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { if (it.length <= 20) nickname = it },
                label = { Text("Tu apodo retro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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
                Text("EMPEZAR", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = {
                userStatusManager.syncUser("local_anon@speccyos.local", "Jugador Retro", "")
                onComplete()
            }) {
                Text("Omitir por ahora", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}
