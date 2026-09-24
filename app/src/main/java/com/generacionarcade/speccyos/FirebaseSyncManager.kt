/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirebaseSync"

    // Guardar configuración del sistema (Temas, Wallpapers, etc.)
    suspend fun syncSystemConfig(userId: String, config: SystemConfig) {
        try {
            db.collection("users").document(userId)
                .collection("config").document("current")
                .set(config, SetOptions.merge())
                .await()
            Log.d(TAG, "Configuración del sistema sincronizada.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando configuración", e)
        }
    }

    // Guardar progreso de un juego específico
    suspend fun syncGameProgress(userId: String, progress: GameProgress) {
        try {
            db.collection("users").document(userId)
                .collection("games").document(progress.gameId)
                .set(progress, SetOptions.merge())
                .await()
            Log.d(TAG, "Progreso de ${progress.gameName} guardado en la nube.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando juego", e)
        }
    }

    // Obtener configuración desde la nube
    suspend fun fetchSystemConfig(userId: String): SystemConfig? {
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("config").document("current")
                .get()
                .await()
            snapshot.toObject(SystemConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
