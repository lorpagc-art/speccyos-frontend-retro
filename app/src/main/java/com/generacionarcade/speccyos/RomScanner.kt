/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * RomScanner
 * ----------------------------------------------------------------------------
 * Escáner de biblioteca. Reescrito en la auditoría de agosto de 2026.
 *
 * QUÉ SE ARREGLA
 * --------------
 * 1. ARRANQUE. La configuración de sistemas se leía en el `init`: `assets.list()`
 *    más un `systeminfo.txt` parseado por cada una de las 174 plataformas. Como
 *    el ViewModel se construye dentro de `setContent`, todo ese I/O ocurría en el
 *    hilo principal ANTES del primer frame. Ahora es `by lazy` y se materializa
 *    en el primer escaneo, que ya corre en Dispatchers.IO.
 *
 * 2. CARPETAS INVISIBLES. Se comparaba el nombre de carpeta con IGUALDAD EXACTA
 *    (`allowedPlatforms.find { it == dirName }`) con un único caso especial
 *    (3ds→n3ds). Carpetas llamadas `ps1`, `gamecube`, `nintendo64`, `md`,
 *    `playstation`, `sega genesis`… simplemente NO se detectaban, y el usuario
 *    veía una biblioteca vacía sin ningún mensaje. Ahora hay resolución por
 *    alias, por normalización y, en último término, por RetroArchDatabase.
 *
 * 3. FAN-OUT SIN LÍMITE. La recursión lanzaba una corrutina por subdirectorio sin
 *    ningún tope, dentro de un proveedor SAF que serializa las consultas: miles
 *    de corrutinas compitiendo, presión de memoria y ANR en bibliotecas grandes.
 *    Ahora es un recorrido iterativo con cola, semáforo global de I/O y límite de
 *    profundidad.
 *
 * 4. FORMATOS DE DISCO Y MULTI-DISCO. No había soporte de `.chd` fuera de arcade,
 *    ni de `.cue/.bin`, `.gdi`, `.m3u`, `.pbp`, `.rvz`, `.wua`. Un juego de dos
 *    discos aparecía como dos entradas. Ahora: extensiones de disco por sistema,
 *    los `.bin` sueltos se ocultan cuando existe su `.cue` hermano, y si hay un
 *    `.m3u` se lanza ése y se ocultan los discos individuales.
 */
class RomScanner(private val context: Context) {

    private val TAG = "RomScannerTurbo"

    // ── Configuración perezosa ───────────────────────────────────────────────
    // Antes esto se cargaba en el init, en el hilo principal, durante el arranque.
    private data class Configs(
        val systemConfigs: Map<String, Set<String>>,
        val allowedPlatforms: Set<String>
    )

    private val configs: Configs by lazy { loadSystemConfigsFromAssets() }
    private val systemConfigs: Map<String, Set<String>> get() = configs.systemConfigs
    private val allowedPlatforms: Set<String> get() = configs.allowedPlatforms

    // Carpetas que nunca contienen ROMs. La lista anterior dejaba fuera bios,
    // saves, states, cheats, overlays, gamelists y el propio árbol de ES-DE, que
    // se escaneaban enteros para nada.
    private val blacklistedFolders = setOf(
        "media", "images", "videos", "screenshots", "downloaded_media", "boxart",
        "wheel", "data", "bios", "saves", "savestates", "states", "cheats",
        "overlays", "gamelists", "manuals", "shaders", "system", "es-de",
        "themes", "logs", "cache", "thumbnails", "covers", "marquees",
        "miximages", "titlescreens", "backcovers", "physicalmedia", "fanart",
        "videos_preview", "snap", "artwork", "info", ".directory", ".git"
    )

    /** Un único semáforo global: el proveedor SAF serializa las consultas igual. */
    private val ioSemaphore = Semaphore(4)

