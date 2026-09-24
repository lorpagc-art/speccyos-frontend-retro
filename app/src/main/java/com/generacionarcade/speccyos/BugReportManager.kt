/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BugReportManager {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "BugTracker"

    // Enviar reporte de error a la colección "bug_reports"
    /**
     * userId se ignora si es un correo: las reglas de Firestore exigen que el
     * campo coincida con request.auth.uid. Se usa siempre el UID de Auth.
     */
    suspend fun sendReport(userId: String, description: String, currentTheme: String) {
        val uid = SpeccyIdentity.ensureSignedIn()
        if (uid == null) {
            Log.w(TAG, "Sin sesión: el reporte no se envía.")
            return
        }
        val report = BugReport(
            reportId = "BUG_${System.currentTimeMillis()}",
            userId = uid,
            description = description,
            activeTheme = currentTheme
        )

        try {
            db.collection("bug_reports")
                .document(report.reportId)
                .set(report)
                .await()
            Log.d(TAG, "Reporte enviado con éxito")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al enviar el reporte", e)
        }
    }
}
