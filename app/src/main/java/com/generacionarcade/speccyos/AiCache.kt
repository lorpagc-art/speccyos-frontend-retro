/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCache(
    @PrimaryKey val queryHash: String,
    val responseText: String,
    val timestamp: Long = System.currentTimeMillis()
)
