package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * SpeccyBiosManager
 * ----------------------------------------------------------------------------
 * Gestor de BIOS real. Sustituye a `BiosManager`, que era código muerto y roto:
 *
 *  - Apuntaba a una ruta FIJA (`/storage/emulated/0/ROMS/BIOS`) que no tiene por
 *    qué existir y que, desde Android 11, `listFiles()` devuelve null sin
 *    MANAGE_EXTERNAL_STORAGE (permiso que la app no declara). Resultado: reportaba
 *    TODAS las BIOS como faltantes, siempre.
 *  - No verificaba hashes, así que una BIOS corrupta o de la región equivocada
 *    pasaba por buena y el emulador fallaba con un error incomprensible.
 *  - No tenía tabla de qué BIOS necesita cada sistema.
 *  - Y no lo llamaba nadie: cero referencias en todo el proyecto.
 *
 * Esta versión escanea por SAF (dentro del árbol de ROMs que el usuario ya
 * concedió), verifica por MD5 y sabe distinguir entre BIOS obligatoria y opcional.
 * Es la queja número uno de los usuarios de cualquier frontend retro.
 */
object SpeccyBiosManager {

    private const val TAG = "SpeccyBios"

    data class BiosFile(
        val fileName: String,
        val md5: String?,
        val required: Boolean,
        val note: String = ""
    )

    data class BiosStatus(
        val systemId: String,
        val present: List<String>,
        val missingRequired: List<BiosFile>,
        val missingOptional: List<BiosFile>,
        val corrupt: List<String>
    ) {
        val isPlayable: Boolean get() = missingRequired.isEmpty() && corrupt.isEmpty()
        val summary: String
            get() = when {
                corrupt.isNotEmpty() -> "${corrupt.size} BIOS con hash incorrecto"
                missingRequired.isNotEmpty() -> "Faltan ${missingRequired.size} BIOS obligatorias"
                missingOptional.isNotEmpty() -> "Listo (faltan ${missingOptional.size} opcionales)"
                else -> "Todas las BIOS presentes"
            }
    }