    private val regexIdPrefix = Regex("^\\d+\\s*-\\s*")
    private val regexParentheses = Regex("\\s*\\(.*?\\)")
    private val regexBrackets = Regex("\\s*\\[.*?\\]")
    private val regexDiscTag = Regex("\\s*\\((?:disc|disk|cd)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE)

    private fun loadSystemConfigsFromAssets(): Configs {
        val cfg = mutableMapOf<String, Set<String>>()
        val allowed = mutableSetOf<String>()
        try {
            val romsDir = "contentimg/ROMs"
            val folders = context.assets.list(romsDir) ?: emptyArray()
            for (folder in folders) {
                if (folder.startsWith(".") || folder.endsWith(".txt")) continue
                val platformId = folder.lowercase()
                allowed.add(platformId)
                val infoFile = "$romsDir/$folder/systeminfo.txt"
                try {
                    context.assets.open(infoFile).use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val extensions = mutableSetOf<String>()
                        var captureExtensions = false
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                if (trimmed.startsWith("Supported file extensions:", ignoreCase = true)) {
                                    captureExtensions = true
                                } else if (captureExtensions) {
                                    if (trimmed.endsWith(":")) break
                                    trimmed.split(" ")
                                        .map { it.trim().lowercase().removePrefix(".") }
                                        .filter { it.isNotEmpty() }
                                        .forEach { extensions.add(it) }
                                }
                            }
                            line = reader.readLine()
                        }
                        cfg[platformId] = if (extensions.isNotEmpty()) extensions else DEFAULT_EXTENSIONS
                    }
                } catch (e: Exception) {
                    cfg[platformId] = DEFAULT_EXTENSIONS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando la configuración de sistemas", e)
        }

        // Arcade y sistemas sin systeminfo.txt
        listOf("model2", "model3", "cps1", "cps2", "cps3", "mame", "fbneo", "fba",
               "naomi", "naomi2", "atomiswave", "stv", "neogeo", "arcade").forEach { sys ->
            allowed.add(sys)
            cfg[sys] = (cfg[sys] ?: emptySet()) + setOf("zip", "7z", "chd")
        }

        // FORMATOS DE DISCO. Antes sólo arcade reconocía .chd y no existía ningún
        // soporte de .cue/.bin/.gdi/.m3u: los sistemas de CD eran inusables.
        DISC_SYSTEMS.forEach { sys ->
            if (allowed.contains(sys)) {
                cfg[sys] = (cfg[sys] ?: DEFAULT_EXTENSIONS) + DISC_EXTENSIONS
            }
        }

        Log.i(TAG, "Configuración cargada: ${allowed.size} plataformas.")
        return Configs(cfg, allowed)
    }

    // ── Resolución de plataforma ────────────────────────────────────────────

    /**
     * Convierte el nombre de una carpeta en un id de plataforma soportado.
     * Cuatro pasadas, de la más barata a la más cara.
     */
    private fun resolvePlatform(rawFolderName: String): String? {
        val name = rawFolderName.trim().lowercase()
        if (name.isEmpty()) return null

        // 1. Coincidencia exacta con una carpeta de assets
        if (name in allowedPlatforms) return name

        // 2. Alias frecuentes de nombres de carpeta
        FOLDER_ALIASES[name]?.let { if (it in allowedPlatforms) return it }

        // 3. Normalizado: sin espacios, guiones ni puntos
        val normalized = name.replace(Regex("[^a-z0-9]"), "")
        if (normalized in allowedPlatforms) return normalized
        FOLDER_ALIASES[normalized]?.let { if (it in allowedPlatforms) return it }
        allowedPlatforms.firstOrNull { it.replace(Regex("[^a-z0-9]"), "") == normalized }?.let { return it }

        // 4. Base de datos de RetroArch como último recurso
        val fromDb = runCatching { RetroArchDatabase.findSystemById(name)?.id }.getOrNull()
        if (fromDb != null && fromDb in allowedPlatforms) return fromDb

        return null
    }

    // ── Escaneo ─────────────────────────────────────────────────────────────

    suspend fun scanAllRecursive(
        rootUri: String,
        onGameFound: suspend (Game) -> Unit,
        onPlatformDiscovered: suspend (String) -> Unit,
        onPlatformFinished: suspend (String) -> Unit
    ) = coroutineScope {
        // La base de sistemas debe estar COMPLETA antes de resolver plataformas:
        // con solo la fase rapida (27 de ~173 sistemas), resolvePlatform() devuelve
        // null para el resto y sus ROMs se descartan sin avisar. Ya estamos fuera
        // del hilo principal, asi que esperar aqui no bloquea la interfaz.
        RetroArchDatabase.awaitFullyLoaded()

        // Fuerza la carga perezosa aquí, ya fuera del hilo principal.
        withContext(Dispatchers.IO) { allowedPlatforms.size }

        val rootUriParsed = Uri.parse(rootUri)
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(rootUriParsed)
        } catch (e: Exception) {
            Log.e(TAG, "URI raíz no válida: $rootUri", e)
            return@coroutineScope
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUriParsed, rootDocId)

        // 1. ¿La propia raíz es ya una plataforma? (una SD con una sola consola)
        val rootDoc = try { DocumentFile.fromTreeUri(context, rootUriParsed) } catch (e: Exception) { null }
        val matchedRootPlatform = resolvePlatform(rootDoc?.name.orEmpty())
        if (matchedRootPlatform != null) {
            Log.i(TAG, "Raíz detectada como plataforma: $matchedRootPlatform")
            onPlatformDiscovered(matchedRootPlatform)
            val validExtensions = systemConfigs[matchedRootPlatform] ?: DEFAULT_EXTENSIONS
            launch(Dispatchers.IO) {
                scanPlatformTree(rootUriParsed, matchedRootPlatform, validExtensions, onGameFound)
                onPlatformFinished(matchedRootPlatform)
            }
        }

