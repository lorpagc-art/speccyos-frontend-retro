/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 🚀 NEBULA TWEAKER V0.2 (Speccy Engine Edition)
 * Centro de control de hardware de bajo nivel (Root/Shizuku).
 * Control de Clusters, Gobernadores, GPU y Lanzamiento del Motor.
 */
@Composable
fun NebulaTweakerScreen(
    hardwareViewModel: HardwareViewModel,
    soundManager: SoundManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = ThemeManager.primaryColor
    val state by hardwareViewModel.uiState.collectAsStateWithLifecycle()
    val appLauncher = remember { AppLauncherManager(context) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
            
            // --- HEADER MODDER ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { soundManager.playBack(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = primaryColor)
                }
                Spacer(Modifier.width(24.dp))
                Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NEBULA TWEAKER", color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = ThemeManager.titleFont)
                    Text("OVERCLOCK & THERMAL CONTROL MODULE", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- ESTADO DE PRIVILEGIOS ---
            Surface(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("ESTADO DE PRIVILEGIOS (ROOT / P-SERVER)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text("Requerido para aplicar frecuencias extremas al Kernel de Linux.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    val isPrivileged = state.rootStatus == "ROOT" || state.rootStatus == "SHIZUKU" || state.rootStatus == "PSERVER"
                    Text(
                        text = if (isPrivileged) state.rootStatus else "NO DETECTADO", 
                        color = if (isPrivileged) SpeccyPalette.ok else MaterialTheme.colorScheme.error, 
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- SECCIÓN SPECCY ENGINE (EL CORAZÓN) ---
            Text("MOTOR DE EMULACIÓN (SPECCY ENGINE)", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            
            Surface(
                color = primaryColor.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    soundManager.playLaunch()
                    // Lanzar el motor directamente para navegar por sus menús
                    try {
                        appLauncher.launchApp("com.retroarch.speccy")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Speccy Engine no instalado. Usa el script de forja.", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RocketLaunch, null, tint = primaryColor, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("IGNICIÓN DEL SPECCY ENGINE", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("Entra en el entorno XMB Neon Cian optimizado. Pulsa para navegar por los menús del motor.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = primaryColor)
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- PANELES DE OVERCLOCK (NEON SLIDERS) ---
            Text("CONTROL DE CLUSTERS (CPU)", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            
            NeonSliderModule(title = "CLUSTER 0 (EFFICIENCY CORES)", primaryColor = primaryColor, minVal = 500, maxVal = 1800, unit = "MHz", initialValue = 0.3f)
            NeonSliderModule(title = "CLUSTER 1 (PERFORMANCE CORES)", primaryColor = primaryColor, minVal = 800, maxVal = 2600, unit = "MHz", initialValue = 0.5f)
            
            Spacer(Modifier.height(24.dp))
            
            Text("UNIDAD DE PROCESAMIENTO GRÁFICO (GPU)", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            NeonSliderModule(title = "MALI / ADRENO FREQUENCY", primaryColor = primaryColor, minVal = 300, maxVal = 950, unit = "MHz", initialValue = 0.4f)

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun NeonSliderModule(title: String, primaryColor: Color, minVal: Int, maxVal: Int, unit: String, initialValue: Float) {
    var sliderValue by remember { mutableFloatStateOf(initialValue) }
    val currentValue = minVal + ((maxVal - minVal) * sliderValue).toInt()
    
    // Lógica del color: Si pasas del 85%, el color muta a ROJO (Peligro)
    val isDanger = sliderValue > 0.85f
    val targetColor = if (isDanger) MaterialTheme.colorScheme.error else primaryColor
    
    // Animaciones suaves para la transición del neón
    val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(500), label = "color")
    val glowAlpha by animateFloatAsState(targetValue = 0.1f + (0.9f * sliderValue), animationSpec = tween(300), label = "alpha")
    // El radio salia directo del valor crudo del slider, asi que cada delta del
    // arrastre reconfiguraba el RenderEffect del blur -y hay tres de estas
    // tarjetas en pantalla a la vez-. Justo mientras el dedo esta encima. Ahora
    // se anima (menos pasos por segundo que eventos tactiles) y el techo lo pone
    // SpeccyFx segun el equipo, que a 40 dp era inasumible en gama baja.
    val targetBlur = SpeccyFx.blurRadius((10f + (30f * sliderValue)).dp)
    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(150),
        label = "blur"
    )

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        
        // 1. LA MAGIA: El "Aura" de Neón detrás de la tarjeta
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(animatedColor.copy(alpha = glowAlpha * 0.4f), RoundedCornerShape(16.dp))
                .blur(blurRadius) // Efecto de resplandor dinámico
        )

        // 2. La Tarjeta Física Principal
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (sliderValue > 0.05f) 2.dp else 1.dp, 
                color = animatedColor.copy(alpha = glowAlpha)
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                // Título y Valor en Tiempo Real
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    
                    // Texto con sombra emisiva
                    Text(
                        text = "$currentValue $unit", 
                        color = animatedColor, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Black, 
                        style = TextStyle(
                            shadow = Shadow(color = animatedColor, blurRadius = 15f * glowAlpha)
                        )
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = animatedColor,
                        activeTrackColor = animatedColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                
                // Mensaje de Alerta si entra en "Zona Roja"
                if (isDanger) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("¡ADVERTENCIA! ZONA DE THROTTLING TÉRMICO", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Mantenemos el espacio para que la UI no salte
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
    }
}
