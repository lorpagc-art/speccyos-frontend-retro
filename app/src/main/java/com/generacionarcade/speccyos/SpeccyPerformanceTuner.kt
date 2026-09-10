package com.generacionarcade.speccyos

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SpeccyPerformanceTuner
 * ----------------------------------------------------------------------------
 * Motor de rendimiento por consola. Sustituye a `applyHardwareMode()`.
 *
 * Qué arregla respecto al motor anterior:
 *  1. NO usa rutas sysfs fijas de un JSON: sondea el hardware real
 *     (SpeccySysfsProbe) y sólo escribe en nodos que existen.
 *  2. VALIDA ruta y valor con listas blancas antes de construir el comando
 *     (antes se concatenaban strings directamente hacia un shell root).
 *  3. Verifica que el gobernador exista en el kernel; si no, degrada en vez
 *     de fallar en silencio.
 *  4. GUARDA el estado original y sabe restaurarlo (`restoreDefaults`).
 *  5. Vigilancia térmica real: degrada solo el modo, con histéresis, y avisa.
 *  6. Reporta si el cambio se aplicó de verdad (releyendo el nodo), en lugar
 *     de mostrar un "aplicado" optimista.
 *
 * Backends, por orden: Shizuku UserService > root (libsu) > PServer del
 * fabricante > ninguno (modo informativo).
 */
object SpeccyPerformanceTuner {

    private const val TAG = "SpeccyTuner"

    const val MODE_ECO = "ECO"
    const val MODE_BALANCED = "BALANCED"
    const val MODE_PERFORMANCE = "PERFORMANCE"
    const val MODE_EXTREME = "EXTREME"

    val ALL_MODES = listOf(MODE_ECO, MODE_BALANCED, MODE_PERFORMANCE, MODE_EXTREME)

    /** Resultado honesto de una aplicación de perfil. */
    data class ApplyResult(
        val requestedMode: String,
        val effectiveMode: String,
        val backend: String,
        val applied: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val verified: Boolean = false,
        val message: String = ""
    ) {
        val success: Boolean get() = applied.isNotEmpty()
    }

    private data class ModeSpec(
        val cpuGov: String,
        val gpuGovKey: String,           // "balanced" | "performance" | "powersave"
        val ioScheduler: String,
        val fanRatio: Float,             // 0f..1f del maxFanLevel
        val vulkanHint: Boolean,
        val description: String
    )

    private val SPECS = mapOf(
        MODE_ECO to ModeSpec("powersave", "powersave", "noop", 0f, false,
            "Ahorro máximo. 8/16 bits y navegación por la biblioteca."),
        MODE_BALANCED to ModeSpec("schedutil", "balanced", "cfq", 0.34f, false,
            "Equilibrio. PS1, N64, PSP y Dreamcast sin castigar la batería."),
        MODE_PERFORMANCE to ModeSpec("performance", "performance", "deadline", 0.67f, true,
            "Frecuencias sostenidas. GameCube, PS2 y 3DS."),
        MODE_EXTREME to ModeSpec("performance", "performance", "deadline", 1f, true,
            "Sin límites. Switch y Windows. Requiere refrigeración activa.")
    )

    // ------------------------------------------------------------------ estado

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentMode = MutableStateFlow(MODE_BALANCED)
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()

    private val _throttled = MutableStateFlow(false)
    val throttled: StateFlow<Boolean> = _throttled.asStateFlow()

    private val _lastResult = MutableStateFlow<ApplyResult?>(null)
    val lastResult: StateFlow<ApplyResult?> = _lastResult.asStateFlow()

    /** Valores originales por nodo, para poder restaurar al salir. */
    private val originalValues = LinkedHashMap<String, String>()

    private var device: SpeccyHardwareRegistry.SpeccyDevice = SpeccyHardwareRegistry.get("generic")
    private var appContext: Context? = null
    private var watchdogRunning = false
    private var userRequestedMode = MODE_BALANCED

    // ------------------------------------------------------------------- API

