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

    /**
     * Nodo de ventilador con SU rango real. No todos van de 0 a 3: en las GameMT
     * (E5 Ultra, EX8; Unisoc ums9230) el ventilador es un PWM expuesto como
     * "backlight" (`/sys/class/backlight/sprd_backlight_fan/brightness`) con
     * rango 0-255, mientras que un `cooling_device` de tipo fan suele ir de 0 a
     * max_state. Escribir "3" en el PWM dejaba el ventilador casi parado.
     */
    data class FanNode(val path: String, val maxValue: Int)

    data class HardwareMap(
        val cpuGovernorNodes: List<String>,
        val cpuAvailableGovernors: List<String>,
        val cpuMaxFreqNodes: List<String>,
        val gpuGovernorNode: String?,
        val gpuAvailableGovernors: List<String>,
        val gpuMaxFreqNode: String?,
        val fanNodes: List<FanNode>,
        /** Interruptores de control manual (`fsenable` en GameMT): 1 activa, 0 devuelve al automático. */
        val fanEnableNodes: List<String>,
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
        val fans = mutableListOf<FanNode>()
        // Rutas fijas conocidas, con el rango tipico de cada una: los pwm* de
        // hwmon van de 0 a 255; los "level"/"mode" de fabricante, de 0 a 5.
        listOf(
            "/sys/class/fan/level" to 5,
            "/sys/class/fan/fan_speed" to 5,
            "/sys/class/fan/mode" to 5,
            "/sys/class/hwmon/hwmon0/pwm1" to 255
        ).filter { File(it.first).exists() }
            .forEach { (path, max) -> fans += FanNode(path, max) }

        File("/sys/class/thermal").listFiles { f -> f.name.startsWith("cooling_device") }
            ?.forEach { cd ->
                val type = readText(File(cd, "type"))?.lowercase().orEmpty()
                if (type.contains("fan") || type.contains("blower")) {
                    val cur = File(cd, "cur_state")
                    if (cur.exists()) {
                        val max = readText(File(cd, "max_state"))?.toIntOrNull() ?: 3
                        fans += FanNode(cur.absolutePath, max)
                    }
                }
            }

        // 4b) Ventilador por PWM disfrazado de backlight (Unisoc/GameMT). OJO:
        // el panel tambien es un backlight (`sprd_backlight`), asi que solo se
        // aceptan los que llevan "fan" en el nombre. Verificado en una GameMT E5
        // Ultra el 24-sep-2026: sprd_backlight=14/255 (pantalla),
        // sprd_backlight_fan=200/255 (ventilador, responde al instante).
        File("/sys/class/backlight").listFiles { f -> f.name.lowercase().contains("fan") }
            ?.forEach { b ->
                val bri = File(b, "brightness")
                if (bri.exists()) {
                    val max = readText(File(b, "max_brightness"))?.toIntOrNull() ?: 255
                    fans += FanNode(bri.absolutePath, max)
                }
            }

        // 4b-bis) Ventilador por /proc (GameMT EX8 y otras MediaTek). Rango
        // observado 0..5; el nodo acepta cualquier numero sin validar, asi que
        // el limite lo ponemos nosotros.
        NODOS_PROC_PERMITIDOS.forEach { ruta ->
            if (File(ruta).exists()) fans += FanNode(ruta, 5)
        }

        // 4c) Nodo propio del fabricante: /sys/class/fan -> device/{fsenable,fslevel}.
        // `fsenable` a 1 saca al ventilador del control automatico; `fslevel` no
        // retiene el valor en la E5 Ultra (lo reescribe el driver), asi que se
        // registra igualmente pero el que manda es el PWM de arriba.
        val fanEnables = mutableListOf<String>()
        listOf("/sys/class/fan/device", "/sys/devices/virtual/fan/device").forEach { d ->
            val en = File(d, "fsenable")
            if (en.exists()) fanEnables += en.absolutePath
            val lvl = File(d, "fslevel")
            if (lvl.exists() && fans.none { it.path == lvl.absolutePath }) fans += FanNode(lvl.absolutePath, 5)
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
            fanNodes = fans.distinctBy { it.path },
            fanEnableNodes = fanEnables.distinct(),
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
        (ALLOWED_PATH.matches(path) || path in NODOS_PROC_PERMITIDOS) && ALLOWED_VALUE.matches(value)

    /**
     * Nodos fuera de /sys que si se pueden escribir, uno a uno y por nombre
     * completo. /proc no entra en la lista blanca general -ahi viven cosas como
     * `/proc/sysrq-trigger`-, pero en las GameMT EX8 (MediaTek mt6789) el
     * ventilador SOLO se controla por `/proc/fan_ctr`: no hay ningun
     * `cooling_device` de tipo fan ni PWM en /sys. Verificado el 24-sep-2026.
     */
    private val NODOS_PROC_PERMITIDOS = setOf(
        "/proc/fan_ctr",
        "/proc/mtk_cooler/fan",
        "/proc/driver/fan"
    )

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
        cmd.startsWith("setprop ") || COMANDO_ESCRITURA.matches(cmd) ||
            COMANDOS_FRAMEWORK.any { it.matches(cmd) }

    /**
     * Ordenes del framework de Android que el tuner puede lanzar con shell
     * (Shizuku) o root. Cada una con su forma exacta: nada de argumentos libres.
     *   - `cmd game set --mode N <pkg>`: Game Mode (Android 13+). 2 = rendimiento,
     *     3 = bateria, 1 = estandar. El fabricante sube reloj/ventilador para ese paquete.
     *   - `cmd game reset <pkg>`: vuelve al modo por defecto.
     *   - `am kill-all`: mata procesos en segundo plano cacheados (no servicios en
     *     primer plano). Libera RAM antes de un emulador pesado en equipos de 4-6 GB.
     */
    private val COMANDOS_FRAMEWORK = listOf(
        Regex("^cmd game set --mode [123] [A-Za-z][A-Za-z0-9_.]{2,80}$"),
        Regex("^cmd game reset [A-Za-z][A-Za-z0-9_.]{2,80}$"),
        Regex("^am kill-all$")
    )

    /**
     * Nodos de suelo de frecuencia que cuelgan de cada nodo de gobernador ya
     * sondeado. Se derivan aqui (y no en probe()) para no tocar HardwareMap.
     */
    fun cpuMinFreqNode(governorNode: String): String =
        governorNode.substringBeforeLast('/') + "/scaling_min_freq"

    fun cpuAvailableFreqs(governorNode: String): List<Long> =
        readList(File(governorNode.substringBeforeLast('/') + "/scaling_available_frequencies"))
            .mapNotNull { it.toLongOrNull() }.sorted()

    fun cpuHardwareMaxFreq(governorNode: String): Long? =
        readText(File(governorNode.substringBeforeLast('/') + "/cpuinfo_max_freq"))?.toLongOrNull()

    fun cpuHardwareMinFreq(governorNode: String): Long? =
        readText(File(governorNode.substringBeforeLast('/') + "/cpuinfo_min_freq"))?.toLongOrNull()

    /** devfreq (Mali/kgsl): `min_freq` junto a `governor`, y `available_frequencies`. */
    fun gpuMinFreqNode(governorNode: String): String =
        governorNode.substringBeforeLast('/') + "/min_freq"

    fun gpuAvailableFreqs(governorNode: String): List<Long> =
        readList(File(governorNode.substringBeforeLast('/') + "/available_frequencies"))
            .mapNotNull { it.toLongOrNull() }.sorted()

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
