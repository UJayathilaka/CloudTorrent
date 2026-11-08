package com.vonko.cloudtorrent.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AddTorrentRequest(val magnet_uri: String)
data class ApiMessage(val status: String? = null, val detail: String? = null)
data class TorrentDto(
    val hash: String = "",
    val name: String = "Unnamed torrent",
    val progress: Double = 0.0,
    val dlspeed: Long = 0L,
    val upspeed: Long = 0L,
    val eta: Long = 0L,
    val state: String = "unknown",
    val size: Long = 0L,
    val amount_left: Long = 0L
)

interface CloudTorrentApi {
    @GET("api/torrents")
    suspend fun getTorrents(): List<TorrentDto>

    @POST("api/torrents")
    suspend fun addTorrent(@Body request: AddTorrentRequest): ApiMessage

    @POST("api/torrents/{hash}/pause")
    suspend fun pauseTorrent(@Path("hash") hash: String): ApiMessage

    @POST("api/torrents/{hash}/resume")
    suspend fun resumeTorrent(@Path("hash") hash: String): ApiMessage

    @DELETE("api/torrents/{hash}")
    suspend fun deleteTorrent(@Path("hash") hash: String, @Query("delete_files") deleteFiles: Boolean = false): ApiMessage
}

object CloudTorrentApiFactory {
    fun create(serverUrl: String, token: String): CloudTorrentApi {
        val normalized = serverUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        require(normalized.startsWith("https://", ignoreCase = true)) { "Server URL must use HTTPS." }
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
            })
            .build()
        return Retrofit.Builder().baseUrl(normalized).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(CloudTorrentApi::class.java)
    }
}