    fun init(context: Context, deviceId: String? = null) {
        appContext = context.applicationContext
        device = if (SpeccyHardwareRegistry.exists(deviceId)) {
            SpeccyHardwareRegistry.get(deviceId)
        } else {
            SpeccyHardwareRegistry.detect()
        }
        scope.launch {
            // libsu 5.x: isAppGrantedRoot() devuelve NULL mientras no exista un shell.
            // Sin este precalentamiento, backend() nunca detectaba ROOT.
            rootGranted = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
            SpeccySysfsProbe.map(force = true)
            snapshotOriginals()
            startThermalWatchdog()
        }
    }

    fun activeDevice(): SpeccyHardwareRegistry.SpeccyDevice = device

    fun describe(mode: String): String = SPECS[mode]?.description.orEmpty()

    /**
     * Estado de root cacheado. `Shell.getShell()` BLOQUEA (puede lanzar el dialogo
     * de superusuario), asi que jamas debe evaluarse desde el hilo principal:
     * se calcula una sola vez en init(), sobre Dispatchers.IO.
     */
    @Volatile private var rootGranted = false

    fun backend(): String = when {
        HardwareControlManagerBeta.performanceService != null -> "SHIZUKU"
        rootGranted -> "ROOT"
        HardwareControlManagerBeta.isPServerAvailable -> "PSERVER"
        else -> "NONE"
    }

    /** ¿Puede este dispositivo aguantar el modo pedido? */
    fun isModeAdvisable(mode: String): Boolean = when (mode) {
        MODE_EXTREME -> device.hasFan
        else -> true
    }

    fun applyMode(mode: String, fromWatchdog: Boolean = false): ApplyResult {
        if (!fromWatchdog) userRequestedMode = mode
        val spec = SPECS[mode] ?: SPECS.getValue(MODE_BALANCED)
        val map = SpeccySysfsProbe.map()
        val back = backend()

        if (back == "NONE") {
            val r = ApplyResult(
                mode, _currentMode.value, back,
                message = "Sin Shizuku ni root: SpeccyOS aplicará sólo ajustes " +
                    "de RetroArch y de la propia interfaz."
            )
            _lastResult.value = r
            return r
        }
        if (!map.hasAnyTunable) {
            val r = ApplyResult(mode, _currentMode.value, back,
                message = "El kernel de este dispositivo no expone nodos ajustables.")
            _lastResult.value = r
            return r
        }

        val cmds = mutableListOf<String>()
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // --- CPU: un gobernador por clúster, verificado contra el kernel
        val cpuGov = SpeccySysfsProbe.pickGovernor(spec.cpuGov, map.cpuAvailableGovernors)
        if (cpuGov != null) {
            map.cpuGovernorNodes.forEach { node ->
                val c = SpeccySysfsProbe.writeCmd(node, cpuGov)
                if (c != null) { cmds += c; applied += "cpu:${node.substringAfterLast('/')}=$cpuGov" }
                else skipped += node
            }
        } else skipped += "cpu_governor(${spec.cpuGov} no soportado)"

        // --- GPU: nombre de gobernador dependiente de la familia de SoC
        val gpuWanted = when (spec.gpuGovKey) {
            "performance" -> device.socFamily.gpuGovPerformance
            "powersave" -> "powersave"
            else -> device.socFamily.gpuGovBalanced
        }
        map.gpuGovernorNode?.let { node ->
            val gov = SpeccySysfsProbe.pickGovernor(gpuWanted, map.gpuAvailableGovernors)
            val c = gov?.let { SpeccySysfsProbe.writeCmd(node, it) }
            if (c != null) { cmds += c; applied += "gpu=$gov" } else skipped += node
        }

        // --- Ventilador: proporcional al nivel máximo real del dispositivo
        if (device.hasFan && device.maxFanLevel > 0) {
            val level = Math.round(spec.fanRatio * device.maxFanLevel).coerceIn(0, device.maxFanLevel)
            map.fanNodes.forEach { node ->
                val c = SpeccySysfsProbe.writeCmd(node, level.toString())
                if (c != null) { cmds += c; applied += "fan=$level" } else skipped += node
            }
        }

        // --- Planificador de E/S: acelera la carga de ROMs grandes (CHD/ISO)
        map.ioSchedulerNodes.forEach { node ->
            val avail = SpeccySysfsProbe.readText(File(node))
                ?.replace("[", "")?.replace("]", "")
                ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            val wanted = when {
                avail.contains(spec.ioScheduler) -> spec.ioScheduler
                avail.contains("mq-deadline") -> "mq-deadline"
                avail.contains("kyber") -> "kyber"
                avail.contains("bfq") -> "bfq"
                else -> null
            }
            val c = wanted?.let { SpeccySysfsProbe.writeCmd(node, it) }
            if (c != null) { cmds += c; applied += "io=$wanted" } else skipped += node
        }

        // --- Propiedades de renderizado (no son sysfs: van por setprop)
        if (spec.vulkanHint && device.tuning.videoDriver == "vulkan") {
            cmds += "setprop debug.hwui.renderer vulkan"
            cmds += "setprop debug.renderengine.backend vulkan"
            applied += "hwui=vulkan"
        } else if (!spec.vulkanHint) {
            cmds += "setprop debug.hwui.renderer skiagl"
            cmds += "setprop debug.renderengine.backend skiagl"
        }

        execute(cmds)

        _currentMode.value = mode
        val result = ApplyResult(
            requestedMode = mode,
            effectiveMode = mode,
            backend = back,
            applied = applied,
            skipped = skipped,
            verified = false,
            message = spec.description
        )
        _lastResult.value = result

        // Verificación diferida: releemos los nodos para confirmar el cambio.
        scope.launch {
            delay(600)
            val ok = map.cpuGovernorNodes.firstOrNull()?.let { n ->
                SpeccySysfsProbe.readText(File(n)) == cpuGov
            } ?: false
            _lastResult.value = result.copy(
                verified = ok,
                message = if (ok) spec.description
                else spec.description + " (no se pudo confirmar el cambio en el kernel)"
            )
        }
        return result
    }

