package com.generacionarcade.speccyos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * LauncherManager - Gestor de lanzamiento universal de emuladores.
 *
 * ── MEJORAS v2 ────────────────────────────────────────────────────────────────
 * 1. INTEGRACIÓN RetroArchPerformanceConfig: usa resolveCorePath() y buildFor()
 *    para obtener la ruta real del .so y generar el append_config por plataforma.
 *
 * 2. RUTAS DE CORE CORREGIDAS:
 *    - Antes: hardcoded "/data/data/$pkg/cores/…"  ← falla en Android 11+ (ENOENT)
 *    - Ahora:  ResolveCortePath() busca en nativeLibraryDir primero (ruta correcta)
 *
 * 3. EXTRA "APPENDCONFIG" ENVIADO A RETROARCH:
 *    - RetroArch ≥ 1.15 acepta APPENDCONFIG para sobreescribir cfg sin tocar el global.
 *    - Soluciona desincronización de video_driver / audio_driver entre sesiones.
 *
 * 4. CONFIGFILE:
 *    - SI se envia, apuntando al retroarch.cfg del PROPIO RetroArch, y solo si
 *      se puede verificar que existe. Sin el, RetroArch arranca sin cargar
 *      ninguna configuracion: sin overlay y sin atajos de teclado. Ver el
 *      comentario largo en launchRetroArch().
 *
 * 5. FALLBACK DE ACTIVIDAD MEJORADO:
 *    - Prueba RetroActivityFuture → RetroActivity → getLaunchIntentForPackage
 *    - Siempre lleva extras ROM/LIBRETRO/APPENDCONFIG en los tres intentos.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class LauncherManager(private val context: Context) {

    private val settingsManager = SettingsManager(context)
    private val prefs = context.getSharedPreferences("launcher_stats", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SpeccyLauncher"

        // Speccy Engine (com.retroarch.speccy) tiene prioridad absoluta
        private val RETROARCH_PACKAGES = listOf(
            "com.retroarch.speccy",
            "com.retroarch.aarch64",
            "com.retroarch",
            "com.retroarch.plus",
            "com.retroarch.ra32"
        )

        // ── Standalone emulators ────────────────────────────────────────
        private const val PKG_NETHERSX2        = "xyz.aethersx2.android"
        private const val PKG_AETHERSX2        = "com.tahlreth.aethersx2.android"
        private const val PKG_DOLPHIN          = "org.dolphinemu.dolphinemu"
        private const val PKG_CITRA            = "org.citra.citra_emu"
        private const val PKG_DRASTIC          = "com.dsemu.drastic"
        private const val PKG_XANITE           = "org.xanite.emu"
        private const val PKG_X1BOX            = "com.izzy2lost.x1box"   // X1 BOX — Original Xbox (xemu)
        private const val PKG_PPSSPP           = "org.ppsspp.ppsspp"
        private const val PKG_PPSSPP_GOLD      = "org.ppsspp.ppssppgold"
        private const val PKG_M64PLUS_FZ       = "org.mupen64plusae.v3.fxyz"
        private const val PKG_M64PLUS_FZ_PRO   = "org.mupen64plusae.v3.fxyz.pro"
        private const val PKG_YUZU             = "org.yuzu.yuzu_emu"
        private const val PKG_DUCKSTATION      = "com.github.stenzek.duckstation"
        private const val PKG_NES_EMU          = "com.explusalpha.NesEmu"
        private const val PKG_SNES9X_EX        = "com.explusalpha.Snes9xPlus"
        private const val PKG_GBA_EMU          = "com.explusalpha.GbaEmu"
        private const val PKG_MD_EMU           = "com.explusalpha.MdEmu"
        private const val PKG_PCE_EMU          = "com.explusalpha.PceEmu"
        private const val PKG_MSX_EMU          = "com.explusalpha.MsxEmu"
        private const val PKG_C64_EMU          = "com.explusalpha.C64Emu"
        private const val PKG_NEOGEO_EMU       = "com.explusalpha.NeoEmu"
        private const val PKG_2600_EMU         = "com.explusalpha.V2600Emu"
        private const val PKG_SUDACHI          = "org.sudachi.sudachi_emu"
        private const val PKG_SUDACHI_EA       = "org.sudachi.sudachi_emu.early_access"
        private const val PKG_CITRON           = "org.citron_emu.citronn"
        private const val PKG_RYUJINX         = "org.ryujinx.android"
        private const val PKG_LIME3DS          = "io.github.lime3ds.emulator"
        private const val PKG_AZAHAR           = "io.github.azahar_emu.azahar"
        private const val PKG_VITA3K           = "com.vita3k.emulator"
        private const val PKG_REDREAM          = "io.recompiled.redream"
        private const val PKG_FLYCAST          = "com.flycast.emulator"
        private const val PKG_WINLATOR         = "com.winlator"
        private const val PKG_WINLATOR_CMOD    = "com.winlator.cmod"
        private const val PKG_YABA_SANSHIRO    = "org.uoyabause.uoyabause"
        private const val PKG_SUPERMODEL       = "com.supermodel3.android"

        // ── AÑADIDOS EN LA AUDITORÍA DE AGOSTO 2026 ──────────────────────
        // Emuladores que la matriz de compatibilidad ya listaba (o que son hoy
        // los de referencia) pero para los que no existía ninguna rama de
        // lanzamiento: todos caían al genérico de RetroArch.
        private const val PKG_YABA_SANSHIRO2   = "org.uoyabause.android.uoyabause2"
        private const val PKG_MELONDS          = "me.magnum.melonds"
        private const val PKG_MAME4DROID_2024  = "com.seleuco.mame4d2024"
        private const val PKG_MAME4DROID       = "com.seleuco.mame4droid"
        private const val PKG_LEMUROID         = "com.swordfish.lemuroid"
        private const val PKG_PANDA3DS         = "com.panda3ds.pandroid"
        private const val PKG_EDEN             = "org.eden_emu.eden"
        private const val PKG_EDEN_ALT         = "dev.eden.eden_emulator"
        private const val PKG_CEMU             = "info.cemu.Cemu"
        private const val PKG_RPCS3            = "net.rpcs3"
        private const val PKG_MICEWINE         = "com.micewine.emulator"
        private const val PKG_MUPEN_FZ_REAL    = "org.mupen64plusae.v3.fzurita"

        private const val RETROARCH_STABLE_URL =
            "https://play.google.com/store/apps/details?id=com.retroarch.aarch64"

        // ── RetroArch Intent extras ─────────────────────────────────────
        // Documentados en RetroArch source: frontend/drivers/platform_android.c
        private const val EXTRA_ROM          = "ROM"
        private const val EXTRA_LIBRETRO     = "LIBRETRO"
        private const val EXTRA_APPENDCONFIG = "APPENDCONFIG"  // ← NUEVO: sobreescribe cfg sin romper el global
        private const val EXTRA_CONFIGFILE   = "CONFIGFILE"
        private const val EXTRA_STATE        = "STATE"
        private const val EXTRA_SUBSYSTEM    = "SUBSYSTEM"

        // Actividades de RetroArch (en orden de preferencia)
        private const val RA_ACTIVITY_FUTURE  = "com.retroarch.browser.retroactivity.RetroActivityFuture"
        private const val RA_ACTIVITY_CLASSIC = "com.retroarch.browser.retroactivity.RetroActivity"

        /**
         * TRUE mientras hay un emulador en primer plano lanzado desde aqui.
         *
         * MainActivity la consulta en ON_STOP: cuando el launcher pasa a segundo
         * plano PORQUE acaba de arrancar un juego, NO debe bajar el hardware a
         * "ECO". Antes lo hacia siempre, asi que RetroArch cogia el foco justo
         * cuando el CPU acababa de ser limitado -> tirones y ANR de entrada
         * ("Waited 5001ms for KeyEvent"). Se limpia al volver al launcher.
         */
        @Volatile
        @JvmStatic
        var gameSessionActive: Boolean = false
    }

    /** Scope propio para las comprobaciones de BIOS: nunca en el hilo principal. */
    private val biosScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Scope para todo lo que acompania a un lanzamiento pero NO es requisito de
     * el: perfilado de rendimiento, avisos, telemetria. Nada de lo que se lance
     * aqui debe poder retrasar el startActivity() del emulador.
     */
    private val prelaunchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────────────────
    // PUNTO DE ENTRADA PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────────

    fun launchGame(game: Game, saveStateFile: File? = null) {
        registerLaunch(game)
        // Desde aqui y hasta que el launcher vuelva a primer plano, el hardware
        // es del emulador: MainActivity no lo bajara a ECO en ON_STOP.
        gameSessionActive = true

        // Deshabilitar StrictMode para file:// URIs (necesario en algunos paths de ROM)
        // La versión anterior era `StrictMode.setVmPolicy(StrictMode.setVmPolicy(...).run{...})`:
        // setVmPolicy devuelve Unit, así que el .run{} descartaba todo y sólo
        // funcionaba por accidente. Se desactiva la detección de exposición de
        // file:// URIs porque los emuladores de terceros esperan rutas, no content://.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        val platformId = game.platformId.lowercase()
        val safePath   = resolveRealPath(Uri.parse(game.path))

        // FileProvider en Android 7+ para evitar FileUriExposedException.
        // Fallback a Uri.fromFile con StrictMode desactivado para rutas externas
        // que no pueden ser servidas por FileProvider (SD cards, paths de root).
        val romUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                File(safePath)
            )
        } catch (_: IllegalArgumentException) {
            // La ruta no está cubierta por provider_paths (ej: /storage/sdcard1/…)
            // StrictMode ya fue deshabilitado arriba; file:// es el único fallback viable.
            @Suppress("DEPRECATION")
            Uri.fromFile(File(safePath))
        }

        val emulatorTypeByPlatform = settingsManager.getPreferredEmulator(platformId)
        // OVERRIDE POR JUEGO (nuevo). Antes la granularidad era sólo por
        // plataforma: no se podía decir "este juego con este core y el resto con
        // otro", que es lo primero que pide un usuario avanzado.
        val overrides = SpeccyGameOverrides(context)
        val preferredPkg  = overrides.resolveEmulatorPackage(game, settingsManager)
        // Si el usuario ha fijado un emulador PARA ESTE JUEGO, manda sobre el ajuste
        // de la plataforma. Sin esto el override por juego era inalcanzable: se
        // guardaba pero el flujo seguia yendo por RetroArch.
        val emulatorType = if (overrides.getEmulatorPackage(game) != null)
            SettingsManager.EMULATOR_STANDALONE
        else emulatorTypeByPlatform

        // Perfil de rendimiento por SISTEMA: un NES no necesita el mismo techo que
        // un PS2. Sube frecuencias solo cuando hace falta y avisa si el equipo no da.
        //
        // FUERA DEL HILO PRINCIPAL. applyForPlatform() sondea y escribe en sysfs
        // (cpufreq, devfreq, thermal_zone*): son decenas de lecturas de ficheros
        // que bloqueaban el hilo de UI JUSTO antes de disparar el intent, de modo
        // que el emulador tardaba en recibir el foco. Ahora el intent sale primero
        // y el ajuste se aplica en paralelo en IO, que es como se comportaba en la
        // version que funcionaba.
        prelaunchScope.launch { runCatching { SpeccyPerformanceTuner.applyForPlatform(platformId) } }

        // AVISO DE BIOS. Antes el usuario pulsaba A, el emulador arrancaba, fallaba
        // con un error críptico y no había forma de saber que faltaba un fichero.
        if (SpeccyBiosManager.needsBios(platformId)) {
            biosScope.launch {
                val romsTree = runCatching {
                    settingsManager.romsLocation.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                }.getOrNull()
                val status = SpeccyBiosManager.check(context, platformId, romsTree)
                if (!status.isPlayable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "$platformId: ${status.summary}. Cópialas en la carpeta BIOS de tus ROMs.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        // El aviso de "tu maquina no da para esto" tambien se calcula en IO:
        // canRun()/activeDevice() leen el perfil del aparato desde disco y solo
        // sirven para pintar un Toast. No pueden retrasar el arranque del juego.
        prelaunchScope.launch {
            val puede = runCatching { SpeccyPerformanceTuner.canRun(platformId) }.getOrDefault(true)
            if (!puede) {
                val dev = runCatching { SpeccyPerformanceTuner.activeDevice() }.getOrNull()
                if (dev != null) withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Aviso: $platformId supera lo que ${dev.name} puede mover con soltura (${dev.tier.label}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        try {
            if (emulatorType == SettingsManager.EMULATOR_STANDALONE) {
                if (preferredPkg != null && isPackageInstalled(preferredPkg)) {
                    launchByPackage(preferredPkg, romUri, platformId)
                } else {
                    when (platformId) {
                        "ps2"                        -> launchNetherSX2(romUri)
                        "wii", "gamecube", "gc"      -> launchDolphin(romUri)
                        "3ds", "n3ds"                -> launchCitraNew(romUri, null)
                        "nds"                        -> launchNds(romUri)
                        "psp"                        -> launchPPSSPP(romUri)
                        "n64"                        -> launchN64(romUri)
                        "xbox"                       -> launchXbox(romUri)
                        "switch"                     -> launchSwitch(romUri, null)
                        "vita", "psvita"             -> launchVita3K(romUri)
                        "windows", "win", "exodos"   -> launchWinlator(romUri)
                        "psx", "ps1"                 -> launchGenericStandalone(PKG_DUCKSTATION, romUri)
                        // ── AÑADIDAS EN LA AUDITORÍA ──────────────────────
                        // Estos sistemas ya aparecían en la matriz de compatibilidad
                        // (Redream, Flycast, Yaba Sanshiro, Supermodel) pero no tenían
                        // rama aquí: todos caían al `else` de RetroArch, así que sólo
                        // funcionaban si el usuario fijaba el paquete a mano.
                        "dreamcast", "dc"            -> launchDreamcast(romUri)
                        "naomi", "naomi2", "naomigd",
                        "atomiswave"                 -> launchGenericStandalone(PKG_FLYCAST, romUri)
                        "saturn", "saturnjp", "stv"  -> launchSaturn(romUri)
                        "model2", "model3"           -> launchGenericStandalone(PKG_SUPERMODEL, romUri)
                        "arcade", "mame", "fbneo", "fba" -> launchArcade(romUri)
                        "wiiu"                       -> launchGenericStandalone(PKG_CEMU, romUri)
                        "ps3"                        -> launchGenericStandalone(PKG_RPCS3, romUri)
                        "gba"                        -> launchGenericStandalone(PKG_GBA_EMU, romUri)
                        "sfc", "snes", "snesna"      -> launchGenericStandalone(PKG_SNES9X_EX, romUri)
                        "genesis", "megadrive"       -> launchGenericStandalone(PKG_MD_EMU, romUri)
                        else                         -> launchRetroArch(safePath, platformId, saveStateFile, gameForCore = game)
                    }
                }
            } else {
                launchRetroArch(safePath, platformId, saveStateFile, gameForCore = game)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lanzamiento fallido para $platformId", e)
            gameSessionActive = false
            Toast.makeText(context, "Fallo al iniciar emulador: ${e.message}", Toast.LENGTH_SHORT).show()
            HardwareControlManagerBeta.applyHardwareMode("BALANCED")
        }
    }

    /**
     * Lanza un juego multijugador local en RetroArch en modo Host o Cliente.
     */
    fun launchGameWithNetplay(game: Game, isHost: Boolean, hostIp: String? = null) {
        registerLaunch(game)
        gameSessionActive = true

        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

        val platformId = game.platformId.lowercase()
        val safePath   = resolveRealPath(Uri.parse(game.path))
        val netplayArgs = if (isHost) "-H" else "--connect $hostIp"

        try {
            val installedPkg = RETROARCH_PACKAGES.find { isPackageInstalled(it) }
            if (installedPkg != null) {
                RetroArchSyncManager.onGameLaunched(platformId, installedPkg)
            }
            
            // Forzar perfil de overclock
            val baseProfile = settingsManager.manualProfile.ifEmpty { "BALANCED" }
            val perfMode = if (baseProfile == "ECO") "BALANCED" else baseProfile
            HardwareControlManagerBeta.applyHardwareMode(perfMode)

            launchRetroArch(safePath, platformId, saveStateFile = null, netplayArgs = netplayArgs, gameForCore = game)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en Netplay: $platformId", e)
            gameSessionActive = false
            Toast.makeText(context, "Error iniciando Netplay: ${e.message}", Toast.LENGTH_SHORT).show()
            HardwareControlManagerBeta.applyHardwareMode("BALANCED")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETROARCH — LANZAMIENTO MEJORADO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lanza RetroArch con configuración optimizada por plataforma.
     *
     * MEJORAS respecto a la versión anterior:
     * - resolveCorePath() → ruta nativa correcta en Android 11+
     * - RetroArchPerformanceConfig.buildFor() → genera append_config por plataforma
     * - APPENDCONFIG extra → RetroArch fusiona la config sin sobrescribir la global
     * - NUNCA se manda CONFIGFILE: sustituiria la configuracion propia de
     *   RetroArch (mandos y atajos incluidos) por la que le apuntemos
     * - Tres intentos de actividad con extras completos en cada uno
     */
    private fun launchRetroArch(
        safePath: String,
        platformId: String,
        saveStateFile: File? = null,
        netplayArgs: String? = null,
        gameForCore: Game? = null
    ) {
        val installedPkg = RETROARCH_PACKAGES.find { isPackageInstalled(it) }
        if (installedPkg == null) {
            gameSessionActive = false
            Toast.makeText(context, "Redirigiendo a Play Store para instalar RetroArch…", Toast.LENGTH_LONG).show()
            openStoreOrLink("com.retroarch.aarch64", RETROARCH_STABLE_URL)
            return
        }

        // Cascada juego -> resolutor -> detect.
        //
        // El override por juego sigue mandando sobre todo lo demas. Por debajo
        // ya no se coge a ciegas el `defaultCore` del systeminfo.txt: decide
        // SpeccyCoreResolver, que reordena los cores que ESA plataforma declara
        // (arcade primero mame2003_plus, no el MAME actual) y ademas aparta los
        // que ya se ha comprobado que no arrancan en esta consola.
        //
        // OJO: aqui se usa getCore(), NO resolveCore(). resolveCore() nunca
        // devuelve null -termina en `?: defaultCore ?: "detect"`-, asi que con
        // un Game delante se quedaba con el `mamearcade` del systeminfo.txt y
        // todo lo de abajo era codigo muerto. getCore() devuelve null cuando el
        // usuario no ha fijado nada para ESE juego, que es lo que hace falta
        // para que la cascada siga.
        val sysInfo = RetroArchDatabase.findSystemById(platformId)
        val coreId = gameForCore?.let { SpeccyGameOverrides(context).getCore(it) }
            ?: SpeccyCoreResolver.resolver(
                context = context,
                platformId = platformId,
                sys = sysInfo,
                preferidoDelUsuario = settingsManager.getPreferredCore(platformId)
            )

        // Cronometro para el aprendizaje: si el usuario vuelve enseguida, ese
        // core no arranco; si juega un rato, se asciende a preferido.
        SpeccyCoreResolver.anotarLanzamiento(context, platformId, coreId)

        // ── 1. Ruta del core (CORRECCIÓN PRINCIPAL) ──────────────────────
        // resolveCorePath() busca en nativeLibraryDir (correcto en Android 11+)
        // antes se usaba el path hardcoded /data/data/…/cores/… que falla.
        val corePath = RetroArchPerformanceConfig.resolveCorePath(context, installedPkg, coreId)
        Log.d(TAG, "Core resuelto para '$platformId': $corePath")

        // ── 2. Config global de RetroArch (ruta dinámica, no hardcoded) ──
        //
        // NO se llama a buildFor() aqui. buildFor() genera y ESCRIBE el .cfg
        // (y con un arbol SAF concedido pasa por DocumentFile/ContentResolver),
        // o sea I/O de cientos de ms en el hilo principal justo antes de
        // startActivity(): el emulador tardaba en recibir el foco y el sistema
        // acababa cantando "ANR in com.retroarch". Aqui se usa lo ya calculado
        // (o el .cfg que dejo el arranque anterior) y el fichero se regenera
        // despues, en IO, para el siguiente lanzamiento.
        val platformConfig = RetroArchPerformanceConfig.cachedFor(platformId)

        // ── CONFIGFILE: hay que mandarlo, y apuntando al cfg PROPIO de RetroArch
        //
        // Aqui habia un comentario diciendo que este extra no se manda NUNCA
        // porque "sustituye la configuracion del usuario". El matiz que faltaba
        // es que apuntandolo al retroarch.cfg DEL PROPIO RetroArch no sustituye
        // nada: le dice cual es el suyo. Sin este extra, RetroArch arranca SIN
        // CARGAR NINGUNA configuracion.
        //
        // Verificado en una GameMT EX8 el 11 de septiembre de 2026, prueba A/B
        // con el mismo core (Gambatte), el mismo juego y la misma pulsacion:
        //
        //   - sin CONFIGFILE -> "Config directory is not set" al arrancar, sin
        //     overlay, y el combo L3+R3 NO abre el Quick Menu.
        //   - con CONFIGFILE -> el combo L3+R3 abre el Quick Menu.
        //
        // Es ademas lo que hacen los `systeminfo.txt` de ES-DE, de donde salen
        // los comandos de lanzamiento de este proyecto:
        //   %EXTRA_CONFIGFILE%=/storage/emulated/0/Android/data/<pkg>/files/retroarch.cfg
        //
        // NO se puede comprobar que el fichero exista: desde Android 11 ninguna
        // app puede mirar dentro del Android/data de otra, asi que un
        // File(...).exists() sobre el de RetroArch da false SIEMPRE y el extra
        // no se enviaria nunca. Se manda tal cual, que es lo que hace ES-DE en
        // estas mismas consolas, y se deja un interruptor por si acaso.
        val retroArchConfigPath =
            if (!settingsManager.sendRetroArchConfigFile) null
            else File(
                Environment.getExternalStorageDirectory(),
                "Android/data/$installedPkg/files/retroarch.cfg"
            ).absolutePath
        prelaunchScope.launch {
            runCatching { RetroArchPerformanceConfig.buildFor(context, platformId, installedPkg) }
        }
        // CONFIGFILE NO SE ENVIA NUNCA. Es la razon de que RetroArch ignorara
        // sus propias teclas: ese extra le dice "carga ESTE fichero como tu
        // configuracion", asi que sustituye el retroarch.cfg del usuario entero
        // -mandos, atajos, todo- por el que le apuntemos. Y desde nuestro UID
        // no podemos ni leer el suyo de verdad (Android 11+), asi que como
        // mucho acertariamos por casualidad.
        //
        // Sin este extra, RetroArch carga su config de siempre y sus teclas
        // preestablecidas funcionan. APPENDCONFIG si se puede mandar: solo
        // FUSIONA las claves que trae (video/audio/menu), sin tocar el resto.

        // ── 3. Append-config por plataforma (NUEVA FUNCIONALIDAD) ─────────
        val appendConfigPath = platformConfig.appendConfigPath
        val hasAppendConfig = appendConfigPath.isNotBlank() &&
            File(appendConfigPath).exists() && File(appendConfigPath).length() > 0
        if (hasAppendConfig) {
            Log.d(TAG, "append_config escrito en: $appendConfigPath")
        }
        // ── 4. Construcción del Intent ────────────────────────────────────
        fun buildIntent(pkg: String, activityClass: String): Intent =
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(pkg, activityClass)
                
                // Si la ruta comienza por /storage/emulated/0, intentaremos pasar el extra como String nativo.
                // En RetroArch de Android moderno, pasar la ruta directa como EXTRA_ROM es preferible a una URI 
                // cuando se tiene acceso real al almacenamiento (Storage Access Framework).
                putExtra(EXTRA_ROM, safePath)
                
                if (corePath.isNotBlank()) putExtra(EXTRA_LIBRETRO, corePath)
                retroArchConfigPath?.let { putExtra(EXTRA_CONFIGFILE, it) }
                
                if (hasAppendConfig) putExtra(EXTRA_APPENDCONFIG, appendConfigPath)
                if (saveStateFile != null && saveStateFile.exists()) {
                    Log.d(TAG, "TIME JUMP: cargando estado desde ${saveStateFile.absolutePath}")
                    putExtra(EXTRA_STATE, saveStateFile.absolutePath)
                }

                if (!netplayArgs.isNullOrBlank()) {
                    putExtra("args", netplayArgs)
                    Log.d(TAG, "Lanzando RetroArch con args: $netplayArgs")
                }
                
                // RetroArch puede necesitar que se le limpie la cola de tareas
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        // ── 5. Triple-fallback de actividad ───────────────────────────────
        val launched = tryStartActivity(buildIntent(installedPkg, RA_ACTIVITY_FUTURE))
            || tryStartActivity(buildIntent(installedPkg, RA_ACTIVITY_CLASSIC))
            || run {
                context.packageManager.getLaunchIntentForPackage(installedPkg)
                    ?.apply {
                        putExtra(EXTRA_ROM, safePath)
                        if (corePath.isNotBlank()) putExtra(EXTRA_LIBRETRO, corePath)
                retroArchConfigPath?.let { putExtra(EXTRA_CONFIGFILE, it) }
                        if (hasAppendConfig) putExtra(EXTRA_APPENDCONFIG, appendConfigPath)
                        if (saveStateFile != null && saveStateFile.exists())
                            putExtra(EXTRA_STATE, saveStateFile.absolutePath)
                        if (!netplayArgs.isNullOrBlank()) {
                            putExtra("args", netplayArgs)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    ?.let { tryStartActivity(it) }
                    ?: false
            }

        if (launched) {
            RetroArchSyncManager.onGameLaunched(platformId, installedPkg)
        } else {
            Log.e(TAG, "No se pudo lanzar RetroArch ($installedPkg) con ninguna actividad")
            Toast.makeText(context, "Error: No se pudo abrir RetroArch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Actividad no disponible: ${intent.component?.className} — ${e.message}")
        false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILIDAD — EMULADORES INSTALADOS
    // ─────────────────────────────────────────────────────────────────────────

    fun getCompatibleInstalledEmulators(platformId: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        when (platformId.lowercase()) {
            "ps2"                    -> { addIf(list, PKG_NETHERSX2, "NetherSX2"); addIf(list, PKG_AETHERSX2, "AetherSX2") }
            "wii", "gamecube", "gc"  -> addIf(list, PKG_DOLPHIN, "Dolphin")
            "3ds", "n3ds"            -> { addIf(list, PKG_LIME3DS, "Lime3DS"); addIf(list, PKG_AZAHAR, "Azahar"); addIf(list, PKG_CITRA, "Citra") }
            "nds"                    -> addIf(list, PKG_DRASTIC, "DraStic")
            "psp"                    -> { addIf(list, PKG_PPSSPP, "PPSSPP"); addIf(list, PKG_PPSSPP_GOLD, "PPSSPP Gold") }
            "n64"                    -> { addIf(list, PKG_M64PLUS_FZ, "M64Plus FZ"); addIf(list, PKG_M64PLUS_FZ_PRO, "M64Plus FZ Pro") }
            "switch"                 -> { addIf(list, PKG_SUDACHI, "Sudachi"); addIf(list, PKG_SUDACHI_EA, "Sudachi EA"); addIf(list, PKG_CITRON, "Citron"); addIf(list, PKG_RYUJINX, "Ryujinx"); addIf(list, PKG_YUZU, "yuzu") }
            "vita", "psvita"         -> addIf(list, PKG_VITA3K, "Vita3K")
            "psx", "ps1"             -> addIf(list, PKG_DUCKSTATION, "DuckStation")
            "gba"                    -> addIf(list, PKG_GBA_EMU, "GBA.emu")
            "sfc", "snes", "snesna"  -> addIf(list, PKG_SNES9X_EX, "Snes9x EX+")
            "genesis", "megadrive"   -> addIf(list, PKG_MD_EMU, "MD.emu")
            "nes"                    -> addIf(list, PKG_NES_EMU, "NES.emu")
            "dc", "dreamcast"        -> { addIf(list, PKG_REDREAM, "Redream"); addIf(list, PKG_FLYCAST, "Flycast") }
            "saturn"                 -> addIf(list, PKG_YABA_SANSHIRO, "Yaba Sanshiro")
            "model3"                 -> addIf(list, PKG_SUPERMODEL, "Supermodel 3")
            "windows", "win", "exodos" -> { addIf(list, PKG_WINLATOR, "Winlator"); addIf(list, PKG_WINLATOR_CMOD, "Winlator CMod") }
            "xbox"                     -> { addIf(list, PKG_X1BOX, "X1 BOX"); addIf(list, PKG_XANITE, "Xanite") }
        }
        return list
    }

    private fun addIf(list: MutableList<Pair<String, String>>, pkg: String, label: String) {
        if (isPackageInstalled(pkg)) list.add(pkg to label)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LANZADORES STANDALONE
    // ─────────────────────────────────────────────────────────────────────────

    private fun launchByPackage(pkg: String, romUri: Uri, platformId: String) {
        when {
            pkg.contains("aethersx2") || pkg.contains("nethersx2") -> launchNetherSX2(romUri, pkg)
            pkg.contains("dolphin")       -> launchDolphin(romUri, pkg)
            pkg.contains("mupen64plusae") -> launchN64(romUri, pkg)
            pkg.contains("drastic")       -> launchDraStic(romUri, pkg)
            pkg.contains("citra")         -> launchCitra(romUri, pkg)
            pkg.contains("ppsspp")        -> launchPPSSPP(romUri, pkg)
            pkg.contains("yuzu") || pkg.contains("sudachi") -> launchSwitch(romUri, pkg)
            pkg.contains("xanite")        -> launchXanite(romUri, pkg)
            pkg.contains("redream") || pkg.contains("flycast") -> launchDreamcast(romUri)
            pkg.contains("uoyabause")     -> launchSaturn(romUri)
            pkg.contains("mame4d")        -> launchArcade(romUri)
            pkg.contains("melonds")       -> launchNds(romUri)
            pkg.contains("eden")          -> launchSwitch(romUri, pkg)
            pkg.contains("x1box") || pkg.contains("izzy2lost") -> launchX1Box(romUri, pkg)
            else                          -> launchGenericStandalone(pkg, romUri)
        }
    }

    private fun launchNetherSX2(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName
            ?: if (isPackageInstalled(PKG_NETHERSX2)) PKG_NETHERSX2
            else if (isPackageInstalled(PKG_AETHERSX2)) PKG_AETHERSX2
            else null
        if (pkg == null) { openStoreOrLink(PKG_NETHERSX2); return }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // Antes se forzaba la clase de AetherSX2 incluso cuando el paquete era
            // NetherSX2 (com.tahlreth...), con lo que el componente no existía.
            component = ComponentName(pkg, "$pkg.EmulationActivity")
            setDataAndType(romUri, "application/x-iso9660-image")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(intent)) launchStandalone(pkg, "", romUri)
    }

    private fun launchDolphin(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName ?: PKG_DOLPHIN
        if (!isPackageInstalled(pkg)) { openStoreOrLink(pkg); return }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // La clase real de Dolphin es `.activities.EmulationActivity`, no
            // `.ui.main.EmulationActivity` (que no existe): antes esta rama fallaba
            // siempre y caía al fallback genérico, perdiendo el arranque directo.
            component = ComponentName(pkg, "org.dolphinemu.dolphinemu.activities.EmulationActivity")
            data = romUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(intent)) launchStandalone(pkg, "", romUri)
    }

    private fun launchN64(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName
            ?: listOf(PKG_M64PLUS_FZ_PRO, PKG_MUPEN_FZ_REAL, PKG_M64PLUS_FZ)
                .firstOrNull { isPackageInstalled(it) } ?: PKG_M64PLUS_FZ
        if (!isPackageInstalled(pkg)) { openStoreOrLink(PKG_M64PLUS_FZ); return }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // El id de paquete de Mupen64Plus FZ es `...v3.fzurita` y su entrada
            // real está en `paulscode.android.mupen64plusae`, no en una MainActivity
            // del propio id: la rama anterior nunca resolvía.
            component = ComponentName(pkg, "paulscode.android.mupen64plusae.SplashActivity")
            data = romUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(intent)) launchStandalone(pkg, "", romUri)
    }

    private fun launchSwitch(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName ?: listOf(PKG_EDEN, PKG_EDEN_ALT, PKG_SUDACHI, PKG_SUDACHI_EA, PKG_CITRON, PKG_RYUJINX, PKG_YUZU)
            .firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { openStoreOrLink(PKG_SUDACHI); return }
        launchStandalone(pkg, "", romUri)
    }

    private fun launchCitraNew(romUri: Uri, pkgName: String?) {
        val pkg = pkgName ?: listOf(PKG_AZAHAR, PKG_LIME3DS, PKG_PANDA3DS, PKG_CITRA)
            .firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { openStoreOrLink(PKG_LIME3DS); return }
        launchStandalone(pkg, "", romUri)
    }

    /**
     * Dreamcast: Redream es el más rápido en gama media, Flycast el más compatible.
     * Antes no había ninguna rama y todo iba por RetroArch.
     */
    private fun launchDreamcast(romUri: Uri) {
        val pkg = listOf(PKG_REDREAM, PKG_FLYCAST).firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { openStoreOrLink(PKG_REDREAM); return }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(pkg)
            data = romUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(intent)) launchStandalone(pkg, "", romUri)
    }

    /** Saturn / ST-V. El id legacy `org.uoyabause.uoyabause` ya no se publica. */
    private fun launchSaturn(romUri: Uri) {
        val pkg = listOf(PKG_YABA_SANSHIRO2, PKG_YABA_SANSHIRO)
            .firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { openStoreOrLink(PKG_YABA_SANSHIRO2); return }
        launchStandalone(pkg, "", romUri)
    }

    /** Arcade: MAME4droid 2024 si está, si no el clásico, si no RetroArch. */
    private fun launchArcade(romUri: Uri) {
        val pkg = listOf(PKG_MAME4DROID_2024, PKG_MAME4DROID, PKG_LEMUROID)
            .firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { openStoreOrLink(PKG_MAME4DROID_2024); return }
        launchStandalone(pkg, "", romUri)
    }

    /** NDS: melonDS es hoy el más preciso; DraStic sigue siendo el más rápido. */
    private fun launchNds(romUri: Uri) {
        val pkg = listOf(PKG_MELONDS, PKG_DRASTIC).firstOrNull { isPackageInstalled(it) }
        if (pkg == null) { launchDraStic(romUri); return }
        launchStandalone(pkg, "", romUri)
    }

    private fun launchWinlator(romUri: Uri) {
        val pkg = listOf(PKG_WINLATOR_CMOD, PKG_WINLATOR, PKG_MICEWINE)
            .firstOrNull { isPackageInstalled(it) } ?: PKG_WINLATOR
        launchStandalone(pkg, "", romUri)
    }

    private fun launchVita3K(romUri: Uri) {
        if (!isPackageInstalled(PKG_VITA3K)) { openStoreOrLink(PKG_VITA3K); return }
        launchStandalone(PKG_VITA3K, "org.vita3k.emulator.Vita3KEmu", romUri)
    }

    private fun launchPPSSPP(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName ?: if (isPackageInstalled(PKG_PPSSPP_GOLD)) PKG_PPSSPP_GOLD else PKG_PPSSPP
        launchStandalone(pkg, "", romUri)
    }

    private fun launchDraStic(romUri: Uri, pkgName: String? = null) {
        launchStandalone(pkgName ?: PKG_DRASTIC, "com.dsemu.drastic.DraSticActivity", romUri)
    }

    private fun launchCitra(romUri: Uri, pkgName: String? = null) =
        launchStandalone(pkgName ?: PKG_CITRA, "", romUri)

    private fun launchXanite(romUri: Uri, pkgName: String? = null) =
        launchStandalone(pkgName ?: PKG_XANITE, "", romUri)

    /**
     * Lanzador genérico para Xbox original.
     * Prioridad: X1 BOX (xemu-based, más compatible) → Xanite → tienda
     */
    private fun launchXbox(romUri: Uri) {
        when {
            isPackageInstalled(PKG_X1BOX)  -> launchX1Box(romUri)
            isPackageInstalled(PKG_XANITE) -> launchXanite(romUri)
            else -> {
                // X1 BOX disponible en Play Store y GitHub Releases
                Toast.makeText(
                    context,
                    "Instala X1 BOX para emular Xbox original",
                    Toast.LENGTH_LONG
                ).show()
                openStoreOrLink(
                    PKG_X1BOX,
                    "https://play.google.com/store/apps/details?id=$PKG_X1BOX"
                )
            }
        }
    }

    /**
     * Lanza X1 BOX (emulador Xbox original basado en xemu).
     *
     * X1 BOX acepta un Intent ACTION_VIEW con la ruta de la imagen de disco (.iso/.xiso).
     * Si falla la actividad directa, usa el intent genérico del launcher de la app.
     *
     * Requisitos del usuario: MCPX Boot ROM + Flash ROM/BIOS + HDD image
     * (configurados la primera vez desde el Setup Wizard de X1 BOX)
     */
    private fun launchX1Box(romUri: Uri, pkgName: String? = null) {
        val pkg = pkgName ?: PKG_X1BOX
        if (!isPackageInstalled(pkg)) {
            openStoreOrLink(pkg, "https://play.google.com/store/apps/details?id=$PKG_X1BOX")
            return
        }
        // X1 BOX registra un intent-filter para .iso/.xiso con ACTION_VIEW
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "application/octet-stream")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(viewIntent)) {
            // Fallback: lanzar la app normalmente (el usuario selecciona la ROM desde la UI de X1 BOX)
            launchStandalone(pkg, "", romUri)
        }
    }

    private fun launchGenericStandalone(pkg: String, romUri: Uri) {
        if (!isPackageInstalled(pkg)) { openStoreOrLink(pkg); return }
        launchStandalone(pkg, "", romUri)
    }

    private fun launchStandalone(pkg: String, activity: String, romUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (activity.isNotEmpty()) component = ComponentName(pkg, activity)
            else setPackage(pkg)
            data = romUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (!tryStartActivity(intent)) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.data = romUri
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tryStartActivity(it)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerLaunch(game: Game) {
        settingsManager.gamesLaunchedCount = settingsManager.gamesLaunchedCount + 1
        prefs.edit().apply {
            putString("last_launched_game_title", game.title)
            putString("last_launched_platform", game.platformId)
            putLong("last_launch_timestamp", System.currentTimeMillis())
            putBoolean("returning_from_game", true)
            apply()
        }
        val targetMode = when (game.platformId.lowercase()) {
            "ps2", "gc", "gamecube", "wii", "3ds", "n3ds", "switch", "ps3", "xbox" -> "EXTREME"
            "n64", "psp", "dc", "dreamcast", "saturn", "model2", "model3"           -> "PERFORMANCE"
            else                                                                      -> "BALANCED"
        }
        HardwareControlManagerBeta.applyHardwareMode(targetMode)
    }

    /**
     * Resuelve la ruta real en disco a partir de una URI (file:// o content://).
     * Soporta almacenamiento primario y tarjetas SD externas.
     */
    private fun resolveRealPath(uri: Uri): String {
        if (uri.scheme != "content") return uri.path ?: ""
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val (type, path) = docId.split(":", limit = 2).let { it[0] to it[1] }
            if ("primary".equals(type, ignoreCase = true)) {
                "/storage/emulated/0/$path"
            } else {
                context.getExternalFilesDirs(null)
                    .filterNotNull()
                    .firstOrNull { it.absolutePath.contains(type) }
                    ?.absolutePath?.split("/Android")?.firstOrNull()
                    ?.let { "$it/$path" }
                    ?: "/storage/$type/$path"
            }
        } catch (e: Exception) {
            val raw = uri.toString()
                .substringAfter("document/")
                .replace("%3A", "/")
                .replace("%2F", "/")
            if (raw.contains("primary/"))
                raw.replace("primary/", "/storage/emulated/0/")
            else
                "/storage/$raw"
        }
    }

    private fun openStoreOrLink(pkg: String, fallbackUrl: String? = null) {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStartActivity(marketIntent)) {
            val url = fallbackUrl ?: "https://play.google.com/store/apps/details?id=$pkg"
            tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun isPackageInstalled(p: String): Boolean =
        try { context.packageManager.getPackageInfo(p, 0); true } catch (_: Exception) { false }
}
