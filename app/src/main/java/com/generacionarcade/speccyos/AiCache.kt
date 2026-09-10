package com.generacionarcade.speccyos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCache(
    @PrimaryKey val queryHash: String,
    val responseText: String,
    val timestamp: Long = System.currentTimeMillis()
)