        // 2. Subcarpetas que son sistemas
        val systemFolders = mutableListOf<Pair<Uri, String>>()
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        if (cursor.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val docId = cursor.getString(idCol)
                            val name = cursor.getString(nameCol) ?: continue
                            systemFolders.add(
                                DocumentsContract.buildDocumentUriUsingTree(rootUriParsed, docId) to name
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listando la raíz $rootUri", e)
            }
        }

        var unmatched = 0
        systemFolders.forEach { (uri, name) ->
            val matchedPlatform = resolvePlatform(name)
            if (matchedPlatform == null) {
                if (name.lowercase() !in blacklistedFolders) unmatched++
                return@forEach
            }
            onPlatformDiscovered(matchedPlatform)
            val validExtensions = systemConfigs[matchedPlatform] ?: DEFAULT_EXTENSIONS
            launch(Dispatchers.IO) {
                scanPlatformTree(uri, matchedPlatform, validExtensions, onGameFound)
                onPlatformFinished(matchedPlatform)
            }
        }
        if (unmatched > 0) {
            Log.w(TAG, "$unmatched carpetas no coinciden con ninguna plataforma conocida.")
        }
    }

    /**
     * Recorrido ITERATIVO en anchura con cola y límite de profundidad.
     * La versión anterior era recursiva con `launch` por subdirectorio y sin tope.
     */
    private suspend fun scanPlatformTree(
        rootDirUri: Uri,
        platformId: String,
        validExtensions: Set<String>,
        onGameFound: suspend (Game) -> Unit
    ) {
        val queue = ArrayDeque<Pair<Uri, Int>>()
        queue.addLast(rootDirUri to 0)

        // Nombres de fichero ya vistos en este sistema, para agrupar multi-disco.
        val emittedTitles = HashSet<String>()

        while (queue.isNotEmpty()) {
            val (dirUri, depth) = queue.removeFirst()
            val entries = ioSemaphore.withPermit { listDirectory(dirUri) }

            val filesInDir = entries.filter { !it.isDirectory }
            val namesLower = filesInDir.map { it.name.lowercase() }.toSet()

            for (entry in entries) {
                if (entry.isDirectory) {
                    val lower = entry.name.lowercase()
                    if (lower in blacklistedFolders || lower.startsWith(".")) continue
                    if (platformId == "ports") {
                        // En `ports` cada subcarpeta ES el juego. Antes se creaba un
                        // Game con extension ".dir" que LauncherManager no sabía
                        // lanzar y acababa pasando un DIRECTORIO como EXTRA_ROM.
                        onGameFound(
                            Game(
                                title = cleanRomName(entry.name),
                                path = entry.uri.toString(),
                                platformId = platformId,
                                extension = ".dir",
                                fileName = entry.name
                            )
                        )
                    } else if (depth < MAX_DEPTH) {
                        queue.addLast(entry.uri to depth + 1)
                    }
                    continue
                }

                val dotIndex = entry.name.lastIndexOf('.')
                if (dotIndex == -1) continue
                val ext = entry.name.substring(dotIndex + 1).lowercase()
                if (ext !in validExtensions) continue

                val stem = entry.name.substring(0, dotIndex)

                // MULTI-DISCO Y CUE/BIN
                // - Un .bin con su .cue hermano no es un juego: es una pista.
                // - Si existe un .m3u, el juego es el .m3u y los discos sueltos se
                //   ocultan, en lugar de aparecer como entradas duplicadas.
                if (ext in TRACK_EXTENSIONS && namesLower.contains("${stem.lowercase()}.cue")) continue
                if (ext != "m3u") {
                    val baseTitle = stem.replace(regexDiscTag, "").trim().lowercase()
                    if (namesLower.any { it.endsWith(".m3u") && it.removeSuffix(".m3u").replace(regexDiscTag, "").trim() == baseTitle }) continue
                }

                val title = cleanRomName(stem)
                // Discos de un mismo juego sin .m3u: se emite sólo el primero y se
                // marca, para no llenar la lista de "Final Fantasy VII (Disc 1..3)".
                val dedupeKey = title.lowercase()
                if (ext !in DISC_EXTENSIONS || emittedTitles.add(dedupeKey)) {
                    onGameFound(
                        Game(
                            title = title,
                            path = entry.uri.toString(),
                            platformId = platformId,
                            extension = ".$ext",
                            fileName = entry.name
                        )
                    )
                }
            }
        }
    }

    private data class Entry(val uri: Uri, val name: String, val isDirectory: Boolean)

    private suspend fun listDirectory(directoryUri: Uri): List<Entry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Entry>()
        try {
            val docId = DocumentsContract.getDocumentId(directoryUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, docId)
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol)
                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(
                        directoryUri, cursor.getString(idCol)
                    )
                    result.add(Entry(itemUri, name, mime == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listando $directoryUri", e)
        }
        result
    }

    private fun cleanRomName(rawName: String): String = try {
        rawName.replace(regexIdPrefix, "")
            .replace(regexParentheses, "")
            .replace(regexBrackets, "")
            .trim()
            .ifEmpty { rawName }
    } catch (e: Exception) {
        rawName
    }

    companion object {
        private const val MAX_DEPTH = 6

        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        private val DEFAULT_EXTENSIONS = setOf(
            "zip", "7z", "iso", "bin", "nes", "fds", "gba", "sfc", "smc"
        )

        /** Formatos de imagen de disco que antes no se reconocían en ningún sistema. */
        private val DISC_EXTENSIONS = setOf(
            "chd", "cue", "gdi", "m3u", "pbp", "rvz", "wua", "ccd", "toc",
            "iso", "img", "mds", "nrg", "wbfs", "cso", "wud", "wux"
        )

        /** Pistas de datos: nunca son un juego por sí solas si existe el .cue. */
        private val TRACK_EXTENSIONS = setOf("bin", "img", "raw", "wav")

        private val DISC_SYSTEMS = setOf(
            "psx", "ps2", "psp", "saturn", "saturnjp", "dreamcast", "segacd",
            "megacd", "megacdjp", "pcenginecd", "tg-cd", "neogeocd", "neogeocdjp",
            "3do", "amigacd32", "cdtv", "cdimono1", "gc", "wii", "wiiu",
            "naomi", "naomigd", "atomiswave", "pcfx", "fmtowns", "pc98", "x68000"
        )

        /**
         * Nombres de carpeta que la gente usa de verdad, mapeados al id de assets.
         * Sin esto, una carpeta llamada "ps1" o "gamecube" no se detectaba.
         */
        private val FOLDER_ALIASES = mapOf(
            "ps1" to "psx", "playstation" to "psx", "psone" to "psx", "sony psx" to "psx",
            "playstation2" to "ps2", "sony ps2" to "ps2",
            "playstationportable" to "psp", "vita" to "psvita", "playstationvita" to "psvita",
            "nintendo64" to "n64", "n64dd" to "n64dd",
            "gamecube" to "gc", "ngc" to "gc", "dolphin" to "gc",
            "supernintendo" to "snes", "superfamicom" to "sfc", "sfam" to "sfc",
            "nintendo" to "nes", "nintendoentertainmentsystem" to "nes",
            "gameboy" to "gb", "gameboycolor" to "gbc", "gameboyadvance" to "gba",
            "nintendods" to "nds", "ds" to "nds",
            "nintendo3ds" to "n3ds", "3ds" to "n3ds", "citra" to "n3ds",
            "md" to "megadrive", "segagenesis" to "genesis", "segamegadrive" to "megadrive",
            "sms" to "mastersystem", "segamastersystem" to "mastersystem",
            "gg" to "gamegear", "segagamegear" to "gamegear",
            "32x" to "sega32x", "sega32x" to "sega32x",
            "segacd" to "segacd", "megacd" to "megacd",
            "dc" to "dreamcast", "segadreamcast" to "dreamcast", "flycast" to "dreamcast",
            "segasaturn" to "saturn",
            "pce" to "pcengine", "turbografx" to "tg16", "turbografx16" to "tg16",
            "pcecd" to "pcenginecd", "turbografxcd" to "tg-cd",
            "ngp" to "ngp", "neogeopocket" to "ngp", "neogeopocketcolor" to "ngpc",
            "ws" to "wonderswan", "wsc" to "wonderswancolor",
            "lynx" to "atarilynx", "jaguar" to "atarijaguar",
            "a2600" to "atari2600", "vcs" to "atari2600",
            "a5200" to "atari5200", "a7800" to "atari7800",
            "st" to "atarist", "amigaocs" to "amiga", "amigaaga" to "amiga1200",
            "cpc" to "amstradcpc", "amstrad" to "amstradcpc",
            "spectrum" to "zxspectrum", "zx" to "zxspectrum", "speccy" to "zxspectrum",
            "c64" to "c64", "commodore64" to "c64",
            "arcade" to "arcade", "mame2003" to "mame", "mame2010" to "mame",
            "fba" to "fbneo", "finalburn" to "fbneo", "finalburnneo" to "fbneo",
            "cps" to "cps", "capcom" to "cps",
            "nintendoswitch" to "switch", "yuzu" to "switch", "ryujinx" to "switch",
            "wiiu" to "wiiu", "cemu" to "wiiu",
            "pcwindows" to "windows", "win" to "windows", "winlator" to "windows",
            "msdos" to "dos", "dosbox" to "dos",
            "scummvm" to "scummvm", "ports" to "ports", "pico-8" to "pico8"
        )
    }
}
