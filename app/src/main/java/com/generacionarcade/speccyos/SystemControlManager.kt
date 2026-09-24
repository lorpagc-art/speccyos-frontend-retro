/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class SystemControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // --- VOLUMEN ---
    fun getVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun setVolume(level: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }
    fun getMaxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // --- BRILLO ---
    fun getBrightness(): Int {
        return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }
    fun setBrightness(level: Int) {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
    }

    // --- WIFI ---
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled
    fun setWifiEnabled(enabled: Boolean) {
        // Nota: En Android moderno esto puede requerir que el usuario lo haga manualmente o ser System App
        // wifiManager.isWifiEnabled = enabled 
    }

    // --- BLUETOOTH ---
    fun isBluetoothEnabled(): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }
}