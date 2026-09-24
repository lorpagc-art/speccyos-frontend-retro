/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PlayTimeTracker — seguimiento de tiempo real de juego.
 *
 * INTEGRACIÓN en MainActivity.kt:
 *
 *   private val playTimeTracker by lazy { PlayTimeTracker(this, lifecycleScope) }
 *
 *   // Antes de lanzar un juego (en LauncherManager o donde hagas el Intent):
 *   playTimeTracker.recordPlayStart(game.path)
 *
 *   // En onResume() de MainActivity:
 *   override fun onResume() {
 *       super.onResume()
 *       playTimeTracker.recordPlayEnd(AppDatabase.getDatabase(this).gameDao())
 *   }
 */
class PlayTimeTracker(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("play_tracker", Context.MODE_PRIVATE)

    fun recordPlayStart(gamePath: String) {
        prefs.edit()
            .putLong("session_start", System.currentTimeMillis())
            .putString("game_path", gamePath)
            .apply()
    }

    fun recordPlayEnd(gameDao: GameDao) {
        val path  = prefs.getString("game_path", null) ?: return
        val start = prefs.getLong("session_start", 0L)
        if (start == 0L) return
        val secondsPlayed = (System.currentTimeMillis() - start) / 1000L
        if (secondsPlayed < 30) { clear(); return }
        scope.launch(Dispatchers.IO) { gameDao.addPlayTime(path, secondsPlayed) }
        clear()
    }

    private fun clear() {
        prefs.edit().remove("session_start").remove("game_path").apply()
    }
}
