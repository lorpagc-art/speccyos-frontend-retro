/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale

class HardwareViewModel(application: Application) : AndroidViewModel(application) {

    private val hardwareManager = HardwareControlManagerBeta
    private val settingsManager = SettingsManager(application)
    private val nebulaBinder = NebulaBinder()

    private val _uiState = MutableStateFlow(HardwareUiState())
    val uiState = _uiState.asStateFlow()
    
    /**
     * El bucle de monitorizacion solo trabaja mientras el launcher se ve.
     * Sondear sysfs cada 2 s (temperatura, RAM, bateria, bluetooth, root, chequeo
     * de seguridad y escritura de ventilador) mientras hay un emulador delante
     * le roba ciclos al juego. MainActivity lo apaga en ON_STOP y lo enciende en
     * ON_START.
     */
    @Volatile
    private var isMonitoringActive = true

    fun setMonitoringActive(active: Boolean) {
        isMonitoringActive = active
    }

    private var isFanManualOverride = false
    private var temperatureErrorCount = 0
    private var isTemperatureReadingEnabled = true

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                // En segundo plano no se sondea nada: se duerme barato y se sale.
                if (!isMonitoringActive) {
                    delay(5000)
                    continue
                }

                // Ejecutamos la recolección de datos en un hilo secundario (IO)
                val snapshot = withContext(Dispatchers.IO) {
                    val currentTemp = if (isTemperatureReadingEnabled) {
                        hardwareManager.getCpuTemperature()
                    } else null

                    val currentRamUsageStr = hardwareManager.getRamUsage()
                    // /proc/stat es I/O: se lee aqui, no en el hilo principal.
                    val cargaCpu = SpeccyCpuLoad.leer()
                    val batteryLevel = getBatteryLevel()
                    val isWifiEnabled = isWifiConnected()
                    val isBluetoothEnabled = isBluetoothOn()
                    
                    // Prioridad de Privilegios
                    val rootStatus = if (nebulaBinder.isBackdoorOpen) "PSERVER" else hardwareManager.getRootStatus()
                    
                    if (isTemperatureReadingEnabled) {
                        hardwareManager.runSafetyCheck()
                    }

                    MonitoringSnapshot(
                        temp = currentTemp,
                        ramStr = currentRamUsageStr,
                        cpu = cargaCpu,
                        battery = batteryLevel,
                        wifi = isWifiEnabled,
                        bluetooth = isBluetoothEnabled,
                        root = rootStatus
                    )
                }

                // Lógica de control de errores de temperatura tras 3 intentos
                if (isTemperatureReadingEnabled) {
                    if (snapshot.temp == null) {
                        temperatureErrorCount++
                        if (temperatureErrorCount >= 3) {
                            isTemperatureReadingEnabled = false
                            Log.e("HardwareViewModel", "Lectura de temperatura abortada permanentemente tras 3 fallos.")
                        }
                    } else {
                        temperatureErrorCount = 0
                    }
                }

                // Procesamiento de datos y actualización de UI en el hilo principal
                val currentRamUsage = try {
                    val used = snapshot.ramStr.substringBefore("GB").trim().toFloat()
                    val total = snapshot.ramStr.substringAfter("/").substringBefore("GB").trim().toFloat()
                    used / total
                } catch (e: Exception) { 0.35f }

                // Carga de CPU REAL. Antes era una constante del perfil elegido
                // (ECO 0.3, BALANCED 0.5...), o sea un numero que se enseñaba
                // como telemetria sin serlo: no se movia hiciera lo que hiciera
                // la consola. Si el sistema no deja leer /proc/stat se deja en
                // -1 y la interfaz muestra "—", en vez de inventarse un valor.
                val cpuLoad = snapshot.cpu ?: -1f
                
                var fanSpeedLevel = _uiState.value.fanSpeedLevel
                if (!isFanManualOverride) {
                    fanSpeedLevel = when (settingsManager.manualProfile) {
                        "ECO" -> 0.0f  
                        "BALANCED" -> 0.33f 
                        "PERFORMANCE" -> 0.66f 
                        "EXTREME" -> 1.0f 
                        else -> 0.33f
                    }
                }

                val estimatedFps = when (settingsManager.manualProfile) {
                    "ECO" -> 30 
                    "BALANCED" -> 45
                    "PERFORMANCE" -> 60
                    "EXTREME" -> 75 
                    else -> 45
                }

                _uiState.value = _uiState.value.copy(
                    temperature = snapshot.temp ?: _uiState.value.temperature,
                    ramUsage = currentRamUsage,
                    isManualMode = settingsManager.isManualPerformanceMode,
                    currentProfile = settingsManager.manualProfile,
                    isPro = settingsManager.isProUser,
                    cpuLoad = cpuLoad,
                    fanSpeedLevel = fanSpeedLevel,
                    estimatedFps = estimatedFps,
                    batteryLevel = snapshot.battery,
                    isWifiEnabled = snapshot.wifi,
                    isBluetoothEnabled = snapshot.bluetooth,
                    rootStatus = snapshot.root
                )
                
                // Solo cuando cambia: reescribir el mismo nivel cada 2 segundos
                // no aporta nada y, con Shizuku, es un binder por vuelta.
                if (!isFanManualOverride) {
                    val level = (fanSpeedLevel * 3).toInt()
                    if (level != ultimoNivelVentilador) {
                        ultimoNivelVentilador = level
                        withContext(Dispatchers.IO) {
                            hardwareManager.setFanSpeed(level)
                        }
                    }
                }
                
                delay(2000)
            }
        }
    }

    // Estructura interna para el paso de datos entre hilos
    private data class MonitoringSnapshot(
        val temp: Float?,
        val ramStr: String,
        val cpu: Float?,
        val battery: Float,
        val wifi: Boolean,
        val bluetooth: Boolean,
        val root: String
    )

    private fun getBatteryLevel(): Float {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = getApplication<Application>().registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 1f else level.toFloat() / scale.toFloat()
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }

    private fun isBluetoothOn(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bluetoothManager.adapter?.isEnabled == true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun setManualProfile(profileId: String) {
        settingsManager.isManualPerformanceMode = true
        settingsManager.manualProfile = profileId
        hardwareManager.applyPerformanceProfile(profileId)

        _uiState.value = _uiState.value.copy(
            isManualMode = true,
            currentProfile = profileId
        )
    }

    fun setManualFanSpeed(level: Float) {
        isFanManualOverride = true
        _uiState.value = _uiState.value.copy(fanSpeedLevel = level)
        hardwareManager.setFanSpeed((level * 3).toInt())
    }

    fun toggleVulkan(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isVulkanForced = enabled)
        hardwareManager.forceVulkanRenderer(enabled)
    }

    /** Ultimo nivel de ventilador escrito; -1 = todavia ninguno. */
    private var ultimoNivelVentilador: Int = -1

    fun requestShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) {
                // Not supported
            } else {
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.e("HardwareViewModel", "Shizuku request failed", e)
        }
    }

    fun reconnectShizuku() {
        hardwareManager.initialize(settingsManager.manualHardwareId, getApplication())
    }
}

data class HardwareUiState(
    val temperature: Float = 0f,
    val ramUsage: Float = 0f,
    val cpuLoad: Float = 0f,
    val fanSpeedLevel: Float = 0f,
    val estimatedFps: Int = 0,
    val isManualMode: Boolean = false,
    val currentProfile: String = "BALANCED",
    val isPro: Boolean = false,
    val batteryLevel: Float = 1f,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isVulkanForced: Boolean = false,
    val rootStatus: String = "NATIVE"
)
