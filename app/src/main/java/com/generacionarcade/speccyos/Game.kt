package com.generacionarcade.speccyos

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore

/**
 * ESTABILIDAD EN COMPOSE.
 *
 * Las 22 propiedades de abajo estaban declaradas como `var`. Con al menos un `var`
 * público, Compose marca la clase como INESTABLE: no puede comparar por `equals`
 * para saltarse una recomposición, así que cualquier emisión de Room recomponía
 * la lista de juegos entera aunque el contenido fuese idéntico. Es la segunda
 * causa de jank al navegar la biblioteca.
 *
 * Room soporta perfectamente propiedades `val`, y en todo el proyecto no había ni
 * una sola escritura directa a estos campos: las actualizaciones ya se hacían con
 * `copy()`. El cambio es puramente de estabilidad, sin efectos funcionales.
 */
@Entity(
    tableName = "games",
    indices = [
        Index("platformId"),
        Index("isFavorite"),
        Index("lastPlayed"),
        Index("playCount"),
        Index("playTimeSeconds"),
        // Index("boxArt") ELIMINADO: era un índice sobre un TEXT con URIs SAF
        // largas. Inflaba la base de datos y ralentizaba las inserciones masivas
        // del escaneo sin acelerar ninguna consulta real.
        Index("raGameId"),
        Index(value = ["platformId", "title"])
    ]
)
data class Game(
    @PrimaryKey val path: String,
    val title: String,
    val platformId: String,
    val extension: String,
    val fileName: String,
    val boxArt: String? = null,
    val videoPreview: String? = null,
    val wheel: String? = null,
    val fanart: String? = null,
    val cdArt: String? = null,
    val screenshot: String? = null,
    val description: String? = null,
    val developer: String? = "Desconocido",
    val genre: String? = "Retro",
    val releaseDate: String? = "N/A",
    val rating: Float = 0f,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val isFavorite: Boolean = false,
    val playTimeSeconds: Long = 0,
    val md5: String? = null,
    val crc32: String? = null,
    val sha1: String? = null,
    val raGameId: Int = 0,
    val raAchievementsTotal: Int = 0,
    val raAchievementsEarned: Int = 0,
    val raLastSync: Long = 0
) {
    @get:Ignore
    val name: String get() = title

    @get:Ignore
    val year: String? get() = releaseDate?.take(4)?.takeIf { it.all { c -> c.isDigit() } }

    @get:Ignore
    val formattedPlayTime: String get() {
        if (playTimeSeconds < 60) return "< 1m"
        val hours   = playTimeSeconds / 3600
        val minutes = (playTimeSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    @get:Ignore
    val hasMedia: Boolean get() = boxArt != null || videoPreview != null || screenshot != null
}
