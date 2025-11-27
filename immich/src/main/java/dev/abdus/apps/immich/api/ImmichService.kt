package dev.abdus.apps.immich.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

private const val HEADER_API_KEY = "x-api-key"

interface ImmichService {
    @GET("server/about")
    suspend fun getServerInfo(): kotlinx.serialization.json.JsonObject

    @GET("albums")
    suspend fun getAlbums(): List<ImmichAlbum>

    @GET("tags")
    suspend fun getTags(): List<ImmichTag>

    @POST("search/random")
    suspend fun getRandomAssets(@Body request: RandomRequest): List<ImmichAsset>

    @retrofit2.http.PUT("assets")
    suspend fun updateAssets(@Body request: UpdateAssetsRequest)

    @Serializable
    data class RandomRequest(
        val albumIds: List<String>? = null,
        val tagIds: List<String>? = null,
        val size: Int = 10,
        val isFavorite: Boolean? = null
    )

    @Serializable
    data class UpdateAssetsRequest(
        val ids: List<String>,
        val isFavorite: Boolean? = null
    )

    companion object {
        fun create(baseUrl: String, apiKey: String): ImmichService {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true  // Changed to true so size parameter is sent
                explicitNulls = false
            }
            val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(ApiKeyInterceptor(apiKey))
                .addInterceptor(loggingInterceptor)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .client(client)
                .build()
                .create(ImmichService::class.java)
        }
    }
}

@Serializable
data class ImmichAlbum(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String?,
    val assetCount: Int
)

@Serializable
data class ImmichTag(
    val id: String,
    val name: String,
    val value: String
)

@Serializable
data class ImmichAsset(
    val id: String,
    val albumId: String? = null,
    val originalFileName: String? = null,
    val originalMimeType: String? = null,
    val ownerId: String? = null,
    val resized: Boolean? = null,
    val originalPath: String,
    val deviceAssetId: String? = null
)

private class ApiKeyInterceptor(
    private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val newRequest = chain.request().newBuilder()
            .addHeader(HEADER_API_KEY, apiKey)
            .build()
        return chain.proceed(newRequest)
    }
}

