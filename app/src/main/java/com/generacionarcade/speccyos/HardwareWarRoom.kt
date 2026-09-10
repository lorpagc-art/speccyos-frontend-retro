package com.generacionarcade.speccyos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.generacionarcade.speccyos.theme.NeonBlue
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HardwareWarRoom(
    hardwareViewModel: HardwareViewModel,
    onBack: () -> Unit
) {
    val uiState by hardwareViewModel.uiState.collectAsStateWithLifecycle()
    val primaryColor = ThemeManager.primaryColor
    
    // Simulación de "Eficiencia Imperial" basada en telemetría real
    val efficiencyScore = remember(uiState.temperature, uiState.cpuLoad) {
        val tempBase = (100 - (uiState.temperature.coerceIn(30f, 90f) - 30) * 1.5f).toInt()
        val loadBase = (100 - uiState.cpuLoad.coerceIn(0f, 100f) * 0.5f).toInt()
        ((tempBase + loadBase) / 2).coerceIn(0, 100)
    }

    val rank = when {
        efficiencyScore > 90 -> "LEGADO IMPERIAL"
        efficiencyScore > 75 -> "ARQUITECTO DE SISTEMAS"
        efficiencyScore > 50 -> "INGENIERO DE CAMPO"
        else -> "RECLUTA TECNOLÓGICO"
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Fondo de Radar/Grid
        RadarBackground(primaryColor)

        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("WAR-ROOM IMPERIAL", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("CENTRO DE MANDO Y OPTIMIZACIÓN DE HARDWARE", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(48.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                // LADO IZQUIERDO: Radar de Eficiencia
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    EfficiencyRadar(efficiencyScore, primaryColor, rank)
                }

                // LADO DERECHO: Telemetría Detallada
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TelemetryCard("TEMPERATURA SOC", "${uiState.temperature.toInt()}°C", Icons.Default.Thermostat, primaryColor, uiState.temperature / 100f)
                    TelemetryCard("CARGA DE NÚCLEOS", "${uiState.cpuLoad.toInt()}%", Icons.Default.Memory, primaryColor, uiState.cpuLoad / 100f)
                    TelemetryCard("MODO DE ENERGÍA", uiState.currentProfile, Icons.Default.Bolt, primaryColor, if(uiState.currentProfile == "EXTREME") 1f else 0.5f)
                    
                    Spacer(Modifier.weight(1f))
                    
                    // Botón de Optimización
                    Button(
                        onClick = { hardwareViewModel.setManualProfile("EXTREME") },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, primaryColor),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("PROTOCOLIZAR OVERCLOCK", color = primaryColor, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
fun EfficiencyRadar(score: Int, color: Color, rank: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "angle"
    )

    Box(contentAlignment = Alignment.Center) {
        // Círculos concéntricos
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color, radius = size.width / 2, style = Stroke(1f), alpha = 0.2f)
            drawCircle(color, radius = size.width / 3, style = Stroke(1f), alpha = 0.1f)
            drawCircle(color, radius = size.width / 6, style = Stroke(1f), alpha = 0.05f)
            
            // Línea de escaneo
            val rad = Math.toRadians(angle.toDouble())
            val endX = center.x + (size.width / 2) * cos(rad).toFloat()
            val endY = center.y + (size.width / 2) * sin(rad).toFloat()
            drawLine(color, center, Offset(endX, endY), strokeWidth = 2f, alpha = 0.5f)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$score", color = color, fontSize = 64.sp, fontWeight = FontWeight.Black)
            Text(text = "PUNTAJE IMPERIAL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Surface(color = color, shape = RoundedCornerShape(4.dp)) {
                Text(text = rank, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun RadarBackground(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridStep = 100f
        for (i in 0..(size.width / gridStep).toInt()) {
            drawLine(color.copy(alpha = 0.03f), Offset(i * gridStep, 0f), Offset(i * gridStep, size.height))
        }
        for (i in 0..(size.height / gridStep).toInt()) {
            drawLine(color.copy(alpha = 0.03f), Offset(0f, i * gridStep), Offset(size.width, i * gridStep))
        }
    }
}
