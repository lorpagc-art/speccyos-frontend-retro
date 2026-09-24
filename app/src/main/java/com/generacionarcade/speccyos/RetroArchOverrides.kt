/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Overrides de RetroArch escritos por Speccy OS.
 *
 * ── POR QUE EXISTE ──────────────────────────────────────────────────────────
 * RetroArch Android solo lee del Intent ROM, LIBRETRO y CONFIGFILE (ver la
 * cabecera de LauncherManager). Los extras STATE y "args" con los que se
 * pretendia cargar un estado (Maquina del Tiempo) o arrancar netplay no
 * existen: nunca hicieron nada. La unica via real para influir en UNA partida
 * concreta es lo que el propio RetroArch ofrece: sus overrides de
 * configuracion, que aplica solo al cargar contenido y quita al cerrarlo.
 *
 *   <RetroArch>/config/<core>/<core>.cfg    override de core (todos sus juegos)
 *   <RetroArch>/config/<core>/<juego>.cfg   override de juego
 *
 * `<core>` es el `library_name` que declara el core (p. ej. "Gambatte",
 * "Genesis Plus GX", "dolphin-emu"), NO el nombre del .so. `<juego>` es el
 * nombre del fichero de la ROM sin extension; con ROMs comprimidas es el del
 * .zip, que es tambien como RetroArch nombra sus estados.
 *
 * ── QUE SE ESCRIBE ──────────────────────────────────────────────────────────
 * Maquina del Tiempo (override de juego, solo en ese lanzamiento):
 *   - `savestate_auto_load = "true"`, y el estado elegido copiado al lado
 *     como `<juego>.state.auto`, que es lo que RetroArch carga al arrancar.
 *   - `cheevos_hardcore_mode_enable = "false"`: en modo hardcore RetroArch se
 *     niega a cargar estados, y la Maquina del Tiempo es exactamente eso.
 * RetroAchievements (override de core, mientras el ajuste este activo):
 *   - `cheevos_enable`, `cheevos_username`, `cheevos_token`. Con el token de
 *     conexion RetroArch entra sin contraseña, igual que hace el solo cuando
 *     ya ha iniciado sesion una vez. Speccy OS no guarda la contraseña.
 * Netplay (override de core, solo en ese lanzamiento):
 *   - `netplay_ip_address`, `netplay_ip_port`, `netplay_nickname`. RetroArch
 *     no tiene ninguna clave que arranque netplay al cargar contenido; solo
 *     se le puede dejar todo rellenado para que el usuario pulse
 *     "Conectar" o "Iniciar anfitrion" en su menu.
 *
 * ── LIMPIEZA ────────────────────────────────────────────────────────────────
 * Los overrides se quedan en disco, y un `savestate_auto_load` olvidado haria
 * que RetroArch cargara el mismo estado en cada partida, incluso lanzada
 * desde el propio RetroArch. Por eso solo se tocan las claves de
 * [CLAVES_JUEGO] y [CLAVES_CORE]: al preparar un lanzamiento normal se
 * eliminan, y ademas [limpiarPendiente] se llama al volver a Speccy OS. Si
 * el fichero lo creo Speccy OS y se queda vacio, se borra; si ya existia
 * (el usuario guardo su propio override desde RetroArch), se conservan sus
 * lineas y se quitan solo las nuestras.
 *
 * Todo pasa por SAF sobre la carpeta RetroArch que el usuario concede en
 * Ajustes → Cuenta (la misma que usa Nebula Sync). Sin ella no se escribe
 * nada y la Maquina del Tiempo avisa en vez de fingir que funciona.
 */
class RetroArchOverrides(private val context: Context) {

