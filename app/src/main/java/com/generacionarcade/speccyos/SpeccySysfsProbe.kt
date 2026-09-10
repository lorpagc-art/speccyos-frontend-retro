package com.generacionarcade.speccyos

import android.util.Log
import java.io.File

/**
 * SpeccySysfsProbe
 * ----------------------------------------------------------------------------
 * Descubre EN RUNTIME los nodos sysfs reales del dispositivo en lugar de
 * confiar en rutas fijas escritas a mano en un JSON.
 *
 * Motivo: el motor anterior escribía en rutas como
 * "/sys/class/fan/level" o "/sys/class/devfreq/6000000.gpu/governor".
 * Si el dispositivo no las tenía (la mayoría), el comando fallaba en silencio
 * y el usuario veía un "modo aplicado" que no aplicaba nada.
 *
 * Aquí sondeamos, validamos y cacheamos. Lo que no existe, no se toca.
 */
object SpeccySysfsProbe {

    private const val TAG = "SpeccyProbe"

    /** Rutas permitidas. Cualquier escritura fuera de esto se rechaza. */
    private val ALLOWED_PATH = Regex("^/sys/(devices|class|block|module|kernel)/[A-Za-z0-9_.:@/-]+$")

    /** Valores permitidos: gobernadores, enteros, schedulers. Nada de shell. */
    private val ALLOWED_VALUE = Regex("^[A-Za-z0-9_.-]{1,48}$")

    data class Node(val path: String, val current: String?, val available: List<String>)

    data class HardwareMap(
        val cpuGovernorNodes: List<String>,
        val cpuAvailableGovernors: List<String>,
        val cpuMaxFreqNodes: List<String>,
        val gpuGovernorNode: String?,
        val gpuAvailableGovernors: List<String>,
        val gpuMaxFreqNode: String?,
        val fanNodes: List<String>,
        val ioSchedulerNodes: List<String>,
        val thermalZones: List<String>,
        val cpuCoreCount: Int,
        val bigCoreIds: List<Int>
    ) {
        val hasAnyTunable: Boolean
            get() = cpuGovernorNodes.isNotEmpty() || gpuGovernorNode != null || fanNodes.isNotEmpty()
    }

    @Volatile
    private var cached: HardwareMap? = null

    fun map(force: Boolean = false): HardwareMap {
        cached?.let { if (!force) return it }
        val m = probe()
        cached = m
        return m
    }

    // ------------------------------------------------------------------ probe

    private fun probe(): HardwareMap {
        val cpuGov = mutableListOf<String>()
        val cpuMax = mutableListOf<String>()
        var availableGovs: List<String> = emptyList()

        // 1) Política moderna: /sys/devices/system/cpu/cpufreq/policyN/
        val policyDir = File("/sys/devices/system/cpu/cpufreq")
        policyDir.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }
            ?.forEach { p ->
                val gov = File(p, "scaling_governor")
                if (gov.exists()) {
                    cpuGov += gov.absolutePath
                    if (availableGovs.isEmpty()) {
                        availableGovs = readList(File(p, "scaling_available_governors"))
                    }
                }
                val max = File(p, "scaling_max_freq")
                if (max.exists()) cpuMax += max.absolutePath
            }

