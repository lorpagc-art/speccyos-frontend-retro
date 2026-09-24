/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/**
 * PerformanceService — Implementación del AIDL ejecutada con privilegios Shizuku.
 *
 * ── MEJORAS v2 ────────────────────────────────────────────────────────────────
 * - writeSysfs(): escritura directa con verificación de resultado.
 * - getSocTemperatureMc(): lee de thermal_zone para detectar throttling.
 * - getRetroArchPid(): busca el PID real del proceso RetroArch.
 * - applyPerformanceProfile(): aplica governor + frecuencias de forma atómica.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class PerformanceService : IPerformanceService.Stub() {

    companion object {
        private const val TAG = "SpeccyPerfService"

        // Nodos sysfs de temperatura (por orden de prioridad)
        private val THERMAL_ZONE_PATHS = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )

        /** Raices permitidas para copyFile: solo almacenamiento del usuario. */
        private val ALMACENAMIENTO = listOf("/storage/emulated/0/", "/storage/", "/sdcard/")

        /** Ninguna BIOS legitima pasa de esto (la mayor, naomi.zip, ronda 10 MB). */
        private const val MAX_BIOS_BYTES = 64L * 1024 * 1024

        // Cpus a configurar (cores big — más impacto en emulación)
        private val CPU_CORES = (0..7).map { "/sys/devices/system/cpu/cpu$it" }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MÉTODOS ORIGINALES (sin cambios de firma para compatibilidad)
    // ─────────────────────────────────────────────────────────────────────────

    override fun executeCommand(command: String) {
        // Este metodo corre CON PRIVILEGIOS y ejecutaba literalmente lo que le
        // llegara por el binder. Es la ultima linea de defensa y hasta ahora no
        // habia ninguna: se valida con el mismo criterio que el resto de la app.
        if (!SpeccySysfsProbe.isSafeCommand(command)) {
            Log.w(TAG, "executeCommand rechazado por validacion: $command")
            return
        }
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command)).waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "executeCommand error: $command", e)
        }
    }

    override fun executeCommands(commands: List<String>) {
        commands.forEach { executeCommand(it) }
    }

    override fun readSysfs(path: String): String =
        try { File(path).readText().trim() } catch (_: Exception) { "" }

    override fun exit() = exitProcess(0)

    /** Ver el AIDL: listado de una carpeta, para la carpeta `system` de RetroArch. */
    override fun listDir(dir: String): String = try {
        File(dir).listFiles()?.joinToString("\n") { it.name }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "listDir($dir) falló: ${e.message}")
        ""
    }

    /**
     * Copia un fichero con privilegios. Solo dentro del almacenamiento del
     * usuario y con un tamaño razonable para una BIOS: este servicio corre como
     * root o shell, y no tiene por que servir de copiadora general.
     */
    override fun copyFile(src: String, dst: String): Boolean = try {
        val origen = File(src)
        val valido = ALMACENAMIENTO.any { src.startsWith(it) } &&
            ALMACENAMIENTO.any { dst.startsWith(it) } &&
            origen.isFile && origen.length() in 1..MAX_BIOS_BYTES
        if (!valido) {
            Log.w(TAG, "copyFile rechazado: $src -> $dst")
            false
        } else {
            File(dst).parentFile?.mkdirs()
            origen.copyTo(File(dst), overwrite = true)
            File(dst).length() == origen.length()
        }
    } catch (e: Exception) {
        Log.e(TAG, "copyFile($src -> $dst) falló", e)
        false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NUEVOS MÉTODOS v2
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escribe [value] en [path] con privilegios Shizuku.
     * @return true si el fichero existe y la escritura no lanza excepción.
     */
    override fun writeSysfs(path: String, value: String): Boolean {
        return try {
            val f = File(path)
            if (!f.exists()) {
                Log.w(TAG, "writeSysfs: nodo no existe — $path")
                return false
            }
            f.writeText(value)
            Log.d(TAG, "writeSysfs OK: $path = $value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeSysfs error: $path = $value", e)
            false
        }
    }

    /**
     * Lee la temperatura del SoC desde thermal_zone.
     * Algunos SoCs reportan en milligrados, otros en grados; normalizamos a mC.
     * @return Temperatura en milligrados Celsius, o -1 si no disponible.
     */
    override fun getSocTemperatureMc(): Int {
        for (path in THERMAL_ZONE_PATHS) {
            val raw = readSysfs(path)
            if (raw.isNotEmpty()) {
                val value = raw.toIntOrNull() ?: continue
                // Valores < 1000 → probablemente en °C, convertir a mC
                return if (value < 1000) value * 1000 else value
            }
        }
        return -1
    }

    /**
     * Obtiene el PID del proceso principal de RetroArch.
     * Usa /proc para evitar depender de herramientas externas.
     */
    override fun getRetroArchPid(retroArchPkg: String): String {
        return try {
            // Leer /proc/{pid}/cmdline para cada proceso y buscar el paquete
            File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }
                ?.firstOrNull { procDir ->
                    try {
                        File(procDir, "cmdline").readText()
                            .replace("\u0000", "")
                            .startsWith(retroArchPkg)
                    } catch (_: Exception) { false }
                }
                ?.name
                ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "getRetroArchPid error", e)
            ""
        }
    }

    /**
     * Aplica un perfil de CPU/GPU de forma atómica.
     * Escribe governor y frecuencias en todos los cores disponibles.
     */
    override fun applyPerformanceProfile(
        cpuGovernor: String,
        cpuMinFreqKhz: Int,
        cpuMaxFreqKhz: Int,
        gpuGovernor: String
    ) {
        Log.i(TAG, "Aplicando perfil: cpu=$cpuGovernor [$cpuMinFreqKhz-$cpuMaxFreqKhz kHz], gpu=$gpuGovernor")

        for (cpuPath in CPU_CORES) {
            val freqPath = "$cpuPath/cpufreq"
            if (!File(freqPath).exists()) continue

            // Governor
            if (cpuGovernor.isNotBlank()) {
                writeSysfs("$freqPath/scaling_governor", cpuGovernor)
            }
            // Frecuencia mínima
            if (cpuMinFreqKhz > 0) {
                writeSysfs("$freqPath/scaling_min_freq", cpuMinFreqKhz.toString())
            }
            // Frecuencia máxima
            if (cpuMaxFreqKhz > 0) {
                writeSysfs("$freqPath/scaling_max_freq", cpuMaxFreqKhz.toString())
            }
        }

        // GPU governor — rutas comunes para Adreno y Mali
        if (gpuGovernor.isNotBlank()) {
            val gpuPaths = listOf(
                // Adreno (Qualcomm)
                "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
                "/sys/class/kgsl/kgsl-3d0/governor",
                // Mali (MediaTek / Samsung Exynos)
                "/sys/class/devfreq/gpu/governor",
                "/sys/bus/platform/drivers/mali/gpu/devfreq/governor"
            )
            for (path in gpuPaths) {
                if (File(path).exists()) {
                    writeSysfs(path, gpuGovernor)
                    break
                }
            }
        }
    }

    override fun getPackageGfxInfo(packageName: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "gfxinfo", packageName))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "getPackageGfxInfo error: $packageName", e)
            ""
        }
    }
}
