package dev.abdus.apps.immich.data

import dev.abdus.apps.immich.api.ImmichAlbum
import dev.abdus.apps.immich.api.ImmichAsset
import dev.abdus.apps.immich.api.ImmichClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImmichRepository {
    companion object {
        private const val TAG = "ImmichRepository"
    }

    suspend fun fetchAlbums(service: ImmichClient): List<ImmichAlbum> =
        withContext(Dispatchers.IO) { service.getAlbums() }

    suspend fun fetchTags(service: ImmichClient): List<dev.abdus.apps.immich.api.ImmichTag> =
        withContext(Dispatchers.IO) { service.getTags() }

    suspend fun fetchRandomAssets(
        service: ImmichClient,
        albumIds: List<String>?,
        tagIds: List<String>?,
        favoritesOnly: Boolean = false,
        createdAfter: String? = null,
        createdBefore: String? = null
    ): List<ImmichAsset> = withContext(Dispatchers.IO) {
        val request = ImmichClient.SearchRandomRequest(
            albumIds = albumIds,
            tagIds = tagIds,
            size = 10,
            isFavorite = if (favoritesOnly) true else null,
            createdAfter = createdAfter,
            createdBefore = createdBefore
        )
        android.util.Log.d(TAG, "Fetching random assets with albumIds: ${request.albumIds}, tagIds: ${request.tagIds}, size: ${request.size}, isFavorite: ${request.isFavorite}, createdAfter: ${request.createdAfter}, createdBefore: ${request.createdBefore}")
        val result = service.getRandomAssets(request)
        android.util.Log.d(TAG, "Response: ${result.size} assets")
        result
    }
}
