package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * SpeccyRaTuning
 * ----------------------------------------------------------------------------
 * Ajustes de RetroArch POR CONSOLA + POR SISTEMA EMULADO, y — sobre todo —
 * escritura del appendconfig en un sitio que RetroArch pueda leer de verdad.
 *
 * EL BUG QUE ARREGLA
 * ------------------
 * `RetroArchPerformanceConfig.buildFor()` escribe el .cfg en
 *     context.cacheDir/ra_configs/<sistema>_perf.cfg
 *  =  /data/user/0/com.generacionarcade.speccyos/cache/...
 * y luego pasa esa ruta a RetroArch por EXTRA_APPENDCONFIG. RetroArch corre
 * con OTRO UID: ese directorio es ilegible para él. Resultado: TODOS los
 * overrides (vídeo, audio, latencia, mandos, XMB) se descartan en silencio
 * desde siempre, y el usuario ve "Speccy Engine aplicado" sin que se aplique.
 *
 * Aquí se elige un destino compartido, se verifica que sea legible y se
 * informa honestamente si no se ha podido.
 */
object SpeccyRaTuning {

    private const val TAG = "SpeccyRaTuning"
    private const val PUBLIC_DIR = "SpeccyOS/ra_configs"

    // ---------------------------------------------------------------- tuning

    /**
     * Líneas de configuración derivadas del catálogo de hardware y del sistema
     * que se va a emular. Se concatenan al appendconfig existente.
     */
    fun linesFor(platformId: String): List<String> {
        val dev = SpeccyPerformanceTuner.activeDevice()
        val t = dev.tuning
        val p = platformId.lowercase()
        val out = mutableListOf<String>()

        out += "# --- SpeccyOS · perfil de ${dev.name} (${dev.soc}) ---"

        // Driver de vídeo: vulkan sólo donde de verdad rinde mejor.
        val useVulkan = t.videoDriver == "vulkan" &&
            p !in setOf("psx", "n64", "saturn", "dreamcast") // cores GL puros
        // Respetamos el driver del catalogo: glcore no inicializa en Mali-450.
        out += "video_driver = \"${if (useVulkan) "vulkan" else t.videoDriver}\""
        out += "video_threaded = \"${t.threadedVideo.toString()}\""

        // Latencia: run-ahead es el mayor salto de sensación de respuesta, pero
        // cuesta una CPU entera. Sólo en sistemas ligeros y equipos capaces.
        val lightSystem = p in setOf(
            "nes", "famicom", "snes", "sfc", "gb", "gbc", "gba", "megadrive",
            "genesis", "mastersystem", "pcengine", "tg16", "neogeo", "arcade",
            "fbneo", "cps1", "cps2", "cps3", "atari2600", "gamegear"
        )
        if (t.runAheadFrames > 0 && lightSystem) {
            out += "run_ahead_enabled = \"true\""
            out += "run_ahead_frames = \"${t.runAheadFrames}\""
            out += "run_ahead_secondary_instance = \"${t.runAheadSecondInstance}\""
        } else {
            out += "run_ahead_enabled = \"false\""
        }

        // Sincronía dura de GPU: baja el input lag en equipos con margen.
        out += "video_hard_sync = \"${t.hardGpuSync && lightSystem}\""
        out += "video_frame_delay = \"${t.frameDelay}\""
        out += "video_frame_delay_auto = \"${t.frameDelay == 0}\""
        out += "video_swap_interval = \"${t.vsyncSwapInterval}\""

        // Audio: latencias bajas sólo donde el SoC aguanta, si no hay crackling.
        out += "audio_latency = \"${t.audioLatencyMs}\""
        // AAudio exige API 26; con minSdk 24 y cajas Android 7 hay que degradar.
        out += "audio_driver = \"${if (android.os.Build.VERSION.SDK_INT >= 26) "aaudio" else "audiotrack"}\""

        // Shaders: prohibidos en equipos de gama baja y en TV boxes de 2 GB.
        when (t.maxShader) {
            "none" -> out += "video_shader_enable = \"false\""
            "crt-lite" -> {
                out += "video_shader_enable = \"true\""
                out += "video_shader = \"shaders/shaders_glsl/crt/crt-pi.glslp\""
            }
            "crt-full" -> {
                out += "video_shader_enable = \"true\""
                out += "video_shader = \"shaders/shaders_slang/crt/crt-royale.slangp\""
            }
        }

        // Relación de aspecto: paneles cuadrados (RG Cube) o verticales se deforman
        // con "full"; se fuerza siempre la que provee el core (índice 22).
        out += "aspect_ratio_index = \"22\""
        out += "video_scale_integer = \"${lightSystem}\""

        // Resolución interna de los cores 3D, en función del tier real.
        if (p in setOf("psx", "n64", "dreamcast", "psp", "saturn", "gc", "wii", "ps2")) {
            out += "# resolución interna sugerida: x${t.preferredInternalScale}"
        }

        // Aviso si el sistema supera la capacidad del equipo.
        if (!SpeccyPerformanceTuner.canRun(p)) {
            out += "# AVISO SpeccyOS: $p supera el tier de ${dev.name} (${dev.tier.label})."
        }
        return out
    }

