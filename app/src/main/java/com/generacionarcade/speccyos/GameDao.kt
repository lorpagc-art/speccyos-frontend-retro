/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title ASC")
    fun getAllGames(): Flow<List<Game>>

    @Query("SELECT * FROM games ORDER BY title ASC")
    suspend fun getAllGamesList(): List<Game>

    @Query("SELECT * FROM games")
    fun getAllGamesSync(): List<Game>

    @Query("SELECT * FROM games WHERE developer != 'Desconocido' AND description IS NOT NULL ORDER BY RANDOM() LIMIT 20")
    suspend fun getRandomSampleForAi(): List<Game>

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId")
    suspend fun getGameCountByPlatform(platformId: String): Int

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getGameCountSync(): Int

    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY title ASC")
    fun getGamesByPlatform(platformId: String): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY title ASC")
    suspend fun getGamesByPlatformList(platformId: String): List<Game>

    @Query("SELECT DISTINCT platformId FROM games")
    fun getActivePlatformIds(): Flow<List<String>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteGames(): Flow<List<Game>>

    @Query("SELECT COUNT(*) FROM games WHERE isFavorite = 1")
    suspend fun getFavoriteCountSync(): Int

    @Query("SELECT * FROM games WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 15")
    fun getRecentGames(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE path = :path LIMIT 1")
    suspend fun getGameByPath(path: String): Game?

    @Query("SELECT * FROM games WHERE videoPreview IS NOT NULL ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomGameWithVideo(): Game?

    // ── Búsqueda en tiempo real ──────────────────────────────────
    @Query("""
        SELECT * FROM games
        WHERE title LIKE :query OR developer LIKE :query OR genre LIKE :query
        ORDER BY
            CASE WHEN title LIKE :query THEN 0 ELSE 1 END,
            playCount DESC,
            title ASC
        LIMIT 100
    """)
    fun searchGames(query: String): Flow<List<Game>>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId AND (title LIKE :query OR developer LIKE :query)
        ORDER BY title ASC LIMIT 50
    """)
    suspend fun searchInPlatform(platformId: String, query: String): List<Game>

    // ── Top jugados ──────────────────────────────────────────────
    @Query("SELECT * FROM games WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayedGames(limit: Int = 10): List<Game>

    @Query("SELECT * FROM games WHERE playCount > 0 AND platformId = :platformId ORDER BY playCount DESC LIMIT 5")
    suspend fun getTopPlayedByPlatform(platformId: String): List<Game>

    // ── Tiempo de juego ──────────────────────────────────────────
    @Query("SELECT SUM(playTimeSeconds) FROM games")
    suspend fun getTotalPlayTimeSeconds(): Long?

    @Query("SELECT SUM(playTimeSeconds) FROM games WHERE platformId = :platformId")
    suspend fun getPlayTimeByPlatform(platformId: String): Long?

    @Query("UPDATE games SET playTimeSeconds = playTimeSeconds + :seconds, lastPlayed = :timestamp WHERE path = :path")
    suspend fun addPlayTime(path: String, seconds: Long, timestamp: Long = System.currentTimeMillis())

    // ── Plataformas con más tiempo de juego ──────────────────────
    @Query("""
        SELECT platformId, SUM(playTimeSeconds) as totalTime
        FROM games WHERE playTimeSeconds > 0
        GROUP BY platformId ORDER BY totalTime DESC
    """)
    suspend fun getPlatformsByPlayTime(): List<PlatformPlayTime>

    // ── Scraping stats ───────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM games WHERE boxArt IS NOT NULL")
    fun getScrapedGamesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE boxArt IS NULL")
    fun getMissingMediaCount(): Flow<Int>

    @Query("SELECT platformId, COUNT(*) as total, SUM(CASE WHEN boxArt IS NOT NULL THEN 1 ELSE 0 END) as scraped FROM games GROUP BY platformId")
    fun getScrapingStatsByPlatform(): Flow<List<ScrapingStat>>

    @Query("SELECT platformId, COUNT(*) as count FROM games GROUP BY platformId")
    suspend fun getPlatformCounts(): List<PlatformCount>

    // ── Limpieza de medios obsoletos ─────────────────────────────
    @Query("SELECT * FROM games WHERE boxArt IS NOT NULL")
    suspend fun getGamesWithBoxArt(): List<Game>

    @Query("UPDATE games SET boxArt = NULL WHERE path = :gamePath")
    suspend fun clearBoxArt(gamePath: String)

    @Query("UPDATE games SET videoPreview = NULL WHERE path = :gamePath")
    suspend fun clearVideoPreview(gamePath: String)

    @Query("UPDATE games SET videoPreview = NULL")
    suspend fun clearAllVideoPaths()

    // ── CRUD ─────────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<Game>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(games: List<Game>)

    @Update
    suspend fun updateGame(game: Game)

    /**
     * ACTUALIZACIÓN EN LOTE.
     *
     * ScraperWorker hacía `updateGame(g)` fila a fila dentro de un bucle sobre
     * toda la biblioteca: con 20.000 juegos son 20.000 transacciones SQLite
     * independientes, cada una con su fsync incluso en modo WAL. Room envuelve
     * un @Update con lista en UNA sola transacción.
     */
    @Update
    suspend fun updateGames(games: List<Game>)

    // ─────────────────────────────────────────────────────────────────────────
    // AÑADIDO EN LA AUDITORÍA DE AGOSTO 2026
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * "Un día como hoy", filtrado en SQL.
     *
     * Antes se traía la tabla ENTERA a memoria con getAllGames() y se le aplicaba
     * un Regex por juego en el contexto del colector — es decir, en el hilo
     * principal, en cada cambio de la tabla, también durante el escaneo.
     */
    @Query(
        "SELECT * FROM games " +
        "WHERE releaseDate LIKE :isoPattern OR releaseDate LIKE :euroPattern " +
        "ORDER BY releaseDate ASC LIMIT 60"
    )
    suspend fun getGamesReleasedOn(isoPattern: String, euroPattern: String): List<Game>

    /** Un juego al azar, sin traerse la biblioteca entera a memoria. */
    @Query("SELECT * FROM games ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomGame(): Game?

    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomGameByPlatform(platformId: String): Game?

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomFavorite(): Game?

    /**
     * PAGINACIÓN.
     *
     * `getGamesByPlatform` devuelve la lista completa: con una colección MAME de
     * 30.000 ROMs son decenas de MB de objetos Game con descripciones largas, y
     * la UI además la duplicaba al filtrar por idioma. Riesgo real de OOM en
     * equipos de 2 GB. Con PagingSource sólo vive en memoria lo que se ve.
     */
    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY title ASC")
    fun pagingGamesByPlatform(platformId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun pagingAllGames(): PagingSource<Int, Game>

    // ── Ordenación y filtros avanzados (paridad con ES-DE / Pegasus) ─────────
    // Antes sólo se podía ordenar por título ascendente.
    @Query(
        "SELECT * FROM games " +
        "WHERE (:platformId IS NULL OR platformId = :platformId) " +
        "  AND (:genre      IS NULL OR genre LIKE '%' || :genre || '%') " +
        "  AND (:developer  IS NULL OR developer LIKE '%' || :developer || '%') " +
        // Game.releaseDate vale "N/A" por defecto: sin esta salvaguarda, en cuanto
        // el usuario tocaba el filtro de anos desaparecia casi toda una biblioteca
        // sin scrapear, sin ninguna indicacion de por que.
        "  AND (:yearFrom   IS NULL OR releaseDate IS NULL OR CAST(substr(releaseDate, 1, 4) AS INTEGER) = 0 OR CAST(substr(releaseDate, 1, 4) AS INTEGER) >= :yearFrom) " +
        "  AND (:yearTo     IS NULL OR releaseDate IS NULL OR CAST(substr(releaseDate, 1, 4) AS INTEGER) = 0 OR CAST(substr(releaseDate, 1, 4) AS INTEGER) <= :yearTo) " +
        "  AND (:onlyFavorites = 0 OR isFavorite = 1) " +
        "  AND (:onlyPlayed    = 0 OR playCount > 0) " +
        "  AND (:onlyWithMedia = 0 OR boxArt IS NOT NULL) " +
        "ORDER BY " +
        "  CASE WHEN :sort = 'title_desc'  THEN title END DESC, " +
        "  CASE WHEN :sort = 'recent'      THEN lastPlayed END DESC, " +
        "  CASE WHEN :sort = 'most_played' THEN playCount END DESC, " +
        "  CASE WHEN :sort = 'play_time'   THEN playTimeSeconds END DESC, " +
        "  CASE WHEN :sort = 'rating'      THEN rating END DESC, " +
        "  CASE WHEN :sort = 'year_desc'   THEN releaseDate END DESC, " +
        "  CASE WHEN :sort = 'year_asc'    THEN releaseDate END ASC, " +
        "  title ASC"
    )
    fun filterGames(
        platformId: String?,
        genre: String?,
        developer: String?,
        yearFrom: Int?,
        yearTo: Int?,
        onlyFavorites: Int,
        onlyPlayed: Int,
        onlyWithMedia: Int,
        sort: String
    ): PagingSource<Int, Game>

    @Query("SELECT DISTINCT genre FROM games WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    suspend fun getAllGenres(): List<String>

    @Query("SELECT DISTINCT developer FROM games WHERE developer IS NOT NULL AND developer != 'Desconocido' ORDER BY developer ASC")
    suspend fun getAllDevelopers(): List<String>

    /** Juegos aún sin hash calculado: los consume el indexado para RetroAchievements. */
    @Query("SELECT * FROM games WHERE crc32 IS NULL LIMIT :limit")
    suspend fun getGamesWithoutHash(limit: Int): List<Game>

    @Query("UPDATE games SET crc32 = :crc, md5 = :md5 WHERE path = :path")
    suspend fun setHashes(path: String, crc: String?, md5: String?)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}

data class PlatformCount(val platformId: String, val count: Int)
data class ScrapingStat(val platformId: String, val total: Int, val scraped: Int)
data class PlatformPlayTime(val platformId: String, val totalTime: Long)