    companion object {
        private const val TAG = "SpeccyRaOverrides"
        private const val PREFS = "retroarch_overrides"
        private const val CABECERA =
            "# Speccy OS: las claves siguientes las gestiona Speccy OS en cada lanzamiento."

        private val CLAVES_JUEGO = setOf("savestate_auto_load", "cheevos_hardcore_mode_enable")
        private val CLAVES_CORE = setOf(
            "savestate_auto_save",
            "cheevos_enable", "cheevos_username", "cheevos_token",
            "netplay_ip_address", "netplay_ip_port", "netplay_nickname",
            "input_player1_analog_dpad_mode", "input_player2_analog_dpad_mode"
        )

        /**
         * Cores cuyos juegos usan EJES analogicos de verdad (volantes, sticks
         * de 360 grados, gatillos).
         *
         * RetroArch trae `input_playerN_analog_dpad_mode = "3"` (Left Analog
         * FORCED), que convierte el stick izquierdo en cruceta digital aunque
         * el core soporte analogico. Resultado: en los juegos de conduccion de
         * Atomiswave/Naomi el volante no se mueve NI con el stick (convertido en
         * cruceta) NI con la cruceta (que nunca fue analogica), y la pantalla de
         * calibracion del juego no se puede completar. Verificado con
         * "Faster Than Speed" en una GameMT E5 Ultra el 24-sep-2026.
         *
         * Para estos cores se pone a "0" (None): el stick manda ejes puros y la
         * cruceta fisica sigue haciendo de cruceta.
         */
        private val CORES_CON_ANALOGICO = setOf(
            "flycast", "flycast_gles2", "beetle_saturn", "yabasanshiro", "kronos",
            "mupen64plus_next", "mupen64plus_next_gles3", "parallel_n64",
            "pcsx_rearmed", "swanstation", "mednafen_psx", "mednafen_psx_hw",
            "dolphin", "ppsspp", "citra", "citra_canary", "play", "pcsx2",
            "melonds", "desmume"
        )

        /**
         * "Continuar donde lo dejaste". Se pasa en el sitio del estado a cargar
         * y significa: no copies nada, RetroArch ya tiene su propio
         * `<juego>.state.auto` (lo escribe al salir gracias a
         * `savestate_auto_save`); solo pon el override que se lo hace cargar.
         * Un File imposible, para no cambiar la firma de todo el camino de
         * lanzamiento (dashboard -> LauncherManager -> aqui).
         */
        val REANUDAR = File("/speccyos/reanudar-ultima-partida")

        /**
         * `library_name` de cada core que Speccy OS puede lanzar (id del .so
         * sin `_libretro_android`). Sacado de los .info de RetroArch 1.22 y
         * cotejado con las carpetas que RetroArch crea en `states/` y
         * `config/` en la E5. `dolphin` es la excepcion: su corename es
         * "Dolphin" pero el core se presenta como "dolphin-emu".
         */
        val LIBRARY_NAMES: Map<String, String> = mapOf(
            "beetle_psx" to "Beetle PSX",
            "beetle_saturn" to "Beetle Saturn",
            "bluemsx" to "blueMSX",
            "bsnes" to "bsnes",
            "citra" to "Citra",
            "citra_canary" to "Citra Canary",
            "desmume" to "DeSmuME",
            "dolphin" to "dolphin-emu",
            "dosbox_pure" to "DOSBox-pure",
            "dosbox_svn" to "DOSBox-SVN",
            "duckstation" to "DuckStation",
            "fbneo" to "FinalBurn Neo",
            "fceumm" to "FCEUmm",
            "flycast" to "Flycast",
            "fmsx" to "fMSX",
            "gambatte" to "Gambatte",
            "genesis_plus_gx" to "Genesis Plus GX",
            "gpsp" to "gpSP",
            "mame" to "MAME",
            // El core "MAME (Git)" del .info se presenta como "MAME" a secas:
            // en la EX8 sus carpetas de config/states/saves son "MAME".
            "mamearcade" to "MAME",
            "mame2000" to "MAME 2000",
            "mame2003" to "MAME 2003 (0.78)",
            "mame2003_plus" to "MAME 2003-Plus",
            "mame2010" to "MAME 2010",
            "mame2015" to "MAME 2015",
            "mame2016" to "MAME 2016",
            "mednafen_ngp" to "Beetle NeoPop",
            "mednafen_pce_fast" to "Beetle PCE Fast",
            "melonds" to "melonDS",
            "mesen" to "Mesen",
            "mgba" to "mGBA",
            "mupen64plus_next" to "Mupen64Plus-Next",
            "nestopia" to "Nestopia",
            "parallel_n64" to "ParaLLEl N64",
            "pcsx2" to "LRPS2",
            "pcsx_rearmed" to "PCSX-ReARMed",
            "picodrive" to "PicoDrive",
            "play" to "Play!",
            "ppsspp" to "PPSSPP",
            "puae" to "PUAE",
            "sameboy" to "SameBoy",
            "snes9x" to "Snes9x",
            "snes9x2010" to "Snes9x 2010",
            "uae4arm" to "UAE4ARM",
            "yabause" to "Yabause"
        )

        fun libraryName(coreId: String): String? = LIBRARY_NAMES[coreId.lowercase()]
    }

