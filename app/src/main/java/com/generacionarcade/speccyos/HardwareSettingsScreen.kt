/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.generacionarcade.speccyos.theme.NeonBlue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HardwareSettingsScreen(viewModel: HardwareViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(scrollState)
    ) {
        // --- BARRA SUPERIOR DE ESTADO EN TIEMPO REAL ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ESTADO DEL SISTEMA", 
                color = NeonBlue, 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Black
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Estado de Privilegios
                Surface(
                    color = when(state.rootStatus) {
                        "PSERVER" -> SpeccyPalette.warn
                        "SHIZUKU" -> NeonBlue
                        "SU" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        state.rootStatus, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Temperatura
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat, 
                        contentDescription = "Temp", 
                        tint = if (state.temperature > 65) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, 
                        modifier = Modifier.size(18.dp)
                    )
                    Text("${state.temperature.toInt()}°", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))

        // --- SECCIÓN: ENLACE IMPERIAL (SHIZUKU / PSERVER) ---
        Text(
            text = "ENLACE DE HARDWARE", 
            color = MaterialTheme.colorScheme.onSurface, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = if (state.rootStatus == "NATIVE") BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.rootStatus == "NATIVE") Icons.Default.Lock else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (state.rootStatus == "NATIVE") MaterialTheme.colorScheme.error else NeonBlue
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (state.rootStatus == "NATIVE") "MODO LIMITADO (SIN ACCESO AL KERNEL)" else "VÍNCULO PRIVILEGIADO ACTIVO",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.rootStatus == "NATIVE") "Usa Shizuku o PServer para overclock y ventilador." else "Control total de frecuencias y refrigeración activo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                
                if (state.rootStatus == "NATIVE") {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.requestShizukuPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                    ) {
                        Text("VINCULAR CON SHIZUKU", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    }
                } else if (state.rootStatus == "SHIZUKU") {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.reconnectShizuku() }) {
                        Text("REINICIAR SERVICIO PRIVILEGIADO", color = NeonBlue, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Monitor de Temperatura
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TEMPERATURA SOC", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = "${state.temperature.toInt()}°C", 
                        color = if (state.temperature > 65) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
                CircularProgressIndicator(
                    progress = { (state.temperature / 100f).coerceIn(0f, 1f) },
                    color = if (state.temperature > 65) MaterialTheme.colorScheme.error else NeonBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Perfiles de Rendimiento
        Text(
            text = "PERFILES DE NÚCLEO (TDP Y GOBERNADOR)", 
            color = MaterialTheme.colorScheme.onSurface, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        
        val profiles = listOf("ECO", "BALANCED", "PERFORMANCE", "EXTREME")
        profiles.forEach { profile ->
            val isSelected = state.currentProfile == profile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.setManualProfile(profile) },
                color = if (isSelected) NeonBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = if (isSelected) BorderStroke(1.dp, NeonBlue) else null
            ) {
                Text(
                    text = profile, 
                    modifier = Modifier.padding(16.dp), 
                    color = if (isSelected) NeonBlue else MaterialTheme.colorScheme.onSurface, 
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))

        // Control Manual del Ventilador (Slider)
        Text(
            text = "CONTROL TÉRMICO (VENTILADOR)", 
            color = MaterialTheme.colorScheme.onSurface, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val fanLevelInt = (state.fanSpeedLevel * 3).toInt()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Nivel del Ventilador:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        text = if (fanLevelInt == 0) "APAGADO" else "NIVEL $fanLevelInt",
                        color = NeonBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Slider(
                    value = fanLevelInt.toFloat(),
                    onValueChange = { newValue -> 
                        viewModel.setManualFanSpeed(newValue / 3f)
                    },
                    valueRange = 0f..3f,
                    steps = 2,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonBlue,
                        activeTrackColor = NeonBlue,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        
        Spacer(Modifier.height(100.dp))
    }
}