    /**
     * Perfil recomendado por el catalogo. Es seguro llamarlo desde el hilo
     * principal: no bloquea (backend() lee el estado cacheado) y la escritura
     * real de los nodos ocurre en Dispatchers.IO dentro de execute().
     */
    fun applyRecommended(): ApplyResult = applyMode(device.defaultMode)

    /**
     * Perfil por sistema emulado: un NES no necesita el mismo techo que un PS2.
     * Ahorra batería sin que el usuario tenga que tocar nada.
     */
    fun applyForPlatform(platformId: String?): ApplyResult {
        val p = platformId?.lowercase().orEmpty()
        val demand = when {
            p.isEmpty() -> 2
            p in setOf("switch", "wiiu", "windows", "ps3", "psvita") -> 4
            p in setOf("ps2", "gc", "wii", "n3ds", "naomi2", "stv", "model3") -> 3
            p in setOf("dreamcast", "psp", "nds", "saturn", "naomi", "n64", "model2") -> 2
            p in setOf("psx", "segacd", "pcenginecd", "3do", "atarijaguar", "amiga") -> 1
            else -> 0
        }
        val ceiling = device.tier.rank
        val mode = when {
            demand >= 4 && ceiling >= 5 && device.hasFan -> MODE_EXTREME
            demand >= 3 -> MODE_PERFORMANCE
            demand >= 1 -> MODE_BALANCED
            else -> MODE_ECO
        }
        return applyMode(mode)
    }

    /** ¿El dispositivo puede con este sistema? Para avisar antes de lanzar. */
    fun canRun(platformId: String?): Boolean {
        val p = platformId?.lowercase().orEmpty()
        val needed = when {
            p in setOf("switch", "wiiu", "windows", "ps3") -> 5
            p in setOf("ps2", "gc", "wii", "n3ds", "psvita") -> 4
            p in setOf("dreamcast", "psp", "nds", "saturn", "naomi") -> 2
            p in setOf("psx", "n64", "segacd", "3do") -> 1
            else -> 0
        }
        return device.tier.rank >= needed
    }

