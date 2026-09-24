/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Speccy OS E5 Ultra - Credits
 * Desarrollado por: Speccy81 / Lola Vico Webstudio 2026
 */

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val primaryColor = ThemeManager.primaryColor
    
    LaunchedEffect(Unit) {
        delay(2000)
        scrollState.animateScrollTo(
            value = scrollState.maxValue,
            animationSpec = tween(durationMillis = 45000, easing = LinearEasing)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AsyncImage(
            model = "file:///android_asset/contentimg/banner-neon.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.1f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(250.dp))

            Text(
                text = "SPECCY OS: E5 ULTRA",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = primaryColor,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "COMMUNITY EDITION // 2026",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(100.dp))

            CreditSection(
                role = "DIRECCIÓN Y DESARROLLO",
                name = "SPECCY81 / LOLA VICO WEBSTUDIO",
                desc = "Arquitectura de sistema, diseño de interfaz y optimización de núcleo."
            )

            Text(
                text = "--- RECURSOS EXTERNOS ---",
                fontSize = 12.sp,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            
            CreditSection(
                role = "MOTOR DE EMULACIÓN",
                name = "RETROARCH & LIBRETRO",
                desc = "A los creadores de RetroArch por su incansable labor en la preservación del videojuego y su motor multiplataforma."
            )

            Spacer(modifier = Modifier.height(60.dp))
            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SPECCY81 & LOLA VICO",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "LOLA VICO WEBSTUDIO",
                fontSize = 12.sp,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Este proyecto nace del amor por el retro y la necesidad de un sistema unificado y potente para la comunidad.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                lineHeight = 22.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(120.dp))

            Text(
                text = "GRACIAS POR MANTENER VIVA LA LLAMA DEL RETRO",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(400.dp))
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun CreditSection(role: String, name: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = role,
            fontSize = 12.sp,
            color = ThemeManager.primaryColor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = desc,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 500.dp),
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}