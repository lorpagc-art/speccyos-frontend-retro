package com.generacionarcade.speccyos.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IgdbGame(
    val id: Long,
    val name: String,
    val summary: String?,
    val cover: IgdbCover?,
    @Json(name = "first_release_date") val firstReleaseDate: Long?,
    val genres: List<IgdbGenre>?,
    val involved_companies: List<IgdbInvolvedCompany>?
)

@JsonClass(generateAdapter = true)
data class IgdbCover(
    val id: Long,
    val url: String?
)

@JsonClass(generateAdapter = true)
data class IgdbGenre(
    val id: Long,
    val name: String
)

@JsonClass(generateAdapter = true)
data class IgdbInvolvedCompany(
    val id: Long,
    val company: IgdbCompany?,
    val developer: Boolean
)

@JsonClass(generateAdapter = true)
data class IgdbCompany(
    val id: Long,
    val name: String
)

@JsonClass(generateAdapter = true)
data class IgdbTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "token_type") val tokenType: String
)
