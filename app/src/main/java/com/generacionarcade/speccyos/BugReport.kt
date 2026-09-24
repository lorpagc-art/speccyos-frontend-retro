/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import com.google.firebase.Timestamp

data class BugReport(
    val reportId: String = "",
    val userId: String = "",
    val description: String = "",
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
    val activeTheme: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "OPEN" // OPEN, FIXED, IGNORED
)
