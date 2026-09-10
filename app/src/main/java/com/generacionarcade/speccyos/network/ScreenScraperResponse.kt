package com.generacionarcade.speccyos.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScreenScraperResponse(
    @Json(name = "response") val response: ResponseData
)

@JsonClass(generateAdapter = true)
data class ResponseData(
    @Json(name = "jeu") val game: GameData?
)

@JsonClass(generateAdapter = true)
data class GameData(
    @Json(name = "noms") val names: List<GameName>?,
    @Json(name = "synopsis") val synopsis: List<GameSynopsis>?,
    @Json(name = "developpeur") val developer: GameDeveloper?,
    @Json(name = "editeur") val publisher: GamePublisher?,
    @Json(name = "dates") val dates: List<GameDate>?,
    @Json(name = "genres") val genres: List<GameGenre>?,
    @Json(name = "medias") val medias: List<GameMedia>?,
    @Json(name = "note") val note: GameNote? // Modificado para recibir objeto JSON
)

@JsonClass(generateAdapter = true)
data class GameName(
    @Json(name = "langue") val language: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GameSynopsis(
    @Json(name = "langue") val language: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GameDeveloper(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GamePublisher(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GameDate(
    @Json(name = "region") val region: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class GameGenre(
    @Json(name = "noms") val noms: List<GameName>?
)

@JsonClass(generateAdapter = true)
data class GameMedia(
    @Json(name = "type") val type: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "region") val region: String?
)

@JsonClass(generateAdapter = true)
data class GameNote(
    @Json(name = "text") val text: String?
)
