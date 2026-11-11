package com.vonko.cloudtorrent.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AddTorrentRequest(
    val magnet_link: String
)

data class ApiMessage(
    val success: Boolean = false,
    val already_exists: Boolean = false,
    val message: String? = null,
    val detail: String? = null
)

data class TorrentDto(
    val hash: String = "",
    val name: String = "Unnamed torrent",
    val progress: Double = 0.0,

    @SerializedName("download_speed")
    val dlspeed: Long = 0L,

    @SerializedName("upload_speed")
    val upspeed: Long = 0L,

    val state: String = "unknown",
    val size: Long = 0L,
    val ratio: Double = 0.0
)

interface CloudTorrentApi {

    @GET("api/torrents")
    suspend fun getTorrents(): List<TorrentDto>

    @POST("api/torrents")
    suspend fun addTorrent(
        @Body request: AddTorrentRequest
    ): ApiMessage

    @POST("api/torrents/{hash}/pause")
    suspend fun pauseTorrent(
        @Path("hash") hash: String
    ): ApiMessage

    @POST("api/torrents/{hash}/resume")
    suspend fun resumeTorrent(
        @Path("hash") hash: String
    ): ApiMessage

    @DELETE("api/torrents/{hash}")
    suspend fun deleteTorrent(
        @Path("hash") hash: String,
        @Query("delete_files") deleteFiles: Boolean = false
    ): ApiMessage
}

object CloudTorrentApiFactory {

    fun create(serverUrl: String): CloudTorrentApi {
        val normalized = serverUrl.trim().let {
            if (it.endsWith("/")) it else "$it/"
        }

        require(
            normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        ) {
            "Enter a valid HTTP or HTTPS server URL."
        }

        return Retrofit.Builder()
            .baseUrl(normalized)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudTorrentApi::class.java)
    }
}