        // 2) Fallback legacy: /sys/devices/system/cpu/cpuN/cpufreq/
        if (cpuGov.isEmpty()) {
            File("/sys/devices/system/cpu").listFiles { f ->
                f.isDirectory && f.name.matches(Regex("cpu\\d+"))
            }?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }?.forEach { c ->
                val gov = File(c, "cpufreq/scaling_governor")
                if (gov.exists()) {
                    cpuGov += gov.absolutePath
                    if (availableGovs.isEmpty()) {
                        availableGovs = readList(File(c, "cpufreq/scaling_available_governors"))
                    }
                }
            }
        }

        // 3) GPU: probamos los candidatos habituales de cada familia + devfreq genérico
        var gpuGov: String? = null
        var gpuMax: String? = null
        var gpuGovs: List<String> = emptyList()

        val gpuCandidates = buildList {
            add("/sys/class/kgsl/kgsl-3d0/devfreq/governor")            // Adreno
            File("/sys/class/devfreq").listFiles()?.forEach { d ->
                val n = d.name.lowercase()
                if (n.contains("gpu") || n.contains("mali") || n.contains("bifrost") ||
                    n.contains("kgsl") || n.contains("gpufreq")
                ) add(File(d, "governor").absolutePath)
            }
        }
        for (c in gpuCandidates) {
            val f = File(c)
            if (f.exists()) {
                gpuGov = c
                gpuGovs = readList(File(f.parentFile, "available_governors"))
                val mf = File(f.parentFile, "max_freq")
                if (mf.exists()) gpuMax = mf.absolutePath
                break
            }
        }

        // 4) Ventilador / cooling device. Buscamos por *tipo*, no por ruta fija.
        val fans = mutableListOf<String>()
        listOf("/sys/class/fan/level", "/sys/class/fan/fan_speed", "/sys/class/fan/mode",
               "/sys/class/hwmon/hwmon0/pwm1")
            .filter { File(it).exists() }
            .forEach { fans += it }

        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("cooling_device") }
            ?.forEach { cd ->
                val type = readText(File(cd, "type"))?.lowercase().orEmpty()
                if (type.contains("fan") || type.contains("blower")) {
                    val cur = File(cd, "cur_state")
                    if (cur.exists()) fans += cur.absolutePath
                }
            }

        // 5) Planificador de E/S del almacenamiento real (no asumimos mmcblk0)
        val ioNodes = mutableListOf<String>()
        File("/sys/block").listFiles { f ->
            f.name.startsWith("mmcblk") || f.name.startsWith("sd") || f.name.startsWith("nvme")
        }?.forEach { b ->
            val s = File(b, "queue/scheduler")
            if (s.exists()) ioNodes += s.absolutePath
        }

        // 6) Zonas térmicas útiles (cpu / soc / gpu / battery excluida)
        val zones = mutableListOf<String>()
        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name }
            ?.forEach { z ->
                val type = readText(File(z, "type"))?.lowercase().orEmpty()
                val temp = File(z, "temp")
                if (temp.exists() && (
                        type.contains("cpu") || type.contains("soc") ||
                        type.contains("gpu") || type.contains("tsens") || type.contains("ap")
                    )
                ) zones += temp.absolutePath
            }
        if (zones.isEmpty() && File("/sys/class/thermal/thermal_zone0/temp").exists()) {
            zones += "/sys/class/thermal/thermal_zone0/temp"
        }

        // 7) Núcleos grandes: los de mayor cpuinfo_max_freq
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until cores).map { i ->
            i to (readText(File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"))
                ?.trim()?.toLongOrNull() ?: 0L)
        }
        val maxFreq = freqs.maxOfOrNull { it.second } ?: 0L
        val big = if (maxFreq > 0) freqs.filter { it.second >= maxFreq * 0.9 }.map { it.first } else emptyList()

        val result = HardwareMap(
            cpuGovernorNodes = cpuGov,
            cpuAvailableGovernors = availableGovs,
            cpuMaxFreqNodes = cpuMax,
            gpuGovernorNode = gpuGov,
            gpuAvailableGovernors = gpuGovs,
            gpuMaxFreqNode = gpuMax,
            fanNodes = fans.distinct(),
            ioSchedulerNodes = ioNodes,
            thermalZones = zones,
            cpuCoreCount = cores,
            bigCoreIds = big
        )
        Log.i(TAG, "Sondeo: cpu=${cpuGov.size} gpu=${gpuGov != null} fans=${fans.size} zones=${zones.size}")
        return result
    }

    // ------------------------------------------------------------- validación

    /** Un comando sólo se acepta si escribe un valor seguro en una ruta permitida. */
    fun isSafeWrite(path: String, value: String): Boolean =
        ALLOWED_PATH.matches(path) && ALLOWED_VALUE.matches(value)

    /** Forma exacta que produce [writeCmd]: escritura citada sobre un nodo de /sys. */
    private val COMANDO_ESCRITURA =
        Regex("^echo '[A-Za-z0-9_.-]{1,48}' > '/sys/[A-Za-z0-9_.:@/-]+'$")

    /**
     * Unico criterio de "comando privilegiado aceptable" de toda la app.
     *
     * Lo comparten las tres rutas que acaban ejecutando shell con privilegios
     * (PerformanceService via Shizuku, el binder PServer del fabricante y el
     * shell root directo). Antes cada una validaba por su cuenta -o no validaba
     * en absoluto-, asi que el nivel de proteccion dependia de por donde entrara
     * el comando.
     */
    fun isSafeCommand(cmd: String): Boolean =
        cmd.startsWith("setprop ") || COMANDO_ESCRITURA.matches(cmd)

    /**
     * Construye "echo <valor> > <ruta>" sólo si es seguro Y el nodo existe.
     * Devuelve null en caso contrario, para que el llamante lo descarte.
     */
    fun writeCmd(path: String, value: String): String? {
        if (!isSafeWrite(path, value)) {
            Log.w(TAG, "Escritura rechazada por validación: $path=$value")
            return null
        }
        if (!File(path).exists()) return null
        return "echo '$value' > '$path'"
    }

    /**
     * Elige el gobernador solicitado sólo si el kernel lo soporta.
     * Si no, degrada a la mejor alternativa disponible en vez de fallar mudo.
     */
    fun pickGovernor(requested: String, available: List<String>): String? {
        if (available.isEmpty()) return requested          // no podemos verificar: lo intentamos
        if (available.contains(requested)) return requested
        val fallbacks = when (requested) {
            "performance" -> listOf("performance", "schedutil", "interactive", "ondemand")
            "powersave" -> listOf("powersave", "conservative", "schedutil")
            else -> listOf("schedutil", "interactive", "ondemand", "conservative")
        }
        return fallbacks.firstOrNull { available.contains(it) }
    }

    // ------------------------------------------------------------------- I/O

    fun readText(f: File): String? = runCatching { f.readText().trim() }.getOrNull()

    private fun readList(f: File): List<String> =
        readText(f)?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()

    /** Temperatura más alta entre las zonas relevantes, en °C. */
    fun currentTemperatureC(): Float? {
        val zones = map().thermalZones
        var best: Float? = null
        for (z in zones) {
            val raw = readText(File(z))?.toFloatOrNull() ?: continue
            val c = if (raw > 1000f) raw / 1000f else raw
            val current = best
            if (c in 1f..120f && (current == null || c > current)) best = c
        }
        return best
    }
}
