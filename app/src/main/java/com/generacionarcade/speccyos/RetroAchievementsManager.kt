/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RetroAchievementsManager(private val context: Context, private val settingsManager: SettingsManager) {
    private val client = OkHttpClient()
    private val BASE_URL = "https://retroachievements.org/API/"
    private val TAG = "RetroAchievements"

    /**
     * Login por contraseña. La versión anterior mandaba la contraseña en la QUERY
     * STRING (`?u=..&p=..`), que queda registrada en logs de servidor, proxies y
     * cachés. Ahora viaja en el cuerpo de un POST.
     *
     * Aun así, PREFIERE `verifyAndSaveApiKey`: la clave de API es revocable y no
     * expone la contraseña de la cuenta del usuario.
     */
    suspend fun loginSilent(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${BASE_URL}API_Login.php"
        val form = FormBody.Builder().add("u", user).add("p", pass).build()
        val request = Request.Builder()
            .url(url)
            .post(form)
            .header("User-Agent", "SpeccyOS-E5-Ultra/3.7")
            .build()
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (body != null) {
                val json = JSONObject(body)
                if (json.optBoolean("Success", false)) {
                    settingsManager.raUsername = user
                    settingsManager.raToken = json.optString("Token", "")
                    settingsManager.isRAEnabled = true
                    return@withContext true
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error Login", e) }
        false
    }

    suspend fun verifyAndSaveApiKey(user: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (user.isEmpty() || apiKey.isEmpty()) return@withContext false
        val url = "${BASE_URL}API_GetUserSummary.php?u=$user&y=$apiKey&z=$user"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.body?.string()?.contains("LastActivity") == true) {
                settingsManager.raUsername = user
                settingsManager.raToken = apiKey
                settingsManager.isRAEnabled = true
                return@withContext true
            }
        } catch (e: Exception) { Log.e(TAG, "Error API Key", e) }
        false
    }

    /**
     * 🔍 BUSCA EL ID DEL JUEGO USANDO EL HASH (MD5)
     */
    suspend fun getGameIdByHash(md5: String): Int = withContext(Dispatchers.IO) {
        val user = settingsManager.raUsername
        val token = settingsManager.raToken
        if (token.isEmpty() || md5.isEmpty()) return@withContext 0

        val url = "${BASE_URL}API_GetGameID.php?u=$user&y=$token&m=$md5"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string()
            if (body != null && body != "null") {
                return@withContext body.trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) { Log.e(TAG, "Error buscando ID por Hash", e) }
        0
    }

    suspend fun getUserProfile(): String = withContext(Dispatchers.IO) {
        val user = settingsManager.raUsername
        val token = settingsManager.raToken
        if (user.isEmpty() || token.isEmpty()) return@withContext "Desconectado"
        val url = "${BASE_URL}API_GetUserSummary.php?u=$user&y=$token&z=$user"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("TotalPoints")) {
                return@withContext "Puntos: ${json.optString("TotalPoints")} | Ratio: ${json.optString("RetroRatio")}"
            }
        } catch (e: Exception) { Log.e(TAG, "Error perfil", e) }
        "Sync Error"
    }

    suspend fun getGameAchievements(raGameId: Int): JSONObject? = withContext(Dispatchers.IO) {
        val user = settingsManager.raUsername
        val token = settingsManager.raToken
        if (token.isEmpty() || raGameId <= 0) return@withContext null
        val url = "${BASE_URL}API_GetGameInfoAndUserProgress.php?u=$user&y=$token&g=$raGameId"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string()
            if (body != null) return@withContext JSONObject(body)
        } catch (e: Exception) { Log.e(TAG, "Error logros", e) }
        null
    }
}
