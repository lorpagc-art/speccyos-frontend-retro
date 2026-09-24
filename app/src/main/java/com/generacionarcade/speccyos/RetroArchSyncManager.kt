/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

/**
 * RetroArchSyncManager — Sincronización activa entre SpeccyOS y RetroArch.
 *
 * Resuelve el problema de desincronización que ocurre cuando:
 * 1. SpeccyOS lanza RetroArch → RA hereda su propio cfg global sin el perfil de plataforma.
 * 2. El usuario vuelve de RetroArch → SpeccyOS no sabe si la sesión fue exitosa.
 * 3. El hardware está en modo EXTREME/PERFORMANCE pero RetroArch ya terminó.
 *
 * FUNCIONALIDADES:
 * ─────────────────────────────────────────────────────────────────────────────
 * A) MONITOR DE RETORNO: detecta cuando el usuario vuelve de RetroArch
 *    y restaura el modo de hardware a BALANCED automáticamente.
 *
 * B) THERMAL GUARD: si el SoC supera 85°C durante emulación, baja el perfil
 *    de EXTREME → PERFORMANCE → BALANCED para evitar throttling.
 *
 * C) BROADCAST RECEIVER: escucha el intent de RetroArch cuando cierra un juego
 *    (org.libretro.RetroArch.EXIT) para limpiar el estado.
 *
 * D) PRE-LAUNCH CHECK: antes de lanzar, verifica si RetroArch ya está en primer
 *    plano (evita lanzamientos duplicados).
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * USO (desde LauncherManager o MainActivity):
 *   RetroArchSyncManager.initialize(context)
 *   RetroArchSyncManager.onGameLaunched(platformId, retroArchPkg)
 *   // … el usuario juega …
 *   // onReturnFromGame() se llama automáticamente via BroadcastReceiver o monitor
 */
object RetroArchSyncManager {

    private const val TAG = "RASyncManager"

    // Intent de RetroArch al cerrar sesión de juego (RetroArch ≥ 1.15)
    private const val RA_EXIT_ACTION = "org.libretro.RetroArch.EXIT"

    // Temperatura de throttling (85°C = 85000 mC)
    private const val THERMAL_THROTTLE_MC = 85_000
    private const val THERMAL_WARN_MC     = 78_000

    private var initialized = false
    private var monitorJob: Job? = null
    private var currentRetroArchPkg: String? = null
    private var currentPlatformId: String? = null
    private lateinit var appContext: Context

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback opcional para notificar a la UI (ej: mostrar "Bienvenido de vuelta")
    var onReturnFromGame: ((platformId: String) -> Unit)? = null
    var onThermalThrottle: ((tempMc: Int, newMode: String) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // BroadcastReceiver para el intent de cierre de RetroArch
    // ─────────────────────────────────────────────────────────────────────────

    private var exitReceiver: BroadcastReceiver? = null

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true

        // Registrar receptor para RA_EXIT_ACTION
        exitReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != RA_EXIT_ACTION) return

                // En Android 12 y anteriores -frecuente en los handhelds baratos que
                // son el publico de la app- no hay forma de registrar este receptor
                // como no exportado, asi que cualquier otra app puede mandar el
                // broadcast y disparar el "he salido del juego": cancelaria el
                // monitor termico y bajaria el hardware a BALANCED en mitad de una
                // partida. Sin sesion activa el aviso no puede ser legitimo.
                if (currentPlatformId == null) {
                    Log.w(TAG, "EXIT de RetroArch ignorado: no hay sesion de juego activa")
                    return
                }

