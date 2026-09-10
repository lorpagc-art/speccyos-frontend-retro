package com.generacionarcade.speccyos

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Collections

/**
 * ☁️ NEBULA SYNC V2.0 - Motor Inteligente de Guardado en la Nube
 * Subida y Bajada de partidas (.srm, .state) al AppData seguro de Google Drive.
 * ⏱️ TIME MACHINE V1.0 - Motor de escaneo de Save States Locales.
 */
class CloudSaveManager(private val context: Context) {
    private val TAG = "NebulaSync"

    // Rutas comunes de RetroArch en Android
    private val retroarchSavesDir = java.io.File(Environment.getExternalStorageDirectory(), "RetroArch/saves")
    private val retroarchStatesDir = java.io.File(Environment.getExternalStorageDirectory(), "RetroArch/states")

    // --- TIME MACHINE: Explorador Local de Save States ---
    data class SaveState(val file: java.io.File, val imageFile: java.io.File?, val lastModified: Long)

    suspend fun getLocalSaveStates(game: Game): List<SaveState> = withContext(Dispatchers.IO) {
        try {
            if (!retroarchStatesDir.exists()) return@withContext emptyList()
            
            val gameFileNameBase = game.fileName.substringBeforeLast(".")
            
            // Buscamos todos los archivos que empiezan por el nombre y terminan en número (o nada) y extension .state
            val stateFiles = retroarchStatesDir.listFiles { _, name -> 
                name.startsWith(gameFileNameBase) && name.contains(".state") && !name.endsWith(".png") 
            } ?: return@withContext emptyList()

            val results = mutableListOf<SaveState>()
            
            for (stateFile in stateFiles) {
                // Retroarch suele guardar una imagen con el mismo nombre + .png
                val imageFile = java.io.File(retroarchStatesDir, "${stateFile.name}.png")
                results.add(
                    SaveState(
                        file = stateFile,
                        imageFile = if (imageFile.exists()) imageFile else null,
                        lastModified = stateFile.lastModified()
                    )
                )
            }
            
            // Los ordenamos de más reciente a más antiguo
            return@withContext results.sortedByDescending { it.lastModified }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando Save States locales", e)
            emptyList()
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account?.account == null) {
            Log.w(TAG, "No hay cuenta vinculada para Nebula Sync")
            return null
        }

        return try {
            val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
            credential.selectedAccount = account.account

            Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Speccy OS E5 Ultra")
                .build()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Drive Service", e)
            return null
        }
    }

    /**
     * ⬆️ UPLOAD: Sube los archivos locales más recientes a Google Drive
     */
    suspend fun backupGameSaves(game: Game): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        val gameFileNameBase = game.fileName.substringBeforeLast(".")
        var success = false

        try {
            // Buscamos Save RAM (.srm)
            val srmFile = java.io.File(retroarchSavesDir, "$gameFileNameBase.srm")
            if (srmFile.exists()) {
                uploadFileToAppFolder(service, srmFile, "application/octet-stream")
                success = true
            }

            // Buscamos Save States (.state, .state.auto, .state1...)
            if (retroarchStatesDir.exists()) {
                val stateFiles = retroarchStatesDir.listFiles { _, name -> name.startsWith(gameFileNameBase) && name.contains(".state") }
                stateFiles?.forEach { stateFile ->
                    uploadFileToAppFolder(service, stateFile, "application/octet-stream")
                    success = true
                }
            }

            if (success) Log.d(TAG, "✅ Partidas de ${game.title} subidas al Núcleo Nebula.")
            else Log.d(TAG, "ℹ️ No se encontraron partidas locales de ${game.title} para subir.")
            
            return@withContext success
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al subir partidas a Drive", e)
            return@withContext false
        }
    }

    /**
     * ⬇️ DOWNLOAD: Descarga los archivos de Drive si son piùs recenti de los locales
     */
    suspend fun restoreGameSaves(game: Game): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        val gameFileNameBase = game.fileName.substringBeforeLast(".")
        var success = false

        try {
            val query = "name contains '$gameFileNameBase' and 'appDataFolder' in parents and trashed = false"
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, modifiedTime, size)")
                .execute()

            val cloudFiles = result.files ?: emptyList()
            
            if (cloudFiles.isEmpty()) {
                Log.d(TAG, "ℹ️ No hay partidas en la nube para ${game.title}.")
                return@withContext false
            }

            if (!retroarchSavesDir.exists()) retroarchSavesDir.mkdirs()
            if (!retroarchStatesDir.exists()) retroarchStatesDir.mkdirs()

            cloudFiles.forEach { cloudFile ->
                val fileName = cloudFile.name
                val isState = fileName.contains(".state")
                val targetDir = if (isState) retroarchStatesDir else retroarchSavesDir
                val localFile = java.io.File(targetDir, fileName)

                val cloudModifiedTime = cloudFile.modifiedTime?.value ?: 0L
                val localModifiedTime = if (localFile.exists()) localFile.lastModified() else 0L

                if (cloudModifiedTime > localModifiedTime) {
                    Log.d(TAG, "⬇️ Descargando partida más reciente: $fileName")
                    FileOutputStream(localFile).use { outputStream ->
                        service.files().get(cloudFile.id).executeMediaAndDownloadTo(outputStream)
                    }
                    success = true
                } else {
                    Log.d(TAG, "⚡ El archivo local $fileName ya es la versión más reciente.")
                }
            }

            if (success) Log.d(TAG, "✅ Partidas de ${game.title} restauradas con éxito.")
            return@withContext success

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al descargar partidas de Drive", e)
            return@withContext false
        }
    }

    private fun uploadFileToAppFolder(service: Drive, localFile: java.io.File, mimeType: String) {
        val fileName = localFile.name
        
        val query = "name = '$fileName' and 'appDataFolder' in parents and trashed = false"
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id, modifiedTime)")
            .execute()

        val existingFile = result.files?.firstOrNull()
        
        val fileMetadata = File().apply {
            name = fileName
            parents = listOf("appDataFolder")
        }
        
        val mediaContent = FileContent(mimeType, localFile)

        if (existingFile != null) {
            val cloudModified = existingFile.modifiedTime?.value ?: 0L
            if (localFile.lastModified() <= cloudModified) {
                return
            }
            service.files().update(existingFile.id, null, mediaContent).execute()
        } else {
            service.files().create(fileMetadata, mediaContent).execute()
        }
    }
}
