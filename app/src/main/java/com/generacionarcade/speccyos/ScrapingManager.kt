package com.generacionarcade.speccyos

import android.util.Log
import com.generacionarcade.speccyos.network.RetrofitInstance

object ScrapingManager {

    private val apiService = RetrofitInstance.api

    suspend fun scrapeGameData(game: Game): Game? {
        val apiKey = RetrofitInstance.API_KEY
        if (apiKey.isEmpty()) {
            Log.e("ScrapingManager", "API Key is not set.")
            return null
        }

        val cleanedTitle = cleanGameTitle(game.title)
        Log.d("ScrapingManager", "Searching for game: $cleanedTitle")

        try {
            val response = apiService.searchGameByName(apiKey, cleanedTitle)
            if (response.isSuccessful && response.body() != null) {
                val searchResult = response.body()!!
                val bestMatch = searchResult.data?.games?.firstOrNull { 
                    it.gameTitle?.equals(cleanedTitle, ignoreCase = true) == true 
                } ?: searchResult.data?.games?.firstOrNull()

                if (bestMatch != null) {
                    val imgResponse = apiService.getGameImages(apiKey, bestMatch.id)
                    var boxArtUrl: String? = null
                    
                    if (imgResponse.isSuccessful && imgResponse.body() != null) {
                        val imgData = imgResponse.body()!!.data
                        val images = imgData.images[bestMatch.id.toString()]
                        val image = images?.find { it.type == "boxart" } 
                                 ?: images?.find { it.type == "fanart" }
                                 ?: images?.find { it.type == "screenshot" }

                        if (image != null) {
                            boxArtUrl = "${imgData.baseUrl.original}${image.filename}"
                        }
                    }
                    
                    return game.copy(
                        description = bestMatch.overview ?: game.description,
                        developer = bestMatch.developer?.toString() ?: game.developer,
                        releaseDate = bestMatch.releaseDate ?: game.releaseDate,
                        genre = bestMatch.genres?.joinToString(", ") ?: game.genre,
                        boxArt = boxArtUrl ?: game.boxArt
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ScrapingManager", "Scraping failed", e)
        }

        return null
    }

    private fun cleanGameTitle(title: String): String {
        return title.replace(Regex("""\s*\(.*?\)\s*"""), "")
                    .replace(Regex("""\s*\[.*?]\s*"""), "")
                    .trim()
    }
}
