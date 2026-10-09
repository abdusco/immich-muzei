package dev.abdus.apps.immich.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.abdus.apps.immich.BuildConfig
import dev.abdus.apps.immich.data.ImmichConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

private const val HEADER_API_KEY = "x-api-key"

interface ImmichApi {
    @GET("server/about")
    suspend fun getServerInfo(): kotlinx.serialization.json.JsonObject

    @GET("server/version")
    suspend fun getServerVersion(): ServerVersionResponseDto

    @GET("albums")
    suspend fun getAlbums(): List<ImmichAlbum>

    @GET("tags")
    suspend fun getTags(): List<ImmichTag>

    @POST("search/random")
    suspend fun getRandomAssets(@Body request: SearchRandomRequest): List<ImmichAsset>

    @PUT("assets")
    suspend fun updateAssets(@Body request: UpdateAssetsRequest)
}

class ImmichClient private constructor(
    val baseUrl: String,
    val apiKey: String,
    private val api: ImmichApi,
) : ImmichApi by api {
    fun buildAssetDownloadUrl(assetId: String): String {
        return "${baseUrl}assets/$assetId/original?apiKey=$apiKey"
    }

    fun buildAssetThumbnailUrl(assetId: String): String {
        return "${baseUrl}assets/$assetId/thumbnail?size=thumbnail&apiKey=$apiKey"
    }

    fun buildAssetViewUrl(assetId: String): String {
        return "${baseUrl}photos/$assetId"
    }

    companion object {
        /**
         * Shared HTTP client for everything that talks to Immich (API, image loading, downloads).
         * Derived clients via [OkHttpClient.newBuilder] share its connection pool and threads.
         */
        val http: OkHttpClient = OkHttpClient()

        @Volatile
        private var cached: ImmichClient? = null

        /** Client for the configured server, reusing the previous one if server and key are unchanged. */
        fun fromConfig(config: ImmichConfig): ImmichClient? {
            if (!config.isConfigured) return null
            val baseUrl = checkNotNull(config.apiBaseUrl)
            val apiKey = checkNotNull(config.apiKey)
            cached?.let { if (it.baseUrl == baseUrl && it.apiKey == apiKey) return it }
            return create(baseUrl, apiKey).also { cached = it }
        }

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true  // so the size parameter is sent
            explicitNulls = false
        }

        fun create(baseUrl: String, apiKey: String): ImmichClient {
            val baseUrlClean = baseUrl.trimEnd('/') + "/"
            val apiKeyClean = apiKey.trim()

            val client = http.newBuilder()
                .addInterceptor(ApiKeyInterceptor(apiKeyClean))
                .apply {
                    // BASIC logs method, URL and status only; API calls carry the key in a header.
                    if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
                .build()

            val retrofitApi = Retrofit.Builder()
                .baseUrl(baseUrlClean)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .client(client)
                .build()
                .create(ImmichApi::class.java)

            return ImmichClient(baseUrlClean, apiKeyClean, retrofitApi)
        }
    }
}

@Serializable
data class SearchRandomRequest(
    val size: Int = 10,
    // Only images: the provider and Muzei can't display videos.
    val type: String? = "IMAGE",
    // Include exifInfo (file size) in the response so no per-asset request is needed.
    val withExif: Boolean? = true,
    val isFavorite: Boolean? = null,
    // Filter assets created after this timestamp (ISO-8601 string expected by the API)
    val createdAfter: String? = null,
    val filter: SearchFilterRequest? = null
)

@Serializable
data class SearchFilterRequest(
    val albumIds: IdsFilterRequest? = null,
    val tagIds: IdsFilterRequest? = null
)

@Serializable
data class IdsFilterRequest(
    val any: List<String>? = null
)

@Serializable
data class ServerVersionResponseDto(
    val major: Int,
    val minor: Int,
    val patch: Int
)

/**
 * Minimum Immich server version this client targets. Versions older than this
 * don't expose `filter.albumIds.any` (added in v3.2.0), so they aren't supported.
 */
object ImmichMinServerVersion {
    const val MAJOR = 3
    const val MINOR = 2
    const val PATCH = 0

    fun isSupported(version: ServerVersionResponseDto): Boolean {
        if (version.major != MAJOR) return version.major > MAJOR
        if (version.minor != MINOR) return version.minor > MINOR
        return version.patch >= PATCH
    }

    fun label(): String = "v$MAJOR.$MINOR.$PATCH"
}

@Serializable
data class UpdateAssetsRequest(
    val ids: List<String>,
    val isFavorite: Boolean? = null
)


@Serializable
data class ImmichAlbum(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String?,
    val assetCount: Int,
    val updatedAt: String? = null,
    val lastModifiedAssetTimestamp: String? = null
)

@Serializable
data class ImmichTag(
    val id: String,
    val name: String
)

@Serializable
data class ImmichExif(
    val fileSizeInByte: Long? = null
)

@Serializable
data class ImmichAsset(
    val id: String,
    val originalFileName: String? = null,
    val fileCreatedAt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val originalMimeType: String? = null,
    val exifInfo: ImmichExif? = null,
) {
    fun createdDate(): String {
        return fileCreatedAt?.substringBefore('T') ?: "Unknown"
    }
}

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