    // ------------------------------------------------------- escritura real

    data class WriteResult(
        val path: String?,
        val readableByOtherApps: Boolean,
        val reason: String
    )

    /**
     * Escribe el appendconfig donde RetroArch pueda leerlo.
     * Orden: almacenamiento público -> árbol SAF de ROMs concedido por el
     * usuario -> caché privada (último recurso, sabiendo que no servirá).
     */
    /**
     * Claves que NO pueden salir nunca de aqui hacia RetroArch.
     *
     * El appendconfig se FUSIONA sobre la configuracion de RetroArch: cualquier
     * clave que mandemos pisa la del usuario. Con las de entrada eso significa
     * quedarse sin mando o sin atajos dentro del juego, que es exactamente lo
     * que paso dos veces (ver REVERSION_Y_HISTORIAL_CAMBIOS.md, seccion 3).
     *
     * Speccy OS NO configura los controles de RetroArch: eso es cosa de
     * RetroArch y de su autoconfiguracion. Este filtro es la garantia de que
     * seguira siendo asi aunque alguien vuelva a anadir lineas de entrada mas
     * arriba sin darse cuenta.
     */
    private val CLAVES_PROHIBIDAS = listOf(
        "input_",                       // mapeados, hotkeys, overlays, poll
        "menu_swap_ok_cancel_buttons",  // invierte A/B en el menu
        "menu_swap_scroll_buttons",
        "quit_press_twice"              // cambia como se sale del juego
    )

    /**
     * true si el contenido trae alguna clave que no debe salir nunca hacia
     * RetroArch.
     *
     * Mismo criterio EXACTO que el filtro de escritura, y por eso es publico:
     * un .cfg que dejo una version anterior en almacenamiento compartido sigue
     * ahi despues de actualizar la app, y se le pasa a RetroArch por
     * EXTRA_APPENDCONFIG tal cual. Si quien decide si ese fichero es usable
     * mira menos claves que este filtro, el residuo se cuela igual y los
     * atajos siguen rotos aunque el codigo nuevo ya no inyecte nada.
     */
    fun tieneClavesProhibidas(contenido: String): Boolean =
        contenido.lineSequence().any { linea ->
            val clave = linea.substringBefore("=").trim()
            CLAVES_PROHIBIDAS.any { clave.startsWith(it) }
        }

    /**
     * Deja una sola linea por clave: la ULTIMA.
     *
     * El appendconfig se monta pegando dos bloques —la cabecera de identidad de
     * `buildAppendConfig()` y el perfil de hardware de `linesFor()`— y los dos
     * escriben las mismas claves con valores distintos. El fichero real de la
     * consola tenia `video_driver = "vulkan"` y doce lineas mas abajo
     * `video_driver = "gl"`, y lo mismo con `audio_latency` (32 y 64).
     *
     * RetroArch ya se queda con la ultima, asi que esto NO cambia el
     * comportamiento: quita ruido para que el fichero diga lo que de verdad se
     * aplica. Si alguien lo abre para depurar, ve el valor efectivo y no dos
     * contradictorios.
     *
     * Los comentarios y las lineas en blanco se conservan tal cual.
     */
    private fun sinClavesDuplicadas(contenido: String): Pair<String, Int> {
        val lineas = contenido.lines()

        val ultimaAparicion = HashMap<String, Int>()
        lineas.forEachIndexed { i, linea ->
            claveDeAsignacion(linea)?.let { ultimaAparicion[it] = i }
        }

        var quitadas = 0
        val limpio = lineas.filterIndexed { i, linea ->
            val clave = claveDeAsignacion(linea) ?: return@filterIndexed true
            val esLaUltima = ultimaAparicion[clave] == i
            if (!esLaUltima) quitadas++
            esLaUltima
        }
        return limpio.joinToString("\n") to quitadas
    }

