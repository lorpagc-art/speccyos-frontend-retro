package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

object HashUtility {

    /**
     * SHA-256 en hexadecimal. Para claves de cache donde una colision se traduce
     * en servir el contenido equivocado sin que salte ningun error.
     */
    fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Calcula el MD5 y CRC32 de un archivo a ultra velocidad.
     * Buffer optimizado para SoCs modernos (64KB).
     */
    fun calculateHashes(context: Context, uri: Uri): Pair<String?, String?> {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return Pair(null, null)

            val md5Digest = MessageDigest.getInstance("MD5")
            val crc32Digest = CRC32()
            // Buffer de 64KB para máxima velocidad de lectura
            val buffer = ByteArray(65536) 
            var bytesRead: Int

            inputStream.use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md5Digest.update(buffer, 0, bytesRead)
                    crc32Digest.update(buffer, 0, bytesRead)
                }
            }

            val md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }
            val crc32 = "%08x".format(crc32Digest.value).uppercase()

            Pair(md5, crc32)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }
}
