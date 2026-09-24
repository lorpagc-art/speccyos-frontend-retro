/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ThemeCyberpunkContent(
    platforms: List<String>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onSelect: (String) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    
    // Corregido: Lectura segura del estado cambiante
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                onIndexChanged(index)
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(platforms, key = { _, id -> id }) { index, id ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clickable { onSelect(id) },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ">",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = id.uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