    fun setFan(level: Int) {
        if (!device.hasFan) return
        val lvl = level.coerceIn(0, maxOf(device.maxFanLevel, 1))
        val cmds = SpeccySysfsProbe.map().fanNodes.mapNotNull {
            SpeccySysfsProbe.writeCmd(it, lvl.toString())
        }
        execute(cmds)
    }

    /** Devuelve el hardware al estado en que estaba cuando arrancó SpeccyOS. */
    fun restoreDefaults() {
        if (originalValues.isEmpty()) return
        val cmds = originalValues.mapNotNull { (path, value) ->
            SpeccySysfsProbe.writeCmd(path, value)
        }
        execute(cmds)
        _currentMode.value = MODE_BALANCED
        _throttled.value = false
    }

    // -------------------------------------------------------------- internos

    private suspend fun snapshotOriginals() = withContext(Dispatchers.IO) {
        if (originalValues.isNotEmpty()) return@withContext
        val m = SpeccySysfsProbe.map()
        (m.cpuGovernorNodes + listOfNotNull(m.gpuGovernorNode) + m.fanNodes).forEach { p ->
            SpeccySysfsProbe.readText(File(p))?.let { v ->
                if (SpeccySysfsProbe.isSafeWrite(p, v)) originalValues[p] = v
            }
        }
        Log.i(TAG, "Estado original guardado de ${originalValues.size} nodos.")
    }

    /**
     * Vigilancia térmica con histéresis: baja el modo al pasar el techo del
     * dispositivo y lo recupera cuando se enfría 8 °C por debajo. El motor
     * anterior forzaba BALANCED sin recuperar nunca el modo del usuario.
     */
    private fun startThermalWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        scope.launch {
            while (isActive) {
                val t = SpeccySysfsProbe.currentTemperatureC()
                _temperature.value = t
                if (t != null) {
                    val ceiling = device.thermalCeiling.toFloat()
                    if (!_throttled.value && t >= ceiling) {
                        _throttled.value = true
                        // Solo degradamos: pasar de ECO a BALANCED calentaria MAS.
                        if (_currentMode.value == MODE_PERFORMANCE || _currentMode.value == MODE_EXTREME) {
                            applyMode(MODE_BALANCED, fromWatchdog = true)
                        }
                        // El ventilador va DESPUES: applyMode reescribe su nivel.
                        if (device.hasFan) setFan(device.maxFanLevel)
                        Log.w(TAG, "Throttling térmico a ${t}°C (techo $ceiling)")
                    } else if (_throttled.value && t <= ceiling - 8f) {
                        _throttled.value = false
                        // Reaplicar siempre: es idempotente y devuelve el ventilador al
                        // nivel del perfil (antes se quedaba al maximo indefinidamente).
                        applyMode(userRequestedMode, fromWatchdog = true)
                    }
                }
                delay(if (_throttled.value) 5_000L else 15_000L)
            }
        }
    }

    private fun execute(commands: List<String>) {
        if (commands.isEmpty()) return
        scope.launch {
            // 1) Servicio privilegiado propio vía Shizuku
            HardwareControlManagerBeta.performanceService?.let { svc ->
                runCatching { svc.executeCommands(commands); return@launch }
                    .onFailure { Log.e(TAG, "Shizuku falló", it) }
            }
            // 2) Root directo (ya estamos en Dispatchers.IO, exec() puede bloquear)
            if (rootGranted) {
                runCatching { Shell.cmd(*commands.toTypedArray()).exec() }
                return@launch
            }
            // 3) Backdoor del fabricante (PServer) — delegado al manager existente
            HardwareControlManagerBeta.executePrivilegedFallback(commands)
        }
    }
}
