/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ImperialDonationScreen(
    onDonateClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val neonGold = SpeccyPalette.imperial
    val neonBlue = MaterialTheme.colorScheme.primary
    
    val infiniteTransition = rememberInfiniteTransition(label = "imperial")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Fondo Atmosférico
        AsyncImage(
            model = "file:///android_asset/contentimg/banner-neon.jpg",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.3f },
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Diamond,
                contentDescription = null,
                tint = neonGold,
                modifier = Modifier.size(80.dp).scale(scale)
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "ASCENSO IMPERIAL",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "DESBLOQUEA EL NÚCLEO DEFINITIVO",
                color = neonGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            // Tarjeta de Beneficios
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, neonGold.copy(alpha = 0.3f)),
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    BenefitItem("Sin Publicidad en el Dashboard", neonBlue)
                    BenefitItem("Acceso al Modo EXTREME (Tuning Pro)", neonBlue)
                    BenefitItem("Nebula Sync: Guardado en la Nube", neonBlue)
                    BenefitItem("IA El Arquitecto: Consultas Ilimitadas", neonBlue)
                    BenefitItem("Soporte Directo y Actualizaciones Gold", neonBlue)
                }
            }

            Spacer(Modifier.height(48.dp))

            // Botón de Acción Imperial
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(35.dp))
                    .clickable { onDonateClick() },
                color = neonGold,
                shadowElevation = 20.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "CONVERTIRSE EN MIEMBRO IMPERIAL",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "¿YA ERES IMPERIAL? RESTAURAR ACCESO",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRestoreClick() }
            )
        }
    }
}

@Composable
fun BenefitItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
