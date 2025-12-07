package dev.abdus.apps.immich.data

import dev.abdus.apps.immich.api.ImmichAlbum
import dev.abdus.apps.immich.api.ImmichAsset
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.SearchRandomRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImmichRepository(private val client: ImmichClient) {
    companion object {
        private const val TAG = "ImmichRepository"
    }

    suspend fun fetchAlbums(): List<ImmichAlbum> {
        val result = withContext(Dispatchers.IO) { client.getAlbums() }
        return result.map {
            if (it.albumThumbnailAssetId != null) {
               it.albumThumbnailAssetThumbnailUrl = client.buildAssetThumbnailUrl(it.albumThumbnailAssetId)
            }
            it
        }
    }

    suspend fun fetchTags(): List<dev.abdus.apps.immich.api.ImmichTag> =
        withContext(Dispatchers.IO) { client.getTags() }

    suspend fun fetchRandomAssets(
        albumIds: List<String>?,
        tagIds: List<String>?,
        favoritesOnly: Boolean = false,
        createdAfter: String? = null,
        createdBefore: String? = null
    ): List<ImmichAsset> = withContext(Dispatchers.IO) {
        val request = SearchRandomRequest(
            albumIds = albumIds,
            tagIds = tagIds,
            size = 10,
            isFavorite = if (favoritesOnly) true else null,
            createdAfter = createdAfter,
            createdBefore = createdBefore
        )
        val result = client.getRandomAssets(request)
        result.map {
            it.downloadUrl = client.buildAssetDownloadUrl(it.id)
            it.previewUrl = client.buildAssetPreviewUrl(it.id)
            it.viewUrl = client.buildAssetViewUrl(it.id)
            it
        }
    }
}
