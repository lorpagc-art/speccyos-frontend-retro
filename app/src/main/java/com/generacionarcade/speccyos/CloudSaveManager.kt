package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Collections

/**
 * ☁️ NEBULA SYNC V2.0 - Motor Inteligente de Guardado en la Nube
 * Subida y Bajada de partidas (.srm, .state) al AppData seguro de Google Drive.
 * ⏱️ TIME MACHINE V1.0 - Motor de escaneo de Save States Locales.
 */
class CloudSaveManager(private val context: Context) {
    private val TAG = "NebulaSync"

    companion object {
        /**
         * Separa la subcarpeta del core del nombre del fichero dentro del
         * appDataFolder de Drive, que es plano. Se elige '|' porque no aparece
         * en los nombres de core de RetroArch ("FB Alpha 2012 CPS-1",
         * "MAME 2003-Plus", "ParaLLEl N64"...) ni es valido en nombres de
         * fichero de Android.
         */
        private const val SEPARADOR_RUTA = "|"
    }

    // Rutas comunes de RetroArch en Android
    private val retroarchSavesDir = java.io.File(Environment.getExternalStorageDirectory(), "RetroArch/saves")
    private val retroarchStatesDir = java.io.File(Environment.getExternalStorageDirectory(), "RetroArch/states")

    // --- TIME MACHINE: Explorador Local de Save States ---
    /**
     * [file] es la ruta REAL del save state. Puede que nosotros no podamos
     * abrirla —en Android 11+ `/sdcard/RetroArch` es ilegible sin acceso a
     * todos los archivos— pero es la que hay que pasarle a RetroArch, que si es
     * su dueño.
     *
     * [imageUri] es la miniatura leida por SAF. Se usa cuando la ruta directa
     * no se puede abrir, que es el caso normal.
     */
    data class SaveState(
        val file: java.io.File,
        val imageFile: java.io.File?,
        val lastModified: Long,
        val imageUri: Uri? = null
    )

    /**
     * Ficheros de [game] dentro de [raiz], mirando TAMBIEN una carpeta de
     * profundidad.
     *
     * RetroArch trae `sort_savefiles_enable` y `sort_savestates_enable` a true
     * de fabrica en estas consolas, y entonces NO guarda en plano: mete cada
     * partida en una subcarpeta con el nombre del core.
     *
     *     /sdcard/RetroArch/states/Gambatte/Kirby's Block Ball (USA).state
     *     /sdcard/RetroArch/saves/FinalBurn Neo/fbneo/…
     *
     * Todo esto miraba solo la raiz, donde no hay ni un fichero: `listFiles`
     * devolvia las CARPETAS de los cores, cuyos nombres no empiezan por el del
     * juego, asi que el filtro las descartaba y la lista salia vacia siempre.
     * De ahi que la Maquina del Tiempo no mostrara nunca nada y que la copia a
     * Drive no encontrara que subir.
     *
     * Verificado en consola el 11 de septiembre de 2026.
     */
    private fun ficherosDe(raiz: java.io.File, nombreBase: String): List<java.io.File> {
        if (!raiz.exists()) return emptyList()
        val coincide = { f: java.io.File -> f.isFile && f.name.startsWith(nombreBase) }
        val enRaiz = raiz.listFiles()?.filter(coincide).orEmpty()
        val enCores = raiz.listFiles()?.filter { it.isDirectory }.orEmpty()
            .flatMap { carpeta -> carpeta.listFiles()?.filter(coincide).orEmpty() }
        return enRaiz + enCores
    }

    /**
     * Escapa un literal para la sintaxis de consulta de Drive.
     *
     * Sin esto, cualquier juego con apostrofo rompia la consulta: con
     * "Kirby's Block Ball" quedaba `name contains 'Kirby's Block Ball'`, que es
     * sintaxis invalida. Y hay bastantes: Link's Awakening, Zelda's…
     */
    private fun escaparDrive(texto: String): String =
        texto.replace("\\", "\\\\").replace("'", "\\'")

    /**
     * true si de verdad podemos mirar las carpetas de partidas de RetroArch.
     *
     * En Android 11+ un `java.io.File` sobre `/sdcard/RetroArch/` NO se puede
     * leer sin "Acceso a todos los archivos" (MANAGE_EXTERNAL_STORAGE), y esta
     * app **no lo declara ni lo tiene concedido**. Verificado en consola el 11
     * de septiembre de 2026: `appops` registra un rechazo en el instante exacto
     * de abrir la Maquina del Tiempo.
     *
     * Sin esto, `getLocalSaveStates()` devolvia una lista vacia y la interfaz
     * decia "no hay partidas guardadas", que es FALSO y ademas manda al usuario
     * a buscar el problema donde no esta. Con esto se puede distinguir "no hay
     * partidas" de "no puedo verlas".
     */
    fun puedeLeerPartidas(): Boolean =
        arbolRetroArch() != null || retroarchStatesDir.canRead() || retroarchSavesDir.canRead()

