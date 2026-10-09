package dev.abdus.apps.immich.data

import dev.abdus.apps.immich.api.IdsFilterRequest
import dev.abdus.apps.immich.api.ImmichAsset
import dev.abdus.apps.immich.api.ImmichClient
import dev.abdus.apps.immich.api.SearchFilterRequest
import dev.abdus.apps.immich.api.SearchRandomRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ImmichRepository(private val client: ImmichClient) {
    companion object {
        // Immich's /search/random "size" accepts at most 1000 results per request.
        const val MAX_RANDOM_ASSETS = 1000
    }

    suspend fun fetchAlbums(): List<ImmichAlbumUiModel> =
        client.getAlbums().map { album ->
            ImmichAlbumUiModel(
                id = album.id,
                title = album.albumName,
                coverUrl = album.albumThumbnailAssetId?.let(client::buildAssetThumbnailUrl),
                assetCount = album.assetCount,
                updatedAt = album.updatedAt,
                lastModifiedAssetTimestamp = album.lastModifiedAssetTimestamp
            )
        }

    suspend fun fetchTags(): List<ImmichTagUiModel> =
        client.getTags().map { ImmichTagUiModel(id = it.id, name = it.name) }

    /**
     * Random assets matching the configured filters. Empty album/tag selections mean no filter;
     * multiple selections match any of them.
     */
    suspend fun fetchRandomAssets(
        config: ImmichConfig,
        size: Int = MAX_RANDOM_ASSETS
    ): List<ImmichAsset> {
        val albumFilter = config.selectedAlbumIds.takeIf { it.isNotEmpty() }?.let { IdsFilterRequest(any = it.toList()) }
        val tagFilter = config.selectedTagIds.takeIf { it.isNotEmpty() }?.let { IdsFilterRequest(any = it.toList()) }
        val request = SearchRandomRequest(
            size = size,
            isFavorite = if (config.favoritesOnly) true else null,
            createdAfter = config.filterPresetDaysBack?.let {
                LocalDate.now().minusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            filter = SearchFilterRequest(albumIds = albumFilter, tagIds = tagFilter)
        )
        return client.getRandomAssets(request)
    }
}
