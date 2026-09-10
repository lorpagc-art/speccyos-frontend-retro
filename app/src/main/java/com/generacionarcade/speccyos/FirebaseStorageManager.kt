package com.generacionarcade.speccyos

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseStorageManager {
    private val storage = FirebaseStorage.getInstance()
    private val TAG = "SpeccyStorage"

    // Subir un archivo de guardado (.state, .srm, etc)
    suspend fun uploadSaveState(userId: String, platform: String, file: File) {
        val storageRef = storage.reference
        val saveRef = storageRef.child("users/$userId/saves/$platform/${file.name}")

        try {
            val fileUri = Uri.fromFile(file)
            saveRef.putFile(fileUri).await()
            Log.d(TAG, "Partida de $platform sincronizada: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al subir partida", e)
        }
    }

    // Descargar una partida desde la nube
    suspend fun downloadSaveState(userId: String, platform: String, fileName: String, destinationFile: File) {
        val storageRef = storage.reference
        val saveRef = storageRef.child("users/$userId/saves/$platform/$fileName")

        try {
            saveRef.getFile(destinationFile).await()
            Log.d(TAG, "Partida descargada: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error al descargar partida", e)
        }
    }
}