    // ─────────────────────────────────────────────────────────────────────
    // Acceso por SAF al arbol de RetroArch
    // ─────────────────────────────────────────────────────────────────────

    /** Arbol concedido por el usuario, o null si aun no ha elegido carpeta. */
    private fun arbolRetroArch(): DocumentFile? = runCatching {
        val uri = SettingsManager(context).retroarchFolderUri
        if (uri.isEmpty()) return null
        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.takeIf { it.canRead() }
    }.getOrNull()

    /**
     * Documentos de [nombreBase] dentro de `<arbol>/<subcarpeta>`, mirando
     * tambien una carpeta mas abajo (la del core).
     */
    private fun docsDe(subcarpeta: String, nombreBase: String): List<Pair<DocumentFile, String>> {
        val raiz = arbolRetroArch() ?: return emptyList()
        val dir = raiz.findFile(subcarpeta) ?: return emptyList()
        val coincide = { d: DocumentFile -> d.isFile && (d.name ?: "").startsWith(nombreBase) }

        // Pair(documento, subcarpeta del core) — "" si esta en la raiz.
        val enRaiz = dir.listFiles().filter(coincide).map { it to "" }
        val enCores = dir.listFiles().filter { it.isDirectory }
            .flatMap { carpeta ->
                carpeta.listFiles().filter(coincide).map { it to (carpeta.name ?: "") }
            }
        return enRaiz + enCores
    }

    /**
     * Ruta absoluta real de un documento SAF del almacenamiento primario.
     *
     * Nosotros no podemos abrir esa ruta, pero **RetroArch si**: es el dueño de
     * la carpeta. Por eso el save state se le sigue pasando como ruta en el
     * Intent, aunque aqui se lea y escriba por SAF.
     */
    private fun rutaRealDe(doc: DocumentFile): String? = runCatching {
        val docId = android.provider.DocumentsContract.getDocumentId(doc.uri)
        val partes = docId.split(":")
        if (partes.size < 2) return null
        when (partes[0]) {
            "primary" -> "${Environment.getExternalStorageDirectory()}/${partes[1]}"
            else -> "/storage/${partes[0]}/${partes[1]}"
        }
    }.getOrNull()

    /**
     * Escribe una partida bajada de Drive dentro del arbol SAF, en la
     * subcarpeta del core de la que salio, creandola si hace falta.
     *
     * Devuelve true solo si se ha escrito de verdad. Si el fichero local es
     * igual o mas nuevo que el de la nube no se toca, que es la misma regla que
     * en el camino por ruta directa.
     */
    private fun escribirPorSaf(
        raiz: DocumentFile,
        esState: Boolean,
        subcarpeta: String,
        nombre: String,
        modificadoEnNube: Long,
        volcar: (java.io.OutputStream) -> Unit
    ): Boolean = runCatching {
        val nombreBase = if (esState) "states" else "saves"
        val dirBase = raiz.findFile(nombreBase) ?: raiz.createDirectory(nombreBase) ?: return false
        val destino = if (subcarpeta.isBlank()) dirBase
            else (dirBase.findFile(subcarpeta) ?: dirBase.createDirectory(subcarpeta) ?: return false)

        val existente = destino.findFile(nombre)
        if (existente != null && existente.lastModified() >= modificadoEnNube) {
            Log.d(TAG, "⚡ El archivo local $nombre ya es la versión más reciente.")
            return false
        }

        val doc = existente ?: destino.createFile("application/octet-stream", nombre) ?: return false
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { salida ->
            volcar(salida)
        } ?: return false
        Log.d(TAG, "⬇️ Descargada partida más reciente: $nombre")
        true
    }.getOrElse {
        Log.e(TAG, "No se pudo escribir $nombre por SAF: ${it.message}")
        false
    }

    /** Copia un documento SAF a un fichero temporal, para poder subirlo a Drive. */
    private fun aTemporal(doc: DocumentFile, nombre: String): java.io.File? = runCatching {
        val destino = java.io.File(context.cacheDir, "nebula_$nombre")
        context.contentResolver.openInputStream(doc.uri)?.use { entrada ->
            FileOutputStream(destino).use { salida -> entrada.copyTo(salida) }
        } ?: return null
        destino
    }.getOrNull()

