/*
 * Speccy OS — frontend retro para Android
 * Copyright (c) 2026 LV-Webstudio · lv-webstudio.com
 * Desarrollado por Speccy81 (LORPAGC) · administracion@lv-webstudio.com
 * Todos los derechos reservados.
 */
package com.generacionarcade.speccyos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE queryHash = :hash LIMIT 1")
    suspend fun getCachedResponse(hash: String): AiCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(aiCache: AiCache)

    @Query("DELETE FROM ai_cache WHERE timestamp < :expirationTime")
    suspend fun deleteExpiredCache(expirationTime: Long)
}
