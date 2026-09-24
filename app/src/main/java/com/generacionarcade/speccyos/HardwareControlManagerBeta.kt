package com.generacionarcade.speccyos

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.util.Locale

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
object HardwareControlManagerBeta {

    private const val TAG = "HardwareEngineUltra"
    private var isInitialized = false
    private lateinit var applicationContext: Context
    private var currentLoadedProfileJson: JSONObject? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Variables de PServer (Tuning nativo sin root)
    private var pServerBinder: IBinder? = null
    var isPServerAvailable = false
        private set

    // Shizuku User Service (Vínculo persistente)
    var performanceService: IPerformanceService? = null
        private set
    private var shizukuServiceArgs: Shizuku.UserServiceArgs? = null

    /** Codigo de la solicitud de permiso; lo recibe el listener de MainActivity. */
    const val SOLICITUD_SHIZUKU = 0

    /** Solo se pide una vez por proceso: si el usuario dice que no, no se insiste. */
    private var permisoShizukuPedido = false
    
    // Callback para notificar a la UI cuando Shizuku termine de conectar
    var onShizukuConnected: (() -> Unit)? = null

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            performanceService = IPerformanceService.Stub.asInterface(service)
            Log.i(TAG, "✅ SpeccyPerformanceService vinculado vía Shizuku exitosamente.")
            onShizukuConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            performanceService = null
            Log.w(TAG, "⚠️ SpeccyPerformanceService desconectado.")
        }
    }

    data class HardwareProfile(
        val id: String,
        val name: String,
        val chipset: String,
        val gpu: String,
        val ram: String,
        val emulationCapacity: String,
        val hasActiveCooling: Boolean,
        val maxFanLevel: Int,
        val iconRes: Int,
        val category: String, // Marca o Familia
        val type: String,      // HANDHELD, SMARTPHONE, TABLET, DESKTOP
        val themeDefault: String = "ULTRA"
    )

    val hardwareProfiles = mapOf(
        "generic" to HardwareProfile("generic", "Hardware Genérico", "Desconocido", "Desconocida", "N/A", "Básico", false, 0, R.drawable.ic_generic_hardware, "OTROS", "OTROS"),
        
        // --- AYN ---
        "odin_2" to HardwareProfile("odin_2", "Odin 2 / Pro / Max", "Snapdragon 8 Gen 2", "Adreno 740", "8/12/16GB", "Xanite Ultra (PS2/Switch Full)", true, 3, R.drawable.ic_handheld_widescreen, "AYN", "HANDHELD"),
        "odin_2_mini" to HardwareProfile("odin_2_mini", "Odin 2 Mini", "Snapdragon 8 Gen 2", "Adreno 740", "8/12GB", "Xanite Ultra (PS2/Switch Full)", true, 3, R.drawable.ic_handheld_widescreen, "AYN", "HANDHELD"),
        "odin_pro" to HardwareProfile("odin_pro", "Odin Pro / Base", "Snapdragon 845", "Adreno 630", "4/8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_widescreen, "AYN", "HANDHELD"),
        "odin_lite" to HardwareProfile("odin_lite", "Odin Lite", "Dimensity 900", "Mali-G68", "4/6/8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_widescreen, "AYN", "HANDHELD"),

        // --- RETROID ---
        "retroid_pocket_5" to HardwareProfile("retroid_pocket_5", "Retroid Pocket 5", "Snapdragon 865", "Adreno 650", "8GB", "PS2 / GC Pro+", true, 3, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD"),
        "retroid_pocket_mini" to HardwareProfile("retroid_pocket_mini", "Retroid Pocket Mini", "Snapdragon 865", "Adreno 650", "6GB", "PS2 / GC Pro+", true, 3, R.drawable.ic_handheld_vertical, "RETROID", "HANDHELD"),
        "retroid_pocket_4_pro" to HardwareProfile("retroid_pocket_4_pro", "Retroid Pocket 4 Pro", "Dimensity 1100", "Mali-G77", "8GB", "PS2 / GC Extremo", true, 3, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD"),
        "retroid_pocket_4" to HardwareProfile("retroid_pocket_4", "Retroid Pocket 4 Base", "Dimensity 900", "Mali-G68", "4GB", "PS2 / GC Alto", true, 3, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD"),
        "retroid_pocket_3_plus" to HardwareProfile("retroid_pocket_3_plus", "Retroid Pocket 3+", "Unisoc T618", "Mali-G52", "4GB", "PS2 / GC Básico", false, 0, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD"),
        "retroid_pocket_2s" to HardwareProfile("retroid_pocket_2s", "Retroid Pocket 2S", "Unisoc T610", "Mali-G52", "3/4GB", "PS2 / GC Básico", false, 0, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD"),

        // --- ANBERNIC ---
        "anbernic_rg556" to HardwareProfile("anbernic_rg556", "Anbernic RG556", "Unisoc T820", "Mali-G57", "8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "anbernic_rg_cube" to HardwareProfile("anbernic_rg_cube", "Anbernic RG Cube", "Unisoc T820", "Mali-G57", "8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_vertical, "ANBERNIC", "HANDHELD"),
        "anbernic_rg406v" to HardwareProfile("anbernic_rg406v", "Anbernic RG406V / RG406H", "Unisoc T820", "Mali-G57", "8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_vertical, "ANBERNIC", "HANDHELD"),
        "anbernic_rg505" to HardwareProfile("anbernic_rg505", "Anbernic RG505", "Unisoc T618", "Mali-G52", "4GB", "PS2 Básico", false, 0, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "anbernic_rg405m" to HardwareProfile("anbernic_rg405m", "Anbernic RG405M / RG405V", "Unisoc T618", "Mali-G52", "4GB", "PS2 Básico", true, 2, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "anbernic_rg557" to HardwareProfile("anbernic_rg557", "Anbernic RG557", "Dimensity 8300", "Mali-G615", "8/12GB", "Switch / Wii U", true, 3, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "anbernic_rg_vita" to HardwareProfile("anbernic_rg_vita", "Anbernic RG Vita / RG477M", "Snapdragon 865", "Adreno 650", "8GB", "PS2 / GC Pro+", true, 3, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "anbernic_rg_slide" to HardwareProfile("anbernic_rg_slide", "Anbernic RG Slide", "Unisoc T820", "Mali-G57", "8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_widescreen, "ANBERNIC", "HANDHELD"),
        "chromecast_4k" to HardwareProfile("chromecast_4k", "Chromecast con Google TV 4K", "Amlogic S905X3", "Mali-G31 MP2", "2GB", "PS1 / N64 Básicos", false, 0, R.drawable.ic_generic_hardware, "GOOGLE", "DESKTOP"),
        "ayaneo_flip_ds" to HardwareProfile("ayaneo_flip_ds", "AYANEO Pocket DS (doble pantalla)", "Snapdragon G3x Gen 2", "Adreno 735", "12GB", "Switch / Wii U (DS nativo)", true, 3, R.drawable.ic_handheld_widescreen, "AYANEO", "HANDHELD", "DUAL_SCREEN"),
        "onexsugar_1" to HardwareProfile("onexsugar_1", "ONEXSUGAR Sugar 1 (plegable)", "Snapdragon G3x Gen 2", "Adreno 735", "12GB", "Switch / Wii U (DS nativo)", true, 3, R.drawable.ic_handheld_widescreen, "ONEXPLAYER", "HANDHELD", "DUAL_SCREEN"),
        "retroid_rp_flip2" to HardwareProfile("retroid_rp_flip2", "Retroid Pocket Flip 2", "Snapdragon 865", "Adreno 650", "8GB", "PS2 / GC Pro+", true, 3, R.drawable.ic_handheld_widescreen, "RETROID", "HANDHELD", "DUAL_SCREEN"),
        "samsung_z_fold_7" to HardwareProfile("samsung_z_fold_7", "Galaxy Z Fold 7", "Snapdragon 8 Elite", "Adreno 830", "12/16GB", "Switch Full", false, 0, R.drawable.ic_handheld_widescreen, "SAMSUNG", "SMARTPHONE", "DUAL_SCREEN"),
        
        // --- AYANEO ---
        "ayaneo_pocket_s" to HardwareProfile("ayaneo_pocket_s", "Ayaneo Pocket S", "Snapdragon G3x Gen 2", "Adreno 750", "12/16GB", "Xanite Ultra", true, 3, R.drawable.ic_handheld_widescreen, "AYANEO", "HANDHELD"),
        "ayaneo_pocket_evo" to HardwareProfile("ayaneo_pocket_evo", "Ayaneo Pocket EVO", "Snapdragon G3x Gen 2", "Adreno 750", "12/16GB", "Xanite Ultra", true, 3, R.drawable.ic_handheld_widescreen, "AYANEO", "HANDHELD"),
        "ayaneo_pocket_micro" to HardwareProfile("ayaneo_pocket_micro", "Ayaneo Pocket Micro", "Helio G99", "Mali-G57", "6/8GB", "Hasta PS2 Básicos", true, 3, R.drawable.ic_handheld_widescreen, "AYANEO", "HANDHELD"),
        
        // --- POWKIDDY ---
        "powkiddy_x28" to HardwareProfile("powkiddy_x28", "Powkiddy X28", "Unisoc T618", "Mali-G52", "4GB", "PS2 Básico", false, 0, R.drawable.ic_handheld_widescreen, "POWKIDDY", "HANDHELD"),
        
        // --- GAMEMT ---
        "gamemt_e5_ultra" to HardwareProfile("gamemt_e5_ultra", "GameMT E5 Ultra", "Unisoc T620", "Mali-G57", "6GB", "PS2 / GC Medio", true, 3, R.drawable.ic_gamemt_e5_ultra, "GAMEMT", "HANDHELD"),
        "gamemt_ex8" to HardwareProfile("gamemt_ex8", "GameMT EX8", "Helio G99", "Mali-G57", "6/8GB", "PS2 / GC Pro", true, 3, R.drawable.ic_handheld_widescreen, "GAMEMT", "HANDHELD"),
        "gamemt_psk5000" to HardwareProfile("gamemt_psk5000", "GameMT PSK5000", "Helio G85", "Mali-G52", "4GB", "PS1 / N64 / DC", false, 0, R.drawable.ic_handheld_widescreen, "GAMEMT", "HANDHELD"),
        "gamemt_e6" to HardwareProfile("gamemt_e6", "GameMT E6", "Unisoc T616", "Mali-G57", "4GB", "Hasta PS2 Básicos", true, 3, R.drawable.ic_gamemt_e5_ultra, "GAMEMT", "HANDHELD"),

        // --- KT POCKET / MANGMI ---
        "kt_r1" to HardwareProfile("kt_r1", "KT-R1", "Helio G99", "Mali-G57", "4/6/8GB", "PS2 / GC Medio", false, 0, R.drawable.ic_handheld_widescreen, "KTPOCKET", "HANDHELD"),
        "mangmi_pocket_max" to HardwareProfile("mangmi_pocket_max", "Mangmi Pocket Max", "Snapdragon 8 Gen 2", "Adreno 740", "12GB", "Xanite Ultra", true, 3, R.drawable.ic_handheld_widescreen, "MANGMI", "HANDHELD"),
        
        // --- LOGITECH / RAZER ---
        "logitech_g_cloud" to HardwareProfile("logitech_g_cloud", "Logitech G Cloud", "Snapdragon 720G", "Adreno 618", "4GB", "PS2 / GC Básico", false, 0, R.drawable.ic_handheld_widescreen, "LOGITECH", "HANDHELD"),
        "razer_edge" to HardwareProfile("razer_edge", "Razer Edge", "Snapdragon G3x Gen 1", "Adreno 730", "6/8GB", "PS2 / GC Extremo", true, 3, R.drawable.ic_handheld_widescreen, "RAZER", "HANDHELD"),

        // --- SMARTPHONES SAMSUNG ---
        "samsung_s24_ultra" to HardwareProfile("samsung_s24_ultra", "Galaxy S24 Ultra", "Snapdragon 8 Gen 3", "Adreno 750", "12GB", "Xanite Ultra (Switch Full)", false, 0, R.drawable.ic_handheld_vertical, "SAMSUNG", "SMARTPHONE"),
        "samsung_z_fold_6" to HardwareProfile("samsung_z_fold_6", "Galaxy Z Fold 6", "Snapdragon 8 Gen 3", "Adreno 750", "12GB", "Xanite Ultra (Switch Full)", false, 0, R.drawable.ic_handheld_widescreen, "SAMSUNG", "SMARTPHONE"),
        "samsung_s23_ultra" to HardwareProfile("samsung_s23_ultra", "Galaxy S23 Ultra", "Snapdragon 8 Gen 2", "Adreno 740", "8/12GB", "Xanite Ultra (Switch Full)", false, 0, R.drawable.ic_handheld_vertical, "SAMSUNG", "SMARTPHONE"),
        "samsung_s22_ultra" to HardwareProfile("samsung_s22_ultra", "Galaxy S22 Ultra", "Snapdragon 8 Gen 1 / Exynos 2200", "Adreno 730", "8/12GB", "Switch / PS2 Extremo", false, 0, R.drawable.ic_handheld_vertical, "SAMSUNG", "SMARTPHONE"),
        
        // --- SMARTPHONES POCO / XIAOMI ---
        "poco_f5_pro" to HardwareProfile("poco_f5_pro", "Poco F5 Pro", "Snapdragon 8+ Gen 1", "Adreno 730", "8/12GB", "Switch / PS2 Extremo", false, 0, R.drawable.ic_handheld_vertical, "POCO", "SMARTPHONE"),
        "poco_x6_pro" to HardwareProfile("poco_x6_pro", "Poco X6 Pro", "Dimensity 8300 Ultra", "Mali-G615", "8/12GB", "Switch / PS2 Extremo", false, 0, R.drawable.ic_handheld_vertical, "POCO", "SMARTPHONE"),
        "xiaomi_14" to HardwareProfile("xiaomi_14", "Xiaomi 14 / Pro / Ultra", "Snapdragon 8 Gen 3", "Adreno 750", "12/16GB", "Xanite Ultra (Switch Full)", false, 0, R.drawable.ic_handheld_vertical, "XIAOMI", "SMARTPHONE"),
        
        // --- SMARTPHONES ONEPLUS ---
        "oneplus_12" to HardwareProfile("oneplus_12", "OnePlus 12", "Snapdragon 8 Gen 3", "Adreno 750", "12/16GB", "Xanite Ultra (Switch Full)", false, 0, R.drawable.ic_handheld_vertical, "ONEPLUS", "SMARTPHONE"),

        // --- ANDROID TV / SOBREMESA / TV BOX CHINAS ---
        "nvidia_shield_tv_pro" to HardwareProfile("nvidia_shield_tv_pro", "Nvidia Shield TV Pro", "Tegra X1+", "Maxwell 256", "3GB", "GameCube / Wii Pro", true, 3, R.drawable.ic_generic_hardware, "NVIDIA", "DESKTOP", "SPECCY_OS"),
        "xiaomi_mi_box_s_2gen" to HardwareProfile("xiaomi_mi_box_s_2gen", "Xiaomi TV Box S (2nd Gen)", "Cortex-A55", "Mali-G31 MP2", "2GB", "PS1 / PSP Básico", false, 0, R.drawable.ic_generic_hardware, "XIAOMI", "DESKTOP"),
        "fire_tv_stick_4k_max" to HardwareProfile("fire_tv_stick_4k_max", "Fire TV Stick 4K Max", "Cortex-A78", "Mali-G52", "2GB", "PS1 / N64 / Dreamcast", false, 0, R.drawable.ic_generic_hardware, "AMAZON", "DESKTOP"),
        "chromecast_google_tv_4k" to HardwareProfile("chromecast_google_tv_4k", "Chromecast Google TV 4K", "Amlogic S905X3", "Mali-G31 MP2", "2GB", "PS1 / N64 / DC Básicos", false, 0, R.drawable.ic_generic_hardware, "GOOGLE", "DESKTOP"),
        "x96_max_plus" to HardwareProfile("x96_max_plus", "X96 Max Plus", "Amlogic S905X3", "Mali-G31 MP2", "4GB", "PSP / DC Básicos", false, 0, R.drawable.ic_generic_hardware, "TVBOX_GENERIC", "DESKTOP"),
        "h96_max" to HardwareProfile("h96_max", "H96 Max", "Rockchip RK3318", "Mali-450", "4GB", "PS1 / N64 Básico", false, 0, R.drawable.ic_generic_hardware, "TVBOX_GENERIC", "DESKTOP"),
        "kinhank_super_console_x" to HardwareProfile("kinhank_super_console_x", "Super Console X (Android)", "Amlogic S905X", "Mali-450", "2GB", "PS1 Básico", false, 0, R.drawable.ic_generic_hardware, "KINHANK", "DESKTOP"),
        "tx3_mini" to HardwareProfile("tx3_mini", "Tanix TX3 Mini", "Amlogic S905W", "Mali-450", "2GB", "Hasta PS1", false, 0, R.drawable.ic_generic_hardware, "TANIX", "DESKTOP")
    )

    fun initialize(profileId: String, application: Application) {
        currentProfileId = profileId
        applicationContext = application.applicationContext
        isInitialized = true

        shizukuServiceArgs = Shizuku.UserServiceArgs(ComponentName(application.packageName, PerformanceService::class.java.name))
            .daemon(false)
            .processNameSuffix("performance_provider")
            .debuggable(BuildConfig.DEBUG)

        // Intentamos detectar PServer nativo (Backdoor de fábrica)
        pServerBinder = runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            val rawBinder = getService.invoke(serviceManager, "PServerBinder") as IBinder
            isPServerAvailable = true
            Log.i(TAG, "PServerBinder nativo detectado.")
            rawBinder
        }.getOrDefault(null)

        // Forzar inicialización de Shizuku
        bindShizukuService()

        loadProfileConfig(profileId)

        // Motor nuevo: sondea el hardware real, guarda el estado original para poder
        // restaurarlo y arranca la vigilancia termica con histeresis.
        SpeccyPerformanceTuner.init(applicationContext, profileId)
        val recommended = SpeccyPerformanceTuner.applyRecommended()
        Log.i(TAG, "Perfil recomendado: ${recommended.effectiveMode} via ${recommended.backend} " +
            "(${recommended.applied.size} ajustes aplicados, ${recommended.skipped.size} no disponibles)")

        // Nada de reaplicar BALANCED aqui: el tuner acaba de poner el modo
        // recomendado del dispositivo y machacarlo era justo el bug de arriba.
    }

    fun bindShizukuService() {
        try {
            if (!isShizukuAvailable()) {
                // Distinguir "no hay Shizuku" de "hay Shizuku pero sin permiso":
                // en el segundo caso se pide solo. Antes el permiso unicamente se
                // pedia desde un boton escondido en Ajustes > Hardware, asi que
                // quien instalaba Shizuku seguia sin motor de rendimiento y sin
                // saber por que (verificado en una GameMT E5 Ultra el 24-sep-2026).
                val vivo = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
                if (vivo && !permisoShizukuPedido) {
                    permisoShizukuPedido = true
                    Log.i(TAG, "Shizuku está corriendo pero sin permiso: solicitándolo.")
                    runCatching { Shizuku.requestPermission(SOLICITUD_SHIZUKU) }
                        .onFailure { Log.w(TAG, "No se pudo solicitar el permiso de Shizuku", it) }
                } else {
                    Log.w(TAG, "Intentando vincular Shizuku pero la API no está disponible o no hay permisos.")
                }
                return
            }
            
            // Si ya estamos vinculados, forzamos la recarga de UI
            if (performanceService != null) {
                Log.i(TAG, "Shizuku ya estaba vinculado. Refrescando UI.")
                onShizukuConnected?.invoke()
                return
            }

            shizukuServiceArgs?.let {
                Log.i(TAG, "Solicitando vinculación Shizuku UserService...")
                Shizuku.bindUserService(it, shizukuConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico vinculando SpeccyPerformanceService", e)
        }
    }

    private fun loadProfileConfig(profileId: String) {
        val fileName = when (profileId) {
            "gamemt_e5_ultra", "gamemt_e6" -> "profiles/t620.json"
            "ayaneo_pocket_s", "odin_2", "mangmi_pocket_max" -> "profiles/odin_2.json"
            "anbernic_rg556", "anbernic_rg557", "anbernic_rg_vita", "anbernic_rg406v" -> "profiles/anbernic_rg556.json"
            "retroid_pocket_4_pro", "retroid_pocket_5", "retroid_pocket_mini" -> "profiles/retroid_pocket_4.json"
            "gamemt_ex8", "ayaneo_pocket_micro", "kt_r1" -> "profiles/mobile_mid.json"
            "gamemt_psk5000" -> "profiles/anbernic_rg353.json"
            "nvidia_shield_tv_pro", "nvidia_shield_tube" -> "profiles/mobile_mid.json" // nvidia_shield.json no existe en assets
            else -> {
                when {
                    profileId.contains("samsung") || profileId.contains("mangmi") || profileId.contains("razer") -> "profiles/mobile_high.json"
                    profileId.contains("anbernic") || profileId.contains("retroid") || profileId.contains("gamemt_ex8") -> "profiles/mobile_mid.json"
                    else -> "profiles/mobile_low.json"
                }
            }
        }

        try {
            val inputStream: InputStream = applicationContext.assets.open(fileName)
            val buffer = ByteArray(inputStream.available())
            inputStream.read(buffer)
            inputStream.close()
            currentLoadedProfileJson = JSONObject(String(buffer, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando ADN de hardware: $fileName", e)
        }
    }

    fun applyPerformanceProfile(mode: String) {
        applyHardwareMode(mode)
    }

    /**
     * "echo '<valor>' > '<ruta>'" solo si ruta y valor pasan la lista blanca.
     *
     * No comprueba que el nodo exista, a diferencia de SpeccySysfsProbe.writeCmd:
     * hay nodos de /sys que nuestro UID no puede ver y el servicio privilegiado
     * si, y descartarlos aqui nos dejaria sin control de hardware en esos equipos.
     */
    private fun escrituraSegura(path: String, value: String): String? {
        if (path.isBlank() || !SpeccySysfsProbe.isSafeWrite(path, value)) {
            Log.w(TAG, "Escritura rechazada por validacion: $path=$value")
            return null
        }
        return "echo '$value' > '$path'"
    }

    /**
     * Perfil de potencia. Punto unico de entrada del resto de la app.
     *
     * ANTES: construia los comandos desde el JSON del perfil (`sysfs_mapping`),
     * con rutas FIJAS que en la mayoria de equipos no existen —en una GameMT E5
     * Ultra el `gpu_path` apuntaba a `/sys/class/kgsl/...`, que es de Adreno, y
     * el `fan_path` a un nodo inexistente— y encima se llamaba DESPUES de
     * `SpeccyPerformanceTuner.applyRecommended()`, asi que machacaba con
     * BALANCED lo que el tuner acababa de aplicar. En el log se veia la pelea:
     * `powersave` y acto seguido `schedutil` sobre el mismo clúster en cada
     * lanzamiento (visto el 24-sep-2026 en la E5 Ultra).
     *
     * AHORA: manda el tuner, que sondea el hardware real. El JSON del perfil se
     * conserva solo como documentacion/descripcion en la interfaz.
     */
    fun applyHardwareMode(mode: String) {
        val r = SpeccyPerformanceTuner.applyMode(mode)
        Log.i(TAG, "Modo $mode via ${r.backend}: ${r.applied.size} ajustes, ${r.skipped.size} descartados")
    }

    fun forceVulkanRenderer(enabled: Boolean) {
        val commands = if (enabled) {
            listOf(
                "setprop debug.hwui.renderer vulkan",
                "setprop debug.vulkan.layers.none 1",
                "setprop debug.renderengine.backend vulkan",
                "setprop debug.egl.buffercount 2"
            )
        } else {
            listOf(
                "setprop debug.hwui.renderer opengl",
                "setprop debug.renderengine.backend skiagl"
            )
        }
        executeCommands(commands)
    }

    private fun executeCommands(commands: List<String>) {
        if (commands.isEmpty()) return

        // Mismo filtro que executePrivilegedFallback. Este camino "legacy" mandaba
        // los comandos sin comprobar nada a PerformanceService, que hace
        // Runtime.exec("sh","-c",...) con privilegios: eran dos niveles de
        // proteccion distintos para el mismo backend.
        val commands = commands.filter { SpeccySysfsProbe.isSafeCommand(it) }
        if (commands.isEmpty()) {
            Log.w(TAG, "executeCommands: todos los comandos rechazados por validacion.")
            return
        }

        serviceScope.launch {
            // 1. Prioridad: Nuestro servicio privilegiado vía Shizuku (Más estable en Android moderno)
            val sService = performanceService
            if (sService != null) {
                try {
                    sService.executeCommands(commands)
                    Log.d(TAG, "⚡ Comandos inyectados vía Shizuku PerformanceService: ${commands.firstOrNull()}")
                    return@launch
                } catch (e: Exception) { Log.e(TAG, "Fallo en PerformanceService Shizuku", e) }
            } else if (isShizukuAvailable()) {
                // Auto-reconectar si se ha perdido
                bindShizukuService()
            }

            // 2. Segunda opción: PServer Nativo (Fabricante)
            if (isPServerAvailable && pServerBinder != null) {
                try {
                    val scriptDir = File(applicationContext.filesDir, "root-scripts")
                    if (!scriptDir.exists()) scriptDir.mkdirs()
                    val scriptFile = File(scriptDir, "apply-perf.sh")
                    scriptFile.writeText("#!/system/bin/sh\n" + commands.joinToString("\n"))
                    
                    val data = Parcel.obtain()
                    data.writeStringArray(arrayOf("sh ${scriptFile.absolutePath}", "1"))
                    pServerBinder?.transact(0, data, null, 0)
                    data.recycle()
                    Log.d(TAG, "⚡ Comandos inyectados vía PServer nativo")
                    return@launch
                } catch (e: Exception) { Log.e(TAG, "Fallo en PServer nativo", e) }
            }

            // 3. Fallback: Shell directo (Lento pero seguro)
            val hasRoot = Shell.isAppGrantedRoot() == true
            if (hasRoot) {
                Shell.cmd(*commands.toTypedArray()).submit()
            } else {
                // Shell.sh() esta deprecado y es el MISMO shell principal que cmd():
                // nunca ejecuto nada "via Shizuku", como daba a entender el comentario.
                commands.forEach { Shell.cmd(it).submit() }
            }
        }
    }

    /**
     * Fallback privilegiado expuesto para SpeccyPerformanceTuner: intenta el
     * binder PServer del fabricante y, si no, un shell normal. Los comandos ya
     * vienen validados por SpeccySysfsProbe (lista blanca de rutas y valores);
     * aquí se vuelve a comprobar para que ningún llamante futuro pueda inyectar.
     */
    fun executePrivilegedFallback(commands: List<String>) {
        val safe = commands.filter { SpeccySysfsProbe.isSafeCommand(it) }
        if (safe.isEmpty()) {
            Log.w(TAG, "executePrivilegedFallback: todos los comandos rechazados por validación.")
            return
        }
        if (isPServerAvailable && pServerBinder != null) {
            try {
                val scriptDir = File(applicationContext.filesDir, "root-scripts")
                if (!scriptDir.exists()) scriptDir.mkdirs()
                val scriptFile = File(scriptDir, "apply-perf.sh")
                scriptFile.writeText("#!/system/bin/sh\n" + safe.joinToString("\n"))
                // Sólo el propio proceso puede leer/ejecutar el script: evita que otra
                // app con el mismo sharedUserId lo reescriba antes de ejecutarse.
                scriptFile.setReadable(false, false)
                scriptFile.setReadable(true, true)
                scriptFile.setWritable(false, false)
                scriptFile.setWritable(true, true)
                scriptFile.setExecutable(true, true)

                val data = Parcel.obtain()
                data.writeStringArray(arrayOf("sh ${scriptFile.absolutePath}", "1"))
                pServerBinder?.transact(0, data, null, 0)
                data.recycle()
                return
            } catch (e: Exception) { Log.e(TAG, "Fallo en PServer nativo", e) }
        }
        // Shell.sh() esta deprecado en libsu 5.x y NO ejecuta via Shizuku: es el mismo
        // shell principal. Usamos cmd() y que el llamante informe si no hay privilegios.
        safe.forEach { Shell.cmd(it).submit() }
    }

    var currentProfileId: String = "generic"
        private set

    fun getProfile(id: String): HardwareProfile = hardwareProfiles[id] ?: hardwareProfiles["generic"]!!

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }

    fun getRootStatus(): String {
        val hasRoot = Shell.isAppGrantedRoot() == true
        val hasShizuku = isShizukuAvailable()
        
        return when {
            performanceService != null -> "SHIZUKU" // Validamos que el SERVICIO está realmente conectado
            hasRoot -> "SU"
            isPServerAvailable -> "PSERVER"
            hasShizuku -> "SHIZUKU_STANDBY" // Tiene permiso pero el servicio no enganchó (o está enganchando)
            else -> "NATIVE"
        }
    }

    /**
     * CORREGIDO: la versión anterior hacía `hardwareProfiles["anbernic_rg557"]!!`,
     * `["anbernic_rg_vita"]!!` y `["chromecast_4k"]!!` — tres claves que NO existen
     * en el mapa. En un RG557, una RG Vita o un Chromecast la app crasheaba con
     * NullPointerException nada más arrancar.
     *
     * Ahora se delega en SpeccyHardwareRegistry (catálogo único, ~75 modelos,
     * detección por MODEL/DEVICE/BOARD/PRODUCT con la coincidencia más larga) y
     * jamás se usa `!!`.
     */
    fun detectActualHardware(): HardwareProfile {
        val detected = SpeccyHardwareRegistry.detect()
        // Si detect() cayo en la inferencia por SoC, su id sigue siendo "generic" pero
        // nombre/marca/chipset SI son reales: no lo machacamos con el perfil legacy.
        if (detected.id != "generic") hardwareProfiles[detected.id]?.let { return it }

        // Compatibilidad con los IDs antiguos del mapa legacy.
        val legacyId = LEGACY_ID_MAP[detected.id]
        legacyId?.let { id -> hardwareProfiles[id]?.let { return it } }

        // Sin equivalencia legacy: sintetizamos el perfil desde el catálogo nuevo.
        return HardwareProfile(
            id = detected.id,
            name = detected.name,
            chipset = detected.soc,
            gpu = detected.gpu,
            ram = detected.ram,
            emulationCapacity = detected.tier.label,
            hasActiveCooling = detected.hasFan,
            maxFanLevel = detected.maxFanLevel,
            iconRes = detected.iconRes,
            category = detected.brand.uppercase(),
            type = detected.form.name
        )
    }

    /** Puente entre los IDs del catálogo nuevo y las claves del mapa legacy. */
    private val LEGACY_ID_MAP = mapOf(
        "ayn_odin2" to "odin_2",
        "ayn_odin2_mini" to "odin_2_mini",
        "ayn_odin_pro" to "odin_pro",
        "ayn_odin_lite" to "odin_lite",
        "retroid_rp5" to "retroid_pocket_5",
        "retroid_rp_mini" to "retroid_pocket_mini",
        "retroid_rp4pro" to "retroid_pocket_4_pro",
        "retroid_rp4" to "retroid_pocket_4",
        "retroid_rp3p" to "retroid_pocket_3_plus",
        "retroid_rp2s" to "retroid_pocket_2s",
        "anbernic_rg406" to "anbernic_rg406v",
        "anbernic_rg405" to "anbernic_rg405m",
        "ayaneo_pocket_s" to "ayaneo_pocket_s",
        "ayaneo_pocket_evo" to "ayaneo_pocket_evo",
        "ayaneo_pocket_micro" to "ayaneo_pocket_micro",
        "nvidia_shield_pro" to "nvidia_shield_tv_pro",
        "chromecast_gtv_4k" to "chromecast_google_tv_4k",
        "fire_tv_4k_max" to "fire_tv_stick_4k_max",
        "xiaomi_tvbox_s2" to "xiaomi_mi_box_s_2gen",
        "razer_edge" to "razer_edge",
        "logitech_g_cloud" to "logitech_g_cloud"
    )

    fun getCpuTemperature(): Float? {
        val sService = performanceService
        if (sService != null) {
            try {
                val tempStr = sService.readSysfs("/sys/class/thermal/thermal_zone0/temp")
                if (tempStr.isNotEmpty()) {
                    val temp = tempStr.toFloat()
                    return if (temp > 1000) temp / 1000f else temp
                }
            } catch (e: Exception) {}
        }
        
        // Fallback standard
        val paths = listOf("/sys/class/thermal/thermal_zone0/temp", "/sys/class/thermal/thermal_zone1/temp")
        for (path in paths) {
            try {
                val temp = File(path).readText().trim().toFloat()
                return if (temp > 1000) temp / 1000f else temp
            } catch (e: Exception) {}
        }
        return null
    }

    fun getRamUsage(): String {
        return try {
            val actManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalMem = memInfo.totalMem / (1024 * 1024 * 1024f)
            val availMem = memInfo.availMem / (1024 * 1024 * 1024f)
            val usedMem = totalMem - availMem
            String.format(Locale.US, "%.1f GB / %.1f GB", usedMem, totalMem)
        } catch (e: Exception) { "N/A" }
    }

    /**
     * Velocidad del ventilador, en la escala del catalogo (0..maxFanLevel).
     *
     * Antes se construia "echo N > <fan_path del JSON>": ruta fija que no existe
     * en casi ningun dispositivo real Y sin las comillas que exige la validacion,
     * asi que el comando se rechazaba SIEMPRE. Como el ViewModel llama a esto
     * cada 2 segundos, el log se llenaba de "todos los comandos rechazados por
     * validacion" y el ventilador nunca se tocaba. Ahora manda el tuner, que usa
     * los nodos realmente sondeados y el rango de cada uno.
     */
    fun setFanSpeed(level: Int) = SpeccyPerformanceTuner.setFan(level)

    fun runSafetyCheck() {
        val temp = getCpuTemperature() ?: return
        if (temp > 85f) {
            applyHardwareMode("BALANCED")
            setFanSpeed(3)
        }
    }

    fun setPerformanceMode(isPerformance: Boolean) {
        if (isPerformance) applyHardwareMode("PERFORMANCE")
        else applyHardwareMode("BALANCED")
    }
}