    suspend fun getLocalSaveStates(game: Game): List<SaveState> = withContext(Dispatchers.IO) {
        try {
            val gameFileNameBase = game.fileName.substringBeforeLast(".")

            // Via SAF: la unica que funciona en Android 11+ sin acceso a todos
            // los archivos. Si el usuario ha concedido la carpeta de RetroArch
            // se usa esta y no se toca `java.io.File` para nada.
            val docs = docsDe("states", gameFileNameBase)
                .filter { (d, _) ->
                    val n = d.name ?: ""
                    n.contains(".state") && !n.endsWith(".png")
                }
            if (docs.isNotEmpty()) {
                val raizDocs = docsDe("states", gameFileNameBase)
                return@withContext docs.mapNotNull { (doc, _) ->
                    val ruta = rutaRealDe(doc) ?: return@mapNotNull null
                    val nombrePng = "${doc.name}.png"
                    val miniatura = raizDocs.firstOrNull { it.first.name == nombrePng }?.first
                    SaveState(
                        file = java.io.File(ruta),
                        imageFile = null,
                        lastModified = doc.lastModified(),
                        imageUri = miniatura?.uri
                    )
                }.sortedByDescending { it.lastModified }
            }

            // Respaldo por ruta directa: solo sirve donde la app SI puede leer
            // el almacenamiento (ROM propia, flavor systemos, o con acceso a
            // todos los archivos concedido a mano).
            if (!retroarchStatesDir.exists()) return@withContext emptyList()

            val stateFiles = ficherosDe(retroarchStatesDir, gameFileNameBase)
                .filter { it.name.contains(".state") && !it.name.endsWith(".png") }
            if (stateFiles.isEmpty()) return@withContext emptyList()

            val results = mutableListOf<SaveState>()

            for (stateFile in stateFiles) {
                // Retroarch guarda la captura junto al propio .state, en su
                // misma carpeta, no en la raiz.
                val imageFile = java.io.File(stateFile.parentFile, "${stateFile.name}.png")
                results.add(
                    SaveState(
                        file = stateFile,
                        imageFile = if (imageFile.exists()) imageFile else null,
                        lastModified = stateFile.lastModified()
                    )
                )
            }
            
            // Los ordenamos de más reciente a más antiguo
            return@withContext results.sortedByDescending { it.lastModified }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error escaneando Save States locales", e)
            emptyList()
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account?.account == null) {
            Log.w(TAG, "No hay cuenta vinculada para Nebula Sync")
            return null
        }

        return try {
            val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
            credential.selectedAccount = account.account

            Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Speccy OS E5 Ultra")
                .build()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Drive Service", e)
            return null
        }
    }

    /**
     * ⬆️ UPLOAD: Sube los archivos locales más recientes a Google Drive
     */
    suspend fun backupGameSaves(game: Game): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        val gameFileNameBase = game.fileName.substringBeforeLast(".")
        var success = false