    /** Clave de una linea `clave = valor`, o null si no lo es (comentario, blanco). */
    private fun claveDeAsignacion(linea: String): String? {
        val t = linea.trim()
        if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) return null
        return t.substringBefore("=").trim().ifEmpty { null }
    }

    /** Quita del cfg cualquier linea que toque la entrada. */
    private fun sinClavesDeEntrada(contenido: String): Pair<String, Int> {
        var quitadas = 0
        val limpio = contenido.lineSequence().filter { linea ->
            val clave = linea.substringBefore("=").trim()
            val prohibida = CLAVES_PROHIBIDAS.any { clave.startsWith(it) }
            if (prohibida) quitadas++
            !prohibida
        }.joinToString("\n")
        return limpio to quitadas
    }

    fun writeAppendConfig(
        context: Context,
        platformId: String,
        content: String,
        romsTreeUri: Uri? = null
    ): WriteResult {
        val name = "${platformId.lowercase()}_speccy.cfg"

        val (sinEntrada, quitadas) = sinClavesDeEntrada(content)
        if (quitadas > 0) {
            Log.w(TAG, "Descartadas $quitadas lineas de entrada del appendconfig: " +
                "los controles y atajos los gobierna RetroArch, no Speccy OS.")
        }

        val (cfg, duplicadas) = sinClavesDuplicadas(sinEntrada)
        if (duplicadas > 0) {
            Log.d(TAG, "Fusionadas $duplicadas claves repetidas del appendconfig.")
        }

        // 1) Almacenamiento público: legible por cualquier app con permiso de lectura.
        runCatching {
            val dir = File(Environment.getExternalStorageDirectory(), PUBLIC_DIR)
            if (dir.exists() || dir.mkdirs()) {
                val f = File(dir, name)
                f.writeText(cfg)
                f.setReadable(true, false)
                // OJO: File.canRead() solo habla de NUESTRO proceso, y setReadable es
                // un no-op en FUSE/sdcardfs. Solo damos por buena la ruta si esta
                // dentro del almacenamiento compartido real.
                val shared = Environment.getExternalStorageDirectory().absolutePath
                if (f.exists() && f.absolutePath.startsWith(shared)) {
                    return WriteResult(f.absolutePath, true, "Almacenamiento compartido")
                }
            }
        }.onFailure { Log.d(TAG, "Público no disponible: ${it.message}") }

        // 2) Dentro del árbol de ROMs que el usuario ya nos concedió por SAF.
        if (romsTreeUri != null) {
            runCatching {
                val root = DocumentFile.fromTreeUri(context, romsTreeUri)
                val dir = root?.findFile("SpeccyOS_cfg")
                    ?: root?.createDirectory("SpeccyOS_cfg")

                // OJO CON EL NOMBRE. createFile("text/plain", "mame_speccy.cfg")
                // NO crea ese fichero: SAF le anade la extension propia del MIME
                // y queda "mame_speccy.cfg.txt". Al siguiente lanzamiento,
                // findFile("mame_speccy.cfg") no encuentra nada, se vuelve a
                // crear, y SAF resuelve la colision con "(1)", "(2)"...
                //
                // Resultado: UN FICHERO NUEVO POR PARTIDA, para siempre, dentro
                // de la carpeta de ROMs del usuario. Verificado en consola el 10
                // de septiembre de 2026: tras un lanzamiento aparecio
                // "mame_speccy.cfg (1).txt" junto al "mame_speccy.cfg.txt" ya
                // existente.
                //
                // Se busca por los dos nombres y se crea ya con el definitivo,
                // que es el que SAF iba a poner de todos modos.
                val safName = "$name.txt"
                val doc = dir?.findFile(safName)
                    ?: dir?.findFile(name)
                    ?: dir?.createFile("text/plain", safName)

                limpiarDuplicadosSaf(dir, name, doc?.name)
                if (doc != null) {
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                        it.write(cfg.toByteArray())
                    }
                    val real = resolveTreePath(doc.uri)
                    if (real != null && File(real).canRead()) {
                        return WriteResult(real, true, "Carpeta de ROMs")
                    }
                    return WriteResult(real, false, "Escrito en SAF, ruta real no resoluble")
                }
            }.onFailure { Log.d(TAG, "SAF no disponible: ${it.message}") }
        }

        // 3) Caché privada: se escribe, pero avisamos de que RetroArch no lo leerá.
        return runCatching {
            val dir = File(context.cacheDir, "ra_configs").apply { mkdirs() }
            val f = File(dir, name)
            f.writeText(cfg)
            WriteResult(
                f.absolutePath, false,
                "Sólo caché privada: RetroArch (otro UID) NO podrá leerlo. " +
                    "Concede acceso a la carpeta de ROMs para que los ajustes se apliquen."
            )
        }.getOrElse { WriteResult(null, false, "No se pudo escribir: ${it.message}") }
    }

    /**
     * Borra los duplicados que dejo el bug del nombre: "<base>.cfg (1).txt",
     * "(2)", "(3)"... Se conserva [conservar] y cualquier fichero ajeno.
     *
     * Es limpieza de residuos propios, no del contenido del usuario: solo se
     * tocan nombres que empiezan por el prefijo que genera esta misma clase.
     */
    private fun limpiarDuplicadosSaf(dir: DocumentFile?, name: String, conservar: String?) {
        if (dir == null) return
        runCatching {
            val duplicado = Regex(
                "^" + Regex.escape(name) + """ \(\d+\)(\.txt)?$"""
            )
            dir.listFiles()
                .filter { it.name != null && it.name != conservar && duplicado.matches(it.name!!) }
                .forEach { residuo ->
                    if (residuo.delete()) {
                        Log.i(TAG, "Duplicado borrado: ${residuo.name}")
                    }
                }
        }.onFailure { Log.d(TAG, "No se pudieron limpiar duplicados: ${it.message}") }
    }

    /** Traduce una URI SAF de almacenamiento externo a ruta absoluta si se puede. */
    private fun resolveTreePath(uri: Uri): String? = runCatching {
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        val parts = docId.split(":")
        if (parts.size < 2) return null
        when (parts[0]) {
            "primary" -> "${Environment.getExternalStorageDirectory()}/${parts[1]}"
            else -> "/storage/${parts[0]}/${parts[1]}"
        }
    }.getOrNull()
}
