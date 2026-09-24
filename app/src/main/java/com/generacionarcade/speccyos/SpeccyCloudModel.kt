/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import com.google.firebase.Timestamp

// Configuración Global del Sistema
data class SystemConfig(
    val themePack: String = "Press-Start",
    val lastPlatform: String = "Atari 5200",
    val isRootEnabled: Boolean = false,
    val lastSync: Timestamp = Timestamp.now()
)

// Progreso de Juegos y Puntuaciones
data class GameProgress(
    val gameId: String = "",
    val gameName: String = "",
    val platform: String = "",
    val playTimeMinutes: Long = 0,
    val lastPlayed: Timestamp = Timestamp.now(),
    val favorite: Boolean = false,
    val score: Int = 0,             // Campo añadido para el Ranking del Trivial
    val rankTitle: String = ""      // Campo añadido para el Rango del Trivial (ej: "DIOS DEL ARCADE")
)

// Perfil de Usuario
data class UserProfile(
    val userId: String = "",
    val displayName: String = "Speccy User",
    val deviceModel: String = android.os.Build.MODEL,
    val config: SystemConfig = SystemConfig()
)
