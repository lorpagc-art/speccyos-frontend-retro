package com.generacionarcade.speccyos

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.nio.charset.Charset

/**
 * 🌌 NEBULA BINDER (Protocolo Imperial de Extracción de Hardware)
 * 
 * Este módulo busca puertas traseras de bajo nivel (Servicios Privilegiados de Fabricantes)
 * implementadas en dispositivos portátiles modernos (Anbernic, Retroid, GameMT, etc.)
 * para inyectar comandos nativos de Linux sin requerir Root o Shizuku.
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class NebulaBinder {
    
    private val TAG = "NebulaBinder"
    private val secretBinder: IBinder?
    
    var isBackdoorOpen: Boolean = false
        private set

    init {
        secretBinder = try {
            // Infiltración mediante Reflexión en el ServiceManager nativo de Android
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            
            // "PServerBinder" es el servicio de Performance oculto en muchos firmwares chinos.
            val rawBinder = getServiceMethod.invoke(serviceManagerClass, "PServerBinder") as IBinder?
            
            if (rawBinder != null) {
                isBackdoorOpen = true
                Log.d(TAG, "🔥 PUERTA TRASERA IMPERIAL ABIERTA (PServer Detectado).")
            } else {
                Log.w(TAG, "❌ PServerBinder no disponible en este dispositivo.")
            }
            
            rawBinder
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al interceptar el Kernel: ${e.message}")
            null
        }
    }

    /**
     * Inyecta un comando directo a la consola de Linux saltándose SELinux.
     */
    fun injectCommand(command: String): Result<String?> {
        if (secretBinder == null) return Result.failure(IllegalStateException("Enlace Neural inactivo."))

        val dataPayload = Parcel.obtain()
        val responsePayload = Parcel.obtain()
        
        return try {
            dataPayload.writeStringArray(arrayOf(command, "1"))
            secretBinder.transact(0, dataPayload, responsePayload, 0)
            Result.success(decodeKernelResponse(responsePayload))
        } catch (throwable: Throwable) {
            Log.e(TAG, "Error inyectando comando: ${throwable.message}")
            Result.failure(throwable)
        } finally {
            dataPayload.recycle()
            responsePayload.recycle()
        }
    }

    private fun decodeKernelResponse(payload: Parcel): String? {
        val byteArray = payload.createByteArray() ?: return null
        val decodedString = String(byteArray, Charset.defaultCharset()).trim()
        return if (decodedString == "null" || decodedString.isEmpty()) null else decodedString
    }
}