        try {
            // Camino SAF (el que funciona en Android 11+). Drive necesita un
            // java.io.File para subir, asi que el documento se copia antes a la
            // cache privada y se borra despues.
            val docs = docsDe("saves", gameFileNameBase)
                .filter { (d, _) -> (d.name ?: "").endsWith(".srm") } +
                docsDe("states", gameFileNameBase)
                    .filter { (d, _) -> (d.name ?: "").contains(".state") }

            if (docs.isNotEmpty()) {
                docs.forEach { (doc, subcarpeta) ->
                    val nombre = doc.name ?: return@forEach
                    val temporal = aTemporal(doc, nombre) ?: return@forEach
                    try {
                        subirConNombre(
                            service = service,
                            local = temporal,
                            nombreEnDrive = if (subcarpeta.isBlank()) nombre
                                else "$subcarpeta$SEPARADOR_RUTA$nombre",
                            modificadoLocal = doc.lastModified()
                        )
                        success = true
                    } finally {
                        temporal.delete()
                    }
                }
                if (success) Log.d(TAG, "✅ Partidas de ${game.title} subidas al Núcleo Nebula.")
                return@withContext success
            }

            // Respaldo por ruta directa, para donde si se puede leer el disco.
            val partidas = ficherosDe(retroarchSavesDir, gameFileNameBase)
                .filter { it.name.endsWith(".srm") } +
                ficherosDe(retroarchStatesDir, gameFileNameBase)
                    .filter { it.name.contains(".state") }

            partidas.forEach { fichero ->
                uploadFileToAppFolder(service, fichero, "application/octet-stream")
                success = true
            }

            if (success) Log.d(TAG, "✅ Partidas de ${game.title} subidas al Núcleo Nebula.")
            else Log.d(TAG, "ℹ️ No se encontraron partidas locales de ${game.title} para subir.")
            
            return@withContext success
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al subir partidas a Drive", e)
            return@withContext false
        }
    }

    /**
     * ⬇️ DOWNLOAD: Descarga los archivos de Drive si son piùs recenti de los locales
     */
    suspend fun restoreGameSaves(game: Game): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        val gameFileNameBase = game.fileName.substringBeforeLast(".")
        var success = false

        try {
            val query = "name contains '${escaparDrive(gameFileNameBase)}' and " +
                "'appDataFolder' in parents and trashed = false"
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, modifiedTime, size)")
                .execute()

            val cloudFiles = result.files ?: emptyList()
            
            if (cloudFiles.isEmpty()) {
                Log.d(TAG, "ℹ️ No hay partidas en la nube para ${game.title}.")
                return@withContext false
            }

            if (!retroarchSavesDir.exists()) retroarchSavesDir.mkdirs()
            if (!retroarchStatesDir.exists()) retroarchStatesDir.mkdirs()

            cloudFiles.forEach { cloudFile ->
                // El nombre en Drive lleva delante la subcarpeta del core, si
                // la habia: "Gambatte|Kirby's Block Ball (USA).state". Se
                // deshace aqui para devolver el fichero a la MISMA carpeta de
                // la que salio; si se dejara en la raiz, RetroArch no lo
                // encontraria (guarda ordenado por core).
                val subcarpeta = cloudFile.name.substringBefore(SEPARADOR_RUTA, "")
                val fileName = cloudFile.name.substringAfter(SEPARADOR_RUTA)
                val isState = fileName.contains(".state")

                // Camino SAF: escribir dentro del arbol concedido.
                val raizSaf = arbolRetroArch()
                if (raizSaf != null) {
                    val cloudModificado = cloudFile.modifiedTime?.value ?: 0L
                    if (escribirPorSaf(raizSaf, isState, subcarpeta, fileName, cloudModificado) { salida ->
                            service.files().get(cloudFile.id).executeMediaAndDownloadTo(salida)
                        }
                    ) {
                        success = true
                    }
                    return@forEach
                }

                val baseDir = if (isState) retroarchStatesDir else retroarchSavesDir
                val targetDir =
                    if (subcarpeta.isBlank()) baseDir else java.io.File(baseDir, subcarpeta)
                targetDir.mkdirs()
                val localFile = java.io.File(targetDir, fileName)

                val cloudModifiedTime = cloudFile.modifiedTime?.value ?: 0L
                val localModifiedTime = if (localFile.exists()) localFile.lastModified() else 0L

                if (cloudModifiedTime > localModifiedTime) {
                    Log.d(TAG, "⬇️ Descargando partida más reciente: $fileName")
                    FileOutputStream(localFile).use { outputStream ->
                        service.files().get(cloudFile.id).executeMediaAndDownloadTo(outputStream)
                    }
                    success = true
                } else {
                    Log.d(TAG, "⚡ El archivo local $fileName ya es la versión más reciente.")
                }
            }

            if (success) Log.d(TAG, "✅ Partidas de ${game.title} restauradas con éxito.")
            return@withContext success

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Una cancelacion no es un error: sin relanzarla, cerrar la
            // pantalla se registraba como fallo y la corrutina seguia
            // trabajando un rato mas, gastando bateria y red.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al descargar partidas de Drive", e)
            return@withContext false
        }
    }

    private fun uploadFileToAppFolder(service: Drive, localFile: java.io.File, mimeType: String) {
        // El appDataFolder de Drive es plano: no hay carpetas. Para no perder
        // la del core -y para que dos cores con una partida del mismo juego no
        // se pisen- se guarda delante del nombre.
        val subcarpeta = localFile.parentFile?.name.orEmpty()
        val esRaiz = subcarpeta == retroarchSavesDir.name || subcarpeta == retroarchStatesDir.name
        val fileName =
            if (esRaiz) localFile.name else "$subcarpeta$SEPARADOR_RUTA${localFile.name}"
        subirConNombre(service, localFile, fileName, localFile.lastModified())
    }

    /**
     * Sube [local] al appDataFolder con el nombre [nombreEnDrive].
     *
     * El nombre va aparte del fichero porque con SAF el contenido viaja en un
     * temporal de la cache, cuyo nombre no vale: el que importa es el original,
     * con la subcarpeta del core delante.
     */
    private fun subirConNombre(
        service: Drive,
        local: java.io.File,
        nombreEnDrive: String,
        modificadoLocal: Long
    ) {
        val fileName = nombreEnDrive
        val mimeType = "application/octet-stream"

        val query = "name = '${escaparDrive(fileName)}' and " +
            "'appDataFolder' in parents and trashed = false"
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id, modifiedTime)")
            .execute()

        val existingFile = result.files?.firstOrNull()
        
        val fileMetadata = File().apply {
            name = fileName
            parents = listOf("appDataFolder")
        }
        
        val mediaContent = FileContent(mimeType, local)

        if (existingFile != null) {
            val cloudModified = existingFile.modifiedTime?.value ?: 0L
            if (modificadoLocal <= cloudModified) {
                return
            }
            service.files().update(existingFile.id, null, mediaContent).execute()
        } else {
            service.files().create(fileMetadata, mediaContent).execute()
        }
    }
}