                Log.i(TAG, "RetroArch EXIT broadcast recibido")
                handleReturnFromGame()
            }
        }

        val filter = IntentFilter(RA_EXIT_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(exitReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(exitReceiver, filter)
        }

        Log.i(TAG, "RetroArchSyncManager inicializado")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CICLO DE VIDA DE SESIÓN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Llamar justo antes de lanzar RetroArch.
     * Inicia el monitor de retorno y el thermal guard.
     */
    fun onGameLaunched(platformId: String, retroArchPkg: String) {
        currentPlatformId  = platformId
        currentRetroArchPkg = retroArchPkg
        Log.i(TAG, "Sesión iniciada: $platformId en $retroArchPkg")

        // Cancelar cualquier monitor previo
        monitorJob?.cancel()
        monitorJob = scope.launch {
            monitorSession(retroArchPkg)
        }
    }

    /**
     * Monitor de sesión:
     * - Thermal guard: cada 5 segundos comprueba temperatura.
     * - Return detection: detecta cuando RetroArch deja de estar en primer plano.
     *   (Fallback si el broadcast no llega — ocurre en algunas builds de RA)
     */
    private suspend fun monitorSession(retroArchPkg: String) {
        var thermalMode = "EXTREME"  // modo actual asumido
        var consecutiveCoolChecks = 0
        val settingsManager = SettingsManager(appContext)
        var lastProfileChangeTime = 0L

        // currentCoroutineContext().isActive es la forma correcta dentro de una suspend fun
        while (currentCoroutineContext().isActive) {
            delay(5_000)  // check cada 5 segundos

            // ── Thermal Guard ──────────────────────────────────────────────
            val tempMc = try {
                ((HardwareControlManagerBeta.getCpuTemperature() ?: -1f) * 1000).toInt()
            } catch (_: Exception) { -1 }

            if (tempMc > 0) {
                when {
                    tempMc >= THERMAL_THROTTLE_MC && thermalMode != "BALANCED" -> {
                        Log.w(TAG, "⚠️ Thermal throttle: ${tempMc / 1000}°C → bajando a BALANCED")
                        withContext(Dispatchers.Main) {
                            HardwareControlManagerBeta.applyHardwareMode("BALANCED")
                            onThermalThrottle?.invoke(tempMc, "BALANCED")
                        }
                        thermalMode = "BALANCED"
                        consecutiveCoolChecks = 0
                    }
                    tempMc >= THERMAL_WARN_MC && thermalMode == "EXTREME" -> {
                        Log.w(TAG, "⚠️ Temperatura alta: ${tempMc / 1000}°C → bajando a PERFORMANCE")
                        withContext(Dispatchers.Main) {
                            HardwareControlManagerBeta.applyHardwareMode("PERFORMANCE")
                            onThermalThrottle?.invoke(tempMc, "PERFORMANCE")
                        }
                        thermalMode = "PERFORMANCE"
                        consecutiveCoolChecks = 0
                    }
                    tempMc < THERMAL_WARN_MC - 5_000 -> {
                        consecutiveCoolChecks++
                        if (consecutiveCoolChecks >= 3 && thermalMode != "EXTREME") {
                            Log.i(TAG, "SoC enfriado (${tempMc / 1000}°C) — restaurando modo")
                            consecutiveCoolChecks = 0
                        }
                    }
                    else -> consecutiveCoolChecks = 0
                }
            }

            // ── Dynamic Tuning (FPS / Jank-based) ───────────────────────────
            if (settingsManager.isDynamicTuningEnabled) {
                val sService = HardwareControlManagerBeta.performanceService
                if (sService != null) {
                    val gfxInfo = sService.getPackageGfxInfo(retroArchPkg)
                    if (gfxInfo.isNotEmpty()) {
                        val stats = parseGfxInfo(gfxInfo)
                        if (stats != null && stats.totalFrames > 0) {
                            Log.d(TAG, "FPS-MONITOR: Total frames = ${stats.totalFrames}, Jank = ${stats.jankyFrames} (${stats.jankPercentage}%)")
                            val now = System.currentTimeMillis()
                            if (now - lastProfileChangeTime > 15_000L) {
                                val currentMode = thermalMode
                                if (stats.jankPercentage > 12f && currentMode != "EXTREME") {
                                    val nextMode = if (currentMode == "BALANCED") "PERFORMANCE" else "EXTREME"
                                    Log.i(TAG, "🚀 Jank alto (${stats.jankPercentage}%) -> Escalando perfil a $nextMode")
                                    withContext(Dispatchers.Main) {
                                        HardwareControlManagerBeta.applyHardwareMode(nextMode)
                                    }
                                    thermalMode = nextMode
                                    lastProfileChangeTime = now
                                } else if (stats.jankPercentage < 2f && stats.totalFrames > 30 && currentMode != "BALANCED") {
                                    val nextMode = if (currentMode == "EXTREME") "PERFORMANCE" else "BALANCED"
                                    Log.i(TAG, "🔋 Rendimiento óptimo (${stats.jankPercentage}%) -> Reduciendo perfil a $nextMode")
                                    withContext(Dispatchers.Main) {
                                        HardwareControlManagerBeta.applyHardwareMode(nextMode)
                                    }
                                    thermalMode = nextMode
                                    lastProfileChangeTime = now
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class GfxStats(val totalFrames: Int, val jankyFrames: Int, val jankPercentage: Float)

    fun parseGfxInfo(output: String): GfxStats? {
        try {
            var total = -1
            var janky = -1
            var percentage = -1f

            output.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("Total frames rendered:")) {
                    total = trimmed.substringAfter("rendered:").trim().toIntOrNull() ?: -1
                } else if (trimmed.startsWith("Janky frames:")) {
                    val parts = trimmed.substringAfter("frames:").trim().split(" ")
                    janky = parts.firstOrNull()?.toIntOrNull() ?: -1
                    val pctStr = parts.getOrNull(1)?.replace("(", "")?.replace(")", "")?.replace("%", "")
                    percentage = pctStr?.toFloatOrNull() ?: -1f
                }
            }

            if (total != -1 && janky != -1) {
                return GfxStats(total, janky, percentage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gfxinfo", e)
        }
        return null
    }

    /**
     * Llamar cuando se detecta que el usuario regresa de RetroArch.
     * Restaura el hardware a BALANCED y notifica a la UI.
     */
    fun handleReturnFromGame() {
        monitorJob?.cancel()
        monitorJob = null

        Log.i(TAG, "Regreso de RetroArch detectado — restaurando hardware a BALANCED")
        HardwareControlManagerBeta.applyHardwareMode("BALANCED")

        val platform = currentPlatformId ?: "unknown"
        onReturnFromGame?.invoke(platform)

        currentPlatformId   = null
        currentRetroArchPkg = null
    }

    /**
     * Verifica si RetroArch ya está en ejecución en primer plano.
     * Evita lanzamientos duplicados.
     */
    fun isRetroArchRunning(context: Context, retroArchPkg: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.any { it.processName == retroArchPkg } ?: false
        } catch (_: Exception) { false }
    }

    /**
     * OJO CON EL CONTEXTO.
     *
     * El receiver se REGISTRA con el Application (desde SpeccyApplication.onCreate)
     * y antes se DESREGISTRABA con la Activity desde MainActivity.onDestroy. Eso
     * lanzaba IllegalArgumentException — silenciada por el catch — así que el
     * receiver seguía vivo en el Application mientras `exitReceiver` se ponía a
     * null e `initialized` a false: receiver huérfano permanente y, al volver a
     * inicializar, un segundo receiver duplicado. La detección de salida de
     * RetroArch quedaba rota tras la primera recreación de la Activity.
     *
     * Ahora se usa SIEMPRE el appContext, que es el que lo registró. El parámetro
     * se mantiene por compatibilidad de firma pero se ignora.
     *
     * Además, el ciclo de vida de este manager es el del PROCESO, no el de una
     * Activity: MainActivity.onDestroy ya no debe llamarlo.
     */
    @Suppress("UNUSED_PARAMETER")
    fun destroy(context: Context? = null) {
        monitorJob?.cancel()
        val ctx = if (::appContext.isInitialized) appContext else context
        exitReceiver?.let { r ->
            try { ctx?.unregisterReceiver(r) } catch (_: Exception) {}
        }
        exitReceiver = null
        initialized  = false
    }

    /**
     * Suelta las referencias de UI sin desmontar el receiver. Es lo que debe
     * llamar una Activity al destruirse: si no, sus lambdas (que capturan la
     * Activity para mostrar Toasts) la mantienen viva en este singleton estático
     * para siempre, y cada rotación o cambio de tema deja otra fuga.
     */
    fun releaseUiCallbacks() {
        onReturnFromGame = null
        onThermalThrottle = null
    }
}