    /**
     * Requisitos por sistema. Los MD5 son los canónicos de las bases de datos
     * públicas de RetroArch / Redump. `null` = no se verifica el hash, sólo la
     * presencia (casos en los que existen variantes legítimas).
     */
    private val REQUIREMENTS: Map<String, List<BiosFile>> = mapOf(
        "psx" to listOf(
            BiosFile("scph5501.bin", "490f666e1afb15b7362b406ed1cea246", true, "NTSC-U"),
            BiosFile("scph5500.bin", "8dd7d5296a650fac7319bce665a6a53c", false, "NTSC-J"),
            BiosFile("scph5502.bin", "32736f17079d0b2b7024407c39bd3050", false, "PAL")
        ),
        "ps2" to listOf(
            BiosFile("SCPH-70012_BIOS_V12_USA_200.BIN", null, true, "Cualquier BIOS de PS2 válida")
        ),
        "saturn" to listOf(
            BiosFile("sega_101.bin", "85ec9ca47d8f6807718151cbcca8b964", true, "NTSC-J"),
            BiosFile("mpr-17933.bin", "3240872c70984b6cbfda1586cab68dbe", true, "NTSC-U / PAL")
        ),
        "segacd" to listOf(
            BiosFile("bios_CD_U.bin", "854b9150240a198070150e4566ae1290", true, "Mega CD USA"),
            BiosFile("bios_CD_E.bin", "e66fa1dc5820d254611fdcdba0662372", false, "Mega CD Europa"),
            BiosFile("bios_CD_J.bin", "278a9397d192149e84e820ac621a8edd", false, "Mega CD Japón")
        ),
        "megacd" to listOf(
            BiosFile("bios_CD_E.bin", "e66fa1dc5820d254611fdcdba0662372", true, "Mega CD Europa")
        ),
        "pcenginecd" to listOf(
            BiosFile("syscard3.pce", "38179df8f4ac870017db21ebcbf53114", true, "System Card 3.0")
        ),
        "tg-cd" to listOf(
            BiosFile("syscard3.pce", "38179df8f4ac870017db21ebcbf53114", true, "System Card 3.0")
        ),
        "3do" to listOf(
            BiosFile("panafz10.bin", "51f2f43ae2f3508a14d9f56597e2d3ce", true, "Panasonic FZ-10")
        ),
        "neogeo" to listOf(
            BiosFile("neogeo.zip", null, true, "BIOS set de Neo Geo")
        ),
        "neogeocd" to listOf(
            BiosFile("neocd.bin", null, true, "Neo Geo CD")
        ),
        "gba" to listOf(
            BiosFile("gba_bios.bin", "a860e8c0b6d573d191e4ec7db1b1e4f6", false, "Mejora la compatibilidad")
        ),
        "nds" to listOf(
            BiosFile("bios7.bin", "df692a80a5b1bc90728bc3dfc76cd948", true, "ARM7"),
            BiosFile("bios9.bin", "a392174eb3e572fed6447e956bde4b25", true, "ARM9"),
            BiosFile("firmware.bin", null, true, "Firmware DS")
        ),
        "dreamcast" to listOf(
            BiosFile("dc_boot.bin", "e10c53c2f8b90bab96ead2d368858623", true, "Boot ROM"),
            BiosFile("dc_flash.bin", "0a93f7940c455905bea6e392dfde92a4", true, "Flash")
        ),
        "naomi" to listOf(
            BiosFile("naomi.zip", null, true, "BIOS set de NAOMI")
        ),
        "atomiswave" to listOf(
            BiosFile("awbios.zip", null, true, "BIOS set de Atomiswave")
        ),
        "pcfx" to listOf(
            BiosFile("pcfx.rom", "08e36edbea28a017f79f8d4f7ff9b6d7", true)
        ),
        "x68000" to listOf(
            BiosFile("iplrom.dat", "7fd4caabac1d9169e289f0f7bbf71d8e", true),
            BiosFile("cgrom.dat", "cb0a5cfcf7247a7eab74bb2716260269", true)
        ),
        "amiga" to listOf(
            BiosFile("kick34005.A500", "82a21c1890cae844b3df741f2762d48d", true, "Kickstart 1.3"),
            BiosFile("kick40068.A1200", "646773759326fbac3b2311fd8c8793ee", false, "Kickstart 3.1")
        ),
        "amigacd32" to listOf(
            BiosFile("kick40060.CD32", "5f8924d013dd57a89cf349f4cdedc6b1", true)
        ),
        "msx2" to listOf(
            BiosFile("MSX2.ROM", null, true),
            BiosFile("MSX2EXT.ROM", null, true)
        ),
        "colecovision" to listOf(
            BiosFile("colecovision.rom", "2c66f5911e5b42b8ebe113403548eee7", true)
        ),
        "atari5200" to listOf(
            BiosFile("5200.rom", "281f20ea4320404ec820fb7ec0693b38", true)
        ),
        "atari7800" to listOf(
            BiosFile("7800 BIOS (U).rom", null, false)
        ),
        "fds" to listOf(
            BiosFile("disksys.rom", "ca30b50f880eb660a320674ed365ef7a", true)
        ),
        "psp" to listOf(
            BiosFile("ppge_atlas.zim", null, false, "Sólo para fuentes personalizadas")
        )
    )

    /** Carpetas donde la gente guarda las BIOS, en orden de probabilidad. */
    private val BIOS_FOLDER_NAMES = listOf("bios", "BIOS", "system", "System", "firmware")

