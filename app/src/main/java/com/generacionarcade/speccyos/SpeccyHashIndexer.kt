/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/**
 * SpeccyHashIndexer
 * ----------------------------------------------------------------------------
 * Calcula e indexa los hashes de la biblioteca.
 *
 * POR QUE HACIA FALTA
 * -------------------
 * `HashUtility` existia pero NO LO LLAMABA NADIE: cero referencias en todo el
 * proyecto. Como consecuencia `Game.md5`, `crc32` y `sha1` eran siempre null,
 * `RetroAchievementsManager.getGameIdByHash()` salia inmediatamente por
 * `md5.isEmpty()` y `raGameId` se quedaba en 0. Es decir: RetroAchievements no
 * podia funcionar nunca, por mucho que el usuario configurase su cuenta.
 *
 * Ademas, el MD5 del fichero completo NO es el algoritmo de RetroAchievements:
 *  - NES: se salta la cabecera iNES de 16 bytes.
 *  - Arcade: se usa el NOMBRE del set, no su contenido.
 *  - El resto de sistemas de cartucho: fichero completo.
 *
 * El CRC32 se lee ademas del propio indice del ZIP cuando la ROM esta
 * comprimida, sin descomprimir nada: es practicamente gratis y es lo que
 * necesitan los DAT de No-Intro y Redump para dar el titulo canonico.
 */
object SpeccyHashIndexer {

    private const val TAG = "SpeccyHash"

    /** Sistemas cuyo hash de RetroAchievements se calcula sobre el nombre del set. */
    private val ARCADE_SYSTEMS = setOf(
        "arcade", "mame", "fbneo", "fba", "neogeo", "cps", "cps1", "cps2", "cps3"
    )

    data class Hashes(val crc32: String?, val md5: String?)

    /**
     * Indexa por lotes los juegos que aun no tienen hash.
     * Pensado para llamarse desde un Worker o tras el escaneo, nunca en la UI.
     */
    suspend fun indexPending(
        context: Context,
        dao: GameDao,
        batchSize: Int = 200,
        onProgress: suspend (done: Int) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var total = 0
        while (true) {
            val pending = runCatching { dao.getGamesWithoutHash(batchSize) }.getOrDefault(emptyList())
            if (pending.isEmpty()) break
            for (game in pending) {
                val h = hashesFor(context, game)
                // Se escribe siempre, aunque salga vacio: si no, el mismo juego se
                // reintentaria en cada pasada para siempre.
                val written = runCatching { dao.setHashes(game.path, h.crc32 ?: "", h.md5) }.isSuccess
                if (!written) {
                    // Si la escritura falla de forma persistente, getGamesWithoutHash
                    // devolveria siempre el mismo lote completo y el while(true) giraria
                    // para siempre martilleando la base de datos.
                    Log.e(TAG, "No se ha podido guardar el hash de ${game.fileName}; se aborta el indexado.")
                    return@withContext total
                }
                total++
            }
            onProgress(total)
            if (pending.size < batchSize) break
        }
        Log.i(TAG, "Hashes calculados para $total juegos.")
        total
    }

    fun hashesFor(context: Context, game: Game): Hashes {
        val uri = runCatching { Uri.parse(game.path) }.getOrNull() ?: return Hashes(null, null)
        val platform = game.platformId.lowercase()

        // Arcade: el identificador es el nombre del set, no el contenido.
        if (platform in ARCADE_SYSTEMS) {
            val setName = game.fileName.substringBeforeLast('.').lowercase()
            return Hashes(null, md5OfString(setName))
        }

        val isZip = game.extension.equals(".zip", true)
        val crcFromIndex = if (isZip) crcFromZipIndex(context, uri) else null

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // RetroAchievements hashea la ROM DESCOMPRIMIDA. Si abriamos el .zip
                // directamente estabamos hasheando el contenedor, con lo que el md5
                // no coincidia con su base de datos para NINGUN juego comprimido
                // (que en una biblioteca retro son la mayoria), y detectINesHeader
                // veia la firma "PK" del zip en vez de la cabecera iNES.
                val raw = if (isZip) {
                    val zip = ZipInputStream(stream)
                    var entry = zip.nextEntry
                    while (entry != null && entry.isDirectory) entry = zip.nextEntry
                    if (entry == null) return Hashes(crcFromIndex, null)
                    zip
                } else stream

                // Cabeceras que RetroAchievements descarta antes de hashear.
                val skip = if (platform == "nes" || platform == "famicom") {
                    detectINesHeader(context, uri, isZip)
                } else 0
                // InputStream.skip puede saltar MENOS bytes de los pedidos: si no se
                // insiste, parte de la cabecera entra en el digest y el hash no sirve.
                var remaining = skip.toLong()
                while (remaining > 0) {
                    val skipped = raw.skip(remaining)
                    if (skipped <= 0) { if (raw.read() < 0) break else remaining-- }
                    else remaining -= skipped
                }

                val md5 = MessageDigest.getInstance("MD5")
                val crc32 = CRC32()
                val buffer = ByteArray(65536)
                var read = raw.read(buffer)
                while (read != -1) {
                    md5.update(buffer, 0, read)
                    crc32.update(buffer, 0, read)
                    read = raw.read(buffer)
                }
                Hashes(
                    crc32 = crcFromIndex ?: "%08x".format(crc32.value).uppercase(),
                    md5 = md5.digest().joinToString("") { "%02x".format(it) }
                )
            } ?: Hashes(crcFromIndex, null)
        } catch (e: Exception) {
            Log.w(TAG, "No se ha podido hashear ${game.fileName}: ${e.message}")
            Hashes(crcFromIndex, null)
        }
    }

    /**
     * CRC32 leido del indice del ZIP. No descomprime: el ZIP ya lo lleva escrito
     * en su cabecera, asi que el coste es de milisegundos incluso en ROMs grandes.
     */
    private fun crcFromZipIndex(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var best: Long? = null
                while (entry != null) {
                    if (!entry.isDirectory && entry.crc != -1L && best == null) {
                        best = entry.crc
                    }
                    entry = zip.nextEntry
                }
                best?.let { "%08x".format(it).uppercase() }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** iNES: cabecera de 16 bytes con la firma "NES" seguida de 0x1A. */
    private fun detectINesHeader(context: Context, uri: Uri, insideZip: Boolean): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val input = if (insideZip) {
                val zip = ZipInputStream(stream)
                var entry = zip.nextEntry
                while (entry != null && entry.isDirectory) entry = zip.nextEntry
                if (entry == null) null else zip
            } else stream
            if (input == null) 0 else {
                val head = ByteArray(4)
                val n = input.read(head)
                val magic = byteArrayOf(0x4E, 0x45, 0x53, 0x1A)
                if (n == 4 && head.contentEquals(magic)) 16 else 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    private fun md5OfString(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
