package dev.abdus.apps.immich.data

import kotlinx.serialization.Serializable

@Serializable
data class ImmichAlbumUiModel(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val assetCount: Int
)

