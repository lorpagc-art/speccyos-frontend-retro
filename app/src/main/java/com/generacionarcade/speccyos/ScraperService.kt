package com.generacionarcade.speccyos

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

class ScraperService(private val context: Context) {

    private val TAG = "ScraperService"
    
    // Extensiones soportadas
    private val IMG_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp")
    private val VID_EXTENSIONS = listOf(".mp4", ".mkv", ".avi", ".webm")

    // Carpetas estándar (ES-DE style y personalizadas)
    private val IMG_SUBFOLDERS = listOf("images", "box2d", "box3d", "screenshots", "mixrb", "covers")
    private val VID_SUBFOLDERS = listOf("videos", "previews", "movies")

    /**
     * Busca media local para un juego.
     * Soporta archivos de ROMs y directorios (Ports).
     */
    fun findLocalMediaForGame(game: Game, romsRootUri: String?): Pair<String?, String?> {
        var boxArt: String? = null
        var video: String? = null

        try {
            val platformId = game.platformId.lowercase()
            val gameFileName = game.fileName
            
            // Si es un puerto (.dir), el nombre base es el nombre de la carpeta completo
            val rawName = if (game.extension == ".dir") {
                gameFileName
            } else {
                gameFileName.substringBeforeLast(".")
            }
            
            val gameRealPath = translateSafToRealPath(game.path) ?: return Pair(null, null)
            val gameFile = File(gameRealPath)
            
            // Si es un puerto, el padre de la carpeta es la carpeta 'ports'
            // Si es una ROM, el padre del archivo es la carpeta del sistema
            val platformDir = if (game.extension == ".dir") gameFile.parentFile else gameFile.parentFile
            val romsRootDir = if (romsRootUri != null) File(translateSafToRealPath(romsRootUri) ?: "") else null

            // --- ESTRATEGIA DE BÚSQUEDA ---
            val imageBaseDirs = mutableListOf<File>()
            val videoBaseDirs = mutableListOf<File>()

            // 1. Ruta interna del sistema: [roms]/[platform]/media/
            platformDir?.let {
                imageBaseDirs.add(File(it, "media"))
                videoBaseDirs.add(File(it, "media"))
                
                // Caso especial para Ports: a veces la media está DENTRO de la carpeta del juego
                if (game.extension == ".dir") {
                    imageBaseDirs.add(File(gameFile, "media"))
                    videoBaseDirs.add(File(gameFile, "media"))
                }
            }

            // 2. Ruta raíz centralizada: [roms]/media/[platform]/
            romsRootDir?.let {
                imageBaseDirs.add(File(it, "media/$platformId"))
                imageBaseDirs.add(File(it, "ES-DE/downloaded_media/$platformId"))
                videoBaseDirs.add(File(it, "media/$platformId"))
                videoBaseDirs.add(File(it, "ES-DE/downloaded_media/$platformId"))
            }
            
            boxArt = searchMedia(imageBaseDirs, IMG_SUBFOLDERS, rawName, IMG_EXTENSIONS)
            video = searchMedia(videoBaseDirs, VID_SUBFOLDERS, rawName, VID_EXTENSIONS)

        } catch (e: Exception) {
            Log.e(TAG, "Media search error for ${game.title}", e)
        }
        
        return Pair(boxArt, video)
    }

    private fun searchMedia(baseDirs: List<File>, subFolders: List<String>, fileName: String, extensions: List<String>): String? {
        for (base in baseDirs) {
            if (!base.exists() || !base.isDirectory) continue
            
            // Buscar en subcarpetas (images, videos, etc)
            for (sub in subFolders) {
                val folder = File(base, sub)
                if (folder.exists() && folder.isDirectory) {
                    val result = checkFolderForMedia(folder, fileName, extensions)
                    if (result != null) return result
                }
            }
            
            // También buscar directamente en la carpeta base (algunos setups lo tienen así)
            val directResult = checkFolderForMedia(base, fileName, extensions)
            if (directResult != null) return directResult
        }
        return null
    }

    private fun checkFolderForMedia(folder: File, fileName: String, extensions: List<String>): String? {
        for (ext in extensions) {
            // 1. Nombre exacto
            val file = File(folder, "$fileName$ext")
            if (file.exists()) return Uri.fromFile(file).toString()
            
            // 2. Case-insensitive fallback (común en Linux/Android con ports)
            val files = folder.listFiles()
            val insensitiveMatch = files?.find { it.name.equals("$fileName$ext", ignoreCase = true) }
            if (insensitiveMatch != null) return Uri.fromFile(insensitiveMatch).toString()

            // 3. Sufijos comunes (-image, -video, -thumb)
            val suffix = if (IMG_EXTENSIONS.contains(ext)) "-image" else "-video"
            val fallback = File(folder, "$fileName$suffix$ext")
            if (fallback.exists()) return Uri.fromFile(fallback).toString()
        }
        return null
    }

    private fun translateSafToRealPath(uriStr: String): String? {
        if (uriStr.startsWith("/")) return uriStr
        if (!uriStr.startsWith("content://")) return null
        
        return try {
            val uri = Uri.parse(uriStr)
            val documentId = DocumentsContract.getDocumentId(uri)
            val split = documentId.split(":")
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                "/storage/emulated/0/" + split[1]
            } else {
                "/storage/$type/" + split[1]
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun findLocalArtForGame(game: Game): String? = findLocalMediaForGame(game, null).first
}
