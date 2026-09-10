package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TimeMachineManager {
    private const val TAG = "TimeMachine"

    /**
     * Busca el último archivo de guardado rápido (.state o .state.auto) para un juego específico.
     * Retorna el archivo (File) y una cadena descriptiva para la interfaz ("Guardado hace X horas").
     */
    fun findLatestSaveState(context: Context, game: Game): Pair<File?, String> {
        try {
            val romPathUri = Uri.parse(game.path)
            val romRealPath = getRealPathFromUri(context, romPathUri)
            if (romRealPath.isEmpty()) return Pair(null, "")

            val romFile = File(romRealPath)
            val romNameWithoutExt = romFile.nameWithoutExtension

            val candidateDirs = getCandidateStateDirs()
            candidateDirs.add(romFile.parentFile ?: File("/"))

            var latestStateFile: File? = null
            var latestTime = 0L

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { _, name ->
                        name == "$romNameWithoutExt.state" || name == "$romNameWithoutExt.state.auto" || name.startsWith("$romNameWithoutExt.state")
                    }
                    if (files != null) {
                        for (file in files) {
                            if (file.lastModified() > latestTime) {
                                latestTime = file.lastModified()
                                latestStateFile = file
                            }
                        }
                    }
                }
            }

            if (latestStateFile != null) {
                return Pair(latestStateFile, formatTimeAgo(latestTime))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando save state para ${game.title}", e)
        }
        return Pair(null, "")
    }

    /**
     * Devuelve todos los save states disponibles para un juego.
     */
    suspend fun getAvailableSaveStates(context: Context, game: Game): List<CloudSaveManager.SaveState> = withContext(Dispatchers.IO) {
        val cloudSaveManager = CloudSaveManager(context)
        cloudSaveManager.getLocalSaveStates(game)
    }

    /**
     * Busca la captura de pantalla asociada al último Save State (.state.png)
     */
    fun findLatestSaveScreenshot(context: Context, game: Game): File? {
        try {
            val romPathUri = Uri.parse(game.path)
            val romRealPath = getRealPathFromUri(context, romPathUri)
            if (romRealPath.isEmpty()) return null

            val romFile = File(romRealPath)
            val romNameWithoutExt = romFile.nameWithoutExtension

            val candidateDirs = getCandidateStateDirs()
            candidateDirs.add(romFile.parentFile ?: File("/"))

            var latestScreenshot: File? = null
            var latestTime = 0L

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    // RetroArch suele guardar capturas como romname.state.png o romname.state.auto.png
                    val files = dir.listFiles { _, name ->
                        (name.startsWith(romNameWithoutExt) && name.endsWith(".png") && name.contains(".state"))
                    }
                    if (files != null) {
                        for (file in files) {
                            if (file.lastModified() > latestTime) {
                                latestTime = file.lastModified()
                                latestScreenshot = file
                            }
                        }
                    }
                }
            }
            return latestScreenshot
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * FASE 2: Busca juegos que se lanzaron un día como hoy (mismo día y mes)
     */
    fun getGamesReleasedToday(allGames: List<Game>): List<Game> {
        val today = Calendar.getInstance()
        val day = today.get(Calendar.DAY_OF_MONTH)
        val month = today.get(Calendar.MONTH) + 1 // Calendar.MONTH es 0-based

        return allGames.filter { game ->
            val releaseDate = game.releaseDate ?: ""
            // Formatos comunes: YYYY-MM-DD o DD/MM/YYYY o YYYY
            // Intentamos detectar si contiene el mes y día actual
            val match = Regex("(\\d{2,4})[-/](\\d{1,2})[-/](\\d{1,2})").find(releaseDate)
            if (match != null) {
                val m = match.groupValues[2].toIntOrNull()
                val d = match.groupValues[3].toIntOrNull()
                m == month && d == day
            } else {
                false
            }
        }
    }

    private fun getCandidateStateDirs(): MutableList<File> {
        return mutableListOf(
            File("/storage/emulated/0/RetroArch/states/"),
            File("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states/"),
            File("/storage/emulated/0/Android/data/com.retroarch/files/states/"),
            File("/storage/emulated/0/Android/data/com.retroarch.plus/files/states/")
        )
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 60 -> "Hace $minutes min"
            hours < 24 -> "Hace $hours h"
            days == 1L -> "Ayer"
            else -> "Hace $days d"
        }
    }

    private fun getRealPathFromUri(context: Context, uri: Uri): String {
        if (uri.scheme != "content") return uri.path ?: ""
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = split[1]
            if (type == "primary") "/storage/emulated/0/$path" else "/storage/$type/$path"
        } catch (e: Exception) {
            uri.toString().substringAfter("document/").replace("primary%3A", "/storage/emulated/0/").replace("%2F", "/")
        }
    }
}