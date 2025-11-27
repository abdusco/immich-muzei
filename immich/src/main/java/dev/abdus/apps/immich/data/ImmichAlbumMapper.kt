package dev.abdus.apps.immich.data

import dev.abdus.apps.immich.api.ImmichAlbum

object ImmichAlbumMapper {
    fun toUiModel(album: ImmichAlbum, serverUrl: String, apiKey: String): ImmichAlbumUiModel {
        val cover = album.albumThumbnailAssetId?.let { assetId ->
            // Remove /api suffix if present, then build correct thumbnail URL
            val base = serverUrl.removeSuffix("/api").removeSuffix("/")
            "$base/api/assets/$assetId/thumbnail?size=thumbnail&apiKey=$apiKey"
        }
        return ImmichAlbumUiModel(
            id = album.id,
            title = album.albumName,
            coverUrl = cover,
            assetCount = album.assetCount
        )
    }
}