    /** Datos de netplay para un lanzamiento. Solo se rellenan; no arrancan nada. */
    data class Netplay(val ip: String, val puerto: Int, val apodo: String)

    /**
     * Resultado de [prepararLanzamiento]. `estadoListo` es true solo si el
     * `.state.auto` y el override de juego han quedado escritos; `aviso`
     * explica al usuario por que no, cuando se pidio un estado.
     */
    data class Resultado(val estadoListo: Boolean, val aviso: String? = null)

    private val settings = SettingsManager(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────
    // Entrada principal
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Deja los overrides como deben estar para el lanzamiento que viene.
     * Se llama SIEMPRE antes de abrir RetroArch, tambien sin estado ni
     * netplay: es lo que limpia lo del lanzamiento anterior.
     *
     * @param nombreBase nombre de la ROM sin extension (como nombra RetroArch
     *   sus estados).
     * @param coreId id del core ("gambatte", "fbneo"...).
     * @param estado fichero de estado a cargar al arrancar, o null.
     */
    fun prepararLanzamiento(
        nombreBase: String,
        coreId: String,
        estado: File? = null,
        netplay: Netplay? = null
    ): Resultado {
        val lib = libraryName(coreId)
        if (lib == null) {
            Log.w(TAG, "Sin library_name conocido para '$coreId': no se escriben overrides")
            return if (estado != null)
                Resultado(false, "La Máquina del Tiempo no está disponible con el core $coreId.")
            else Resultado(false)
        }
        val raiz = arbol()
        if (raiz == null) {
            return if (estado != null)
                Resultado(
                    false,
                    "Concede la carpeta RetroArch en Ajustes → Cuenta para que la Máquina del Tiempo pueda cargar la partida."
                )
            else Resultado(false)
        }

        val reanudar = estado === REANUDAR

        return runCatching {
            val carpetaCore = subcarpeta(subcarpeta(raiz, "config"), lib)

            // Override de juego: Maquina del Tiempo o "Continuar".
            val clavesJuego = if (estado != null) linkedMapOf(
                "savestate_auto_load" to "true",
                "cheevos_hardcore_mode_enable" to "false"
            ) else linkedMapOf()
            aplicar(carpetaCore, "$nombreBase.cfg", CLAVES_JUEGO, clavesJuego)

            var estadoListo = false
            var aviso: String? = null
            if (reanudar) {
                // El .state.auto lo escribio RetroArch al salir, alli donde el
                // guarda los estados; no hay nada que copiar ni que borrar luego.
                estadoListo = true
            } else if (estado != null) {
                estadoListo = copiarComoAuto(raiz, estado, nombreBase)
                if (!estadoListo) {
                    // Sin .state.auto el override es un peligro: lo quitamos.
                    aplicar(carpetaCore, "$nombreBase.cfg", CLAVES_JUEGO, linkedMapOf())
                    aviso = "No se pudo preparar la partida guardada; se abre el juego desde el principio."
                }
            } else {
                borrarAutoSiEsNuestro(raiz, lib, nombreBase)
            }

            // Override de core: autoguardado al salir, RetroAchievements y netplay.
            val clavesCore = linkedMapOf<String, String>()
            // Es lo que hace posible "Continuar donde lo dejaste": RetroArch
            // escribe <juego>.state.auto al cerrar el contenido. No lo carga
            // solo (savestate_auto_load va por juego y solo cuando se pide),
            // asi que un lanzamiento normal sigue empezando de cero.
            if (settings.continuarPartida) clavesCore["savestate_auto_save"] = "true"
            if (settings.raPassToRetroArch &&
                settings.raUsername.isNotBlank() && settings.raConnectToken.isNotBlank()
            ) {
                clavesCore["cheevos_enable"] = "true"
                clavesCore["cheevos_username"] = settings.raUsername
                clavesCore["cheevos_token"] = settings.raConnectToken
            }
            // Libera el stick en los cores que usan ejes de verdad (ver
            // CORES_CON_ANALOGICO). En el resto no se toca nada: en un core de
            // 8/16 bits el modo forzado es util, porque deja jugar con el stick.
            if (coreId.lowercase() in CORES_CON_ANALOGICO) {
                clavesCore["input_player1_analog_dpad_mode"] = "0"
                clavesCore["input_player2_analog_dpad_mode"] = "0"
            }
            if (netplay != null) {
                clavesCore["netplay_ip_address"] = netplay.ip
                clavesCore["netplay_ip_port"] = netplay.puerto.toString()
                if (netplay.apodo.isNotBlank()) clavesCore["netplay_nickname"] = netplay.apodo
            }
            aplicar(carpetaCore, "$lib.cfg", CLAVES_CORE, clavesCore)

            // Para poder limpiar al volver aunque el siguiente lanzamiento
            // sea de otro juego (o desde el propio RetroArch).
            prefs.edit()
                .putString("pendiente_lib", lib)
                .putString("pendiente_juego", nombreBase)
                .putBoolean("pendiente_netplay", netplay != null)
                .apply()

            Resultado(estadoListo, aviso)
        }.getOrElse {
            Log.e(TAG, "No se pudieron escribir los overrides de $nombreBase", it)
            Resultado(false, if (estado != null) "No se pudo preparar la partida guardada." else null)
        }
    }

    /**
     * Quita lo que solo valia para el ultimo lanzamiento (estado automatico y
     * netplay). Se llama al volver a Speccy OS; barato si no hay nada.
     */
    fun limpiarPendiente() {
        val lib = prefs.getString("pendiente_lib", null) ?: return
        val juego = prefs.getString("pendiente_juego", null) ?: return
        val habiaNetplay = prefs.getBoolean("pendiente_netplay", false)
        prefs.edit().remove("pendiente_lib").remove("pendiente_juego").remove("pendiente_netplay").apply()

        val raiz = arbol() ?: return
        runCatching {
            val config = raiz.findFile("config") ?: return
            val carpetaCore = config.findFile(lib) ?: return
            aplicar(carpetaCore, "$juego.cfg", CLAVES_JUEGO, linkedMapOf())
            borrarAutoSiEsNuestro(raiz, lib, juego)
            if (habiaNetplay) {
                // Las claves de RetroAchievements se conservan; solo cae netplay.
                aplicar(
                    carpetaCore, "$lib.cfg",
                    setOf("netplay_ip_address", "netplay_ip_port", "netplay_nickname"),
                    linkedMapOf()
                )
            }
        }.onFailure { Log.w(TAG, "Limpieza de overrides de $juego fallida: ${it.message}") }
    }

    /**
     * Cuando el usuario apaga el ajuste de RetroAchievements hay que retirar
     * las credenciales de todos los overrides de core donde se pusieron.
     */
    fun retirarCredencialesRa() {
        val raiz = arbol() ?: return
        runCatching {
            val config = raiz.findFile("config") ?: return
            config.listFiles().filter { it.isDirectory }.forEach { carpeta ->
                val nombre = carpeta.name ?: return@forEach
                if (carpeta.findFile("$nombre.cfg") != null) {
                    aplicar(
                        carpeta, "$nombre.cfg",
                        setOf("cheevos_enable", "cheevos_username", "cheevos_token"),
                        linkedMapOf()
                    )
                }
            }
        }.onFailure { Log.w(TAG, "No se pudieron retirar las credenciales de RA: ${it.message}") }
    }

    /**
     * Al apagar "Continuar donde lo dejaste" se retira `savestate_auto_save`
     * de todos los overrides de core donde se puso; si no, RetroArch seguiria
     * escribiendo estados al salir sin que nadie los ofreciera.
     */
    fun retirarAutoguardado() {
        val raiz = arbol() ?: return
        runCatching {
            val config = raiz.findFile("config") ?: return
            config.listFiles().filter { it.isDirectory }.forEach { carpeta ->
                val nombre = carpeta.name ?: return@forEach
                if (carpeta.findFile("$nombre.cfg") != null) {
                    aplicar(carpeta, "$nombre.cfg", setOf("savestate_auto_save"), linkedMapOf())
                }
            }
        }.onFailure { Log.w(TAG, "No se pudo retirar el autoguardado: ${it.message}") }
    }

    /** ¿Hay carpeta RetroArch concedida? Para que la interfaz avise antes. */
    fun disponible(): Boolean = arbol() != null

    /**
     * Fecha (epoch ms) del `<juego>.state.auto` que RetroArch dejo al salir,
     * o null si no hay. Mira en todos los sitios donde RetroArch puede
     * haberlo puesto segun su configuracion, que Speccy OS no puede leer:
     * junto a la ROM (`savestates_in_content_dir`, como viene la GameMT EX8),
     * en `states/<core>/` (`sort_savestates_enable`, como la E5), en
     * `states/<core>/<carpeta de la ROM>/`, en `states/<carpeta>/` y en
     * `states/`. Se consulta cada ruta directamente, sin listar carpetas.
     */
    fun fechaAutoEstado(game: Game, coreId: String): Long? {
        val lib = libraryName(coreId) ?: return null
        val nombreBase = game.fileName.substringBeforeLast(".")
        val nombreAuto = "$nombreBase.state.auto"
        val candidatos = mutableListOf<Uri>()

        // Junto a la ROM. El arbol se saca de la propia URI de la ROM, no de
        // `romsLocation`: la biblioteca puede venir de mas de una raiz (en la
        // EX8 la carpeta concedida es la interna y Final Fight esta en la SD,
        // "26B5-EF19:Roms/cps1/ffight.zip") y un arbol ajeno no resuelve.
        runCatching {
            val romUri = Uri.parse(game.path)
            if (romUri.scheme == "content") {
                DocumentsContract.getTreeDocumentId(romUri) // lanza si no es URI de arbol
                val docId = DocumentsContract.getDocumentId(romUri)
                val hermano = docId.substringBeforeLast("/", "").let { dir ->
                    if (dir.isEmpty()) nombreAuto else "$dir/$nombreAuto"
                }
                candidatos += DocumentsContract.buildDocumentUriUsingTree(romUri, hermano)
            }
        }

        // Dentro de la carpeta RetroArch concedida.
        runCatching {
            val raTree = settings.retroarchFolderUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            if (raTree != null) {
                val raizId = DocumentsContract.getTreeDocumentId(raTree)
                val carpetaRom = runCatching {
                    DocumentsContract.getDocumentId(Uri.parse(game.path))
                        .substringBeforeLast("/").substringAfterLast("/").substringAfter(":")
                }.getOrNull().orEmpty()
                val rutas = buildList {
                    add("states/$lib/$nombreAuto")
                    if (carpetaRom.isNotBlank()) {
                        add("states/$lib/$carpetaRom/$nombreAuto")
                        add("states/$carpetaRom/$nombreAuto")
                    }
                    add("states/$nombreAuto")
                }
                rutas.forEach { candidatos += DocumentsContract.buildDocumentUriUsingTree(raTree, "$raizId/$it") }
            }
        }

        // Respaldo por ruta directa, donde la app puede leer el disco.
        val directos = buildList {
            runCatching {
                val romUri = Uri.parse(game.path)
                val real = if (romUri.scheme == "content") {
                    val docId = DocumentsContract.getDocumentId(romUri)
                    val partes = docId.split(":", limit = 2)
                    if (partes.size == 2) {
                        val raizFs = if (partes[0] == "primary") Environment.getExternalStorageDirectory().path else "/storage/${partes[0]}"
                        File(raizFs, partes[1])
                    } else null
                } else File(romUri.path ?: "")
                real?.parentFile?.let { add(File(it, nombreAuto)) }
            }
            rutaRaiz()?.let { add(File("$it/states/$lib/$nombreAuto")); add(File("$it/states/$nombreAuto")) }
        }

        var mejor: Long? = null
        candidatos.forEach { uri ->
            runCatching {
                val doc = DocumentFile.fromSingleUri(context, uri) ?: return@runCatching
                if (doc.exists() && doc.length() > 0) {
                    val f = doc.lastModified()
                    if (f > (mejor ?: 0L)) mejor = f
                }
            }
        }
        directos.forEach { f ->
            runCatching { if (f.isFile && f.length() > 0 && f.lastModified() > (mejor ?: 0L)) mejor = f.lastModified() }
        }
        return mejor
    }

    // ─────────────────────────────────────────────────────────────────────
    // Ficheros .cfg
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reescribe `<dir>/<nombre>` dejando intactas las lineas ajenas, quitando
     * todas las de [gestionadas] y añadiendo [valores] al final. Con
     * [valores] vacio y nada ajeno, el fichero se borra.
     */
    private fun aplicar(
        dir: DocumentFile,
        nombre: String,
        gestionadas: Set<String>,
        valores: Map<String, String>
    ) {
        val existente = dir.findFile(nombre)
        val ajenas = existente?.let { leer(it) }
            ?.lines()
            ?.filter { linea ->
                val t = linea.trim()
                t != CABECERA && claveDe(t) !in gestionadas
            }
            ?.dropLastWhile { it.isBlank() }
            ?: emptyList()

        if (valores.isEmpty()) {
            if (existente == null) return
            if (ajenas.all { it.isBlank() }) {
                existente.delete()
                Log.d(TAG, "Override $nombre borrado (solo tenia claves de Speccy OS)")
            } else {
                escribir(existente, ajenas.joinToString("\n") + "\n")
            }
            return
        }

        val cuerpo = buildString {
            if (ajenas.any { it.isNotBlank() }) {
                append(ajenas.joinToString("\n")); append("\n\n")
            }
            append(CABECERA); append("\n")
            valores.forEach { (k, v) -> append(k); append(" = \""); append(v.replace("\"", "")); append("\"\n") }
        }
        val doc = existente ?: crearFichero(dir, nombre) ?: error("No se pudo crear $nombre")
        escribir(doc, cuerpo)
        Log.d(TAG, "Override $nombre escrito: ${valores.keys}")
    }

    private fun claveDe(linea: String): String? {
        if (linea.isBlank() || linea.startsWith("#")) return null
        val igual = linea.indexOf('=')
        if (igual <= 0) return null
        return linea.substring(0, igual).trim()
    }

    private fun leer(doc: DocumentFile): String? = runCatching {
        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    private fun escribir(doc: DocumentFile, texto: String) {
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
            it.write(texto.toByteArray(Charsets.UTF_8))
        } ?: error("No se pudo abrir ${doc.name} para escribir")
    }

    /**
     * `createFile` puede añadir una extension segun el MIME (asi nacieron los
     * `.cfg.txt` del antiguo Speccy Engine). Se crea como binario y, si aun
     * asi el nombre cambia, se renombra.
     */
    private fun crearFichero(dir: DocumentFile, nombre: String): DocumentFile? {
        val doc = dir.createFile("application/octet-stream", nombre) ?: return null
        if (doc.name != nombre && !doc.renameTo(nombre)) {
            Log.w(TAG, "El proveedor nombro el fichero '${doc.name}' en vez de '$nombre'")
        }
        return doc
    }

    private fun subcarpeta(padre: DocumentFile, nombre: String): DocumentFile =
        padre.findFile(nombre)?.takeIf { it.isDirectory }
            ?: padre.createDirectory(nombre)
            ?: error("No se pudo crear la carpeta $nombre")

    // ─────────────────────────────────────────────────────────────────────
    // .state.auto
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Copia [estado] como `<nombreBase>.state.auto` en su misma carpeta, que
     * es donde RetroArch lo buscara (respeta `sort_savestates_enable`). Se
     * anota tamaño y fecha para poder borrarlo luego sin tocar un
     * `.state.auto` que haya escrito RetroArch por su cuenta.
     */
    private fun copiarComoAuto(raiz: DocumentFile, estado: File, nombreBase: String): Boolean {
        val nombreAuto = "$nombreBase.state.auto"
        val origen = documentoDe(raiz, estado) ?: run {
            Log.w(TAG, "El estado ${estado.path} no esta dentro de la carpeta RetroArch concedida")
            return false
        }
        val carpeta = carpetaDe(raiz, estado) ?: return false

        if (estado.name == nombreAuto) {
            anotarAuto(nombreBase, origen)
            return true
        }
        val destino = carpeta.findFile(nombreAuto) ?: crearFichero(carpeta, nombreAuto) ?: return false
        context.contentResolver.openInputStream(origen.uri)?.use { entrada ->
            context.contentResolver.openOutputStream(destino.uri, "wt")?.use { salida ->
                entrada.copyTo(salida)
            } ?: return false
        } ?: return false
        anotarAuto(nombreBase, destino)
        Log.i(TAG, "Estado ${estado.name} copiado como $nombreAuto")
        return true
    }

    private fun anotarAuto(nombreBase: String, doc: DocumentFile) {
        prefs.edit().putString("auto_$nombreBase", "${doc.length()}:${doc.lastModified()}").apply()
    }

    private fun borrarAutoSiEsNuestro(raiz: DocumentFile, lib: String, nombreBase: String) {
        val firma = prefs.getString("auto_$nombreBase", null) ?: return
        prefs.edit().remove("auto_$nombreBase").apply()
        val nombreAuto = "$nombreBase.state.auto"
        val states = raiz.findFile("states") ?: return
        val candidatos = listOfNotNull(states.findFile(nombreAuto), states.findFile(lib)?.findFile(nombreAuto))
        candidatos.forEach { doc ->
            if ("${doc.length()}:${doc.lastModified()}" == firma) {
                doc.delete()
                Log.d(TAG, "$nombreAuto borrado (lo habia escrito Speccy OS)")
            } else {
                Log.d(TAG, "$nombreAuto ha cambiado desde que lo escribimos; se respeta")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Arbol SAF
    // ─────────────────────────────────────────────────────────────────────

    private fun arbol(): DocumentFile? = runCatching {
        val uri = settings.retroarchFolderUri
        if (uri.isEmpty()) return null
        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.takeIf { it.canRead() && it.canWrite() }
    }.getOrNull()

    /** Ruta real de la raiz concedida ("/storage/emulated/0/RetroArch"). */
    private fun rutaRaiz(): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(settings.retroarchFolderUri))
        val partes = docId.split(":", limit = 2)
        if (partes.size < 2) return null
        when (partes[0]) {
            "primary" -> "${Environment.getExternalStorageDirectory()}/${partes[1]}"
            else -> "/storage/${partes[0]}/${partes[1]}"
        }.trimEnd('/')
    }.getOrNull()

    private fun partesRelativas(raiz: DocumentFile, fichero: File): List<String>? {
        val base = rutaRaiz() ?: return null
        val ruta = fichero.absolutePath
        if (!ruta.startsWith("$base/")) return null
        return ruta.removePrefix("$base/").split('/').filter { it.isNotBlank() }
    }

    private fun documentoDe(raiz: DocumentFile, fichero: File): DocumentFile? {
        val partes = partesRelativas(raiz, fichero) ?: return null
        var actual: DocumentFile = raiz
        for (p in partes) actual = actual.findFile(p) ?: return null
        return actual
    }

    private fun carpetaDe(raiz: DocumentFile, fichero: File): DocumentFile? {
        val partes = partesRelativas(raiz, fichero) ?: return null
        var actual: DocumentFile = raiz
        for (p in partes.dropLast(1)) actual = actual.findFile(p) ?: return null
        return actual
    }
}