    /**
     * Caché por sesión: escanear el árbol SAF es caro.
     *
     * ConcurrentHashMap y no mutableMapOf: check() corre en Dispatchers.IO, que es
     * un pool de varios hilos, y dos comprobaciones de plataformas distintas a la
     * vez escribian sin sincronizar sobre el mismo HashMap.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, BiosStatus>()
    @Volatile private var indexedFolder: Map<String, DocumentFile>? = null

    fun invalidate() {
        cache.clear()
        indexedFolder = null
    }

    fun requirementsFor(systemId: String): List<BiosFile> =
        REQUIREMENTS[systemId.lowercase()] ?: emptyList()

    fun needsBios(systemId: String): Boolean = requirementsFor(systemId).any { it.required }

    /**
     * Comprueba el estado de las BIOS de un sistema.
     * @param romsTreeUri árbol SAF que el usuario concedió para sus ROMs.
     * @param verifyHashes leer y hashear ficheros es caro; se hace sólo al pedirlo.
     */
    suspend fun check(
        context: Context,
        systemId: String,
        romsTreeUri: Uri?,
        verifyHashes: Boolean = false
    ): BiosStatus = withContext(Dispatchers.IO) {
        val id = systemId.lowercase()
        val required = requirementsFor(id)
        if (required.isEmpty()) {
            return@withContext BiosStatus(id, emptyList(), emptyList(), emptyList(), emptyList())
        }
        // La cache solo vale si se calculo CON arbol SAF: si la primera llamada
        // llego sin el (romsLocation vacio), el resultado vacio se servia para
        // siempre y toda BIOS quedaba marcada como ausente.
        cache[id]?.let { if (!verifyHashes && romsTreeUri != null) return@withContext it }

        val index = indexBiosFolder(context, romsTreeUri)
        val present = mutableListOf<String>()
        val missingRequired = mutableListOf<BiosFile>()
        val missingOptional = mutableListOf<BiosFile>()
        val corrupt = mutableListOf<String>()

        for (bios in required) {
            val doc = index[bios.fileName.lowercase()]
            if (doc == null) {
                if (bios.required) missingRequired += bios else missingOptional += bios
                continue
            }
            if (verifyHashes && bios.md5 != null) {
                val actual = md5Of(context, doc.uri)
                if (actual != null && !actual.equals(bios.md5, ignoreCase = true)) {
                    corrupt += "${bios.fileName} (md5 $actual, esperado ${bios.md5})"
                    continue
                }
            }
            present += bios.fileName
        }

        val status = BiosStatus(id, present, missingRequired, missingOptional, corrupt)
        cache[id] = status
        Log.i(TAG, "BIOS $id: ${status.summary}")
        status
    }

    /**
     * Indexa la carpeta de BIOS UNA vez por sesión.
     * Se busca dentro del árbol de ROMs concedido por el usuario: nada de rutas
     * absolutas que Android 11+ bloquea.
     */
    private fun indexBiosFolder(context: Context, romsTreeUri: Uri?): Map<String, DocumentFile> {
        indexedFolder?.let { return it }
        val result = mutableMapOf<String, DocumentFile>()
        try {
            val root = romsTreeUri?.let { DocumentFile.fromTreeUri(context, it) }
            val biosDir = BIOS_FOLDER_NAMES.firstNotNullOfOrNull { name ->
                root?.findFile(name)?.takeIf { it.isDirectory }
            }
            biosDir?.listFiles()?.forEach { f ->
                f.name?.let { result[it.lowercase()] = f }
            }
            // Muchos usuarios reparten las BIOS por subcarpetas de sistema.
            biosDir?.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                sub.listFiles().forEach { f -> f.name?.let { result.putIfAbsent(it.lowercase(), f) } }
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se ha podido indexar la carpeta BIOS", e)
        }
        // Mismo motivo: no cachear un indice vacio obtenido sin permiso de carpeta.
        if (romsTreeUri != null && result.isNotEmpty()) indexedFolder = result
        return result
    }

    private fun md5Of(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(65536)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        null
    }
}
